// Browser interaction regression against compiled assets and explicitly mocked APIs.
// No real login, database, native tool, or game server is used.
const assert = require('node:assert/strict');
const { createServer } = require('node:http');
const { readFile, mkdir } = require('node:fs/promises');
const path = require('node:path');
const { createHash } = require('node:crypto');
const { parseArgs } = require('node:util');
const { chromium } = require(process.env.PLAYWRIGHT_MODULE || 'playwright');

const { values: args } = parseArgs({ options: { dist: { type: 'string' }, output: { type: 'string' } } });
if (!args.dist || !args.output) throw new Error('Use --dist <compiled frontend> --output <private test directory>');
const dist = path.resolve(args.dist), output = path.resolve(args.output);
const state = {
  plugin: { Inlay_function: '0' },
  anton: { anton_member_min: '1', anton_name: '安徒恩' },
  luke: { luke_member_min: '2' },
};
const titles = { plugin: '基础插件', anton: '安徒恩团本', luke: '卢克团本' };
const rows = [], calls = [], errors = [];
const revision = module => createHash('sha256').update(JSON.stringify(state[module])).digest('hex');
const content = module => '// MOCK BROWSER FIXTURE ONLY\n' + Object.entries(state[module]).map(([k, v]) => `${k} = ${v}`).join('\n') + '\nunknown_setting = keep\n';
function view(module) {
  return { module, title: titles[module], instanceId: 'dof-browser-fixture', revision: revision(module),
    content: content(module), encoding: 'UTF-8', activation: '模拟界面测试数据，仅验证网页交互。', effectState: 'not_observed',
    application: { configuredEnabled: true, dependencyReady: true, webExecutionSupported: false, runtimeObserved: false, instruction: '模拟生效指引', command: module === 'anton' ? '模拟刷新命令' : null },
    fields: Object.entries(state[module]).map(([key, value]) => ({ key, label: key.endsWith('member_min') ? '最少开团人数' : key.endsWith('name') ? '团本名称' : '装备镶嵌', kind: 'text', value, available: true })) };
}
const mime = { '.js': 'text/javascript', '.css': 'text/css', '.html': 'text/html', '.svg': 'image/svg+xml', '.png': 'image/png', '.jpg': 'image/jpeg' };
const server = createServer(async (request, response) => {
  try {
    const url = new URL(request.url, 'http://localhost');
    let file = path.resolve(dist, '.' + decodeURIComponent(url.pathname));
    if (file !== dist && !file.startsWith(dist + path.sep)) { response.writeHead(403).end(); return; }
    let data;
    try { data = await readFile(file); } catch { file = path.join(dist, 'index.html'); data = await readFile(file); }
    response.writeHead(200, { 'Content-Type': mime[path.extname(file)] || 'application/octet-stream' }); response.end(data);
  } catch { response.writeHead(500).end(); }
});

