"""Original Qingkong integration contracts; no proprietary binary is executed."""
import configparser
import hashlib
import importlib
import json
import os
from pathlib import Path
import subprocess
import sys
import tempfile
import unittest
from unittest.mock import patch, Mock

ROOT = Path(__file__).resolve().parents[2]
BASE = ROOT / 'services/game/runtime/ubuntu'
sys.path.insert(0, str(BASE))

class QingkongRuntimeTests(unittest.TestCase):
    def modules(self):
        self.assertTrue((BASE / 'qingkong/assets.py').is_file(), 'original Qingkong asset profile is absent')
        return importlib.import_module('qingkong.assets'), importlib.import_module('qingkong.service')

    def fixture(self, root, assets):
        source = root / 'source'; source.mkdir()
        files = []
        for name, architecture in [('QK', 2), ('qk_plugin.so', 1), ('libantisvrinline.so', 1), ('qingkong/libuv.so.1', 2)]:
            raw = b'\x7fELF' + bytes([architecture]) + b'original bytes'
            target = source / name; target.parent.mkdir(exist_ok=True); target.write_bytes(raw)
            files.append(dict(path=name, bytes=len(raw), sha256=hashlib.sha256(raw).hexdigest(), elf_class=architecture))
        lock = root / 'lock.json'; lock.write_text(json.dumps(dict(schema=1, profile='qingkong-2.8-replacement', files=files)))
        (source / 'config').mkdir()
        for name in assets.CONFIGS:
            (source / 'config' / name).write_bytes(b'; operator\r\ncustom=1\r\n')
        (source / 'config/config.ini').write_text('[sever]\nDungeonCheck=1\n[Gate]\nRobotKey=keep\ngateProt=8887\nDfGameR=old\n[db]\ndb_database_name=d_taiwan\ndb_host=old\ndb_prot=3306\ndb_user_name=old\ndb_password=old\n')
        return source, lock

    def test_installs_original_bytes_and_preserves_custom_settings(self):
        assets, _ = self.modules()
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory).resolve(); source, lock = self.fixture(root, assets)
            with patch.object(assets, 'LOCK', lock):
                target = root / 'state'; assets.install(source, target)
                for row in assets.manifest():
                    self.assertEqual((source / row['path']).read_bytes(), (target / 'assets' / row['path']).read_bytes())
                config = target / 'config/qk_plugin_config.ini'
                self.assertEqual(config.read_bytes(), (source / 'config/qk_plugin_config.ini').read_bytes())
                config.write_bytes(b'operator edit')
                assets.install(source, target)
                self.assertEqual(config.read_bytes(), b'operator edit')
                (source / 'QK').write_bytes(b'changed')
                with self.assertRaises(ValueError): assets.verify(source)

    def test_missing_original_config_and_symlinks_fail_closed(self):
        assets, _ = self.modules()
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory).resolve(); source, lock = self.fixture(root, assets)
            with patch.object(assets, 'LOCK', lock):
                missing = source / 'config/qk_plugin_antonRaid.ini'; missing.unlink()
                with self.assertRaises(ValueError): assets.install(source, root / 'state')
                self.assertFalse((root / 'state').exists())
                missing.symlink_to(source / 'config/config.ini')
                with self.assertRaises(ValueError): assets.install(source, root / 'state')

    def test_preflight_rejects_derived_raid_and_unknown_plugin_mode(self):
        assets, _ = self.modules()
        self.assertEqual(assets.state_for({}, Path('/instance')), Path('/instance/qingkong'))
        assets.validate_options('none', 'off')
        with self.assertRaises(ValueError): assets.validate_options('none', 'on')
        for mode in ('dp2', 'frida', 'dp2-frida'):
            with self.assertRaises(ValueError): assets.validate_options(mode, 'off')
        with self.assertRaises(ValueError): assets.validate_options('unknown', 'off')

    def test_original_gateway_config_is_validated_before_installing(self):
        assets, _ = self.modules()
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory).resolve(); source, lock = self.fixture(root, assets)
            with patch.object(assets, 'LOCK', lock):
                for data in ('[Gate]\ngateProt=8888\n', '[Gate]\ngateProt=8887\n'):
                    (source / 'config/config.ini').write_text(data)
                    with self.assertRaises(ValueError): assets.install(source, root / 'state')
                    self.assertFalse((root / 'state').exists())

    def test_gateway_config_changes_only_managed_fields(self):
        assets, service = self.modules()
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory).resolve(); source, lock = self.fixture(root, assets)
            with patch.object(assets, 'LOCK', lock): assets.install(source, root / 'state')
            state = root / 'state'
            values = dict(CUR_SG_DB_HOST='db.example', CUR_SG_DB_PORT='3307', DOF_DB_GAME_PASSWORD='secret12')
            service.configure(state, values)
            parser = configparser.ConfigParser(); parser.read(state / 'config/config.ini')
            self.assertEqual(parser['sever']['DungeonCheck'], '1')
            self.assertEqual(parser['Gate']['RobotKey'], 'keep')
            self.assertEqual(parser['db']['db_host'], 'db.example')
            self.assertEqual(parser['db']['db_password'], 'secret12')
            self.assertEqual(parser['db']['db_database_name'], 'd_taiwan')
            self.assertEqual((state / 'config/config.ini').stat().st_mode & 0o777, 0o600)

    def test_loader_readiness_accepts_only_exact_original_rewrite(self):
        _, service = self.modules()
        with tempfile.TemporaryDirectory() as directory:
            source, target = Path(directory)/'source', Path(directory)/'game'
            original = b'prefix\0libantisvrimport.so\0suffix'
            source.write_bytes(original); target.write_bytes(original)
            self.assertFalse(service.runtime_game_ready(source, target))
            target.write_bytes(original.replace(b'libantisvrimport.so', b'libantisvrinline.so'))
            self.assertTrue(service.runtime_game_ready(source, target))
            target.write_bytes(target.read_bytes()+b'changed')
            with self.assertRaises(ValueError): service.runtime_game_ready(source, target)

    def test_database_readiness_requires_original_tables(self):
        _, service = self.modules()
        with patch.dict(os.environ, dict(CUR_SG_DB_HOST='db', CUR_SG_DB_PORT='3306', DOF_DB_GAME_PASSWORD='secret12')), patch.object(service.subprocess, 'run') as run:
            run.return_value = subprocess.CompletedProcess([], 0, b'1\n', b'')
            self.assertTrue(service.database_ready())
            command = run.call_args[0][0]
            self.assertIn('qk.qingkong_config_v2', command[-1]); self.assertIn('qk.billing', command[-1])
            self.assertNotIn('secret12', ' '.join(command))

    def test_supervisor_restores_identity_before_readiness_and_reaps_daemon(self):
        _, service = self.modules()
        with tempfile.TemporaryDirectory() as directory:
            root=Path(directory); marker=root/'ready'; callbacks={}; restored=[]; observed=[]
            launcher=Mock(); launcher.poll.return_value=0
            libc=Mock(); libc.prctl.return_value=0
            def register(signum, handler): callbacks[signum]=handler
            def sleep(seconds):
                if marker.exists():
                    observed.append(marker.read_text())
                    self.assertEqual(len(restored), 2)
                    callbacks[service.signal.SIGTERM](service.signal.SIGTERM, None)
            def read(path): return b'known-key'
            with patch.object(service, 'READY', marker), patch.object(service.ctypes, 'CDLL', return_value=libc), patch.object(service.signal, 'signal', side_effect=register), patch.object(service, 'channels_running', return_value=False), patch.object(service, 'install_gateway_keys', side_effect=lambda: restored.append(True)), patch.object(service.subprocess, 'Popen', return_value=launcher) as spawn, patch.object(service.os, 'waitpid', side_effect=ChildProcessError), patch.object(service, 'gateway_children', side_effect=[[123], [123], [123], [123], [], []]), patch.object(service.socket, 'create_connection'), patch.object(service, 'database_ready', return_value=True), patch.object(service, 'runtime_game_ready', return_value=True), patch.object(service.time, 'sleep', side_effect=sleep), patch.object(Path, 'read_bytes', read), patch.object(service.os, 'kill') as kill:
                service.run()
            self.assertEqual(observed, ['123'])
            self.assertFalse(marker.exists())
            kill.assert_called_once_with(123, service.signal.SIGTERM)
            self.assertEqual(spawn.call_args[0][0], ['/opt/dnf/ubuntu/run-game', '/root/QK', 'start'])
            self.assertEqual(spawn.call_args[1]['cwd'], '/root')

    def test_prepared_libraries_keep_original_bytes_and_separate_architectures(self):
        assets, service = self.modules()
        with tempfile.TemporaryDirectory() as directory:
            root=Path(directory).resolve(); source, lock=self.fixture(root, assets)
            state=root/'state'; destination=root/'root'; game=root/'game'; i386=root/'i386'
            with patch.object(assets, 'LOCK', lock):
                assets.install(source, state)
                service.prepare_libraries(state, destination, game, i386)
                for row in assets.manifest():
                    target=(game if row['path']=='libantisvrinline.so' else destination)/row['path']
                    self.assertEqual(target.read_bytes(), (source/row['path']).read_bytes())
                self.assertTrue((destination/'QK').stat().st_mode & 0o100)
                self.assertEqual({p.name for p in i386.iterdir()}, {'qk_plugin.so', 'libantisvrinline.so'})

    def test_wrapper_is_idempotent_and_does_not_load_derived_features(self):
        _, service = self.modules()
        adapter = (BASE/'qingkong/db_adapter.js').read_bytes()
        body = b'rpc.exports = {init: function () {}};'
        wrapper = service.add_adapter(body, adapter)
        self.assertEqual(service.add_adapter(wrapper, adapter), wrapper)
        self.assertNotIn(b'dof_native', adapter)
        self.assertIn(b'qk_plugin.so', adapter); self.assertIn(b'libantisvrinline.so', adapter)
        self.assertIn(b'0x083F4024', adapter)

