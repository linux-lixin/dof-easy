// Isolated fixture only. Raw native observers avoid this Gadget's suppression
// of nested Interceptor listeners. All original instructions still execute.
(function () {
    var started=false,active=false,packets=[],messages=[],messageBytes=[],headers=[],taps=[];
    var result={passed:false,playerLoggedIn:false,actualPickupVerified:false,clientDisplayVerified:false,nonTextPayloads:[]};
    var user=null,saved=null,constructed=false,baseline=0;
    function native(at,ret,args){return new NativeFunction(ptr(at),ret,args,{abi:'sysv',exceptions:'propagate'});}
    function hex(at,n){return Array.prototype.map.call(new Uint8Array(at.readByteArray(n)),function(b){return ('0'+b.toString(16)).slice(-2);}).join('');}
    function u32(a,n){n=n>>>0;a.push(n&255,(n>>>8)&255,(n>>>16)&255,(n>>>24)&255);}
    function report() {
        // The native log sink replaces non-ASCII console text. Escape JSON
        // values and retain bytes captured at put_binary before that sink.
        var text=JSON.stringify(result).replace(/[\u007f-\uffff]/g,function(c){return '\\u'+('0000'+c.charCodeAt(0).toString(16)).slice(-4);});
        console.log('[Dof equipment feature] '+text);
    }
    function jump(a,from,to){a.push(0xe9);u32(a,to.sub(from.add(a.length+4)).toUInt32());}
    function install(at,expected,body) {
        at=ptr(at);if(hex(at,6)!==expected)throw Error('Fixture observer ABI differs: '+at);
        var original=Array.prototype.slice.call(new Uint8Array(at.readByteArray(6)));
        var code=Memory.alloc(Process.pageSize),bytes=body.slice();
        bytes=bytes.concat(original);jump(bytes,code,at.add(6));
        code.writeByteArray(bytes);Memory.protect(code,Process.pageSize,'r-x');
        var redirect=[];jump(redirect,at,code);redirect.push(0x90);
        Memory.patchCode(at,6,function(p){p.writeByteArray(redirect);});
        taps.push({at:at,original:original,code:code});
    }
    function observe(at,expected,read) {
        var cb=new NativeCallback(function(stack){if(active)read(stack);},'void',['pointer'],'sysv');
        // At entry: preserve flags/registers and pass original esp to callback.
        // Maintain the original i386 caller's 16-byte stack alignment.
        var b=[0x9c,0x60,0x8d,0x44,0x24,0x24,0x83,0xec,0x04,0x50,0xb8];
        u32(b,cb.toUInt32());b=b.concat([0xff,0xd0,0x83,0xc4,0x08,0x61,0x9d]);
        install(at,expected,b);taps[taps.length-1].callback=cb;
    }
    function finish() {
        active=false;
        result.sentDelta=DofEquipment.stats.sent-baseline;
        result.packets=packets;result.headers=headers;result.messages=messages;result.messageBytes=messageBytes;
        result.hookDispatched=result.sentDelta===2;
        result.observer='native entry taps; original instructions retained';
        result.passed=result.nativeReturn===1&&result.rejected.duplicate===0&&result.rejected.ordinary===0&&result.rejected.invalid===0&&result.rejected.zero===0&&result.sentDelta===2&&packets.length===2&&packets.every(function(p){return p.minimumState===3&&p.world;})&&headers.filter(function(h){return h[0]===0&&h[1]===12;}).length===2&&messages.length===2&&messages.every(function(s){return s.indexOf('DofFixture')>=0&&s.indexOf('×1')>=0;});
        clean();report();
    }
    function clean() {
        active=false;
        taps.forEach(function(t){Memory.patchCode(t.at,6,function(p){p.writeByteArray(t.original);});});
        if(constructed){if(saved!==null)user.add(0x10).writePointer(saved);native('0x0864ef9c','void',['pointer'])(user);constructed=false;}
    }
    function armNativePickup(epic) {
        // Resume on a subsequent real game call to G_GameWorld, on the same
        // registered game thread. Never run pickup from a JS timer or nested
        // NativeFunction call: their instrumentation can be suppressed.
        var armed=Memory.alloc(4);armed.writeU32(1);
        var done=new NativeCallback(finish,'void',[],'sysv'),b=[0x9c,0x60],skips=[];
        function skip(){b.push(0x0f,0x85);skips.push(b.length);u32(b,0);}
        b.push(0x83,0x3d);u32(b,armed.toUInt32());b.push(1);skip();
        b.push(0xb8);u32(b,224);b.push(0xcd,0x80,0x3d);u32(b,Process.getCurrentThreadId());skip();
        // Only a return PC in game text, not an FFI trampoline, may trigger.
        b=b.concat([0x8b,0x44,0x24,0x24,0x3d]);u32(b,0x08000000);
        b.push(0x0f,0x82);skips.push(b.length);u32(b,0);
        b.push(0x3d);u32(b,0x08b00000);b.push(0x0f,0x83);skips.push(b.length);u32(b,0);
        b.push(0xc7,0x05);u32(b,armed.toUInt32());u32(b,0);
        b=b.concat([0x83,0xec,0x08,0x68]);u32(b,1);b.push(0x68);u32(b,epic);b.push(0x68);u32(b,user.toUInt32());b.push(0x6a,0,0xb8);u32(b,0x085b949c);
        b=b.concat([0xff,0xd0,0x83,0xc4,0x10,0xb8]);u32(b,done.toUInt32());b=b.concat([0xff,0xd0,0x83,0xc4,0x08]);
        var end=b.length;skips.forEach(function(at){var offset=end-at-4;for(var i=0;i<4;i++)b[at+i]=(offset>>>(8*i))&255;});
        b=b.concat([0x61,0x9d]);install('0x080da3a7','5589e583ec08',b);
        taps[taps.length-1].callback=done;taps[taps.length-1].armed=armed;
    }
    function exercise() {
        try {
            observe('0x086c9184','5589e583ec28',function(s){packets.push({minimumState:s.add(12).readS32(),world:!s.add(4).readPointer().isNull()});});
            observe('0x080cb8fc','5589e583ec18',function(s){headers.push([s.add(8).readS32(),s.add(12).readS32()]);});
            observe('0x0811df08','5589e583ec18',function(s){
                var n=s.add(12).readS32();if(n<=0||n>=1024)return;
                var p=s.add(8).readPointer(),raw=Array.prototype.slice.call(new Uint8Array(p.readByteArray(n))),text;
                // put_binary also carries non-text packet fields. Preserve their
                // bytes without turning a decoder exception into a script error.
                // Missing either expected announcement still fails finish().
                try{text=p.readUtf8String(n);}catch(e){result.nonTextPayloads.push({length:n,bytes:raw});return;}
                messages.push(text);messageBytes.push(raw);
            });
            var dm=native('0x080cc19b','pointer',[])(),find=native('0x0835fa32','pointer',['pointer','int']);
            var rarity=native('0x080f12d6','int',['pointer']),stack=native('0x080f12fa','uint8',['pointer']);
            // IDs located by the prior diagnostic scan, always validated against
            // actual loaded PVF metadata before the test can proceed.
            var epic=10114,ordinary=300,e=find(dm,epic),o=find(dm,ordinary);
            if(e.isNull()||o.isNull()||stack(e)||stack(o)||rarity(e)!==4||rarity(o)===4)throw Error('Fixture PVF equipment differs');
            user=Memory.alloc(2*1024*1024);native('0x0864e410','void',['pointer'])(user);constructed=true;
            saved=user.add(0x10).readPointer();
            var character=Memory.alloc(256);character.writeU32(900001);character.add(4).writeUtf8String('DofFixture');user.add(0x10).writePointer(character);
            var entry=Process.getModuleByName('libdof-plugin.so').getExportByName('_ZN5DBMgr19Equipment_BroadcastEP5CUserii');
            var broadcast=native(entry,'int',['pointer','pointer','int','int']);
            baseline=DofEquipment.stats.sent;active=true;
            result.nativeReturn=broadcast(ptr(0),user,epic,1);
            result.rejected={duplicate:broadcast(ptr(0),user,epic,1),ordinary:broadcast(ptr(0),user,ordinary,1),invalid:broadcast(ptr(0),user,2147483647,1),zero:broadcast(ptr(0),user,epic,0)};
            result.epic=epic;result.ordinary=ordinary;
            character.writeU32(900002);armNativePickup(epic);
            // Retain the synthetic character until the native completion hook.
            taps[taps.length-1].character=character;
        } catch(e) {result.error=String(e);clean();report();}
    }
    Interceptor.attach(ptr('0x086c4134'),{onLeave:function(r){if(!started&&r.toInt32()!==0){started=true;exercise();}}});
})();
