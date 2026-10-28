import importlib.util
import os
from pathlib import Path
import tarfile
import tempfile
import unittest

spec=importlib.util.spec_from_file_location('admin_backup',Path(__file__).resolve().parents[3]/'scripts/dof/admin_backup.py')
backup=importlib.util.module_from_spec(spec);spec.loader.exec_module(backup)


class AdminBackupTest(unittest.TestCase):
    def fixture(self, root):
        tree=root/'data';tree.mkdir();(tree/'nested').mkdir();(tree/'nested/state.json').write_text('before')
        archive=root/'data.tgz'
        with tarfile.open(archive,'w:gz') as tar:tar.add(tree,arcname='admin-data')
        return tree,archive

    def test_exact_archive_reuse_does_not_extract_or_change_live_data(self):
        with tempfile.TemporaryDirectory() as tmp:
            root=Path(tmp);tree,archive=self.fixture(root)
            self.assertTrue(backup.matches(tree,archive))
            os.link(archive,root/'new-restore-point.tgz')
            self.assertTrue(backup.matches(tree,root/'new-restore-point.tgz'))
            self.assertEqual((tree/'nested/state.json').read_text(),'before')

    def test_same_size_and_mtime_do_not_hide_changed_bytes(self):
        with tempfile.TemporaryDirectory() as tmp:
            tree,archive=self.fixture(Path(tmp));p=tree/'nested/state.json';old=p.stat()
            p.write_text('after!');os.utime(p,ns=(old.st_atime_ns,old.st_mtime_ns))
            self.assertFalse(backup.matches(tree,archive))

    def test_added_removed_and_permission_changed_files_refuse_reuse(self):
        for change in ['add','remove','mode']:
            with self.subTest(change=change),tempfile.TemporaryDirectory() as tmp:
                tree,archive=self.fixture(Path(tmp));p=tree/'nested/state.json'
                if change=='add':(tree/'extra').write_text('new')
                elif change=='remove':p.unlink()
                else:p.chmod(0o600 if p.stat().st_mode&0o777!=0o600 else 0o644)
                self.assertFalse(backup.matches(tree,archive))

    def test_links_and_corrupt_gzip_fall_back_to_fresh_backup(self):
        with tempfile.TemporaryDirectory() as tmp:
            root=Path(tmp);tree,archive=self.fixture(root);raw=archive.read_bytes()
            archive.write_bytes(raw[:-5]);self.assertFalse(backup.matches(tree,archive));archive.write_bytes(raw)
            os.link(tree/'nested/state.json',tree/'alias')
            with tarfile.open(archive,'w:gz') as tar:tar.add(tree,arcname='admin-data')
            self.assertFalse(backup.matches(tree,archive))
            (tree/'alias').unlink();(tree/'alias').symlink_to('nested/state.json')
            with tarfile.open(archive,'w:gz') as tar:tar.add(tree,arcname='admin-data')
            self.assertFalse(backup.matches(tree,archive))


if __name__=='__main__':unittest.main()
