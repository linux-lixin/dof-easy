from pathlib import Path
import hashlib,json,os,shutil,subprocess,time,urllib.request,urllib.error
OUT=Path('/opt/dof-easy/.dof-easy/admin-replacement/20261028-s33-validation');ROOT=Path('/opt/dof-easy/.dof-easy/instances/main');checkpoint=OUT/'restart-task-state.json';base='/api/v1/admin/activities/recovery';batch=base+'/batches';state={'phase':'INITIAL'};token=None
import uuid
assert not checkpoint.exists();assert shutil.disk_usage(OUT).free>2*1024**3+128*1024**2
for name in ['recovery','cross-border']:
 assert json.loads((OUT/(name+'-browser/result.json')).read_text())['passed'];assert not Path('/proc/'+(OUT/(name+'-browser.pid')).read_text().strip()).exists()
creds=json.loads(Path('/opt/dof-easy/.dof-easy/admin-replacement/20261027-s3-registration/test-admin.json').read_text())
def phase(value):
 state.update(phase=value,pid=os.getpid(),updatedAt=time.time());p=checkpoint.with_suffix('.tmp');p.write_text(json.dumps(state,indent=2)+'\n');os.chmod(p,0o600);p.replace(checkpoint)
def inspect(name):return json.loads(subprocess.check_output(['docker','inspect',name]))[0]
def api(method,path,data=None,status=200,raw=False):
 headers={'Content-Type':'application/json'}
 if token:headers['Authorization']='Bearer '+token
 payload=None if data is None else data if isinstance(data,bytes) else json.dumps(data).encode();request=urllib.request.Request('http://127.0.0.1:18080'+path,method=method,data=payload,headers=headers)
 try:
  with urllib.request.urlopen(request,timeout=60) as r:code=r.status;body=r.read()
 except urllib.error.HTTPError as e:code=e.code;body=e.read()
 assert code==status,(method,path,code,status)
 return body if raw else json.loads(body)
