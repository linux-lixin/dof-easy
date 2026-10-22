#!/usr/bin/env python3
"""Audit trusted, locally supplied game ELFs inside the target Linux image.

ldd executes the system loader: never use this on untrusted uploaded files.
Reports static requirements and resolved relocations; gameplay needs separate tests.
"""
import argparse
import hashlib
import json
import os
from pathlib import Path
import re
import subprocess


def run(*args, env=None):
    result = subprocess.run(args, text=True, stdout=subprocess.PIPE,
                            stderr=subprocess.STDOUT, env=env)
    return result.stdout.strip()


def audit(root, compat, extra=(), preload_i386=None):
    if preload_i386 is not None:
        preload_i386 = Path(preload_i386).resolve(strict=True)
        with preload_i386.open('rb') as stream:
            header = stream.read(20)
        if header[:7] != b'\x7fELF\x01\x01\x01' or header[18:20] != b'\x03\x00':
            raise ValueError('scoped preload must be a little-endian i386 ELF')
    records = []
    for path in sorted(set(root.rglob('*')) | set(extra)):
        if not path.is_file():
            continue
        with path.open('rb') as stream:
            head = stream.read(20)
        if head[:4] != b'\x7fELF':
            continue
        arch = 'i386' if head[4] == 1 else 'amd64'
        program = run('readelf', '-lW', str(path))
        dynamic = run('readelf', '-dW', str(path))
        versions = run('readelf', '--version-info', '-W', str(path))
        interp = re.search(r'Requesting program interpreter: (.*?)\]', program)
        # Do not let ambient injection alter the audit or its helper processes.
        env = {key: value for key, value in os.environ.items()
               if key not in ('LD_PRELOAD', 'LD_AUDIT', 'LD_TRACE_LOADED_OBJECTS', 'LD_WARN', 'LD_BIND_NOW')}
        env['LD_LIBRARY_PATH'] = f'{compat}/{arch}'
        relocation_status = 0
        if preload_i386 is not None and arch == 'i386':
            # These are the same trace/relocation flags used by glibc ldd -r.
            # --preload scopes the i386 shim to this loader; the 64-bit Python,
            # readelf and shell processes never load it. Constructors do not run.
            env.update(LD_TRACE_LOADED_OBJECTS='1', LD_WARN='yes', LD_BIND_NOW='yes')
            result = subprocess.run(['/lib/ld-linux.so.2', '--preload', str(preload_i386), str(path)],
                                    text=True, stdout=subprocess.PIPE, stderr=subprocess.STDOUT, env=env)
            linkage, relocation_status = result.stdout.strip(), result.returncode
        else:
            linkage = run('ldd', '-r', str(path), env=env)
        errors = [line.strip() for line in linkage.splitlines()
                  if re.search(r'not found|undefined symbol|error while loading', line)]
        if relocation_status:
            errors.append(f'relocation loader exited with status {relocation_status}')
        records.append(dict(path=str(path), sha256=hashlib.sha256(path.read_bytes()).hexdigest(),
                            architecture=arch, interpreter=interp.group(1) if interp else None,
                            needed=re.findall(r'Shared library: \[(.*?)\]', dynamic),
                            symbol_versions=sorted(set(re.findall(r'(?:GLIBCXX|GLIBC|CXXABI|GCC)_[\d.]+', versions))),
                            resolution=linkage, errors=errors,
                            preload=str(preload_i386) if preload_i386 is not None and arch == 'i386' else None))
    return records


if __name__ == '__main__':
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('root', type=Path)
    parser.add_argument('--compat', default='/opt/dnf/compat')
    parser.add_argument('--output', type=Path, required=True)
    parser.add_argument('--extra', type=Path, action='append', default=[])
    parser.add_argument('--preload-i386', type=Path, help='explicit compatibility library for i386 relocation checks only')
    args = parser.parse_args()
    records = audit(args.root, args.compat, args.extra, args.preload_i386)
    args.output.write_text(json.dumps(records, indent=2) + '\n')
    bad = [r for r in records if r['errors']]
    print(f'{len(records)} ELF files; {len(bad)} unresolved')
    for record in bad:
        print(record['path'], *record['errors'], sep='\n  ')
    raise SystemExit(bool(bad))
