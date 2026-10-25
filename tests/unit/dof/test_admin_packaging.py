import importlib.util
from pathlib import Path
import tempfile
import unittest
import zipfile

ROOT = Path(__file__).resolve().parents[3]
spec = importlib.util.spec_from_file_location('qingkong_packager', ROOT / 'scripts/dof/package_admin.py')
module = importlib.util.module_from_spec(spec)
spec.loader.exec_module(module)


class DofPackagingTest(unittest.TestCase):
    def test_boot_directory_nested_jar_and_sensitive_file_exclusion(self):
        with tempfile.TemporaryDirectory() as tmp:
            root = Path(tmp)
            classes, resources, frontend = (root / x for x in ('classes', 'resources', 'frontend'))
            controller = classes / 'com/aiyi/game/dnfserver/controller/DofAdminController.class'
            controller.parent.mkdir(parents=True)
            controller.write_bytes(b'new-controller')
            (classes / 'SecretTest.class').write_bytes(b'test')
            resources.mkdir(); (resources / 'private.pem').write_bytes(b'private')
            (resources / 'application.yml').write_bytes(b'safe-config')
            frontend.mkdir(); (frontend / 'index.html').write_bytes(b'<html></html>')
            with zipfile.ZipFile(root / 'base.jar', 'w') as base:
                base.writestr('BOOT-INF/classes/', b'')
                base.writestr('BOOT-INF/classes/Old.class', b'old')
                base.writestr('BOOT-INF/lib/dependency.jar', b'nested', compress_type=zipfile.ZIP_STORED)
            module.package(root / 'base.jar', classes, resources, frontend, root / 'result')
            with zipfile.ZipFile(root / 'result/app.jar') as built:
                self.assertTrue(built.getinfo('BOOT-INF/classes/').is_dir())
                self.assertTrue(built.getinfo('BOOT-INF/classes/com/aiyi/game/dnfserver/').is_dir())
                self.assertTrue(built.getinfo('BOOT-INF/classes/com/aiyi/game/dnfserver/controller/').is_dir())
                self.assertEqual(built.getinfo('BOOT-INF/lib/dependency.jar').compress_type, zipfile.ZIP_STORED)
                self.assertEqual(built.read('BOOT-INF/classes/application.yml'), b'safe-config')
                self.assertNotIn('BOOT-INF/classes/Old.class', built.namelist())
                self.assertNotIn('BOOT-INF/classes/SecretTest.class', built.namelist())
                self.assertNotIn('BOOT-INF/classes/private.pem', built.namelist())
                self.assertEqual(len(built.namelist()), len(set(built.namelist())))


if __name__ == '__main__':
    unittest.main()
