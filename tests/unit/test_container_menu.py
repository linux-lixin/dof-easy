"""Menu inputs route to the real manager; tests never start containers."""
import contextlib
import io
from pathlib import Path
import tempfile
import unittest
from unittest.mock import patch, Mock
import ipaddress

from scripts.container import menu


class ContainerMenu(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory()
        self.addCleanup(self.temp.cleanup)
        self.root = Path(self.temp.name)
        self.pvf = self.root / 'a file.pvf'
        self.pvf.write_bytes(b'p' * 2048)
        self.state = patch.object(menu.manager, 'STATE', self.root / 'state')
        self.state.start(); self.addCleanup(self.state.stop)
        for name in ('system_check','local_docker','prepare_dependencies','disk_check','create_swap','firewall'):
            mock = patch.object(menu.host, name); mock.start(); self.addCleanup(mock.stop)
        mock = patch.object(menu.host,'memory',return_value={'MemTotal':8*menu.host.GIB,'MemAvailable':5*menu.host.GIB,'SwapTotal':0})
        mock.start(); self.addCleanup(mock.stop)
        mock = patch.object(menu.shutil,'which',return_value=None);mock.start();self.addCleanup(mock.stop)
        mock = patch.object(menu,'detect_public_ipv4',return_value='8.8.4.4');mock.start();self.addCleanup(mock.stop)
        for target, name, value in ((menu,'mysql_binary','/mysql'),(menu,'mysql_sockets',[]),
                                    (menu.host,'public_database_listener',False)):
            mock = patch.object(target,name,return_value=value);mock.start();self.addCleanup(mock.stop)


    def inputs(self, level='', database='1'):
        return [level, '192.0.2.1', str(self.pvf), '', '', '', database, '1', '', '', '0', '2']

    def test_public_ip_default_override_and_failure_reprompt(self):
        for detected, answers, expected in [
            ('8.8.4.4', [''], '8.8.4.4'),
            ('8.8.4.4', ['192.0.2.1'], '192.0.2.1'),
            ('', ['', '::1', 'bad', '192.0.2.1'], '192.0.2.1'),
        ]:
            with self.subTest(detected=detected, answers=answers), \
                 patch.object(menu,'detect_public_ipv4',return_value=detected), \
                 patch('builtins.input',side_effect=answers):
                self.assertEqual(menu.public_ipv4(), expected)

    def test_low_memory_build_continues_and_swap_precedes_build(self):
        for ram in (menu.host.MIN_RAM, menu.host.GIB, 2*menu.host.GIB, int(3.8*menu.host.GIB)):
            answers = self.inputs()
            answers[1] = ''  # Accept detected IP.
            answers[-2] = ''  # Accept recommended total Swap.
            events = []
            with self.subTest(ram=ram), patch.object(menu.host,'memory',return_value={
                    'MemTotal':ram,'MemAvailable':ram//2,'SwapTotal':0}), \
                 patch('builtins.input',side_effect=answers+['y']), \
                 patch.object(menu.host,'create_swap',side_effect=lambda total:events.append(('swap',total))), \
                 patch.object(menu.manager,'docker_check'), \
                 patch.object(menu,'assets_directory',return_value=Path('/verified')), \
                 patch.object(menu,'web_instructions'), \
                 patch.object(menu.manager,'main',side_effect=lambda args:events.append(('deploy',args))):
                menu.install('test')
            self.assertEqual(events[0],('swap',8*menu.host.GIB))
            self.assertIn('--build', events[1][1])
            args = events[1][1]
            self.assertEqual(args[args.index('--ip')+1], '8.8.4.4')

    def test_below_minimum_rejected_before_prompts_or_mutations(self):
        with patch.object(menu.host,'memory',return_value={
                'MemTotal':512*1024**2,'MemAvailable':256*1024**2,'SwapTotal':8*menu.host.GIB}), \
             patch('builtins.input') as prompts, patch.object(menu.manager,'main') as deploy:
            with self.assertRaisesRegex(ValueError,'1 GiB'):
                menu.install('test')
        prompts.assert_not_called(); deploy.assert_not_called()
        menu.host.prepare_dependencies.assert_not_called()

    def test_selected_level_and_empty_default_reach_install(self):
        for value, expected in [('', '86'), ('70', '70'), ('120', '120')]:
            with self.subTest(level=value), patch('builtins.input', side_effect=self.inputs(value)+['y']), \
                 patch.object(menu.manager, 'docker_check'), patch.object(menu, 'assets_directory', return_value=Path('/verified assets')), \
                 patch.object(menu, 'web_instructions'), patch.object(menu.manager, 'main') as run:
                menu.install('test')
                args=run.call_args.args[0]
                self.assertEqual(args[args.index('--level')+1], expected)
                self.assertEqual(args[args.index('--launcher')+1], 'native')
                self.assertEqual(args[args.index('--raid-extension')+1], 'off')
                self.assertEqual(args[args.index('--database')+1], 'internal')
                self.assertIn(str(self.pvf), args)
                self.assertIn('/verified assets', args)
                self.assertIn('--build', args)

    def test_no_assets_question_uses_checked_in_bundle(self):
        answers=self.inputs()
        with patch('builtins.input',side_effect=answers+['y']) as prompts, \
             patch.object(menu.manager,'docker_check'),patch.object(menu,'web_instructions'), \
             patch.object(menu.manager,'main') as run:
            menu.install('test')
        self.assertFalse(any('资源目录' in str(call) for call in prompts.call_args_list))
        args=run.call_args.args[0]
        self.assertEqual(Path(args[args.index('--dof-assets')+1]),menu.manager.ROOT/'services/game/resources/native')

    def test_invalid_level_reprompt_and_cancel_leave_host_unchanged(self):
        with patch('builtins.input',side_effect=['88']+self.inputs('86')+['n']), \
             patch.object(menu.manager,'main') as deploy,patch.object(menu,'assets_directory') as assets:
            menu.install('test')
        deploy.assert_not_called(); assets.assert_not_called()
        menu.host.prepare_dependencies.assert_not_called()

    def test_plugin_combinations_and_preserve_disabled_raid(self):
        for plugin in menu.manager.MODES:
            with patch('builtins.input',side_effect=[plugin]+(['off'] if plugin in ('dp2','dp2-frida') else [])):
                self.assertEqual(menu.plugin_selection(),(plugin,'off'))
        with patch('builtins.input',side_effect=['','']):
            self.assertEqual(menu.plugin_selection({'PLUGIN_MODE':'dp2','RAID_EXTENSION':'off'}),('dp2','off'))
        with patch('builtins.input',side_effect=['','']):
            self.assertEqual(menu.plugin_selection(),('dp2-frida','off'))
        with patch('builtins.input',side_effect=['','']):
            self.assertEqual(menu.plugin_selection({'PLUGIN_MODE':'dp2','RAID_EXTENSION':'on'}),('dp2','on'))
        with patch('builtins.input',side_effect=['','1']):
            self.assertEqual(menu.plugin_selection(),('dp2-frida','on'))

    def test_existing_instance_only_starts_without_install_prompts(self):
        (menu.manager.STATE/'instances/test').mkdir(parents=True)
        with patch('builtins.input') as inputs, patch.object(menu.manager, 'main') as run, patch.object(menu, 'web_instructions'):
            menu.install('test')
        inputs.assert_not_called(); run.assert_called_once_with(['start', '--instance', 'test'])

    def test_external_password_private_and_removed_on_success_or_failure(self):
        secret='demo!$`"\\password'
        for fail in (False, True):
            paths=[]
            def run(args):
                self.assertNotIn(secret, str(args))
                self.assertEqual(args[args.index('--database')+1], 'external')
                path=Path(args[args.index('--mysql-config')+1]); paths.append(path)
                self.assertEqual(path.stat().st_mode & 0o777, 0o600)
                content = path.read_text()
                self.assertIn('password='+menu.quoted_option(secret),content)
                self.assertIn('user="bootstrap_admin"',content)
                self.assertIn('protocol=TCP',content)
                if fail:raise ValueError('simulated deploy failure')
            with patch('builtins.input',side_effect=self.inputs(database='2')+['172.17.0.1','','bootstrap_admin','y']) as prompts, \
                 patch.object(menu.manager,'docker_check'), patch.object(menu,'inspect_network',return_value=(['172.17.0.1'],[])), \
                 patch.object(menu.manager.database,'host_query',return_value=Mock(return_value='0.0.0.0\t3306')), \
                 patch.object(menu.getpass,'getpass',return_value=secret), patch.object(menu,'check_external',return_value='172.17.0.1'), \
                 patch.object(menu,'assets_directory',return_value=Path('/verified')),patch.object(menu,'web_instructions'), \
                 patch.object(menu.manager,'main',side_effect=run):
                if fail:
                    with self.assertRaisesRegex(ValueError,'simulated'):menu.install('test')
                else:menu.install('test')
            self.assertEqual(len(paths),1)
            self.assertFalse(paths[0].exists());self.assertFalse(paths[0].parent.exists())
            self.assertFalse(any('socket' in str(call) or '客户端完整路径' in str(call) for call in prompts.call_args_list))

    def test_wildcard_mysql_respects_firewall_choice_and_deploys(self):
        for firewall_choice in ('1','2'):
            menu.host.firewall.reset_mock()
            answers=self.inputs(database='2'); answers[-1]=firewall_choice
            with self.subTest(firewall=firewall_choice), \
                 patch('builtins.input',side_effect=answers+['172.17.0.1','','','y']), \
                 patch.object(menu.getpass,'getpass',return_value='private'), \
                 patch.object(menu.manager.database,'host_query',return_value=Mock(return_value='0.0.0.0\t3306')), \
                 patch.object(menu,'check_external',return_value='0.0.0.0'), \
                 patch.object(menu,'inspect_network',return_value=(['172.17.0.1'],[])), \
                 patch.object(menu.manager,'docker_check'), \
                 patch.object(menu,'assets_directory',return_value=Path('/verified')), \
                 patch.object(menu,'web_instructions'),patch.object(menu.manager,'main') as deploy:
                menu.install('test')
            deploy.assert_called_once()
            if firewall_choice == '1':
                menu.host.firewall.assert_called_once_with(extra_ports=[3306],external_access_control=True)
            else:
                menu.host.firewall.assert_not_called()

    def test_option_file_rejects_newline_injection(self):
        with self.assertRaises(ValueError):menu.quoted_option('password\nuser=someone')
        self.assertEqual(menu.quoted_option('a"b\\c'), '"a\\"b\\\\c"')

    def test_subnet_skips_overlapping_routes_and_exhaustion_is_explicit(self):
        value=menu.free_subnet([ipaddress.ip_network('172.29.0.0/16')])
        self.assertTrue(value.startswith('10.253.'))
        with self.assertRaises(ValueError):menu.free_subnet([ipaddress.ip_network('0.0.0.0/0')])

    def test_external_preflight_is_read_only_and_rejects_loopback_binding(self):
        good=['5.6.51\t11111111-1111-1111-1111-111111111111','mysql','0','0.0.0.0\t3306\t67108864\tmode:']
        for last, should_fail in [(good[-1],False),('127.0.0.1\t3306\t67108864\tmode:',True),('0.0.0.0\t3306\t1024\tmode:',True)]:
            query=Mock(side_effect=good[:-1]+[last])
            with patch.object(menu.manager.database,'host_query',return_value=query):
                if should_fail:
                    with self.assertRaises(ValueError):menu.check_external('/mysql',Path('/private'),'172.17.0.1',3306)
                else:menu.check_external('/mysql',Path('/private'),'172.17.0.1',3306)
            self.assertTrue(all(call.args[0].startswith(('SELECT','SHOW')) for call in query.call_args_list))

    def test_menu_returns_after_operation_and_switches_instance(self):
        with patch('builtins.input',side_effect=['11','other','3','1','0']),patch.object(menu.manager,'main') as run:
            self.assertEqual(menu.main([]),0)
        run.assert_called_once_with(['status','--instance','other'])

    def test_firewall_precedes_new_docker_and_errors_stop_deploy(self):
        answers=self.inputs();answers[-1]='1'
        events=[]
        with patch('builtins.input',side_effect=answers+['y']), \
             patch.object(menu,'assets_directory',return_value=Path('/verified')), \
             patch.object(menu.host,'prepare_dependencies',side_effect=lambda **kw:events.append('base' if kw else 'docker')), \
             patch.object(menu.host,'firewall',side_effect=lambda **kw:events.append('firewall')), \
             patch.object(menu.manager,'docker_check'),patch.object(menu,'web_instructions'), \
             patch.object(menu.manager,'main',side_effect=lambda args:events.append('deploy')):
            menu.install('test')
        self.assertEqual(events,['base','firewall','docker','deploy'])

    def test_incomplete_instance_header_still_allows_exit(self):
        root=menu.manager.instance_path('main');root.mkdir(parents=True)
        (root/'.env').write_text('INVALID')
        with patch('builtins.input',return_value='0'),contextlib.redirect_stdout(io.StringIO()) as output:
            self.assertEqual(menu.main([]),0)
        self.assertIn('配置不完整',output.getvalue())

    def test_backup_listing_excludes_other_instance_prefix(self):
        directory=menu.manager.STATE/'backups';directory.mkdir(parents=True)
        (directory/'main-20261026-100000.tar.gz').write_bytes(b'backup')
        (directory/'main-other-20261026-100000.tar.gz').write_bytes(b'other')
        with patch('builtins.input',return_value='2'),contextlib.redirect_stdout(io.StringIO()) as output:
            menu.backups('main')
        self.assertIn('无原始摘要',output.getvalue())
        self.assertNotIn('main-other',output.getvalue())

    def test_generated_proxy_keeps_existing_user_config(self):
        root=menu.manager.instance_path('test');root.mkdir(parents=True)
        with patch.object(menu.manager,'read_env',return_value={'ADMIN_PORT':'18888','OPEN_CHANNEL':'15,52','PUBLIC_IP':'192.0.2.1'}):
            menu.web_instructions('test')
            path=root/'nginx-location.conf.example'
            self.assertIn('127.0.0.1:18888',path.read_text())
            path.write_text('operator config')
            menu.web_instructions('test')
            self.assertEqual(path.read_text(),'operator config')


class AutomaticMySQL(unittest.TestCase):
    def test_binary_discovery_checks_baota_custom_and_path(self):
        for chosen in ('/www/server/mysql/bin/mysql','/usr/local/mysql/bin/mysql','/usr/bin/mysql'):
            with patch.object(menu.shutil,'which',return_value='/usr/bin/mysql'), \
                 patch.object(Path,'is_file',lambda path: str(path)==chosen), \
                 patch.object(menu.os,'access',return_value=True):
                self.assertEqual(menu.mysql_binary(),chosen)
        with patch.object(Path,'is_file',return_value=False), self.assertRaisesRegex(ValueError,'未检测到'):
            menu.mysql_binary()

    def test_socket_discovery_reads_config_without_exposing_password(self):
        result=Mock(returncode=0,stdout='mysql --socket="/private db/mysql.sock" --password=SECRET --socket=/tmp/mysql.sock')
        with patch.object(menu.subprocess,'run',return_value=result), \
             patch.object(Path,'is_socket',lambda path: str(path) in ('/private db/mysql.sock','/tmp/mysql.sock')), \
             contextlib.redirect_stdout(io.StringIO()) as output:
            self.assertEqual(menu.mysql_sockets('/mysql'),['/private db/mysql.sock','/tmp/mysql.sock'])
        self.assertNotIn('SECRET',output.getvalue())

    def test_socket_discovery_failure_keeps_common_candidates(self):
        with patch.object(menu.subprocess,'run',side_effect=OSError('unavailable')), \
             patch.object(Path,'is_socket',lambda path: str(path)=='/tmp/mysql.sock'):
            self.assertEqual(menu.mysql_sockets('/mysql'),['/tmp/mysql.sock'])

    def test_auto_connection_filters_other_servers_and_uses_custom_account(self):
        paths=[]
        def make_query(binary,defaults,timeout):
            paths.append(defaults)
            content=defaults.read_text()
            self.assertEqual(defaults.stat().st_mode & 0o777,0o600)
            self.assertIn('user="installer"',content)
            self.assertEqual(timeout,10)
            if '/wrong-port.sock' in content:return Mock(return_value='0.0.0.0\t3307')
            if '/wrong-bind.sock' in content:return Mock(return_value='127.0.0.1\t3306')
            self.assertIn('socket="/right.sock"',content)
            return Mock(return_value='0.0.0.0\t3306')
        with patch.object(menu,'mysql_sockets',return_value=['/wrong-port.sock','/wrong-bind.sock','/right.sock']), \
             patch.object(menu.manager.database,'host_query',side_effect=make_query):
            with menu.automatic_mysql_connection('/mysql','installer','secret','172.17.0.1',3306) as defaults:
                self.assertEqual(defaults,paths[-1])
                self.assertTrue(defaults.exists())
                self.assertFalse(any(path.exists() for path in paths[:-1]))
        self.assertEqual(len(paths),3)
        self.assertFalse(any(path.exists() for path in paths))

    def test_socket_failure_falls_back_to_explicit_tcp(self):
        paths=[]
        def make_query(binary,defaults,timeout):
            paths.append(defaults)
            content=defaults.read_text()
            if 'protocol=SOCKET' in content:return Mock(side_effect=ValueError('ERROR 1045'))
            self.assertIn('protocol=TCP\nhost="172.17.0.1"\nport=3306',content)
            return Mock(return_value='0.0.0.0\t3306')
        with patch.object(menu,'mysql_sockets',return_value=['/mysql.sock']), \
             patch.object(menu.manager.database,'host_query',side_effect=make_query):
            with menu.automatic_mysql_connection('/mysql','installer','secret','172.17.0.1',3306):pass
        self.assertEqual(len(paths),2)
        self.assertFalse(any(path.exists() for path in paths))

    def test_failed_initialization_does_not_retry_another_connection(self):
        with patch.object(menu,'mysql_sockets',return_value=['/mysql.sock','/other.sock']), \
             patch.object(menu.manager.database,'host_query',return_value=Mock(return_value='0.0.0.0\t3306')) as query:
            with self.assertRaisesRegex(ValueError,'initialization failed'):
                with menu.automatic_mysql_connection('/mysql','installer','secret','172.17.0.1',3306) as defaults:
                    raise ValueError('initialization failed')
        query.assert_called_once()
        self.assertFalse(defaults.exists())

    def test_credentials_invalid_and_unreachable_fail_without_leaking(self):
        paths=[]
        def make_query(binary,defaults,timeout):
            paths.append(defaults)
            return Mock(side_effect=ValueError('do not expose SECRET'))
        with patch.object(menu,'mysql_sockets',return_value=[]), \
             patch.object(menu.manager.database,'host_query',side_effect=make_query):
            with self.assertRaises(ValueError) as caught:
                with menu.automatic_mysql_connection('/mysql','installer','SECRET','172.17.0.1',3306):
                    self.fail('must not connect')
        self.assertNotIn('SECRET',str(caught.exception))
        self.assertFalse(any(path.exists() for path in paths))
        with self.assertRaises(ValueError):
            with menu.mysql_connection('root\nprotocol=SOCKET','secret','172.17.0.1',3306):pass


class PublicIPDiscovery(unittest.TestCase):
    def test_fallback_validation_and_timeouts(self):
        for first in (OSError('timeout'), b'not an IP', b'127.0.0.1', b'10.0.0.1',
                      b'::1', b'224.0.0.1', b'x'*65, b'\xff'):
            opener = Mock()
            good = Mock()
            good.__enter__ = Mock(return_value=good)
            good.__exit__ = Mock(return_value=False)
            good.read.return_value = b'8.8.4.4\n'
            bad = Mock()
            bad.__enter__ = Mock(return_value=bad)
            bad.__exit__ = Mock(return_value=False)
            bad.read.return_value = first
            opener.open.side_effect = [first if isinstance(first, Exception) else bad, good]
            with self.subTest(first=first), patch.object(menu.urllib.request,'build_opener',return_value=opener):
                self.assertEqual(menu.detect_public_ipv4(),'8.8.4.4')
            self.assertEqual(opener.open.call_count,2)
            self.assertTrue(all(call.kwargs['timeout']==3 for call in opener.open.call_args_list))
            good.read.assert_called_once_with(65)

    def test_unavailable_services_return_manual_fallback(self):
        opener = Mock()
        opener.open.side_effect = OSError('offline')
        with patch.object(menu.urllib.request,'build_opener',return_value=opener):
            self.assertEqual(menu.detect_public_ipv4(),'')
        self.assertEqual(opener.open.call_count,2)


if __name__ == '__main__':unittest.main()
