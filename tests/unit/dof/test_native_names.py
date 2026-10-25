import hashlib
from pathlib import Path
import tempfile
import unittest

from scripts.dof.audit_native_names import inspect_bytes, inspect_file


class NativeNameAudit(unittest.TestCase):
    def test_both_encodings_are_reported_without_leaking_neighbor_values(self):
        data = b'secret-value\0qingkong.dll\0' + 'token_qingkong'.encode('utf-16le')
        report = inspect_bytes(data)
        self.assertEqual(report['matches']['qingkong.dll']['ascii'], [13])
        self.assertTrue(report['matches']['token_qingkong']['utf-16le'])
        self.assertNotIn('secret-value', repr(report))

    def test_pins_and_unchanged_source(self):
        with tempfile.TemporaryDirectory() as tmp:
            path = Path(tmp) / 'input'
            source = b'qingkong.dll\0'
            path.write_bytes(source)
            expected = {'bytes': len(source), 'sha256': hashlib.sha256(source).hexdigest()}
            report = inspect_file(path, expected)
            self.assertTrue(report['pinned'])
            self.assertEqual(path.read_bytes(), source)
            with self.assertRaisesRegex(ValueError, 'pinned'):
                inspect_file(path, dict(expected, bytes=len(source) + 1))
            link = Path(tmp) / 'link'
            link.symlink_to(path)
            with self.assertRaisesRegex(ValueError, 'regular'):
                inspect_file(link, expected)

    def test_malformed_executables_do_not_become_successful_reports(self):
        for data in (b'MZ', b'MZ' + bytes(100), b'\x7fELF' + bytes(80)):
            with self.assertRaises(ValueError):
                inspect_bytes(data)


if __name__ == '__main__':
    unittest.main()
