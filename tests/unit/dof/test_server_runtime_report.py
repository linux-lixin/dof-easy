"""Prevent successful wrappers or rendered text from becoming native acceptance."""
import importlib.util
import json
import hashlib
from pathlib import Path
import tempfile
import unittest

ROOT = Path(__file__).resolve().parents[3]
spec = importlib.util.spec_from_file_location('server_runtime_report',
    ROOT / 'tests/integration/dof/server_stack/report.py')
report = importlib.util.module_from_spec(spec)
spec.loader.exec_module(report)


class NativeEvidence(unittest.TestCase):
    def setUp(self):
        temporary = tempfile.TemporaryDirectory()
        self.addCleanup(temporary.cleanup)
        self.root = Path(temporary.name)
        for path in ['bin/dof-gateway', 'lib/libdof-plugin.so', 'lib/libdof-inline.so']:
            target = self.root / 'runtime' / path
            target.parent.mkdir(parents=True, exist_ok=True)
            target.write_bytes(b'unit fixture, never executed')
        rows=[dict(path=p.relative_to(self.root/'runtime').as_posix(), sha256=hashlib.sha256(p.read_bytes()).hexdigest()) for p in (self.root/'runtime').rglob('*') if p.is_file()]
        (self.root/'production-prepared.json').write_text(json.dumps(dict(sourceManifest=dict(files=rows), legacyRootEntries=[])))
        (self.root/'production-gateway-ready').write_text('unit fixture, never executed')
        self.modules = ['libdof-plugin.so', 'libdof-inline.so', 'libdp2.so', 'libdp2game.so', 'frida.so']
        self.binding = {'symbolResolved': False}
        self.equipment = dict(passed=True, sentDelta=2, packets=[{}, {}])
        self.random_box = dict(returned=True,queries=['SELECT DISTINCT item_id FROM dof_native.randombox'],observedDistinct=True,sourceRows=3,userConstructed=True,rowCounts=[2])
        self.raid = {'results': [dict(raid=key, returned=True, name='???',
            nameBytes=list(name.encode()) + [0]) for key, name in [('anton', '安徒恩'), ('luke', '卢克')]]}
        self.callback = dict(originalItemCallback='0x85b949c',
            gameItemEntryBytes=[85,137,229,86,83,131,236,32,139,93,16,232,239,44,177,255])
        (self.root / 'game.exit').write_text('0\n')
        (self.root / 'game-stop.json').write_text('{}')
        (self.root / 'bridge-live.json').write_text('{"pids":[10],"port7000Listening":true}')
        (self.root / 'mysql-general.log').write_text('Query SELECT * FROM dof_native.config\nQuery SELECT DISTINCT item_id FROM dof_native.randombox\n')
        (self.root / 'game.trace.1').write_text('\n'.join(
            f'openat(AT_FDCWD, "/data/dof-easy/config/{raid}-raid.ini", O_RDONLY) = 7'
            for raid in ['anton', 'luke']) + '\n'
            'openat(AT_FDCWD, "/data/dof-easy/keys/privatekey.pem", O_RDONLY) = 7\n'
            'openat(AT_FDCWD, "server_str.xml", O_RDONLY) = 8\n')

    def summarize(self):
        # Frida output can appear inside a native status line.
        lines = ['[Dof retired native literals] '+json.dumps(dict(passed=True,count=1,results=[dict(cleared=True)])),
                 '[Dof equipment feature] '+json.dumps(self.equipment),
                 '[Dof random-box feature] '+json.dumps(self.random_box),
                 '[Dof native ABI names] '+json.dumps(dict(count=29,passed=True,socketRtti='7DofSock',singletonRtti='9SingletonI7DofSockE')),
                 '[Dof native tag getters] '+json.dumps(dict(rarity='[dof rarity color]',randomBox='[dof randombox]')),
                 '[Dof strict binding] ' + json.dumps(self.binding),
                 '[Dof private key reader] {"returnedKey":true}',
                 '[Dof private key size] {"bytes":256}',
                 '[!] Init Login State [Dof raid configuration exercise] ' + json.dumps(self.raid),
                 'Success', '[Dof runtime observation] ' + json.dumps(dict(
                     modules=self.modules, equipmentBroadcast=self.callback))]
        (self.root / 'game.stdout').write_text('\n'.join(lines))
        return report.summarize(self.root)

    def test_missing_export_does_not_claim_live_callback_failure_or_full_success(self):
        result = self.summarize()
        self.assertEqual(result['failures'], ['equipmentBroadcastResolved'])
        evidence = result['equipmentBroadcastInspection']
        self.assertTrue(evidence['afterGameWorldInitialization'])
        self.assertTrue(evidence['originalItemEntryUnchanged'])
        self.assertFalse(evidence['activeCallPathVerified'])
        self.assertFalse(result['releaseReady'])

    def test_random_box_requires_real_query_and_distinct_result(self):
        self.assertTrue(self.summarize()['checks']['randomBoxQuery'])
        self.random_box['rowCounts']=[3]
        self.assertFalse(self.summarize()['checks']['randomBoxQuery'])

        self.random_box['rowCounts']=[2]
        (self.root/'mysql-general.log').write_text('no database query')
        self.assertFalse(self.summarize()['checks']['randomBoxQuery'])

    def test_export_resolution_alone_does_not_prove_equipment_dispatch(self):
        self.binding['symbolResolved'] = True
        self.equipment.update(passed=False, packets=[])
        result = self.summarize()
        self.assertTrue(result['checks']['equipmentBroadcastResolved'])
        self.assertFalse(result['checks']['equipmentBroadcastDispatch'])

    def test_payload_change_invalidates_formal_install_evidence(self):
        (self.root/'runtime/bin/dof-gateway').write_bytes(b'changed')
        self.assertFalse(self.summarize()['checks']['formalPayloadInstalled'])

    def test_config_validation_uses_native_bytes_and_successful_file_open(self):
        self.assertTrue(self.summarize()['checks']['raidConfiguration'])
        (self.root / 'game.trace.1').write_text('openat(AT_FDCWD, "/data/dof-easy/config/anton-raid.ini", O_RDONLY) = -1 ENOENT')
        self.assertFalse(self.summarize()['checks']['raidConfiguration'])

    def test_wrong_native_name_is_not_accepted_from_rendered_text(self):
        self.raid['results'][0].update(name='安徒恩', nameBytes=list(b'wrong\0'))
        self.assertFalse(self.summarize()['checks']['raidConfiguration'])

    def test_killed_game_cannot_pass_with_successful_initialization(self):
        (self.root / 'game.exit').write_text('137\n')
        self.assertFalse(self.summarize()['checks']['normalStop'])

    def test_module_presence_and_old_sql_are_checked_independently(self):
        self.modules.remove('libdp2game.so')
        (self.root / 'mysql-general.log').write_text('Query SELECT * FROM qk.qingkong_config\n')
        result = self.summarize()['checks']
        self.assertFalse(result['combinedModulesLoaded'])
        self.assertFalse(result['oldDatabaseQueriesAbsent'])
        self.assertFalse(result['oldConfigTableQueriesAbsent'])

    def test_older_probe_without_entry_bytes_remains_unknown(self):
        self.callback = {}
        result = self.summarize()['equipmentBroadcastInspection']
        self.assertIsNone(result['originalItemEntryUnchanged'])
        self.assertIsNone(result['originalCallbackUnchanged'])

    def test_later_success_does_not_hide_failed_login_state(self):
        self.assertFalse(report.login_state_initialized('Init Login State Fail\nOther Init Success'))
        self.assertFalse(report.login_state_initialized('Init Login State [Dof test] {"ok":true}\nFail\nOther Success'))
        self.assertTrue(report.login_state_initialized('Init Login State [Dof test] {"ok":true}\nSuccess'))


if __name__ == '__main__':
    unittest.main()
