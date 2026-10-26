"""Local asset integrity, compatibility gates and preservation of operator scripts."""
import contextlib
import hashlib
import importlib.util
import io
import os
import json
import shutil
import subprocess
import struct
from pathlib import Path
import tempfile
import unittest
from unittest.mock import patch

ROOT = Path(__file__).resolve().parents[2]
BASE = ROOT / 'services/game/runtime/ubuntu'


def module(name):
    spec = importlib.util.spec_from_file_location(name, BASE / ({'raid_assets': 'gateway/assets.py', 'qk': 'gateway/service.py'}.get(name, name + '.py')))
    mod = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(mod)
    return mod


class RaidAssets(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory()
        self.addCleanup(self.temp.cleanup)
        self.root = Path(self.temp.name)
        self.source = self.root / 'source'
        self.source.mkdir()
        self.assets = module('raid_assets')
        data = b'\x7fELF\x01\x01' + b'\0' * 30
        (self.source / 'libdof-plugin.so').write_bytes(data)
        self.rows = [dict(path='libdof-plugin.so', bytes=len(data),
                          sha256=hashlib.sha256(data).hexdigest(), elf_class=1)]
        lock = dict(schema=2, sourceEdition='replacement-only', files=self.rows)
        lock_path = self.root / 'lock.json'; lock_path.write_text(json.dumps(lock))
        (self.source / 'manifest.json').write_text(json.dumps(lock))
        self.assets.LOCK = lock_path
        self.patcher = patch.object(self.assets, 'manifest', return_value=self.rows)
        self.patcher.start()
        self.addCleanup(self.patcher.stop)

    def test_import_is_complete_and_preserves_edited_config(self):
        target = self.root / 'installed'
        self.assets.install(self.source, target)
        config = target / 'config/plugin.ini'
        content = config.read_text()
        for key in ['Inlay_function', 'Equ_unlocked_instantly', 'Task_Completion_coupon']:
            self.assertIn(key + '=0', content)
        config.write_text('operator config')
        self.assets.install(self.source, target)
        self.assertEqual(config.read_text(), 'operator config')
        self.assertTrue((target / 'config/anton-raid.ini').is_file())
        self.assertTrue((target / 'config/luke-raid.ini').is_file())

    def test_corruption_or_symlink_rejected_before_publish(self):
        target = self.root / 'installed'
        path = self.source / 'libdof-plugin.so'
        original = path.read_bytes()
        path.write_bytes(original + b'changed')
        with self.assertRaises(ValueError):
            self.assets.install(self.source, target)
        self.assertFalse(target.exists())
        path.unlink()
        other = self.root / 'binary'
        other.write_bytes(original)
        path.symlink_to(other)
        with self.assertRaises(ValueError):
            self.assets.install(self.source, target)
        self.assertFalse(target.exists())

    def test_raid_bundle_override_and_link_rejection(self):
        config = self.source / 'plugins/raid/config/anton-raid.ini'
        config.parent.mkdir(parents=True)
        config.write_text('; custom raid parameters\n')
        self.assets.install(self.source, self.root / 'configured')
        self.assertEqual((self.root / 'configured/config/anton-raid.ini').read_text(),
                         '; custom raid parameters\n')
        config.unlink()
        config.symlink_to(self.root / 'absent')
        with self.assertRaisesRegex(ValueError, 'must not contain links'):
            self.assets.install(self.source, self.root / 'rejected')
        self.assertFalse((self.root / 'rejected').exists())

    def test_old_bundle_raid_config_is_not_silently_discarded(self):
        config = self.source / 'config/anton-raid.ini'
        config.parent.mkdir()
        config.write_text('; existing custom raid parameters\n')
        with self.assertRaisesRegex(ValueError, 'plugins/raid/config'):
            self.assets.install(self.source, self.root / 'rejected')
        self.assertFalse((self.root / 'rejected').exists())
        self.assertEqual(config.read_text(), '; existing custom raid parameters\n')

    def test_missing_mutable_config_is_not_silently_replaced(self):
        target = self.root / 'installed'
        self.assets.install(self.source, target)
        (target / 'config/plugin.ini').unlink()
        with self.assertRaises(ValueError):
            self.assets.install(self.source, target)

    @unittest.skipUnless(shutil.which('openssl'), 'OpenSSL required for key restoration test')
    def test_native_initialization_restores_existing_login_pair(self):
        runtime = module('qk')
        state, game = self.root / 'keys', self.root / 'game'
        (state / 'keys').mkdir(parents=True)
        game.mkdir()
        private, public = state / 'keys/privatekey.pem', self.root / 'publickey.pem'
        subprocess.run(['openssl', 'genpkey', '-algorithm', 'RSA', '-pkeyopt',
                        'rsa_keygen_bits:1024', '-out', str(private)], check=True, capture_output=True)
        public.write_bytes(subprocess.run(['openssl', 'pkey', '-in', str(private), '-pubout'],
                                         check=True, capture_output=True).stdout)
        for name in ('privatekey.pem', 'publickey.pem'):
            (game / name).write_bytes(b'native regenerated working key')
        runtime.install_gateway_keys(state, public, game)
        self.assertEqual((game / 'privatekey.pem').read_bytes(), private.read_bytes())
        self.assertEqual((game / 'publickey.pem').read_bytes(), public.read_bytes())
        before = (game / 'publickey.pem').read_bytes()
        public.unlink()
        public.symlink_to(game / 'publickey.pem')
        with self.assertRaises(ValueError):
            runtime.install_gateway_keys(state, public, game)
        self.assertEqual((game / 'publickey.pem').read_bytes(), before)

    def test_gateway_restart_detects_only_native_game_channels(self):
        runtime = module('qk')
        proc = self.root / 'proc'
        (proc / '1').mkdir(parents=True)
        (proc / '1/comm').write_text('python3\n')
        real_path = Path
        def mapped(value):
            return proc if str(value) == '/proc' else real_path(value)
        with patch.object(runtime, 'Path', mapped):
            self.assertFalse(runtime.channels_running())
            (proc / '2').mkdir()
            (proc / '2/comm').write_text('df_game_r\n')
            self.assertTrue(runtime.channels_running())

    def test_native_game_accepts_only_the_exact_inline_name_change(self):
        runtime = module('qk')
        source, game = self.root / 'source-game', self.root / 'runtime-game'
        original = b'ELF prefix libantisvrimport.so suffix'
        source.write_bytes(original)
        game.write_bytes(original)
        self.assertFalse(runtime.runtime_game_ready(source, game))
        game.write_bytes(original.replace(b'libantisvrimport.so', b'libdof-inline.so'.ljust(len(b'libantisvrimport.so'), b'\0')))
        self.assertTrue(runtime.runtime_game_ready(source, game))
        game.write_bytes(game.read_bytes() + b'changed')
        with self.assertRaises(ValueError):
            runtime.runtime_game_ready(source, game)
        self.assertEqual(source.read_bytes(), original)

    def test_library_install_copies_only_verified_derived_files(self):
        runtime = module('qk')
        state = self.root / 'state'
        self.assets.install(self.source, state)
        target = self.root / 'runtime-libs'
        import gateway.assets as canonical
        with patch.object(canonical, 'LOCK', self.assets.LOCK):
            runtime.prepare_libraries(state, target)
        self.assertEqual((target / 'libdof-plugin.so').read_bytes(),
                         (self.source / 'libdof-plugin.so').read_bytes())
        self.assertFalse((target / 'libdof-plugin.so').is_symlink())

    def test_unsupported_combo_rejected_and_old_default_is_off(self):
        for mode in ['dp2', 'dp2-frida']:
            self.assets.validate_options(mode, 'on')
        for mode in ['none', 'frida']:
            with self.assertRaises(ValueError):
                self.assets.validate_options(mode, 'on')
            self.assets.validate_options(mode, 'off')
        with patch.dict(self.assets.os.environ, {}, clear=True), patch.object(self.assets, 'verify') as verify:
            self.assets.preflight()
            verify.assert_not_called()

    def abi_fixture(self):
        # Deliberately use a nonstandard file/VA delta to exercise PT_LOAD mapping.
        game = self.root / 'df_game_r'
        raw = bytearray(512)
        raw[:7] = b'\x7fELF\x01\x01\x01'
        struct.pack_into('<HHI', raw, 16, 2, 3, 1)
        struct.pack_into('<I', raw, 28, 52)
        struct.pack_into('<HHH', raw, 40, 52, 32, 1)
        struct.pack_into('<8I', raw, 52, 1, 256, 0x8048000, 0, 256, 256, 5, 4096)
        functions = []
        for index, name in enumerate(('MySQL_open', 'pickup', 'packet_send')):
            data = bytes([index + 1]) * 32
            raw[256 + index * 32:288 + index * 32] = data
            functions.append(dict(address=0x8048000 + index * 32, bytes=32,
                                  symbols=[name], sha256=hashlib.sha256(data).hexdigest()))
        game.write_bytes(raw)
        contract = self.root / 'abi.json'
        contract.write_text(json.dumps(dict(schema=1, architecture='elf32-i386-exec', functions=functions)))
        self.assets.GAME_ABI = contract
        return game

    def test_full_abi_rejects_changed_pickup_or_packet_with_intact_mysql(self):
        game = self.abi_fixture()
        original = game.read_bytes()
        self.assets.validate_game(game)
        for offset, symbol in ((300, 'pickup'), (332, 'packet_send')):
            changed = bytearray(original); changed[offset] ^= 1
            game.write_bytes(changed)
            with self.assertRaisesRegex(ValueError, symbol):
                self.assets.validate_game(game)
        self.assertEqual(game.read_bytes()[256:288], original[256:288])

    def test_abi_rejects_truncated_nonexecutable_and_ambiguous_segments(self):
        game = self.abi_fixture()
        original = game.read_bytes()
        invalid = [original[:100]]
        changed = bytearray(original)
        struct.pack_into('<I', changed, 52 + 24, 4)
        invalid.append(changed)
        changed = bytearray(original)
        struct.pack_into('<H', changed, 44, 2)
        changed[84:116] = changed[52:84]
        invalid.append(changed)
        for raw in invalid:
            game.write_bytes(raw)
            with self.assertRaises(ValueError):
                self.assets.validate_game(game)

    def test_custom_overlap_is_reported_without_writes(self):
        runtime = module('plugins')
        target = self.root / 'frida.js'
        content = b'operator code 2021458801 2021458802\xff'
        target.write_bytes(content)
        output = io.StringIO()
        with contextlib.redirect_stdout(output):
            runtime.check_frida_ownership(self.root / 'missing', target, 'dp2-frida')
        self.assertIn('未自动修改', output.getvalue())
        self.assertEqual(target.read_bytes(), content)
        output = io.StringIO()
        with contextlib.redirect_stdout(output):
            runtime.check_frida_ownership(self.root / 'missing', target, 'frida')
        self.assertEqual(output.getvalue(), '')

    @unittest.skipUnless(os.environ.get('DOF_NODE_EXE') or shutil.which('node'), 'Node.js required for real JavaScript handler test')
    def test_actual_frida_item_handler_ownership(self):
        subprocess.run([os.environ.get('DOF_NODE_EXE') or shutil.which('node'), 'tests/unit/frida-item-ownership.cjs'], cwd=ROOT, check=True)


    def test_disabling_removes_gateway_configuration_without_loading_resources(self):
        runtime = module('qk')
        conf = self.root / 'qk.conf'
        ready = self.root / 'qk-ready'
        conf.write_text('old supervisor config')
        ready.write_text('old marker')
        real_path = Path
        def mapped(value):
            return conf if str(value) == '/etc/supervisor/conf.d/dof-gateway.conf' else real_path(value)
        with patch.dict(runtime.os.environ, {'RAID_EXTENSION': 'off'}), patch.object(runtime, 'READY', ready), patch.object(runtime, 'Path', mapped):
            runtime.prepare(conf)
        self.assertFalse(conf.exists())
        self.assertFalse(ready.exists())


if __name__ == '__main__':
    unittest.main()
