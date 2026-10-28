"""One-use, server-local rollback drill for the bound 86-level test instance.

Run as the server operator with --execute-fixed-test-instance, after S42.
Credentials, SQL dumps and configuration bytes remain in the server-local
0700 output directory. Only final-verification.json is a public report.
This does not start the original Windows tool or test native reward issuance.
"""
from pathlib import Path
import argparse
import contextlib
import hashlib
import importlib.util
import io
import json
import os
import re
import shutil
import subprocess
import sys
import tarfile
import time
import urllib.error
import urllib.request

ROOT = Path('/opt/dof-easy/.dof-easy/instances/main')
BASE = Path('/opt/dof-easy/.dof-easy/admin-replacement')
OUT = BASE / '20261028-s43-charts-rollback'
RELEASE = BASE / '20261028-s41-release'
PREVIOUS = BASE / '20261028-s42-charts-config-v2'
DATABASE_UUID = '899d147e-d0ef-11f1-9ff4-525400542f67'
BACKUP_HELPER_SHA256 = 'd191b36671910d381a77ab293542b76267f34dec66cf050c2aa1cf5ad9d630fd'


def digest(path):
    result = hashlib.sha256()
    with path.open('rb') as stream:
        for block in iter(lambda: stream.read(1024 * 1024), b''):
            result.update(block)
    return result.hexdigest()


def run(*args, timeout=60):
    return subprocess.check_output(args, text=True, timeout=timeout).strip()


def inspect(name):
    return json.loads(run('docker', 'inspect', name))[0]


