"""Read the locked original admin PE; never load, execute, or modify it.

Resource strings and PE metadata are evidence about the file, not about native
business behavior. No packer name is guessed from incidental byte signatures.
"""
import argparse
import hashlib
import json
from pathlib import Path
import struct

ROOT = Path(__file__).resolve().parents[2]
LOCK = ROOT / 'metadata/dof/admin-baseline.lock.json'
TOOL = '晴空单机后台工具.exe'
RESOURCE_TYPES = {2: 'bitmap', 3: 'icon', 4: 'menu', 5: 'dialog',
                  6: 'string', 10: 'rcdata', 14: 'group-icon', 16: 'version', 24: 'manifest'}


class PE:
    def __init__(self, data):
        self.data = data
        if self.take(0, 2) != b'MZ':
            raise ValueError('Not a PE executable')
        pe = self.unpack('<I', 60)[0]
        if self.take(pe, 4) != b'PE\0\0':
            raise ValueError('Missing PE signature')
        self.machine, count, _, _, self.symbols, optional_size, _ = self.unpack('<HHIIIHH', pe + 4)
        optional = pe + 24
        magic = self.unpack('<H', optional)[0]
        directory = {0x10b: 96, 0x20b: 112}.get(magic)
        if directory is None or optional_size < directory + 15 * 8 or not 1 <= count <= 96:
            raise ValueError('Unsupported PE headers')
        self.take(optional, optional_size)
        if self.unpack('<I', optional + directory - 4)[0] < 15:
            raise ValueError('Missing PE data directories')
        self.entry = self.unpack('<I', optional + 16)[0]
        self.clr = self.unpack('<II', optional + directory + 14 * 8)
        self.resources = self.unpack('<II', optional + directory + 2 * 8)
        self.sections = []
        for i in range(count):
            at = optional + optional_size + i * 40
            raw = self.take(at, 40)
            virtual_size, rva, size, offset = struct.unpack_from('<IIII', raw, 8)
            flags = struct.unpack_from('<I', raw, 36)[0]
            self.take(offset, size)
            self.sections.append(dict(name=raw[:8].split(b'\0')[0].decode('ascii', errors='replace'),
                                      rva=rva, virtualSize=virtual_size, rawSize=size,
                                      fileOffset=offset, executable=bool(flags & 0x20000000)))

    def take(self, at, size):
        if at < 0 or size < 0 or at > len(self.data) or size > len(self.data) - at:
            raise ValueError('PE data outside file bounds')
        return self.data[at:at + size]

    def unpack(self, fmt, at):
        return struct.unpack(fmt, self.take(at, struct.calcsize(fmt)))

    def offset(self, rva, size):
        matches = [s['fileOffset'] + rva - s['rva'] for s in self.sections
                   if s['rva'] <= rva and size <= s['rawSize'] - (rva - s['rva'])]
        if len(matches) != 1:
            raise ValueError('RVA has no unique file-backed section')
        self.take(matches[0], size)
        return matches[0]

    def resource_leaves(self):
        rva, size = self.resources
        if not rva and not size:
            return []
        base = self.offset(rva, size)
        result = []
        active = set()
        visited = set()

        def read(relative, count):
            if relative < 0 or count > size - relative:
                raise ValueError('Resource outside resource directory')
            return self.take(base + relative, count)

        def walk(relative, path):
            if relative in active or relative in visited or len(path) > 3:
                raise ValueError('Cyclic, repeated, or excessive resource directory')
            active.add(relative); visited.add(relative)
            _, _, _, _, named, ids = struct.unpack('<IIHHHH', read(relative, 16))
            if named + ids > 4096 or len(visited) > 4096:
                raise ValueError('Excessive resource entries')
            for i in range(named + ids):
                name, child = struct.unpack('<II', read(relative + 16 + i * 8, 8))
                if name & 0x80000000:
                    pointer = name & 0x7fffffff
                    length = struct.unpack('<H', read(pointer, 2))[0]
                    name = read(pointer + 2, length * 2).decode('utf-16le')
                new_path = path + [name]
                if child & 0x80000000:
                    walk(child & 0x7fffffff, new_path)
                else:
                    data_rva, length, codepage, _ = struct.unpack('<IIII', read(child, 16))
                    if len(new_path) != 3:
                        raise ValueError('Unexpected resource leaf depth')
                    result.append((new_path, codepage, self.take(self.offset(data_rva, length), length)))
            active.remove(relative)
        walk(0, [])
        return result


