"""Follow the original VM-protected random-box function to its native SQL reader.

Synthetic DB/user objects are diagnostic inputs, not a functional game test.
Only malloc/free/memory operations are modeled. SQL/results are never mocked:
execution stops before formatting or executing the first query.
"""
import argparse
import hashlib
import json
from pathlib import Path
import sys
ROOT=Path(__file__).resolve().parents[3]
sys.path.insert(0,str(ROOT))
from scripts.dof.native_candidates import replacement_source, PLUGIN_PIN, plugin_candidate
from tests.integration.dof.native_rename_oracle import Machine
from unicorn import UC_HOOK_CODE, UC_HOOK_INTR
from unicorn.x86_const import UC_X86_REG_ESP, UC_X86_REG_EAX, UC_X86_REG_EIP, UC_X86_REG_EBX


def execute(raw, operation, base=0x10000000):
    m=Machine(raw,base);u=m.u;stack=0x700f0000;heap=0x71010000
    u.reg_write(UC_X86_REG_ESP,stack)
    for i,v in enumerate([0x710ff000,0x71001000,0x71003000,operation,1]):m.put(stack+i*4,v)
    # Explicit non-null connection precondition, never dereferenced by a real DB.
    for off in range(0,100,4):m.put(0x71001000+off,0x71002000+off*256)
    result={};count=0;imports=[]
    def code(u,address,size,unused):
        nonlocal heap,count
        count+=1;sp=u.reg_read(UC_X86_REG_ESP)
        if address==base+0x40ac9:
            result.update(query=m.text(m.word(sp+24)),formatPointer=m.word(sp+24)-base,
                          callerReturn=m.word(sp+16)-base,ebx=u.reg_read(UC_X86_REG_EBX)-base,
                          connection=m.word(sp+20),connectionField=(m.word(sp+20)-0x71002000)//256,
                          stack=sp,instructions=count)
            u.emu_stop();return
        if address in m.imports:
            name=m.imports[address];args=[m.word(sp+4+i*4) for i in range(3)]
            imports.append(name)
            if name=='malloc':
                if not 0<=args[0]<=65536 or heap+args[0]>0x710e0000:raise ValueError('Allocation bound')
                value=heap;heap+=(max(1,args[0])+15)&~15
            elif name=='free':value=0
            elif name in ('memcpy','memmove','memset'):
                if args[2]>65536:raise ValueError('Memory operation bound')
                data=bytes([args[1]&255])*args[2] if name=='memset' else bytes(u.mem_read(args[1],args[2]))
                u.mem_write(args[0],data);value=args[0]
            else:raise ValueError('Unmodeled import: '+name)
            u.reg_write(UC_X86_REG_EAX,value);u.reg_write(UC_X86_REG_EIP,m.word(sp));u.reg_write(UC_X86_REG_ESP,sp+4)
    def interrupt(u,number,unused):raise ValueError('Native OS interrupt refused')
    u.hook_add(UC_HOOK_CODE,code);u.hook_add(UC_HOOK_INTR,interrupt)
    u.emu_start(base+0x8b2d0,0x710ff000,count=200000,timeout=15000000)
    if not result:raise ValueError('SQL consumer not reached')
    result.update(operation=operation,base=base,imports=imports)
    return result


def verify(source):
    derived,plan=plugin_candidate(source);cases=[]
    for base in (0x10000000,0x20000000):
        for operation in (3,4,5):
            old=execute(source,operation,base);new=execute(derived,operation,base)
            assert new['query']==old['query'].replace('qk.randombox','dof_native.randombox')
            for key in ('callerReturn','ebx','connection','stack'):assert old[key]==new[key],key
            if operation==5:
                assert old['callerReturn']==0x7480b9
                assert old['connectionField']==new['connectionField']==0x40
                assert old['formatPointer']==0x2982a8
                assert new['formatPointer']==plan['randomBox']['pointer']
            else:assert old['formatPointer']==new['formatPointer']
            cases.append(dict(operation=operation,base=hex(base),old=old,new=new,passed=True))
            print('Verified full VM function to SQL consumer:',operation,hex(base),flush=True)
    return dict(schema=1,sourceSha256=hashlib.sha256(source).hexdigest(),derivedSha256=hashlib.sha256(derived).hexdigest(),
                oracleSha256=hashlib.sha256(Path(__file__).read_bytes()).hexdigest(),cases=cases,
                realDatabaseExecution=False,gameplayVerified=False,serverAccessed=False,releaseReady=False)

if __name__=='__main__':
    p=argparse.ArgumentParser(description=__doc__);p.add_argument('--source',type=Path,required=True);p.add_argument('--report',type=Path,required=True)
    a=p.parse_args();report=verify(replacement_source(a.source,PLUGIN_PIN))
    with a.report.open('x') as f:json.dump(report,f,indent=2);f.write('\n')
