import importlib.util
import json
from pathlib import Path
import tempfile
import unittest
from unittest.mock import patch
import sys
from types import SimpleNamespace

ROOT = Path(__file__).resolve().parents[3]
sys.path.insert(0, str(ROOT / 'scripts/dof'))
import space_budget as budget
spec = importlib.util.spec_from_file_location('qingkong_deploy', ROOT / 'scripts/dof/run_isolated.py')
deploy = importlib.util.module_from_spec(spec)
spec.loader.exec_module(deploy)


class DeploymentGuards(unittest.TestCase):
    def test_budget_excludes_reserved_blocks_and_includes_operation_peak(self):
        with tempfile.TemporaryDirectory() as directory:
            usage = SimpleNamespace(f_bavail=2 * 1024**3, f_bfree=9 * 1024**3, f_frsize=1)
            with patch.object(budget.os, 'statvfs', return_value=usage):
                with self.assertRaisesRegex(RuntimeError, 'shortfall=1024'):
                    budget.require_space([directory], 1024)
                usage.f_bavail += 1024
                result = budget.require_space([directory, Path(directory) / 'future/data'], 1024)
                self.assertEqual(len(result), 1)
                self.assertEqual(result[0]['requiredBytes'], 2 * 1024**3 + 1024)
                for bad in (0, -1, None, True, 1.5):
                    with self.assertRaises(ValueError): budget.require_space([directory], bad)

    def test_insufficient_space_stops_before_inspecting_or_mutating_container(self):
        args = ['deploy', '--profile', '/private/profile.json', '--image', 'sha256:' + '1' * 64, '--replace-web', '--peak-bytes', '1024']
        with patch.object(sys, 'argv', args), patch.object(deploy, 'load_profile', return_value={}), patch.object(deploy, 'configure'), patch.object(deploy.os, 'geteuid', return_value=0), \
             patch.object(deploy.subprocess, 'check_output', return_value='/var/lib/docker\n') as query, \
             patch.object(deploy, 'require_space', side_effect=RuntimeError('Insufficient space')), \
             patch.object(deploy.subprocess, 'run') as mutate, patch.object(deploy, 'inspect') as inspect:
            with self.assertRaisesRegex(RuntimeError, 'Insufficient space'): deploy.main()
            query.assert_called_once()
            mutate.assert_not_called()
            inspect.assert_not_called()

    def test_container_command_cannot_publish_database_or_start_game(self):
        cmd = deploy.command('sha256:' + '1' * 64)
        self.assertEqual(cmd[cmd.index('--name') + 1], deploy.WEB)
        self.assertNotIn('-p', cmd)
        self.assertNotIn('--privileged', cmd)
        self.assertNotIn('--network=host', cmd)
        self.assertEqual(cmd[cmd.index('--restart') + 1], 'no')
        mounts = [cmd[i + 1] for i, value in enumerate(cmd) if value == '-v']
        self.assertEqual(len(mounts), 2)
        self.assertTrue(all(value.startswith(str(deploy.ROOT) + '/') for value in mounts))

    def test_runtime_identity_and_credentials_permissions_are_required(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            (root / 'data').mkdir()
            (root / 'data/qingkong-isolated.json').write_text(json.dumps({
                'instanceId': deploy.INSTANCE, 'databaseUuid': deploy.UUID, 'environment': 'isolated'}))
            runtime = root / 'runtime.env'
            lines = ['QINGKONG_ADMIN_ENABLED=true', 'QINGKONG_ENVIRONMENT=isolated',
                     'QINGKONG_DATABASE_UUID=' + deploy.UUID, 'QINGKONG_INSTANCE_ID=' + deploy.INSTANCE,
                     'QINGKONG_ADAPTER_VERSION=qingkong-2.8-current-20261024', 'SPRING_DATASOURCE_USERNAME=' + deploy.DB_USER,
                     'SPRING_DATASOURCE_URL=jdbc:mysql://' + deploy.DB + ':3306/dnf_service?useUnicode=true&characterEncoding=UTF-8&useSSL=false']
            runtime.write_text('\n'.join(lines)); runtime.chmod(0o600)
            deploy.validate_files(root)
            runtime.chmod(0o644)
            with self.assertRaises(ValueError): deploy.validate_files(root)
            runtime.chmod(0o600); runtime.write_text(runtime.read_text().replace('QINGKONG_ENVIRONMENT=isolated','QINGKONG_ENVIRONMENT=production'))
            with self.assertRaises(ValueError): deploy.validate_files(root)

    def test_pvf_binding_is_explicit_read_only(self):
        image = 'sha256:' + '1' * 64
        self.assertNotIn('--mount', deploy.command(image))
        cmd = deploy.command(image, {'QINGKONG_GAME_PVF': '/app/paired/Script.pvf'})
        self.assertEqual(cmd[cmd.index('--mount') + 1],
                         'type=bind,src=' + str(deploy.GAME_PVF) + ',dst=/app/paired/Script.pvf,readonly')
        with self.assertRaises(ValueError):
            deploy.command(image, {'QINGKONG_GAME_PVF': '/some/other/path'})


if __name__ == '__main__':
    unittest.main()
