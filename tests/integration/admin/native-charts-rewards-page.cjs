// All APIs below are synthetic. Real SQL, real deployment and gameplay are separate gates.
const assert=require('node:assert/strict'),{createServer}=require('node:http'),{createHash}=require('node:crypto');
const {readFile,mkdir,writeFile}=require('node:fs/promises'),path=require('node:path'),{parseArgs}=require('node:util');
const {chromium}=require(process.env.PLAYWRIGHT_MODULE||'playwright');
const {values:args}=parseArgs({options:{dist:{type:'string'},catalog:{type:'string'},output:{type:'string'}}});
const dist=path.resolve(args.dist),output=path.resolve(args.output),checks=[],errors=[],records=new Map(),base='/api/v1/admin/activities/charts-rewards';
const hash=value=>createHash('sha256').update(JSON.stringify(value)).digest('hex'),copy=value=>JSON.parse(JSON.stringify(value));
let values={top_index:0,code_data:'3037',code_num:'1'},writes=0,conflict=false,last;
const server=createServer(async(req,res)=>{try{let file=path.resolve(dist,'.'+decodeURIComponent(new URL(req.url,'http://localhost').pathname));if(!file.startsWith(dist+path.sep))return res.writeHead(403).end();let bytes;try{bytes=await readFile(file)}catch{file=path.join(dist,'index.html');bytes=await readFile(file)}res.writeHead(200,{'Content-Type':({'.js':'text/javascript','.css':'text/css','.html':'text/html'})[path.extname(file)]||'application/octet-stream'}).end(bytes)}catch{res.writeHead(500).end()}});
(async()=>{
 const catalog=JSON.parse(await readFile(args.catalog,'utf8')).filter(section=>['signin','charts-rewards'].includes(section.key)),section=catalog.find(section=>section.key==='charts-rewards');
 await mkdir(output,{recursive:true});await new Promise(resolve=>server.listen(0,'127.0.0.1',resolve));const origin=`http://127.0.0.1:${server.address().port}`;let browser,page;
 try{
  browser=await chromium.launch({headless:true});page=await browser.newPage({viewport:{width:1600,height:1200}});page.on('pageerror',e=>errors.push(e.message));await page.addInitScript(()=>localStorage.setItem('token','synthetic-rank-rewards-admin'));
  await page.route('**/*',async route=>{
   const req=route.request(),url=new URL(req.url());if(url.origin!==origin)return route.abort();if(!url.pathname.startsWith('/api/'))return route.continue();const reply=(data,status=200)=>route.fulfill({status,contentType:'application/json',body:JSON.stringify(data)});
   if(url.pathname==='/api/v1/admin/launcher/runtime')return reply({mode:'dof',instanceId:'synthetic-rank-rewards'});
   if(url.pathname==='/api/v1/admin/activities')return reply(catalog);
   if(url.pathname===base&&req.method()==='GET')return reply({rows:values?[{values,revision:hash(values)}]:[],moduleNotice:section.notice});
   if(url.pathname===base+'/history')return reply([...records.values()].reverse().map(r=>({...r,id:r.requestId,operation_name:r.request.operation,before_json:JSON.stringify(r.before),after_json:JSON.stringify(r.after),legacy:false})));
   if(url.pathname===base+'/validate'){
    const body=req.postDataJSON();if(conflict&&body.operation==='restore')return reply({message:'模拟：排行奖励代码或数量已有其他修改，停止成组恢复'},409);
    assert.equal(body.revision,values?hash(values):'new');let after;
    if(body.operation==='restore'){assert.equal('values' in body,false);assert.ok(records.has(body.targetRequestId));after=copy(records.get(body.targetRequestId).before)}
    else after=body.operation==='delete'?null:copy(body.values);
    last={requestId:body.requestId,request:copy(body),before:copy(values),after,state:'PREVIEWED',actor:1,changes:[]};records.set(body.requestId,last);return reply(last);
   }
   if((url.pathname===base&&['PUT','DELETE'].includes(req.method()))||url.pathname.match(/\/history\/[^/]+\/restore$/)){
    const body=req.postDataJSON(),record=records.get(body.requestId);assert.ok(record);assert.deepEqual(body,record.request);values=copy(record.after);record.state='PERSISTED';writes++;return reply({...record,persisted:true});
   }
   if(url.pathname.match(/\/history\/[^/]+\/current$/))return reply({values:copy(values),revision:values?hash(values):'new'});
   return reply({rows:[]});
  });
  await page.goto(origin+'/admin/activities');await page.locator('.arco-select').first().click();await page.locator('.arco-select-option').getByText(section.title,{exact:true}).click();await page.getByRole('button',{name:'编辑',exact:true}).waitFor();
  assert.equal(await page.getByRole('button',{name:'新增配置',exact:true}).isEnabled(),true);assert.equal(await page.getByRole('button',{name:'删除',exact:true}).isEnabled(),true);checks.push('fixed native ranks expose reversible configuration create and delete, including worship rank zero');
  await page.getByRole('button',{name:'编辑',exact:true}).click();const modal=page.locator('.arco-modal:visible'),input=key=>modal.locator('.arco-form-item').filter({has:page.locator('.arco-form-item-label').getByText(section.fields.find(f=>f.name===key).label,{exact:true})}).locator('input');
  const ids=Array(50).fill('2147483647').join(','),counts=Array(50).fill('2147483646').join(',');assert.ok(ids.length>255&&counts.length>255);
  await input('code_data').fill(ids);await input('code_num').fill(counts);assert.equal(await input('code_data').inputValue(),ids);assert.equal(await input('code_num').inputValue(),counts);assert.equal(await input('top_index').isDisabled(),true);
  await modal.getByRole('button',{name:'预览操作',exact:true}).click();await modal.getByRole('button',{name:'确认本次预览',exact:true}).click();await modal.waitFor({state:'hidden'});assert.deepEqual(values,{top_index:0,code_data:ids,code_num:counts});assert.equal(writes,1);const modified=last.requestId;checks.push('both 549-character native CSV fields survive form editing, exact numbered preview and submission without 255-character truncation');
  await page.getByRole('button',{name:'删除',exact:true}).click();await modal.getByRole('button',{name:'预览操作',exact:true}).click();await modal.getByRole('button',{name:'确认本次预览',exact:true}).click();await modal.waitFor({state:'hidden'});const deleted=last.requestId;assert.equal(values,null);assert.equal(writes,2);checks.push('deletion uses its own preview and operation ID without a hidden upsert');
  await page.getByRole('button',{name:'修改记录与快照',exact:true}).click();const historyRow=page.locator('.arco-modal:visible .arco-table-tr').filter({hasText:deleted});await historyRow.getByRole('button',{name:'预览恢复',exact:true}).click();
  await modal.getByRole('button',{name:'预览操作',exact:true}).click();assert.equal(last.request.targetRequestId,deleted);assert.notEqual(last.requestId,deleted);assert.equal('values' in last.request,false);await modal.getByRole('button',{name:'确认本次预览',exact:true}).click();await modal.waitFor({state:'hidden'});assert.deepEqual(values,{top_index:0,code_data:ids,code_num:counts});assert.equal(writes,3);checks.push('history restores the deleted long pair with a fresh linked ID and server-owned snapshots');
  conflict=true;values.code_num=Array(50).fill('3').join(',');await page.getByRole('button',{name:'修改记录与快照',exact:true}).click();await page.locator('.arco-modal:visible .arco-table-tr').filter({hasText:modified}).getByRole('button',{name:'预览恢复',exact:true}).click();await modal.getByRole('button',{name:'预览操作',exact:true}).click();await modal.locator('.arco-alert-content').getByText('模拟：排行奖励代码或数量已有其他修改，停止成组恢复',{exact:true}).waitFor();assert.equal(await modal.getByRole('button',{name:'确认本次预览',exact:true}).isDisabled(),true);assert.equal(writes,3);assert.deepEqual(errors,[]);checks.push('a paired restore conflict is visible and never submits a replacement write');
  const result={passed:true,mockedApis:true,realDeploymentVerified:false,nativeGameplayVerified:false,nativeWritesInMock:writes,externalConfigChangesInMock:1,checks,pageErrors:errors.length};await writeFile(path.join(output,'result.json'),JSON.stringify(result,null,2));console.log(JSON.stringify(result));
 }catch(e){if(page){await writeFile(path.join(output,'failure.json'),JSON.stringify({errors,text:await page.locator('body').innerText()},null,2));await page.screenshot({path:path.join(output,'failure.png'),fullPage:true})}throw e}finally{if(browser)await browser.close();await new Promise(resolve=>server.close(resolve))}
})().catch(e=>{console.error(e);process.exitCode=1});
