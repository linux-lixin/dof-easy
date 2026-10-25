"""Bounded game replacement guards; these tests never invoke Docker."""
import importlib.util
import json
import hashlib
from pathlib import Path
import sys
import tempfile
import unittest
from unittest.mock import patch

ROOT = Path(__file__).resolve().parents[3]
sys.path.insert(0, str(ROOT / 'scripts/dof'))
spec = importlib.util.spec_from_file_location('game_deployment', ROOT / 'scripts/dof/deploy_game_test.py')
deploy = importlib.util.module_from_spec(spec); spec.loader.exec_module(deploy)


class GameDeployment(unittest.TestCase):
    def profile(self, root):
        return dict(gameContainer='test-game', gameId='1'*64, gameImage='sha256:'+'2'*64,
                    databaseContainer='test-db', databaseId='3'*64, databaseUuid='instance-uuid',
                    network='test-internal', dataDirectory=str(root/'original/data'),
                    pvf=str(root/'assets/Script.pvf'), pvfSha256='4'*64, gameSha256='5'*64,
                    publicKeySha256='6'*64, outputDirectory=str(root/'release'), mainCommit='7'*40)

    def test_profile_rejects_shared_target_alias_and_nested_output(self):
        with tempfile.TemporaryDirectory() as directory:
            root=Path(directory); path=root/'profile.json'; value=self.profile(root)
            path.write_text(json.dumps(value)); path.chmod(0o600)
            self.assertEqual(deploy.load(path),value)
            for change in (dict(gameContainer='test-db'), dict(outputDirectory=str(root/'original/data/new')),
                           dict(mainCommit='main'), dict(gameId='latest')):
                path.write_text(json.dumps(dict(value,**change)))
                with self.assertRaises(ValueError): deploy.load(path)
            path.write_text(json.dumps(value)); path.chmod(0o644)
            with self.assertRaises(ValueError): deploy.load(path)

    def test_changed_container_is_rejected_before_database_access(self):
        with tempfile.TemporaryDirectory() as directory:
            p=self.profile(Path(directory))
            with patch.object(deploy,'inspect',side_effect=[dict(Id='changed',Image=p['gameImage']),dict(Id=p['databaseId'])]), patch.object(deploy,'docker') as docker:
                with self.assertRaisesRegex(ValueError,'identity changed'):
                    deploy.validate_target(p,'sha256:'+'8'*64)
                docker.assert_not_called()

    def test_existing_cpu_memory_and_descriptor_limits_are_retained(self):
        args=deploy.runtime_limits(dict(NanoCpus=1000000000, Memory=2147483648,
            MemorySwap=6442450944, Ulimits=[dict(Name='nofile',Soft=65535,Hard=65535)]))
        self.assertEqual(args[args.index('--cpus')+1], '1.000000000')
        self.assertEqual(args[args.index('--memory')+1], '2147483648')
        self.assertEqual(args[args.index('--memory-swap')+1], '6442450944')
        self.assertEqual(args[args.index('--ulimit')+1], 'nofile=65535:65535')
        with self.assertRaisesRegex(ValueError,'Conflicting CPU'):
            deploy.runtime_limits(dict(NanoCpus=1000000000,CpuQuota=100000))

    def test_native_upgrade_replaces_only_staged_assets_and_keeps_private_state(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            source, destination, runtime = root/'source', root/'next', root/'runtime'
            state = source/'dof-easy'
            (state/'assets/lib').mkdir(parents=True)
            (state/'config').mkdir(); (state/'keys').mkdir()
            (state/'config/plugin.ini').write_bytes(b'operator-config')
            (state/'keys/privatekey.pem').write_bytes(b'private-test-key')
            (state/'assets/lib/library.so').write_bytes(b'old')
            def manifest(content):
                return dict(artifact='dof-server-runtime', sourceEdition='replacement-only',
                    files=[dict(path='lib/library.so', bytes=len(content), sha256=hashlib.sha256(content).hexdigest())])
            previous, current = manifest(b'old'), manifest(b'new')
            (state/'assets/manifest.json').write_text(json.dumps(previous))
            lock = root/'previous.lock.json'; lock.write_text(json.dumps(previous))
            (runtime/'lib').mkdir(parents=True)
            (runtime/'lib/library.so').write_bytes(b'new')
            (runtime/'manifest.json').write_text(json.dumps(current))
            managed = root/'managed'; managed.mkdir()
            before = {p.relative_to(source):p.read_bytes() for p in source.rglob('*') if p.is_file()}
            with patch.object(deploy.assets, 'manifest', return_value=current['files']), \
                    patch.object(deploy.assets, 'verify'), patch.object(deploy.assets, 'verify_config'):
                deploy.stage_data(source, destination, runtime, managed, managed, lock)
            self.assertEqual((destination/'dof-easy/assets/lib/library.so').read_bytes(), b'new')
            for name in ('config/plugin.ini', 'keys/privatekey.pem'):
                self.assertEqual((destination/'dof-easy'/name).read_bytes(), (state/name).read_bytes())
            self.assertEqual(before, {p.relative_to(source):p.read_bytes() for p in source.rglob('*') if p.is_file()})
            (state/'assets/lib/library.so').write_bytes(b'tampered')
            with self.assertRaisesRegex(ValueError, 'modified'):
                deploy.verify_previous_assets(state, lock)

    def test_native_upgrade_requires_previous_image_lock(self):
        with tempfile.TemporaryDirectory() as directory:
            root=Path(directory); (root/'source/dof-easy').mkdir(parents=True)
            with self.assertRaisesRegex(ValueError, 'previous image lock'):
                deploy.stage_data(root/'source', root/'output', root/'runtime', root/'managed', root/'managed')
            self.assertFalse((root/'output').exists())

    def test_explicit_import_preserves_password_and_disables_legacy_init(self):
        original=dict(LAUNCHER_MODE='qingkong', QINGKONG_VARIANT='replacement',
                      DNF_DB_GAME_PASSWORD='Demo1234', PLUGIN_MODE='dp2-frida', OPEN_CHANNEL='15')
        converted=deploy.imported_environment(original)
        self.assertEqual(converted['DOF_DB_GAME_PASSWORD'],original['DNF_DB_GAME_PASSWORD'])
        self.assertEqual(converted['DOF_DATABASE_MODE'],'existing')
        self.assertEqual(converted['LAUNCHER_MODE'],'native')
        self.assertFalse(any(k.startswith('QINGKONG_') for k in converted))
        self.assertEqual(original['LAUNCHER_MODE'],'qingkong')


if __name__ == '__main__': unittest.main()
