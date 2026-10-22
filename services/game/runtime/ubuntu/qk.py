"""Optional, version-pinned QK 2.8 gateway and DP2 credential integration."""
import configparser
import ctypes
import hashlib
import os
from pathlib import Path
import secrets
import signal
import shutil
import socket
import subprocess
import sys
import time

BASE = Path('/opt/dnf/ubuntu')
READY = Path('/run/dnf/qk-ready')
PLUGIN_SHA256 = '33cd006a364d4c3e0b8738fb2333cf16771fb796443368be4eee7105e40aadfa'
GATEWAY_SHA256 = '67553a3b45de80ebea5f767f6e3a51d577d2cbb6c6647bd4c97037cb50a41f9b'
INLINE_SHA256 = 'd413b7df6c928b519986a9ceb3dbed4c78148564cca352d579f18e3650819da8'
PREVIOUS_ADAPTERS = {
    '8e156b2825fef49be702262a39c4360b93b897ce0f70808f490ee5cbee5c50d7',
    '13959cf52ad1aa5b04ec064eb0c57533312e5c7126c5f9e448e356e990ce6b81',
}


def add_adapter(script, adapter):
    """Preserve the user's DP2 script verbatim and install our prefix once."""
    if script.startswith(adapter + b'\n'):
        return script
    end_marker = b'\n})();\n'
    end = script.find(end_marker)
    if end >= 0:
        boundary = end + len(end_marker)
        if (hashlib.sha256(script[:boundary]).hexdigest() in PREVIOUS_ADAPTERS
                and script[boundary:boundary + 1] == b'\n'):
            return adapter + b'\n' + script[boundary + 1:]
    if b'installQkDatabaseCredentials' in script:
        raise ValueError('different QK adapter already installed; inspect before updating')
    return adapter + b'\n' + script


