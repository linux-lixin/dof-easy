"""Prepare only an explicitly bound, verified isolated instance.

Run as root on its Ubuntu host, with init.sql and dof-admin.sql in --sources.
Extracts additive service table definitions; never executes the legacy initializer.
No production credentials, gameplay defaults, keys or native schema changes.
"""
import argparse
import datetime
import hashlib
import json
import os
from pathlib import Path
import re
import secrets
import subprocess
try:
    from .space_budget import require_space
except ImportError:
    from space_budget import require_space

from deployment_profile import load as load_profile, bind as bind_profile
ROOT = Path('/opt/dof-isolated/web-admin')
DB = 'dof-isolated-db'
DB_DIRECTORY = Path('/opt/dof-isolated/database')
UUID = ''
INSTANCE = 'dof-isolated'
VERSION = 'dof-native-1'
PROTECTED = (DB, 'dof-isolated-game')
DB_USER = 'dof_adapter'
ADMIN_ACCOUNT = 'isolated_admin'
DB_HOST = '127.0.0.1'


def configure(profile):
    bind_profile(globals(), profile)


SERVICE_TABLES = {'ADMIN_TEMP_PASSWORD', 'ASSIST_CONFIG', 'CLIENT_LAUNCHER_BANNER',
                  'CLIENT_LAUNCHER_CONFIG', 'CLIENT_LAUNCHER_VERSION', 'GM_REGIST_AUTH_CODE', 'recharge_key'}


def inspect(name):
    return json.loads(subprocess.check_output(['docker', 'inspect', name]))[0]


def protected_state():
    result = {}
    for name in PROTECTED:
        obj = inspect(name)
        result[name] = {k: obj[k] for k in ('Image', 'RestartCount')}
        result[name]['started'] = obj['State']['StartedAt']
    return result


def service_ddl(source):
    definitions = re.findall(r'CREATE TABLE `([A-Za-z_]+)` (\(.*?\) ENGINE=.*?);', source, re.S)
    if {name for name, _ in definitions} != SERVICE_TABLES or len(definitions) != 7:
        raise ValueError('Unexpected legacy service schema; refusing extraction')
    return '\n'.join('CREATE TABLE IF NOT EXISTS dnf_service.`' + name + '` ' + body + ';'
                     for name, body in definitions)


