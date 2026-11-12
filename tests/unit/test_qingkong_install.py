"""Original Qingkong deployment boundaries, without Docker."""
import argparse
import hashlib
import io
import json
import tarfile
import tempfile
from unittest.mock import Mock
import importlib.util
from pathlib import Path
import unittest
from unittest.mock import patch
ROOT=Path(__file__).resolve().parents[2]
def load(name,path):
    spec=importlib.util.spec_from_file_location(name,ROOT/path)
    module=importlib.util.module_from_spec(spec);spec.loader.exec_module(module);return module
manager=load('qingkong_manager','scripts/container/manage.py')
db=manager.database
class QingkongInstall(unittest.TestCase):
    def values(self):
        return dict(LAUNCHER_MODE='qingkong',RAID_EXTENSION='off',PLUGIN_MODE='none',
                    DOF_DATABASE_KIND='external',DOF_DB_HOST='10.8.0.7',DOF_DB_PORT='3306',
                    DOF_DB_SUBNET='172.29.86.0/24',DOF_DB_ALLOW_HOST='172.29.86.0/255.255.255.0',
                    DOF_DATABASE_UUID='11111111-1111-1111-1111-111111111111',GAME_DB_PASSWORD='abcdef12',
                    BIND_IP='0.0.0.0',ADMIN_PORT='18080',OPEN_CHANNEL='15,52')
    def test_profile_and_database_scope(self):
        values=self.values()
        self.assertEqual(manager.raid_assets.profile_for(values),'qingkong')
        self.assertEqual(manager.raid_assets.state_for(values,Path('/data')),Path('/data/qingkong'))
        self.assertTrue(manager.raid_assets.required(values))
        self.assertEqual(db.required_databases(values),db.GAME_DATABASES+('d_channel_cain','qk'))
        self.assertFalse(db.web_management_enabled(dict(values,DOF_ADMIN_ENABLED='true')))
        with self.assertRaises(ValueError): manager.raid_assets.profile_for(dict(values,RAID_EXTENSION='on'))
    def test_only_game_and_gateway_ports(self):
        ports=manager.published_ports(self.values())
        self.assertIn(('0.0.0.0',8887,'tcp'),ports)
        self.assertNotIn(18080,[p[1] for p in ports])
        self.assertIn('0.0.0.0:8887:8887/tcp',manager.ports_override(self.values())['services']['game']['ports'])
    def test_compose_removes_db_admin_and_uses_game_image_for_keys(self):
        model=dict(services={
            'game':dict(image='game:test',environment={},depends_on={'db':{},'keys':{}},networks={'private':None,'public':None}),
            'keys':dict(image='admin:test',environment={}),
            'admin':dict(image='admin:test',networks={},environment={}), 'db':{}},
            networks={'public':{},'private':{'internal':True}},volumes={'database':None})
        value=manager.deployment.configure(model,self.values(),Path('/instance/main'),db.required_databases(self.values()))
        self.assertEqual(set(value['services']),{'keys','game','db-tools'})
        self.assertEqual(value['services']['keys']['image'],'game:test')
        self.assertNotIn('db',value['services']['game']['depends_on'])
        self.assertEqual(value['services']['game']['environment']['MAIN_MYSQL_ROOT_PASSWORD'],'unused')
    def test_qingkong_acquisition_never_builds_admin(self):
        commands=[]
        with patch.object(manager.image_config,'references',return_value={'db':'db:test','admin':'admin:test','game':'game:test'}),patch.object(manager,'run',side_effect=lambda c,**k:commands.append(c)):
            manager.build({},database_mode='external',launcher_mode='qingkong')
        self.assertEqual(len(commands),1)
        self.assertIn('game:test',commands[0])
    def test_qingkong_requires_external_database_before_download(self):
        args=argparse.Namespace(launcher='qingkong',plugins='none',raid_extension='off',channels='15',admin_port=18080,database='internal',ip='43.155.147.117',bind='0.0.0.0')
        with self.assertRaisesRegex(ValueError,'external'):
            manager.initial_values(args,{'level':86,'game':{'sha256':'a'*64}},Path('/instance'))
    def test_ip_change_never_touches_web_database(self):
        with tempfile.TemporaryDirectory() as folder:
            root=Path(folder)/'instance';(root/'runtime/game/data/qingkong').mkdir(parents=True)
            values=self.values()
            with patch.object(manager.sys,'platform','linux'), patch.object(manager,'STATE',Path(folder)), \
                 patch.object(manager,'instance_path',return_value=root), patch.object(manager,'docker_check'), \
                 patch.object(manager,'read_env',return_value=values), patch.object(manager,'check_instance'), \
                 patch.object(manager,'check_ports'), patch.object(manager,'check_gateway_port'), \
                 patch.object(manager.raid_assets,'validate_game'), patch.object(manager,'validate_raid'), \
                 patch.object(manager,'apply_configuration') as apply, patch.object(manager,'update_admin_address') as web:
                manager.main(['config','--ip','43.155.147.117'])
                apply.assert_called_once();web.assert_not_called()
    def test_original_schema_preserves_names_and_filters_destructive_commands(self):
        module=load('qingkong_schema','scripts/container/qingkong_database.py')
        block=('DROP DATABASE qk;CREATE DATABASE qk;USE qk;CREATE TABLE `billing` (ID int);'
               'CREATE TABLE `qingkong_config` (ID int);'+''.join('CREATE TABLE `t'+str(i)+'` (ID int);' for i in range(18))+
               'INSERT INTO qingkong_config VALUES (1);')
        raw=b'prefix\0'+block.encode()+b'\0suffix'
        with patch.object(module,'GATEWAY_PIN',hashlib.sha256(raw).hexdigest()),patch.object(module,'SCHEMA_PIN',hashlib.sha256(block.encode()).hexdigest()):
            statements=module.statements(raw)
        self.assertEqual(len(statements),21)
        self.assertIn('INSERT INTO qingkong_config VALUES (1)',statements)
        self.assertFalse(any(s.startswith(('DROP','USE','CREATE DATABASE')) for s in statements))
        with self.assertRaises(ValueError):module.statements(raw+b'changed')
    def test_frida_grants_are_left_to_restricted_migration(self):
        with tempfile.TemporaryDirectory() as folder:
            root=Path(folder);instance=root/'instance';instance.mkdir()
            seed=root/'seed.sql';seed.write_text('-- seed')
            compat=root/'compat.sql';compat.write_text('-- compat')
            channel=root/'channel.tgz'
            with tarfile.open(channel,'w:gz') as archive:
                item=tarfile.TarInfo('d_channel.sql');item.size=0;archive.addfile(item,io.BytesIO())
            (root/'metadata').mkdir()
            (root/'metadata/build-inputs.lock.json').write_text(json.dumps(dict(files=[dict(path=p.name,sha256=hashlib.sha256(p.read_bytes()).hexdigest()) for p in (seed,compat,channel)])))
            calls=[];values=dict(self.values(),LAUNCHER_MODE='native',PLUGIN_MODE='frida')
            def query(sql):
                calls.append(sql)
                if sql=='SELECT VERSION(),@@server_uuid;':return '5.6.50\t'+values['DOF_DATABASE_UUID']
                if sql=='SHOW DATABASES;':return 'mysql'
                if sql.startswith('SELECT COUNT'):return '0'
                if sql.startswith('SHOW GRANTS'):return 'GRANT ALL PRIVILEGES ON *.* WITH GRANT OPTION'
                return ''
            with patch.object(db,'ROOT',root),patch.object(db,'SEED',seed),patch.object(db,'COMPAT',compat),patch.object(db,'CHANNEL_SEED',channel),patch.object(db,'prepare_frida') as frida,patch.object(db,'prepare_web',return_value=values['DOF_DATABASE_UUID']):
                db.initialize_external(instance,values,query)
                frida.assert_called_once()
            self.assertFalse(any(sql.startswith('GRANT ALL') and 'frida' in sql for sql in calls))
if __name__=='__main__': unittest.main()
