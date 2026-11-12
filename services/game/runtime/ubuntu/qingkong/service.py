"""Supervise the original Qingkong gateway and its unmodified game modules."""
import configparser
import ctypes
import hashlib
import json
import os
from pathlib import Path
import signal
import shutil
import socket
import subprocess
import sys
import tempfile
import time

BASE = Path(__file__).resolve().parent
sys.path.insert(0, str(BASE.parent))
sys.path.insert(0, '/opt/dof-easy/runtime')
READY = Path('/run/qingkong/gateway-ready')
GATEWAY = Path('/root/QK')
RUN_GAME = Path('/opt/dnf/ubuntu/run-game')


def add_adapter(script, adapter):
    if script.startswith(adapter + b'\n'):
        return script
    if b'installQingkongDatabaseCredentials' in script or b'installDofDatabaseCredentials' in script:
        raise ValueError('Different credential adapter already present')
    return adapter + b'\n' + script


def install_gateway_keys(state=None, public=Path('/data/publickey.pem'), game=Path('/home/neople/game')):
    from gateway.service import install_gateway_keys as install_validated_keys
    from qingkong.assets import state_for
    install_validated_keys(state if state is not None else state_for(os.environ), public, game)


def runtime_game_ready(source=Path('/data/df_game_r'), game=Path('/home/neople/game/df_game_r')):
    original = source.read_bytes()
    old, new = b'libantisvrimport.so', b'libantisvrinline.so'
    if original.count(old) != 1:
        raise ValueError('Unsupported original inline loader string')
    actual = game.read_bytes()
    if actual == original:
        return False
    if actual != original.replace(old, new):
        raise ValueError('Unexpected Qingkong game modification')
    return True


def channels_running():
    from gateway.service import channels_running as running
    return running()


def configure(state, values):
    """Preserve original INI settings; manage only endpoint, credentials and game path."""
    from qingkong.assets import regular
    path = regular(state / 'config/config.ini', state)
    # Byte-wise line substitution retains comments, encoding, order and CRLF.
    import re
    data = path.read_bytes()
    parser = configparser.ConfigParser(interpolation=None)
    parser.optionxform = str
    parser.read_string(data.decode('utf-8-sig'))
    if parser['Gate']['gateProt'] != '8887':
        raise ValueError('Original Qingkong gateway must use port 8887')
    updates = {'db': {'db_host': values['CUR_SG_DB_HOST'], 'db_prot': values['CUR_SG_DB_PORT'],
                      'db_user_name': 'game', 'db_password': values['DOF_DB_GAME_PASSWORD']},
               'Gate': {'DfGameR': '/home/neople/game/df_game_r'}}
    section = None
    found = set()
    lines = []
    for line in data.splitlines(keepends=True):
        header = re.match(rb'^\s*\[([^]]+)\]', line.lstrip(b'\xef\xbb\xbf'))
        if header:
            section = header.group(1).decode('ascii')
        match = re.match(rb'^(\s*([^=\s]+)\s*=\s*)([^\r\n]*)(\r?\n)?$', line)
        if match and section in updates:
            key = match.group(2).decode('ascii')
            if key in updates[section]:
                value = str(updates[section][key])
                if any(c in value for c in ('\r', '\n', '\0')):
                    raise ValueError('Invalid Qingkong config value')
                line = match.group(1) + value.encode('utf-8') + (match.group(4) or b'')
                found.add((section, key))
        lines.append(line)
    if found != {(section, key) for section, entries in updates.items() for key in entries}:
        raise ValueError('Original Qingkong config is missing a managed field')
    descriptor, name = tempfile.mkstemp(prefix='.qingkong-config-', dir=path.parent)
    temporary = Path(name)
    try:
        with os.fdopen(descriptor, 'wb') as stream:
            stream.write(b''.join(lines)); stream.flush(); os.fsync(stream.fileno())
        temporary.replace(path)
    finally:
        temporary.unlink(missing_ok=True)


