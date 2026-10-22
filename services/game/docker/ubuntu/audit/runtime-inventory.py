#!/usr/bin/env python3
"""Read-only Ubuntu trusted-image inventory.

Run in a disposable image container with this directory mounted at /audit.
No packages are installed and no application services are started.
"""
import argparse
import hashlib
import json
import os
import re
import subprocess


def run(arguments, env=None):
    process = subprocess.Popen(arguments, stdout=subprocess.PIPE,
                               stderr=subprocess.STDOUT, env=env)
    output = process.communicate()[0]
    return output.decode('utf-8', 'replace').strip()


def digest(path):
    checksum = hashlib.sha256()
    with open(path, 'rb') as stream:
        for block in iter(lambda: stream.read(1024 * 1024), b''):
            checksum.update(block)
    return checksum.hexdigest()


def write_json(path, data):
    with open(path, 'w') as stream:
        json.dump(data, stream, indent=2, sort_keys=True)
        stream.write('\n')


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--prefix', required=True)
    parser.add_argument('--image-id', required=True)
    parser.add_argument('--compat', default='')
    args = parser.parse_args()
    files = ['/TeaEncrypt']
    for directory, _, names in os.walk('/home/template'):
        files.extend(os.path.join(directory, name) for name in names)
    records, libraries = [], {}
    for path in sorted(files):
        if not os.path.isfile(path):
            continue
        with open(path, 'rb') as stream:
            head = stream.read(20)
        if head[:4] != b'\x7fELF':
            continue
        elf_class = bytearray(head)[4]
        arch = 'i386' if elf_class == 1 else 'amd64'
        headers = run(['readelf', '-lW', path])
        dynamic = run(['readelf', '-dW', path])
        version = run(['readelf', '--version-info', '-W', path])
        interpreter = re.search(r'Requesting program interpreter: (.*?)\]', headers)
        environment = dict(os.environ)
        for key in ('LD_PRELOAD', 'LD_LIBRARY_PATH', 'LD_TRACE_LOADED_OBJECTS'):
            environment.pop(key, None)
        if args.compat:
            environment['LD_LIBRARY_PATH'] = args.compat + '/' + arch
        resolution = run(['ldd', '-r', path], env=environment)
        record = dict(path=path, sha256=digest(path), architecture=arch,
                      interpreter=interpreter.group(1) if interpreter else None,
                      needed=re.findall(r'Shared library: \[(.*?)\]', dynamic),
                      symbol_versions=sorted(set(re.findall(r'(?:GLIBCXX|GLIBC|CXXABI|GCC)_[\d.]+', version))),
                      resolution=resolution)
        resolved = sorted(set(re.findall(r'(?:=>\s+|^\s*)(/[^\s]+)\s+\(', resolution, re.M)))
        record['resolved_library_paths'] = resolved
        for library in resolved:
            if library in libraries or not os.path.isfile(library):
                continue
            actual = os.path.realpath(library)
            owner = run(['dpkg-query', '-S', actual])
            libraries[library] = dict(realpath=actual, sha256=digest(actual), package_owner=owner)
        records.append(record)
    packages = run(['dpkg-query', '-W'])
    output = '/audit/' + args.prefix
    with open(output + '-packages.tsv', 'w') as stream:
        stream.write('\n'.join(sorted(packages.splitlines())) + '\n')
    with open('/etc/os-release') as stream:
        os_release = stream.read()
    write_json(output + '-elf.json', dict(image_id=args.image_id, os_release=os_release,
                                         compatibility_path=args.compat or None, files=records))
    write_json(output + '-libraries.json', dict(image_id=args.image_id, libraries=libraries))
    print('%s: %d ELF files, %d distinct resolved library paths' % (args.prefix, len(records), len(libraries)))


if __name__ == '__main__':
    main()
