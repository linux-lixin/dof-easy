import importlib.util
import os
from pathlib import Path
import sys
import tarfile
import tempfile
import unittest
from unittest.mock import patch

SCRIPTS = Path(__file__).resolve().parents[3] / 'scripts/dof'
sys.path.insert(0, str(SCRIPTS))
spec = importlib.util.spec_from_file_location('admin_restore_drill', SCRIPTS / 'admin_restore_drill.py')
drill = importlib.util.module_from_spec(spec)
spec.loader.exec_module(drill)


def member(name, kind=tarfile.REGTYPE):
    item = tarfile.TarInfo(name)
    item.type = kind
    item.mode = 0o700 if kind == tarfile.DIRTYPE else 0o600
    return item


class RestoreDrillTest(unittest.TestCase):
    def test_unsafe_archive_is_rejected_before_any_extraction(self):
        for bad in [member('../outside'), member('/outside'), member('admin-data/../outside'),
                    member('admin-data\\outside'), member('admin-data/link', tarfile.SYMTYPE),
                    member('admin-data/link', tarfile.LNKTYPE), member('admin-data/pipe', tarfile.FIFOTYPE),
                    member('admin-data/a/b'), member('admin-data//file')]:
            with self.subTest(name=bad.name, kind=bad.type), self.assertRaises(ValueError):
                drill.validate_members([member('admin-data', tarfile.DIRTYPE), bad])
        root = member('admin-data', tarfile.DIRTYPE)
        for entries in [[root, root], [root, member('admin-data/a'), member('admin-data/a/b')],
                        [member('admin-data')]]:
            with self.assertRaises(ValueError):
                drill.validate_members(entries)

    @unittest.skipUnless(os.name == 'posix', 'Real ownership/mode restore requires POSIX; no simulated pass')
    def test_real_restore_preserves_bytes_and_metadata_without_touching_original(self):
        with tempfile.TemporaryDirectory() as tmp:
            root = Path(tmp)
            live = root / 'live'
            live.mkdir()
            (live / 'nested').mkdir()
            (live / 'nested/state').write_bytes('真实配置\x00checkpoint'.encode())
            (live / 'nested/state').chmod(0o600)
            archive = root / 'restore.tgz'
            with tarfile.open(archive, 'w:gz') as tar:
                tar.add(live, arcname='admin-data')
            pinned = drill.digest(archive)
            with self.assertRaisesRegex(ValueError, 'overlaps'):
                drill.restore(archive, live / 'drill', pinned, live)
            result = drill.restore(archive, root / 'drill', pinned, live)
            self.assertTrue(result['fullBytesAndMetadataVerified'])
            self.assertFalse(result['databaseRestoreVerified'])
            self.assertTrue(drill.matches(live, archive))
            with self.assertRaisesRegex(ValueError, 'must not exist'):
                drill.restore(archive, root / 'drill', pinned, live)

    @unittest.skipUnless(os.name == 'posix', 'Real restore guards require POSIX')
    def test_corruption_capacity_and_wrong_digest_create_no_destination(self):
        with tempfile.TemporaryDirectory() as tmp:
            root = Path(tmp)
            archive = root / 'restore.tgz'
            live = root / 'live'
            live.mkdir()
            with tarfile.open(archive, 'w:gz') as tar:
                tar.addfile(member('admin-data', tarfile.DIRTYPE))
            destination = root / 'drill'
            with self.assertRaisesRegex(ValueError, 'digest'):
                drill.restore(archive, destination, '0' * 64, live)
            with patch.object(drill.shutil, 'disk_usage', return_value=type('Space', (), {'free': drill.RESERVE})()):
                with self.assertRaisesRegex(ValueError, 'Insufficient'):
                    drill.restore(archive, destination, drill.digest(archive), live)
            self.assertFalse(destination.exists())
            archive.write_bytes(archive.read_bytes()[:-5])
            with self.assertRaises((EOFError, tarfile.TarError, ValueError)):
                drill.restore(archive, destination, drill.digest(archive), live)


if __name__ == '__main__':
    unittest.main()
