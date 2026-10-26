"""Exercise the asset importer with real miniature archives, never game binaries."""
import hashlib
import io
import json
from pathlib import Path
import subprocess
import sys
import tarfile
import tempfile
import unittest

SCRIPT = Path(__file__).resolve().parents[2] / 'scripts/imports/prepare_assets.py'
GAME = 'df_game_r_files__unnotice86.tar.gz'
DP2 = 'dp2frida__dp2.tar.gz'
FRIDA = 'dp2frida__frida.tar.gz'
SQL = 'mysql_files__dnfsql_5.6.51_20230919.sql'


class ImportAssets(unittest.TestCase):
    def setUp(self):
        # Keep subprocess fixtures in the workspace; Windows sandbox temp ACLs
        # can deny an atomic directory rename across independently granted roots.
        self.temp = tempfile.TemporaryDirectory(dir=SCRIPT.parents[2] if sys.platform == 'win32' else None)
        self.addCleanup(self.temp.cleanup)
        self.root = Path(self.temp.name)
        self.source = self.root / 'source'
        self.source.mkdir()
        self.out = self.root / 'overlay'
        self.members = {
            GAME: [('df_game_r', b'\x7fELF' + b'86binary')],
            DP2: [('dp2/libdp2.so', b'plugin'),
                  ('dp2/lua/df/game/report.lua', b'return {create=function() socket.udp() end}'),
                  ('dp2/df_game_r.lua', b'-- ordinary plugin'),
                  ('dp2/script/delete_creature_item.sh', b"mysql -ugame -p'oldpass'"),
                  ('dp2/script/covert_at_swordman.sh', b"mysql -ugame -p'oldpass'"),
                  ('dp2/script/old.log', b'historical'),
                  ('dp2/patch.zip', b'pvf-zip')],
            FRIDA: [('home/neople/game/' + n, b'{}' if n.endswith('json') else b'frida')
                    for n in ('frida.so', 'frida.js', 'frida.config', 'frida_config.json')],
        }
        self.make_sources()

    def make_sources(self):
        for name, members in self.members.items():
            with tarfile.open(self.source / name, 'w:gz') as archive:
                for name, content in members:
                    member = tarfile.TarInfo(name)
                    if isinstance(content, bytes):
                        member.size = len(content)
                        archive.addfile(member, io.BytesIO(content))
                    else:
                        member.type = content[1]
                        member.linkname = '../outside'
                        archive.addfile(member)
        (self.source / SQL).write_bytes(b'CREATE DATABASE example;\n')
        self.write_manifest()

    def write_manifest(self):
        rows = [dict(id=p.name, status='downloaded', size=p.stat().st_size,
                     sha256=hashlib.sha256(p.read_bytes()).hexdigest())
                for p in sorted(self.source.iterdir()) if p.name != 'manifest.json']
        (self.source / 'manifest.json').write_text(json.dumps(rows), encoding='utf-8')

    def run_import(self, success=True):
        self.assertTrue(SCRIPT.is_file(), 'safe asset importer is not implemented')
        result = subprocess.run([sys.executable, str(SCRIPT), '--source', str(self.source),
                                 '--output', str(self.out)], capture_output=True, text=True)
        if success:
            self.assertEqual(result.returncode, 0, result.stderr)
        else:
            self.assertNotEqual(result.returncode, 0)
        return result

    def test_import_is_deterministic_preserves_originals_and_filters_history(self):
        before = {p.name: p.read_bytes() for p in self.source.iterdir()}
        self.run_import()
        first = {p.relative_to(self.out): p.read_bytes() for p in self.out.rglob('*') if p.is_file()}
        self.run_import()
        second = {p.relative_to(self.out): p.read_bytes() for p in self.out.rglob('*') if p.is_file()}
        self.assertEqual(first, second)
        self.assertEqual(before, {p.name: p.read_bytes() for p in self.source.iterdir()})
        self.assertEqual((self.out / 'game/df_game_r').read_bytes(), b'\x7fELF86binary')
        self.assertEqual((self.out / 'seed/seed.sql').read_bytes(), b'CREATE DATABASE example;\n')
        self.assertEqual((self.out / 'archive/dp2-pvf-patch.zip').read_bytes(), b'pvf-zip')
        self.assertFalse(list(self.out.rglob('*.log')))
        report = (self.out / 'dp2/lua/df/game/report.lua').read_text()
        self.assertNotIn('socket', report)
        self.assertIn('function', report)
        for name in ('delete_creature_item.sh', 'covert_at_swordman.sh'):
            script = (self.out / 'dp2/script' / name).read_text()
            self.assertNotIn('oldpass', script)
            self.assertIn('MYSQL_PWD', script)
            self.assertIn('DNF_DB_GAME_PASSWORD', script)
        manifest = json.loads((self.out / 'manifest.json').read_text())
        for row in manifest['files']:
            self.assertEqual(row['sha256'], hashlib.sha256((self.out / row['path']).read_bytes()).hexdigest())

    def test_bad_hash_rejected_without_partial_output(self):
        with (self.source / GAME).open('ab') as f:
            f.write(b'changed')
        self.assertIn('mismatch', self.run_import(False).stderr)
        self.assertFalse(self.out.exists())

    def test_unsafe_paths_are_rejected_even_in_excluded_members(self):
        for name in ('../outside.log', '/absolute.log', 'C:/outside.log',
                     'dp2/../../outside.log', 'dp2\\outside.log', 'dp2/x:stream',
                     'dp2/CON', 'dp2/evil. /old.log'):
            with self.subTest(name=name):
                self.members[DP2].append((name, b'bad'))
                self.make_sources()
                self.run_import(False)
                self.assertFalse(self.out.exists())
                self.members[DP2].pop()
        self.assertFalse((self.root / 'outside.log').exists())

    def test_links_devices_and_duplicate_members_rejected(self):
        for kind in (tarfile.SYMTYPE, tarfile.LNKTYPE, tarfile.CHRTYPE, tarfile.FIFOTYPE):
            with self.subTest(kind=kind):
                self.members[DP2].append(('dp2/unsafe.log', ('special', kind)))
                self.make_sources()
                self.run_import(False)
                self.assertFalse(self.out.exists())
                self.members[DP2].pop()
        self.members[DP2].append(('dp2/libdp2.so', b'replacement'))
        self.make_sources()
        self.run_import(False)
        self.assertFalse(self.out.exists())

    def test_missing_required_member_and_duplicate_manifest_rejected(self):
        self.members[GAME] = [('different', b'data')]
        self.make_sources()
        self.run_import(False)
        self.assertFalse(self.out.exists())
        manifest = self.source / 'manifest.json'
        rows = json.loads(manifest.read_text())
        manifest.write_text(json.dumps(rows + [rows[0]]))
        self.assertIn('duplicate', self.run_import(False).stderr)

    def test_existing_changed_output_is_never_overwritten(self):
        self.run_import()
        target = self.out / 'game/df_game_r'
        target.write_bytes(b'user changes')
        self.run_import(False)
        self.assertEqual(target.read_bytes(), b'user changes')

    def test_shared_plugin_import_needs_no_level_executable(self):
        (self.source / GAME).unlink()
        self.write_manifest()
        result = subprocess.run([sys.executable, str(SCRIPT), '--source', str(self.source),
                                 '--output', str(self.out), '--plugins-only'], capture_output=True, text=True)
        self.assertEqual(result.returncode, 0, result.stderr)
        self.assertFalse((self.out / 'game').exists())
        self.assertTrue((self.out / 'dp2/libdp2.so').is_file())


if __name__ == '__main__':
    unittest.main()
