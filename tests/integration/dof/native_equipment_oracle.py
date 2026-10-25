"""Execute the equipment ABI bridge at two bases; no native OS execution."""
import sys,json,hashlib,struct,argparse
from pathlib import Path
ROOT=Path(__file__).resolve().parents[3];sys.path.insert(0,str(ROOT))
from scripts.dof.native_candidates import plugin_candidate,replacement_source,PLUGIN_PIN,Elf
from tests.integration.dof.native_rename_oracle import Machine
from unicorn import UC_HOOK_CODE,UcError
from unicorn.x86_const import UC_X86_REG_ESP,UC_X86_REG_EAX,UC_X86_REG_EBX,UC_X86_REG_EIP

def verify(source):
    data,recipe=plugin_candidate(source);a=recipe['equipment'];cases=[]
    elf=Elf(data);symbol=struct.unpack_from('<IIIBBH',data,elf.offset(elf.tags[6]+839*16))
    assert symbol[1]==a['entry'] and symbol[3]==18 and symbol[5]==a['sectionIndex']
    for base in (0x10000000,0x30000000):
        for ready in (False,True):
            m=Machine(data,base);stack=0x700f0000;callback=0x710ff000
            args=[0x710fe000,0x71001000,0x71002000,12345,1]
            for i,v in enumerate(args):m.put(stack+4*i,v)
            m.u.reg_write(UC_X86_REG_ESP,stack);m.u.reg_write(UC_X86_REG_EAX,0x12345678);m.u.reg_write(UC_X86_REG_EBX,0x87654321)
            if ready:m.put(base+a['dispatchSlot'],callback)
            trapped=False
            try:m.u.emu_start(base+a['entry'],callback,count=50)
            except UcError:trapped=True
            if ready:
                assert not trapped and m.u.reg_read(UC_X86_REG_EIP)==callback
                assert m.u.reg_read(UC_X86_REG_ESP)==stack
                assert [m.word(stack+i*4) for i in range(5)]==args
                assert m.u.reg_read(UC_X86_REG_EAX)==0x12345678 and m.u.reg_read(UC_X86_REG_EBX)==0x87654321
            else:assert trapped and m.u.reg_read(UC_X86_REG_EIP)==base+a['entry']+21
            cases.append({'base':hex(base),'registered':ready,'passed':True})
    return {'schema':1,'sourceSha256':PLUGIN_PIN,'derivedSha256':hashlib.sha256(data).hexdigest(),'cases':cases,'nativeExecution':False,'serverAccessed':False}
if __name__=='__main__':
    p=argparse.ArgumentParser();p.add_argument('--source',type=Path,required=True);p.add_argument('--report',type=Path,required=True);args=p.parse_args()
    result=verify(replacement_source(args.source,PLUGIN_PIN))
    with args.report.open('x') as f:json.dump(result,f,indent=2);f.write('\n')
    print('Equipment ABI: 4 relocation/argument/unregistered-failure cases passed')
