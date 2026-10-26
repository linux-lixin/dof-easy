#!/usr/bin/env python3
"""Opt-in MySQL 5.6 test; all processes run in a new disconnected namespace.

Use an extracted Ubuntu game rootfs (with ip, Python, mariadb-client), an
extracted MySQL 5.6 package and verified native assets. Output must not exist.
Optional --jdk/--dependencies/--classes enable the real Spring web HTTP test.
"""
import argparse
import json
import hashlib
from pathlib import Path
import subprocess
import sys


def main():
    parser=argparse.ArgumentParser(description=__doc__)
    for name in ('rootfs','mysql-root','native-runtime','output'):
        parser.add_argument('--'+name,type=Path,required=True)
    for name in ('jdk','dependencies','classes','java-etc'):
        parser.add_argument('--'+name,type=Path)
    args=parser.parse_args()
    root=Path(__file__).resolve().parents[3]
    from importlib.util import spec_from_file_location,module_from_spec
    spec=spec_from_file_location('checked_assets',root/'services/game/runtime/ubuntu/gateway/assets.py')
    assets=module_from_spec(spec);spec.loader.exec_module(assets)
    assets.verify(args.native_runtime)
    client=json.loads((root/'services/game/docker/ubuntu/mysql56-client.lock.json').read_text())
    if hashlib.sha256((args.mysql_root/'usr/bin/mysqldump').read_bytes()).hexdigest()!=client['sha256']:
        parser.error('MySQL dump client differs from pinned image')
    java=[args.jdk,args.dependencies,args.classes,args.java_etc]
    if any(java) and not all(java):parser.error('Supply all four Java paths together')
    for path in (args.rootfs,args.mysql_root,args.native_runtime,*[p for p in java if p]):
        if not path.is_dir():parser.error('Missing local input: '+str(path))
    out=args.output.absolute()
    out.mkdir(mode=0o700,parents=True,exist_ok=False)
    command=['bwrap','--unshare-all','--cap-add','CAP_NET_ADMIN','--uid','0','--gid','0',
        '--die-with-parent','--new-session','--clearenv','--setenv','LC_ALL','C.UTF-8',
        '--setenv','PATH','/usr/bin:/bin','--ro-bind',str(args.rootfs.resolve()),'/',
        '--proc','/proc','--dev','/dev','--tmpfs','/tmp','--tmpfs','/run']
    mounts=[(root,'/tmp/repo'),(args.mysql_root,'/tmp/mysql'),(args.native_runtime,'/tmp/runtime'),
        (Path(__file__).with_name('probe.py'),'/tmp/check.py'),(Path(__file__).with_name('mysql.sh'),'/tmp/check.sh'),
        (root/'services/game/assets/TeaEncrypt','/tmp/TeaEncrypt')]
    if all(java):
        mounts += [(args.jdk,'/tmp/jdk'),(args.dependencies,'/tmp/deps'),(args.classes,'/tmp/classes'),
                   (args.java_etc,args.java_etc.resolve().as_posix())]
    for source,target in mounts:command+=['--ro-bind',str(source.resolve()),target]
    command+=['--bind',str(out),'/tmp/output','--','/bin/sh','/tmp/check.sh']
    result=subprocess.run(command,timeout=300)
    report=out/'result.json'
    if result.returncode==0 and report.is_file():print(report.read_text())
    else:print('Failed; private diagnostic logs: '+str(out),file=sys.stderr)
    return result.returncode or (0 if report.is_file() else 1)

if __name__=='__main__':sys.exit(main())
