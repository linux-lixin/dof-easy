"""Run the real admin app/browser against a fresh synthetic loopback-only MySQL.

Requires already provisioned tools. Never downloads or connects to a game instance.
The work directory must not exist; the script creates and terminates both servers.
"""
import argparse
import hashlib
import json
import os
from pathlib import Path
import re
import socket
import subprocess
import time
import urllib.error
import urllib.request

ROOT = Path(__file__).resolve().parents[3]


def port():
    with socket.socket() as sock:
        sock.bind(('127.0.0.1', 0))
        return sock.getsockname()[1]


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    for key in ('mysql-base', 'java', 'classes', 'dependencies', 'node', 'dist', 'work'):
        parser.add_argument('--' + key, type=Path, required=True)
    args = parser.parse_args()
    args.work.mkdir(mode=0o700, exist_ok=False)
    mysql, backend = None, None
    mysql_base = args.mysql_base.resolve()
    env = dict(os.environ, LD_LIBRARY_PATH=str(mysql_base / 'lib/x86_64-linux-gnu'))
    try:
        with (args.work / 'mysql-init.log').open('w') as log:
            subprocess.run([str(mysql_base / 'usr/bin/mysql_install_db'), '--no-defaults', '--keep-my-cnf',
                            '--basedir=' + str(mysql_base / 'usr'), '--datadir=' + str(args.work / 'data')],
                           env=env, check=True, stdout=log, stderr=subprocess.STDOUT)
        db_port, web_port = port(), port()
        server = [str(mysql_base / 'usr/sbin/mysqld'), '--no-defaults', '--basedir=' + str(mysql_base / 'usr'),
                  '--datadir=' + str(args.work / 'data'), '--socket=' + str(args.work / 'mysql.sock'),
                  '--pid-file=' + str(args.work / 'mysql.pid'), '--bind-address=127.0.0.1', '--port=' + str(db_port),
                  '--lc-messages-dir=' + str(mysql_base / 'usr/share/mysql'), '--secure-file-priv=NULL',
                  '--innodb-buffer-pool-size=64M', '--innodb-log-file-size=8M', '--max-connections=30']
        mysql_log = (args.work / 'mysql.log').open('w')
        mysql = subprocess.Popen(server, env=env, stdout=mysql_log, stderr=subprocess.STDOUT)
        client = [str(mysql_base / 'usr/bin/mysql'), '--no-defaults', '--socket=' + str(args.work / 'mysql.sock'), '-uroot', '-N', '--default-character-set=utf8']
        def query(sql):
            return subprocess.run(client, input=sql, env=env, check=True, capture_output=True, text=True).stdout.strip()
        for _ in range(100):
            try:
                identity = query('SELECT @@server_uuid;')
                break
            except subprocess.CalledProcessError:
                if mysql.poll() is not None:
                    raise RuntimeError('Synthetic MySQL failed; inspect private logs')
                time.sleep(.2)
        else:
            raise RuntimeError('Synthetic MySQL startup timed out')
        query('CREATE DATABASE dnf_service; CREATE DATABASE dof_native; CREATE DATABASE d_taiwan;')
        query((ROOT / 'services/admin/database/dof-admin.sql').read_text())
        query("INSERT INTO dnf_service.DOF_INSTANCE_BINDING VALUES('dof-browser-fixture','isolated','" + identity + "','dof-native-1');"
              "CREATE TABLE dnf_service.DOF_CONTRACT_TEST_MARKER(purpose VARCHAR(64));"
              "INSERT INTO dnf_service.DOF_CONTRACT_TEST_MARKER VALUES('disposable-admin-browser');")
        query("CREATE TABLE d_taiwan.accounts(UID BIGINT PRIMARY KEY,accountname VARCHAR(64),password VARCHAR(32),qq VARCHAR(64),VIP VARCHAR(64),admin INT,parent_uid BIGINT) ENGINE=InnoDB;"
              "CREATE TABLE d_taiwan.member_punish_info(m_id BIGINT,punish_type INT,punish_value INT,apply_flag INT,start_time DATETIME,end_time DATETIME) ENGINE=MyISAM;")
        password = 'synthetic-admin-password-only'
        digest = hashlib.md5(password.encode()).hexdigest()
        query("INSERT INTO d_taiwan.accounts VALUES(999,'fixture-admin','" + digest + "','','',1,0);")
        sections = json.loads((ROOT / 'services/admin/backend/src/main/resources/extension/activities.json').read_text())
        for section in sections:
            if section['key'] not in ('signin', 'rewards'):
                continue
            definitions, values = [], []
            for field in section['fields']:
                assert re.fullmatch(r'[A-Za-z_][A-Za-z0-9_]*', field['name'])
                kind = field['type']
                assert kind in ('int', 'varchar', 'date', 'datetime')
                definitions.append('`' + field['name'] + '` ' + ('VARCHAR(255)' if kind == 'varchar' else kind.upper()))
                value = field['default']
                if kind == 'date': value = '2026-01-01' if field['name'] == 'start_time' else '2026-12-31'
                if field['name'] == 'd_Name': value = 'synthetic-signin'
                if section['key'] == 'rewards' and field['name'] == 'item1': value = 100
                if section['key'] == 'rewards' and field['name'] == 'item_code1': value = 1
                values.append(str(value) if kind == 'int' else "'" + value.replace("'", "''") + "'")
            if section['primaryKey']:
                definitions.append('PRIMARY KEY (`' + section['primaryKey'] + '`)')
            query('CREATE TABLE dof_native.`' + section['table'] + '`(' + ','.join(definitions) + ") ENGINE=MyISAM DEFAULT CHARSET=utf8;INSERT INTO dof_native.`" + section['table'] + '` VALUES(' + ','.join(values) + ');')
        query("CREATE DATABASE taiwan_cain; CREATE DATABASE taiwan_login;"
              "CREATE TABLE taiwan_cain.charac_info(charac_no INT PRIMARY KEY,m_id INT,charac_name VARCHAR(32),village INT,job INT,lev INT,exp INT,grow_type INT,HP INT,maxHP SMALLINT UNSIGNED,maxMP SMALLINT UNSIGNED,phy_attack SMALLINT UNSIGNED,phy_defense SMALLINT UNSIGNED,mag_attack SMALLINT UNSIGNED,mag_defense SMALLINT UNSIGNED,attack_speed SMALLINT UNSIGNED,cast_speed SMALLINT UNSIGNED,move_speed SMALLINT UNSIGNED,hit_recovery SMALLINT,jump SMALLINT,fatigue SMALLINT,create_time DATETIME,unknown_setting VARBINARY(8)) ENGINE=MyISAM;"
              "CREATE TABLE taiwan_login.login_account_3(m_id INT,login_status TINYINT) ENGINE=MyISAM;"
              "INSERT INTO d_taiwan.accounts VALUES(1000,'fixture-player','','','',0,0);"
              "INSERT INTO taiwan_cain.charac_info VALUES(1,1000,'SyntheticRole',1,0,86,0,0,100,100,100,10,10,10,10,10,10,10,0,0,156,NOW(),X'00FF');"
              "INSERT INTO taiwan_login.login_account_3 VALUES(1000,0);")
        credentials = args.work / 'credentials.json'
        credentials.write_text(json.dumps(dict(account='fixture-admin', password=password)))
        credentials.chmod(0o600)
        classpath = os.pathsep.join([str(args.classes.resolve()), str(ROOT / 'services/admin/backend/src/main/resources')] + [str(p) for p in sorted(args.dependencies.resolve().glob('*.jar'))])
        java_env = dict(os.environ, DOF_ADMIN_ENABLED='true', DOF_ENVIRONMENT='isolated', DOF_INSTANCE_ID='dof-browser-fixture',
                        DOF_ADAPTER_VERSION='dof-native-1', DOF_DATABASE_UUID=identity, LAUNCHER_MODE='native',
                        SPRING_DATASOURCE_URL=f'jdbc:mysql://127.0.0.1:{db_port}/dnf_service?useUnicode=true&characterEncoding=utf8&useSSL=false',
                        SPRING_DATASOURCE_USERNAME='root', SPRING_DATASOURCE_PASSWORD='')
        java_log = (args.work / 'backend.log').open('w')
        backend = subprocess.Popen([str(args.java.resolve()), '-Xmx256m', '-cp', classpath,
                                    'com.aiyi.game.dnfserver.DnfServerApplication', '--server.address=127.0.0.1', '--server.port=' + str(web_port)],
                                   cwd=args.work, env=java_env, stdout=java_log, stderr=subprocess.STDOUT)
        ready = False
        for _ in range(120):
            try:
                urllib.request.urlopen(f'http://127.0.0.1:{web_port}/api/v1/admin/activities', timeout=1)
            except urllib.error.HTTPError as error:
                ready = error.code in (401, 403)
            except OSError:
                pass
            if ready: break
            if backend.poll() is not None: raise RuntimeError('Synthetic backend failed; inspect private logs')
            time.sleep(.5)
        if not ready: raise RuntimeError('Synthetic backend startup timed out')
        subprocess.run([str(args.node.resolve()), str(ROOT / 'tests/integration/admin/activity-page.cjs'), '--dist', str(args.dist.resolve()),
                        '--backend', f'http://127.0.0.1:{web_port}', '--credentials', str(credentials), '--output', str(args.work / 'browser')], check=True, env=os.environ)
        assert query('SELECT item_code1 FROM dof_native.list_of_warorder_rewards') == '1'
        assert query("SELECT COUNT(*) FROM dnf_service.DOF_OPERATION_JOURNAL WHERE state='PERSISTED'") == '4'
        assert query('SELECT maxHP FROM taiwan_cain.charac_info') == '100'
        assert query('SELECT HEX(unknown_setting) FROM taiwan_cain.charac_info') == '00FF'
        print('Synthetic native configuration restored; no real game database was accessed')
    finally:
        for process in (backend, mysql):
            if process is not None:
                process.terminate()
                try: process.wait(timeout=15)
                except subprocess.TimeoutExpired: process.kill(); process.wait()
        (args.work / 'credentials.json').unlink(missing_ok=True)


if __name__ == '__main__':
    main()
