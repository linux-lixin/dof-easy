"""Execute pinned native rename call sites in Unicorn, never in the host OS.

A stop at fopen/ConfigParser.load/DBMgr.open2 is evidence of the supplied
argument only. It is not plugin initialization, database or game acceptance.
"""
import argparse
import hashlib
import json
from pathlib import Path
import struct
import sys

ROOT = Path(__file__).resolve().parents[3]
sys.path.insert(0, str(ROOT))
from scripts.dof.native_candidates import Elf, PLUGIN_PIN, GATEWAY_PIN, replacement_source, plugin_candidate, gateway_candidate
from unicorn import Uc, UcError, UC_ARCH_X86, UC_MODE_32, UC_MODE_64, UC_HOOK_CODE, UC_HOOK_INTR
from unicorn.x86_const import UC_X86_REG_ESP, UC_X86_REG_EIP, UC_X86_REG_EAX, UC_X86_REG_EBP, UC_X86_REG_ESI, UC_X86_REG_RSI, UC_X86_REG_RDI, UC_X86_REG_RAX, UC_X86_REG_RDX


class Machine:
    def __init__(self, raw, base=0x10000000, unresolved_weak_zero=False):
        self.elf = Elf(raw)
        self.base = 0 if self.elf.wide else base
        self.unresolved_weak_zero = unresolved_weak_zero
        self.u = Uc(UC_ARCH_X86, UC_MODE_64 if self.elf.wide else UC_MODE_32)
        self.pages = set()
        for p in self.elf.segments:
            if p['type'] != 1:
                continue
            for page in range((self.base + p['address']) & ~4095,
                              (self.base + p['address'] + p['memorySize'] + 4095) & ~4095, 4096):
                if page not in self.pages:
                    self.u.mem_map(page, 4096); self.pages.add(page)
            if p['fileSize']:
                self.u.mem_write(self.base + p['address'], raw[p['offset']:p['offset'] + p['fileSize']])
        self.u.mem_map(0x70000000, 0x100000)  # synthetic stack
        self.u.mem_map(0x71000000, 0x100000)  # synthetic controller and data
        self.u.mem_map(0x72000000, 0x100000)  # unresolved import traps
        self.imports = {}
        if not self.elf.wide:
            self.relocate()
        self.count = 0
        self.boundary = None
        self.value = None
        self.last = []

    def word(self, address):
        return struct.unpack('<I', self.u.mem_read(address, 4))[0]

    def put(self, address, value):
        self.u.mem_write(address, struct.pack('<I', value & 0xffffffff))

    def text(self, address):
        out = bytearray()
        for i in range(4096):
            value = bytes(self.u.mem_read(address + i, 1))
            if value == b'\0':
                return out.decode('utf-8')
            out.extend(value)
        raise ValueError('Unterminated/bounded native string')

    def relocate(self):
        tags, symbols = self.elf.tags, {}
        trap = 0x72000000
        for rva, size in [(tags.get(17, 0), tags.get(18, 0)), (tags.get(23, 0), tags.get(2, 0))]:
            for at in range(self.base + rva, self.base + rva + size, 8):
                where, info = struct.unpack('<II', self.u.mem_read(at, 8))
                kind, index, value = info & 255, info >> 8, 0
                if index:
                    ni, symbol, _, info_byte, _, section = struct.unpack('<IIIBBH', self.u.mem_read(self.base + tags[6] + index * 16, 16))
                    if section:
                        value = self.base + symbol
                    elif self.unresolved_weak_zero and info_byte >> 4 == 2:
                        value = 0  # ELF STB_WEAK without an available definition.
                    else:
                        name = self.text(self.base + tags[5] + ni)
                        if name not in symbols:
                            symbols[name] = trap; self.imports[trap] = name; trap += 16
                        value = symbols[name]
                address = self.base + where
                old = self.word(address)
                if kind == 8: self.put(address, self.base + old)
                elif kind in (6, 7): self.put(address, value)
                elif kind == 1: self.put(address, value + old)
                elif kind == 2: self.put(address, value + old - address)
                elif kind in (14, 35): self.put(address, 0)  # unused by reviewed slices
                elif kind != 0: raise ValueError('Unsupported relocation: ' + str(kind))

    def run_plugin(self, role):
        self.u.reg_write(UC_X86_REG_ESP, 0x700f0000)
        self.put(0x700f0000, 0x71000000)
        self.put(0x700f0004, 0x71001000)
        if role in ('rarity-tag', 'randombox-tag'):
            entry = 0x30a00 if role == 'rarity-tag' else 0x30a20
            self.u.emu_start(self.base + entry, self.base + entry + 16, timeout=1000000, count=20)
            return dict(role=role, loadBase=hex(self.base), boundary='native-tag-getter-return',
                        value=self.text(self.u.reg_read(UC_X86_REG_EAX)), scope='tag-getter-only')
        if role == 'database':
            # Explicit basic-block precondition; this is NOT full initialization.
            self.u.reg_write(UC_X86_REG_EAX, self.base + self.elf.tags[3])
            self.u.reg_write(UC_X86_REG_EBP, 0x700e0000)
            self.u.reg_write(UC_X86_REG_ESI, 0x71002000)
            self.put(0x700e0008, 0x71001000)
        entries = {'main-config': 0x31880, 'anton-config': 0x9edf0,
                   'luke-config': 0xaf800, 'database': 0x740a3f}

        def hook(u, address, size, unused):
            self.count += 1
            self.last.append(hex(address - self.base)); self.last[:] = self.last[-12:]
            sp = u.reg_read(UC_X86_REG_ESP)
            if address in self.imports:
                name = self.imports[address]
                if role == 'main-config' and name == 'fopen':
                    self.value = self.text(self.word(sp + 4)); self.boundary = 'fopen'
                    u.emu_stop(); return
                raise RuntimeError('Unmodelled native import: ' + name)
            if address == self.base + 0xa7a10 and role in ('anton-config', 'luke-config'):
                self.value = self.text(self.word(sp + 8)); self.boundary = 'ConfigParserex.load'
                u.emu_stop(); return
            if address == self.base + 0x48fd0 and role == 'database':
                self.value = self.text(self.word(sp + 16)); self.boundary = 'DBMgr.open2'
                u.emu_stop(); return
            if not any(p['type'] == 1 and p['flags'] & 1 and self.base + p['address'] <= address
                       and address + size <= self.base + p['address'] + p['memorySize'] for p in self.elf.segments):
                raise RuntimeError('Execution left native executable segments')

        def interrupt(u, number, unused):
            raise RuntimeError('OS interrupt is not allowed: ' + str(number))

        self.u.hook_add(UC_HOOK_CODE, hook)
        self.u.hook_add(UC_HOOK_INTR, interrupt)
        self.u.emu_start(self.base + entries[role], 0x71000000, timeout=10000000, count=500000)
        if self.boundary is None:
            raise RuntimeError('Native call site not reached within bound: ' + repr(self.last))
        return dict(role=role, loadBase=hex(self.base), boundary=self.boundary,
                    value=self.value, instructions=self.count,
                    scope='database-basic-block' if role == 'database' else 'function-entry-to-reader-call')

    def gateway_load(self, instruction, register):
        # Execute the original x86-64 load instruction with its changed operand.
        self.u.emu_start(instruction, instruction + 5, timeout=1000000, count=1)
        return self.text(self.u.reg_read(register))


