"""Database selection, ownership and no-overwrite boundaries, without a server."""
import argparse
import copy
import importlib.util
import json
from pathlib import Path
import tempfile
import unittest
from unittest.mock import patch, Mock

ROOT=Path(__file__).resolve().parents[2]
def load(name,path):
    spec=importlib.util.spec_from_file_location(name,ROOT/path)
    value=importlib.util.module_from_spec(spec);spec.loader.exec_module(value);return value

db=load('db_choice','scripts/container/database.py')
deploy=load('db_compose','scripts/container/deployment.py')
manager=load('choice_manager','scripts/container/manage.py')

class DatabaseModes(unittest.TestCase):
    def options(self,**extra):
        result=dict(database='external',db_host='172.17.0.1',db_port=3306,
                    db_allow_host='172.29.86.0/255.255.255.0',mysql_config=Path('/private/client.cnf'),mysql_bin=None)
        result.update(extra);return argparse.Namespace(**result)

    def test_choices_and_bad_external_endpoints(self):
        self.assertEqual(db.external_options(argparse.Namespace(database=None)),{'DOF_DATABASE_KIND':'internal'})
        self.assertEqual(db.kind({}),'internal')
        for host in ('127.0.0.1','0.0.0.0','8.8.8.8','db','172.29.86.5'):
            with self.subTest(host=host),self.assertRaises(ValueError):db.external_options(self.options(db_host=host))
        for allow in ('%','0.0.0.0/0','172.29.86.5/255.255.255.0','127.0.0.0/24'):
            with self.assertRaises(ValueError):db.external_options(self.options(db_allow_host=allow))
        with self.assertRaises(ValueError):db.external_options(self.options(database='internal'))
        for port in (0,-1,65536):
            with self.assertRaises(ValueError):db.external_options(self.options(db_port=port))
        value=db.external_options(self.options())
        self.assertEqual(value['DOF_DB_SUBNET'],'172.29.86.0/24')
        self.assertNotIn('DB_ROOT_PASSWORD',value)

    def test_wrong_version_or_uuid_rejected(self):
        for answer in ('8.0.32\t11111111-1111-1111-1111-111111111111','5.6.51\tbad'):
            with self.assertRaises(ValueError):db.identity(lambda _:answer)

    def test_existing_managed_instance_applies_only_additive_management_migrations(self):
        with tempfile.TemporaryDirectory() as folder:
            instance=Path(folder)/'instance';(instance/'runtime/game/data/dof-easy/config').mkdir(parents=True)
            identity='11111111-1111-1111-1111-111111111111'
            db.private_json(instance/'database-web.json',dict(databaseUuid=identity,instanceId='dof-instance',native=True,state='ready'))
            calls=[]
            def query(sql):
                calls.append(sql)
                if sql=='SELECT VERSION(),@@server_uuid;':return '5.6.51\t'+identity
                if sql.startswith('SELECT CONCAT(database_uuid'):return identity+'|dof-native-1'
                self.assertTrue(sql.startswith('CREATE TABLE IF NOT EXISTS dnf_service.DOF_'))
                self.assertIn('DOF_OPERATION_JOURNAL',sql)
                self.assertNotRegex(sql,r'\b(?:DROP|TRUNCATE|INSERT|UPDATE|DELETE|ALTER)\b')
                return ''
            self.assertEqual(db.prepare_web(instance,{'LAUNCHER_MODE':'native'},query),identity)
            self.assertEqual(len(calls),3)
            self.assertEqual(json.loads((instance/'database-web.json').read_text())['state'],'ready')

    def test_management_migration_rejects_player_writes_before_any_statement(self):
        with tempfile.TemporaryDirectory() as folder:
            root=Path(folder);path=root/'services/admin/database';path.mkdir(parents=True)
            (path/'dof-admin.sql').write_text('CREATE TABLE IF NOT EXISTS dnf_service.DOF_TEST(id INT);DELETE FROM d_taiwan.accounts;')
            query=Mock()
            with patch.object(db,'ROOT',root),self.assertRaisesRegex(ValueError,'非追加式'):
                db.management_migrations(query)
            query.assert_not_called()

    def test_existing_schema_rejected_before_any_write(self):
        answers=iter(['5.6.51\t11111111-1111-1111-1111-111111111111','mysql\nd_taiwan'])
        query=Mock(side_effect=lambda _:next(answers))
        with tempfile.TemporaryDirectory() as folder:
            path=Path(folder)/'instance';path.mkdir()
            with self.assertRaisesRegex(ValueError,'已有项目库'):
                db.initialize_external(path,db.external_options(self.options()),query)
            self.assertFalse((path/'external-database.json').exists())
        self.assertEqual(query.call_count,2)

    def test_partial_import_never_replayed_and_uuid_change_rejected(self):
        with tempfile.TemporaryDirectory() as folder:
            path=Path(folder)/'instance';path.mkdir()
            values=db.external_options(self.options())
            uuid='11111111-1111-1111-1111-111111111111'
            record=dict(databaseUuid=uuid,host=values['DOF_DB_HOST'],port=values['DOF_DB_PORT'],
                        grantHost=values['DOF_DB_ALLOW_HOST'],instanceId='dof-instance',state='initializing')
            db.private_json(path/'external-database.json',record)
            query=Mock(return_value='5.6.51\t'+uuid)
            with self.assertRaisesRegex(ValueError,'中断'):db.initialize_external(path,values,query)
            self.assertEqual(query.call_count,1)
            query=Mock(return_value='5.6.51\t22222222-2222-2222-2222-222222222222')
            with self.assertRaisesRegex(ValueError,'身份改变'):db.initialize_external(path,values,query)
            self.assertEqual(query.call_count,1)

    def model(self):
        return dict(services={
            'db':{'image':'db'},'keys':{'image':'web'},
            'game':{'image':'game','environment':{},'depends_on':{'db':{'condition':'service_healthy'}}},
            'admin':{'image':'web','environment':{},'networks':{'private':None},'volumes':[]}},
            networks={'private':{'internal':True},'public':{'driver':'bridge'}},volumes={'database':{}})

    def test_external_model_removes_database_and_keeps_native_web(self):
        values=db.external_options(self.options())
        values.update(DOF_DATABASE_UUID='11111111-1111-1111-1111-111111111111',GAME_DB_PASSWORD='test1234',LAUNCHER_MODE='native')
        source=self.model();copy_before=copy.deepcopy(source)
        model=deploy.configure(source,values,Path('/local/instance'),db.DATABASES)
        self.assertEqual(source,copy_before)
        self.assertNotIn('db',model['services']);self.assertNotIn('database',model['volumes'])
        self.assertNotIn('db',model['services']['game']['depends_on'])
        self.assertEqual(model['services']['game']['environment']['DOF_DATABASE_MODE'],'existing')
        self.assertEqual(model['services']['admin']['entrypoint'],['/app/dof-entrypoint.sh'])
        self.assertEqual(model['services']['admin']['environment']['DOF_INSTANCE_ID'],'dof-instance')
        self.assertEqual(model['services']['db-tools']['profiles'],['tools'])
        self.assertEqual(model['services']['game']['environment']['MAIN_MYSQL_ROOT_PASSWORD'],'unused')
        self.assertNotIn('MYSQL_ROOT_PASSWORD',model['services']['db-tools']['environment'])
        self.assertNotIn('db-check',model['services'])
        self.assertNotIn('netguard',model['services'])

    def test_dof_login_keeps_independent_raid_management_and_fixed_file_mount(self):
        values=dict(LAUNCHER_MODE='dof',RAID_EXTENSION='on',PLUGIN_MODE='dp2',
                    GAME_DB_PASSWORD='test1234',DOF_DATABASE_UUID='11111111-1111-1111-1111-111111111111')
        model=deploy.configure(self.model(),values,Path('/local/instance'),db.DATABASES)
        env=model['services']['admin']['environment']
        self.assertEqual(env['LAUNCHER_MODE'],'dof')
        self.assertEqual(env['DOF_ADMIN_ENABLED'],'true')
        self.assertEqual(env['DOF_GAME_CONFIG_ROOT'],'/app/game-config')

        self.assertTrue(db.web_management_enabled(values))
        mounts=[v for v in model['services']['admin']['volumes'] if isinstance(v,dict) and v['target']=='/app/game-config']
        self.assertEqual(mounts,[dict(type='bind',source='/local/instance/runtime/game/data/dof-easy/config',target='/app/game-config',read_only=False)])
        values.update(RAID_EXTENSION='off',DOF_ADMIN_ENABLED='true')
        self.assertTrue(db.web_management_enabled(values))
        self.assertEqual(deploy.configure(self.model(),values,Path('/local/instance'),db.DATABASES)['services']['admin']['environment']['DOF_ADMIN_ENABLED'],'true')

    def test_internal_dof_compose_actually_uses_managed_model_when_raid_is_enabled(self):
        import subprocess
        values=dict(LAUNCHER_MODE='dof',RAID_EXTENSION='on',PLUGIN_MODE='dp2',GAME_DB_PASSWORD='synthetic',DOF_DATABASE_UUID='11111111-1111-1111-1111-111111111111')
        def run(command,**kwargs):
            if '--format' in command:return subprocess.CompletedProcess(command,0,json.dumps(self.model()),'')
            return subprocess.CompletedProcess(command,0,'','')
        with patch.object(manager,'read_env',return_value=values),patch.object(manager,'ports_override',return_value={}),patch.object(manager,'write_json'),patch.object(manager,'run',side_effect=run) as called,patch.object(manager.database,'private_json') as saved:
            manager.compose(Path('/local/instance'),'config','--quiet')
        self.assertEqual(called.call_count,2)
        model=saved.call_args.args[1]
        self.assertEqual(model['services']['admin']['environment']['LAUNCHER_MODE'],'dof')
        self.assertEqual(model['services']['admin']['environment']['DOF_ADMIN_ENABLED'],'true')
        self.assertEqual([v['target'] for v in model['services']['admin']['volumes']].count('/app/game-config'),1)

    def test_admin_upgrade_preserves_existing_key_initializer_image(self):
        model = {'services': {'keys': {'image': 'new-admin'}, 'admin': {'image': 'new-admin'}}}
        values = {'LAUNCHER_MODE': 'dof'}
        previous = {'services': {'keys': {'image': 'verified-key-image'}}}
        result = deploy.configure(model, values, Path('/tmp/main'), [], previous)
        self.assertEqual(result['services']['keys']['image'], 'verified-key-image')
        self.assertEqual(result['services']['admin']['image'], 'new-admin')
        self.assertEqual(model['services']['keys']['image'], 'new-admin')
        values['DOF_KEYS_IMAGE'] = 'explicit-key-upgrade'
        self.assertEqual(deploy.configure(model, values, Path('/tmp/main'), [], previous)['services']['keys']['image'], 'explicit-key-upgrade')

    def test_resolved_compose_dollar_is_literal_on_second_pass(self):
        source={'test':['CMD-SHELL','MYSQL_PWD="$MYSQL_ROOT_PASSWORD" mysql -uroot']}
        self.assertEqual(deploy.literal_model(source)['test'][1],
                         'MYSQL_PWD="$$MYSQL_ROOT_PASSWORD" mysql -uroot')
        self.assertIn('$MYSQL_ROOT_PASSWORD',source['test'][1])

    def test_plugin_file_binding_cannot_be_redirected_or_overwritten(self):
        with tempfile.TemporaryDirectory() as folder:
            instance=Path(folder)/'instance';root=instance/'runtime/game/data/dof-easy/config';root.mkdir(parents=True)
            db.bind_plugin_files(instance,'dof-instance','test-uuid')
            marker=root/'.admin-instance.json';saved=marker.read_bytes()
            db.bind_plugin_files(instance,'dof-instance','test-uuid')
            self.assertEqual(saved,marker.read_bytes())
            with self.assertRaisesRegex(ValueError,'其他实例'):
                db.bind_plugin_files(instance,'dof-other','test-uuid')
            self.assertEqual(saved,marker.read_bytes())
            marker.unlink();outside=Path(folder)/'outside';outside.write_text('{}');marker.symlink_to(outside)
            with self.assertRaisesRegex(ValueError,'符号链接'):
                db.bind_plugin_files(instance,'dof-instance','test-uuid')
            self.assertEqual(outside.read_text(),'{}')

    def test_host_defaults_file_rejects_shared_permissions_and_symlinks(self):
        with tempfile.TemporaryDirectory() as folder:
            root=Path(folder); binary=root/'mysql';binary.touch()
            config=root/'client.cnf';config.write_text('[client]\nuser=root\n');config.chmod(0o644)
            with self.assertRaisesRegex(ValueError,'0600'):db.host_query(binary,config)
            config.chmod(0o600);link=root/'link';link.symlink_to(config)
            with self.assertRaisesRegex(ValueError,'0600'):db.host_query(binary,link)

    def test_bound_external_start_rejects_endpoint_change_before_compose(self):
        with tempfile.TemporaryDirectory() as folder:
            root=Path(folder)/'instance';root.mkdir()
            values=dict(DOF_DATABASE_KIND='external',DOF_DB_HOST='172.17.0.1',DOF_DB_PORT='3306',
                DOF_DB_ALLOW_HOST='172.29.86.0/255.255.255.0',DOF_DATABASE_UUID='u',LAUNCHER_MODE='dof')
            db.private_json(root/'external-database.json',dict(state='ready',databaseUuid='u',host='172.17.0.2',port='3306',grantHost=values['DOF_DB_ALLOW_HOST']))
            with patch.object(manager,'check_instance',return_value=values),patch.object(manager,'ports_override',return_value={}),patch.object(manager,'compose') as compose:
                with self.assertRaisesRegex(ValueError,'绑定不符'):manager.start(root)
                compose.assert_not_called()

    def test_failed_external_backup_restores_writers_and_publishes_no_archive(self):
        import subprocess
        with tempfile.TemporaryDirectory() as folder:
            root=Path(folder)/'instance';root.mkdir();commands=[]
            def compose(root,*args,**kwargs):
                commands.append(args)
                if args[-1]=='dump':raise subprocess.CalledProcessError(1,['local-test'])
                return subprocess.CompletedProcess([],0,'game\nadmin\ningress\n')
            with patch.object(manager,'read_env',return_value={'DOF_DATABASE_KIND':'external'}),patch.object(manager,'STATE',Path(folder)),patch.object(manager,'compose',side_effect=compose):
                with self.assertRaises(subprocess.CalledProcessError):manager.backup(root)
            self.assertEqual(commands[-1],('start','admin','game'))
            self.assertEqual(list((Path(folder)/'backups').glob('*.tar.gz')),[])

    def test_internal_default_graph_preserved(self):
        model=self.model()
        self.assertEqual(deploy.configure(model,{},Path('/local/instance'),db.DATABASES),model)

    def test_external_image_acquisition_never_pulls_db(self):
        import subprocess
        with patch.object(manager,'run',return_value=subprocess.CompletedProcess([],0,'linux/amd64\n')) as run:
            manager.acquire_images({'registry':'ghcr.io/example','tag':'test'},'pull',database_mode='external')
        commands=[c.args[0] for c in run.call_args_list]
        self.assertEqual(len([c for c in commands if c[1]=='pull']),2)
        self.assertFalse(any('dof-easy-db:' in str(c) for c in commands))

    def test_external_backup_stops_only_project_writers_and_uses_scoped_dump(self):
        import subprocess
        with tempfile.TemporaryDirectory() as folder:
            instance=Path(folder)/'instance';instance.mkdir()
            values={'DOF_DATABASE_KIND':'external','DOF_GAME_IMAGE':'game:test'}
            commands=[]
            def compose(root,*args,**kwargs):
                commands.append(args)
                if kwargs.get('stdout') is not None:kwargs['stdout'].write(b'-- disposable fixture dump\n'*8)
                return subprocess.CompletedProcess([],0,'game\nadmin\ningress\nnetguard\ndb-check\n')
            with patch.object(manager,'read_env',return_value=values),patch.object(manager,'STATE',Path(folder)),patch.object(manager,'compose',side_effect=compose),patch.object(manager,'run'):
                manager.backup(instance)
            self.assertIn(('run','--rm','--no-deps','-T','db-tools','dump'),commands)
            self.assertIn(('stop','admin','game'),commands)
            self.assertFalse(any('db' in c for c in commands))
            self.assertEqual(commands[-1],('start','admin','game'))

if __name__=='__main__':unittest.main()
