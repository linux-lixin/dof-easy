// Real HTTP/browser checks. Configuration edits are opt-in and intended for a copied candidate directory.
const assert = require('node:assert/strict');
const { readFile, mkdir, writeFile } = require('node:fs/promises');
const path = require('node:path');
const { randomUUID } = require('node:crypto');
const { parseArgs } = require('node:util');
const { chromium } = require(process.env.PLAYWRIGHT_MODULE || 'playwright');
const { values: args } = parseArgs({ options: {
  origin: { type: 'string' }, credentials: { type: 'string' }, instance: { type: 'string' }, output: { type: 'string' },
  'allow-config-edits': { type: 'boolean', default: false },
  'require-all-records': { type: 'boolean', default: false },
  'inspect-management': { type: 'boolean', default: false },
  'expect-native-runtime': { type: 'boolean', default: false },
} });
if (!args.origin || !args.credentials || !args.instance || !args.output) throw new Error('Required: --origin --credentials --instance --output; edits require --allow-config-edits');
const origin = new URL(args.origin).origin;
if (!['127.0.0.1', 'localhost', '[::1]'].includes(new URL(origin).hostname)) throw new Error('Use a private loopback SSH tunnel');
const root = '/api/v1/admin/dof', output = path.resolve(args.output), errors = [], checks = [], recovery = [];
const observe = promise => { promise.catch(() => {}); return promise; };

