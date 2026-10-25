"""Verify and install the single pinned, derived server runtime."""
import hashlib
import json
import os
from pathlib import Path
import shutil
import tempfile

BASE = Path(__file__).resolve().parent
LOCK = BASE / 'runtime.lock.json'


def templates():
    return [(name, BASE / 'defaults' / name) for name in
            ('plugin.ini', 'anton-raid.ini', 'luke-raid.ini', 'dungeon.lst', 'equipment.json')]


def manifest(profile='native'):
    if profile != 'native':
        raise ValueError('Only the derived native server runtime is accepted')
    value = json.loads(LOCK.read_text())
    if value.get('schema') != 2 or value.get('sourceEdition') != 'replacement-only':
        raise ValueError('Invalid server runtime lock')
    return value['files']


def launcher_mode(values):
    mode = values.get('LAUNCHER_MODE', 'dof')
    if mode not in ('dof', 'native'):
        raise ValueError('LAUNCHER_MODE must be dof or native; import old data explicitly')
    return mode


def required(values):
    return launcher_mode(values) == 'native' or values.get('RAID_EXTENSION', 'off') == 'on'


def profile_for(values):
    launcher_mode(values)
    if any(name.startswith('QINGKONG_') for name in values):
        raise ValueError('Old native environment settings require explicit offline migration')
    return 'native'


def state_for(values, data=Path('/data')):
    profile_for(values)
    return Path(data) / 'dof-easy'


def regular(path, root):
    path, root = Path(path), Path(root)
    for item in (path, *path.parents):
        if item.is_symlink():
            raise ValueError('Server assets must not contain links: ' + str(path))
        if item == root:
            break
    if not path.is_file():
        raise ValueError('Missing server asset: ' + str(path))
    return path


def validate_options(mode, enabled):
    if enabled not in ('on', 'off'):
        raise ValueError('RAID_EXTENSION must be on or off')
    if enabled == 'on' and mode not in ('dp2', 'dp2-frida'):
        raise ValueError('Raid extension requires DP2 (dp2 or dp2-frida)')


def verify(root, profile='native'):
    root = Path(root)
    expected = json.loads(LOCK.read_text())
    actual = json.loads(regular(root / 'manifest.json', root).read_text())
    if actual != expected:
        raise ValueError('Server runtime manifest differs from the installed version')
    for row in manifest(profile):
        path = regular(root / row['path'], root)
        raw = path.read_bytes()
        if len(raw) != row['bytes'] or hashlib.sha256(raw).hexdigest() != row['sha256']:
            raise ValueError('Server runtime hash mismatch: ' + row['path'])
        if 'elf_class' in row and (raw[:4] != b'\x7fELF' or raw[4] != row['elf_class']):
            raise ValueError('Server runtime architecture mismatch: ' + row['path'])
    return root


def verify_config(root):
    for name, _ in templates():
        regular(Path(root) / 'config' / name, root)


def validate_game(path):
    with Path(path).open('rb') as stream:
        header = stream.read(20)
        stream.seek(0x083F4024 - 0x08048000)
        prologue = stream.read(16)
    if header[:6] != b'\x7fELF\x01\x01' or prologue.hex() != '5589e55383ec44837d10007507c74510':
        raise ValueError('Unsupported game MySQL_open ABI')


def install(source, target, profile='native'):
    source, target = verify(source, profile), Path(target).absolute()
    if target.is_symlink() or any(p.is_symlink() for p in target.parents):
        raise ValueError('Installation target cannot contain links')
    if target.exists():
        verify(target / 'assets', profile)
        verify_config(target)
        return
    target.parent.mkdir(parents=True, exist_ok=True)
    with tempfile.TemporaryDirectory(prefix='.dof-install-', dir=target.parent) as temporary:
        stage = Path(temporary) / 'state'
        assets = stage / 'assets'; assets.mkdir(parents=True, mode=0o700)
        for row in manifest(profile):
            destination = assets / row['path']; destination.parent.mkdir(parents=True, exist_ok=True)
            shutil.copyfile(source / row['path'], destination)
            destination.chmod(0o700 if row['path'].startswith('bin/') else 0o600)
        shutil.copyfile(source / 'manifest.json', assets / 'manifest.json')
        (stage / 'config').mkdir(mode=0o700)
        for name, default in templates():
            custom = source / 'config' / name
            data = (regular(custom, source) if custom.exists() else default).read_text(encoding='utf-8-sig')
            if name == 'plugin.ini':
                import re
                for key in ('Inlay_function', 'Equ_unlocked_instantly', 'Task_Completion_coupon'):
                    data, count = re.subn(r'(?m)^' + key + r'\s*=.*$', key + '=0', data)
                    if count != 1:
                        raise ValueError('Missing or duplicate feature ownership switch: ' + key)
            path = stage / 'config' / name; path.write_text(data); path.chmod(0o600)
        (stage / 'keys').mkdir(mode=0o700)
        verify(assets, profile); verify_config(stage)
        if target.exists() or target.is_symlink():
            raise ValueError('Installation target appeared during preparation')
        stage.rename(target)


def preflight():
    profile_for(os.environ)
    validate_options(os.environ.get('PLUGIN_MODE', 'dp2-frida'), os.environ.get('RAID_EXTENSION', 'off'))
    if required(os.environ) and os.environ.get('SERVER_TYPE', 'ALL') in ('ALL', 'GAME'):
        state = state_for(os.environ)
        verify(state / 'assets'); verify_config(state)
        validate_game('/data/df_game_r')


if __name__ == '__main__':
    preflight()
