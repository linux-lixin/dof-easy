// Real Spring HTTP through a local static proxy, with explicitly synthetic database data.
const assert = require('node:assert/strict');
const { createServer, request: forward } = require('node:http');
const { readFile, mkdir, writeFile } = require('node:fs/promises');
const path = require('node:path');
const { parseArgs } = require('node:util');
const { chromium } = require(process.env.PLAYWRIGHT_MODULE || 'playwright');
const { values: args } = parseArgs({ options: { dist: { type: 'string' }, backend: { type: 'string' }, credentials: { type: 'string' }, output: { type: 'string' }, retained: { type: 'boolean', default: false } } });
if (!args.dist || !args.backend || !args.credentials || !args.output) throw new Error('Required: --dist --backend --credentials --output');
const backend = new URL(args.backend), dist = path.resolve(args.dist), output = path.resolve(args.output);
if (backend.hostname !== '127.0.0.1' || backend.protocol !== 'http:') throw new Error('Only an ephemeral loopback fixture backend is allowed');
const observe = promise => { promise.catch(() => {}); return promise; };
const types = { '.js': 'text/javascript', '.css': 'text/css', '.html': 'text/html', '.svg': 'image/svg+xml', '.png': 'image/png' };
const server = createServer(async (request, response) => {
  if (request.url.startsWith('/api/')) {
    const proxy = forward(new URL(request.url, backend), { method: request.method, headers: { ...request.headers, host: backend.host } }, upstream => { response.writeHead(upstream.statusCode, upstream.headers);upstream.pipe(response); });
    proxy.on('error', () => response.writeHead(502).end());request.pipe(proxy);return;
  }
  try {
    let file = path.resolve(dist, '.' + decodeURIComponent(new URL(request.url, 'http://localhost').pathname));
    if (file !== dist && !file.startsWith(dist + path.sep)) { response.writeHead(403).end();return; }
    let bytes;try { bytes = await readFile(file); } catch { file = path.join(dist, 'index.html');bytes = await readFile(file); }
    response.writeHead(200, { 'Content-Type': types[path.extname(file)] || 'application/octet-stream' });response.end(bytes);
  } catch { response.writeHead(500).end(); }
});

