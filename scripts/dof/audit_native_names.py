"""Read-only inventory of pinned native inputs; never executes or patches them.

This is a feasibility report, not proof that replacing visible strings is safe.
Exit 2 means the requested fully renamed native distribution is not certified.
"""
import argparse
import hashlib
import json
from pathlib import Path
import re
import struct

ROOT = Path(__file__).resolve().parents[2]
TOKENS = ('/root/qingkong/', 'qk_plugin.so', 'libantisvrinline.so',
          'qk_plugin_config.ini', 'qk_plugin_antonRaid.ini',
          'qk_plugin_lukeRaid.ini', 'qingkong_config', 'USE qk',
          'qingkong.dll', 'token_qingkong', 'VMProtect')


def inspect_bytes(data):
    """Report exact offsets only, without copying config values or credentials."""
    hits = {}
    for token in TOKENS:
        matches = {}
        for encoding in ('ascii', 'utf-16le'):
            offsets = [m.start() for m in re.finditer(re.escape(token.encode(encoding)), data)]
            if offsets:
                matches[encoding] = offsets
        if matches:
            hits[token] = matches
    result = {'format': 'data', 'matches': hits, 'sections': []}
    if data.startswith(b'\x7fELF'):
        if len(data) < 64 or data[4] not in (1, 2) or data[5] != 1:
            raise ValueError('Unsupported ELF header')
        wide = data[4] == 2
        header = struct.unpack_from('<HHIQQQIHHHHHH' if wide else '<HHIIIIIHHHHHH', data, 16)
        _, _, _, entry, phoff, _, _, _, phsize, phnum, *_ = header
        expected = 56 if wide else 32
        if phsize != expected or phoff + phnum * phsize > len(data):
            raise ValueError('Invalid ELF program headers')
        segments = []
        for index in range(phnum):
            fields = struct.unpack_from('<IIQQQQQQ' if wide else '<8I', data, phoff + index * phsize)
            if wide:
                kind, flags, offset, address, _, size, memory, _ = fields
            else:
                kind, offset, address, _, size, memory, flags, _ = fields
            if kind == 1:
                if offset + size > len(data):
                    raise ValueError('ELF segment extends past input')
                segments.append(dict(offset=offset, address=address, fileBytes=size,
                                     memoryBytes=memory, flags=flags))
        result.update(format='ELF64' if wide else 'ELF32', segments=segments, entry=entry)
    elif data.startswith(b'MZ'):
        if len(data) < 64:
            raise ValueError('Truncated DOS header')
        pe = struct.unpack_from('<I', data, 60)[0]
        if pe + 24 > len(data) or data[pe:pe + 4] != b'PE\0\0':
            raise ValueError('Invalid PE signature')
        machine, count, _, _, _, optsize, _ = struct.unpack_from('<HHIIIHH', data, pe + 4)
        sections = pe + 24 + optsize
        if sections + count * 40 > len(data):
            raise ValueError('Truncated PE sections')
        result.update(format='PE', machine=machine)
        for index in range(count):
            offset = sections + index * 40
            name = data[offset:offset + 8].split(b'\0', 1)[0].decode('ascii', 'replace')
            memory, address, size, raw = struct.unpack_from('<4I', data, offset + 8)
            if raw + size > len(data):
                raise ValueError('PE section extends past input')
            result['sections'].append(dict(name=name, address=address, offset=raw,
                                            fileBytes=size, memoryBytes=memory))
    return result


def inspect_file(path, expected=None):
    path = Path(path)
    if path.is_symlink() or not path.is_file():
        raise ValueError('Input must be a regular local file: ' + str(path))
    before = path.stat()
    data = path.read_bytes()
    digest = hashlib.sha256(data).hexdigest()
    if expected and (digest != expected['sha256'] or len(data) != expected['bytes']):
        raise ValueError('Native input differs from its pinned source: ' + path.name)
    result = dict(sha256=digest, bytes=len(data), pinned=expected is not None,
                  **inspect_bytes(data))
    after = path.stat()
    if (before.st_ino, before.st_size, before.st_mtime_ns) != (after.st_ino, after.st_size, after.st_mtime_ns):
        raise ValueError('Input changed during inspection')
    return result


def audit(server, tool, client, game=None):
    server, client = Path(server), Path(client)
    replacements = json.loads((ROOT / 'metadata/dof/runtime.lock.json').read_text())['files']
    pins = {row['path']: row for row in replacements}
    runtime = json.loads((ROOT / 'metadata/dof/source-audit/server-inputs/replacement.lock.json').read_text())['files']
    pins.update({row['path']: row for row in runtime})
    client_pins = json.loads((ROOT / 'metadata/dof/client.lock.json').read_text())['files']
    files = {
        'gateway': inspect_file(server / 'QK', pins['QK']),
        'plugin': inspect_file(server / 'qk_plugin.so', pins['qk_plugin.so']),
        'inline': inspect_file(server / 'libantisvrinline.so', pins['libantisvrinline.so']),
        'admin': inspect_file(tool, pins['晴空单机后台工具.exe']),
    }
    for row in client_pins:
        files[row['role']] = inspect_file(client / row['path'], row)
    files['client_config'] = inspect_file(client / 'qingkong.dll')
    if game is not None:
        files['game'] = inspect_file(game)
    return {
        'schema': 1, 'method': 'local-static-only', 'executedNativeCode': False,
        'serverAccessed': False, 'sourceFilesModified': False,
        'releaseReady': False, 'files': files,
        'blockers': [
            {'id': 'native-readers', 'status': 'unverified',
             'detail': 'No validated patches for all protected plugin/inline database and path readers.'},
            {'id': 'windows-protocol', 'status': 'unverified',
             'detail': 'No paired native launcher/loader/admin accepting dof-client.dll and token_dof.'},
            {'id': 'game-load-chain', 'status': 'unverified' if game else 'missing-input',
             'detail': 'Renamed inline library requires validation of the game and gateway rewrite together.'},
            {'id': 'end-to-end', 'status': 'not-run',
             'detail': 'Static string offsets cannot establish integrity-check or runtime compatibility.'},
        ],
    }


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    for name in ('server', 'tool', 'client', 'output'):
        parser.add_argument('--' + name, type=Path, required=True)
    parser.add_argument('--game', type=Path)
    args = parser.parse_args()
    report = audit(args.server, args.tool, args.client, args.game)
    # Never overwrite an input or existing evidence, even if output aliases it.
    with args.output.open('x', encoding='utf-8') as stream:
        json.dump(report, stream, ensure_ascii=False, indent=2)
        stream.write('\n')
    print('Native rename remains blocked; report: ' + str(args.output))
    return 2


if __name__ == '__main__':
    raise SystemExit(main())
