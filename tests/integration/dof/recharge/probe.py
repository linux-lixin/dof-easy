"""Actual recharge and generated mail receipt contracts on namespace-only MySQL."""
import json
from pathlib import Path
import subprocess

client = ['/inputs/mysql/usr/bin/mysql', '--no-defaults', '--socket=/tmp/mysql-run/mysql.sock', '-uroot', '-N', '-B']
def sql(text):
    r = subprocess.run(client, input=text, text=True, capture_output=True, timeout=20)
    if r.returncode:
        raise RuntimeError('Disposable recharge fixture setup failed; database output withheld')
    return r.stdout.strip()

uuid = sql('SELECT @@server_uuid')
sql("CREATE DATABASE dnf_service; CREATE TABLE dnf_service.DOF_RECHARGE_TEST_MARKER(purpose VARCHAR(80)); INSERT INTO dnf_service.DOF_RECHARGE_TEST_MARKER VALUES('disposable-recharge-contract');")
sql(Path('/inputs/dof-admin.sql').read_text())
sql("INSERT INTO dnf_service.DOF_INSTANCE_BINDING VALUES('dof-recharge-contract','isolated','" + uuid + "','dof-native-1');")
properties = Path('/output/recharge-contract.properties')
properties.write_text('url=jdbc:mysql://127.0.0.1:33306/dnf_service?useUnicode=true&characterEncoding=UTF-8&useSSL=false\nusername=root\npassword=\nuuid=' + uuid + '\n')
properties.chmod(0o600)
r = subprocess.run([
    '/inputs/java/bin/java', '-Dnet.bytebuddy.experimental=true',
    '-Ddof.recharge.contract.properties=' + str(properties), '-Ddof.recharge.contract.seed=/inputs/seed.sql',
    '-cp', '/inputs/classes:/inputs/dependencies/*', 'org.junit.runner.JUnitCore',
    'com.aiyi.game.dnfserver.service.dof.ManagedRechargeContractTest',
    'com.aiyi.game.dnfserver.service.dof.ManagedMailReceiptContractTest',
    'com.aiyi.game.dnfserver.service.dof.ManagedPostalContractTest',
    'com.aiyi.game.dnfserver.service.dof.ManagedPostalBatchContractTest',
    'com.aiyi.game.dnfserver.service.dof.ManagedPostalReceiptContractTest',
], text=True, capture_output=True, timeout=150)
Path('/output/junit.log').write_text(r.stdout + '\n' + r.stderr)
passed = r.returncode == 0 and 'OK (47 tests)' in r.stdout
summary = dict(passed=passed, contractTests=14, databaseVersion=sql('SELECT VERSION()'),
               generatedMailReceiptContractTests=3, managedPostalContractTests=12, postalBatchContractTests=11, postalReceiptControlContractTests=7, totalContractTests=47,
               realServiceSqlFaultAndConcurrencyGatePassed=passed,
               fixtureSchemasRemoved=sql("SELECT COUNT(*) FROM information_schema.SCHEMATA WHERE SCHEMA_NAME IN ('d_taiwan','taiwan_billing','taiwan_cain','taiwan_cain_2nd')") == '0',
               originalToolEquivalenceVerified=False, realDeploymentVerified=False, actualPlayerReceiptVerified=False)
Path('/output/recharge-result.json').write_text(json.dumps(summary, indent=2) + '\n')
print(json.dumps(summary))
if not passed or not summary['fixtureSchemasRemoved']:
    raise SystemExit(1)
