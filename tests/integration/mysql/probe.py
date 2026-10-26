from pathlib import Path
import json, os, sys, subprocess, hashlib
sys.path.insert(0,'/tmp/repo')
from scripts.container import database
from services.game.runtime.ubuntu import external_database
base=Path('/tmp/output')
cnf=base/'admin.cnf';cnf.write_text('[client]\nuser=root\nsocket=/tmp/output/run/mysql.sock\n');cnf.chmod(0o600)
query=database.host_query('/tmp/mysql/usr/bin/mysql',cnf)
identity=database.identity(query)
from tests.integration.mysql.menu_probe import verify
menu_result=verify(query)
query('CREATE DATABASE unrelated_site; CREATE TABLE unrelated_site.sentinel (id int PRIMARY KEY, value varchar(20)); INSERT INTO unrelated_site.sentinel VALUES (1,\'keep\');')
instance=base/'native86';instance.mkdir()
assets=instance/'runtime/game/data/dof-easy/assets';assets.parent.mkdir(parents=True);assets.symlink_to('/tmp/runtime')
values=dict(DOF_DATABASE_KIND='external',DOF_DB_HOST='127.0.0.1',DOF_DB_PORT='3307',
            DOF_DB_ALLOW_HOST='127.0.0.0/255.0.0.0',PUBLIC_IP='192.0.2.1',DNF_MAX_LEVEL='86',
            GAME_DB_PASSWORD='aB12cD34',ADMIN_PASSWORD='DisposableWebTest1234',ADMIN_TEMP_SECRET='DisposableTemporary123',LAUNCHER_MODE='native')
assert database.initialize_external(instance,values,query)==identity
assert query('SELECT value FROM unrelated_site.sentinel;')=='keep'
assert query("SELECT COUNT(*) FROM information_schema.TABLES WHERE TABLE_SCHEMA='dof_native';")=='23'
assert query("SELECT database_uuid FROM dnf_service.DOF_INSTANCE_BINDING WHERE instance_id='dof-native86';")==identity
assert query("SELECT COUNT(*) FROM d_taiwan.accounts WHERE accountname='admin' AND admin=1;")=='1'
query("UPDATE dof_native.config SET name='preserve-after-restart' WHERE ID=1;")
assert database.initialize_external(instance,values,query)==identity
assert database.prepare_web(instance,values,query)==identity
assert query('SELECT name FROM dof_native.config WHERE ID=1;')=='preserve-after-restart'
other=base/'other86';other.mkdir()
try: database.initialize_external(other,values,query)
except ValueError: pass
else: raise AssertionError('Existing game DB accepted for another instance')
os.environ.update(DOF_DB_HOST='127.0.0.1',DOF_DB_PORT='3307',DOF_DATABASE_UUID=identity,
                  DNF_DB_GAME_PASSWORD=values['GAME_DB_PASSWORD'],DOF_BACKUP_DATABASES=json.dumps(database.DATABASES))
external_database.check()
# Scoped game cannot read or overwrite another site's DB or mysql.user.
for sql in ['SELECT * FROM unrelated_site.sentinel;','SELECT User FROM mysql.user;','CREATE DATABASE outside_scope;', 'CREATE DATABASE dxtaiwan;' ]:
 try: external_database.query(sql)
 except ValueError: pass
 else: raise AssertionError('Privilege escaped project scope')
os.environ['DEC_GAME_PWD']=subprocess.check_output(['/tmp/TeaEncrypt-run',values['GAME_DB_PASSWORD']],text=True).strip()
external_database.configure()
assert query('SELECT COUNT(*) FROM d_channel_cain.ch_dungeon_data;') != '0'
assert query("SELECT COUNT(*) FROM d_taiwan.db_connect WHERE db_server_group=3 AND db_passwd<>'';")!='0'
wrong=dict(os.environ,DOF_DATABASE_UUID='22222222-2222-2222-2222-222222222222')
proc=subprocess.run([sys.executable,'/tmp/repo/services/game/runtime/ubuntu/external_database.py','check'],env=wrong,capture_output=True)
assert proc.returncode!=0
out=base/'project.sql'
with out.open('wb') as stream:
 subprocess.run([sys.executable,'/tmp/repo/services/game/runtime/ubuntu/external_database.py','dump'],stdout=stream,check=True)
