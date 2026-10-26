"""Deterministic PE32 adaptation of the pinned native launcher reader.

Redirect restored literal references at their native consumers. Existing
initialization, imports, protection code and configuration decoding stay intact.
This recipe alone does not certify the game DLL or a complete login session.
"""
import argparse
import hashlib
import json
from pathlib import Path
import struct

SOURCE_SHA256 = '5b0c5195c58e1583cb5456e2b7139acf02f6ebb34ff86e4445468c91184be733'
DLL_SHA256 = 'd2b207e32f1d366c29f43d080366b7880d87659aaeb93a9bd8e36f44bf54b678'
RECIPE = 'dof-launcher-reader-2.2'
LAUNCHER_ABI = dict(file_exists=0x53330, compare=0x11ca, length=0x1267,
                    concat_return=0x12f6, getter=0x2da1a, empty=0xf22c3,
                    free=0x35660, message_box=0x165e4f4, exit_process=0x165e1bc)
DLL_ABI = dict(file_exists=0x3f1d50, compare=0x10c0, length=0x1054,
               concat_return=0x118c, getter=0x3d43ec, empty=0x4d41d4,
               free=0x3d78d8, message_box=0x4c0574, exit_process=0x4c0318)


def align(value, alignment):
    if alignment <= 0 or alignment & (alignment - 1):
        raise ValueError('Invalid PE alignment')
    return (value + alignment - 1) & -alignment


class Pe32:
    def __init__(self, raw):
        self.raw = raw
        if len(raw) < 64 or raw[:2] != b'MZ':
            raise ValueError('Not a PE image')
        self.pe = struct.unpack_from('<I', raw, 60)[0]
        if self.pe + 24 > len(raw) or raw[self.pe:self.pe + 4] != b'PE\0\0':
            raise ValueError('Invalid PE header')
        machine, self.count = struct.unpack_from('<HH', raw, self.pe + 4)
        optional_size = struct.unpack_from('<H', raw, self.pe + 20)[0]
        self.optional = self.pe + 24
        self.table = self.optional + optional_size
        if machine != 0x14c or optional_size != 224 or self.table + self.count * 40 > len(raw):
            raise ValueError('Expected an i386 PE32 image')
        if struct.unpack_from('<H', raw, self.optional)[0] != 0x10b:
            raise ValueError('Expected PE32 optional header')
        self.base = struct.unpack_from('<I', raw, self.optional + 28)[0]
        self.section_alignment, self.file_alignment = struct.unpack_from('<II', raw, self.optional + 32)
        align(0, self.section_alignment)
        align(0, self.file_alignment)
        self.sections = []
        for index in range(self.count):
            header = self.table + index * 40
            size, rva, raw_size, offset = struct.unpack_from('<4I', raw, header + 8)
            flags = struct.unpack_from('<I', raw, header + 36)[0]
            if offset + raw_size > len(raw):
                raise ValueError('Truncated PE section')
            self.sections.append(dict(header=header, size=size, rva=rva, raw_size=raw_size,
                                      offset=offset, flags=flags))

    def offset(self, rva, size=1):
        found = [s['offset'] + rva - s['rva'] for s in self.sections
                 if s['rva'] <= rva and rva + size <= s['rva'] + s['raw_size']]
        if len(found) != 1:
            raise ValueError('PE address is not uniquely file-backed')
        return found[0]

    def at(self, rva, size):
        offset = self.offset(rva, size)
        return self.raw[offset:offset + size]


class Code:
    """Small checked rel32 assembler; all new addresses are position independent."""
    def __init__(self, rva):
        self.rva, self.data, self.labels, self.fixups = rva, bytearray(), {}, []

    def emit(self, value):
        self.data.extend(bytes.fromhex(value) if isinstance(value, str) else value)

    def u32(self, value):
        self.emit(struct.pack('<I', value & 0xffffffff))

    def label(self, name):
        if name in self.labels:
            raise ValueError('Duplicate assembler label')
        self.labels[name] = self.rva + len(self.data)

    def branch(self, opcode, target):
        self.emit(opcode)
        self.fixups.append((len(self.data), target))
        self.u32(0)

    def base_in_edx(self):
        self.emit('e8000000005a81ea')  # call next; pop edx; sub edx, return RVA
        self.u32(self.rva + len(self.data) - 3)

    def finish(self):
        for at, target in self.fixups:
            destination = self.labels[target] if isinstance(target, str) else target
            struct.pack_into('<i', self.data, at, destination - (self.rva + at + 4))
        return bytes(self.data)


