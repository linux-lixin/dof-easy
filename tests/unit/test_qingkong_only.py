"""Public Qingkong installation defaults and fail-closed upgrade boundaries."""
import argparse
import importlib.util
from pathlib import Path
import tempfile
import unittest
from unittest.mock import patch

ROOT = Path(__file__).resolve().parents[2]
spec = importlib.util.spec_from_file_location('qingkong_only_manager', ROOT/'scripts/container/manage.py')
manager = importlib.util.module_from_spec(spec); spec.loader.exec_module(manager)

class QingkongOnly(unittest.TestCase):
    def args(self, **changes):
        values = dict(launcher=None, database=None, plugins='none', raid_extension=None,
            admin_port=18080, channels='15,52', ip='10.8.0.7', bind='0.0.0.0',
            db_host='10.8.0.7', db_port=None, db_allow_host='172.29.86.0/255.255.255.0',
            mysql_config=Path('/private/client.cnf'))
        values.update(changes)
        return argparse.Namespace(**values)

    def test_new_instance_defaults_to_qingkong_external_without_web_credentials(self):
        values = manager.initial_values(self.args(), {'level':86,'game':{'sha256':'a'*64}}, Path('/instance'))
        self.assertEqual(values['LAUNCHER_MODE'], 'qingkong')
        self.assertEqual(values['DOF_DATABASE_KIND'], 'external')
        self.assertEqual(values['PLUGIN_MODE'], 'none')
        self.assertFalse(any(k.startswith('ADMIN_') for k in values))

    def test_existing_unsupported_modes_are_rejected_without_rewriting(self):
        for launcher, database, plugin in [('native','external','none'), ('dof','internal','none'), ('qingkong','internal','none'), ('qingkong','external','dp2')]:
            with self.subTest(launcher=launcher, database=database, plugin=plugin), tempfile.TemporaryDirectory() as folder:
                root = Path(folder)
                original = f'LAUNCHER_MODE={launcher}\nDOF_DATABASE_KIND={database}\nPLUGIN_MODE={plugin}\nRAID_EXTENSION=off\n'
                (root/'.env').write_text(original)
                with self.assertRaises(ValueError): manager.read_env(root)
                self.assertEqual((root/'.env').read_text(), original)

    def test_unspecified_legacy_mode_does_not_silently_migrate(self):
        with tempfile.TemporaryDirectory() as folder:
            root=Path(folder); (root/'.env').write_text('PLUGIN_MODE=none\n')
            with self.assertRaises(ValueError): manager.read_env(root)

    def test_build_without_mode_arguments_builds_only_game(self):
        calls=[]
        with patch.object(manager,'run', side_effect=lambda command,**kw:calls.append(command)):
            manager.build({'registry':'','tag':'local'})
        self.assertEqual(len(calls),1)
        self.assertIn('dof-easy-game:local',calls[0])

    def test_game_is_only_configurable_image(self):
        self.assertEqual(manager.image_config.references({'registry':'','tag':'local'}), {'game':'dof-easy-game:local'})

    def test_original_config_is_checked_before_image_acquisition(self):
        with tempfile.TemporaryDirectory() as folder:
            root=Path(folder); pvf=root/'Script.pvf'; pvf.write_bytes(b'x'*2048)
            with patch.object(manager.sys,'platform','linux'), patch.object(manager,'STATE',root/'state'), patch.object(manager,'docker_check'), patch.object(manager,'check_ports'), patch.object(manager,'check_gateway_port'), patch.object(manager.raid_assets,'verify',return_value=root), patch.object(manager.raid_assets,'verify_config',side_effect=ValueError('missing original config')) as config, patch.object(manager,'acquire_images') as images:
                with self.assertRaisesRegex(ValueError,'missing original config'):
                    manager.main(['install','--ip','10.8.0.7','--pvf',str(pvf),'--db-host','10.8.0.7','--db-allow-host','172.29.86.0/255.255.255.0','--mysql-config','/private/client.cnf','--build'])
                images.assert_not_called(); config.assert_called_once()


