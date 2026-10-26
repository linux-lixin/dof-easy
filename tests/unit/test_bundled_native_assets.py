"""Verify deployable native inputs survive Git publication and reproduce exactly."""
import hashlib
import importlib.util
import shutil
import tarfile
import json
from pathlib import Path
import tempfile
import unittest
from unittest.mock import patch
from scripts.container import manage, resources, menu
from scripts.dof import package_server, runtime_evidence
from scripts.dof.build_server_runtime import build

ROOT=Path(__file__).resolve().parents[2]


class BundledNativeAssets(unittest.TestCase):
    def test_default_bundle_installs_without_network_or_operator_inputs(self):
        with patch('urllib.request.urlopen',side_effect=AssertionError('Unexpected download')), tempfile.TemporaryDirectory() as temp:
            self.assertEqual(resources.verify_native_inputs(),15)
            bundle=manage.default_dof_assets({'LAUNCHER_MODE':'native'})
            self.assertEqual(bundle,ROOT/'services/game/resources/native')
            target=Path(temp)/'state'
            manage.raid_assets.install(bundle,target)
            manage.raid_assets.verify(target/'assets')
            manage.raid_assets.verify_config(target)
            self.assertEqual(list((target/'keys').iterdir()),[])
            self.assertFalse((target/'assets/source-audit').exists())

    def test_committed_originals_reproduce_formal_payloads(self):
        source=ROOT/'metadata/dof/source-audit/server-inputs/files'
        original={p.relative_to(source).as_posix():hashlib.sha256(p.read_bytes()).hexdigest()
                  for p in source.rglob('*') if p.is_file()}
        self.assertEqual(len(original),12)
        with tempfile.TemporaryDirectory() as temp:
            output=Path(temp)/'bundle'
            build(source,output)
            manage.raid_assets.verify(output)
            committed=ROOT/'services/game/resources/native'
            for row in manage.raid_assets.manifest():
                self.assertEqual((output/row['path']).read_bytes(),(committed/row['path']).read_bytes())
            for name,_ in manage.raid_assets.templates():
                self.assertEqual((output/'config'/name).read_bytes(),(committed/'config'/name).read_bytes())
        for path,sha in original.items():
            self.assertEqual(hashlib.sha256((source/path).read_bytes()).hexdigest(),sha)

    def test_deployment_does_not_need_raw_inputs_or_audit_records(self):
        with tempfile.TemporaryDirectory() as temp:
            root=Path(temp)
            for name in ('services/game/resources/native', 'services/game/runtime/ubuntu/gateway'):
                shutil.copytree(ROOT/name,root/name)
            with patch.object(resources,'ROOT',root):
                self.assertEqual(resources.verify_native_inputs(),15)
            self.assertEqual(menu.assets_directory(root/'services/game/resources/native','test'),root/'services/game/resources/native')
            (root/'services/game/resources/native/lib/libdof-plugin.so').write_bytes(b'corrupt')
            with patch.object(resources,'ROOT',root),self.assertRaisesRegex(ValueError,'Hash/size mismatch'):
                resources.verify_native_inputs()
            with self.assertRaisesRegex(ValueError,'hash mismatch'):
                menu.assets_directory(root/'services/game/resources/native','test')

    def test_raw_inputs_are_rejected_even_if_old_quick_output_exists(self):
        with tempfile.TemporaryDirectory() as temp,patch.object(manage,'STATE',Path(temp)):
            existing=Path(temp)/'quick-runtime/test';existing.mkdir(parents=True)
            with self.assertRaisesRegex(ValueError,'正式原生资源'):
                menu.assets_directory(ROOT/'metadata/dof/source-audit/server-inputs/files','test')
            self.assertEqual(list(existing.iterdir()),[])

    def test_audit_hashes_and_deployment_allowlists_are_separate(self):
        self.assertEqual(resources.verify_source_audit(),17)
        rows=json.loads((ROOT/'metadata/build-inputs.lock.json').read_text())['files']
        for row in rows:
            self.assertNotIn('/source-audit/',row['path'])
            self.assertNotIn('/archive/',row['path'])
            self.assertNotIn('/seed/',row['path'])
        actual={p.relative_to(ROOT/'services/game/resources/plugins').as_posix()
                for p in (ROOT/'services/game/resources/plugins').rglob('*') if p.is_file()}
        locked={row['path'].removeprefix('services/game/resources/plugins/') for row in rows
                if row['path'].startswith('services/game/resources/plugins/')}
        self.assertEqual(actual,locked)
        self.assertEqual({p.split('/')[0] for p in actual},{'dp2','frida'})

    def test_real_plugin_files_prepare_all_combinations(self):
        spec=importlib.util.spec_from_file_location('plugin_runtime',ROOT/'services/game/runtime/ubuntu/vipai86.py')
        module=importlib.util.module_from_spec(spec);spec.loader.exec_module(module)
        source=ROOT/'services/game/resources/plugins'
        for mode in ('none','dp2','frida','dp2-frida'):
            with self.subTest(mode=mode),tempfile.TemporaryDirectory() as temp:
                data=Path(temp)/'data';game=Path(temp)/'game'
                module.prepare(source,data,game,'Ab123456',mode)
                self.assertEqual((data/'dp/libdp2pre.so').exists(),mode in ('dp2','dp2-frida'))
                self.assertEqual((game/'frida.so').exists(),mode in ('frida','dp2-frida'))
                if mode in ('frida','dp2-frida'):
                    self.assertEqual((game/'frida.so').read_bytes(),(source/'frida/frida.so').read_bytes())
                    config=json.loads((data/'plugins/frida/frida_config.json').read_text())
                    self.assertEqual(config['db_config']['password'],'Ab123456')
                self.assertFalse(any(p.name in ('original-seed.sql','dp2-pvf-patch.zip','QK') for p in Path(temp).rglob('*')))

    def test_formal_package_uses_archived_evidence_without_original_programs(self):
        bundle=ROOT/'services/game/resources/native'
        self.assertFalse((bundle/'source-audit').exists())
        with tempfile.TemporaryDirectory() as temp:
            root=Path(temp);report=root/'validation.json'
            report.write_text(json.dumps(dict(runtimeManifest=json.loads(manage.raid_assets.LOCK.read_text()),releaseReady=False)))
            result=package_server.package(bundle,report,root/'runtime.tar.gz')
            self.assertFalse(result['releaseReady'])
            with tarfile.open(root/'runtime.tar.gz') as tar:
                names=tar.getnames()
                self.assertIn('dof-server-runtime/bin/dof-gateway',names)
                self.assertFalse(any('/qingkong/' in n or n.endswith(('/QK','/qk_plugin.so','/libantisvrinline.so')) for n in names))
                evidence=tar.extractfile('dof-server-runtime/source-audit/derivation.json').read()
                self.assertEqual(evidence,runtime_evidence.derivation_path(bundle).read_bytes())
            alien=root/'unrelated';alien.mkdir()
            with self.assertRaisesRegex(ValueError,'own provenance'):
                runtime_evidence.derivation_path(alien)



if __name__=='__main__':unittest.main()
