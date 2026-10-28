import importlib.util
import json
from pathlib import Path
import tempfile
import unittest
from unittest import mock

ROOT = Path(__file__).resolve().parents[2]
SPEC = importlib.util.spec_from_file_location('plugins', ROOT/'services/game/runtime/ubuntu/plugins.py')


class RuntimeTests(unittest.TestCase):
    def module(self):
        self.assertTrue(Path(SPEC.origin).exists(), 'plugins runtime not implemented')
        module = importlib.util.module_from_spec(SPEC)
        SPEC.loader.exec_module(module)
        return module

    def test_plugin_edits_and_logs_survive_reconfigure(self):
        module = self.module()
        with tempfile.TemporaryDirectory() as td:
            root = Path(td); source=root/'assets'; data=root/'data'; game=root/'game'
            (source/'dp2').mkdir(parents=True); (source/'frida').mkdir()
            (source/'dp2/df_game_r.lua').write_text('original')
            for name, value in {'frida.js':'original', 'frida.config':'{}', 'frida_config.json':'{"reward":42,"db_config":{}}', 'frida.so':'binary'}.items():
                (source/'frida'/name).write_text(value)
            data.mkdir(); game.mkdir(); (game/'existing.log').write_text('evidence')
            module.prepare(source,data,game,'Ab123456','dp2-frida')
            (data/'dp/df_game_r.lua').write_text('operator edit')
            (data/'plugins/frida/frida.js').write_text('operator js')
            module.prepare(source,data,game,'Cd123456','dp2-frida')
            self.assertEqual((data/'dp/df_game_r.lua').read_text(),'operator edit')
            self.assertEqual((data/'plugins/frida/frida.js').read_text(),'operator js')
            self.assertEqual((game/'existing.log').read_text(),'evidence')
            config=json.loads((data/'plugins/frida/frida_config.json').read_text())
            self.assertEqual(config['db_config']['password'],'Cd123456')
            self.assertEqual(config['reward'],42)

    def test_invalid_mode_or_password_rejected_before_writes(self):
        module=self.module()
        with tempfile.TemporaryDirectory() as td:
            root=Path(td)
            for password,mode in [('unsafe;','dp2-frida'),('Ab123456','flower')]:
                with self.assertRaises(ValueError): module.prepare(root/'src',root/'data',root/'game',password,mode)
            self.assertFalse((root/'data').exists())

    def test_runtime_preserves_logs_and_prepares_optional_extension(self):
        content=(ROOT/'services/game/runtime/ubuntu/bootstrap.sh').read_text(encoding='utf-8')
        for forbidden in ['rm -rf /data/log/*','rm -rf /home/neople/game/log/*', "-name '*.log' -type f -print -exec rm"]:
            self.assertNotIn(forbidden,content)
        self.assertIn('gateway/service.py prepare', content)
        qk=(ROOT/'services/game/runtime/ubuntu/gateway/service.py').read_text()
        self.assertIn("os.environ.get('RAID_EXTENSION', 'off') != 'on'", qk)

    def test_operator_pvf_seed_only_initializes_missing_file(self):
        spec=importlib.util.spec_from_file_location('pvf', ROOT/'services/game/runtime/ubuntu/pvf.py')
        module=importlib.util.module_from_spec(spec);spec.loader.exec_module(module)
        with tempfile.TemporaryDirectory() as td:
            root=Path(td);seed=root/'seed';seed.write_bytes(b'operator resource')
            target=root/'data/Script.pvf'
            module.seed_missing('CORE',target,seed)
            self.assertFalse(target.exists())
            module.seed_missing('ALL',target,seed)
            self.assertEqual(target.read_bytes(),seed.read_bytes())
            target.write_bytes(b'operator edit')
            module.seed_missing('ALL',target,seed)
            self.assertEqual(target.read_bytes(),b'operator edit')
            target.write_bytes(b'')
            module.seed_missing('ALL',target,seed)
            with self.assertRaises(ValueError):module.validate('ALL',target)

    def test_interrupted_pvf_copy_does_not_publish_partial_file(self):
        spec=importlib.util.spec_from_file_location('pvf', ROOT/'services/game/runtime/ubuntu/pvf.py')
        module=importlib.util.module_from_spec(spec);spec.loader.exec_module(module)
        with tempfile.TemporaryDirectory() as td:
            root=Path(td);seed=root/'seed';seed.write_bytes(b'operator resource')
            target=root/'data/Script.pvf'
            def fail(source,destination):
                destination.write(b'partial')
                raise OSError('simulated interrupted copy')
            with mock.patch.object(module.shutil,'copyfileobj',side_effect=fail):
                with self.assertRaises(OSError):module.seed_missing('ALL',target,seed)
            self.assertFalse(target.exists())
            self.assertFalse(list(target.parent.glob('.pvf-seed-*')))
            stale=target.parent/'.pvf-seed-interrupted';stale.write_bytes(b'partial left by SIGKILL')
            module.seed_missing('ALL',target,seed)
            self.assertEqual(target.read_bytes(),seed.read_bytes())
            self.assertFalse(stale.exists())


