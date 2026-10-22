"""Install ordinary plugins without overwriting operator scripts or erasing logs."""
import json
import os
from pathlib import Path
import re
import shutil


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


def prepare(source, data, game, password, mode):
    if mode not in ('none','dp2','frida','dp2-frida'):
        raise ValueError('PLUGIN_MODE must be none, dp2, frida or dp2-frida')
    if not re.fullmatch('[A-Za-z0-9]{8}',password):
        raise ValueError('game password must be exactly eight alphanumeric characters')
    source,data,game=Path(source),Path(data),Path(game)
    if mode in ('dp2','dp2-frida'):
        copy_missing(source/'dp2',data/'dp')
    if mode in ('frida','dp2-frida'):
        destination=data/'plugins/frida'
        copy_missing(source/'frida',destination)
        config_path=destination/'frida_config.json'
        config=json.loads(config_path.read_text(encoding='utf-8'))
        config['db_config']={'account':'game','password':password}
        config_path.write_text(json.dumps(config,ensure_ascii=False,indent=2),encoding='utf-8')
        config_path.chmod(0o600)
        game.mkdir(parents=True,exist_ok=True)
        shutil.copyfile(source/'frida/frida.so',game/'frida.so')
        shutil.copyfile(config_path,game/'frida_config.json')
        (game/'frida_config.json').chmod(0o600)
        # Gadget reloads the operator-owned JS; config remains outside the image layer.
        (game/'frida.config').write_text(json.dumps({'interaction':{
            'type':'script','path':str(destination/'frida.js'),'on_change':'reload'}}),encoding='utf-8')


if __name__=='__main__':
    prepare('/opt/dnf/vipai86','/data','/home/neople/game',
            os.environ['DNF_DB_GAME_PASSWORD'],os.environ.get('PLUGIN_MODE','dp2-frida'))
