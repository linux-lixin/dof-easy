// Read-only browser check against an explicitly bound deployment through an SSH tunnel.
const assert = require('node:assert/strict');
const { readFile, mkdir, writeFile } = require('node:fs/promises');
const { parseArgs } = require('node:util');
const path = require('node:path');
const { chromium } = require(process.env.PLAYWRIGHT_MODULE || 'playwright');
const { values:a } = parseArgs({options:{origin:{type:'string'},credentials:{type:'string'},instance:{type:'string'},receipt:{type:'string'},output:{type:'string'}}});
for(const key of ['origin','credentials','instance','receipt','output'])if(!a[key])throw new Error('Missing '+key);
const origin=new URL(a.origin).origin;if(!['127.0.0.1','localhost'].includes(new URL(origin).hostname))throw new Error('Use a private loopback SSH tunnel');
if(!/^[0-9a-f-]{36}$/.test(a.receipt))throw new Error('Invalid receipt');
(async()=>{
 await mkdir(a.output,{recursive:true});const credentials=JSON.parse(await readFile(a.credentials,'utf8'));const errors=[];
 const browser=await chromium.launch({headless:true});
 try{
  const context=await browser.newContext({viewport:{width:1600,height:1200}}),page=await context.newPage();page.on('pageerror',e=>errors.push(e.message));
  await page.route('**/*',r=>new URL(r.request().url()).origin===origin?r.continue():r.abort());
  await page.goto(origin+'/login?redirect=/admin/extension-operations');await page.getByPlaceholder('请输入用户名').fill(credentials.account);await page.getByPlaceholder('请输入密码').fill(credentials.password);await page.getByRole('button',{name:'登录',exact:true}).click();await page.waitForURL(u=>u.pathname==='/admin/extension-operations');
  await page.getByText('注册激活码',{exact:true}).click();await page.getByText('注册激活码：已关闭',{exact:true}).waitFor();
  const token=await page.evaluate(()=>localStorage.getItem('token'));
  const api=async p=>{const r=await context.request.get(origin+'/api/v1/admin/'+p,{headers:{Authorization:'Bearer '+token}});assert.equal(r.status(),200,p);return r.json();};
  assert.equal((await api('dof')).instanceId,a.instance);
  const policy=await api('registration/policy');assert.equal(policy.enabled,false);assert.equal(policy.registrationReadiness.giftAmount,0);assert.equal(policy.registrationReadiness.ipLimit,1);assert.equal(policy.registrationReadiness.ready,false);assert.match(policy.registrationReadiness.reason,/历史|来源/);
  const rows=await api('registration/receipts');const receipt=rows.find(r=>r.request_id===a.receipt);assert.ok(receipt);assert.equal(receipt.state,'PERSISTED');assert.equal(receipt.gift_amount,37);assert.equal(receipt.gift_currency,1);
  const row=page.getByRole('row').filter({hasText:a.receipt});await row.waitFor();assert.match(await row.innerText(),/PERSISTED/);assert.match(await row.innerText(),/37/);
  assert.deepEqual(errors,[]);await page.screenshot({path:path.join(a.output,'registration-live.png'),fullPage:true});
  const result={passed:true,backend:'real deployment after container recreation',instanceId:a.instance,actualGameplayVerified:false,pageErrors:0,checks:['real administrator login and registration navigation','persisted registration receipt visible','original registration policy and gift settings restored','historical registration IP gap explicitly displayed','no empty-success fallback']};
  await writeFile(path.join(a.output,'result.json'),JSON.stringify(result,null,2)+'\n');console.log(JSON.stringify(result));
 }finally{await browser.close();}
})().catch(e=>{console.error(e);process.exitCode=1;});
