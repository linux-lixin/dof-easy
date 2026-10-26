"""Exercise instance isolation, resource integrity and level selection without Docker."""
import argparse
import hashlib
import importlib.util
import io
import json
import os
from pathlib import Path
import subprocess
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
    def test_non_linux_host_rejected_before_state_or_docker_access(self):
        state = self.root / 'state'
        for platform in ('win32', 'darwin'):
            with self.subTest(platform=platform), patch.object(manager.sys, 'platform', platform), \
                 patch.object(manager, 'STATE', state), patch.object(manager, 'run') as run:
                with self.assertRaisesRegex(ValueError, '仅支持 Linux'):
                    manager.main(['images', '--registry', 'ghcr.io/example', '--tag', 'v1'])
                self.assertFalse(state.exists())
                run.assert_not_called()

    def test_effective_docker_context_excludes_instance_resources(self):
        # Dockerfile-specific ignores take precedence over the context root.
        context = ROOT / 'services/game'
        rules = (context / 'docker/ubuntu/Dockerfile.dockerignore').read_text().splitlines()
        for resource in ('vipai86-pvf', 'vipai86-assets', 'assets/home/template/init/Script.tgz',
                         'assets/home/template/init/Script.pvf', 'assets/home/template/init/df_game_r',
                         'assets/root/QK', 'assets/root/qk_plugin.so', 'assets/root/qingkong',
                         'assets/home/template/neople/game/libantisvrinline.so'):
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

    def test_raid_defaults_off_and_incompatible_enable_preserves_instance(self):
        instance = self.root / 'main'
        values = manager.create_instance(instance, self.args, self.row, self.archive)
        self.assertEqual(values['RAID_EXTENSION'], 'off')
        before = (instance / '.env').read_bytes()
        with patch.object(manager, 'instance_path', return_value=instance), patch.object(manager, 'docker_check'), \
             patch.object(manager, 'catalog', return_value={70: self.row}), \
             patch.object(manager, 'start') as start:
            with self.assertRaisesRegex(ValueError, 'DP2'):
                manager.main(['config', '--raid-extension', 'on', '--plugins', 'frida'])
            start.assert_not_called()
        self.assertEqual((instance / '.env').read_bytes(), before)

    def test_disabling_raid_can_recover_from_missing_optional_assets(self):
        instance = self.root / 'main'
        values = manager.create_instance(instance, self.args, self.row, self.archive)
        values.update(PLUGIN_MODE='dp2', RAID_EXTENSION='on')
        manager.write_env(instance / '.env', values)
        with patch.object(manager, 'instance_path', return_value=instance), patch.object(manager, 'docker_check'), \
             patch.object(manager, 'catalog', return_value={70: self.row}), \
             patch.object(manager, 'start') as start:
            manager.main(['config', '--raid-extension', 'off'])
            start.assert_called_once_with(instance, recreate=True)
        self.assertEqual(manager.read_env(instance)['RAID_EXTENSION'], 'off')

    def test_raid_rejects_missing_resources_before_publishing_new_instance(self):
        self.args.plugins = 'dp2'
        self.args.raid_extension = 'on'
        self.args.dof_assets = self.root / 'missing'
        with self.assertRaises(ValueError):
            manager.create_instance(self.root / 'main', self.args, self.row, self.archive)
        self.assertFalse((self.root / 'main').exists())

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

    def test_game_binding_and_explicit_client_sources(self):
        values = manager.initial_values(self.args, self.row, self.root / 'main')
        values['GAME_BIND_IP'] = '0.0.0.0'
        self.assertTrue(all(p.startswith('0.0.0.0:') for p in manager.ports_override(values)['services']['netguard']['ports']))
        self.assertEqual(values['BIND_IP'], '127.0.0.1')
        with patch.dict('os.environ', {'NETGUARD_ALLOWED_CLIENTS': '192.0.2.17/32'}):
            rules = guard.render_rules(['172.30.0.2'])
            ingress = [line for line in rules.splitlines() if '--dports' in line]
            self.assertTrue(ingress)
            self.assertTrue(all('-s 192.0.2.17/32' in line for line in ingress))
            self.assertNotIn('--dports', guard.render_rules(ipv6=True))
            self.assertIn('-d 172.30.0.2/32 -p tcp --dport 3306', rules)
        with patch.dict('os.environ', {'NETGUARD_ALLOWED_CLIENTS': 'invalid'}):
            with self.assertRaises(ValueError): guard.render_rules(['172.30.0.2'])

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
        settings = {'registry': 'ghcr.io/test-owner', 'tag': 'v1'}
        manager.image_config.save(self.root / 'state', settings)
        with patch.object(manager, 'STATE', self.root / 'state'), \
             patch.object(manager, 'docker_check'), patch.object(manager, 'catalog', return_value={70: self.row}), \
             patch.object(manager, 'fetch', return_value=self.archive), \
             patch.object(manager, 'prepare') as prepare, patch.object(manager, 'build') as build, \
             patch.object(manager, 'run', return_value=subprocess.CompletedProcess([], 0, 'linux/amd64\n')) as docker, \
             patch.object(manager, 'start') as start:
            manager.main(['install', '--ip', '127.0.0.1', '--pvf', str(self.pvf)])
            record = json.loads((self.root / 'state/instances/main/instance.json').read_text())
            self.assertEqual(record['level'], 70)
            prepare.assert_not_called()
            build.assert_not_called()
            pulls = [call.args[0] for call in docker.call_args_list if call.args[0][1] == 'pull']
            self.assertEqual(pulls, [['docker', 'pull', '--platform', 'linux/amd64', ref]
                                    for ref in manager.image_config.references(settings).values()])
            values = manager.read_env(self.root / 'state/instances/main')
            self.assertEqual(values['DOF_GAME_IMAGE'], 'ghcr.io/test-owner/dof-easy-game:v1')
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
             patch.object(manager, 'run', return_value=subprocess.CompletedProcess([], 0, 'linux/amd64\n')) as docker, \
             patch.object(manager, 'start'), patch.object(manager.urllib.request, 'urlopen') as network:
            manager.main(['install', '--ip', '127.0.0.1', '--pvf', str(self.pvf), '--resources', str(cache), '--offline'])
            prepare.assert_not_called()
            build.assert_not_called()
            network.assert_not_called()
            self.assertTrue(all(call.args[0][1:3] == ['image', 'inspect'] for call in docker.call_args_list))

    def test_missing_registry_never_builds_or_creates_instance(self):
        with patch.object(manager, 'STATE', self.root / 'state'), patch.object(manager, 'docker_check'), \
             patch.object(manager, 'catalog', return_value={70: self.row}), \
             patch.object(manager, 'fetch') as fetch, patch.object(manager, 'build') as build:
            with self.assertRaisesRegex(ValueError, '尚未配置镜像仓库'):
                manager.main(['install', '--ip', '127.0.0.1', '--pvf', str(self.pvf)])
            fetch.assert_not_called()
            build.assert_not_called()
            self.assertFalse((self.root / 'state/instances/main').exists())

    def test_explicit_build_uses_configured_tags_without_pulling(self):
        settings = {'registry': 'registry.example.com:5000/team', 'tag': 'v2'}
        with patch.object(manager, 'STATE', self.root / 'state'), patch.object(manager, 'docker_check'), \
             patch.object(manager, 'prepare') as prepare, \
             patch.object(manager, 'run', return_value=subprocess.CompletedProcess([], 0, 'linux/amd64\n')) as docker:
            manager.main(['build', '--registry', settings['registry'], '--tag', settings['tag']])
            prepare.assert_called_once()
            commands = [call.args[0] for call in docker.call_args_list]
            built = [command[command.index('-t') + 1] for command in commands if command[1] == 'build']
            self.assertEqual(set(built), set(manager.image_config.references(settings).values()))
            self.assertFalse(any(command[1] in ('pull', 'push') for command in commands))

    def test_failed_pull_preserves_existing_instance_and_never_builds(self):
        state = self.root / 'state'
        instance = state / 'instances/main'
        old = {'registry': 'ghcr.io/old-owner', 'tag': 'v1'}
        manager.create_instance(instance, self.args, self.row, self.archive, old)
        before = (instance / '.env').read_bytes()
        with patch.object(manager, 'STATE', state), patch.object(manager, 'docker_check'), \
             patch.object(manager, 'catalog', return_value={70: self.row}), \
             patch.object(manager, 'run', side_effect=subprocess.CalledProcessError(1, ['docker', 'pull'])), \
             patch.object(manager, 'build') as build, patch.object(manager, 'start') as start:
            with self.assertRaisesRegex(ValueError, '不会回退到源码构建'):
                manager.main(['install', '--registry', 'ghcr.io/new-owner', '--tag', 'v2'])
            self.assertEqual((instance / '.env').read_bytes(), before)
            build.assert_not_called()
            start.assert_not_called()

    def test_global_defaults_do_not_override_existing_instance_or_shell(self):
        state = self.root / 'state'
        instance = state / 'instances/main'
        settings = {'registry': 'ghcr.io/instance-owner', 'tag': 'v1'}
        manager.create_instance(instance, self.args, self.row, self.archive, settings)
        manager.image_config.save(state, {'registry': 'ghcr.io/new-default', 'tag': 'v9'})
        with patch.object(manager, 'STATE', state), patch.object(manager, 'docker_check'), \
             patch.object(manager, 'catalog', return_value={70: self.row}), \
             patch.object(manager, 'acquire_images') as acquire, patch.object(manager, 'start'):
            manager.main(['install'])
            acquire.assert_called_once_with(settings, 'pull', None)
        with patch.dict(os.environ, {'DOF_GAME_IMAGE': 'other:image'}), patch.object(manager, 'run') as docker:
            manager.compose(instance, 'config')
            self.assertEqual(docker.call_args.kwargs['env']['DOF_GAME_IMAGE'], 'ghcr.io/instance-owner/dof-easy-game:v1')

    def test_bad_registry_and_architecture_are_rejected(self):
        for registry in ('https://ghcr.io/owner', 'ghcr.io/Owner', 'bad host/team', 'ghcr.io/u;cmd',
                         'localhost:70000/team', 'ghcr.io/a//b', 'ghcr.io/user:password@host'):
            with self.subTest(registry=registry), self.assertRaises(ValueError):
                manager.image_config.validate(registry, 'v1')
        with patch.object(manager, 'run', return_value=subprocess.CompletedProcess([], 0, 'linux/arm64\n')):
            with self.assertRaisesRegex(ValueError, 'linux/amd64'):
                manager.acquire_images({'registry': '', 'tag': 'local'}, 'offline')

    def test_backup_uses_instance_game_image(self):
        instance = self.root / 'state/instances/main'
        settings = {'registry': 'ghcr.io/backup-owner', 'tag': 'v3'}
        manager.create_instance(instance, self.args, self.row, self.archive, settings)
        def fixture_compose(*args, **kwargs):
            if kwargs.get('stdout') is not None:
                kwargs['stdout'].write(b'-- simulated database dump for archive routing test\n' * 4)
            return subprocess.CompletedProcess([], 0, 'db\n')
        with patch.object(manager, 'STATE', self.root / 'state'), \
             patch.object(manager, 'compose', side_effect=fixture_compose) as compose, \
             patch.object(manager, 'run') as docker:
            manager.backup(instance)
            command = docker.call_args.args[0]
            self.assertIn('ghcr.io/backup-owner/dof-easy-game:v3', command)
            self.assertNotIn('dof-easy-game:local', command)
            dump = next(call.args[-1] for call in compose.call_args_list if any('mysqldump' in str(arg) for arg in call.args))
            self.assertIn('--all-databases', dump)
            self.assertIn('--ignore-table=mysql.innodb_index_stats', dump)
            self.assertIn('--ignore-table=mysql.innodb_table_stats', dump)

    def test_registry_settings_persist_without_touching_docker(self):
        with patch.object(manager, 'STATE', self.root / 'state'), patch.object(manager, 'docker_check') as docker:
            manager.main(['images', '--registry', 'ghcr.io/owner', '--tag', 'v4'])
            self.assertEqual(manager.image_config.load(self.root / 'state'), {'registry': 'ghcr.io/owner', 'tag': 'v4'})
            manager.main(['images', '--tag', 'v5'])
            self.assertEqual(manager.image_config.load(self.root / 'state')['tag'], 'v5')
            docker.assert_not_called()

    def test_offline_import_keeps_custom_references_and_never_pulls(self):
        settings = {'registry': 'ghcr.io/offline-owner', 'tag': 'v1'}
        with patch.object(manager, 'prepare') as prepare, patch.object(manager, 'build') as build, \
             patch.object(manager, 'run', return_value=subprocess.CompletedProcess([], 0, 'linux/amd64\n')) as docker:
            manager.acquire_images(settings, 'offline', self.root / 'images.tar')
            commands = [call.args[0] for call in docker.call_args_list]
            self.assertEqual(commands[0], ['docker', 'load', '-i', self.root / 'images.tar'])
            self.assertEqual({command[-1] for command in commands[1:]}, set(manager.image_config.references(settings).values()))
            self.assertFalse(any(command[1] in ('pull', 'build') for command in commands))
            prepare.assert_not_called()
            build.assert_not_called()

    def test_install_tag_change_preserves_credentials_and_runtime(self):
        state = self.root / 'state'
        instance = state / 'instances/main'
        manager.create_instance(instance, self.args, self.row, self.archive,
                                {'registry': 'ghcr.io/instance-owner', 'tag': 'v1'})
        before = manager.read_env(instance)
        sentinel = instance / 'runtime/game/data/operator.lua'
        sentinel.write_bytes(b'operator edits')
        with patch.object(manager, 'STATE', state), patch.object(manager, 'docker_check'), \
             patch.object(manager, 'catalog', return_value={70: self.row}), \
             patch.object(manager, 'acquire_images') as acquire, patch.object(manager, 'start'):
            manager.main(['install', '--tag', 'v2'])
            acquire.assert_called_once_with({'registry': 'ghcr.io/instance-owner', 'tag': 'v2'}, 'pull', None)
        after = manager.read_env(instance)
        for key, value in before.items():
            if not key.startswith('DOF_') and key != 'IMAGE_TAG':
                self.assertEqual(after[key], value)
        self.assertEqual(after['DOF_GAME_IMAGE'], 'ghcr.io/instance-owner/dof-easy-game:v2')
        self.assertEqual(sentinel.read_bytes(), b'operator edits')


if __name__ == '__main__':
    unittest.main()
