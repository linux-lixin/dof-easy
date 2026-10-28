import copy
import hashlib
import importlib.util
from pathlib import Path
import unittest

ROOT = Path(__file__).resolve().parents[3]
def module(name):
    spec=importlib.util.spec_from_file_location(name,ROOT/'scripts/dof'/(name+'.py'))
    value=importlib.util.module_from_spec(spec);spec.loader.exec_module(value);return value
proxy=module('rollback_equipment_batches_proxy');guard=module('release_rollback_proxy')
BASE='/api/v1/admin/activities/inheritance/batches'
TASK=BASE+'/00000000-0000-4000-8000-000000000001'
class EquipmentBatchRollbackTests(unittest.TestCase):
    def values(self):
        raw=(ROOT/'tests/unit/dof/fixtures/nginx-admin-s29.conf.template').read_bytes()
        source=guard.render(raw,hashlib.sha256(raw).hexdigest());routes={}
        for url in [BASE,TASK,TASK+'?page=1&pageSize=20',TASK+'?page=2&pageSize=20',TASK+'/controls',TASK+'/controls/00000000-0000-4000-8000-000000000002']:
            routes[url]={'file':hashlib.sha256(url.encode()).hexdigest()+'.json','sha256':'a'*64}
        return source,dict(schema=1,module='equipment-batches',instanceId='dof-main',databaseUuid='899d147e-d0ef-11f1-9ff4-525400542f67',sourceCommit='b'*40,routes=routes)
    def test_every_exact_history_path_rechecks_live_backend_admin_and_blocks_writes(self):
        source,manifest=self.values();text=proxy.render(source,manifest).decode()
        self.assertEqual(text.count('auth_request /__dof_batch_rollback_auth;'),4)
        self.assertIn('internal;',text);self.assertIn('proxy_set_header Authorization $http_authorization;',text)
        self.assertIn('if ($rollback_write_blocked) { return 503; }',text)
        self.assertEqual(text.count('if ($request_method !~ ^(GET|HEAD)$)'),4)
        self.assertEqual(text.count('Cache-Control "no-store"'),4)
    def test_pages_are_distinct_fixed_files_and_unsnapshotted_queries_are_refused(self):
        source,manifest=self.values();text=proxy.render(source,manifest).decode()
        for url,record in manifest['routes'].items():self.assertIn(record['file'],text)
        self.assertIn('map "$uri?$args"',text);self.assertIn('if ($frozen_batch_file = "") { return 400; }',text)
        bucket=int(text.split('map_hash_bucket_size ',1)[1].split(';',1)[0])
        # Leave room for Nginx's key length, value pointer and alignment.
        self.assertTrue(all(len((url+'?').encode())+32<bucket for url in manifest['routes']))
        self.assertNotEqual(manifest['routes'][TASK+'?page=1&pageSize=20']['file'],manifest['routes'][TASK+'?page=2&pageSize=20']['file'])
    def test_live_state_child_current_arbitrary_paths_and_query_injection_never_freeze(self):
        source,manifest=self.values()
        for url in [BASE+'/state',TASK+'/step',TASK+'/items/00000000-0000-4000-8000-000000000002',TASK+'?pageSize=100&page=1',TASK+'?page=1&pageSize=20&sql=bad',TASK+'?page=0&pageSize=20',TASK+'#fragment','https://example.invalid'+TASK,TASK+'?',TASK+'/controls/00000000-0000-4000-8000-000000000002?page=1&pageSize=20']:
            changed=copy.deepcopy(manifest);changed['routes'][url]={'file':hashlib.sha256(url.encode()).hexdigest()+'.json','sha256':'a'*64}
            with self.assertRaises(ValueError,msg=url):proxy.render(source,changed)
    def test_unpinned_source_wrong_identity_traversal_and_missing_catalog_fail_closed(self):
        source,manifest=self.values()
        with self.assertRaises(ValueError):proxy.render(source+b'\n',manifest)
        for field,value in [('instanceId','other'),('module','other'),('sourceCommit','unknown')]:
            changed=copy.deepcopy(manifest);changed[field]=value
            with self.assertRaises(ValueError):proxy.render(source,changed)
        changed=copy.deepcopy(manifest);changed['routes'][BASE]['file']='../private.json'
        with self.assertRaises(ValueError):proxy.render(source,changed)
        changed=copy.deepcopy(manifest);del changed['routes'][BASE]
        with self.assertRaises(ValueError):proxy.render(source,changed)
if __name__=='__main__':unittest.main()
