"""Host mutation tests use temporary files and a simulated kernel, never host services."""
import json
import os
from pathlib import Path
import tempfile
import unittest
from unittest.mock import patch
from scripts.container import host


class HostPreparation(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory(); self.addCleanup(self.temp.cleanup)
        self.root = Path(self.temp.name)
        for name, path in [('STATE',self.root/'state'),('SWAP',self.root/'swap'),('FSTAB',self.root/'fstab')]:
            mock = patch.object(host,name,path); mock.start(); self.addCleanup(mock.stop)
        host.FSTAB.write_text('/existing none swap sw 0 0\n# operator entry\n')
        self.active = {'/existing'}
        for name,value in [('active_swap',lambda:self.active.copy()),('memory',lambda:{'SwapTotal':sum(1024**2 for x in self.active if x != '/existing')}),('system_check',lambda:None),('disk_check',lambda x:None)]:
            mock=patch.object(host,name,side_effect=value);mock.start();self.addCleanup(mock.stop)
        mock=patch.object(host.os,'geteuid',return_value=0);mock.start();self.addCleanup(mock.stop)
        self.commands=[]
        self.commandner=patch.object(host,'run',side_effect=self.command);self.commandner.start();self.addCleanup(self.commandner.stop)

    def command(self,args,**kwargs):
        args=list(map(str,args));self.commands.append(args)
        if args[0]=='findmnt':return 'ext4'
        if args[0]=='swapon':self.active.add(args[1])
        if args[0]=='swapoff':self.active.discard(args[1])
        return ''

    def test_memory_policy_and_existing_swap(self):
        for gib,target in [(1,8),(2,8),(3,8),(4,4),(7,4),(8,2),(16,2)]:
            self.assertEqual(host.recommended_swap(gib*host.GIB),target*host.GIB)
        self.assertEqual(host.recommended_swap(host.MIN_RAM),8*host.GIB)
        with self.assertRaises(ValueError):host.recommended_swap(host.MIN_RAM-1)
        with self.assertRaises(ValueError):host.recommended_swap(host.GIB//2)
        self.assertEqual(host.swap_missing(2*host.GIB,3*host.GIB),0)
        self.assertEqual(host.disk_budget(False,100,200),12*host.GIB+500)

    def test_swap_persist_idempotent_remove_preserves_foreign(self):
        host.create_swap(1024**2)
        entry=next(iter(host.swap_entries()))
        self.assertEqual(Path(entry).stat().st_mode & 0o777,0o600)
        before=host.FSTAB.read_text()
        host.create_swap(1024**2)
        self.assertEqual(host.FSTAB.read_text(),before)
        self.assertEqual(sum(x[0]=='dd' for x in self.commands),1)
        host.remove_swap(entry)
        self.assertEqual(host.FSTAB.read_text(),'/existing none swap sw 0 0\n# operator entry\n')
        self.assertIn('/existing',self.active)
        self.assertFalse(Path(entry).exists())

    def test_failed_swapon_cleans_only_owned_inactive_file(self):
        original=self.command
        def fail(args,**kwargs):
            if args[0]=='swapon':raise ValueError('injected failure')
            return original(args,**kwargs)
        with patch.object(host,'run',side_effect=fail),self.assertRaises(ValueError):host.create_swap(1024**2)
        self.assertFalse(host.swap_entries());self.assertEqual(list(host.SWAP.iterdir()),[])
        self.assertIn('/existing',host.FSTAB.read_text())

    def test_fstab_failure_keeps_active_file_and_resume_repairs(self):
        with patch.object(host,'persist_swap',side_effect=OSError('full')),self.assertRaises(OSError):host.create_swap(1024**2)
        entry=next(iter(host.swap_entries()))
        self.assertTrue(Path(entry).exists());self.assertIn(entry,self.active)
        host.create_swap(1024**2)
        self.assertEqual(host.swap_entries()[entry],'ready')
        self.assertIn(entry,host.FSTAB.read_text())

    def test_swapoff_failure_preserves_file_and_fstab(self):
        host.create_swap(1024**2);entry=next(iter(host.swap_entries()));before=host.FSTAB.read_text()
        with patch.object(host,'run',side_effect=ValueError('busy')),self.assertRaises(ValueError):host.remove_swap(entry)
        self.assertTrue(Path(entry).exists());self.assertEqual(host.FSTAB.read_text(),before)

    def test_unowned_swap_and_symlink_rejected(self):
        with self.assertRaises(ValueError):host.remove_swap('/existing')
        host.SWAP.mkdir();link=host.SWAP/'link';link.symlink_to(host.FSTAB)
        host.save(host.STATE/'swap.json',{str(link):'ready'})
        with self.assertRaises(ValueError):host.remove_swap(str(link))

    def test_unsupported_filesystem_and_space_never_allocate(self):
        with patch.object(host,'run',return_value='btrfs'),self.assertRaises(ValueError):host.create_swap(1024**2)
        self.assertEqual(list(host.SWAP.iterdir()),[])
        with patch.object(host,'disk_check',side_effect=ValueError('space')),self.assertRaises(ValueError):host.create_swap(1024**2)
        self.assertFalse(any(x[0]=='dd' for x in self.commands))

    def test_public_mysql_blocks_firewall_before_mutation(self):
        with patch.object(host,'public_database_listener',return_value=True),self.assertRaises(ValueError):host.firewall()
        self.assertFalse((host.STATE/'firewall.json').exists())

    def test_firewall_close_and_restore_records_before_state(self):
        state={'ufw':{'active':True}}
        def command(args,**kwargs):
            self.commands.append(args)
            if args==['ufw','disable']:state['ufw']['active']=False
            if args==['ufw','--force','enable']:state['ufw']['active']=True
            return ''
        with patch.object(host,'public_database_listener',return_value=False), \
             patch.object(host,'firewall_status',side_effect=lambda:json.loads(json.dumps(state))), \
             patch.object(host,'docker_rules',return_value={'iptables-save':[':DOCKER']}), \
             patch.object(host,'run',side_effect=command),patch.object(host.shutil,'copytree'):
            host.firewall();self.assertFalse(state['ufw']['active'])
            host.firewall(False);self.assertTrue(state['ufw']['active'])
            host.firewall(False)
        self.assertEqual(json.loads((host.STATE/'firewall.json').read_text())['phase'],'restored')
        self.assertEqual(self.commands.count(['ufw','--force','enable']),1)

    def test_docker_rule_change_restores_and_does_not_claim_success(self):
        with patch.object(host,'public_database_listener',return_value=False), \
             patch.object(host,'firewall_status',side_effect=[{'ufw':{'active':True}},{'ufw':{'active':False}}]), \
             patch.object(host,'docker_rules',side_effect=[{'rules':['DOCKER']},{'rules':[]}]), \
             patch.object(host,'restore_firewall_state') as restore,patch.object(host.shutil,'copytree'),self.assertRaises(ValueError):
            host.firewall()
        restore.assert_called_once()
        self.assertEqual(json.loads((host.STATE/'firewall.json').read_text())['phase'],'changing')

    def test_inactive_but_enabled_firewalld_is_disabled_and_restored(self):
        state={'firewalld':{'active':False,'enabled':'enabled'}}
        def command(args,**kwargs):
            self.commands.append(args)
            if args==['systemctl','disable','--now','firewalld']:state['firewalld']['enabled']='disabled'
            if args==['systemctl','enable','firewalld']:state['firewalld']['enabled']='enabled'
            return ''
        with patch.object(host,'public_database_listener',return_value=False), \
             patch.object(host,'firewall_status',side_effect=lambda:json.loads(json.dumps(state))), \
             patch.object(host,'docker_rules',return_value={}),patch.object(host,'run',side_effect=command),patch.object(host.shutil,'copytree'):
            host.firewall();self.assertEqual(state['firewalld']['enabled'],'disabled')
            host.firewall(False);self.assertEqual(state['firewalld']['enabled'],'enabled')
            self.assertNotIn(['systemctl','start','firewalld'],self.commands)

    def test_firewalld_with_foreign_rules_refuses_before_disable(self):
        with patch.object(host,'public_database_listener',return_value=False), \
             patch.object(host,'firewall_status',return_value={'firewalld':{'active':True,'enabled':'enabled'}}), \
             patch.object(host.shutil,'which',return_value='/iptables-save'), \
             patch.object(host,'run',return_value='*filter\n:DOCKER - [0:0]\nCOMMIT') as run,self.assertRaises(ValueError):
            host.firewall()
        self.assertFalse((host.STATE/'firewall.json').exists())
        self.assertTrue(all(c.args[0][0]=='iptables-save' for c in run.call_args_list))

    def test_firewalld_restore_guard_precedes_enablement(self):
        with patch.object(host,'firewalld_legacy_guard',side_effect=ValueError('foreign rules')), self.assertRaises(ValueError):
            host.restore_firewall_state({'firewalld':{'active':True,'enabled':'enabled'}})
        self.assertEqual(self.commands,[['systemctl','is-active','firewalld']])

    def test_multiple_firewalls_no_mutation(self):
        with patch.object(host,'public_database_listener',return_value=False),patch.object(host,'firewall_status',return_value={'ufw':{'active':True},'firewalld':{'active':True}}),self.assertRaises(ValueError):host.firewall()
        self.assertFalse((host.STATE/'firewall.json').exists())

    def test_remote_docker_environment_and_context_rejected(self):
        with patch.dict(os.environ,{'DOCKER_HOST':'ssh://unrelated'}),self.assertRaises(ValueError):host.local_docker()
        with patch.dict(os.environ,{},clear=True),patch.object(host.shutil,'which',return_value='/docker'),patch.object(host,'run',return_value=json.dumps([{'Name':'remote','Endpoints':{'docker':{'Host':'tcp://example:2376'}}}])),self.assertRaises(ValueError):host.local_docker()

    def test_mysql_listener_detection(self):
        for address,blocked in [('0.0.0.0:3306',True),('[::]:3306',True),('203.0.113.4:3306',False),('172.17.0.1:3306',False),('127.0.0.1:3306',False),('*:13306',True)]:
            # Python marks documentation ranges private; public real address tested separately.
            with patch.object(host,'run',return_value=f'LISTEN 0 80 {address} *:* users:(("mysqld",pid=1,fd=2))'):
                self.assertEqual(host.public_database_listener(),blocked)
        with patch.object(host,'run',return_value='LISTEN 0 80 8.8.8.8:3306 *:*'):
            self.assertTrue(host.public_database_listener())


if __name__=='__main__':unittest.main()
