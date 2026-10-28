// Pinned isolated instance only. Builds private candidates; never applies server parameters.
// Checkpoints and downloads stay on the server. Existing output means reconcile, not rerun.
const assert=require('node:assert/strict');
const fs=require('node:fs/promises'),path=require('node:path'),{createHash}=require('node:crypto'),{parseArgs}=require('node:util');
const {chromium}=require(process.env.PLAYWRIGHT_MODULE||'playwright');
const {values:args}=parseArgs({options:{origin:{type:'string'},credentials:{type:'string'},output:{type:'string'}}});
const origin=new URL(args.origin).origin;
if(!['127.0.0.1','localhost'].includes(new URL(origin).hostname))throw new Error('Server loopback only');
const output=path.resolve(args.output),base='/api/v1/admin/dof',checks=[],errors=[];
const state={phase:'INITIAL',serverParametersApplied:false};
async function phase(value){state.phase=value;await fs.writeFile(path.join(output,'state.tmp'),JSON.stringify(state,null,2),{mode:0o600});await fs.rename(path.join(output,'state.tmp'),path.join(output,'operation-state.json'));}
async function streamDownload(url,token,name,expected){
 const response=await fetch(origin+url,{headers:{Authorization:'Bearer '+token},redirect:'error'});
 assert.equal(response.status,200);const hash=createHash('sha256');let bytes=0;
 const file=await fs.open(path.join(output,name),'wx',0o600);
 try{for await(const chunk of response.body){bytes+=chunk.length;assert.ok(bytes<=expected.bytes);hash.update(chunk);await file.writeFile(chunk);}await file.sync();}finally{await file.close();}
 assert.equal(bytes,expected.bytes);assert.equal(hash.digest('hex'),expected.sha256);
}
(async()=>{
 await fs.mkdir(output,{recursive:true,mode:0o700});
 try{await fs.stat(path.join(output,'operation-state.json'));throw new Error('Existing checkpoint: reconcile original task IDs');}catch(e){if(e.code!=='ENOENT')throw e;}
 assert.equal((await fs.stat(args.credentials)).mode&0o077,0);
 const credentials=JSON.parse(await fs.readFile(args.credentials,'utf8'));
 const browser=await chromium.launch({headless:true});let loseGenerateReply=true;
 try{
  const context=await browser.newContext({viewport:{width:1700,height:1300}}),page=await context.newPage();
  page.on('pageerror',e=>errors.push(e.message));
  await page.route('**/*',async route=>{
   const request=route.request(),url=new URL(request.url());if(url.origin!==origin)return route.abort();
   if(loseGenerateReply&&url.pathname===base+'/script/tasks'&&request.method()==='POST'){
    loseGenerateReply=false;state.scriptRequest=request.postDataJSON();await phase('SCRIPT_REQUEST_SENT');
    const response=await route.fetch({maxRetries:0,timeout:30000});assert.equal(response.status(),200);
    const job=await response.json();assert.equal(job.requestId,state.scriptRequest.requestId);
    await phase('SCRIPT_ACCEPTED_REPLY_WITHHELD');return route.abort('connectionreset');
   }return route.continue();
  });
  await page.goto(origin+'/login?redirect=/admin/dof');
  await page.getByPlaceholder('请输入用户名').fill(credentials.account);await page.getByPlaceholder('请输入密码').fill(credentials.password);
  await page.getByRole('button',{name:'登录',exact:true}).click();await page.waitForURL(u=>u.pathname==='/admin/dof');
  const token=await page.evaluate(()=>localStorage.getItem('token'));assert.ok(token);
  const api=async(method,url,data,status=200)=>{const r=await context.request.fetch(origin+url,{method,data,headers:{Authorization:'Bearer '+token},timeout:1800000});assert.equal(r.status(),status,method+' '+url);return r.json();};
  const catalog=await api('GET',base);assert.equal(catalog.instanceId,'dof-main');assert.equal(catalog.completeReplacement,false);
  const wallet=await api('GET','/api/v1/recharge/6');assert.equal(wallet.databaseUuid,'899d147e-d0ef-11f1-9ff4-525400542f67');assert.equal(Number(wallet.cera),37);
  assert.equal(Number((await api('GET','/api/v1/recharge/points/6')).amount),0);
  const source=await api('GET',base+'/script');assert.equal(source.enabled,true);assert.equal(source.pairedSourceAvailable,true);assert.equal(source.mode,'uncompressed');assert.equal(source.nativeClientAcceptance,false);
  const anonymous=await context.request.get(origin+base+'/script');assert.ok([401,403].includes(anonymous.status()));
  checks.push('bound isolated database, native source and anonymous refusal');await phase('BOUND');
  await page.getByText('客户端配置与背景',{exact:true}).click();
  const response=page.waitForResponse(r=>r.url().endsWith(base+'/script/uploads/paired')&&r.request().method()==='POST',{timeout:1800000});
  await page.getByRole('button',{name:'使用当前服务端 PVF',exact:true}).click();
  const uploadResponse=await response;assert.equal(uploadResponse.status(),200);const upload=await uploadResponse.json();
  assert.equal(upload.state,'READY');assert.equal(upload.revision,source.revision);state.uploadId=upload.uploadId;state.sourceSha256=upload.sha256;await phase('SOURCE_IMPORTED');
  const uploadRow=page.locator('tr').filter({hasText:upload.uploadId});await uploadRow.getByRole('button',{name:'生成加密 PVF',exact:true}).click();
  await page.getByText('待核对生成编号：',{exact:false}).waitFor({timeout:30000});
  await page.getByRole('button',{name:'刷新来源和任务',exact:true}).click();
  assert.ok(state.scriptRequest);const id=state.scriptRequest.requestId;
  assert.equal((await api('GET',base+'/script/tasks')).filter(x=>x.requestId===id).length,1);
  const deadline=Date.now()+15*60*1000;let job;
  do{job=await api('GET',base+'/script/tasks/'+id);if(!['QUEUED','RUNNING'].includes(job.state))break;await new Promise(r=>setTimeout(r,3000));}while(Date.now()<deadline);
  assert.equal(job.state,'READY_FOR_TEST');assert.equal(job.roundTripVerified,true);assert.equal(job.compressed,false);assert.equal(job.nativeClientAcceptance,false);assert.equal(job.applicationState,'NOT_APPLIED');assert.equal(job.sourceSha256,upload.sha256);
  assert.equal((await api('POST',base+'/script/tasks',state.scriptRequest)).sha256,job.sha256);
  await page.getByRole('button',{name:'刷新来源和任务',exact:true}).click();
  const row=page.locator('tr').filter({hasText:id});await row.getByText('已还原核对原 PVF；组件读取及游戏效果待验',{exact:true}).waitFor();
  checks.push('real browser generation reply withheld; original ID reconciled and replayed without a second build');
  state.scriptSha256=job.sha256;state.scriptBytes=job.bytes;await phase('SCRIPT_READY');
  await streamDownload(base+'/script/tasks/'+id+'/download',token,'Script.pvf',job);
  checks.push('complete candidate download streamed and matched task length/SHA-256');await phase('SCRIPT_DOWNLOADED');
  await row.getByRole('button',{name:'选择此批次生成登录器配套包',exact:true}).click();
  const bundleResponse=page.waitForResponse(r=>r.url().endsWith(base+'/client/tasks')&&r.request().method()==='POST',{timeout:30000});
  await page.getByRole('button',{name:'生成 dof-easy 配套测试包',exact:true}).click();
  const accepted=await bundleResponse;assert.equal(accepted.status(),200);const submitted=await accepted.json();state.bundleId=submitted.requestId;await phase('BUNDLE_ACCEPTED');
  let bundle;const bundleDeadline=Date.now()+15*60*1000;
  do{bundle=await api('GET',base+'/client/tasks/'+state.bundleId);if(!['QUEUED','RUNNING'].includes(bundle.state))break;await new Promise(r=>setTimeout(r,3000));}while(Date.now()<bundleDeadline);
  assert.equal(bundle.state,'READY_FOR_TEST');assert.equal(bundle.scriptTaskId,id);assert.equal(bundle.scriptSha256,job.sha256);assert.equal(bundle.scriptBytes,job.bytes);assert.equal(bundle.nativeClientAcceptance,false);assert.equal(bundle.packageKind,'isolated-dof-pvf-bundle');
  state.bundleSha256=bundle.sha256;state.bundleBytes=bundle.bytes;await phase('BUNDLE_READY');
  await streamDownload(base+'/client/tasks/'+state.bundleId+'/download',token,'candidate.zip',bundle);
  checks.push('same-batch native candidate package built and full ZIP download SHA-256 verified');
  assert.equal(Number((await api('GET','/api/v1/recharge/6')).cera),37);assert.equal(Number((await api('GET','/api/v1/recharge/points/6')).amount),0);assert.deepEqual(errors,[]);
  checks.push('no server apply, rewards or wallet writes; no browser page errors');
  await phase('COMPLETED');const result={passed:true,mockedApis:false,serverParametersApplied:false,nativeClientAcceptance:false,originalWindowsToolStarted:false,originalToolEquivalenceVerified:false,uploadId:upload.uploadId,scriptTaskId:id,scriptSha256:job.sha256,scriptSourceSha256:upload.sha256,scriptBytes:job.bytes,roundTripVerified:true,bundleTaskId:bundle.requestId,bundleSha256:bundle.sha256,bundleBytes:bundle.bytes,checks,pageErrors:errors.length,responseLoss:'actual generation accepted; reply withheld by browser route, not a physical network fault'};
  await fs.writeFile(path.join(output,'result.json'),JSON.stringify(result,null,2),{mode:0o600});console.log(JSON.stringify(result));
 }catch(e){state.failureType=e.name;await phase('FAILED');throw e;}finally{await browser.close();}
})().catch(e=>{console.error(e);process.exitCode=1;});
