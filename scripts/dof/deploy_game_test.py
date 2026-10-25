"""Replace one identity-pinned isolated game container, retaining its recovery copy.

Requires a private explicit profile and locally built image from the same main
commit. Never operates the web/database containers or initializes gameplay DBs.
The old /data directory and container are retained; rollback never restores a DB.
"""
import argparse
import configparser
import hashlib
import json
import os
from pathlib import Path
import re
import shutil
import subprocess
import sys
import time

ROOT = Path(__file__).resolve().parents[2]
sys.path.insert(0, str(ROOT / 'services/game/runtime/ubuntu'))
from gateway import assets
from space_budget import require_space

FIELDS = {'gameContainer', 'gameId', 'gameImage', 'databaseContainer', 'databaseId',
          'databaseUuid', 'network', 'dataDirectory', 'pvf', 'pvfSha256',
          'gameSha256', 'publicKeySha256', 'outputDirectory', 'mainCommit'}


def load(path):
    path = Path(path)
    if path.is_symlink() or not path.is_file() or path.stat().st_size > 16384 or path.stat().st_mode & 0o077:
        raise ValueError('Profile must be a private regular file')
    p = json.loads(path.read_text())
    if set(p) != FIELDS: raise ValueError('Unexpected game deployment profile fields')
    for key in ('gameId', 'databaseId', 'pvfSha256', 'gameSha256', 'publicKeySha256'):
        if not re.fullmatch('[0-9a-f]{64}', p[key]): raise ValueError('Invalid identity: ' + key)
    if not re.fullmatch('sha256:[0-9a-f]{64}', p['gameImage']): raise ValueError('Invalid base image')
    if not re.fullmatch('[0-9a-f]{40}', p['mainCommit']): raise ValueError('Invalid main revision')
    for key in ('gameContainer', 'databaseContainer', 'network'):
        if not re.fullmatch('[a-z][a-z0-9_-]{2,63}', p[key]): raise ValueError('Invalid target name')
    if p['gameContainer'] == p['databaseContainer'] or p['gameId'] == p['databaseId']:
        raise ValueError('Game and database targets must differ')
    for key in ('dataDirectory', 'pvf', 'outputDirectory'):
        v = Path(p[key])
        if not v.is_absolute() or v.resolve() != v or len(v.parts) < 4:
            raise ValueError('Use direct, explicit deployment paths')
    source, output = Path(p['dataDirectory']), Path(p['outputDirectory'])
    if source == output or source in output.parents or output in source.parents:
        raise ValueError('Recovery source and output must be separate')
    return p


def docker(*args, **kwargs):
    return subprocess.check_output(['docker', *args], **kwargs)


def inspect(name):
    return json.loads(docker('inspect', name))[0]


def digest(path):
    with Path(path).open('rb') as stream: return hashlib.file_digest(stream, 'sha256').hexdigest()


def private_json(path, value):
    with open(path, 'x') as stream:
        os.chmod(path, 0o600); json.dump(value, stream, indent=2)


def states(exclude):
    ids = docker('ps', '-aq', text=True).split()
    return {o['Id']: (o['Image'], o['State']['StartedAt'], o['State']['Running'], o['RestartCount'])
            for o in json.loads(docker('inspect', *ids)) if o['Id'] != exclude}


