import importlib.util
from pathlib import Path
import tempfile
import unittest
import zipfile

spec=importlib.util.spec_from_file_location('delta',Path(__file__).resolve().parents[3]/'scripts/dof/jar_delta.py')
delta=importlib.util.module_from_spec(spec);spec.loader.exec_module(delta)


class JarDeltaTest(unittest.TestCase):
    def test_exact_archive_reconstruction_and_corruption_refusal(self):
        with tempfile.TemporaryDirectory() as tmp:
            root=Path(tmp)
            for name, data in [('base',b'before'),('target',b'after')]:
                with zipfile.ZipFile(root/name,'w') as archive:
                    archive.writestr('unchanged.jar',bytes(range(256))*100)
                    archive.writestr('App.class',data,compress_type=zipfile.ZIP_DEFLATED)
            result=delta.prepare(root/'base',root/'target',root/'delta')
            self.assertLess(result['transferBytes'],result['targetBytes']//2)
            delta.apply(root/'base',root/'delta',root/'restored')
            self.assertEqual((root/'target').read_bytes(),(root/'restored').read_bytes())
            (root/'delta/payload.bin').write_bytes(b'corrupt')
            with self.assertRaises(ValueError):delta.apply(root/'base',root/'delta',root/'bad')
            self.assertFalse((root/'bad').exists())


if __name__=='__main__':unittest.main()