def link(target, source):
    if target.is_dir() and not target.is_symlink():
        raise ValueError('Qingkong runtime alias conflicts with a directory')
    target.unlink(missing_ok=True)
    target.symlink_to(source)


def prepare_libraries(state, root=Path('/root'), game=Path('/home/neople/game'), i386=Path('/opt/qingkong/i386')):
    from qingkong.assets import verify, manifest
    from gateway.binary import prepare as copy_verified
    source = verify(state / 'assets')
    for row in manifest():
        target = (game if row['path'] == 'libantisvrinline.so' else root) / row['path']
        target.parent.mkdir(parents=True, exist_ok=True, mode=0o700)
        copy_verified(source / row['path'], target, row)
        if row['path'] == 'QK':
            target.chmod(0o700)
    i386.mkdir(mode=0o700, parents=True, exist_ok=True)
    link(i386 / 'qk_plugin.so', root / 'qk_plugin.so')
    link(i386 / 'libantisvrinline.so', game / 'libantisvrinline.so')


def prepare(conf=Path('/etc/supervisor/conf.d/qingkong.conf')):
    READY.unlink(missing_ok=True)
    if os.environ.get('LAUNCHER_MODE', 'dof') != 'qingkong' or os.environ.get('SERVER_TYPE', 'ALL') not in ('ALL', 'GAME'):
        conf.unlink(missing_ok=True)
        return
    from qingkong.assets import preflight, state_for, CONFIGS
    preflight()
    state = state_for(os.environ)
    game = Path('/home/neople/game')
    READY.parent.mkdir(mode=0o700, parents=True, exist_ok=True)
    prepare_libraries(state)
    configure(state, os.environ)
    for name in CONFIGS:
        link(Path('/root') / name, state / 'config' / name)
    mode = os.environ.get('PLUGIN_MODE', 'dp2-frida')
    body = (Path('/data/plugins/frida/frida.js').read_bytes() if mode in ('frida', 'dp2-frida')
            else b'rpc.exports = {init: function () {}};\n')
    wrapper = READY.parent / 'game-adapter.js'
    wrapper.write_bytes(add_adapter(body, (BASE / 'db_adapter.js').read_bytes()))
    wrapper.chmod(0o600)
    shutil.copyfile('/opt/dnf/plugins/frida/frida.so', game / 'frida.so')
    (game / 'frida.config').write_text(json.dumps({'interaction': {
        'type': 'script', 'path': str(wrapper), 'on_change': 'ignore'}}))
    install_gateway_keys()
    conf.write_text('[program:qingkong]\n'
                    'command=/usr/bin/python3 -u /opt/dnf/ubuntu/qingkong/service.py run\n'
                    'directory=/root\nautostart=true\nautorestart=true\nstartsecs=5\n'
                    'startretries=3\npriority=8\nstopwaitsecs=20\n'
                    'stdout_logfile=/dev/fd/1\nstdout_logfile_maxbytes=0\nredirect_stderr=true\n')
    print('[Qingkong] original gateway and credential adapter prepared', flush=True)


def gateway_children():
    result = []
    children = Path(f'/proc/self/task/{os.getpid()}/children').read_text().split()
    for child in children:
        try:
            if os.readlink(f'/proc/{child}/exe') == os.path.realpath(GATEWAY):
                result.append(int(child))
        except FileNotFoundError:
            pass
    return result


def database_ready():
    result = subprocess.run(
        ['/usr/local/bin/mysql', '-h', os.environ['CUR_SG_DB_HOST'],
         '-P', os.environ['CUR_SG_DB_PORT'], '-ugame', '-N', '-e',
         'SELECT 1 FROM qk.qingkong_config_v2 AS config '
         'JOIN qk.billing AS billing ON billing.ID=1 '
         'WHERE config.ID=1 LIMIT 1'],
        env=dict(os.environ, MYSQL_PWD=os.environ['DOF_DB_GAME_PASSWORD']),
        capture_output=True, timeout=5)
    return result.returncode == 0 and result.stdout.strip() == b'1'


