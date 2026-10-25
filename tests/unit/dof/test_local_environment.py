import json
from pathlib import Path
import subprocess
import tempfile
import unittest
from unittest.mock import patch
from scripts.dof import local_environment as env


class LocalIsolation(unittest.TestCase):
    def setUp(self):
        self.tmp = tempfile.TemporaryDirectory(); self.addCleanup(self.tmp.cleanup)
        self.root = Path(self.tmp.name)
        self.workspace = self.root / 'environment'; self.workspace.mkdir()
        (self.workspace / 'rootfs').mkdir()
        (self.workspace / 'packages.tsv').write_bytes(b'fixture-package\t1\n')
        self.record = dict(schema=1, baseSha256=env.BASE_SHA256, dependenciesReady=True,
                           rootfsFingerprint=env.fingerprint({}),
                           packageManifestSha256=env.sha256(self.workspace / 'packages.tsv'))
        (self.workspace / 'environment.json').write_text(json.dumps(self.record))
        self.inputs = self.root / 'inputs'; self.inputs.mkdir()

    def test_native_run_has_no_host_network_or_root_mount(self):
        output = self.root / 'output'
        with patch.object(env.subprocess, 'run', return_value=subprocess.CompletedProcess([], 0)) as execute:
            self.assertEqual(env.run(self.workspace, self.inputs, output, ['/bin/true'], 10), 0)
        command = execute.call_args.args[0]
        self.assertIn('--unshare-all', command)
        self.assertNotIn('--share-net', command)
        self.assertIn('--clearenv', command)
        self.assertIn('--die-with-parent', command)
        self.assertIn('--bounding-set=-all', ' '.join(command))
        self.assertEqual(command[command.index('--ro-bind') + 1], str(self.workspace / 'rootfs'))
        self.assertNotIn('/var/run/docker.sock', command)
        self.assertEqual(json.loads((output / 'run.json').read_text())['exitCode'], 0)

    def test_input_change_invalidates_successful_process_exit(self):
        fixture = self.inputs / 'sample'; fixture.write_bytes(b'original')
        def change(*args, **kwargs):
            fixture.write_bytes(b'modified')
            return subprocess.CompletedProcess([], 0)
        output = self.root / 'output'
        with patch.object(env.subprocess, 'run', side_effect=change):
            self.assertEqual(env.run(self.workspace, self.inputs, output, ['/bin/true'], 10), 2)
        self.assertFalse(json.loads((output / 'run.json').read_text())['evidenceValid'])

    def test_changed_runtime_dependency_is_rejected_before_execution(self):
        (self.workspace / 'rootfs' / 'loader').write_bytes(b'changed')
        with self.assertRaisesRegex(ValueError, 'hashes'), patch.object(env.subprocess, 'run') as execute:
            env.run(self.workspace, self.inputs, self.root / 'output', ['/bin/true'], 10)
        execute.assert_not_called()

    def test_input_replaced_by_link_records_invalid_evidence(self):
        fixture = self.inputs / 'sample'; fixture.write_bytes(b'original')
        def change(*args, **kwargs):
            fixture.unlink(); fixture.symlink_to('/etc/passwd')
            return subprocess.CompletedProcess([], 0)
        output = self.root / 'output'
        with patch.object(env.subprocess, 'run', side_effect=change):
            self.assertEqual(env.run(self.workspace, self.inputs, output, ['/bin/true'], 10), 2)
        report = json.loads((output / 'run.json').read_text())
        self.assertFalse(report['evidenceValid'])
        self.assertFalse(report['inputsUnchanged'])
        self.assertTrue(report['environmentUnchanged'])
        self.assertEqual(report['verificationErrors']['inputsUnchanged'], 'ValueError')

    def test_symlink_environment_cannot_mount_host_root(self):
        (self.workspace / 'rootfs').rmdir(); (self.workspace / 'rootfs').symlink_to('/')
        with self.assertRaises(ValueError), patch.object(env.subprocess, 'run') as execute:
            env.run(self.workspace, self.inputs, self.root / 'output', ['/bin/true'], 10)
        execute.assert_not_called()

    def test_existing_output_and_changed_manifest_are_rejected(self):
        output = self.root / 'output'; output.mkdir(); (output / 'keep').write_bytes(b'original')
        with self.assertRaises(ValueError): env.run(self.workspace, self.inputs, output, ['/bin/true'], 10)
        self.assertEqual((output / 'keep').read_bytes(), b'original')
        (self.workspace / 'packages.tsv').write_bytes(b'changed')
        with self.assertRaises(ValueError): env.run(self.workspace, self.inputs, self.root / 'new', ['/bin/true'], 10)

    def test_output_cannot_mutate_environment_or_inputs(self):
        for parent in (self.workspace, self.inputs):
            with self.assertRaises(ValueError):
                env.run(self.workspace, self.inputs, parent / 'output', ['/bin/true'], 10)

    def test_timeout_is_reported_as_incomplete(self):
        output = self.root / 'output'
        with patch.object(env.subprocess, 'run', side_effect=subprocess.TimeoutExpired('fixture', 1)):
            self.assertEqual(env.run(self.workspace, self.inputs, output, ['/bin/true'], 1), 2)
        record = json.loads((output / 'run.json').read_text())
        self.assertFalse(record['completed']); self.assertTrue(record['timedOut'])
        self.assertNotIn('exitCode', record)

    def test_dp2_mount_is_private_and_does_not_expose_host_files(self):
        (self.workspace / 'rootfs/dp2').mkdir()
        self.record['extraPrivateMountpoints'] = ['/dp2']
        (self.workspace / 'environment.json').write_text(json.dumps(self.record))
        with patch.object(env.subprocess, 'run', return_value=subprocess.CompletedProcess([], 0)) as execute:
            env.run(self.workspace, self.inputs, self.root / 'output', ['/bin/true'], 10)
        command = execute.call_args.args[0]
        index = command.index('/dp2')
        self.assertEqual(command[index - 1], '--tmpfs')

    def test_unapproved_mountpoint_leaves_no_output_tree(self):
        self.record['extraPrivateMountpoints'] = ['/home']
        (self.workspace / 'environment.json').write_text(json.dumps(self.record))
        output = self.root / 'output'
        with self.assertRaises(ValueError), patch.object(env.subprocess, 'run') as execute:
            env.run(self.workspace, self.inputs, output, ['/bin/true'], 10)
        execute.assert_not_called()
        self.assertFalse(output.exists())


if __name__ == '__main__': unittest.main()
