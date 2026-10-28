// Real isolated instance only. Two configuration columns are applied then restored.
// Checkpoints/credentials stay on the server; native consumption remains a separate gate.
const assert=require('node:assert/strict'),fs=require('node:fs/promises'),path=require('node:path'),{parseArgs}=require('node:util');
const {chromium}=require(process.env.PLAYWRIGHT_MODULE||'playwright');
const {values:args}=parseArgs({options:{origin:{type:'string'},credentials:{type:'string'},output:{type:'string'}}});
const origin=new URL(args.origin).origin,root='/api/v1/admin/dof/client/releases';
if(!['127.0.0.1','localhost'].includes(new URL(origin).hostname))throw new Error('Server loopback only');
const output=path.resolve(args.output),bundleId='1c9d7de5-a1de-4225-a599-b286560a5cd9',checks=[],errors=[];
const state={phase:'INITIAL',nativeConsumerAcceptance:false};let loseApply=true,loseRestore=true,loseCancel=true;
async function phase(value){state.phase=value;await fs.writeFile(path.join(output,'state.tmp'),JSON.stringify(state,null,2),{mode:0o600});await fs.rename(path.join(output,'state.tmp'),path.join(output,'operation-state.json'));}
(async()=>{
 await fs.mkdir(output,{recursive:true,mode:0o700});try{await fs.stat(path.join(output,'operation-state.json'));throw new Error('Existing checkpoint: reconcile original IDs, never rerun');}catch(e){if(e.code!=='ENOENT')throw e;}
 assert.equal((await fs.stat(args.credentials)).mode&0o077,0);const credentials=JSON.parse(await fs.readFile(args.credentials,'utf8'));
 const browser=await chromium.launch({headless:true});
 try{
  const context=await browser.newContext({viewport:{width:1700,height:1300}}),page=await context.newPage();page.on('pageerror',e=>errors.push(e.message));
  await page.route('**/*',async route=>{
   const request=route.request(),u=new URL(request.url());if(u.origin!==origin)return route.abort();
   if(request.method()==='POST'&&u.pathname===root&&loseApply){loseApply=false;state.applyRequest=request.postDataJSON();await phase('APPLY_SENT');const response=await route.fetch({maxRetries:0,timeout:1800000});assert.equal(response.status(),200);assert.equal((await response.json()).state,'PERSISTED');await phase('APPLY_PERSISTED_REPLY_WITHHELD');return route.abort('connectionreset');}
   if(request.method()==='POST'&&u.pathname.endsWith('/restore')&&u.pathname.startsWith(root+'/')&&loseRestore){loseRestore=false;state.restoreRequest=request.postDataJSON();await phase('RESTORE_SENT');const response=await route.fetch({maxRetries:0,timeout:1800000});assert.equal(response.status(),200);assert.equal((await response.json()).state,'PERSISTED');await phase('RESTORE_PERSISTED_REPLY_WITHHELD');return route.abort('connectionreset');}
   if(request.method()==='POST'&&u.pathname.endsWith('/cancellations')&&u.pathname.startsWith(root+'/')&&loseCancel){loseCancel=false;state.cancelRequest=request.postDataJSON();const response=await route.fetch({maxRetries:0,timeout:30000});assert.equal(response.status(),200);assert.equal((await response.json()).state,'PERSISTED');await phase('CANCEL_PERSISTED_REPLY_WITHHELD');return route.abort('connectionreset');}
   return route.continue();
  });
  await page.goto(origin+'/login?redirect=/admin/dof');await page.getByPlaceholder('请输入用户名').fill(credentials.account);await page.getByPlaceholder('请输入密码').fill(credentials.password);await page.getByRole('button',{name:'登录',exact:true}).click();await page.waitForURL(u=>u.pathname==='/admin/dof');
  const token=await page.evaluate(()=>localStorage.getItem('token'));assert.ok(token);
  const api=async(method,url,data,status=200)=>{const r=await context.request.fetch(origin+url,{method,data,headers:{Authorization:'Bearer '+token},timeout:1800000});assert.equal(r.status(),status,method+' '+url);return r.json();};
  const catalog=await api('GET','/api/v1/admin/dof');assert.equal(catalog.instanceId,'dof-main');assert.equal(catalog.completeReplacement,false);
  assert.equal((await api('GET','/api/v1/recharge/6')).databaseUuid,'899d147e-d0ef-11f1-9ff4-525400542f67');
  const status=await api('GET',root+'/status');assert.equal(status.protocol,'script-release-preview-v1');assert.equal(status.manualModeCompatible,true);state.beforeRevision=status.revision;state.beforeCopiesEqual=status.parameterCopiesEqual;
  const anonymous=await context.request.get(origin+root);assert.ok([401,403].includes(anonymous.status()));
  await api('POST',root,{requestId:'00000000-0000-4000-8000-000000000090',revision:status.revision,bundleId},400);
  checks.push('bound instance, protocol, manual mode and anonymous/old-version refusal');await phase('BOUND');
  async function open(){await page.getByText('客户端配置与背景',{exact:true}).click();await page.getByRole('button',{name:'刷新配置与历史',exact:true}).waitFor();}
  async function select(){await page.locator('tr').filter({hasText:bundleId}).filter({has:page.getByRole('button',{name:'选择并配置隔离参数',exact:true})}).getByRole('button',{name:'选择并配置隔离参数',exact:true}).click();}
  async function preview(){await select();const response=page.waitForResponse(r=>r.url().endsWith(root+'/preview')&&r.request().method()==='POST',{timeout:1800000});await page.getByRole('button',{name:'核对配套包并预览应用',exact:true}).click();const r=await response;assert.equal(r.status(),200);const row=await r.json();assert.equal(row.state,'PREVIEWED');await page.getByRole('button',{name:'应用此配套包参数',exact:true}).waitFor();return row;}
  await open();const first=await preview();state.cancelledRequest=first.request;assert.ok(state.cancelledRequest);await phase('CANCELLABLE_PREVIEW_RECORDED');
  await page.reload();await open();await page.getByRole('button',{name:'取消本次预览',exact:true}).waitFor();assert.equal((await api('GET',root+'/'+first.requestId)).state,'PREVIEWED');
  await page.getByRole('button',{name:'取消本次预览',exact:true}).click();await page.locator('.arco-alert').filter({hasText:/Network Error|连接失败/}).first().waitFor();await page.getByRole('button',{name:'刷新配置与历史',exact:true}).click();
  assert.equal((await api('GET',root+'/'+first.requestId)).state,'NOT_APPLIED');assert.equal((await api('POST',root,state.cancelledRequest)).state,'NOT_APPLIED');assert.equal((await api('GET',root+'/status')).revision,status.revision);
  checks.push('durable preview reload, actual cancellation reply withheld, original-ID lookup and late apply fence');await phase('CANCELLED_AND_FENCED');
  const applied=await preview();state.applyRequest=applied.request;assert.ok(state.applyRequest);await phase('APPLY_PREVIEW_RECORDED');
  await page.getByRole('button',{name:'应用此配套包参数',exact:true}).click();await page.locator('.arco-alert').filter({hasText:/Network Error|连接失败/}).first().waitFor();
  assert.equal((await api('GET',root+'/'+applied.requestId)).state,'PERSISTED');assert.equal((await api('GET',root+'/status')).parameterCopiesEqual,true);
  await page.reload();await open();await page.waitForFunction(()=>sessionStorage.getItem('script.release.pending.v1')===null);assert.equal((await api('POST',root,state.applyRequest)).state,'PERSISTED');
  checks.push('real paired parameter application, reply withheld after persistence, reload and exact-ID replay');
  const restoreResponse=page.waitForResponse(r=>r.url().endsWith('/restore/preview')&&r.request().method()==='POST');await page.locator('tr').filter({hasText:applied.requestId}).getByRole('button',{name:'准备定向恢复',exact:true}).click();const restore=await (await restoreResponse).json();assert.equal(restore.state,'PREVIEWED');state.restoreRequest=restore.request;await phase('RESTORE_PREVIEW_RECORDED');
  await page.reload();await open();await page.getByRole('button',{name:'执行定向恢复',exact:true}).click();await page.locator('.arco-alert').filter({hasText:/Network Error|连接失败/}).first().waitFor();
  assert.equal((await api('GET',root+'/'+restore.requestId)).state,'PERSISTED');assert.equal((await api('GET',root+'/'+applied.requestId)).state,'RESTORED');
  await page.reload();await open();await page.waitForFunction(()=>sessionStorage.getItem('script.release.pending.v1')===null);const after=await api('GET',root+'/status');assert.equal(after.revision,status.revision);assert.equal(after.parameterCopiesEqual,status.parameterCopiesEqual);
  assert.equal((await api('POST',root+'/'+applied.requestId+'/restore',state.restoreRequest)).state,'PERSISTED');
  checks.push('separate restore preview, reload, actual restoration reply loss, replay and exact original six-field revision');
  assert.equal(Number((await api('GET','/api/v1/recharge/6')).cera),37);assert.equal(Number((await api('GET','/api/v1/recharge/points/6')).amount),0);assert.deepEqual(errors,[]);
  await phase('COMPLETED');const result={passed:true,mockedApis:false,cancelledRequestId:first.requestId,cancelRequestId:state.cancelRequest.requestId,applyRequestId:applied.requestId,restoreRequestId:restore.requestId,beforeRevision:status.revision,restoredRevision:after.revision,nativeConsumerAcceptance:false,originalWindowsToolStarted:false,completeReplacement:false,checks,pageErrors:errors.length,responseLoss:'browser withheld replies after actual persistence; not a physical network outage'};await fs.writeFile(path.join(output,'result.json'),JSON.stringify(result,null,2),{mode:0o600});console.log(JSON.stringify(result));
 }catch(e){state.failureType=e.name;await phase('FAILED');throw e;}finally{await browser.close();}
})().catch(e=>{console.error(e);process.exitCode=1});
