"""Require an operator-supplied PVF before any database initialization."""
import os
from pathlib import Path
import sys
import shutil
import tempfile


def seed_missing(role, path=Path('/data/Script.pvf'), seed=Path('/opt/dnf/pvf/Script.pvf')):
    """First boot may use the archived operator resource; never replace persisted data."""
    path, seed = Path(path), Path(seed)
    if role not in ('ALL', 'GAME'):
        return
    # Reserved staging files may survive SIGKILL; they are never accepted as a PVF.
    # This deployment has one writer per data directory. Do not follow symlinks.
    for stale in path.parent.glob('.pvf-seed-*'):
        if stale.is_file() and not stale.is_symlink(): stale.unlink()
    if path.exists() or path.is_symlink() or not seed.is_file():
        return
    path.parent.mkdir(parents=True, exist_ok=True)
    temporary=None
    try:
        with tempfile.NamedTemporaryFile(dir=path.parent,prefix='.pvf-seed-',delete=False) as destination:
            temporary=Path(destination.name)
            with seed.open('rb') as source:
                shutil.copyfileobj(source,destination)
            destination.flush()
            os.fsync(destination.fileno())
        temporary.chmod(0o600)
        # Atomic, exclusive publication: never overwrite a concurrently supplied PVF.
        try: os.link(temporary,path)
        except FileExistsError: pass
    finally:
        if temporary is not None: temporary.unlink(missing_ok=True)


def validate(role, path=Path('/data/Script.pvf')):
    if role not in ('ALL', 'GAME'):
        return
    path = Path(path)
    if not path.is_file() or path.stat().st_size == 0 or not os.access(path, os.R_OK):
        raise ValueError('Supply a readable, non-empty Script.pvf at /data/Script.pvf '
                         '(host: runtime/game/data/Script.pvf) before starting ALL/GAME.')


if __name__ == '__main__':
    try:
        seed_missing(os.environ.get('SERVER_TYPE'))
        validate(os.environ.get('SERVER_TYPE'))
    except (ValueError, OSError) as exc:
        print('Ubuntu PVF: ' + str(exc), file=sys.stderr)
        sys.exit(1)
