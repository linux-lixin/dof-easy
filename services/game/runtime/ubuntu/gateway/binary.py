"""Atomically install a verified derived file; startup performs no binary edits."""
import hashlib
import os
from pathlib import Path
import tempfile


def prepare(source, destination, expected):
    source, destination = Path(source), Path(destination)
    if source.is_symlink() or not source.is_file():
        raise ValueError('Expected a regular pinned runtime input')
    if source.resolve() == destination.resolve():
        raise ValueError('Runtime output cannot overwrite its source')
    raw = source.read_bytes()
    if len(raw) != expected['bytes'] or hashlib.sha256(raw).hexdigest() != expected['sha256']:
        raise ValueError('Runtime input differs from the installed lock')
    mode = 0o700 if expected['path'].startswith('bin/') else 0o600
    if destination.is_symlink() or any(p.is_symlink() for p in destination.parents):
        raise ValueError('Runtime destination cannot contain links')
    if destination.is_file() and destination.read_bytes() == raw:
        destination.chmod(mode)
        return
    temporary = None
    try:
        with tempfile.NamedTemporaryFile(prefix='.dof-install-', dir=destination.parent, delete=False) as output:
            temporary = Path(output.name)
            os.fchmod(output.fileno(), mode)
            output.write(raw)
            output.flush()
            os.fsync(output.fileno())
        temporary.replace(destination)
    finally:
        if temporary is not None:
            temporary.unlink(missing_ok=True)
