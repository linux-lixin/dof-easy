// Compiled UI with explicit API mocks; actual MySQL and native delivery are separate gates.
const assert=require('node:assert/strict'),{createServer}=require('node:http');
const {readFile,mkdir,writeFile}=require('node:fs/promises'),{createHash}=require('node:crypto'),path=require('node:path');
const {parseArgs}=require('node:util'),{chromium}=require(process.env.PLAYWRIGHT_MODULE||'playwright');
const {values:args}=parseArgs({options:{dist:{type:'string'},output:{type:'string'}}});
if(!args.dist||!args.output)throw new Error('Use --dist <built frontend> --output <private test directory>');
const dist=path.resolve(args.dist),output=path.resolve(args.output),rows=[],bodies=new Map(),posts=[],checks=[],errors=[];
let generation=0,nativeWrites=0,lose=true,engine='InnoDB',allowed=true,historyFailure=false,deleted=false;
const controls=[];
const revision=()=>createHash('sha256').update(JSON.stringify({generation,engine})).digest('hex');
const server=createServer(async(req,res)=>{try{
 let file=path.resolve(dist,'.'+decodeURIComponent(new URL(req.url,'http://localhost').pathname));
 if(!file.startsWith(dist+path.sep))return res.writeHead(403).end();
 let data;try{data=await readFile(file)}catch{file=path.join(dist,'index.html');data=await readFile(file)}
 res.writeHead(200,{'Content-Type':({'.html':'text/html','.js':'text/javascript','.css':'text/css','.svg':'image/svg+xml','.png':'image/png'})[path.extname(file)]||'application/octet-stream'}).end(data);
}catch{res.writeHead(500).end()}});
(async()=>{
 await mkdir(output,{recursive:true});await new Promise(resolve=>server.listen(0,'127.0.0.1',resolve));const origin=`http://127.0.0.1:${server.address().port}`;let browser,page;
 try{
  browser=await chromium.launch({headless:true});page=await browser.newPage({viewport:{width:1440,height:1100}});page.on('pageerror',e=>errors.push(e.message));
  await page.addInitScript(()=>localStorage.setItem('token','mock-postal-fixture'));
  await page.route('**/*',async route=>{
   const req=route.request(),url=new URL(req.url());if(url.origin!==origin)return route.abort();if(!url.pathname.startsWith('/api/'))return route.continue();
   const json=(x,status=200)=>route.fulfill({status,contentType:'application/json',body:JSON.stringify(x)});
   if(url.pathname==='/api/v1/admin/launcher/runtime')return json({mode:'dof',instanceId:'mock-postal'});
   if(url.pathname==='/api/v1/charac')return json({page:1,totalPageSize:1,totalSize:1,list:[{characNo:123,characName:'synthetic-role',mId:22,job:0,lev:86,growType:0}]});
   if(!url.pathname.startsWith('/api/v1/postal'))return json([]);
   if(!allowed)return json({message:'模拟：仅管理员可发送邮件'},403);
   if(url.pathname.endsWith('/send-status')&&deleted)return json({message:'模拟：收件角色不存在'},404);
   if(url.pathname.endsWith('/receipt-controls'))return json(controls);
   if(url.pathname.endsWith('/send-status'))return json({recipient:123,uid:22,actor:1,revision:revision(),atomicReceipt:engine==='InnoDB'});
   if(url.pathname.endsWith('/history'))return historyFailure?json({message:'模拟：历史查询失败'},503):json(rows);
   const reconcile=url.pathname.match(/\/history\/([^/]+)\/reconcile$/);
   if(reconcile){const row=rows.find(r=>r.requestId===reconcile[1]);assert.ok(row);if(row.state==='PREPARED')row.state='CONFLICT';row.receiptRevision=createHash('sha256').update(row.requestId+row.state).digest('hex');return json(row)}
   const ackPath=url.pathname.match(/\/history\/([^/]+)\/acknowledgments(\/preview|\/[^/]+\/reconcile)?$/);
   if(ackPath){
    const source=rows.find(r=>r.requestId===ackPath[1]);assert.ok(source);
    if(ackPath[2]?.endsWith('/reconcile'))return json(controls.find(r=>r.requestId===ackPath[2].split('/')[1]));
    const body=req.postDataJSON();assert.ok(body.requestId);assert.equal(body.receiveCharacNo,undefined);
    let row=controls.find(r=>r.requestId===body.requestId);
    if(ackPath[2]==='/preview'){
     if(!row){assert.equal(source.state,'CONFLICT');assert.equal(body.revision,source.receiptRevision);row={...body,actor:1,targetRequestId:source.requestId,state:'PREVIEWED',operation:'ack-unknown',nativeDataChanged:false};controls.push(row)}
     return json(row);
    }
    assert.ok(row);if(row.state==='PREVIEWED'){assert.equal(source.state,'CONFLICT');source.state='ACKNOWLEDGED';row.state='NO_CHANGE'}return json(row);
   }
   const body=req.postDataJSON();assert.equal(body.receiveCharacNo,123);assert.ok(body.requestId);
   if(url.pathname.endsWith('/preview')){
    const old=rows.find(r=>r.requestId===body.requestId);if(old){assert.deepEqual(body,bodies.get(body.requestId));return json(old)}
    assert.equal(body.revision,revision());const row={requestId:body.requestId,revision:body.revision,actor:1,operation:body.operation,targetRequestId:body.targetRequestId||null,reason:body.reason,state:'PREVIEWED',persisted:false,outcome:body.operation==='send'?{mail:body.mail,sender:'DNF Manager'}:{}};
    rows.unshift(row);bodies.set(body.requestId,body);return json(row);
   }
   assert.equal(url.pathname,'/api/v1/postal');posts.push(body);const row=rows.find(r=>r.requestId===body.requestId);assert.ok(row);assert.deepEqual(body,bodies.get(body.requestId));
   if(row.state!=='PREVIEWED')return json(row);
   if(body.operation==='accept-current'){const original=rows.find(r=>r.requestId===body.targetRequestId);assert.equal(original.state,'CONFLICT');original.state='ACKNOWLEDGED';row.state='NO_CHANGE';row.persisted=true;generation++;return json(row)}
   nativeWrites++;generation++;row.state=engine==='InnoDB'?'PERSISTED':'PREPARED';row.persisted=row.state==='PERSISTED';
   if(row.persisted)row.outcome={plan:row.outcome,postalId:100+nativeWrites,letterId:body.mail.message?200+nativeWrites:0};
   if(lose){lose=false;return route.abort()}return json(row);
  });
  const dialog=page.locator('.arco-modal');
  const open=async(waitIdentity=true)=>{await page.getByRole('button',{name:'操作',exact:true}).click();await page.getByText('发送邮件',{exact:true}).click();if(waitIdentity)await dialog.getByText(/账号 UID 22/).waitFor()};
  const formInput=label=>dialog.getByPlaceholder(label==='金币'?'邮件金币':'填写本次邮件操作原因',{exact:true});
  const clickPost=async(name,suffix)=>{const result=page.waitForResponse(r=>new URL(r.url()).pathname.endsWith(suffix)&&r.request().method()==='POST');await dialog.getByRole('button',{name,exact:true}).click();const r=await result;assert.equal(r.status(),200);return r.json()};
  await page.goto(origin+'/admin/player/roles');await open();await formInput('金币').fill('5');await dialog.locator('textarea').fill('模拟中文正文');await formInput('操作原因').fill('synthetic frozen mail');
  const first=await clickPost('生成预览','/123/preview');assert.equal(nativeWrites,0);assert.equal(first.outcome.mail.message,'模拟中文正文');assert.equal(first.outcome.mail.itemId,0);assert.equal(await formInput('金币').isDisabled(),true);checks.push('gold plus body preview freezes content without sending');
  await page.reload();await open();await dialog.getByRole('row').filter({hasText:first.requestId}).getByRole('button',{name:'加载原预览',exact:true}).click();assert.equal((await clickPost('重读原预览','/123/preview')).requestId,first.requestId);checks.push('reload rehydrates original preview with the same ID and content');
  await dialog.getByRole('button',{name:'提交已预览内容',exact:true}).click();await dialog.getByText(/响应不明，保留原编号/).waitFor();assert.equal(posts.length,1);assert.equal(nativeWrites,1);assert.equal(await dialog.getByRole('button',{name:'新建发送预览',exact:true}).isDisabled(),true);
  const checked=await clickPost('核对原操作',`/${first.requestId}/reconcile`);assert.equal(checked.state,'PERSISTED');assert.equal(checked.outcome.postalId,101);assert.equal(posts.length,1);checks.push('lost submit keeps original ID; reconcile recovers receipt without reissuing');
  await page.reload();await open();await dialog.getByRole('row').filter({hasText:first.requestId}).waitFor();assert.equal(posts.length,1);checks.push('history recovers generated postal ID after page reload');
  engine='MyISAM';await dialog.getByRole('button',{name:'新建发送预览',exact:true}).click();await formInput('金币').fill('1');await formInput('操作原因').fill('synthetic MyISAM interruption');const pending=await clickPost('生成预览','/123/preview');lose=true;
  await dialog.getByRole('button',{name:'提交已预览内容',exact:true}).click();await dialog.getByText(/响应不明，保留原编号/).waitFor();assert.equal((await clickPost('核对原操作',`/${pending.requestId}/reconcile`)).state,'CONFLICT');assert.equal(await dialog.getByRole('button',{name:'新建发送预览',exact:true}).isDisabled(),true);checks.push('uncertain MyISAM mail blocks new sends');
  await dialog.getByRole('row').filter({hasText:pending.requestId}).getByRole('button',{name:'人工确认保留',exact:true}).click();await formInput('操作原因').fill('synthetic manually checked, outcome remains unknown');const ack=await clickPost('生成预览','/123/preview');assert.equal(ack.operation,'accept-current');assert.equal((await clickPost('提交已预览内容','/api/v1/postal')).state,'NO_CHANGE');assert.equal(nativeWrites,2);checks.push('explicit acknowledgment sends no new mail and preserves unknown original outcome');
  await dialog.getByRole('button',{name:'新建发送预览',exact:true}).click();await formInput('金币').fill('1');await formInput('操作原因').fill('synthetic deleted recipient');const deletedMail=await clickPost('生成预览','/123/preview');lose=true;
  await dialog.getByRole('button',{name:'提交已预览内容',exact:true}).click();await dialog.getByText(/响应不明，保留原编号/).waitFor();await clickPost('核对原操作',`/${deletedMail.requestId}/reconcile`);deleted=true;
  await page.reload();await open(false);await dialog.getByText('模拟：收件角色不存在',{exact:true}).waitFor();assert.equal(await dialog.getByRole('button',{name:'生成预览',exact:true}).isDisabled(),true);const writesBefore=nativeWrites;
  await dialog.getByRole('row').filter({hasText:deletedMail.requestId}).getByRole('button',{name:'仅确认未知回执',exact:true}).click();
  const control=page.locator('.arco-modal').filter({hasText:'仅确认未知回执'}).last();await control.getByPlaceholder('填写人工核对原因').fill('synthetic review after character deletion');
  const ackPreview=page.waitForResponse(r=>r.url().endsWith('/acknowledgments/preview'));await control.getByRole('button',{name:'预览回执确认',exact:true}).click();assert.equal((await (await ackPreview).json()).state,'PREVIEWED');
  const ackSubmit=page.waitForResponse(r=>r.url().endsWith('/acknowledgments')&&r.request().method()==='POST');await control.getByRole('button',{name:'提交回执确认',exact:true}).click();assert.equal((await (await ackSubmit).json()).state,'NO_CHANGE');await control.getByText(/原邮件结果仍未知/).waitFor();assert.equal(nativeWrites,writesBefore);assert.equal(rows.find(r=>r.requestId===deletedMail.requestId).state,'ACKNOWLEDGED');checks.push('deleted recipient can acknowledge an unknown receipt without enabling new mail or issuing native writes');
  deleted=false;
  historyFailure=true;await page.reload();await open();await dialog.getByText('模拟：历史查询失败',{exact:true}).waitFor();assert.equal(await dialog.getByRole('button',{name:'生成预览',exact:true}).isDisabled(),true);checks.push('failed history read disables new mail even with a valid identity');
  historyFailure=false;allowed=false;await page.reload();await page.getByRole('button',{name:'操作',exact:true}).click();await page.getByText('发送邮件',{exact:true}).click();await dialog.getByText('模拟：仅管理员可发送邮件',{exact:true}).waitFor();assert.equal(await dialog.getByRole('button',{name:'生成预览',exact:true}).isDisabled(),true);checks.push('permission failure disables sending');
  assert.deepEqual(errors,[]);const result={passed:true,mockedApis:true,realDeploymentVerified:false,actualPlayerReceiptVerified:false,checks,pageErrors:errors.length,nativeWritesInMock:nativeWrites,submitRequests:posts.length};await writeFile(path.join(output,'result.json'),JSON.stringify(result,null,2));console.log(JSON.stringify(result));
 }catch(e){if(page){await writeFile(path.join(output,'failure.json'),JSON.stringify({url:page.url(),pageErrors:errors,text:await page.locator('body').innerText()},null,2));await page.screenshot({path:path.join(output,'failure.png'),fullPage:true})}throw e}
 finally{if(browser)await browser.close();await new Promise(resolve=>server.close(resolve))}
})().catch(e=>{console.error(e);process.exitCode=1});
