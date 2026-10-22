#!/usr/bin/env python3
"""Bounded ABI startup proof, not a gameplay or database acceptance test."""
import json
import os
from pathlib import Path
import signal
import subprocess
import tempfile

root = Path('/assets/home/template')
report = json.loads(Path('/audit/audit/native-after.json').read_text())
results = []
for item in report:
    if not item['interpreter'] and item['needed']:
        continue
    binary = Path(item['path'])
    environment = dict(os.environ, LD_LIBRARY_PATH='/opt/dnf/compat/' + item['architecture'], LD_BIND_NOW='1')
    hook = root / 'init/libhook.so'
    if binary.name == 'df_bridge_r':
        environment['LD_PRELOAD'] = f'{root}/init/bridge_hook.so:{hook}'
    elif binary.name == 'df_channel_r':
        environment['LD_PRELOAD'] = f'{root}/init/channel_hook.so:{hook}'
    elif item['architecture'] == 'i386':
        environment['LD_PRELOAD'] = str(hook)
    with tempfile.TemporaryDirectory() as directory:
        output = Path(directory) / 'output'
        with output.open('wb') as stream:
            proc = subprocess.Popen([str(binary)], cwd=directory, env=environment,
                                    stdout=stream, stderr=subprocess.STDOUT, start_new_session=True)
            try:
                code = proc.wait(timeout=2)
            except subprocess.TimeoutExpired:
                code = 'timeout'
            finally:
                try:
                    os.killpg(proc.pid, signal.SIGKILL)
                except ProcessLookupError:
                    pass
                proc.wait()
        results.append(dict(path=str(binary), preload=environment.get('LD_PRELOAD'),
                            result=code, output=output.read_text(errors='replace')[:12000]))
Path('/audit/audit/start-proof.json').write_text(json.dumps(results, indent=2) + '\n')
print(json.dumps([{k: v for k, v in item.items() if k != 'output'} for item in results], indent=2))
