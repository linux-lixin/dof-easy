from pathlib import Path
import os,secrets,shutil,subprocess,configparser,json
Path('/usr/local/share/GeoIP').mkdir(parents=True)
shutil.copyfile('/inputs/GeoIP.dat','/usr/local/share/GeoIP/GeoIP.dat')
p=secrets.token_hex(4)
cipher=subprocess.check_output(['/inputs/TeaEncrypt',p],text=True,env={**os.environ,'LD_LIBRARY_PATH':''}).strip()
assert len(cipher)==48 and all(c in '0123456789abcdefABCDEF' for c in cipher)
root=Path('/opt/dof-easy')
(root/'runtime').mkdir(parents=True)
shutil.copytree('/inputs/production/gateway', root/'runtime/gateway')
import sys
sys.path.insert(0, str(root/'runtime'))
from gateway import assets, service
assets.install('/inputs/server-runtime', '/data/dof-easy')
compat=Path('/opt/dnf/compat/i386');compat.mkdir(parents=True)
subprocess.run(['gcc','-m32','-shared','-fPIC','-fvisibility=hidden','-fno-ident','-Wl,--build-id=none','-Wall','-Wextra','-Werror','-O2',
    '/inputs/admin-runtime-digest.c','-o',str(compat/'libdof-admin-runtime.so')],check=True)
shutil.copyfile('/inputs/admin-runtime-digest.c',compat/'admin-runtime-digest.c')
for f in ['libGeoIP.so.1','libnxencryption.so']:shutil.copyfile('/inputs/'+f,compat/f)
(root/'lib/i386').mkdir(parents=True)
for f in ['libGeoIP.so.1','libnxencryption.so']:shutil.copyfile('/inputs/'+f,root/'lib/i386'/f)
Path('/opt/dnf/ubuntu').mkdir(parents=True)
shutil.copyfile('/inputs/production/run-game','/opt/dnf/ubuntu/run-game')
Path('/opt/dnf/ubuntu/run-game').chmod(0o700)
Path('/opt/dnf/plugins/frida').mkdir(parents=True)
shutil.copyfile('/inputs/frida.so','/opt/dnf/plugins/frida/frida.so')
Path('/home/neople').mkdir(parents=True)
Path('/home/neople/game').symlink_to('/output/game')
shutil.copyfile('/inputs/df_game_r','/data/df_game_r')
Path('/usr/local/bin/mysql').write_text('#!/bin/sh\nLD_LIBRARY_PATH=/inputs/mysql/lib/x86_64-linux-gnu exec /inputs/mysql/usr/bin/mysql "$@"\n')
Path('/usr/local/bin/mysql').chmod(0o700)
game=Path('/output/game');(game/'cfg').mkdir(parents=True);(game/'pid').mkdir();(game/'log/local').mkdir(parents=True)
shutil.copyfile('/inputs/libantisvrimport.so',game/'libantisvrimport.so')
shutil.copyfile('/inputs/df_game_r',game/'df_game_r');(game/'df_game_r').chmod(0o755)
values={'SERVER_GROUP_NAME':'taiwan_cain','SERVER_GROUP':'3','CHANNEL_NO':'15','PROCESS_SEQUENCE':'3','CORE_PUBLIC_IP':'127.0.0.1','MAIN_BRIDGE_IP':'127.0.0.1','P2P_PUBLIC_IP':'127.0.0.1','PUBLIC_IP':'127.0.0.1','DEC_GAME_PWD':cipher}
for source,target in [('server.template','local.cfg'),('db-info.template','db_info_tw.cfg')]:
 s=Path('/inputs/'+source).read_text()
 for k,v in values.items():s=s.replace(k,v)
 (game/'cfg'/target).write_text(s)
subprocess.run(['openssl','genpkey','-algorithm','RSA','-pkeyopt','rsa_keygen_bits:2048','-out',str(game/'privatekey.pem')],check=True,stdout=subprocess.DEVNULL,stderr=subprocess.DEVNULL)
subprocess.run(['openssl','pkey','-in',str(game/'privatekey.pem'),'-pubout','-out',str(game/'publickey.pem')],check=True,stdout=subprocess.DEVNULL,stderr=subprocess.DEVNULL)
for f in ['privatekey.pem','publickey.pem']:shutil.copyfile(game/f,root/'runtime/gateway'/f)
keys=Path('/data/dof-easy/keys');keys.mkdir(mode=0o700,exist_ok=True)
for name in ['privatekey.pem','publickey.pem']:
 shutil.copyfile(game/name,keys/name)
 (keys/name).chmod(0o600)
shutil.copyfile('/inputs/server_str.xml',game/'server_str.xml')
shutil.copytree('/inputs/captcha_font',game/'captcha_font')
from gateway.pvf import convert as convert_pvf
pvf_report=convert_pvf('/inputs/Script.pvf', game/'Script.pvf')
Path('/output/pvf-migration.json').write_text(json.dumps(pvf_report,indent=2))
shutil.copytree('/inputs/channel_info',game/'channel_info')
# Input assets are immutable; the game's private channel cache is writable.
# copytree preserves source modes, so set runtime permissions explicitly.
for cache in (game/'channel_info').rglob('*'):
 cache.chmod(0o700 if cache.is_dir() else 0o600)
