"""Publication boundary for external build assets; no network or binaries run."""
import hashlib
import importlib.util
import io
from pathlib import Path
import tempfile
import unittest
from unittest.mock import patch

ROOT = Path(__file__).resolve().parents[2]
SPEC = importlib.util.spec_from_file_location('prepare_source', ROOT / 'scripts/vipai86/prepare_source.py')
source = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(source)


class SourcePreparation(unittest.TestCase):
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
