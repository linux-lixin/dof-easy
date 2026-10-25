"""Native gateway relocation, source preservation and process identity checks."""
import hashlib
import importlib.util
import json
import os
from pathlib import Path
import struct
import subprocess
import sys
import tempfile
import unittest
from unittest.mock import patch

ROOT = Path(__file__).resolve().parents[3]
BASE = ROOT / 'services/game/runtime/ubuntu'


def module(name, file):
    spec = importlib.util.spec_from_file_location(name, BASE / file)
    result = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(result)
    return result


gateway = module('gateway_binary_test', 'gateway/binary.py')


class Publication(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory()
        self.addCleanup(self.temp.cleanup)
        self.root = Path(self.temp.name)
        self.source = self.root / 'derived'
        self.destination = self.root / 'dof-gateway'
        self.data = b'fixed derived payload'
        self.source.write_bytes(self.data)
        self.expected = dict(path='bin/dof-gateway', bytes=len(self.data),
                             sha256=hashlib.sha256(self.data).hexdigest())

    def test_source_and_idempotent_runtime_inode_are_preserved(self):
        gateway.prepare(self.source, self.destination, self.expected)
        inode = self.destination.stat().st_ino
        gateway.prepare(self.source, self.destination, self.expected)
        self.assertEqual(self.source.read_bytes(), self.data)
        self.assertEqual(self.destination.read_bytes(), self.data)
        self.assertEqual(self.destination.stat().st_ino, inode)
        self.assertEqual(self.destination.stat().st_mode & 0o777, 0o700)

    def test_bad_hash_and_aliases_cannot_overwrite_previous_output(self):
        self.destination.write_bytes(b'previous')
        with self.assertRaises(ValueError):
            gateway.prepare(self.source, self.destination, dict(self.expected, sha256='0'*64))
        self.assertEqual(self.destination.read_bytes(), b'previous')
        alias = self.root / 'alias'
        alias.symlink_to(self.source)
        for source, output in [(alias, self.destination), (self.source, alias), (self.source, self.source)]:
            with self.assertRaises(ValueError):
                gateway.prepare(source, output, self.expected)
        self.assertEqual(self.source.read_bytes(), self.data)

    def test_failed_publication_preserves_previous_output_and_cleans_stage(self):
        self.destination.write_bytes(b'previous')
        with patch.object(Path, 'replace', side_effect=OSError('publication failed')):
            with self.assertRaises(OSError):
                gateway.prepare(self.source, self.destination, self.expected)
        self.assertEqual(self.destination.read_bytes(), b'previous')
        self.assertFalse(list(self.root.glob('.dof-install-*')))


class RuntimeIdentity(unittest.TestCase):
    def test_child_tracking_accepts_new_executable_and_ignores_old_one(self):
        runtime = module('gateway_identity_runtime', 'gateway/service.py')
        with tempfile.TemporaryDirectory() as td:
            children = Path(td) / 'children'
            children.write_text('11 12 13')
            real_path = Path
            def mapped(value):
                return children if str(value).endswith('/children') else real_path(value)
            paths = {'/proc/11/exe': '/opt/dof-easy/bin/dof-gateway', '/proc/12/exe': '/root/QK',
                     '/proc/13/exe': '/usr/bin/python3'}
            with patch.object(runtime, 'Path', mapped), patch.object(runtime.os, 'readlink', side_effect=paths.__getitem__):
                self.assertEqual(runtime.gateway_children(), [11])

    def test_health_requires_new_gateway_process(self):
        config = module('gateway_health_config', 'config.py')
        with patch.dict(sys.modules, {'config': config}):
            health = module('gateway_health_test', 'health.py')
        with tempfile.TemporaryDirectory() as td:
            root = Path(td)
            files = ['/etc/supervisor/conf.d/dof-gateway.conf', '/run/dof-easy/gateway-ready',
                     '/home/neople/game/publickey.pem', '/data/publickey.pem']
            mapping = {}
            for i, name in enumerate(files):
                mapping[name] = root / str(i)
                mapping[name].write_bytes(b'same key')
            real_path = Path
            def mapped(value):
                return mapping.get(str(value), real_path(value))
            processes = [(str(i), name, []) for i, name in enumerate(['gunnersvr', 'zergsvr', 'secagent', 'dof-gateway'])]
            processes.append(('10', 'df_game_r', ['siroco15']))
            env = dict(SERVER_TYPE='GAME', SERVER_GROUP='3', OPEN_CHANNEL='15', LAUNCHER_MODE='native',
                       DNF_DB_GAME_PASSWORD='fixture', MAIN_MYSQL_HOST='db', MAIN_MYSQL_PORT='3306',
                       MYSQL_HOST='db', MYSQL_PORT='3306')
            with patch.dict(os.environ, env, clear=True), patch.object(health, 'Path', mapped), \
                 patch.object(health, 'snapshot', return_value=(processes, {'tcp': {8887, 30015}, 'udp': {31015}})), \
                 patch.object(health.subprocess, 'run', return_value=subprocess.CompletedProcess([], 0)):
                self.assertTrue(health.check())
                processes[3] = ('3', 'QK', [])
                with self.assertRaisesRegex(ValueError, 'dof-gateway'):
                    health.check()



if __name__ == '__main__':
    unittest.main()
