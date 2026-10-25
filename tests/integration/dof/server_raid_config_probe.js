// Explicit local integration exercise, not production startup code.
// Calls the real namespace-level configuration APIs on the game thread after
// GameWorld::init returns; never substitutes configuration-reader results.
(function exerciseRaidConfiguration() {
    'use strict';
    if (Process.arch !== 'ia32') throw new Error('Raid probe requires ia32');
    var completed = false;
    Interceptor.attach(ptr('0x086c4134'), {
        onLeave: function (result) {
            if (completed || result.toInt32() === 0) return;
            completed = true;
            var module = Process.getModuleByName('libdof-plugin.so');
            var reads = [];
            var reader = Interceptor.attach(module.base.add(0xa7a10), {
                onEnter: function (args) { this.path = args[1].readUtf8String(); },
                onLeave: function (value) {
                    reads.push({path: this.path, loaded: value.toInt32() !== 0});
                }
            });
            var observed = [];
            Interceptor.flush();
            try {
                [
                    ['anton', '_ZN5anton11init_configEv', 'get_anton_name'],
                    ['luke', '_ZN4luke11init_configEv', 'get_luke_name']
                ].forEach(function (row) {
                    var initialize = new NativeFunction(module.getExportByName(row[1]),
                        'void', [], {abi: 'sysv', exceptions: 'propagate'});
                    initialize();
                    var name = module.getExportByName(row[2]);
                    observed.push({raid: row[0], returned: true,
                        name: name.readUtf8String(),
                        nameBytes: Array.prototype.slice.call(new Uint8Array(name.readByteArray(16)))});
                });
            } finally {
                reader.detach();
            }
            console.log('[Dof raid configuration exercise] ' + JSON.stringify({
                readers: reads, results: observed, gameplayVerified: false
            }));
        }
    });
})();
