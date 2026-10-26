"""Verify deployable native inputs survive Git publication and reproduce exactly."""
import hashlib
import os
import importlib.util
import shutil
import tarfile
import json
from pathlib import Path
import tempfile
import unittest
from unittest.mock import patch
from scripts.container import manage, resources, menu
from scripts.dof import package_server, native_name_contract
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

    @unittest.skipUnless(os.environ.get('DOF_NATIVE_SAMPLE_DIR'), 'Explicit local originals required only for derivation tests')
    def test_external_originals_reproduce_formal_payloads(self):
        source=Path(os.environ['DOF_NATIVE_SAMPLE_DIR']).resolve()
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
            for name,default in manage.raid_assets.templates():
                self.assertEqual((output/manage.raid_assets.template_path(name)).read_bytes(),default.read_bytes())
        for path,sha in original.items():
            self.assertEqual(hashlib.sha256((source/path).read_bytes()).hexdigest(),sha)

    def test_deployment_does_not_need_raw_inputs_or_audit_records(self):
        with tempfile.TemporaryDirectory() as temp:
            root=Path(temp)
            for name in ('services/game/resources/native', 'services/game/runtime/ubuntu/gateway',
                         'services/game/resources/plugins/raid'):
                shutil.copytree(ROOT/name,root/name)
            (root/'metadata').mkdir()
            shutil.copyfile(ROOT/'metadata/build-inputs.lock.json',root/'metadata/build-inputs.lock.json')
            with patch.object(resources,'ROOT',root):
                self.assertEqual(resources.verify_native_inputs(),15)
            raid=root/'services/game/resources/plugins/raid/config/anton-raid.ini'
            saved=raid.read_bytes()
            raid.write_bytes(b'corrupt')
            with patch.object(resources,'ROOT',root),self.assertRaisesRegex(ValueError,'Hash/size mismatch'):
                resources.verify_native_inputs()
            raid.write_bytes(saved)
            self.assertEqual(menu.assets_directory(root/'services/game/resources/native','test'),root/'services/game/resources/native')
            (root/'services/game/resources/native/lib/libdof-plugin.so').write_bytes(b'corrupt')
            with patch.object(resources,'ROOT',root),self.assertRaisesRegex(ValueError,'Hash/size mismatch'):
                resources.verify_native_inputs()
            with self.assertRaisesRegex(ValueError,'hash mismatch'):
                menu.assets_directory(root/'services/game/resources/native','test')

    def test_raw_inputs_are_rejected_even_if_old_quick_output_exists(self):
        with tempfile.TemporaryDirectory() as temp,patch.object(manage,'STATE',Path(temp)):
            existing=Path(temp)/'quick-runtime/test';existing.mkdir(parents=True)
            raw=Path(temp)/'raw';raw.mkdir();(raw/'QK').write_bytes(b'raw fixture')
            with self.assertRaisesRegex(ValueError,'正式原生资源'):
                menu.assets_directory(raw,'test')
            self.assertEqual(list(existing.iterdir()),[])

    def test_all_plugins_are_locked_without_source_archives(self):
        self.assertFalse((ROOT/'metadata/dof/source-audit').exists())
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
        self.assertEqual({p.split('/')[0] for p in actual},{'dp2','frida','raid'})

    def test_raid_templates_have_one_source_and_preserve_existing_configuration(self):
        assets=manage.raid_assets
        with tempfile.TemporaryDirectory() as temp:
            target=Path(temp)/'state'
            assets.install(manage.default_dof_assets({'LAUNCHER_MODE':'native'}),target)
            for name in assets.RAID_CONFIGS:
                source=ROOT/'services/game/resources/plugins/raid/config'/name
                self.assertFalse((ROOT/'services/game/resources/native/config'/name).exists())
                self.assertFalse((assets.BASE/'defaults'/name).exists())
                runtime=target/'config'/name
                self.assertEqual(runtime.read_text(),source.read_text(encoding='utf-8-sig'))
                runtime.write_text('; operator custom configuration\n')
            assets.install(manage.default_dof_assets({'LAUNCHER_MODE':'native'}),target)
            for name in assets.RAID_CONFIGS:
                self.assertEqual((target/'config'/name).read_text(),'; operator custom configuration\n')

    def test_real_plugin_files_prepare_all_combinations(self):
        spec=importlib.util.spec_from_file_location('plugin_runtime',ROOT/'services/game/runtime/ubuntu/plugins.py')
        module=importlib.util.module_from_spec(spec);spec.loader.exec_module(module)
        source=ROOT/'services/game/resources/plugins'
        for mode in ('none','dp2','frida','dp2-frida'):
            with self.subTest(mode=mode),tempfile.TemporaryDirectory() as temp:
                data=Path(temp)/'data';game=Path(temp)/'game'
                module.prepare(source,data,game,'Ab123456',mode)
                self.assertEqual((data/'dp/libdp2pre.so').exists(),mode in ('dp2','dp2-frida'))
                self.assertEqual((data/'dp/pvf-patch.zip').exists(),mode in ('dp2','dp2-frida'))
                if mode in ('dp2','dp2-frida'):
                    self.assertEqual((data/'dp/pvf-patch.zip').read_bytes(),(source/'dp2/pvf-patch.zip').read_bytes())
                self.assertEqual((game/'frida.so').exists(),mode in ('frida','dp2-frida'))
                if mode in ('frida','dp2-frida'):
                    self.assertEqual((game/'frida.so').read_bytes(),(source/'frida/frida.so').read_bytes())
                    config=json.loads((data/'plugins/frida/frida_config.json').read_text())
                    self.assertEqual(config['db_config']['password'],'Ab123456')
                for component, target in (('dp2',data/'dp'),('frida',data/'plugins/frida')):
                    enabled = mode == component or mode == 'dp2-frida'
                    originals={p.relative_to(source/component).as_posix():p for p in (source/component).rglob('*') if p.is_file()}
                    installed={p.relative_to(target).as_posix():p for p in target.rglob('*') if p.is_file()}
                    self.assertEqual(set(installed),set(originals) if enabled else set())
                    for name,path in installed.items():
                        if component=='frida' and name=='frida_config.json':continue
                        self.assertEqual(path.read_bytes(),originals[name].read_bytes())
                self.assertFalse(any(p.name in ('original-seed.sql','dp2-pvf-patch.zip','QK') for p in Path(temp).rglob('*')))

    def test_formal_package_requires_no_originals_or_archived_evidence(self):
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
                self.assertFalse(any('/source-audit/' in n for n in names))
                self.assertIn('dof-server-runtime/NOTICES.txt',names)
                for name in manage.raid_assets.RAID_CONFIGS:
                    self.assertIn('dof-server-runtime/plugins/raid/config/'+name,names)
                    self.assertNotIn('dof-server-runtime/config/'+name,names)
                tar.extractall(root/'extracted',filter='data')
            # A standalone archive must work without the repository's templates.
            assets=manage.raid_assets
            with patch.object(assets,'RAID_DEFAULTS',root/'missing-templates'):
                assets.install(root/'extracted/dof-server-runtime',root/'installed')
                assets.verify_config(root/'installed')
            for name in assets.RAID_CONFIGS:
                self.assertEqual((root/'installed/config'/name).read_text(),
                                 (assets.RAID_DEFAULTS/name).read_text(encoding='utf-8-sig'))
            self.assertIn('lib/libdof-plugin.so',native_name_contract.module_contract(bundle))
            alien=root/'unrelated';alien.mkdir();(alien/'manifest.json').write_text('{}')
            with self.assertRaisesRegex(ValueError,'does not match'):
                native_name_contract.module_contract(alien)



if __name__=='__main__':unittest.main()