(async () => {
  await mkdir(output, { recursive: true });
  const credentials = JSON.parse(await readFile(args.credentials, 'utf8'));
  const browser = await chromium.launch({ headless: true });
  const context = await browser.newContext({ viewport: { width: 1440, height: 1100 }, acceptDownloads: true });
  let token, original, request, page;
  const api = async (method, suffix, data, expected = 200) => {
    const response = await context.request.fetch(origin + root + suffix, { method, data, headers: { Authorization: 'Bearer ' + token } });
    assert.equal(response.status(), expected, `${method} ${suffix} status`);
    return response.json();
  };
  const restore = async id => {
    const current = await api('GET', '/plugins/anton');
    const result = await api('POST', `/plugins/anton/history/${id}/restore`, { requestId: randomUUID(), revision: current.revision });
    assert.equal(result.persisted, true);
    recovery.splice(recovery.indexOf(id), 1);
  };
  try {
    page = await context.newPage();
    page.on('pageerror', error => errors.push(error.message));
    await page.route('**/*', route => new URL(route.request().url()).origin === origin ? route.continue() : route.abort());
    await page.goto(origin + '/login?redirect=/admin/dof');
    await page.getByPlaceholder('请输入用户名').fill(credentials.account);
    await page.getByPlaceholder('请输入密码').fill(credentials.password);
    const login = observe(page.waitForResponse(r => r.url().endsWith('/api/v1/login') && r.request().method() === 'POST'));
    await page.getByRole('button', { name: '登录', exact: true }).click();
    assert.equal((await login).status(), 200);
    await page.waitForURL(url => url.pathname === '/admin/dof');
    token = await page.evaluate(() => localStorage.getItem('token'));
    assert.ok(token);checks.push('real browser administrator login');
    const catalog = await api('GET', '');assert.equal(catalog.instanceId, args.instance);
    const anonymous = await context.request.get(origin + root + '/records');
    assert.ok([401, 403].includes(anonymous.status()));checks.push('anonymous access rejected');
    await page.locator('.arco-tabs-tab').filter({ hasText: '团本与插件' }).click();
    const panel = page.locator('.dof-manager > .arco-tabs > .arco-tabs-content > .arco-tabs-content-list > .arco-tabs-content-item-active');
    // The feature ledger has an identically named collapse header with role=button.
    await panel.locator('button').filter({ hasText: /^安徒恩团本$/ }).click();
    original = await api('GET', '/plugins/anton');assert.equal(original.instanceId, args.instance);
    assert.equal(original.application.webExecutionSupported, args['expect-native-runtime']);assert.equal(original.application.runtimeObserved, args['expect-native-runtime']);
    if (original.application.command) await page.getByText('当前 GM 刷新命令：').waitFor();
    checks.push('real plugin read and application guidance');
    if (args['expect-native-runtime']) {
      assert.equal(args['allow-config-edits'], false, 'Runtime acceptance uses the saved configuration; keep copied-directory edits separate');
      for (const [module, title] of [['anton', '安徒恩团本'], ['luke', '卢克团本']]) {
        const clickPost = async (button, suffix, beforeRetry) => {
          for (let attempt = 0; ; attempt++) {
            const pending = observe(page.waitForResponse(r => r.url().endsWith(suffix) && r.request().method() === 'POST'));
            await button().click();const reply = await pending, value = await reply.json();
            if (reply.status() === 200) return value;
            assert.equal(reply.status(), 409);assert.match(value.message, /另一个.*操作正在执行/);assert.ok(attempt < 15);
            if (beforeRetry) await beforeRetry(reply.request().postDataJSON());
            await page.waitForTimeout(170 + attempt * 35);
          }
        };
        await panel.locator('button').filter({ hasText: new RegExp('^' + title + '$') }).click();
        await panel.getByRole('heading', { name: '刷新已保存的团本配置' }).waitFor();
        const before = await api('GET', '/plugins/' + module);
        const frozen = await clickPost(() => panel.getByRole('button', { name: '预览原生刷新', exact: true }), `/${module}/runtime/preview`, async request => {
          const history = await api('GET', `/plugins/${module}/runtime/history`);
          assert.ok(!history.some(row => row.requestId === request.requestId), 'Failed initial preview must have no durable task before preparing another ID');
        });
        assert.equal(frozen.revision, before.revision);assert.equal(frozen.targets.length, 2);
        for (let attempt = 0; ; attempt++) {
          const submitReply = observe(page.waitForResponse(r => r.url().endsWith(`/${module}/runtime`) && r.request().method() === 'POST'));
          await panel.getByRole('button', { name: '提交本次刷新', exact: true }).click();
          const reply = await submitReply, submitted = await reply.json();
          if (reply.status() === 200) { assert.equal(submitted.requestId, frozen.requestId);break; }
          assert.equal(reply.status(), 409);assert.match(submitted.message, /另一个.*操作正在执行/);assert.ok(attempt < 15);
          // Exercise the user's explicit reconciliation/reload flow. Only a
          // confirmed PREVIEWED task may be submitted again, with its original ID.
          const reconciled = await clickPost(() => panel.getByRole('button', { name: '核对本次刷新', exact: true }), `/${frozen.requestId}/reconcile`);
          assert.equal(reconciled.state, 'PREVIEWED');
          await page.waitForTimeout(150 + attempt * 30);
          const loaded = await clickPost(() => panel.getByRole('row').filter({ hasText: frozen.requestId }).getByRole('button', { name: '加载预览', exact: true }), `/${module}/runtime/preview`);
          assert.equal(loaded.requestId, frozen.requestId);
        }
        let result;
        for (let attempt = 0; attempt < 25; attempt++) {
          result = await clickPost(() => panel.getByRole('button', { name: '核对本次刷新', exact: true }), `/${frozen.requestId}/reconcile`);
          if (['APPLIED', 'REJECTED', 'UNKNOWN'].includes(result.state)) break;
          await page.waitForTimeout(1000);
        }
        assert.equal(result.state, 'APPLIED');assert.equal(result.receipts.length, 2);
        assert.equal(result.gameplayVerified, false);
        for (const row of result.receipts) { assert.equal(row.state, 'APPLIED');assert.deepEqual(row.observed, result.expected); }
        assert.equal((await api('GET', '/plugins/' + module)).revision, before.revision);
        await panel.getByText('各频道已调用刷新，四项回读一致；团本流程、结算和奖励仍需实际验证。', { exact: true }).waitFor();
        checks.push(`real browser ${module} frozen preview, submit, two-channel native receipts and four-field readback`);
      }
    }
    if (args['allow-config-edits']) {
      const field = original.fields.find(f => f.key === 'anton_member_min' && f.available);
      assert.ok(field && /^[0-9]+$/.test(field.value));
      const next = String(Number(field.value) === 1 ? 2 : 1);
      await panel.getByPlaceholder('搜索配置名称或字段').fill('anton_member_min');
      const minimum = panel.locator('.arco-form-item').filter({ hasText: 'anton_member_min' }).locator('input');
      await minimum.fill(next);
      const previewRequest = observe(page.waitForRequest(r => r.url().endsWith('/anton/preview') && r.method() === 'POST'));
      await panel.getByRole('button', { name: '预览变更', exact: true }).click();
      request = (await previewRequest).postDataJSON();
      const save = panel.getByRole('button', { name: '保存本次预览', exact: true });await save.waitFor({ state: 'visible' });
      await page.waitForFunction(() => [...document.querySelectorAll('button')].some(b => b.textContent.includes('保存本次预览') && !b.disabled));
      assert.equal((await api('GET', '/plugins/anton')).revision, original.revision);
      const saved = observe(page.waitForResponse(r => r.url().endsWith('/plugins/anton') && r.request().method() === 'PUT'));
      await save.click();const result = await (await saved).json();assert.equal(result.persisted, true);recovery.push(request.requestId);
      assert.equal(result.requestId, request.requestId);checks.push('preview and real persistence');
      assert.equal((await api('PUT', '/plugins/anton', request)).requestId, request.requestId);checks.push('identical request replay');
      await api('POST', '/plugins/anton/preview', { ...request, requestId: randomUUID() }, 409);checks.push('stale revision rejection');
      const row = panel.getByRole('row').filter({ hasText: request.requestId });
      await row.getByRole('button', { name: '恢复本次修改', exact: true }).click();
      const restored = observe(page.waitForResponse(r => r.url().endsWith(`/history/${request.requestId}/restore`) && r.request().method() === 'POST'));
      await page.getByRole('button', { name: /^(确定|确认|OK)$/ }).click();
      assert.equal((await (await restored).json()).persisted, true);recovery.pop();
      assert.equal((await api('GET', '/plugins/anton')).revision, original.revision);checks.push('browser targeted restore with exact original hash');
      const downloaded = observe(page.waitForEvent('download'));await panel.getByRole('button', { name: '导出当前配置', exact: true }).click();
      const download = await downloaded;await download.saveAs(path.join(output, 'export.ini'));
      assert.equal(await readFile(path.join(output, 'export.ini'), 'utf8'), original.content);checks.push('real configuration export');
    }
    for (const key of ['signin-users', 'signin-checkins']) {
      const diagnosis = await api('GET', `/records/${key}/schema`);assert.equal(diagnosis.instanceId, args.instance);assert.equal(diagnosis.view.available, true);
      const result = await api('GET', `/records/${key}?page=1&size=1`);assert.ok(Array.isArray(result.rows));
    }
    checks.push('real signin schema and queries');
    const recordCatalog = await api('GET', '/records');
    const unavailable = recordCatalog.views.filter(v => !v.available);
    if (args['require-all-records']) assert.equal(unavailable.length, 0, 'record categories unavailable');
    let queried = 0;
    for (const view of recordCatalog.views.filter(v => v.available)) {
      const result = await api('GET', `/records/${view.key}?page=1&size=1`);
      assert.equal(result.instanceId, args.instance);assert.ok(Array.isArray(result.rows));queried++;
    }
    checks.push(`real read-only record projections: ${queried}/${recordCatalog.views.length}`);
    if (args['inspect-management']) {
      const get = async suffix => {
        const response = await context.request.get(origin + '/api/v1/' + suffix, { headers: { Authorization: 'Bearer ' + token } });
        assert.equal(response.status(), 200, 'management read ' + suffix);return response.json();
      };
      assert.ok(Array.isArray(await get('admin/activities')));
      assert.ok(Array.isArray((await get('admin/activities/rewards')).rows));
      assert.ok(Array.isArray(await get('admin/activities/rewards/history')));checks.push('deployed activity adapter and durable journal read');
      if (Number.isSafeInteger(credentials.uid) && credentials.uid > 0) {
        const ban = await get(`admin/accounts/${credentials.uid}/ban`);assert.equal(Number(ban.uid), credentials.uid);
        assert.ok(Array.isArray(await get(`admin/accounts/${credentials.uid}/ban/history`)));
        const passwordState = await get(`admin/accounts/${credentials.uid}/password-state`);assert.equal(passwordState.resetAllowed, false);
        const history = await get(`admin/accounts/${credentials.uid}/password/history`);assert.ok(Array.isArray(history));assert.ok(!JSON.stringify(history).includes('passwordTag'));
        checks.push('deployed penalty schema and protected-admin password-state/journal read without writes');
      }

      const roles = await get('charac?page=1&pageSize=1');
      if (roles.list.length) {
        const state = await get(`charac/${roles.list[0].characNo}/edit`);assert.equal(state.fields.length, 12);
        assert.equal(state.effectState, 'not_observed');assert.ok(['offline', 'online', 'unknown'].includes(state.onlineState));
        assert.ok(Array.isArray(await get(`charac/${roles.list[0].characNo}/history`)));checks.push('deployed role attribute schema, online diagnosis and history read');
      } else checks.push('role adapter sample unavailable: character list empty; no player data initialized');
    }

    await page.locator('.arco-tabs-tab').filter({ hasText: '记录查询' }).click();
    await page.getByRole('button', { name: '核对表结构', exact: true }).click();
    await page.getByText(/查询所需字段齐全，实际读取及权限仍需验证/).waitFor();checks.push('browser schema diagnosis');
    await page.locator('.arco-tabs-tab').filter({ hasText: '团本与插件' }).click();
    await page.screenshot({ path: path.join(output, 'plugin-live.png'), fullPage: true });
    if (args['inspect-management']) {
      await page.goto(origin + '/admin/extension-operations');await page.getByText(/统一清单：71 类、239 项/).waitFor();
      await page.getByPlaceholder('搜索编号、功能或缺口').fill('feature-010-01');
      const feature = page.getByRole('row').filter({ hasText: 'feature-010-01' });await feature.getByText('管理协议已有证据，原生效果待验', { exact: true }).waitFor();
      checks.push('feature ledger uses canonical 71 classes / 239 operations and current management evidence');
    }
    assert.deepEqual(errors, []);
    const result = { passed: true, backend: 'real', instanceId: args.instance, configEdits: args['allow-config-edits'], actualGameplayVerified: false, checks, pageErrors: 0 };
    await writeFile(path.join(output, 'result.json'), JSON.stringify(result, null, 2) + '\n');console.log(JSON.stringify(result));
  } catch (error) {
    if (page) {
      await page.screenshot({ path: path.join(output, 'failure.png'), fullPage: true }).catch(() => {});
      const tabs = await page.locator('.arco-tabs-tab').evaluateAll(nodes => nodes.map(n => ({ title: n.textContent, className: n.className })));
      const controls = await page.locator('button').filter({ hasText: '安徒恩团本' }).evaluateAll(nodes => nodes.map(n => {
        const ancestors=[];for(let p=n;p&&ancestors.length<7;p=p.parentElement)ancestors.push({tag:p.tagName,className:p.className,hidden:p.getAttribute('aria-hidden'),display:getComputedStyle(p).display});
        return {text:n.textContent,ancestors};
      }));
      await writeFile(path.join(output, 'failure.json'), JSON.stringify({ error: error.message, tabs, controls, pageErrors: errors, checks }, null, 2));
    }
    throw error;
  } finally {
    try { for (const id of [...recovery].reverse()) await restore(id); }
    finally { await browser.close(); }
  }
})().catch(error => { console.error(error.stack); process.exitCode = 1; });
