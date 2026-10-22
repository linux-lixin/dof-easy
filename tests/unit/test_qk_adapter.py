"""Preserve operator DP2 code and reject ambiguous duplicate hook installation."""
import importlib.util
from pathlib import Path
import unittest
from unittest.mock import patch
import hashlib

ROOT = Path(__file__).resolve().parents[2]
BASE = ROOT / 'services/game/runtime/ubuntu'
spec = importlib.util.spec_from_file_location('qk', BASE / 'qk.py')
qk = importlib.util.module_from_spec(spec)
spec.loader.exec_module(qk)


class AdapterInstallation(unittest.TestCase):
    def test_binary_script_preserved_and_installation_idempotent(self):
        original = b'// operator code \xff\r\nconst value = 1;\r\n'
        adapter = (BASE / 'qk-db-adapter.js').read_bytes()
        installed = qk.add_adapter(original, adapter)
        self.assertEqual(installed[len(adapter) + 1:], original)
        self.assertEqual(qk.add_adapter(installed, adapter), installed)

    def test_different_adapter_is_not_silently_layered(self):
        with self.assertRaises(ValueError):
            qk.add_adapter(b'function installQkDatabaseCredentials() {}', b'new adapter')

    def test_only_known_previous_prefix_can_be_upgraded(self):
        old = b'(function installQkDatabaseCredentials() {\n})();\n'
        body = b'// operator code\r\n\xff'
        with patch.object(qk, 'PREVIOUS_ADAPTERS', {hashlib.sha256(old).hexdigest()}):
            self.assertEqual(qk.add_adapter(old + b'\n' + body, b'new'), b'new\n' + body)
            with self.assertRaises(ValueError):
                qk.add_adapter(old.replace(b'{', b'{ ') + b'\n' + body, b'new')
