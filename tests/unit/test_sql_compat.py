"""The compatibility seed contains only explicitly selected missing-table DDL."""
import hashlib
import importlib.util
import io
from pathlib import Path
import tarfile
import tempfile
import unittest

ROOT = Path(__file__).resolve().parents[2]
SCRIPT = ROOT / 'deploy/database/generate_compat.py'


class CompatibilitySeed(unittest.TestCase):
    def module(self):
        self.assertTrue(SCRIPT.exists(), 'missing safe compatibility generator')
        spec = importlib.util.spec_from_file_location('generate_compat', SCRIPT)
        module = importlib.util.module_from_spec(spec)
        spec.loader.exec_module(module)
        return module

    def test_only_allowlisted_create_statements_are_emitted(self):
        module = self.module()
        with tempfile.TemporaryDirectory() as directory:
            archive = Path(directory) / 'seed.tgz'
            with tarfile.open(archive, 'w:gz') as stream:
                for database, tables in module.TABLES.items():
                    data = ('DROP TABLE `existing`;\nINSERT INTO `existing` VALUES (1);\n' +
                            '\n'.join('CREATE TABLE `' + table + '` (\n `id` int NOT NULL, PRIMARY KEY (`id`)\n) ENGINE=InnoDB;' for table in tables)).encode()
                    entry = tarfile.TarInfo(database + '.sql')
                    entry.size = len(data)
                    stream.addfile(entry, io.BytesIO(data))
            output = module.generate(archive, hashlib.sha256(archive.read_bytes()).hexdigest())
            self.assertEqual(output.count('CREATE TABLE IF NOT EXISTS'), 5)
            self.assertNotIn('DROP', output)
            self.assertNotIn('INSERT', output)
            self.assertNotIn('UPDATE', output)
            self.assertIn('`taiwan_cain_auction_cera`.`auction_history_201603`', output)
            self.assertIn('`taiwan_cain_log`.`log_response_time_201604`', output)
            with self.assertRaisesRegex(ValueError, 'hash mismatch'):
                module.generate(archive, '0' * 64)

    def test_missing_template_is_rejected_without_partial_sql(self):
        module = self.module()
        with tempfile.TemporaryDirectory() as directory:
            archive = Path(directory) / 'seed.tgz'
            with tarfile.open(archive, 'w:gz'):
                pass
            with self.assertRaisesRegex(ValueError, 'missing'):
                module.generate(archive, hashlib.sha256(archive.read_bytes()).hexdigest())

    def test_committed_seed_is_reproducible_from_pinned_archive(self):
        module = self.module()
        output = module.generate(ROOT / 'services/game/assets/home/template/init/init_sql.tgz')
        self.assertEqual(output, (SCRIPT.parent / 'compat-tables.sql').read_text(encoding='utf-8'))


if __name__ == '__main__':
    unittest.main()
