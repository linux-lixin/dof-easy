#!/bin/sh
set -eu
export LD_LIBRARY_PATH=/inputs/mysql/lib/x86_64-linux-gnu
export PATH=/inputs/mysql/usr/bin:$PATH
mysql --no-defaults --version > /output/mysql-client-version.txt
mkdir -p /tmp/mysql/mysql /tmp/mysql-run /tmp/mysql-files
(printf 'USE mysql;\n'; cat /inputs/mysql/usr/share/mysql/mysql_system_tables.sql /inputs/mysql/usr/share/mysql/mysql_system_tables_data.sql) | /inputs/mysql/usr/sbin/mysqld --no-defaults --bootstrap --user=root --basedir=/inputs/mysql/usr --datadir=/tmp/mysql --default-storage-engine=MyISAM --max-allowed-packet=8M > /output/db-bootstrap.log 2>&1
/inputs/mysql/usr/sbin/mysqld --no-defaults --user=root --basedir=/inputs/mysql/usr --datadir=/tmp/mysql --secure-file-priv=/tmp/mysql-files --socket=/tmp/mysql-run/mysql.sock --pid-file=/tmp/mysql-run/mysql.pid --bind-address=127.0.0.1 --port=3307 --innodb-buffer-pool-size=64M --log-error=/output/mysql.log --general-log=1 --general-log-file=/output/mysql-general.log &
dbpid=$!
python3 /inputs/db-port.py &
proxy_pid=$!
trap 'kill "$proxy_pid" 2>/dev/null || true; kill "$dbpid" 2>/dev/null || true; wait "$dbpid" 2>/dev/null || true' EXIT
for attempt in $(seq 1 30); do
 if mysql --socket=/tmp/mysql-run/mysql.sock -uroot -N -e 'SELECT VERSION()' > /output/db-version.txt 2>/dev/null; then break; fi
 sleep 1
