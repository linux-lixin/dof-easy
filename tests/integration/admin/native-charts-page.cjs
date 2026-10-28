// Compiled UI with explicitly synthetic APIs; SQL/native acceptance is separate.
const assert=require('node:assert/strict'),{createServer}=require('node:http'),{createHash}=require('node:crypto');
const {readFile,mkdir,writeFile}=require('node:fs/promises'),path=require('node:path'),{parseArgs}=require('node:util');
const {chromium}=require(process.env.PLAYWRIGHT_MODULE||'playwright');
const {values:args}=parseArgs({options:{dist:{type:'string'},catalog:{type:'string'},output:{type:'string'}}});
const dist=path.resolve(args.dist),output=path.resolve(args.output),checks=[],errors=[],requests=[];
const hash=value=>createHash('sha256').update(JSON.stringify(value)).digest('hex');
let values={id:1,min_zhanli:100,jiangli_day:1,jiangli_hour:3,is_mobai:0,status:0,next_time:'2026-11-01 03:00:00'},writes=0,active=false,failedRead=false,preview;
const server=createServer(async(req,res)=>{try{let f=path.resolve(dist,'.'+decodeURIComponent(new URL(req.url,'http://localhost').pathname));if(!f.startsWith(dist+path.sep))return res.writeHead(403).end();let bytes;try{bytes=await readFile(f)}catch{f=path.join(dist,'index.html');bytes=await readFile(f)}res.writeHead(200,{'Content-Type':({'.js':'text/javascript','.css':'text/css','.html':'text/html'})[path.extname(f)]||'application/octet-stream'}).end(bytes)}catch{res.writeHead(500).end()}});
(async()=>{
 const catalog=JSON.parse(await readFile(args.catalog,'utf8')).filter(s=>['signin','charts'].includes(s.key));
 await mkdir(output,{recursive:true});await new Promise(resolve=>server.listen(0,'127.0.0.1',resolve));const origin=`http://127.0.0.1:${server.address().port}`;let browser,page;
 try{
  browser=await chromium.launch({headless:true});page=await browser.newPage({viewport:{width:1600,height:1200}});page.on('pageerror',e=>errors.push(e.message));await page.addInitScript(()=>localStorage.setItem('token','synthetic-charts-admin'));
  await page.route('**/*',async route=>{
   const req=route.request(),u=new URL(req.url());if(u.origin!==origin)return route.abort();if(!u.pathname.startsWith('/api/'))return route.continue();const reply=(data,status=200)=>route.fulfill({status,contentType:'application/json',body:JSON.stringify(data)});
   if(u.pathname==='/api/v1/admin/launcher/runtime')return reply({mode:'dof',instanceId:'synthetic-charts'});
   if(u.pathname==='/api/v1/admin/activities')return reply(catalog);
   if(u.pathname==='/api/v1/admin/activities/charts'&&req.method()==='GET')return failedRead?reply({message:'模拟：排行榜规则表结构不匹配'},409):reply({rows:[{values,revision:hash(values)}],moduleNotice:catalog.find(s=>s.key==='charts').notice});
   if(u.pathname==='/api/v1/admin/activities/charts/validate'){
    const body=req.postDataJSON();requests.push(body);if(active)return reply({message:'模拟：排行榜或膜拜入口仍启用，拒绝改写'},409);
    assert.equal(body.revision,hash(values));preview=body;return reply({requestId:body.requestId,state:'PREVIEWED',revision:body.revision,changes:[{label:'最低上榜战力',before:100,after:12345}]});
   }
   if(u.pathname==='/api/v1/admin/activities/charts'&&req.method()==='PUT'){
    assert.deepEqual(req.postDataJSON(),preview);values=JSON.parse(JSON.stringify(preview.values));writes++;return reply({requestId:preview.requestId,state:'PERSISTED',persisted:true});
   }
   return reply({rows:[]});
  });
  await page.goto(origin+'/admin/activities');await page.locator('.arco-select').first().click();await page.locator('.arco-select-option').getByText('原生战力排行榜规则',{exact:true}).click();
  await page.getByRole('button',{name:'编辑',exact:true}).waitFor();assert.equal(await page.getByRole('button',{name:'新增配置',exact:true}).count(),0);assert.equal(await page.getByRole('button',{name:'删除',exact:true}).count(),0);checks.push('existing-only ranking module exposes edit but no create or delete');
  await page.getByRole('button',{name:'编辑',exact:true}).click();const modal=page.locator('.arco-modal:visible'),input=label=>modal.locator('.arco-form-item').filter({has:page.locator('.arco-form-item-label').getByText(label,{exact:true})}).locator('input');
  for(const label of ['原生规则ID（只读）','膜拜开关（原生保留）','排行榜开关（原生保留）','下次结算时间（原生维护）'])assert.equal(await input(label).isDisabled(),true);
  assert.equal(await modal.getByRole('button',{name:'确认本次预览',exact:true}).isDisabled(),true);checks.push('identity, independent entry flags and native deadline remain disabled; submission requires preview');
  await input('最低上榜战力').fill('12345');await input('奖励间隔（天）').fill('2');await input('奖励小时（0～23）').fill('4');await modal.getByRole('button',{name:'预览操作',exact:true}).click();await modal.getByRole('button',{name:'确认本次预览',exact:true}).click();await modal.waitFor({state:'hidden'});
  assert.equal(writes,1);assert.deepEqual(values,{id:1,min_zhanli:12345,jiangli_day:2,jiangli_hour:4,is_mobai:0,status:0,next_time:'2026-11-01 03:00:00'});assert.match(preview.requestId,/^[0-9a-f-]{36}$/);checks.push('one numbered preview and commit change only threshold/interval/hour and retain original native-owned fields');
  active=true;await page.getByRole('button',{name:'编辑',exact:true}).click();await input('最低上榜战力').fill('12346');await modal.getByRole('button',{name:'预览操作',exact:true}).click();await modal.getByText('模拟：排行榜或膜拜入口仍启用，拒绝改写',{exact:true}).waitFor();assert.equal(writes,1);assert.equal(await modal.getByRole('button',{name:'确认本次预览',exact:true}).isDisabled(),true);checks.push('native entry guard failure is visible and cannot proceed to commit');
  await modal.getByRole('button',{name:'关闭并保留编号',exact:true}).click();failedRead=true;await page.getByRole('button',{name:'刷新',exact:true}).click();await page.locator('.arco-alert-content').getByText('模拟：排行榜规则表结构不匹配',{exact:true}).waitFor();assert.deepEqual(errors,[]);checks.push('schema failure stays visible instead of becoming an empty successful list');
  const result={passed:true,mockedApis:true,realDeploymentVerified:false,nativeGameplayVerified:false,nativeWritesInMock:writes,checks,pageErrors:errors.length};await writeFile(path.join(output,'result.json'),JSON.stringify(result,null,2));console.log(JSON.stringify(result));
 }catch(e){if(page){await writeFile(path.join(output,'failure.json'),JSON.stringify({errors,text:await page.locator('body').innerText()},null,2));await page.screenshot({path:path.join(output,'failure.png'),fullPage:true})}throw e}finally{if(browser)await browser.close();await new Promise(resolve=>server.close(resolve))}
})().catch(e=>{console.error(e);process.exitCode=1});
