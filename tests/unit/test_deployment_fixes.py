"""Offline regression checks; no connection to a deployed server."""
import os
import pathlib
import re
import shutil
import subprocess
import tarfile
import tempfile
import unittest

ROOT = pathlib.Path(__file__).resolve().parents[2]
BASH = shutil.which('bash') if os.name != 'nt' else r'C:\Program Files\Git\bin\bash.exe'

def shellpath(p):
    s = str(p).replace('\\', '/')
    return '/' + s[0].lower() + s[2:] if re.match(r'^[A-Za-z]:', s) else s

def run(script, *args, env=None):
    return subprocess.run([BASH, shellpath(script), *map(shellpath, args)],
                          env=env, text=True, capture_output=True)

class DeploymentFixes(unittest.TestCase):
    def test_seed_repair_preserves_queries_and_is_repeatable(self):
        init = ROOT/'services/game/assets/home/template/init'
        with tempfile.TemporaryDirectory() as td:
            target = pathlib.Path(td)/'taiwan_login.sql'
            with tarfile.open(init/'init_sql.tgz') as archive:
                original = archive.extractfile('taiwan_login.sql').read()
            target.write_bytes(original)
            result = run(init/'prepare_login_seed.sh', td)
            self.assertEqual(result.returncode, 0, result.stderr)
            modified = target.read_text(encoding='utf8')
            self.assertIn('AUTO_INCREMENT=50940', modified)
            rows = re.findall(r"INSERT INTO `log_query_ref` VALUES \('[^']+', (\d+), (.*)", modified)
            self.assertEqual(len(rows), 394)
            self.assertEqual(max(int(x[0]) for x in rows), 50939)
            original_queries = re.findall(rb"INSERT INTO `log_query_ref` VALUES \('[^']+', \d+, (.*)", original)
            self.assertEqual([x[1].encode() for x in rows], original_queries)
            before = target.read_bytes()
            self.assertEqual(run(init/'prepare_login_seed.sh', td).returncode, 0)
            self.assertEqual(target.read_bytes(), before)

    def test_modified_seed_is_rejected_without_changes(self):
        init = ROOT/'services/game/assets/home/template/init'
        with tempfile.TemporaryDirectory() as td:
            target = pathlib.Path(td)/'taiwan_login.sql'
            with tarfile.open(init/'init_sql.tgz') as archive:
                original = archive.extractfile('taiwan_login.sql').read()
            target.write_bytes(original.replace(b", 50938,", b", 50939,"))
            before = target.read_bytes()
            self.assertNotEqual(run(init/'prepare_login_seed.sh',td).returncode,0)
            self.assertEqual(target.read_bytes(),before)

    def entrypoint(self, mode='ok', marker=False, ssl='disabled', partial_key=False):
        with tempfile.TemporaryDirectory() as td:
            root = pathlib.Path(td)
            app = root/'app'; (app/'data').mkdir(parents=True)
            (app/'data/private.key').touch(); (app/'data/publickey.pem').touch()
            if partial_key: (app/'data/publickey.pem').unlink()
            if marker: (app/'data/.db_initialized').touch()
            (app/'init.sql.template').write_text('SELECT 1;\n')
            nginx = root/'nginx'; nginx.mkdir()
            (nginx/'nginx.conf.template').write_text('mock\n')
            binpath = root/'bin'; binpath.mkdir()
            scripts = {
                'mysql': '''#!/bin/sh
printf '%s\\n' "$*" >> "$TEST_CALLS"
case "$*" in *information_schema*)
  [ "$TEST_MODE" != partial ] || { echo 1; exit 0; }
  echo 0; exit 0;; esac
cat > /dev/null
[ "$TEST_MODE" != fail ] || exit 42
''',
                'envsubst': '#!/bin/sh\ncat\n',
                'service': '#!/bin/sh\nexit 0\n',
                'java': '#!/bin/sh\necho java >> "$TEST_CALLS"\n',
            }
            for name, content in scripts.items():
                p = binpath/name; p.write_text(content); p.chmod(0o755)
            source = (ROOT/'services/admin/docker/entrypoint.sh').read_text(encoding='utf8')
            source = source.replace('/app', shellpath(app)).replace('/etc/nginx', shellpath(nginx))
            p = root/'run.sh'
            p.write_text('export PATH="'+shellpath(binpath)+':$PATH"\n'+source, encoding='utf8')
            calls = root/'calls'
            env = dict(os.environ, TEST_CALLS=shellpath(calls), TEST_MODE=mode,
                       SPRING_DATASOURCE_URL='jdbc:mysql://10.0.0.20:3306/dnf_service?useSSL=false',
                       SPRING_DATASOURCE_USERNAME='root', SPRING_DATASOURCE_PASSWORD='testonly',
                       MYSQL_INIT_SSL_MODE=ssl, NGINX_PORT='80', SERVER_PORT='9001')
            result = run(p, env=env)
            return result, calls.read_text() if calls.exists() else '', (app/'data/.db_initialized').exists()

    def test_failed_import_never_starts_java_or_marks_success(self):
        result, calls, marker = self.entrypoint(mode='fail')
        self.assertNotEqual(result.returncode, 0)
        self.assertNotIn('java', calls)
        self.assertFalse(marker)

    def test_success_uses_explicit_legacy_ssl_option(self):
        result, calls, marker = self.entrypoint()
        self.assertEqual(result.returncode, 0, result.stderr)
        self.assertIn('--skip-ssl', calls)
        self.assertIn('java', calls)
        self.assertTrue(marker)

    def test_partial_initialization_is_not_reimported(self):
        result, calls, marker = self.entrypoint(mode='partial')
        self.assertNotEqual(result.returncode, 0)
        self.assertNotIn('java', calls)
        self.assertFalse(marker)

    def test_existing_marker_skips_import(self):
        result, calls, marker = self.entrypoint(marker=True)
        self.assertEqual(result.returncode, 0, result.stderr)
        self.assertEqual(calls.strip(), 'java')
        self.assertTrue(marker)

    def test_partial_key_pair_does_not_initialize(self):
        result,calls,marker=self.entrypoint(partial_key=True)
        self.assertNotEqual(result.returncode,0)
        self.assertEqual(calls,'')
        self.assertFalse(marker)

    def test_auto_tls_preserves_client_defaults(self):
        result,calls,marker=self.entrypoint(ssl='auto')
        self.assertEqual(result.returncode,0,result.stderr)
        self.assertNotIn('--skip-ssl',calls)
        self.assertTrue(marker)

    def test_failed_date_does_not_create_monthly_tables(self):
        with tempfile.TemporaryDirectory() as td:
            root=pathlib.Path(td)
            (root/'date').write_bytes(b'#!/bin/sh\nexit 1\n')
            (root/'mysql').write_bytes(b'#!/bin/sh\necho called > "$TEST_CALLS"\n')
            for name in ('date','mysql'): (root/name).chmod(0o755)
            script=ROOT/'services/game/assets/home/template/init/daily_job/response_time_tables.sh'
            wrapper=root/'run.sh'
            wrapper.write_bytes(('export PATH="'+shellpath(root)+':$PATH"\nbash "'+shellpath(script)+'"\n').encode())
            calls=root/'calls'
            result=run(wrapper,env=dict(os.environ,SERVER_GROUP_DB='cain',TEST_CALLS=shellpath(calls)))
            self.assertNotEqual(result.returncode,0)
            self.assertFalse(calls.exists())

if __name__ == '__main__':
    unittest.main(verbosity=2)
