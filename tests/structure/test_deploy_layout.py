"""Validate actual Compose resolution after directory moves, without starting services."""
import json
import os
from pathlib import Path
import re
import subprocess
import tempfile
import unittest

ROOT=Path(__file__).resolve().parents[2]
DEPLOY=ROOT/'deploy'

def config(folder, overrides=None):
    # Do not accidentally use real shell credentials while checking example files.
    variables=set()
    for p in DEPLOY.rglob('*.yaml'):
        variables.update(re.findall(r'\$\{([A-Z_][A-Z_0-9]*)',p.read_text()))
    env={k:v for k,v in os.environ.items() if k not in variables}
    env.update(overrides or {})
    result=subprocess.run(['docker','compose','--env-file',str(folder/'.env.example'),
                           '-f',str(folder/'compose.yaml'),'config','--format','json'],
                          cwd=ROOT,env=env,capture_output=True,text=True,encoding='utf8')
    if result.returncode: raise AssertionError(result.stderr)
    return json.loads(result.stdout)

class DeploymentLayout(unittest.TestCase):
    def test_all_leaf_composes_have_docs_and_resolve_local_mounts(self):
        files=list(DEPLOY.rglob('compose.yaml'))
        self.assertEqual(len(files),5)
        for file in files:
            with self.subTest(file=file.relative_to(ROOT)):
                self.assertTrue((file.parent/'README.md').is_file())
                self.assertTrue((file.parent/'.env.example').is_file())
                model=config(file.parent)
                for name,service in model['services'].items():
                    self.assertNotIn('platform',service)
                    self.assertFalse(service.get('privileged',False))
                    self.assertNotIn('NET_ADMIN',service.get('cap_add',[]))
                    self.assertFalse(any('/dev/net/tun' in str(d) for d in service.get('devices',[])))
                    for mount in service.get('volumes',[]):
                        if mount['type']=='bind':
                            source=Path(mount['source']).resolve()
                            self.assertEqual(os.path.commonpath((file.parent.resolve(),source)),str(file.parent.resolve()),
                                             f'{name}: mount escaped its deployment leaf: {source}')

    def test_ubuntu_roles_and_existing_project_names(self):
        for subdir,service,role,project in (
            ('all','game','ALL','dnf-ubuntu-all'),
            ('split/core','core','CORE','dnf-ubuntu-core'),
            ('split/game','game','GAME','dnf-ubuntu-game'),
            ('split/p2p','p2p','P2P','dnf-ubuntu-p2p')):
            with self.subTest(role=role):
                folder=DEPLOY/'ubuntu'/subdir
                model=config(folder)
                self.assertEqual(model['name'],project)
                spec=model['services'][service]
                self.assertEqual(spec['environment']['SERVER_TYPE'],role)
                mount=next(v for v in spec['volumes'] if v['target']=='/data')
                self.assertEqual(Path(mount['source']).resolve(),(folder/'runtime'/service/'data').resolve())
                if role=='P2P':
                    self.assertFalse(any('MYSQL' in k or 'PASSWORD' in k for k in spec['environment']))

    def test_runtime_override_reuses_existing_data(self):
        with tempfile.TemporaryDirectory() as old:
            for relative in ('ubuntu/all','ubuntu/split/game','ubuntu/split/core','ubuntu/split/p2p','ubuntu/split/admin'):
                model=config(DEPLOY/relative,{'RUNTIME_ROOT':Path(old).as_posix()})
                for service in model['services'].values():
                    for mount in service.get('volumes',[]):
                        if mount['target'] in ('/data','/home/neople/game/log','/app/data'):
                            self.assertEqual(os.path.commonpath((Path(old).resolve(),Path(mount['source']).resolve())),str(Path(old).resolve()))

    def test_independent_admin_requires_no_game_configuration(self):
        for platform in ('ubuntu',):
            model=config(DEPLOY/platform/'split/admin')
            self.assertEqual(set(model['services']),{'admin'})
            admin=model['services']['admin']
            self.assertNotIn('depends_on',admin)
            self.assertEqual(admin['environment']['SPRING_DATASOURCE_USERNAME'],'root')

    def test_document_links_resolve(self):
        docs=[ROOT/'README.md',ROOT/'CONTRIBUTING.md',ROOT/'tests/README.md',
              *DEPLOY.rglob('*.md'),*(ROOT/'docs').rglob('*.md'),
              *(ROOT/'services').rglob('README.md'),*(ROOT/'clients').rglob('README.md'),
              *(ROOT/'metadata').glob('*.md'),*(ROOT/'scripts').rglob('README.md')]
        for file in docs:
            for target in re.findall(r'\]\(([^)]+)\)',file.read_text(encoding='utf8')):
                if re.match(r'\w+://|#|mailto:',target) or target.startswith('/'): continue
                path=target.partition('#')[0]
                self.assertTrue((file.parent/path).exists(),f'{file.relative_to(ROOT)}: {target}')

if __name__=='__main__': unittest.main()
