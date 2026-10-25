import importlib.util
from pathlib import Path
import struct
import sys
import unittest
import zlib

SPEC = importlib.util.spec_from_file_location('qingkong_native_config', Path(__file__).resolve().parents[3] / 'scripts/protocol/dof/config.py')
q = importlib.util.module_from_spec(SPEC)
sys.modules[SPEC.name] = q
SPEC.loader.exec_module(q)


class NativeConfigCodecTest(unittest.TestCase):
    def test_standard_rc4_and_native_empty_password_vectors(self):
        self.assertEqual(q.native_rc4(b'Plaintext', b'Key').hex(), 'bbf316e8d940af0ad3')
        self.assertEqual(q.native_rc4(bytes(8)).hex(), '0205070d0d171f28')

    def test_binary_unknown_records_duplicates_order_and_modes_survive(self):
        records = (q.Record(b'unknown', bytes(range(256)), b'NEW'),
                   q.Record(b'unknown', b'', b''), q.Record(b'', '中文'.encode('gbk'), b'TXT'))
        for compressed in [False, True]:
            for password in [b'', b'nonproduction-test-password']:
                d = q.Document(records, compressed, bool(password))
                self.assertEqual(q.decode(q.encode(d, password), password), d)

    def test_native_flags_header_and_wrong_password_rejected(self):
        d = q.Document((q.Record(b'a', b'b', b'TXT'),), True, False)
        wire = q.encode(d)
        for invalid in [b'', wire[:15], b'bad' + wire[3:], wire[:14] + b'\2\0' + wire[16:]]:
            with self.assertRaises(q.FormatError): q.decode(invalid)
        with self.assertRaises(q.FormatError): q.decode(wire, b'unexpected')
        protected = q.encode(q.Document(d.records, True, True), b'test')
        with self.assertRaises(q.FormatError): q.decode(protected, b'wrong')
        with self.assertRaises(q.FormatError): q.encode(d, b'wrong')

    def test_bad_lengths_trailing_stream_and_decompression_bomb_rejected(self):
        raw = q.FIELD_SEPARATOR.join([b'name', b'value', b'TXT'])
        for size, payload in [(len(raw)-1, zlib.compress(raw)), (len(raw)+1, zlib.compress(raw)),
                              (q.MAX_BYTES+1, zlib.compress(raw)),
                              (len(raw), zlib.compress(raw)+b'trailing'),
                              (len(raw), zlib.compress(raw)[:-1]),
                              (1, zlib.compress(b'A'*100000))]:
            body = struct.pack('<II', q.COMPRESSION_MAGIC, size) + payload
            wire = q.MAGIC + b'\1\0' + q.native_rc4(body)
            with self.assertRaises(q.FormatError): q.decode(wire)

    def test_separator_collision_and_invalid_record_rejected(self):
        for v in [q.FIELD_SEPARATOR, q.RECORD_SEPARATOR]:
            with self.assertRaises(q.FormatError):
                q.encode(q.Document((q.Record(b'a', v, b'TXT'),), True, False))
        with self.assertRaises(q.FormatError): q.decode(q.MAGIC + b'\0\0' + q.native_rc4(b'one-field'))
        with self.assertRaises(q.FormatError): q.native_rc4(b'a', b'bad\0key')

    def test_empty_native_document_uses_header_only_compression(self):
        d = q.Document((), True, False)
        wire = q.encode(d)
        self.assertEqual(len(wire), 24)
        self.assertEqual(q.decode(wire), d)
        self.assertEqual(q.native_rc4(wire[16:]), struct.pack('<II', q.COMPRESSION_MAGIC, 0))

    def test_metadata_never_contains_record_values_or_names(self):
        d = q.Document((q.Record(b'secret-name', b'private-value', b'PRIVATE'),), True, False)
        report = str(q.metadata(q.encode(d), d))
        for value in ['secret-name', 'private-value', 'PRIVATE']:
            self.assertNotIn(value, report)
        self.assertIn('nativeClientAcceptance', report)


if __name__ == '__main__': unittest.main()
