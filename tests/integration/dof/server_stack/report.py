"""Summarize real local server observations; never turn partial loading into release acceptance."""
import hashlib
import json
from pathlib import Path
import re
import sys


def events(text, marker):
    result = []
    for line in text.splitlines():
        if marker in line:
            event, _ = json.JSONDecoder().raw_decode(line.split(marker, 1)[1].lstrip())
            result.append(event)
    return result


def login_state_initialized(text):
    # A JSON probe may interrupt the native status line. Remove only complete
    # probe events, then require Success to be the status for this exact step.
    # A later unrelated Success must never turn an earlier Fail into readiness.
    pattern = re.compile(r'\[Dof [^\]\n]+\] \{')
    cleaned, position = [], 0
    for match in pattern.finditer(text):
        if match.start() < position:
            continue
        start = match.end() - 1
        try:
            _, consumed = json.JSONDecoder().raw_decode(text[start:])
        except ValueError:
            continue
        cleaned.append(text[position:match.start()])
        position = start + consumed
    cleaned.append(text[position:])
    return bool(re.search(r'\bInit Login State\s+Success\b', ''.join(cleaned)))


def raid_configuration_evidence(raids, trace):
    configs = {}
    reloads = {}
    expected_names = {'anton': '安徒恩', 'luke': '卢克'}
    for raid, name in expected_names.items():
        expected = list(name.encode('utf-8')) + [0]
        value = next((row for entry in raids for row in entry['results'] if row['raid'] == raid), None)
        opened = re.search(r'openat\([^\n]*"/data/dof-easy/config/' + raid
                           + r'-raid\.ini"[^\n]*= [0-9]+', trace) is not None
        configs[raid] = bool(opened and value and value['returned']
                             and value.get('nameBytes', [])[:len(expected)] == expected)
        reload = next((row for entry in raids for row in entry.get('reloads', [])
                       if row.get('raid') == raid), {})
        initial = dict(name=name, open_day='1,2,3,4,5,6,7',
                       open_time='00:00:00-23:59:59', member_min=1 if raid == 'anton' else 2)
        changed = dict(name=name + '刷新验证', open_day='2,4,6',
                       open_time='01:02:03-04:05:06', member_min=3)

        def matches(snapshot, wanted):
            for field, expected_value in wanted.items():
                if isinstance(expected_value, str):
                    # The native logger may replace non-ASCII display text.
                    # Read bytes from the actual exported buffer, as for names
                    # above; rendered console text is not the native oracle.
                    encoded = list(expected_value.encode('utf-8')) + [0]
                    if snapshot.get(field + 'Bytes', [])[:len(encoded)] != encoded:
                        return False
                elif snapshot.get(field) != expected_value:
                    return False
            return True

        reloads[raid] = bool(configs[raid] and reload.get('fileRestored') is True
                            and isinstance(reload.get('threadId'), int) and reload['threadId'] > 0
                            and matches(reload.get('before', {}), initial)
                            and matches(reload.get('changed', {}), changed)
                            and matches(reload.get('restored', {}), initial))
    return configs, reloads


