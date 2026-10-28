// Fixed raid configuration actions. No command, SQL, path or function comes from HTTP.
var DofRuntimeApply = (function () {
    'use strict';
    var pin='44b1481d503083227a298fb47b5d0f0c4b6a5bb4b4e8d07dfa88e46241a8a42b';
    function integer(x){return typeof x==='number'&&isFinite(x)&&Math.floor(x)===x;}
    function sha256(bytes) {
        var k=[0x428a2f98,0x71374491,0xb5c0fbcf,0xe9b5dba5,0x3956c25b,0x59f111f1,0x923f82a4,0xab1c5ed5,0xd807aa98,0x12835b01,0x243185be,0x550c7dc3,0x72be5d74,0x80deb1fe,0x9bdc06a7,0xc19bf174,0xe49b69c1,0xefbe4786,0x0fc19dc6,0x240ca1cc,0x2de92c6f,0x4a7484aa,0x5cb0a9dc,0x76f988da,0x983e5152,0xa831c66d,0xb00327c8,0xbf597fc7,0xc6e00bf3,0xd5a79147,0x06ca6351,0x14292967,0x27b70a85,0x2e1b2138,0x4d2c6dfc,0x53380d13,0x650a7354,0x766a0abb,0x81c2c92e,0x92722c85,0xa2bfe8a1,0xa81a664b,0xc24b8b70,0xc76c51a3,0xd192e819,0xd6990624,0xf40e3585,0x106aa070,0x19a4c116,0x1e376c08,0x2748774c,0x34b0bcb5,0x391c0cb3,0x4ed8aa4a,0x5b9cca4f,0x682e6ff3,0x748f82ee,0x78a5636f,0x84c87814,0x8cc70208,0x90befffa,0xa4506ceb,0xbef9a3f7,0xc67178f2];
        var h=[0x6a09e667,0xbb67ae85,0x3c6ef372,0xa54ff53a,0x510e527f,0x9b05688c,0x1f83d9ab,0x5be0cd19];
        var n=bytes.length,blocks=Math.ceil((n+9)/64),w=new Array(64);
        function rotate(x,n){return (x>>>n)|(x<<(32-n));}
        for(var block=0;block<blocks;block++){
            for(var i=0;i<16;i++){
                var word=0;
                for(var j=0;j<4;j++){
                    var at=block*64+i*4+j,v=at<n?bytes[at]:at===n?128:0;
                    if(at>=blocks*64-4)v=(n*8>>>(8*(blocks*64-at-1)))&255;
                    word=(word<<8)|v;
                }
                w[i]=word;
            }
            for(i=16;i<64;i++){
                var x=w[i-15],y=w[i-2];
                w[i]=((rotate(x,7)^rotate(x,18)^(x>>>3))+w[i-16]+(rotate(y,17)^rotate(y,19)^(y>>>10))+w[i-7])|0;
            }
            var a=h[0],b=h[1],c=h[2],d=h[3],e=h[4],f=h[5],g=h[6],z=h[7];
            for(i=0;i<64;i++){
                var t1=(z+(rotate(e,6)^rotate(e,11)^rotate(e,25))+((e&f)^(~e&g))+k[i]+w[i])|0;
                var t2=((rotate(a,2)^rotate(a,13)^rotate(a,22))+((a&b)^(a&c)^(b&c)))|0;
                z=g;g=f;f=e;e=(d+t1)|0;d=c;c=b;b=a;a=(t1+t2)|0;
            }
            var v=[a,b,c,d,e,f,g,z];for(i=0;i<8;i++)h[i]=(h[i]+v[i])|0;
        }
        return h.map(function(v){return ('00000000'+(v>>>0).toString(16)).slice(-8);}).join('');
    }
    function sameIdentity(a,b){return a&&a.schema===1&&a.instanceId===b.instanceId&&a.databaseUuid===b.databaseUuid&&a.bindingRevision===b.bindingRevision;}
    function create(io,input,channel,bootId) {
        if(!input||input.schema!==1||input.pluginSHA256!==pin||io.pluginSHA256!==pin||typeof io.threadId!=='function'||!integer(io.threadId())||io.threadId()<=0||!Array.isArray(input.channels)||input.channels.indexOf(channel)<0||!/^[0-9a-f]{32}$/.test(bootId))throw Error('Unsupported runtime apply binding');
        var keys=['schema','instanceId','databaseUuid','requestId','module','revision','bindingRevision','targets','expiresAt','submittedAt','expected'].sort().join(',');
        var fields=['member_min','name','open_day','open_time'];
        var nativeCalls={anton:0,luke:0};
        function receipt(request,state,reason,observed){
            var r={schema:1,instanceId:input.instanceId,databaseUuid:input.databaseUuid,bindingRevision:input.bindingRevision,
                channel:channel,bootId:bootId,executorThreadId:io.threadId(),requestId:request.requestId,module:request.module,revision:request.revision,
                state:state,updatedAt:io.now(),reason:reason||null};
            if(observed)r.observed=observed;
            io.atomic('receipt-'+channel+'-'+request.module+'.json',r);
        }
        function attempt(module) {
            var request=io.json('request-'+module+'.json',true);
            if(!request)return;
            if(Object.keys(request).sort().join(',')!==keys||!sameIdentity(request,input)||request.module!==module||
               !/^[A-Za-z0-9][A-Za-z0-9-]{7,63}$/.test(request.requestId)||!/^[0-9a-f]{64}$/.test(request.revision)||
               !integer(request.expiresAt)||!integer(request.submittedAt)||request.submittedAt>request.expiresAt||request.expiresAt-request.submittedAt>120000||
               !request.expected||Object.keys(request.expected).sort().join(',')!==fields.join(',')||
               fields.some(function(k){return typeof request.expected[k]!=='string'||request.expected[k].length>256||unescape(encodeURIComponent(request.expected[k])).length>254;})||!Array.isArray(request.targets))throw Error('Invalid fixed refresh request');
            var targets=request.targets.filter(function(t){return t.channel===channel;});
            if(targets.length!==1||targets[0].bootId!==bootId||targets[0].pid!==io.pid||targets[0].dispatcherThreadId!==io.threadId())return;
            var old=io.json('receipt-'+channel+'-'+module+'.json',true);
            if(old&&old.requestId===request.requestId)return; // RUNNING is deliberately never replayed.
            if(io.now()>request.expiresAt){receipt(request,'REJECTED','EXPIRED');return;}
            if(io.now()<request.submittedAt-5000)throw Error('Refresh clock is ahead of the game');
            if(sha256(io.config(module))!==request.revision){receipt(request,'REJECTED','STALE_CONFIG');return;}
            // This durable marker must succeed BEFORE entering the native function.
            receipt(request,'RUNNING');
            try {
                nativeCalls[module]++;
                io.reload(module);
                var observed=io.snapshot(module);
                if(sha256(io.config(module))!==request.revision||fields.some(function(k){return String(observed[k])!==request.expected[k];})){
                    receipt(request,'UNKNOWN','OBSERVED_MISMATCH',observed);return;
                }
                receipt(request,'APPLIED',null,observed);
            } catch(error) {
                // A failed completion write can leave RUNNING. Do not invoke twice.
                receipt(request,'UNKNOWN','NATIVE_OR_RECEIPT_ERROR');
            }
        }
        return {tick:function(){return io.lock(function(){
            if(sha256(io.binding())!==input.bindingRevision)throw Error('Runtime binding changed');
            var error=null;
            try {['anton','luke'].forEach(attempt);}catch(e){error='REQUEST_OR_RECEIPT_UNAVAILABLE';}
            var observed={};
            ['anton','luke'].forEach(function(module){observed[module]={revision:sha256(io.config(module)),values:io.snapshot(module)};});
            io.atomic('status-'+channel+'.json',{schema:1,instanceId:input.instanceId,databaseUuid:input.databaseUuid,
                bindingRevision:input.bindingRevision,pluginSHA256:io.pluginSHA256,channel:channel,bootId:bootId,pid:io.pid,
                updatedAt:io.now(),idle:true,dispatcherThreadId:io.threadId(),lastError:error,observed:observed,nativeInvocationAttempts:nativeCalls});
        });}};
    }
    var api={sha256:sha256,create:create,pluginSHA256:pin};
    if(typeof Process==='undefined')return api;
    if(typeof dofRuntimeApplyInput==='undefined'||dofRuntimeApplyInput===null)return api;
    function boot() {
        if(Process.arch!=='ia32'||Process.getCurrentThreadId()!==Process.id)throw Error('Runtime apply requires game main thread');
        var input=dofRuntimeApplyInput,base='/data/dof-easy/config',directory=base+'/.runtime-apply';
        var libc=Process.getModuleByName('libc.so.6');
        function native(name,ret,args){return new NativeFunction(libc.getExportByName(name),ret,args,{abi:'sysv',exceptions:'propagate'});}
        var systemOpen=new SystemFunction(libc.getExportByName('open'),'int',['pointer','int','int'],{abi:'sysv'});
        function open(path,flags,mode){var result=systemOpen(path,flags,mode);open.errno=result.errno;return result.value;}
        var read=native('read','int',['int','pointer','uint']);
        var write=native('write','int',['int','pointer','uint']),close=native('close','int',['int']);
        var sync=native('fsync','int',['int']),rename=native('rename','int',['pointer','pointer']),unlink=native('unlink','int',['pointer']);
        var systemFcntl=new SystemFunction(libc.getExportByName('fcntl'),'int',['int','int','pointer'],{abi:'sysv'});
        function fcntl(fd,command,value){var result=systemFcntl(fd,command,value);fcntl.errno=result.errno;return result.value;}
        var realpath=native('realpath','pointer',['pointer','pointer']);
        var fstat=native('fstat64','int',['int','pointer']);
        function regular(fd){var stat=Memory.alloc(128);if(fstat(fd,stat)!==0||(stat.add(16).readU32()&0xf000)!==0x8000)throw Error('Runtime file is not regular');}
        function directorySafe(path){var target=Memory.alloc(4096);if(realpath(Memory.allocUtf8String(path),target).isNull()||target.readUtf8String()!==path)throw Error('Runtime directory is missing or contains links');}
        directorySafe(base);directorySafe(directory);
        function bytes(path,max,optional){
            var fd=open(Memory.allocUtf8String(path),0x20800,0); // NOFOLLOW, NONBLOCK, read-only
            if(fd<0){if(optional&&open.errno===2)return null;throw Error('Cannot open fixed runtime file');}
            try {
                regular(fd);
                var buffer=Memory.alloc(max+1),length=0;
                while(length<=max){var n=read(fd,buffer.add(length),Math.min(32768,max+1-length));if(n<0)throw Error('Cannot read fixed runtime file');if(n===0)break;length+=n;}
                if(length>max)throw Error('Runtime file too large');
                return new Uint8Array(buffer.readByteArray(length));
            }finally{close(fd);}
        }
        var helperPath='/opt/dnf/compat/i386/libdof-admin-runtime.so';
        if(!integer(input.digestHelperBytes)||input.digestHelperBytes<1||input.digestHelperBytes>1048576||!/^[0-9a-f]{64}$/.test(input.digestHelperSHA256))throw Error('Native digest helper contract is invalid');
        var dlopen=native('dlopen','pointer',['pointer','int']),dlsym=native('dlsym','pointer',['pointer','pointer']);
        var helper=dlopen(Memory.allocUtf8String(helperPath),2);
        if(helper.isNull())throw Error('Native digest helper is unavailable');
        var version=dlsym(helper,Memory.allocUtf8String('dof_admin_digest_version'));
        var digestEntry=dlsym(helper,Memory.allocUtf8String('dof_admin_sha256_fd'));
        if(version.isNull()||digestEntry.isNull()||new NativeFunction(version,'int',[],{abi:'sysv'})()!==1)throw Error('Unsupported native digest helper ABI');
        var digestFile=new NativeFunction(digestEntry,'int',['int','uint','pointer'],{abi:'sysv',exceptions:'propagate'});
        function fileDigest(path,expectedBytes){
            var fd=open(Memory.allocUtf8String(path),0x20800,0);if(fd<0)throw Error('Cannot open verified runtime module');
            try {
                regular(fd);var digest=Memory.alloc(32);
                if(digestFile(fd,expectedBytes,digest)!==0)throw Error('Native runtime digest failed');
                return Array.prototype.map.call(new Uint8Array(digest.readByteArray(32)),function(b){return ('0'+b.toString(16)).slice(-2);}).join('');
            }finally{close(fd);}
        }
        if(fileDigest(helperPath,input.digestHelperBytes)!==input.digestHelperSHA256)throw Error('Native digest helper bytes differ from prepared image');
        function text(raw){if(raw===null)return null;var p=Memory.alloc(raw.length+1);p.writeByteArray(raw);p.add(raw.length).writeU8(0);return p.readUtf8String(raw.length);}
        var plugin=Process.getModuleByName('libdof-plugin.so');
        var canonical=Memory.alloc(4096);
        if(realpath(Memory.allocUtf8String(plugin.path),canonical).isNull()||canonical.readUtf8String()!=='/opt/dof-easy/lib/libdof-plugin.so')throw Error('Native module is outside the verified runtime');
        if(!integer(input.pluginBytes)||input.pluginBytes<1||input.pluginBytes>16777216)throw Error('Native module byte contract is invalid');
        var actualPlugin=fileDigest(canonical.readUtf8String(),input.pluginBytes);
        var random=Memory.alloc(16);if(native('getrandom','int',['pointer','uint','uint'])(random,16,0)!==16)throw Error('Cannot allocate refresh boot identity');
        var bootId=Array.prototype.map.call(new Uint8Array(random.readByteArray(16)),function(v){return ('0'+v.toString(16)).slice(-2);}).join('');
        var args=bytes('/proc/self/cmdline',4096,false),argv=[],part='';
        Array.prototype.forEach.call(args,function(b){if(b===0){argv.push(part);part='';}else part+=String.fromCharCode(b);});
        var channel=argv[1];
        function file(name){if(!/^(?:binding|request-(?:anton|luke)|(?:status|receipt)-(?:cain|diregie|siroco|prey|casillas|hilder)[0-9]{2}(?:-(?:anton|luke))?|(?:status|receipt)-local(?:-(?:anton|luke))?)\.json$/.test(name))throw Error('Unsupported runtime filename');return directory+'/'+name;}
        var io={pid:Process.id,pluginSHA256:actualPlugin,threadId:function(){return Process.getCurrentThreadId();},now:function(){return Date.now();},
            binding:function(){return bytes(file('binding.json'),16384,false);},
            config:function(module){if(['anton','luke'].indexOf(module)<0)throw Error('Unsupported raid');return bytes(base+'/'+module+'-raid.ini',131072,false);},
            json:function(name,optional){var raw=bytes(file(name),16384,optional);return raw===null?null:JSON.parse(text(raw));},
            atomic:function(name,value){
                var destination=file(name),temporary=directory+'/.tmp-'+bootId+'-'+name;
                var raw=Memory.allocUtf8String(JSON.stringify(value)),n=native('strlen','uint',['pointer'])(raw),fd=open(Memory.allocUtf8String(temporary),0x200c1,384);
                if(fd<0)throw Error('Cannot create runtime receipt');
                try {
                    var at=0;while(at<n){var count=write(fd,raw.add(at),n-at);if(count<=0)throw Error('Cannot write runtime receipt');at+=count;}
                    if(sync(fd)!==0)throw Error('Cannot sync runtime receipt');
                    if(rename(Memory.allocUtf8String(temporary),Memory.allocUtf8String(destination))!==0)throw Error('Cannot publish runtime receipt');
                    var parent=open(Memory.allocUtf8String(directory),0x30000,0);if(parent<0)throw Error('Cannot open runtime directory');
                    try{if(sync(parent)!==0)throw Error('Cannot sync runtime directory');}finally{close(parent);}
                }finally{close(fd);unlink(Memory.allocUtf8String(temporary));}
            },
            lock:function(action){
                directorySafe(base);directorySafe(directory);
                var fd=open(Memory.allocUtf8String(base+'/.admin-operation.lock'),0x20842,384);
                if(fd<0)throw Error('Cannot open shared configuration lock');
                var lock=Memory.alloc(16);lock.writeByteArray(new Uint8Array(16));lock.writeU16(1);
                try {regular(fd);if(fcntl(fd,6,lock)!==0){if([11,13].indexOf(fcntl.errno)>=0)return false;throw Error('Cannot lock game configuration');}action();return true;}
                finally{close(fd);}
            },
            reload:function(module){var symbol=module==='anton'?'_ZN5anton11init_configEv':'_ZN4luke11init_configEv';new NativeFunction(plugin.getExportByName(symbol),'void',[],{abi:'sysv',exceptions:'propagate'})();},
            snapshot:function(module){var out={};['name','open_day','open_time'].forEach(function(k){var p=plugin.getExportByName('get_'+module+'_'+k),n=0;while(n<255&&p.add(n).readU8()!==0)n++;if(n===255)throw Error('Unterminated native configuration string');out[k]=n===0?'':p.readUtf8String(n);});out.member_min=String(plugin.getExportByName('get_'+module+'_member_min').readS32());return out;}
        };
        var policy=create(io,input,channel,bootId),last=0,dispatcherThread=0;
        function tick(){
            var thread=Process.getCurrentThreadId();
            if(!dispatcherThread){dispatcherThread=thread;console.log('[Dof runtime apply dispatcher] '+JSON.stringify({pid:Process.id,threadId:thread}));}
            if(thread!==dispatcherThread||Date.now()-last<2000)return;last=Date.now();
            try{policy.tick();}catch(e){console.error('[Dof runtime apply] channel observation unavailable: '+String(e));}
        }
        // The production ELF preflight pins this TimerDispatcher entry before
        // loading any scripts. Native work runs on its actual dispatcher thread,
        // not on the JS timer thread or an assumed process-main-thread ID.
        api.timer=Interceptor.attach(ptr('0x08632a18'),{onLeave:tick});
    }
    var initialized=false;
    api.initializer=Interceptor.attach(ptr('0x086c4134'),{onLeave:function(value){
        if(initialized||value.toInt32()===0)return;initialized=true;
        try{boot();}catch(e){console.error('[Dof runtime apply] startup unavailable: '+String(e));}
    }});
    return api;
})();
if(typeof module!=='undefined'&&module.exports)module.exports=DofRuntimeApply;
