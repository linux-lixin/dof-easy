"""Prepare a private Ubuntu container root and run offline local diagnostics.

Package preparation has network access and never mounts native samples. Sample
runs use a read-only root, private PID/user/network namespaces and fresh output.
No Docker context, SSH connection or existing service is inspected or used.
"""
import argparse
import hashlib
import json
import os
from pathlib import Path
import subprocess
import tarfile
import tempfile


def inventory(root, allow_links=False):
    """Bind evidence to actual bytes, including tools; never follow tree links."""
    root = real_directory(root)
    entries = {}
    for parent, dirs, files in os.walk(root, followlinks=False):
        for name in sorted(dirs + files):
            path = Path(parent) / name
            relative = path.relative_to(root).as_posix()
            if path.is_symlink():
                if not allow_links:
                    raise ValueError('Input evidence cannot include symlinks')
                entries[relative] = {'link': os.readlink(path)}
            elif path.is_file():
                entries[relative] = {'sha256': sha256(path), 'mode': path.stat().st_mode & 0o777}
            elif not path.is_dir():
                raise ValueError('Unexpected special file in evidence tree')
    return entries


def fingerprint(entries):
    return hashlib.sha256(json.dumps(entries, sort_keys=True, separators=(',', ':')).encode()).hexdigest()


def seal(workspace):
    workspace, record = provenance(workspace)
    if not record['dependenciesReady'] or (workspace / 'rootfs-files.json').exists():
        raise ValueError('Only a prepared, not yet sealed environment can be sealed')
    entries = inventory(workspace / 'rootfs', allow_links=True)
    (workspace / 'rootfs-files.json').write_text(json.dumps(entries, sort_keys=True, indent=2) + '\n')
    record['rootfsFingerprint'] = fingerprint(entries)
    (workspace / 'environment.json').write_text(json.dumps(record, indent=2) + '\n')
    print(json.dumps({'rootfsFingerprint': record['rootfsFingerprint'], 'entries': len(entries)}))

BASE_SHA256 = '6bc2cde3930ad088b3bb46fa45279e96d25bc3810f209850ecbe4722711874f9'
BASE_URL = 'https://cdimage.ubuntu.com/ubuntu-base/releases/24.04/release/ubuntu-base-24.04.3-base-amd64.tar.gz'
PACKAGES = ['ca-certificates', 'libc6:i386', 'libstdc++6:i386', 'libgcc-s1:i386',
            'zlib1g:i386', 'gcc', 'gcc-multilib', 'libc6-dev-i386', 'binutils',
            'strace', 'gdb', 'python3', 'procps', 'iproute2', 'openssl', 'libcrypt1:i386']


def sha256(path):
    with Path(path).open('rb') as stream:
        return hashlib.file_digest(stream, 'sha256').hexdigest()


def real_directory(path):
    path = Path(path).absolute()
    if path != path.resolve() or not path.is_dir():
        raise ValueError('Expected an existing directory without symlink ancestors')
    return path


def provenance(workspace):
    workspace = real_directory(workspace)
    record = json.loads((workspace / 'environment.json').read_text())
    if record.get('baseSha256') != BASE_SHA256 or record.get('schema') != 1:
        raise ValueError('Unrecognized local environment')
    real_directory(workspace / 'rootfs')
    return workspace, record


def extract(archive, workspace):
    archive = Path(archive)
    if archive.is_symlink() or sha256(archive) != BASE_SHA256:
        raise ValueError('Ubuntu base archive does not match the pinned official SHA256')
    workspace = Path(workspace).absolute()
    if workspace.exists() or workspace.is_symlink() or workspace.parent != workspace.parent.resolve():
        raise ValueError('Environment destination must be new with real ancestors')
    with tempfile.TemporaryDirectory(prefix='.dof-filesystem-check-', dir=workspace.parent) as probe:
        (Path(probe) / 'CaseSensitive').touch()
        if (Path(probe) / 'casesensitive').exists():
            raise ValueError('Container root requires a case-sensitive Linux filesystem; use a private WSL cache directory')
    # The pinned vendor image includes ordinary rootfs symlinks. Reject archive
    # path traversal and devices before extraction; GNU tar handles rootfs links.
    with tarfile.open(archive) as stream:
        for member in stream:
            if Path(member.name).is_absolute() or '..' in Path(member.name).parts or member.isdev():
                raise ValueError('Unexpected rootfs archive entry')
    workspace.mkdir(parents=True, mode=0o700)
    root = workspace / 'rootfs'; root.mkdir()
    subprocess.run(['tar', '-xzf', str(archive.resolve()), '--no-same-owner',
                    '--no-same-permissions', '-C', str(root)], check=True)
    for mountpoint in ('inputs', 'output', 'data', 'opt/dof-easy'):
        (root / mountpoint).mkdir(parents=True, exist_ok=True)
    record = dict(schema=1, baseUrl=BASE_URL, baseSha256=BASE_SHA256,
                  dependenciesReady=False, samplesExecuted=False, serverAccessed=False)
    (workspace / 'environment.json').write_text(json.dumps(record, indent=2) + '\n')


