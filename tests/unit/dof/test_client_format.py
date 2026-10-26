"""New client format validation and explicit import without legacy fallback."""
import base64
import unittest
from cryptography.hazmat.primitives import serialization
from cryptography.hazmat.primitives.asymmetric import padding, rsa
from scripts.protocol.dof import client_format as client
from scripts.protocol.dof.config import Document, Record, FormatError, decode, encode


class ClientFormatTests(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.key = rsa.generate_private_key(public_exponent=65537, key_size=2048)
        cls.server = rsa.generate_private_key(public_exponent=65537, key_size=2048)
        cls.public = cls.server.public_key().public_bytes(
            serialization.Encoding.DER, serialization.PublicFormat.SubjectPublicKeyInfo)

    def fixture(self):
        pem = self.key.private_bytes(serialization.Encoding.PEM,
                                    serialization.PrivateFormat.TraditionalOpenSSL,
                                    serialization.NoEncryption())
        cipher = self.key.public_key().encrypt(
            b'127.0.0.1*&qingkong.dll*&opaque-1*&opaque-2*&opaque-3*&opaque-4*&opaque-5*&opaque-6*&',
            padding.PKCS1v15())
        return encode(Document((Record(b'dffefrrr+(((((', b'GIF89a-fixture', b'PNG'),
                                Record(b'token_qingkong+', base64.b64encode(pem), b'TXT'),
                                Record(b'token_qingkong-', b'qingkong.dll', b'TXT'),
                                Record(b'token_qingkong+(((((', base64.b64encode(cipher), b'TXT')), True, False))

    def test_import_updates_encrypted_filename_and_preserves_key_and_opaque_fields(self):
        original = self.fixture()
        upgraded = client.import_legacy(original, self.public, '192.0.2.10')
        result = client.validate(upgraded, self.public)
        self.assertEqual(result, dict(clientFormatVersion=2, endpoint='192.0.2.10',
                                      records=5, nativeClientAcceptance=False))
        entries = {r.name:r for r in decode(upgraded).records}
        self.assertEqual(set(entries), set(client.CLIENT_FIELDS))
        self.assertEqual(entries[b'token_dof+'].value, decode(original).records[1].value)
        fields = self.key.decrypt(base64.b64decode(entries[b'token_dof+((((('].value),
                                  padding.PKCS1v15()).split(b'*&')
        self.assertEqual(fields, [b'192.0.2.10', b'dof-client.dll', *[f'opaque-{i}'.encode() for i in range(1,7)], b''])
        with self.assertRaises(FormatError): client.validate(original, self.public)

    def test_missing_wrong_and_duplicate_versions_are_rejected(self):
        raw = client.import_legacy(self.fixture(), self.public, '127.0.0.1')
        doc = decode(raw)
        for records in (doc.records[:-1], doc.records[:-1]+(Record(client.VERSION_FIELD,b'1',b'TXT'),),
                        doc.records+(doc.records[-1],)):
            with self.assertRaises(FormatError):
                client.validate(encode(Document(records, True, False)), self.public)
        same = self.key.public_key().public_bytes(serialization.Encoding.DER,
                                                  serialization.PublicFormat.SubjectPublicKeyInfo)
        with self.assertRaises(FormatError): client.validate(raw, same)

    def test_unknown_or_partial_tool_records_and_bad_endpoints_fail_without_leaking_data(self):
        doc = decode(self.fixture())
        for record in (Record(b'unknown',b'SECRET-VALUE',b'TXT'),
                       Record(client.TOOL_FIELDS[1],b'SECRET-PASSWORD',b'TXT')):
            with self.assertRaises(FormatError) as caught:
                client.import_legacy(encode(Document(doc.records+(record,),True,False)), self.public, '127.0.0.1')
            self.assertNotIn('SECRET', str(caught.exception))
        for address in ('', '127.00.0.1', '300.0.0.1', 'host*&injected', 'host/path', 'host..name'):
            with self.assertRaises(FormatError): client.import_legacy(self.fixture(), self.public, address)


if __name__ == '__main__': unittest.main()
