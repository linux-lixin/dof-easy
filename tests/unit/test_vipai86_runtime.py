import importlib.util
import json
from pathlib import Path
import tempfile
import unittest
from unittest import mock

ROOT = Path(__file__).resolve().parents[2]
SPEC = importlib.util.spec_from_file_location('vipai86', ROOT/'services/game/runtime/ubuntu/vipai86.py')


class RuntimeTests(unittest.TestCase):
    def module(self):
        self.assertTrue(Path(SPEC.origin).exists(), 'vipai86 runtime not implemented')
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

    def test_runtime_no_log_wipe_or_qk_start(self):
        content=(ROOT/'services/game/runtime/ubuntu/bootstrap.sh').read_text(encoding='utf-8')
        for forbidden in ['rm -rf /data/log/*','rm -rf /home/neople/game/log/*', "-name '*.log' -type f -print -exec rm", 'qk.py prepare']:
            self.assertNotIn(forbidden,content)

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


if __name__ == '__main__': unittest.main()