(async () => {
  await mkdir(output, { recursive: true });
  await new Promise(resolve => server.listen(0, '127.0.0.1', resolve));
  const origin = `http://127.0.0.1:${server.address().port}`;
  const credentials = JSON.parse(await readFile(args.credentials, 'utf8'));
  assert.equal(credentials.account, 'activity_admin');
  let browser,page;const errors=[],checks=[];
  try {
    browser=await chromium.launch({headless:true});const context=await browser.newContext({viewport:{width:1600,height:1200}});
    page=await context.newPage();page.on('pageerror', e=>errors.push(e.message));
    await page.route('**/*', r=>new URL(r.request().url()).origin===origin?r.continue():r.abort());
    await page.goto(origin+'/login?redirect=/admin/extension-operations');
    await page.getByPlaceholder('请输入用户名').fill(credentials.account);await page.getByPlaceholder('请输入密码').fill(credentials.password);
    await page.getByRole('button',{name:'登录',exact:true}).click();await page.waitForURL(u=>u.pathname==='/admin/extension-operations');
    await page.getByText('注册激活码',{exact:true}).click();
    const token=await page.evaluate(()=>localStorage.getItem('token'));assert.ok(token);
    const api=async suffix=>{const r=await context.request.get(origin+'/api/v1/admin/registration/'+suffix,{headers:{Authorization:'Bearer '+token}});assert.equal(r.status(),200,suffix);return r.json();};
    await page.getByText('注册激活码：已关闭',{exact:true}).waitFor();checks.push('real login and registration status');
    if(args.retained){const expected=JSON.parse(await readFile(path.join(output,'expected-private.json'),'utf8'));const rows=await api('operations');const row=rows.find(r=>r.requestId===expected.requestId);assert.equal(row.state,'PERSISTED');
      const response=observe(page.waitForResponse(r=>r.url().endsWith(`/operations/${row.requestId}/reconcile`)));await page.getByRole('row').filter({hasText:row.requestId}).getByRole('button',{name:'取回同一码',exact:true}).click();assert.equal((await(await response).json()).code,expected.code);checks.push('same code recovered after backend process restart');
    }else{
      const before=(await api('codes')).length;await page.getByPlaceholder('操作原因（必填）').fill('synthetic browser registration contract');
      const previewResponse=observe(page.waitForResponse(r=>r.url().endsWith('/registration/preview')&&r.request().method()==='POST'));
      await page.getByRole('button',{name:'预览生成激活码',exact:true}).click();const preview=await(await previewResponse).json();assert.equal(preview.state,'PREVIEWED');assert.equal((await api('codes')).length,before);checks.push('preview persists without creating code');
      await page.getByRole('button',{name:'确认提交',exact:true}).click();
      const saved=observe(page.waitForResponse(r=>r.url().endsWith('/registration/codes')&&r.request().method()==='POST'));
      await page.locator('.arco-popconfirm').getByRole('button',{name:'确定',exact:true}).click();const result=await(await saved).json();assert.equal(result.state,'PERSISTED');assert.match(result.code,/^[A-Za-z0-9_-]{43}$/);checks.push('browser confirms frozen preview');
      await page.getByRole('button',{name:'隐藏明文',exact:true}).click();
      const recovered=observe(page.waitForResponse(r=>r.url().endsWith(`/operations/${result.requestId}/reconcile`)));
      await page.getByRole('row').filter({hasText:result.requestId}).getByRole('button',{name:'取回同一码',exact:true}).click();assert.equal((await(await recovered).json()).code,result.code);assert.equal((await api('codes')).length,before+1);checks.push('recover same code without duplicate generation');
      await page.getByRole('button',{name:'隐藏明文',exact:true}).click();
      const secondPreview=observe(page.waitForResponse(r=>r.url().endsWith('/registration/preview')&&r.request().method()==='POST'));
      await page.getByRole('button',{name:'预览生成激活码',exact:true}).click();const pending=await(await secondPreview).json();
      let lost;let acknowledge;const dropped=new Promise(resolve=>acknowledge=resolve);
      const fault=async route=>{if(route.request().method()!=='POST')return route.continue();const response=await route.fetch();lost=await response.json();await route.abort('failed');acknowledge();};
      await page.route('**/api/v1/admin/registration/codes',fault);
      await page.getByRole('button',{name:'确认提交',exact:true}).click();await page.locator('.arco-popconfirm').getByRole('button',{name:'确定',exact:true}).click();await dropped;
      assert.equal(lost.state,'PERSISTED');assert.equal(await page.getByRole('button',{name:'关闭未提交预览',exact:true}).count(),0);assert.equal(await page.getByRole('button',{name:'确认提交',exact:true}).isDisabled(),true);
      await page.unroute('**/api/v1/admin/registration/codes',fault);
      const checked=observe(page.waitForResponse(r=>r.url().endsWith(`/operations/${pending.requestId}/reconcile`)));
      await page.getByRole('button',{name:'核对操作结果',exact:true}).click();assert.equal((await(await checked).json()).code,lost.code);assert.equal((await api('codes')).length,before+2);checks.push('lost commit reply keeps nonce and requires reconciliation');
      await writeFile(path.join(output,'expected-private.json'),JSON.stringify({requestId:result.requestId,code:result.code}),{mode:0o600});
    }
    assert.deepEqual(errors,[]);await page.screenshot({path:path.join(output,args.retained?'registration-retained.png':'registration.png'),fullPage:true});
    const result={passed:true,syntheticDatabase:true,actualGameDataAccessed:false,backendProcessRestarted:args.retained,checks,pageErrors:errors.length};
    await writeFile(path.join(output,args.retained?'result-retained.json':'result.json'),JSON.stringify(result,null,2)+'\n');console.log(JSON.stringify(result));
  } catch(e) {if(page){await writeFile(path.join(output,'failure.html'),await page.content());await page.screenshot({path:path.join(output,'failure.png'),fullPage:true}).catch(()=>{});}throw e;} finally {if(browser)await browser.close();await new Promise(resolve=>server.close(resolve));}
})().catch(e=>{console.error(e);process.exitCode=1;});
