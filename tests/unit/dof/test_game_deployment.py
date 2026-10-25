"""Bounded game replacement guards; these tests never invoke Docker."""
import importlib.util
import json
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
