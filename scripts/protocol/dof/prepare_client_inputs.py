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
import sys
import uuid
from pathlib import Path

ROOT = Path(__file__).resolve().parents[3]
sys.path.insert(0, str(ROOT))
from scripts.dof.client_reader import derive, SOURCE_SHA256, DLL_SHA256, RECIPE
from scripts.protocol.dof.client_format import import_legacy, VERSION

PINNED_BINARIES = {
    'dof-launcher.exe': 'da078f1883a83c64438fdaf96d7147770254d7b63310f74a5c31d5bd512b2554',
    'ijl15.dll': 'dd5b3a9be419b910fb82f01d432fdf9a12d374d491c554e10103ba345942f66b',
}
SOURCE_BINARIES = {'dof-launcher.exe': ('启动器.exe', SOURCE_SHA256), 'ijl15.dll': ('ijl15.dll', DLL_SHA256)}


def prepare(client, public_key, workspace, instance, public_sha256, database_uuid, endpoint):
    if not re.fullmatch(r'[a-z0-9][a-z0-9-]{2,63}', instance):
        raise ValueError('Invalid isolated instance ID')
    if not re.fullmatch(r'[0-9a-f]{64}', public_sha256):
        raise ValueError('A previously verified game public-key SHA-256 is required')
    if str(uuid.UUID(database_uuid)) != database_uuid:
        raise ValueError('Explicit current database UUID required')
    if workspace.exists() or workspace.is_symlink():
        raise ValueError('Workspace already exists; existing jobs/inputs will not be overwritten')
    data = {}
    sources = {'template.dll': client / 'qingkong.dll', 'game-public.der': public_key,
               **{name: client / original for name, (original, _) in SOURCE_BINARIES.items()}}
    for name, source in sources.items():
        limit = 4096 if name == 'game-public.der' else 1024 * 1024 if name == 'template.dll' else 64 * 1024 * 1024
        if source.is_symlink() or not source.is_file() or source.stat().st_size > limit:
            raise ValueError('Missing, linked or oversized publication input: ' + name)
        with source.open('rb') as stream:
            value = stream.read(limit + 1)
        if len(value) > limit:
            raise ValueError('Publication input changed size: ' + name)
        digest = hashlib.sha256(value).hexdigest()
        expected = public_sha256 if name == 'game-public.der' else SOURCE_BINARIES.get(name, ('', None))[1]
        if expected and digest != expected:
            raise ValueError('Publication input does not match the paired identity/version: ' + name)
        if name == 'game-public.der' and (not value.startswith(b'\x30\x82') or b'PRIVATE KEY' in value):
            raise ValueError('Supply the verified DER public key, never a server private key')
        data[name] = value
    for name in PINNED_BINARIES:
        data[name], report = derive(data[name])
        if report['file'] != name or report['outputSha256'] != PINNED_BINARIES[name]:
            raise ValueError('Reader derivation changed; invalidate acceptance and update the pinned recipe')
    data['template.dll'] = import_legacy(data['template.dll'], data['game-public.der'], endpoint)
    workspace.mkdir(mode=0o700)
    try:
        inputs = workspace / 'inputs'; inputs.mkdir(mode=0o700)
        binding = {'instanceId': instance, 'environment': 'isolated',
                   'adapterVersion': 'dof-native-1', 'databaseUuid': database_uuid,
                   'clientFormatVersion': VERSION, 'readerRecipe': RECIPE,
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
    parser.add_argument('--database-uuid', required=True)
    parser.add_argument('--endpoint', required=True)
    args = parser.parse_args()
    print(json.dumps(prepare(args.client, args.public_key, args.workspace, args.instance,
                             args.public_sha256, args.database_uuid, args.endpoint), ensure_ascii=False, indent=2))
