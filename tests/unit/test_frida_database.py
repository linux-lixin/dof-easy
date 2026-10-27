"""Synthetic metadata tests; not native component or real-MySQL acceptance."""
import importlib.util
import json
from pathlib import Path
import tempfile
import unittest

ROOT = Path(__file__).resolve().parents[2]
spec = importlib.util.spec_from_file_location('frida_db_installer', ROOT/'scripts/container/database.py')
db = importlib.util.module_from_spec(spec); spec.loader.exec_module(db)
UUID = '11111111-1111-1111-1111-111111111111'
COLUMNS = 'event_id|varchar(30)|NO|<NULL>||utf8\nevent_info|mediumtext|YES|<NULL>||utf8'


class Metadata:
    def __init__(self, exists=False):
        self.exists = exists; self.calls = []; self.engine = 'BASE TABLE|InnoDB|utf8_general_ci'
        self.columns = COLUMNS; self.primary = 'event_id|<NULL>'; self.unique = 'PRIMARY'
        self.account = '1'; self.uuid = UUID; self.fail_grant = False
        self.triggers = '0'; self.references = '0'

    def __call__(self, sql):
        self.calls.append(sql)
        if sql == 'SELECT VERSION(),@@server_uuid;': return '5.6.51\t'+self.uuid
        if 'FROM mysql.user' in sql: return self.account
        if 'FROM information_schema.TABLES' in sql: return self.engine if self.exists else ''
        if 'FROM information_schema.COLUMNS' in sql: return self.columns
        if 'FROM information_schema.STATISTICS' in sql: return self.unique if 'DISTINCT' in sql else self.primary
        if 'FROM information_schema.TRIGGERS' in sql: return self.triggers
        if 'FROM information_schema.KEY_COLUMN_USAGE' in sql: return self.references
        if sql == db.FRIDA_TABLE: self.exists = True; return ''
        if sql.startswith('GRANT') and self.fail_grant: raise ValueError('synthetic grant failure')
        if sql.startswith(('GRANT ', 'CREATE DATABASE ')): return ''
        raise AssertionError(sql)

    def writes(self): return [s for s in self.calls if s.startswith(('GRANT','CREATE'))]


