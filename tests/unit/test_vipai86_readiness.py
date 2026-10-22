"""Readiness is service health plus stable native processes, not gameplay proof."""
import importlib.util
from pathlib import Path
import tempfile
import unittest
from unittest.mock import patch

ROOT = Path(__file__).resolve().parents[2]
SPEC = importlib.util.spec_from_file_location('startup_notice', ROOT / 'services/game/runtime/ubuntu/startup_notice.py')


class HealthState:
    def __init__(self, ready=True):
        self.ready = ready
        self.processes = [('10', 'df_game_r', ['siroco15', 'nofork']),
                          ('11', 'df_game_r', ['siroco52', 'nofork']),
                          ('12', 'df_auction_r', ['server.cfg', 'start']),
                          ('50', 'python3', ['health.py'])]

    def check(self):
        if not self.ready:
            raise ValueError('required channel port absent')

    def snapshot(self):
        return self.processes, {'tcp': {30015, 30052}, 'udp': {31015, 31052}}


class ReadinessProfiles(unittest.TestCase):
    def module(self):
        module = importlib.util.module_from_spec(SPEC)
        SPEC.loader.exec_module(module)
        self.assertTrue(hasattr(module, 'make_check'), 'readiness profiles are not implemented')
        return module

    def test_vipai86_ready_without_geo_logs_but_health_failure_still_blocks(self):
        module = self.module()
        with tempfile.TemporaryDirectory() as directory:
            state = HealthState()
            check = module.make_check(state, {'DNF_READINESS_PROFILE': 'vipai86'}, Path(directory))
            result = check()
            self.assertEqual(len(result), 3)
            state.ready = False
            with self.assertRaisesRegex(ValueError, 'port absent'):
                check()
            with patch.object(module.time, 'monotonic', side_effect=[0, 0, 1, 2, 10]), patch.object(module.time, 'sleep'):
                messages = []
                self.assertEqual(module.wait_ready(check, 'READY', 5, messages.append), 1)
            self.assertNotIn('READY', messages)

    def test_legacy_default_and_explicit_dof_still_require_geo_logs(self):
        module = self.module()
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            for profile in ({}, {'DNF_READINESS_PROFILE': 'dof'}):
                env = dict(SERVER_TYPE='GAME', SERVER_GROUP='3', OPEN_CHANNEL='15', **profile)
                with self.assertRaisesRegex(ValueError, 'initialization incomplete'):
                    module.make_check(HealthState(), env, root)()
            (root / 'siroco15').mkdir()
            (root / 'siroco15/Log20261022.init').write_text(''.join(
                'GeoIP Allow Country Code: ' + country + '\n' for country in ('CN', 'HK', 'KR', 'MO', 'TW')))
            module.make_check(HealthState(), env, root)()

    def test_invalid_profile_and_empty_native_snapshot_fail_closed(self):
        module = self.module()
        with self.assertRaisesRegex(ValueError, 'DNF_READINESS_PROFILE'):
            module.make_check(HealthState(), {'DNF_READINESS_PROFILE': 'vipai68'})
        state = HealthState()
        state.processes = [('50', 'python3', ['health.py'])]
        with self.assertRaisesRegex(ValueError, 'native processes'):
            module.make_check(state, {'DNF_READINESS_PROFILE': 'vipai86'})()

    def test_pid_changes_reset_stability_but_health_helpers_do_not(self):
        module = self.module()
        state = HealthState()
        check = module.make_check(state, {'DNF_READINESS_PROFILE': 'vipai86'})
        calls = []

        def sequence():
            calls.append(1)
            # Two successful samples, then a native restart: require three new ones.
            if len(calls) == 3:
                state.processes[0] = ('20', 'df_game_r', ['siroco15', 'nofork'])
            state.processes[-1] = (str(50 + len(calls)), 'python3', ['health.py'])
            return check()

        with patch.object(module.time, 'monotonic', return_value=0), patch.object(module.time, 'sleep'):
            messages = []
            self.assertEqual(module.wait_ready(sequence, 'READY', 5, messages.append), 0)
        self.assertEqual(len(calls), 5)
        self.assertEqual(messages, ['READY'])


if __name__ == '__main__':
    unittest.main()