done
test -s /output/db-version.txt
for sql in /inputs/sql/*.sql; do
 name=$(basename "$sql" .sql)
 mysql --socket=/tmp/mysql-run/mysql.sock -uroot -e "CREATE DATABASE $name CHARACTER SET utf8"
 mysql --socket=/tmp/mysql-run/mysql.sock -uroot "$name" < "$sql" >> /output/db-import.log 2>&1
done
mysql --socket=/tmp/mysql-run/mysql.sock -uroot -N -e 'SELECT TABLE_SCHEMA,COUNT(*) FROM information_schema.TABLES GROUP BY TABLE_SCHEMA' > /output/db-tables.tsv
cat /output/db-version.txt

python3 /inputs/setup-stack.py
cd /opt/dof-easy/runtime/gateway
unset LD_LIBRARY_PATH
export PLUGIN_MODE=dp2-frida LAUNCHER_MODE=native RAID_EXTENSION=on SERVER_TYPE=GAME CUR_SG_DB_HOST=127.0.0.1 CUR_SG_DB_PORT=3307
export DOF_DB_GAME_PASSWORD=$(cat /output/game-password)
strace -ff -o /output/gateway.trace -e trace=file,memory,network python3 -u /opt/dof-easy/runtime/gateway/service.py run > /output/gateway.stdout 2> /output/gateway.stderr &
gateway_pid=$!
for attempt in $(seq 1 100); do
 test ! -s /run/dof-easy/gateway-ready || break
 sleep 1
done
test -s /run/dof-easy/gateway-ready
cp /run/dof-easy/gateway-ready /output/production-gateway-ready
python3 - <<'PY2'
from pathlib import Path
import json,hashlib
result=[]
for p in Path('/proc').iterdir():
 if not p.name.isdecimal():continue
 try:
  target=str((p/'exe').resolve())
  if target.startswith('/opt/dof-easy/'):
   result.append(dict(pid=int(p.name),exe=target,maps=(p/'maps').read_text()))
 except (PermissionError,FileNotFoundError):pass
Path('/output/gateway-processes.json').write_text(json.dumps(result,indent=2))
b=Path('/output/game/df_game_r').read_bytes()
Path('/output/game-patch.json').write_text(json.dumps(dict(sha256=hashlib.sha256(b).hexdigest(),newLibraryReferences=b.count(b'libdof-inline.so'),originalLibraryReferences=b.count(b'libantisvrimport.so'))))
PY2
cd /output/game
gcc -m32 -shared -fPIC /inputs/frida-compat.c -Wl,--version-script=/inputs/frida-compat.map -ldl -o /opt/dof-easy/lib/i386/libfrida-compat.so
for shim in legacy-stat legacy-shm; do
 gcc -m32 -shared -fPIC -Wall -Wextra -Werror -O2 "/inputs/$shim.c" -ldl -o "/opt/dof-easy/lib/i386/lib$shim.so"
done
cp /opt/dof-easy/lib/i386/libfrida-compat.so /opt/dnf/compat/i386/
cp /opt/dof-easy/lib/i386/liblegacy-*.so /opt/dnf/compat/i386/
mkdir -p -m 700 /dev/shm/sec
for service in gunnersvr zergsvr; do
 cd "/output/secsvr/$service"
 env LD_LIBRARY_PATH=/opt/dof-easy/lib/i386 LD_PRELOAD=/opt/dof-easy/lib/i386/liblegacy-stat.so:/opt/dof-easy/lib/i386/liblegacy-shm.so "./$service" -t30 -i1 > "/output/$service.stdout" 2> "/output/$service.stderr" &
done
sleep 2
cd /output/secsvr/zergsvr
env LD_LIBRARY_PATH=/opt/dof-easy/lib/i386 LD_PRELOAD=/opt/dof-easy/lib/i386/liblegacy-stat.so:/opt/dof-easy/lib/i386/liblegacy-shm.so ./secagent > /output/secagent.stdout 2> /output/secagent.stderr &
cd /output/bridge
strace -ff -o /output/bridge.trace -e trace=file,network -E LD_LIBRARY_PATH=/opt/dof-easy/lib/i386 -E LD_PRELOAD=/opt/dof-easy/lib/i386/liblegacy-stat.so:/opt/dof-easy/lib/i386/liblegacy-shm.so:/inputs/bridge_hook.so:/inputs/libhook.so ./df_bridge_r server start > /output/bridge.stdout 2> /output/bridge.stderr &
bridge_probe=$!
sleep 3
cd /output/game
export PLUGIN_MODE=dp2-frida
export DOF_DB_GAME_PASSWORD=$(cat /output/game-password)
export MYSQL_PWD="$DOF_DB_GAME_PASSWORD"
run_game_cycle() {
result=0
timeout --kill-after=15s 200s strace -ff -o /output/game.trace -e trace=file,memory,network /opt/dnf/ubuntu/run-game /home/neople/game/df_game_r local nofork > /output/game.stdout 2> /output/game.stderr &
game_probe=$!
python3 - "$game_probe" <<'PYPROC'
from pathlib import Path
import json,sys,time,os,signal
sys.path.insert(0, '/inputs')
from report import login_state_initialized
ready_at=None
stop_sent=False
parent=Path('/proc')/sys.argv[1]
observations=0
while parent.exists():
 for p in Path('/proc').iterdir():
  if not p.name.isdecimal():continue
  try:
   if str((p/'exe').resolve())=='/output/game/df_game_r':
    observations+=1
    log=Path('/output/game.stdout').read_text(errors='replace')
    if login_state_initialized(log) and ready_at is None: ready_at=time.monotonic()
    if ready_at is not None and not stop_sent and time.monotonic()-ready_at>15:
     Path('/output/game-stop.json').write_text(json.dumps(dict(pid=int(p.name),signal='SIGUSR2',afterLoginInitialization=True)))
     os.kill(int(p.name),signal.SIGUSR2)
     stop_sent=True
    Path('/output/game-process.json').write_text(json.dumps(dict(pid=int(p.name),exe='/output/game/df_game_r',maps=(p/'maps').read_text(),observations=observations)))
  except (FileNotFoundError,ProcessLookupError,PermissionError):pass
 if observations>100:break
 time.sleep(2)
PYPROC
wait "$game_probe" || result=$?
printf '%s\n' "$result" > /output/game.exit
}
acceptance=0
run_game_cycle
python3 /inputs/report.py --output /output/first-start.json || acceptance=$?
if [ "$result" -eq 0 ] && [ -f /output/game-stop.json ] && [ "${DOF_PROBE_SINGLE_CYCLE:-0}" != 1 ]; then
 # Keep the same database, keys, gateway, bridge and security services alive.
 # Preserve every first-cycle observation before starting the game again.
 python3 - <<'PYRESTART'
from pathlib import Path
root = Path('/output')
archive = root / 'first-start'
archive.mkdir()
for pattern in ['game.trace.*', 'game.stdout', 'game.stderr', 'game.exit', 'game-stop.json', 'game-process.json', 'bridge-live.json']:
 for path in root.glob(pattern): path.rename(archive / path.name)
PYRESTART
 run_game_cycle
fi
# Stop and restart the actual production owner after both game processes exit.
# Never kill an unrelated process: this PID namespace contains only this fixture.
stop_gateway_owner() {
python3 - <<'PYSTOP'
from pathlib import Path
import os,signal
for p in Path('/proc').iterdir():
 if not p.name.isdecimal():continue
 try:
  args=(p/'cmdline').read_bytes().split(b'\0')
  if b'/opt/dof-easy/runtime/gateway/service.py' in args and b'run' in args:
   os.kill(int(p.name),signal.SIGTERM)
 except (ProcessLookupError,FileNotFoundError,PermissionError):pass
PYSTOP
}
stop_gateway_owner
owner_exit=0
wait "$gateway_pid" || owner_exit=$?
test ! -e /run/dof-easy/gateway-ready
printf '%s\n' "$owner_exit" > /output/gateway-owner-first.exit
if [ "${DOF_PROBE_SINGLE_CYCLE:-0}" = 1 ]; then
 python3 /inputs/report.py || acceptance=$?
 printf '%s\n' '{"performed":false,"scope":"One affected native-load/function regression; startup accepted by user"}' > /output/restart.json
 printf '%s\n' '{"performed":false,"scope":"Gateway stopped normally; restart not repeated"}' > /output/gateway-restart.json
 if [ "$acceptance" -ne 0 ]; then exit "$acceptance"; fi
 exit "$result"
fi
cd /opt/dof-easy/runtime/gateway
strace -ff -o /output/gateway-restart.trace -e trace=file,memory,network python3 -u /opt/dof-easy/runtime/gateway/service.py run > /output/gateway-restart.stdout 2> /output/gateway-restart.stderr &
gateway_pid=$!
for attempt in $(seq 1 100); do
 test ! -s /run/dof-easy/gateway-ready || break
 sleep 1
done
test -s /run/dof-easy/gateway-ready
cp /run/dof-easy/gateway-ready /output/gateway-second-ready
stop_gateway_owner
owner_exit=0
wait "$gateway_pid" || owner_exit=$?
printf '%s\n' "$owner_exit" > /output/gateway-owner-second.exit
python3 - <<'PYOWNER'
from pathlib import Path
import json
root=Path('/output')
first=int((root/'gateway-owner-first.exit').read_text())
second=int((root/'gateway-owner-second.exit').read_text())
(root/'gateway-restart.json').write_text(json.dumps(dict(
 passed=first==second==0 and not Path('/run/dof-easy/gateway-ready').exists(),
 firstOwnerExit=first, secondOwnerExit=second, reusedDatabaseAndKeys=True,
 firstPid=int((root/'production-gateway-ready').read_text()),
 secondPid=int((root/'gateway-second-ready').read_text()),
 serverAccessed=False, scope='Production gateway owner stop/start/stop after both game cycles; other services retained'
),indent=2)+'\n')
PYOWNER

python3 /inputs/report.py || acceptance=$?
python3 - <<'PYRESTART'
from pathlib import Path
import json
root = Path('/output')
first = json.loads((root / 'first-start.json').read_text())
second = json.loads((root / 'native-observations.json').read_text())
required = ['loginStateInitialized', 'combinedModulesLoaded', 'raidConfiguration',
            'privateKeyConsumed', 'serverTextResourceLoaded', 'bridgeHealthy', 'normalStop']
passed = (root / 'first-start').is_dir() and all(
 report['checks'][name] for report in [first, second] for name in required)
(root / 'restart.json').write_text(json.dumps(dict(
 schema=1, serverAccessed=False, releaseReady=False, passed=passed,
 sameDatabaseAndKeys=True, gatewayKeptRunning=True,
 firstGameExit=first['gameExit'], secondGameExit=second['gameExit'],
 scope='Game process cold start, normal stop and start again; not full stack restart or gameplay'
), indent=2) + '\n')
PYRESTART
if [ "$acceptance" -ne 0 ]; then exit "$acceptance"; fi
exit "$result"
