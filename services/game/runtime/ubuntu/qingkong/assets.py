"""Install the pinned original Qingkong payload without transforming its bytes."""
import hashlib
import json
import os
from pathlib import Path
import shutil
import sys
import tempfile

BASE = Path(__file__).resolve().parent
LOCK = BASE / 'inputs.lock.json'
CONFIGS = ('config.ini', 'qk_plugin_config.ini', 'qk_plugin_antonRaid.ini',
           'qk_plugin_lukeRaid.ini', 'dungeon.lst')


def manifest(profile='qingkong'):
    if profile != 'qingkong':
        raise ValueError('Expected original Qingkong profile')
    value = json.loads(LOCK.read_text())
    if value.get('schema') != 1 or value.get('profile') != 'qingkong-2.8-replacement':
        raise ValueError('Invalid original Qingkong lock')
    return value['files']


def regular(path, root):
    path, root = Path(path), Path(root)
    for item in (path, *path.parents):
        if item.is_symlink():
            raise ValueError('Qingkong input must not contain links: ' + str(path))
        if item == root:
            break
    if not path.is_file():
        raise ValueError('Missing original Qingkong input: ' + str(path))
    return path


def verify(source, profile='qingkong'):
    source = Path(source)
    for row in manifest(profile):
        raw = regular(source / row['path'], source).read_bytes()
        if len(raw) != row['bytes'] or hashlib.sha256(raw).hexdigest() != row['sha256']:
            raise ValueError('Original Qingkong hash mismatch: ' + row['path'])
        if raw[:4] != b'\x7fELF' or raw[4] != row['elf_class']:
            raise ValueError('Original Qingkong architecture mismatch: ' + row['path'])
    return source


def verify_config(state):
    for name in CONFIGS:
        regular(Path(state) / 'config' / name, state)


def state_for(values, data=Path('/data')):
    return Path(data) / 'qingkong'


def validate_options(mode, raid):
    if mode not in ('none', 'dp2', 'frida', 'dp2-frida'):
        raise ValueError('Invalid Qingkong PLUGIN_MODE')
    if raid != 'off':
        raise ValueError('Original Qingkong requires RAID_EXTENSION=off')


def install(source, state, profile='qingkong'):
    source, state = verify(source, profile), Path(state).absolute()
    if any(p.is_symlink() for p in (state, *state.parents)):
        raise ValueError('Qingkong state cannot contain links')
    if state.exists():
        verify(state / 'assets', profile)
        verify_config(state)
        return
    # Original configuration is operator input, never a derived runtime default.
    verify_config(source)
    state.parent.mkdir(parents=True, exist_ok=True)
    with tempfile.TemporaryDirectory(prefix='.qingkong-install-', dir=state.parent) as temporary:
        stage = Path(temporary) / 'state'
        for row in manifest(profile):
            target = stage / 'assets' / row['path']
            target.parent.mkdir(parents=True, exist_ok=True, mode=0o700)
            shutil.copyfile(source / row['path'], target)
            target.chmod(0o700 if row['path'] == 'QK' else 0o600)
        (stage / 'config').mkdir(mode=0o700)
        for name in CONFIGS:
            target = stage / 'config' / name
            shutil.copyfile(source / 'config' / name, target)
            target.chmod(0o600)
        (stage / 'keys').mkdir(mode=0o700)
        verify(stage / 'assets', profile)
        verify_config(stage)
        if state.exists() or state.is_symlink():
            raise ValueError('Qingkong state appeared during installation')
        stage.rename(state)


def preflight():
    validate_options(os.environ.get('PLUGIN_MODE', 'dp2-frida'), os.environ.get('RAID_EXTENSION', 'off'))
    if os.environ.get('SERVER_TYPE', 'ALL') in ('ALL', 'GAME'):
        state = state_for(os.environ)
        verify(state / 'assets')
        verify_config(state)
        sys.path.insert(0, '/opt/dof-easy/runtime')
        from gateway.assets import validate_game
        validate_game('/data/df_game_r')


if __name__ == '__main__':
    preflight()
