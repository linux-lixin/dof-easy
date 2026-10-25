"""Create/recreate only the dedicated Dof development web container.

Run on the Ubuntu host as root. Requires an existing locally built image ID and
the private files produced by prepare_isolated.py. Never builds,
pulls, initializes a database, changes credentials, or operates game containers.
"""
import argparse
import datetime
import json
import os
from pathlib import Path
import re
import subprocess
try:
    from .space_budget import require_space
except ImportError:
    from space_budget import require_space

from deployment_profile import load as load_profile, bind as bind_profile
ROOT = Path('/opt/dof-isolated/web-admin')
DB = 'dof-isolated-db'
WEB = 'dof-isolated-web'
GAME = 'dof-isolated-game'
NETWORK = 'dof-isolated-internal'
UUID = ''
INSTANCE = 'dof-isolated'
DB_USER = 'dof_adapter'
GAME_PVF = Path('/opt/dof-isolated/game/Script.pvf')
PROTECTED = (DB, GAME)


def configure(profile):
    bind_profile(globals(), profile)


def inspect(name):
    return json.loads(subprocess.check_output(['docker', 'inspect', name]))[0]


def protected_state():
    return {name: [obj['Image'], obj['State']['StartedAt'], obj['RestartCount']]
            for name in PROTECTED for obj in [inspect(name)]}


def validate_files(root):
    expected = {'QINGKONG_ADMIN_ENABLED': 'true', 'QINGKONG_ENVIRONMENT': 'isolated',
                'QINGKONG_DATABASE_UUID': UUID, 'QINGKONG_INSTANCE_ID': INSTANCE,
                'QINGKONG_ADAPTER_VERSION': 'qingkong-2.8-current-20261024',
                'SPRING_DATASOURCE_USERNAME': DB_USER}
    env_file = root / 'runtime.env'
    if env_file.stat().st_mode & 0o077:
        raise ValueError('Runtime credentials must not be group/world readable')
    values = dict(line.split('=', 1) for line in env_file.read_text().splitlines() if line)
    if any(values.get(k) != v for k, v in expected.items()):
        raise ValueError('Isolated runtime identity mismatch')
    if values.get('SPRING_DATASOURCE_URL') != ('jdbc:mysql://' + DB + ':3306/'
            'dnf_service?useUnicode=true&characterEncoding=UTF-8&useSSL=false'):
        raise ValueError('Unexpected database connection')
    marker = json.loads((root / 'data/qingkong-isolated.json').read_text())
    if marker != {'instanceId': expected['QINGKONG_INSTANCE_ID'], 'databaseUuid': UUID, 'environment': 'isolated'}:
        raise ValueError('Isolated deployment marker mismatch')
    if 'QINGKONG_CLIENT_WORKSPACE' in values:
        if values['QINGKONG_CLIENT_WORKSPACE'] != '/app/data/qingkong-client-publication':
            raise ValueError('Unexpected client publication workspace')
        workspace = root / 'data/qingkong-client-publication'
        if workspace.is_symlink() or not workspace.is_dir() or workspace.stat().st_mode & 0o077:
            raise ValueError('Client publication workspace must be a private directory')
        binding = json.loads((workspace / 'inputs/binding.json').read_text())
        if (binding.get('instanceId') != expected['QINGKONG_INSTANCE_ID']
                or binding.get('environment') != 'isolated'
                or binding.get('adapterVersion') != expected['QINGKONG_ADAPTER_VERSION']):
            raise ValueError('Client publication sources belong to a different instance')
    if 'QINGKONG_GAME_PVF' in values:
        if values['QINGKONG_GAME_PVF'] != '/app/paired/Script.pvf':
            raise ValueError('Unexpected paired PVF container path')
        if not GAME_PVF.is_file() or GAME_PVF.resolve() != GAME_PVF:
            raise ValueError('Paired PVF source must be a regular, direct file')
        game = inspect(GAME)
        if [m['Source'] for m in game['Mounts'] if m['Destination'] == '/data/Script.pvf'] != [str(GAME_PVF)]:
            raise ValueError('Paired PVF differs from the isolated game mount')


