"""Verify (or explicitly regenerate) matching admin/game runtime protocol hashes."""
import argparse
import hashlib
import json
from pathlib import Path

ROOT=Path(__file__).resolve().parents[2]
GAME=ROOT/'services/game/runtime/ubuntu/gateway'
TARGETS=[GAME/'runtime-apply.json',ROOT/'services/admin/backend/src/main/resources/dof/runtime-apply.json']


def contract():
    files=dict(executorScriptSHA256=GAME/'admin_apply.js',gameAbiSHA256=GAME/'game-abi.json',
               digestHelperSourceSHA256=ROOT/'services/game/docker/ubuntu/admin-runtime-digest.c')
    return dict(schema=1,**{key:hashlib.sha256(path.read_bytes()).hexdigest() for key,path in files.items()})


def main():
    parser=argparse.ArgumentParser(description=__doc__);parser.add_argument('--write',action='store_true');args=parser.parse_args()
    expected=contract()
    for path in TARGETS:
        if args.write:path.write_text(json.dumps(expected,indent=2)+'\n')
        elif not path.is_file() or json.loads(path.read_text())!=expected:raise SystemExit('Runtime contract differs: '+str(path.relative_to(ROOT)))
    print('Matching runtime protocol hashes '+('written' if args.write else 'verified'))


if __name__=='__main__':main()
