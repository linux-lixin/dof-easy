"""Stage fixed local server test inputs without running binaries or using a server.

This produces an offline integration fixture, never an installable release.
Run it only through local_environment.py's private network/PID namespace.
"""
import argparse
try:
    from .native_name_contract import module_contract
except ImportError:
    from native_name_contract import module_contract
import hashlib
import json
import os
from pathlib import Path, PurePosixPath
import shutil
import tempfile

ROOT = Path(__file__).resolve().parents[2]
LOCK = ROOT / 'metadata/dof/server-probe.lock.json'
FIXTURES = ROOT / 'tests/integration/dof/server_stack'


def digest(path):
    with path.open('rb') as stream:
        return hashlib.file_digest(stream, 'sha256').hexdigest()


def regular(root, relative):
    name = PurePosixPath(relative)
    if name.is_absolute() or '..' in name.parts or '\\' in relative:
        raise ValueError('Invalid local fixture path')
    target = root.joinpath(*name.parts)
    if any(p.is_symlink() for p in (target, *target.parents)) or not target.is_file():
        raise ValueError('Fixture requires regular local files: ' + relative)
    return target


def stage(source, output, runtime=None, reuse_assets=False):
    source, output = Path(source).absolute(), Path(output).absolute()
    if source != source.resolve() or not source.is_dir():
        raise ValueError('Source must be a real local directory')
    if output.exists() or output.is_symlink() or output.parent != output.parent.resolve():
        raise ValueError('Output must be a new local directory with real ancestors')
    if output.is_relative_to(source) or source.is_relative_to(output):
        raise ValueError('Source and output trees must be separate')
    rows = json.loads(LOCK.read_text())['files']
    if reuse_assets:
        provenance = json.loads(regular(source, 'probe-source.json').read_text())
        if provenance.get('assetLockSha256') != digest(LOCK):
            raise ValueError('Only a previously staged, fixed asset set can be reused')
    # Validate local paths before staging, then hash each completed private copy.
    # No output directory is published until the complete fixed set passes.
    for name in rows:
        regular(source, name)
    if runtime is not None:
        import sys
        sys.path.insert(0, str(ROOT / 'services/game/runtime/ubuntu'))
        from gateway.assets import verify
        runtime = verify(runtime)
    output.parent.mkdir(parents=True, exist_ok=True)
    with tempfile.TemporaryDirectory(prefix='.dof-server-probe-', dir=output.parent) as temporary:
        working = Path(temporary) / 'inputs'
        working.mkdir(mode=0o700)
        for name, row in rows.items():
            destination = working / name
            destination.parent.mkdir(mode=0o700, parents=True, exist_ok=True)
            if reuse_assets:
                original = regular(source, name)
                if original.stat().st_mode & 0o222:
                    raise ValueError('Reused fixture assets must already be read-only')
                os.link(original, destination)
            else:
                shutil.copyfile(regular(source, name), destination)
            if digest(destination) != row['sha256']:
                raise ValueError('Fixed server fixture hash mismatch during staging: ' + name)
            if not reuse_assets:
                destination.chmod(0o500 if row['mode'] & 0o111 else 0o400)
        scripts = {name: FIXTURES / name for name in
                   ('setup-stack.py', 'stack-probe.sh', 'db-port.py', 'db_adapter.js', 'report.py')}
        scripts.update({name: ROOT / 'services/game/docker/ubuntu' / name for name in
                        ('frida-compat.c', 'frida-compat.map', 'legacy-stat.c', 'legacy-shm.c')})
        scripts.update({name: ROOT / 'tests/integration/dof' / name for name in
                        ('server_runtime_probe.js', 'server_raid_config_probe.js', 'server_feature_probe.js', 'server_equipment_probe.js')})
        for name, path in scripts.items():
            shutil.copyfile(path, working / name)
            (working / name).chmod(0o400)
        if runtime is not None:
            import sys
            sys.path.insert(0, str(ROOT / 'services/game/runtime/ubuntu'))
            from gateway.assets import verify
            runtime = verify(runtime)
            shutil.copytree(runtime, working / 'server-runtime')
            derivation = module_contract(runtime)
            abi = derivation['lib/libdof-plugin.so'].get('dynamicNames', [])
            probe = working / 'server_runtime_probe.js'
            probe.chmod(0o600)
            retired={name:derivation['lib/'+name].get('retiredLiterals',[])
                     for name in ('libdof-plugin.so','libdof-inline.so')}
            probe.write_text('var dofExpectedAbiNames = ' + json.dumps(abi) + ';\n' +
                             'var dofExpectedRetiredLiterals = '+json.dumps(retired)+';\n'+probe.read_text())
            probe.chmod(0o400)
            shutil.copytree(ROOT / 'services/game/runtime/ubuntu/gateway', working / 'production/gateway',
                            ignore=shutil.ignore_patterns('__pycache__', 'manifests'))
            shutil.copyfile(ROOT / 'services/game/docker/ubuntu/run-game', working / 'production/run-game')
        record = dict(schema=1, releaseReady=False, serverAccessed=False, reusedReadOnlyAssetLinks=reuse_assets,
                      assetLockSha256=digest(LOCK),
                      scripts={name: digest(working / name) for name in scripts})
        (working / 'probe-source.json').write_text(json.dumps(record, indent=2) + '\n')
        # The output becomes visible only after all hashes and copies succeed.
        if output.exists() or output.is_symlink():
            raise ValueError('Output appeared during staging')
        working.rename(output)
    return record


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--source', required=True, type=Path)
    parser.add_argument('--output', required=True, type=Path)
    parser.add_argument('--runtime', type=Path, required=True, help='Formal derived server bundle')
    parser.add_argument('--reuse-assets', action='store_true', help='Link a prior read-only fixture asset set; all hashes remain verified')
    args = parser.parse_args()
    stage(args.source, args.output, args.runtime, args.reuse_assets)
    print('Prepared offline integration inputs; not an installable release: ' + str(args.output))


if __name__ == '__main__':
    main()
