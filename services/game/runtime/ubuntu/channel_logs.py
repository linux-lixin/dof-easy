"""Give every channel process a fresh initialization log, preserving the last run."""
from pathlib import Path
import re
import sys
import time

def rotate(root,channel):
    if not re.fullmatch(r'(cain|diregie|siroco|prey|casillas|hilder)[0-9]{2}',channel):
        raise ValueError('invalid channel log directory')
    folder=Path(root)/channel
    if folder.is_symlink(): raise ValueError('channel log directory is a symlink')
    folder.mkdir(parents=True,exist_ok=True)
    for path in folder.glob('Log*.init'):
        if path.is_symlink(): raise ValueError('channel init log is a symlink')
        previous=path.with_suffix('.init.previous')
        if previous.exists():
            previous.rename(previous.with_name(previous.name+'.'+str(time.time_ns())))
        path.replace(previous)

if __name__=='__main__':
    rotate('/home/neople/game/log',sys.argv[1])