def command(image, values=None):
    args = ['docker', 'run', '-d', '--name', WEB, '--network', NETWORK,
            '--memory', '384m', '--memory-swap', '384m', '--cpus', '.5', '--pids-limit', '128',
            '--restart', 'no', '--log-opt', 'max-size=10m', '--log-opt', 'max-file=2',
            '--env-file', str(ROOT / 'runtime.env'),
            '-v', str(ROOT / 'data') + ':/app/data', '-v', str(ROOT / 'logs') + ':/app/logs']
    values = values or {}
    if 'QINGKONG_GAME_PVF' in values:
        if values['QINGKONG_GAME_PVF'] != '/app/paired/Script.pvf':
            raise ValueError('Unexpected paired PVF container path')
        args += ['--mount', 'type=bind,src=' + str(GAME_PVF) + ',dst=/app/paired/Script.pvf,readonly']
    return args + [image]


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--profile', type=Path, required=True, help='Private verified isolation target JSON')
    parser.add_argument('--image', required=True, help='Exact locally available sha256 image ID')
    parser.add_argument('--replace-web', action='store_true', help='Recreate only the dedicated development web container')
    parser.add_argument('--peak-bytes', type=int, required=True,
                        help='Additional peak storage estimate, excluding the mandatory 2 GiB reserve')
    args = parser.parse_args()
    configure(load_profile(args.profile))
    if os.geteuid() != 0:
        raise RuntimeError('Requires root')
    docker_root = subprocess.check_output(['docker', 'info', '--format', '{{.DockerRootDir}}'], text=True).strip()
    if not docker_root.startswith('/'):
        raise RuntimeError('Cannot determine Docker storage filesystem')
    space = require_space([ROOT, docker_root], args.peak_bytes)
    if not re.fullmatch(r'sha256:[0-9a-f]{64}', args.image):
        raise ValueError('Use an immutable local image ID')
    validate_files(ROOT)
    image = inspect(args.image)
    if image['Id'] != args.image or image['Config']['Entrypoint'] != ['/app/dof-entrypoint.sh']:
        raise ValueError('Image is not the isolated Dof runtime')
    before = protected_state()
    existing = subprocess.run(['docker', 'inspect', WEB], capture_output=True)
    previous = None
    if existing.returncode == 0:
        current = json.loads(existing.stdout)[0]
        env = dict(v.split('=', 1) for v in current['Config']['Env'] if '=' in v)
        if env.get('QINGKONG_DATABASE_UUID') != UUID or env.get('QINGKONG_ENVIRONMENT') != 'isolated':
            raise ValueError('Existing container does not match the isolated identity')
        if not args.replace_web:
            raise ValueError('Web container exists; use --replace-web for explicit development-only recreation')
        previous = current['Image']
        # Preserve only a safe recovery descriptor: inspect contains credentials.
        recovery = ROOT / ('recovery-' + datetime.datetime.now(datetime.timezone.utc).strftime('%Y%m%dT%H%M%SZ') + '.json')
        recovery.write_text(json.dumps({'previousImage': previous, 'replacementImage': args.image,
            'container': WEB, 'restore': 'Rerun with the same --profile, --replace-web --image previousImage --peak-bytes assessedPeakBytes'}))
        space = require_space([ROOT, docker_root], args.peak_bytes)
        subprocess.run(['docker', 'stop', '-t', '20', WEB], check=True, stdout=subprocess.DEVNULL)
        subprocess.run(['docker', 'rm', WEB], check=True, stdout=subprocess.DEVNULL)
    try:
        values = dict(line.split('=', 1) for line in (ROOT / 'runtime.env').read_text().splitlines() if line)
        subprocess.run(command(args.image, values), check=True, stdout=subprocess.DEVNULL)
    except subprocess.CalledProcessError:
        # Do not attempt database rollback. Original image ID is retained above.
        raise RuntimeError('Development web creation failed; use the saved recovery descriptor') from None
    if protected_state() != before:
        raise RuntimeError('Protected container state changed; stop deployment investigation here')
    obj = inspect(WEB)
    result = {'container': WEB, 'image': args.image, 'previousImage': previous,
              'createdAt': obj['Created'], 'environment': 'isolated', 'completeReplacement': False,
              'publishedPorts': obj['HostConfig']['PortBindings'], 'protectedContainersUnchanged': True,
              'spaceBudget': space,
              'verification': 'Check authenticated isolation identity and retained state, then perform the documented client acceptance; creation alone is not readiness'}
    (ROOT / 'deployment.json').write_text(json.dumps(result, indent=2))
    print(json.dumps(result))


if __name__ == '__main__':
    main()