class QingkongMenu(unittest.TestCase):
    def test_menu_routes_original_assets_and_external_credentials_before_mutations(self):
        from scripts.container import menu
        from unittest.mock import Mock
        events=[]; prompts=[]
        with tempfile.TemporaryDirectory() as folder:
            root=Path(folder); pvf=root/'Script.pvf'; pvf.write_bytes(b'p'*2048)
            def answer(label):
                prompts.append(label)
                if 'Script.pvf' in label:return str(pvf)
                if '宿主机私网' in label:return '172.17.0.1'
                if '防火墙' in label:return '2'
                if 'Swap GiB' in label:return '0'
                if '确认' in label:return 'y'
                return ''
            with patch.object(menu.manager,'STATE',root/'state'), patch('builtins.input',side_effect=answer), patch.object(menu,'detect_public_ipv4',return_value='8.8.4.4'), patch.object(menu.host,'system_check'), patch.object(menu.host,'memory',return_value=dict(MemTotal=8*menu.host.GIB,MemAvailable=5*menu.host.GIB,SwapTotal=0)), patch.object(menu.shutil,'which',return_value=None), patch.object(menu.host,'disk_check'), patch.object(menu,'mysql_binary',return_value='/mysql'), patch.object(menu,'mysql_sockets',return_value=[]), patch.object(menu.manager.database,'host_query',return_value=Mock(return_value='0.0.0.0\t3306')), patch.object(menu,'inspect_network',return_value=(['172.17.0.1'],[])), patch.object(menu,'check_external',side_effect=lambda *a:events.append('database')), patch.object(menu.getpass,'getpass',return_value='private'), patch.object(menu,'assets_directory',side_effect=lambda value,instance:events.append(('assets',str(value))) or Path(value)), patch.object(menu.host,'prepare_dependencies',side_effect=lambda **kw:events.append('host')), patch.object(menu.manager,'docker_check'), patch.object(menu.manager,'check_ports'), patch.object(menu.manager,'main',side_effect=lambda args:events.append(('install',args))):
                with patch.object(menu,'game_instructions',create=True):menu.install('test')
            command=next(item[1] for item in events if isinstance(item,tuple) and item[0]=='install')
            self.assertIn(('assets','/opt/dof-inputs/qingkong-original'),events)
            self.assertLess(events.index(('assets','/opt/dof-inputs/qingkong-original')),events.index('host'))
            self.assertLess(events.index('database'),events.index('host'))
            for name,value in [('launcher','qingkong'),('database','external'),('plugins','none'),('raid-extension','off'),('level','86')]:
                self.assertEqual(command[command.index('--'+name)+1],value)
            self.assertIn('--qingkong-assets',command)
            self.assertFalse(any('网页' in p or '普通插件' in p or '内置容器' in p for p in prompts))

class QingkongRuntimeBoundary(unittest.TestCase):
    def test_runtime_accepts_only_bound_external_qingkong_settings(self):
        settings=dict(SERVER_TYPE='ALL',PUBLIC_IP='127.0.0.1',P2P_RELAY_INDEX='1',WEB_PASS='testweb',
            MAIN_MYSQL_HOST='172.17.0.1',MAIN_MYSQL_PORT='3306',MAIN_MYSQL_ROOT_PASSWORD='unused',
            MYSQL_HOST='172.17.0.1',MYSQL_PORT='3306',DNF_DB_ROOT_PASSWORD='unused',DNF_DB_GAME_PASSWORD='Demo1234')
        self.assertEqual(manager.runtime_config.validate(settings)['LAUNCHER_MODE'],'qingkong')
        for changed in ({'DOF_DATABASE_KIND':'internal'}, {'DOF_DATABASE_MODE':'provision'},
                        {'LAUNCHER_MODE':'dof'}, {'LAUNCHER_MODE':'native'}, {'PLUGIN_MODE':'dp2'}, {'RAID_EXTENSION':'on'}):
            with self.subTest(changed=changed), self.assertRaises(ValueError):
                manager.runtime_config.validate(dict(settings,**changed))

    def test_deployment_rejects_foreign_modes_before_mutating_the_model(self):
        model={'services':{'game':{'image':'game:test','environment':{}},'keys':{'image':'game:test'}},'networks':{'public':{}}}
        for values in ({'LAUNCHER_MODE':'native','DOF_DATABASE_KIND':'external'}, {'LAUNCHER_MODE':'qingkong','DOF_DATABASE_KIND':'internal'}):
            with self.assertRaisesRegex(ValueError, 'qingkong|external'):
                manager.deployment.configure(model,values,Path('/main'),[])
        self.assertEqual(set(model['services']),{'game','keys'})

if __name__ == '__main__': unittest.main()
