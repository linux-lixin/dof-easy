// Source-owned Dof equipment announcements. Native bridge and pickup hook share
// this implementation. No item mutation, rewards, database writes or networking.
var DofEquipment = (function () {
    'use strict';
    function integer(n) {return typeof n==='number' && isFinite(n) && Math.floor(n)===n;}
    function validate(c) {
        var keys=['enabled','scope','rarities','itemIds','cooldownMs','duplicateMs','maxPerMinute'];
        if (!c || Object.keys(c).sort().join(',')!==keys.sort().join(',')) throw Error('Equipment configuration keys differ');
        if (typeof c.enabled!=='boolean' || c.scope!=='channel') throw Error('Equipment requires a boolean switch and channel scope');
        function list(v,lo,hi) {
            if(!Array.isArray(v)||v.length>256||v.some(function(n){return !integer(n)||n<lo||n>hi;})||v.some(function(n,i){return v.indexOf(n)!==i;})) throw Error('Invalid equipment filter');
        }
        list(c.rarities,0,6);list(c.itemIds,1,2147483647);
        [['cooldownMs',0,600000],['duplicateMs',1,60000],['maxPerMinute',1,1000]].forEach(function(row){
            if(!integer(c[row[0]])||c[row[0]]<row[1]||c[row[0]]>row[2])throw Error('Invalid equipment rate limit');
        });
        return JSON.parse(JSON.stringify(c));
    }
    function safeName(value,limit) {
        return String(value).replace(/[\x00-\x1f\x7f<>\[\]{}]/g,' ').trim().slice(0,limit);
    }
    function create(config,clock,send) {
        var c=validate(config),recent=Object.create(null),users=Object.create(null),sent=[];
        var stats={attempts:0,sent:0,filtered:0,duplicate:0,limited:0,errors:0};
        function prune(now) {
            Object.keys(recent).forEach(function(k){if(now-recent[k]>=c.duplicateMs)delete recent[k];});
            Object.keys(users).forEach(function(k){if(now-users[k]>=c.cooldownMs)delete users[k];});
            while(sent.length && now-sent[0]>=60000)sent.shift();
        }
        function publish(event) {
            stats.attempts++;
            if(!c.enabled||!event||!event.equipment||!integer(event.itemId)||event.itemId<=0||
               !integer(event.quantity)||event.quantity<=0||event.quantity>2147483647||
               !integer(event.characterId)||event.characterId<=0||
               (c.rarities.indexOf(event.rarity)<0 && c.itemIds.indexOf(event.itemId)<0)) {stats.filtered++;return 0;}
            var who=safeName(event.characterName,32),what=safeName(event.itemName,96);
            if(!who||!what){stats.filtered++;return 0;}
            var now=clock();prune(now);
            var key=event.characterId+':'+event.itemId+':'+event.quantity;
            if(Object.prototype.hasOwnProperty.call(recent,key)){stats.duplicate++;return 0;}
            if(Object.prototype.hasOwnProperty.call(users,event.characterId)||sent.length>=c.maxPerMinute){stats.limited++;return 0;}
            // Insert only after actual dispatch; failures must not count as sent.
            var message='恭喜玩家【'+who+'】获得【'+what+'】×'+event.quantity+'！';
            try {send(message,event);} catch(e){stats.errors++;throw e;}
            recent[key]=now;users[event.characterId]=now;sent.push(now);stats.sent++;return 1;
        }
        return {publish:publish,stats:stats,config:c};
    }
    var api={validate:validate,create:create};
    if(typeof Process==='undefined')return api;
    function boot() {
        if(Process.arch!=='ia32'||typeof dofEquipmentInput==='undefined')throw Error('Equipment runtime inputs absent');
        var input=dofEquipmentInput,plugin=Process.getModuleByName('libdof-plugin.so');
        function native(at,ret,args){return new NativeFunction(ptr(at),ret,args,{abi:'sysv',exceptions:'propagate',traps:'all'});}
        function hex(at,n){return Array.prototype.map.call(new Uint8Array(at.readByteArray(n)),function(b){return ('0'+b.toString(16)).slice(-2);}).join('');}
        var entry=plugin.base.add(input.abi.entry),slot=plugin.base.add(input.abi.dispatchSlot);
        if(hex(entry,input.abi.code.length/2)!==input.abi.code||!slot.readPointer().isNull())throw Error('Equipment bridge differs or is already registered');
        if(!plugin.getExportByName(input.abi.symbol).equals(entry))throw Error('Equipment export differs');
        var pickup=ptr('0x085b949c');
        if(hex(pickup,16)!=='5589e5565383ec208b5d10e8ef2cb1ff')throw Error('Unsupported equipment pickup ABI');
        var manager=native('0x080cc19b','pointer',[]),find=native('0x0835fa32','pointer',['pointer','int']);
        var rarity=native('0x080f12d6','int',['pointer']),stackable=native('0x080f12fa','uint8',['pointer']);
        var itemName=native('0x0811ed82','pointer',['pointer']);
        var charId=native('0x080cbc4e','int',['pointer']),charName=native('0x08101028','pointer',['pointer']);
        var world=native('0x080da3a7','pointer',[]),send=native('0x086c9184','int',['pointer','pointer','int']);
        var make=native('0x0858dd4c','void',['pointer']),destroy=native('0x0858de80','void',['pointer']);
        var header=native('0x080cb8fc','int',['pointer','int','int']);
        var byte=native('0x080cb920','int',['pointer','int']),short=native('0x080d9ea4','int',['pointer','int']);
        var integer=native('0x080cb93c','int',['pointer','int']);
        var binary=native('0x0811df08','int',['pointer','pointer','int']),finish=native('0x080cb958','int',['pointer','int']);
        // monotonic time, not the adjustable wall clock; timespec is two i386 longs.
        var monotonic=native(Module.getExportByName(null,'clock_gettime'),'int',['int','pointer']);
        var timespec=Memory.alloc(8);
        function now(){if(monotonic(1,timespec)!==0)throw Error('Equipment clock failed');return timespec.readU32()*1000+Math.floor(timespec.add(4).readU32()/1000000);}
        function channel(message) {
            var w=world();if(w.isNull())throw Error('Equipment channel is not initialized');
            var guard=Memory.alloc(128),text=Memory.allocUtf8String(message),length=0;
            while(length<1024 && text.add(length).readU8()!==0)length++;
            if(length>=1024)throw Error('Equipment message too long');
            make(guard);
            // PacketGuard borrows from the current game thread's PacketPool.
            // No pool means no buffer and no owned resource to release; its
            // native destructor also requires a non-null buffer in this case.
            if(guard.readPointer().isNull())throw Error('Equipment packet buffer unavailable on this thread');
            try {header(guard,0,12);byte(guard,14);short(guard,0);byte(guard,0);integer(guard,length);binary(guard,text,length);finish(guard,1);send(w,guard,3);}
            finally {destroy(guard);}
        }
        var policy=create(input.config,now,channel);
        function dispatch(unused,user,id,quantity) {
            try {
                if(user.isNull()||id<=0||quantity<=0)return 0;
                var cid=charId(user);if(cid<=0)return 0;
                var dm=manager();if(dm.isNull())return 0;
                var item=find(dm,id);if(item.isNull()||stackable(item))return 0;
                return policy.publish({equipment:true,itemId:id,quantity:quantity,rarity:rarity(item),
                    characterId:cid,characterName:charName(user).readUtf8String(),itemName:itemName(item).readUtf8String()});
            } catch(e) {console.error('[Dof equipment] dispatch failed: '+e);return -1;}
        }
        var callback=new NativeCallback(dispatch,'int',['pointer','pointer','int','int'],'sysv');
        slot.writePointer(callback);
        var listener=Interceptor.attach(pickup,{
            onEnter:function(args){this.user=args[1];this.item=args[2].toInt32();this.quantity=args[3].toInt32();},
            onLeave:function(){dispatch(ptr(0),this.user,this.item,this.quantity);}
        });
        // Retain callbacks for the life of the process. Stats are diagnostic;
        // no test-only configuration or bypass is exposed in production.
        api.stats=policy.stats;api.config=policy.config;api.callback=callback;api.listener=listener;
        api.entry=entry;api.slot=slot;api.ready=true;
        console.log('[Dof equipment] '+JSON.stringify({ready:true,scope:'channel',rarities:policy.config.rarities}));
    }
    // Gadget starts while the dynamic loader is still running constructors.
    // Looking up this plugin's exports there can wait on the loader lock. Bind
    // on the game initialization thread after constructors, before gameplay.
    var booted=false;
    api.initializer=Interceptor.attach(ptr('0x086c4134'),{onEnter:function(){
        if(booted)return;booted=true;
        try {boot();} catch(e) {
            var failure='[Dof equipment] startup failed: '+String(e)+'\n';
            var failureBytes=Memory.allocUtf8String(failure);
            var length=new NativeFunction(Module.getExportByName(null,'strlen'),'uint',['pointer'],{abi:'sysv'})(failureBytes);
            new NativeFunction(Module.getExportByName(null,'write'),'int',['int','pointer','uint'],{abi:'sysv'})(2,failureBytes,length);
            new NativeFunction(Module.getExportByName(null,'_exit'),'void',['int'],{abi:'sysv'})(78);
        }
    }});
    return api;
})();
if(typeof module!=='undefined' && module.exports)module.exports=DofEquipment;
