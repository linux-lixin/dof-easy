"""Ubuntu acceptance, invoked by test_built_images.py --ubuntu.

Disposable MySQL is an external test fixture. No production host is contacted.
State contains ephemeral credentials and stays in ignored dist/; reports do not.
"""
import hashlib
import ipaddress
import json
import re
from pathlib import Path
import secrets
import subprocess
import time
import uuid

ROOT=Path(__file__).resolve().parents[2]
MYSQL='mysql:5.6@sha256:20575ecebe6216036d25dab5903808211f1e9ba63dc7825ac20cb975e34cfcae'

def docker(*args, data=None, check=True):
    result=subprocess.run(['docker',*map(str,args)],input=data,capture_output=True,text=True,encoding='utf8',errors='replace')
    if check and result.returncode: raise RuntimeError(result.stdout+result.stderr)
    return result

def say(message): print(time.strftime('%H:%M:%S'),message,flush=True)

def wait(description, callback, limit=600):
    deadline=time.monotonic()+limit
    while time.monotonic()<deadline:
        try:
            result=callback()
            if result: say(description+': OK'); return result
        except (RuntimeError,ValueError,KeyError): pass
        time.sleep(3)
    raise RuntimeError('Timeout: '+description)

def cleanup(state):
    remaining=[]
    resources=[('container',n) for n in reversed(state.get('containers',[]))]
    resources += [('volume',n) for n in state.get('volumes',[])]+[('network',state['network'])]
    for kind,name in resources:
        args=['rm','-fv',name] if kind=='container' else [kind,'rm',name]
        result=docker(*args,check=False)
        if result.returncode and docker(kind,'inspect',name,check=False).returncode==0:
            remaining.append(name)
    state['cleaned_up']=not remaining
    state['cleanup_errors']=remaining

