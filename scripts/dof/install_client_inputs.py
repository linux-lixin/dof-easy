"""Install only the private publication inputs into the existing isolated web volume.

Does not operate Docker, initialize databases, modify game/client directories, or
run uploaded code. The existing web deployment guard must pass before any write.
"""
import argparse
import hashlib
import json
import os
from pathlib import Path
import shutil
import tarfile
import tempfile
import run_isolated as deployment
from deployment_profile import load as load_profile, bind as bind_profile
ROOT = deployment.ROOT
INSTANCE = deployment.INSTANCE
validate_files = deployment.validate_files

NAMES = {'inputs/binding.json', 'inputs/template.dll', 'inputs/game-public.der',
         'inputs/启动器.exe', 'inputs/ijl15.dll'}
PUBLIC_PIN = ''
BINARY_PINS = {'启动器.exe': '5b0c5195c58e1583cb5456e2b7139acf02f6ebb34ff86e4445468c91184be733',
               'ijl15.dll': 'd2b207e32f1d366c29f43d080366b7880d87659aaeb93a9bd8e36f44bf54b678'}


def read_bundle(path):
    if not PUBLIC_PIN or len(PUBLIC_PIN)!=64:raise ValueError('Explicit public-key hash binding required')
    values = {}
    with tarfile.open(path) as archive:
        for item in archive:
            limit = 16384 if item.name.endswith('binding.json') else 64 * 1024 * 1024
            if item.name not in NAMES or item.name in values or not item.isfile() or item.size > limit:
                raise ValueError('Unexpected client publication archive entry')
            stream = archive.extractfile(item)
            values[item.name] = stream.read(limit + 1)
            if len(values[item.name]) != item.size:
                raise ValueError('Publication input size mismatch')
    if set(values) != NAMES:
        raise ValueError('Publication input archive is incomplete')
    binding = json.loads(values['inputs/binding.json'])
    if (binding.get('instanceId') != INSTANCE
            or binding.get('environment') != 'isolated'
            or binding.get('adapterVersion') != 'dof-native-1'
            or set(binding.get('sha256', {})) != {name[7:] for name in NAMES - {'inputs/binding.json'}}):
        raise ValueError('Publication input binding mismatch')
    for name, value in values.items():
        if name.endswith('binding.json'):
            continue
        actual = hashlib.sha256(value).hexdigest()
        if actual != binding['sha256'][name[7:]]:
            raise ValueError('Publication input checksum mismatch')
        pin = PUBLIC_PIN if name.endswith('game-public.der') else BINARY_PINS.get(name[7:])
        if pin and actual != pin:
            raise ValueError('Publication input is not the current paired version/identity')
    return values


def install(archive):
    if os.geteuid() != 0:
        raise RuntimeError('Requires root')
    validate_files(ROOT)
    values = read_bundle(archive)
    peak=sum(len(value) for value in values.values()) + (ROOT/'runtime.env').stat().st_size*3 + 1024*1024
    if shutil.disk_usage(ROOT).free < 2*1024**3 + peak:
        raise RuntimeError('Insufficient space: requires 2 GiB reserve plus installation peak')
    destination = ROOT / 'data/dof-client-publication'
    if destination.exists() or destination.is_symlink():
        raise ValueError('Existing publication workspace will not be overwritten')
    env_path = ROOT / 'runtime.env'
    original = env_path.read_bytes()
    if b'DOF_CLIENT_WORKSPACE=' in original:
        raise ValueError('Publication environment is already configured')
    temp = Path(tempfile.mkdtemp(prefix='.client-inputs-', dir=ROOT / 'data'))
    try:
        (temp / 'inputs').mkdir(mode=0o700)
        for name, value in values.items():
            fd = os.open(temp / name, os.O_WRONLY | os.O_CREAT | os.O_EXCL, 0o600)
            with os.fdopen(fd, 'wb') as stream:
                stream.write(value); stream.flush(); os.fsync(stream.fileno())
        temp.rename(destination)
        backup = ROOT / 'runtime-before-client-publication.env'
        fd = os.open(backup, os.O_WRONLY | os.O_CREAT | os.O_EXCL, 0o600)
        with os.fdopen(fd, 'wb') as stream:
            stream.write(original); stream.flush(); os.fsync(stream.fileno())
        fd, temporary = tempfile.mkstemp(prefix='.runtime-client-', dir=ROOT)
        with os.fdopen(fd, 'wb') as stream:
            stream.write(original.rstrip(b'\n') + b'\nDOF_CLIENT_WORKSPACE=/app/data/dof-client-publication\n')
            stream.flush(); os.fsync(stream.fileno())
        os.replace(temporary, env_path)
        validate_files(ROOT)
    finally:
        if temp.exists():
            shutil.rmtree(temp)
    return {'installed': True, 'environment': 'isolated', 'inputs': len(values),
            'existingClientModified': False, 'webRestarted': False, 'productionModified': False}


if __name__ == '__main__':
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--profile', type=Path, required=True)
    parser.add_argument('--archive', type=Path, required=True)
    args=parser.parse_args()
    profile=load_profile(args.profile)
    deployment.configure(profile);bind_profile(globals(),profile)
    print(json.dumps(install(args.archive)))
