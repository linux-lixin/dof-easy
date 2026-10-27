// Compiled frontend with explicitly mocked read-only APIs. No server/database evidence.
const assert = require('node:assert/strict');
const { createServer } = require('node:http');
const { readFile, mkdir, writeFile } = require('node:fs/promises');
const path = require('node:path');
const { parseArgs } = require('node:util');
const { chromium } = require(process.env.PLAYWRIGHT_MODULE || 'playwright');
const { values: args } = parseArgs({ options: { dist: { type: 'string' }, output: { type: 'string' } } });
if (!args.dist || !args.output) throw new Error('Use --dist <compiled frontend> --output <private test directory>');
const dist = path.resolve(args.dist), output = path.resolve(args.output), errors = [], calls = [], checks = [];
const mime = { '.js': 'text/javascript', '.css': 'text/css', '.html': 'text/html', '.svg': 'image/svg+xml', '.png': 'image/png' };
const server = createServer(async (request, response) => {
  try {
    let file = path.resolve(dist, '.' + decodeURIComponent(new URL(request.url, 'http://localhost').pathname));
    if (file !== dist && !file.startsWith(dist + path.sep)) return response.writeHead(403).end();
    let bytes;
    try { bytes = await readFile(file); } catch { file = path.join(dist, 'index.html'); bytes = await readFile(file); }
    response.writeHead(200, { 'Content-Type': mime[path.extname(file)] || 'application/octet-stream' }).end(bytes);
  } catch { response.writeHead(500).end(); }
});
(async () => {
  await mkdir(output, { recursive: true });
  await new Promise(resolve => server.listen(0, '127.0.0.1', resolve));
  const origin = `http://127.0.0.1:${server.address().port}`;
  let browser;
  try {
    browser = await chromium.launch({ headless: true });
    const context = await browser.newContext({ viewport: { width: 1600, height: 1100 } });
    await context.addInitScript(() => localStorage.setItem('token', 'mock-postal-fixture'));
    const page = await context.newPage();page.on('pageerror', error => errors.push(error.message));
    await page.route('**/*', async route => {
      const request = route.request(), url = new URL(request.url());
      if (url.origin !== origin) return route.abort();
      if (!url.pathname.startsWith('/api/')) return route.continue();
      const json = (value, status = 200) => route.fulfill({ status, contentType: 'application/json', body: JSON.stringify(value) });
      if (url.pathname === '/api/v1/admin/launcher/runtime') return json({ mode: 'dof', nativeDevelopment: true });
      assert.equal(url.pathname, '/api/v1/postal');assert.equal(request.method(), 'GET');
      calls.push(Object.fromEntries(url.searchParams));
      if (url.searchParams.get('account') === 'fault') return json({ message: '模拟邮件表结构不兼容' }, 409);
      const current = Number(url.searchParams.get('page')), size = Number(url.searchParams.get('pageSize'));
      const total = url.searchParams.has('account') ? 0 : 25;
      const list = Array.from({ length: Math.max(0, Math.min(size, total - (current - 1) * size)) }, (_, i) => ({
        postalId: 100 - (current - 1) * size - i, occTime: '2026-01-02 23:59:59', sendCharacName: '模拟邮件',
        receiveCharacNo: '101', itemId: '5001', addInfo: 2, gold: 789, upgrade: 12, seperateUpgrade: 7,
        amplifyOption: 3, amplifyValue: 45, sealFlag: true, deleteFlag: false, letterId: 0,
      }));
      return json({ page: current, totalSize: total, totalPageSize: Math.ceil(total / size), list });
    });
    await page.goto(origin + '/admin/player/postal');
    await page.getByRole('columnheader', { name: '收件角色编号', exact: true }).waitFor();
    await page.getByText('模拟邮件', { exact: true }).first().waitFor();
    assert.ok(await page.getByText('邮件记录', { exact: true }).count());
    assert.equal(await page.getByRole('row').filter({ hasText: '模拟邮件' }).count(), 20);
    checks.push('navigation reaches compiled postal page and populated rows');
    const next = page.waitForResponse(r => new URL(r.url()).pathname === '/api/v1/postal' && new URL(r.url()).searchParams.get('page') === '2');
    await page.locator('.arco-pagination-item-next').click();await next;
    await page.waitForFunction(() => [...document.querySelectorAll('tbody tr')].filter(row => row.textContent.includes('模拟邮件')).length === 5);
    checks.push('next page preserves page size and displays five remaining rows');
    const input = page.getByPlaceholder('收件人账号（精确匹配，可选）');
    const search = async text => {
      await input.fill(text);
      const pending = page.waitForResponse(r => new URL(r.url()).pathname === '/api/v1/postal' && (new URL(r.url()).searchParams.get('account') || '') === text);
      await page.getByRole('button', { name: '查询邮件', exact: true }).click();return pending;
    };
    const payload = "missing' OR 1=1 -- &pageSize=100";
    assert.equal((await search(payload)).status(), 200);
    assert.equal(calls.at(-1).account, payload);assert.equal(calls.at(-1).pageSize, '20');assert.equal(calls.at(-1).page, '1');
    checks.push('literal account query cannot add URL parameters and resets pagination');
    assert.equal((await search('fault')).status(), 409);
    await page.locator('.arco-alert-content').getByText('模拟邮件表结构不兼容', { exact: true }).waitFor();
    assert.equal(await page.locator('.arco-table').count(), 0);
    checks.push('query error is visible and stale rows are hidden');
    await search('');await page.getByText('模拟邮件', { exact: true }).first().waitFor();
    assert.equal(await page.locator('.arco-alert-content').getByText('模拟邮件表结构不兼容', { exact: true }).count(), 0);
    checks.push('successful retry clears the error and restores populated results');
    assert.deepEqual(errors, []);
    await page.screenshot({ path: path.join(output, 'postal-page.png'), fullPage: true });
    await writeFile(path.join(output, 'result.json'), JSON.stringify({ passed: true, checks, pageErrors: errors.length, mockedApis: true, realDeploymentVerified: false }, null, 2));
    console.log(JSON.stringify({ passed: true, checks, pageErrors: errors.length, mockedApis: true }));
  } finally { if (browser) await browser.close();await new Promise(resolve => server.close(resolve)); }
})().catch(error => { console.error(error);process.exitCode = 1; });
