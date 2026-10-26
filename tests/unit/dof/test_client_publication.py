import hashlib
import importlib.util
import io
import json
import shutil
import subprocess
from pathlib import Path
import sys
import tarfile
import tempfile
import unittest
from unittest.mock import patch
from types import SimpleNamespace

ROOT = Path(__file__).resolve().parents[3]


def load(name, path):
    spec = importlib.util.spec_from_file_location(name, path)
    module = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(module)
    return module


prepare = load('dof_inputs', ROOT / 'scripts/protocol/dof/prepare_client_inputs.py')
with patch.object(sys, 'path', [str(ROOT / 'scripts/dof')] + sys.path):
    install = load('dof_input_install', ROOT / 'scripts/dof/install_client_inputs.py')
DATABASE_UUID = '00000000-0000-4000-8000-000000000001'


class PublicationInputTests(unittest.TestCase):
    def test_private_input_copy_excludes_unrelated_files_and_refuses_overwrite(self):
        with tempfile.TemporaryDirectory() as folder:
            root = Path(folder); client = root / 'client'; client.mkdir()
            files = {'启动器.exe': b'launcher', 'ijl15.dll': b'library', 'qingkong.dll': b'config'}
            for name, value in files.items(): (client / name).write_bytes(value)
            (client / 'login-password.ini').write_text('PRIVATE')
            key = root / 'public.der'; key.write_bytes(b'\x30\x82test-public')
            outputs = {'dof-launcher.exe': b'derived-launcher', 'ijl15.dll': b'derived-library'}
            pins = {name: hashlib.sha256(raw).hexdigest() for name, raw in outputs.items()}
            sources = {'dof-launcher.exe': ('启动器.exe', hashlib.sha256(files['启动器.exe']).hexdigest()),
                       'ijl15.dll': ('ijl15.dll', hashlib.sha256(files['ijl15.dll']).hexdigest())}
            def derive(raw):
                name = 'dof-launcher.exe' if raw == b'launcher' else 'ijl15.dll'
                return outputs[name], dict(file=name, outputSha256=pins[name])
            public_pin = hashlib.sha256(key.read_bytes()).hexdigest()
            workspace = root / 'output'
            with patch.object(prepare, 'PINNED_BINARIES', pins), patch.object(prepare, 'SOURCE_BINARIES', sources), \
                    patch.object(prepare, 'derive', side_effect=derive), patch.object(prepare, 'import_legacy', return_value=b'format2'):
                prepare.prepare(client, key, workspace, 'dof-main', public_pin, DATABASE_UUID, '192.0.2.1')
                with self.assertRaises(ValueError):
                    prepare.prepare(client, key, workspace, 'dof-main', public_pin, DATABASE_UUID, '192.0.2.1')
            self.assertEqual({p.name for p in (workspace / 'inputs').iterdir()},
                             {'binding.json', 'dof-launcher.exe', 'ijl15.dll', 'template.dll', 'game-public.der'})
            self.assertEqual((workspace.stat().st_mode & 0o777), 0o700)
            for file in (workspace / 'inputs').iterdir(): self.assertEqual(file.stat().st_mode & 0o777, 0o600)
            archive = root / 'inputs.tar'
            with tarfile.open(archive, 'w') as tar:
                for file in (workspace / 'inputs').iterdir(): tar.add(file, arcname='inputs/' + file.name)
            with patch.object(install, 'PINNED_BINARIES', pins), patch.object(install, 'validate_container'):
                self.assertEqual(set(install.read_bundle(archive, 'dof-main', public_pin, DATABASE_UUID)), install.NAMES)
                with self.assertRaisesRegex(ValueError, 'binding mismatch'):
                    install.read_bundle(archive, 'dof-other', public_pin, DATABASE_UUID)
                with self.assertRaisesRegex(ValueError, 'binding mismatch'):
                    install.read_bundle(archive, 'dof-main', public_pin, '00000000-0000-4000-8000-000000000002')
                with self.assertRaisesRegex(ValueError, 'current paired version/identity'):
                    install.read_bundle(archive, 'dof-main', '0'*64, DATABASE_UUID)
            for name, value in files.items(): self.assertEqual((client / name).read_bytes(), value)

    def test_archive_rejects_paths_links_duplicates_and_oversize(self):
        with tempfile.TemporaryDirectory() as folder:
            archive = Path(folder) / 'bad.tar'
            for name, kind, duplicate, size in [('../runtime.env', tarfile.REGTYPE, False, 1),
                    ('inputs/template.dll', tarfile.SYMTYPE, False, 0),
                    ('inputs/binding.json', tarfile.REGTYPE, True, 1),
                    ('inputs/binding.json', tarfile.REGTYPE, False, 16385)]:
                with tarfile.open(archive, 'w') as tar:
                    item = tarfile.TarInfo(name); item.type = kind; item.size = size
                    if kind == tarfile.SYMTYPE: item.linkname = '/etc/passwd'
                    tar.addfile(item, io.BytesIO(b'x' * size) if size else None)
                    if duplicate: tar.addfile(item, io.BytesIO(b'x' * size))
                with self.assertRaises(ValueError): install.read_bundle(archive, 'dof-main', '1'*64, DATABASE_UUID)

    @unittest.skipUnless(shutil.which('openssl'), 'OpenSSL required for real public-key binding')
    def test_installer_binds_live_database_and_key_without_modifying_existing_data(self):
        with tempfile.TemporaryDirectory() as temp:
            root=Path(temp)/'main';data=root/'runtime/admin/data';data.mkdir(parents=True)
            (root/'runtime/game/data').mkdir(parents=True);(root/'client').mkdir()
            (root/'.env').write_text('LAUNCHER_MODE=native\nDOF_DATABASE_KIND=external\n')
            private=Path(temp)/'key.pem'
            subprocess.run(['openssl','genpkey','-algorithm','RSA','-pkeyopt','rsa_keygen_bits:1024',
                            '-out',str(private)],check=True,capture_output=True)
            public=subprocess.run(['openssl','pkey','-in',str(private),'-pubout'],
                                  check=True,capture_output=True).stdout
            (root/'client/publickey.pem').write_bytes(public)
            (root/'runtime/game/data/publickey.pem').write_bytes(public)
            identity='00000000-0000-4000-8000-000000000001'
            saved=dict(databaseUuid=identity,instanceId='dof-main',native=True,state='ready')
            (root/'database-web.json').write_text(json.dumps(saved))
            (data/'dof-isolated.json').write_text(json.dumps(dict(
                instanceId='dof-main',databaseUuid=identity,environment='isolated')))
            sql=[]
            def query(statement):
                self.assertTrue(statement.startswith('SELECT '))
                sql.append(statement)
                return '5.6.51\t'+identity if 'VERSION()' in statement else 'isolated|'+identity+'|dof-native-1'
            instance,pin,actual,actual_uuid=install.checked_identity(root,query)
            self.assertEqual(actual_uuid,identity)
            self.assertEqual(instance,'dof-main');self.assertEqual(actual,data)
            der=subprocess.run(['openssl','pkey','-pubin','-in',str(root/'client/publickey.pem'),'-outform','DER'],
                               check=True,capture_output=True).stdout
            self.assertEqual(pin,hashlib.sha256(der).hexdigest())
            before={p.relative_to(root):p.read_bytes() for p in root.rglob('*') if p.is_file()}
            bundle={'inputs/binding.json':b'{}','inputs/template.dll':b'config'}
            with patch.object(install,'read_bundle',return_value=bundle):
                with patch.object(install.shutil,'disk_usage',return_value=SimpleNamespace(free=1)):
                    with self.assertRaisesRegex(RuntimeError,'Insufficient space'):
                        install.install(Path(temp)/'private.tar',root,query)
                self.assertFalse((data/'dof-client-publication').exists())
                with patch.object(install.shutil,'disk_usage',return_value=SimpleNamespace(free=10*1024**3)):
                    result=install.install(Path(temp)/'private.tar',root,query)
                self.assertFalse(result['databaseModified']);self.assertFalse(result['webRestarted'])
                self.assertEqual((data/'dof-client-publication/inputs/template.dll').read_bytes(),b'config')
                with patch.object(install.shutil,'disk_usage',return_value=SimpleNamespace(free=10*1024**3)):
                    with self.assertRaisesRegex(ValueError,'will not be overwritten'):
                        install.install(Path(temp)/'private.tar',root,query)
            for name,raw in before.items():self.assertEqual((root/name).read_bytes(),raw)
            self.assertEqual((data/'dof-client-publication/inputs/template.dll').stat().st_mode & 0o777,0o600)
            saved['databaseUuid']='00000000-0000-4000-8000-000000000002'
            (root/'database-web.json').write_text(json.dumps(saved))
            with self.assertRaisesRegex(ValueError,'database identity'):
                install.checked_identity(root,query)


if __name__ == '__main__': unittest.main()
