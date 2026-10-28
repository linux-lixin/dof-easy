// Owned isolated instance only. A real one-point test CDK is compensated through the existing writer.
// Credentials, code, browser screenshots and raw logs stay on the server. Only the public captcha PNG may be viewed for this test.
const assert=require('node:assert/strict'),{readFile,writeFile,mkdir,stat}=require('node:fs/promises'),path=require('node:path');
const {parseArgs}=require('node:util'),{chromium}=require(process.env.PLAYWRIGHT_MODULE||'playwright');
const {values:args}=parseArgs({options:{origin:{type:'string'},credentials:{type:'string'},coupon:{type:'string'},output:{type:'string'}}});
if(!args.origin||!args.credentials||!args.coupon||!args.output)throw new Error('Requires loopback origin and server-private input files');
const origin=new URL(args.origin).origin;if(!['127.0.0.1','localhost'].includes(new URL(origin).hostname))throw new Error('Server loopback only');
const output=path.resolve(args.output),checks=[],errors=[];let coupon;
const phase=async(state,extra={})=>writeFile(path.join(output,'phase.json'),JSON.stringify({state,...extra},null,2),{mode:0o600});
const sleep=ms=>new Promise(resolve=>setTimeout(resolve,ms));
(async()=>{
 await mkdir(output,{recursive:true});for(const file of [args.credentials,args.coupon])assert.equal((await stat(file)).mode&0o077,0);
 const credentials=JSON.parse(await readFile(args.credentials,'utf8'));coupon=JSON.parse(await readFile(args.coupon,'utf8'));
 assert.equal(coupon.purpose,'isolated-s14-cdk-acceptance');assert.equal(coupon.instanceId,'dof-main');assert.equal(coupon.databaseUuid,'899d147e-d0ef-11f1-9ff4-525400542f67');assert.equal(coupon.uid,6);assert.equal(coupon.face,1);assert.ok(coupon.cardId>0);assert.match(coupon.requestId,/^[0-9a-f-]{36}$/);
 const browser=await chromium.launch({headless:true});let admin,player;let usePosts=0,reconcilePosts=0,compensation;
 try{
  await phase('AUTHENTICATING');admin=await browser.newContext({viewport:{width:1500,height:1100}});const page=await admin.newPage();page.on('pageerror',e=>errors.push(e.message));await page.route('**/*',route=>new URL(route.request().url()).origin===origin?route.continue():route.abort());
  await page.goto(origin+'/login?redirect=/admin/player/cdk');await page.getByPlaceholder('请输入用户名').fill(credentials.account);await page.getByPlaceholder('请输入密码').fill(credentials.password);await page.getByRole('button',{name:'登录',exact:true}).click();await page.waitForURL(url=>url.pathname==='/admin/player/cdk');
  const token=await page.evaluate(()=>localStorage.getItem('token'));assert.ok(token);
  const api=async(method,url,data,status=200)=>{const r=await admin.request.fetch(origin+url,{method,data,headers:{Authorization:'Bearer '+token}});assert.equal(r.status(),status,method+' '+url);return r.json()};
  const target=await api('GET','/api/v1/account/6');assert.equal(target.accountname,coupon.account);const balance=await api('GET','/api/v1/recharge/6');assert.equal(balance.databaseUuid,coupon.databaseUuid);assert.equal(Number(balance.cera),37);assert.equal(Number((await api('GET','/api/v1/charac?account='+encodeURIComponent(coupon.account)+'&page=1&pageSize=10')).totalSize),0);checks.push('pinned real dedicated UID 6, baseline 37 points and zero characters verified');
  player=await browser.newContext({viewport:{width:1100,height:1000}});const form=await player.newPage();form.on('pageerror',e=>errors.push(e.message));let abortFirst=true;
  await form.route('**/*',async route=>{
   const request=route.request(),url=new URL(request.url());if(url.origin!==origin)return route.abort();
   if(url.pathname==='/api/v1/keys/use'&&request.method()==='POST'){
    usePosts++;const body=request.postDataJSON();assert.equal(body.requestId,coupon.requestId);assert.equal(body.content,coupon.code);assert.equal(body.useAccount,coupon.account);assert.equal(body.charach,0);assert.equal(request.headers().authorization,undefined);
    const response=await route.fetch();if(abortFirst&&response.status()===200){const result=await response.json();assert.equal(result.state,'PERSISTED');abortFirst=false;return route.abort()}return route.fulfill({response});
   }
   if(url.pathname==='/api/v1/keys/use/reconcile')reconcilePosts++;return route.continue();
  });
  const initialCaptcha=form.waitForResponse(r=>new URL(r.url()).pathname.startsWith('/api/v1/keys/captcha/'));await form.goto(origin+'/cdk');let image=await initialCaptcha;await form.getByPlaceholder('CDK操作编号').fill(coupon.requestId);await form.getByPlaceholder('输入卡密',{exact:true}).fill(coupon.code);await form.getByPlaceholder('输入收取账号',{exact:true}).fill(coupon.account);
  let completed=false;
  for(let attempt=1;attempt<=3;attempt++){
   assert.equal(image.status(),200);assert.match(image.headers()['cache-control'],/no-store/);const captchaId=new URL(image.url()).pathname.split('/').at(-1);await writeFile(path.join(output,'public-captcha.png'),await image.body(),{mode:0o600});await phase('WAITING_FOR_PUBLIC_CAPTCHA',{attempt,captchaId,requestId:coupon.requestId});
   let answer;const deadline=Date.now()+180000;
   while(Date.now()<deadline){try{const candidate=JSON.parse(await readFile(path.join(output,'captcha-answer.json'),'utf8'));if(candidate.captchaId===captchaId&&/^[A-Za-z0-9]{5}$/.test(candidate.value)){answer=candidate.value;break}}catch{}await sleep(1000)}
   if(!answer)throw new Error('Public test captcha was not answered; no new operation ID is created');
   await form.getByPlaceholder('输入CDK验证码',{exact:true}).fill(answer);await form.getByRole('button',{name:attempt===1?'提交兑换':'继续同编号兑换',exact:true}).click();
   await form.getByRole('button',{name:'核对原编号',exact:true}).waitFor();
   const outcome=await Promise.race([form.getByText(/响应未知，先按原编号核对/).waitFor().then(()=> 'unknown'),form.locator('.arco-alert').getByText(/CDK验证码无效或已过期/).waitFor().then(()=> 'captcha')]);
   if(outcome==='unknown'){completed=true;break}
   const next=form.waitForResponse(r=>new URL(r.url()).pathname.startsWith('/api/v1/keys/captcha/'));await form.getByRole('button',{name:'刷新验证码',exact:true}).click();image=await next;
  }
  if(!completed)throw new Error('Captcha retries exhausted without creating another redemption ID');
  await phase('PUBLIC_RESPONSE_WITHHELD',{requestId:coupon.requestId});assert.equal(await form.getByRole('button',{name:'新建兑换编号',exact:true}).isDisabled(),true);await form.getByRole('button',{name:'核对原编号',exact:true}).click();await form.getByText(/兑换已写入，游戏到账或领取仍待核实/).waitFor();const receipt=await api('GET','/api/v1/keys/claims/'+coupon.requestId);assert.equal(receipt.state,'PERSISTED');assert.equal(Number(receipt.uid),6);assert.equal(Number(receipt.keyId),coupon.cardId);assert.equal(receipt.face,1);assert.equal(receipt.outcome.usedKey.status,1);assert.equal(Number(receipt.outcome.usedKey.useUid),6);assert.equal(Number((await api('GET','/api/v1/recharge/6')).cera),38);checks.push('anonymous real one-point redemption commits code use and credit; intentionally lost browser response recovers by original ID');
  const stored=await form.evaluate(()=>JSON.stringify([Object.entries(localStorage),Object.entries(sessionStorage)]));assert.ok(!stored.includes(coupon.code));assert.equal(await form.evaluate(()=>localStorage.getItem('token')),null);checks.push('public redemption uses no administrator session and does not persist the code in browser storage');await phase('PUBLIC_RECEIPT_PERSISTED',{requestId:coupon.requestId});
  await page.reload();const row=page.getByRole('row').filter({hasText:coupon.requestId});await row.getByRole('button',{name:'预览点券补偿扣回',exact:true}).click();const dialog=page.locator('.arco-modal');await dialog.getByPlaceholder('填写本次调整的原因').fill('S14 isolated CDK acceptance one-point compensation');
  const preview=page.waitForResponse(r=>new URL(r.url()).pathname==='/api/v1/recharge/6/preview'&&r.request().method()==='POST');await dialog.getByRole('button',{name:'预览点券变更',exact:true}).click();const previewResponse=await preview;assert.equal(previewResponse.status(),200);compensation=previewResponse.request().postDataJSON();assert.equal(compensation.targetRequestId,coupon.requestId);assert.equal(compensation.cera,-1);await phase('COMPENSATION_PREVIEWED',{requestId:coupon.requestId,compensationRequestId:compensation.requestId});
  const applied=page.waitForResponse(r=>new URL(r.url()).pathname==='/api/v1/recharge'&&r.request().method()==='POST');await dialog.getByRole('button',{name:'提交本次预览',exact:true}).click();const appliedResponse=await applied;assert.equal(appliedResponse.status(),200);assert.equal((await appliedResponse.json()).state,'PERSISTED');await phase('COMPENSATION_COMMITTED',{requestId:coupon.requestId,compensationRequestId:compensation.requestId});
  assert.equal((await api('POST','/api/v1/recharge',compensation)).state,'PERSISTED');assert.equal(Number((await api('GET','/api/v1/recharge/6')).cera),37);const publicReplay=await player.request.post(origin+'/api/v1/keys/use',{data:{requestId:coupon.requestId,content:coupon.code,useAccount:coupon.account,charach:0}});assert.equal(publicReplay.status(),200);assert.equal((await publicReplay.json()).state,'PERSISTED');assert.equal(Number((await api('GET','/api/v1/recharge/6')).cera),37);
  const status=await api('GET','/api/v1/recharge/6');await api('POST','/api/v1/recharge/6/preview',{...compensation,requestId:require('node:crypto').randomUUID(),revision:status.revision,reason:'controlled duplicate CDK compensation refusal'},409);checks.push('real administrator UI compensates only the original one-point award; replays and a second compensation cannot credit or deduct again');
  const current=await api('GET','/api/v1/keys/claims/'+coupon.requestId);assert.equal(current.outcome.usedKey.status,1);assert.deepEqual(errors,[]);await page.screenshot({path:path.join(output,'admin-cdk-private.png'),fullPage:true});const result={passed:true,mockedApis:false,uid:6,cardId:coupon.cardId,claimRequestId:coupon.requestId,compensationRequestId:compensation.requestId,face:1,balanceBefore:37,balanceAfterClaim:38,balanceAfterCompensation:37,codeRemainsUsed:true,controlledLostBrowserResponse:true,publicUsePosts:usePosts,publicReconcilePosts:reconcilePosts,actualClientConsumptionVerified:false,equipmentCdkVerified:false,nativeCompanionParityVerified:false,checks,pageErrors:errors.length};await writeFile(path.join(output,'result.json'),JSON.stringify(result,null,2),{mode:0o600});await phase('PASSED',{requestId:coupon.requestId,compensationRequestId:compensation.requestId});console.log(JSON.stringify(result));
 }catch(e){await phase('NEEDS_RECONCILIATION',{requestId:coupon.requestId,compensationRequestId:compensation?.requestId,errorType:e.constructor.name});throw e}
 finally{if(player)await player.close();if(admin)await admin.close();await browser.close()}
})().catch(e=>{console.error(e);process.exitCode=1});
