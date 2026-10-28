"""Real dual-channel fixed refresh acceptance; mutates only member_min, then restores.

This checks component consumption, not raid entry/progression/rewards. It stores
operation IDs before submitting. Unknown submissions are never resent under new
IDs. If interrupted, reconcile live-state.json before starting any new run.
Only definite shared-lock conflicts retry the exact same payload and ID.
"""
import json,urllib.request,urllib.error,time,uuid,hashlib,sys,os
from pathlib import Path
import argparse, urllib.parse
parser=argparse.ArgumentParser(description='Opt-in real native raid refresh/change/restore acceptance. Run on an identity-bound isolated test server. Credentials stay on that server. Not full gameplay acceptance.')
for name in ['origin','credentials','mailbox','output','instance']:parser.add_argument('--'+name,required=True)
args=parser.parse_args();url=urllib.parse.urlparse(args.origin)
assert url.scheme=='http' and url.hostname in ('127.0.0.1','localhost','::1') and url.path in ('','/')
base=args.origin.rstrip('/');out=Path(args.output);mail=Path(args.mailbox)
out.mkdir(parents=True,exist_ok=True);assert not (out/'live-state.json').exists(), 'Preserve/reconcile existing run; never replace a prior state file'
credential=json.loads(Path(args.credentials).read_text());token=None
def call_once(method,path,data=None,expected=200):
 headers={'Content-Type':'application/json'}
 if token:headers['Authorization']='Bearer '+token
 try:
  with urllib.request.urlopen(urllib.request.Request(base+path,data=None if data is None else json.dumps(data).encode(),headers=headers,method=method),timeout=30) as r:status=r.status;raw=r.read()
 except urllib.error.HTTPError as e:status=e.code;raw=e.read()
 if status!=expected:
  try:message=json.loads(raw).get('message','HTTP failure')
  except Exception:message='HTTP failure'
  raise RuntimeError(f'{method} {path}: {status} {message}')
 try:return json.loads(raw)
 except ValueError:return raw.decode()
def call(method,path,data=None,expected=200):
 for attempt in range(30):
  try:
   result=call_once(method,path,data,expected)
   if expected==409 and isinstance(result,dict) and str(result.get('message','')).startswith('另一个'):
    if attempt==29:raise RuntimeError('Shared lock remained busy; cannot attribute rejection to the expected conflict')
    time.sleep(0.07+(time.time()%0.13));continue
   return result
  except RuntimeError as error:
   if expected!=200 or '409 另一个' not in str(error) or attempt==29:raise
   time.sleep(0.07+(time.time()%0.13))
token=call('POST','/api/v1/login',{'account':credential['account'],'password':credential['password']});assert isinstance(token,str) and len(token)==32
root='/api/v1/admin/dof/plugins';state={'instanceId':args.instance,'actions':[],'checks':[],'completeReplacement':False}
statepath=out/'live-state.json';resultpath=out/'live-result.json'
def persist():
 tmp=statepath.with_suffix('.tmp');tmp.write_text(json.dumps(state,indent=2,ensure_ascii=False)+'\n');os.chmod(tmp,0o600);tmp.replace(statepath)
def get(m):return call('GET',root+'/'+m)
def counts():
 rows={p.name:json.load(open(p))['nativeInvocationAttempts'] for p in mail.glob('status-*.json')};assert len(rows)==2;return rows
def apply(m,label):
 view=get(m);p=root+'/'+m;request={'requestId':str(uuid.uuid4()),'revision':view['revision']};state['actions'].append({'module':m,'label':label,'request':request});persist();a=state['actions'][-1]
 preview=call('POST',p+'/runtime/preview',request);assert len(preview['targets'])==2
 a['previewTargets']=preview['targets'];persist();a['submitted']=True;persist()
 submitted=call('POST',p+'/runtime',request);assert submitted['requestId']==request['requestId']
 for _ in range(25):
  result=call('POST',p+'/runtime/'+request['requestId']+'/reconcile')
  if result['state'] in ['APPLIED','REJECTED','UNKNOWN']:break
  time.sleep(1)
 a['result']=result;persist();assert result['state']=='APPLIED',result['state'];assert len(result['receipts'])==2
 for receipt in result['receipts']:assert receipt['state']=='APPLIED' and receipt['observed']==result['expected']
 time.sleep(3);before=counts();again=call('POST',p+'/runtime',request);assert again['requestId']==request['requestId'];time.sleep(3);after=counts();assert after==before
 a['duplicateNoExtraNativeCall']=True;a['invocationCountsAfter']=after;persist()
 rejected=call('POST',p+'/runtime',dict(request,revision='0'*64),409)
 assert '编号' in rejected['message'] and '不同' in rejected['message']
 return result
for _ in range(100):
 try:
  v=get('anton')
  if v['application']['webExecutionSupported']:break
 except Exception:pass
 time.sleep(2)
else:raise RuntimeError('Native runtime did not become ready')
assert call('GET','/api/v1/admin/dof')['instanceId']==args.instance
binding=json.loads((mail/'binding.json').read_text());assert binding['instanceId']==args.instance
state['initialCounts']=counts();persist()
for m in ['anton','luke']:
 p=root+'/'+m;original=get(m);field=next(f for f in original['fields'] if f['key']==m+'_member_min' and f['available']);new='2' if field['value']=='1' else '1'
 edit={'requestId':str(uuid.uuid4()),'revision':original['revision'],'values':{m+'_member_min':new}}
 state[m]={'originalRevision':original['revision'],'edit':edit};persist();saved=False
 try:
  call('POST',p+'/preview',edit);r=call('PUT',p,edit);assert r['persisted'];saved=True;state[m]['saved']=True;persist()
  rejected=call('POST',p+'/runtime/preview',{'requestId':str(uuid.uuid4()),'revision':original['revision']},409)
  assert '配置版本已改变' in rejected['message']
  r=apply(m,'changed');assert r['expected']['member_min']==new
 finally:
  if saved:
   current=get(m);restore={'requestId':str(uuid.uuid4()),'revision':current['revision']};state[m]['restoreRequest']=restore;persist()
   r=call('POST',p+'/history/'+edit['requestId']+'/restore',restore);assert r['persisted'];assert get(m)['revision']==original['revision'];state[m]['fileRestored']=True;persist()
   if not any(a.get('submitted') and a.get('result',{}).get('state')!='APPLIED' for a in state['actions']):
    r=apply(m,'restored');assert r['expected']['member_min']==str(int(field['value']));state[m]['nativeRestored']=True;persist()
state['checks']=['real administrator login','two-channel ready and bound','saved config change and native readback for both raids','targeted file and native restore for both raids','same request ID no repeated native calls','different payload rejected','old config revision rejected']
state['finalCounts']=counts();state['pageBrowserVerified']=False;persist();resultpath.write_text(json.dumps(state,indent=2,ensure_ascii=False)+'\n');print(json.dumps({'actions':len(state['actions']),'checks':state['checks'],'counts':state['finalCounts']}))
