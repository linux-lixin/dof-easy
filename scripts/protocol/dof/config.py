#!/usr/bin/env python3
"""Lossless codec for the pinned Dof 2.8 native configuration container.

Offline format adapter only: does not publish packages, connect to a database,
regenerate identities, or claim that a changed client configuration was accepted.
Values may contain credentials. The CLI prints metadata, never record values.
"""
from __future__ import annotations

import argparse
from dataclasses import dataclass
import hashlib
import json
from pathlib import Path
import struct
import zlib

MAGIC = bytes.fromhex('c005d4eba402d0adc5ce06cece05')
COMPRESSION_MAGIC = 0x033E0F0D
VERSION = '花枝5.0'.encode('gbk')
FIELD_SEPARATOR = bytes.fromhex('cdb5c4e3c2e9b1d4d4b4c2ebb0a1a3acb2bbbbe1d7d4bcbac8a5d0b4c2efa3bf') + VERSION
RECORD_SEPARATOR = bytes.fromhex('0f74d3848a') + VERSION
MAX_BYTES = 16 * 1024 * 1024
MAX_RECORDS = 4096


class FormatError(ValueError):
    """Malformed, unsupported or ambiguous native data; contains no input values."""


def native_rc4(data: bytes, key: bytes = b'') -> bytes:
    """Native algorithm 2, including its empty-key identity-S-box behavior.

    Confirmed against ijl15.dll 10438890/10438960, not a recommendation for new
    cryptographic designs. RC4 with an empty key is obfuscation, not secrecy.
    """
    if b'\0' in key:
        raise FormatError('Native password cannot contain NUL')
    state = list(range(256))
    j = 0
    if key:
        for i in range(256):
            j = (j + state[i] + key[i % len(key)]) & 255
            state[i], state[j] = state[j], state[i]
    i = j = 0
    out = bytearray(len(data))
    for n, value in enumerate(data):
        i = (i + 1) & 255
        j = (j + state[i]) & 255
        state[i], state[j] = state[j], state[i]
        out[n] = value ^ state[(state[i] + state[j]) & 255]
    return bytes(out)


@dataclass(frozen=True)
class Record:
    name: bytes
    value: bytes
    kind: bytes


@dataclass(frozen=True)
class Document:
    records: tuple[Record, ...]
    compressed: bool
    password_required: bool


def _bounded(data: bytes) -> None:
    if len(data) > MAX_BYTES:
        raise FormatError('Native configuration exceeds size limit')


def decode(data: bytes, password: bytes = b'') -> Document:
    _bounded(data)
    if len(data) < len(MAGIC) + 2 or data[:14] != MAGIC:
        raise FormatError('Unsupported native container header')
    compressed, required = data[14:16]
    if compressed not in (0, 1) or required not in (0, 1):
        raise FormatError('Unsupported native container flags')
    if bool(required) != bool(password):
        raise FormatError('Native password mode mismatch')
    body = native_rc4(data[16:], password)
    if compressed:
        if len(body) < 8:
            raise FormatError('Truncated compression header')
        signature, length = struct.unpack_from('<II', body)
        if signature != COMPRESSION_MAGIC or length > MAX_BYTES:
            raise FormatError('Invalid compression header or password')
        if length == 0:
            if len(body) != 8:
                raise FormatError('Invalid empty native compression stream')
            return Document((), True, bool(required))
        try:
            inflater = zlib.decompressobj()
            raw = inflater.decompress(body[8:], length + 1)
        except zlib.error as e:
            raise FormatError('Invalid compressed native data') from e
        if (len(raw) != length or not inflater.eof or inflater.unused_data
                or inflater.unconsumed_tail):
            raise FormatError('Compressed native length or stream mismatch')
        body = raw
    _bounded(body)
    if not body:
        return Document((), bool(compressed), bool(required))
    chunks = body.split(RECORD_SEPARATOR)
    if len(chunks) > MAX_RECORDS:
        raise FormatError('Too many native records')
    records = []
    for chunk in chunks:
        fields = chunk.split(FIELD_SEPARATOR)
        if len(fields) != 3:
            raise FormatError('Invalid native record or password')
        records.append(Record(*fields))
    # Do not decode values as text or normalize unknown names, order or kinds.
    return Document(tuple(records), bool(compressed), bool(required))


def encode(document: Document, password: bytes = b'') -> bytes:
    if document.password_required != bool(password):
        raise FormatError('Native password mode mismatch')
    if len(document.records) > MAX_RECORDS:
        raise FormatError('Too many native records')
    chunks = []
    size = 0
    for record in document.records:
        fields = (record.name, record.value, record.kind)
        for field in fields:
            if not isinstance(field, bytes):
                raise FormatError('Native record fields must be bytes')
            if FIELD_SEPARATOR in field or RECORD_SEPARATOR in field:
                raise FormatError('Native field contains an unescaped delimiter')
        chunk = FIELD_SEPARATOR.join(fields)
        size += len(chunk) + (len(RECORD_SEPARATOR) if chunks else 0)
        if size > MAX_BYTES:
            raise FormatError('Native configuration exceeds size limit')
        chunks.append(chunk)
    body = RECORD_SEPARATOR.join(chunks)
    if document.compressed:
        body = struct.pack('<II', COMPRESSION_MAGIC, len(body)) + (zlib.compress(body) if body else b'')
    result = MAGIC + bytes((document.compressed, document.password_required)) + native_rc4(body, password)
    _bounded(result)
    return result


def metadata(data: bytes, document: Document) -> dict:
    # Names may also be user-supplied. Do not print names, values or hashes of
    # individual low-entropy credentials. Whole-file hashes identify artifacts.
    return {'bytes': len(data), 'sha256': hashlib.sha256(data).hexdigest(),
            'compressed': document.compressed, 'passwordRequired': document.password_required,
            'records': [{'index': i, 'nameBytes': len(r.name), 'valueBytes': len(r.value),
                         'kindBytes': len(r.kind)} for i, r in enumerate(document.records)],
            'nativeClientAcceptance': False}


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('input', type=Path)
    args = parser.parse_args()
    if args.input.stat().st_size > MAX_BYTES:
        raise FormatError('Native configuration exceeds size limit')
    data = args.input.read_bytes()
    document = decode(data)
    regenerated = encode(document)
    report = metadata(data, document)
    report['semanticRoundTrip'] = decode(regenerated) == document
    report['byteIdenticalRegeneration'] = regenerated == data
    print(json.dumps(report, ensure_ascii=False, indent=2))


if __name__ == '__main__':
    main()
