"""Verify test-only native observers and pickup scheduling without running a sample.

Generate --layout with Node and equipment_probe_layout.cjs, then run this oracle
with the local Unicorn dependency. No game or plugin code is executed natively.
"""
import argparse,hashlib,json
from pathlib import Path
from unicorn import Uc,UC_ARCH_X86,UC_MODE_32,UC_HOOK_CODE,UC_HOOK_INTR
from unicorn.x86_const import *
parser=argparse.ArgumentParser(description=__doc__)
parser.add_argument('--layout',type=Path,required=True)
parser.add_argument('--report',type=Path,required=True)
args=parser.parse_args()
j=json.loads(args.layout.read_text())
assert j['sourceSha256']==hashlib.sha256(Path(__file__).with_name('server_equipment_probe.js').read_bytes()).hexdigest()
rows=[]
for stub in j['stubs']:
 gate=stub['at']=='0x080da3a7'
 cases=[(0,123,0x086d1000),(1,999,0x086d1000),(1,123,0xf0001234),(1,123,0x07000000),(1,123,0x086d1000)] if gate else [(1,123,0x086d1000)]
 for armed,tid,ret in cases:
  u=Uc(UC_ARCH_X86,UC_MODE_32)
  for at,size in [(0x30000000,0x1000),(0x31000000,0x1000),(0x32000000,0x1000),(0x085b9000,0x1000),(0x70000000,0x2000)]:u.mem_map(at,size)
  code=bytes(stub['body']);u.mem_write(0x30000000,code);u.mem_write(0x31000000,armed.to_bytes(4,'little'))
  esp=0x70000ffc;u.mem_write(esp,ret.to_bytes(4,'little')+bytes.fromhex('112233445566778899aabbcc'))
  registers={UC_X86_REG_EAX:0x12345678,UC_X86_REG_EBX:0x13246578,UC_X86_REG_ECX:0x23456789,UC_X86_REG_EDX:0x3456789a,UC_X86_REG_ESI:0x456789ab,UC_X86_REG_EDI:0x56789abc,UC_X86_REG_EBP:0x70001abc,UC_X86_REG_ESP:esp,UC_X86_REG_EFLAGS:0x246}
  for reg,value in registers.items():u.reg_write(reg,value)
  calls=[]
  def read(n):return int.from_bytes(u.mem_read(n,4),'little')
  def interrupt(uc,n,user):
   assert n==0x80 and uc.reg_read(UC_X86_REG_EAX)==224
   uc.reg_write(UC_X86_REG_EAX,tid)
  def observe(uc,at,size,user):
   if at not in (0x32000000,0x085b949c):return
   sp=uc.reg_read(UC_X86_REG_ESP);assert sp%16==12
   if at==0x085b949c:assert [read(sp+4*i) for i in range(1,5)]==[0,0x33000000,10114,1]
   elif not gate:assert read(sp+4)==esp
   calls.append(at)
   uc.reg_write(UC_X86_REG_EIP,read(sp));uc.reg_write(UC_X86_REG_ESP,sp+4)
   for reg in (UC_X86_REG_EAX,UC_X86_REG_ECX,UC_X86_REG_EDX):uc.reg_write(reg,0xdeadbeef)
  u.hook_add(UC_HOOK_INTR,interrupt);u.hook_add(UC_HOOK_CODE,observe)
  u.emu_start(0x30000000,0x30000000+len(code),count=300)
  for reg,value in registers.items():assert u.reg_read(reg)==value,(stub['at'],reg,hex(u.reg_read(reg)),hex(value))
  expected=([0x085b949c,0x32000000] if armed and tid==123 and 0x08000000<=ret<0x08b00000 else []) if gate else [0x32000000]
  assert calls==expected
  assert read(0x31000000)==(0 if gate and calls else armed)
  rows.append(dict(entry=stub['at'],armed=armed,tid=tid,returnAddress=hex(ret),calls=[hex(x) for x in calls],passed=True))
args.report.write_text(json.dumps(dict(sourceSha256=j['sourceSha256'],cases=rows,registersAndFlagsPreserved=True,stackAligned=True,serverAccessed=False,nativeExecution=False,oracleSha256=hashlib.sha256(Path(__file__).read_bytes()).hexdigest()),indent=2)+'\n')
print(len(rows),'native probe stub ABI cases passed')