def run():
    """Adopt Qingkong gateway's daemon child so Supervisor owns its full lifetime."""
    libc = ctypes.CDLL(None, use_errno=True)
    if libc.prctl(36, 1, 0, 0, 0) != 0:  # PR_SET_CHILD_SUBREAPER
        raise OSError(ctypes.get_errno(), 'cannot adopt Qingkong gateway daemon')
    stopping = False

    def stop(signum, frame):
        nonlocal stopping
        stopping = True

    for signum in (signal.SIGTERM, signal.SIGINT):
        signal.signal(signum, stop)
    READY.unlink(missing_ok=True)
    if channels_running():
        raise RuntimeError('stop game channels before restarting the internal gateway')
    install_gateway_keys()
    launcher = subprocess.Popen(
        [str(RUN_GAME), str(GATEWAY), 'start'], cwd=str(GATEWAY.parent),
        env=dict(os.environ, LINES='24', COLUMNS='80', PWD=str(GATEWAY.parent)))
    deadline = time.monotonic() + 90
    ready = False
    stable_signature = None
    stable_count = 0
    try:
        while not stopping:
            # Reap the launcher before the generic adopted-child reaper.
            exit_code = launcher.poll()
            if exit_code is not None:
                while True:
                    try:
                        pid, _ = os.waitpid(-1, os.WNOHANG)
                    except ChildProcessError:
                        break
                    if not pid:
                        break
            children = gateway_children()
            if children and not ready:
                try:
                    with socket.create_connection(('127.0.0.1', 8887), timeout=0.2):
                        pass
                except OSError:
                    pass
                else:
                    if not database_ready():
                        if time.monotonic() > deadline:
                            raise RuntimeError('Qingkong database initialization timed out')
                        time.sleep(0.5)
                        continue
                    if not runtime_game_ready():
                        if time.monotonic() > deadline:
                            raise RuntimeError('Qingkong game initialization timed out')
                        time.sleep(0.5)
                        continue
                    try:
                        signature = tuple(hashlib.sha256(Path('/home/neople/game', name).read_bytes()).digest()
                                          for name in ('privatekey.pem', 'publickey.pem'))
                    except FileNotFoundError:
                        signature = None
                    stable_count = stable_count + 1 if signature and signature == stable_signature else 1
                    stable_signature = signature
                    if not signature or stable_count < 3:
                        if time.monotonic() > deadline:
                            raise RuntimeError('Qingkong key initialization timed out')
                        time.sleep(0.5)
                        continue
                    install_gateway_keys()
                    READY.write_text(str(children[0]))
                    ready = True
                    print('[Qingkong] internal gateway ready', flush=True)
            if ready and not children:
                raise RuntimeError('Qingkong gateway daemon exited')
            if ready and Path('/home/neople/game/publickey.pem').read_bytes() != Path('/data/publickey.pem').read_bytes():
                raise RuntimeError('internal gateway changed the login identity after startup')
            if not ready and time.monotonic() > deadline:
                raise RuntimeError('Qingkong gateway gateway startup timed out')
            time.sleep(0.5)
    finally:
        READY.unlink(missing_ok=True)
        if launcher.poll() is None:
            launcher.terminate()
        for pid in gateway_children():
            os.kill(pid, signal.SIGTERM)
        end = time.monotonic() + 10
        while gateway_children() and time.monotonic() < end:
            try:
                os.waitpid(-1, os.WNOHANG)
            except ChildProcessError:
                break
            time.sleep(0.1)
        for pid in gateway_children():
            os.kill(pid, signal.SIGKILL)


if __name__ == '__main__':
    try:
        {'prepare': prepare, 'run': run}[sys.argv[1]]()
    except Exception as exc:
        # Configuration values can contain credentials; never echo exception data.
        print('[Qingkong] failed (' + type(exc).__name__ + '); check supported files and private configuration',
              file=sys.stderr, flush=True)
        sys.exit(1)
