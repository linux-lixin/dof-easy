"""Run against bundled plugin assets in a network-disabled game container.

Only prepares files under /probe; no game executable, Frida gadget or database
connection is started. The runtime module may be bind-mounted for validation.
"""
from pathlib import Path
import hashlib
import importlib.util
import json
import tempfile


def sha(path):
    return hashlib.sha256(path.read_bytes()).hexdigest()


def main():
    runtime = Path('/opt/dnf/ubuntu/plugins.py')
    assets = Path('/opt/dnf/plugins')
    spec = importlib.util.spec_from_file_location('frida_config_runtime_probe', runtime)
    module = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(module)
    source_hashes = {name: sha(assets/'frida'/name)
                     for name in ['frida.js', 'frida.so', 'frida_config.json']}
    with tempfile.TemporaryDirectory(dir='/probe') as folder:
        root = Path(folder)
        data, game = root/'data', root/'game'
        target = data/'plugins/frida/frida_config.json'
        target.parent.mkdir(parents=True)
        original = json.loads((assets/'frida/frida_config.json').read_text())
        assert original['db_config']['password'] == ''
        original['db_config']['operator_pool'] = {'timeout': 17, 'labels': ['中文', False, None]}
        original['operator_event'] = {'enabled': False, 'items': [[8, 3]]}
        target.write_text(json.dumps(original))
        for password in ['Ab123456', 'Cd123456']:
            module.prepare(assets, data, game, password, 'dp2-frida')
            original['db_config'].update(account='game', password=password)
            for path in [target, game/'frida_config.json']:
                assert json.loads(path.read_text()) == original
                assert path.stat().st_mode & 0o777 == 0o600
            assert sha(game/'frida.so') == source_hashes['frida.so']
        custom = data/'plugins/frida/frida.js'
        custom.write_text('// operator script retained; never executed by this probe')
        log = game/'existing.log'
        log.write_text('operator evidence')
        module.prepare(assets, data, game, 'Cd123456', 'dp2-frida')
        assert custom.read_text().startswith('// operator script retained;')
        assert log.read_text() == 'operator evidence'
        refused = 0
        for index, invalid in enumerate([[], None, {'db_config': []}, {'db_config': None}]):
            bad_data = root/('bad-data-' + str(index))
            bad_game = root/('bad-game-' + str(index))
            config = bad_data/'plugins/frida/frida_config.json'
            config.parent.mkdir(parents=True)
            raw = json.dumps(invalid).encode()
            config.write_bytes(raw)
            try:
                module.prepare(assets, bad_data, bad_game, 'Ab123456', 'dp2-frida')
            except ValueError as error:
                assert 'JSON objects' in str(error)
                refused += 1
            else:
                raise AssertionError('Unknown configuration shape was accepted')
            assert config.read_bytes() == raw
            assert not (bad_data/'dp').exists() and not bad_game.exists()
        assert source_hashes == {name: sha(assets/'frida'/name) for name in source_hashes}
        assert not list(root.rglob('.frida-config-*'))
    print(json.dumps({'passed': True, 'runtimeSHA256': sha(runtime),
        'bundledFridaInputsSHA256': source_hashes, 'nestedOptionsAndFalseNullUnicodePreserved': True,
        'passwordRotationAndRuntimeMirrorVerified': True, 'privatePermissionsVerified': True,
        'operatorScriptAndLogsPreserved': True, 'unknownShapesRefusedBeforePluginWrites': refused,
        'bundledInputsUnchanged': True, 'temporaryFilesCleaned': True,
        'networkRequired': False, 'gameOrFridaBinaryExecuted': False,
        'databaseConnectionOpened': False, 'nativeConsumerAcceptanceVerified': False,
        'completeReplacement': False}))


if __name__ == '__main__':
    main()
