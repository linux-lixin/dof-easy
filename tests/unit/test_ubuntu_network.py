"""Ubuntu routing must separate public advertisement from local/remote targets."""
import importlib.util
from pathlib import Path
import shutil
import sys
import tempfile
import unittest

ROOT = Path(__file__).resolve().parents[2]
ADAPTER = ROOT / 'services/game/runtime/ubuntu'
sys.path.insert(0, str(ADAPTER))
from config import validate


def core_env(**overrides):
    return dict(dict(SERVER_TYPE='CORE', PUBLIC_IP='192.0.2.10',
                     MAIN_MYSQL_HOST='10.2.4.20', MAIN_MYSQL_PORT='3306',
                     MAIN_MYSQL_ROOT_PASSWORD='DemoRoot', MYSQL_HOST='10.2.4.20',
                     MYSQL_PORT='3306', DNF_DB_ROOT_PASSWORD='DemoRoot',
                     DNF_DB_GAME_PASSWORD='Demo1234', WEB_PASS='DemoWeb',
                     GAME_CHANNEL_HOSTS='11=10.2.4.11,52=10.2.4.12'),
                **overrides)


class NetworkConfiguration(unittest.TestCase):
    def test_core_normalizes_channel_ranges_and_host_order(self):
        env = validate(core_env(OPEN_CHANNEL='52,11-12',
                               GAME_CHANNEL_HOSTS='52=10.2.4.12,12=10.2.4.11,11=10.2.4.11'))
        self.assertEqual(env['OPEN_CHANNEL'], '11,12,52')
        self.assertEqual(env['GAME_CHANNEL_HOSTS'], '11=10.2.4.11,12=10.2.4.11,52=10.2.4.12')

    def test_core_rejects_incomplete_ambiguous_or_invalid_host_maps(self):
        for value in ('', '11=10.2.4.11', '11=10.2.4.11,52=10.2.4.12,56=10.2.4.13',
                      '11=10.2.4.11,11=10.2.4.12,52=10.2.4.12',
                      '11=10.2.4.11,011=10.2.4.12,52=10.2.4.12',
                      '11:10.2.4.11,52=10.2.4.12', '11=host.example,52=10.2.4.12',
                      '11=::1,52=10.2.4.12', '11=999.2.4.11,52=10.2.4.12'):
            with self.subTest(value=value), self.assertRaisesRegex(ValueError, 'GAME_CHANNEL_HOSTS'):
                validate(core_env(GAME_CHANNEL_HOSTS=value))

    def test_all_routes_only_configured_channels_to_loopback(self):
        env = validate(core_env(SERVER_TYPE='ALL', OPEN_CHANNEL='1,11,52', P2P_RELAY_INDEX='1'))
        self.assertEqual(env['GAME_CHANNEL_HOSTS'], '1=127.0.0.1,11=127.0.0.1,52=127.0.0.1')

    def network_module(self):
        path = ADAPTER / 'network_config.py'
        self.assertTrue(path.exists(), 'Ubuntu runtime routing generator is missing')
        spec = importlib.util.spec_from_file_location('network_config', path)
        module = importlib.util.module_from_spec(spec)
        spec.loader.exec_module(module)
        return module

    def test_generated_tables_use_local_core_and_actual_remote_channels(self):
        network = self.network_module()
        original = ROOT / 'services/game/assets/home/template/neople'
        with tempfile.TemporaryDirectory() as td:
            root = Path(td)
            for name in ('manager', 'dbmw_mnt', 'dbmw_guild', 'dbmw_stat', 'statics', 'coserver'):
                shutil.copytree(original/name/'table', root/name/'table')
            env = validate(core_env(SERVER_GROUP='6', OPEN_CHANNEL='1,52',
                                    GAME_CHANNEL_HOSTS='1=10.2.4.11,52=10.2.4.12'))
            network.configure(root, env)
            def rows(name):
                return [line.split() for line in (root/name/'table/server_config.tbl').read_text().splitlines()
                        if line.strip() and not line.lstrip().startswith('#')]
            self.assertEqual(rows('manager'), [['3', '6', '127.0.0.1', '36303']])
            self.assertEqual(rows('dbmw_mnt'), [['3', '6', '201', '127.0.0.1', '36303']])
            self.assertEqual(rows('dbmw_guild'), [['5', '6', '203', '127.0.0.1', '36403']])
            self.assertEqual(rows('dbmw_stat'), [['7', '6', '205', '127.0.0.1', '36503']])
            game_rows = [['1', '6', '1', '10.2.4.11', '61001'],
                         ['1', '6', '52', '10.2.4.12', '61052']]
            self.assertEqual(rows('coserver'), game_rows)
            self.assertEqual(rows('statics'), game_rows + [['2', '6', '200', '127.0.0.1', '20303']])
            before = {path: path.read_bytes() for path in root.rglob('*.tbl')}
            network.configure(root, env)
            self.assertEqual({path: path.read_bytes() for path in before}, before)

    def test_non_core_roles_do_not_require_core_templates_or_routes(self):
        network = self.network_module()
        with tempfile.TemporaryDirectory() as td:
            root = Path(td)
            for role in ('GAME', 'P2P'):
                network.configure(root, {'SERVER_TYPE': role})
            self.assertEqual(list(root.iterdir()), [])


if __name__ == '__main__':
    unittest.main()
