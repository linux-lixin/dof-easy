"""Run registration contracts against a fresh, disposable loopback MySQL 5.6.

The directory must not exist. No connection to an existing instance is accepted.
Synthetic rows test management semantics, never original-tool/game equivalence.
"""
import argparse
import hashlib
import json
import urllib.request
import urllib.error
import os
from pathlib import Path
import subprocess
import time
from run_activity_fixture import port, ROOT


def main():
    p = argparse.ArgumentParser(description=__doc__)
    for name in ('mysql-base', 'java', 'classes', 'test-classes', 'dependencies', 'spring-test', 'work'):
        p.add_argument('--' + name, type=Path, required=True)
    p.add_argument('--browser-only', action='store_true', help='Run only the browser against a fresh fixture; does not count as the registration contract test')
    p.add_argument('--node', type=Path)
    p.add_argument('--dist', type=Path)
    a = p.parse_args()
    if a.browser_only and not a.node:
        p.error('--browser-only requires --node and --dist')
    if bool(a.node) != bool(a.dist):
        p.error('--node and --dist must be supplied together')
    a.work = a.work.resolve()
    a.work.mkdir(mode=0o700, exist_ok=False)
    mysql, backend = None, None
    base = a.mysql_base.resolve()
    env = dict(os.environ, LD_LIBRARY_PATH=str(base / 'lib/x86_64-linux-gnu'))
    try:
        with (a.work / 'init.log').open('w') as log:
            subprocess.run([str(base / 'usr/bin/mysql_install_db'), '--no-defaults', '--keep-my-cnf', '--basedir='+str(base / 'usr'), '--datadir='+str(a.work / 'data')], env=env, stdout=log, stderr=subprocess.STDOUT, check=True)
        db_port = port()
        with (a.work / 'mysql.log').open('w') as log:
            mysql = subprocess.Popen([str(base / 'usr/sbin/mysqld'), '--no-defaults', '--basedir='+str(base / 'usr'), '--datadir='+str(a.work / 'data'), '--socket='+str(a.work / 'mysql.sock'), '--pid-file='+str(a.work / 'mysql.pid'), '--bind-address=127.0.0.1', '--port='+str(db_port), '--lc-messages-dir='+str(base / 'usr/share/mysql'), '--secure-file-priv=NULL', '--innodb-buffer-pool-size=64M', '--innodb-log-file-size=8M', '--max-connections=30'], env=env, stdout=log, stderr=subprocess.STDOUT)
        client = [str(base / 'usr/bin/mysql'), '--no-defaults', '--socket='+str(a.work / 'mysql.sock'), '-uroot', '-N', '--default-character-set=utf8']
        def query(sql):
            return subprocess.run(client, input=sql, env=env, text=True, capture_output=True, check=True).stdout.strip()
        for _ in range(100):
            try:
                identity = query('SELECT @@server_uuid;')
                break
            except subprocess.CalledProcessError:
                if mysql.poll() is not None:
                    raise RuntimeError('Fresh MySQL failed; inspect fixture logs')
                time.sleep(.2)
        else:
            raise RuntimeError('Fresh MySQL did not start')
        query(''.join('CREATE DATABASE '+db+' DEFAULT CHARACTER SET utf8 COLLATE utf8_general_ci;' for db in ('dnf_service', 'dof_native', 'd_taiwan', 'taiwan_login', 'taiwan_billing', 'taiwan_cain_2nd')))
        query((ROOT / 'services/admin/database/dof-admin.sql').read_text())
        query("INSERT INTO dnf_service.DOF_INSTANCE_BINDING VALUES('dof-registration-fixture','isolated','"+identity+"','dof-native-1');CREATE TABLE dnf_service.DOF_CONTRACT_TEST_MARKER(purpose VARCHAR(64));INSERT INTO dnf_service.DOF_CONTRACT_TEST_MARKER VALUES('disposable-registration');")
        query("CREATE TABLE d_taiwan.accounts(UID INT NOT NULL AUTO_INCREMENT PRIMARY KEY,accountname VARCHAR(64) NOT NULL,password VARCHAR(64) NOT NULL,qq VARCHAR(64),ip VARCHAR(64),VIP VARCHAR(64),admin INT,parent_uid BIGINT) ENGINE=InnoDB;"
              "CREATE TABLE d_taiwan.member_punish_info(m_id INT NOT NULL,punish_type INT NOT NULL,occ_time DATETIME NOT NULL,punish_value INT NOT NULL,apply_flag TINYINT NOT NULL,start_time DATETIME NOT NULL,end_time DATETIME NOT NULL,admin_id VARCHAR(25) NOT NULL,reason VARCHAR(100) NOT NULL,PRIMARY KEY(m_id,punish_type)) ENGINE=MyISAM;"
              "CREATE TABLE d_taiwan.member_info(m_id INT NOT NULL PRIMARY KEY,user_id VARCHAR(30)) ENGINE=InnoDB;"
              "CREATE TABLE d_taiwan.member_white_account(m_id INT NOT NULL PRIMARY KEY) ENGINE=InnoDB;"
              "CREATE TABLE taiwan_login.member_login(m_id INT NOT NULL PRIMARY KEY) ENGINE=InnoDB;"
              "CREATE TABLE taiwan_billing.cash_cera(account VARCHAR(30) NOT NULL PRIMARY KEY,cera INT UNSIGNED NOT NULL,mod_tran BIGINT NOT NULL,mod_date DATETIME NOT NULL,reg_date DATETIME NOT NULL) ENGINE=InnoDB;"
              "CREATE TABLE taiwan_billing.cash_cera_point(account VARCHAR(30) NOT NULL PRIMARY KEY,cera_point INT UNSIGNED NOT NULL,mod_date DATETIME NOT NULL,reg_date DATETIME NOT NULL) ENGINE=InnoDB;"
              "CREATE TABLE taiwan_cain_2nd.member_avatar_coin(m_id INT NOT NULL PRIMARY KEY,avatar_coin INT UNSIGNED DEFAULT 0) ENGINE=InnoDB;"
              "CREATE TABLE dof_native.config(ID INT PRIMARY KEY,switch_regCdkey INT);INSERT INTO dof_native.config VALUES(1,0);"
              "CREATE TABLE dof_native.billing(ID INT PRIMARY KEY,ip_reg_control INT,regnumber INT,regtype INT) ENGINE=MyISAM;INSERT INTO dof_native.billing VALUES(1,-1,0,0);")
        password = 'synthetic-registration-admin-only'
        query("INSERT INTO d_taiwan.accounts VALUES(1,'activity_admin','"+hashlib.md5(password.encode()).hexdigest()+"','','','',1,0);")
        key = a.work / 'registration-state.key'
        key.write_bytes(os.urandom(64)); key.chmod(0o600)
        java_env = dict(os.environ, DOF_REGISTRATION_IT='true', MYSQL_ROOT_PASSWORD=password, DOF_REGISTRATION_STATE_KEY_FILE=str(key), DOF_ADMIN_ENABLED='true', DOF_ENVIRONMENT='isolated', DOF_INSTANCE_ID='dof-registration-fixture', DOF_ADAPTER_VERSION='dof-native-1', DOF_DATABASE_UUID=identity, LAUNCHER_MODE='native', SPRING_DATASOURCE_URL=f'jdbc:mysql://127.0.0.1:{db_port}/dnf_service?useUnicode=true&characterEncoding=utf8&useSSL=false', SPRING_DATASOURCE_USERNAME='root', SPRING_DATASOURCE_PASSWORD='')
        classpath = os.pathsep.join([str(a.test_classes.resolve()), str(a.classes.resolve()), str(ROOT / 'services/admin/backend/src/main/resources'), str(a.spring_test.resolve())]+[str(f) for f in sorted(a.dependencies.resolve().glob('*.jar'))])
        report = []
        if not a.browser_only:
            with (a.work / 'registration.log').open('w') as log:
                subprocess.run([str(a.java.resolve()), '-Xmx384m', '-cp', classpath, 'org.junit.runner.JUnitCore', 'com.aiyi.game.dnfserver.service.extension.RegistrationCodesIntegrationTest'], env=java_env, cwd=a.work, stdout=log, stderr=subprocess.STDOUT, timeout=240, check=True)
            report = [line for line in (a.work / 'registration.log').read_text(errors='replace').splitlines() if line.startswith('REGISTRATION_CHECKS:')]
            if not report:
                raise RuntimeError('No contract result produced')
        if a.node:
            credentials = a.work / 'credentials.json'
            credentials.write_text(json.dumps(dict(account='activity_admin', password=password))); credentials.chmod(0o600)
            web_port = port()
            production_cp = os.pathsep.join([str(a.classes.resolve()), str(ROOT / 'services/admin/backend/src/main/resources')]+[str(f) for f in sorted(a.dependencies.resolve().glob('*.jar'))])
            command = [str(a.java.resolve()), '-Xmx384m', '-cp', production_cp, 'com.aiyi.game.dnfserver.DnfServerApplication', '--server.address=127.0.0.1', '--server.port='+str(web_port)]
            for retained in (False, True):
                with (a.work / 'browser-backend.log').open('a') as log:
                    backend = subprocess.Popen(command, env=java_env, cwd=a.work, stdout=log, stderr=subprocess.STDOUT)
                for _ in range(120):
                    try:
                        urllib.request.urlopen(f'http://127.0.0.1:{web_port}/api/v1/admin/registration/policy', timeout=1)
                    except urllib.error.HTTPError as e:
                        if e.code in (401, 403): break
                    except OSError:
                        pass
                    if backend.poll() is not None: raise RuntimeError('Browser fixture backend failed')
                    time.sleep(.5)
                else: raise RuntimeError('Browser backend did not start')
                browser_command = [str(a.node.resolve()), str(ROOT / 'tests/integration/admin/registration-page.cjs'), '--backend', f'http://127.0.0.1:{web_port}', '--dist', str(a.dist.resolve()), '--credentials', str(credentials), '--output', str(a.work / 'browser')]
                if retained: browser_command.append('--retained')
                subprocess.run(browser_command, check=True, env=os.environ, timeout=120)
                backend.terminate(); backend.wait(timeout=15); backend = None
            (a.work / 'browser/expected-private.json').unlink(missing_ok=True)
        print('\n'.join(report))
    finally:
        if backend:
            backend.terminate()
            try: backend.wait(timeout=15)
            except subprocess.TimeoutExpired: backend.kill(); backend.wait()
        (a.work / 'credentials.json').unlink(missing_ok=True)
        if mysql:
            mysql.terminate()
            try:
                mysql.wait(timeout=15)
            except subprocess.TimeoutExpired:
                mysql.kill(); mysql.wait()
        (a.work / 'registration-state.key').unlink(missing_ok=True)


if __name__ == '__main__':
    main()
