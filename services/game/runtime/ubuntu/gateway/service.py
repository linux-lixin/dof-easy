"""Manage the pinned derived native gateway and its game integration."""
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

BASE = Path(__file__).resolve().parent
sys.path.insert(0, str(BASE.parent))
READY = Path('/run/dof-easy/gateway-ready')
GATEWAY = Path('/opt/dof-easy/bin/dof-gateway')
RUN_GAME = Path('/opt/dnf/ubuntu/run-game')
def add_adapter(script, adapter):
    """Prepend the current adapter once, preserving operator-owned script bytes."""
    if script.startswith(adapter + b'\n'):
        return script
    if b'installDofDatabaseCredentials' in script:
        raise ValueError('Different credential adapter already present')
    return adapter + b'\n' + script


def install_gateway_keys(state=None, public=Path('/data/publickey.pem'),
                         game=Path('/home/neople/game')):
    if state is None:
        from gateway.assets import state_for
        state = state_for(os.environ)
    private = state / 'keys/privatekey.pem'
    paths = (private, public, game / 'privatekey.pem', game / 'publickey.pem')
    if any(p.is_symlink() for p in paths):
        raise ValueError('raid extension key paths must not be symlinks')
    if any(not 0 < p.stat().st_size <= 16384 for p in (private, public)):
        raise ValueError('invalid raid extension key size')
    contents = private.read_bytes(), public.read_bytes()
    derived = subprocess.run(['openssl', 'pkey', '-pubout', '-outform', 'DER'],
                             input=contents[0], capture_output=True, check=True).stdout
    configured = subprocess.run(['openssl', 'pkey', '-pubin', '-outform', 'DER'],
                                input=contents[1], capture_output=True, check=True).stdout
    if not derived or derived != configured:
        raise ValueError('raid extension private key differs from the login public key')
    for name, content in zip(('privatekey.pem', 'publickey.pem'), contents):
        target = game / name
        target.write_bytes(content)
        target.chmod(0o600)


def runtime_game_ready(source=Path('/data/df_game_r'), game=Path('/home/neople/game/df_game_r')):
    """Accept only the native gateway's exact, bounded loader-name rewrite."""
    original = source.read_bytes()
    old = b'libantisvrimport.so'
    new = b'libdof-inline.so'.ljust(len(old), b'\0')
    if original.count(old) != 1:
        raise ValueError('unsupported game inline loader string')
    actual = game.read_bytes()
    if actual == original:
        return False
    if actual != original.replace(old, new):
        raise ValueError('unexpected native gateway modification to the game')
    return True


def channels_running():
    for comm in Path('/proc').glob('[0-9]*/comm'):
        try:
            if comm.read_text().strip() == 'df_game_r':
                return True
        except (FileNotFoundError, ProcessLookupError):
            pass
    return False


def prepare_libraries(state, target):
    """Install already-derived immutable payloads, never patch at startup."""
    from gateway.assets import verify, manifest
    from gateway.binary import prepare as copy_verified
    source = verify(state / 'assets')
    for row in manifest():
        destination = target / row['path']
        destination.parent.mkdir(parents=True, exist_ok=True)
        copy_verified(source / row['path'], destination, row)


