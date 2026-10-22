"""Linux/i386 integration proof; run in the no-network compiler container."""
import importlib.util
from pathlib import Path
import subprocess
import sys
import tempfile
import unittest
import shutil

ROOT = Path(__file__).resolve().parents[2]
SOURCE = ROOT / 'services/game/docker/ubuntu'


@unittest.skipUnless(sys.platform == 'linux' and shutil.which('gcc'),
                     'requires Linux ELF loader and gcc-multilib')
class FridaCompatibility(unittest.TestCase):
    def test_scoped_audit_resolves_gadget_but_keeps_other_missing_symbols_fatal(self):
        spec = importlib.util.spec_from_file_location('elf_audit', SOURCE / 'audit/elf-audit.py')
        audit = importlib.util.module_from_spec(spec)
        spec.loader.exec_module(audit)
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            assets = root / 'assets'
            assets.mkdir()
            shim = root / 'libfrida-compat.so'
            subprocess.run(['gcc', '-m32', '-shared', '-fPIC',
                            '-Wl,--version-script=' + str(SOURCE / 'frida-compat.map'),
                            '-o', str(shim), str(SOURCE / 'frida-compat.c'), '-ldl'], check=True)
            shutil.copyfile(ROOT / 'services/game/plugin-assets/frida/frida.so', assets / 'gadget.so')
            fixture = root / 'fixture.c'
            fixture.write_text('extern int missing_probe_symbol(void); int test(void) { return missing_probe_symbol(); }')
            subprocess.run(['gcc', '-m32', '-shared', '-fPIC', '-o', str(assets / 'broken.so'), str(fixture)], check=True)
            fixture.write_text('int test(void) { return 42; }')
            subprocess.run(['gcc', '-shared', '-fPIC', '-o', str(assets / 'native64.so'), str(fixture)], check=True)
            baseline = {Path(r['path']).name: r for r in audit.audit(assets, str(root))}
            self.assertTrue(any('__libc_dlopen_mode' in e for e in baseline['gadget.so']['errors']))
            records = {Path(r['path']).name: r for r in audit.audit(assets, str(root), preload_i386=shim)}
            self.assertEqual(records['gadget.so']['errors'], [])
            self.assertIn(str(shim), records['gadget.so']['resolution'])
            self.assertEqual(records['native64.so']['errors'], [])
            self.assertNotIn(str(shim), records['native64.so']['resolution'])
            self.assertTrue(any('missing_probe_symbol' in e for e in records['broken.so']['errors']))

    def test_archived_gadget_executes_minimal_script_with_versioned_shim(self):
        spec = importlib.util.spec_from_file_location('frida_probe', SOURCE / 'audit/frida-probe.py')
        module = importlib.util.module_from_spec(spec)
        spec.loader.exec_module(module)
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            shim, probe = root / 'libfrida-compat.so', root / 'frida-probe'
            self.assertTrue((SOURCE / 'frida-compat.c').is_file(), 'compatibility shim is missing')
            subprocess.run(['gcc', '-m32', '-shared', '-fPIC', '-Wall', '-Wextra', '-Werror',
                            '-Wl,--version-script=' + str(SOURCE / 'frida-compat.map'),
                            '-o', str(shim), str(SOURCE / 'frida-compat.c'), '-ldl'], check=True)
            subprocess.run(['gcc', '-m32', '-Wall', '-Wextra', '-Werror', '-rdynamic',
                            '-o', str(probe), str(SOURCE / 'audit/frida-probe.c'), '-ldl'], check=True)
            result = module.exercise(ROOT / 'services/game/plugin-assets/frida/frida.so', shim, probe)
            self.assertEqual(result['pointerSize'], 4)
            self.assertTrue(result['nativeCall'])


if __name__ == '__main__':
    unittest.main()
