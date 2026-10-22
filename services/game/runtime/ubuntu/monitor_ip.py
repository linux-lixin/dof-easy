"""Bounded first address discovery; explicit IPv4 wins over auto/DDNS."""
import ipaddress
import os
from pathlib import Path
import subprocess
import time
import sys

def ipv4(value):
    return str(ipaddress.IPv4Address(value.strip()))

def discover():
    if os.environ.get('PUBLIC_IP'):
        return ipv4(os.environ['PUBLIC_IP'])
    if os.environ.get('DDNS_ENABLE') == 'true' and os.environ.get('DDNS_DOMAIN'):
        result=subprocess.check_output(['timeout','2','getent','ahostsv4',os.environ['DDNS_DOMAIN']],text=True)
        return ipv4(result.split()[0])
    if os.environ.get('AUTO_PUBLIC_IP') == 'true' or os.environ.get('DDNS_ENABLE') == 'true':
        script='/data/monitor_ip/get_ddns_ip.sh' if os.environ.get('DDNS_ENABLE')=='true' else '/data/monitor_ip/auto_public_ip.sh'
        return ipv4(subprocess.check_output(['timeout','2','bash',script],text=True))
    raise ValueError('no address discovery method enabled')

def restart_address_services():
    role=os.environ['SERVER_TYPE']; targets=[]
    if role in ('ALL','CORE'):
        targets.extend(['core:monitor','core:manager'])
        if os.environ.get('MAIN_BRIDGE_IP','127.0.0.1')=='127.0.0.1':
            targets.extend(['core:bridge','core:channel'])
        targets.extend('core:'+name for name in ('dbmw_guild','dbmw_mnt','dbmw_stat','auction','point','guild','statics','coserver','community'))
    if role in ('ALL','P2P'): targets.append('p2p:relay')
    if role in ('ALL','GAME'): targets.append('game:*')
    for target in targets:
        subprocess.run(['supervisorctl','-c','/etc/supervisord.conf','restart',target],check=True,timeout=30)

def main():
    path=Path('/data/monitor_ip/MONITOR_PUBLIC_IP')
    deadline=time.monotonic()+int(os.environ.get('STARTUP_TIMEOUT','600'))
    old=None
    while True:
        try:
            address=discover()
            if address!=old:
                temp=path.with_suffix('.tmp'); temp.write_text(address+'\n'); temp.replace(path)
                print('Public IPv4: '+address,flush=True)
                if old is not None:
                    restart_address_services()
                old=address
            if os.environ.get('PUBLIC_IP') or os.environ.get('DDNS_ENABLE')!='true': return 0
        except (ValueError,IndexError,subprocess.SubprocessError) as exc:
            if old is None and time.monotonic()>=deadline:
                print('Address discovery timed out: '+type(exc).__name__,file=sys.stderr); return 1
        time.sleep(int(os.environ.get('DDNS_INTERVAL','10')) if old else 1)

if __name__=='__main__': sys.exit(main())