shutil.copyfile('/inputs/frida.so',game/'frida.so')
(game/'frida.config').write_text(json.dumps({'interaction':{'type':'script','path':'/inputs/db_adapter.js','on_change':'ignore'}}))
Path('/output/game-password').write_text(p)

query="GRANT ALL PRIVILEGES ON *.* TO 'game'@'localhost' IDENTIFIED BY '"+p+"';\nGRANT ALL PRIVILEGES ON *.* TO 'game'@'127.0.0.1' IDENTIFIED BY '"+p+"';\nUPDATE d_taiwan.db_connect SET db_ip='127.0.0.1',db_port=3307,db_userid='game',db_passwd='"+cipher+"';\n"
subprocess.run(['/inputs/mysql/usr/bin/mysql','--socket=/tmp/mysql-run/mysql.sock','-uroot'],input=query.encode(),check=True)
print('Fresh local credentials, configuration and RSA keys prepared; values omitted')

bridge=Path('/output/bridge');(bridge/'cfg').mkdir(parents=True);(bridge/'pid').mkdir();(bridge/'log').mkdir()
shutil.copyfile('/inputs/df_bridge_r',bridge/'df_bridge_r');(bridge/'df_bridge_r').chmod(0o755)
shutil.copyfile('/inputs/bridge-channel_info.etc',bridge/'cfg/channel_info.etc')
(bridge/'cfg/server.cfg').write_text('[server]\nmax_client = 1000\nthis_ip = 127.0.0.1\nthis_udp_port = 7000\nthis_tcp_port = 7000\n[DB]\ndb_ip = 127.0.0.1\ndb_name = d_channel\ndb_id = game\ndb_pwd = '+p+'\n')

shutil.copytree('/inputs/secsvr','/output/secsvr')
for rel in ['gunnersvr/gunnersvr','zergsvr/zergsvr','zergsvr/secagent']:
 Path('/output/secsvr',rel).chmod(0o755)

shutil.copytree('/inputs/dp2','/dp2',dirs_exist_ok=True)
body=b'\n'.join(Path('/inputs', name).read_bytes() for name in ['db_adapter.js','server_runtime_probe.js','server_raid_config_probe.js','server_feature_probe.js','server_equipment_probe.js','frida.js'])
(game/'combined.js').write_bytes(body)
(game/'frida.config').write_text(json.dumps({'interaction':{'type':'script','path':str(game/'combined.js'),'on_change':'ignore'}}))
frida_config=json.loads(Path('/inputs/frida_config.json').read_text())
frida_config['db_config']={'account':'game','password':p}
(game/'frida_config.json').write_text(json.dumps(frida_config))
(game/'frida_config.json').chmod(0o600)

# Exercise the actual install/prepare code used by the image. Only the location
# of Supervisor's generated configuration is redirected into private /run.
shutil.copyfile(game/'publickey.pem','/data/publickey.pem')
Path('/data/plugins/frida').mkdir(parents=True)
body=b'\n'.join(Path('/inputs', name).read_bytes() for name in ['server_runtime_probe.js','server_raid_config_probe.js','server_feature_probe.js','server_equipment_probe.js','frida.js'])
Path('/data/plugins/frida/frida.js').write_bytes(body)
os.environ.update(LAUNCHER_MODE='native', RAID_EXTENSION='on', PLUGIN_MODE='dp2-frida',
                  SERVER_TYPE='GAME', CUR_SG_DB_HOST='127.0.0.1', CUR_SG_DB_PORT='3307',
                  DOF_DB_GAME_PASSWORD=p)
database_uuid=subprocess.check_output(['/inputs/mysql/usr/bin/mysql','--socket=/tmp/mysql-run/mysql.sock','-uroot','-N','-e','SELECT @@server_uuid'],text=True).strip()
os.environ.update(DOF_INSTANCE_ID='dof-offline',DOF_DATABASE_UUID=database_uuid,
                  DOF_OFFLINE_RUNTIME_CHANNEL='local',CORE_PUBLIC_IP='127.0.0.1')
Path('/data/dof-easy/config/.admin-instance.json').write_text(json.dumps(dict(
    instanceId='dof-offline',databaseUuid=database_uuid,environment='isolated')))
service.prepare(Path('/run/dof-gateway.conf'))
Path('/output/production-prepared.json').write_text(json.dumps(dict(
    entrypoint='gateway.assets.install + gateway.service.prepare',
    sourceManifest=json.loads(Path('/inputs/server-runtime/manifest.json').read_text()),
    legacyRootEntries=[x.name for x in Path('/root').iterdir()],
    configuration=Path('/run/dof-gateway.conf').read_text())))