def verify(server, candidate):
    original = replacement_source(server / 'qk_plugin.so', PLUGIN_PIN)
    expected, _ = plugin_candidate(original)
    actual = (candidate / ('lib/libdof-plugin.so' if (candidate / 'manifest.json').is_file() else 'libdof-plugin.so')).read_bytes()
    if actual != expected:
        raise ValueError('Candidate differs from the deterministic patch recipe')
    old = {'main-config': '/root/qk_plugin_config.ini', 'anton-config': '/root/qk_plugin_antonRaid.ini',
           'luke-config': '/root/qk_plugin_lukeRaid.ini', 'database': 'qk',
           'rarity-tag': '[qk rarity color]', 'randombox-tag': '[qk randombox]'}
    new = {'main-config': '/data/dof-easy/config/plugin.ini', 'anton-config': '/data/dof-easy/config/anton-raid.ini',
           'luke-config': '/data/dof-easy/config/luke-raid.ini', 'database': 'dof_native',
           'rarity-tag': '[dof rarity color]', 'randombox-tag': '[dof randombox]'}
    cases = []
    for label, raw, values in [('original', original, old), ('candidate', actual, new)]:
        for base in (0x10000000, 0x20000000):
            for role, value in values.items():
                case = Machine(raw, base).run_plugin(role)
                if case['value'] != value:
                    raise AssertionError('Native reader received a different name')
                case['input'] = label; cases.append(case)
    gateway = replacement_source(server / 'QK', GATEWAY_PIN)
    derived, plan = gateway_candidate(gateway)
    if (candidate / ('bin/dof-gateway' if (candidate / 'manifest.json').is_file() else 'dof-gateway')).read_bytes() != derived:
        raise ValueError('Gateway candidate differs from the deterministic patch recipe')
    machine = Machine(derived)
    gateway_cases = []
    for row in plan['names']:
        for address in row['references']:
            value = machine.gateway_load(address, {0xbf: UC_X86_REG_RDI, 0xbe: UC_X86_REG_RSI, 0xba: UC_X86_REG_RDX}[row['opcode']])
            if value != row['value']:
                raise AssertionError('Gateway operand did not load the new string')
            gateway_cases.append(dict(instruction=hex(address), value=value, scope='one-native-operand-load'))
    for label, raw, expected in [('original', gateway, b'libantisvrinline.so'),
                                  ('candidate', derived, b'libdof-inline.so')]:
        instance = Machine(raw)
        buffer = 0x71001000
        before = b'guardABC' + b'libantisvrimport.so\0' + b'guardXYZ'
        instance.u.mem_write(buffer - 8, before)
        instance.u.reg_write(UC_X86_REG_RAX, buffer)
        instance.u.emu_start(plan['gameLoader']['start'], plan['gameLoader']['end'], count=19, timeout=1000000)
        after = bytes(instance.u.mem_read(buffer - 8, len(before)))
        if after != b'guardABC' + expected.ljust(19, b'\0') + b'\0guardXYZ':
            raise AssertionError('Gateway native game-loader writes escaped the original string allocation')
        gateway_cases.append(dict(input=label, value=expected.decode(), scope='19-native-game-loader-byte-stores'))
    return dict(schema=1, sourceVersion='pinned-replacement-only', hostNativeExecution=False,
                pluginSourceSha256=PLUGIN_PIN, pluginCandidateSha256=hashlib.sha256(actual).hexdigest(),
                gatewaySourceSha256=GATEWAY_PIN, gatewayCandidateSha256=hashlib.sha256(derived).hexdigest(),
                oracleSha256=hashlib.sha256(Path(__file__).read_bytes()).hexdigest(),
                serverAccessed=False, releaseReady=False, pluginCases=cases, gatewayCases=gateway_cases,
                limits=['No ELF loader/constructor acceptance', 'No database connection',
                        'No full game, Windows or integrity-check acceptance'])


def main():
    p = argparse.ArgumentParser(description=__doc__)
    p.add_argument('--server', type=Path, required=True)
    p.add_argument('--candidate', type=Path, required=True)
    p.add_argument('--report', type=Path, required=True)
    args = p.parse_args()
    report = verify(args.server, args.candidate)
    with args.report.open('x') as output:
        json.dump(report, output, indent=2); output.write('\n')
    print('Verified native call-site cases:', len(report['pluginCases']) + len(report['gatewayCases']))


if __name__ == '__main__':
    main()
