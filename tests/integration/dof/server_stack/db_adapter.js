// Dof DB credential adapter for this project's df_game_r and supplied DP2/Frida.
// Only approved Dof module/database pairs are changed; no secrets are logged.
(function installDofDatabaseCredentials() {
    if (Process.arch !== 'ia32') throw new Error('Dof adapter requires ia32');
    var getenv = new NativeFunction(Module.getExportByName(null, 'getenv'),
        'pointer', ['pointer'], { abi: 'sysv' });
    var value = getenv(Memory.allocUtf8String('DOF_DB_GAME_PASSWORD'));
    if (value.isNull()) throw new Error('Dof adapter: DB credential is missing');
    var password = Memory.allocUtf8String(value.readUtf8String());
    var successes = {};
    Interceptor.attach(ptr(0x083F4024), {
        onEnter: function (args) {
            var caller = Process.findModuleByAddress(this.returnAddress);
            if (!caller || ['libdof-plugin.so', 'libdof-inline.so'].indexOf(caller.name) === -1) return;
            var database = args[3].readUtf8String();
            if (args[4].readUtf8String() !== 'game') return;
            if (caller.name === 'libdof-plugin.so' &&
                ['dof_native', 'taiwan_cain_2nd'].indexOf(database) === -1) return;
            if (caller.name === 'libdof-inline.so' &&
                ['dof_native', 'taiwan_cain', 'taiwan_cain_2nd'].indexOf(database) === -1) return;
            args[5] = password;
            this.dofDatabase = caller.name + '/' + database;
        },
        onLeave: function (result) {
            if (!this.dofDatabase) return;
            var state = result.toInt32() !== 0 ? 'connected' : 'failed';
            var key = this.dofDatabase + ':' + state;
            if (!successes[key]) {
                successes[key] = true;
                console.log('[Dof DB adapter] ' + this.dofDatabase + ' ' + state);
            }
        }
    });
    console.log('[Dof DB adapter] installed');
})();

rpc.exports = {init: function () {console.log("[Dof local adapter] ready");}};

// Diagnostic only: observe real channel lookup arguments; never alter results.
Interceptor.attach(ptr('0x088dcd38'), {
 onEnter(args) { this.group = args[1].toUInt32(); this.channel = args[2].toUInt32(); },
 onLeave(result) { console.log('[Dof channel probe] group=' + this.group + ' channel=' + this.channel + ' found=' + !result.isNull()); }
});