def private_json(path, data):
    with open(path, 'x', encoding='utf-8') as stream:
        os.chmod(path, 0o600)
        json.dump(data, stream, indent=2)


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--profile', type=Path, required=True)
    parser.add_argument('--sources', required=True, type=Path)
    parser.add_argument('--peak-bytes', type=int, required=True,
                        help='Additional peak for backups and preparation, excluding the 2 GiB reserve')
    args = parser.parse_args()
    configure(load_profile(args.profile))
    if os.geteuid() != 0:
        raise RuntimeError('Requires root')
    require_space([ROOT, str(DB_DIRECTORY)], args.peak_bytes)
    if (ROOT / 'runtime.env').exists():
        raise RuntimeError('Already prepared; use additive migrations and run_isolated, not initialization')
    before = protected_state()
    obj = inspect(DB)
    mounts = [m['Source'] for m in obj['Mounts'] if m['Destination'] == '/var/lib/mysql']
    if mounts != [str(DB_DIRECTORY)]:
        raise RuntimeError('Isolated database mount identity changed')
    env = dict(v.split('=', 1) for v in obj['Config']['Env'] if '=' in v)
    command = ['docker', 'exec', '-i', '-e', 'MYSQL_PWD=' + env['MYSQL_ROOT_PASSWORD'], DB]

    def sql(value):
        for marker, replacement in (('__ADMIN_ACCOUNT__', ADMIN_ACCOUNT), ('__DB_USER__', DB_USER), ('__DB_HOST__', DB_HOST)):
            value=value.replace(marker, replacement)
        return subprocess.check_output(command + ['mysql', '-uroot', '--batch', '--raw', '--skip-column-names'],
                                       input=value.encode('utf-8')).decode('utf-8').strip()

    if sql('SELECT @@server_uuid;') != UUID:
        raise RuntimeError('Isolated database UUID mismatch')
    ddl = service_ddl((args.sources / 'init.sql').read_text())
    migration = (args.sources / 'dof-admin.sql').read_text()
    if re.search(r'\b(DROP|TRUNCATE|DELETE|UPDATE|ALTER)\b', re.sub(r'(?m)^--.*$', '', migration), re.I):
        raise ValueError('Management migration is not additive')
    ROOT.mkdir(mode=0o700, parents=True, exist_ok=True)
    os.chmod(ROOT, 0o700)
    backup = ROOT / 'backups' / datetime.datetime.now(datetime.timezone.utc).strftime('%Y%m%dT%H%M%SZ')
    backup.mkdir(mode=0o700, parents=True)
    for database, tables in [('dof_native', []), ('d_taiwan', ['accounts'])]:
        dest = backup / (database + '.sql')
        with dest.open('xb') as output:
            os.chmod(dest, 0o600)
            subprocess.run(command + ['mysqldump', '-uroot', '--hex-blob', '--lock-tables', database] + tables,
                           stdout=output, check=True)
        if dest.stat().st_size < 100:
            raise RuntimeError('Empty backup')
    # Player accounts/HUD are recorded before adding the dedicated management identity.
    sample_sql = ('SELECT UID,MD5(CONCAT(accountname,password)),COALESCE(admin,0) FROM d_taiwan.accounts ORDER BY UID;'
                  'SELECT ID,MD5(Hotkeys),MD5(temp_2),MD5(temp_3) FROM dof_native.config ORDER BY ID;'
                  'SELECT COUNT(*) FROM taiwan_cain.charac_info;')
    baseline = sql(sample_sql)
    private_json(backup / 'baseline.json', {'containers': before, 'databaseUuid': UUID, 'data': baseline,
                  'files': {p.name: hashlib.sha256(p.read_bytes()).hexdigest() for p in backup.glob('*.sql')}})
    credentials_path = ROOT / 'credentials.json'
    if not credentials_path.exists():
        private_json(credentials_path, {'databaseUser': DB_USER, 'databasePassword': secrets.token_hex(24),
                    'adminAccount': ADMIN_ACCOUNT, 'adminPassword': secrets.token_urlsafe(24)})
    credentials = json.loads(credentials_path.read_text())
    password = credentials['databasePassword']
    if not re.fullmatch(r'[0-9a-f]{48}', password) or credentials['databaseUser'] != DB_USER or credentials['adminAccount'] != ADMIN_ACCOUNT:
        raise RuntimeError('Unexpected saved credential identity')
    sql('CREATE DATABASE IF NOT EXISTS dnf_service CHARACTER SET utf8;\n' + ddl + '\n' + migration)
    binding = sql("SELECT CONCAT(environment,'|',database_uuid,'|',adapter_version) FROM dnf_service.DOF_INSTANCE_BINDING WHERE instance_id='" + INSTANCE + "';")
    expected = 'isolated|' + UUID + '|' + VERSION
    if binding and binding != expected:
        raise RuntimeError('Existing management binding conflicts; refusing overwrite')
    if not binding:
        sql("INSERT INTO dnf_service.DOF_INSTANCE_BINDING VALUES ('" + INSTANCE + "','isolated','" + UUID + "','" + VERSION + "');")
    native_password = hashlib.md5(credentials['adminPassword'].encode()).hexdigest()
    account = sql("SELECT CONCAT(password,'|',admin,'|',parent_uid) FROM d_taiwan.accounts WHERE accountname='__ADMIN_ACCOUNT__';")
    if account and account != native_password + '|1|0':
        raise RuntimeError('Existing management account conflicts; refusing overwrite')
    if not account:
        sql("INSERT INTO d_taiwan.accounts (accountname,password,qq,ip,VIP,qcc,login_IP,login_Mac,seal_IP,seal_MAC,seal_accountname,admin,parent_uid,isRobot) VALUES ('__ADMIN_ACCOUNT__','" + native_password + "','','','','','','',0,0,0,1,0,0);")
    db_user_exists = sql("SELECT COUNT(*) FROM mysql.user WHERE User='__DB_USER__' AND Host='__DB_HOST__';")
    if db_user_exists == '0':
        sql("CREATE USER '__DB_USER__'@'__DB_HOST__' IDENTIFIED BY '" + password + "';")
    sql("GRANT SELECT ON d_taiwan.* TO '__DB_USER__'@'__DB_HOST__';"
        "GRANT SELECT ON taiwan_cain.* TO '__DB_USER__'@'__DB_HOST__';"
        "GRANT SELECT,LOCK TABLES ON dof_native.* TO '__DB_USER__'@'__DB_HOST__';"
        "GRANT UPDATE (Hotkeys,temp_2,temp_3,name,Notice,GameNotice,Rechar,crowd,Official,webreg,is_open_up_pwd,switch_regCdkey,`Switch`,pvfencryption) ON dof_native.config TO '__DB_USER__'@'__DB_HOST__';"
        "GRANT UPDATE (ip_reg_control,regnumber,regtype,bubbleCount,bubbleTime,bubbleType,decrypt) ON dof_native.billing TO '__DB_USER__'@'__DB_HOST__';"
        "GRANT SELECT,INSERT,UPDATE,DELETE,CREATE,LOCK TABLES ON dnf_service.* TO '__DB_USER__'@'__DB_HOST__';")
    data = ROOT / 'data'
    data.mkdir(mode=0o700, exist_ok=True)
    marker = data / 'dof-isolated.json'
    if not marker.exists():
        private_json(marker, {'instanceId': INSTANCE, 'databaseUuid': UUID, 'environment': 'isolated'})
    variables = {'DOF_ADMIN_ENABLED': 'true', 'DOF_ENVIRONMENT': 'isolated', 'DOF_INSTANCE_ID': INSTANCE,
                 'DOF_DATABASE_UUID': UUID, 'DOF_ADAPTER_VERSION': VERSION, 'LAUNCHER_MODE': 'native',
                 'SPRING_DATASOURCE_URL': 'jdbc:mysql://' + DB + ':3306/dnf_service?useUnicode=true&characterEncoding=UTF-8&useSSL=false',
                 'SPRING_DATASOURCE_USERNAME': DB_USER, 'SPRING_DATASOURCE_PASSWORD': password,
                 'NGINX_PORT': '80', 'SERVER_PORT': '9001'}
    runtime = ROOT / 'runtime.env'
    descriptor = os.open(runtime, os.O_WRONLY | os.O_CREAT | os.O_TRUNC, 0o600)
    with os.fdopen(descriptor, 'w') as stream:
        stream.write(''.join(key + '=' + value + '\n' for key, value in variables.items()))
    after = protected_state()
    if before != after:
        raise RuntimeError('Protected container state changed; inspect before proceeding')
    result = {'prepared': True, 'instanceId': INSTANCE, 'backup': str(backup), 'protectedContainersUnchanged': True,
              'nativeInitializationExecuted': False, 'keysGenerated': False, 'completeReplacement': False}
    private_json(backup / 'result.json', result)
    print(json.dumps(result))


if __name__ == '__main__':
    main()
