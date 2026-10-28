// Real deployed read-only record page. No response fixtures or native rows are inserted.
const assert=require('node:assert/strict'),{readFile,writeFile,mkdir,stat}=require('node:fs/promises'),path=require('node:path'),{parseArgs}=require('node:util');
const {chromium}=require(process.env.PLAYWRIGHT_MODULE||'playwright');
const {values:args}=parseArgs({options:{origin:{type:'string'},credentials:{type:'string'},output:{type:'string'}}});
(async()=>{
 const origin=new URL(args.origin);assert.equal(origin.origin,'http://127.0.0.1:18080');
 assert.equal((await stat(args.credentials)).mode&0o077,0);const output=path.resolve(args.output);await mkdir(output,{recursive:false});
 let browser,page,forceInvalidField=false;const errors=[],checks=[],samples=[],recordMethods=[];
 try{
  browser=await chromium.launch({headless:true});const context=await browser.newContext({viewport:{width:1600,height:1200}});page=await context.newPage();page.on('pageerror',e=>errors.push(e.message));
  await page.route('**/*',route=>{
   const u=new URL(route.request().url());if(u.origin!==origin.origin)return route.abort();
   if(u.pathname.startsWith('/api/v1/admin/dof/records'))recordMethods.push(route.request().method());
   // One explicit invalid field tests a REAL backend 400 and the local error alert.
   if(forceInvalidField&&u.pathname==='/api/v1/admin/dof/records/transactions'){
    forceInvalidField=false;u.searchParams.set('searchField','not-a-configured-column');u.searchParams.set('searchText','invalid-field-check');return route.continue({url:u.href});
   }
   return route.continue();
  });
  const login=await context.request.post(origin.href+'api/v1/login',{data:JSON.parse(await readFile(args.credentials,'utf8'))});assert.equal(login.status(),200);
  const token=(await login.text()).replace(/^"|"$/g,'');assert.match(token,/^[0-9a-f]{32}$/);const headers={Authorization:'Bearer '+token};
  const get=async route=>{const r=await context.request.get(origin.href+route,{headers});assert.equal(r.status(),200,route);return r.json()};
  for(const route of ['records','records/transactions','records/transactions/schema'])assert.ok([401,403].includes((await context.request.get(origin.href+'api/v1/admin/dof/'+route)).status()));
  const catalog=await get('api/v1/admin/dof/records');assert.equal(catalog.instanceId,'dof-main');assert.equal(catalog.views.length,30);
  const view=catalog.views.find(v=>v.key==='transactions');assert.ok(view?.available);assert.ok(view.ownerAvailable&&view.periodAvailable&&view.searchFields.length>1);
  checks.push('real bound catalog exposes all thirty fixed record views; anonymous catalog, query and schema access are refused');
  await page.addInitScript(t=>localStorage.setItem('token',t),token);await page.goto(origin.href+'admin/dof');await page.locator('.arco-tabs-tab').filter({hasText:'记录查询'}).click();
  await page.getByRole('button',{name:'下一页',exact:true}).waitFor();
  const controls=page.locator('.controls:visible').filter({has:page.getByRole('button',{name:'核对表结构',exact:true})});
  const select=async(index,label)=>{await controls.locator('.arco-select').nth(index).click();await page.locator('.arco-select-option:visible').getByText(label,{exact:true}).click()};
  const waitQuery=()=>page.waitForResponse(r=>new URL(r.url()).pathname==='/api/v1/admin/dof/records/transactions');
  let pending=waitQuery();await select(0,view.title);assert.equal((await pending).status(),200);
  const text=page.getByPlaceholder('包含文字（可选）'),owner=page.getByPlaceholder(view.ownerLabel+'（可选）');
  const invalidated=async()=>{await page.waitForFunction(()=>![...document.querySelectorAll('button')].some(x=>x.textContent.trim()==='下一页'));assert.equal(await page.getByRole('button',{name:'下一页',exact:true}).count(),0)};
  const query=async()=>{const pending=waitQuery();await controls.getByRole('button',{name:'查询',exact:true}).click();const r=await pending;assert.equal(r.status(),200);const body=await r.json();samples.push({view:'transactions',rowCount:body.rows.length,hasMore:body.hasMore});await page.getByText('第 1 页',{exact:true}).waitFor();return new URL(r.url()).searchParams};
  await text.fill('%_!中文');await invalidated();let params=await query();assert.equal(params.get('searchField'),view.searchFields[0].key);assert.equal(params.get('searchText'),'%_!中文');assert.equal(params.get('page'),'1');
  checks.push('real selected record view supplies its default search column and preserves literal Unicode and wildcard text in the first-page request');
  await owner.fill('6');await owner.press('Tab');await invalidated();params=await query();assert.equal(params.get('owner'),'6');assert.equal(params.get('page'),'1');
  await select(1,'今天');await invalidated();params=await query();assert.equal(params.get('period'),'today');assert.equal(params.get('page'),'1');
  checks.push('owner and period edits remove prior results and pagination; explicit queries start on page one');
  await select(2,view.searchFields[1].label);await invalidated();params=await query();assert.equal(params.get('searchField'),view.searchFields[1].key);assert.equal(params.get('searchText'),'%_!中文');
  await owner.fill('');await owner.press('Tab');await invalidated();params=await query();assert.equal(params.has('owner'),false);
  await text.fill('');await invalidated();params=await query();assert.equal(params.has('searchField'),false);assert.equal(params.has('searchText'),false);
  await select(1,'全部时间');await invalidated();params=await query();assert.equal(params.get('period'),'all');
  checks.push('changing search column retains text; clearing owner, text and period removes the corresponding active conditions');
  // Empty real tables must not enable navigation to an invented second page.
  const next=page.getByRole('button',{name:'下一页',exact:true});const realEmpty=samples.every(s=>s.rowCount===0);if(realEmpty)assert.equal(await next.isDisabled(),true);
  checks.push('real pagination reflects backend hasMore; empty tables keep the next-page action disabled');
  forceInvalidField=true;pending=waitQuery();await controls.getByRole('button',{name:'查询',exact:true}).click();const bad=await pending;assert.equal(bad.status(),400);const message=(await bad.json()).message;await page.locator('.arco-alert-content').getByText(message,{exact:true}).waitFor();await invalidated();
  await query();checks.push('a deliberately invalid fixed search column receives a real backend 400, displays its error and clears stale pagination; a normal query recovers');
  const schemaPending=page.waitForResponse(r=>new URL(r.url()).pathname==='/api/v1/admin/dof/records/transactions/schema');await controls.getByRole('button',{name:'核对表结构',exact:true}).click();const schemaResponse=await schemaPending;assert.equal(schemaResponse.status(),200);assert.equal((await schemaResponse.json()).view.available,true);
  const signin=await get('api/v1/admin/dof/records/signin-users?page=1&size=1');samples.push({view:'signin-users',rowCount:signin.rows.length,hasMore:signin.hasMore});
  assert.deepEqual(errors,[]);assert.ok(recordMethods.every(m=>m==='GET'));checks.push('real structure diagnosis and signin query return 200; all browser record requests remain GET and page errors stay zero');
  const result={passed:true,mockedApis:false,realDeploymentVerified:true,invalidRequestInjectedForErrorDisplay:true,responseMocksUsed:false,checks,pageErrors:errors.length,catalogViews:catalog.views.length,samples,recordApiMethods:[...new Set(recordMethods)],populatedNativePaginationVerified:false,nativeDatabaseEncodingVerified:false,originalWindowsToolStarted:false,completeReplacement:false};
  await writeFile(path.join(output,'result.json'),JSON.stringify(result,null,2)+'\n',{mode:0o600});console.log(JSON.stringify(result));
 }catch(e){if(page){await writeFile(path.join(output,'failure.private.json'),JSON.stringify({errors,text:await page.locator('body').innerText()},null,2),{mode:0o600});await page.screenshot({path:path.join(output,'failure.private.png'),fullPage:true})}throw e}finally{if(browser)await browser.close()}
})().catch(e=>{console.error(e);process.exitCode=1});
