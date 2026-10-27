// Compiled UI; every API below is an explicitly synthetic fixture.
const assert=require('node:assert/strict'),{createServer}=require('node:http'),{createHash}=require('node:crypto');
const {readFile,mkdir,writeFile}=require('node:fs/promises'),path=require('node:path'),{parseArgs}=require('node:util');
const {chromium}=require(process.env.PLAYWRIGHT_MODULE||'playwright');
const {values:args}=parseArgs({options:{dist:{type:'string'},catalog:{type:'string'},output:{type:'string'}}});
const dist=path.resolve(args.dist),output=path.resolve(args.output),base='/api/v1/admin/activities';
const checks=[],errors=[],queries=[];let writes=0,failCheck=false,slowExport=false,uploaded;
const rows=Array.from({length:1005},(_,code)=>({code,restitution:'3037',Return_quantity:'01',differ_lv:5}));
const fields=['code','restitution','Return_quantity','differ_lv'];
const header={format:'dof-equipment-rules-v1',schema:1,section:'inheritance',instanceId:'synthetic-instance',databaseUuid:'synthetic-uuid',fields};
const digest=createHash('sha256');for(const row of [header,...rows])digest.update(JSON.stringify(row)+'\n');
const dataset={...header,rows,rowCount:rows.length,revision:digest.digest('hex')},file=Buffer.from(JSON.stringify(dataset));
const server=createServer(async(req,res)=>{try{let f=path.resolve(dist,'.'+decodeURIComponent(new URL(req.url,'http://localhost').pathname));if(!f.startsWith(dist+path.sep))return res.writeHead(403).end();let bytes;try{bytes=await readFile(f)}catch{f=path.join(dist,'index.html');bytes=await readFile(f)}res.writeHead(200,{'Content-Type':({'.js':'text/javascript','.css':'text/css','.html':'text/html'})[path.extname(f)]||'application/octet-stream'}).end(bytes)}catch{res.writeHead(500).end()}});
(async()=>{
 const catalog=JSON.parse(await readFile(args.catalog,'utf8')).filter(s=>['signin','inheritance','cross-border'].includes(s.key));
 await mkdir(output,{recursive:true});await new Promise(r=>server.listen(0,'127.0.0.1',r));const origin=`http://127.0.0.1:${server.address().port}`;let browser,page;
 try{
  browser=await chromium.launch({headless:true});page=await browser.newPage({viewport:{width:1700,height:1400}});page.on('pageerror',e=>errors.push(e.message));await page.addInitScript(()=>localStorage.setItem('token','synthetic-dataset-admin'));
  await page.route('**/*',async route=>{
   const req=route.request(),u=new URL(req.url());if(u.origin!==origin)return route.abort();if(!u.pathname.startsWith('/api/'))return route.continue();const reply=(data,status=200)=>route.fulfill({status,contentType:'application/json',body:JSON.stringify(data)});
   if(u.pathname==='/api/v1/admin/launcher/runtime')return reply({mode:'dof'});if(u.pathname===base)return reply(catalog);
   if(u.pathname===base+'/inheritance/dataset/export'){if(slowExport)return;return route.fulfill({status:200,contentType:'application/json',body:file});}
   if(u.pathname===base+'/inheritance/dataset/check'){uploaded=req.postDataBuffer();assert.deepEqual(uploaded,file);if(failCheck)return reply({message:'规则文件实例或数据库UUID不匹配'},409);return reply({...header,rowCount:1005,revision:dataset.revision,integrityVerified:true,nativeWrites:false,writePlanCreated:false});}
   if(req.method()!=='GET'){writes++;return reply({message:'synthetic fixture refuses writes'},405)}
   if(u.pathname===base+'/inheritance'){const p=Number(u.searchParams.get('page')),n=Number(u.searchParams.get('pageSize'));queries.push([p,n]);return reply({rows:rows.slice((p-1)*n,p*n).map(values=>({values,revision:'synthetic-'+values.code})),total:1005,page:p,pageSize:n,writable:true});}
   return reply({rows:[]});
  });
  await page.goto(origin+'/admin/activities');await page.locator('.arco-select').first().click();await page.locator('.arco-select-option').getByText('原生装备继承规则',{exact:true}).click();
  await page.getByRole('button',{name:'导出全表规则',exact:true}).waitFor();await page.getByRole('cell',{name:'19',exact:true}).waitFor();assert.deepEqual(queries.at(-1),[1,20]);
  await page.locator('.arco-pagination-item').filter({hasText:/^2$/}).click();await page.getByRole('cell',{name:'39',exact:true}).waitFor();assert.deepEqual(queries.at(-1),[2,20]);assert.equal(await page.getByRole('cell',{name:'19',exact:true}).count(),0);checks.push('server pagination requests page two and renders its twenty rows without local double slicing');
  const download=page.waitForEvent('download');await page.getByRole('button',{name:'导出全表规则',exact:true}).click();const artifact=await download;assert.equal(artifact.suggestedFilename(),'inheritance-rules-v1.json');assert.deepEqual(JSON.parse(await readFile(await artifact.path(),'utf8')),dataset);checks.push('download contains all 1005 synthetic rows rather than only the current page');
  await page.getByLabel('选择全表规则文件').setInputFiles({name:'inheritance.json',mimeType:'application/json',buffer:file});await page.getByRole('button',{name:'检查文件完整性',exact:true}).click();await page.getByText('文件完整性与绑定已通过，共 1005 条；没有生成写入计划或保存配置。',{exact:true}).waitFor();assert.deepEqual(uploaded,file);checks.push('raw file upload preserves exact bytes and reports integrity only, with no write plan or native write');
  failCheck=true;await page.getByRole('button',{name:'检查文件完整性',exact:true}).click();await page.getByText('规则文件实例或数据库UUID不匹配',{exact:true}).first().waitFor();assert.equal(await page.getByText('文件完整性与绑定已通过，共 1005 条；没有生成写入计划或保存配置。',{exact:true}).count(),0);await page.getByRole('cell',{name:'39',exact:true}).waitFor();checks.push('binding error clears stale success while keeping the current rule page usable');
  slowExport=true;await page.getByRole('button',{name:'导出全表规则',exact:true}).click();await page.getByRole('button',{name:'取消文件读取',exact:true}).click();await page.getByText('已取消读取；没有提交原生写入。',{exact:true}).waitFor();checks.push('in-flight export cancellation returns to the page without submitting any native writes');
  const select=async title=>{await page.locator('.arco-select').first().click();await page.locator('.arco-select-option').getByText(title,{exact:true}).click()};await select('原生装备跨界规则');assert.equal(await page.getByLabel('选择全表规则文件').evaluate(e=>e.files.length),0);assert.equal(await page.getByRole('button',{name:'检查文件完整性',exact:true}).isDisabled(),true);await select('原生装备继承规则');assert.equal(await page.getByLabel('选择全表规则文件').evaluate(e=>e.files.length),0);await page.getByLabel('选择全表规则文件').setInputFiles({name:'inheritance.json',mimeType:'application/json',buffer:file});assert.equal(await page.getByRole('button',{name:'检查文件完整性',exact:true}).isEnabled(),true);checks.push('switching native modules clears the actual file input and permits selecting the same file again');
  assert.equal(writes,0);assert.deepEqual(errors,[]);const result={passed:true,mockedApis:true,realDeploymentVerified:false,originalToolEquivalenceVerified:false,nativeGameplayVerified:false,nativeWrites:writes,checks,pageErrors:errors.length};await writeFile(path.join(output,'result.json'),JSON.stringify(result,null,2));console.log(JSON.stringify(result));
 }catch(e){if(page){await writeFile(path.join(output,'failure.json'),JSON.stringify({errors,text:await page.locator('body').innerText()},null,2));await page.screenshot({path:path.join(output,'failure.png'),fullPage:true})}throw e}finally{if(browser)await browser.close();await new Promise(r=>server.close(r))}
})().catch(e=>{console.error(e);process.exitCode=1});
