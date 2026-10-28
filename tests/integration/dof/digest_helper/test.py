from pathlib import Path
import hashlib,json,subprocess
flags=['gcc','-m32','-shared','-fPIC','-fvisibility=hidden','-fno-ident','-Wl,--build-id=none','-Wall','-Wextra','-Werror','-O2']
subprocess.run(flags+['/inputs/admin-runtime-digest.c','-o','/output/libdof-admin-runtime.so'],check=True)
subprocess.run(['gcc','-m32','-O2','/inputs/probe.c','-ldl','-o','/output/digest-probe'],check=True)
def run(p,n,mode=None):
 args=['/output/digest-probe','/output/libdof-admin-runtime.so',str(p),str(n)]+([mode] if mode else [])
 return subprocess.check_output(args,text=True).strip()
lengths=[0,1,55,56,63,64,65,127,128,129,32767,32768,32769,1048576,9193440]
for length in lengths:
 raw=(bytes(range(256))*((length+255)//256))[:length]
 p=Path('/output/vector');p.write_bytes(raw)
 assert run(p,length)==hashlib.sha256(raw).hexdigest(),length
p=Path('/output/vector');p.write_bytes(b'abc')
for n,mode in [(0,None),(4,None),(3,'advance'),(3,'null'),(4294967295,None)]:assert run(p,n,mode)=='REJECTED'
assert run(Path('/output'),0)=='REJECTED'
r=dict(passed=True,vectors=lengths,negativeCases=6,sourceSHA256=hashlib.sha256(Path('/inputs/admin-runtime-digest.c').read_bytes()).hexdigest(),helperSHA256=hashlib.sha256(Path('/output/libdof-admin-runtime.so').read_bytes()).hexdigest(),compiler=subprocess.check_output(['gcc','--version'],text=True).splitlines()[0],serverAccessed=False)
Path('/output/digest-validation.json').write_text(json.dumps(r,indent=2)+'\n');print('Actual i386 shared helper: 15 vectors and 6 refusal cases passed')
