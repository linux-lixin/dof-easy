"""Publication boundary for external build assets; no network or binaries run."""
import hashlib
import importlib.util
import io
import json
from pathlib import Path
import tempfile
import unittest
from unittest.mock import patch

ROOT = Path(__file__).resolve().parents[2]
SPEC = importlib.util.spec_from_file_location('prepare_source', ROOT / 'scripts/container/resources.py')
source = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(source)


class SourcePreparation(unittest.TestCase):
    def test_only_level_packages_need_the_pinned_release(self):
        base = 'https://github.com/linux-lixin/dof-easy/releases/download/resources-v1/'
        rows = json.loads((ROOT / 'metadata/levels.lock.json').read_text())['levels']
        self.assertEqual(len(rows), 13)
        for row in rows:
            self.assertEqual(row['url'], base + row['id'])
        self.assertFalse((ROOT / 'metadata/vipai86-inputs.lock.json').exists())

    def test_shared_build_inputs_are_available_without_network(self):
        with patch('urllib.request.urlopen', side_effect=AssertionError('Unexpected network access')):
            self.assertEqual(source.verify_build_inputs(), 422)

    def test_missing_or_changed_repo_input_is_rejected_without_restore(self):
        with tempfile.TemporaryDirectory() as directory, patch.object(source, 'ROOT', Path(directory)):
            root = Path(directory)
            (root / 'metadata').mkdir()
            lock = {'schema': 1, 'storage': 'git', 'files': [dict(self.row(b'valid'), path='asset')]}
            (root / 'metadata/build-inputs.lock.json').write_text(json.dumps(lock))
            with self.assertRaisesRegex(ValueError, 'Missing/non-regular'):
                source.verify_build_inputs()
            target = root / 'asset'
            target.write_bytes(b'wrong')
            with self.assertRaisesRegex(ValueError, 'Hash/size mismatch'):
                source.verify_build_inputs()
            self.assertEqual(target.read_bytes(), b'wrong')

    def test_published_plugin_has_no_static_database_password(self):
        config = json.loads((ROOT / 'services/game/resources/plugins/frida/frida_config.json').read_text())
        self.assertEqual(config['db_config']['password'], '')

    def row(self, data):
        return {'bytes': len(data), 'sha256': hashlib.sha256(data).hexdigest()}

    def test_verified_complete_input_is_published(self):
        with tempfile.TemporaryDirectory() as directory:
            target = Path(directory) / 'asset'
            source.write_verified(io.BytesIO(b'valid'), target, self.row(b'valid'))
            self.assertEqual(target.read_bytes(), b'valid')
            self.assertEqual(list(target.parent.glob('.download-*')), [])

    def test_corrupt_or_truncated_input_never_published(self):
        for data in (b'wrong', b'vali'):
            with self.subTest(data=data), tempfile.TemporaryDirectory() as directory:
                target = Path(directory) / 'asset'
                with self.assertRaises(ValueError):
                    source.write_verified(io.BytesIO(data), target, self.row(b'valid'))
                self.assertFalse(target.exists())
                self.assertEqual(list(target.parent.iterdir()), [])

    def test_oversized_input_never_published(self):
        with tempfile.TemporaryDirectory() as directory:
            target = Path(directory) / 'asset'
            with self.assertRaises(ValueError):
                source.write_verified(io.BytesIO(b'valid extra'), target, self.row(b'valid'))
            self.assertFalse(target.exists())

    def test_existing_operator_file_is_not_overwritten(self):
        with tempfile.TemporaryDirectory() as directory:
            target = Path(directory) / 'asset'
            target.write_bytes(b'operator data')
            with self.assertRaises(FileExistsError):
                source.write_verified(io.BytesIO(b'valid'), target, self.row(b'valid'))
            self.assertEqual(target.read_bytes(), b'operator data')

    def test_archive_paths_stay_inside_checkout(self):
        with tempfile.TemporaryDirectory() as directory, patch.object(source, 'ROOT', Path(directory)):
            for name in ('../outside', '/absolute', 'C:/outside', 'folder\\outside'):
                with self.subTest(name=name), self.assertRaises(ValueError):
                    source.destination(name)
            self.assertEqual(source.destination('services/game/asset'), Path(directory) / 'services/game/asset')
