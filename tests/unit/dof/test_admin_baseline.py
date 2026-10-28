import hashlib
import importlib.util
import json
from pathlib import Path
import tempfile
import unittest
from zipfile import ZipFile

ROOT = Path(__file__).resolve().parents[3]
spec = importlib.util.spec_from_file_location('admin_baseline', ROOT / 'scripts/dof/verify_admin_baseline.py')
baseline = importlib.util.module_from_spec(spec)
spec.loader.exec_module(baseline)


class BaselineTests(unittest.TestCase):
    def test_sources_are_verified_without_execution_or_modification(self):
        with tempfile.TemporaryDirectory() as folder:
            root = Path(folder)
            server, tool = root / 'server', root / 'tool'
            server.mkdir(); tool.mkdir()
            rows = []
            for role, name, data in [('server', 'QK', b'fixed-elf'), ('tool', 'tool.exe', b'fixed-pe'), ('client', 'launcher.exe', b'fixed-client')]:
                rows.append(dict(role=role, path=name, bytes=len(data), sha256=hashlib.sha256(data).hexdigest()))
                if role != 'client':
                    (server if role == 'server' else tool).joinpath(name).write_bytes(data)
            archive = root / 'client.zip'
            with ZipFile(archive, 'w') as z:
                z.writestr('客户端文件/launcher.exe', b'fixed-client')
            lock = dict(edition='qingkong-2.8-replacement', files=rows)
            self.assertTrue(baseline.verify(server, tool, archive, lock)['sourceVerified'])
            (server / 'QK').write_bytes(b'other-edition')
            report = baseline.verify(server, tool, archive, lock)
            self.assertFalse(report['sourceVerified'])
            self.assertEqual([r['path'] for r in report['files'] if not r['matched']], ['QK'])
            self.assertEqual((server / 'QK').read_bytes(), b'other-edition')

    def test_redirected_sources_and_duplicate_archive_names_are_rejected(self):
        with tempfile.TemporaryDirectory() as folder:
            root = Path(folder); (root / 'original').write_bytes(b'fixed')
            (root / 'QK').symlink_to(root / 'original')
            archive = root / 'client.zip'
            with ZipFile(archive, 'w'):
                pass
            lock = dict(edition='qingkong-2.8-replacement', files=[dict(role='server', path='QK', bytes=5, sha256=hashlib.sha256(b'fixed').hexdigest())])
            self.assertFalse(baseline.verify(root, root, archive, lock)['sourceVerified'])
            with ZipFile(archive, 'w') as z:
                z.writestr('duplicate', b'a')
                import warnings
                with warnings.catch_warnings():
                    warnings.simplefilter('ignore')
                    z.writestr('duplicate', b'b')
            with self.assertRaisesRegex(ValueError, 'Duplicate'):
                baseline.verify(root, root, archive, lock)

    def test_source_lock_reuses_the_existing_runtime_input_pins(self):
        lock = json.loads(baseline.LOCK.read_text())
        original = json.loads((ROOT / 'metadata/dof/derivation/inputs.lock.json').read_text())
        self.assertEqual(lock['edition'], original['profile'])
        self.assertEqual([{k: v for k, v in row.items() if k in ('path', 'bytes', 'sha256')} for row in lock['files'] if row['role'] == 'server'],
                         [{k: row[k] for k in ('path', 'bytes', 'sha256')} for row in original['files']])


if __name__ == '__main__':
    unittest.main()
