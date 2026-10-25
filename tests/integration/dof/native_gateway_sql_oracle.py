"""Execute full fixed SQL construction blocks through their original consumer.

Local instruction emulation only; no database/server access. This checks the
actual stack-built SQL and the call adapter, not all gateway business behavior.
"""
import argparse
import json
from pathlib import Path
import struct
import re
import sys

ROOT = Path(__file__).resolve().parents[3]
sys.path.insert(0, str(ROOT))
from scripts.dof.native_candidates import (GATEWAY_PIN, SQL_CALLS_PIN, SQL_LITERALS_PIN,
    digest, gateway_candidate, gateway_sql_calls, gateway_sql_literals, replacement_source)
from tests.integration.dof.native_rename_oracle import Machine
import unicorn.x86_const as x86

REGISTERS = ('rax', 'rbx', 'rcx', 'rdx', 'rsi', 'rdi', 'rbp', 'rsp',
             'r8', 'r9', 'r10', 'r11', 'r12', 'r13', 'r14', 'r15', 'eflags')
MACHINES = {}


def read_sql(machine, pointer):
    literal = bytearray()
    for offset in range(65536):
        byte = machine.u.mem_read(pointer + offset, 1)[0]
        if byte == 0:
            return literal.decode('utf-8')
        literal.append(byte)
    raise AssertionError('Native SQL has no bounded terminator')


def execute(raw, row, frame):
    key = digest(raw)
    if key not in MACHINES:
        machine = Machine(raw)
        writable = [(p['address'], bytes(machine.u.mem_read(p['address'], p['memorySize'])))
                    for p in machine.elf.segments if p['type'] == 1 and p['flags'] & 2]
        MACHINES[key] = machine, machine.u.context_save(), writable
    machine, context, writable = MACHINES[key]
    machine.u.context_restore(context)
    for address, data in writable:
        machine.u.mem_write(address, data)
    for address in (0x70000000, 0x71000000):
        machine.u.mem_write(address, bytes(0x100000))
    stack = frame - 0x20000
    for index, name in enumerate(REGISTERS):
        register = getattr(x86, 'UC_X86_REG_' + name.upper())
        # Live C++ receivers are caller-provided heap objects. Allocate distinct
        # valid storage for unspecified registers instead of null receivers.
        machine.u.reg_write(register, 0x202 if name == 'eflags' else 0x71020000 + index * 4096)
    for name, value in row['initialRegisters'].items():
        if name not in REGISTERS:
            continue
        if isinstance(value, list):
            base = frame if value[0] == 'rbp' else stack
            value = base + value[1]
        machine.u.reg_write(getattr(x86, 'UC_X86_REG_' + name.upper()), value)
    machine.u.reg_write(x86.UC_X86_REG_RBP, frame)
    machine.u.reg_write(x86.UC_X86_REG_RSP, stack)
    # This basic block clears its SQL array through a saved local pointer.
    # Supply that real caller precondition; do not replace REP STOS execution.
    if row['call'] == 0x44201e:
        machine.u.mem_write(frame - 0x260, struct.pack('<Q', frame - 0x130))
    machine.u.emu_start(row['blockStart'], row['target'], count=10000)
    if machine.u.reg_read(x86.UC_X86_REG_RIP) != row['target']:
        raise AssertionError('SQL consumer was not reached')
    registers = {name: machine.u.reg_read(getattr(x86, 'UC_X86_REG_' + name.upper()))
                 for name in REGISTERS}
    text = read_sql(machine, registers['rsi'])
    memory = (bytes(machine.u.mem_read(0x70000000, 0x100000)),
              bytes(machine.u.mem_read(0x71000000, 0x100000)))
    return text, registers, memory


