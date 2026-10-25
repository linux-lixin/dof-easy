"""Prepare a new, private Linux publication workspace from the paired client.

Copies four explicitly selected inputs only. Never traverses the client directory,
changes keys, modifies the client, or opens a database connection.
"""
import argparse
import hashlib
import json
import os
import re
import shutil
from pathlib import Path

PINNED_BINARIES = {
    '启动器.exe': '5b0c5195c58e1583cb5456e2b7139acf02f6ebb34ff86e4445468c91184be733',
    'ijl15.dll': 'd2b207e32f1d366c29f43d080366b7880d87659aaeb93a9bd8e36f44bf54b678',
}


def prepare(client, public_key, workspace, instance, public_sha256):
    if not re.fullmatch(r'[a-z0-9][a-z0-9-]{2,63}', instance):
        raise ValueError('Invalid isolated instance ID')
    if not re.fullmatch(r'[0-9a-f]{64}', public_sha256):
        raise ValueError('A previously verified game public-key SHA-256 is required')
    if workspace.exists() or workspace.is_symlink():
        raise ValueError('Workspace already exists; existing jobs/inputs will not be overwritten')
    data = {}
    sources = {'template.dll': client / 'qingkong.dll', 'game-public.der': public_key,
               **{name: client / name for name in PINNED_BINARIES}}
    for name, source in sources.items():
        limit = 4096 if name == 'game-public.der' else 1024 * 1024 if name == 'template.dll' else 64 * 1024 * 1024
        if source.is_symlink() or not source.is_file() or source.stat().st_size > limit:
            raise ValueError('Missing, linked or oversized publication input: ' + name)
        with source.open('rb') as stream:
            value = stream.read(limit + 1)
        if len(value) > limit:
            raise ValueError('Publication input changed size: ' + name)
        digest = hashlib.sha256(value).hexdigest()
        expected = public_sha256 if name == 'game-public.der' else PINNED_BINARIES.get(name)
        if expected and digest != expected:
            raise ValueError('Publication input does not match the paired identity/version: ' + name)
        if name == 'game-public.der' and (not value.startswith(b'\x30\x82') or b'PRIVATE KEY' in value):
            raise ValueError('Supply the verified DER public key, never a server private key')
        data[name] = value
    workspace.mkdir(mode=0o700)
    try:
        inputs = workspace / 'inputs'; inputs.mkdir(mode=0o700)
        binding = {'instanceId': instance, 'environment': 'isolated',
                   'adapterVersion': 'dof-native-1',
                   'sha256': {name: hashlib.sha256(value).hexdigest() for name, value in data.items()}}
        for name, value in data.items():
            fd = os.open(inputs / name, os.O_WRONLY | os.O_CREAT | os.O_EXCL, 0o600)
            with os.fdopen(fd, 'wb') as stream:
                stream.write(value); stream.flush(); os.fsync(stream.fileno())
        fd = os.open(inputs / 'binding.json', os.O_WRONLY | os.O_CREAT | os.O_EXCL, 0o600)
        with os.fdopen(fd, 'w', encoding='utf-8') as stream:
            json.dump(binding, stream, ensure_ascii=False, indent=2); stream.write('\n'); stream.flush(); os.fsync(stream.fileno())
    except Exception:
        # This directory was created exclusively by this call and has no prior jobs.
        shutil.rmtree(workspace)
        raise
    return {'instanceId': instance, 'environment': 'isolated', 'inputCount': len(data),
            'sourceHashes': binding['sha256'], 'workspace': str(workspace.resolve()),
            'nativeClientAcceptance': False, 'deployed': False}


if __name__ == '__main__':
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--client', type=Path, required=True)
    parser.add_argument('--public-key', type=Path, required=True)
    parser.add_argument('--public-sha256', required=True)
    parser.add_argument('--workspace', type=Path, required=True)
    parser.add_argument('--instance', required=True)
    args = parser.parse_args()
    print(json.dumps(prepare(args.client, args.public_key, args.workspace, args.instance, args.public_sha256), ensure_ascii=False, indent=2))