text=out.read_bytes();assert b'unrelated_site' not in text and b'CREATE DATABASE /*!32312 IF NOT EXISTS*/ `mysql`' not in text
assert b'dof_native' in text and b'preserve-after-restart' in text
result = dict(mysqlVersion=query('SELECT VERSION();'),freshNativeTables=23,secondInitializationPreserved=True,
 menuConnection=menu_result,
 unrelatedSitePreserved=True,scopedGrants=True,wrongUuidRejected=True,existingSchemasRejected=True,
 gameConnectionConfigured=True,projectBackupBytes=len(text),systemAndUnrelatedExcluded=True)

# Restore only into the same disposable namespace, never an operator DB.
query("UPDATE dof_native.config SET name='changed-after-backup' WHERE ID=1;")
query(out.read_text())
assert query('SELECT name FROM dof_native.config WHERE ID=1;')=='preserve-after-restart'
assert query('SELECT value FROM unrelated_site.sentinel;')=='keep'
result['logicalRestoreVerified']=True
# The same additive preparer is used by internal native installs.
internal=dict(values,DOF_DATABASE_KIND='internal')
assert database.prepare_web(instance,internal,query)==identity
result['internalNativePreparationVerified']=True
if Path('/tmp/jdk/bin/java').exists():
    import urllib.request, urllib.error, time, uuid
    env=dict(os.environ,SPRING_DATASOURCE_URL='jdbc:mysql://127.0.0.1:3307/dnf_service?useSSL=false&useUnicode=true&characterEncoding=utf8',
        SPRING_DATASOURCE_USERNAME='game',SPRING_DATASOURCE_PASSWORD=values['GAME_DB_PASSWORD'],SERVER_PORT='19001',
        DOF_ADMIN_ENABLED='true',DOF_ENVIRONMENT='isolated',DOF_INSTANCE_ID='dof-native86',DOF_DATABASE_UUID=identity,
        DOF_ADAPTER_VERSION='dof-native-1',LAUNCHER_MODE='native',DOF_CLIENT_WORKSPACE='/tmp/output/web-publication')
    env.pop('LD_LIBRARY_PATH',None)
    log=(base/'web.log').open('w')
    web=subprocess.Popen(['/tmp/jdk/bin/java','-Xmx256m','-XX:ActiveProcessorCount=2','-Djava.io.tmpdir=/tmp',
        '-cp','/tmp/deps/*:/tmp/classes:/tmp/repo/services/admin/backend/src/main/resources',
        'com.aiyi.game.dnfserver.DnfServerApplication'],env=env,cwd=base,stdout=log,stderr=subprocess.STDOUT)
    def request(path,method='GET',payload=None,token=None):
        headers={'Content-Type':'application/json'}
        if token:headers['token']=token
        req=urllib.request.Request('http://127.0.0.1:19001/api/v1/'+path,
            data=None if payload is None else json.dumps(payload).encode(),headers=headers,method=method)
        with urllib.request.urlopen(req,timeout=10) as response:
            data=response.read().decode()
            try:return json.loads(data)
            except ValueError:return data
    try:
        for _ in range(90):
            if web.poll() is not None:raise AssertionError('Web exited; inspect private web.log')
            try:
                request('admin/assist/config-text');break
            except (OSError,urllib.error.URLError):time.sleep(1)
        else:raise AssertionError('Web readiness timeout')
        token=request('login','POST',{'account':'admin','password':values['ADMIN_PASSWORD']})
        assert isinstance(token,str) and len(token)==32, 'Web login failed'
        home=request('admin/dof/home',token=token)
        changed=dict(home['values'],name='Local MySQL 5.6 Web Test')
        edit=dict(revision=home['revision'],requestId=str(uuid.uuid4()),values=changed)
        saved=request('admin/dof/home','PUT',edit,token)
        assert saved['persisted'], 'Web write was not persisted'
        assert query("SELECT state FROM dnf_service.DOF_TEXT_AUDIT WHERE request_id='"+edit['requestId']+"';")=='PERSISTED'
        assert request('admin/dof/home',token=token)['values']['name']==changed['name']
        try:request('admin/dof/home','PUT',dict(edit,requestId=str(uuid.uuid4())),token)
        except urllib.error.HTTPError as error:assert error.code==409
        else:raise AssertionError('Stale revision accepted')
        result['realWebLoginReadWriteAuditAndConflict']=True
    finally:
        web.terminate()
        try:web.wait(timeout=20)
        except subprocess.TimeoutExpired:web.kill();web.wait()
        log.close()
(base/'result.json').write_text(json.dumps(result,indent=2)+'\n')
print(json.dumps(result))
