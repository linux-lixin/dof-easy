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

    def test_changed_native_definition_is_rejected_before_database_access(self):
        with tempfile.TemporaryDirectory() as td:
            binary = Path(td) / 'schema.sql'
            binary.write_bytes(b'prefix\0CREATE TABLE `billing` (altered int);\0suffix')
            with self.assertRaisesRegex(ValueError, 'differs from'):
                schema.definitions(binary)


if __name__ == '__main__':
    unittest.main()
