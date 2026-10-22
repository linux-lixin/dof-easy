#!/usr/bin/env python3
"""Validate checked-in build inputs and atomically publish verified level downloads."""
import argparse
import hashlib
import json
import os
from pathlib import Path, PurePosixPath
import tempfile

ROOT = Path(__file__).resolve().parents[2]


def digest(path):
    h = hashlib.sha256()
    with path.open('rb') as stream:
        for block in iter(lambda: stream.read(1024 * 1024), b''):
            h.update(block)
    return h.hexdigest()


def verify(path, row):
    if path.is_symlink() or not path.is_file():
        raise ValueError('Missing/non-regular input: ' + str(path))
    if path.stat().st_size != row.get('size', row.get('bytes')) or digest(path) != row['sha256']:
        raise ValueError('Hash/size mismatch: ' + str(path))


def destination(name):
    parts = PurePosixPath(name).parts
    if not parts or '\\' in name or ':' in name or name.startswith('/') or '..' in parts:
        raise ValueError('Unsafe asset path')
    path = ROOT.joinpath(*parts)
    path.resolve().relative_to(ROOT.resolve())
    for parent in (path, *path.parents):
        if parent == ROOT:
            break
        if parent.is_symlink():
            raise ValueError('Symlink in asset path')
    return path


def write_verified(stream, target, row):
    target.parent.mkdir(parents=True, exist_ok=True)
    fd, name = tempfile.mkstemp(prefix='.download-', dir=str(target.parent))
    temporary = Path(name)
    try:
        remaining = row.get('size', row.get('bytes'))
        with os.fdopen(fd, 'wb') as output:
            while True:
                block = stream.read(min(1024 * 1024, remaining + 1))
                if not block:
                    break
                remaining -= len(block)
                if remaining < 0:
                    raise ValueError('Input exceeds locked size')
                output.write(block)
        verify(temporary, row)
        # Publish a complete file atomically without overwriting existing files.
        if os.name == 'nt':
            os.rename(temporary, target)  # Windows rename refuses an existing target.
        else:
            os.link(temporary, target)
    finally:
        temporary.unlink(missing_ok=True)


def verify_build_inputs():
    lock = json.loads((ROOT / 'metadata/build-inputs.lock.json').read_text(encoding='utf-8'))
    if lock.get('schema') != 1 or lock.get('storage') != 'git':
        raise ValueError('Unsupported build input manifest')
    seen = set()
    for row in lock['files']:
        if row['path'] in seen:
            raise ValueError('Duplicate build input: ' + row['path'])
        seen.add(row['path'])
        verify(destination(row['path']), row)
    return len(seen)


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    # Accept old preparation flags for existing callers; they cause no download.
    parser.add_argument('--resources', help=argparse.SUPPRESS)
    parser.add_argument('--generic', action='store_true', help=argparse.SUPPRESS)
    parser.add_argument('--download', action='store_true', help=argparse.SUPPRESS)
    parser.parse_args()
    count = verify_build_inputs()
    print('Verified %d checked-in build inputs; no external resource download required.' % count)


if __name__ == '__main__':
    main()
