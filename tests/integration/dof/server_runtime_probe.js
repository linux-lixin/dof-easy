// Observation only. Run through the test Gadget in a private local namespace.
// Never changes native arguments, results, code or deferred symbol bindings.
(function observeDofServer() {
    'use strict';
    if (Process.arch !== 'ia32') throw new Error('Server observation requires ia32');
    var symbol = '_ZN5DBMgr19Equipment_BroadcastEP5CUserii';
    var observations = 0;
    var sql = { calls: 0, oldDatabase: 0, oldConfigTable: 0 };
    var checked = false;
    // Observe the real OpenSSL reader used by the inline library. No key bytes
    // or private pointer values are published; the result must be non-null.
    Interceptor.attach(ptr('0x087f0aa0'), {
        onEnter: function () {
            var caller = Process.findModuleByAddress(this.returnAddress);
            this.inlineCaller = caller && caller.name === 'libdof-inline.so';
        },
        onLeave: function (result) {
            if (this.inlineCaller) console.log('[Dof private key reader] ' +
                JSON.stringify({returnedKey: !result.isNull()}));
        }
    });
    Interceptor.attach(ptr('0x087eb2e0'), {
        onEnter: function () {
            var caller = Process.findModuleByAddress(this.returnAddress);
            this.inlineCaller = caller && caller.name === 'libdof-inline.so';
        },
        onLeave: function (result) {
            if (this.inlineCaller) console.log('[Dof private key size] ' +
                JSON.stringify({bytes: result.toInt32()}));
        }
    });
    Interceptor.attach(ptr('0x086c4134'), {
        onLeave: function (result) {
            if (checked || result.toInt32() === 0) return;
            checked = true;
            var open = new NativeFunction(Module.getExportByName(null, 'dlopen'),
                'pointer', ['pointer', 'int'], {abi: 'sysv'});
            var error = new NativeFunction(Module.getExportByName(null, 'dlerror'),
                'pointer', [], {abi: 'sysv'});
            var close = new NativeFunction(Module.getExportByName(null, 'dlclose'),
                'int', ['pointer'], {abi: 'sysv'});
            var lookup = new NativeFunction(Module.getExportByName(null, 'dlsym'),
                'pointer', ['pointer', 'pointer'], {abi: 'sysv'});
            error();
            var handle = open(Memory.allocUtf8String('/opt/dof-easy/lib/libdof-plugin.so'), 6);
            var resolved = handle.isNull() ? ptr(0) : lookup(handle, Memory.allocUtf8String(symbol));
            var message = error();
            console.log('[Dof strict binding] ' + JSON.stringify({
                afterGameWorldInitialization: true, passed: !resolved.isNull(),
                loaderAcceptedReopen: !handle.isNull(), symbolResolved: !resolved.isNull(),
                error: message.isNull() ? null : message.readUtf8String()
            }));
            if (!handle.isNull()) close(handle);
            var plugin = Process.findModuleByName('libdof-plugin.so');
            var sites = [];
            Process.enumerateRangesSync('r-x').filter(function (range) {
                return range.base.compare(plugin.base) >= 0 && range.base.compare(plugin.base.add(plugin.size)) < 0;
            }).forEach(function (range) {
                Memory.scanSync(range.base, range.size, '68 05 f0 ff').forEach(function (hit) {
                    try {
                        var insn = Instruction.parse(hit.address.sub(2));
                        if (insn.mnemonic === 'lea' && insn.size === 6)
                            sites.push({offset: hit.address.sub(2).sub(plugin.base).toString(), instruction: insn.toString()});
                    } catch (_) {}
                });
            });
            console.log('[Dof random-box SQL references] ' + JSON.stringify({sites: sites, observedAfterGameWorld: true}));
            var tags = {};
            [ ['rarity', 0x30a00], ['randomBox', 0x30a20] ].forEach(function (row) {
                var getter = new NativeFunction(plugin.base.add(row[1]), 'pointer', [], {abi: 'sysv'});
                tags[row[0]] = getter().readUtf8String();
            });
            console.log('[Dof native tag getters] ' + JSON.stringify(tags));
            var names = typeof dofExpectedAbiNames === 'undefined' ? [] : dofExpectedAbiNames;
            var abiHandle = open(Memory.allocUtf8String('/opt/dof-easy/lib/libdof-plugin.so'), 5);
            var abi = names.map(function (row) {
                var nativeNew = lookup(abiHandle, Memory.allocUtf8String(row.new));
                var nativeOld = lookup(abiHandle, Memory.allocUtf8String(row.old));
                return {name: row.new,
                    newFound: !nativeNew.isNull(), oldAbsent: nativeOld.isNull(),
                    fridaNewFound: Module.findExportByName(plugin.name, row.new) !== null,
                    fridaFullPathFound: Module.findExportByName(plugin.path, row.new) !== null,
                    fridaOldAbsent: Module.findExportByName(plugin.name, row.old) === null};
            });
            function nativeRtti(name) {
                var result = lookup(abiHandle, Memory.allocUtf8String(name));
                return result.isNull() ? null : result.readUtf8String();
            }
            console.log('[Dof native ABI names] ' + JSON.stringify({
                count: abi.length, passed: abi.length === 29 && abi.every(function (row) {return row.newFound && row.oldAbsent;}),
                results: abi,
                socketRtti: nativeRtti('_ZTS7DofSock'),
                singletonRtti: nativeRtti('_ZTS9SingletonI7DofSockE')
            }));
            if (!abiHandle.isNull()) close(abiHandle);
            var retiredResults=[];
            Object.keys(dofExpectedRetiredLiterals).forEach(function(name){
                var module=Process.getModuleByName(name);
                dofExpectedRetiredLiterals[name].forEach(function(row){
                    var bytes=Array.prototype.slice.call(new Uint8Array(module.base.add(row.address).readByteArray(row.bytes)));
                    retiredResults.push({module:name,address:row.address,bytes:row.bytes,cleared:bytes.every(function(b){return b===0;})});
                });
            });
            console.log('[Dof retired native literals] '+JSON.stringify({passed:retiredResults.length>0&&retiredResults.every(function(r){return r.cleared;}),count:retiredResults.length,results:retiredResults}));
            var dynamic = {}, dynamicAt = plugin.base.add(0x82a114);
            for (var d=0; d<26; d++) {
                var tag=dynamicAt.add(d*8).readU32();
                if ([4,5,6,10].indexOf(tag)>=0) dynamic[tag]=dynamicAt.add(d*8+4).readU32();
            }
            function loadedAddress(value) {return value < plugin.size ? plugin.base.add(value) : ptr(value);}
            var symbols=loadedAddress(dynamic[6]), strings=loadedAddress(dynamic[5]);
            var rttiIndex=symbols.add(70*16).readU32();
            console.log('[Dof loaded symbol metadata] '+JSON.stringify({
                dynamicStringAddress: loadedAddress(dynamic[5]).sub(plugin.base).toString(),
                stringSize:dynamic[10],rttiNameOffset:rttiIndex,
                rttiName:strings.add(rttiIndex).readUtf8String(),
                openingInstructions:Array.prototype.slice.call(new Uint8Array(plugin.base.add(0xe1292).readByteArray(10)))
            }));
        }
    });
    Interceptor.attach(ptr('0x083f41c0'), {
        onEnter: function (args) {
            var caller = Process.findModuleByAddress(this.returnAddress);
            if (!caller || ['libdof-plugin.so', 'libdof-inline.so'].indexOf(caller.name) < 0) return;
            // Record identifier evidence only. Queries may contain private data.
            var format = args[1].readUtf8String();
            sql.calls++;
            if (/\bqk\s*\./i.test(format)) sql.oldDatabase++;
            if (/\bqingkong_config(?:_v2)?\b/i.test(format)) sql.oldConfigTable++;
        }
    });
    var timer = setInterval(function () {
        observations++;
        var plugin = Process.findModuleByName('libdof-plugin.so');
        var binding = null;
        if (plugin) {
            var target = plugin.base.add(0x397f6c).readPointer();
            var owner = Process.findModuleByAddress(target);
            var exported = Module.findExportByName(null, symbol);
            binding = {
                target: target.toString(),
                owner: owner ? owner.name : null,
                offset: owner ? target.sub(owner.base).toString() : null,
                exportFound: exported !== null,
                lazyPltStillPresent: target.equals(plugin.base.add(0x28e4a)),
                originalItemCallback: plugin.base.add(0x3980b4).readPointer().toString(),
                gameItemEntryBytes: Array.prototype.slice.call(
                    new Uint8Array(ptr('0x085b949c').readByteArray(16)))
            };
        }
        console.log('[Dof runtime observation] ' + JSON.stringify({
            observation: observations,
            modules: Process.enumerateModules().map(function (m) { return m.name; })
                .filter(function (name) { return /dof|dp2|frida/.test(name); }),
            equipmentBroadcast: binding,
            sql: sql
        }));
        if (observations === 24) clearInterval(timer);
    }, 5000);
})();