def base_command(root, writable=False):
    return ['bwrap', '--unshare-all', '--uid', '0', '--gid', '0', '--die-with-parent',
            '--new-session', '--hostname', 'dof-local-validation', '--clearenv',
            '--setenv', 'PATH', '/usr/local/sbin:/usr/local/bin:/usr/sbin:/usr/bin:/sbin:/bin',
            '--setenv', 'LC_ALL', 'C', '--setenv', 'TZ', 'UTC',
            '--bind' if writable else '--ro-bind', str(root), '/',
            '--proc', '/proc', '--dev', '/dev', '--tmpfs', '/tmp', '--tmpfs', '/run']


def packages(workspace):
    workspace, record = provenance(workspace)
    if record['dependenciesReady']:
        raise ValueError('Dependencies already frozen; create a new environment to update them')
    root = workspace / 'rootfs'
    # No samples, project directory, host filesystem or Docker socket is mounted.
    command = base_command(root, writable=True)
    command += ['--share-net', '--ro-bind', '/etc/resolv.conf', '/etc/resolv.conf',
                '--setenv', 'DEBIAN_FRONTEND', 'noninteractive', '--', '/bin/sh', '-ec',
                "printf '#!/bin/sh\\nexit 101\\n' > /usr/sbin/policy-rc.d; chmod 755 /usr/sbin/policy-rc.d; "
                "dpkg --add-architecture i386; apt-get -o APT::Sandbox::User=root update; "
                "apt-get -o APT::Sandbox::User=root install -y --no-install-recommends " + ' '.join(PACKAGES) +
                "; dpkg-query -W > /var/lib/dof-package-versions.tsv"]
    subprocess.run(command, check=True)
    manifest = (root / 'var/lib/dof-package-versions.tsv').read_bytes()
    (workspace / 'packages.tsv').write_bytes(manifest)
    record.update(dependenciesReady=True, packageManifestSha256=hashlib.sha256(manifest).hexdigest(),
                  requestedPackages=PACKAGES, isolation='bubblewrap user/pid/ipc/uts/network namespaces')
    (workspace / 'environment.json').write_text(json.dumps(record, indent=2) + '\n')


