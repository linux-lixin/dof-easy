"""Synthetic rejection tests; real original-artifact evidence is recorded separately."""
import hashlib
import importlib.util
import json
from pathlib import Path
import struct
import tempfile
import unittest
from unittest.mock import patch
import zlib

ROOT = Path(__file__).resolve().parents[3]
spec = importlib.util.spec_from_file_location('compressed_audit', ROOT / 'scripts/dof/audit_compressed_script.py')
module = importlib.util.module_from_spec(spec)
spec.loader.exec_module(module)


class CompressedAuditTest(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory()
        self.addCleanup(self.temp.cleanup)
        self.root = Path(self.temp.name)
        self.client = self.root / 'client.dll'
        self.client.write_bytes(b'synthetic client identity, never native code')
        lock = self.root / 'metadata/dof/admin-baseline.lock.json'
        lock.parent.mkdir(parents=True)
        lock.write_text(json.dumps({'files': [{'role': 'client', 'path': 'ijl15.dll',
                                              'sha256': self.sha(self.client.read_bytes())}]}))
        self.scope = patch.object(module, 'ROOT', self.root)
        self.scope.start()
        self.addCleanup(self.scope.stop)
        self.source, self.candidate = self.root / 'source.pvf', self.root / 'candidate.pvf'
        crc = 0x12345678
        self.number = 12345
        header = struct.pack('<I', 36) + b'01234567-89ab-cdef-0123-456789abcdef' + struct.pack('<IIII', 0xf851, 16, crc, 0)
        original = struct.pack('<IIII', 0, 1, 0xffffffff, 7)
        native = []
        for word in struct.unpack('<IIII', original):
            value = word ^ crc ^ 0x81a79011
            native.append((((value >> 6) | (value << 26)) & 0xffffffff) ^ (self.number ^ crc))
        packed = zlib.compress(struct.pack('<IIII', *native), 6)
        self.marker = len(packed) ^ module.marker_constant()
        self.source.write_bytes(header + original + b'raw resources')
        self.good = header + packed + b'raw resources' + bytes(1024) + b'a' * 256
        self.candidate.write_bytes(self.good)

    @staticmethod
    def sha(data):
        return hashlib.sha256(data).hexdigest()

    def parameter(self, first, number):
        payload = f'{first}##{number}'.encode()
        encoded = ''.join(f'{b ^ 16:02X}' for b in payload)
        encoded += hashlib.md5(module.CONTEXT).hexdigest()[2:11].upper()
        path = self.root / 'parameters.private'
        path.write_text(encoded)
        return path

    def check(self, parameter=None):
        return module.audit(self.source, self.candidate, self.sha(self.source.read_bytes()),
                            self.sha(self.candidate.read_bytes()), self.client, parameter)

    def test_matching_numeric_parameter_and_marker_still_never_authorize_publish(self):
        result = self.check(self.parameter(self.marker, self.number))
        self.assertTrue(result['completeFileVerified'])
        self.assertTrue(result['pairedNumericParameterVerified'])
        self.assertTrue(result['compressedMarkerHypothesisMatched'])
        self.assertFalse(result['publishable'])
        self.assertFalse(result['nativeConsumerAcceptanceVerified'])
        self.assertNotIn('mask', result)
        self.assertNotIn('number', result)
        self.assertIsNone(self.check()['compressedMarkerHypothesisMatched'])

    def test_numeric_mismatch_rejected_and_mode_one_does_not_pass_hypothesis(self):
        with self.assertRaises(module.AuditError):
            self.check(self.parameter(self.marker, self.number + 1))
        self.assertFalse(self.check(self.parameter(1, self.number))['compressedMarkerHypothesisMatched'])

    def test_corruption_and_trailing_bytes_rejected_even_with_recomputed_cipher_hash(self):
        for at in (0, 44, 56, len(self.good) - 1281, len(self.good) - 1280, len(self.good) - 1):
            damaged = bytearray(self.good)
            damaged[at] ^= 1
            self.candidate.write_bytes(damaged)
            with self.assertRaises(module.AuditError):
                self.check()
        for data in (self.good[:-1], self.good + b'\0'):
            self.candidate.write_bytes(data)
            with self.assertRaises(module.AuditError):
                self.check()

    def test_wrong_client_and_expected_source_identity_are_rejected(self):
        with self.assertRaises(module.AuditError):
            module.audit(self.source, self.candidate, '0' * 64,
                         self.sha(self.good), self.client)
        self.client.write_bytes(b'wrong synthetic identity')
        with self.assertRaises(module.AuditError):
            self.check()

    def test_inflated_index_cannot_cross_declared_index_boundary(self):
        self.candidate.write_bytes(self.good[:56] + zlib.compress(b'x' * 131072)
                                   + bytes(1024) + b'a' * 256)
        with self.assertRaisesRegex(module.AuditError, 'declared output length'):
            self.check()


if __name__ == '__main__':
    unittest.main()
