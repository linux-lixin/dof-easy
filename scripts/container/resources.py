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


def verify_native_inputs():
    """Verify deployable payloads without consulting or executing raw inputs."""
    runtime_lock = json.loads((ROOT / 'services/game/runtime/ubuntu/gateway/runtime.lock.json').read_text())
    prefix = 'services/game/resources/native/'
    if json.loads(destination(prefix + 'manifest.json').read_text()) != runtime_lock:
        raise ValueError('Bundled native manifest differs from runtime lock')
    for row in runtime_lock['files']:
        path = destination(prefix + row['path'])
        verify(path, row)
        if 'elf_class' in row:
            with path.open('rb') as stream:
                header = stream.read(5)
            if header != b'\x7fELF' + bytes([row['elf_class']]):
                raise ValueError('Native architecture mismatch: ' + row['path'])
    for name in ('plugin.ini', 'anton-raid.ini', 'luke-raid.ini', 'dungeon.lst', 'equipment.json'):
        bundled = destination(prefix + 'config/' + name)
        default = destination('services/game/runtime/ubuntu/gateway/defaults/' + name)
        verify(bundled, dict(bytes=default.stat().st_size, sha256=digest(default)))
    return len(runtime_lock['files'])


def verify_source_audit():
    """Explicit maintainer audit; originals are not deployment dependencies."""
    prefix = 'metadata/dof/source-audit/'
    source = json.loads(destination(prefix + 'server-inputs/replacement.lock.json').read_text())
    archive = json.loads(destination(prefix + 'resources.lock.json').read_text())
    for base, rows in ((prefix + 'server-inputs/files/', source['files']), (prefix, archive['files'])):
        for row in rows:
            verify(destination(base + row['path']), row)
    return len(source['files']) + len(archive['files'])


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    # Accept old preparation flags for existing callers; they cause no download.
    parser.add_argument('--resources', help=argparse.SUPPRESS)
    parser.add_argument('--generic', action='store_true', help=argparse.SUPPRESS)
    parser.add_argument('--download', action='store_true', help=argparse.SUPPRESS)
    parser.add_argument('--audit', action='store_true', help='Also verify archived originals and provenance (maintainers only)')
    args = parser.parse_args()
    count = verify_build_inputs()
    native_count = verify_native_inputs()
    print('Verified %d shared inputs and %d native payload files; no resource download required.' % (count, native_count))
    if args.audit:
        print('Verified %d archived source/evidence files.' % verify_source_audit())


if __name__ == '__main__':
    main()
