import importlib.util
from pathlib import Path
import shutil
import tempfile
import unittest
import xml.etree.ElementTree as ET

ROOT = Path(__file__).resolve().parents[2]
SPEC = importlib.util.spec_from_file_location('tss_config', ROOT / 'services/game/runtime/ubuntu/tss_config.py')
tss = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(tss)


class LocalTssConfiguration(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory()
        self.addCleanup(self.temp.cleanup)
        self.root = Path(self.temp.name)
        source = ROOT / 'services/game/assets/home/template/neople/secsvr'
        for service in ('zergsvr', 'gunnersvr'):
            shutil.copytree(source / service / 'cfg', self.root / service / 'cfg')

    def test_local_mapping_has_unique_self_and_no_phantom_peers(self):
        tss.adapt(self.root)
        config = ET.parse(self.root / 'zergsvr/cfg/zergsvrd.xml').getroot()
        self.assertEqual('cfgfile', config.findtext('comm_cfg/get_svr_info_type'))
        self.assertEqual('0', config.findtext('auto_connect_cfg/auto_connect_num'))
        self.assertEqual([], config.findall('auto_connect_cfg/auto_connect_svrs'))
        services = ET.parse(self.root / 'zergsvr/cfg/svcid.xml').getroot()
        self.assertEqual('1', services.findtext('service_num_'))
        records = services.findall('service_info_')
        self.assertEqual(1, len(records))
        self.assertEqual(('30', '570011', '127.0.0.1', '9000'),
                         tuple(records[0].findtext(tag) for tag in ('svr_type_', 'svr_id_', 'svr_ip_', 'svr_port_')))

    def test_security_configuration_unchanged_and_adaptation_idempotent(self):
        protected = list((self.root / 'gunnersvr/cfg').glob('*')) + [self.root / 'zergsvr/cfg/secagent_config.xml']
        before = {path: path.read_bytes() for path in protected if path.is_file()}
        tss.adapt(self.root)
        self.assertEqual(before, {path: path.read_bytes() for path in before})
        output = {path: path.read_bytes() for path in (self.root / 'zergsvr/cfg').glob('*.xml')}
        tss.adapt(self.root)
        self.assertEqual(output, {path: path.read_bytes() for path in output})

    def test_missing_self_identity_fails_before_writing(self):
        path = self.root / 'zergsvr/cfg/zergsvrd.xml'
        path.write_bytes(path.read_bytes().replace(b'<svr_id>570011</svr_id>', b'<svr_id>0</svr_id>'))
        before = (self.root / 'zergsvr/cfg/svcid.xml').read_bytes()
        with self.assertRaisesRegex(ValueError, 'self identity'):
            tss.adapt(self.root)
        self.assertEqual(before, (self.root / 'zergsvr/cfg/svcid.xml').read_bytes())

    def test_conflicting_duplicate_mapping_is_not_silently_selected(self):
        path = self.root / 'zergsvr/cfg/svcid.xml'
        raw = path.read_bytes()
        start = raw.index(b'<svr_type_> 30 </svr_type_>')
        raw = raw[:start] + raw[start:].replace(b'<svr_port_> 9000 </svr_port_>',
                                               b'<svr_port_> 9002 </svr_port_>', 1)
        path.write_bytes(raw)
        with self.assertRaisesRegex(ValueError, 'conflicting'):
            tss.adapt(self.root)
        self.assertEqual(raw, path.read_bytes())


if __name__ == '__main__':
    unittest.main()
