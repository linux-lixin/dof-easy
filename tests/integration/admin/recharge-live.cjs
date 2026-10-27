// Controlled writes only to the pre-existing S3 synthetic UID 6 on the pinned isolated instance.
// Run on the test server; credentials and recovery state remain there.
const assert=require('node:assert/strict');
const {readFile,writeFile,rename,mkdir,stat}=require('node:fs/promises');
const path=require('node:path');
const {parseArgs}=require('node:util');
const {randomUUID}=require('node:crypto');
const {chromium}=require(process.env.PLAYWRIGHT_MODULE||'playwright');
const {values:args}=parseArgs({options:{origin:{type:'string'},credentials:{type:'string'},output:{type:'string'},instance:{type:'string'},'database-uuid':{type:'string'},'target-uid':{type:'string'},'allow-test-recharge':{type:'boolean',default:false},points:{type:'boolean',default:false}}});
if(!args.origin||!args.credentials||!args.output||args.instance!=='dof-main'||args['target-uid']!=='6'||args['database-uuid']!=='899d147e-d0ef-11f1-9ff4-525400542f67'||!args['allow-test-recharge'])throw new Error('Requires explicitly pinned dof-main, UUID, synthetic UID 6 and --allow-test-recharge');
const origin=new URL(args.origin).origin;if(!['127.0.0.1','localhost','[::1]'].includes(new URL(origin).hostname))throw new Error('Server loopback origin required');
const base=args.points?'/api/v1/recharge/points':'/api/v1/recharge',balanceField=args.points?'amount':'cera',beforeField=args.points?'beforeAmount':'beforeCera',afterField=args.points?'afterAmount':'afterCera',initial=args.points?0:37,delta=args.points?1:5,unit=args.points?'代币券':'点券',openButton=args.points?'代币券管理':'充值';
const output=path.resolve(args.output),state={phase:'INITIAL',targetUid:6,instanceId:args.instance,databaseUuid:args['database-uuid'],moneyRestored:false},checks=[],errors=[];
const statePath=path.join(output,'operation-state.json');
async function checkpoint(phase){state.phase=phase;const tmp=statePath+'.tmp';await writeFile(tmp,JSON.stringify(state,null,2),{mode:0o600});await rename(tmp,statePath)}
(async()=>{
 await mkdir(output,{recursive:true});
 try{await stat(statePath);throw new Error('Existing operation state: reconcile it instead of rerunning money writes')}catch(e){if(e.code!=='ENOENT')throw e}
 assert.equal((await stat(args.credentials)).mode&0o077,0,'Credentials must remain private');
 const credentials=JSON.parse(await readFile(args.credentials,'utf8'));
 const browser=await chromium.launch({headless:true});let context,token,loseCreditReply=true;
 try{
  context=await browser.newContext({viewport:{width:1600,height:1100}});const page=await context.newPage();page.on('pageerror',e=>errors.push(e.message));
  await page.route('**/*',async route=>{
   const req=route.request(),url=new URL(req.url());if(url.origin!==origin)return route.abort();
   if(loseCreditReply&&url.pathname===base&&req.method()==='POST'&&req.postDataJSON()?.operation==='credit'){
    loseCreditReply=false;state.creditResponseLossInjected=true;
    const response=await route.fetch({maxRetries:0,timeout:30000});assert.equal(response.status(),200);
    state.creditReceipt=await response.json();assert.equal(state.creditReceipt.state,'PERSISTED');await checkpoint('CREDIT_COMMITTED_REPLY_WITHHELD');
    return route.abort('connectionreset');
   }
   return route.continue();
  });
  await page.goto(origin+'/login?redirect=/admin/player/accounts');
  await page.getByPlaceholder('请输入用户名').fill(credentials.account);await page.getByPlaceholder('请输入密码').fill(credentials.password);
  const login=page.waitForResponse(r=>new URL(r.url()).pathname==='/api/v1/login'&&r.request().method()==='POST');
  await page.getByRole('button',{name:'登录',exact:true}).click();assert.equal((await login).status(),200);await page.waitForURL(url=>url.pathname==='/admin/player/accounts');
  token=await page.evaluate(()=>localStorage.getItem('token'));assert.ok(token);
  const api=async(method,url,data,expected=200)=>{
   const reply=await context.request.fetch(origin+url,{method,data,headers:{Authorization:'Bearer '+token}});assert.equal(reply.status(),expected,method+' '+url);return reply.json();
  };
  const catalog=await api('GET','/api/v1/admin/dof');assert.equal(catalog.instanceId,args.instance);assert.equal(catalog.completeReplacement,false);
  assert.equal(catalog.scope.baselineClasses,71);assert.equal(catalog.scope.baselineOperations,239);assert.equal(catalog.scope.initialBaselineOperations,209);assert.equal(catalog.scope.discoveredOperations,239);assert.equal(catalog.scope.nativeMenuAuditComplete,false);
  assert.equal(catalog.scope.groups.reduce((n,g)=>n+g.operations.length,0),239);assert.ok(catalog.scope.groups.every(g=>g.operations.every(op=>op.included)));
  const target=await api('GET','/api/v1/account/6');assert.equal(Number(target.uid),6);assert.ok(target.password==null);
  if(args.points)assert.equal(Number((await api('GET','/api/v1/recharge/6')).cera),37);
  const before=await api('GET',base+'/6');assert.equal(before.databaseUuid,args['database-uuid']);assert.equal(before.instanceId,args.instance);assert.equal(before.engine,'InnoDB');assert.equal(before.atomicReceipt,true);assert.equal(Number(before[balanceField]),initial);
  state[beforeField]=initial;state.actor=before.actor;await checkpoint('AUTHENTICATED_AND_BOUND');checks.push('real administrator login, instance/UUID, 71/239 scope and native incomplete gate');
  const anonymous=await context.request.get(origin+base+'/6');assert.ok([401,403].includes(anonymous.status()));
  await api('POST',base,{uid:6,[balanceField]:delta},400);assert.equal(Number((await api('GET',base+'/6'))[balanceField]),initial);checks.push('anonymous and old unversioned write rejected without changing balance');
  const dialog=page.locator('.arco-modal');
  const open=async()=>{
   await page.getByPlaceholder('搜索玩家账号').fill(target.accountname);
   const listed=page.waitForResponse(r=>new URL(r.url()).pathname==='/api/v1/account'&&new URL(r.url()).searchParams.get('account')===target.accountname);
   await page.getByRole('button',{name:'搜索',exact:true}).click();const list=await (await listed).json();assert.equal(list.list.length,1);assert.equal(Number(list.list[0].uid),6);
   await page.getByRole('button',{name:openButton,exact:true}).click();await dialog.getByText(new RegExp('当前'+unit+'：')).waitFor();
  };
  const preview=async()=>{
   const body=page.waitForRequest(r=>new URL(r.url()).pathname===base+'/6/preview'&&r.method()==='POST');
   const response=page.waitForResponse(r=>new URL(r.url()).pathname===base+'/6/preview'&&r.request().method()==='POST');
   await dialog.getByRole('button',{name:`预览${unit}变更`,exact:true}).click();const request=(await body).postDataJSON(),reply=await response;assert.equal(reply.status(),200);return {request,receipt:await reply.json()};
  };
  await open();await dialog.locator('.arco-input-number input').fill(String(delta));await dialog.getByPlaceholder('填写本次调整的原因').fill(`codex_${args.points?'s16_points':'s8'}_controlled_credit_uid6`);
  const credit=await preview();state.creditRequest=credit.request;assert.equal(credit.receipt.state,'PREVIEWED');assert.equal(Number(credit.receipt[beforeField]),initial);assert.equal(Number(credit.receipt[afterField]),initial+delta);assert.equal(Number((await api('GET',base+'/6'))[balanceField]),initial);await checkpoint('CREDIT_PREVIEWED');
  await dialog.getByRole('button',{name:'提交本次预览',exact:true}).click();await dialog.getByText(/响应不明，保留原编号/).waitFor();assert.equal(await dialog.getByRole('button',{name:'提交本次预览',exact:true}).isDisabled(),true);
  assert.equal(Number((await api('GET',base+'/6'))[balanceField]),initial+delta);assert.equal(state.creditReceipt.requestId,state.creditRequest.requestId);
  const reconciled=page.waitForResponse(r=>new URL(r.url()).pathname.endsWith('/'+state.creditRequest.requestId+'/reconcile')&&r.request().method()==='POST');
  await dialog.getByRole('button',{name:'核对本次操作',exact:true}).click();assert.equal((await (await reconciled).json()).state,'PERSISTED');
  assert.equal((await api('POST',base,state.creditRequest)).state,'PERSISTED');assert.equal(Number((await api('GET',base+'/6'))[balanceField]),initial+delta);await checkpoint('CREDIT_CONFIRMED_ONCE');checks.push('real UI preview/submit, committed response withheld, original-ID reconciliation and replay award only once');
  await page.reload();await open();await dialog.getByRole('row').filter({hasText:state.creditRequest.requestId}).getByRole('button',{name:'预览补偿扣回',exact:true}).click();
  await dialog.getByPlaceholder('填写本次调整的原因').fill(`codex_${args.points?'s16_points':'s8'}_linked_compensation_uid6`);
  const compensation=await preview();state.compensationRequest=compensation.request;assert.equal(compensation.receipt.targetRequestId,state.creditRequest.requestId);assert.equal(Number(compensation.receipt.delta),-delta);assert.equal(Number(compensation.receipt[beforeField]),initial+delta);assert.equal(Number(compensation.receipt[afterField]),initial);await checkpoint('COMPENSATION_PREVIEWED');
  const compensated=page.waitForResponse(r=>new URL(r.url()).pathname===base&&r.request().method()==='POST');await dialog.getByRole('button',{name:'提交本次预览',exact:true}).click();assert.equal((await (await compensated).json()).state,'PERSISTED');
  assert.equal(Number((await api('GET',base+'/6'))[balanceField]),initial);state.moneyRestored=true;await checkpoint('COMPENSATED');
  assert.equal((await api('POST',base,state.compensationRequest)).state,'PERSISTED');assert.equal((await api('POST',base,state.creditRequest)).state,'PERSISTED');assert.equal(Number((await api('GET',base+'/6'))[balanceField]),initial);
  const current=await api('GET',base+'/6');await api('POST',base+'/6/preview',{...state.compensationRequest,requestId:randomUUID(),revision:current.revision},409);assert.equal(Number((await api('GET',base+'/6'))[balanceField]),initial);
  if(args.points){const cera=await api('GET','/api/v1/recharge/6');assert.equal(Number(cera.cera),37);await api('POST','/api/v1/recharge/6/preview',{uid:6,cera:-delta,operation:'compensate',reason:'S16 reject cross currency debit',requestId:randomUUID(),revision:cera.revision,targetRequestId:state.creditRequest.requestId},409);checks.push('token coupon receipt cannot debit cera; original cera wallet remains 37')}
  const history=await api('GET',base+'/6/history');assert.equal(history.filter(row=>row.requestId===state.creditRequest.requestId).length,1);assert.equal(history.filter(row=>row.requestId===state.compensationRequest.requestId).length,1);checks.push('real reload/history, linked one-time compensation, replay after compensation and duplicate-compensation refusal');
  await page.screenshot({path:path.join(output,'recharge-live.png'),fullPage:true});
  const mailLoaded=page.waitForResponse(r=>new URL(r.url()).pathname==='/api/v1/postal'&&r.request().method()==='GET');await page.goto(origin+'/admin/player/postal');const mail=await mailLoaded;assert.equal(mail.status(),200);const mailData=await mail.json();assert.ok(Array.isArray(mailData.list));
  const literal="codex_s8_missing_'&pageSize=100";await page.getByPlaceholder('收件人账号（精确匹配，可选）').fill(literal);
  const filtered=page.waitForResponse(r=>new URL(r.url()).pathname==='/api/v1/postal'&&new URL(r.url()).searchParams.get('account')===literal);await page.getByRole('button',{name:'查询邮件',exact:true}).click();const reply=await filtered;assert.equal(reply.status(),200);assert.equal(new URL(reply.url()).searchParams.get('pageSize'),'20');assert.equal(Number((await reply.json()).totalSize),0);checks.push('real postal page and literal nonexistent account filter; populated native mail comparison still pending');
  assert.deepEqual(errors,[]);await checkpoint('COMPLETED');
  const result={passed:true,checks,pageErrors:errors.length,realDeployment:true,actualPlayerReceiptVerified:false,originalToolEquivalenceVerified:false,responseLoss:'intercepted browser response after actual server commit; not a physical outage',targetUid:6,[beforeField]:initial,[afterField]:initial,creditRequestId:state.creditRequest.requestId,compensationRequestId:state.compensationRequest.requestId,netAmountChange:0,currency:args.points?'cera_point':'cera',scopeMinimum:239,completeReplacement:false,mailObservedRows:mailData.list.length};
  await writeFile(path.join(output,'result.json'),JSON.stringify(result,null,2),{mode:0o600});console.log(JSON.stringify(result));
 }catch(error){state.failureType=error.name;await checkpoint('FAILED');throw error}
 finally{await browser.close()}
})().catch(error=>{console.error(error);process.exitCode=1});
