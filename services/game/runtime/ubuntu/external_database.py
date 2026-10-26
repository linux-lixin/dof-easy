"""Bound external MySQL health, connection setup and scoped backup helper."""
import json
import os
import re
import subprocess
import sys


def command(program='mysql'):
    return [program, '--protocol=TCP', '-h', os.environ['DOF_DB_HOST'],
            '-P', os.environ['DOF_DB_PORT'], '-ugame', '--default-character-set=utf8']


def query(sql):
    result = subprocess.run(command()+['-N','-B','-r'], input=sql, text=True,
        env=dict(os.environ,MYSQL_PWD=os.environ['DNF_DB_GAME_PASSWORD']), capture_output=True, timeout=20)
    if result.returncode:
        raise ValueError('External MySQL authentication/query failed; no SQL or password logged')
    return result.stdout.strip()


def check():
    version, identity = query('SELECT VERSION(),@@server_uuid;').split('\t')
    if not version.startswith('5.6.') or identity.lower() != os.environ['DOF_DATABASE_UUID'].lower():
        raise ValueError('External MySQL version or identity mismatch')
    query('SELECT UID FROM d_taiwan.accounts LIMIT 1; SELECT m_id FROM taiwan_cain.charac_info LIMIT 1;')


def configure():
    check()
    password = os.environ['DNF_DB_GAME_PASSWORD']
    if not re.fullmatch('[a-zA-Z0-9]{8}',password):
        raise ValueError('Invalid game credential')
    encrypted = os.environ['DEC_GAME_PWD']
    if not re.fullmatch('[a-zA-Z0-9+/=]+',encrypted):
        raise ValueError('Invalid encrypted database credential')
    # Runtime proxies keep the native loopback contract even for a host DB.
    query("UPDATE d_taiwan.db_connect SET db_ip='127.0.0.1', db_port=IF(db_type IN (1,10,15),3307,3306), db_passwd='"+encrypted+"' WHERE db_server_group=3;")


def dump():
    check()
    databases = json.loads(os.environ['DOF_BACKUP_DATABASES'])
    if not databases or any(not re.fullmatch('[a-z][a-z0-9_]*', d) or d in ('mysql','sys','performance_schema','information_schema') for d in databases):
        raise ValueError('Invalid project backup scope')
    # Game and web writers are stopped by the manager. LOCK TABLES also covers
    # MyISAM; no global/system databases or users are included in this dump.
    result = subprocess.run(command('dof-mysqldump56')+['--skip-ssl','--hex-blob','--lock-tables','--databases',*databases],
        env=dict(os.environ,MYSQL_PWD=os.environ['DNF_DB_GAME_PASSWORD']), stderr=subprocess.PIPE)
    if result.returncode:
        raise ValueError('Project database backup failed; no credentials logged')


def main():
    action=sys.argv[1]
    if action == 'query':
        check(); print(query(sys.stdin.read()))
    else:
        {'check':check, 'configure':configure, 'dump':dump}[action]()


if __name__ == '__main__':
    try: main()
    except Exception:
        print('External MySQL operation failed; verify version, UUID, grants and connectivity.',file=sys.stderr)
        sys.exit(1)
