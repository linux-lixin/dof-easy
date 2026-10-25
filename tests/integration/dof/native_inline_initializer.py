"""Bounded emulation of the pinned inline initializer, with no host native IO.

The CRC accelerator computes the original result; it does not skip a checksum
comparison. Its register/flag effects are compared with the native loop first.
A recovered memory image is research evidence, NOT a loadable/repacked ELF.
"""
import argparse
import hashlib
import json
from pathlib import Path
import random
import struct
import sys
import zlib

sys.path.insert(0, str(Path(__file__).resolve().parent))
from native_rename_oracle import Machine, replacement_source
from scripts.dof.native_candidates import inline_candidate
from unicorn import UcError, UC_HOOK_CODE, UC_HOOK_INTR, UC_QUERY_TIMEOUT
from unicorn.x86_const import (UC_X86_REG_EAX, UC_X86_REG_ECX, UC_X86_REG_EDX,
    UC_X86_REG_EDI, UC_X86_REG_ESP, UC_X86_REG_EFLAGS, UC_X86_REG_EIP)

INLINE_PIN = 'd413b7df6c928b519986a9ceb3dbed4c78148564cca352d579f18e3650819da8'
REGS = [UC_X86_REG_EAX, UC_X86_REG_ECX, UC_X86_REG_EDX,
        UC_X86_REG_EDI, UC_X86_REG_ESP, UC_X86_REG_EFLAGS]


def crc_result(data, seed, table):
    previous = zlib.crc32(data[:-1], seed ^ 0xffffffff) ^ 0xffffffff
    return (zlib.crc32(data, seed ^ 0xffffffff) ^ 0xffffffff,
            table[(data[-1] ^ previous) & 255])


def crc_model(machine):
    u, base = machine.u, machine.base
    table = list(struct.unpack('<256I', u.mem_read(base + 0x39cf8c, 1024)))
    for i, value in enumerate(table):
        crc = i
        for _ in range(8):
            crc = (crc >> 1) ^ (0xedb88320 if crc & 1 else 0)
        if value != crc ^ 0xcb081198:
            raise ValueError('Unrecognized native CRC table')
    context, rng = u.context_save(), random.Random(101)
    cases = 0
    for count in (1, 2, 3, 16, 255, 4096):
        for seed in (0, 0xffffffff, 0x12345678):
            data = bytes(rng.randrange(256) for _ in range(count))
            ptr, stack = 0x71010000, 0x70080000
            u.mem_write(ptr, data)
            for reg, value in zip(REGS, (count, ptr, seed, 0xdeadbeef, stack, 0x202)):
                u.reg_write(reg, value)
            u.emu_start(base + 0x37cd35, base + 0x26805e, count=count * 20 + 10)
            crc, edi = crc_result(data, seed, table)
            expected = [0, ptr + count, crc, edi, stack, 0x246]
            if u.reg_read(UC_X86_REG_EIP) != base + 0x26805e or [u.reg_read(r) for r in REGS] != expected:
                raise ValueError('CRC accelerator differs from native registers/flags')
            expected_stack = struct.pack('<III', 0x33adfa9b, base + 0x2070ba, base + 0x37cd42)
            if bytes(u.mem_read(stack - 12, 12)) != expected_stack:
                raise ValueError('CRC accelerator differs from native temporary stack writes')
            cases += 1
    u.context_restore(context)
    return table, cases


