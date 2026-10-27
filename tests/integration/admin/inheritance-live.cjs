// Real, pinned isolated level-86 admin. Test-owned rule only; native game effects stay unverified.
// Keep credentials/checkpoints on server. An existing output must be reconciled, never rerun.
const assert=require('node:assert/strict'),fs=require('node:fs/promises'),path=require('node:path');
const {randomUUID}=require('node:crypto'),{parseArgs}=require('node:util');
const {chromium}=require(process.env.PLAYWRIGHT_MODULE||'playwright');
const {values:args}=parseArgs({options:{origin:{type:'string'},credentials:{type:'string'},output:{type:'string'}}});
const origin=new URL(args.origin).origin,output=path.resolve(args.output),base='/api/v1/admin/activities/inheritance',code=2147483001;
assert.ok(['localhost','127.0.0.1'].includes(new URL(origin).hostname));
const state={phase:'INITIAL',testCode:code,gameplayVerified:false},checks=[],errors=[];let lose=null;
async function phase(value){state.phase=value;await fs.writeFile(path.join(output,'state.tmp'),JSON.stringify(state,null,2),{mode:0o600});await fs.rename(path.join(output,'state.tmp'),path.join(output,'operation-state.json'));}
(async()=>{
 await fs.mkdir(output,{recursive:false,mode:0o700});assert.equal((await fs.stat(args.credentials)).mode&0o077,0);
 const credentials=JSON.parse(await fs.readFile(args.credentials,'utf8')),browser=await chromium.launch({headless:true});
 try{
  const context=await browser.newContext({viewport:{width:1800,height:1400}}),page=await context.newPage();page.on('pageerror',e=>errors.push(e.message));
  await page.route('**/*',async route=>{
   const r=route.request(),u=new URL(r.url());if(u.origin!==origin)return route.abort();
   if(u.pathname===base+'/validate'&&r.method()==='POST'){state.lastPreview=r.postDataJSON();await phase('UI_PREVIEW_SENT');}
   if(lose&&((u.pathname===base&&r.method()==='PUT')||(/\/history\/[^/]+\/(restore|cancellations)$/.test(u.pathname)&&r.method()==='POST'))){
    const kind=lose;lose=null;const response=await route.fetch({maxRetries:0,timeout:30000});assert.equal(response.status(),200);const receipt=await response.json();assert.equal(receipt.state,'PERSISTED');state[kind+'Receipt']=receipt;await phase(kind.toUpperCase()+'_COMMITTED_REPLY_WITHHELD');return route.abort('connectionreset');
   }return route.continue();
  });
  await page.goto(origin+'/login?redirect=/admin/activities');await page.getByPlaceholder('请输入用户名').fill(credentials.account);await page.getByPlaceholder('请输入密码').fill(credentials.password);await page.getByRole('button',{name:'登录',exact:true}).click();await page.waitForURL(u=>u.pathname==='/admin/activities');
  const token=await page.evaluate(()=>localStorage.getItem('token'));assert.ok(token);
  const api=async(method,url,data,status=200)=>{const r=await context.request.fetch(origin+url,{method,data,headers:{Authorization:'Bearer '+token},timeout:30000});assert.equal(r.status(),status,method+' '+url);return r.json();};
  const rows=async()=>(await api('GET',base)).rows;
  const catalog=await api('GET','/api/v1/admin/dof');assert.equal(catalog.instanceId,'dof-main');assert.equal(catalog.completeReplacement,false);assert.equal(catalog.scope.baselineOperations,239);
  const wallet=await api('GET','/api/v1/recharge/6');assert.equal(wallet.databaseUuid,'899d147e-d0ef-11f1-9ff4-525400542f67');assert.equal(Number(wallet.cera),37);
  const sections=await api('GET','/api/v1/admin/activities'),section=sections.find(s=>s.key==='inheritance');assert.equal(section.writable,true);assert.equal(sections.find(s=>s.key==='cross-border').writable,false);assert.equal(sections.find(s=>s.key==='recovery').writable,false);
  assert.deepEqual(await rows(),[]);await phase('BOUND_EMPTY_RULE_TABLE');
  const select=async()=>{await page.locator('.arco-select').first().click();await page.locator('.arco-select-option').getByText(section.title,{exact:true}).click();};
  const modal=page.locator('.arco-modal').filter({has:page.getByRole('button',{name:'确认本次预览',exact:true})});
  const field=label=>modal.locator('.arco-form-item').filter({has:page.locator('.arco-form-item-label').getByText(label,{exact:true})}).locator('input');
  const preview=async()=>{const response=page.waitForResponse(r=>r.url().endsWith('/inheritance/validate')&&r.request().method()==='POST');await modal.getByRole('button',{name:'预览操作',exact:true}).click();assert.equal((await (await response).json()).state,'PREVIEWED');return structuredClone(state.lastPreview);};
  const reopen=async()=>{await page.reload();await page.getByRole('button',{name:'新增配置',exact:true}).waitFor();};
  await select();await page.getByRole('button',{name:'新增配置',exact:true}).click();
  assert.equal(await field('继承装备代码（0 为默认规则）').inputValue(),'0');state.defaultRequest=await preview();await phase('DEFAULT_PREVIEWED');
  await modal.getByRole('button',{name:'关闭并保留编号',exact:true}).click();await page.reload();await page.getByRole('button',{name:'恢复原操作表单',exact:true}).click();await modal.getByRole('button',{name:'重读原预览',exact:true}).waitFor();
  assert.equal((await api('GET',base+'/history/'+state.defaultRequest.requestId)).requestReplayAvailable,true);assert.deepEqual(await rows(),[]);
  await modal.getByRole('button',{name:'关闭并保留编号',exact:true}).click();await page.getByPlaceholder('填写取消未提交预览的原因').fill('S24 default rule preview only; no native default activation');
  const cp=page.waitForResponse(r=>r.url().endsWith('/cancellations/preview'));await page.getByRole('button',{name:'预览取消未提交操作',exact:true}).click();state.defaultCancel=await (await cp).json();await phase('DEFAULT_CANCELLATION_PREVIEWED');lose='defaultCancel';
  await page.getByRole('button',{name:'确认取消原预览',exact:true}).click();await page.locator('.arco-alert').getByText('Network Error',{exact:true}).waitFor();await page.getByRole('button',{name:'核对取消编号',exact:true}).click();await page.getByText(new RegExp('最近核对 '+state.defaultRequest.requestId)).waitFor();
  assert.equal((await api('PUT',base,state.defaultRequest)).state,'NOT_APPLIED');assert.deepEqual(await rows(),[]);checks.push('code-zero default preview survives reload; actual cancellation reply loss reconciles without creating the default rule');
  await page.getByRole('button',{name:'新增配置',exact:true}).click();await field('继承装备代码（0 为默认规则）').fill(String(code));await field('扣除道具代码').fill('3037/3340');await field('扣除道具数量').fill('10/20');await field('允许装备等级差').fill('5');
  state.create=await preview();assert.equal(state.create.values.code,code);await phase('TEST_RULE_CREATE_PREVIEWED');lose='create';await modal.getByRole('button',{name:'确认本次预览',exact:true}).click();await modal.locator('.arco-alert').getByText('Network Error',{exact:true}).waitFor();
  await reopen();assert.equal((await api('PUT',base,state.create)).state,'PERSISTED');let current=(await rows())[0];assert.equal(current.values.code,code);assert.equal(current.values.Return_quantity,'10/20');assert.equal(current.values.differ_lv,5);checks.push('real test-owned rule creation persists; withheld reply and reload replay the original ID without another insert');
  await page.getByRole('button',{name:'编辑',exact:true}).click();await field('扣除道具数量').fill('30/40');await field('允许装备等级差').fill('6');state.update=await preview();
  state.stale={...structuredClone(state.update),requestId:randomUUID(),values:{...state.update.values,Return_quantity:'50/60',differ_lv:7}};await phase('TWO_SAME_REVISION_PREVIEWS_REQUESTED');assert.equal((await api('POST',base+'/validate',state.stale)).state,'PREVIEWED');
  await modal.getByRole('button',{name:'确认本次预览',exact:true}).click();await modal.waitFor({state:'hidden'});await api('PUT',base,state.stale,409);
  const stale=await api('GET',base+'/history/'+state.stale.requestId);state.staleCancel={requestId:randomUUID(),revision:stale.receiptRevision,reason:'S24 stale concurrent preview rejected; retain committed rule'};await phase('CANCELLING_STALE_PREVIEW');
  await api('POST',base+'/history/'+state.stale.requestId+'/cancellations/preview',state.staleCancel);assert.equal((await api('POST',base+'/history/'+state.stale.requestId+'/cancellations',state.staleCancel)).state,'PERSISTED');
  current=(await rows())[0];assert.equal(current.values.Return_quantity,'30/40');assert.equal(current.values.differ_lv,6);checks.push('second preview from the same revision cannot overwrite the committed rule and is explicitly cancelled');
  await page.getByRole('button',{name:'修改记录与快照',exact:true}).click();const history=page.locator('.arco-modal').filter({has:page.getByText('操作历史与旧版快照',{exact:true})});await history.locator('tr').filter({hasText:state.update.requestId}).getByRole('button',{name:'预览恢复',exact:true}).click();
  state.undoUpdate=await preview();assert.equal(state.undoUpdate.targetRequestId,state.update.requestId);await phase('UPDATE_RESTORE_PREVIEWED');lose='undoUpdate';await modal.getByRole('button',{name:'确认本次预览',exact:true}).click();await modal.locator('.arco-alert').getByText('Network Error',{exact:true}).waitFor();await reopen();
  assert.equal((await api('POST',base+'/history/'+state.update.requestId+'/restore',state.undoUpdate)).state,'PERSISTED');current=(await rows())[0];assert.deepEqual(current.values,state.create.values);checks.push('history starts a separately previewed restoration; withheld reply replays its own ID and restores the original paired costs');
  // Complete delete/restore lifecycle through the same real service, then undo only this test's create.
  state.remove={requestId:randomUUID(),revision:current.revision,operation:'delete',values:current.values};await phase('TEST_RULE_DELETE_REQUESTED');await api('POST',base+'/validate',state.remove);assert.equal((await api('DELETE',base,state.remove)).state,'PERSISTED');assert.deepEqual(await rows(),[]);
  state.undoDelete={requestId:randomUUID(),revision:'new',operation:'restore',targetRequestId:state.remove.requestId};await phase('TEST_RULE_DELETE_RESTORE_REQUESTED');await api('POST',base+'/validate',state.undoDelete);await api('POST',base+'/history/'+state.remove.requestId+'/restore',state.undoDelete);current=(await rows())[0];assert.deepEqual(current.values,state.create.values);
  state.undoCreate={requestId:randomUUID(),revision:current.revision,operation:'restore',targetRequestId:state.create.requestId};await phase('RESTORING_INITIAL_EMPTY_TABLE');await api('POST',base+'/validate',state.undoCreate);await api('POST',base+'/history/'+state.create.requestId+'/restore',state.undoCreate);assert.deepEqual(await rows(),[]);checks.push('delete, restore-deleted and undo-test-create all persist; initial empty rule table is restored without database rollback');
  const anonymous=await context.request.get(origin+base);assert.ok([401,403].includes(anonymous.status()));
  await api('POST',base+'/validate',{...state.create,requestId:randomUUID(),values:{...state.create.values,restitution:'3037/3340',Return_quantity:'1'}},400);
  assert.equal(Number((await api('GET','/api/v1/recharge/6')).cera),37);assert.equal(Number((await api('GET','/api/v1/recharge/points/6')).amount),0);assert.deepEqual(errors,[]);checks.push('anonymous and malformed paired costs are refused; dedicated wallets unchanged and zero page errors');
  await phase('COMPLETED');const result={passed:true,mockedApis:false,testCode:code,initialRows:0,finalRows:0,zeroDefaultActuallyCreated:false,realRuleConfigurationWrittenAndRestored:true,defaultPreviewId:state.defaultRequest.requestId,defaultCancelId:state.defaultCancel.requestId,createId:state.create.requestId,updateId:state.update.requestId,staleId:state.stale.requestId,staleCancelId:state.staleCancel.requestId,restoreUpdateId:state.undoUpdate.requestId,deleteId:state.remove.requestId,restoreDeleteId:state.undoDelete.requestId,restoreCreateId:state.undoCreate.requestId,responseLoss:'browser withholds actual committed replies; not a physical outage',originalWindowsToolStarted:false,nativeGameplayVerified:false,nativeConsumerAcceptance:false,completeReplacement:false,checks,pageErrors:errors.length};await fs.writeFile(path.join(output,'result.json'),JSON.stringify(result,null,2),{mode:0o600});console.log(JSON.stringify(result));
 }catch(e){state.failureType=e.name;await phase('FAILED');throw e}finally{await browser.close()}
})().catch(e=>{console.error(e);process.exitCode=1});
