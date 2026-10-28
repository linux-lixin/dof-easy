"""Restore an admin-data archive into a NEW private directory and verify it.

This is an offline file restore drill, not database recovery or an application
rollback. The destination and all failed output are retained for operator review.
No live tree is accepted or modified. Reports contain no member names or bytes.
"""
import argparse
import hashlib
import json
import os
from pathlib import Path, PurePosixPath
import re
import shutil
import tarfile

from admin_backup import matches

RESERVE = 2 * 1024 ** 3


def digest(path):
    result = hashlib.sha256()
    with Path(path).open('rb') as stream:
        for block in iter(lambda: stream.read(1024 * 1024), b''):
            result.update(block)
    return result.hexdigest()


def validate_members(members):
    entries = {}
    for member in members:
        name = member.name.rstrip('/')
        path = PurePosixPath(name)
        if (not name or '\\' in name or path.is_absolute() or
                any(part in ('', '.', '..') for part in name.split('/')) or
                path.parts[0] != 'admin-data' or name in entries or
                not (member.isdir() or member.isreg()) or member.size < 0 or
                (member.isdir() and member.size != 0) or
                member.mode & ~0o777 or member.uid < 0 or member.gid < 0 or
                member.issparse()):
            raise ValueError('Unsupported or unsafe archive member')
        entries[name] = member
    if 'admin-data' not in entries or not entries['admin-data'].isdir():
        raise ValueError('Archive must contain its admin-data root directory')
    for name in entries:
        for parent in PurePosixPath(name).parents:
            if str(parent) != '.' and (str(parent) not in entries or not entries[str(parent)].isdir()):
                raise ValueError('Archive parent is missing or is not a directory')
    return entries


def restore(archive, destination, expected_sha256, protected_tree):
    if os.name != 'posix':
        raise ValueError('Full ownership/mode restore verification requires a POSIX host')
    if not re.fullmatch('[0-9a-f]{64}', expected_sha256):
        raise ValueError('A pinned archive SHA-256 is required')
    archive, destination = Path(archive), Path(destination)
    if archive.is_symlink() or not archive.is_file():
        raise ValueError('Archive must be a regular non-link file')
    # Do not resolve the final component: that would hide a destination symlink.
    parent = destination.parent.resolve(strict=True)
    destination = parent / destination.name
    protected_tree = Path(protected_tree).resolve(strict=True)
    if not protected_tree.is_dir():
        raise ValueError('Protected live tree must be an existing directory')
    if (destination == protected_tree or protected_tree in destination.parents or
            destination in protected_tree.parents):
        raise ValueError('Restore destination overlaps the protected live tree')
    if destination.exists() or destination.is_symlink():
        raise ValueError('Destination must not exist; existing output is never reused')
    if digest(archive) != expected_sha256:
        raise ValueError('Archive digest differs from the pinned restore point')
    with tarfile.open(archive, 'r:gz') as source:
        entries = validate_members(source.getmembers())
        total = sum(m.size for m in entries.values() if m.isreg())
        peak = (total * 101 + 99) // 100 + len(entries) * 16384 + 1024 ** 2
        if shutil.disk_usage(parent).free < RESERVE + peak:
            raise ValueError('Insufficient space for restore peak plus 2 GiB reserve')
        if os.geteuid() != 0 and any((m.uid, m.gid) != (os.geteuid(), os.getegid()) for m in entries.values()):
            raise ValueError('Archive ownership requires the server operator account')
        destination.mkdir(mode=0o700)
        # Restrict files until the complete payload has been written and verified.
        for name, member in sorted(entries.items(), key=lambda item: len(PurePosixPath(item[0]).parts)):
            target = destination.joinpath(*PurePosixPath(name).parts)
            if member.isdir():
                target.mkdir(mode=0o700)
            else:
                with source.extractfile(member) as incoming, target.open('xb') as outgoing:
                    os.chmod(target, 0o600)
                    shutil.copyfileobj(incoming, outgoing, 1024 * 1024)
                    outgoing.flush()
                    os.fsync(outgoing.fileno())
                if target.stat().st_size != member.size:
                    raise ValueError('Restored member size differs')
        for name, member in sorted(entries.items(), key=lambda item: len(PurePosixPath(item[0]).parts), reverse=True):
            target = destination.joinpath(*PurePosixPath(name).parts)
            os.chown(target, member.uid, member.gid)
            os.chmod(target, member.mode)
            os.utime(target, (member.mtime, member.mtime))
    if digest(archive) != expected_sha256 or not matches(destination / 'admin-data', archive):
        raise ValueError('Restored bytes, membership or metadata verification failed; retain output')
    if shutil.disk_usage(parent).free < RESERVE:
        raise ValueError('Base space reserve no longer available; retain output')
    return dict(passed=True, archiveSHA256=expected_sha256, restoredIntoNewDirectory=True,
                restoredMembers=len(entries), restoredFileBytes=total,
                fullBytesAndMetadataVerified=True, baseReserveBytes=RESERVE,
                databaseRestoreVerified=False, applicationRecoveryVerified=False,
                liveDataModified=False, completeReplacement=False)


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--archive', type=Path, required=True)
    parser.add_argument('--sha256', required=True)
    parser.add_argument('--destination', type=Path, required=True)
    parser.add_argument('--protected-tree', type=Path, required=True)
    args = parser.parse_args()
    try:
        result = restore(args.archive, args.destination, args.sha256, args.protected_tree)
    except (ValueError, OSError, EOFError, tarfile.TarError):
        # Exceptions can contain private paths; keep public output deliberately small.
        parser.exit(1, 'Restore drill failed; retain private output for operator review.\n')
    print(json.dumps(result, indent=2))


if __name__ == '__main__':
    main()
