#!/usr/bin/env python3
"""Restore hash-pinned build inputs without executing downloaded programs."""
import argparse
import hashlib
import json
import os
from pathlib import Path, PurePosixPath
import subprocess
import sys
import tarfile
import tempfile
import urllib.request

ROOT = Path(__file__).resolve().parents[2]
ARCHIVE = 'dof-60d58715.tar.gz'
PREFIX = 'dof-60d58715beaa870d0314b5c7fe050bd78cb3f258/'


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


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--resources', type=Path, default=ROOT.parent / 'resource-archive')
    parser.add_argument('--download', action='store_true', help='download missing locked inputs')
    parser.add_argument('--generic', action='store_true', help='prepare shared build inputs; select the game at deployment time')
    args = parser.parse_args()
    lock = json.loads((ROOT / 'metadata/vipai86-inputs.lock.json').read_text(encoding='utf-8'))
    resources = args.resources.resolve()
    resources.mkdir(parents=True, exist_ok=True)
    verified_inputs = []
    for row in lock['inputs']:
        if args.generic and row['id'].startswith('df_game_r_files__'):
            continue
        if Path(row['id']).name != row['id']:
            raise ValueError('Invalid input filename')
        target = resources / row['id']
        if not target.exists() and args.download:
            print('Downloading', row['id'], flush=True)
            request = urllib.request.Request(row['url'], headers={'User-Agent': 'dof-easy-source-preparation'})
            with urllib.request.urlopen(request, timeout=60) as response:
                write_verified(response, target, row)
        verify(target, row)
        verified_inputs.append(row)
    manifest = resources / 'manifest.json'
    if not manifest.exists():
        with manifest.open('x', encoding='utf-8') as stream:
            json.dump([dict(row, status='downloaded') for row in verified_inputs], stream, indent=2)
    with tarfile.open(resources / ARCHIVE, 'r:gz') as archive:
        members = {}
        for member in archive:
            if member.name in members:
                raise ValueError('Duplicate archive member')
            members[member.name] = member
        for row in lock['upstream_assets']:
            target = destination(row['path'])
            if target.exists():
                verify(target, row)
                continue
            member = members[PREFIX + row['path']]
            if not member.isfile() or member.size != row['bytes']:
                raise ValueError('Unexpected archive member type/size')
            with archive.extractfile(member) as stream:
                write_verified(stream, target, row)
    command = [sys.executable, str(ROOT / 'scripts/vipai86/prepare_assets.py'), '--source', str(resources)]
    if args.generic:
        command += ['--plugins-only', '--output', str(ROOT / 'services/game/plugin-assets')]
    subprocess.run(command, check=True)
    subprocess.run([sys.executable, str(ROOT / 'deploy/vipai86/db/generate_compat.py')], check=True)
    print('Shared build inputs verified. The installer supplies the selected game and PVF at runtime.')


if __name__ == '__main__':
    main()