class QingkongProcessIntegration(unittest.TestCase):
    def run_game(self, directory, mode='none', raid='off', arch=1, name='df_game_r'):
        import io
        path = Path(directory) / name
        path.write_bytes(b'\x7fELF' + bytes([arch]) + b'\0' * 15)
        source = (ROOT/'services/game/docker/ubuntu/run-game').read_text()
        source = source.replace('\nredact_output()\n', '\n# Output redaction is independently tested.\n')
        environment = dict(LAUNCHER_MODE='qingkong', PLUGIN_MODE=mode, RAID_EXTENSION=raid)
        with patch.dict(os.environ, environment, clear=True), patch.object(sys, 'argv', ['run-game', str(path)]), patch.object(os, 'execve') as execute, patch.object(Path, 'is_file', return_value=True) as exists, patch('builtins.open', side_effect=lambda *a, **k: io.BytesIO(b'\x7fELF'+bytes([arch]))):
            exec(compile(source, 'run-game', 'exec'), {})
        return execute.call_args[0][2], exists

    def test_qingkong_gets_one_credential_gadget_and_original_plugin(self):
        for mode in ('none',):
            with self.subTest(mode=mode), tempfile.TemporaryDirectory() as directory:
                environment, exists = self.run_game(directory, mode)
                loaders = environment['LD_PRELOAD'].split(':')
                self.assertEqual(loaders.count('/home/neople/game/frida.so'), 1)
                self.assertIn('/root/qk_plugin.so', loaders)
                self.assertNotIn('/opt/dof-easy/lib/libdof-plugin.so', loaders)
                self.assertEqual('/dp2/libdp2pre.so' in loaders, mode in ('dp2', 'dp2-frida'))
                self.assertEqual(environment['LD_LIBRARY_PATH'], '/opt/qingkong/i386:/opt/dnf/compat/i386')

    def test_ordinary_gameplay_modes_cannot_be_loaded(self):
        for mode in ('dp2', 'frida', 'dp2-frida'):
            with self.subTest(mode=mode), tempfile.TemporaryDirectory() as directory:
                with self.assertRaises(SystemExit): self.run_game(directory, mode)

    def test_original_qk_uses_only_amd64_dependency_directory(self):
        with tempfile.TemporaryDirectory() as directory:
            environment, _ = self.run_game(directory, arch=2, name='QK')
            self.assertEqual(environment['LD_LIBRARY_PATH'], '/root/qingkong:/opt/qingkong/amd64')
            self.assertNotIn('i386', environment['LD_LIBRARY_PATH'])
            self.assertNotIn('LD_PRELOAD', environment)

    def test_legacy_nsl_is_copied_from_pinned_image_and_hash_checked(self):
        dockerfile=(ROOT/'services/game/docker/ubuntu/Dockerfile').read_text()
        self.assertIn('COPY --from=mysql56_client /lib/x86_64-linux-gnu/libnsl-2.24.so /opt/qingkong/amd64/libnsl.so.1', dockerfile)
        self.assertIn('2637c7317a3059440922897e03208f9e19866fa7be5ecbc11d9e18a0e62db3de  /opt/qingkong/amd64/libnsl.so.1', dockerfile)
        self.assertIn('COPY --from=mysql56_client /usr/share/doc/libc6/copyright /opt/dnf/licenses/qingkong-libnsl-copyright', dockerfile)

    def test_qingkong_derived_raid_combination_rejected(self):
        with tempfile.TemporaryDirectory() as directory, self.assertRaises(SystemExit):
            self.run_game(directory, mode='dp2', raid='on')

    def test_health_requires_marker_actual_executable_and_both_instance_keys(self):
        health = importlib.import_module('health')
        self.assertTrue(hasattr(health, 'qingkong_ready'), 'Qingkong health verification is absent')
        with tempfile.TemporaryDirectory() as directory:
            root=Path(directory); state=root/'state'; game=root/'game'; proc=root/'proc'
            (state/'keys').mkdir(parents=True); game.mkdir(); (proc/'123').mkdir(parents=True)
            ready=root/'ready'; ready.write_text('123'); public=root/'publickey.pem'; public.write_bytes(b'public')
            (state/'keys/privatekey.pem').write_bytes(b'private')
            (state/'keys/publickey.pem').write_bytes(b'public')
            (game/'privatekey.pem').write_bytes(b'private'); (game/'publickey.pem').write_bytes(b'public')
            (proc/'123/exe').symlink_to('/root/QK')
            health.qingkong_ready(state, game, public, ready, proc)
            (proc/'123/exe').unlink(); (proc/'123/exe').symlink_to('/usr/bin/python3')
            with self.assertRaises(ValueError): health.qingkong_ready(state, game, public, ready, proc)
            (proc/'123/exe').unlink(); (proc/'123/exe').symlink_to('/root/QK')
            (game/'privatekey.pem').write_bytes(b'wrong')
            with self.assertRaises(ValueError): health.qingkong_ready(state, game, public, ready, proc)

    def test_first_boot_accepts_qingkong_seed_but_rejects_unowned_directories(self):
        manager=importlib.import_module('managed')
        with tempfile.TemporaryDirectory() as directory:
            root=Path(directory).resolve(); data=root/'data'; source=root/'source'
            (data/'qingkong/config').mkdir(parents=True)
            original=data/'qingkong/config/qk_plugin_config.ini'; original.write_bytes(b'operator original')
            (source/'run').mkdir(parents=True); (source/'run/start.sh').write_text('managed startup')
            # An unrelated legacy runtime still prevents every persistent write.
            (data/'legacy').mkdir()
            with self.assertRaisesRegex(ValueError, 'unmarked runtime directory'):
                manager.synchronize(data, source, {})
            self.assertFalse((data/manager.MARKER).exists())
            self.assertFalse((data/'run').exists())
            (data/'legacy').rmdir()
            manager.synchronize(data, source, {})
            self.assertEqual(original.read_bytes(), b'operator original')
            self.assertEqual((data/'run/start.sh').read_text(), 'managed startup')
            marker_data=json.loads((data/manager.MARKER).read_text())
            self.assertEqual(marker_data['platform'], 'ubuntu24')
            self.assertEqual(set(marker_data['files']), {'run/start.sh'})

    def test_shared_base_hook_path_is_available_before_template_initialization(self):
        with tempfile.TemporaryDirectory() as directory:
            root=Path(directory).resolve()
            (root/'home/template/init').mkdir(parents=True)
            (root/'run/dnf').mkdir(parents=True)
            (root/'dev/shm').mkdir(parents=True)
            (root/'home/template/init/libhook.so').write_bytes(b'shared base hook fixture')
            init=root/'home/template/init/init.sh'
            init.write_text('cp "'+str(root/'home/template/init/libhook.so')+'" "'+str(root/'data/dp/libhook.so')+'" || exit 1\nexit 23\n')
            source=(BASE/'bootstrap.sh').read_text()
            import re
            source=re.sub(r'(?<![A-Za-z0-9_}])/(data|home|run|dev|dp2)(?=/|["\s]|$)',lambda match:str(root/match[1]),source)
            result=subprocess.run(['bash','-c',source],env=dict(os.environ,SERVER_TYPE='ALL'),capture_output=True,text=True)
            self.assertEqual(result.returncode,23,result.stderr)
            self.assertEqual((root/'dp2/libhook.so').read_bytes(),b'shared base hook fixture')

    def test_bootstrap_and_keys_route_original_profile(self):
        bootstrap=(BASE/'bootstrap.sh').read_text()
        keys=(ROOT/'deploy/bootstrap/init-keys.sh').read_text()
        self.assertIn('/opt/dnf/ubuntu/qingkong/service.py prepare', bootstrap)
        self.assertIn('/game/qingkong', keys)


