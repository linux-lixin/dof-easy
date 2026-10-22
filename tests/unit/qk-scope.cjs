// Run with node tests/unit/qk-scope.cjs; mocked Frida, no network or real credentials.
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const vm = require('node:vm');
const logs = [];
let interceptor;
const pointer = value => ({ isNull: () => false, readUtf8String: () => value });
const secret = pointer('test-only-credential');
vm.runInNewContext(fs.readFileSync(path.join(__dirname,
  '../../services/game/runtime/ubuntu/qk-db-adapter.js'), 'utf8'), {
  Process: { arch: 'ia32', findModuleByAddress: address => address },
  Module: { getExportByName: () => 'getenv' },
  NativeFunction: function () { return () => secret; },
  Memory: { allocUtf8String: value => pointer(value) },
  Interceptor: { attach: (address, callbacks) => { interceptor = callbacks; } },
  ptr: value => value,
  console: { log: text => logs.push(text) }
});
for (const module of [{name: 'qk_plugin.so'}, {name: 'libantisvrinline.so'},
                     {name: 'libdp2.so'}, null]) {
  for (const database of ['qk', 'taiwan_cain', 'taiwan_cain_2nd', 'd_taiwan']) {
    for (const account of ['game', 'root']) {
      const original = pointer('original-test-value');
      const args = [null, null, null, pointer(database), pointer(account), original];
      const context = { returnAddress: module };
      interceptor.onEnter.call(context, args);
      const allowed = account === 'game' && (
        (module?.name === 'qk_plugin.so' && ['qk', 'taiwan_cain_2nd'].includes(database))
        || (module?.name === 'libantisvrinline.so'
          && ['qk', 'taiwan_cain', 'taiwan_cain_2nd'].includes(database)));
      assert.equal(args[5] !== original, allowed);
      interceptor.onLeave.call(context, { toInt32: () => 1 });
    }
  }
}
assert(logs.includes('[QK DB adapter] qk_plugin.so/qk connected'));
assert(logs.includes('[QK DB adapter] qk_plugin.so/taiwan_cain_2nd connected'));
assert(logs.includes('[QK DB adapter] libantisvrinline.so/qk connected'));
assert(logs.includes('[QK DB adapter] libantisvrinline.so/taiwan_cain connected'));
assert(logs.includes('[QK DB adapter] libantisvrinline.so/taiwan_cain_2nd connected'));
assert(logs.every(text => !text.includes('test-only-credential')));
console.log('32 caller/database/account combinations passed; no credential logged');
