// Compiled frontend against explicit synthetic APIs; native persistence has separate SQL evidence.
const assert=require('node:assert/strict'),{createServer}=require('node:http'),{createHash}=require('node:crypto');
const {readFile,writeFile,mkdir}=require('node:fs/promises'),path=require('node:path'),{parseArgs}=require('node:util');
const {chromium}=require(process.env.PLAYWRIGHT_MODULE||'playwright');
const {values:args}=parseArgs({options:{dist:{type:'string'},output:{type:'string'}}});
const dist=path.resolve(args.dist),output=path.resolve(args.output),root='/api/v1/admin/dof/client/releases';
const records=new Map(),controls=new Map(),checks=[],errors=[],calls=[];let native='synthetic-original',writes=0,loseApply=true,loseRestore=true,loseCancel=true,protocolSupported=true;
const hash=x=>createHash('sha256').update(JSON.stringify(x)).digest('hex'),copy=x=>JSON.parse(JSON.stringify(x));
const bundles=['00000000-0000-4000-8000-000000000011','00000000-0000-4000-8000-000000000012'];
const canonical=b=>({protocol:b.protocol,requestId:b.requestId,revision:b.revision,bundleId:b.bundleId||null});
const view=r=>({requestId:r.requestId,state:r.state,operation:r.operation,createdAt:'2026-10-28 00:00:00',bundleId:r.request?.bundleId,targetRequestId:r.target,
 receiptRevision:hash([r.requestId,r.state]),requestReplayAvailable:r.state==='PREVIEWED'&&r.operation!=='cancel-preview',
 ...(r.state==='PREVIEWED'&&r.request?{request:r.request}:{}),...(r.state==='PREVIEWED'&&r.control?{cancelRequest:r.control}:{}),protocol:'script-release-preview-v1',instanceId:'mock-script'});
