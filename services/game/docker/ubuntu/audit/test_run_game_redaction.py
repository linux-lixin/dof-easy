"""Linux integration regression; compile run-game-probe.c for both ELF classes first."""
import os
from pathlib import Path
import select
import signal
import subprocess
import time
import unittest

LAUNCHER = '/source/run-game'
SECRETS = {name: 'Synthetic_' + str(index) + '_DoNotLog' for index, name in enumerate((
    'DNF_DB_GAME_PASSWORD', 'DEC_GAME_PWD', 'DNF_DB_ROOT_PASSWORD',
    'MAIN_MYSQL_ROOT_PASSWORD', 'WEB_PASS', 'GM_PASSWORD', 'GM_CONNECT_KEY'))}


class NativeOutputTests(unittest.TestCase):
    def launch(self, arch, *args):
        env = dict(os.environ, PWD='/', **SECRETS)
        env.pop('LD_PRELOAD', None)
        env.pop('DNF_GAME_PRELOAD', None)
        return subprocess.Popen(['python3', LAUNCHER, '/probes/probe-' + arch, *args],
                                env=env, stdout=subprocess.PIPE, stderr=subprocess.PIPE,
                                start_new_session=True)

    def test_native_stdout_stderr_redaction_and_exit_status(self):
        for arch in ('amd64', 'i386'):
            with self.subTest(arch=arch):
                child = self.launch(arch)
                out, err = child.communicate(timeout=15)
                self.assertEqual(child.returncode, 37)
                self.assertTrue(('native-pid=%d' % child.pid).encode() in out, 'exec must retain PID')
                for number, output in ((1, out), (2, err)):
                    self.assertTrue(('stream-%d-readable' % number).encode() in output)
                    self.assertTrue(b'path=/opt/dnf/example/data' in output,
                                    'standard shell PWD must not redact ordinary paths')
                    for secret in [*SECRETS.values(), 'unknown_unconfigured_secret',
                                   'unquoted_unknown_secret', 'overflow_secret',
                                   'unknown_game_encoded_secret', 'unknown_auction_encoded_secret']:
                        self.assertTrue(secret.encode() not in output, 'native credential escaped redaction')
                    self.assertTrue(b'[REDACTED]' in output)
                    self.assertTrue(len(output) < 10000, 'oversized lines must be bounded and suppressed')

    def test_exec_pid_direct_signal_and_group_shutdown(self):
        for arch in ('amd64', 'i386'):
            for group in (False, True):
                with self.subTest(arch=arch, group=group):
                    child = self.launch(arch, 'wait')
                    try:
                        deadline = time.monotonic() + 5
                        while time.monotonic() < deadline:
                            if os.readlink('/proc/%d/exe' % child.pid) == '/probes/probe-' + arch:
                                break
                            time.sleep(.01)
                        self.assertEqual(os.readlink('/proc/%d/exe' % child.pid), '/probes/probe-' + arch)
                        helpers = Path('/proc/%d/task/%d/children' % (child.pid, child.pid)).read_text().split()
                        self.assertEqual(len(helpers), 1, 'one isolated redaction reader required')
                        maps = Path('/proc/%s/maps' % helpers[0]).read_text()
                        self.assertTrue('/opt/dnf/compat/' not in maps, 'helper must not load game libraries')
                        # exec is visible in /proc before native main necessarily runs.
                        # Synchronize on its flushed marker before testing shutdown.
                        marker = ('native-pid=%d\n' % child.pid).encode()
                        ready_output = b''
                        deadline = time.monotonic() + 5
                        while marker not in ready_output:
                            remaining = deadline - time.monotonic()
                            self.assertGreater(remaining, 0, 'native startup marker timed out')
                            readable, _, _ = select.select([child.stdout], [], [], remaining)
                            self.assertTrue(readable, 'native startup marker timed out')
                            chunk = os.read(child.stdout.fileno(), 4096)
                            self.assertTrue(chunk, 'native stdout closed before startup marker')
                            ready_output += chunk
                        if group:
                            os.killpg(child.pid, signal.SIGTERM)
                        else:
                            child.terminate()
                        out, err = child.communicate(timeout=5)
                        self.assertEqual(child.returncode, -signal.SIGTERM)
                        self.assertTrue(marker in ready_output + out)
                        self.assertEqual(err, b'')
                    finally:
                        if child.poll() is None:
                            child.kill()
                            child.communicate(timeout=5)


if __name__ == '__main__':
    unittest.main()
