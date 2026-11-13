"""Resolve the maintained Qingkong Compose template without starting services."""
import json
import os
from pathlib import Path
import subprocess
import tempfile
import unittest

ROOT=Path(__file__).resolve().parents[2]

class DeploymentLayout(unittest.TestCase):
    def test_resolved_services_and_identity_mounts(self):
        # No operator environment or real database credentials enter this check.
        env={k:v for k,v in os.environ.items() if k in ('PATH','HOME','DOCKER_CONFIG')}
        env.update(PUBLIC_IP='203.0.113.10',DOF_DB_HOST='10.0.0.10',
                   GAME_DB_PASSWORD='example-game-password',SUPERVISOR_PASSWORD='example-supervisor-password',
                   DNF_MAX_LEVEL='86',DNF_GAME_SHA256='0'*64)
        with tempfile.TemporaryDirectory() as directory:
            env['RUNTIME_ROOT']=directory
            result=subprocess.run(['docker','compose','--env-file','/dev/null','-f',str(ROOT/'compose.yaml'),
                                   'config','--format','json'],env=env,cwd=ROOT,capture_output=True,text=True)
            self.assertEqual(result.returncode,0,result.stderr)
            model=json.loads(result.stdout)
            self.assertEqual(set(model['services']),{'game','keys'})
            game=model['services']['game']; keys=model['services']['keys']
            self.assertEqual(game['image'],keys['image'])
            self.assertEqual(game['environment']['LAUNCHER_MODE'],'qingkong')
            self.assertEqual(game['environment']['DOF_DATABASE_KIND'],'external')
            self.assertEqual(game['environment']['PLUGIN_MODE'],'none')
            self.assertEqual(keys['network_mode'],'none')
            mounts={v['target']:v['source'] for v in keys['volumes']}
            self.assertEqual(Path(mounts['/keys']),Path(directory)/'admin/data')
            self.assertEqual(Path(mounts['/game']),Path(directory)/'game/data')
            for service in model['services'].values():
                self.assertFalse(service.get('privileged',False))
                self.assertNotIn('NET_ADMIN',service.get('cap_add',[]))

    def test_document_links_resolve(self):
        result=subprocess.run([os.sys.executable,str(ROOT/'scripts/diagnostics/check_docs.py')],
                              cwd=ROOT,capture_output=True,text=True)
        self.assertEqual(result.returncode,0,result.stdout+result.stderr)

if __name__=='__main__': unittest.main()
