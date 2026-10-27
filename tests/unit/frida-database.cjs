// Actual shipped function bodies; native/database calls are explicitly mocked.
// This is control-flow evidence, not original component/gameplay acceptance.
const assert = require('node:assert/strict');
const fs = require('node:fs');
const vm = require('node:vm');
const source = fs.readFileSync('services/game/resources/plugins/frida/frida.js', 'utf8');
function body(name) {
  const start = source.indexOf('function ' + name + '(');
  assert.ok(start >= 0);
  const end = source.indexOf('\nfunction ', start + 10);
  assert.ok(end > start);
  return source.slice(start, end);
}
function fixture(failedDatabase, failTable = false) {
  const calls = [], opened = [], messages = [];let loaded = 0;
  const scope = {
    mysql_taiwan_cain: null, mysql_taiwan_cain_2nd: null, mysql_taiwan_billing: null, mysql_frida: null,
    global_config: { db_config: { account: 'game', password: 'synthetic-credential-never-log' } },
    log: line => messages.push(line),
    Memory: { allocUtf8String: sql => sql },
    api_MYSQL_open: database => { opened.push(database);return database === failedDatabase ? null : { isNull: () => false, database }; },
    MySQL_set_query_2: (handle, sql) => { assert.ok(handle && !handle.isNull());calls.push({ database: handle.database, sql }); },
    MySQL_exec: () => failTable && calls.at(-1).sql.startsWith('CREATE TABLE') ? 0 : 1,
    event_villageattack_load_from_db: () => { loaded++;return true; },
  };
  vm.createContext(scope);
  vm.runInContext(body('api_MySQL_exec') + '\n' + body('init_db'), scope);
  return { scope, calls, opened, messages, loaded: () => loaded };
}
{
  const f = fixture();assert.equal(f.scope.init_db(), true);assert.equal(f.loaded(), 1);
  assert.equal(f.opened.length, 4);assert.match(f.calls.at(-1).sql, /^CREATE TABLE IF NOT EXISTS game_event/);
  assert.equal(f.scope.init_db(), true);assert.equal(f.opened.length, 4);assert.equal(f.loaded(), 2);
}
for (const failed of ['taiwan_cain', 'taiwan_cain_2nd', 'taiwan_billing', 'frida']) {
  const f = fixture(failed);assert.equal(f.scope.init_db(), false);assert.equal(f.loaded(), 0);
  assert.ok(f.calls.every(row => row.database !== failed));assert.ok(f.messages.length > 0);
  assert.ok(!JSON.stringify(f.messages).includes('synthetic-credential'));
}
{
  const f = fixture(undefined, true);assert.equal(f.scope.init_db(), false);assert.equal(f.loaded(), 0);
  assert.match(f.messages.at(-1), /schema could not be prepared/);
}
for (const handle of [null, undefined, { isNull: () => true }]) {
  const f = fixture();assert.equal(f.scope.api_MySQL_exec(handle, 'synthetic SQL'), 0);assert.equal(f.calls.length, 0);
  assert.ok(!JSON.stringify(f.messages).includes('synthetic SQL'));
}
console.log('9 mocked Frida DB connection scenarios passed: null connections, startup failure, idempotent table preparation, existing handle reuse and no credential/SQL logging');
for (const [raw, rows, queryOK, expected] of [['{"progress":7,"custom":"keep"}',1,true,true],['{bad',1,true,false],['null',1,true,false],['[]',1,true,false],['7',1,true,false],['',0,true,true],['',2,true,false],['',0,false,false]]) {
  let writes=0;const original={progress:3};
  const scope={mysql_frida:{},mysql_frida_ready:false,villageAttackEventInfo:original,JSON,Array,
    MySQL_get_n_rows:()=>rows,MySQL_fetch:()=>{},api_MySQL_get_str:()=>raw,
    api_MySQL_exec:(_,sql)=>{if(sql.startsWith('replace')){writes++;return 1;}return queryOK?1:0;}};
  vm.createContext(scope);vm.runInContext(body('event_villageattack_load_from_db')+'\n'+body('event_villageattack_save_to_db'),scope);
  const loaded=scope.event_villageattack_load_from_db();assert.equal(loaded,expected);
  if(!loaded)assert.equal(scope.villageAttackEventInfo,original);
  assert.equal(scope.event_villageattack_save_to_db(),false);assert.equal(writes,0,'Never overwrite data before storage initialization');
  scope.mysql_frida_ready=loaded;scope.event_villageattack_save_to_db();assert.equal(writes,loaded?1:0);
  if(loaded&&rows===1)assert.equal(scope.villageAttackEventInfo.custom,'keep');
}
{
  const f=fixture();f.scope.event_villageattack_load_from_db=()=>false;
  assert.equal(f.scope.init_db(),false);assert.equal(f.scope.mysql_frida_ready,false);
}
console.log('9 mocked activity-load scenarios passed: malformed/unknown data retained, no save before successful initialization, unknown object fields preserved');