LITERALS = (
    (0xf2aff, b'./qingkong.dll', b'./dof-client.dll', 0),
    (0xf233f, b'qingkong.dll', b'Missing dof-client.dll configuration. Generate it in the web admin.', 0),
    (0xf28a6, b'qingkong.dll', b'dof-client.dll', 0),
    (0xf28b7, b'qingkong.dll', b'dof-client.dll configuration', 0),
    (0xf2b01, b'qingkong.dll', b'dof-client.dll', 0),
    (0xf2b2f, b'qingkong.dll', b'dof-client.dll', 0),
    (0xf2b41, b'token_qingkong+', b'token_dof+', 1),
    (0xf2b51, b'token_qingkong+(((((', b'token_dof+(((((', 1),
    (0xf2bb0, b'token_qingkong-', b'token_dof-', 1),
)
DLL_LITERALS = (
    (0x4d8c9c, b'qingkong.dll', b'dof-client.dll', 0),
    (0x4d8cac, b'token_qingkong+', b'token_dof+', 1),
    (0x4d8cbc, b'token_qingkong+(((((', b'token_dof+(((((', 1),
)


def make_code(code_rva, data_rva, rows, abi=LAUNCHER_ABI):
    c = Code(code_rva)
    # map(EAX) -> EAX; return literal kind in ECX (0=file, 1=client field,
    # -1=unmapped). Outer wrappers save every register and the flags.
    c.label('map')
    c.base_in_edx()
    c.emit('89c729d7')  # edi = input pointer - actual image base
    c.emit('8db2'); c.u32(data_rva)
    for index in range(len(rows)):
        c.emit('3bbe'); c.u32(index * 12)
        c.branch('0f85', 'map-next-' + str(index))
        c.emit('8b86'); c.u32(index * 12 + 4)
        c.emit('01d08b8e'); c.u32(index * 12 + 8)
        c.emit('c3')
        c.label('map-next-' + str(index))
    c.emit('b9ffffffffc3')

    c.label('file-exists')
    c.emit('9c608b442428')
    c.branch('e8', 'map')
    c.emit('89442428619d8b4c240481ec40010000')
    c.branch('e9', abi['file_exists'] + 10)

    c.label('compare')
    c.emit('9c608b442428')
    c.branch('e8', 'map')
    c.emit('894424288b44242c')
    c.branch('e8', 'map')
    c.emit('8944242c619d8b5424048b4c2408')
    c.branch('e9', abi['compare'] + 8)

    c.label('length')
    c.emit('9c608b442410')
    c.branch('e8', 'map')
    c.emit('3b442410')
    c.branch('0f84', 'length-done')
    c.emit('8b5c241089442410395c2428')
    c.branch('0f85', 'length-concat')
    c.emit('89442428')
    c.label('length-concat')
    c.emit('81c2'); c.u32(abi['concat_return'])
    c.emit('39542424')
    c.branch('0f85', 'length-done')
    c.emit('8b742404395efc')
    c.branch('0f85', 'length-done')
    c.emit('8946fc')
    c.label('length-done')
    c.emit('619d85db')
    c.branch('0f85', abi['length'] + 7)
    c.emit('33c0c3')

    # Preserve the native getter without an additional wire-version query.
    c.label('get-field')
    c.label('getter-trampoline')
    c.emit('558bec81ec0c000000')
    c.branch('e9', abi['getter'] + 9)
    return c.finish(), c.labels


