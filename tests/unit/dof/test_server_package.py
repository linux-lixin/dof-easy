import hashlib
import json
from pathlib import Path
import tarfile
import tempfile
import unittest
from unittest.mock import patch
from scripts.dof import package_server

class ServerPackage(unittest.TestCase):
    def test_fixed_allowlist_excludes_private_material_and_is_deterministic(self):
        with tempfile.TemporaryDirectory() as td:
            root=Path(td);bundle=root/'bundle';(bundle/'bin').mkdir(parents=True)
            payload=b'unit fixture; never executed';(bundle/'bin/dof-gateway').write_bytes(payload)
            (bundle/'privatekey.pem').write_bytes(b'private material must not be packaged')
            (bundle/'source-audit').mkdir();(bundle/'source-audit/derivation.json').write_text('{}\n')
            locked=dict(schema=2,sourceEdition='replacement-only',files=[dict(path='bin/dof-gateway',bytes=len(payload),sha256=hashlib.sha256(payload).hexdigest())])
            lock=root/'lock.json';lock.write_text(json.dumps(locked));(bundle/'manifest.json').write_text(json.dumps(locked))
            validation=root/'validation.json';validation.write_text(json.dumps(dict(runtimeManifest=locked,releaseReady=False)))
            with patch.object(package_server.assets,'LOCK',lock),patch.object(package_server.assets,'templates',return_value=[]):
                first=package_server.package(bundle,validation,root/'one.tar.gz')
                second=package_server.package(bundle,validation,root/'two.tar.gz')
                self.assertEqual(first['sha256'],second['sha256']);self.assertFalse(first['releaseReady'])
                with tarfile.open(root/'one.tar.gz') as tar:
                    self.assertFalse(any('privatekey' in name for name in tar.getnames()))
                    self.assertEqual(tar.extractfile('dof-server-runtime/bin/dof-gateway').read(),payload)
                validation.write_text(json.dumps(dict(runtimeManifest={},releaseReady=True)))
                with self.assertRaises(ValueError):package_server.package(bundle,validation,root/'bad.tar.gz')
                self.assertFalse((root/'bad.tar.gz').exists())
            self.assertEqual((bundle/'privatekey.pem').read_bytes(),b'private material must not be packaged')
