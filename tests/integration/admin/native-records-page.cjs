// Compiled Vue UI with synthetic APIs; no player database or native acceptance.
const assert=require('node:assert/strict'),{createServer}=require('node:http');
const {readFile,writeFile,mkdir}=require('node:fs/promises'),path=require('node:path'),{parseArgs}=require('node:util');
const {chromium}=require(process.env.PLAYWRIGHT_MODULE||'playwright');
const {values:args}=parseArgs({options:{dist:{type:'string'},output:{type:'string'}}});
const dist=path.resolve(args.dist),output=path.resolve(args.output),calls=[],errors=[],checks=[];
let failRead=false;
const columns=[{key:'ID',label:'记录编号'},{key:'accname',label:'账号名称'}];
const view={key:'transactions',title:'模拟交易记录',available:true,ownerAvailable:true,ownerLabel:'账号编号',periodAvailable:true,searchFields:[{key:'accname',label:'账号名称'},{key:'MING',label:'记录说明'}],columns};
const server=createServer(async(req,res)=>{try{
 let file=path.resolve(dist,'.'+decodeURIComponent(new URL(req.url,'http://localhost').pathname));
 if(!file.startsWith(dist+path.sep))return res.writeHead(403).end();
 let data;try{data=await readFile(file)}catch{file=path.join(dist,'index.html');data=await readFile(file)}
 res.writeHead(200,{'Content-Type':({'.js':'text/javascript','.css':'text/css','.html':'text/html'})[path.extname(file)]||'application/octet-stream'}).end(data);
}catch{res.writeHead(500).end()}});
(async()=>{
 await mkdir(output,{recursive:false});await new Promise(resolve=>server.listen(0,'127.0.0.1',resolve));
 const origin=`http://127.0.0.1:${server.address().port}`;let browser,page;
 try{
  browser=await chromium.launch({headless:true});page=await browser.newPage({viewport:{width:1600,height:1200}});
  page.on('pageerror',error=>errors.push(error.message));await page.addInitScript(()=>localStorage.setItem('token','synthetic-records-admin'));
  await page.route('**/*',async route=>{
   const request=route.request(),u=new URL(request.url());if(u.origin!==origin)return route.abort();if(!u.pathname.startsWith('/api/'))return route.continue();
   calls.push({method:request.method(),path:u.pathname,query:Object.fromEntries(u.searchParams)});
   const reply=(body,status=200)=>route.fulfill({status,contentType:'application/json',body:JSON.stringify(body)});
   if(u.pathname==='/api/v1/admin/launcher/runtime')return reply({mode:'dof',instanceId:'synthetic-records'});
   const key=u.pathname.replace('/api/v1/admin/dof','');
   if(key==='')return reply({scope:{baselineClasses:71,baselineOperations:239,groups:[],nativeMenus:[]},executionPlan:{packages:[]}});
   if(key==='/ui')return reply({message:'模拟：UI配置不可用，记录模块独立'},409);
   if(key==='/ui/history')return reply([]);
   if(key==='/records')return reply({views:[view,{key:'missing',title:'模拟缺失表',available:false,reason:'模拟：缺少记录字段',searchFields:[]}]});
   if(key==='/records/transactions/schema')return reply({view:{...view,database:'dof_native',table:'transaction'},fields:[{key:'ID',present:true,dataType:'int'}],notice:'模拟只读诊断'});
   if(key==='/records/transactions'){
    assert.equal(request.method(),'GET');if(failRead)return reply({message:'模拟：数据库中断'},503);
    const id=`fixture-${u.searchParams.get('owner')||'all'}-${u.searchParams.get('page')}`;
    return reply({rows:[{ID:id,accname:'synthetic player'}],hasMore:true,view,observedAt:'2026-10-28T00:00:00Z',notice:'模拟记录响应'});
   }
   return reply({message:'Unexpected mocked API'},404);
  });
  await page.goto(origin+'/admin/dof');await page.locator('.arco-tabs-tab').filter({hasText:'记录查询'}).click();
  await page.getByText('fixture-all-1',{exact:true}).waitFor();
  await page.getByRole('button',{name:'下一页',exact:true}).click();await page.getByText('fixture-all-2',{exact:true}).waitFor();
  const owner=page.getByPlaceholder('账号编号（可选）'),text=page.getByPlaceholder('包含文字（可选）');
  const select=async(index,label)=>{await page.locator('.controls .arco-select').nth(index).click();await page.locator('.arco-select-option').getByText(label,{exact:true}).click()};
  const empty=async()=>{await page.waitForFunction(()=>!document.body.innerText.includes('fixture-'),{},{timeout:5000});assert.equal(await page.getByRole('button',{name:'下一页',exact:true}).count(),0)};
  const query=async()=>{const pending=page.waitForResponse(r=>new URL(r.url()).pathname.endsWith('/records/transactions'));await page.getByRole('button',{name:'查询',exact:true}).click();const response=await pending;assert.equal(response.status(),200);const u=new URL(response.url());await page.getByText(`fixture-${u.searchParams.get('owner')||'all'}-${u.searchParams.get('page')}`,{exact:true}).waitFor();return u.searchParams};
  await owner.fill('42');await owner.press('Tab');await empty();let params=await query();assert.equal(params.get('page'),'1');assert.equal(params.get('owner'),'42');checks.push('editing owner on page two hides old rows and pagination; next explicit query starts on page one');
  await page.getByRole('button',{name:'下一页',exact:true}).click();await page.getByText('fixture-42-2',{exact:true}).waitFor();
  await select(1,'今天');await empty();params=await query();assert.equal(params.get('page'),'1');assert.equal(params.get('period'),'today');checks.push('period changes invalidate the previous result and its page offset');
  await text.fill('%_!中文');await empty();params=await query();assert.equal(params.get('page'),'1');assert.equal(params.get('searchField'),'accname');assert.equal(params.get('searchText'),'%_!中文');checks.push('initially selected searchable view supplies its default text field and sends literal search text unchanged');
  await select(2,'记录说明');await empty();params=await query();assert.equal(params.get('searchField'),'MING');assert.equal(params.get('searchText'),'%_!中文');checks.push('changing search column invalidates rows while retaining the entered search text');
  await owner.fill('');await owner.press('Tab');await empty();params=await query();assert.equal(params.get('page'),'1');assert.equal(params.has('owner'),false);
  await text.fill('');await empty();params=await query();assert.equal(params.has('searchField'),false);assert.equal(params.has('searchText'),false);checks.push('clearing owner or text returns to page one and omits cleared API filters');
  failRead=true;const failure=page.waitForResponse(r=>new URL(r.url()).pathname.endsWith('/records/transactions'));await page.getByRole('button',{name:'查询',exact:true}).click();assert.equal((await failure).status(),503);await page.locator('.arco-alert-content').getByText('模拟：数据库中断',{exact:true}).waitFor();assert.equal(await page.getByRole('button',{name:'下一页',exact:true}).count(),0);checks.push('query failure remains visible and cannot leave old rows or a stale next-page action');
  await select(0,'模拟缺失表（结构不匹配）');await page.locator('.arco-alert-content').getByText('模拟：缺少记录字段',{exact:true}).waitFor();assert.equal(calls.some(c=>c.path.endsWith('/records/missing')),false);checks.push('missing record schema remains explicit without querying or initializing it');
  assert.deepEqual(errors,[]);assert.ok(calls.every(c=>c.method==='GET'));
  const result={passed:true,mockedApis:true,realDeploymentVerified:false,nativeRecordsOrEncodingVerified:false,checks,pageErrors:errors.length,apiMethods:[...new Set(calls.map(c=>c.method))],completeReplacement:false};
  await writeFile(path.join(output,'result.json'),JSON.stringify(result,null,2)+'\n');console.log(JSON.stringify(result));
 }catch(error){if(page){await writeFile(path.join(output,'failure.json'),JSON.stringify({errors,text:await page.locator('body').innerText()},null,2));await page.screenshot({path:path.join(output,'failure.png'),fullPage:true})}throw error}
 finally{if(browser)await browser.close();await new Promise(resolve=>server.close(resolve))}
})().catch(error=>{console.error(error);process.exitCode=1});
