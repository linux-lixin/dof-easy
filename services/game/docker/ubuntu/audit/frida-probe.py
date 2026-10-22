#!/usr/bin/env python3
"""Probe a supplied archived gadget using only a synthetic JavaScript hook.

Run this inside a --network none Linux container. No game executable, original
game script, game configuration, credentials or database is opened.
"""
import argparse
import json
import os
from pathlib import Path
import shutil
import subprocess
import tempfile


def exercise(gadget, shim, probe):
    with tempfile.TemporaryDirectory(prefix='frida-abi-') as directory:
        root = Path(directory)
        shutil.copyfile(gadget, root / 'frida.so')
        marker = root / 'result.json'
        script = '''rpc.exports = { init: function () {
    if (Process.pointerSize !== 4) throw new Error('expected i386');
    var target = Module.findExportByName(null, 'vipai_probe_target');
    if (target === null) throw new Error('probe export absent');
    Interceptor.replace(target, new NativeCallback(function (n) { return n + 2; }, 'int', ['int']));
    var getpid = new NativeFunction(Module.findExportByName(null, 'getpid'), 'int', []);
    if (getpid() <= 0) throw new Error('NativeFunction failed');
    var buffer = Memory.allocUtf8String('old-api-ok');
    if (buffer.readUtf8String() !== 'old-api-ok') throw new Error('Memory API failed');
    var file = new File(MARKER, 'w');
    file.write(JSON.stringify({ version: Frida.version, pointerSize: Process.pointerSize, nativeCall: true }));
    file.close();
} };
'''.replace('MARKER', json.dumps(str(marker)))
        (root / 'probe.js').write_text(script)
        (root / 'frida.config').write_text(json.dumps({
            'interaction': {'type': 'script', 'path': str(root / 'probe.js')},
            'teardown': 'minimal'}))
        clean_env = {key: value for key, value in os.environ.items()
                     if key not in ('LD_PRELOAD', 'LD_LIBRARY_PATH')}
        baseline = subprocess.run([str(probe), str(root / 'frida.so')],
                                  env=clean_env, capture_output=True, text=True, timeout=20)
        if baseline.returncode == 0 or '__libc_dlopen_mode' not in baseline.stderr:
            raise RuntimeError('expected missing-private-symbol baseline: ' + baseline.stderr)
        env = dict(clean_env, LD_PRELOAD=str(shim))
        flag_test = subprocess.run([str(probe), '--shim'], env=env,
                                   capture_output=True, text=True, timeout=20)
        if flag_test.returncode:
            raise RuntimeError('flag probe failed: ' + flag_test.stdout + flag_test.stderr)
        loaded = subprocess.run([str(probe), str(root / 'frida.so')], env=env,
                                capture_output=True, text=True, timeout=20)
        if loaded.returncode or not marker.is_file():
            raise RuntimeError('gadget probe failed: ' + loaded.stdout + loaded.stderr)
        result = json.loads(marker.read_text())
        if result['pointerSize'] != 4 or not result['nativeCall']:
            raise RuntimeError('incorrect probe evidence')
        print(flag_test.stdout.strip())
        print(loaded.stdout.strip())
        print(json.dumps(result, sort_keys=True))
        return result


if __name__ == '__main__':
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--gadget', type=Path, required=True)
    parser.add_argument('--shim', type=Path, required=True)
    parser.add_argument('--probe', type=Path, required=True)
    args = parser.parse_args()
    exercise(args.gadget.resolve(), args.shim.resolve(), args.probe.resolve())