def prepare(conf=Path('/etc/supervisor/conf.d/dof-gateway.conf')):
    READY.unlink(missing_ok=True)
    if (os.environ.get('RAID_EXTENSION', 'off') != 'on' and os.environ.get('LAUNCHER_MODE', 'dof') == 'dof') or os.environ.get('SERVER_TYPE', 'ALL') not in ('ALL', 'GAME'):
        conf.unlink(missing_ok=True)
        return
    from gateway.assets import preflight, state_for
    preflight()
    Path('/var/log/dof-easy').mkdir(mode=0o700, parents=True, exist_ok=True)
    state = state_for(os.environ)
    game = Path('/home/neople/game')
    READY.parent.mkdir(mode=0o700, parents=True, exist_ok=True)
    prepare_libraries(state, Path('/opt/dof-easy'))
    # The game's dlopen searches its working directory. Only the new module
    # name is exposed; no old-brand compatibility aliases are created.
    target = game / 'libdof-inline.so'
    if target.exists() or target.is_symlink():
        target.unlink()
    target.symlink_to('/opt/dof-easy/lib/libdof-inline.so')
    inline_search = Path('/opt/dof-easy/lib/i386')
    inline_search.mkdir(mode=0o700, parents=True, exist_ok=True)
    # Both 32-bit modules must also resolve by basename (dlopen/Frida). The
    # amd64 dependency directory is deliberately outside the i386 search path.
    for name in ('libdof-inline.so', 'libdof-plugin.so'):
        alias = inline_search / name
        alias.unlink(missing_ok=True)
        alias.symlink_to('../' + name)
    dungeon = GATEWAY.parent.parent / 'runtime/gateway/dungeon.lst'
    dungeon.unlink(missing_ok=True)
    dungeon.symlink_to(state / 'config/dungeon.lst')
    # One Gadget per process: prepend credentials to a generated wrapper, never
    # edit the operator-owned Frida/DP2 scripts. DP2-only also uses the Gadget
    # internally for credentials, without enabling ordinary Frida features.
    adapter = (BASE / 'db_adapter.js').read_bytes()
    import json
    equipment_abi = json.loads((state / 'assets/runtime/gateway/equipment-abi.json').read_text())
    equipment_config = json.loads((state / 'config/equipment.json').read_text())
    equipment = (b'var dofEquipmentInput = ' + json.dumps(dict(abi=equipment_abi,
                  config=equipment_config), ensure_ascii=True).encode() + b';\n' +
                  (state / 'assets/runtime/gateway/equipment.js').read_bytes())
    mode = os.environ.get('PLUGIN_MODE', 'dp2-frida')
    body = (Path('/data/plugins/frida/frida.js').read_bytes() if mode in ('frida', 'dp2-frida')
            else b'rpc.exports = {init: function () {}};\n')
    from gateway.admin_runtime import prepare as prepare_admin_runtime
    try:
        runtime_input = prepare_admin_runtime(state)
    except (ValueError, OSError) as error:
        runtime_input = None
        detail=str(error) if isinstance(error,ValueError) else 'mailbox/helper I/O unavailable (errno='+str(error.errno)+')'
        print('[Dof runtime apply] unavailable: '+detail, flush=True)
    runtime_control = (b'var dofRuntimeApplyInput = ' + json.dumps(runtime_input, ensure_ascii=True).encode() + b';\n'
                       + (BASE / 'admin_apply.js').read_bytes() + b'\n')
    wrapper = Path('/run/dof-easy/game-adapter.js')
    wrapper.write_bytes(add_adapter(equipment + b'\n' + runtime_control + body, adapter))
    wrapper.chmod(0o600)
    shutil.copyfile('/opt/dnf/plugins/frida/frida.so', game / 'frida.so')
    import json
    (game / 'frida.config').write_text(json.dumps({'interaction': {
        'type': 'script', 'path': str(wrapper), 'on_change': 'ignore'}}))
    # Native initialization regenerates its working keys. Restore the validated
    # login pair both before launch and after native initialization, before games.
    install_gateway_keys()
    config_path = state / 'config/gateway.ini'
    if config_path.is_symlink():
        raise ValueError('Gateway configuration cannot be a symlink')
    config = configparser.ConfigParser(interpolation=None)
    config.optionxform = str
    if config_path.exists():
        config.read(config_path)
    else:
        config.read_dict({
            'sever': {'LogPath': '/var/log/dof-easy/', 'DungeonCheck': '0',
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
                        db_password=os.environ['DOF_DB_GAME_PASSWORD'])
    config['Gate']['DfGameR'] = '/home/neople/game/df_game_r'
    if config['Gate']['gateProt'] != '8887':
        raise ValueError('the native integration expects gateway port 8887')
    temporary = state / 'config/.gateway.ini.tmp'
    fd = os.open(temporary, os.O_WRONLY | os.O_CREAT | os.O_TRUNC, 0o600)
    with os.fdopen(fd, 'w') as output:
        config.write(output, space_around_delimiters=False)
    temporary.replace(config_path)
    conf.write_text('''[program:dof-gateway]
command=/usr/bin/python3 -u /opt/dof-easy/runtime/gateway/service.py run
directory=/opt/dof-easy/runtime/gateway
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
    print('[Dof] prepared gateway and credential adapter', flush=True)


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
         'SELECT 1 FROM dof_native.config_v2 AS config '
         'JOIN dof_native.billing AS billing ON billing.ID=1 '
         'WHERE config.ID=1 LIMIT 1'],
        env=dict(os.environ, MYSQL_PWD=os.environ['DOF_DB_GAME_PASSWORD']),
        capture_output=True, timeout=5)
    return result.returncode == 0 and result.stdout.strip() == b'1'


def run():
    """Adopt native gateway's daemon child so Supervisor owns its full lifetime."""
    libc = ctypes.CDLL(None, use_errno=True)
    if libc.prctl(36, 1, 0, 0, 0) != 0:  # PR_SET_CHILD_SUBREAPER
        raise OSError(ctypes.get_errno(), 'cannot adopt native gateway daemon')
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
    from gateway.schema import prepare as prepare_schema
    prepare_schema(GATEWAY.parent.parent / 'runtime/gateway/schema.sql')
    launcher = subprocess.Popen(
        [str(RUN_GAME), str(GATEWAY), 'start'], cwd=str(GATEWAY.parent.parent / 'runtime/gateway'),
        env=dict(os.environ, LINES='24', COLUMNS='80', PWD=str(GATEWAY.parent.parent / 'runtime/gateway')))
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
                            raise RuntimeError('raid extension database initialization timed out')
                        time.sleep(0.5)
                        continue
                    if not runtime_game_ready():
                        if time.monotonic() > deadline:
                            raise RuntimeError('native game initialization timed out')
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
                            raise RuntimeError('native key initialization timed out')
                        time.sleep(0.5)
                        continue
                    install_gateway_keys()
                    READY.write_text(str(children[0]))
                    ready = True
                    print('[团本扩展] internal gateway ready', flush=True)
            if ready and not children:
                raise RuntimeError('native gateway daemon exited')
            if ready and Path('/home/neople/game/publickey.pem').read_bytes() != Path('/data/publickey.pem').read_bytes():
                raise RuntimeError('internal gateway changed the login identity after startup')
            if not ready and time.monotonic() > deadline:
                raise RuntimeError('native gateway gateway startup timed out')
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
        print('[团本扩展] failed (' + type(exc).__name__ + '); check supported files and private configuration',
              file=sys.stderr, flush=True)
        sys.exit(1)
