"""Preserve operator DP2 code and reject ambiguous duplicate hook installation."""
import importlib.util
from pathlib import Path
import unittest
from unittest.mock import patch
import hashlib

ROOT = Path(__file__).resolve().parents[3]
BASE = ROOT / 'services/game/runtime/ubuntu'
spec = importlib.util.spec_from_file_location('gateway_runtime', BASE / 'gateway/service.py')
runtime = importlib.util.module_from_spec(spec)
spec.loader.exec_module(runtime)


class AdapterInstallation(unittest.TestCase):
    def test_binary_script_preserved_and_installation_idempotent(self):
        original = b'// operator code \xff\r\nconst value = 1;\r\n'
        adapter = (BASE / 'gateway/db_adapter.js').read_bytes()
        installed = runtime.add_adapter(original, adapter)
        self.assertEqual(installed[len(adapter) + 1:], original)
        self.assertEqual(runtime.add_adapter(installed, adapter), installed)

    def test_different_adapter_is_not_silently_layered(self):
        with self.assertRaises(ValueError):
            runtime.add_adapter(b'function installDofDatabaseCredentials() {}', b'new adapter')
