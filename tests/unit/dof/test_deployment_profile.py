import importlib.util
import json
from pathlib import Path
import tempfile
import unittest
ROOT=Path(__file__).resolve().parents[3]
spec=importlib.util.spec_from_file_location('profile_loader',ROOT/'scripts/dof/deployment_profile.py')
profile=importlib.util.module_from_spec(spec);spec.loader.exec_module(profile)
class ProfileTests(unittest.TestCase):
 def valid(self):
  p=json.loads((ROOT/'scripts/dof/deployment-profile.example.json').read_text())
  p['databaseUuid']='12345678-1234-4234-8234-123456789abc';p['publicKeySha256']='1'*64
  return p
 def test_private_explicit_targets_and_binding(self):
  with tempfile.TemporaryDirectory()as folder:
   path=Path(folder)/'targets.json';path.write_text(json.dumps(self.valid()));path.chmod(0o600)
   p=profile.load(path);namespace={'ROOT':None,'UUID':None,'PROTECTED':None};profile.bind(namespace,p)
   self.assertIsInstance(namespace['ROOT'],Path);self.assertEqual(namespace['UUID'],p['databaseUuid'])
   path.chmod(0o644)
   with self.assertRaises(ValueError):profile.load(path)
 def test_unsafe_or_placeholder_profiles_rejected(self):
  edits=[{'environment':'production'},{'databaseUuid':'0'*8+'-0000-0000-0000-'+'0'*12},
         {'publicKeySha256':'0'*64},{'databaseClientHost':'%'},{'databaseClientHost':'8.8.8.%'},
         {'root':'/'},{'root':'/opt/../data'},{'databaseUser':"bad'user"},
         {'protectedContainers':['dof-isolated-web']},{'gameContainer':'dof-isolated-db'},{'password':'do-not-store'}]
  with tempfile.TemporaryDirectory()as folder:
   path=Path(folder)/'targets.json'
   for edit in edits:
    with self.subTest(fields=list(edit)):
     data=self.valid();data.update(edit);path.write_text(json.dumps(data));path.chmod(0o600)
     with self.assertRaises(ValueError):profile.load(path)
 def test_links_and_missing_profiles_rejected(self):
  with tempfile.TemporaryDirectory()as folder:
   target=Path(folder)/'target';target.write_text(json.dumps(self.valid()));target.chmod(0o600)
   link=Path(folder)/'link';link.symlink_to(target)
   for path in (link,Path(folder)/'missing'):
    with self.assertRaises(ValueError):profile.load(path)
if __name__=='__main__':unittest.main()
