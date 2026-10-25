import json
from pathlib import Path
import tempfile
import unittest
from unittest.mock import patch

from scripts.dof import stage_server_probe as probe


class ServerProbeStaging(unittest.TestCase):
    def setUp(self):
        temporary = tempfile.TemporaryDirectory()
        self.addCleanup(temporary.cleanup)
        self.root = Path(temporary.name)
        self.source = self.root / 'source'
        self.source.mkdir()
        self.sample = self.source / 'sample'
        self.sample.write_bytes(b'pinned native fixture')
        self.output = self.root / 'output'
        self.lock = self.root / 'lock.json'
        self.lock.write_text(json.dumps({'files': {
            'sample': {'sha256': probe.digest(self.sample), 'mode': 0o700}}}))
        patcher = patch.object(probe, 'LOCK', self.lock)
        patcher.start()
        self.addCleanup(patcher.stop)

    def test_only_pinned_assets_and_tracked_scripts_are_published(self):
        (self.source / 'privatekey.pem').write_bytes(b'not a publication input')
        record = probe.stage(self.source, self.output)
        self.assertEqual((self.output / 'sample').read_bytes(), b'pinned native fixture')
        self.assertEqual(self.sample.read_bytes(), b'pinned native fixture')
        self.assertFalse((self.output / 'privatekey.pem').exists())
        self.assertFalse(record['releaseReady'])
        for name, expected in record['scripts'].items():
            self.assertEqual(probe.digest(self.output / name), expected)

    def test_reuse_preserves_source_bytes_and_read_only_mode(self):
        first=self.root/'first'
        probe.stage(self.source,first)
        mode=(first/'sample').stat().st_mode
        second=self.root/'second'
        record=probe.stage(first,second,reuse_assets=True)
        self.assertTrue(record['reusedReadOnlyAssetLinks'])
        self.assertEqual((first/'sample').stat().st_ino,(second/'sample').stat().st_ino)
        self.assertEqual((first/'sample').stat().st_mode,mode)
        self.assertEqual((first/'sample').read_bytes(),b'pinned native fixture')

    def test_mismatched_binary_cannot_create_output(self):
        self.sample.write_bytes(b'other version')
        with self.assertRaisesRegex(ValueError, 'hash mismatch'):
            probe.stage(self.source, self.output)
        self.assertFalse(self.output.exists())

    def test_source_link_is_rejected(self):
        real = self.source / 'real'
        self.sample.rename(real)
        self.sample.symlink_to(real)
        with self.assertRaisesRegex(ValueError, 'regular local'):
            probe.stage(self.source, self.output)
        self.assertFalse(self.output.exists())

    def test_existing_output_is_preserved(self):
        self.output.mkdir()
        marker = self.output / 'keep'
        marker.write_bytes(b'original')
        with self.assertRaises(ValueError):
            probe.stage(self.source, self.output)
        self.assertEqual(marker.read_bytes(), b'original')

    def test_interrupted_copy_leaves_no_publishable_partial_tree(self):
        with patch.object(probe.shutil, 'copyfile', side_effect=OSError('copy interrupted')):
            with self.assertRaises(OSError):
                probe.stage(self.source, self.output)
        self.assertFalse(self.output.exists())
        self.assertFalse(list(self.root.glob('.dof-server-probe-*')))

    def test_traversal_and_source_output_overlap_are_rejected(self):
        with self.assertRaises(ValueError):
            probe.regular(self.source, '../lock.json')
        with self.assertRaises(ValueError):
            probe.stage(self.source, self.source / 'output')


if __name__ == '__main__':
    unittest.main()
