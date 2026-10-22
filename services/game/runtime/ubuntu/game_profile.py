"""Check the operator-selected executable before touching persistent databases."""
import hashlib
import os
from pathlib import Path
import re


def validate(path, expected=None):
    path = Path(path)
    if path.is_symlink() or not path.is_file():
        raise ValueError('Missing regular /data/df_game_r; run the dof-easy installer')
    with path.open('rb') as stream:
        header = stream.read(20)
        if len(header) != 20 or header[:6] != b'\x7fELF\x01\x01' or header[18:20] != b'\x03\x00':
            raise ValueError('df_game_r must be a little-endian i386 ELF')
        if expected:
            if not re.fullmatch('[0-9a-f]{64}', expected):
                raise ValueError('Invalid DNF_GAME_SHA256')
            stream.seek(0)
            digest = hashlib.sha256()
            for block in iter(lambda: stream.read(1024 * 1024), b''):
                digest.update(block)
            if digest.hexdigest() != expected:
                raise ValueError('df_game_r differs from the selected deployment profile')
    path.chmod(0o700)


if __name__ == '__main__' and os.environ.get('SERVER_TYPE', 'ALL') in ('ALL', 'GAME'):
    validate('/data/df_game_r', os.environ.get('DNF_GAME_SHA256'))
