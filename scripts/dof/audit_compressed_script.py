"""Read-only, independent audit of a complete original compressed Script pair.

No Windows code is loaded or executed. Private parameter values and derived masks
are never returned. A successful file comparison does not authorize publication.
"""
import argparse
import array
import hashlib
import json
from pathlib import Path
import re
import struct
import sys
import zlib

ROOT = Path(__file__).resolve().parents[2]
CONTEXT = b'JKGEF*^^&%^&%^^\xb0\xa1\xce\xf7\xb0\xd1mdddnfrh'
BLOCK = 65536


class AuditError(ValueError):
    pass


def require(condition, message):
    if not condition:
        raise AuditError(message)


def marker_constant():
    # A hypothesis from the captured producer trace, NOT an accepted native mode.
    envelope = ''.join(f'{b ^ 16:02X}' for b in b'jmc')
    envelope += hashlib.md5(b'geah').hexdigest()[2:11].upper()
    return int(str(int(envelope, 16))[-8:])


def private_parameters(path):
    require(path.is_file() and not path.is_symlink() and path.stat().st_size <= 4096,
            'Private parameter input must be a bounded regular file')
    try:
        encoded = path.read_text(encoding='ascii').strip()
        tag = hashlib.md5(CONTEXT).hexdigest()[2:11].upper()
        require(re.fullmatch(r'[0-9A-F]{9,2057}', encoded) is not None
                and (len(encoded) - 9) % 2 == 0 and encoded[-9:] == tag,
                'Private parameter format or context mismatch')
        payload = bytes(b ^ 16 for b in bytes.fromhex(encoded[:-9]))
        require(re.fullmatch(rb'(?:0|[1-9][0-9]{0,9})##(?:0|[1-9][0-9]{0,7})', payload) is not None,
                'Unsupported private parameter fields')
        first, number = map(int, payload.split(b'##'))
        require(first <= 0xffffffff, 'Private parameter field out of range')
        return first, number
    except (UnicodeError, OSError):
        raise AuditError('Private parameter input could not be read') from None


