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
            var reloads = [];
            var libc = Process.getModuleByName('libc.so.6');
            function syscall(name, result, args) {
                return new NativeFunction(libc.getExportByName(name), result, args,
                    {abi: 'sysv', exceptions: 'propagate'});
            }
            var open = syscall('open', 'int', ['pointer', 'int']);
            var read = syscall('read', 'int', ['int', 'pointer', 'uint']);
            var write = syscall('write', 'int', ['int', 'pointer', 'uint']);
            var close = syscall('close', 'int', ['int']);
            var fsync = syscall('fsync', 'int', ['int']);
            function contents(path) {
                var fd = open(Memory.allocUtf8String(path), 0);
                if (fd < 0) throw new Error('Cannot read private raid fixture');
                try {
                    var buffer = Memory.alloc(65537), count = read(fd, buffer, 65536);
                    if (count < 1 || count === 65536) throw new Error('Unexpected raid fixture size');
                    buffer.add(count).writeU8(0);
                    return buffer.readUtf8String(count);
                } finally { close(fd); }
            }
            function replace(path, text) {
                var buffer = Memory.allocUtf8String(text);
                var size = unescape(encodeURIComponent(text)).length;
                // Fixed existing private fixture; no creation or arbitrary paths.
                var fd = open(Memory.allocUtf8String(path), 0x201);
                if (fd < 0) throw new Error('Cannot write private raid fixture');
                try {
                    var offset = 0;
                    while (offset < size) {
                        var count = write(fd, buffer.add(offset), size - offset);
                        if (count <= 0) throw new Error('Short raid fixture write');
                        offset += count;
                    }
                    if (fsync(fd) !== 0) throw new Error('Cannot sync raid fixture');
                } finally { close(fd); }
            }
            function snapshot(raid) {
                var values = {};
                ['name', 'open_day', 'open_time'].forEach(function (key) {
                    var address = module.getExportByName('get_' + raid + '_' + key);
                    values[key] = address.readUtf8String();
                    values[key + 'Bytes'] = Array.prototype.slice.call(
                        new Uint8Array(address.readByteArray(64)));
                });
                values.member_min = module.getExportByName('get_' + raid + '_member_min').readS32();
                return values;
            }
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
                    var path = '/data/dof-easy/config/' + row[0] + '-raid.ini';
                    var original = contents(path), before = snapshot(row[0]);
                    var expected = {name: row[0] === 'anton' ? '安徒恩刷新验证' : '卢克刷新验证',
                        open_day: '2,4,6', open_time: '01:02:03-04:05:06', member_min: 3};
                    var changed = original;
                    Object.keys(expected).forEach(function (key) {
                        var expression = new RegExp('^([ \\t]*' + row[0] + '_' + key + '[ \\t]*=)[^\\r\\n]*$', 'm');
                        if (!expression.test(changed)) throw new Error('Missing pinned raid field: ' + key);
                        changed = changed.replace(expression, '$1 ' + expected[key]);
                    });
                    var after, restored, fileRestored = false;
                    try {
                        replace(path, changed);
                        initialize();
                        after = snapshot(row[0]);
                    } finally {
                        replace(path, original);
                        initialize();
                        restored = snapshot(row[0]);
                        fileRestored = contents(path) === original;
                    }
                    reloads.push({raid: row[0], before: before, changed: after,
                        restored: restored, fileRestored: fileRestored,
                        threadId: Process.getCurrentThreadId()});
                });
            } finally {
                reader.detach();
            }
            console.log('[Dof raid configuration exercise] ' + JSON.stringify({
                readers: reads, results: observed, reloads: reloads,
                gameplayVerified: false, activeRaidRefreshVerified: false
            }));
        }
    });
})();
