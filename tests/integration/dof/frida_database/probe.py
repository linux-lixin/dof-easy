"""Real isolated MySQL 5.6 contract test; no vendor tool or game execution."""
from pathlib import Path
import importlib.util,json,subprocess,os,secrets,hashlib
spec=importlib.util.spec_from_file_location('db','/inputs/repo/scripts/container/database.py');db=importlib.util.module_from_spec(spec);spec.loader.exec_module(db)
client=['/inputs/mysql/usr/bin/mysql','--no-defaults','--socket=/tmp/mysql-run/mysql.sock','-uroot','-N','-B','-r','--default-character-set=utf8']
def query(sql):
 r=subprocess.run(client,input=sql,text=True,capture_output=True,timeout=20)
 if r.returncode:raise RuntimeError('Isolated MySQL query failed; SQL/credentials withheld')
 return r.stdout.strip()
identity=db.identity(query);root=Path('/output/instance');root.mkdir();host='172.29.86.0/255.255.255.0';password=secrets.token_hex(4)
values=dict(PLUGIN_MODE='dp2-frida',DOF_DATABASE_UUID=identity,DOF_DB_HOST='172.17.0.1',DOF_DB_PORT='3306',DOF_DB_ALLOW_HOST=host)
db.private_json(root/'external-database.json',dict(databaseUuid=identity,instanceId='dof-instance',host=values['DOF_DB_HOST'],port='3306',grantHost=host,state='ready',databases=list(db.DATABASES)))
query("CREATE USER 'game'@'"+host+"' IDENTIFIED BY '"+password+"';")
assert db.prepare_frida(root,values,query,identity)
operation=json.load(open(root/'plugin-databases.json'))['operationId']
content='{"round":7,"progress":"测试进度不清空","claimed":[1,3]}'
query("INSERT INTO frida.game_event VALUES ('villageattack',CONVERT(0x"+content.encode().hex()+" USING utf8));")
before=query('SELECT HEX(event_id),HEX(event_info) FROM frida.game_event ORDER BY event_id;')
assert db.prepare_frida(root,values,query,identity);assert json.load(open(root/'plugin-databases.json'))['operationId']==operation
assert query('SELECT HEX(event_id),HEX(event_info) FROM frida.game_event ORDER BY event_id;')==before
# Confirm schema-scoped privileges for the actual target account, without publishing password hashes.
grants=query("SHOW GRANTS FOR 'game'@'"+host+"';").splitlines();scoped=[s for s in grants if ' ON `frida`.* ' in s]
assert len(scoped)==1 and 'LOCK TABLES' in scoped[0] and 'CREATE' in scoped[0] and 'DROP' not in scoped[0] and 'GRANT OPTION' not in scoped[0]
assert all('GRANT USAGE ON *.*' in s or ' ON `frida`.* ' in s for s in grants)
# Namespace has only loopback. Independently exercise these exact permissions over
# the local socket; target Docker subnet routing remains a real-host acceptance item.
query("CREATE USER 'game'@'localhost' IDENTIFIED BY '"+password+"';")
query("GRANT SELECT, INSERT, UPDATE, DELETE, CREATE, LOCK TABLES ON frida.* TO 'game'@'localhost';")
env=dict(os.environ,MYSQL_PWD=password)
cmd=client.copy();cmd[3]='-ugame'
r=subprocess.run(cmd,input='CREATE DATABASE IF NOT EXISTS frida;CREATE TABLE IF NOT EXISTS frida.game_event (event_id varchar(30) NOT NULL,event_info mediumtext NULL,PRIMARY KEY(event_id)) ENGINE=InnoDB DEFAULT CHARSET=utf8;SELECT HEX(event_info) FROM frida.game_event;',env=env,text=True,capture_output=True,timeout=20)
assert r.returncode==0 and content.encode().hex().upper() in r.stdout
r=subprocess.run(['/inputs/mysql/usr/bin/mysqldump','--no-defaults','--socket=/tmp/mysql-run/mysql.sock','-ugame','--lock-tables','--hex-blob','--databases','frida'],env=env,capture_output=True,timeout=20)
assert r.returncode==0
assert query('SELECT HEX(event_id),HEX(event_info) FROM frida.game_event ORDER BY event_id;')==before
assert db.initialize_external(root,values,query)==identity
assert 'frida' in db.backup_databases(root,dict(values,PLUGIN_MODE='none'))
# Unknown columns, indexes, triggers and foreign keys must stop without changing rows.
refusals=[]
for setup,cleanup,label in [
 ('ALTER TABLE frida.game_event ADD custom_value INT NULL;','ALTER TABLE frida.game_event DROP custom_value;','unknown-column'),
 ('CREATE UNIQUE INDEX extra_unique ON frida.game_event(event_id,event_info(5));','DROP INDEX extra_unique ON frida.game_event;','extra-unique-index'),
 ("CREATE TRIGGER frida.unknown_trigger BEFORE INSERT ON frida.game_event FOR EACH ROW SET NEW.event_info='must-not-run';",'DROP TRIGGER frida.unknown_trigger;','unknown-trigger')]:
 query(setup)
 try:db.prepare_frida(root,values,query,identity)
 except ValueError:refusals.append(label)
 else:raise AssertionError('Unknown schema was accepted')
 assert query('SELECT HEX(event_id),HEX(event_info) FROM frida.game_event ORDER BY event_id;')==before
 query(cleanup)
query('CREATE TABLE frida.reference_ids (event_id varchar(30) NOT NULL PRIMARY KEY) ENGINE=InnoDB DEFAULT CHARSET=utf8;INSERT INTO frida.reference_ids SELECT event_id FROM frida.game_event;ALTER TABLE frida.game_event ADD CONSTRAINT unknown_ref FOREIGN KEY(event_id) REFERENCES frida.reference_ids(event_id);')
try:db.prepare_frida(root,values,query,identity)
except ValueError:refusals.append('unknown-foreign-key')
else:raise AssertionError('Unknown foreign key was accepted')
assert query('SELECT HEX(event_id),HEX(event_info) FROM frida.game_event ORDER BY event_id;')==before
query('ALTER TABLE frida.game_event DROP FOREIGN KEY unknown_ref;')
result={'passed':True,'databaseVersion':query('SELECT VERSION();'),'freshTableAndScopedGrant':True,'existingUtf8ProgressBytePreserved':True,'repeatOperationIdPreserved':True,'scopedLockTablesDumpPassed':True,'dumpSHA256':hashlib.sha256(r.stdout).hexdigest(),'unknownSchemaRefusals':refusals,'nativeFridaOrGameVerified':False,'targetSubnetRoutingVerified':False}
Path('/output/frida-database-result.json').write_text(json.dumps(result,indent=2)+'\n');print(json.dumps(result))
