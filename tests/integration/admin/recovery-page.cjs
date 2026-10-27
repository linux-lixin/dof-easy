// Compiled UI with the production field catalog and explicitly synthetic APIs.
const assert=require('node:assert/strict'),{createServer}=require('node:http'),{createHash}=require('node:crypto');
const {readFile,mkdir,writeFile}=require('node:fs/promises'),path=require('node:path'),{parseArgs}=require('node:util');
const {chromium}=require(process.env.PLAYWRIGHT_MODULE||'playwright');
const {values:args}=parseArgs({options:{dist:{type:'string'},catalog:{type:'string'},output:{type:'string'}}});
const dist=path.resolve(args.dist),output=path.resolve(args.output),base='/api/v1/admin/activities';
const checks=[],errors=[],records=new Map();let native={code:27600,restitution:'3037/3340',Return_quantity:'10/20',announcement:0,codenum:1,extra_consumption_id:'0/3037',extra_consumption_num:'500/1'},writes=0;
const hash=x=>createHash('sha256').update(JSON.stringify(x)).digest('hex'),copy=x=>JSON.parse(JSON.stringify(x));
const view=r=>({requestId:r.body.requestId,state:r.state,operation:r.body.operation,values:r.body.values,revision:hash(r.body.values),persisted:r.state==='PERSISTED',requestReplayAvailable:r.state==='PREVIEWED',request:r.body});
const server=createServer(async(req,res)=>{try{let f=path.resolve(dist,'.'+decodeURIComponent(new URL(req.url,'http://localhost').pathname));if(!f.startsWith(dist+path.sep))return res.writeHead(403).end();let bytes;try{bytes=await readFile(f)}catch{f=path.join(dist,'index.html');bytes=await readFile(f)}res.writeHead(200,{'Content-Type':({'.js':'text/javascript','.css':'text/css','.html':'text/html'})[path.extname(f)]||'application/octet-stream'}).end(bytes)}catch{res.writeHead(500).end()}});
(async()=>{
 const all=JSON.parse(await readFile(args.catalog,'utf8')),catalog=all.filter(s=>['signin','recovery','cross-border'].includes(s.key));
 assert.equal(catalog.find(s=>s.key==='recovery').writable,true);assert.equal(catalog.find(s=>s.key==='cross-border').writable,false);
 await mkdir(output,{recursive:true});await new Promise(r=>server.listen(0,'127.0.0.1',r));const origin=`http://127.0.0.1:${server.address().port}`;let browser,page;
 try{
  browser=await chromium.launch({headless:true});page=await browser.newPage({viewport:{width:1700,height:1300}});page.on('pageerror',e=>errors.push(e.message));await page.addInitScript(()=>localStorage.setItem('token','synthetic-recovery-admin'));
  await page.route('**/*',async route=>{
   const req=route.request(),u=new URL(req.url());if(u.origin!==origin)return route.abort();if(!u.pathname.startsWith('/api/'))return route.continue();const reply=(data,status=200)=>route.fulfill({status,contentType:'application/json',body:JSON.stringify(data)});
   if(u.pathname==='/api/v1/admin/launcher/runtime')return reply({mode:'dof'});
   if(u.pathname===base)return reply(catalog);
   const match=u.pathname.slice(base.length).match(/^\/(signin|recovery|cross-border)(?:\/(.*))?$/);if(!match)return reply([]);const key=match[1],suffix=match[2]||'';
   if(!suffix&&req.method()==='GET')return reply({rows:key==='signin'?[]:[{revision:hash(native),values:key==='recovery'?native:{code:0,restitution:'3037',Return_quantity:'1',announcement:0,codenum:1}}],moduleNotice:catalog.find(s=>s.key===key).notice});
   if(suffix==='validate') {const body=req.postDataJSON();assert.equal(key,'recovery');const r={body:copy(body),state:'PREVIEWED'};records.set(body.requestId,r);return reply({...view(r),changes:[]});}
   if(!suffix&&req.method()==='PUT'){const body=req.postDataJSON(),r=records.get(body.requestId);assert.ok(r);assert.deepEqual(body,r.body);assert.equal(r.state,'PREVIEWED');native=copy(body.values);writes++;r.state='PERSISTED';return reply(view(r));}
   return reply([]);
  });
  const select=async title=>{await page.locator('.arco-select').first().click();await page.locator('.arco-select-option').getByText(title,{exact:true}).click()};
  const modal=page.locator('.arco-modal').filter({has:page.getByRole('button',{name:'确认本次预览',exact:true})});
  const field=label=>modal.locator('.arco-form-item').filter({has:page.locator('.arco-form-item-label').getByText(label,{exact:true})}).locator('input');
  await page.goto(origin+'/admin/activities');await select('原生物品回收规则');
  await page.getByRole('columnheader',{name:'奖励物品代码',exact:true}).waitFor();await page.getByText('3037/3340',{exact:true}).waitFor();
  await page.getByRole('button',{name:'编辑',exact:true}).click();
  assert.equal(await field('回收物品代码').inputValue(),'27600');assert.equal(await field('回收物品代码').isDisabled(),true);
  await modal.getByText('使用 / 分隔，最多 7 组；0 为金币，正数为道具代码；不额外扣除时两项均留空。',{exact:true}).waitFor();checks.push('production catalog renders all seven recovery fields, separate reward/cost summaries and explicit gold-zero guidance');
  await field('奖励物品数量').fill('30/40');await field('回收扣除数量').fill('6');await field('额外扣除物品数量').fill('700/2');
  const request=page.waitForRequest(r=>r.url().endsWith('/recovery/validate'));await modal.getByRole('button',{name:'预览操作',exact:true}).click();const body=(await request).postDataJSON();
  await modal.getByRole('button',{name:'重读原预览',exact:true}).waitFor();assert.deepEqual(body.values,{code:27600,restitution:'3037/3340',Return_quantity:'30/40',announcement:0,codenum:6,extra_consumption_id:'0/3037',extra_consumption_num:'700/2'});assert.equal(await field('奖励物品数量').isDisabled(),true);
  await modal.getByRole('button',{name:'确认本次预览',exact:true}).click();await modal.waitFor({state:'hidden'});await page.getByText('30/40',{exact:true}).waitFor();assert.equal(writes,1);checks.push('preview freezes reward pair, gold/material cost pair, broadcast flag and original-item removal count; one confirmed synthetic write updates the row');
  await page.getByRole('button',{name:'新增配置',exact:true}).click();assert.equal(await field('回收物品代码').isDisabled(),false);await field('回收物品代码').fill('27601');
  const create=page.waitForRequest(r=>r.url().endsWith('/recovery/validate'));await modal.getByRole('button',{name:'预览操作',exact:true}).click();const proposed=(await create).postDataJSON();assert.equal(proposed.revision,'new');assert.equal(proposed.values.code,27601);assert.equal(proposed.values.restitution,'');assert.equal(proposed.values.extra_consumption_id,'');assert.equal(proposed.values.extra_consumption_num,'');assert.equal(proposed.values.codenum,1);assert.equal(writes,1);checks.push('new recovery rule has its own request ID and explicit empty reward/extra cost pairs');
  await modal.getByRole('button',{name:'关闭并保留编号',exact:true}).click();await select('装备跨界（只读）');assert.equal(await page.getByRole('button',{name:'新增配置',exact:true}).count(),0);await page.getByRole('button',{name:'查看',exact:true}).click();assert.equal(await modal.locator('input:not([disabled])').count(),0);checks.push('unverified cross-border rules remain read-only while recovery retains its own pending preview');
  assert.deepEqual(errors,[]);const result={passed:true,mockedApis:true,realDeploymentVerified:false,originalToolEquivalenceVerified:false,nativeGameplayVerified:false,syntheticWrites:writes,checks,pageErrors:errors.length};await writeFile(path.join(output,'result.json'),JSON.stringify(result,null,2));console.log(JSON.stringify(result));
 }catch(e){if(page){await writeFile(path.join(output,'failure.json'),JSON.stringify({errors,text:await page.locator('body').innerText()},null,2));await page.screenshot({path:path.join(output,'failure.png'),fullPage:true})}throw e}finally{if(browser)await browser.close();await new Promise(r=>server.close(r))}
})().catch(e=>{console.error(e);process.exitCode=1});