def probe(raw, rounds):
    machine = Machine(raw, unresolved_weak_zero=True)
    u, base = machine.u, machine.base
    table, comparisons = crc_model(machine)
    u.mem_map(0x60000000, 0x2000000)
    heap, events, crc_calls, reason = 0x60000000, [], 0, None
    finalizers = []

    def crc_hook(u, address, size, unused):
        nonlocal crc_calls
        count, ptr, seed = [u.reg_read(r) for r in REGS[:3]]
        if not 0 < count <= 0x1000000:
            raise ValueError('CRC input bound exceeded')
        crc, edi = crc_result(bytes(u.mem_read(ptr, count)), seed, table)
        sp = u.reg_read(UC_X86_REG_ESP)
        u.mem_write(sp - 12, struct.pack('<III', 0x33adfa9b, base + 0x2070ba, base + 0x37cd42))
        for reg, value in ((UC_X86_REG_EAX, 0), (UC_X86_REG_ECX, ptr + count),
                (UC_X86_REG_EDX, crc), (UC_X86_REG_EDI, edi),
                (UC_X86_REG_EFLAGS, (u.reg_read(UC_X86_REG_EFLAGS) & ~0x8d5) | 0x44),
                (UC_X86_REG_EIP, base + 0x26805e)):
            u.reg_write(reg, value)
        crc_calls += 1
        if crc_calls > 100000:
            raise ValueError('CRC call bound exceeded')

    def external(u, address, size, unused):
        nonlocal heap, reason
        name = machine.imports.get(address)
        if name is None:
            reason = 'unknown-import-trap'; u.emu_stop(); return
        sp = u.reg_read(UC_X86_REG_ESP)
        args = [machine.word(sp + 4 + 4 * i) for i in range(4)]
        result = None
        events.append(dict(name=name))  # No stack contents or credentials in reports.
        if name == 'malloc':
            count = args[0]
            if count > 0x1000000 or heap + count >= 0x62000000:
                raise ValueError('Allocation bound exceeded')
            result = heap
            heap += (max(count, 1) + 15) & ~15
        elif name == 'free':
            result = 0
        elif name == '__cxa_atexit':
            finalizers.append(tuple(args[:3]))
            result = 0  # Register only. Probe never runs process/DSO teardown.
        elif name in ('memset', 'memcpy', 'memmove'):
            if args[2] > 0x1000000:
                raise ValueError('Memory operation bound exceeded')
            value = (bytes([args[1] & 255]) * args[2] if name == 'memset'
                     else bytes(u.mem_read(args[1], args[2])))
            u.mem_write(args[0], value); result = args[0]
        elif name == 'mprotect':
            if not all(p in machine.pages for p in range(args[0] & ~4095,
                    (args[0] + args[1] + 4095) & ~4095, 4096)):
                raise ValueError('mprotect outside modeled ELF')
            result = 0  # Only emulated pages are writable; no host mprotect call.
        if result is None:
            reason = 'unmodeled-import:' + name; u.emu_stop(); return
        u.reg_write(UC_X86_REG_EAX, result)
        u.reg_write(UC_X86_REG_EIP, machine.word(sp))
        u.reg_write(UC_X86_REG_ESP, sp + 4)

    def interrupt(u, number, unused):
        nonlocal reason
        reason = 'unmodeled-interrupt:' + str(number); u.emu_stop()

    u.hook_add(UC_HOOK_CODE, crc_hook, begin=base + 0x37cd35, end=base + 0x37cd35)
    u.hook_add(UC_HOOK_CODE, external, begin=0x72000000, end=0x720fffff)
    u.hook_add(UC_HOOK_INTR, interrupt)
    stack, sentinel = 0x700f0000, 0x71000000
    u.reg_write(UC_X86_REG_ESP, stack)
    machine.put(stack, sentinel); machine.put(stack + 4, 0x71001000)
    pc = base + machine.elf.tags[12]
    progress = []
    for turn in range(rounds):
        try:
            u.emu_start(pc, sentinel, timeout=45000000, count=300000000)
        except UcError as exc:
            reason = 'emulator:' + str(exc)
        pc = u.reg_read(UC_X86_REG_EIP)
        if pc == sentinel:
            reason = 'initializer-returned'
        state = dict(round=turn + 1, rva=pc - base, crcLoops=crc_calls,
                     imports=len(events), timedOut=bool(u.query(UC_QUERY_TIMEOUT)))
        progress.append(state)
        print(json.dumps(state), flush=True)
        if reason:
            break
    image = bytes(u.mem_read(base, 0x3cd000))
    literals = []
    # Reviewed string addresses only; never dump arbitrary data as text.
    for label, rva in [('database', 0x388a8), ('gateway-config', 0x38a8e),
                       ('robot-command', 0x38b4d), ('upstream-loader', 0x389bf)]:
        end = image.find(b'\0', rva, rva + 100)
        value = image[rva:end] if end >= 0 else b''
        if value and all(32 <= x < 127 for x in value):
            literals.append(dict(role=label, rva=rva, value=value.decode()))
    report = dict(schema=1, sourceSha256=INLINE_PIN, hostNativeExecution=False,
                  serverAccessed=False, releaseReady=False, stop=reason or 'execution-budget',
                  crcDifferentialCases=comparisons, crcLoops=crc_calls,
                  unresolvedWeakSymbols='zero (ELF STB_WEAK)',
                  memoryShims=['malloc', 'free', 'memset', 'memcpy', 'memmove', 'mprotect', '__cxa_atexit'],
                  registeredFinalizers=len(finalizers),
                  progress=progress, imports=events, recoveredLiterals=literals,
                  imageSha256=hashlib.sha256(image).hexdigest(),
                  exportBytes=image[0x7ede:0x7efe].hex(),
                  limitations=['Emulated dependencies, no real dynamic loader or game process',
                               'Memory image is not a repacked ELF or renamed release'])
    return image, report


def main():
    p = argparse.ArgumentParser(description=__doc__)
    p.add_argument('--inline', type=Path, required=True)
    p.add_argument('--output', type=Path, required=True)
    p.add_argument('--candidate', type=Path, help='Optional deterministic local inline candidate')
    p.add_argument('--rounds', type=int, choices=range(1, 5), default=1)
    args = p.parse_args()
    if args.output.exists() or args.output.is_symlink():
        raise ValueError('Use a new research output directory')
    raw = replacement_source(args.inline, INLINE_PIN)
    if args.candidate:
        expected, _ = inline_candidate(raw)
        if args.candidate.read_bytes() != expected:
            raise ValueError('Inline candidate differs from the pinned derivation')
        raw = expected
    image, report = probe(raw, args.rounds)
    report['inputSha256'] = hashlib.sha256(raw).hexdigest()
    report['candidate'] = bool(args.candidate)
    args.output.mkdir(parents=True, mode=0o700)
    for name, data in [('restored-memory.bin', image),
                       ('report.json', (json.dumps(report, indent=2) + '\n').encode())]:
        path = args.output / name
        with path.open('xb') as stream:
            stream.write(data)
        path.chmod(0o600)


if __name__ == '__main__':
    main()