const server=createServer(async(req,res)=>{try{let f=path.resolve(dist,'.'+decodeURIComponent(new URL(req.url,'http://localhost').pathname));if(!f.startsWith(dist+path.sep))return res.writeHead(403).end();let bytes;try{bytes=await readFile(f)}catch{f=path.join(dist,'index.html');bytes=await readFile(f)}res.writeHead(200,{'Content-Type':({'.js':'text/javascript','.css':'text/css','.html':'text/html'})[path.extname(f)]||'application/octet-stream'}).end(bytes)}catch{res.writeHead(500).end()}});
(async()=>{
 await mkdir(output,{recursive:true});await new Promise(resolve=>server.listen(0,'127.0.0.1',resolve));const origin=`http://127.0.0.1:${server.address().port}`;let browser,page;
 try{
  browser=await chromium.launch({headless:true});page=await browser.newPage({viewport:{width:1700,height:1300}});page.on('pageerror',e=>errors.push(e.message));await page.addInitScript(()=>localStorage.setItem('token','mock-script-admin'));
  await page.route('**/*',async route=>{
   const req=route.request(),u=new URL(req.url());if(u.origin!==origin)return route.abort();if(!u.pathname.startsWith('/api/'))return route.continue();const reply=(data,status=200)=>route.fulfill({status,contentType:'application/json',body:JSON.stringify(data)});const method=req.method();calls.push({method,path:u.pathname});
   if(u.pathname==='/api/v1/admin/launcher/runtime')return reply({mode:'dof',nativeDevelopment:true});
   const p=u.pathname.replace('/api/v1/admin/dof','');
   if(p==='')return reply({instanceId:'mock-script',scope:{baselineClasses:71,baselineOperations:239,discoveredOperations:239,nativeMenus:[],groups:[]},executionPlan:{packages:[]}});
   if(p==='/ui'||p==='/home')return reply({message:'synthetic incompatible UI; publication remains independent'},409);
   if(p==='/client')return reply({enabled:true,endpoint:'127.0.0.1',clientFormatVersion:2,revision:hash('inputs')});
   if(p==='/script')return reply({enabled:false,blocker:'synthetic browser test; no actual PVF generation'});
   if(p==='/client/tasks')return reply(bundles.map((id,i)=>({requestId:id,state:'READY_FOR_TEST',scriptTaskId:'synthetic-script-'+i,clientFormatVersion:2,createdAt:'2026-10-28',sha256:hash(id),bytes:100,endpoint:'127.0.0.1',...(i===0?{manifestVersion:1,manifestSha256:hash('manifest'),sourceBinding:{databaseUuid:'00000000-0000-4000-8000-000000000099',readerRecipe:'dof-launcher-reader-2.2'}}:{})})));
   if(u.pathname===root+'/status')return reply({instanceId:'mock-script',revision:hash(native),parameterCopiesEqual:true,sourceConfigured:true,manualModeCompatible:true,protocol:protocolSupported?'script-release-preview-v1':undefined});
   if(u.pathname===root&&method==='GET')return reply([...records.values(),...controls.values()].reverse().map(view));
   if(u.pathname===root+'/preview'||/\/restore\/preview$/.test(u.pathname)){
    const b=canonical(req.postDataJSON());assert.equal(b.protocol,'script-release-preview-v1');if(records.has(b.requestId)){assert.deepEqual(b,records.get(b.requestId).request);return reply(view(records.get(b.requestId)))}
    assert.equal(b.revision,hash(native));const target=u.pathname===root+'/preview'?null:u.pathname.slice(root.length+1).split('/')[0];
    const r={requestId:b.requestId,request:b,state:'PREVIEWED',operation:target?'restore':'apply',target,before:native,after:target?records.get(target).before:'synthetic-new'};records.set(b.requestId,r);return reply(view(r));
   }
   if(u.pathname===root&&method==='POST'||/\/restore$/.test(u.pathname)){
    const b=canonical(req.postDataJSON()),r=records.get(b.requestId);assert.ok(r);assert.deepEqual(b,r.request);
    if(r.state==='PREVIEWED'){assert.equal(b.revision,hash(native));native=r.after;writes++;r.state='PERSISTED';if(r.target)records.get(r.target).state='RESTORED'}
    if(r.operation==='apply'&&loseApply){loseApply=false;return route.abort()}
    if(r.operation==='restore'&&loseRestore){loseRestore=false;return route.abort()}
    return reply(view(r));
   }
   const control=u.pathname.match(/\/releases\/([^/]+)\/cancellations(\/preview)?$/);
   if(control){const source=records.get(control[1]),b=req.postDataJSON();assert.ok(source);
    if(control[2]){assert.equal(b.revision,view(source).receiptRevision);const c={requestId:b.requestId,operation:'cancel-preview',target:source.requestId,state:'PREVIEWED',control:b};controls.set(c.requestId,c);return reply(view(c))}
    const c=controls.get(b.requestId);assert.ok(c);assert.deepEqual(b,c.control);assert.equal(source.state,'PREVIEWED');source.state='NOT_APPLIED';c.state='PERSISTED';if(loseCancel){loseCancel=false;return route.abort()}return reply(view(c));
   }
   if(u.pathname.startsWith(root+'/')){const id=u.pathname.slice(root.length+1).split('/')[0],r=records.get(id)||controls.get(id);return r?reply(view(r)):reply({message:'synthetic missing receipt'},404)}
   return reply([]);
  });
  async function open(){await page.goto(origin+'/admin/dof');await page.getByText('客户端配置与背景',{exact:true}).click();await page.getByRole('button',{name:'刷新配置与历史',exact:true}).waitFor()}
  async function select(i=0){await page.locator('tr').filter({hasText:bundles[i]}).filter({has:page.getByRole('button',{name:'选择并配置隔离参数',exact:true})}).getByRole('button',{name:'选择并配置隔离参数',exact:true}).click()}
  async function preview(){await select();await page.getByRole('button',{name:'核对配套包并预览应用',exact:true}).click();await page.getByRole('button',{name:'应用此配套包参数',exact:true}).waitFor();return [...records.values()].findLast(r=>r.state==='PREVIEWED'&&r.operation==='apply')}
  const stored=()=>page.evaluate(()=>sessionStorage.getItem('script.release.pending.v1'));
  await open();
  const newRow=page.locator('tr').filter({hasText:bundles[0]}),legacyRow=page.locator('tr').filter({hasText:bundles[1]});
  await newRow.getByText('数据库 UUID：00000000-0000-4000-8000-000000000099',{exact:true}).waitFor();
  await newRow.getByText('材料哈希已绑定；组件读取和游戏效果待验。',{exact:true}).waitFor();
  await legacyRow.getByText('历史任务：未记录完整来源清单，保留原有产物校验。',{exact:true}).waitFor();
  assert.ok(!(await legacyRow.textContent()).includes('材料哈希已绑定'));
  checks.push('new provenance displays database and reader recipe; legacy archives remain explicitly unverified');
  const first=await preview();assert.ok(first);const state=await stored();assert.ok(state.includes(first.requestId));assert.ok(!state.includes('bundleId')&&!state.includes('revision'));assert.equal(writes,0);
  await select(1);await page.reload();await page.getByText('客户端配置与背景',{exact:true}).click();await page.getByRole('button',{name:'应用此配套包参数',exact:true}).waitFor();assert.equal(records.size,1);checks.push('durable preview restored after reload; changed parent bundle cannot replace frozen request; storage contains identifiers only');
  await page.getByRole('button',{name:'应用此配套包参数',exact:true}).click();await page.locator('.arco-alert').filter({hasText:/Network Error|连接失败/}).first().waitFor();assert.equal(writes,1);assert.ok((await stored()).includes(first.requestId));checks.push('lost apply reply retains original operation ID after exactly one synthetic write');
  await page.reload();await page.getByText('客户端配置与背景',{exact:true}).click();await page.getByRole('button',{name:'核对配套包并预览应用',exact:true}).waitFor();await page.waitForFunction(()=>sessionStorage.getItem('script.release.pending.v1')===null);assert.equal(writes,1);checks.push('refresh reads persisted receipt and clears pending identity without submitting again');
  await page.locator('tr').filter({hasText:first.requestId}).getByRole('button',{name:'准备定向恢复',exact:true}).click();await page.getByRole('button',{name:'执行定向恢复',exact:true}).waitFor();const restore=[...records.values()].find(r=>r.operation==='restore');assert.ok(restore&&restore.requestId!==first.requestId);assert.equal(writes,1);
  await page.reload();await page.getByText('客户端配置与背景',{exact:true}).click();await page.getByRole('button',{name:'执行定向恢复',exact:true}).click();await page.locator('.arco-alert').filter({hasText:/Network Error|连接失败/}).first().waitFor();assert.equal(native,'synthetic-original');assert.equal(writes,2);
  await page.reload();await page.getByText('客户端配置与背景',{exact:true}).click();await page.waitForFunction(()=>sessionStorage.getItem('script.release.pending.v1')===null);assert.equal(writes,2);checks.push('restoration has a separate durable preview and ID; reload and lost restore reply preserve exact target without a second write');
  const cancelled=await preview();await page.getByRole('button',{name:'取消本次预览',exact:true}).click();await page.locator('.arco-alert').filter({hasText:/Network Error|连接失败/}).first().waitFor();const c=[...controls.values()][0];assert.ok(c&&c.requestId!==cancelled.requestId);assert.ok((await stored()).includes(c.requestId));assert.equal(writes,2);
  await page.reload();await page.getByText('客户端配置与背景',{exact:true}).click();await page.waitForFunction(()=>sessionStorage.getItem('script.release.pending.v1')===null);checks.push('cancel uses separate preview and persisted control; reply loss is reconciled from original IDs');
  const late=await page.evaluate(async({root,body})=>fetch(root,{method:'POST',headers:{'Content-Type':'application/json'},body:JSON.stringify(body)}).then(r=>r.json()),{root,body:cancelled.request});assert.equal(late.state,'NOT_APPLIED');assert.equal(writes,2);checks.push('late cancelled submission remains fenced in explicit synthetic protocol fixture');
  protocolSupported=false;await open();await select();assert.equal(await page.getByRole('button',{name:'核对配套包并预览应用',exact:true}).isDisabled(),true);checks.push('mismatched server protocol disables new publication writes before request');
  assert.deepEqual(errors,[]);await writeFile(path.join(output,'result.json'),JSON.stringify({passed:true,mockedApis:true,realDeploymentVerified:false,nativeSqlVerified:false,originalToolEquivalenceVerified:false,checks,syntheticNativeWrites:writes,pageErrors:errors.length},null,2));
 }catch(e){if(page)await page.screenshot({path:path.join(output,'failure.png'),fullPage:true});throw e}finally{if(browser)await browser.close();await new Promise(resolve=>server.close(resolve))}
})().catch(e=>{console.error(e);process.exitCode=1});