def verify(source):
    candidate, recipe = gateway_candidate(source)
    by_site = {row['callSite']: row for row in recipe['sqlCalls']}
    cases = []
    for frame in (0x700d0000, 0x700f0000):
        for row in gateway_sql_calls():
            original, registers, memory = execute(source, row, frame)
            adapted, new_registers, new_memory = execute(candidate, row, frame)
            if original != row['text']:
                raise AssertionError('Original SQL construction differs from reviewed literal at ' + hex(row['call']))
            if adapted != by_site[row['call']]['value']:
                raise AssertionError('Candidate did not pass new SQL to original consumer')
            # The old stack SQL is now deliberately retired. The sole memory
            # differences allowed are printable bytes inside its exact original
            # allocation, changed to spaces. No pointer/length/guard is relaxed.
            pointer=registers['rsi'];length=len(row['text'].encode())
            for base,old_mem,new_mem in zip((0x70000000,0x71000000),memory,new_memory):
                for i,(before,after) in enumerate(zip(old_mem,new_mem)):
                    if before!=after and not (pointer<=base+i<pointer+length and after==32 and 32<=before<127):
                        raise AssertionError('Retirement altered unrelated memory')
                if base<=pointer<base+len(new_mem):
                    scratch=new_mem[pointer-base:pointer-base+length]
                    if re.search(rb'(?i)qk|qingkong',scratch):
                        raise AssertionError('Legacy identifier remains in retired stack SQL')
            for name in REGISTERS:
                if name=='rsi' or registers[name]==new_registers[name]:continue
                # These are caller-saved scratch registers, not arguments to
                # either reviewed mysql wrapper. Both wrappers call mysql_ping
                # before their first RAX read; RCX is not an incoming argument.
                if name not in ('rax','rcx') or new_registers[name]!=0x2020202020202020:
                    raise AssertionError('SQL adapter altered live ABI state: '+name)
            cases.append({'callSite': row['call'], 'frame': frame, 'passed': True})
        print('Verified', len(by_site), 'SQL consumers at stack frame', hex(frame), flush=True)
    literal_cases = []
    for row, new in zip(gateway_sql_literals(), recipe['sqlLiterals']):
        for reference in row['references']:
            name = 'rsi' if bytes.fromhex(reference['before'])[0] == 0xbe else 'rdx'
            for raw, expected in ((source, row['text']), (candidate, new['value'])):
                machine, context, _ = MACHINES[digest(raw)]
                machine.u.context_restore(context)
                before = {n: machine.u.reg_read(getattr(x86, 'UC_X86_REG_' + n.upper()))
                          for n in REGISTERS}
                address = reference['address']
                machine.u.emu_start(address, address + 5, count=1)
                register = getattr(x86, 'UC_X86_REG_' + name.upper())
                if read_sql(machine, machine.u.reg_read(register)) != expected:
                    raise AssertionError('Incorrect native SQL literal reference')
                for n in REGISTERS:
                    if n != name and machine.u.reg_read(getattr(x86, 'UC_X86_REG_' + n.upper())) != before[n]:
                        raise AssertionError('Literal relocation changed unrelated registers')
            literal_cases.append({'instruction': reference['address'], 'register': name, 'passed': True})
    print('Verified', len(literal_cases), 'SQL literal references', flush=True)
    return {'schema': 1, 'sourceSha256': digest(source), 'candidateSha256': digest(candidate),
            'callSites': len(by_site), 'cases': cases, 'liveAbiAndNonSqlMemoryPreserved': True, 'retiredScratchBuffersHaveNoLegacyNames': True,
            'literalCases': literal_cases, 'literalCount': len(recipe['sqlLiterals']),
            'callRecipeSha256': SQL_CALLS_PIN, 'literalRecipeSha256': SQL_LITERALS_PIN,
            'oracleSha256': digest(Path(__file__).read_bytes()),
            'realDatabaseExecution': False, 'releaseReady': False}


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--gateway', required=True, type=Path)
    parser.add_argument('--report', required=True, type=Path)
    args = parser.parse_args()
    if args.report.exists():
        raise ValueError('Report must be new')
    report = verify(replacement_source(args.gateway, GATEWAY_PIN))
    args.report.write_text(json.dumps(report, indent=2) + '\n')
    print('Passed', len(report['cases']), 'full SQL construction/call-adapter cases; not full gateway acceptance')


if __name__ == '__main__':
    main()
