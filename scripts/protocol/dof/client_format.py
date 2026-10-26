"""Explicit offline conversion of a pinned native template to client format 2.

The normal validator accepts only new names. Legacy names are confined to this
import boundary. This codec does not certify the Windows consumer or publish a
package; the native-reader validation is a separate requirement.
"""
import base64
import re

from .config import Document, FormatError, Record, decode, encode

VERSION = 2
FILENAME = b'dof-client.dll'
VERSION_FIELD = b'token_dof_version'
CLIENT_FIELDS = (b'dffefrrr+(((((', b'token_dof+', b'token_dof-', b'token_dof+(((((')
LEGACY_FIELDS = dict(zip((b'dffefrrr+(((((', b'token_qingkong+', b'token_qingkong-',
                         b'token_qingkong+((((('), CLIENT_FIELDS[:4]))
TOOL_FIELDS = (b'dffefrrr+(((((KLHGJLKEJGL', b'dffefrrr+(((((KLHGJLKEJGL234',
               b'dffefrrr+(((((KLHGJLKEJGL5')


def invalid():
    return FormatError('Incompatible native client template, identity or format version')


def endpoint(value):
    try:
        raw = value.encode('ascii')
    except (AttributeError, UnicodeEncodeError):
        raise invalid() from None
    if not raw or len(raw) > 253:
        raise invalid()
    parts = value.split('.')
    if any(len(p) > 63 or not re.fullmatch(r'[A-Za-z0-9](?:[A-Za-z0-9-]*[A-Za-z0-9])?', p) for p in parts):
        raise invalid()
    if re.fullmatch(r'[0-9.]+', value):
        if len(parts) != 4 or any(int(p) > 255 or len(p) > 1 and p.startswith('0') for p in parts):
            raise invalid()
    return raw


def records(document, allowed):
    if not document.compressed or document.password_required:
        raise invalid()
    result = {}
    for record in document.records:
        if record.name not in allowed or record.name in result:
            raise invalid()
        kind = b'PNG' if record.name == CLIENT_FIELDS[0] else b'TXT'
        if record.kind != kind:
            raise invalid()
        result[record.name] = record
    return result


def payload(entries, key_name, payload_name, filename, public_der):
    try:
        from cryptography.hazmat.primitives import serialization
        from cryptography.hazmat.primitives.asymmetric import padding, rsa
        pem = base64.b64decode(entries[key_name].value, validate=True)
        if len(pem) > 4096:
            raise invalid()
        key = serialization.load_pem_private_key(pem, None)
        identity = serialization.load_der_public_key(public_der)
        if (not isinstance(key, rsa.RSAPrivateKey) or key.key_size != 2048
                or not isinstance(identity, rsa.RSAPublicKey)
                or key.public_key().public_numbers().n == identity.public_numbers().n):
            raise invalid()
        cipher = base64.b64decode(entries[payload_name].value, validate=True)
        if len(cipher) != 256:
            raise invalid()
        raw = key.decrypt(cipher, padding.PKCS1v15())
        if any(c < 32 or c > 126 for c in raw):
            raise invalid()
        fields = raw.split(b'*&')
        if len(fields) != 9 or fields[-1] or fields[1] != filename:
            raise invalid()
        endpoint(fields[0].decode('ascii'))
        return key, fields
    except ImportError:
        raise RuntimeError('The explicit client-template importer requires cryptography') from None
    except Exception:
        raise invalid() from None


def validate_container(raw):
    """Structural check usable on deployment hosts without crypto packages."""
    document = decode(raw)
    entries = records(document, CLIENT_FIELDS + (VERSION_FIELD,))
    if not set(CLIENT_FIELDS).issubset(entries):
        raise invalid()
    if entries[b'token_dof-'].value != FILENAME:
        raise invalid()
    return entries


def validate(raw, public_der):
    entries = validate_container(raw)
    _, fields = payload(entries, b'token_dof+', b'token_dof+(((((', FILENAME, public_der)
    return {'clientFormatVersion': VERSION, 'endpoint': fields[0].decode('ascii'),
            'records': len(entries), 'nativeClientAcceptance': False}


def import_legacy(raw, public_der, address):
    """Convert once; preserve the client key and six opaque protocol fields."""
    from cryptography.hazmat.primitives.asymmetric import padding
    address = endpoint(address)
    document = decode(raw)
    entries = records(document, tuple(LEGACY_FIELDS) + TOOL_FIELDS)
    if not set(LEGACY_FIELDS).issubset(entries):
        raise invalid()
    tools = set(entries).intersection(TOOL_FIELDS)
    if tools and tools != set(TOOL_FIELDS):
        raise invalid()
    if entries[b'token_qingkong-'].value != b'qingkong.dll':
        raise invalid()
    key, fields = payload(entries, b'token_qingkong+', b'token_qingkong+(((((', b'qingkong.dll', public_der)
    fields[0], fields[1] = address, FILENAME
    plain = b'*&'.join(fields)
    if len(plain) > 245:
        raise invalid()
    cipher = base64.b64encode(key.public_key().encrypt(plain, padding.PKCS1v15()))
    password = entries[TOOL_FIELDS[1]].value if tools else b''
    result = []
    for record in document.records:
        if record.name not in LEGACY_FIELDS:
            continue
        value = (cipher if record.name == b'token_qingkong+(((((' else
                 FILENAME if record.name == b'token_qingkong-' else record.value)
        if password and (password in value or password in plain):
            raise invalid()
        result.append(Record(LEGACY_FIELDS[record.name], value, record.kind))
    output = encode(Document(tuple(result), True, False))
    validate(output, public_der)
    return output
