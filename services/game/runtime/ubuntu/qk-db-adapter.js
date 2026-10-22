// QK DB credential adapter for this project's df_game_r and supplied DP2/Frida.
// Only approved QK module/database pairs are changed; no secrets are logged.
(function installQkDatabaseCredentials() {
    if (Process.arch !== 'ia32') throw new Error('QK adapter requires ia32');
    var getenv = new NativeFunction(Module.getExportByName(null, 'getenv'),
        'pointer', ['pointer'], { abi: 'sysv' });
    var value = getenv(Memory.allocUtf8String('DNF_DB_GAME_PASSWORD'));
    if (value.isNull()) throw new Error('QK adapter: DB credential is missing');
    var password = Memory.allocUtf8String(value.readUtf8String());
    var successes = {};
    Interceptor.attach(ptr(0x083F4024), {
        onEnter: function (args) {
            var caller = Process.findModuleByAddress(this.returnAddress);
            if (!caller || ['qk_plugin.so', 'libantisvrinline.so'].indexOf(caller.name) === -1) return;
            var database = args[3].readUtf8String();
            if (args[4].readUtf8String() !== 'game') return;
            if (caller.name === 'qk_plugin.so' &&
                ['qk', 'taiwan_cain_2nd'].indexOf(database) === -1) return;
            if (caller.name === 'libantisvrinline.so' &&
                ['qk', 'taiwan_cain', 'taiwan_cain_2nd'].indexOf(database) === -1) return;
            args[5] = password;
            this.qkDatabase = caller.name + '/' + database;
        },
        onLeave: function (result) {
            if (!this.qkDatabase) return;
            var state = result.toInt32() !== 0 ? 'connected' : 'failed';
            var key = this.qkDatabase + ':' + state;
            if (!successes[key]) {
                successes[key] = true;
                console.log('[QK DB adapter] ' + this.qkDatabase + ' ' + state);
            }
        }
    });
    console.log('[QK DB adapter] installed');
})();
