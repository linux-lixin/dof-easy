"""Run the actual Java postal mapper contract on an empty, disposable MySQL 5.6."""
import json
from pathlib import Path
import subprocess

client = ['/inputs/mysql/usr/bin/mysql', '--no-defaults', '--socket=/tmp/mysql-run/mysql.sock', '-uroot', '-N', '-B']
def sql(text):
    result = subprocess.run(client, input=text, text=True, capture_output=True, timeout=20)
    if result.returncode:
        raise RuntimeError('Disposable postal fixture setup failed; database output withheld')
    return result.stdout.strip()

uuid = sql('SELECT @@server_uuid')
sql("CREATE DATABASE dnf_service; CREATE TABLE dnf_service.DOF_POSTAL_TEST_MARKER(purpose VARCHAR(80)); INSERT INTO dnf_service.DOF_POSTAL_TEST_MARKER VALUES('disposable-postal-read-contract');")
properties = Path('/output/postal-contract.properties')
properties.write_text('url=jdbc:mysql://127.0.0.1:33306/dnf_service?useUnicode=true&characterEncoding=UTF-8&useSSL=false\nusername=root\npassword=\nuuid=' + uuid + '\n')
properties.chmod(0o600)
result = subprocess.run([
    '/inputs/java/bin/java', '-Dnet.bytebuddy.experimental=true',
    '-Ddof.postal.contract.properties=' + str(properties),
    '-Ddof.postal.contract.seed=/inputs/seed.sql',
    '-cp', '/inputs/classes:/inputs/dependencies/*',
    'org.junit.runner.JUnitCore', 'com.aiyi.game.dnfserver.service.dof.PostalReadContractTest',
], text=True, capture_output=True, timeout=90)
Path('/output/junit.log').write_text(result.stdout + '\n' + result.stderr)
passed = result.returncode == 0 and 'OK (4 tests)' in result.stdout
summary = dict(passed=passed, databaseVersion=sql('SELECT VERSION()'),
               actualMapperExecuted=passed, populatedFixture=True,
               fixtureSchemasRemoved=sql("SELECT COUNT(*) FROM information_schema.SCHEMATA WHERE SCHEMA_NAME IN ('d_taiwan','taiwan_cain','taiwan_cain_2nd')") == '0',
               contractTests=4, nativeToolEquivalenceVerified=False,
               realDeploymentVerified=False, playerReceiptVerified=False)
Path('/output/postal-read-result.json').write_text(json.dumps(summary, indent=2) + '\n')
print(json.dumps(summary))
if not passed or not summary['fixtureSchemasRemoved']:
    raise SystemExit(1)