def root(id):return api('GET',batch+'/'+id+'?page=1&pageSize=20')
try:
 phase('PREFLIGHT');token=api('POST','/api/v1/login',creds,raw=True).decode().strip(chr(34));assert api('GET','/api/v1/admin/dof')['instanceId']=='dof-main';assert api('GET','/api/v1/recharge/6')['databaseUuid']=='899d147e-d0ef-11f1-9ff4-525400542f67'
 for section in ['inheritance','recovery','cross-border']:assert api('GET','/api/v1/admin/activities/'+section+'?page=1&pageSize=100')['rows']==[]
 identities=json.loads((OUT/'before/runtime-identities.json').read_text());admin,game=inspect('dof-main-admin-1'),inspect('dof-main-game-1');assert admin['Id']==identities['admin']['id'] and game['Id']==identities['game']['id'];assert (ROOT/'deployment.json').read_bytes()==(OUT/'before/deployment.json').read_bytes();assert (ROOT/'.env').read_bytes()==(OUT/'before/.env').read_bytes()
 source=(OUT/'recovery-browser/private-rule-source.json').read_bytes();business=json.loads((OUT/'recovery-browser/result.json').read_text());assert hashlib.sha256(source).hexdigest()==business['sourceSHA256']
 snapshot=api('GET',batch+'/state');request={'requestId':str(uuid.uuid4()),'revision':snapshot['revision'],'reason':'S33 actual restart after confirmed twenty native configuration writes'};state['request']=request;phase('IMPORT_PREVIEW_REQUESTED')
 from urllib.parse import urlencode
 original=api('POST',batch+'/import/preview?'+urlencode(request),source);assert original['state']=='PREVIEWED';state['originalPreviewRevision']=original['revision'];phase('IMPORT_STEP_REQUESTED');partial=api('POST',batch+'/'+request['requestId']+'/step',{'revision':original['revision']});assert partial['state']=='PREPARED' and int(partial['summary']['counts']['PERSISTED'])==20 and int(partial['summary']['counts']['PREVIEWED'])==1
 state['partialRevision']=partial['revision'];state['partialDetailSHA256']=hashlib.sha256(api('GET',batch+'/'+request['requestId']+'?page=1&pageSize=20',raw=True)).hexdigest();state['beforeRowsSHA256']=hashlib.sha256(api('GET',base+'?page=1&pageSize=100',raw=True)).hexdigest();state['beforeAdminId']=admin['Id'];phase('PARTIAL_TASK_PERSISTED_AWAITING_RECREATE')
 with (OUT/'restart-recreate.log').open('x') as log:subprocess.run(['docker','compose','-p','dof-main','-f',str(ROOT/'deployment.json'),'up','-d','--no-deps','--force-recreate','admin'],stdout=log,stderr=subprocess.STDOUT,check=True)
 for i in range(45):
  try:
   token=None;token=api('POST','/api/v1/login',creds,raw=True).decode().strip(chr(34));after=root(request['requestId']);break
  except Exception:time.sleep(2)
 else:raise RuntimeError('Recreated admin not ready; keep original task ID and checkpoint')
 current=inspect('dof-main-admin-1');assert current['Id']!=admin['Id'] and current['Image']==admin['Image'];assert inspect('dof-main-game-1')['Id']==game['Id'] and inspect('dof-main-game-1')['State']['StartedAt']==game['State']['StartedAt'];state['afterAdminId']=current['Id'];phase('RECREATED_ORIGINAL_TASK_RELOADED')
 assert after['state']=='PREPARED' and after['revision']==state['partialRevision'];assert int(after['summary']['counts']['PERSISTED'])==20 and int(after['summary']['counts']['PREVIEWED'])==1
 assert hashlib.sha256(api('GET',batch+'/'+request['requestId']+'?page=1&pageSize=20',raw=True)).hexdigest()==state['partialDetailSHA256'];assert hashlib.sha256(api('GET',base+'?page=1&pageSize=100',raw=True)).hexdigest()==state['beforeRowsSHA256']
 assert api('POST',batch+'/import/preview?'+urlencode(request),source)['revision']==after['revision'];api('POST',batch+'/'+request['requestId']+'/step',{'revision':state['originalPreviewRevision']},409)
 rejected=dict(request,requestId=str(uuid.uuid4()),revision=api('GET',batch+'/state')['revision']);state['rejectedDuplicateId']=rejected['requestId'];phase('VERIFYING_DUPLICATE_REFUSAL');api('POST',batch+'/import/preview?'+urlencode(rejected),source,409);api('GET',batch+'/'+rejected['requestId'],status=404)
 control={'requestId':str(uuid.uuid4()),'revision':after['revision'],'operation':'cancel','reason':'S33 stop only the never-submitted item after actual admin recreation'};state['control']=control;phase('CANCELLATION_REQUESTED');api('POST',batch+'/'+request['requestId']+'/controls/preview',control);assert api('POST',batch+'/'+request['requestId']+'/controls',control)['state']=='NO_CHANGE';assert api('POST',batch+'/'+request['requestId']+'/controls',control)['state']=='NO_CHANGE';ended=root(request['requestId']);assert ended['state']=='PERSISTED' and int(ended['summary']['counts']['PERSISTED'])==20 and int(ended['summary']['counts']['NOT_APPLIED'])==1
 restore={'requestId':str(uuid.uuid4()),'revision':api('GET',batch+'/state')['revision'],'reason':'S33 undo only twenty confirmed config imports after restart','sourceTaskId':request['requestId']};state['restore']=restore;phase('RESTORATION_PREVIEW_REQUESTED');prepared=api('POST',batch+'/restore/preview',restore);assert int(prepared['summary']['total'])==20;phase('RESTORATION_STEP_REQUESTED');done=api('POST',batch+'/'+restore['requestId']+'/step',{'revision':prepared['revision']});assert done['state']=='PERSISTED'
 for section in ['inheritance','recovery','cross-border']:assert api('GET','/api/v1/admin/activities/'+section+'?page=1&pageSize=100')['rows']==[]
 assert (ROOT/'deployment.json').read_bytes()==(OUT/'before/deployment.json').read_bytes() and (ROOT/'.env').read_bytes()==(OUT/'before/.env').read_bytes();assert shutil.disk_usage(OUT).free>2*1024**3
 result={'passed':True,'actualAdminContainerRecreated':True,'imageUnchanged':True,'gameNotRecreated':True,'partialTaskAndNativeRowsUnchangedAcrossRestart':True,'noAutomaticReplayOrUnexpectedLastItem':True,'originalPreviewReplayReturnsSameProgress':True,'staleSegmentAndNewDuplicateIdRefused':True,'cancelThenRestoreOnlyTwentyConfirmedRows':True,'threeRuleTablesEmpty':True,'taskId':request['requestId'],'restoreTaskId':restore['requestId'],'controlId':control['requestId'],'rejectedDuplicateId':rejected['requestId'],'beforeAdminId':admin['Id'],'afterAdminId':current['Id'],'adminImage':current['Image'],'originalPreviewRevision':state['originalPreviewRevision'],'partialRevision':state['partialRevision'],'nativeGameplayVerified':False,'completeReplacement':False}
 (OUT/'restart-task-result.json').write_text(json.dumps(result,indent=2)+'\n');phase('COMPLETED');print(json.dumps(result))
except BaseException as error:state['failureType']=type(error).__name__;phase('FAILED');raise