def validate_target(p, image):
    game, db = inspect(p['gameContainer']), inspect(p['databaseContainer'])
    if game['Id'] != p['gameId'] or game['Image'] != p['gameImage'] or db['Id'] != p['databaseId']:
        raise ValueError('Container identity changed')
    if not game['State']['Running'] or not db['State']['Running']:
        raise ValueError('Expected running source game and database')
    if [m['Source'] for m in game['Mounts'] if m['Destination'] == '/data'] != [p['dataDirectory']]:
        raise ValueError('Game data mount changed')
    if [m['Source'] for m in game['Mounts'] if m['Destination'] == '/data/Script.pvf' and not m['RW']] != [p['pvf']]:
        raise ValueError('Paired read-only PVF mount changed')
    if set(game['NetworkSettings']['Networks']) != {p['network']}:
        raise ValueError('Unexpected game networks')
    if game['HostConfig'].get('PortBindings') or not inspect(p['network']).get('Internal'):
        raise ValueError('This entrypoint only replaces the isolated, unpublished game')
    env = dict(x.split('=', 1) for x in game['Config']['Env'] if '=' in x)
    if env.get('QINGKONG_VARIANT', 'replacement') != 'replacement':
        raise ValueError('Only replacement source is accepted')
    if env.get('OPEN_CHANNEL') != '15' or env.get('PLUGIN_MODE') != 'dp2-frida':
        raise ValueError('This integration entrypoint is restricted to the channel-15 plugin test instance')
    db_env = dict(x.split('=', 1) for x in db['Config']['Env'] if '=' in x)
    uuid = docker('exec', '-e', 'MYSQL_PWD=' + db_env['MYSQL_ROOT_PASSWORD'], p['databaseContainer'],
                  'mysql', '-uroot', '-N', '-e', 'SELECT @@server_uuid;', text=True).strip()
    if uuid != p['databaseUuid']: raise ValueError('Database UUID changed')
    data = Path(p['dataDirectory'])
    for file, pin in ((data / 'df_game_r', p['gameSha256']),
                      (data / 'publickey.pem', p['publicKeySha256']), (p['pvf'], p['pvfSha256'])):
        if digest(file) != pin: raise ValueError('Game/PVF/key identity changed')
    assets.validate_game(data / 'df_game_r')
    built = inspect(image); labels = built['Config'].get('Labels') or {}
    if (built['Id'] != image or labels.get('org.opencontainers.image.revision') != p['mainCommit']
            or labels.get('org.opencontainers.image.base.digest') != p['gameImage']
            or labels.get('io.dof.runtime') != 'native-server-v1'
            or built['Config']['Entrypoint'] != ['/bin/bash', '/opt/dnf/ubuntu/entrypoint.sh']):
        raise ValueError('Replacement image must bind the exact main revision and base')
    layers = inspect(p['gameImage'])['RootFS']['Layers']
    if built['RootFS']['Layers'][:len(layers)] != layers: raise ValueError('Image base layers differ')
    runtime_limits(game['HostConfig'])
    return game, env


