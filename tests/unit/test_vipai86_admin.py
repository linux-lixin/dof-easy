"""Execute the admin entrypoint offline; the fake DB captures its SQL boundary."""
import hashlib
import os
import pathlib
import shlex
import subprocess
import tempfile
import unittest
import uuid

from test_deployment_fixes import BASH, ROOT, shellpath


class Vipai86Admin(unittest.TestCase):
    def boot(self, *, password='LocalTestPass42', secret='LocalTestSecret43',
             mode='ok', marker=False, public_ip='203.0.113.86', level='86'):
        with tempfile.TemporaryDirectory() as td:
            root = pathlib.Path(td)
            app = root / 'app'
            (app / 'data').mkdir(parents=True)
            for name in ('private.key', 'publickey.pem'):
                (app / 'data' / name).touch()
            if marker:
                (app / 'data/.db_initialized').touch()
            (app / 'init.sql.template').write_bytes((ROOT / 'services/admin/database/init.sql').read_bytes())
            nginx = root / 'nginx'
            nginx.mkdir()
            (nginx / 'nginx.conf.template').write_bytes(b'local nginx\n')
            binpath = root / 'bin'
            binpath.mkdir()
            scripts = {
                'mysql': '''#!/bin/sh
case "$*" in *information_schema*) echo 0; exit 0;; esac
input=$(cat)
printf '%s\\n' "$input" >> "$TEST_SQL"
case "$input" in *'ALTER TABLE'*) [ "$TEST_MODE" != import_fail ] || exit 42;;
*'INSERT INTO d_taiwan.accounts'*) [ "$TEST_MODE" != bootstrap_fail ] || exit 43;; esac
''',
                'envsubst': r'''#!/bin/sh
sed -e "s/\${DB_NAME}/$DB_NAME/g" -e "s/\${DNF_PUBLIC_IP}/$DNF_PUBLIC_IP/g" -e "s/\${DNF_MAX_LEVEL}/$DNF_MAX_LEVEL/g"
''',
                'service': '#!/bin/sh\nexit 0\n',
                'java': '#!/bin/sh\necho java-started\n',
            }
            for name, source in scripts.items():
                path = binpath / name
                # Scripts are data for Bash, never PATH executables. This also works
                # on noexec tmpfs, and emits LF on Windows and Linux alike.
                path.write_bytes(source.encode('utf8'))
                path.chmod(0o600)
            script = (ROOT / 'services/admin/docker/entrypoint.sh').read_text(encoding='utf8')
            script = script.replace('/app', shellpath(app)).replace('/etc/nginx', shellpath(nginx))
            interpreter = shlex.quote(shellpath(BASH))
            wrappers = []
            for name in scripts:
                executable = interpreter + ' ' + shlex.quote(shellpath(binpath / name))
                wrappers.append(name + '() { ' + executable + ' "$@"; }\n')
                wrappers.append('[ "$(command -v ' + name + ')" = "' + name + '" ] || exit 90\n')
            # exec does not resolve shell functions; replace this one boundary
            # explicitly so an installed JVM can never be launched by the fixture.
            self.assertEqual(script.count('exec java -jar '), 1)
            script = script.replace('exec java -jar ', 'exec ' + interpreter + ' ' +
                                    shlex.quote(shellpath(binpath / 'java')) + ' -jar ')
            target = root / 'run.sh'
            target.write_bytes((''.join(wrappers) + script).encode('utf8'))
            sql = root / 'queries.sql'
            env = dict(os.environ, TEST_SQL=shellpath(sql), TEST_MODE=mode,
                       SPRING_DATASOURCE_URL='jdbc:mysql://mysql:3306/dnf_service?useSSL=false',
                       SPRING_DATASOURCE_USERNAME='root', SPRING_DATASOURCE_PASSWORD='TestOnlyDb',
                       ADMIN_PASSWORD=password, ADMIN_TEMP_SECRET=secret,
                       DNF_PUBLIC_IP=public_ip, DNF_MAX_LEVEL=level, NGINX_PORT='80', SERVER_PORT='9001')
            result = subprocess.run([BASH, shellpath(target)], env=env, text=True,
                                    encoding='utf8', capture_output=True, timeout=20)
            return result, sql.read_text(encoding='utf8') if sql.exists() else '', (app / 'data/.db_initialized').exists()

    def test_fresh_import_bootstraps_account_after_schema(self):
        result, sql, marker = self.boot()
        self.assertEqual(result.returncode, 0, result.stderr)
        self.assertTrue(marker)
        self.assertIn('INSERT INTO d_taiwan.accounts', sql)
        self.assertLess(sql.index('ALTER TABLE'), sql.index('INSERT INTO d_taiwan.accounts'))
        self.assertIn(hashlib.md5(b'LocalTestPass42').hexdigest(), sql)
        self.assertNotIn('LocalTestPass42', result.stdout + result.stderr)
        self.assertNotIn('LocalTestSecret43', result.stdout + result.stderr)
        self.assertIn('WHERE NOT EXISTS', sql)
        self.assertNotIn('ON DUPLICATE KEY UPDATE', sql)

    def test_bootstrap_failure_does_not_mark_or_start(self):
        result, sql, marker = self.boot(mode='bootstrap_fail')
        self.assertNotEqual(result.returncode, 0)
        self.assertFalse(marker)
        self.assertNotIn('java-started', result.stdout)

    def test_import_failure_never_attempts_bootstrap(self):
        result, sql, marker = self.boot(mode='import_fail')
        self.assertNotEqual(result.returncode, 0)
        self.assertFalse(marker)
        self.assertNotIn('INSERT INTO d_taiwan.accounts', sql)

    def test_restart_does_not_apply_changed_admin_credentials(self):
        result, sql, marker = self.boot(marker=True)
        self.assertEqual(result.returncode, 0, result.stderr)
        self.assertEqual(sql, '')
        self.assertTrue(marker)

    def test_incomplete_bootstrap_secrets_rejected_before_import(self):
        result, sql, marker = self.boot(secret='')
        self.assertNotEqual(result.returncode, 0)
        self.assertEqual(sql, '')
        self.assertFalse(marker)

    def test_initial_client_settings_are_local_86_and_updates_empty(self):
        result, sql, marker = self.boot()
        self.assertEqual(result.returncode, 0, result.stderr)
        self.assertIn('203.0.113.86', sql)
        self.assertIn('角色等级上限\\":86', sql)
        self.assertNotIn('https://', sql)
        self.assertNotIn('INSERT INTO `CLIENT_LAUNCHER_VERSION`', sql)
        self.assertNotIn('INSERT INTO `CLIENT_LAUNCHER_BANNER`', sql)

    def test_invalid_public_ip_is_rejected_before_sql_rendering(self):
        result, sql, marker = self.boot(public_ip="1.2.3.4'bad")
        self.assertNotEqual(result.returncode, 0)
        self.assertEqual(sql, '')
        self.assertFalse(marker)

    def test_selected_level_is_rendered_and_invalid_level_rejected(self):
        for level in ('55', '70', '120'):
            result, sql, marker = self.boot(level=level)
            self.assertEqual(result.returncode, 0, result.stderr)
            self.assertIn('角色等级上限\\":' + level, sql)
        result, sql, marker = self.boot(level='70;DROP')
        self.assertNotEqual(result.returncode, 0)
        self.assertFalse(marker)
        self.assertEqual(sql, '')

    @unittest.skipUnless(os.environ.get('VIPAI86_DB_TESTS') == '1', 'requires isolated vipai86-audit MySQL 5.6')
    def test_mysql56_bootstrap_sql_creates_once_and_preserves_credentials(self):
        result, sql, marker = self.boot()
        self.assertEqual(result.returncode, 0, result.stderr)
        schema = 'vipai86_admin_probe_' + uuid.uuid4().hex
        bootstrap = sql[sql.index('INSERT INTO d_taiwan.accounts'):]
        bootstrap = bootstrap.replace('d_taiwan.accounts', schema + '.accounts')
        bootstrap = bootstrap.replace('`dnf_service`.ADMIN_TEMP_PASSWORD', schema + '.ADMIN_TEMP_PASSWORD')
        command = ['docker', 'exec', '-i', 'vipai86-audit-db-1', 'sh', '-c',
                   'MYSQL_PWD="$MYSQL_ROOT_PASSWORD" mysql -uroot -N']
        def query(value):
            return subprocess.run(command, input=value, text=True, capture_output=True, timeout=20)
        try:
            setup = query('CREATE DATABASE ' + schema + ';\nCREATE TABLE ' + schema +
                          '.accounts LIKE d_taiwan.accounts;\nCREATE TABLE ' + schema +
                          '.ADMIN_TEMP_PASSWORD LIKE dnf_service.ADMIN_TEMP_PASSWORD;\n')
            self.assertEqual(setup.returncode, 0, setup.stderr)
            first = query(bootstrap)
            self.assertEqual(first.returncode, 0, first.stderr)
            first_check = query('SELECT COUNT(*), SUM(password=MD5(\'LocalTestPass42\')) FROM ' +
                                schema + '.accounts;')
            self.assertEqual(first_check.stdout.strip(), '1\t1', 'Initial password must match the supplied credential')
            second = query(bootstrap.replace(hashlib.md5(b'LocalTestPass42').hexdigest(),
                                             hashlib.md5(b'ChangedPassForTest').hexdigest()))
            self.assertEqual(second.returncode, 0, second.stderr)
            check = query('SELECT COUNT(*), SUM(password=MD5(\'LocalTestPass42\')) FROM ' +
                          schema + '.accounts; SELECT COUNT(*) FROM ' + schema + '.ADMIN_TEMP_PASSWORD;')
            self.assertEqual(check.returncode, 0, check.stderr)
            self.assertEqual(check.stdout.strip().splitlines(), ['1\t1', '1'])
        finally:
            cleanup = query('DROP DATABASE IF EXISTS ' + schema + ';')
            self.assertEqual(cleanup.returncode, 0, cleanup.stderr)


if __name__ == '__main__':
    unittest.main(verbosity=2)
