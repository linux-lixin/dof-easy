#!/usr/bin/env python3
"""Verify local replacement-edition materials without executing or changing them."""
import argparse
import hashlib
import json
from pathlib import Path
import sys
from zipfile import BadZipFile, ZipFile

ROOT = Path(__file__).resolve().parents[2]
LOCK = ROOT / 'metadata/dof/admin-baseline.lock.json'


def digest(stream):
    result = hashlib.sha256()
    size = 0
    for block in iter(lambda: stream.read(1024 * 1024), b''):
        size += len(block)
        result.update(block)
    return size, result.hexdigest()


def archive_name(info):
    name = info.filename
    if not info.flag_bits & 0x800:
        try:
            name = name.encode('cp437').decode('gbk')
        except UnicodeError:
            pass
    return name


def verify(server, tool, client_archive, lock=None):
    lock = lock or json.loads(LOCK.read_text(encoding='utf-8'))
    if lock.get('edition') != 'qingkong-2.8-replacement':
        raise ValueError('Unsupported source edition')
    rows = []
    with ZipFile(client_archive) as client:
        names = {}
        for entry in client.infolist():
            name = archive_name(entry)
            if name in names:
                raise ValueError('Duplicate client archive entry')
            names[name] = entry
        for row in lock['files']:
            actual = None
            if row['role'] == 'client':
                entry = names.get('客户端文件/' + row['path'])
                if entry and not entry.is_dir() and not entry.flag_bits & 1:
                    with client.open(entry) as stream:
                        actual = digest(stream)
            else:
                root = {'server': server, 'tool': tool}[row['role']].resolve(strict=True)
                path = root / row['path']
                # Every input must be the named regular file, not a redirected link.
                if path.is_file() and not path.is_symlink() and path.resolve() == path.absolute():
                    with path.open('rb') as stream:
                        actual = digest(stream)
            rows.append(dict(role=row['role'], path=row['path'],
                             matched=actual == (row['bytes'], row['sha256']),
                             bytes=None if actual is None else actual[0],
                             sha256=None if actual is None else actual[1]))
    return dict(schema=1, edition=lock['edition'], sourceVerified=all(r['matched'] for r in rows),
                files=rows, originalToolMenuAuditComplete=False,
                nativeClientAcceptance=False, releaseReady=False)


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--server', type=Path, required=True)
    parser.add_argument('--tool', type=Path, required=True)
    parser.add_argument('--client-archive', type=Path, required=True)
    args = parser.parse_args()
    try:
        report = verify(args.server, args.tool, args.client_archive)
    except ValueError as error:
        parser.exit(1, str(error) + '\n')
    except (OSError, BadZipFile):
        parser.exit(1, '材料目录或客户端归档无法读取；未修改输入\n')
    print(json.dumps(report, ensure_ascii=False, indent=2))
    return 0 if report['sourceVerified'] else 1


if __name__ == '__main__':
    sys.exit(main())
