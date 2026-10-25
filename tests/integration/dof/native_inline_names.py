"""Check post-unpack name operands in a verified emulated inline memory image.

This proves the local operand adaptation, not a durable ELF rewrite. The
protected initializer would overwrite these sites when loading the packed ELF.
"""
import argparse
import hashlib
import json
from pathlib import Path
import struct
from native_inline_initializer import INLINE_PIN
from native_rename_oracle import Machine, replacement_source
from unicorn import UC_HOOK_CODE
from unicorn.x86_const import UC_X86_REG_EBX, UC_X86_REG_EBP, UC_X86_REG_ESP

SITES = [
    ('database-string-constructor', 0x6314, 0x27ec, 8, 0x388a8, 'qk', 'dof_native'),
    ('gateway-config-read-1', 0x807e, 0x7d14, 16, 0x38a8e,
     '/root/config.ini', '/data/dof-easy/config/gateway.ini'),
    ('gateway-config-read-2', 0x85f5, 0x7d14, 16, 0x38a8e,
     '/root/config.ini', '/data/dof-easy/config/gateway.ini'),
    ('robot-string-constructor', 0x8a8b, 0x27ec, 8, 0x38b4d,
     '<qt><c>qkRobotc</c><key>', '<qt><c>dofRobotc</c><key>'),
    ('private-key-open', 0x813f, 0x27cc, 4, 0x38ab4,
     '/home/neople/game/privatekey.pem', '/data/dof-easy/keys/privatekey.pem'),
]


def check(source, image):
    results = []
    for renamed in (False, True):
        for role, start, boundary, slot, old_address, old, new in SITES:
            machine = Machine(source, unresolved_weak_zero=True)
            u, base = machine.u, machine.base
            u.mem_write(base, image)
            expected = b'\x8d\x83' + struct.pack('<i', old_address - 0x45360)
            if bytes(u.mem_read(base + start, 6)) != expected:
                raise ValueError('Unrecognized restored operand: ' + role)
            if machine.text(base + old_address) != old:
                raise ValueError('Unrecognized restored literal: ' + role)
            if renamed:
                u.mem_map(0x73000000, 4096)
                u.mem_write(0x73000000, new.encode() + b'\0')
                u.mem_write(base + start + 2, struct.pack('<I', 0x73000000 - base - 0x45360))
            u.reg_write(UC_X86_REG_EBX, base + 0x45360)
            u.reg_write(UC_X86_REG_EBP, 0x71010000)
            u.reg_write(UC_X86_REG_ESP, 0x70080000)
            captured = []
            def hook(u, address, size, unused):
                if address == base + boundary:
                    captured.append(machine.text(machine.word(u.reg_read(UC_X86_REG_ESP) + slot)))
                    u.emu_stop()
                elif not base + start <= address < base + start + 48:
                    raise ValueError('Execution left the reviewed call site')
            u.hook_add(UC_HOOK_CODE, hook)
            u.emu_start(base + start, 0x71000000, timeout=1000000, count=100)
            wanted = new if renamed else old
            if captured != [wanted]:
                raise AssertionError('Native operand did not reach the expected boundary')
            results.append(dict(role=role, modifiedMemory=renamed, callSite=start,
                                boundary=boundary, argument=captured[0]))
    return results


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--inline', required=True, type=Path)
    parser.add_argument('--snapshot', required=True, type=Path)
    parser.add_argument('--report', required=True, type=Path)
    args = parser.parse_args()
    if args.report.exists() or args.report.is_symlink():
        raise ValueError('Report must be new')
    source = replacement_source(args.inline, INLINE_PIN)
    evidence = json.loads((args.snapshot / 'report.json').read_text())
    image = (args.snapshot / 'restored-memory.bin').read_bytes()
    if evidence['sourceSha256'] != INLINE_PIN or hashlib.sha256(image).hexdigest() != evidence['imageSha256']:
        raise ValueError('Restored image provenance mismatch')
    if len(image) != 0x3cd000 or evidence['crcDifferentialCases'] != 18 or evidence['crcLoops'] != 295:
        raise ValueError('Unexpected initializer evidence')
    results = check(source, image)
    report = dict(sourceSha256=INLINE_PIN, imageSha256=evidence['imageSha256'],
                  hostNativeExecution=False, serverAccessed=False, releaseReady=False,
                  scope='post-unpack operand proof, memory changes only', cases=results,
                  blocker='Packed initializer restores old operands; no persistent ELF adaptation validated')
    with args.report.open('x') as stream:
        json.dump(report, stream, indent=2); stream.write('\n')
    print('Verified post-unpack call sites:', len(results))


if __name__ == '__main__':
    main()
