import hashlib
import importlib.util
import io
import json
from pathlib import Path
import sys
import tarfile
import tempfile
import unittest
from unittest.mock import patch

ROOT = Path(__file__).resolve().parents[3]


def load(name, path):
    spec = importlib.util.spec_from_file_location(name, path)
    module = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(module)
    return module


prepare = load('qk_inputs', ROOT / 'scripts/protocol/dof/prepare_client_inputs.py')
with patch.object(sys, 'path', [str(ROOT / 'scripts/dof')] + sys.path):
    install = load('qk_input_install', ROOT / 'scripts/dof/install_client_inputs.py')


class PublicationInputTests(unittest.TestCase):
    def test_private_input_copy_excludes_unrelated_files_and_refuses_overwrite(self):
        with tempfile.TemporaryDirectory() as folder:
            root = Path(folder); client = root / 'client'; client.mkdir()
            files = {'启动器.exe': b'launcher', 'ijl15.dll': b'library', 'qingkong.dll': b'config'}
            for name, value in files.items(): (client / name).write_bytes(value)
            (client / 'login-password.ini').write_text('PRIVATE')
            key = root / 'public.der'; key.write_bytes(b'\x30\x82test-public')
            pins = {name: hashlib.sha256(files[name]).hexdigest() for name in ('启动器.exe', 'ijl15.dll')}
            public_pin = hashlib.sha256(key.read_bytes()).hexdigest()
            workspace = root / 'output'
            with patch.object(prepare, 'PINNED_BINARIES', pins):
                prepare.prepare(client, key, workspace, install.INSTANCE, public_pin)
                with self.assertRaises(ValueError): prepare.prepare(client, key, workspace, install.INSTANCE, public_pin)
            self.assertEqual({p.name for p in (workspace / 'inputs').iterdir()},
                             {'binding.json', '启动器.exe', 'ijl15.dll', 'template.dll', 'game-public.der'})
            self.assertEqual((workspace.stat().st_mode & 0o777), 0o700)
            for file in (workspace / 'inputs').iterdir(): self.assertEqual(file.stat().st_mode & 0o777, 0o600)
            archive = root / 'inputs.tar'
            with tarfile.open(archive, 'w') as tar:
                for file in (workspace / 'inputs').iterdir(): tar.add(file, arcname='inputs/' + file.name)
            with patch.object(install, 'PUBLIC_PIN', public_pin), patch.object(install, 'BINARY_PINS', pins):
                self.assertEqual(set(install.read_bundle(archive)), install.NAMES)
            for name, value in files.items(): self.assertEqual((client / name).read_bytes(), value)

    def test_archive_rejects_paths_links_duplicates_and_oversize(self):
        with tempfile.TemporaryDirectory() as folder:
            archive = Path(folder) / 'bad.tar'
            install.PUBLIC_PIN='1'*64
            for name, kind, duplicate, size in [('../runtime.env', tarfile.REGTYPE, False, 1),
                    ('inputs/template.dll', tarfile.SYMTYPE, False, 0),
                    ('inputs/binding.json', tarfile.REGTYPE, True, 1),
                    ('inputs/binding.json', tarfile.REGTYPE, False, 16385)]:
                with tarfile.open(archive, 'w') as tar:
                    item = tarfile.TarInfo(name); item.type = kind; item.size = size
                    if kind == tarfile.SYMTYPE: item.linkname = '/etc/passwd'
                    tar.addfile(item, io.BytesIO(b'x' * size) if size else None)
                    if duplicate: tar.addfile(item, io.BytesIO(b'x' * size))
                with self.assertRaises(ValueError): install.read_bundle(archive)


if __name__ == '__main__': unittest.main()
