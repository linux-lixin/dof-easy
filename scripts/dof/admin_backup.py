"""Compare a private admin-data archive to the live tree without extracting it.

A matching archive may be hard-linked into a new server-local restore point.
Links/special files deliberately fall back to a fresh backup; no live data is
changed and no file names or contents are returned in the report.
"""
import gzip
import hashlib
from pathlib import Path
import stat
import tarfile
import zlib


def _digest(stream):
    result = hashlib.sha256()
    for block in iter(lambda: stream.read(1024 * 1024), b''):
        result.update(block)
    return result.digest()


def _identity(info):
    return (info.st_dev, info.st_ino, info.st_size, info.st_mtime_ns, info.st_ctime_ns,
            info.st_mode, info.st_uid, info.st_gid)


def matches(tree, archive):
    tree, archive = Path(tree), Path(archive)
    if not tree.is_dir() or tree.is_symlink() or not archive.is_file() or archive.is_symlink():
        return False
    paths = {'admin-data': tree}
    paths.update({'admin-data/' + p.relative_to(tree).as_posix(): p for p in tree.rglob('*')})
    seen = set()
    identities = {}
    archive_before = _identity(archive.stat())
    try:
        with gzip.open(archive, 'rb') as stream:
            with tarfile.open(fileobj=stream, mode='r|') as source:
                for item in source:
                    name = item.name.rstrip('/')
                    if name in seen or name not in paths:
                        return False
                    seen.add(name)
                    path = paths[name]
                    info = path.lstat()
                    identities[name] = _identity(info)
                    if (stat.S_IMODE(info.st_mode), info.st_uid, info.st_gid, info.st_mtime) != (
                            item.mode, item.uid, item.gid, item.mtime):
                        return False
                    if item.isdir():
                        if not stat.S_ISDIR(info.st_mode):
                            return False
                    elif item.isfile():
                        if not stat.S_ISREG(info.st_mode) or info.st_nlink != 1 or info.st_size != item.size:
                            return False
                        member = source.extractfile(item)
                        with path.open('rb') as live:
                            if _digest(live) != _digest(member):
                                return False
                        # A file modified during comparison is not a stable snapshot.
                        after = path.lstat()
                        if (info.st_ino, info.st_size, info.st_mtime_ns, info.st_ctime_ns) != (
                                after.st_ino, after.st_size, after.st_mtime_ns, after.st_ctime_ns):
                            return False
                    else:
                        return False
            # Consume the gzip footer as well: truncated/corrupt archives must
            # never become a new restore point even if member bytes matched.
            while stream.read(1024 * 1024):
                pass
        final_names = {'admin-data'} | {'admin-data/' + p.relative_to(tree).as_posix() for p in tree.rglob('*')}
        return (seen == final_names == set(paths) and
                all(_identity(paths[name].lstat()) == value for name, value in identities.items()) and
                _identity(archive.stat()) == archive_before)
    except (OSError, EOFError, tarfile.TarError, ValueError, zlib.error):
        return False