class FridaDatabase(unittest.TestCase):
    def setup_instance(self, root):
        values = dict(PLUGIN_MODE='dp2-frida', DOF_DATABASE_UUID=UUID, DOF_DB_HOST='172.17.0.1', DOF_DB_PORT='3306', DOF_DB_ALLOW_HOST='172.29.86.0/255.255.255.0')
        db.private_json(root/'external-database.json',dict(databaseUuid=UUID,host=values['DOF_DB_HOST'],port='3306',grantHost=values['DOF_DB_ALLOW_HOST'],instanceId='dof-'+root.name,state='ready',databases=list(db.DATABASES)))
        return values

    def test_disabled_modes_do_not_query_or_create(self):
        for mode in ('none','dp2',None):
            query=Metadata()
            self.assertFalse(db.prepare_frida(Path('/not-used'),{'PLUGIN_MODE':mode},query,UUID))
            self.assertEqual(query.calls,[])

    def test_fresh_known_schema_scoped_grant_and_durable_result(self):
        with tempfile.TemporaryDirectory() as folder:
            root=Path(folder); values=self.setup_instance(root); query=Metadata()
            self.assertTrue(db.prepare_frida(root,values,query,UUID))
            record=json.loads((root/'plugin-databases.json').read_text())
            self.assertEqual(record['state'],'ready')
            self.assertEqual((root/'plugin-databases.json').stat().st_mode & 0o777,0o600)
            self.assertEqual(query.writes(),['CREATE DATABASE IF NOT EXISTS frida CHARACTER SET utf8;',db.FRIDA_TABLE,"GRANT SELECT, INSERT, UPDATE, DELETE, CREATE, LOCK TABLES ON frida.* TO 'game'@'172.29.86.0/255.255.255.0';"])
            self.assertNotIn('PASSWORD','\n'.join(query.calls))

    def test_compatible_existing_table_never_recreates_or_changes_progress(self):
        with tempfile.TemporaryDirectory() as folder:
            root=Path(folder); values=self.setup_instance(root); query=Metadata(True)
            db.prepare_frida(root,values,query,UUID)
            self.assertNotIn(db.FRIDA_TABLE,query.calls)
            self.assertFalse(any(s.startswith(('INSERT','DELETE','UPDATE','REPLACE','DROP','ALTER','TRUNCATE')) for s in query.calls))

    def test_unknown_schema_refused_before_any_write(self):
        for key,value in [('engine','BASE TABLE|MyISAM|utf8_general_ci'),('engine','VIEW||utf8_general_ci'),('columns',COLUMNS+'\nextra|int(11)|NO|<NULL>||'),('columns',COLUMNS.replace('varchar(30)','varchar(20)')),('primary','event_id|10'),('unique','PRIMARY\nother_unique')]:
            with self.subTest(key=key,value=value),tempfile.TemporaryDirectory() as folder:
                root=Path(folder); values=self.setup_instance(root); query=Metadata(True);setattr(query,key,value)
                with self.assertRaisesRegex(ValueError,'不兼容'):db.prepare_frida(root,values,query,UUID)
                self.assertEqual(query.writes(),[]);self.assertFalse((root/'plugin-databases.json').exists())

    def test_wrong_database_or_foreign_binding_never_writes(self):
        for patch in [dict(DOF_DATABASE_UUID='22222222-2222-2222-2222-222222222222'),dict(DOF_DB_HOST='172.17.0.2'),dict(DOF_DB_ALLOW_HOST='%')]:
            with tempfile.TemporaryDirectory() as folder:
                root=Path(folder);values=self.setup_instance(root);values.update(patch);query=Metadata()
                with self.assertRaises(ValueError):db.prepare_frida(root,values,query,UUID)
                self.assertEqual(query.writes(),[])

    def test_unknown_trigger_or_foreign_key_does_not_change_progress(self):
        for key in ('triggers','references'):
            with tempfile.TemporaryDirectory() as folder:
                root=Path(folder);values=self.setup_instance(root);query=Metadata(True);setattr(query,key,'1')
                with self.assertRaisesRegex(ValueError,'未知触发器或外键'):db.prepare_frida(root,values,query,UUID)
                self.assertEqual(query.writes(),[])

    def test_missing_account_not_created_or_password_reset(self):
        with tempfile.TemporaryDirectory() as folder:
            root=Path(folder);values=self.setup_instance(root);query=Metadata();query.account='0'
            with self.assertRaisesRegex(ValueError,'账号不存在'):db.prepare_frida(root,values,query,UUID)
            self.assertEqual(query.writes(),[])

    def test_interrupted_grant_preserves_operation_and_does_not_recreate(self):
        with tempfile.TemporaryDirectory() as folder:
            root=Path(folder);values=self.setup_instance(root);query=Metadata();query.fail_grant=True
            with self.assertRaisesRegex(ValueError,'synthetic grant'):db.prepare_frida(root,values,query,UUID)
            before=json.loads((root/'plugin-databases.json').read_text());self.assertEqual(before['state'],'applying')
            query.calls=[];query.fail_grant=False;db.prepare_frida(root,values,query,UUID)
            after=json.loads((root/'plugin-databases.json').read_text());self.assertEqual(after['operationId'],before['operationId']);self.assertEqual(after['state'],'ready');self.assertNotIn(db.FRIDA_TABLE,query.calls)

    def test_marker_symlink_and_foreign_journal_refused(self):
        with tempfile.TemporaryDirectory() as folder:
            root=Path(folder);values=self.setup_instance(root);query=Metadata()
            (root/'other').write_text('{}');(root/'plugin-databases.json').symlink_to(root/'other')
            with self.assertRaisesRegex(ValueError,'符号链接'):db.prepare_frida(root,values,query,UUID)
            self.assertEqual(query.writes(),[])
            (root/'plugin-databases.json').unlink();(root/'plugin-databases.json').write_text('{"databaseUuid":"foreign"}')
            with self.assertRaisesRegex(ValueError,'冲突'):db.prepare_frida(root,values,query,UUID)
            self.assertEqual(query.writes(),[])

    def test_disabled_plugin_progress_still_in_backup(self):
        with tempfile.TemporaryDirectory() as folder:
            root=Path(folder);values=self.setup_instance(root);values['PLUGIN_MODE']='none'
            self.assertNotIn('frida',db.backup_databases(root,values))
            p=root/'external-database.json';j=json.loads(p.read_text());j['databases'].append('frida');db.private_json(p,j)
            self.assertIn('frida',db.backup_databases(root,values));self.assertNotIn('frida',db.required_databases(values))
            j['databaseUuid']='foreign';db.private_json(p,j)
            with self.assertRaisesRegex(ValueError,'身份'):db.backup_databases(root,values)


if __name__ == '__main__': unittest.main()
