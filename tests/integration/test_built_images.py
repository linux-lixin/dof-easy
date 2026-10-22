"""Run Ubuntu game/admin acceptance against a disposable external MySQL fixture."""
import argparse
from pathlib import Path
from test_ubuntu_images import main

ROOT=Path(__file__).resolve().parents[2]
parser=argparse.ArgumentParser(description=__doc__)
parser.add_argument('--game-image',default='dnf-game:ubuntu24.04')
parser.add_argument('--admin-image',default='dnf-admin:latest')
parser.add_argument('--state-file',type=Path,default=ROOT/'dist/image-test-state.json')
parser.add_argument('--stable-seconds',type=int,default=300)
parser.add_argument('--mode',choices=['all','split'],default='all')
parser.add_argument('--ubuntu',action='store_true',help=argparse.SUPPRESS)
parser.add_argument('--cleanup',action='store_true')
if __name__=='__main__':
    options=parser.parse_args()
    if options.stable_seconds < 1: parser.error('--stable-seconds must be positive')
    raise SystemExit(main(options))
