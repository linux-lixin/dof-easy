"""Packaging must not reintroduce legacy key backups through the resource tree."""
import importlib.util
import json
import os
from pathlib import Path
import tempfile
import unittest
import zipfile

ROOT = Path(__file__).resolve().parents[3]
spec = importlib.util.spec_from_file_location('package_admin', ROOT / 'scripts/dof/package_admin.py')
package_admin = importlib.util.module_from_spec(spec)
spec.loader.exec_module(package_admin)


class AdminPackageTest(unittest.TestCase):
    def test_same_payloads_package_identically_after_timestamp_and_mode_changes(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            classes, resources, frontend = [root / name for name in ('classes', 'resources', 'frontend')]
            controller = classes / 'com/aiyi/game/dnfserver/controller/DofAdminController.class'
            controller.parent.mkdir(parents=True)
            controller.write_bytes(b'synthetic controller')
            resources.mkdir()
            (resources / 'scope.json').write_text('{"completeReplacement":false}')
            frontend.mkdir()
            (frontend / 'index.html').write_text('<html>synthetic frontend</html>')
            base = root / 'base.jar'
            with zipfile.ZipFile(base, 'w') as archive:
                archive.writestr('org/springframework/boot/loader/JarLauncher.class', b'synthetic loader')
                archive.writestr('BOOT-INF/lib/dependency.jar', b'synthetic dependency',
                                 compress_type=zipfile.ZIP_STORED)
            first, second = root / 'first', root / 'second'
            hashes = package_admin.package(base, classes, resources, frontend, first)
            for folder in (classes, resources, frontend):
                for path in folder.rglob('*'):
                    os.utime(path, (946684800, 946684800))
                    if path.is_file():
                        path.chmod(0o600)
            self.assertEqual(hashes, package_admin.package(base, classes, resources, frontend, second))
            for path in first.rglob('*'):
                counterpart = second / path.relative_to(first)
                self.assertEqual(path.stat().st_mtime_ns, counterpart.stat().st_mtime_ns)
                self.assertEqual(path.stat().st_mode, counterpart.stat().st_mode)
                if path.is_file():
                    self.assertEqual(path.read_bytes(), counterpart.read_bytes())
            manifest = json.loads((first / 'build-manifest.json').read_text())
            self.assertEqual(manifest['packagingPolicy']['id'], 'dof-admin-prebuilt-v2')
            self.assertIn('baseJarSHA256', manifest['inputs'])
            with zipfile.ZipFile(first / 'app.jar') as archive:
                self.assertEqual(archive.getinfo('BOOT-INF/lib/dependency.jar').compress_type,
                                 zipfile.ZIP_STORED)
                self.assertTrue(all(i.date_time == (1980, 1, 1, 0, 0, 0) for i in archive.infolist()))
            # A real content change must not be hidden by timestamp normalization.
            controller.write_bytes(b'changed synthetic controller')
            changed = package_admin.package(base, classes, resources, frontend, root / 'changed')
            self.assertNotEqual(hashes['app.jar'], changed['app.jar'])

    def test_archives_exclude_keys_with_backup_suffixes_and_old_base_resources(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            classes, resources, frontend = [root / name for name in ('classes', 'resources', 'frontend')]
            for folder in (classes, resources, frontend):
                folder.mkdir()
            controller = classes / 'com/aiyi/game/dnfserver/controller/DofAdminController.class'
            controller.parent.mkdir(parents=True)
            controller.write_bytes(b'synthetic compiled class fixture')
            (frontend / 'index.html').write_text('<html>synthetic frontend</html>')
            (resources / 'scope.json').write_text('{"completeReplacement":false}')
            for name in ('private.key', 'private.key.server.bak', 'server.PEM.old', 'state.p12.bak', 'state.pfx.backup'):
                (resources / name).write_text('synthetic secret marker; never a real key')
            base = root / 'base.jar'
            with zipfile.ZipFile(base, 'w') as archive:
                archive.writestr('org/springframework/boot/loader/JarLauncher.class', b'synthetic loader')
                archive.writestr('BOOT-INF/lib/dependency.jar', b'synthetic dependency')
                archive.writestr('BOOT-INF/classes/private.key.server.bak', b'synthetic old key')
            output = root / 'output'
            package_admin.package(base, classes, resources, frontend, output)
            with zipfile.ZipFile(output / 'app.jar') as archive:
                names = archive.namelist()
                self.assertIn('BOOT-INF/classes/scope.json', names)
                self.assertIn('BOOT-INF/classes/com/aiyi/game/dnfserver/controller/DofAdminController.class', names)
                self.assertIn('BOOT-INF/lib/dependency.jar', names)
                self.assertFalse(any('synthetic secret marker' in archive.read(name).decode(errors='ignore') for name in names))
                self.assertFalse(any('private.key' in name or '.PEM.' in name or '.p12.' in name or '.pfx.' in name for name in names))


if __name__ == '__main__':
    unittest.main()
