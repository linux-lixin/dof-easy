"""Readiness is service health plus stable Qingkong processes, not gameplay proof."""
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

    def test_qingkong_ready_without_geo_logs_but_health_failure_still_blocks(self):
        module = self.module()
        with tempfile.TemporaryDirectory() as directory:
            state = HealthState()
            check = module.make_check(state, {'LAUNCHER_MODE': 'qingkong'}, Path(directory))
            result = check()
            self.assertEqual(len(result), 3)
            state.ready = False
            with self.assertRaisesRegex(ValueError, 'port absent'):
                check()
            with patch.object(module.time, 'monotonic', side_effect=[0, 0, 1, 2, 10]), patch.object(module.time, 'sleep'):
                messages = []
                self.assertEqual(module.wait_ready(check, 'READY', 5, messages.append), 1)
            self.assertNotIn('READY', messages)


    def test_invalid_profile_and_empty_qingkong_snapshot_fail_closed(self):
        module = self.module()
        for profile in ('unknown', 'native', 'dof'):
            with self.assertRaisesRegex(ValueError, 'LAUNCHER_MODE'):
                module.make_check(HealthState(), {'LAUNCHER_MODE': profile})
        state = HealthState()
        state.processes = [('50', 'python3', ['health.py'])]
        with self.assertRaisesRegex(ValueError, 'Qingkong processes'):
            module.make_check(state, {'LAUNCHER_MODE': 'qingkong'})()

    def test_pid_changes_reset_stability_but_health_helpers_do_not(self):
        module = self.module()
        state = HealthState()
        check = module.make_check(state, {'LAUNCHER_MODE': 'qingkong'})
        calls = []

        def sequence():
            calls.append(1)
            # Two successful samples, then a native restart: require three new ones.
            if len(calls) == 3:
                state.processes[0] = ('20', 'df_game_r', ['siroco15', 'nofork'])
            state.processes[-1] = (str(50 + len(calls)), 'python3', ['health.py'])
            return check()

        with patch.object(module.time, 'monotonic', side_effect=[0,0,0,0,0,0,0,10]), patch.object(module.time, 'sleep'):
            messages = []
            self.assertEqual(module.wait_ready(sequence, 'READY', 5, messages.append), 0)
        self.assertEqual(len(calls), 5)
        self.assertEqual(messages, ['READY'])


if __name__ == '__main__':
    unittest.main()
