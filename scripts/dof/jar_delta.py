"""Transfer changed ZIP members while reconstructing the exact verified JAR bytes.

Local prepare and Ubuntu apply helpers. No network, deployment or database access.
This is a build transfer format, unrelated to Dof's native client updates.
"""
import argparse
import hashlib
import json
from pathlib import Path
import zipfile


def digest(data):
    return hashlib.sha256(data).hexdigest()


def pieces(path):
    data = path.read_bytes()
    with zipfile.ZipFile(path) as archive:
        offsets = sorted({0, archive.start_dir, len(data)} | {e.header_offset for e in archive.infolist()})
    return data, [(a, b - a) for a, b in zip(offsets, offsets[1:])]


def prepare(base, target, directory):
    old, old_pieces = pieces(base)
    new, new_pieces = pieces(target)
    lookup = {digest(old[a:a+n]): (a, n) for a, n in old_pieces}
    directory.mkdir(parents=True, exist_ok=False)
    payload = bytearray()
    plan = []
    for offset, length in new_pieces:
        block = new[offset:offset+length]
        key = digest(block)
        if key in lookup:
            start, size = lookup[key]
            plan.append(['base', start, size])
        else:
            plan.append(['payload', len(payload), length])
            payload.extend(block)
    (directory / 'payload.bin').write_bytes(payload)
    manifest = {'format': 1, 'baseSha256': digest(old), 'targetSha256': digest(new),
                'payloadSha256': digest(payload), 'targetBytes': len(new), 'pieces': plan}
    (directory / 'delta.json').write_text(json.dumps(manifest))
    return {'targetBytes': len(new), 'transferBytes': len(payload), 'targetSha256': manifest['targetSha256']}


def apply(base, directory, output):
    if output.exists():
        raise ValueError('Output already exists; refusing overwrite')
    manifest = json.loads((directory / 'delta.json').read_text())
    old = base.read_bytes()
    payload = (directory / 'payload.bin').read_bytes()
    if manifest['format'] != 1 or digest(old) != manifest['baseSha256'] or digest(payload) != manifest['payloadSha256']:
        raise ValueError('Delta format or input checksum mismatch')
    size = manifest['targetBytes']
    if type(size) is not int or not 0 < size <= 512 * 1024**2:
        raise ValueError('Invalid target size')
    result = bytearray()
    for source, start, length in manifest['pieces']:
        if source not in ('base', 'payload') or type(start) is not int or type(length) is not int:
            raise ValueError('Invalid delta segment')
        data = old if source == 'base' else payload
        if start < 0 or length < 1 or start + length > len(data) or len(result) + length > size:
            raise ValueError('Delta segment outside source/target bounds')
        result.extend(data[start:start+length])
    if len(result) != size or digest(result) != manifest['targetSha256']:
        raise ValueError('Reconstructed JAR checksum mismatch; no output written')
    with output.open('xb') as stream:
        stream.write(result)
    return {'targetSha256': digest(result), 'targetBytes': size}


if __name__ == '__main__':
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('mode', choices=['prepare', 'apply'])
    parser.add_argument('--base', type=Path, required=True)
    parser.add_argument('--directory', type=Path, required=True)
    parser.add_argument('--target', type=Path, required=True)
    args = parser.parse_args()
    result = prepare(args.base, args.target, args.directory) if args.mode == 'prepare' else apply(args.base, args.directory, args.target)
    print(json.dumps(result))
