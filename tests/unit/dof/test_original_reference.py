import os
from pathlib import Path
import tempfile
import unittest

from scripts.dof.original_reference import export, extract


class RejectionTests(unittest.TestCase):
    def test_unknown_gateway_cannot_create_output(self):
        with tempfile.TemporaryDirectory() as directory:
            source = Path(directory) / 'QK'
            source.write_bytes(b'not the pinned gateway')
            output = Path(directory) / 'out'
            with self.assertRaises(ValueError):
                export(source, output)
            self.assertFalse(output.exists())


@unittest.skipUnless(os.environ.get('DOF_NATIVE_SAMPLE_DIR'), 'Pinned original gateway required')
class OriginalSchemaTests(unittest.TestCase):
    def setUp(self):
        self.gateway = Path(os.environ['DOF_NATIVE_SAMPLE_DIR']) / 'QK'

    def test_preserves_original_schema_and_avoids_destructive_reinitialization(self):
        source = self.gateway.read_bytes()
        sql, report = extract(source)
        self.assertIn(b'CREATE DATABASE `qk`', sql)
        self.assertIn(b'`qingkong_config`', sql)
        self.assertIn(b'`qingkong_config_v2`', sql)
        self.assertNotIn(b'dof_native', sql)
        self.assertNotIn(b'DROP TABLE', sql)
        self.assertNotIn(b'IF NOT EXISTS', sql)
        self.assertEqual(len(report['tables']), 20)
        self.assertFalse(report['originalGuiLoginVerified'])
        self.assertEqual(source, self.gateway.read_bytes())

    def test_existing_reference_output_is_never_overwritten(self):
        with tempfile.TemporaryDirectory() as directory:
            output = Path(directory) / 'out'
            export(self.gateway, output)
            before = {p.name: p.read_bytes() for p in output.iterdir()}
            with self.assertRaises(FileExistsError):
                export(self.gateway, output)
            self.assertEqual(before, {p.name: p.read_bytes() for p in output.iterdir()})


if __name__ == '__main__':
    unittest.main()