def version_info(payload):
    strings = {}
    fixed = {}
    nodes = 0

    def walk(at, limit, depth):
        nonlocal nodes
        nodes += 1
        if depth > 8 or nodes > 1024 or at + 6 > limit:
            raise ValueError('Malformed version resource')
        length, value_length, kind = struct.unpack_from('<HHH', payload, at)
        end = at + length
        if length < 6 or end > limit:
            raise ValueError('Version block outside resource bounds')
        key_end = at + 6
        while key_end + 2 <= end and payload[key_end:key_end + 2] != b'\0\0':
            key_end += 2
        if key_end + 2 > end:
            raise ValueError('Unterminated version key')
        key = payload[at + 6:key_end].decode('utf-16le')
        value = (key_end + 2 + 3) & ~3
        value_end = value + value_length * (2 if kind == 1 else 1)
        if value_length and value_end > end:
            raise ValueError('Version value outside resource bounds')
        if depth == 0:
            if key != 'VS_VERSION_INFO' or value_length != 52 or kind != 0:
                raise ValueError('Unsupported version root')
            fields = struct.unpack_from('<13I', payload, value)
            if fields[0] != 0xfeef04bd:
                raise ValueError('Missing fixed version signature')
            for name, ms, ls in [('fileVersion', fields[2], fields[3]), ('productVersion', fields[4], fields[5])]:
                fixed[name] = '.'.join(map(str, (ms >> 16, ms & 65535, ls >> 16, ls & 65535)))
        elif kind == 1 and value_length:
            if key in strings:
                raise ValueError('Ambiguous duplicate version string')
            strings[key] = payload[value:value_end].decode('utf-16le').rstrip('\0')
        child = (value_end + 3) & ~3
        while child + 6 <= end:
            child = (walk(child, end, depth + 1) + 3) & ~3
        return end
    walk(0, len(payload), 0)
    return dict(fixed=fixed, strings=strings)


def inspect(data):
    pe = PE(data)
    leaves = []
    strings = []
    versions = []
    for path, codepage, payload in pe.resource_leaves():
        kind, name, language = path
        leaves.append(dict(type=RESOURCE_TYPES.get(kind, kind), name=name, language=language,
                           codepage=codepage, bytes=len(payload), sha256=hashlib.sha256(payload).hexdigest()))
        if kind == 16:
            versions.append(dict(language=language, **version_info(payload)))
        if kind == 6 and isinstance(name, int):
            at = 0
            for index in range(16):
                if at + 2 > len(payload):
                    raise ValueError('Truncated resource string table')
                length = struct.unpack_from('<H', payload, at)[0]; at += 2
                end = at + length * 2
                if end > len(payload):
                    raise ValueError('Truncated resource string')
                if length:
                    strings.append(dict(id=(name - 1) * 16 + index, language=language,
                                        text=payload[at:end].decode('utf-16le')))
                at = end
    empty = [s['name'] for s in pe.sections if s['virtualSize'] and not s['rawSize']]
    entry = [s['name'] for s in pe.sections if s['rva'] <= pe.entry < s['rva'] + max(s['virtualSize'], s['rawSize'])]
    return dict(machine=hex(pe.machine), clrDirectory=list(pe.clr), coffSymbolCount=pe.symbols,
                entryRva=pe.entry, entrySections=entry, sections=pe.sections,
                resourceLeaves=leaves, stringTableEntries=strings, versionResources=versions,
                protectionIndicators=dict(virtualSectionsWithoutFileData=empty,
                                          codeNotDirectlyFileBacked=bool('.text' in empty),
                                          exactProtectorIdentified=False))


def audit(path, lock):
    path = Path(path)
    if path.is_symlink() or not path.is_file():
        raise ValueError('Expected a regular original tool file')
    rows = [r for r in lock['files'] if r['role'] == 'tool' and r['path'] == TOOL]
    if len(rows) != 1:
        raise ValueError('Original tool lock missing or duplicated')
    data = path.read_bytes(); digest = hashlib.sha256(data).hexdigest()
    if len(data) != rows[0]['bytes'] or digest != rows[0]['sha256']:
        raise ValueError('Tool differs from locked original; audit stopped')
    return dict(schema=1, method='static-file-only', edition=lock['edition'],
                input=dict(path=TOOL, bytes=len(data), sha256=digest, sourceMatched=True),
                pe=inspect(data), executableLoaded=False, sourceCodeRecovered=False,
                nativeMenuAuditVerified=False, behaviorEquivalenceVerified=False,
                limitations=['Resource presence does not prove menu coverage or defaults.',
                             'Packed code and runtime-built SQL are not recovered by this audit.',
                             'The edition name comes from the lock, not a verified runtime version screen.'])


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--tool', type=Path, required=True)
    parser.add_argument('--lock', type=Path, default=LOCK)
    parser.add_argument('--output', type=Path, required=True)
    args = parser.parse_args()
    report = audit(args.tool, json.loads(args.lock.read_text()))
    args.output.write_text(json.dumps(report, ensure_ascii=False, indent=2) + '\n')
    print(json.dumps(dict(sourceMatched=True, resourceLeaves=len(report['pe']['resourceLeaves']),
                          resourceStrings=len(report['pe']['stringTableEntries']),
                          sourceCodeRecovered=False, behaviorEquivalenceVerified=False)))


if __name__ == '__main__':
    main()
