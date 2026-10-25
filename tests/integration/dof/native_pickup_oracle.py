"""Check pinned pickup success/failure control flow with modeled callees.

Starts immediately after inventory insertion. This checks branch reachability
and arguments only; it is not a real inventory insertion or player test.
"""
import sys,json,hashlib,argparse,struct
from pathlib import Path
ROOT=Path(__file__).resolve().parents[3];sys.path.insert(0,str(ROOT))
from scripts.dof.native_candidates import Elf
from unicorn import Uc,UC_ARCH_X86,UC_MODE_32,UC_HOOK_CODE
from unicorn.x86_const import UC_X86_REG_EBP,UC_X86_REG_ESP,UC_X86_REG_EAX,UC_X86_REG_EIP

def verify(path):
    raw=path.read_bytes();pin=json.loads((ROOT/'metadata/dof/source-audit/server-probe-inputs.lock.json').read_text())['files']['df_game_r']['sha256']
    assert hashlib.sha256(raw).hexdigest()==pin
    cases=[];elf=Elf(raw)
    for inserted,pickup,expected in [(-1,0,False),(0,1,False),(0,0,True)]:
        u=Uc(UC_ARCH_X86,UC_MODE_32);frame=0x700e8000;stack=0x700e7000
        u.mem_map(0x85a6000,4096);at=elf.offset(0x85a6000,4096);u.mem_write(0x85a6000,raw[at:at+4096])
        u.mem_map(0x70000000,0x100000);u.mem_map(0x71000000,0x100000)
        def put(address,value):u.mem_write(address,struct.pack('<I',value&0xffffffff))
        def word(address):return struct.unpack('<I',u.mem_read(address,4))[0]
        u.reg_write(UC_X86_REG_EBP,frame);u.reg_write(UC_X86_REG_ESP,stack);u.reg_write(UC_X86_REG_EAX,inserted&0xffffffff)
        for off,value in [(8,0x71001000),(12,0x71002000),(16,1),(-0xe8,0x71002000),(-0x2aa,12345),(-0x2a5,1)]:put(frame+off,value)
        reached=[];observed=[]
        modeled={0x80cb8fc,0x80cb920,0x80cb958,0x86485ba,0x860d5ef,0x860d682,0x830daee,0x854f718,0x854f788}
        for page in {address&~4095 for address in modeled}:u.mem_map(page,4096)
        def code(u,address,size,unused):
            if address in (0x85a63d9,0x85a6228):
                reached.append(address)
                if address==0x85a6228:
                    observed.extend(word(u.reg_read(UC_X86_REG_ESP)+i*4) for i in range(4))
                u.emu_stop();return
            if address in modeled:
                sp=u.reg_read(UC_X86_REG_ESP);u.reg_write(UC_X86_REG_EAX,pickup if address==0x830daee else 0)
                u.reg_write(UC_X86_REG_EIP,word(sp));u.reg_write(UC_X86_REG_ESP,sp+4)
        u.hook_add(UC_HOOK_CODE,code);u.emu_start(0x85a6046,0,count=1000,timeout=1000000)
        assert reached==([0x85a6228] if expected else [0x85a63d9]),reached
        if expected:assert observed==[0x71001000,0x71002000,12345,1]
        cases.append(dict(inventoryReturn=inserted,pickupReturn=pickup,completionCallbackReached=expected,arguments=observed,passed=True))
    return dict(schema=1,gameSha256=pin,cases=cases,calleesModeled=True,actualPickupVerified=False,serverAccessed=False)
if __name__=='__main__':
    p=argparse.ArgumentParser();p.add_argument('--game',type=Path,required=True);p.add_argument('--report',type=Path,required=True);args=p.parse_args();result=verify(args.game)
    with args.report.open('x') as f:json.dump(result,f,indent=2);f.write('\n')
    print('Pickup branches: insert failure, pickup failure and success arguments passed')
