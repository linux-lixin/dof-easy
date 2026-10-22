import importlib.util
from pathlib import Path
import tempfile
import unittest

ROOT=Path(__file__).resolve().parents[2]
spec=importlib.util.spec_from_file_location('configure86',ROOT/'deploy/vipai86/configure.py')
module=importlib.util.module_from_spec(spec);spec.loader.exec_module(module)


class DeploymentTests(unittest.TestCase):
    def test_configuration_never_replaces_existing_secrets(self):
        with tempfile.TemporaryDirectory() as td:
            path=module.configure(td,'127.0.0.1'); original=path.read_bytes()
            with self.assertRaises(ValueError): module.configure(td,'127.0.0.2')
            self.assertEqual(path.read_bytes(),original)

    def test_incomplete_download_rejected_before_configuration(self):
        with tempfile.TemporaryDirectory() as td:
            root=Path(td);pvf=root/'Script.pvf.baiduyun.p.downloading';pvf.write_bytes(b'x'*2048)
            with self.assertRaises(ValueError): module.configure(root/'deploy','127.0.0.1',pvf)
            self.assertFalse((root/'deploy/.env').exists())

    def test_pvf_copied_without_modifying_source(self):
        with tempfile.TemporaryDirectory() as td:
            root=Path(td);pvf=root/'Script.pvf';pvf.write_bytes(b'x'*2048)
            module.configure(root/'deploy','127.0.0.1',pvf)
            self.assertEqual((root/'deploy/runtime/game/data/Script.pvf').read_bytes(),pvf.read_bytes())

if __name__=='__main__': unittest.main()