class QingkongKeys(unittest.TestCase):
    def execute(self, root, launcher='qingkong'):
        source = (ROOT/'deploy/bootstrap/init-keys.sh').read_text()
        import re
        source = re.sub(r'(?<![A-Za-z0-9_}])/(keys|game)(?=/|\s|$)', lambda match: str(root/match[1]), source)
        source = source.replace('/tmp/derived.pub', str(root/'derived.pub'))
        return subprocess.run(['sh','-c',source], env=dict(os.environ,LAUNCHER_MODE=launcher,RAID_EXTENSION='off'),capture_output=True,text=True)

    def test_unsupported_mode_is_rejected_before_generating_keys(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory).resolve()
            result = self.execute(root, 'native')
            self.assertNotEqual(result.returncode,0)
            self.assertFalse((root/'keys').exists())

    @unittest.skipUnless(sys.platform == 'linux', 'requires deployed Linux OpenSSL pkey -check')
    def test_existing_keys_are_preserved_and_conflicting_game_key_is_not_overwritten(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory).resolve(); (root/'game/qingkong').mkdir(parents=True)
            result = self.execute(root)
            self.assertEqual(result.returncode,0,result.stderr)
            before = (root/'keys/private.key').read_bytes()
            public = (root/'keys/publickey.pem').read_bytes()
            self.assertEqual((root/'game/qingkong/keys/privatekey.pem').read_bytes(), before)
            self.assertEqual((root/'game/publickey.pem').read_bytes(), public)
            self.assertEqual(self.execute(root).returncode,0)
            self.assertEqual((root/'keys/private.key').read_bytes(),before)
            (root/'game/publickey.pem').write_bytes(b'operator different key')
            result = self.execute(root)
            self.assertNotEqual(result.returncode,0)
            self.assertEqual((root/'game/publickey.pem').read_bytes(),b'operator different key')
            self.assertEqual((root/'keys/private.key').read_bytes(),before)

if __name__ == '__main__': unittest.main()
