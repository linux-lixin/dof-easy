"""Check moved build inputs and provenance without downloading or starting services."""
import glob
import hashlib
import json
from pathlib import Path
import shlex
import subprocess
import unittest
import xml.etree.ElementTree as ET

ROOT=Path(__file__).resolve().parents[2]

class RepositoryLayout(unittest.TestCase):
    def test_retired_runtime_components_are_absent(self):
        retired=('netbird','tailscale','tailscaled','DnfGateServer','GateRestart','GateStop',
                 'init_local_db.sh','gate.conf.template','netbird.conf','tailscale.conf',
                 'openssl-0.9.8zh.tar.gz','libmysqlclient.so.15.0.0')
        for folder in ('services/game/assets','services/game/runtime','services/game/docker','deploy'):
            for path in (ROOT/folder).rglob('*'):
                if path.is_file():
                    self.assertNotIn(path.name,retired,str(path))
                    self.assertNotIn('centos',path.relative_to(ROOT).parts,str(path))
        self.assertFalse((ROOT/'services/game/assets/etc/my.cnf').exists())
        self.assertFalse((ROOT/'services/game/assets/docker-entrypoint.sh').exists())

    def test_locked_game_resources_match(self):
        records=json.loads((ROOT/'metadata/game-assets.lock.json').read_text(encoding='utf8'))['assets']
        self.assertEqual(len(records),394)
        for record in records:
            with self.subTest(path=record['path']):
                path=ROOT/record['path']
                self.assertTrue(path.is_file())
                data=path.read_bytes()
                self.assertEqual(len(data),record['bytes'])
                self.assertEqual(hashlib.sha256(data).hexdigest(),record['sha256'])

    def test_compatibility_resources_match(self):
        folder=ROOT/'services/game/docker/ubuntu'
        records=json.loads((folder/'compat-lock.json').read_text(encoding='utf8'))['resources']
        for record in records:
            if 'file' in record and 'sha256' in record:
                with self.subTest(file=record['file']):
                    self.assertEqual(hashlib.sha256((folder/record['file']).read_bytes()).hexdigest(),record['sha256'])

    def test_docker_copy_inputs_exist_inside_correct_context(self):
        for context,dockerfile in [('services/game','docker/ubuntu/Dockerfile'),
                                   ('services/admin','docker/Dockerfile')]:
            folder=ROOT/context
            for line in (folder/dockerfile).read_text(encoding='utf8').splitlines():
                if not line.startswith(('COPY ','ADD ')) or '--from=' in line: continue
                for source in shlex.split(line)[1:-1]:
                    self.assertFalse(source.startswith('/'),source)
                    self.assertNotIn('..',Path(source).parts)
                    self.assertTrue(glob.glob(str(folder/source)),f'{context}/{dockerfile}: missing {source}')

    def test_source_components_and_solution_references_exist(self):
        origins=json.loads((ROOT/'metadata/source-lock.json').read_text(encoding='utf8'))['repositories']
        self.assertEqual({r['directory'] for r in origins},{'services/game','services/admin','clients/launcher'})
        for origin in origins: self.assertTrue((ROOT/origin['directory']).is_dir())
        launcher=ROOT/'clients/launcher'
        for project in ET.parse(launcher/'DNFLogin.slnx').getroot().findall('Project'):
            self.assertTrue((launcher/project.attrib['Path']).is_file())
        for resource in ET.parse(launcher/'DNFLogin.csproj').iter('Resource'):
            self.assertTrue((launcher/resource.attrib['Include'].replace('\\','/')).is_file())
        self.assertTrue((ROOT/'services/admin/backend/pom.xml').is_file())
        self.assertTrue((ROOT/'services/admin/frontend/package-lock.json').is_file())

    def test_runtime_source_and_compat_archives_are_not_ignored(self):
        paths=['services/game/runtime/ubuntu/entrypoint.sh','services/game/runtime/ubuntu/health.py',
               'services/game/docker/ubuntu/geoip_1.6.12.orig.tar.gz']
        result=subprocess.run(['git','check-ignore','--no-index',*paths],cwd=ROOT,capture_output=True,text=True)
        self.assertEqual(result.returncode,1,result.stdout+result.stderr)

if __name__=='__main__': unittest.main()
