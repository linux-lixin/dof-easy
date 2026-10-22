"""Offline safety properties for the Ubuntu-only runtime adapter."""
import importlib.util
import json
from pathlib import Path
import tempfile
import unittest
from unittest.mock import patch
import contextlib
import io
import subprocess

ROOT = Path(__file__).resolve().parents[2]
ADAPTER = ROOT / 'services/game/runtime/ubuntu'

def module(name, directory=ADAPTER):
    path = directory / (name + '.py')
    if not path.exists():
        raise AssertionError('Ubuntu adapter missing: ' + name)
    spec = importlib.util.spec_from_file_location(name, path)
    result = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(result)
    return result

class UbuntuRuntime(unittest.TestCase):
    def test_startup_notice_requires_consecutive_ready_checks(self):
        notice=module('startup_notice')
        output=[]
        results=iter([None,ValueError('database unavailable'),None,None,None])
        def check():
            value=next(results)
            if value: raise value
        with patch.object(notice.time,'sleep'), patch.object(notice.time,'monotonic',side_effect=[0,0,1,2,3,4]):
            self.assertEqual(notice.wait_ready(check,'READY',600,output.append),0)
        self.assertEqual(output,['READY'])

    def test_startup_timeout_does_not_claim_ready(self):
        notice=module('startup_notice')
        output=[]
        def check(): raise ValueError('MAIN_MYSQL connection failed')
        with patch.object(notice.time,'sleep'), patch.object(notice.time,'monotonic',side_effect=[0,0,601]):
            self.assertEqual(notice.wait_ready(check,'READY',600,output.append),1)
        self.assertEqual(len(output),1)
        self.assertIn('MAIN_MYSQL connection failed',output[0])
        self.assertNotIn('READY',output[0])

    def test_startup_country_check_ignores_previous_logs(self):
        notice=module('startup_notice')
        with tempfile.TemporaryDirectory() as td:
            root=Path(td); folder=root/'siroco11'; folder.mkdir()
            content='\n'.join('GeoIP Allow Country Code : '+x for x in ('CN','HK','KR','MO','TW'))
            (folder/'Log-old.init.previous').write_text(content)
            env={'SERVER_TYPE':'ALL','SERVER_GROUP':'3','OPEN_CHANNEL':'11'}
            with self.assertRaisesRegex(ValueError,'siroco11'):
                notice.check_countries(root,env)
            (folder/'Log-new.init').write_text(content)
            notice.check_countries(root,env)
            notice.check_countries(root,{'SERVER_TYPE':'CORE'})

    def test_onlyguo_runtime_has_no_legacy_gateway(self):
        base=ROOT/'services/game/assets'
        entry=(ADAPTER/'bootstrap.sh').read_text(encoding='utf8')
        init=(base/'home/template/init/init.sh').read_text(encoding='utf8')
        starter=(base/'home/template/init/starter/start_program.sh').read_text(encoding='utf8')
        for text in (entry,init,starter):
            for obsolete in ('tongyi_gate','DnfGateServer','Config.ini','privatekey.pem','GM_PASSWORD'):
                self.assertNotIn(obsolete,text)
        self.assertIn('core:bridge',starter)
        self.assertIn('core:channel',starter)
        self.assertIn('publickey.pem',entry)

    def test_onlyguo_config_does_not_require_gate_password(self):
        config=module('config')
        env=dict(SERVER_TYPE='ALL',PUBLIC_IP='127.0.0.1',P2P_RELAY_INDEX='1',WEB_PASS='testweb',
                 MAIN_MYSQL_HOST='192.0.2.20',MAIN_MYSQL_PORT='3306',MAIN_MYSQL_ROOT_PASSWORD='RootTest',
                 MYSQL_HOST='192.0.2.20',MYSQL_PORT='3306',DNF_DB_ROOT_PASSWORD='RootTest',DNF_DB_GAME_PASSWORD='Demo1234')
        result=config.validate(env)
        self.assertFalse(any(k.startswith('GM_') for k in config.DEFAULTS))
        self.assertEqual(result['SERVER_TYPE'],'ALL')

    def test_pvf_required_only_for_game_roles(self):
        pvf=module('pvf')
        with tempfile.TemporaryDirectory() as td:
            path=Path(td)/'Script.pvf'
            for role in ('CORE','P2P'):
                pvf.validate(role,path)
            for role in ('ALL','GAME'):
                with self.assertRaisesRegex(ValueError,'Script.pvf'):
                    pvf.validate(role,path)
            path.mkdir()
            with self.assertRaises(ValueError): pvf.validate('ALL',path)
            path.rmdir(); path.touch()
            with self.assertRaises(ValueError): pvf.validate('GAME',path)
            path.write_bytes(b'user supplied pvf')
            pvf.validate('ALL',path)
            self.assertEqual(path.read_bytes(),b'user supplied pvf')

    def test_ubuntu_init_cannot_restore_bundled_pvf(self):
        source=(ROOT/'services/game/assets/home/template/init/init.sh').read_text(encoding='utf8')
        adapted=source
        self.assertNotIn('Script.tgz',adapted)
        self.assertNotIn('cp /home/template/init/Script.pvf',adapted)
        self.assertIn('publickey.pem',adapted)

    def test_game_password_is_never_silently_truncated(self):
        config=module('config')
        env=dict(SERVER_TYPE='ALL',PUBLIC_IP='127.0.0.1',P2P_RELAY_INDEX='1',WEB_PASS='testweb',
                 MAIN_MYSQL_HOST='192.0.2.20',MAIN_MYSQL_PORT='3306',MAIN_MYSQL_ROOT_PASSWORD='RootTest',
                 MYSQL_HOST='192.0.2.20',MYSQL_PORT='3306',DNF_DB_ROOT_PASSWORD='RootTest')
        for password in ('Short77','Long99999','BadPass!'):
            with self.assertRaisesRegex(ValueError,'exactly 8 alphanumeric'):
                config.validate(dict(env,DNF_DB_GAME_PASSWORD=password))

    def test_cleanup_does_not_claim_success_when_docker_retains_container(self):
        harness=module('test_ubuntu_images',ROOT/'tests/integration')
        state={'network':'isolated','containers':['stuck'],'volumes':[]}
        def fake(*args,**kwargs):
            return subprocess.CompletedProcess(args,1 if args[0]=='rm' else 0,stdout='',stderr='')
        with patch.object(harness,'docker',side_effect=fake):
            harness.cleanup(state)
        self.assertFalse(state['cleaned_up'])
        self.assertIn('stuck',state['cleanup_errors'])

    def test_ddns_restarts_every_address_dependent_service(self):
        monitor=module('monitor_ip')
        core={'core:'+name for name in ('monitor','manager','bridge','channel','dbmw_guild','dbmw_mnt','dbmw_stat','auction','point','guild','statics','coserver','community')}
        expected={'ALL':core|{'p2p:relay','game:*'},
                  'CORE':core,'GAME':{'game:*'},'P2P':{'p2p:relay'}}
        for role,targets in expected.items():
            with patch.dict(monitor.os.environ,{'SERVER_TYPE':role,'MAIN_BRIDGE_IP':'127.0.0.1'},clear=True), patch.object(monitor.subprocess,'run') as run:
                monitor.restart_address_services()
                self.assertEqual({call.args[0][-1] for call in run.call_args_list},targets)
                self.assertTrue(all(call.kwargs.get('timeout')==30 for call in run.call_args_list))

    def test_game_preflight_uses_game_credentials_from_its_node(self):
        preflight=module('database_preflight')
        env=dict(SERVER_TYPE='GAME',SERVER_GROUP_DB='cain',DNF_DB_GAME_PASSWORD='Demo1234')
        for prefix in ('CUR_MAIN_DB','CUR_SG_DB'):
            env.update({prefix+'_HOST':'192.0.2.20',prefix+'_PORT':'3306',prefix+'_ROOT_PASSWORD':'RootTest'})
        result=subprocess.CompletedProcess([],0,stdout='1',stderr='')
        with patch.dict(preflight.os.environ,env,clear=True), patch.object(preflight.subprocess,'run',return_value=result) as call:
            preflight.check()
            self.assertEqual(call.call_count,2)
            for args,kwargs in call.call_args_list:
                self.assertIn('-ugame',args[0])
                self.assertEqual(kwargs['env']['MYSQL_PWD'],'Demo1234')

    def test_explicit_ipv4_wins_over_discovery(self):
        monitor=module('monitor_ip')
        with patch.dict(monitor.os.environ,{'PUBLIC_IP':'192.0.2.10','AUTO_PUBLIC_IP':'true','DDNS_ENABLE':'true','DDNS_DOMAIN':'unused.example'},clear=True), patch.object(monitor.subprocess,'check_output',side_effect=AssertionError('must not resolve')):
            self.assertEqual(monitor.discover(),'192.0.2.10')

    def test_first_address_discovery_timeout_is_finite(self):
        monitor=module('monitor_ip')
        output=io.StringIO()
        with patch.dict(monitor.os.environ,{'STARTUP_TIMEOUT':'1'},clear=True), patch.object(monitor,'discover',side_effect=ValueError('unavailable')), patch.object(monitor.time,'monotonic',side_effect=[0,2]), contextlib.redirect_stderr(output):
            self.assertEqual(monitor.main(),1)
        self.assertIn('timed out',output.getvalue())

    def test_relay_failed_apply_is_retried_before_acknowledgment(self):
        relay=module('relay')
        self.assertTrue(hasattr(relay,'apply_registration'),'registration apply must be independently retryable')
        with tempfile.TemporaryDirectory() as td:
            path=Path(td)/'MONITOR_RELAY_CONFIG'
            failure=subprocess.CalledProcessError(1,['supervisorctl'])
            with patch.object(relay.subprocess,'run',side_effect=[failure,None]) as restart:
                with self.assertRaises(subprocess.CalledProcessError):
                    relay.apply_registration(path,'127.0.0.1',1,7300)
                relay.apply_registration(path,'127.0.0.1',1,7300)
                self.assertEqual(restart.call_count,2)
            self.assertFalse(path.with_suffix('.pending').exists())

    def test_channel_restart_excludes_previous_init_evidence(self):
        logs=module('channel_logs')
        with tempfile.TemporaryDirectory() as td:
            root=Path(td); folder=root/'siroco11'; folder.mkdir()
            (folder/'Log20261016.init').write_text('old five countries')
            logs.rotate(root,'siroco11')
            self.assertEqual(list(folder.glob('Log*.init')),[])
            self.assertEqual((folder/'Log20261016.init.previous').read_text(),'old five countries')

    def test_encoder_failure_does_not_log_password(self):
        config=module('config')
        env=dict(SERVER_TYPE='ALL',PUBLIC_IP='127.0.0.1',P2P_RELAY_INDEX='1',WEB_PASS='testweb',
                 MAIN_MYSQL_HOST='127.0.0.1',MAIN_MYSQL_PORT='3306',MAIN_MYSQL_ROOT_PASSWORD='RootTest',
                 MYSQL_HOST='127.0.0.1',MYSQL_PORT='3306',DNF_DB_ROOT_PASSWORD='RootTest',DNF_DB_GAME_PASSWORD='Demo1234')
        error=subprocess.CalledProcessError(1,['/TeaEncrypt','Demo1234'])
        output=io.StringIO()
        with patch.dict(config.os.environ,env,clear=True), patch.object(config.subprocess,'check_output',side_effect=error), contextlib.redirect_stderr(output):
            self.assertEqual(config.main(),1)
        self.assertNotIn('Demo1234',output.getvalue())

    def test_validation_rejects_missing_db_before_any_write(self):
        with self.assertRaisesRegex(ValueError, 'MAIN_MYSQL_HOST'):
            module('config').validate({'SERVER_TYPE': 'ALL', 'PUBLIC_IP': '127.0.0.1'})

    def test_p2p_needs_no_database_but_rejects_vpn(self):
        config = module('config')
        env = {'SERVER_TYPE':'P2P', 'PUBLIC_IP':'127.0.0.1', 'CORE_PUBLIC_IP':'127.0.0.1', 'P2P_RELAY_INDEX':'1'}
        self.assertEqual(config.validate(env)['SERVER_TYPE'], 'P2P')
        with self.assertRaisesRegex(ValueError, 'TS_AUTH_KEY'):
            config.validate(dict(env, TS_AUTH_KEY='private-secret'))

    def test_invalid_role_is_rejected(self):
        with self.assertRaisesRegex(ValueError, 'SERVER_TYPE'):
            module('config').validate({'SERVER_TYPE':'INVALID'})

    def test_existing_unmarked_scripts_are_never_overwritten(self):
        manager = module('managed')
        with tempfile.TemporaryDirectory() as td:
            root = Path(td); data = root/'data'; source = root/'source'
            (data/'run').mkdir(parents=True); (source/'run').mkdir(parents=True)
            (data/'run/start.sh').write_text('unmarked runtime')
            (source/'run/start.sh').write_text('ubuntu')
            with self.assertRaisesRegex(ValueError, 'unmarked'):
                manager.synchronize(data, source, {})
            self.assertEqual((data/'run/start.sh').read_text(), 'unmarked runtime')

    def test_managed_upgrade_and_user_conflict_are_transactional(self):
        manager = module('managed')
        with tempfile.TemporaryDirectory() as td:
            root = Path(td); data=root/'data'; source=root/'source'
            source.mkdir(); (source/'run').mkdir(); data.mkdir()
            (data/'publickey.pem').write_text('user key')
            (source/'run/a.sh').write_text('v1a'); (source/'run/b.sh').write_text('v1b')
            manager.synchronize(data, source, {})
            old = json.loads((data/'.dnf-ubuntu.json').read_text())['files']
            (source/'run/a.sh').write_text('v2a'); (source/'run/b.sh').write_text('v2b')
            (data/'run/b.sh').write_text('custom')
            with self.assertRaisesRegex(ValueError, 'run/b.sh'):
                manager.synchronize(data, source, {'v1': old})
            self.assertEqual((data/'run/a.sh').read_text(), 'v1a')
            (data/'run/b.sh').write_text('v1b')
            manager.synchronize(data, source, {'v1':old})
            self.assertEqual((data/'run/a.sh').read_text(), 'v2a')
            self.assertEqual((data/'publickey.pem').read_text(), 'user key')

if __name__ == '__main__':
    unittest.main()
