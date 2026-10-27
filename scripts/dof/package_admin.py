"""Package already compiled local artifacts; never connects to a server.

No game keys, generated client configuration, or private workspace files are inputs.
The original Spring Boot archive contributes only its loader and dependency jars.
"""
import argparse
import hashlib
import json
from pathlib import Path
import shutil
import zipfile


def package(base_jar, classes, resources, frontend, output):
    if output.exists():
        raise ValueError('Output directory already exists; use a new build directory')
    if not (classes / 'com/aiyi/game/dnfserver/controller/DofAdminController.class').is_file():
        raise ValueError('Compiled Dof controller is missing')
    if not (frontend / 'index.html').is_file():
        raise ValueError('Compiled frontend is missing')
    output.mkdir(parents=True)
    with zipfile.ZipFile(base_jar) as source, zipfile.ZipFile(output / 'app.jar', 'w') as target:
        for item in source.infolist():
            if item.filename.startswith('BOOT-INF/classes/'):
                continue
            target.writestr(item, source.read(item))
        # Boot 2's JarLauncher discovers this directory entry, not just its children.
        # Omitting it makes otherwise present application classes invisible to the loader.
        directories = set()

        def directory_entry(name):
            if name in directories:
                return
            directory = zipfile.ZipInfo(name, date_time=(2026, 1, 1, 0, 0, 0))
            directory.external_attr = (0o40755 << 16) | 0x10
            target.writestr(directory, b'')
            directories.add(name)

        directory_entry('BOOT-INF/classes/')
        written = set()
        for root in (classes, resources):
            for path in sorted(root.rglob('*')):
                if not path.is_file():
                    continue
                relative = path.relative_to(root).as_posix()
                if any(part.startswith('.') for part in path.relative_to(root).parts):
                    continue
                # Test classes and credentials must not be included in the application.
                if relative.endswith('Test.class') or 'Test$' in relative or any(
                    suffix.lower() in ('.key', '.pem', '.p12', '.pfx') for suffix in path.suffixes
                ):
                    continue
                archive = 'BOOT-INF/classes/' + relative
                if archive in written:
                    raise ValueError('Duplicate compiled/resource path: ' + relative)
                written.add(archive)
                # Spring's package scanner also discovers package/resource directory URLs.
                parts = archive.split('/')
                for count in range(3, len(parts)):
                    directory_entry('/'.join(parts[:count]) + '/')
                target.write(path, archive, compress_type=zipfile.ZIP_DEFLATED)
    shutil.copytree(frontend, output / 'static')
    repo = Path(__file__).resolve().parents[2]
    shutil.copy(repo / 'services/admin/docker/dof-entrypoint.sh', output)
    shutil.copy(repo / 'services/admin/docker/dof-nginx.conf.template', output)
    shutil.copy(repo / 'services/admin/docker/Dockerfile.dof-prebuilt', output / 'Dockerfile')
    hashes = {p.relative_to(output).as_posix(): hashlib.sha256(p.read_bytes()).hexdigest()
              for p in sorted(output.rglob('*')) if p.is_file()}
    (output / 'build-manifest.json').write_text(json.dumps({
        'adapter': 'dof-native-1', 'environment': 'isolated',
        'completeReplacement': False, 'files': hashes}, indent=2) + '\n')
    return hashes


def main():
    p = argparse.ArgumentParser(description=__doc__)
    for name in ('base-jar', 'classes', 'resources', 'frontend', 'output'):
        p.add_argument('--' + name, required=True, type=Path)
    a = p.parse_args()
    hashes = package(a.base_jar, a.classes, a.resources, a.frontend, a.output)
    print(json.dumps({'output': str(a.output), 'files': len(hashes), 'appSHA256': hashes['app.jar']}))


if __name__ == '__main__':
    main()
