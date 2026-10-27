import copy
import hashlib
import importlib.util
from pathlib import Path
import unittest

ROOT = Path(__file__).resolve().parents[3]
def module(name):
    spec=importlib.util.spec_from_file_location(name, ROOT/'scripts/dof'/ (name+'.py'))
    value=importlib.util.module_from_spec(spec);spec.loader.exec_module(value);return value
proxy=module('rollback_receipts_proxy');guard=module('release_rollback_proxy')

class FrozenReceiptsProxyTests(unittest.TestCase):
    def setup_values(self):
        raw=(ROOT/'tests/unit/dof/fixtures/nginx-admin-s29.conf.template').read_bytes()
        source=guard.render(raw,hashlib.sha256(raw).hexdigest())
        routes={}
        for path in [proxy.BASE,proxy.BASE+'/00000000-0000-4000-8000-000000000001']:
            routes[path]={'file':hashlib.sha256(path.encode()).hexdigest()+'.json','sha256':'a'*64}
        return source,dict(schema=1,module='cross-border',instanceId='dof-main',databaseUuid='899d147e-d0ef-11f1-9ff4-525400542f67',sourceCommit='b'*40,routes=routes)
    def test_every_exact_read_requires_live_backend_admin_auth_and_no_store(self):
        source,manifest=self.setup_values();result=proxy.render(source,manifest).decode()
        self.assertEqual(result.count('auth_request /__dof_rollback_receipts_auth;'),2)
        self.assertIn('internal;',result);self.assertIn('proxy_set_header Authorization $http_authorization;',result)
        self.assertIn('/api/v1/admin/dof;',result);self.assertIn('if ($rollback_write_blocked) { return 503; }',result)
        self.assertEqual(result.count('if ($request_method !~ ^(GET|HEAD)$) { return 503; }'),2)
        self.assertEqual(result.count('Cache-Control "no-store"'),2)
        self.assertNotIn('/app/static/rollback',result)
    def test_arbitrary_paths_credentials_routes_and_file_traversal_are_rejected(self):
        source,manifest=self.setup_values()
        for path in ['/api/v1/login',proxy.BASE+'/../credentials',proxy.BASE+'/not-an-id',proxy.BASE+'/00000000-0000-4000-8000-000000000001/restore']:
            changed=copy.deepcopy(manifest);changed['routes'][path]={'file':hashlib.sha256(path.encode()).hexdigest()+'.json','sha256':'a'*64}
            with self.assertRaises(ValueError):proxy.render(source,changed)
        changed=copy.deepcopy(manifest);changed['routes'][proxy.BASE]['file']='../secret.json'
        with self.assertRaises(ValueError):proxy.render(source,changed)
    def test_only_historical_cancellation_reads_extend_a_receipt_route(self):
        source,manifest=self.setup_values();route=proxy.BASE+'/00000000-0000-4000-8000-000000000001/cancellations'
        manifest['routes'][route]={'file':hashlib.sha256(route.encode()).hexdigest()+'.json','sha256':'c'*64}
        self.assertIn('location = '+route,proxy.render(source,manifest).decode())
        current=proxy.BASE+'/00000000-0000-4000-8000-000000000001/current'
        manifest['routes'][current]={'file':hashlib.sha256(current.encode()).hexdigest()+'.json','sha256':'d'*64}
        with self.assertRaises(ValueError):proxy.render(source,manifest)
    def test_wrong_binding_unpinned_proxy_and_incomplete_snapshot_fail_closed(self):
        source,manifest=self.setup_values()
        with self.assertRaises(ValueError):proxy.render(source+b'\n',manifest)
        for key,value in [('instanceId','other'),('module','arbitrary'),('sourceCommit','unknown'),('databaseUuid','not-a-uuid'),('routes',{})]:
            changed=copy.deepcopy(manifest);changed[key]=value
            with self.assertRaises(ValueError):proxy.render(source,changed)

if __name__=='__main__':unittest.main()