def prepare():
    conf = Path('/etc/supervisor/conf.d/qk.conf')
    READY.unlink(missing_ok=True)
    if os.environ.get('SERVER_TYPE') not in ('ALL', 'GAME') or not Path('/root/QK').is_file():
        conf.unlink(missing_ok=True)
        return
    for name, expected in (('/root/qk_plugin.so', PLUGIN_SHA256), ('/root/QK', GATEWAY_SHA256),
                           ('/home/neople/game/libantisvrinline.so', INLINE_SHA256)):
        if hashlib.sha256(Path(name).read_bytes()).hexdigest() != expected:
            raise ValueError('unsupported QK binary: ' + name)
    if not Path('/dp2/libdp2pre.so').is_file():
        raise ValueError('QK credential adapter requires the supplied DP2 installation')
    # This address is part of the supplied fixed-address i386 game executable.
    with open('/home/neople/game/df_game_r', 'rb') as stream:
        stream.seek(0x083F4024 - 0x08048000)
        if stream.read(16).hex() != '5589e55383ec44837d10007507c74510':
            raise ValueError('unsupported MySQL_open prologue for QK adapter')
    script = Path('/data/dp/df_game_r.js')
    original = script.read_bytes()
    updated = add_adapter(original, (BASE / 'qk-db-adapter.js').read_bytes())
    if updated != original:
        script.write_bytes(updated)
    state = Path('/data/qk')
    state.mkdir(mode=0o700, exist_ok=True)
    state.chmod(0o700)
    # QK otherwise generates another key pair and replaces the game's public key.
    private = state / 'privatekey.pem'
    public = Path('/data/publickey.pem')
    private_public = subprocess.run(
        ['openssl', 'pkey', '-in', str(private), '-pubout', '-outform', 'DER'],
        capture_output=True, check=True).stdout
    configured_public = subprocess.run(
        ['openssl', 'pkey', '-pubin', '-in', str(public), '-outform', 'DER'],
        capture_output=True, check=True).stdout
    if private_public != configured_public:
        raise ValueError('QK private key must match the existing game public key')
    shutil.copyfile(private, '/home/neople/game/privatekey.pem')
    Path('/home/neople/game/privatekey.pem').chmod(0o600)
    shutil.copyfile(public, '/home/neople/game/publickey.pem')
    config_path = state / 'config.ini'
    config = configparser.ConfigParser(interpolation=None)
    config.optionxform = str
    if config_path.exists():
        config.read(config_path)
    else:
        config.read_dict({
            'sever': {'LogPath': '/home/neople/game/log/', 'DungeonCheck': '0',
                      'Robotswitch': '0', 'server_qps': '5', 'server_num': '2'},
            'Gate': {'DfGameR': '/home/neople/game/df_game_r',
                     'RobotKey': secrets.token_hex(16), 'gateProt': '8887'},
            'db': {'db_connection_time_out': '10', 'db_max_Size': '30',
                   'db_init_size': '2', 'db_database_name': 'd_taiwan'}
        })
    # Use the real DB endpoint: local forwarding processes start with Supervisor.
    config['db'].update(db_host=os.environ['CUR_SG_DB_HOST'],
                        db_prot=os.environ['CUR_SG_DB_PORT'],
                        db_user_name='game',
                        db_password=os.environ['DNF_DB_GAME_PASSWORD'])
    config['Gate']['DfGameR'] = '/home/neople/game/df_game_r'
    if config['Gate']['gateProt'] != '8887':
        raise ValueError('this QK integration expects gateway port 8887')
    temporary = state / 'config.ini.tmp'
    fd = os.open(temporary, os.O_WRONLY | os.O_CREAT | os.O_TRUNC, 0o600)
    with os.fdopen(fd, 'w') as output:
        config.write(output, space_around_delimiters=False)
    temporary.replace(config_path)
    target = Path('/root/config.ini')
    target.unlink(missing_ok=True)
    target.symlink_to(config_path)
    conf.write_text('''[program:qk]
command=/usr/bin/python3 -u /opt/dnf/ubuntu/qk.py run
directory=/root
autostart=true
autorestart=true
startsecs=5
startretries=3
priority=8
stopwaitsecs=20
stdout_logfile=/dev/fd/1
stdout_logfile_maxbytes=0
redirect_stderr=true
''')
    print('[QK] prepared gateway and scoped credential adapter', flush=True)


def gateway_children():
    result = []
    children = Path(f'/proc/self/task/{os.getpid()}/children').read_text().split()
    for child in children:
        try:
            if os.readlink(f'/proc/{child}/exe') == '/root/QK':
                result.append(int(child))
        except FileNotFoundError:
            pass
    return result


def run():
    """Adopt QK's daemon child so Supervisor owns its full lifetime."""
    libc = ctypes.CDLL(None, use_errno=True)
    if libc.prctl(36, 1, 0, 0, 0) != 0:  # PR_SET_CHILD_SUBREAPER
        raise OSError(ctypes.get_errno(), 'cannot adopt QK daemon')
    stopping = False

    def stop(signum, frame):
        nonlocal stopping
        stopping = True

    for signum in (signal.SIGTERM, signal.SIGINT):
        signal.signal(signum, stop)
    READY.unlink(missing_ok=True)
    launcher = subprocess.Popen(
        [str(BASE / 'run-game'), '/root/QK', 'start'], cwd='/root',
        env=dict(os.environ, LINES='24', COLUMNS='80', PWD='/root'))
    deadline = time.monotonic() + 90
    ready = False
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
                    if (Path('/home/neople/game/publickey.pem').read_bytes()
                            != Path('/data/publickey.pem').read_bytes()):
                        raise RuntimeError('QK changed the configured public key')
                    READY.write_text(str(children[0]))
                    ready = True
                    print('[QK] gateway ready on 8887', flush=True)
            if ready and not children:
                raise RuntimeError('QK daemon exited')
            if not ready and time.monotonic() > deadline:
                raise RuntimeError('QK gateway startup timed out')
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
        print('[QK] failed (' + type(exc).__name__ + '); check supported files and private configuration',
              file=sys.stderr, flush=True)
        sys.exit(1)