def main(options):
    statefile=options.state_file.resolve(); statefile.parent.mkdir(parents=True,exist_ok=True)
    if options.cleanup:
        state=json.loads(statefile.read_text()); cleanup(state)
        statefile.write_text(json.dumps(state,indent=2))
        if not state['cleaned_up']:
            say('Cleanup incomplete: '+', '.join(state['cleanup_errors'])); return 1
        return 0
    if statefile.exists() and not json.loads(statefile.read_text()).get('cleaned_up'):
        raise RuntimeError('Inspect/cleanup previous test state before reusing '+str(statefile))
    prefix='dnf-ubuntu-test-'+uuid.uuid4().hex[:8]
    state=dict(network=prefix,db=prefix+'-mysql',game=prefix+'-game',admin=prefix+'-admin',
               containers=[],volumes=[],password=secrets.token_hex(16),game_password=secrets.token_hex(4),mode=options.mode)
    report=dict(started_at=time.strftime('%Y-%m-%dT%H:%M:%S'),mode=options.mode,checks=[],status='running',
                real_windows_client='NOT TESTED: registration, gameplay, party, automatic update')
    secrets_to_check=[state['password'],state['game_password']]
    def sanitize(text):
        for value in secrets_to_check:
            if value: text=text.replace(value,'[REDACTED]')
        return re.sub(r'\b(?:pw|pwd|password)\s*[:=][^\r\n]*','password=[REDACTED]',text,flags=re.IGNORECASE)
    def save(): statefile.write_text(json.dumps(state,indent=2))
    def sql(query, check=True):
        return docker('exec','-i','-e','MYSQL_PWD='+state['password'],state['db'],'mysql','-uroot','-N',data=query,check=check)
    def healthy(name):
        return docker('exec',name,'python3','/opt/dnf/ubuntu/health.py',check=False).returncode==0
    def run(name, image, env, volume, ip=None):
        args=['run','-d','--name',name,'--network',state['network'],'--memory','10g','--shm-size','8g']
        if ip: args+=['--ip',ip]
        for key,value in env.items(): args+=['-e',key+'='+value]
        if volume: args+=['-v',volume+(':/app/data' if name==state['admin'] else ':/data')]
        state['containers'].append(name); save()
        docker(*args,image)
    def volume(suffix):
        name=prefix+'-'+suffix; state['volumes'].append(name); save(); docker('volume','create',name); return name
    def evidence():
        pids=docker('exec',state['game'],'pgrep','-x','df_game_r',check=False).stdout.split()
        if len(pids)!=2 or not all(healthy(name) for name in state['nodes']): return None
        for channel in ('11','52'):
            command="grep -h 'GeoIP Allow Country Code' /home/neople/game/log/siroco"+channel+"/Log*.init"
            log=docker('exec',state['game'],'bash','-c',command,check=False).stdout
            if not all(code in log for code in ('CN','HK','KR','MO','TW')): return None
        return sorted(pids)
    def api():
        return docker('exec',state['game'],'curl','-fsS','--max-time','3',state['url']+'/api/v1/client/version/1.0.0',check=False).returncode==0
    def service_pids():
        probe="import sys,json; sys.path.insert(0,'/opt/dnf/ubuntu'); import health; print(json.dumps(sorted((p,n) for p,n,a in health.snapshot()[0] if n.startswith('df_') or n in ('gunnersvr','zergsvr','secagent'))))"
        return {name:docker('exec',name,'python3','-c',probe).stdout for name in state['nodes']}
    def persistent_hashes():
        return docker('exec',state['game'],'bash','-c',
                      'find /data -type f \\( -name Script.pvf -o -name publickey.pem -o -name privatekey.pem -o -name Config.ini -o -name libhook.so -o -name user_daily_script.sh \\) -print0 | sort -z | xargs -0 sha256sum').stdout
    save()
    try:
        report['images']={image:json.loads(docker('image','inspect',image).stdout)[0]['Id'] for image in (options.game_image,options.admin_image)}
        inventory=docker('run','--rm','--network','none','--entrypoint','bash',options.game_image,'-c',
                         'cat /etc/os-release; for p in python2 python2.7 mysqld mariadbd netbird tailscale tailscaled; do command -v "$p" && exit 1; done; '
                         'test -z "$(find /home/template /opt/dnf -type f \\( -name netbird -o -name tailscale -o -name tailscaled -o -name mysqld \\) -print)"').stdout
        assert 'VERSION_ID="24.04"' in inventory
        absent_paths=('/home/template/init/netbird','/home/template/init/tailscale',
                      '/home/template/root','/home/template/init/init_local_db.sh',
                      '/etc/supervisor/conf.d/netbird.conf','/etc/supervisor/conf.d/tailscale.conf',
                      '/etc/supervisor/conf.d/gate.conf.template','/opt/dnf/compat/amd64',
                      '/opt/dnf/ubuntu/legacy-entrypoint.sh')
        check="from pathlib import Path; paths="+repr(absent_paths)+"; assert not [p for p in paths if Path(p).exists()]"
        docker('run','--rm','--read-only','--network','none','--entrypoint','python3',options.game_image,'-c',check)
        report['checks'].append('Ubuntu24; no Python2, database server or VPN binaries')
        for bad,word in [({'SERVER_TYPE':'INVALID'},'SERVER_TYPE'),({'SERVER_TYPE':'ALL'},'MAIN_MYSQL_HOST'),
                         ({'SERVER_TYPE':'P2P','TS_AUTH_KEY':'test-token'},'TS_AUTH_KEY')]:
            args=[]
            for key,value in bad.items(): args+=['-e',key+'='+value]
            result=docker('run','--rm','--network','none',*args,options.game_image,check=False)
            assert result.returncode and word in result.stdout+result.stderr
        report['checks'].append('Invalid role, missing database and nonempty VPN credential fail before startup')
        docker('network','create','--internal',state['network'])
        subnet=json.loads(docker('network','inspect',state['network']).stdout)[0]['IPAM']['Config'][0]['Subnet']
        network=ipaddress.ip_network(subnet)
        coreip=str(network.network_address+10); gameip=str(network.network_address+11); p2pip=str(network.network_address+12)
        # Advertised NAT addresses deliberately do not exist on any container NIC.
        advertised_core='192.0.2.10'; advertised_game='192.0.2.11'; advertised_p2p='192.0.2.12'
        run(state['db'],MYSQL,{'MYSQL_ROOT_PASSWORD':state['password'],'MYSQL_ROOT_HOST':'%'},None)
        wait('external MySQL 5.6',lambda:sql('SELECT 1',False).returncode==0,180)
        report['mysql_version']=sql('SELECT VERSION()').stdout.strip()
        assert report['mysql_version']=='5.6.51'
        dbip=json.loads(docker('inspect',state['db']).stdout)[0]['NetworkSettings']['Networks'][state['network']]['IPAddress']
        # Legacy test fixture must match production sql_mode, not modern strict defaults.
        sql("SET GLOBAL sql_mode='NO_ENGINE_SUBSTITUTION'; SET GLOBAL max_allowed_packet=67108864;")
        gamevol=volume('game-data'); adminvol=volume('admin-data')
        docker('run','--rm','--network','none','--entrypoint','sh','-v',adminvol+':/app/data','-v',gamevol+':/game-data',options.admin_image,'-c',
               'openssl genpkey -algorithm RSA -out /app/data/private.key -pkeyopt rsa_keygen_bits:2048 && chmod 600 /app/data/private.key && '
               'openssl rsa -pubout -in /app/data/private.key -out /app/data/publickey.pem && cp /app/data/publickey.pem /game-data/publickey.pem')
        # Explicit test fixture upload; production startup never supplies a PVF.
        archive=ROOT/'services/game/assets/home/template/init/Script.tgz'
        docker('run','--rm','--network','none','--entrypoint','sh','-v',gamevol+':/data',
               '-v',str(archive)+':/fixture.tgz:ro',options.game_image,'-c','tar -xzf /fixture.tgz -C /data Script.pvf')
        env=dict(SERVER_GROUP='3',SERVER_GROUP_DB='cain',OPEN_CHANNEL='11,52',CLIENT_POOL_SIZE='3',P2P_RELAY_INDEX='1',
                 PUBLIC_IP=advertised_core,CORE_PUBLIC_IP='127.0.0.1',MAIN_BRIDGE_IP='127.0.0.1',P2P_PUBLIC_IP=advertised_core,
                 MAIN_MYSQL_HOST=dbip,MAIN_MYSQL_PORT='3306',MAIN_MYSQL_ROOT_PASSWORD=state['password'],MAIN_MYSQL_GAME_ALLOW_IP='%',
                 MYSQL_HOST=dbip,MYSQL_PORT='3306',MYSQL_GAME_ALLOW_IP='%',DNF_DB_ROOT_PASSWORD=state['password'],
                 DNF_DB_GAME_PASSWORD=state['game_password'],WEB_PASS=secrets.token_hex(12),MYSQL_INIT_SSL_MODE='disabled')
        secrets_to_check.append(env['WEB_PASS'])
        badenv=dict(env,SERVER_TYPE='ALL',MAIN_MYSQL_ROOT_PASSWORD='WrongPassword')
        args=[]
        for key,value in badenv.items(): args+=['-e',key+'='+value]
        bad=docker('run','--rm','--network',state['network'],'-v',gamevol+':/data',*args,options.game_image,check=False)
        assert bad.returncode and 'Access denied' in bad.stdout+bad.stderr
        assert sql("SELECT COUNT(*) FROM information_schema.SCHEMATA WHERE SCHEMA_NAME='d_taiwan'").stdout.strip()=='0'
        report['checks'].append('Wrong password fails before creating game schemas')
        state['nodes']=[]
        if options.mode=='split':
            state['core']=prefix+'-core'; state['p2p']=prefix+'-p2p'
            p2penv=dict(SERVER_TYPE='P2P',PUBLIC_IP=advertised_p2p,CORE_PUBLIC_IP=coreip,MAIN_BRIDGE_IP=coreip,SERVER_GROUP='3',P2P_RELAY_INDEX='1')
            run(state['p2p'],options.game_image,p2penv,volume('p2p-data'),p2pip)
            # Deliberately reverse the documented order to exercise delayed dependencies.
            time.sleep(10)
            assert not healthy(state['p2p']), 'P2P cannot be ready before CORE registration'
            run(state['core'],options.game_image,dict(env,SERVER_TYPE='CORE',GAME_CHANNEL_HOSTS='11='+gameip+',52='+gameip),volume('core-data'),coreip)
            wait('CORE schema',lambda:sql('SELECT 1 FROM taiwan_login.log_query_ref LIMIT 1',False).returncode==0)
            wait('P2P registration and real sockets',lambda:healthy(state['p2p']))
            report['checks'].append('P2P starts before delayed CORE, remains unready, then registers automatically')
            run(state['game'],options.game_image,dict(env,SERVER_TYPE='GAME',PUBLIC_IP=advertised_game,CORE_PUBLIC_IP=coreip,MAIN_BRIDGE_IP=coreip,P2P_PUBLIC_IP=advertised_p2p),gamevol,gameip)
            state['nodes']=[state['core'],state['p2p'],state['game']]
        else:
            run(state['game'],options.game_image,dict(env,SERVER_TYPE='ALL'),gamevol,coreip)
            state['nodes']=[state['game']]
        save()
        wait('game schema imported',lambda:sql('SELECT 1 FROM taiwan_cain_2nd.member_avatar_coin LIMIT 1; SELECT 1 FROM taiwan_login.log_query_ref LIMIT 1',False).returncode==0)
        assert sql("SELECT q_id FROM taiwan_login.log_query_ref WHERE query_hash='6d986dad283ff5a4'").stdout.strip()=='50939'
        sql("INSERT INTO taiwan_login.log_query_ref(query_hash,query) VALUES('ubuntuacceptance','SELECT 1')")
        wait('current and next month tables',lambda:sql("SELECT COUNT(*) FROM information_schema.TABLES WHERE TABLE_SCHEMA='taiwan_cain_log' AND TABLE_NAME IN (CONCAT('log_response_time_',DATE_FORMAT(NOW(),'%Y%m')),CONCAT('log_response_time_',DATE_FORMAT(DATE_ADD(NOW(),INTERVAL 1 MONTH),'%Y%m')))").stdout.strip()=='2')
        report['checks'].append('Fresh schema, repaired seed, new query insert and both monthly tables')
        adminenv={'SPRING_DATASOURCE_URL':f'jdbc:mysql://{dbip}:3306/dnf_service?useUnicode=true&characterEncoding=utf8&useSSL=false&autoReconnect=true',
                  'SPRING_DATASOURCE_USERNAME':'root','SPRING_DATASOURCE_PASSWORD':state['password'],'MYSQL_INIT_SSL_MODE':'disabled','JAVA_TOOL_OPTIONS':'-Xmx512m'}
        run(state['admin'],options.admin_image,adminenv,adminvol)
        adminip=json.loads(docker('inspect',state['admin']).stdout)[0]['NetworkSettings']['Networks'][state['network']]['IPAddress']
        state['url']='http://'+adminip; save()
        wait('backend API',api,180)
        bootstrap=(ROOT/'deploy/shared/bootstrap-admin.sql.example').read_text()
        sql(bootstrap.replace('CHANGE_ME_NEW_PRIVATE_ADMIN_PASSWORD',secrets.token_hex(12)).replace('CHANGE_ME_DIFFERENT_PRIVATE_TEMP_SECRET',secrets.token_hex(12)))
        assert sql("SELECT admin FROM d_taiwan.accounts WHERE accountname='admin'").stdout.strip()=='1'
        fingerprints={name:docker('exec',state['admin'],'sha256sum','/app/data/'+name).stdout.split()[0] for name in ('private.key','publickey.pem')}
        pids=wait('real channels, five countries and role health',evidence)
        startup_message='[启动完成] 游戏服务已全部就绪'
        def startup_count():
            return docker('logs',state['game']).stdout.count(startup_message)
        wait('one overall startup-complete log',lambda:startup_count()==1)
        report['checks'].append('One overall startup-complete log after role health and current five countries')
        for node in state['nodes']:
            status=docker('exec',node,'supervisorctl','status',check=False).stdout
            assert 'tongyi_gate' not in status
            assert docker('exec',node,'pgrep','-x','DnfGateServer',check=False).returncode==1
            assert docker('exec',node,'test','!','-e','/home/template/root/DnfGateServer').returncode==0
            sockets=docker('exec',node,'cat','/proc/net/tcp').stdout
            listening={int(line.split()[1].split(':')[1],16) for line in sockets.splitlines()[1:] if line.split()[3]=='0A'}
            assert not {7600,881} & listening
        report['checks'].append('Old unified launcher gateway absent from Supervisor, processes and listening ports')
        # Preserve a real valid PVF override and a harmless user script customization.
        docker('exec',state['game'],'bash','-c','test -s /data/Script.pvf; '
               'if test -f /data/daily_job/user_daily_script.sh; then printf "\\n# acceptance customization\\n" >> /data/daily_job/user_daily_script.sh; fi')
        persistent=persistent_hashes()
        assert 'Script.pvf' in persistent and 'publickey.pem' in persistent and 'libhook.so' in persistent
        relayip=advertised_p2p if options.mode=='split' else advertised_core
        relayhost=coreip if options.mode=='split' else '127.0.0.1'
        response=docker('exec',state['game'],'bash','-c',
                        'printf "%s,1,7300\\n" "$1" | nc -N -w 2 "$2" 5003','relay-probe',relayip,relayhost).stdout
        assert response.strip()=='OK', 'netcat-openbsd relay registration did not receive acknowledgment'
        report['checks'].append('Pinned netcat implementation exchanges legacy relay registration with acknowledgment')
        route_node=state.get('core',state['game']); expected_target=gameip if options.mode=='split' else '127.0.0.1'
        for service in ('statics','coserver'):
            rows=docker('exec',route_node,'cat','/home/neople/'+service+'/table/server_config.tbl').stdout.splitlines()
            game_rows=[row.split() for row in rows if row.split() and row.split()[0]=='1']
            assert {int(row[2]) for row in game_rows}=={11,52}
            assert all(row[3]==expected_target and row[4]=='310'+row[2] for row in game_rows)
        report['checks'].append('Nonlocal advertised IPv4 binds successfully; CORE routes target actual GAME nodes')
        report['startup_seconds']=round(time.time()-time.mktime(time.strptime(report['started_at'],'%Y-%m-%dT%H:%M:%S')))
        stable_processes=service_pids()
        say('Beginning stability observation '+str(options.stable_seconds)+'s')
        stable_started=time.monotonic(); next_notice=60
        while time.monotonic()-stable_started<options.stable_seconds:
            time.sleep(max(0,min(10,options.stable_seconds-(time.monotonic()-stable_started))))
            assert evidence()==pids,'Actual game PID/port/role readiness changed during stability window'
            assert service_pids()==stable_processes,'A real CORE/GAME/P2P/TSS process restarted during stability window'
            elapsed=int(time.monotonic()-stable_started)
            if elapsed>=next_notice:
                say('Stable: '+str(elapsed)+' seconds'); next_notice=elapsed+60
        report['stable_seconds']=options.stable_seconds
        report['checks'].append('Current-run five countries and real PIDs/ports stable')
        # Fault injection is confined to recorded disposable test resources.
        docker('exec',state['game'],'kill','-KILL',pids[0])
        wait('single channel recovery',lambda:evidence() and evidence()!=pids)
        docker('stop',state['db'])
        assert not healthy(state['game']), 'Database outage must make readiness fail'
        docker('start',state['db']); wait('database restored',lambda:sql('SELECT 1',False).returncode==0,180)
        wait('game recovery after database outage',evidence)
        report['checks'].append('Single channel kill and database outage recover with readiness transitions')
        for cycle in range(3):
            for name in reversed(state['nodes']): docker('stop',name)
            for name in state['nodes']: docker('start',name)
            docker('restart',state['admin'])
            wait('restart cycle '+str(cycle+1),lambda:evidence() and api())
            wait('one fresh completion log for restart '+str(cycle+1),lambda:startup_count()==cycle+2)
            assert sql("SELECT COUNT(*) FROM d_taiwan.accounts WHERE accountname='admin'").stdout.strip()=='1'
            assert sql("SELECT COUNT(*) FROM taiwan_login.log_query_ref WHERE query_hash='ubuntuacceptance'").stdout.strip()=='1'
            for name,expected in fingerprints.items():
                assert docker('exec',state['admin'],'sha256sum','/app/data/'+name).stdout.split()[0]==expected
            assert persistent_hashes()==persistent, 'Game PVF, keys, hook or user configuration changed'
        report['checks'].append('Three stop/start cycles preserve database sentinel, admin and RSA pair')
        encrypted=docker('exec',state['game'],'bash','-c','source /run/dnf/env.sh; printf "%s" "$DEC_GAME_PWD"').stdout.strip()
        secrets_to_check.append(encrypted)
        for name in state['nodes']+[state['admin']]:
            raw=docker('logs',name,check=False)
            assert not any(value and value in raw.stdout+raw.stderr for value in secrets_to_check), 'Configured credential leaked in raw container logs: '+name
        report['checks'].append('Raw game and backend logs contain no configured plaintext or encrypted DB credentials')
        report['resources']=docker('stats','--no-stream','--format','{{.Name}} {{.MemUsage}} {{.CPUPerc}}',*state['nodes'],state['admin'],state['db']).stdout.splitlines()
        report['image_bytes']={image:json.loads(docker('image','inspect',image).stdout)[0]['Size'] for image in report['images']}
        report['container_restarts']={name:json.loads(docker('inspect',name).stdout)[0]['RestartCount'] for name in state['nodes']}
        report['status']='passed'; say('Ubuntu '+options.mode+' acceptance PASSED')
    except Exception as exc:
        report['status']='failed'; report['error']=sanitize(str(exc)); say('FAILED: '+report['error']); return 1
    finally:
        report['finished_at']=time.strftime('%Y-%m-%dT%H:%M:%S')
        report['node_diagnostics']={}
        for name in state.get('nodes',[]):
            health=docker('exec',name,'python3','/opt/dnf/ubuntu/health.py',check=False)
            report['node_diagnostics'][name]={
                'health_exit_code':health.returncode,
                'health_output':health.stdout+health.stderr,
                'supervisor':docker('exec',name,'supervisorctl','status',check=False).stdout,
            }
        folder=statefile.parent/(statefile.stem+'-evidence'); folder.mkdir(exist_ok=True)
        for name in state['containers']:
            result=docker('logs',name,check=False)
            text=result.stdout+result.stderr
            text=sanitize(text)
            (folder/(name+'.log')).write_text(text,encoding='utf8')
        for name in state.get('nodes',[]):
            result=docker('exec',name,'bash','-c',
                         'find /home/neople/game/log -type f -name "Log*.init" -exec tail -n 100 {} +',check=False)
            text=result.stdout+result.stderr
            text=sanitize(text)
            (folder/(name+'-current-init.log')).write_text(text,encoding='utf8')
        (folder/'report.json').write_text(json.dumps(report,indent=2),encoding='utf8')
        save()
    return 0