class ShippedFridaUpgrade(unittest.TestCase):
    def test_known_script_upgrade_keeps_private_recovery_copy(self):
        module=RuntimeTests().module(); real_hash=module.hashlib.sha256
        old=b'synthetic previous shipped script'; old_hash='bab9a8a6abc5ccf8c9ab26a44cfc6e2db7cd0dd15afc6b91fd9cb8a9a73d6843'
        def digest(value): return mock.Mock(hexdigest=lambda:old_hash) if value==old else real_hash(value)
        with tempfile.TemporaryDirectory() as folder:
            root=Path(folder);source=root/'source.js';target=root/'frida.js'
            source.write_bytes(b'synthetic new script');target.write_bytes(old)
            with mock.patch.object(module.hashlib,'sha256',side_effect=digest):module.check_frida_ownership(source,target,'frida')
            backup=root/('frida.js.previous-'+old_hash[:12]);self.assertEqual(backup.read_bytes(),old)
            self.assertEqual(backup.stat().st_mode & 0o777,0o600);self.assertEqual(target.read_bytes(),source.read_bytes())
            module.check_frida_ownership(source,target,'frida');self.assertEqual(backup.read_bytes(),old)

    def test_conflicting_recovery_or_symlink_stops_before_script_replace(self):
        module=RuntimeTests().module();real_hash=module.hashlib.sha256
        old=b'synthetic previous shipped script';old_hash='bab9a8a6abc5ccf8c9ab26a44cfc6e2db7cd0dd15afc6b91fd9cb8a9a73d6843'
        def digest(value):return mock.Mock(hexdigest=lambda:old_hash) if value==old else real_hash(value)
        for problem in ('wrong-backup','backup-link','staging-link'):
            with self.subTest(problem=problem),tempfile.TemporaryDirectory() as folder:
                root=Path(folder);source=root/'source.js';target=root/'frida.js';source.write_bytes(b'new');target.write_bytes(old)
                backup=root/('frida.js.previous-'+old_hash[:12]);other=root/'other';other.write_bytes(b'keep')
                if problem=='wrong-backup':backup.write_bytes(b'foreign backup')
                elif problem=='backup-link':backup.symlink_to(other)
                else:(root/'frida.js.new').symlink_to(other)
                with mock.patch.object(module.hashlib,'sha256',side_effect=digest),self.assertRaises(ValueError):module.check_frida_ownership(source,target,'frida')
                self.assertEqual(target.read_bytes(),old);self.assertEqual(other.read_bytes(),b'keep')


