import importlib.util
from pathlib import Path
import tempfile
import sys
sys.path.insert(0, str(Path(__file__).resolve().parents[3] / 'services/game/runtime/ubuntu'))
import unittest
from unittest.mock import patch

ROOT = Path(__file__).resolve().parents[3]
spec = importlib.util.spec_from_file_location('dof_schema_test', ROOT / 'services/game/runtime/ubuntu/gateway/schema.py')
schema = importlib.util.module_from_spec(spec)
spec.loader.exec_module(schema)


class BaseSchema(unittest.TestCase):
    def test_missing_table_is_seeded_without_resetting_existing_configuration(self):
        creates = {'billing': 'CREATE TABLE `billing` (ID int)',
                   'config_v2': 'CREATE TABLE `config_v2` (ID int)'}
        seeds = {name: 'INSERT INTO `' + name + '` VALUES (1)' for name in creates}
        calls = []
        def query(sql):
            calls.append(sql)
            return 'config_v2' if 'information_schema' in sql else ''
        with patch.object(schema, 'definitions', return_value=(creates, seeds)):
            self.assertEqual(schema.initialize('verified-native-file', query), ['billing'])
        mutations = [s for s in calls if s.startswith('USE')]
        self.assertEqual(mutations, ['USE dof_native;' + creates['billing'], 'USE dof_native;' + seeds['billing']])

    def test_feature_tables_are_created_empty_without_replacing_existing_rewards(self):
        import re
        raw = (ROOT / 'services/game/runtime/ubuntu/gateway/feature-schema.sql').read_text()
        creates = {}
        for statement in raw.split(';'):
            statement = statement.strip()
            if statement:
                match = re.fullmatch(r"CREATE TABLE `([a-z_]+)` \(.*\) ENGINE=MyISAM DEFAULT CHARSET=utf8", statement, re.S)
                self.assertIsNotNone(match)
                creates[match[1]] = statement
        self.assertEqual(set(creates), {'randombox', 'randombox_config', 'randombox_user'})
        existing = {'randombox'}
        calls = []
        def query(sql):
            if 'information_schema' in sql:
                return '\n'.join(existing)
            calls.append(sql)
            match = re.search(r'CREATE TABLE `([a-z_]+)`', sql)
            if match:
                existing.add(match[1])
            return ''
        with patch.object(schema, 'definitions', return_value=(creates, {})):
            self.assertEqual(schema.initialize('pinned', query), ['randombox_config', 'randombox_user'])
            self.assertEqual(schema.initialize('pinned', query), [])
        self.assertEqual(sum('USE dof_native;' in q for q in calls), 2)
        self.assertFalse(any(word in q for q in calls for word in ('INSERT', 'DROP', 'UPDATE', 'DELETE', 'TRUNCATE')))
        self.assertFalse(any('CREATE TABLE `randombox`' in q for q in calls))

    def test_changed_native_definition_is_rejected_before_database_access(self):
        with tempfile.TemporaryDirectory() as td:
            binary = Path(td) / 'schema.sql'
            binary.write_bytes(b'prefix\0CREATE TABLE `billing` (altered int);\0suffix')
            with self.assertRaisesRegex(ValueError, 'differs from'):
                schema.definitions(binary)


if __name__ == '__main__':
    unittest.main()
