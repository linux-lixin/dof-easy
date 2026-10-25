"""Execute the native control-frame buffers and range operands in Unicorn.

This verifies native instructions, buffer bounds, lengths and ABI state. It is
not a live socket/control-command test and must never be reported as one.
"""
import argparse
import hashlib
import json
from pathlib import Path
import struct
import sys
ROOT=Path(__file__).resolve().parents[3]
sys.path[:0]=[str(ROOT),str(Path(__file__).parent)]
from scripts.dof.native_candidates import PLUGIN_PIN,replacement_source,plugin_candidate
from native_rename_oracle import Machine
from unicorn.x86_const import *


def verify(source,candidate):
    original=replacement_source(source,PLUGIN_PIN)
    derived,recipe=plugin_candidate(original)
    if Path(candidate).read_bytes()!=derived:raise ValueError('Derived plugin recipe mismatch')
    results=[]
    for label,raw,opening,closing in [('original',original,b'<qk>',b'</qk>'),('derived',derived,b'<dof>',b'</dof>')]:
        for base in [0x10000000,0x20000000]:
            m=Machine(raw,base);u=m.u;frame=0x700e0000;stack=0x700d0000
            u.mem_write(frame-0x2200,b'\xa5'*0x2200)
            u.reg_write(UC_X86_REG_EBP,frame);u.reg_write(UC_X86_REG_ESP,stack)
            u.reg_write(UC_X86_REG_ESI,m.base+m.elf.tags[3])
            u.reg_write(UC_X86_REG_EAX,0x71234567);u.reg_write(UC_X86_REG_EFLAGS,0x246)
            u.emu_start(base+0xe1292,base+0xe12b2,count=20)
            assert u.reg_read(UC_X86_REG_EDX)==len(opening)
            assert u.reg_read(UC_X86_REG_ESP)==stack and u.reg_read(UC_X86_REG_EAX)==0x71234567
            assert u.reg_read(UC_X86_REG_EFLAGS)==0x246
            assert m.word(frame-0x205c)==len(opening)
            assert bytes(u.mem_read(frame-0x2058,len(opening)+1))==opening+b'\0'
            assert bytes(u.mem_read(frame-0x2058+8,8))==b'\xa5'*8
            u.emu_start(base+0xe12d2,base+0xe12f4,count=20)
            assert m.word(frame-0x2044)==len(closing)
            assert bytes(u.mem_read(frame-0x2040,len(closing)+1))==closing+b'\0'
            assert bytes(u.mem_read(frame-0x2040+8,8))==b'\xa5'*8
            assert u.reg_read(UC_X86_REG_EFLAGS)==0x246
            u.emu_start(base+0xe11cb,base+0xe11d7,count=2)
            begin,end=u.reg_read(UC_X86_REG_EDX),u.reg_read(UC_X86_REG_ECX)
            assert end-begin==len(closing)
            assert bytes(u.mem_read(begin,len(closing)))==closing
            results.append(dict(input=label,base=hex(base),opening=opening.decode(),closing=closing.decode(),passed=True))
    return dict(schema=1,sourceSha256=PLUGIN_PIN,derivedSha256=hashlib.sha256(derived).hexdigest(),
                oracleSha256=hashlib.sha256(Path(__file__).read_bytes()).hexdigest(),cases=results,
                liveSocketProtocolVerified=False,serverAccessed=False,releaseReady=False)

if __name__=='__main__':
    p=argparse.ArgumentParser(description=__doc__)
    p.add_argument('--source',type=Path,required=True);p.add_argument('--plugin',type=Path,required=True);p.add_argument('--report',type=Path,required=True)
    a=p.parse_args();result=verify(a.source,a.plugin)
    with a.report.open('x') as f:json.dump(result,f,indent=2);f.write('\n')
    print('Verified four native frame-buffer cases; live protocol remains separate')
