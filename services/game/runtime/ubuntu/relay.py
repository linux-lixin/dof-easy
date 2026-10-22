"""Legacy ip,index,port registration with bounded I/O and atomic config writes."""
import ipaddress
import os
from pathlib import Path
import socket
import subprocess
import sys
import time

def registration(value):
    fields=value.strip().split(',')
    if len(fields)!=3: raise ValueError('expected ip,index,port')
    address=str(ipaddress.IPv4Address(fields[0]))
    index,port=int(fields[1]),int(fields[2])
    if not 1<=index<=999 or not 1<=port<=65535: raise ValueError('invalid relay index/port')
    return address,index,port

def apply_registration(path,address,index,port):
    rows=path.read_text().splitlines() if path.exists() else []
    newline=f'Relay   1       0         {index}     {address}     {port}'
    newrows=[row for row in rows if len(row.split())<4 or row.split()[3]!=str(index)]
    newrows.append(newline)
    pending=path.with_suffix('.pending')
    if sorted(rows)!=sorted(newrows):
        # Persist pending before replacing the config: interruption must retry apply.
        pending.write_text('monitor reload pending\n')
        temp=path.with_suffix('.tmp'); temp.write_text('\n'.join(newrows)+'\n'); temp.replace(path)
    if pending.exists():
        subprocess.run(['supervisorctl','-c','/etc/supervisord.conf','restart','core:monitor'],timeout=30,check=True)
        pending.unlink()
        print(f'Relay applied: index={index} {address}:{port}',flush=True)

def server():
    group=os.environ['SERVER_GROUP']
    path=Path('/data/relay/MONITOR_RELAY_CONFIG')
    path.parent.mkdir(parents=True,exist_ok=True)
    with socket.socket() as listener:
        listener.setsockopt(socket.SOL_SOCKET,socket.SO_REUSEADDR,1)
        listener.bind(('0.0.0.0',int('500'+group))); listener.listen(16)
        while True:
            conn,_=listener.accept()
            with conn:
                conn.settimeout(2)
                try:
                    data=b''
                    while b'\n' not in data and len(data)<256:
                        chunk=conn.recv(256-len(data))
                        if not chunk: break
                        data+=chunk
                    address,index,port=registration(data.decode('ascii'))
                    apply_registration(path,address,index,port)
                    conn.sendall(b'OK\n')
                except (ValueError,OSError,UnicodeError,subprocess.SubprocessError) as exc:
                    print('Relay registration rejected: '+type(exc).__name__,flush=True)

def client():
    core=os.environ['CORE_PUBLIC_IP']; group=os.environ['SERVER_GROUP']
    deadline=time.monotonic()+int(os.environ.get('STARTUP_TIMEOUT','600'))
    marker=Path('/run/dnf/relay-registered')
    while True:
        try:
            address=Path('/data/monitor_ip/MONITOR_PUBLIC_IP').read_text().strip()
            value=f"{address},{os.environ['P2P_RELAY_INDEX']},7{group}00"
            registration(value)
            with socket.create_connection((core,int('500'+group)),timeout=2) as conn:
                conn.settimeout(2); conn.sendall((value+'\n').encode('ascii')); conn.shutdown(socket.SHUT_WR)
                if conn.recv(16).strip()!=b'OK': raise ValueError('registration not acknowledged')
            marker.write_text(str(time.time()))
            deadline=time.monotonic()+int(os.environ.get('STARTUP_TIMEOUT','600'))
            time.sleep(30)
        except (ValueError,OSError) as exc:
            marker.unlink(missing_ok=True)
            if time.monotonic()>=deadline:
                print(f'Relay registration timeout: {core}:500{group} ({type(exc).__name__})',flush=True); return 1
            time.sleep(2)

if __name__=='__main__':
    sys.exit(server() if sys.argv[1]=='server' else client())