def run(workspace, inputs, output, arguments, timeout):
    workspace, record = provenance(workspace)
    if not record['dependenciesReady'] or sha256(workspace / 'packages.tsv') != record['packageManifestSha256']:
        raise ValueError('Local dependencies are not frozen')
    inputs = real_directory(inputs)
    if 'rootfsFingerprint' not in record or fingerprint(inventory(workspace / 'rootfs', True)) != record['rootfsFingerprint']:
        raise ValueError('Environment file hashes are unsealed or changed; previous validation is invalid')
    input_files = inventory(inputs)
    output = Path(output).absolute()
    if not 1 <= timeout <= 3600:
        raise ValueError('Local run timeout must be between 1 and 3600 seconds')
    if output.is_relative_to(workspace) or output.is_relative_to(inputs):
        raise ValueError('Run output must be separate from the environment and inputs')
    if output.exists() or output.is_symlink() or output.parent != output.parent.resolve():
        raise ValueError('Run output must be a new directory with real ancestors')
    if not arguments:
        raise ValueError('An explicit in-container command is required')
    mountpoints = record.get('extraPrivateMountpoints', [])
    if mountpoints not in ([], ['/dp2']):
        raise ValueError('Unsupported private native-plugin mountpoints')
    for mountpoint in mountpoints:
        real_directory(workspace / 'rootfs' / mountpoint.lstrip('/'))
    output.mkdir(parents=True, mode=0o700)
    (output / 'inputs.manifest.json').write_text(json.dumps(input_files, sort_keys=True, indent=2) + '\n')
    (output / 'data').mkdir(mode=0o700)
    (output / 'runtime').mkdir(mode=0o700)
    command = base_command(workspace / 'rootfs')
    for mountpoint in mountpoints:
        command += ['--tmpfs', mountpoint]
    command += ['--ro-bind', str(inputs), '/inputs', '--bind', str(output), '/output',
                '--tmpfs', '/root', '--tmpfs', '/usr/local/share',
                '--tmpfs', '/usr/local/bin', '--tmpfs', '/home',
                '--tmpfs', '/opt', '--tmpfs', '/var/log',
                '--bind', str(output / 'data'), '/data',
                '--bind', str(output / 'runtime'), '/opt/dof-easy',
                '--chdir', '/output', '--cap-add', 'CAP_NET_ADMIN',
                '--cap-add', 'CAP_SETPCAP', '--',
                '/bin/sh', '-ec',
                'ip link set lo up; exec setpriv --bounding-set=-all --no-new-privs -- "$@"',
                'dof-local-entry', *arguments]
    report = dict(schema=1, serverAccessed=False, network='private namespace; no routes or external interfaces',
                  rootReadOnly=True, inputsReadOnly=True, loopbackOnly=True,
                  inputsFingerprint=fingerprint(input_files), rootfsFingerprint=record['rootfsFingerprint'],
                  packageManifestSha256=record['packageManifestSha256'],
                  command=arguments, completed=False)
    report_file = output / 'run.json'
    report_file.write_text(json.dumps(report, indent=2) + '\n')
    with (output / 'stdout.log').open('xb') as out, (output / 'stderr.log').open('xb') as err:
        try:
            result = subprocess.run(command, stdout=out, stderr=err, timeout=timeout)
            report.update(completed=True, exitCode=result.returncode)
        except subprocess.TimeoutExpired:
            report.update(timedOut=True)
        finally:
            # A missing input, unexpected symlink or unreadable dependency also
            # invalidates evidence. Preserve a failure record in those cases;
            # do not leave the pre-execution report as the only result.
            for field, check in (
                ('inputsUnchanged', lambda: inventory(inputs) == input_files),
                ('environmentUnchanged', lambda: fingerprint(inventory(workspace / 'rootfs', True)) == record['rootfsFingerprint']),
            ):
                try:
                    report[field] = check()
                except (OSError, ValueError) as error:
                    report[field] = False
                    report.setdefault('verificationErrors', {})[field] = type(error).__name__
            report['evidenceValid'] = report['inputsUnchanged'] and report['environmentUnchanged']
            report_file.write_text(json.dumps(report, indent=2) + '\n')
    print(json.dumps(report))
    return 0 if report.get('exitCode') == 0 and report['evidenceValid'] else 2


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    commands = parser.add_subparsers(dest='action', required=True)
    p = commands.add_parser('extract'); p.add_argument('--archive', required=True, type=Path)
    p.add_argument('--workspace', required=True, type=Path)
    p = commands.add_parser('packages'); p.add_argument('--workspace', required=True, type=Path)
    p = commands.add_parser('seal'); p.add_argument('--workspace', required=True, type=Path)
    p = commands.add_parser('run'); p.add_argument('--workspace', required=True, type=Path)
    p.add_argument('--inputs', required=True, type=Path); p.add_argument('--output', required=True, type=Path)
    p.add_argument('--timeout', type=int, default=120); p.add_argument('command', nargs=argparse.REMAINDER)
    args = parser.parse_args()
    if args.action == 'extract': extract(args.archive, args.workspace)
    elif args.action == 'packages': packages(args.workspace)
    elif args.action == 'seal': seal(args.workspace)
    else:
        arguments = args.command[1:] if args.command[:1] == ['--'] else args.command
        raise SystemExit(run(args.workspace, args.inputs, args.output, arguments, args.timeout))


if __name__ == '__main__':
    main()