def summarize(root):
    root = Path(root)
    log = (root / 'game.stdout').read_text(errors='replace')
    queries = (root / 'mysql-general.log').read_text(errors='replace')
    binding = events(log, '[Dof strict binding] ')
    raids = events(log, '[Dof raid configuration exercise] ')
    observations = events(log, '[Dof runtime observation] ')
    keys = events(log, '[Dof private key reader] ')
    key_sizes = events(log, '[Dof private key size] ')
    abi = events(log, '[Dof native ABI names] ')
    tags = events(log, '[Dof native tag getters] ')
    random_box = events(log, '[Dof random-box feature] ')
    equipment = events(log, '[Dof equipment feature] ')
    retired = events(log, '[Dof retired native literals] ')
    bridge_file = root / 'bridge-live.json'
    bridge = json.loads(bridge_file.read_text()) if bridge_file.exists() else {}
    bridge_trace = '\n'.join(p.read_text(errors='replace') for p in root.glob('bridge.trace.*'))
    trace = '\n'.join(p.read_text(errors='replace') for p in root.glob('game.trace.*'))
    configs, reloads = raid_configuration_evidence(raids, trace)
    game_exit = int((root / 'game.exit').read_text())
    broadcast = observations[-1].get('equipmentBroadcast') if observations else None
    # Missing ELF exports and a live installed hook are different claims.
    # Preserve the observed entry bytes without claiming unreachable code.
    expected_entry = [85, 137, 229, 86, 83, 131, 236, 32, 139, 93, 16, 232, 239, 44, 177, 255]
    broadcast_inspection = {
        'snapshot': broadcast,
        'afterGameWorldInitialization': bool(binding and observations
            and log.rfind('[Dof runtime observation] ') > log.rfind('[Dof strict binding] ')),
        'originalItemEntryUnchanged': (broadcast['gameItemEntryBytes'] == expected_entry
            if broadcast and 'gameItemEntryBytes' in broadcast else None),
        'originalCallbackUnchanged': (broadcast['originalItemCallback'] == '0x85b949c'
            if broadcast and 'originalItemCallback' in broadcast else None),
        'activeCallPathVerified': False,
        'limitation': 'An unchanged entry does not prove the callback is unreachable through every path',
    }
    prepared_file = root / 'production-prepared.json'
    prepared = json.loads(prepared_file.read_text()) if prepared_file.is_file() else {}
    payload_ok = bool(prepared)
    for row in prepared.get('sourceManifest', {}).get('files', []):
        p = root / 'runtime' / row['path']
        payload_ok = payload_ok and p.is_file() and hashlib.sha256(p.read_bytes()).hexdigest() == row['sha256']
    checks = {
        'retiredNativeLiterals': bool(retired and retired[-1].get('passed') and retired[-1].get('count',0)>0
            and all(x.get('cleared') for x in retired[-1].get('results',[]))),
        'equipmentBroadcastDispatch': bool(equipment and equipment[-1].get('passed')
            and equipment[-1].get('sentDelta') == 2 and len(equipment[-1].get('packets', [])) == 2),
        'randomBoxQuery': bool(random_box and random_box[-1].get('returned')
            and random_box[-1].get('queries') == ['SELECT DISTINCT item_id FROM dof_native.randombox']
            and random_box[-1].get('observedDistinct') and random_box[-1].get('sourceRows') == 3
            and random_box[-1].get('userConstructed') and 2 in random_box[-1].get('rowCounts', [])
            and 'SELECT DISTINCT item_id FROM dof_native.randombox' in queries),
        'nativeAbiNames': bool(abi and abi[-1]['passed'] and abi[-1]['count'] == 29
                               and abi[-1]['socketRtti'] == '7DofSock' and abi[-1]['singletonRtti'] == '9SingletonI7DofSockE'),
        'nativeTagGetters': bool(tags and tags[-1] == {'rarity':'[dof rarity color]', 'randomBox':'[dof randombox]'}),
        'formalPayloadInstalled': payload_ok,
        'formalGatewayReady': (root / 'production-gateway-ready').is_file(),
        'noLegacyRootAliases': bool(prepared) and prepared['legacyRootEntries'] == [],
        'oldRobotTableQueriesAbsent': not re.search(r'\b(?:qk_ai(?:_\w+)?|v4_qk_user_temp)\b', queries),
        'loginStateInitialized': login_state_initialized(log),
        'combinedModulesLoaded': bool(observations and {
            'libdof-plugin.so', 'libdof-inline.so', 'libdp2.so', 'libdp2game.so', 'frida.so'
        }.issubset(observations[-1]['modules'])),
        'equipmentBroadcastResolved': bool(binding and binding[-1]['symbolResolved']),
        'raidConfiguration': all(configs.values()),
        'raidConfigurationReload': all(reloads.values()),
        'privateKeyConsumed': bool(keys and keys[-1]['returnedKey']
            and key_sizes and key_sizes[-1]['bytes'] == 256
            and re.search(r'openat\([^\n]*"/data/dof-easy/keys/privatekey.pem"[^\n]*= [0-9]+', trace)),
        'serverTextResourceLoaded': bool(re.search(r'openat\([^\n]*"server_str.xml"[^\n]*= [0-9]+', trace)
            and 'Load Fail File : server_str.xml' not in log),
        'bridgeHealthy': bool(bridge.get('pids') and bridge.get('port7000Listening')
                              and 'killed by SIG' not in bridge_trace),
        'oldDatabaseQueriesAbsent': not re.search(r'\bqk\s*\.', queries, re.I),
        'oldConfigTableQueriesAbsent': not re.search(r'\bqingkong_config(?:_v2)?\b', queries, re.I),
        'normalStop': game_exit == 0 and (root / 'game-stop.json').is_file(),
    }
    report = dict(schema=1, serverAccessed=False, releaseReady=False,
                  gameExit=game_exit, checks=checks, raidConfigurations=configs,
                  raidConfigurationReloads=reloads,
                  randomBox=random_box[-1] if random_box else None,
                  equipmentBroadcast=equipment[-1] if equipment else None,
                  retiredNativeLiterals=retired[-1] if retired else None,
                  equipmentBroadcastInspection=broadcast_inspection,
                  bridge=bridge,
                  failures=[name for name, passed in checks.items() if not passed],
                  notVerified=['player login, character selection and game entry', 'raid gameplay',
                               'equipment pickup and client display', 'random-box inventory rewards',
                               'cross-end protocol interaction'],
                  applicability={'databaseMigration':'not-applicable: project not launched',
                                 'startup':'user-confirmed; only changed-payload regression performed',
                                 'target':'current local 70-level server materials'},
                  candidateHashes={name: hashlib.sha256((root / 'runtime' / rel).read_bytes()).hexdigest()
                                   for name, rel in [('gateway', 'bin/dof-gateway'),
                                                     ('plugin', 'lib/libdof-plugin.so'),
                                                     ('inline', 'lib/libdof-inline.so')]})
    return report


if __name__ == '__main__':
    import argparse
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--output', type=Path, default=Path('/output/native-observations.json'))
    args = parser.parse_args()
    pids = []
    for path in Path('/proc').iterdir():
        if not path.name.isdecimal():
            continue
        try:
            if str((path / 'exe').resolve()) == '/output/bridge/df_bridge_r':
                pids.append(int(path.name))
        except (OSError, RuntimeError):
            pass
    listening = any(len(fields := line.split()) >= 4
                    and fields[1].endswith(':1B58') and fields[3] == '0A'
                    for line in Path('/proc/net/tcp').read_text().splitlines()[1:])
    Path('/output/bridge-live.json').write_text(json.dumps(dict(
        pids=pids, port7000Listening=listening,
        sha256=hashlib.sha256(Path('/output/bridge/df_bridge_r').read_bytes()).hexdigest())) + '\n')
    report = summarize('/output')
    with args.output.open('x') as stream:
        stream.write(json.dumps(report, indent=2) + '\n')
    print(json.dumps(report))
    sys.exit(2 if report['failures'] else 0)
