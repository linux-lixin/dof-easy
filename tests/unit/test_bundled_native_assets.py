"""Verify deployable native inputs survive Git publication and reproduce exactly."""
import hashlib
import json
from pathlib import Path
import tempfile
import unittest
from unittest.mock import patch
from scripts.container import manage, resources
from scripts.dof.build_server_runtime import build

ROOT=Path(__file__).resolve().parents[2]


class BundledNativeAssets(unittest.TestCase):
    def test_default_bundle_installs_without_network_or_operator_inputs(self):
        with patch('urllib.request.urlopen',side_effect=AssertionError('Unexpected download')), tempfile.TemporaryDirectory() as temp:
            self.assertEqual(resources.verify_native_inputs(),27)
            bundle=manage.default_dof_assets({'LAUNCHER_MODE':'native'})
            self.assertEqual(bundle,ROOT/'services/game/native-assets')
            target=Path(temp)/'state'
            manage.raid_assets.install(bundle,target)
            manage.raid_assets.verify(target/'assets')
            manage.raid_assets.verify_config(target)
            self.assertEqual(list((target/'keys').iterdir()),[])
            self.assertFalse((target/'assets/source-audit').exists())

    def test_committed_originals_reproduce_formal_payloads(self):
        source=ROOT/'metadata/dof/source-audit/server-inputs/files'
        original={p.relative_to(source).as_posix():hashlib.sha256(p.read_bytes()).hexdigest()
                  for p in source.rglob('*') if p.is_file()}
        self.assertEqual(len(original),12)
        with tempfile.TemporaryDirectory() as temp:
            output=Path(temp)/'bundle'
            build(source,output)
            manage.raid_assets.verify(output)
            committed=ROOT/'services/game/native-assets'
            for row in manage.raid_assets.manifest():
                self.assertEqual((output/row['path']).read_bytes(),(committed/row['path']).read_bytes())
            for name,_ in manage.raid_assets.templates():
                self.assertEqual((output/'config'/name).read_bytes(),(committed/'config'/name).read_bytes())
        for path,sha in original.items():
            self.assertEqual(hashlib.sha256((source/path).read_bytes()).hexdigest(),sha)


if __name__=='__main__':unittest.main()
