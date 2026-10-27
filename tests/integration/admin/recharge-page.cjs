// Compiled UI against explicit mocks. Database/fault acceptance is a separate real-MySQL contract.
const assert=require('node:assert/strict');
const {createServer}=require('node:http');
const {readFile,mkdir,writeFile}=require('node:fs/promises');
const {createHash}=require('node:crypto');
const path=require('node:path');
const {parseArgs}=require('node:util');
const {chromium}=require(process.env.PLAYWRIGHT_MODULE||'playwright');
const {values:args}=parseArgs({options:{dist:{type:'string'},output:{type:'string'},points:{type:'boolean',default:false}}});
if(!args.dist||!args.output)throw new Error('Use --dist <compiled frontend> --output <private test directory>');
const dist=path.resolve(args.dist),output=path.resolve(args.output),errors=[],checks=[],rows=[],bodies=new Map(),submissions=[];
const unit=args.points?'代币券':'点券',base=args.points?'/api/v1/recharge/points':'/api/v1/recharge',field=args.points?'amount':'cera',beforeField=args.points?'beforeAmount':'beforeCera',afterField=args.points?'afterAmount':'afterCera',openButton=args.points?'代币券管理':'充值',previewButton=`预览${unit}变更`;
let balance=37,engine='InnoDB',loseNext=true,staleNext=false,allowed=true,nativeMutations=0;
const revision=()=>createHash('sha256').update(JSON.stringify({balance,engine})).digest('hex');
const mime={'.js':'text/javascript','.css':'text/css','.html':'text/html','.svg':'image/svg+xml','.png':'image/png'};
const server=createServer(async(req,res)=>{try{
 let file=path.resolve(dist,'.'+decodeURIComponent(new URL(req.url,'http://localhost').pathname));
 if(file!==dist&&!file.startsWith(dist+path.sep))return res.writeHead(403).end();
 let data;try{data=await readFile(file)}catch{file=path.join(dist,'index.html');data=await readFile(file)}
 res.writeHead(200,{'Content-Type':mime[path.extname(file)]||'application/octet-stream'}).end(data);
}catch{res.writeHead(500).end()}});
(async()=>{
 await mkdir(output,{recursive:true});await new Promise(resolve=>server.listen(0,'127.0.0.1',resolve));
 const origin=`http://127.0.0.1:${server.address().port}`;let browser;
 try{
  browser=await chromium.launch({headless:true});const context=await browser.newContext({viewport:{width:1600,height:1100}});
  await context.addInitScript(()=>localStorage.setItem('token','mock-recharge-fixture'));
  const page=await context.newPage();page.on('pageerror',e=>errors.push(e.message));
  await page.route('**/*',async route=>{
   const req=route.request(),url=new URL(req.url());if(url.origin!==origin)return route.abort();
   if(!url.pathname.startsWith('/api/'))return route.continue();
   const json=(data,status=200)=>route.fulfill({status,contentType:'application/json',body:JSON.stringify(data)});
   if(url.pathname==='/api/v1/admin/launcher/runtime')return json({mode:'dof',nativeDevelopment:true,instanceId:'mock-recharge'});
   if(url.pathname==='/api/v1/account')return json({page:1,totalPageSize:1,totalSize:1,list:[{uid:22,accountname:'synthetic-player',parentUid:0,loginStatus:false}]});
   if(!allowed)return json({message:`模拟：仅管理员可调整${unit}`},403);
   if(url.pathname===base+'/22'&&req.method()==='GET')return json({uid:22,[field]:balance,revision:revision(),engine,actor:1,instanceId:'mock-recharge',atomicReceipt:engine==='InnoDB'});
   if(url.pathname===base+'/22/history')return json(rows);
   const reconcile=url.pathname.startsWith(base+'/22/history/')?url.pathname.slice((base+'/22/history/').length).match(/^([^/]+)\/reconcile$/):null;
   if(reconcile){const row=rows.find(r=>r.requestId===reconcile[1]);assert.ok(row);if(row.state==='PREPARED')row.state='CONFLICT';return json(row)}
   const body=req.postDataJSON();if(args.points)assert.equal(body.cera,undefined);else assert.equal(body.amount,undefined);assert.equal(body.uid,22);assert.match(body.requestId,/^[0-9a-f-]{36}$/);
   if(url.pathname===base+'/22/preview'){
    const existing=rows.find(r=>r.requestId===body.requestId);if(existing){assert.deepEqual(body,bodies.get(body.requestId));return json(existing)}
    if(rows.some(r=>['PREPARED','CONFLICT'].includes(r.state)&&r.requestId!==body.targetRequestId))return json({message:'模拟：先核对未决操作'},409);
    assert.equal(body.revision,revision());
    const row={requestId:body.requestId,revision:body.revision,actor:1,uid:22,operation:body.operation,targetRequestId:body.targetRequestId||null,reason:body.reason,
      [beforeField]:balance,[afterField]:balance+(body[field]||0),delta:body[field]||0,engine,atomicReceipt:engine==='InnoDB',state:'PREVIEWED',persisted:false,note:'模拟持久化与游戏效果分别验证'};
    rows.unshift(row);bodies.set(body.requestId,body);return json(row);
   }
   assert.equal(url.pathname,base);assert.equal(req.method(),'POST');submissions.push(body);
   const row=rows.find(r=>r.requestId===body.requestId);assert.ok(row);assert.deepEqual(body,bodies.get(body.requestId));
   if(row.state!=='PREVIEWED')return json(row);
   if(staleNext){balance++;staleNext=false}
   if(body.revision!==revision())return json({message:'模拟余额版本已改变，请核对原预览'},409);
   if(body.operation==='accept-current'){
    const old=rows.find(r=>r.requestId===body.targetRequestId);assert.equal(old.state,'CONFLICT');old.state='ACKNOWLEDGED';row.state='NO_CHANGE';row.persisted=true;return json(row);
   }
   if(body.operation==='compensate'){const old=rows.find(r=>r.requestId===body.targetRequestId);assert.equal(old.operation,'credit');assert.equal(body[field],-old.delta)}
   balance+=body[field];nativeMutations++;row.state=engine==='MyISAM'?'PREPARED':'PERSISTED';row.persisted=row.state==='PERSISTED';
   if(loseNext){loseNext=false;return route.abort()}
   row.state='PERSISTED';row.persisted=true;return json(row);
  });
  const dialog=page.locator('.arco-modal');
  const open=async()=>{await page.getByRole('button',{name:openButton,exact:true}).click();await dialog.getByText(new RegExp('当前'+unit+'：'+balance)).waitFor()};
  const clickPost=async(name,suffix)=>{
   const pending=page.waitForResponse(r=>new URL(r.url()).pathname.endsWith(suffix)&&r.request().method()==='POST');
   await dialog.getByRole('button',{name,exact:true}).click();const response=await pending;assert.equal(response.status(),200);return response.json();
  };
  const fill=async(n,text)=>{await dialog.locator('.arco-input-number input').fill(String(n));await dialog.getByPlaceholder('填写本次调整的原因').fill(text)};
  const lostSubmit=async()=>{await dialog.getByRole('button',{name:'提交本次预览',exact:true}).click();await dialog.getByText(/响应不明，保留原编号/).waitFor();assert.equal(await dialog.getByRole('button',{name:'提交本次预览',exact:true}).isDisabled(),true)};
  await page.goto(origin+'/admin/player/accounts');await open();await fill(5,'synthetic lost-response credit');
  const first=await clickPost(previewButton,'/22/preview');assert.equal(nativeMutations,0);assert.equal(await dialog.locator('.arco-input-number input').isDisabled(),true);
  checks.push('read before edit; frozen preview changes no money and locks inputs');
  await page.reload();await open();await dialog.getByRole('row').filter({hasText:first.requestId}).getByRole('button',{name:'加载原预览',exact:true}).click();
  assert.equal((await clickPost('重读原预览','/22/preview')).requestId,first.requestId);assert.equal(nativeMutations,0);
  checks.push('reload rehydrates the exact original preview body and request identity');
  await lostSubmit();assert.equal(submissions.length,1);assert.equal(submissions[0].requestId,first.requestId);assert.equal(nativeMutations,1);
  await dialog.getByText(new RegExp('操作编号：'+first.requestId)).waitFor();
  balance=100;const checked=await clickPost('核对本次操作',`/${first.requestId}/reconcile`);assert.equal(checked.state,'PERSISTED');assert.equal(submissions.length,1);assert.equal(balance,100);
  checks.push('lost submit retains original request; reconciliation never resends credit');
  await page.reload();await open();await dialog.getByRole('row').filter({hasText:first.requestId}).waitFor();assert.equal(submissions.length,1);
  checks.push('reload recovers original durable receipt from history without submitting');
  await dialog.getByRole('row').filter({hasText:first.requestId}).getByRole('button',{name:'预览补偿扣回',exact:true}).click();
  await dialog.getByPlaceholder('填写本次调整的原因').fill('synthetic linked compensation');
  const comp=await clickPost(previewButton,'/22/preview');assert.equal(comp.delta,-5);assert.equal(comp.targetRequestId,first.requestId);assert.equal(comp[beforeField],100);assert.equal(comp[afterField],95);
  await clickPost('提交本次预览',base);assert.equal(balance,95);assert.equal(nativeMutations,2);
  checks.push('compensation links original credit and subtracts only its delta from later balance');
  await dialog.getByRole('button',{name:'新建充值预览',exact:true}).click();await fill(5,'synthetic stale preview');
  const stale=await clickPost(previewButton,'/22/preview');staleNext=true;
  await lostSubmit();assert.equal(balance,96);assert.equal(nativeMutations,2);
  assert.equal((await clickPost('核对本次操作',`/${stale.requestId}/reconcile`)).state,'PREVIEWED');
  checks.push('stale submit does not award and requires explicit reconciliation before new intent');
  engine='MyISAM';await dialog.getByRole('button',{name:'新建充值预览',exact:true}).click();await fill(3,'synthetic MyISAM interruption');
  const uncertain=await clickPost(previewButton,'/22/preview');loseNext=true;await lostSubmit();assert.equal(balance,99);
  assert.equal((await clickPost('核对本次操作',`/${uncertain.requestId}/reconcile`)).state,'CONFLICT');
  assert.equal(await dialog.getByRole('button',{name:'新建充值预览',exact:true}).isDisabled(),true);
  checks.push('MyISAM interrupted result stays unknown and blocks new credits');
  await dialog.getByRole('row').filter({hasText:uncertain.requestId}).getByRole('button',{name:'明确保留当前余额',exact:true}).click();
  await dialog.getByPlaceholder('填写本次调整的原因').fill('synthetic explicit review, original outcome remains unknown');
  const keep=await clickPost(previewButton,'/22/preview');assert.equal(keep.delta,0);assert.equal(keep.targetRequestId,uncertain.requestId);
  assert.equal((await clickPost('提交本次预览',base)).state,'NO_CHANGE');assert.equal(balance,99);assert.equal(nativeMutations,3);assert.equal(rows.find(r=>r.requestId===uncertain.requestId).state,'ACKNOWLEDGED');
  checks.push('explicit keep-current acknowledgment changes no money and preserves unknown original outcome');
  allowed=false;await page.reload();await page.getByRole('button',{name:openButton,exact:true}).click();
  await dialog.locator('.arco-alert-content').getByText(`模拟：仅管理员可调整${unit}`,{exact:true}).waitFor();
  assert.equal(await dialog.getByRole('button',{name:previewButton,exact:true}).isDisabled(),true);assert.equal(nativeMutations,3);
  checks.push('permission/read failure leaves writing disabled');assert.deepEqual(errors,[]);
  const result={passed:true,currency:args.points?'cera_point':'cera',mockedApis:true,realDeploymentVerified:false,pageErrors:errors.length,checks,nativeMutationsInMock:nativeMutations,automaticResubmissions:0};
  await page.screenshot({path:path.join(output,'recharge-page.png'),fullPage:true});await writeFile(path.join(output,'result.json'),JSON.stringify(result,null,2));console.log(JSON.stringify(result));
 }finally{if(browser)await browser.close();await new Promise(resolve=>server.close(resolve))}
})().catch(e=>{console.error(e);process.exitCode=1});
