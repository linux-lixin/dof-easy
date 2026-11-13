"""Behavioral regressions for documentation safety and consistency."""
import importlib.util
import json
from pathlib import Path
import subprocess
import tempfile
import unittest

ROOT = Path(__file__).resolve().parents[2]
spec = importlib.util.spec_from_file_location('check_docs', ROOT/'scripts/diagnostics/check_docs.py')
docs = importlib.util.module_from_spec(spec)
spec.loader.exec_module(docs)


class DocumentationChecker(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory()
        self.addCleanup(self.temp.cleanup)
        self.root = Path(self.temp.name)

    def put(self, path, text):
        p = self.root/path
        p.parent.mkdir(parents=True, exist_ok=True)
        p.write_text(text, encoding='utf8')

    def links(self, text, files):
        self.put('README.md', text)
        return docs.check_links(self.root, set(files)|{'README.md'})[0]

    def test_broken_link_and_missing_image(self):
        errors = self.links('[no](missing.md) ![image](missing.png)', [])
        self.assertTrue(any('missing.md' in e for e in errors))
        self.assertTrue(any('missing.png' in e for e in errors))

    def test_reference_links_images_unicode_and_duplicate_anchors(self):
        self.put('guide.md', '# 中文标题\n# Again\n# Again\n')
        self.put('image.png', 'fixture')
        errors = self.links('[go][page] ![photo][pic]\n\n[page]: guide.md#中文标题\n[pic]: image.png\n[dup](guide.md#again-1)', ['guide.md','image.png'])
        self.assertEqual(errors, [])
        errors = self.links('[bad](guide.md#unknown)', ['guide.md'])
        self.assertTrue(any('anchor' in e for e in errors))

    def test_setext_heading_and_html_image_are_checked(self):
        self.put('guide.md', 'Setext title\n============\n')
        self.assertEqual(self.links('[go](guide.md#setext-title)', ['guide.md']), [])
        self.assertTrue(self.links('<img src="missing.png">', []))

    def test_untracked_file_cannot_satisfy_link(self):
        self.put('private.md', 'untracked')
        self.assertTrue(self.links('[private](private.md)', []))

    def test_orphaned_current_page(self):
        pages=[{'path':'README.md','kind':'current'}, {'path':'docs/README.md','kind':'current'}, {'path':'docs/lost.md','kind':'current'}]
        errors=docs.check_navigation(pages, {'README.md': {'docs/README.md'}})
        self.assertTrue(any('docs/lost.md' in e for e in errors))

    def test_incorrect_cli_default_is_detected_from_source_ast(self):
        self.put('scripts/container/manage.py', "parser.add_argument('--channels', default='15,52')\n")
        self.put('docs/reference/cli.md', '| 频道 | 15,52 | 11,52 |\n')
        errors=docs.check_default_table(self.root, [('频道','--channels')])
        self.assertTrue(any('default' in e for e in errors))




    def test_private_material_and_real_password_patterns(self):
        self.put('README.md','password = "non-example-strong-secret"\n-----BEGIN PRIVATE KEY-----')
        self.assertTrue(docs.check_public_material(self.root, {'README.md'}))

    def test_tracked_inventory_ignores_other_unsubmitted_files(self):
        subprocess.run(['git','init','-q',str(self.root)],check=True)
        self.put('README.md','# Good')
        self.put('other-audit.md','password = "not-for-this-task"')
        subprocess.run(['git','-C',str(self.root),'add','README.md'],check=True)
        self.assertEqual(docs.tracked_files(self.root),{'README.md'})
        self.assertEqual(docs.tracked_files(self.root,['other-audit.md']),{'README.md','other-audit.md'})






class CurrentCatalog(unittest.TestCase):
    def test_unregistered_and_missing_pages_fail(self):
        pages = [{'path':'README.md','kind':'current'}]
        errors = docs.check_catalog(pages, {'README.md', 'docs/new.md'})
        self.assertTrue(any('unregistered' in e for e in errors))
        self.assertTrue(docs.check_catalog(pages, set()))

    def test_complete_catalog_passes_and_duplicates_fail(self):
        pages = [{'path':'README.md','kind':'current'}]
        self.assertEqual(docs.check_catalog(pages, {'README.md'}), [])
        self.assertTrue(docs.check_catalog(pages + pages, {'README.md'}))


if __name__=='__main__': unittest.main()
