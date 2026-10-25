// Emit the actual isolated probe stubs for offline instruction verification.
const fs=require('node:fs'),vm=require('node:vm'),crypto=require('node:crypto'),path=require('node:path');
const source=fs.readFileSync(path.resolve(__dirname,'server_equipment_probe.js'),'utf8');
function extract(start,end){return source.slice(source.indexOf('    function '+start),source.indexOf('    function '+end));}
const stubs=[];const pointer=n=>({toUInt32:()=>n>>>0,writeU32:()=>{}});
const context={user:pointer(0x33000000),Process:{getCurrentThreadId:()=>123},Memory:{alloc:()=>pointer(0x31000000)},NativeCallback:function(){return pointer(0x32000000);},finish:()=>{},taps:[],install:(at,expected,body)=>{stubs.push({at,expected,body});context.taps.push({});}};
vm.createContext(context);
vm.runInContext(extract('u32','jump')+extract('observe','finish')+extract('armNativePickup','exercise'),context);
vm.runInContext("observe('0x086c9184','5589e583ec28',function(){}); observe('0x080cb8fc','5589e583ec18',function(){}); observe('0x0811df08','5589e583ec18',function(){}); armNativePickup(10114);",context);
console.log(JSON.stringify({sourceSha256:crypto.createHash('sha256').update(source).digest('hex'),stubs}));
