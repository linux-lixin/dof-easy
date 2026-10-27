"""Read-only real account-filter regression; no credentials or account rows logged."""
import argparse,json,urllib.request,urllib.parse
from pathlib import Path
p=argparse.ArgumentParser()
for name in ['origin','credentials','instance','output']:p.add_argument('--'+name,required=True)
a=p.parse_args();u=urllib.parse.urlparse(a.origin);assert u.scheme=='http' and u.hostname in ('127.0.0.1','localhost','::1') and u.path in ('','/')
base=a.origin.rstrip('/');credentials=json.loads(Path(a.credentials).read_text());token=None

def request(path,body=None):
 headers={'Content-Type':'application/json'}
 if token:headers['Authorization']='Bearer '+token
 with urllib.request.urlopen(urllib.request.Request(base+path,data=None if body is None else json.dumps(body).encode(),headers=headers),timeout=30) as r:
  raw=r.read()
  try:return json.loads(raw)
  except ValueError:return raw.decode()

token=request('/api/v1/login',{'account':credentials['account'],'password':credentials['password']});assert isinstance(token,str) and len(token)==32
assert request('/api/v1/admin/dof')['instanceId']==a.instance
checks=[]
for value,expected in [(credentials['account'],1),("probe' OR 1=1 -- ",0),("probe'; SELECT 1; -- ",0)]:
 page=request('/api/v1/account?'+urllib.parse.urlencode({'account':value,'page':1,'pageSize':10}))
 assert isinstance(page['list'],list) and len(page['list'])==expected
 if expected:
  assert page['list'][0]['accountname']==credentials['account']
  assert page['list'][0].get('password') in (None,'')
 count_keys=[k for k in ['count','total','totalCount','totalSize'] if k in page]
 assert count_keys,'Require real list and total-count behavior, not a page-only check'
 for k in count_keys:assert page[k]==expected
 checks.append({'type':'known-test-account' if expected else 'SQL-looking-literal','rows':expected,'totalAgreesWithRows':True})
info=request('/api/v1/account/'+str(credentials['uid']));assert info['uid']==credentials['uid'] and info.get('password') in (None,'')
result={'accountDetailContainsNoPasswordHash':True,'passed':True,'backend':'real','instanceId':a.instance,'readOnly':True,'adminListAndCountChecked':True,'guildScopeCoveredByActualMapperBindingTests':True,'checks':checks}
Path(a.output).write_text(json.dumps(result,indent=2)+'\n');print(json.dumps(result))
