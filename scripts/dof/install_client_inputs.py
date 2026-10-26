"""Install private publication inputs into one existing container-managed instance.

Checks the live database and public key without initializing databases, changing
keys, restarting services, or replacing an existing publication workspace.
"""
import argparse
import hashlib
import json
import os
from pathlib import Path
import re
import shutil
import subprocess
import sys
import tarfile
import tempfile

ROOT = Path(__file__).resolve().parents[2]
sys.path.insert(0, str(ROOT))
from scripts.container import manage
from scripts.protocol.dof.prepare_client_inputs import PINNED_BINARIES
from scripts.protocol.dof.client_format import validate_container, VERSION
from scripts.dof.client_reader import RECIPE

NAMES = {'inputs/binding.json', 'inputs/template.dll', 'inputs/game-public.der',
         *('inputs/' + name for name in PINNED_BINARIES)}


def read_bundle(path, instance, public_sha256, database_uuid):
    if len(public_sha256) != 64 or any(c not in '0123456789abcdef' for c in public_sha256):
        raise ValueError('Explicit public-key hash binding required')
    values = {}
    with tarfile.open(path) as archive:
        for item in archive:
            limit = (16384 if item.name.endswith('binding.json') else
                     4096 if item.name.endswith('game-public.der') else
                     1024 * 1024 if item.name.endswith('template.dll') else 64 * 1024 * 1024)
            if item.name not in NAMES or item.name in values or not item.isfile() or not 0 <= item.size <= limit:
                raise ValueError('Unexpected client publication archive entry')
            stream = archive.extractfile(item)
            values[item.name] = stream.read(limit + 1)
            if len(values[item.name]) != item.size:
                raise ValueError('Publication input size mismatch')
    if set(values) != NAMES:
        raise ValueError('Publication input archive is incomplete')
    binding = json.loads(values['inputs/binding.json'])
    if (binding.get('instanceId') != instance or binding.get('environment') != 'isolated'
            or binding.get('adapterVersion') != 'dof-native-1'
            or binding.get('databaseUuid') != database_uuid
            or binding.get('clientFormatVersion') != VERSION or binding.get('readerRecipe') != RECIPE
            or set(binding.get('sha256', {})) != {name[7:] for name in NAMES - {'inputs/binding.json'}}):
        raise ValueError('Publication input binding mismatch')
    for name, value in values.items():
        if name.endswith('binding.json'):
            continue
        actual = hashlib.sha256(value).hexdigest()
        if actual != binding['sha256'][name[7:]]:
            raise ValueError('Publication input checksum mismatch')
        pin = public_sha256 if name.endswith('game-public.der') else PINNED_BINARIES.get(name[7:])
        if pin and actual != pin:
            raise ValueError('Publication input is not the current paired version/identity')
    validate_container(values['inputs/template.dll'])
    return values


def regular(path, root):
    path = Path(path)
    if not path.is_relative_to(root):
        raise ValueError('Publication path escaped instance')
    for parent in (path, *path.parents):
        if parent.is_symlink():
            raise ValueError('Instance publication paths cannot contain links')
        if parent == root:
            break
    if not path.is_file():
        raise ValueError('Missing instance publication identity')
    return path


def checked_identity(root, query):
    root = Path(root).absolute()
    if root.resolve() != root or not re.fullmatch(r'[a-z][a-z0-9-]{0,39}', root.name):
        raise ValueError('Invalid or linked container instance path')
    values = manage.read_env(root)
    if values.get('LAUNCHER_MODE') != 'native':
        raise ValueError('Client publication requires a native instance')
    saved = json.loads(regular(root / 'database-web.json', root).read_text())
    expected = {'databaseUuid': manage.database.identity(query), 'instanceId': 'dof-' + root.name,
                'native': True, 'state': 'ready'}
    if saved != expected:
        raise ValueError('Current database identity differs from instance binding')
    data = root / 'runtime/admin/data'
    web = json.loads(regular(data / 'dof-isolated.json', root).read_text())
    if web != dict(instanceId=expected['instanceId'], databaseUuid=expected['databaseUuid'], environment='isolated'):
        raise ValueError('Web instance identity differs from database binding')
    binding = query("SELECT CONCAT(environment,'|',database_uuid,'|',adapter_version) "
                    "FROM dnf_service.DOF_INSTANCE_BINDING WHERE instance_id='" + expected['instanceId'] + "';")
    if binding != 'isolated|' + expected['databaseUuid'] + '|dof-native-1':
        raise ValueError('Native database instance binding differs from web identity')
    public = regular(root / 'client/publickey.pem', root)
    exported = subprocess.run(['openssl', 'pkey', '-pubin', '-in', str(public), '-outform', 'DER'],
                              check=True, capture_output=True).stdout
    live = regular(root / 'runtime/game/data/publickey.pem', root)
    if public.read_bytes() != live.read_bytes():
        raise ValueError('Exported public key differs from current game public key')
    return expected['instanceId'], hashlib.sha256(exported).hexdigest(), data, expected['databaseUuid']


def install(archive, root, query):
    root = Path(root).absolute()
    instance, public_sha256, data, database_uuid = checked_identity(root, query)
    values = read_bundle(archive, instance, public_sha256, database_uuid)
    peak = sum(len(value) for value in values.values()) + 1024 * 1024
    if shutil.disk_usage(data).free < 2 * 1024**3 + peak:
        raise RuntimeError('Insufficient space: requires 2 GiB reserve plus installation peak')
    destination = data / 'dof-client-publication'
    if destination.exists() or destination.is_symlink():
        raise ValueError('Existing publication workspace will not be overwritten')
    with tempfile.TemporaryDirectory(prefix='.client-inputs-', dir=data) as temporary:
        stage = Path(temporary) / 'workspace'
        (stage / 'inputs').mkdir(parents=True, mode=0o700)
        stage.chmod(0o700)
        for name, value in values.items():
            fd = os.open(stage / name, os.O_WRONLY | os.O_CREAT | os.O_EXCL, 0o600)
            with os.fdopen(fd, 'wb') as stream:
                stream.write(value); stream.flush(); os.fsync(stream.fileno())
        if destination.exists() or destination.is_symlink():
            raise ValueError('Publication workspace appeared during preparation')
        stage.rename(destination)
    return {'installed': True, 'instanceId': instance, 'inputs': len(values),
            'webRestarted': False, 'databaseModified': False, 'clientModified': False}


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--instance', default='main')
    parser.add_argument('--archive', type=Path, required=True)
    args = parser.parse_args()
    root = manage.instance_path(args.instance)
    manage.docker_check()
    with manage.locked(root):
        print(json.dumps(install(args.archive, root, lambda sql: manage.database_query(root, sql))))


if __name__ == '__main__':
    main()
