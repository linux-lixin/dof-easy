const assert = require('node:assert/strict');
const path = require('node:path');
const policy = require(path.resolve(__dirname,'../../../services/game/runtime/ubuntu/gateway/equipment.js'));
const defaults = require(path.resolve(__dirname,'../../../services/game/runtime/ubuntu/gateway/defaults/equipment.json'));
let now=10000, sent=[];
const p=policy.create(defaults,()=>now,(message,event)=>sent.push({message,event}));
const event={equipment:true,itemId:1001,quantity:1,rarity:4,characterId:7,characterName:'测试角色',itemName:'史诗装备'};
assert.equal(p.publish(event),1);assert.equal(sent[0].message,'恭喜玩家【测试角色】获得【史诗装备】×1！');
assert.equal(p.publish(event),0);assert.equal(p.stats.duplicate,1);
assert.equal(p.publish({...event,itemId:1002}),0);assert.equal(p.stats.limited,1);
now+=3001;assert.equal(p.publish(event),1);
for(const patch of [{rarity:3},{equipment:false},{itemId:-1},{quantity:0},{characterId:0},{itemName:''}])assert.equal(p.publish({...event,...patch}),0);
const white=policy.create({...defaults,itemIds:[88],maxPerMinute:1},()=>now,()=>{});
assert.equal(white.publish({...event,rarity:0,itemId:88}),1);
assert.equal(white.publish({...event,characterId:9}),0);
assert.equal(white.stats.limited,1);now+=60001;assert.equal(white.publish({...event,characterId:9}),1);
const disabled=policy.create({...defaults,enabled:false},()=>now,()=>{throw Error('disabled dispatch');});assert.equal(disabled.publish(event),0);
for(const patch of [{scope:'world'},{enabled:1},{rarities:[4,4]},{rarities:[9]},{itemIds:['1']},{extra:true},{maxPerMinute:0},{duplicateMs:0}])assert.throws(()=>policy.create({...defaults,...patch},()=>now,()=>{}));
const failed=policy.create(defaults,()=>now,()=>{throw Error('transport');});assert.throws(()=>failed.publish(event));assert.equal(failed.stats.sent,0);assert.equal(failed.stats.errors,1);
let text='';const sanitised=policy.create(defaults,()=>now,m=>text=m);sanitised.publish({...event,characterName:'a\n<b>',itemName:'[x]'});assert(!/[\n<>\[\]]/.test(text));
console.log('Equipment policy: filters, whitelist, duplicate/cooldown/channel rate limits, message, disabled/errors and configuration rejection passed');
// The bundled Gadget has an older engine. Modern host Node must not hide
// accidental dependencies on ES6 collections or Number.isInteger.
const vm=require('node:vm'),fs=require('node:fs');
const context={module:{exports:{}},Set:undefined,Map:undefined};vm.createContext(context);
vm.runInContext('Number.isInteger=undefined;',context);
vm.runInContext(fs.readFileSync(path.resolve(__dirname,'../../../services/game/runtime/ubuntu/gateway/equipment.js'),'utf8'),context);
const legacy=context.module.exports.create(defaults,()=>now,()=>{});
assert.equal(legacy.publish(event),1);assert.equal(legacy.publish(event),0);
