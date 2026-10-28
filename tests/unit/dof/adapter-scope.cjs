// Run with node tests/unit/dof/adapter-scope.cjs; mocked Frida, no network or real credentials.
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const vm = require('node:vm');
const logs = [];
let interceptor;
const pointer = value => ({ isNull: () => false, readUtf8String: () => value });
const secret = pointer('test-only-credential');
vm.runInNewContext(fs.readFileSync(path.join(__dirname,
  '../../../services/game/runtime/ubuntu/gateway/db_adapter.js'), 'utf8'), {
  Process: { arch: 'ia32', findModuleByAddress: address => address },
  Module: { getExportByName: () => 'getenv' },
  NativeFunction: function () { return () => secret; },
  Memory: { allocUtf8String: value => pointer(value) },
  Interceptor: { attach: (address, callbacks) => { interceptor = callbacks; } },
  ptr: value => value,
  console: { log: text => logs.push(text) }
});
for (const module of [{name: 'libdof-plugin.so'}, {name: 'libdof-inline.so'},
                     {name: 'libdp2.so'}, {name:'qk_plugin.so'}, {name:'libantisvrinline.so'}, null]) {
  for (const database of ['dof_native', 'taiwan_cain', 'taiwan_cain_2nd', 'd_taiwan', 'qk']) {
    for (const account of ['game', 'root']) {
      const original = pointer('original-test-value');
      const args = [null, null, null, pointer(database), pointer(account), original];
      const context = { returnAddress: module };
      interceptor.onEnter.call(context, args);
      const allowed = account === 'game' && (
        (module?.name === 'libdof-plugin.so' && ['dof_native', 'taiwan_cain_2nd'].includes(database))
        || (module?.name === 'libdof-inline.so'
          && ['dof_native', 'taiwan_cain', 'taiwan_cain_2nd'].includes(database)));
      assert.equal(args[5] !== original, allowed);
      interceptor.onLeave.call(context, { toInt32: () => 1 });
    }
  }
}
assert(logs.includes('[Dof DB adapter] libdof-plugin.so/dof_native connected'));
assert(logs.includes('[Dof DB adapter] libdof-plugin.so/taiwan_cain_2nd connected'));
assert(logs.includes('[Dof DB adapter] libdof-inline.so/dof_native connected'));
assert(logs.includes('[Dof DB adapter] libdof-inline.so/taiwan_cain connected'));
assert(logs.includes('[Dof DB adapter] libdof-inline.so/taiwan_cain_2nd connected'));
assert(logs.every(text => !text.includes('test-only-credential')));
console.log('60 caller/database/account combinations passed; no credential logged');
