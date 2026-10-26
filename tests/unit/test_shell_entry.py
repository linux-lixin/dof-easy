"""Execute the real shell entry with disposable Python/bootstrap substitutes."""
import json
import os
from pathlib import Path
import shutil
import subprocess
import sys
import tempfile
import unittest

ROOT=Path(__file__).resolve().parents[2]


class ShellEntry(unittest.TestCase):
    def invoke(self,args=(),*,python='ready',answer='',bootstrap_failure=False):
        with tempfile.TemporaryDirectory() as folder:
            root=Path(folder)/'checkout with spaces';root.mkdir()
            (root/'scripts/container').mkdir(parents=True)
            (root/'bin').mkdir()
            shutil.copyfile(ROOT/'dof.sh',root/'dof.sh')
            for program in ('bash','dirname'):
                (root/'bin'/program).symlink_to(shutil.which(program))
            stub=root/'python-stub'
            stub.write_text('#!'+sys.executable+'\n'+'''import json,os,sys
from pathlib import Path
root=Path(os.environ['TEST_ROOT'])
if sys.argv[1:2]==['-c']:
    sys.exit(0 if os.environ['TEST_PYTHON']=='ready' or (root/'installed').exists() else 1)
(root/'called.json').write_text(json.dumps(dict(args=sys.argv[1:],cwd=os.getcwd())))
''')
            stub.chmod(0o700)
            if python!='missing':shutil.copyfile(stub,root/'bin/python3');(root/'bin/python3').chmod(0o700)
            bootstrap=root/'scripts/container/bootstrap.sh'
            bootstrap.write_text('''#!/bin/bash
printf '%s' "$1" > "$TEST_ROOT/bootstrap-called"
if [[ $TEST_FAIL == 1 ]]; then exit 7; fi
"$TEST_REAL_PYTHON" - <<'PY'
import os,shutil
from pathlib import Path
root=Path(os.environ['TEST_ROOT'])
shutil.copyfile(root/'python-stub',root/'bin/python3')
(root/'bin/python3').chmod(0o700)
(root/'installed').touch()
PY
''')
            env=dict(os.environ,PATH=str(root/'bin'),TEST_ROOT=str(root),TEST_PYTHON=python,
                     TEST_REAL_PYTHON=sys.executable,TEST_FAIL='1' if bootstrap_failure else '0')
            result=subprocess.run([shutil.which('bash'),str(root/'dof.sh'),*args],
                cwd=folder,env=env,input=answer,text=True,capture_output=True,timeout=10)
            called=json.loads((root/'called.json').read_text()) if (root/'called.json').exists() else None
            boot=(root/'bootstrap-called').read_text() if (root/'bootstrap-called').exists() else None
            return result,called,boot,str(root)

    def test_no_arguments_open_menu_from_checkout(self):
        result,called,boot,root=self.invoke()
        self.assertEqual(result.returncode,0,result.stderr)
        self.assertEqual(called,dict(args=['-m','scripts.container.menu'],cwd=root))
        self.assertIsNone(boot)

    def test_arguments_are_forwarded_exactly_to_manager(self):
        args=['install','--pvf','/local/a file.pvf','--instance','sample','--tag','v1']
        result,called,boot,root=self.invoke(args)
        self.assertEqual(result.returncode,0,result.stderr)
        self.assertEqual(called,dict(args=['scripts/container/manage.py',*args],cwd=root))
        self.assertIsNone(boot)

    def test_missing_and_old_python_can_bootstrap_and_resume(self):
        for version in ('missing','old'):
            with self.subTest(version=version):
                result,called,boot,_=self.invoke(['status'],python=version,answer='y\n')
                self.assertEqual(result.returncode,0,result.stderr)
                self.assertEqual(boot,'--python')
                self.assertEqual(called['args'],['scripts/container/manage.py','status'])

    def test_cancel_menu_has_no_bootstrap_or_manager_effect(self):
        result,called,boot,_=self.invoke(python='missing',answer='n\n')
        self.assertEqual(result.returncode,0)
        self.assertIsNone(called);self.assertIsNone(boot)

    def test_cli_cancel_and_unattended_input_fail_without_installing(self):
        for answer in ('n\n',''):
            result,called,boot,_=self.invoke(['status'],python='missing',answer=answer)
            self.assertNotEqual(result.returncode,0)
            self.assertIsNone(called);self.assertIsNone(boot)

    def test_bootstrap_failure_never_dispatches(self):
        result,called,boot,_=self.invoke(python='missing',answer='y\n',bootstrap_failure=True)
        self.assertEqual(result.returncode,7)
        self.assertIsNone(called);self.assertEqual(boot,'--python')

if __name__=='__main__':unittest.main()