def runtime_limits(host):
    args = []
    for key, flag in (('Memory', '--memory'), ('MemorySwap', '--memory-swap'),
                      ('PidsLimit', '--pids-limit'), ('CpuShares', '--cpu-shares'),
                      ('CpuQuota', '--cpu-quota'), ('CpuPeriod', '--cpu-period')):
        value = host.get(key)
        if value:
            if type(value) is not int: raise ValueError('Invalid resource limit: ' + key)
            args += [flag, str(value)]
    if host.get('NanoCpus'):
        if host.get('CpuQuota') or host.get('CpuPeriod'):
            raise ValueError('Conflicting CPU limit modes')
        value = host['NanoCpus']
        if type(value) is not int or value < 0: raise ValueError('Invalid CPU budget')
        args += ['--cpus', str(value // 1000000000) + '.' + str(value % 1000000000).zfill(9)]
    for key, flag in (('CpusetCpus', '--cpuset-cpus'), ('CpusetMems', '--cpuset-mems')):
        if host.get(key): args += [flag, host[key]]
    for limit in host.get('Ulimits') or []:
        if (not re.fullmatch('[a-z]+', limit['Name']) or
                any(type(limit[k]) is not int or limit[k] < -1 for k in ('Soft', 'Hard'))):
            raise ValueError('Invalid ulimit')
        args += ['--ulimit', '{}={}:{}'.format(limit['Name'], limit['Soft'], limit['Hard'])]
    return args


def imported_environment(env):
    result = {k: v for k, v in env.items() if not k.startswith('QINGKONG_')}
    result.update(LAUNCHER_MODE='native', RAID_EXTENSION='on', DOF_DATABASE_MODE='existing',
                  DOF_DB_GAME_PASSWORD=env['DNF_DB_GAME_PASSWORD'])
    return result


def stage_data(source, destination, runtime, old_managed, new_managed):
    source, destination = Path(source), Path(destination)
    # This explicit deployment import is the only conversion of retired paths.
    old = source / 'qingkong-extension'
    inputs = json.loads((ROOT / 'metadata/dof/source-audit/server-inputs/replacement.lock.json').read_text())['files']
    for row in inputs:
        if digest(old / row['path']) != row['sha256']: raise ValueError('Source replacement pin mismatch')
    if any(p.is_symlink() for p in source.rglob('*')): raise ValueError('Source data contains links')
    shutil.copytree(source, destination, ignore=lambda folder, names:
                    {'qingkong-extension', 'Script.pvf'} & set(names) if Path(folder) == source else set())
    state = destination / 'dof-easy'
    assets.install(runtime, state)
    mapping = {'qk_plugin_config.ini': 'plugin.ini', 'qk_plugin_antonRaid.ini': 'anton-raid.ini',
               'qk_plugin_lukeRaid.ini': 'luke-raid.ini', 'dungeon.lst': 'dungeon.lst'}
    for legacy, current in mapping.items():
        candidates = [p for p in (old / 'config' / legacy, old / legacy) if p.is_file()]
        if not candidates and current == 'dungeon.lst': continue
        if len(candidates) != 1: raise ValueError('Ambiguous or missing imported config: ' + legacy)
        text = candidates[0].read_text(encoding='utf-8-sig')
        if current == 'plugin.ini':
            for key in ('Inlay_function', 'Equ_unlocked_instantly', 'Task_Completion_coupon'):
                text, count = re.subn(r'(?m)^' + key + r'\s*=.*$', key + '=0', text)
                if count != 1: raise ValueError('Missing native feature ownership switch')
        (state / 'config' / current).write_text(text)
    config = configparser.ConfigParser(interpolation=None); config.optionxform = str
    config.read(old / 'config.ini')
    config['sever']['LogPath'] = '/var/log/dof-easy/'
    config['Gate']['DfGameR'] = '/home/neople/game/df_game_r'
    with (state / 'config/gateway.ini').open('w') as stream: config.write(stream, space_around_delimiters=False)
    (state / 'config/gateway.ini').chmod(0o600)
    shutil.copyfile(old / 'privatekey.pem', state / 'keys/privatekey.pem')
    (state / 'keys/privatekey.pem').chmod(0o600)
    # Accept only scripts identical to the inspected old image. Custom scripts
    # require deliberate review; do not overwrite them using the marker alone.
    for path in new_managed.rglob('*'):
        if not path.is_file(): continue
        rel = path.relative_to(new_managed); target = destination / rel
        previous = old_managed / rel
        if target.exists() and (not previous.is_file() or digest(target) != digest(previous)):
            raise ValueError('Operator-managed script differs: ' + str(rel))
        target.parent.mkdir(parents=True, exist_ok=True); shutil.copyfile(path, target); target.chmod(0o700)
    hashes = {p.relative_to(new_managed).as_posix(): digest(p) for p in new_managed.rglob('*') if p.is_file()}
    marker = destination / '.dnf-ubuntu.json'
    marker.write_text(json.dumps(dict(schema=1, platform='ubuntu24', files=hashes))); marker.chmod(0o600)
    assets.verify(state / 'assets'); assets.verify_config(state)


def main():
    ap = argparse.ArgumentParser(description=__doc__)
    ap.add_argument('--profile', required=True, type=Path)
    ap.add_argument('--runtime', required=True, type=Path)
    ap.add_argument('--image', required=True)
    ap.add_argument('--replace-game', action='store_true')
    args = ap.parse_args()
    if os.geteuid() != 0: raise ValueError('Requires root on the target host')
    p = load(args.profile)
    if not re.fullmatch('sha256:[0-9a-f]{64}', args.image): raise ValueError('Use an immutable image ID')
    assets.verify(args.runtime)
    game, env = validate_target(p, args.image)
    if not args.replace_game:
        print(json.dumps({'preflight': 'passed', 'mutated': False})); return
    root = Path(p['outputDirectory'])
    if root.exists(): raise ValueError('Deployment output must be new')
    require_space([root.parent, '/var/lib/docker'], 1024 ** 3)
    original_states = states(p['gameId'])
    root.mkdir(mode=0o700, parents=True)
    private_json(root / 'source-container.json', game)
    private_json(root / 'profile.json', p)
    private_json(root / 'protected-containers.json', original_states)
    old_name = p['gameContainer'] + '-recovery-' + p['mainCommit'][:8]
    probe = p['gameContainer'] + '-image-' + p['mainCommit'][:8]
    stopped = renamed = False
    try:
        docker('create', '--name', probe, '--entrypoint', '/bin/true', args.image)
        for name, container in (('old-managed', p['gameContainer']), ('new-managed', probe)):
            docker('cp', container + ':/opt/dnf/ubuntu/managed', str(root / name))
        docker('rm', probe)
        docker('stop', '-t', '30', p['gameContainer']); stopped = True
        stage_data(p['dataDirectory'], root / 'data', args.runtime, root / 'old-managed', root / 'new-managed')
        values = imported_environment(env)
        envfile = root / 'runtime.env'
        if any('\n' in v or '\r' in v for v in values.values()): raise ValueError('Invalid environment value')
        envfile.write_text(''.join(k + '=' + v + '\n' for k, v in sorted(values.items()))); envfile.chmod(0o600)
        docker('rename', p['gameContainer'], old_name); renamed = True
        command = ['run', '-d', '--name', p['gameContainer'], '--network', p['network'],
                   '--restart', 'no', '--env-file', str(envfile), '--log-opt', 'max-size=10m',
                   '--log-opt', 'max-file=2', '--shm-size', str(game['HostConfig']['ShmSize'])]
        for mount in game['Mounts']:
            if mount['Type'] != 'bind': raise ValueError('Only explicit bind mounts are supported')
            src = str(root / 'data') if mount['Destination'] == '/data' else mount['Source']
            command += ['--mount', 'type=bind,src=' + src + ',dst=' + mount['Destination'] + ('' if mount['RW'] else ',readonly')]
        command += runtime_limits(game['HostConfig'])
        command.append(args.image); docker(*command)
        healthy = False
        for _ in range(120):
            current = inspect(p['gameContainer'])
            if not current['State']['Running']: break
            if current['State'].get('Health', {}).get('Status') == 'healthy': healthy = True; break
            time.sleep(5)
        if not healthy: raise RuntimeError('New runtime did not become healthy; recovery required')
        current_states = states(inspect(p['gameContainer'])['Id'])
        if any(current_states.get(k) != v for k, v in original_states.items()):
            raise RuntimeError('A protected container changed; investigate before proceeding')
        private_json(root / 'deployment.json', dict(mainCommit=p['mainCommit'], image=args.image,
            previousContainer=old_name, game=p['gameContainer'], healthy=True, gameAcceptance=False))
        print(json.dumps({'deployed': True, 'healthy': True, 'gameAcceptance': False, 'recoveryContainer': old_name}))
    except Exception:
        # The original stopped container still owns the untouched original /data.
        if renamed:
            current = subprocess.run(['docker', 'inspect', p['gameContainer']], capture_output=True)
            if current.returncode == 0:
                docker('stop', '-t', '20', p['gameContainer'])
                docker('rename', p['gameContainer'], p['gameContainer'] + '-failed-' + p['mainCommit'][:8])
            docker('rename', old_name, p['gameContainer'])
        if stopped: docker('start', p['gameContainer'])
        raise


if __name__ == '__main__': main()
