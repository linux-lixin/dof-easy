"""Role-aware checks of real ELF processes, sockets and database reachability."""
import os
from pathlib import Path
import subprocess
import sys
import time
from config import channels

def snapshot():
    processes=[]
    for proc in Path('/proc').glob('[0-9]*'):
        try:
            args=(proc/'cmdline').read_bytes().split(b'\0')
            if args and args[0]:
                processes.append((proc.name,Path(os.fsdecode(args[0])).name,[os.fsdecode(x) for x in args[1:] if x]))
        except (FileNotFoundError,PermissionError,ProcessLookupError): pass
    ports={}
    for protocol in ('tcp','udp'):
        ports[protocol]=set()
        for line in Path('/proc/net/'+protocol).read_text().splitlines()[1:]:
            columns=line.split()
            if protocol=='udp' or columns[3]=='0A': ports[protocol].add(int(columns[1].split(':')[1],16))
    return processes,ports

def check():
    role=os.environ.get('SERVER_TYPE','ALL'); group=os.environ.get('SERVER_GROUP','3')
    processes,ports=snapshot()
    required=[]; tcp=set(); udp=set()
    if role in ('CORE','ALL'):
        required+=['df_monitor_r','df_manager_r','df_guild_r','df_statics_r','df_coserver_r','df_community_r','df_auction_r','df_point_r']
        if len([p for p in processes if p[1]=='df_dbmw_r'])!=3:
            raise ValueError('expected three real df_dbmw_r instances')
        tcp.update([int('500'+group),int('3'+group+'303'),int('3'+group+'403'),int('3'+group+'100'),int('3'+group+'603'),int('3'+group+'803')])
        udp.update(int('3'+group+suffix) for suffix in ('303','403','503','703','603','803'))
        if os.environ.get('MAIN_BRIDGE_IP','127.0.0.1')=='127.0.0.1':
            required+=['df_bridge_r','df_channel_r']; tcp.update([7000,7001])
    if role in ('P2P','ALL'):
        required+=['df_relay_r','df_stun_r']; tcp.add(int('7'+group+'00')); udp.add(int('7'+group+'00'))
        udp.update(int('2'+group+str(x)) for x in (11,12,13))
        marker=Path('/run/dnf/relay-registered')
        if not marker.exists() or time.time()-marker.stat().st_mtime>90:
            raise ValueError('relay registration not current')
    if role in ('GAME','ALL'):
        required+=['gunnersvr','zergsvr','secagent']
        if Path('/etc/supervisor/conf.d/qk.conf').exists():
            required.append('QK')
            tcp.add(8887)
            if not Path('/run/dnf/qk-ready').exists():
                raise ValueError('QK gateway not ready')
        names=('cain','diregie','siroco','prey','casillas','hilder')
        for channel in channels(os.environ.get('OPEN_CHANNEL','11,52')):
            name=names[int(group)-1]+f'{channel:02d}'
            if len([p for p in processes if p[1]=='df_game_r' and name in p[2]])!=1:
                raise ValueError('missing/duplicate real channel process: '+name)
            tcp.add(int(group+'00'+f'{channel:02d}'))
            udp.add(int(group+'10'+f'{channel:02d}'))
    for name in required:
        if not any(p[1]==name for p in processes): raise ValueError('missing real process: '+name)
    for protocol,expected in (('tcp',tcp),('udp',udp)):
        missing=expected-ports[protocol]
        if missing: raise ValueError(protocol+' ports missing: '+str(sorted(missing)))
    if role!='P2P':
        for prefix in ('MAIN_MYSQL','MYSQL'):
            env=dict(os.environ,MYSQL_PWD=os.environ['DNF_DB_GAME_PASSWORD'])
            result=subprocess.run(['/usr/local/bin/mysql','-h',os.environ[prefix+'_HOST'],'-P',os.environ[prefix+'_PORT'],
                                   '-ugame','-N','-e','SELECT 1'],env=env,capture_output=True,timeout=3)
            if result.returncode: raise ValueError(prefix+' connection failed')
    return True

if __name__=='__main__':
    try: check()
    except (ValueError,OSError,KeyError,subprocess.TimeoutExpired) as exc:
        print('Not ready: '+str(exc),file=sys.stderr); sys.exit(1)
