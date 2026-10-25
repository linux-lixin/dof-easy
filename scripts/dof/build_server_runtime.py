"""Build the installer's server runtime from pinned replacement inputs, offline.

Original names exist only at this explicit import boundary. No binaries are
executed and no keys, client tools or incidental source files are published.
"""
import argparse
import hashlib
import json
from pathlib import Path
import re
import shutil
import struct
import sys
import tempfile

ROOT = Path(__file__).resolve().parents[2]
sys.path.insert(0, str(ROOT))
from scripts.dof.native_candidates import (Elf, replacement_source, gateway_candidate,
    plugin_candidate, inline_candidate, GATEWAY_PIN, PLUGIN_PIN, INLINE_PIN)

INPUTS = ROOT / 'metadata/dof/source-audit/server-inputs/replacement.lock.json'
DEFAULTS = ROOT / 'services/game/runtime/ubuntu/gateway/defaults'
BASE_SCHEMA_PIN = '440604c1e7e786012abab3e69efac077a48da9d7ceae1ab43394a8eed4bc504b'


def sha(raw):
    return hashlib.sha256(raw).hexdigest()


def schema(raw):
    at = raw.index(b'CREATE TABLE `billing`')
    start, end = raw.rfind(b'\0', 0, at) + 1, raw.index(b'\0', at)
    block = raw[start:end]
    if sha(block) != BASE_SCHEMA_PIN:
        raise ValueError('Unrecognized native schema input')
    statements = []
    for statement in block.decode().split(';'):
        statement = statement.strip()
        if not re.match(r'^(CREATE TABLE|INSERT INTO)\s', statement):
            continue
        statement = statement.replace('qingkong_config', 'config')
        statement = re.sub(r'\bqk\b', 'dof_native', statement)
        statements.append(statement + ';')
    if sum(s.startswith('CREATE TABLE') for s in statements) != 20:
        raise ValueError('Unexpected native base schema')
    # Feature tables are absent from the gateway's embedded base schema.
    # Ship empty definitions; never import old rewards, player rows or counters.
    feature = (ROOT / 'services/game/runtime/ubuntu/gateway/feature-schema.sql').read_bytes()
    return ('\n'.join(statements) + '\n').encode() + feature


def normalize_uv(raw):
    """Remove the pinned unused NIS dependency without changing any other tag."""
    elf, data = Elf(raw), bytearray(raw)
    dynamic, = (p for p in elf.segments if p['type'] == 2)
    stride = 16 if elf.wide else 8
    fmt = '<QQ' if elf.wide else '<II'
    entries = []
    removed = 0
    for offset in range(dynamic['offset'], dynamic['offset'] + dynamic['fileSize'], stride):
        tag, value = struct.unpack_from(fmt, raw, offset)
        if not tag:
            break
        if tag == 1:
            at = elf.offset(elf.tags[5] + value)
            if raw[at:raw.index(b'\0', at)] == b'libnsl.so.1':
                removed += 1
                continue
        entries.append(struct.pack(fmt, tag, value))
    if removed != 1:
        raise ValueError('Unexpected pinned libuv dependency layout')
    result = b''.join(entries) + bytes(stride)
    start = dynamic['offset']
    data[start:start + dynamic['fileSize']] = result.ljust(dynamic['fileSize'], b'\0')
    return bytes(data)


def build(source, output):
    source, output = Path(source).absolute(), Path(output).absolute()
    if source != source.resolve(strict=True) or output.parent != output.parent.resolve():
        raise ValueError('Source and output ancestors must be real directories')
    if not source.is_dir() or output.exists() or output.is_symlink():
        raise ValueError('Use an existing local source and a new output directory')
    if output.is_relative_to(source) or source.is_relative_to(output):
        raise ValueError('Source and output must be separate')
    rows = json.loads(INPUTS.read_text())['files']
    verified = {}
    for row in rows:
        path = source / row['path']
        if any(p.is_symlink() for p in (path, *path.parents)):
            raise ValueError('Source links are not accepted')
        raw = replacement_source(path, row['sha256'])
        if len(raw) != row['bytes'] or raw[4] != row['elf_class']:
            raise ValueError('Wrong source architecture or size')
        verified[row['path']] = raw
    payload, derivations = {}, {}
    for old, new, derive in [('QK', 'bin/dof-gateway', gateway_candidate),
                            ('qk_plugin.so', 'lib/libdof-plugin.so', plugin_candidate),
                            ('libantisvrinline.so', 'lib/libdof-inline.so', inline_candidate)]:
        payload[new], derivations[new] = derive(verified[old])
    for name, raw in verified.items():
        if name.startswith('qingkong/'):
            payload['lib/' + Path(name).name] = normalize_uv(raw) if Path(name).name == 'libuv.so.1' else raw
    payload['runtime/gateway/schema.sql'] = schema(verified['QK'])
    payload['runtime/gateway/equipment.js'] = (ROOT / 'services/game/runtime/ubuntu/gateway/equipment.js').read_bytes()
    payload['runtime/gateway/equipment-abi.json'] = (json.dumps(
        derivations['lib/libdof-plugin.so']['equipment'], sort_keys=True) + '\n').encode()
    manifest = dict(schema=2, artifact='dof-server-runtime', sourceEdition='replacement-only',
        files=[dict(path=name, bytes=len(raw), sha256=sha(raw),
                    **({'elf_class':raw[4]} if raw[:4] == b'\x7fELF' else {}))
               for name, raw in sorted(payload.items())])
    output.parent.mkdir(parents=True, exist_ok=True)
    with tempfile.TemporaryDirectory(prefix='.dof-runtime-', dir=output.parent) as temporary:
        stage = Path(temporary) / 'bundle'; stage.mkdir(mode=0o700)
        for name, raw in payload.items():
            path = stage / name; path.parent.mkdir(parents=True, exist_ok=True)
            path.write_bytes(raw); path.chmod(0o700 if name.startswith('bin/') else 0o600)
        config = stage / 'config'; config.mkdir(mode=0o700)
        for name in ['plugin.ini', 'anton-raid.ini', 'luke-raid.ini', 'dungeon.lst', 'equipment.json']:
            (config / name).write_bytes((DEFAULTS / name).read_bytes())
            (config / name).chmod(0o600)
        (stage / 'manifest.json').write_text(json.dumps(manifest, indent=2) + '\n')
        audit = stage / 'source-audit'; audit.mkdir()
        (audit / 'derivation.json').write_text(json.dumps(dict(
            sourceManifestSha256=sha(INPUTS.read_bytes()), sourceFiles=rows,
            recipeSha256=sha((ROOT / 'scripts/dof/native_candidates.py').read_bytes()),
            recipeDependencies={name:sha((ROOT / 'scripts/dof' / name).read_bytes())
                                for name in ('native_symbols.py', 'native_sql.py', 'native_equipment.py', 'native_residuals.py')},
            retirementEvidenceSha256=sha((ROOT/'metadata/dof/source-audit/gateway-retired-stack-constants.json').read_bytes()),
            builderSha256=sha(Path(__file__).read_bytes()), derivations=derivations,
            functionalAcceptance='requires local verification of these exact hashes'), indent=2) + '\n')
        if output.exists() or output.is_symlink():
            raise ValueError('Output appeared during preparation')
        stage.rename(output)
    return manifest


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--source', type=Path, required=True)
    parser.add_argument('--output', type=Path, required=True)
    args = parser.parse_args()
    result = build(args.source, args.output)
    print('Built offline server runtime:', len(result['files']), 'pinned payload files')


if __name__ == '__main__':
    main()
