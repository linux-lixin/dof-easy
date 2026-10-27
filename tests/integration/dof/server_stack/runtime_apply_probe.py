"""Exercise the production mailbox on the real game thread in the private fixture."""
import fcntl
import hashlib
import json
import os
from pathlib import Path
import re
import tempfile
import time
import uuid

CONFIG=Path('/data/dof-easy/config')
MAIL=CONFIG/'.runtime-apply'


def atomic(path, raw):
    fd,name=tempfile.mkstemp(prefix='.fixture-',dir=path.parent)
    try:
        with os.fdopen(fd,'wb') as stream:
            stream.write(raw);stream.flush();os.fsync(stream.fileno())
        os.replace(name,path)
        fd=os.open(path.parent,os.O_RDONLY|os.O_DIRECTORY)
        try:os.fsync(fd)
        finally:os.close(fd)
    finally:Path(name).unlink(missing_ok=True)


def read(path):
    try:return json.loads(path.read_text())
    except FileNotFoundError:return None


def wait(check, timeout=20):
    end=time.monotonic()+timeout
    while time.monotonic()<end:
        result=check()
        if result:return result
        time.sleep(.2)
    raise RuntimeError('Production runtime mailbox observation timed out')


def fields(raw, module):
    text=raw.decode('utf-8');values={}
    for key in ['name','member_min','open_day','open_time']:
        match=re.search(r'^'+module+'_'+key+r'[ \t]*=[ \t]*([^\r\n]*)',text,re.M)
        if not match:raise ValueError('Fixture configuration field missing')
        values[key]=match.group(1).strip()
    return values


def exercise():
    result=dict(passed=False,serverAccessed=False,gameplayVerified=False,actions=[],filesRestored={})
    originals={m:(CONFIG/(m+'-raid.ini')).read_bytes() for m in ['anton','luke']}
    try:
        binding=read(MAIL/'binding.json');assert binding and binding['channels']==['local']
        binding_revision=hashlib.sha256((MAIL/'binding.json').read_bytes()).hexdigest()
        def status():
            value=read(MAIL/'status-local.json')
            return value if value and value.get('idle') and value['bindingRevision']==binding_revision and abs(time.time()*1000-value['updatedAt'])<15000 else None
        first=wait(status)
        def apply(module, raw, phase):
            before=wait(status);request=dict(schema=1,instanceId=binding['instanceId'],databaseUuid=binding['databaseUuid'],
                bindingRevision=binding_revision,requestId=str(uuid.uuid4()),module=module,
                revision=hashlib.sha256(raw).hexdigest(),submittedAt=int(time.time()*1000),expiresAt=int(time.time()*1000)+90000,
                expected=fields(raw,module),targets=[dict(channel='local',bootId=first['bootId'],pid=first['pid'],dispatcherThreadId=first['dispatcherThreadId'])])
            with (CONFIG/'.admin-operation.lock').open('a+b') as lock:
                fcntl.lockf(lock,fcntl.LOCK_EX)
                atomic(CONFIG/(module+'-raid.ini'),raw)
                atomic(MAIL/('request-'+module+'.json'),json.dumps(request).encode())
                time.sleep(3)
                pending=read(MAIL/('receipt-local-'+module+'.json'))
                excluded=pending is None or pending.get('requestId')!=request['requestId']
                assert excluded,'Native worker ignored the shared POSIX configuration lock'
            def completed():
                value=read(MAIL/('receipt-local-'+module+'.json'))
                return value if value and value.get('requestId')==request['requestId'] and value.get('state')!='RUNNING' else None
            receipt=wait(completed)
            assert receipt['state']=='APPLIED' and receipt['observed']==request['expected']
            after=wait(lambda:status() if status() and status()['nativeInvocationAttempts'][module]>before['nativeInvocationAttempts'][module] else None)
            assert after['nativeInvocationAttempts'][module]==before['nativeInvocationAttempts'][module]+1
            # Same request after completion must retain the original receipt and count.
            atomic(MAIL/('request-'+module+'.json'),json.dumps(request).encode());time.sleep(3)
            replay=wait(status)
            assert replay['nativeInvocationAttempts'][module]==after['nativeInvocationAttempts'][module]
            assert completed()['state']=='APPLIED'
            result['actions'].append(dict(module=module,phase=phase,revision=request['revision'],
                state=receipt['state'],observed=receipt['observed'],sharedLockExcludedExecution=excluded,
                duplicateDidNotInvokeAgain=True,gamePid=first['pid'],bootId=first['bootId'],executorThreadId=receipt['executorThreadId'],
                observedDispatcherThreadId=first['dispatcherThreadId']))
        for module,original in originals.items():
            wanted=dict(name='安徒恩网页刷新验证' if module=='anton' else '卢克网页刷新验证',member_min='3',open_day='2,4,6',open_time='01:02:03-04:05:06')
            text=original.decode('utf-8')
            for key,value in wanted.items():text=re.sub(r'(^'+module+'_'+key+r'[ \t]*=)[^\r\n]*',lambda m:m.group(1)+' '+value,text,flags=re.M)
            try:apply(module,text.encode(),'change')
            finally:apply(module,original,'restore')
        result['passed']=len(result['actions'])==4
    except Exception as error:
        result['error']=str(error)
    finally:
        for module,original in originals.items():
            # The private fixture always restores the raw file, even after a failed
            # consumer. This fallback alone never counts as native restoration.
            path=CONFIG/(module+'-raid.ini')
            if path.read_bytes()!=original:atomic(path,original)
            result['filesRestored'][module]=path.read_bytes()==original
        result['passed']=result['passed'] and all(result['filesRestored'].values())
        Path('/output/runtime-apply.json').write_text(json.dumps(result,ensure_ascii=True,indent=2)+'\n')
    return result
