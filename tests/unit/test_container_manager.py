"""Exercise instance isolation, resource integrity and level selection without Docker."""
import argparse
import hashlib
import importlib.util
import io
import json
from pathlib import Path
import tarfile
import tempfile
import unittest
from unittest.mock import patch

ROOT = Path(__file__).resolve().parents[2]
spec = importlib.util.spec_from_file_location('manager', ROOT / 'scripts/container/manage.py')
manager = importlib.util.module_from_spec(spec)
spec.loader.exec_module(manager)
game_profile = manager.module('profile_check', 'services/game/runtime/ubuntu/game_profile.py')
guard = manager.module('container_guard', 'deploy/vipai86/netguard/entrypoint.py')


class ContainerManager(unittest.TestCase):
    def test_effective_docker_context_excludes_instance_resources(self):
        # Dockerfile-specific ignores take precedence over the context root.
        context = ROOT / 'services/game'
        rules = (context / 'docker/ubuntu/Dockerfile.dockerignore').read_text().splitlines()
        for resource in ('vipai86-pvf', 'vipai86-assets', 'assets/home/template/init/Script.tgz',
                         'assets/home/template/init/Script.pvf', 'assets/home/template/init/df_game_r'):
            self.assertIn(resource, rules)

    def setUp(self):
        self.temp = tempfile.TemporaryDirectory(dir=ROOT)
        self.addCleanup(self.temp.cleanup)
        self.root = Path(self.temp.name)
        self.pvf = self.root / 'custom.pvf'
        self.pvf.write_bytes(b'operator PVF\n' * 1024)
        self.binary = b'\x7fELF\x01\x01' + b'\x00' * 12 + b'\x03\x00' + b'level70'
        self.row = {'level': 70, 'game': {'bytes': len(self.binary), 'sha256': hashlib.sha256(self.binary).hexdigest()}}
        self.archive = self.root / 'game.tar.gz'
        self.make_archive()
        self.args = argparse.Namespace(instance='main', ip='203.0.113.70', bind='127.0.0.1', admin_port=18080,
                                       plugins='none', channels='11-12,52', pvf=self.pvf)

    def make_archive(self, name='df_game_r', kind=tarfile.REGTYPE):
        with tarfile.open(self.archive, 'w:gz') as archive:
            item = tarfile.TarInfo(name)
            item.type = kind
            item.size = len(self.binary)
            archive.addfile(item, io.BytesIO(self.binary))

    def test_custom_pvf_and_selected_game_are_runtime_data(self):
        instance = self.root / 'main'
        values = manager.create_instance(instance, self.args, self.row, self.archive)
        self.assertEqual((instance / 'runtime/game/data/df_game_r').read_bytes(), self.binary)
        self.assertEqual((instance / 'runtime/game/data/Script.pvf').read_bytes(), self.pvf.read_bytes())
        self.assertEqual(values['DNF_MAX_LEVEL'], '70')
        self.assertEqual(values['PLUGIN_MODE'], 'none')
        self.assertEqual(values['OPEN_CHANNEL'], '11,12,52')
        self.assertEqual(manager.read_env(instance), values)
        with patch.object(manager, 'catalog', return_value={70: self.row}):
            self.assertEqual(manager.check_instance(instance, 70, self.pvf), values)
        before = (instance / '.env').read_bytes()
        with self.assertRaises(ValueError):
            manager.check_instance(instance, 86)
        self.assertEqual((instance / '.env').read_bytes(), before)
        with self.assertRaises(OSError):
            manager.create_instance(instance, self.args, self.row, self.archive)
        self.assertEqual((instance / '.env').read_bytes(), before)

    def test_tampered_game_rejected_and_separate_instances_have_separate_secrets(self):
        first = manager.create_instance(self.root / 'main', self.args, self.row, self.archive)
        self.args.instance = 'second'
        second = manager.create_instance(self.root / 'second', self.args, self.row, self.archive)
        for key in ('ADMIN_PASSWORD', 'GAME_DB_PASSWORD', 'DB_ROOT_PASSWORD'):
            self.assertNotEqual(first[key], second[key])
        (self.root / 'main/runtime/game/data/df_game_r').write_bytes(b'tampered')
        with patch.object(manager, 'catalog', return_value={70: self.row}), self.assertRaises(ValueError):
            manager.check_instance(self.root / 'main')

    def test_bad_archives_leave_no_instance_and_never_escape(self):
        for name, kind in (('../outside', tarfile.REGTYPE), ('df_game_r', tarfile.SYMTYPE)):
            self.make_archive(name, kind)
            with self.assertRaises(ValueError):
                manager.create_instance(self.root / 'main', self.args, self.row, self.archive)
            self.assertFalse((self.root / 'main').exists())
            self.assertFalse((self.root / 'outside').exists())

    def test_invalid_options_fail_before_any_instance_is_created(self):
        self.args.channels = '40,52'
        with self.assertRaises(ValueError):
            manager.create_instance(self.root / 'main', self.args, self.row, self.archive)
        self.assertFalse((self.root / 'main').exists())
        for name in ('../escape', 'Main', '', 'a/b'):
            with self.assertRaises(ValueError):
                manager.instance_path(name)

    def test_selected_channels_control_published_ports_and_guard(self):
        values = manager.initial_values(self.args, self.row, self.root / 'main')
        ports = manager.ports_override(values)['services']['netguard']['ports']
        self.assertIn('127.0.0.1:30011:30011/tcp', ports)
        self.assertNotIn('127.0.0.1:30015:30015/tcp', ports)
        with patch.dict('os.environ', {'OPEN_CHANNEL': '11,12,52'}):
            rules = guard.render_rules(['172.30.0.2'])
            self.assertIn('30011,30012,30052', rules)
            self.assertNotIn('30015', rules)
        with patch.dict('os.environ', {'OPEN_CHANNEL': ','.join(map(str, range(11, 40)))}):
            rules = guard.render_rules(['172.30.0.2'])
            for line in rules.splitlines():
                if '--dports' in line:
                    self.assertLessEqual(len(line.split('--dports ')[1].split()[0].split(',')), 15)

    def test_executable_profile_checks_abi_and_selected_hash(self):
        path = self.root / 'df_game_r'
        path.write_bytes(self.binary)
        game_profile.validate(path, self.row['game']['sha256'])
        with self.assertRaises(ValueError):
            game_profile.validate(path, '0' * 64)
        path.write_bytes(b'not ELF' * 10)
        with self.assertRaises(ValueError):
            game_profile.validate(path)

    def test_default_install_uses_70_and_drives_all_steps(self):
        with patch.object(manager, 'STATE', self.root / 'state'), \
             patch.object(manager, 'docker_check'), patch.object(manager, 'catalog', return_value={70: self.row}), \
             patch.object(manager, 'fetch', return_value=self.archive), \
             patch.object(manager, 'prepare') as prepare, patch.object(manager, 'build') as build, \
             patch.object(manager, 'start') as start:
            manager.main(['install', '--ip', '127.0.0.1', '--pvf', str(self.pvf)])
            record = json.loads((self.root / 'state/instances/main/instance.json').read_text())
            self.assertEqual(record['level'], 70)
            prepare.assert_called_once()
            build.assert_called_once()
            start.assert_called_once()

    def test_offline_install_never_downloads_or_builds(self):
        cache = self.root / 'cache'
        cache.mkdir()
        row = dict(self.row, id='level70.tar.gz', size=self.archive.stat().st_size,
                   sha256=hashlib.sha256(self.archive.read_bytes()).hexdigest())
        (cache / row['id']).write_bytes(self.archive.read_bytes())
        with patch.object(manager, 'STATE', self.root / 'state'), \
             patch.object(manager, 'catalog', return_value={70: row}), patch.object(manager, 'docker_check'), \
             patch.object(manager, 'prepare') as prepare, patch.object(manager, 'build') as build, \
             patch.object(manager, 'start'), patch.object(manager.urllib.request, 'urlopen') as network:
            manager.main(['install', '--ip', '127.0.0.1', '--pvf', str(self.pvf), '--resources', str(cache), '--offline'])
            prepare.assert_not_called()
            build.assert_not_called()
            network.assert_not_called()


if __name__ == '__main__':
    unittest.main()
