"""Compile isolated admin Java sources using an already provisioned JDK/dependencies.

Runs locally; does not download dependencies or contact/deploy to any server.
Explicit parameter metadata is required by legacy Spring HTTP argument binding.
"""
import argparse
import json
import os
from pathlib import Path
import subprocess


def compile_sources(javac, dependencies, output):
    repo = Path(__file__).resolve().parents[2]
    source = repo / 'services/admin/backend/src/main/java'
    files = sorted(source.rglob('*.java'))
    if output.exists():
        raise ValueError('Use a new classes directory; stale classes must not enter a build')
    jars = sorted(dependencies.glob('*.jar'))
    if not jars or not files:
        raise ValueError('Java sources or provisioned dependencies are missing')
    output.mkdir(parents=True)
    subprocess.run([str(javac), '-encoding', 'UTF-8', '-source', '8', '-target', '8',
                    '-parameters', '-g', '-cp', os.pathsep.join(map(str, jars)),
                    '-d', str(output)] + list(map(str, files)), check=True)
    return {'sources': len(files), 'parameterMetadata': True, 'output': str(output)}


if __name__ == '__main__':
    parser = argparse.ArgumentParser(description=__doc__)
    for name in ('javac', 'dependencies', 'output'):
        parser.add_argument('--' + name, type=Path, required=True)
    args = parser.parse_args()
    print(json.dumps(compile_sources(args.javac, args.dependencies, args.output)))
