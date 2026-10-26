// Execute the actual handler with native calls mocked; no game, network or keys.
const assert = require('node:assert/strict');
const fs = require('node:fs');
const vm = require('node:vm');
const source = fs.readFileSync('services/game/resources/plugins/frida/frida.js', 'utf8');
const begin = source.indexOf('function dp2_owns_item(');
const end = source.indexOf('//增加魔法封印装备的魔法封印等级', begin);
for (const mode of ['dp2-frida', 'frida', 'dp2', 'none']) {
  let completed = 0, crossed = 0;
  const scope = {
    Module: {getExportByName: () => null},
    NativeFunction: function () {return () => ({isNull: () => false, readUtf8String: () => mode});},
    Memory: {allocUtf8String: value => value},
    clear_all_quest_by_character_level: () => {completed++;},
    crossover: () => {crossed++;}
  };
  vm.createContext(scope);
  vm.runInContext(source.slice(begin, end), scope);
  for (const item of [2021458801, '2021458801', 2021458802, '2021458802']) {
    scope.use_item_handler({}, item);
  }
  assert.equal(completed, mode === 'dp2-frida' ? 0 : 2);
  assert.equal(crossed, mode === 'dp2-frida' ? 0 : 2);
  assert.equal(scope.dp2_owns_item(7577), false);
}
console.log('Actual task/crossover handlers: combined mode delegates to DP2; Frida-only preserved');

// Numeric IDs originate in hook_history_log; verify both API representations.
{
  let tasks = 0;
  const scope = { dp2_owns_item: () => false, clear_doing_quest: () => tasks++ };
  vm.createContext(scope);
  const start = source.indexOf('function use_item_handler(');
  vm.runInContext(source.slice(start, endOfHandler()), scope);
  scope.use_item_handler({}, 690000119);
  scope.use_item_handler({}, '690000119');
  assert.equal(tasks, 2);
}
function endOfHandler() { return source.indexOf('//增加魔法封印装备的魔法封印等级'); }
for (const selected of [[true,true,true],[false,true,true],[false,false,true],[false,false,false]]) {
  let resets = [], rewards = 0;
  const ids = [selected[0] ? 102040046 : 0, selected[1] ? 101550609 : 0, selected[2] ? 63040 : 0];
  const scope = {
    dp2_owns_item: () => false,
    INVENTORY_TYPE_ITEM: 0, INVENTORY_TYPE_AVARTAR: 1, INVENTORY_TYPE_CREATURE: 2,
    ENUM_ITEMSPACE_AVATAR: 1, ENUM_ITEMSPACE_CREATURE: 2,
    CUserCharacInfo_getCurCharacInvenW: () => ({}),
    CInventory_GetInvenRef: (_, type) => ({ type, add: () => ({ readU32: () => 0 }) }),
    Inven_Item_getKey: item => ids[item.type],
    Inven_Item_reset: item => resets.push(item.type),
    CUser_SendUpdateItemList: () => {}, CUser_send_itemspace: () => {},
    api_CUser_AddItem: () => rewards++, api_GameWorld_SendNotiPacketMessage: () => {}
  };
  vm.createContext(scope);
  vm.runInContext(source.slice(source.indexOf('function use_item_handler('), endOfHandler()), scope);
  vm.runInContext(source.slice(source.indexOf('function zbhs('), source.indexOf('function randomNumber(')), scope);
  scope.use_item_handler({}, 202145880);
  const first = selected.indexOf(true);
  assert.deepEqual(resets, first === -1 ? [] : [first]);
  assert.equal(rewards, first === -1 ? 0 : 1);
}
console.log('Task ID types and actual recycling functions: at most one removal and one reward per use');
