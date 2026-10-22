"""Configure the bundled TSS relay for a local standalone installation.

The repository ships no remote TSS configuration or telemetry server. Its
original cfgsvr mode exits, and its sample service map aliases the remote
monitor to the relay itself. Use only the relay's explicit local mapping.
This does not change game security checks, secagent channels or gunnersvr.
"""
from pathlib import Path
import sys
import xml.etree.ElementTree as ET


def read_xml(path):
    # Original metadata declares GBK; decoding first also handles legacy comments.
    return ET.fromstring(path.read_bytes().decode('gbk'))


def adapt(root):
    root = Path(root)
    config_path = root / 'zergsvr/cfg/zergsvrd.xml'
    mapping_path = root / 'zergsvr/cfg/svcid.xml'
    config = read_xml(config_path)
    original = read_xml(mapping_path)
    identity = tuple((config.findtext('self_cfg/self_svr_info/' + tag) or '').strip()
                     for tag in ('svr_type', 'svr_id'))
    if not all(value.isdecimal() and int(value) > 0 for value in identity):
        raise ValueError('TSS self identity must contain positive type and id')
    candidates = []
    for record in original.findall('service_info_'):
        values = tuple((record.findtext(tag) or '').strip() for tag in
                       ('svr_type_', 'svr_id_', 'svr_ip_', 'svr_port_', 'svr_idc_'))
        if values[:2] == identity:
            candidates.append(values)
    if not candidates or len(set(candidates)) != 1:
        raise ValueError('TSS self mapping missing or conflicting; review supplied configuration')
    values = candidates[0]
    if values[2] != '127.0.0.1' or not values[3].isdecimal() or not 0 < int(values[3]) < 65536:
        raise ValueError('TSS standalone mapping must use loopback and a valid port')
    mode = config.find('comm_cfg/get_svr_info_type')
    automatic = config.find('auto_connect_cfg')
    if mode is None or automatic is None:
        raise ValueError('TSS relay configuration structure changed')
    mode.text = 'cfgfile'
    automatic.clear()
    ET.SubElement(automatic, 'auto_connect_num').text = '0'
    mapping = ET.Element('svcid_config')
    ET.SubElement(mapping, 'service_num_').text = '1'
    record = ET.SubElement(mapping, 'service_info_')
    for tag, value in zip(('svr_type_', 'svr_id_', 'svr_ip_', 'svr_port_', 'svr_idc_'), values):
        ET.SubElement(record, tag).text = value
    ET.SubElement(record, 'svr_info_').text = ''
    for path, document in ((config_path, config), (mapping_path, mapping)):
        ET.indent(document, space='  ')
        path.write_bytes(ET.tostring(document, encoding='utf-8', xml_declaration=True) + b'\n')


if __name__ == '__main__':
    adapt(Path(sys.argv[1]) if len(sys.argv) > 1 else Path('/home/template/neople/secsvr'))
