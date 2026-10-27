"""Explicit, staged registration acceptance on a bound test admin via an SSH tunnel.

Only configure mutates the fixed registration settings. Always finish with restore.
The state file is private: it contains test passwords and an operational code.
No CAPTCHA bypass or synthetic endpoint is added to the deployed application.
"""
import argparse
import json
import os
from pathlib import Path
import secrets
import urllib.error
import urllib.parse
import urllib.request
import uuid


def main():
    p=argparse.ArgumentParser(description=__doc__)
    p.add_argument('--origin',required=True);p.add_argument('--credentials',type=Path,required=True)
    p.add_argument('--instance',required=True);p.add_argument('--state',type=Path,required=True)
    p.add_argument('--phase',choices=('configure','submit','verify','restore','account-actions'),required=True)
    p.add_argument('--captcha');p.add_argument('--allow-test-writes',action='store_true')
    a=p.parse_args();origin=urllib.parse.urlsplit(a.origin)
    if origin.hostname not in ('127.0.0.1','localhost') or origin.scheme!='http' or origin.path not in ('','/'):
        p.error('Use a loopback SSH tunnel')
    if not a.allow_test_writes:p.error('Explicit --allow-test-writes required')
    base=a.origin.rstrip('/');state=json.loads(a.state.read_text()) if a.state.exists() else {}
    a.state.parent.mkdir(mode=0o700,parents=True,exist_ok=True)
    def persist():
        fd=os.open(a.state,os.O_WRONLY|os.O_CREAT|os.O_TRUNC,0o600)
        with os.fdopen(fd,'w') as f:json.dump(state,f)
        a.state.chmod(0o600)
    def request(method,path,data=None,token=None,expected=200,binary=False):
        headers={'Content-Type':'application/json'}
        if token:headers['Authorization']='Bearer '+token
        body=None if data is None else json.dumps(data).encode()
        try:
            r=urllib.request.urlopen(urllib.request.Request(base+path,data=body,headers=headers,method=method),timeout=30)
            code=r.status;raw=r.read()
        except urllib.error.HTTPError as e:code=e.code;raw=e.read()
        if code not in (expected if isinstance(expected,tuple) else (expected,)):
            try:message=json.loads(raw).get('message','request failed')
            except (ValueError,AttributeError):message='request failed'
            raise RuntimeError(f'{method} {path}: HTTP {code}: {message}')
        if binary:return raw
        try:return json.loads(raw)
        except ValueError:return raw.decode()
    credentials=json.loads(a.credentials.read_text())
    token=request('POST','/api/v1/login',{'account':credentials['account'],'password':credentials['password']})
    assert isinstance(token,str) and len(token)==32
    catalog=request('GET','/api/v1/admin/dof',token=token);assert catalog['instanceId']==a.instance
    if state:assert state['instance']==a.instance and state['origin']==base
    root='/api/v1/admin/registration';native='/api/v1/admin/dof/settings/registration'
    def edit(operation,revision,**values):return dict(requestId=str(uuid.uuid4()),operation=operation,revision=revision,reason='S3 dedicated test account acceptance',**values)
    def save_policy(key,enabled):
        if key not in state:
            policy=request('GET',root+'/policy',token=token);state[key]=edit('policy',policy['revision'],enabled=enabled);persist()
        e=state[key];request('POST',root+'/preview',e,token);return request('PUT',root+'/policy',e,token)
    if a.phase=='configure':
        assert not state,'Configure once; preserve the private state and reconcile unknown results'
        policy=request('GET',root+'/policy',token=token);settings=request('GET',native,token=token)
        before={f['key']:f['value'] for f in settings['fields'] if f['available']}
        assert {'ipLimit','registrationGift','registrationCurrency','activationCode'}<=set(before)
        assert before['activationCode'] is False,'Native registration-card protocol is not replaced by own codes'
        state.update(instance=a.instance,origin=base,originalPolicy=policy['enabled'],originalNative=before,player='codex_s3_player_20261027',playerPassword=secrets.token_urlsafe(24),registrationRequestId=str(uuid.uuid4()),captchaIndex=str(uuid.uuid4()),checks=[])
        persist();save_policy('enablePolicy',True)
        policy=request('GET',root+'/policy',token=token);state['createCode']=edit('create',policy['codeRevision'],days=1);persist()
        request('POST',root+'/preview',state['createCode'],token);created=request('POST',root+'/codes',state['createCode'],token)
        assert created['state']=='PERSISTED';state['code']=created['code'];state['codeId']=created['id'];persist()
        assert request('POST',root+'/codes',state['createCode'],token)['code']==state['code']
        state['nativeRequest']={'requestId':str(uuid.uuid4()),'revision':settings['revision'],'values':{'ipLimit':-1,'registrationGift':37,'registrationCurrency':1}};persist()
        request('POST',native+'/preview',state['nativeRequest'],token);result=request('PUT',native,state['nativeRequest'],token);assert result['persisted']
        state['nativeApplied']=True;persist()
        image=request('GET','/api/v1/vc/img/'+state['captchaIndex'],binary=True)
        (a.state.parent/'captcha.png').write_bytes(image)
        state['checks']+=['deployed instance authenticated','managed policy and generation replay','native test settings persisted'];persist()
        print(json.dumps({'phase':'captcha_required','image':str(a.state.parent/'captcha.png'),'testAccount':state['player']}));return
    if a.phase=='submit':
        assert a.captcha and state.get('nativeApplied') and not state.get('submitted')
        payload={'accountname':state['player'],'password':state['playerPassword'],'recommender':credentials['account'],'registrationRequestId':state['registrationRequestId'],'validationIndex':state['captchaIndex'],'valicode':a.captcha,'activationCode':state['code'],'uid':99999,'admin':True,'vip':'injected','parentUid':99999,'registrationClientIp':'192.0.2.99'}
        state['payload']=payload;persist()
        result=request('POST','/api/v1/client/register',payload);assert result['requestId']==state['registrationRequestId']
        state['submitted']=True;state['checks'].append('real image CAPTCHA and seven-table registration');persist()
    if a.phase in ('submit','verify'):
        assert state.get('submitted')
        assert request('POST','/api/v1/client/register',state['payload'])['requestId']==state['registrationRequestId']
        changed=dict(state['payload'],password=state['playerPassword']+'changed')
        request('POST','/api/v1/client/register',changed,expected=409)
        denied=request('POST','/api/v1/login',{'account':state['player'],'password':state.get('currentPlayerPassword',state['playerPassword'])},expected=400)
        assert 'GM' in denied.get('message',''),'Ordinary player must be refused by the GM admin login'
        request('GET',root+'/policy',expected=(401,403))
        rows=request('GET',root+'/receipts',token=token);receipt=next(r for r in rows if r['request_id']==state['registrationRequestId'])
        assert receipt['state']=='PERSISTED' and receipt['gift_amount']==37 and receipt['gift_currency']==1
        assert not any(k in receipt for k in ('request_tag','ip_tag','name_tag','key_id'))
        code_state=request('GET',root+'/codes/'+str(state['codeId']),token=token)
        revoke=edit('revoke',code_state['revision'],codeId=state['codeId']);request('POST',root+'/preview',revoke,token,expected=409)
        state['uid']=receipt['uid'];state['checks']+=['completed replay without new CAPTCHA','changed payload rejected','ordinary player refused GM admin login; anonymous management rejected','persisted receipt and single configured gift','used code cannot be revoked'];persist()
        print(json.dumps({'phase':a.phase,'uid':state['uid'],'requestId':state['registrationRequestId'],'passed':True,'nativeGameEffectVerified':False}));return
    if a.phase=='account-actions':
        assert state.get('submitted') and state.get('restored') and state.get('uid')==6
        account_root='/api/v1/admin/accounts/'+str(state['uid'])
        def client_login(password,expected=200):
            response=request('POST','/api/v1/client/login',{'accountname':state['player'],'password':password},expected=expected)
            if expected==200:assert isinstance(response,dict) and isinstance(response.get('token'),str) and len(response['token'])>100
            return response
        current_password=state.get('currentPlayerPassword',state['playerPassword'])
        client_login(current_password)
        if not state.get('accountActionsComplete'):
            if 'unbanRequest' not in state:
                ban=request('GET',account_root+'/ban',token=token);assert ban['penalty'] is None
                if 'banRequest' not in state:state['banRequest']=edit('ban',ban['revision'],days=1);persist()
                request('POST',account_root+'/ban/preview',state['banRequest'],token)
                assert request('PUT',account_root+'/ban',state['banRequest'],token)['persisted']
                client_login(current_password,403)
                ban=request('GET',account_root+'/ban',token=token)
                state['unbanRequest']=edit('unban',ban['revision']);persist()
            if not state.get('penaltyRestored'):
                request('POST',account_root+'/ban/preview',state['unbanRequest'],token)
                assert request('DELETE',account_root+'/ban',state['unbanRequest'],token)['persisted']
                client_login(current_password)
                assert request('GET',account_root+'/ban',token=token)['penalty']['apply_flag']==0
                for key,target in [('restoreUnban',state['unbanRequest']['requestId']),('restoreBan',state['banRequest']['requestId'])]:
                    ban=request('GET',account_root+'/ban',token=token)
                    if key not in state:state[key]=edit('restore',ban['revision'],targetRequestId=target);persist()
                    request('POST',account_root+'/ban/preview',state[key],token)
                    assert request('POST',account_root+'/ban/restore',state[key],token)['persisted']
                assert request('GET',account_root+'/ban',token=token)['penalty'] is None
                client_login(current_password);state['penaltyRestored']=True;persist()
            password_state=request('GET',account_root+'/password-state',token=token)
            if 'passwordResetRequest' not in state:state['passwordResetRequest']=edit('reset',password_state['revision'],newPassword=secrets.token_urlsafe(24));persist()
            new_password=state['passwordResetRequest']['newPassword']
            request('POST',account_root+'/password/preview',state['passwordResetRequest'],token)
            assert request('PUT',account_root+'/password',state['passwordResetRequest'],token)['persisted']
            state['currentPlayerPassword']=new_password;persist()
            client_login(state['playerPassword'],400);client_login(new_password)
            assert request('POST','/api/v1/client/register',state['payload'])['requestId']==state['registrationRequestId']
            client_login(new_password);client_login(state['playerPassword'],400)
            history=request('GET',account_root+'/password/history',token=token)
            assert new_password not in json.dumps(history) and current_password not in json.dumps(history)
            assert request('PUT',account_root+'/password',state['passwordResetRequest'],token)['persisted']
            client_login(new_password)
            state['accountActionsComplete']=True;state['checks']+=['real DOF HTTP login credential check; native gateway consumption pending','managed ban rejects subsequent HTTP login','managed unban disables own penalty and HTTP login recovers','directed restore recovers original absence of own penalty','managed password reset validates old/new credentials','completed registration replay preserves later password reset','password reset replay and redacted ordinary history'];persist()
        print(json.dumps({'phase':'account-actions','passed':True,'uid':state['uid'],'nativeGameEffectVerified':False}));return
    if a.phase=='restore':
        if state.get('nativeRequest') and not state.get('nativeRestored'):
            settings=request('GET',native,token=token)
            if 'restoreNative' not in state:state['restoreNative']={'requestId':str(uuid.uuid4()),'revision':settings['revision']};persist()
            result=request('POST',native+'/history/'+state['nativeRequest']['requestId']+'/restore',state['restoreNative'],token)
            assert result['persisted'];state['nativeRestored']=True;persist()
        save_policy('restorePolicy',state['originalPolicy'])
        settings=request('GET',native,token=token);after={f['key']:f['value'] for f in settings['fields'] if f['available']}
        assert after==state['originalNative'];assert request('GET',root+'/policy',token=token)['enabled']==state['originalPolicy']
        state['restored']=True;state['checks'].append('native settings and own activation policy restored');persist()
        print(json.dumps({'phase':'restored','passed':True,'requestId':state['registrationRequestId']}))


if __name__=='__main__':main()
