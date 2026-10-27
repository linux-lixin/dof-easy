// Protocol tests with synthetic files. These never count as native/game evidence.
const {test}=require('node:test'),assert=require('node:assert/strict'),crypto=require('crypto');
const runtime=require('../../../services/game/runtime/ubuntu/gateway/admin_apply.js');
const clone=x=>x==null?x:JSON.parse(JSON.stringify(x));
function fixture(){
 let now=1700000000000,files={},calls=[],locked=false,fail=null;
 const boot='a'.repeat(32),channel='siroco15',binding={schema:1,instanceId:'dof-test',databaseUuid:'00000000-0000-0000-0000-000000000001',channels:[channel],pluginSHA256:runtime.pluginSHA256};
 const bindingBytes=Buffer.from(JSON.stringify(binding));binding.bindingRevision=runtime.sha256(bindingBytes);
 const config=Buffer.from('anton_name = 安徒恩\nanton_member_min = 1\n');
 const values={name:'安徒恩',member_min:'1',open_day:'1,2,3,4,5,6,7',open_time:'00:00:00-23:59:59'};
 const request={schema:1,instanceId:binding.instanceId,databaseUuid:binding.databaseUuid,bindingRevision:binding.bindingRevision,requestId:'refresh-test-0001',module:'anton',revision:runtime.sha256(config),submittedAt:now,expiresAt:now+120000,expected:clone(values),targets:[{channel,bootId:boot,pid:20,dispatcherThreadId:21}]};
 files['request-anton.json']=clone(request);
 const io={pid:20,threadId:()=>21,pluginSHA256:runtime.pluginSHA256,now:()=>now,binding:()=>bindingBytes,config:()=>config,
  json:(name)=>clone(files[name]),atomic:(name,value)=>{if(fail&&fail(name,value))throw Error('injected write failure');files[name]=clone(value);},
  lock:fn=>locked?false:(fn(),true),snapshot:()=>clone(values),reload:module=>calls.push(module)};
 const build=(id=boot)=>runtime.create(io,binding,channel,id);
 return {files,calls,binding,io,request,values,config,build,policy:build(),setTime:n=>now=n,setLock:v=>locked=v,setFailure:fn=>fail=fn,
  receipt:()=>files['receipt-'+channel+'-anton.json']};
}
test('hash exact native bytes including boundaries and NUL, not rendered strings',()=>{
 for(const n of [0,1,55,56,63,64,65,255,4096,65536,1000000]){const b=crypto.randomBytes(n);assert.equal(runtime.sha256(b),crypto.createHash('sha256').update(b).digest('hex'));}
});
test('one fixed native call, selected field acknowledgement and no gameplay claim',()=>{
 const f=fixture();f.policy.tick();assert.deepEqual(f.calls,['anton']);assert.equal(f.receipt().state,'APPLIED');
 assert.deepEqual(f.receipt().observed,f.values);assert.equal(f.receipt().gameplayVerified,undefined);
 f.policy.tick();f.build().tick();assert.equal(f.calls.length,1);
});
test('a durable RUNNING receipt must exist before the native function',()=>{
 const f=fixture();f.io.reload=()=>{assert.equal(f.receipt().state,'RUNNING');f.calls.push('anton');};f.policy.tick();assert.equal(f.calls.length,1);
});
test('failed write-before-call never invokes native and can retry when no marker exists',()=>{
 const f=fixture();f.setFailure((n,v)=>v.state==='RUNNING');f.policy.tick();assert.equal(f.calls.length,0);assert.equal(f.receipt(),undefined);
 f.setFailure(null);f.policy.tick();assert.equal(f.calls.length,1);
});
test('lost completion write leaves RUNNING and never repeats, including executor reconstruction',()=>{
 const f=fixture();f.setFailure((n,v)=>n.startsWith('receipt-')&&v.state!=='RUNNING');f.policy.tick();assert.equal(f.receipt().state,'RUNNING');assert.equal(f.calls.length,1);
 f.setFailure(null);f.build().tick();assert.equal(f.calls.length,1);assert.equal(f.receipt().state,'RUNNING');
});
test('native exception is UNKNOWN and never automatically retried',()=>{
 const f=fixture();f.io.reload=()=>{f.calls.push('anton');throw Error('native failure');};f.policy.tick();assert.equal(f.receipt().state,'UNKNOWN');f.policy.tick();assert.equal(f.calls.length,1);
});
test('wrong configuration revision is rejected before a native call',()=>{
 const f=fixture();f.files['request-anton.json'].revision='0'.repeat(64);f.policy.tick();assert.equal(f.receipt().reason,'STALE_CONFIG');assert.equal(f.calls.length,0);
});
test('revision change during native entry remains unknown',()=>{
 const f=fixture();f.io.reload=()=>{f.calls.push('anton');f.io.config=()=>Buffer.from('external writer');};f.policy.tick();assert.equal(f.receipt().state,'UNKNOWN');assert.equal(f.receipt().reason,'OBSERVED_MISMATCH');
});
test('native getter mismatch never produces APPLIED',()=>{
 const f=fixture();f.io.snapshot=()=>({...f.values,member_min:'9'});f.policy.tick();assert.equal(f.receipt().state,'UNKNOWN');assert.equal(f.calls.length,1);
});
test('expired task is rejected even with matching bytes and boot',()=>{
 const f=fixture();f.setTime(f.request.expiresAt+1);f.policy.tick();assert.equal(f.receipt().reason,'EXPIRED');assert.equal(f.calls.length,0);
});
test('new process boot never replays old task or overwrites its unresolved receipt',()=>{
 const f=fixture();f.files['receipt-siroco15-anton.json']={requestId:f.request.requestId,state:'RUNNING'};
 f.build('b'.repeat(32)).tick();assert.equal(f.calls.length,0);assert.equal(f.receipt().state,'RUNNING');assert.equal(f.files['status-siroco15.json'].bootId,'b'.repeat(32));
});
test('instance, database, roster, binding and arbitrary function inputs fail closed',()=>{
 for(const patch of [{instanceId:'other'},{databaseUuid:'other'},{bindingRevision:'0'.repeat(64)},{module:'plugin'},{command:'reboot'},{expected:{name:'x'}}]){
  const f=fixture();Object.assign(f.files['request-anton.json'],patch);f.policy.tick();assert.equal(f.calls.length,0);assert.equal(f.files['status-siroco15.json'].lastError,'REQUEST_OR_RECEIPT_UNAVAILABLE');
 }
 const f=fixture();f.files['request-anton.json'].targets[0].channel='siroco52';f.policy.tick();assert.equal(f.calls.length,0);
});
test('shared lock contention prevents both native work and misleading fresh heartbeat',()=>{
 const f=fixture();f.setLock(true);f.policy.tick();assert.equal(f.calls.length,0);assert.equal(f.files['status-siroco15.json'],undefined);
 f.setLock(false);f.policy.tick();assert.equal(f.calls.length,1);
});
test('binding changes stop the executor rather than adopting another identity',()=>{
 const f=fixture();f.io.binding=()=>Buffer.from('changed');assert.throws(()=>f.policy.tick(),/binding changed/);assert.equal(f.calls.length,0);
});
test('unsupported native module digest is never advertised as a worker',()=>{
 const f=fixture();f.io.pluginSHA256='0'.repeat(64);assert.throws(()=>f.build(),/Unsupported/);
});
test('native byte buffer bounds count UTF-8 bytes rather than JavaScript characters',()=>{
 const f=fixture();f.files['request-anton.json'].expected.name='安'.repeat(100);f.policy.tick();assert.equal(f.calls.length,0);assert.equal(f.receipt(),undefined);
});
test('only the dispatcher thread frozen by the preview can execute a task',()=>{
 const f=fixture();f.files['request-anton.json'].targets[0].dispatcherThreadId=22;f.policy.tick();assert.equal(f.calls.length,0);assert.equal(f.receipt(),undefined);
 assert.equal(f.files['status-siroco15.json'].dispatcherThreadId,21);
});
