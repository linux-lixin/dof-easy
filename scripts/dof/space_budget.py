"""Fail closed before isolated writes, using space available without root reserve."""
import os
from pathlib import Path

BASE_RESERVE_BYTES = 2 * 1024 ** 3


def require_space(paths, peak_bytes):
    """Budget the caller's additional peak on each distinct destination filesystem.

    peak_bytes must cover temporary outputs, backups and rollback preparation;
    it is additional to existing files. Never count privileged reserved blocks.
    This is an admission check, not a reservation against concurrent writers.
    """
    if type(peak_bytes) is not int or peak_bytes <= 0:
        raise ValueError('Provide a positive operation peak estimate in bytes')
    if not paths:
        raise ValueError('Destination filesystems must be specified')
    results = []
    seen = set()
    for value in paths:
        path = Path(value).resolve()
        while not path.exists():
            path = path.parent
        device = path.stat().st_dev
        if device in seen:
            continue
        seen.add(device)
        usage = os.statvfs(path)
        available = usage.f_bavail * usage.f_frsize
        required = BASE_RESERVE_BYTES + peak_bytes
        if available < required:
            raise RuntimeError('Insufficient space at {}: available={} required={} '
                               '(2 GiB reserve + {} peak), shortfall={}'.format(
                                   path, available, required, peak_bytes, required - available))
        results.append({'filesystemPath': str(path), 'availableBytes': available,
                        'requiredBytes': required, 'peakBytes': peak_bytes})
    return results
