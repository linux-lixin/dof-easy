"""Check real compiled inputs twice without starting Spring or connecting to a DB.

Only copies are retimestamped. The original inputs and deployed containers remain
untouched. The Java probe loads classes without initializing them.
"""
import argparse
import hashlib
import json
import os
from pathlib import Path
import shutil
import subprocess
import sys
import zipfile

ROOT = Path(__file__).resolve().parents[3]
sys.path.insert(0, str(ROOT))
from scripts.dof import package_admin, jar_delta


def sha(path):
    return hashlib.sha256(path.read_bytes()).hexdigest()


def verify(base, classes, resources, frontend, java_home, output):
    if output.exists():
        raise ValueError('Use a new output directory')
    required = sum(p.stat().st_size for folder in (classes, resources, frontend)
                   for p in folder.rglob('*') if p.is_file())
    if shutil.disk_usage(output.parent).free < 2 * 1024**3 + 4 * (required + base.stat().st_size):
        raise ValueError('Insufficient space for two packages, delta reconstruction and 2 GiB reserve')
    output.mkdir()
    copied = [output / name for name in ('classes', 'resources', 'frontend')]
    for source, target in zip((classes, resources, frontend), copied):
        shutil.copytree(source, target)
    first, second = output / 'first', output / 'second'
    first_hashes = package_admin.package(base, *copied, first)
    for folder in copied:
        for path in folder.rglob('*'):
            os.utime(path, (946684800, 946684800))
            if path.is_file():
                path.chmod(0o600)
    second_hashes = package_admin.package(base, *copied, second)
    assert first_hashes == second_hashes
    files = sorted(p.relative_to(first).as_posix() for p in first.rglob('*') if p.is_file())
    assert files == sorted(p.relative_to(second).as_posix() for p in second.rglob('*') if p.is_file())
    for name in files:
        left, right = first / name, second / name
        assert left.read_bytes() == right.read_bytes()
        assert left.stat().st_mode == right.stat().st_mode
        assert left.stat().st_mtime_ns == right.stat().st_mtime_ns
    runtime_classes = []
    with zipfile.ZipFile(first / 'app.jar') as jar, zipfile.ZipFile(base) as original:
        assert jar.testzip() is None
        assert all(info.date_time == package_admin.ZIP_TIMESTAMP for info in jar.infolist())
        for info in original.infolist():
            if info.filename.startswith('BOOT-INF/classes/'):
                continue
            assert jar.read(info.filename) == original.read(info)
            assert jar.getinfo(info.filename).compress_type == info.compress_type
        for name in jar.namelist():
            if name.startswith('BOOT-INF/classes/') and name.endswith('.class'):
                relative = name[len('BOOT-INF/classes/'):]
                assert jar.read(name) == (classes / relative).read_bytes()
                runtime_classes.append(relative[:-6].replace('/', '.'))
    probe = output / 'loader-probe'
    probe.mkdir()
    (probe / 'classes.txt').write_text('\n'.join(runtime_classes) + '\n')
    (probe / 'PackageSmoke.java').write_text('''
import java.nio.file.*;
public class PackageSmoke {
    public static void main(String[] args) throws Exception {
        ClassLoader loader = Thread.currentThread().getContextClassLoader();
        int count = 0;
        for (String name : Files.readAllLines(Paths.get(args[0]))) {
            Class.forName(name, false, loader);
            count++;
        }
        if (loader.getResource("dof/scope.json") == null) throw new Exception("Missing scope");
        System.out.println("LOADED_WITHOUT_INITIALIZATION=" + count);
    }
}
''')
    subprocess.run([str(java_home / 'bin/javac'), '-d', str(probe), str(probe / 'PackageSmoke.java')], check=True)
    loaded = subprocess.check_output([
        str(java_home / 'bin/java'), '-Dloader.path=' + str(probe), '-Dloader.main=PackageSmoke',
        '-cp', str(first / 'app.jar'), 'org.springframework.boot.loader.PropertiesLauncher',
        str(probe / 'classes.txt')], text=True, stderr=subprocess.STDOUT, timeout=60).strip()
    assert loaded == 'LOADED_WITHOUT_INITIALIZATION=' + str(len(runtime_classes))
    delta = jar_delta.prepare(base, first / 'app.jar', output / 'delta')
    jar_delta.apply(base, output / 'delta', output / 'reconstructed.jar')
    assert sha(output / 'reconstructed.jar') == sha(first / 'app.jar')
    result = {'passed': True, 'baseJarSHA256': sha(base), 'appSHA256': sha(first / 'app.jar'),
              'buildManifestSHA256': sha(first / 'build-manifest.json'), 'outputFiles': len(files),
              'runtimeClassesLoadedWithoutInitialization': len(runtime_classes),
              'sameBytesDespiteChangedInputMetadata': True, 'nestedJarBytesAndCompressionPreserved': True,
              'deltaRoundTripVerified': True, 'deltaTransferBytes': delta['transferBytes'],
              'springStarted': False, 'databaseConnected': False, 'serverDeployed': False,
              'sourceCompilationReproduced': False, 'ociImageReproduced': False,
              'completeReplacement': False}
    (output / 'result.json').write_text(json.dumps(result, indent=2) + '\n')
    return result


if __name__ == '__main__':
    parser = argparse.ArgumentParser(description=__doc__)
    for name in ('base', 'classes', 'resources', 'frontend', 'java-home', 'output'):
        parser.add_argument('--' + name, required=True, type=Path)
    args = parser.parse_args()
    print(json.dumps(verify(args.base.resolve(), args.classes.resolve(), args.resources.resolve(),
                            args.frontend.resolve(), args.java_home.resolve(), args.output.resolve())))