def audit(source, candidate, source_sha, candidate_sha, client, parameter=None):
    require(re.fullmatch('[0-9a-f]{64}', source_sha) is not None
            and re.fullmatch('[0-9a-f]{64}', candidate_sha) is not None, 'Expected SHA-256 is required')
    lock = json.loads((ROOT / 'metadata/dof/admin-baseline.lock.json').read_text())
    client_pin = next(x['sha256'] for x in lock['files'] if x['role'] == 'client' and x['path'] == 'ijl15.dll')
    require(client.is_file() and not client.is_symlink(), 'Pinned client must be a regular file')
    require(hashlib.sha256(client.read_bytes()).hexdigest() == client_pin, 'Client source version mismatch')
    for path in (source, candidate):
        require(path.is_file() and not path.is_symlink() and 60 <= path.stat().st_size <= 2 * 1024**3,
                'Script input must be a bounded regular file')
    identities = [(p.stat().st_ino, p.stat().st_size, p.stat().st_mtime_ns) for p in (source, candidate)]
    original_hash, artifact_hash = hashlib.sha256(), hashlib.sha256()
    mask, decoded, pending, supplied, compressed_bytes = None, 0, b'', 0, 0
    with source.open('rb') as original, candidate.open('rb') as wire:
        header, native_header = original.read(56), wire.read(56)
        require(header == native_header and len(header) == 56, 'Script headers differ')
        guid = header[4:40]
        index_bytes, crc, file_count = struct.unpack_from('<III', header, 44)
        require(struct.unpack_from('<I', header)[0] == 36 and struct.unpack_from('<I', header, 40)[0] == 0xf851
                and re.fullmatch(rb'[0-9a-fA-F]{8}-(?:[0-9a-fA-F]{4}-){3}[0-9a-fA-F]{12}', guid) is not None
                and 4 <= index_bytes <= 256 * 1024**2 and index_bytes % 4 == 0
                and index_bytes <= identities[0][1] - 56 and file_count <= index_bytes // 20,
                'Unsupported standard PVF header')
        original_hash.update(header)
        artifact_hash.update(native_header)
        inflater = zlib.decompressobj()
        while not inflater.eof:
            block = wire.read(BLOCK)
            require(bool(block), 'Compressed index truncated')
            supplied += len(block)
            artifact_hash.update(block)
            while True:
                try:
                    plain = inflater.decompress(block, BLOCK)
                except zlib.error:
                    raise AuditError('Compressed index damaged or requires a dictionary') from None
                require(bool(plain) or inflater.eof or len(inflater.unconsumed_tail) < len(block),
                        'Inflater made no progress')
                decoded += len(plain)
                require(decoded <= index_bytes, 'Compressed index exceeds declared output length')
                pending += plain
                aligned = len(pending) & ~3
                if aligned:
                    expected = original.read(aligned)
                    require(len(expected) == aligned, 'Original index truncated')
                    original_hash.update(expected)
                    native_words = array.array('I', pending[:aligned])
                    source_words = array.array('I', expected)
                    if sys.byteorder != 'little':
                        native_words.byteswap()
                        source_words.byteswap()
                    for a, b in zip(native_words, source_words):
                        value = b ^ crc ^ 0x81a79011
                        rotated = ((value >> 6) | (value << 26)) & 0xffffffff
                        if mask is None:
                            mask = a ^ rotated
                        require(a == rotated ^ mask, 'Compressed index differs from paired source')
                    pending = pending[aligned:]
                consumed = supplied - (len(inflater.unused_data) if inflater.eof else len(inflater.unconsumed_tail))
                require(consumed <= index_bytes + index_bytes // 100 + BLOCK,
                        'Compressed index exceeds input bound')
                if inflater.eof or not inflater.unconsumed_tail:
                    break
                block = inflater.unconsumed_tail
            require(bool(plain) or inflater.eof or not inflater.unconsumed_tail, 'Inflater made no progress')
        require(decoded == index_bytes and not pending, 'Compressed index length mismatch')
        compressed_bytes = supplied - len(inflater.unused_data)
        unread = inflater.unused_data

        def read_candidate(count):
            nonlocal unread
            prefix, unread = unread[:count], unread[count:]
            suffix = wire.read(count - len(prefix))
            artifact_hash.update(suffix)
            return prefix + suffix

        remaining = identities[0][1] - 56 - index_bytes
        resource_bytes = remaining
        while remaining:
            size = min(remaining, BLOCK)
            data = original.read(size)
            require(len(data) == size and read_candidate(size) == data, 'Resource bytes differ')
            original_hash.update(data)
            remaining -= size
        trailer = read_candidate(1280)
        require(len(trailer) == 1280 and trailer[:1024] == bytes(1024)
                and all(97 <= b <= 122 for b in trailer[1024:]), 'Script trailer invalid')
        require(original.read(1) == b'' and read_candidate(1) == b'', 'Script has unexpected trailing bytes')
    require(original_hash.hexdigest() == source_sha, 'Original source SHA-256 mismatch')
    require(artifact_hash.hexdigest() == candidate_sha, 'Compressed candidate SHA-256 mismatch')
    require(all((p.stat().st_ino, p.stat().st_size, p.stat().st_mtime_ns) == before
                for p, before in zip((source, candidate), identities)), 'Script input changed during audit')
    result = {'schema': 1, 'completeFileVerified': True, 'sourceSHA256': source_sha,
              'candidateSHA256': candidate_sha, 'clientSourceSHA256': client_pin,
              'indexBytes': index_bytes, 'compressedIndexBytes': compressed_bytes,
              'resourceBytes': resource_bytes, 'trailerVerified': True,
              'privateParameterSupplied': parameter is not None, 'pairedNumericParameterVerified': False,
              'compressedMarkerHypothesisMatched': None, 'originalToolEquivalenceVerified': False,
              'nativeConsumerAcceptanceVerified': False, 'publishable': False, 'completeReplacement': False}
    if parameter is not None:
        first, number = private_parameters(parameter)
        require(number ^ crc == mask, 'Private numeric parameter does not match compressed artifact')
        result['pairedNumericParameterVerified'] = True
        result['compressedMarkerHypothesisMatched'] = first == (compressed_bytes ^ marker_constant())
    return result


if __name__ == '__main__':
    parser = argparse.ArgumentParser(description=__doc__)
    for name in ('source', 'candidate', 'client'):
        parser.add_argument('--' + name, type=Path, required=True)
    parser.add_argument('--source-sha256', required=True)
    parser.add_argument('--candidate-sha256', required=True)
    parser.add_argument('--parameter-file', type=Path)
    args = parser.parse_args()
    try:
        result = audit(args.source, args.candidate, args.source_sha256, args.candidate_sha256,
                       args.client, args.parameter_file)
    except AuditError as error:
        parser.exit(1, str(error) + '; inputs were not modified.\n')
    except OSError:
        # No raw paths, payloads, numeric masks or file content in CLI failures.
        parser.exit(1, 'Compressed Script audit failed; inputs were not modified.\n')
    print(json.dumps(result, indent=2))
