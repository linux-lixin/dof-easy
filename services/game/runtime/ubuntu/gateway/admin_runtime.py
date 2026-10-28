"""Prepare the fixed raid mailbox for an explicitly bound test instance."""
import hashlib
import json
import os
from pathlib import Path
import re
import tempfile

BASE = Path(__file__).resolve().parent
from gateway import assets


def selected_channels(value):
    # The installed gateway runs outside /opt/dnf/ubuntu. Keep this small
    # protocol parser standalone; tests compare it to the launcher's roster.
    selected = set()
    for item in value.split(','):
        if not re.fullmatch(r'\d+(?:-\d+)?', item):
            raise ValueError('Invalid runtime channel roster')
        bounds = [int(v) for v in item.split('-')]
        if bounds[0] > bounds[-1] or bounds[-1] > 56:
            raise ValueError('Invalid runtime channel range')
        selected.update(range(bounds[0], bounds[-1] + 1))
    if not selected or not selected <= ({1, 6, 7} | set(range(11, 40)) | set(range(52, 57))):
        raise ValueError('Unsupported runtime channels')
    return sorted(selected)


def regular_directory(path):
    path = Path(path)
    if path != path.resolve() or not path.is_dir():
        raise ValueError('Runtime apply requires real directories without links')
    return path


def prepare(state, values=None, helper_root=None):
    values = os.environ if values is None else values
    if values.get('RAID_EXTENSION', 'off') != 'on':
        return None
    instance, database = values.get('DOF_INSTANCE_ID', ''), values.get('DOF_DATABASE_UUID', '')
    # Older deployments keep file/GM management; never invent an instance binding.
    if not instance or not database:
        return None
    if not re.fullmatch(r'[a-z0-9][a-z0-9-]{2,63}', instance) or not re.fullmatch(r'[0-9a-fA-F-]{36}', database):
        raise ValueError('Invalid native runtime instance binding')
    helper_root=regular_directory(Path('/opt/dnf/compat/i386') if helper_root is None else helper_root)
    helper=helper_root/'libdof-admin-runtime.so';source=helper_root/'admin-runtime-digest.c'
    if helper.is_symlink() or source.is_symlink() or not helper.is_file() or not source.is_file() or helper.stat().st_size>1048576 or source.stat().st_size>65536:
        raise ValueError('Native digest helper or its source is missing/unrecognized')
    helper_raw=helper.read_bytes();source_raw=source.read_bytes()
    if len(helper_raw)<20 or helper_raw[:6]!=b'\x7fELF\x01\x01' or helper_raw[18:20]!=b'\x03\x00':
        raise ValueError('Native digest helper requires the i386 ELF ABI')
    contract=json.loads((BASE/'runtime-apply.json').read_text())
    actual=dict(executorScriptSHA256=hashlib.sha256((BASE/'admin_apply.js').read_bytes()).hexdigest(),
                gameAbiSHA256=hashlib.sha256(assets.GAME_ABI.read_bytes()).hexdigest(),
                digestHelperSourceSHA256=hashlib.sha256(source_raw).hexdigest())
    if contract!=dict(schema=1,**actual):
        raise ValueError('Runtime executor, ABI or digest helper source differs from the image contract')
    config = regular_directory(Path(state) / 'config')
    marker = config / '.admin-instance.json'
    if marker.is_symlink() or not marker.is_file() or marker.stat().st_size > 4096:
        raise ValueError('Native runtime management marker missing')
    expected = dict(instanceId=instance, databaseUuid=database, environment='isolated')
    if json.loads(marker.read_text()) != expected:
        raise ValueError('Native runtime management marker differs from deployment')
    group = int(values.get('SERVER_GROUP', '3'))
    if not 1 <= group <= 6:
        raise ValueError('Invalid server group')
    names = [('cain', 'diregie', 'siroco', 'prey', 'casillas', 'hilder')[group - 1] + f'{channel:02d}'
             for channel in selected_channels(values.get('OPEN_CHANNEL', '11,52'))]
    # Only the offline full-stack fixture may select its literal local.cfg name.
    if values.get('DOF_OFFLINE_RUNTIME_CHANNEL') == 'local':
        if values.get('CORE_PUBLIC_IP') != '127.0.0.1' or values.get('CUR_SG_DB_HOST') != '127.0.0.1':
            raise ValueError('Offline channel alias requires private loopback fixture')
        names = ['local']
    payload = {row['path']: row for row in assets.manifest()}
    binding = dict(schema=1, instanceId=instance, databaseUuid=database, channels=names,
                   pluginSHA256=payload['lib/libdof-plugin.so']['sha256'],
                   pluginBytes=payload['lib/libdof-plugin.so']['bytes'],
                   digestHelperSHA256=hashlib.sha256(helper_raw).hexdigest(),digestHelperBytes=len(helper_raw),
                   digestHelperSourceSHA256=actual['digestHelperSourceSHA256'],
                   executorScriptSHA256=hashlib.sha256((BASE / 'admin_apply.js').read_bytes()).hexdigest(),
                   gameAbiSHA256=hashlib.sha256(assets.GAME_ABI.read_bytes()).hexdigest())
    directory = config / '.runtime-apply'
    if directory.is_symlink():
        raise ValueError('Runtime apply directory must not be a link')
    directory.mkdir(mode=0o700, exist_ok=True)
    regular_directory(directory)
    destination = directory / 'binding.json'
    if destination.is_symlink():
        raise ValueError('Runtime binding must not be a link')
    if destination.exists():
        if not destination.is_file() or destination.stat().st_size > 16384:
            raise ValueError('Invalid existing runtime binding')
        old = json.loads(destination.read_text())
        if old.get('instanceId') != instance or old.get('databaseUuid') != database:
            raise ValueError('Existing runtime binding belongs to another instance')
    raw = (json.dumps(binding, sort_keys=True, separators=(',', ':')) + '\n').encode()
    fd, name = tempfile.mkstemp(prefix='.binding-', dir=directory)
    try:
        with os.fdopen(fd, 'wb') as stream:
            stream.write(raw)
            stream.flush()
            os.fsync(stream.fileno())
        os.replace(name, destination)
        fd = os.open(directory, os.O_RDONLY | os.O_DIRECTORY | os.O_NOFOLLOW)
        try:
            os.fsync(fd)
        finally:
            os.close(fd)
    finally:
        Path(name).unlink(missing_ok=True)
    return dict(binding, bindingRevision=hashlib.sha256(raw).hexdigest())
