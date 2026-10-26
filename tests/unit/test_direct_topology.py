"""Direct-port cutover preserves databases and targets exact Compose ownership."""
import importlib.util
from contextlib import contextmanager
import json
from pathlib import Path
import subprocess
import tempfile
import unittest
from unittest.mock import patch

ROOT=Path(__file__).resolve().parents[2]
spec=importlib.util.spec_from_file_location('direct_manager',ROOT/'scripts/container/manage.py')
m=importlib.util.module_from_spec(spec);spec.loader.exec_module(m)


class DirectTopology(unittest.TestCase):
    def container(self,service,project='dof-main',running=True):
        return dict(Id=service+'-id',Image='sha256:test',State=dict(Running=running),
                    Config=dict(Labels={'com.docker.compose.project':project,'com.docker.compose.service':service}))

    def exercise(self,root,items,fail_stop=False,fail_config=False):
        commands=[]
        def run(command,**kwargs):
            commands.append(command)
            if command[2]=='ls': out='\n'.join(item['Id'] for item in items)
            elif command[2]=='inspect': out=json.dumps(items)
            elif command[2]=='stop' and fail_stop: raise subprocess.CalledProcessError(1,command)
            else: out=''
            return subprocess.CompletedProcess(command,0,out)
        def compose(root,*args):
            commands.append(['compose',*args])
            if fail_config:raise subprocess.CalledProcessError(1,['compose',*args])
        @contextmanager
        def stub():
            with patch.object(m,'run',side_effect=run),patch.object(m,'compose',side_effect=compose):
                yield
        return commands,stub()

    def test_cutover_stops_only_writers_removes_only_helpers_and_keeps_files(self):
        with tempfile.TemporaryDirectory() as folder:
            root=Path(folder)/'main';root.mkdir()
            for name in ('.env','channels.json','deployment.json'):(root/name).write_text('private fixture')
            (root/'player-data').write_text('keep')
            items=[self.container(s) for s in ('db','game','admin','keys','netguard','ingress','db-check')]
            commands,stub=self.exercise(root,items)
            with stub:m.retire_legacy_helpers(root)
            self.assertEqual(commands[0][3:6],['--all','--filter','label=com.docker.compose.project=dof-main'])
            self.assertEqual(commands[-2],['docker','container','stop','--time','30','game-id','admin-id'])
            self.assertEqual(commands[-1],['docker','container','rm','--force','netguard-id','ingress-id','db-check-id'])
            self.assertEqual((root/'player-data').read_text(),'keep')
            copies=list((root/'config-backups').glob('*/.env'))
            self.assertEqual(len(copies),1)
            self.assertEqual(copies[0].read_text(),'private fixture')
            self.assertEqual(copies[0].stat().st_mode & 0o777,0o600)
            self.assertEqual((root/'.env').read_text(),'private fixture')

    def test_repeated_start_without_helpers_is_read_only(self):
        for items in ([],[self.container('game'),self.container('admin')]):
            with tempfile.TemporaryDirectory() as folder:
                root=Path(folder)/'main';root.mkdir()
                commands,stub=self.exercise(root,items)
                with stub:m.retire_legacy_helpers(root)
                self.assertTrue(all(c[2] in ('ls','inspect') for c in commands))
                self.assertFalse((root/'config-backups').exists())

    def test_wrong_project_and_stop_failure_never_remove_containers(self):
        for wrong in (True,False):
            with tempfile.TemporaryDirectory() as folder:
                root=Path(folder)/'main';root.mkdir()
                items=[self.container('game'),self.container('netguard','dof-other' if wrong else 'dof-main')]
                commands,stub=self.exercise(root,items,fail_stop=not wrong)
                with stub,self.assertRaises((ValueError,subprocess.CalledProcessError)):
                    m.retire_legacy_helpers(root)
                self.assertFalse(any(c[2]=='rm' for c in commands))

    def test_external_health_no_longer_polls_mysql(self):
        runtime=ROOT/'services/game/runtime/ubuntu'
        config=m.module('direct_config','services/game/runtime/ubuntu/config.py')
        with patch.dict('sys.modules',{'config':config}):
            health=m.module('direct_health','services/game/runtime/ubuntu/health.py')
        env={'SERVER_TYPE':'ADMIN','DOF_DATABASE_KIND':'external'}
        with patch.dict('os.environ',env,clear=True),patch.object(health,'snapshot',return_value=([],{'tcp':set(),'udp':set()})),patch.object(health.subprocess,'run') as run:
            self.assertTrue(health.check())
            run.assert_not_called()

    def test_invalid_new_model_does_not_interrupt_existing_services(self):
        with tempfile.TemporaryDirectory() as folder:
            root=Path(folder)/'main';root.mkdir()
            commands,stub=self.exercise(root,[self.container('game'),self.container('netguard')],fail_config=True)
            with stub,self.assertRaises(subprocess.CalledProcessError):m.retire_legacy_helpers(root)
            self.assertFalse(any(c[:3] in (['docker','container','stop'],['docker','container','rm']) for c in commands))

    def test_stale_channel_model_is_replaced_before_compose_resolution(self):
        with tempfile.TemporaryDirectory() as folder:
            root=Path(folder)/'main';root.mkdir()
            (root/'channels.json').write_text('{"services":{"netguard":{"ports":[]}}}')
            values=dict(LAUNCHER_MODE='dof',PLUGIN_MODE='none',BIND_IP='127.0.0.1',OPEN_CHANNEL='15,52')
            with patch.object(m,'read_env',return_value=values),patch.object(m,'run') as run:
                m.compose(root,'ps')
            model=json.loads((root/'channels.json').read_text())
            self.assertEqual(set(model['services']),{'game'})
            run.assert_called_once()

if __name__=='__main__':unittest.main()