class FridaConfigurationPreservation(unittest.TestCase):
    def fixture(self, root):
        source, data, game = root/'source', root/'data', root/'game'
        (source/'frida').mkdir(parents=True)
        (source/'dp2').mkdir()
        (source/'dp2/operator.lua').write_text('default dp2 script')
        for name in ['frida.js', 'frida.so', 'frida.config']:
            (source/'frida'/name).write_text('default ' + name)
        (source/'frida/frida_config.json').write_text('{"db_config":{}}')
        (data/'plugins/frida').mkdir(parents=True)
        game.mkdir()
        (game/'frida_config.json').write_bytes(b'previous runtime configuration')
        return source, data, game

    def test_first_start_preserves_template_options_without_editing_the_template(self):
        module = RuntimeTests().module()
        with tempfile.TemporaryDirectory() as folder:
            source, data, game = self.fixture(Path(folder))
            config = {'db_config': {'pool': {'size': 4}, 'retry': False}, 'reward': [8, 3]}
            template = source/'frida/frida_config.json'
            original = json.dumps(config).encode()
            template.write_bytes(original)
            module.prepare(source, data, game, 'Ab123456', 'frida')
            config['db_config'].update(account='game', password='Ab123456')
            self.assertEqual(template.read_bytes(), original)
            for path in [data/'plugins/frida/frida_config.json', game/'frida_config.json']:
                self.assertEqual(json.loads(path.read_text()), config)

    def test_nested_operator_options_survive_password_rotation_and_runtime_copy(self):
        module = RuntimeTests().module()
        with tempfile.TemporaryDirectory() as folder:
            source, data, game = self.fixture(Path(folder))
            config = {'db_config': {'account': 'old', 'password': 'old-value',
                      'pool': {'timeout': 17, 'labels': ['中文', None, False]},
                      'retry': 0, 'charset': 'utf8'}, 'custom_rewards': {'items': [[8, 3]]}}
            target = data/'plugins/frida/frida_config.json'
            target.write_text(json.dumps(config))
            for password in ['Ab123456', 'Cd123456']:
                module.prepare(source, data, game, password, 'dp2-frida')
                config['db_config'].update(account='game', password=password)
                self.assertEqual(json.loads(target.read_text()), config)
                self.assertEqual(json.loads((game/'frida_config.json').read_text()), config)
                for path in [target, game/'frida_config.json']:
                    self.assertEqual(path.stat().st_mode & 0o777, 0o600)

    def test_unknown_config_shapes_refuse_before_plugin_or_runtime_changes(self):
        module = RuntimeTests().module()
        for invalid in [[], None, 3, {'db_config': []}, {'db_config': None}, {'db_config': 'legacy'}]:
            with self.subTest(invalid=invalid), tempfile.TemporaryDirectory() as folder:
                source, data, game = self.fixture(Path(folder))
                target = data/'plugins/frida/frida_config.json'
                original = json.dumps(invalid).encode()
                target.write_bytes(original)
                with mock.patch.object(module, 'check_frida_ownership') as upgrade:
                    with self.assertRaisesRegex(ValueError, 'Frida.*object'):
                        module.prepare(source, data, game, 'Ab123456', 'dp2-frida')
                    upgrade.assert_not_called()
                self.assertEqual(target.read_bytes(), original)
                self.assertEqual((game/'frida_config.json').read_bytes(), b'previous runtime configuration')
                self.assertFalse((data/'dp').exists())
                self.assertFalse((game/'frida.so').exists())

    def test_linked_persistent_or_runtime_config_refuses_before_any_plugin_copy(self):
        module = RuntimeTests().module()
        for linked_runtime in [False, True]:
            with self.subTest(linked_runtime=linked_runtime), tempfile.TemporaryDirectory() as folder:
                source, data, game = self.fixture(Path(folder))
                other = Path(folder)/'other-config.json'
                original = b'{"db_config":{"pool":{"size":4}}}'
                other.write_bytes(original)
                target = game/'frida_config.json' if linked_runtime else data/'plugins/frida/frida_config.json'
                target.unlink(missing_ok=True)
                target.symlink_to(other)
                with self.assertRaisesRegex(ValueError, 'symlink'):
                    module.prepare(source, data, game, 'Ab123456', 'dp2-frida')
                self.assertEqual(other.read_bytes(), original)
                self.assertFalse((data/'dp').exists())
                self.assertFalse((game/'frida.so').exists())

    def test_interrupted_replace_keeps_complete_files_and_retry_retains_unknown_values(self):
        module = RuntimeTests().module()
        for fail_runtime in [False, True]:
            with self.subTest(fail_runtime=fail_runtime), tempfile.TemporaryDirectory() as folder:
                source, data, game = self.fixture(Path(folder))
                target = data/'plugins/frida/frida_config.json'
                original = b'{"db_config":{"account":"game","password":"old","pool":{"size":9}},"reward":42}'
                target.write_bytes(original)
                real_replace = module.os.replace
                failed_target = game/'frida_config.json' if fail_runtime else target
                def interrupt(source_path, destination_path):
                    if Path(destination_path) == failed_target:
                        raise OSError('simulated interrupted configuration replacement')
                    return real_replace(source_path, destination_path)
                with mock.patch.object(module.os, 'replace', side_effect=interrupt):
                    with self.assertRaisesRegex(OSError, 'simulated interrupted'):
                        module.prepare(source, data, game, 'Ab123456', 'frida')
                self.assertEqual((game/'frida_config.json').read_bytes(), b'previous runtime configuration')
                if not fail_runtime:
                    self.assertEqual(target.read_bytes(), original)
                else:
                    self.assertEqual(json.loads(target.read_text())['db_config']['pool'], {'size': 9})
                self.assertFalse(list(data.rglob('.frida-config-*')))
                self.assertFalse(list(game.glob('.frida-config-*')))
                module.prepare(source, data, game, 'Ab123456', 'frida')
                expected = json.loads(original)
                expected['db_config']['password'] = 'Ab123456'
                self.assertEqual(json.loads(target.read_text()), expected)
                self.assertEqual(json.loads((game/'frida_config.json').read_text()), expected)


if __name__ == '__main__': unittest.main()