def replace_private(path, content):
    info = path.stat()
    temporary = path.with_name(path.name + '.s43-drill')
    with temporary.open('xb') as stream:
        os.chmod(temporary, 0o600)
        stream.write(content)
        stream.flush()
        os.fsync(stream.fileno())
    os.chown(temporary, info.st_uid, info.st_gid)
    temporary.replace(path)


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--execute-fixed-test-instance', action='store_true', required=True)
    parser.parse_args()
    assert os.geteuid() == 0 and ROOT.is_dir()
    assert OUT.is_dir() and not (OUT / 'phase.json').exists()
    state = {}

    def phase(value, **fields):
        state.update(phase=value, pid=os.getpid(), updatedAt=time.time(), **fields)
        replace = OUT / 'phase.tmp'
        replace.write_text(json.dumps(state, indent=2) + '\n')
        os.chmod(replace, 0o600)
        replace.replace(OUT / 'phase.json')

    phase('PREFLIGHT')
    prior = json.loads((PREVIOUS / 'final-verification.json').read_text())
    assert prior['passed']
    assert not Path('/proc/' + str(json.loads((PREVIOUS / 'phase.json').read_text())['pid'])).exists()
    release = json.loads((RELEASE / 'deployment-result.json').read_text())
    rollback = json.loads((RELEASE / 'rollback-image.json').read_text())
    admin, game = inspect('dof-main-admin-1'), inspect('dof-main-game-1')
    assert admin['Image'] == release['adminImage'] == prior['adminImage']
    assert game['State']['Health']['Status'] == 'healthy'
    assert run('docker', 'image', 'inspect', '--format', '{{.Id}}', rollback['tag']) == rollback['image']
    assert run('docker', 'run', '--rm', '--pull=never', '--entrypoint', 'sha256sum',
               rollback['tag'], '/app/app.jar').split()[0] == rollback['oldAppSHA256']
    original_model = (ROOT / 'deployment.json').read_bytes()
    original_env = (ROOT / '.env').read_bytes()
    model = json.loads(original_model)
    assert model['services']['admin']['image'] == release['adminTag']
    before = OUT / 'before-admin'
    before.mkdir(mode=0o700)
    for name in ['deployment.json', '.env']:
        shutil.copy2(ROOT / name, before / name)
        os.chmod(before / name, 0o600)

    os.chdir('/www/server/panel')
    sys.path.insert(0, '/www/server/panel/class')
    with contextlib.redirect_stdout(io.StringIO()), contextlib.redirect_stderr(io.StringIO()):
        import public
        password = public.M('config').where('id=?', (1,)).getField('mysql_root')
    mysql_env = dict(os.environ, MYSQL_PWD=password)

    def sql(query):
        return subprocess.check_output(['/www/server/mysql/bin/mysql', '--no-defaults',
            '--protocol=SOCKET', '-uroot', '-N', '-B', '-e', query],
            env=mysql_env, text=True, timeout=30).strip()

    assert sql('SELECT @@server_uuid') == DATABASE_UUID
    assert sql('SELECT admin FROM d_taiwan.accounts WHERE UID=5') == '1'
    assert sql("SELECT COUNT(*) FROM information_schema.TRIGGERS WHERE EVENT_OBJECT_SCHEMA='d_taiwan' AND EVENT_OBJECT_TABLE='accounts'") == '0'
    assert sql("SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA='d_taiwan' AND TABLE_NAME='accounts' AND EXTRA LIKE '%on update%'") == '0'

    def receipts():
        lines = sql("SELECT request_id,t.* FROM dnf_service.DOF_OPERATION_JOURNAL t WHERE instance_id='dof-main' ORDER BY request_id").splitlines()
        return {line.split('\t', 1)[0]: hashlib.sha256(line.encode()).hexdigest() for line in lines}

    all_receipts = receipts()
    assert len(all_receipts) == 641
    assert all_receipts == json.loads((PREVIOUS / 'receipt-hashes.after.json').read_text())
    (before / 'receipt-hashes.json').write_text(json.dumps(all_receipts) + '\n')
    assert sql("SELECT COUNT(*) FROM dnf_service.DOF_OPERATION_JOURNAL WHERE instance_id='dof-main' AND namespace LIKE 'activity.%' AND state IN ('PREPARED','CONFLICT')") == '0'
    keys = json.loads((RELEASE / 'before-admin/admin-key-hashes.json').read_text())
    assert all(digest(Path(path)) == value for path, value in keys.items())
    paired = json.loads((RELEASE / 'before-admin/paired-game-hashes.json').read_text())
    heartbeat = set(json.loads((RELEASE / 'before-admin/heartbeat-semantics.json').read_text()))
    static = {path: digest(Path(path)) for path in paired if path not in heartbeat}

    def protected():
        query = "SELECT id,min_zhanli,jiangli_day,jiangli_hour,is_mobai,status,next_time FROM dof_native.game_charts_config ORDER BY id;SELECT top_index,HEX(code_data),HEX(code_num) FROM dof_native.game_charts_code ORDER BY top_index;SELECT HEX(decrypt) FROM dof_native.billing WHERE ID=1;SELECT HEX(pvfencryption),HEX(Scriptmd5),HEX(Scriptverification),HEX(updatec),HEX(updatetype) FROM dof_native.config WHERE ID=1;SELECT cera FROM taiwan_billing.cash_cera WHERE BINARY account='6';SELECT cera_point FROM taiwan_billing.cash_cera_point WHERE BINARY account='6';SELECT COUNT(*) FROM taiwan_cain.charac_info WHERE m_id=6;SELECT CONCAT(status,':',IFNULL(use_uid,'NULL'),':',IFNULL(use_account,'NULL'),':',IFNULL(CAST(use_time AS CHAR),'NULL')) FROM dnf_service.recharge_key WHERE id=2;SELECT admin FROM d_taiwan.accounts WHERE UID=5;"
        return hashlib.sha256(sql(query).encode()).hexdigest()

    protected_hash = protected()
    data_mounts = [mount for mount in admin['Mounts'] if mount['Destination'] == '/app/data']
    assert len(data_mounts) == 1
    data = Path(data_mounts[0]['Source'])
    helper = Path('/tmp/dof-admin-backup-s43.py')
    assert digest(helper) == BACKUP_HELPER_SHA256
    spec = importlib.util.spec_from_file_location('backup_s43', helper)
    verifier = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(verifier)
    archive = RELEASE / 'before-admin/admin-data.tgz'
    reusable = verifier.matches(data, archive)
    data_bytes = sum(path.stat().st_size for path in data.rglob('*') if path.is_file())
    backup_peak = 1024**2 if reusable else (data_bytes * 101 + 99) // 100 + 8 * 1024**2
    budget = {'freeBytes': shutil.disk_usage(ROOT).free, 'baseReserveBytes': 2 * 1024**3,
              'selectedBackupPeakBytes': backup_peak, 'otherPeakBytes': 256 * 1024**2,
              'verifiedArchiveReusePlanned': reusable}
    assert budget['freeBytes'] > budget['baseReserveBytes'] + backup_peak + budget['otherPeakBytes']
    (OUT / 'capacity-budget.json').write_text(json.dumps(budget, indent=2) + '\n')
    if reusable:
        os.link(archive, before / 'admin-data.tgz')
    else:
        with tarfile.open(before / 'admin-data.tgz', 'w:gz') as stream:
            stream.add(data, arcname='admin-data')
    with (before / 'management.sql').open('xb') as stream:
        os.chmod(before / 'management.sql', 0o600)
        subprocess.run(['/www/server/mysql/bin/mysqldump', '--no-defaults', '--protocol=SOCKET',
            '-uroot', '--lock-tables', '--hex-blob', '--databases', 'dnf_service'],
            env=mysql_env, stdout=stream, stderr=subprocess.PIPE, check=True, timeout=90)
    assert shutil.disk_usage(ROOT).free > 2 * 1024**3 + 128 * 1024**2

    credentials = json.loads((BASE / '20261027-s3-registration/test-admin.json').read_text())

    def api(method, path, token=None, body=None, expected=(200,)):
        headers = {'Content-Type': 'application/json'}
        if token:
            headers['Authorization'] = 'Bearer ' + token
        request = urllib.request.Request('http://127.0.0.1:18080' + path, method=method,
            data=None if body is None else json.dumps(body).encode(), headers=headers)
        try:
            with urllib.request.urlopen(request, timeout=30) as response:
                code, content = response.status, response.read()
        except urllib.error.HTTPError as error:
            code, content = error.code, error.read()
        assert code in expected, (method, path, code, expected)
        return content

    def login():
        token = api('POST', '/api/v1/login', body=credentials).decode().strip(chr(34))
        assert re.fullmatch('[0-9a-f]{32}', token)
        return token

    def switch(tag):
        replacement = json.loads(original_model)
        replacement['services']['admin']['image'] = tag
        replace_private(ROOT / 'deployment.json', (json.dumps(replacement, indent=2) + '\n').encode())
        lines = original_env.decode().splitlines()
        positions = [i for i, line in enumerate(lines) if line.startswith('DOF_ADMIN_IMAGE=')]
        assert len(positions) == 1
        lines[positions[0]] = 'DOF_ADMIN_IMAGE=' + tag
        replace_private(ROOT / '.env', ('\n'.join(lines) + '\n').encode())
        with (OUT / 'recreate.log').open('a') as log:
            subprocess.run(['docker', 'compose', '-p', 'dof-main', '-f', str(ROOT / 'deployment.json'),
                'up', '-d', '--no-deps', '--force-recreate', 'admin'], stdout=log,
                stderr=subprocess.STDOUT, check=True, timeout=90)
        for _ in range(60):
            try:
                return login()
            except Exception:
                time.sleep(1)
        raise RuntimeError('Switched admin readiness timed out')

    browser = json.loads((PREVIOUS / 'browser/result.json').read_text())
    routes = ['/api/v1/admin/activities/' + section + '/history' for section in ['charts', 'charts-rewards']]
    for request_id in browser['receiptIds']:
        assert re.fullmatch('[0-9a-f-]{36}', request_id)
        namespace = sql("SELECT namespace FROM dnf_service.DOF_OPERATION_JOURNAL WHERE instance_id='dof-main' AND request_id='" + request_id + "'")
        assert namespace in ['activity.charts', 'activity.charts-rewards']
        routes.append('/api/v1/admin/activities/' + namespace.removeprefix('activity.') + '/history/' + request_id)
    token = login()
    baseline = {route: api('GET', route, token) for route in routes}
    for i, (route, content) in enumerate(baseline.items()):
        (before / ('history-' + str(i) + '.private.json')).write_bytes(content)
    list_routes = ['/api/v1/admin/activities/' + section for section in ['charts', 'charts-rewards']]
    lists = {route: json.loads(api('GET', route, token))['rows'] for route in list_routes}
    tests = [('PUT', list_routes[0]), ('DELETE', list_routes[1]),
             ('POST', list_routes[0] + '/validate'), ('POST', routes[2] + '/restore'),
             ('POST', routes[2] + '/reconcile'), ('POST', routes[2] + '/cancellations'),
             ('PATCH', list_routes[0]), ('PUT', '//api/v1/admin/activities/charts'),
             ('PUT', list_routes[0] + '?retry=1'), ('POST', '/api/v1/recharge')]
    switched = False
    old_passed = False
    phase('BACKED_UP', managedReceipts=641, archiveReuse=reusable)
    try:
        switched = True
        phase('SWITCHING_TO_READONLY_OLD')
        token = switch(rollback['tag'])
        assert inspect('dof-main-admin-1')['Image'] == rollback['image']
        subprocess.run(['docker', 'exec', 'dof-main-admin-1', 'nginx', '-t'],
                       check=True, capture_output=True, timeout=30)
        for name in ['nginx.conf.template', 'dof-nginx.conf.template']:
            raw = subprocess.check_output(['docker', 'exec', 'dof-main-admin-1', 'cat', '/etc/nginx/' + name])
            assert hashlib.sha256(raw).hexdigest() == rollback['guardSHA256']
        for route in routes:
            assert api('GET', route, token) == baseline[route]
        for route in list_routes:
            assert json.loads(api('GET', route, token))['rows'] == lists[route]
        for route in [routes[0], routes[2]]:
            for method in ['GET', 'HEAD']:
                api(method, route, expected=(401, 403))
                api(method, route, token='invalid-test-token', expected=(401, 403))
        role_probe = OUT / 'administrator-role-probe.json'
        role_probe.write_text(json.dumps({'uid': 5, 'before': 1, 'phase': 'REVOKING'}) + '\n')
        try:
            assert sql('UPDATE d_taiwan.accounts SET admin=0 WHERE UID=5 AND admin=1;SELECT ROW_COUNT()') == '1'
            api('GET', routes[0], token, expected=(403,))
            api('HEAD', routes[2], token, expected=(403,))
        finally:
            assert sql('UPDATE d_taiwan.accounts SET admin=1 WHERE UID=5 AND admin=0;SELECT ROW_COUNT()') == '1'
            assert sql('SELECT admin FROM d_taiwan.accounts WHERE UID=5') == '1'
            role_probe.write_text(json.dumps({'uid': 5, 'before': 1, 'phase': 'RESTORED'}) + '\n')
        for method, route in tests:
            api(method, route, token, {}, expected=(503,))
        assert receipts() == all_receipts and protected() == protected_hash
        old_passed = True
        phase('OLD_READS_AND_WRITE_ISOLATION_VERIFIED', historyQueries=len(routes), blockedWrites=len(tests))
    finally:
        if switched:
            phase('RETURNING_TO_CANDIDATE', oldChecksPassed=old_passed)
            token = switch(release['adminTag'])
            replace_private(ROOT / 'deployment.json', original_model)
            replace_private(ROOT / '.env', original_env)
            assert inspect('dof-main-admin-1')['Image'] == release['adminImage']
            if not old_passed:
                phase('FAILED_RETURNED_TO_CANDIDATE')
    assert old_passed
    for route in routes:
        assert api('GET', route, token) == baseline[route]
    for route in list_routes:
        assert json.loads(api('GET', route, token))['rows'] == lists[route]
    assert receipts() == all_receipts and protected() == protected_hash
    assert (ROOT / 'deployment.json').read_bytes() == original_model and (ROOT / '.env').read_bytes() == original_env
    assert all(digest(Path(path)) == value for path, value in keys.items())
    assert all(digest(Path(path)) == value for path, value in static.items())
    current_game = inspect('dof-main-game-1')
    assert current_game['Id'] == game['Id'] and current_game['Image'] == game['Image']
    assert current_game['State']['StartedAt'] == game['State']['StartedAt']
    assert current_game['State']['Health']['Status'] == 'healthy'
    assert shutil.disk_usage(ROOT).free > 2 * 1024**3
    result = {'passed': True, 'verificationScope': 'actual old JAR read-only rollback and return to current admin',
        'oldImage': rollback['image'], 'oldAppSHA256': rollback['oldAppSHA256'],
        'currentImage': release['adminImage'], 'guardSHA256': rollback['guardSHA256'],
        'oldReadQueryCount': len(routes), 'newReceiptDetailsVerified': 8,
        'newReceiptsAndHistoryByteIdenticalAcrossOldAndCurrent': True, 'all641ManagedReceiptsUnchanged': True,
        'anonymousGetHeadAndInvalidTokenDenied': True, 'revokedAdministratorGetAndHeadDenied': True,
        'testAdministratorRoleRestored': True, 'businessWriteProbesBlocked': len(tests),
        'currentNativeRuleAndRewardListsReadableOnOld': True, 'frozenHistoryBridgeUsed': False,
        'currentImageActuallyRestoredAndRecreated': True, 'protectedConfigRewardsWalletsAndPausedCodeUnchanged': True,
        'persistentKeysModelEnvAndStaticConfigPvfUnchanged': True,
        'gameContainerIdImageAndStartedAtUnchanged': True, 'gameHealthy': True,
        'verifiedPrivateAdminArchiveReused': reusable, 'baseReserve2GiBMaintained': True,
        'originalWindowsToolStarted': False, 'nativeConsumerAcceptanceVerified': False,
        'nativeGameplayVerified': False, 'completeReplacement': False}
    (OUT / 'final-verification.json').write_text(json.dumps(result, indent=2) + '\n')
    phase('READONLY_ROLLBACK_AND_RETURN_VERIFIED_GAMEPLAY_PENDING')
    print(json.dumps(result))


if __name__ == '__main__':
    main()
