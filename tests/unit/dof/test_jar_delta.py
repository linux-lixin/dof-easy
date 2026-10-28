import importlib.util
import io
from pathlib import Path
import tempfile
import unittest
import zipfile

spec=importlib.util.spec_from_file_location('delta',Path(__file__).resolve().parents[3]/'scripts/dof/jar_delta.py')
delta=importlib.util.module_from_spec(spec);spec.loader.exec_module(delta)


class JarDeltaTest(unittest.TestCase):
    def test_reuses_dependency_bytes_when_zip_metadata_and_offsets_change(self):
        with tempfile.TemporaryDirectory() as tmp:
            root=Path(tmp)
            dependency=bytes(range(256))*4096
            for name,year,prefix in [('base',2025,b'old'),('target',1980,b'a longer application prefix')]:
                with zipfile.ZipFile(root/name,'w') as archive:
                    archive.writestr('App.class',prefix)
                    info=zipfile.ZipInfo('BOOT-INF/lib/unchanged.jar',(year,1,1,0,0,0))
                    info.external_attr=(0o100755 if year==2025 else 0o100644)<<16
                    info.extra=b'\xfe\xca\x00\x00' if year==2025 else b''
                    archive.writestr(info,dependency)
            result=delta.prepare(root/'base',root/'target',root/'delta')
            self.assertLess(result['transferBytes'],4096)
            delta.apply(root/'base',root/'delta',root/'restored')
            self.assertEqual((root/'target').read_bytes(),(root/'restored').read_bytes())

    def test_reconstructs_data_descriptors_and_empty_members(self):
        class Streaming(io.BytesIO):
            def seekable(self):return False
            def seek(self,*args):raise io.UnsupportedOperation()
        with tempfile.TemporaryDirectory() as tmp:
            root=Path(tmp)
            for name,payload in [('base',b'old'),('target',b'new contents')]:
                stream=Streaming()
                with zipfile.ZipFile(stream,'w',compression=zipfile.ZIP_DEFLATED) as archive:
                    archive.writestr('empty',b'')
                    archive.writestr('changed',payload)
                    archive.writestr('same',bytes(range(256))*100)
                (root/name).write_bytes(stream.getvalue())
            delta.prepare(root/'base',root/'target',root/'delta')
            delta.apply(root/'base',root/'delta',root/'restored')
            self.assertEqual((root/'target').read_bytes(),(root/'restored').read_bytes())

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
