// Real isolated admin only; reuse an already verified PVF task. No parameter application.
// Checkpoints, credentials and downloaded archives stay on the test server.
const assert = require('node:assert/strict');
const fs = require('node:fs/promises'), path = require('node:path');
const {createHash} = require('node:crypto'), {parseArgs} = require('node:util');
const {chromium} = require(process.env.PLAYWRIGHT_MODULE || 'playwright');
const {values: args} = parseArgs({options: {
  origin: {type: 'string'}, credentials: {type: 'string'}, output: {type: 'string'},
  'script-task-id': {type: 'string'}, 'legacy-bundle-id': {type: 'string'},
}});
const origin = new URL(args.origin).origin, output = path.resolve(args.output);
assert.ok(['localhost', '127.0.0.1'].includes(new URL(origin).hostname));
for (const name of ['script-task-id', 'legacy-bundle-id'])
  assert.match(args[name] || '', /^[a-f0-9]{8}(-[a-f0-9]{4}){3}-[a-f0-9]{12}$/);
const base = '/api/v1/admin/dof', checks = [], errors = [];
const state = {phase: 'INITIAL', serverParametersApplied: false};
async function phase(value) {
  state.phase = value;
  await fs.writeFile(path.join(output, 'state.tmp'), JSON.stringify(state, null, 2), {mode: 0o600});
  await fs.rename(path.join(output, 'state.tmp'), path.join(output, 'operation-state.json'));
}
async function download(token, job) {
  const response = await fetch(origin + base + '/client/tasks/' + job.requestId + '/download', {
    headers: {Authorization: 'Bearer ' + token}, redirect: 'error',
  });
  assert.equal(response.status, 200);
  const hash = createHash('sha256'); let bytes = 0;
  const file = await fs.open(path.join(output, 'candidate.zip'), 'wx', 0o600);
  try {
    for await (const chunk of response.body) {
      bytes += chunk.length; assert.ok(bytes <= job.bytes);
      hash.update(chunk); await file.writeFile(chunk);
    }
    await file.sync();
  } finally { await file.close(); }
  assert.equal(bytes, job.bytes); assert.equal(hash.digest('hex'), job.sha256);
}
(async () => {
  // A lost observer must resume/reconcile existing IDs, never create another batch.
  await fs.mkdir(output, {recursive: false, mode: 0o700});
  assert.equal((await fs.stat(args.credentials)).mode & 0o077, 0);
  const credentials = JSON.parse(await fs.readFile(args.credentials, 'utf8'));
  const browser = await chromium.launch({headless: true});
  try {
    const context = await browser.newContext({viewport: {width: 1900, height: 1400}});
    const page = await context.newPage(); page.on('pageerror', e => errors.push(e.message));
    await page.route('**/*', async route => {
      const request = route.request();
      if (new URL(request.url()).origin !== origin) return route.abort();
      if (request.url().endsWith(base + '/client/tasks') && request.method() === 'POST') {
        state.bundleRequest = request.postDataJSON(); await phase('BUNDLE_REQUEST_SENT');
      }
      return route.continue();
    });
    await phase('LOGIN');
    await page.goto(origin + '/login?redirect=/admin/dof');
    await page.getByPlaceholder('请输入用户名').fill(credentials.account);
    await page.getByPlaceholder('请输入密码').fill(credentials.password);
    await page.getByRole('button', {name: '登录', exact: true}).click();
    await page.waitForURL(u => u.pathname === '/admin/dof');
    const token = await page.evaluate(() => localStorage.getItem('token')); assert.ok(token);
    const api = async (url) => {
      const response = await context.request.get(origin + url, {headers: {Authorization: 'Bearer ' + token}});
      assert.equal(response.status(), 200); return response.json();
    };
    const catalog = await api(base), wallet = await api('/api/v1/recharge/6');
    assert.equal(catalog.instanceId, 'dof-main'); assert.equal(catalog.completeReplacement, false);
    assert.equal(wallet.databaseUuid, '899d147e-d0ef-11f1-9ff4-525400542f67'); assert.equal(Number(wallet.cera), 37);
    const source = await api(base + '/client'), script = await api(base + '/script/tasks/' + args['script-task-id']);
    assert.equal(source.enabled, true); assert.equal(source.manifestVersion, 1);
    assert.equal(script.state, 'READY_FOR_TEST'); assert.equal(script.revision, source.revision);
    assert.equal(script.roundTripVerified, true); assert.equal(script.compressed, false);
    const legacy = await api(base + '/client/tasks/' + args['legacy-bundle-id']);
    assert.equal(legacy.manifestVersion, 0); assert.ok(legacy.sourceBinding == null);
    state.scriptTaskId = script.requestId; state.legacyBundleId = legacy.requestId;
    state.sourceRevision = source.revision; await phase('BOUND');
    checks.push('real bound database and original verified PVF task; historical bundle remains without new provenance');
    await page.getByText('客户端配置与背景', {exact: true}).click();
    const legacyRow = page.locator('tr').filter({hasText: legacy.requestId});
    await legacyRow.getByText('历史任务：未记录完整来源清单，保留原有产物校验。', {exact: true}).waitFor();
    const scriptRow = page.locator('tr').filter({hasText: script.requestId}).filter({has: page.getByRole('button', {name: '选择此批次生成登录器配套包', exact: true})});
    await scriptRow.getByRole('button', {name: '选择此批次生成登录器配套包', exact: true}).click();
    const accepted = page.waitForResponse(r => r.url().endsWith(base + '/client/tasks') && r.request().method() === 'POST', {timeout: 30000});
    await page.getByRole('button', {name: '生成 dof-easy 配套测试包', exact: true}).click();
    const response = await accepted; assert.equal(response.status(), 200);
    const submitted = await response.json(); state.bundleId = submitted.requestId;
    assert.equal(state.bundleRequest.requestId, state.bundleId); await phase('BUNDLE_ACCEPTED');
    const deadline = Date.now() + 15 * 60 * 1000; let job;
    do {
      job = await api(base + '/client/tasks/' + state.bundleId);
      if (!['QUEUED', 'RUNNING'].includes(job.state)) break;
      await new Promise(resolve => setTimeout(resolve, 3000));
    } while (Date.now() < deadline);
    assert.equal(job.state, 'READY_FOR_TEST'); assert.equal(job.manifestVersion, 1);
    assert.equal(job.sourceBinding.databaseUuid, wallet.databaseUuid);
    assert.equal(job.sourceBinding.readerRecipe, 'dof-launcher-reader-2.2');
    assert.equal(job.scriptTaskId, script.requestId); assert.equal(job.scriptSha256, script.sha256);
    assert.equal(job.scriptBatch.parameterSha256, script.parameterSha256);
    assert.equal(job.scriptBatch.sourceSha256, script.sourceSha256);
    assert.equal(job.scriptBatch.roundTripVerified, true); assert.equal(job.nativeClientAcceptance, false);
    assert.match(job.manifestSha256, /^[a-f0-9]{64}$/);
    state.bundleSha256 = job.sha256; state.manifestSha256 = job.manifestSha256; await phase('BUNDLE_READY');
    await page.getByRole('button', {name: '刷新发布来源与任务', exact: true}).click();
    const row = page.locator('tr').filter({hasText: job.requestId});
    await row.getByText('数据库 UUID：' + wallet.databaseUuid, {exact: true}).waitFor();
    await row.getByText('材料哈希已绑定；组件读取和游戏效果待验。', {exact: true}).waitFor();
    await page.reload(); await page.getByText('客户端配置与背景', {exact: true}).click();
    await page.locator('tr').filter({hasText: job.requestId}).getByText('数据库 UUID：' + wallet.databaseUuid, {exact: true}).waitFor();
    checks.push('real page creates a same-PVF bundle and preserves explicit provenance after reload');
    await download(token, job); await phase('BUNDLE_DOWNLOADED');
    checks.push('complete ZIP streamed on server and matched archive length and SHA-256');
    assert.equal(Number((await api('/api/v1/recharge/6')).cera), 37); assert.deepEqual(errors, []);
    const result = {passed: true, mockedApis: false, bundleId: job.requestId, scriptTaskId: script.requestId,
      legacyBundleId: legacy.requestId, bundleSha256: job.sha256, bundleBytes: job.bytes,
      manifestSha256: job.manifestSha256, manifestVersion: job.manifestVersion,
      databaseUuid: wallet.databaseUuid, serverParametersApplied: false, nativeClientAcceptance: false,
      originalWindowsToolStarted: false, completeReplacement: false, checks, pageErrors: errors.length};
    await fs.writeFile(path.join(output, 'result.json'), JSON.stringify(result, null, 2), {mode: 0o600});
    await phase('COMPLETED'); console.log(JSON.stringify(result));
  } catch (error) { state.failureType = error.name; await phase('FAILED'); throw error; }
  finally { await browser.close(); }
})().catch(error => {console.error(error); process.exitCode = 1;});
