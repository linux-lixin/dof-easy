// Real server browser/API reads plus invalid legacy-write refusal; never issues native mail.
// Run beside the pinned isolated instance. Credentials and screenshots remain on that server.
const assert=require('node:assert/strict'),{readFile,writeFile,mkdir,stat}=require('node:fs/promises'),path=require('node:path');
const {parseArgs}=require('node:util'),{chromium}=require(process.env.PLAYWRIGHT_MODULE||'playwright');
const {values:args}=parseArgs({options:{origin:{type:'string'},credentials:{type:'string'},output:{type:'string'},instance:{type:'string'},'database-uuid':{type:'string'},'target-uid':{type:'string'},'expect-batch':{type:'boolean',default:false},'expect-receipt-control':{type:'boolean',default:false},'expect-batch-control':{type:'boolean',default:false},'expect-cdk':{type:'boolean',default:false}}});
if(!args.origin||!args.credentials||!args.output||args.instance!=='dof-main'||args['target-uid']!=='6'||args['database-uuid']!=='899d147e-d0ef-11f1-9ff4-525400542f67')throw new Error('Requires pinned dof-main, database UUID and existing dedicated UID 6');
const origin=new URL(args.origin).origin;if(!['127.0.0.1','localhost','[::1]'].includes(new URL(origin).hostname))throw new Error('Server loopback origin required');
const output=path.resolve(args.output),checks=[],errors=[];
(async()=>{
 await mkdir(output,{recursive:true});assert.equal((await stat(args.credentials)).mode&0o077,0);const credentials=JSON.parse(await readFile(args.credentials,'utf8'));
 const browser=await chromium.launch({headless:true});let context;
 try{
  context=await browser.newContext({viewport:{width:1440,height:1100}});const page=await context.newPage();page.on('pageerror',e=>errors.push(e.message));
  await page.route('**/*',route=>new URL(route.request().url()).origin===origin?route.continue():route.abort());
  await page.goto(origin+'/login?redirect=/admin/player/roles');await page.getByPlaceholder('请输入用户名').fill(credentials.account);await page.getByPlaceholder('请输入密码').fill(credentials.password);
  const login=page.waitForResponse(r=>new URL(r.url()).pathname==='/api/v1/login'&&r.request().method()==='POST');await page.getByRole('button',{name:'登录',exact:true}).click();assert.equal((await login).status(),200);await page.waitForURL(url=>url.pathname==='/admin/player/roles');
  const token=await page.evaluate(()=>localStorage.getItem('token'));assert.ok(token);
  const api=async(method,url,data,status=200)=>{const r=await context.request.fetch(origin+url,{method,data,headers:{Authorization:'Bearer '+token}});assert.equal(r.status(),status,method+' '+url);return r.json()};
  const catalog=await api('GET','/api/v1/admin/dof');assert.equal(catalog.instanceId,args.instance);assert.equal(catalog.completeReplacement,false);assert.equal(catalog.scope.baselineClasses,71);assert.equal(catalog.scope.baselineOperations,239);assert.equal(catalog.scope.nativeMenuAuditComplete,false);
  const feature=catalog.scope.groups.find(g=>g.id==='feature-007');assert.equal(feature.sendManagementImplementation.protocol,'postal-generated-id-v1');assert.equal(feature.sendManagementImplementation.actualPlayerReceiptVerified,false);checks.push('real administrator login and current mail protocol catalog with native gates pending');
  const anonymous=await context.request.get(origin+'/api/v1/postal/2147483647/history');assert.ok([401,403].includes(anonymous.status()));checks.push('anonymous mail management rejected');
  const legacy=await api('POST','/api/v1/postal',{receiveCharacNo:2147483647,itemId:5001},400);assert.match(legacy.message,/操作编号/);checks.push('legacy unversioned sender rejected before native issuance');
  await api('GET','/api/v1/postal/0/send-status',undefined,400);
  const absent=await api('GET','/api/v1/postal/2147483647/send-status',undefined,404);assert.match(absent.message,/角色不存在/);assert.deepEqual(await api('GET','/api/v1/postal/2147483647/history'),[]);checks.push('actual native mail schema readable; nonexistent recipient rejected and no mail history fabricated');
  if(args['expect-receipt-control']){
   assert.equal(feature.receiptControl.codeImplemented,true);assert.equal(feature.receiptControl.actualPlayerReceiptVerified,false);
   assert.deepEqual(await api('GET','/api/v1/postal/2147483647/receipt-controls'),[]);
   const denied=await context.request.get(origin+'/api/v1/postal/2147483647/receipt-controls');assert.ok([401,403].includes(denied.status()));
   const endpoint='/api/v1/postal/2147483647/history/00000000-0000-4000-8000-000000000001/acknowledgments';
   await api('POST',endpoint+'/preview',{reason:'controlled missing operation ID'},400);
   await api('POST',endpoint+'/preview',{requestId:'00000000-0000-4000-8000-000000000002',revision:'0'.repeat(64),reason:'controlled nonexistent source'},404);
   assert.deepEqual(await api('GET','/api/v1/postal/2147483647/receipt-controls'),[]);
   checks.push('real metadata receipt routes, anonymous denial, missing operation ID and nonexistent source refusal without creating receipts');
  }
  if(args['expect-batch-control']){
   assert.equal(feature.batchProtocol.taskControls.codeImplemented,true);assert.equal(feature.batchProtocol.taskControls.retryLineageClaimGuard,true);
   const root='/api/v1/postal/batch/00000000-0000-4000-8000-000000000003/controls';
   const denied=await context.request.get(origin+root);assert.ok([401,403].includes(denied.status()));
   await api('GET',root,undefined,404);
   await api('POST',root+'/preview',{operation:'cancel-pending',reason:'controlled missing operation ID'},400);
   await api('POST',root+'/preview',{requestId:'00000000-0000-4000-8000-000000000004',revision:'0'.repeat(64),operation:'cancel-pending',reason:'controlled nonexistent parent'},404);
   checks.push('real batch control routes reject anonymous, missing-ID and nonexistent-parent requests without changing any task');
  }
  if(args['expect-cdk']){
   const cdk=catalog.scope.groups.find(g=>g.id==='feature-009');assert.equal(cdk.websiteRedemption.protocol,'cdk-use-v1');assert.equal(cdk.websiteRedemption.nativeCompanionParityVerified,false);
   assert.ok(Array.isArray(await api('GET','/api/v1/keys/claims')));
   for(const url of ['/api/v1/keys/claims','/api/v1/keys?page=1&pageSize=10']){const denied=await context.request.get(origin+url);assert.ok([401,403].includes(denied.status()))}
   const oldUse=await context.request.post(origin+'/api/v1/keys/use',{data:{content:'controlled-nonexistent-cdk',useAccount:'probe',face:999999}});assert.equal(oldUse.status(),400);
   const missing=await context.request.post(origin+'/api/v1/keys/use/reconcile',{data:{requestId:'00000000-0000-4000-8000-000000000014',content:'controlled-nonexistent-cdk',useAccount:'probe',charach:0}});assert.equal(missing.status(),404);checks.push('real CDK history permission, legacy unversioned use refusal and same-ID missing receipt lookup without issuance');
   const image=await context.request.get(origin+'/api/v1/keys/captcha/00000000-0000-4000-8000-000000000015');assert.equal(image.status(),200);assert.match(image.headers()['cache-control'],/no-store/);assert.equal((await image.body()).subarray(0,8).toString('hex'),'89504e470d0a1a0a');checks.push('real purpose-scoped CDK captcha is a PNG with no-store caching');
   const history=page.waitForResponse(r=>new URL(r.url()).pathname==='/api/v1/keys/claims'&&r.request().method()==='GET');await page.goto(origin+'/admin/player/cdk');assert.equal((await history).status(),200);await page.getByPlaceholder('按原兑换编号核对').waitFor();await page.goto(origin+'/cdk');await page.getByPlaceholder('输入卡密',{exact:true}).waitFor();assert.equal(await page.getByPlaceholder('输入卡密',{exact:true}).inputValue(),'');assert.equal(await page.getByPlaceholder('输入收取账号',{exact:true}).inputValue(),'');checks.push('real administrator CDK records and public redemption page load without persisting secret inputs');
  }
  const target=await api('GET','/api/v1/account/6');assert.equal(Number(target.uid),6);assert.ok(target.password==null);const roles=await api('GET','/api/v1/charac?account='+encodeURIComponent(target.accountname)+'&page=1&pageSize=10');assert.equal(Number(roles.totalSize),0);assert.deepEqual(roles.list,[]);checks.push('dedicated test account has no real character; live issuance and consumption explicitly remain pending');
  const balance=await api('GET','/api/v1/recharge/6');assert.equal(balance.databaseUuid,args['database-uuid']);assert.equal(Number(balance.cera),37);const moneyHistory=await api('GET','/api/v1/recharge/6/history');for(const id of ['418f6c07-5349-47ce-8a9a-ebfb34026906','87cd2653-bcfc-473d-a584-30327e03f263'])assert.equal(moneyHistory.find(r=>r.requestId===id)?.state,'PERSISTED');checks.push('existing recharge receipts retained and dedicated balance unchanged without any recharge request');
  if(args['expect-batch']){
   assert.equal(feature.batchProtocol.protocol,'postal-batch-v1');assert.equal(feature.batchProtocol.requiresChildRequestId,true);assert.equal(feature.batchProtocol.actualPlayerReceiptVerified,false);
   assert.ok(Array.isArray(await api('GET','/api/v1/postal/batch/history')));
   await api('POST','/api/v1/postal/batch/validate',{recipients:[],mail:{gold:1},reason:'controlled invalid empty selection'},400);
   await api('POST','/api/v1/postal/batch',{recipients:[2147483647],mail:{gold:1},reason:'controlled invalid missing operation ID'},400);
   const denied=await context.request.get(origin+'/api/v1/postal/batch/history');assert.ok([401,403].includes(denied.status()));checks.push('real batch routes, anonymous rejection, empty selection and unversioned start refusal');
   const loaded=page.waitForResponse(r=>new URL(r.url()).pathname==='/api/v1/charac'&&r.request().method()==='GET');await page.goto(origin+'/admin/player/roles');assert.equal((await loaded).status(),200);
   const batchRead=page.waitForResponse(r=>new URL(r.url()).pathname==='/api/v1/postal/batch/history'&&r.request().method()==='GET');await page.getByRole('button',{name:'批量任务历史',exact:true}).click();assert.equal((await batchRead).status(),200);
   const dialog=page.locator('.arco-modal');await dialog.getByText('持久化批量历史',{exact:true}).waitFor();await dialog.getByRole('button',{name:'生成批量预览',exact:true}).click();await dialog.getByText('请选择1～100个固定角色',{exact:true}).waitFor();checks.push('real batch history modal reads backend and refuses preview with no selected recipients');
  }
  const response=page.waitForResponse(r=>new URL(r.url()).pathname==='/api/v1/postal'&&r.request().method()==='GET');await page.goto(origin+'/admin/player/postal');assert.equal((await response).status(),200);await page.getByPlaceholder('收件人账号（精确匹配，可选）').waitFor();checks.push('real deployed postal records page loads through actual backend');
  assert.deepEqual(errors,[]);const result={passed:true,mockedApis:false,realDeploymentReadAndRefusalVerified:true,batchReadAndRefusalVerified:args['expect-batch'],receiptControlReadAndRefusalVerified:args['expect-receipt-control'],batchControlReadAndRefusalVerified:args['expect-batch-control'],cdkReadAndRefusalVerified:args['expect-cdk'],actualMailIssued:false,actualPlayerReceiptVerified:false,mailComposeUiOnRealCharacterVerified:false,batchComposeUiOnRealTargetsVerified:false,checks,pageErrors:errors.length,targetUid:6,dedicatedCharacterCount:0,balanceUnchanged:37};await page.screenshot({path:path.join(output,'postal-live.png'),fullPage:true});await writeFile(path.join(output,'result.json'),JSON.stringify(result,null,2),{mode:0o600});console.log(JSON.stringify(result));
 }finally{if(context)await context.close();await browser.close()}
})().catch(e=>{console.error(e);process.exitCode=1});
