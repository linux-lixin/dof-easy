"""Synthetic deployment binding checks; no native binaries or services executed."""
import hashlib
import json
import os
from pathlib import Path
import sys
import tempfile
import unittest

ROOT=Path(__file__).resolve().parents[3]
sys.path.insert(0,str(ROOT/'services/game/runtime/ubuntu'))
from gateway import admin_runtime


class RuntimeBinding(unittest.TestCase):
    def setUp(self):
        self.temp=tempfile.TemporaryDirectory();self.addCleanup(self.temp.cleanup)
        self.state=Path(self.temp.name);self.config=self.state/'config';self.config.mkdir()
        self.helper=self.state/'helper';self.helper.mkdir()
        (self.helper/'admin-runtime-digest.c').write_bytes((ROOT/'services/game/docker/ubuntu/admin-runtime-digest.c').read_bytes())
        (self.helper/'libdof-admin-runtime.so').write_bytes(b'\x7fELF\x01\x01'+b'\0'*12+b'\x03\0'+b'unit fixture, never executed')
        self.values=dict(RAID_EXTENSION='on',DOF_INSTANCE_ID='dof-test',DOF_DATABASE_UUID='00000000-0000-0000-0000-000000000001',SERVER_GROUP='3',OPEN_CHANNEL='15,52')
        self.marker=dict(instanceId='dof-test',databaseUuid=self.values['DOF_DATABASE_UUID'],environment='isolated')
        (self.config/'.admin-instance.json').write_text(json.dumps(self.marker))

    def test_fixed_roster_digest_and_original_identity_survive_regeneration(self):
        result=admin_runtime.prepare(self.state,helper_root=self.helper,values=self.values)
        self.assertEqual(result['channels'],['siroco15','siroco52'])
        p=self.config/'.runtime-apply/binding.json';raw=p.read_bytes()
        self.assertEqual(result['bindingRevision'],hashlib.sha256(raw).hexdigest())
        self.assertEqual(result['bindingRevision'],admin_runtime.prepare(self.state,helper_root=self.helper,values=self.values)['bindingRevision'])
        self.assertEqual(json.loads((self.config/'.admin-instance.json').read_text()),self.marker)
        self.assertEqual(p.stat().st_mode&0o777,0o600)

    def test_missing_environment_does_not_invent_a_binding_or_enable_raids(self):
        for values in [{},dict(self.values,RAID_EXTENSION='off'),dict(self.values,DOF_DATABASE_UUID='')]:
            self.assertIsNone(admin_runtime.prepare(self.state,helper_root=self.helper,values=values))
        self.assertFalse((self.config/'.runtime-apply').exists())

    def test_wrong_marker_or_database_stops_mailbox_creation(self):
        (self.config/'.admin-instance.json').write_text(json.dumps(dict(self.marker,databaseUuid='other')))
        with self.assertRaisesRegex(ValueError,'differs'):admin_runtime.prepare(self.state,helper_root=self.helper,values=self.values)
        self.assertFalse((self.config/'.runtime-apply').exists())

    def test_existing_foreign_binding_and_symlinks_are_not_overwritten(self):
        admin_runtime.prepare(self.state,helper_root=self.helper,values=self.values);p=self.config/'.runtime-apply/binding.json'
        raw=json.loads(p.read_text());raw['instanceId']='dof-other';p.write_text(json.dumps(raw))
        with self.assertRaisesRegex(ValueError,'another instance'):admin_runtime.prepare(self.state,helper_root=self.helper,values=self.values)
        self.assertEqual(json.loads(p.read_text())['instanceId'],'dof-other')
        p.unlink();target=self.state/'outside';target.write_text('keep');p.symlink_to(target)
        with self.assertRaisesRegex(ValueError,'link'):admin_runtime.prepare(self.state,helper_root=self.helper,values=self.values)
        self.assertEqual(target.read_text(),'keep')

    def test_offline_alias_requires_loopback_and_is_never_a_browser_parameter(self):
        values=dict(self.values,DOF_OFFLINE_RUNTIME_CHANNEL='local')
        with self.assertRaisesRegex(ValueError,'loopback'):admin_runtime.prepare(self.state,helper_root=self.helper,values=values)
        result=admin_runtime.prepare(self.state,helper_root=self.helper,values=dict(values,CORE_PUBLIC_IP='127.0.0.1',CUR_SG_DB_HOST='127.0.0.1'))
        self.assertEqual(result['channels'],['local'])

    def test_runtime_roster_matches_the_actual_launcher_parser(self):
        from config import channels
        for value in ['15,52','11-39,52-56','1,6,7','15,15','1-56','0','57','52-15','']:
            try:
                expected=channels(value)
            except ValueError:
                with self.assertRaises(ValueError):admin_runtime.selected_channels(value)
            else:
                self.assertEqual(admin_runtime.selected_channels(value),expected)

    def test_admin_and_game_pin_the_same_executor_and_abi(self):
        contract=json.loads((ROOT/'services/admin/backend/src/main/resources/dof/runtime-apply.json').read_text())
        self.assertEqual(contract['executorScriptSHA256'],hashlib.sha256((admin_runtime.BASE/'admin_apply.js').read_bytes()).hexdigest())
        self.assertEqual(contract['gameAbiSHA256'],hashlib.sha256((admin_runtime.BASE/'game-abi.json').read_bytes()).hexdigest())
        self.assertEqual(contract['digestHelperSourceSHA256'],hashlib.sha256((ROOT/'services/game/docker/ubuntu/admin-runtime-digest.c').read_bytes()).hexdigest())
        self.assertEqual(contract,json.loads((admin_runtime.BASE/'runtime-apply.json').read_text()))


if __name__=='__main__':unittest.main()
