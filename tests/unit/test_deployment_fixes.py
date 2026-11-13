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
