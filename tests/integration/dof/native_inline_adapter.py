"""Execute the derived ELF INIT_ARRAY callback after a verified decoder snapshot.

The C++ dependency boundary is not simulated away: this is a separate callback
unit oracle. Full DT_INIT + dependency constructors + INIT_ARRAY is unverified.
"""
import argparse
import hashlib
import json
from pathlib import Path
import struct
from native_inline_names import SITES
from native_rename_oracle import Machine, replacement_source
from scripts.dof.native_candidates import INLINE_PIN, inline_candidate
from unicorn import UcError, UC_HOOK_CODE
from unicorn.x86_const import UC_X86_REG_EAX, UC_X86_REG_EBX, UC_X86_REG_EBP, UC_X86_REG_ESP, UC_X86_REG_EIP


def callback(raw, image, plan, base, fault=None):
    machine = Machine(raw, base, unresolved_weak_zero=True)
    u = machine.u
    # Use only decoded RX bytes; keep the candidate's ELF header, dynamic
    # tables and freshly relocated imports. Never load captured heap state.
    u.mem_write(base + 0x134, image[0x134:0x44f39])
    if fault == 'preimage':
        u.mem_write(base + plan['memoryOperands'][0]['rva'], bytes(4))
    calls = []
    target = machine.word(base + plan['initArray'])
    if target != base + plan['callback']:
        raise AssertionError('INIT_ARRAY relocation did not resolve the callback')
    stack, sentinel = 0x70080000, 0x71000000
    machine.put(stack, sentinel); u.reg_write(UC_X86_REG_ESP, stack)
    def hook(u, address, size, unused):
        if address in machine.imports:
            if machine.imports[address] != 'mprotect':
                raise ValueError('Unexpected adapter import')
            sp = u.reg_read(UC_X86_REG_ESP)
            args = [machine.word(sp + 4 + 4 * i) for i in range(3)]
            expected = [[base+0x6000,0x3000,7],[base+0x6000,0x3000,5],
                        [base+0x38000,0x1000,3],[base+0x38000,0x1000,5]][len(calls)]
            if args != expected:
                raise AssertionError('Adapter requested unexpected memory protection')
            calls.append(args)
            u.reg_write(UC_X86_REG_EAX, 0xffffffff if fault == 'mprotect' else 0)
            u.reg_write(UC_X86_REG_EIP, machine.word(sp)); u.reg_write(UC_X86_REG_ESP, sp + 4)
        elif not target <= address < target + plan['callbackBytes']:
            raise ValueError('Execution left callback')
    handle = u.hook_add(UC_HOOK_CODE, hook)
    error = None
    try:
        u.emu_start(target, sentinel, count=1000, timeout=1000000)
    except UcError as exc:
        error = str(exc)
    u.hook_del(handle)
    if fault:
        if error is None or 'Invalid instruction' not in error:
            raise AssertionError('Invalid preimage/protection failure did not stop the callback')
        for operand in plan['memoryOperands']:
            if fault == 'preimage' and operand == plan['memoryOperands'][0]:
                continue
            if machine.word(base + operand['rva']) != operand['before']:
                raise AssertionError('Callback changed operands after rejecting a precondition')
        return None, dict(base=base, fault=fault, rejected=True, mprotectCalls=len(calls))
    if error or u.reg_read(UC_X86_REG_EIP) != sentinel or len(calls) != 4:
        raise AssertionError('Callback did not restore protections and return: ' + str(error))
    for operand in plan['memoryOperands']:
        if machine.word(base + operand['rva']) != operand['after']:
            raise AssertionError('Callback failed to persist new name operand')
    for row in plan['retiredLiterals']:
        if bytes(u.mem_read(base+row['address'],row['bytes']))!=bytes(row['bytes']):
            raise AssertionError('Restored legacy literal was not retired')
    return machine, dict(base=base, returned=True, mprotectCalls=len(calls))


def names(machine):
    u, base, results = machine.u, machine.base, []
    for role, start, boundary, slot, _, _, wanted in SITES:
        u.reg_write(UC_X86_REG_EBX, base + 0x45360)
        u.reg_write(UC_X86_REG_EBP, 0x71010000)
        u.reg_write(UC_X86_REG_ESP, 0x70080000)
        captured = []
        def hook(u, address, size, unused):
            if address == base + boundary:
                captured.append(machine.text(machine.word(u.reg_read(UC_X86_REG_ESP) + slot)))
                u.emu_stop()
            elif not base + start <= address < base + start + 48:
                raise ValueError('Execution left reviewed name call site')
        handle = u.hook_add(UC_HOOK_CODE, hook)
        u.emu_start(base + start, 0x71000000, count=100, timeout=1000000)
        u.hook_del(handle)
        if captured != [wanted]:
            raise AssertionError('ELF callback did not adapt the native consumer')
        results.append(dict(role=role, base=base, argument=captured[0]))
    return results


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--inline', required=True, type=Path)
    parser.add_argument('--candidate', required=True, type=Path)
    parser.add_argument('--snapshot', required=True, type=Path)
    parser.add_argument('--report', required=True, type=Path)
    args = parser.parse_args()
    if args.report.exists() or args.report.is_symlink():
        raise ValueError('Use a new report')
    source = replacement_source(args.inline, INLINE_PIN)
    raw, plan = inline_candidate(source)
    if args.candidate.read_bytes() != raw:
        raise ValueError('Unrecognized candidate')
    image = (args.snapshot / 'restored-memory.bin').read_bytes()
    evidence = json.loads((args.snapshot / 'report.json').read_text())
    if evidence['sourceSha256'] != INLINE_PIN or hashlib.sha256(image).hexdigest() != evidence['imageSha256']:
        raise ValueError('Restored image provenance mismatch')
    cases, call_sites = [], []
    for base in (0x10000000, 0x20000000):
        machine, case = callback(raw, image, plan, base)
        cases.append(case); call_sites.extend(names(machine))
    for fault in ('preimage', 'mprotect'):
        _, case = callback(raw, image, plan, 0x10000000, fault)
        cases.append(case)
    report = dict(sourceSha256=INLINE_PIN, candidateSha256=hashlib.sha256(raw).hexdigest(),
                  snapshotSha256=evidence['imageSha256'], releaseReady=False,
                  serverAccessed=False, hostNativeExecution=False,
                  scope='ELF INIT_ARRAY callback after original decoded bytes, emulated mprotect',
                  callbackCases=cases, callSites=call_sites,
                  limitations=['Original C++ dependency constructors have not returned in one complete loader run',
                               'Full game loading and Windows protocol consumer not verified'])
    with args.report.open('x') as stream:
        json.dump(report, stream, indent=2); stream.write('\n')
    print('Verified callback cases:', len(cases), 'consumer cases:', len(call_sites))


if __name__ == '__main__':
    main()