def derive(source):
    pin = hashlib.sha256(source).hexdigest()
    if pin not in (SOURCE_SHA256, DLL_SHA256):
        raise ValueError('Only the pinned native reader inputs are supported')
    abi, literals, filename = ((LAUNCHER_ABI, LITERALS, 'dof-launcher.exe') if pin == SOURCE_SHA256
                              else (DLL_ABI, DLL_LITERALS, 'ijl15.dll'))
    pe = Pe32(source)
    patches = [(abi['file_exists'], bytes.fromhex('8b4c240481ec40010000'), 'file-exists'),
               (abi['compare'], bytes.fromhex('8b5424048b4c2408'), 'compare'),
               (abi['length'], bytes.fromhex('85db750333c0c3'), 'length'),
               (abi['getter'], bytes.fromhex('558bec81ec0c000000'), 'get-field')]
    for rva, expected, name in patches:
        if pe.at(rva, len(expected)) != expected:
            raise ValueError('Unexpected native consumer: ' + name)
    for rva, old, _, _ in literals:
        if pe.at(rva, len(old)) != old:
            raise ValueError('Unexpected native literal reference')
    header_end = pe.table + (pe.count + 2) * 40
    first_raw = min(s['offset'] for s in pe.sections if s['raw_size'])
    if header_end > first_raw or any(source[pe.table + pe.count * 40:header_end]):
        raise ValueError('Insufficient unused PE section header space')
    # A whole page separates the read-only map from executable adaptation code.
    code_rva = align(max(s['rva'] + max(s['size'], s['raw_size']) for s in pe.sections), pe.section_alignment)
    data_rva = code_rva + pe.section_alignment
    pool = bytearray(12 * len(literals))
    rows = []
    for index, (old, _, new, kind) in enumerate(literals):
        target = data_rva + len(pool)
        pool.extend(new + b'\0')
        struct.pack_into('<III', pool, index * 12, old, target, kind)
        rows.append(dict(sourceRva=old, targetRva=target, value=new.decode('ascii'), kind=kind))
    code, labels = make_code(code_rva, data_rva, rows, abi)
    if len(code) > pe.section_alignment or len(pool) > pe.section_alignment:
        raise ValueError('Native adaptation exceeds its reserved section layout')
    result = bytearray(source)
    changed = []
    for rva, expected, name in patches:
        value = b'\xe9' + struct.pack('<i', labels[name] - rva - 5) + b'\x90' * (len(expected) - 5)
        off = pe.offset(rva, len(value))
        result[off:off + len(value)] = value
        changed.append(dict(rva=rva, before=expected.hex(), after=value.hex(), consumer=name))
    for index, (name, rva, data, flags) in enumerate([
            (b'.doftext', code_rva, code, 0x60000020),
            (b'.dofro', data_rva, bytes(pool), 0x40000040)]):
        raw_off = align(len(result), pe.file_alignment)
        raw_size = align(len(data), pe.file_alignment)
        result.extend(bytes(raw_off - len(result)))
        result.extend(data + bytes(raw_size - len(data)))
        struct.pack_into('<8sIIIIIIHHI', result, pe.table + (pe.count + index) * 40,
                         name, len(data), rva, raw_size, raw_off, 0, 0, 0, 0, flags)
    struct.pack_into('<H', result, pe.pe + 6, pe.count + 2)
    struct.pack_into('<I', result, pe.optional + 56, align(data_rva + len(pool), pe.section_alignment))
    for field, addition in [(4, align(len(code), pe.file_alignment)), (8, align(len(pool), pe.file_alignment))]:
        struct.pack_into('<I', result, pe.optional + field,
                         struct.unpack_from('<I', result, pe.optional + field)[0] + addition)
    # PE image checksum; the original certificate directory is empty for this pin.
    checksum_offset = pe.optional + 64
    struct.pack_into('<I', result, checksum_offset, 0)
    checksum = 0
    for offset in range(0, len(result), 2):
        checksum += int.from_bytes(result[offset:offset + 2], 'little')
        checksum = (checksum & 0xffff) + (checksum >> 16)
    checksum = (checksum & 0xffff) + (checksum >> 16)
    struct.pack_into('<I', result, checksum_offset, checksum + len(result))
    raw = bytes(result)
    report = dict(recipe=RECIPE, sourceSha256=pin, outputSha256=hashlib.sha256(raw).hexdigest(),
                  file=filename, clientFormatVersion=2, architecture='i386',
                  patches=changed, references=rows, codeRva=code_rva, dataRva=data_rva,
                  nativeAcceptance='unverified', gameDllAcceptance='unverified')
    return raw, report


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--source', type=Path, required=True)
    parser.add_argument('--output', type=Path, required=True)
    args = parser.parse_args()
    if args.source.is_symlink() or not args.source.is_file():
        parser.error('Source must be a regular pinned input file')
    raw, report = derive(args.source.read_bytes())
    # Exclusive creation: an existing output is never mistaken for fresh evidence.
    args.output.mkdir(parents=True, exist_ok=False)
    try:
        (args.output / report['file']).write_bytes(raw)
        (args.output / 'derivation.json').write_text(json.dumps(report, indent=2) + '\n', encoding='utf-8')
    except BaseException:
        (args.output / 'INCOMPLETE').touch()
        raise
    print(json.dumps({key: report[key] for key in ['recipe', 'outputSha256', 'nativeAcceptance']}))


if __name__ == '__main__':
    main()
