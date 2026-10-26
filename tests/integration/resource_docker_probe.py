"""Check production COPY/ignore rules in a disposable local Docker daemon.

Runs no native binary and downloads nothing. Provide a preloaded BusyBox image.
This is a resource-boundary test, not a complete game image build or login test.
"""
import argparse
import json
from pathlib import Path
import shutil
import subprocess
import tempfile
import uuid

ROOT = Path(__file__).resolve().parents[2]


def run(root, base):
    # Refuse to implicitly pull a test dependency.
    base_id = subprocess.check_output(['docker', 'image', 'inspect', base, '--format', '{{.Id}}'], text=True).strip()
    game = root / 'services/game'
    lock = json.loads((root / 'metadata/build-inputs.lock.json').read_text())
    prefix = 'services/game/resources/plugins/'
    rows = [row for row in lock['files'] if row['path'].startswith(prefix)]
    result = dict(serverAccessed=False, nativeProgramsExecuted=False, fullGameDeployment='not-run', baseImage=base_id, profiles=[])
    for profile in ('Dockerfile', 'Dockerfile.integration'):
        image = 'dof-resource-probe:' + uuid.uuid4().hex
        with tempfile.TemporaryDirectory(prefix='dof-resource-probe-') as temp:
            context = Path(temp)
            shutil.copytree(game / 'resources', context / 'resources')
            for path in ('source-audit/should-not-ship', 'archive/should-not-ship', 'seed/should-not-ship'):
                p = context / path; p.parent.mkdir(exist_ok=True); p.write_text('audit sentinel')
            dockerfile = game / 'docker/ubuntu' / profile
            copies = [line for line in dockerfile.read_text().splitlines() if line.startswith('COPY resources/plugins/')]
            if len(copies) != 2:
                raise ValueError('Expected explicit DP2 and Frida COPY rules: ' + profile)
            specific = dockerfile.with_name(profile + '.dockerignore')
            ignore = specific if specific.exists() else game / '.dockerignore'
            shutil.copyfile(ignore, context / '.dockerignore')
            checks = ''.join(row['sha256'] + '  ' + row['path'].removeprefix(prefix) + '\n' for row in rows)
            (context / 'checksums').write_text(checks)
            (context / 'Dockerfile').write_text('\n'.join([
                'FROM ' + base, 'COPY . /context/', *copies,
                'RUN test ! -e /context/resources/native && test ! -e /context/source-audit && test ! -e /context/archive && test ! -e /context/seed',
                'RUN cd /opt/dnf/plugins && sha256sum -c /context/checksums',
                'RUN test "$(find /opt/dnf/plugins -type f | wc -l)" -eq ' + str(len(rows)),
            ]) + '\n')
            try:
                subprocess.run(['docker', 'build', '--pull=false', '--network=none', '-t', image, str(context)], check=True)
                image_id = subprocess.check_output(['docker', 'image', 'inspect', image, '--format', '{{.Id}}'], text=True).strip()
                result['profiles'].append(dict(dockerfile=profile, ignore=str(ignore.relative_to(root)), pluginFiles=len(rows), image=image_id, passed=True))
            finally:
                subprocess.run(['docker', 'image', 'rm', image], check=False, stdout=subprocess.DEVNULL)
    return result


if __name__ == '__main__':
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--root', type=Path, default=ROOT)
    parser.add_argument('--base', default='busybox:1.36')
    parser.add_argument('--report', type=Path, required=True)
    args = parser.parse_args()
    args.report.write_text(json.dumps(run(args.root.resolve(), args.base), indent=2) + '\n')
