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
  assert.equal(credentials.account, 'fixture-admin');
  let browser, page;const checks = [], errors = [];
  try {
    browser = await chromium.launch({ headless: true });const context = await browser.newContext({ viewport: { width: 1440, height: 1100 } });
    page = await context.newPage();page.on('pageerror', error => errors.push(error.message));
    await page.route('**/*', route => new URL(route.request().url()).origin === origin ? route.continue() : route.abort());
    await page.goto(origin + '/login?redirect=/admin/activities');
    await page.getByPlaceholder('请输入用户名').fill(credentials.account);await page.getByPlaceholder('请输入密码').fill(credentials.password);
    await page.getByRole('button', { name: '登录', exact: true }).click();await page.waitForURL(url => url.pathname === '/admin/activities');
    const token = await page.evaluate(() => localStorage.getItem('token'));
    assert.ok(token && /^[a-f0-9]{32}$/.test(token));
    const api = async (method, suffix, data, status = 200) => {
      const r = await context.request.fetch(origin + '/api/v1/admin/' + suffix, { method, data, headers: { Authorization: 'Bearer ' + token } });
      assert.equal(r.status(), status, method + ' ' + suffix);return r.json();
    };
    assert.equal((await api('GET', 'dof')).instanceId, 'dof-browser-fixture');checks.push('real authentication against synthetic accounts');
    if (args.retained) {
      const history = await api('GET', 'activities/rewards/history');assert.equal(history.filter(r => r.state === 'PERSISTED').length, 2);
      assert.equal((await api('GET', 'activities/rewards')).rows[0].values.item_code1, 1);
      const headers = { Authorization: 'Bearer ' + token };
      const role = await context.request.get(origin + '/api/v1/charac/1/edit', { headers });assert.equal(role.status(), 200);assert.equal((await role.json()).values.maxHp, 100);
      const roleHistory = await context.request.get(origin + '/api/v1/charac/1/history', { headers });assert.equal(roleHistory.status(), 200);assert.equal((await roleHistory.json()).filter(r => r.state === 'PERSISTED').length, 2);
      assert.equal((await api('GET', 'accounts/1000/ban')).penalty, null);assert.equal((await api('GET', 'accounts/1000/ban/history')).filter(r => r.state === 'PERSISTED').length, 4);
      const old = (await api('GET', 'activities/signin/history')).find(r => r.legacy);assert.equal(old.state, 'pending');assert.equal(old.resolution.state, 'NO_CHANGE');
      assert.equal((await api('GET', 'accounts/1000/password/history')).filter(r => r.state === 'PERSISTED').length, 1);
      assert.deepEqual(errors, []);
      const result = { passed: true, backendProcessRestarted: true, database: 'same synthetic MySQL 5.6', actualGameDataAccessed: false, containerRecreationVerified: false, checks: ['fresh login after process restart', 'activity history and restored configuration retained', 'role history and restored attributes retained', 'penalty history and original absence retained', 'old audit unchanged and resolution retained', 'password reset metadata retained without plaintext'], pageErrors: 0 };
      await writeFile(path.join(output, 'result-retained.json'), JSON.stringify(result, null, 2) + '\n');console.log(JSON.stringify(result));return;
    }
    const sections = await api('GET', 'activities'), rewards = sections.find(s => s.key === 'rewards');assert.ok(rewards);
    await page.locator('.arco-select').first().click();await page.locator('.arco-select-option').filter({ hasText: rewards.title }).click();
    const before = await api('GET', 'activities/rewards');assert.equal(before.rows[0].values.item_code1, 1);
    await page.getByRole('button', { name: '编辑', exact: true }).first().click();
    const modal = page.locator('.arco-modal').filter({ hasText: '操作编号：' });
    const label = rewards.fields.find(f => f.name === 'item_code1').label;
    const input = modal.locator('.arco-form-item').filter({ hasText: label }).locator('input');
    await input.fill('2');const confirm = modal.getByRole('button', { name: '确认本次预览', exact: true });assert.equal(await confirm.isDisabled(), true);
    const previewRequest = observe(page.waitForRequest(r => r.url().endsWith('/activities/rewards/validate') && r.method() === 'POST'));
    await modal.getByRole('button', { name: '预览操作', exact: true }).click();const body = (await previewRequest).postDataJSON();
    await confirm.waitFor();await page.waitForFunction(() => [...document.querySelectorAll('button')].some(b => b.textContent.includes('确认本次预览') && !b.disabled));
    assert.equal((await api('GET', 'activities/rewards')).rows[0].values.item_code1, 1);checks.push('durable preview without native write');
    const saved = observe(page.waitForResponse(r => r.url().endsWith('/activities/rewards') && r.request().method() === 'PUT'));
    await confirm.click();assert.equal((await (await saved).json()).state, 'PERSISTED');await modal.waitFor({ state: 'hidden' });
    assert.equal((await api('GET', 'activities/rewards')).rows[0].values.item_code1, 2);checks.push('browser commit and readback');
    assert.equal((await api('PUT', 'activities/rewards', body)).state, 'PERSISTED');checks.push('real duplicate replay');
    await api('POST', 'activities/rewards/validate', { ...body, requestId: undefined }, 400);
    await api('PUT', 'activities/rewards', { ...body, values: { ...body.values, item_code1: 3 } }, 409);checks.push('missing identity and changed-body rejection');
    await page.getByRole('button', { name: '修改记录与快照', exact: true }).click();
    const history = page.locator('.arco-modal').filter({ hasText: '操作历史与旧版快照' });
    await history.getByRole('row').filter({ hasText: body.requestId }).getByRole('button', { name: '预览恢复', exact: true }).click();
    await modal.getByRole('button', { name: '预览操作', exact: true }).click();
    await page.waitForFunction(() => [...document.querySelectorAll('button')].some(b => b.textContent.includes('确认本次预览') && !b.disabled));
    const restored = observe(page.waitForResponse(r => r.url().endsWith(`/history/${body.requestId}/restore`) && r.request().method() === 'POST'));
    await confirm.click();assert.equal((await (await restored).json()).state, 'PERSISTED');await modal.waitFor({ state: 'hidden' });
    assert.equal((await api('GET', 'activities/rewards')).rows[0].values.item_code1, 1);checks.push('browser targeted restore');
    const finalHistory = await api('GET', 'activities/rewards/history');assert.equal(finalHistory.filter(r => r.state === 'PERSISTED').length, 2);
    const anonymous = await context.request.get(origin + '/api/v1/admin/activities/rewards');assert.ok([401, 403].includes(anonymous.status()));checks.push('anonymous access rejected');
    const roleApi = async (method, suffix, data, status = 200) => {
      const r = await context.request.fetch(origin + '/api/v1/charac' + suffix, { method, data, headers: { Authorization: 'Bearer ' + token } });
      assert.equal(r.status(), status, method + ' role ' + suffix);return r.json();
    };
    await page.goto(origin + '/admin/player/roles');
    await page.getByRole('row').filter({ hasText: 'SyntheticRole' }).getByRole('button', { name: '操作', exact: true }).click();
    await page.getByText('编辑/详情', { exact: true }).click();
    const roleModal = page.locator('.arco-modal').filter({ hasText: '角色属性：SyntheticRole' });
    await roleModal.locator('[data-field="maxHp"] input').fill('200');
    const roleConfirm = roleModal.getByRole('button', { name: '确认属性预览', exact: true });assert.equal(await roleConfirm.isDisabled(), true);
    const rolePreview = observe(page.waitForRequest(r => r.url().endsWith('/charac/1/preview') && r.method() === 'POST'));
    await roleModal.getByRole('button', { name: '预览属性操作', exact: true }).click();const roleBody = (await rolePreview).postDataJSON();
    await page.waitForFunction(() => [...document.querySelectorAll('button')].some(b => b.textContent.includes('确认属性预览') && !b.disabled));
    assert.equal((await roleApi('GET', '/1/edit')).values.maxHp, 100);checks.push('role browser preview without native write');
    let roleSaved = observe(page.waitForResponse(r => r.url().endsWith('/api/v1/charac') && r.request().method() === 'PUT'));
    await roleConfirm.click();assert.equal((await (await roleSaved).json()).state, 'PERSISTED');
    await roleModal.getByRole('row').filter({ hasText: roleBody.requestId }).waitFor();
    assert.equal((await roleApi('GET', '/1/edit')).values.maxHp, 200);assert.equal((await roleApi('PUT', '', roleBody)).state, 'PERSISTED');
    await roleApi('PUT', '', { ...roleBody, values: { maxHp: 201 } }, 409);checks.push('role browser commit, replay and changed-body refusal');
    await roleModal.getByRole('row').filter({ hasText: roleBody.requestId }).getByRole('button', { name: '预览属性恢复', exact: true }).click();
    await roleModal.getByRole('button', { name: '预览属性操作', exact: true }).click();
    await page.waitForFunction(() => [...document.querySelectorAll('button')].some(b => b.textContent.includes('确认属性预览') && !b.disabled));
    roleSaved = observe(page.waitForResponse(r => r.url().endsWith('/api/v1/charac') && r.request().method() === 'PUT'));
    await roleConfirm.click();assert.equal((await (await roleSaved).json()).state, 'PERSISTED');
    assert.equal((await roleApi('GET', '/1/edit')).values.maxHp, 100);checks.push('role browser targeted restore');
    await page.goto(origin + '/admin/activities');
    const signin = (await api('GET', 'activities/signin')).rows[0];
    const blocked = { ...signin, requestId: require('node:crypto').randomUUID(), operation: 'upsert' };
    await api('POST', 'activities/signin/validate', blocked, 409);
    await page.getByRole('button', { name: '修改记录与快照', exact: true }).click();
    await page.getByRole('button', { name: '核对旧日志', exact: true }).click();
    const legacyModal = page.locator('.arco-modal').filter({ hasText: '核对旧版未决日志' });
    await legacyModal.getByPlaceholder('填写核对依据及保留当前配置的原因').fill('synthetic old record reviewed; retain current rules');
    await legacyModal.getByRole('button', { name: '预览旧日志核对', exact: true }).click();
    const legacyConfirm = legacyModal.getByRole('button', { name: '确认保留当前配置', exact: true });
    await page.waitForFunction(() => [...document.querySelectorAll('button')].some(b => b.textContent.includes('确认保留当前配置') && !b.disabled));
    const legacySaved = observe(page.waitForResponse(r => r.url().endsWith('/legacy/1/confirm') && r.request().method() === 'POST'));
    await legacyConfirm.click();assert.equal((await (await legacySaved).json()).state, 'NO_CHANGE');await legacyModal.waitFor({ state: 'hidden' });
    assert.deepEqual((await api('GET', 'activities/signin')).rows[0].values, signin.values);
    assert.equal((await api('POST', 'activities/signin/validate', blocked)).state, 'PREVIEWED');checks.push('legacy pending reviewed without native write or old-log rewrite; later preview unblocked');
    await page.goto(origin + '/admin/extension-operations');
    await page.locator('.arco-tabs-tab').filter({ hasText: '封禁管理' }).click();await page.getByPlaceholder('账号编号', { exact: true }).fill('1000');
    await page.getByRole('button', { name: '读取处罚', exact: true }).click();await page.getByPlaceholder('操作原因', { exact: true }).fill('synthetic browser ban');
    const banPreview = observe(page.waitForRequest(r => r.url().endsWith('/accounts/1000/ban/preview') && r.method() === 'POST'));
    await page.getByRole('button', { name: '预览处罚操作', exact: true }).click();const banBody = (await banPreview).postDataJSON();
    const banConfirm = page.getByRole('button', { name: '确认处罚预览', exact: true });
    await page.waitForFunction(() => [...document.querySelectorAll('button')].some(b => b.textContent.includes('确认处罚预览') && !b.disabled));
    assert.equal((await api('GET', 'accounts/1000/ban')).penalty, null);
    let banSaved = observe(page.waitForResponse(r => r.url().endsWith('/accounts/1000/ban') && r.request().method() === 'PUT'));
    await banConfirm.click();assert.equal((await (await banSaved).json()).state, 'PERSISTED');
    const fixedEnd = (await api('GET', 'accounts/1000/ban')).penalty.end_time;
    assert.equal((await context.request.post(origin + '/api/v1/login', { data: { account: 'fixture-player', password: 'synthetic-player-before-reset' } })).status(), 403);
    assert.equal((await api('PUT', 'accounts/1000/ban', banBody)).state, 'PERSISTED');assert.equal((await api('GET', 'accounts/1000/ban')).penalty.end_time, fixedEnd);
    await api('PUT', 'accounts/1000/ban', { ...banBody, days: 3 }, 409);checks.push('browser ban preview, persistence, replay and unchanged expiry');
    await page.locator('.arco-select').filter({ hasText: '封禁后续登录' }).click();await page.getByText('解除本后台处罚', { exact: true }).last().click();
    await page.getByPlaceholder('操作原因', { exact: true }).fill('synthetic browser unban');
    const unbanPreview = observe(page.waitForRequest(r => r.url().endsWith('/accounts/1000/ban/preview') && r.method() === 'POST'));
    await page.getByRole('button', { name: '预览处罚操作', exact: true }).click();const unbanBody = (await unbanPreview).postDataJSON();
    await page.waitForFunction(() => [...document.querySelectorAll('button')].some(b => b.textContent.includes('确认处罚预览') && !b.disabled));
    banSaved = observe(page.waitForResponse(r => r.url().endsWith('/accounts/1000/ban') && r.request().method() === 'DELETE'));
    await banConfirm.click();assert.equal((await (await banSaved).json()).state, 'PERSISTED');assert.equal((await api('GET', 'accounts/1000/ban')).penalty.apply_flag, 0);
    assert.equal((await context.request.post(origin + '/api/v1/login', { data: { account: 'fixture-player', password: 'synthetic-player-before-reset' } })).status(), 200);
    for (const [id, reason] of [[unbanBody.requestId, 'synthetic restore unban'], [banBody.requestId, 'synthetic undo original ban']]) {
      await page.getByRole('row').filter({ hasText: id }).getByRole('button', { name: '预览处罚恢复', exact: true }).click();
      await page.getByPlaceholder('操作原因', { exact: true }).fill(reason);await page.getByRole('button', { name: '预览处罚操作', exact: true }).click();
      await page.waitForFunction(() => [...document.querySelectorAll('button')].some(b => b.textContent.includes('确认处罚预览') && !b.disabled));
      banSaved = observe(page.waitForResponse(r => r.url().endsWith('/accounts/1000/ban/restore') && r.request().method() === 'POST'));
      await banConfirm.click();assert.equal((await (await banSaved).json()).state, 'PERSISTED');
    }
    assert.equal((await api('GET', 'accounts/1000/ban')).penalty, null);checks.push('browser unban, login rejection/reenabling and targeted penalty restores');
    const playerLogin = async password => context.request.post(origin + '/api/v1/login', { data: { account: 'fixture-player', password } });
    const beforeLogin = await playerLogin('synthetic-player-before-reset');assert.equal(beforeLogin.status(), 200);const oldPlayerToken = (await beforeLogin.text()).replace(/^"|"$/g, '');assert.match(oldPlayerToken, /^[a-f0-9]{32}$/);
    const ownRoles = playerToken => context.request.get(origin + '/api/v1/charac?page=1&pageSize=1', { headers: { Authorization: 'Bearer ' + playerToken } });assert.equal((await ownRoles(oldPlayerToken)).status(), 200);
    await page.locator('.arco-tabs-tab').filter({ hasText: '管理员改密' }).click();await page.getByPlaceholder('改密账号编号', { exact: true }).fill('1000');await page.getByRole('button', { name: '读取改密状态', exact: true }).click();
    await page.getByPlaceholder('填写8至64位玩家新密码', { exact: true }).fill('synthetic-player-after-reset');await page.getByPlaceholder('填写管理员改密或核对原因', { exact: true }).fill('synthetic administrator password reset');
    const passwordPreview = observe(page.waitForRequest(r => r.url().endsWith('/accounts/1000/password/preview') && r.method() === 'POST'));
    await page.getByRole('button', { name: '预览改密操作', exact: true }).click();const passwordBody = (await passwordPreview).postDataJSON();
    await page.waitForFunction(() => [...document.querySelectorAll('button')].some(b => b.textContent.includes('确认改密预览') && !b.disabled));
    assert.equal((await ownRoles(oldPlayerToken)).status(), 200);
    const passwordSaved = observe(page.waitForResponse(r => r.url().endsWith('/accounts/1000/password') && r.request().method() === 'PUT'));
    await page.getByRole('button', { name: '确认改密预览', exact: true }).click();assert.equal((await (await passwordSaved).json()).state, 'PERSISTED');
    assert.ok([401, 403].includes((await ownRoles(oldPlayerToken)).status()));
    const changedLogin = await playerLogin('synthetic-player-after-reset');assert.equal(changedLogin.status(), 200);const newPlayerToken = (await changedLogin.text()).replace(/^"|"$/g, '');
    assert.ok([400,401,403].includes((await playerLogin('synthetic-player-before-reset')).status()));
    assert.equal((await api('PUT', 'accounts/1000/password', passwordBody)).state, 'PERSISTED');assert.equal((await ownRoles(newPlayerToken)).status(), 200);
    const passwordHistory = JSON.stringify(await api('GET', 'accounts/1000/password/history'));assert.ok(!passwordHistory.includes('passwordTag'));assert.ok(!passwordHistory.includes('synthetic-player-after-reset'));
    checks.push('browser admin password reset, credential verification, session revocation and secret-free history');
    assert.deepEqual(errors, []);await page.screenshot({ path: path.join(output, 'activity-page.png'), fullPage: true });
    const result = { passed: true, backend: 'real Spring HTTP', database: 'fresh synthetic MySQL 5.6 fixture', actualGameDataAccessed: false, gameEffectVerified: false, checks, pageErrors: 0 };
    await writeFile(path.join(output, 'result.json'), JSON.stringify(result, null, 2) + '\n');console.log(JSON.stringify(result));
  } catch (error) {
    if (page) await page.screenshot({ path: path.join(output, 'failure.png'), fullPage: true }).catch(() => {});
    throw error;
  } finally { if (browser) await browser.close();await new Promise(resolve => server.close(resolve)); }
})().catch(error => { console.error(error.stack);process.exitCode = 1; });