(async () => {
  await mkdir(output, { recursive: true });
  await new Promise(resolve => server.listen(0, '127.0.0.1', resolve));
  const origin = `http://127.0.0.1:${server.address().port}`;
  let browser;
  try {
    browser = await chromium.launch({ headless: true });
    const context = await browser.newContext({ viewport: { width: 1440, height: 1000 }, acceptDownloads: true });
    await context.addInitScript(() => localStorage.setItem('token', 'mock-fixture-not-a-server-token'));
    const page = await context.newPage();
    const afterHistory = action => Promise.all([
      page.waitForResponse(r => r.url().endsWith('/anton/history') && r.ok()), action(),
    ]);
    page.on('pageerror', error => errors.push(error.message));
    await page.route('**/*', async route => {
      const request = route.request(), url = new URL(request.url());
      if (url.origin !== origin) { await route.abort(); return; }
      if (!url.pathname.startsWith('/api/')) { await route.continue(); return; }
      const key = url.pathname.replace('/api/v1/admin/dof', ''), method = request.method();
      const body = method === 'GET' ? null : request.postDataJSON();
      calls.push({ method, path: url.pathname, body });
      const json = (data, status = 200) => route.fulfill({ status, contentType: 'application/json', body: JSON.stringify(data) });
      if (url.pathname === '/api/v1/admin/launcher/runtime') return json({ mode: 'dof', nativeDevelopment: true, configurationOwner: 'web-admin' });
      if (key === '') return json({ instanceId: 'dof-browser-fixture', scope: { baselineClasses: 71, baselineOperations: 239, discoveredOperations: 239, nativeMenus: [], groups: [] }, executionPlan: { packages: [] } });
      if (key === '/ui' || key === '/home') return json({ message: '模拟：本实例 UI 结构不兼容' }, 409);
      if (key === '/ui/history') return json([]);
      if (key === '/plugins') return json({ pluginMode: 'dp2', raidExtension: 'on', modules: Object.keys(state).map(id => ({ id, title: titles[id], available: true, dependencyReady: true })) });
      if (key === '/records') return json({ views: [
        { key: 'signin-users', title: '签到进度', available: false, reason: '模拟缺少 last_checkin_date' },
        { key: 'signin-storage', title: '签到奖励状态', available: true, ownerAvailable: false, periodAvailable: false, searchFields: [] },
      ] });
      if (key === '/records/signin-storage') return json({ rows: [{ UID: '42', num: '2' }], hasMore: false, observedAt: '2026-10-26T00:00:00Z', notice: '模拟记录', view: { columns: [{ key: 'UID', label: '玩家编号' }, { key: 'num', label: '签到次数' }] } });
      if (key === '/records/signin-storage/schema') return json({ view: { database: 'dof_native', table: 'sign_in_storage', available: true }, fields: [{ key: 'UID', present: true, dataType: 'int' }], databaseNow: '2026-10-26 12:00:00', databaseTimezone: 'SYSTEM', systemTimezone: 'CST', notice: '模拟只读结构诊断' });
      const match = key.match(/^\/plugins\/(plugin|anton|luke)(.*)$/);
      if (!match) return json({ message: 'Unexpected mocked API' }, 404);
      const [, module, suffix] = match;
      if (method === 'GET' && suffix === '') return json(view(module));
      if (method === 'GET' && suffix === '/history') return json(rows.filter(r => r.module === module).map(({ before, after, ...publicRow }) => publicRow));
      if (suffix === '/preview' || (method === 'PUT' && suffix === '')) {
        if (body.revision !== revision(module)) return json({ message: '模拟配置已被其他操作修改' }, 409);
        const patch = body.values || Object.fromEntries(Object.keys(state[module]).map(k => [k, body.content.match(new RegExp(`^${k} = (.*)$`, 'm'))?.[1] || state[module][k]]));
        const changes = Object.keys(patch).filter(k => patch[k] !== state[module][k]).map(k => ({ key: k, before: state[module][k], after: patch[k] }));
        if (suffix === '/preview') return json({ changes, persisted: false });
        const before = { ...state[module] }; Object.assign(state[module], patch);
        const record = { requestId: body.requestId, module, actor: 1, state: 'PERSISTED', persisted: true, createdAt: new Date().toISOString(), before, after: { ...state[module] } };
        rows.unshift(record); return json({ requestId: record.requestId, state: record.state, persisted: true });
      }
      const restore = suffix.match(/^\/history\/([^/]+)\/restore$/);
      if (restore) {
        const target = rows.find(r => r.requestId === restore[1]);
        if (!target || body.revision !== revision(module)) return json({ message: '模拟恢复冲突' }, 409);
        assert.notEqual(body.requestId, target.requestId);
        const before = { ...state[module] };
        for (const key of Object.keys(target.before)) if (target.before[key] !== target.after[key]) {
          if (state[module][key] !== target.after[key]) return json({ message: '模拟恢复冲突' }, 409);
          state[module][key] = target.before[key];
        }
        target.state = 'RESTORED'; rows.unshift({ requestId: body.requestId, module, actor: 1, state: 'PERSISTED', persisted: true, createdAt: new Date().toISOString(), before, after: { ...state[module] } });
        return json({ requestId: body.requestId, state: 'PERSISTED', persisted: true });
      }
      return json({ message: 'Unexpected mocked operation' }, 404);
    });
    await page.goto(origin + '/admin/dof');
    await page.getByText(/游戏 UI 配置或历史暂不可用/).waitFor();
    await page.locator('.arco-tabs-tab').filter({ hasText: '团本与插件' }).click();
    await page.getByRole('button', { name: '安徒恩团本', exact: true }).click();
    const minimum = page.locator('.arco-form-item').filter({ hasText: 'anton_member_min' }).locator('input');
    await minimum.waitFor();
    await page.getByText('当前 GM 刷新命令：').waitFor();
    assert.equal(await minimum.inputValue(), '1');
    await minimum.fill('2');
    await page.getByRole('button', { name: '预览变更', exact: true }).click();
    const save = page.getByRole('button', { name: '保存本次预览', exact: true });
    await save.waitFor(); await page.waitForFunction(() => [...document.querySelectorAll('button')].some(b => b.textContent.includes('保存本次预览') && !b.disabled));
    assert.equal(state.anton.anton_member_min, '1');
    const firstPreview = calls.filter(c => c.method === 'POST' && c.path.endsWith('/anton/preview')).at(-1).body;
    await afterHistory(() => save.click());
    assert.equal(state.anton.anton_member_min, '2');
    assert.equal(calls.filter(c => c.method === 'PUT').at(-1).body.requestId, firstPreview.requestId);
    state.anton.anton_member_min = '3';
    await minimum.fill('4'); await page.getByRole('button', { name: '预览变更', exact: true }).click();
    await page.getByText('模拟配置已被其他操作修改', { exact: true }).first().waitFor();
    assert.equal(state.anton.anton_member_min, '3'); assert.equal(await save.isDisabled(), true);
    await afterHistory(() => page.getByRole('button', { name: '重新读取', exact: true }).click());
    await page.locator('input[type=file]').first().setInputFiles({ name: 'anton.ini', mimeType: 'text/plain', buffer: Buffer.from(content('anton').replace('anton_member_min = 3', 'anton_member_min = 5')) });
    await page.getByRole('button', { name: '预览变更', exact: true }).click();
    await page.waitForFunction(() => [...document.querySelectorAll('button')].some(b => b.textContent.includes('保存本次预览') && !b.disabled));
    const imported = calls.filter(c => c.method === 'POST' && c.path.endsWith('/anton/preview')).at(-1).body;
    assert.ok(imported.content.includes('anton_member_min = 5')); assert.equal(imported.values, undefined);
    assert.equal(state.anton.anton_member_min, '3');
    await afterHistory(() => save.click());
    assert.equal(state.anton.anton_member_min, '5');
    const download = page.waitForEvent('download'); await page.getByRole('button', { name: '导出当前配置', exact: true }).click();
    const exported = await download; await exported.saveAs(path.join(output, 'export.ini'));
    assert.ok((await readFile(path.join(output, 'export.ini'), 'utf8')).includes('unknown_setting = keep'));
    const target = rows[0];
    await page.getByRole('row').filter({ hasText: target.requestId }).getByRole('button', { name: '恢复本次修改', exact: true }).click();
    await afterHistory(() => page.getByRole('button', { name: /^(确定|确认|OK)$/ }).click());
    assert.equal(state.anton.anton_member_min, '3');
    await page.locator('.arco-tabs-tab').filter({ hasText: '记录查询' }).click();
    await page.getByText('42', { exact: true }).waitFor();
    assert.ok(calls.some(c => c.path.endsWith('/records/signin-storage')));
    assert.ok(!calls.some(c => c.path.endsWith('/records/signin-users')));
    await page.getByRole('button', { name: '核对表结构', exact: true }).click();
    await page.getByText(/模拟只读结构诊断/).waitFor();
    assert.ok(calls.some(c => c.path.endsWith('/records/signin-storage/schema')));
    await page.locator('.arco-tabs-tab').filter({ hasText: '团本与插件' }).click();
    await page.screenshot({ path: path.join(output, 'plugin-page.png'), fullPage: true });
    assert.deepEqual(errors, []);
    console.log(JSON.stringify({ passed: true, backend: 'mocked', realServerAccessed: false,
      scenarios: ['UI failure isolation', 'module selection', 'preview before save', 'request identity', 'stale revision rejection', 'import and export', 'targeted restore', 'available record selection', 'schema diagnosis', 'runtime application guidance'], pageErrors: errors.length }));
  } finally {
    if (browser) await browser.close();
    await new Promise(resolve => server.close(resolve));
  }
})().catch(error => { console.error(error.stack); process.exitCode = 1; });
