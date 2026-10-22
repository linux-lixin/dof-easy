"""Create private deployment settings once; copy only a completed operator PVF."""
import argparse
import hashlib
import ipaddress
import json
import os
from pathlib import Path
import secrets
import shutil


def configure(root, address, pvf=None):
    root=Path(root).resolve()
    ipaddress.IPv4Address(address)
    settings=root/'.env'
    if settings.exists():
        raise ValueError('.env already exists; edit it manually, passwords were not changed')
    if pvf:
        pvf=Path(pvf).resolve()
        if pvf.name.lower()!='script.pvf' or not pvf.is_file() or pvf.stat().st_size<1024:
            raise ValueError('Supply a completed Script.pvf; temporary downloader files are not accepted')
        target=root/'runtime/game/data/Script.pvf'
        if target.exists(): raise ValueError('PVF already exists; nothing overwritten')
        target.parent.mkdir(parents=True,exist_ok=True)
        shutil.copyfile(pvf,target)
        target.chmod(0o600)
        record=dict(source=str(pvf),size=target.stat().st_size,
                    sha256=hashlib.sha256(target.read_bytes()).hexdigest(),
                    compatibility='operator-supplied, client compatibility not yet verified')
        (root/'runtime/pvf-source.json').write_text(json.dumps(record,indent=2),encoding='utf-8')
    root.mkdir(parents=True,exist_ok=True)
    values={'PUBLIC_IP':address,'BIND_IP':'127.0.0.1','ADMIN_PORT':'18080',
            'PLUGIN_MODE':'dp2-frida','DB_ROOT_PASSWORD':secrets.token_hex(24),
            'GAME_DB_PASSWORD':secrets.token_hex(4),'SUPERVISOR_PASSWORD':secrets.token_hex(16),
            'ADMIN_PASSWORD':secrets.token_hex(16),'ADMIN_TEMP_SECRET':secrets.token_hex(16)}
    with settings.open('x',encoding='utf-8',newline='\n') as stream:
        stream.write(''.join(f'{key}={value}\n' for key,value in values.items()))
    settings.chmod(0o600)
    return settings


if __name__=='__main__':
    parser=argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--public-ip',required=True)
    parser.add_argument('--pvf',type=Path)
    args=parser.parse_args()
    try:
        path=configure(Path(__file__).resolve().parent,args.public_ip,args.pvf)
        print('Private settings written to',path,'(passwords not printed).')
        if not args.pvf: print('First boot uses the archived operator PVF embedded in the image; existing runtime PVF is preserved.')
        print('Ports bind to loopback by default. Set BIND_IP explicitly for remote clients after review.')
    except (ValueError,OSError) as exc: parser.exit(1,str(exc)+'\n')
