import os,sys,io,contextlib,json,subprocess,hashlib,time,uuid
from pathlib import Path
out=Path('/opt/dof-easy/.dof-easy/admin-replacement/20261027-s6-frida');root=Path('/opt/dof-easy/.dof-easy/instances/main');marker=out/'fault-private-state.json';public_result=out/'fault-result.json'
def cmd(*args):return subprocess.check_output(args,universal_newlines=True).strip()
def inspect(name):return json.loads(cmd('docker','inspect',name))[0]
def sha(p):return hashlib.sha256(p.read_bytes()).hexdigest()
def save():
 tmp=marker.with_suffix('.tmp');fd=os.open(tmp,os.O_WRONLY|os.O_CREAT|os.O_TRUNC|os.O_NOFOLLOW,0o600)
 with os.fdopen(fd,'w') as f:json.dump(state,f,indent=2)
 tmp.replace(marker)
assert not marker.exists(),'Do not replace previous fault state; reconcile it first'
game=inspect('dof-main-game-1');admin=inspect('dof-main-admin-1');assert game['Image']=='sha256:e417c4162b41d1ca013d75b547896d343fda8465b452dc8b34f5aff62e9e6c07' and game['State']['Health']['Status']=='healthy'
state={'operationId':str(uuid.uuid4()),'phase':'PREPARED','gameId':game['Id'],'adminId':admin['Id'],'gameImage':game['Image']};save()
os.chdir('/www/server/panel');sys.path.insert(0,'/www/server/panel/class')
with contextlib.redirect_stdout(io.StringIO()),contextlib.redirect_stderr(io.StringIO()):
 import public
 password=public.M('config').where('id=?',(1,)).getField('mysql_root')
def query(sql):
 r=subprocess.run(['/www/server/mysql/bin/mysql','--no-defaults','--protocol=SOCKET','-uroot','-N','-B','-r','--default-character-set=utf8'],input=sql,universal_newlines=True,stdout=subprocess.PIPE,stderr=subprocess.PIPE,env=dict(os.environ,MYSQL_PWD=password),timeout=30)
 if r.returncode:raise RuntimeError('Scoped fault-check query failed; credentials and SQL withheld')
 return r.stdout.strip()
def identity():assert query('SELECT @@server_uuid;')=='899d147e-d0ef-11f1-9ff4-525400542f67'
def row():return query("SELECT IF(event_info IS NULL,'NULL',CONCAT('HEX:',HEX(event_info))) FROM frida.game_event WHERE event_id='villageattack';")
def logs():
 started=inspect('dof-main-game-1')['State']['StartedAt']
 return subprocess.run(['docker','logs','--since',started,'dof-main-game-1'],stdout=subprocess.PIPE,stderr=subprocess.STDOUT,check=True).stdout.decode(errors='replace')
def wait(kind):
 for _ in range(300):
  obj=inspect('dof-main-game-1');text=logs()
  if obj['State'].get('Health',{}).get('Status')=='healthy':
   if kind=='fault' and text.count('[Frida DB] activity data could not be loaded; existing progress retained')==2:break
   if kind=='good' and text.count('[Frida DB] activity storage ready')==2:break
  time.sleep(2)
 else:raise RuntimeError('Native startup evidence not observed')
 assert '"type":"error"' not in text and 'name:frida' not in text
 state[kind+'Boot']=obj['State']['StartedAt'];state[kind+'EvidenceCount']=2;save();return text
identity();initial=logs();assert initial.count('[Frida DB] activity storage ready')==2
keypaths=[root/'runtime/game/data/publickey.pem',root/'runtime/game/data/dof-easy/keys/privatekey.pem'];keys={str(p):sha(p) for p in keypaths}
injected=False;restored=False
try:
 cmd('docker','stop','--time','60','dof-main-game-1');identity();before=row();state['originalRow']=before;state['originalRowCapturedAfterNormalStop']=True;save()
 sentinel='DOF_S6_INVALID_ACTIVITY_JSON_'+state['operationId'];encoded=sentinel.encode().hex().upper();state['injectedHex']=encoded;state['phase']='INJECTING';save()
 if not before:
  statement="INSERT INTO frida.game_event(event_id,event_info) SELECT 'villageattack',UNHEX('"+encoded+"') FROM DUAL WHERE NOT EXISTS(SELECT 1 FROM frida.game_event WHERE event_id='villageattack');"
 else:
  condition='event_info IS NULL' if before=='NULL' else "HEX(event_info)='"+before[4:]+"'"
  statement="UPDATE frida.game_event SET event_info=UNHEX('"+encoded+"') WHERE event_id='villageattack' AND "+condition+';'
 assert query(statement+'SELECT ROW_COUNT();')=='1';injected=True;assert row()=='HEX:'+encoded;state['phase']='INJECTED';save()
 cmd('docker','start','dof-main-game-1');wait('fault');assert row()=='HEX:'+encoded;state['badRowRetainedAfterStartup']=True;save()
 cmd('docker','stop','--time','60','dof-main-game-1');identity();assert row()=='HEX:'+encoded;state['badRowRetainedAfterStop']=True;save()
finally:
 if injected:
  obj=inspect('dof-main-game-1')
  if obj['State']['Running']:cmd('docker','stop','--time','60','dof-main-game-1')
  identity();state['phase']='RESTORING';save();assert row()=='HEX:'+state['injectedHex'],'Row changed outside the probe; refuse overwrite'
  if not state['originalRow']:statement="DELETE FROM frida.game_event WHERE event_id='villageattack' AND HEX(event_info)='"+state['injectedHex']+"';"
  else:
   value='NULL' if state['originalRow']=='NULL' else "UNHEX('"+state['originalRow'][4:]+"')"
   statement="UPDATE frida.game_event SET event_info="+value+" WHERE event_id='villageattack' AND HEX(event_info)='"+state['injectedHex']+"';"
  assert query(statement+'SELECT ROW_COUNT();')=='1';assert row()==state['originalRow'];restored=True;state['phase']='RESTORED';save()
 obj=inspect('dof-main-game-1')
 if not obj['State']['Running']:cmd('docker','start','dof-main-game-1')
assert restored;wait('good');assert inspect('dof-main-admin-1')['Id']==admin['Id'];assert inspect('dof-main-game-1')['Id']==game['Id'];assert {str(p):sha(p) for p in keypaths}==keys
state['phase']='VERIFIED';save()
r={'passed':True,'operationId':state['operationId'],'databaseUuid':'899d147e-d0ef-11f1-9ff4-525400542f67','twoChannelsObservedMalformedLoadWithoutNativeError':True,'malformedRowPreservedThroughStartupAndStop':True,'originalSingleRowRestoredExactly':True,'healthyStorageReadyAfterRestore':True,'adminNotRestarted':True,'sameGameContainerAndImage':True,'privateAndPublicKeyBytesUnchanged':True,'gameplayOrRewardsVerified':False,'activityEnabledByProbe':False,'phaseBoots':{k:state[k] for k in ['faultBoot','goodBoot']},'checkpointDisposition':'Actual stored row captured after initial normal stop; raw checkpoint kept only in private state file'};public_result.write_text(json.dumps(r,indent=2)+'\n');print(json.dumps(r))
