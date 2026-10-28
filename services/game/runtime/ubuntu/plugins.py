"""Install ordinary plugins without overwriting operator scripts or erasing logs."""
import hashlib
import json
import os
from pathlib import Path
import re
import shutil
import tempfile


def copy_missing(source, target):
    for original in source.rglob('*'):
        relative=original.relative_to(source)
        destination=target/relative
        if original.is_symlink() or destination.is_symlink():
            raise ValueError('plugin files must not be symlinks: '+str(relative))
        if original.is_dir():
            destination.mkdir(parents=True,exist_ok=True)
        elif original.is_file() and not destination.exists():
            destination.parent.mkdir(parents=True,exist_ok=True)
            shutil.copyfile(original,destination)
            destination.chmod(0o700 if original.suffix=='.sh' else 0o600)


def check_frida_ownership(source, target, mode):
    """Upgrade only the exact previous shipped script; report custom overlap."""
    previous = {'3bf48075fd63db2a25db5076819dc4f902f8b92c953cfeb60ec45ef196998e8d',
                '6ca2de364e9b94405a1f433ad3fcda627b5fb68f6ead7774d2f6af93f37e2afa',
                'bab9a8a6abc5ccf8c9ab26a44cfc6e2db7cd0dd15afc6b91fd9cb8a9a73d6843'}
    original = target.read_bytes()
    if hashlib.sha256(original).hexdigest() in previous:
        backup = target.with_name(target.name + '.previous-' + hashlib.sha256(original).hexdigest()[:12])
        if backup.is_symlink() or (backup.exists() and backup.read_bytes() != original):
            raise ValueError('Shipped Frida recovery copy conflicts; original script retained')
        if not backup.exists():
            fd = os.open(backup, os.O_WRONLY | os.O_CREAT | os.O_EXCL | os.O_NOFOLLOW, 0o600)
            with os.fdopen(fd, 'wb') as stream:
                stream.write(original); stream.flush(); os.fsync(stream.fileno())
        temporary = target.with_name(target.name + '.new')
        if temporary.is_symlink():
            raise ValueError('Frida upgrade staging file must not be a symlink')
        shutil.copyfile(source, temporary)
        temporary.chmod(0o600)
        temporary.replace(target)
        original = target.read_bytes()
    if (mode == 'dp2-frida' and b'DOF_DP2_ITEM_OWNERSHIP_V1' not in original
            and any(item in original for item in (b'2021458801', b'2021458802'))):
        print('[插件冲突提示] 自定义 Frida 脚本含 DP2 同名道具处理；未自动修改：' + str(target), flush=True)


def write_private_config(path, content):
    """Publish one complete config; a failed write leaves its old file intact."""
    if path.is_symlink():
        raise ValueError('Frida configuration must not be a symlink')
    previous = path.stat() if path.exists() else None
    descriptor, name = tempfile.mkstemp(prefix='.frida-config-', dir=path.parent)
    temporary = Path(name)
    try:
        with os.fdopen(descriptor, 'w', encoding='utf-8') as stream:
            if previous is not None and hasattr(os, 'fchown'):
                os.fchown(stream.fileno(), previous.st_uid, previous.st_gid)
            stream.write(content)
            stream.flush()
            os.fsync(stream.fileno())
        temporary.replace(path)
    finally:
        temporary.unlink(missing_ok=True)


def prepare(source, data, game, password, mode):
    if mode not in ('none','dp2','frida','dp2-frida'):
        raise ValueError('PLUGIN_MODE must be none, dp2, frida or dp2-frida')
    if not re.fullmatch('[A-Za-z0-9]{8}',password):
        raise ValueError('game password must be exactly eight alphanumeric characters')
    source,data,game=Path(source),Path(data),Path(game)
    if mode in ('frida','dp2-frida'):
        destination=data/'plugins/frida'
        config_path=destination/'frida_config.json'
        selected=config_path if config_path.exists() else source/'frida/frida_config.json'
        if config_path.is_symlink() or selected.is_symlink() or (game/'frida_config.json').is_symlink():
            raise ValueError('Frida configuration must not be a symlink')
        config=json.loads(selected.read_text(encoding='utf-8-sig'))
        if not isinstance(config,dict) or not isinstance(config.get('db_config',{}),dict):
            raise ValueError('Frida configuration and db_config must be JSON objects')
        # These are the only managed fields. Keep nested operator options and
        # all unrelated activity configuration, including false/null values.
        config['db_config']=dict(config.get('db_config',{}),account='game',password=password)
        content=json.dumps(config,ensure_ascii=False,indent=2,allow_nan=False)
    if mode in ('dp2','dp2-frida'):
        copy_missing(source/'dp2',data/'dp')
    if mode in ('frida','dp2-frida'):
        copy_missing(source/'frida',destination)
        check_frida_ownership(source/'frida/frida.js', destination/'frida.js', mode)
        write_private_config(config_path,content)
        game.mkdir(parents=True,exist_ok=True)
        shutil.copyfile(source/'frida/frida.so',game/'frida.so')
        write_private_config(game/'frida_config.json',content)
        # Gadget reloads the operator-owned JS; config remains outside the image layer.
        (game/'frida.config').write_text(json.dumps({'interaction':{
            'type':'script','path':str(destination/'frida.js'),'on_change':'reload'}}),encoding='utf-8')


if __name__=='__main__':
    prepare(os.environ.get('DNF_PLUGIN_ASSETS','/opt/dnf/plugins'),'/data','/home/neople/game',
            os.environ['DNF_DB_GAME_PASSWORD'],os.environ.get('PLUGIN_MODE','dp2-frida'))
