"""Derive a fixed-hash bridge iterator repair for local acceptance testing.

CheckThread erased its current map node, then incremented that freed iterator.
Keep the original erase, count decrement, timeouts and locking. Advance the
iterator before erasure and omit the second increment on the erase branch.
No native execution, server access, or in-place source modification.
"""
import argparse
import hashlib
import json
from pathlib import Path
import struct


def digest(data):
    return hashlib.sha256(data).hexdigest()


def replacement_source(path, pin):
    path = Path(path)
    if path.is_symlink() or not path.is_file():
        raise ValueError('Expected a regular local source')
    raw = path.read_bytes()
    if digest(raw) != pin:
        raise ValueError('Bridge source hash mismatch')
    return raw


def change(data, offset, before, after, patches, purpose):
    if len(before) != len(after) or data[offset:offset + len(before)] != before:
        raise ValueError('Unexpected bridge patch preimage: ' + purpose)
    data[offset:offset + len(after)] = after
    patches.append(dict(offset=offset, before=before.hex(), after=after.hex(), purpose=purpose))


class Elf:
    """Program headers only; accepts the fixed i386 ET_EXEC bridge layout."""
    def __init__(self, raw):
        if len(raw) < 52 or raw[:6] != b'\x7fELF\x01\x01':
            raise ValueError('Expected little-endian ELF32')
        self.wide = False
        header = struct.unpack_from('<HHIIIIIHHHHHH', raw, 16)
        phoff, phsize, phnum = header[4], header[8], header[9]
        if header[0] != 2 or header[1] != 3 or phsize != 32 or phoff + phsize * phnum > len(raw):
            raise ValueError('Invalid bridge program headers')
        self.segments = []
        for index in range(phnum):
            at = phoff + index * phsize
            kind, offset, va, _, size, mem, flags, align = struct.unpack_from('<8I', raw, at)
            if offset + size > len(raw):
                raise ValueError('Truncated bridge segment')
            self.segments.append(dict(header=at, type=kind, offset=offset, address=va,
                                      fileSize=size, memorySize=mem, flags=flags, align=align))

    def offset(self, address, size):
        matches = [p['offset'] + address - p['address'] for p in self.segments
                   if p['type'] == 1 and p['address'] <= address
                   and address + size <= p['address'] + p['fileSize']]
        if len(matches) != 1:
            raise ValueError('Bridge address is not uniquely file-backed')
        return matches[0]

BRIDGE_PIN = '30a5741015b3701cee2d9b388140ac1711345bbe8d879b6f152e74a9334ea6b0'


def derive(source):
    if digest(source) != BRIDGE_PIN:
        raise ValueError('Only the pinned bridge input is supported')
    elf = Elf(source)
    notes = [p for p in elf.segments if p['type'] == 4]
    if elf.wide or len(notes) != 1 or notes[0]['offset'] != 0x148 or notes[0]['fileSize'] != 32:
        raise ValueError('Unexpected bridge ELF layout')
    pool_off = (len(source) + 4095) & ~4095
    pool_va = (max(p['address'] + p['memorySize'] for p in elf.segments if p['type'] == 1) + 4095) & ~4095
    data = bytearray(source) + bytes(pool_off - len(source))
    patches = []

    def branch(opcode, address, target):
        return bytes([opcode]) + struct.pack('<i', target - address - 5)

    # Entry has the original return address and map/iterator arguments.
    # A separate aligned call frame keeps both original arguments intact.
    first = bytearray.fromhex('83ec0c8b442414890424')
    first += branch(0xe8, pool_va + len(first), 0x0804fe64)  # actual std::_Rb_tree_increment
    first += bytes.fromhex('8945a883c40c')  # save next in the original local iterator
    first += branch(0xe9, pool_va + len(first), 0x08063814)  # tail-call original map::erase
    data += first
    second_va = pool_va + len(first)
    second = branch(0xe8, second_va, 0x080636ae)  # original decreseServerCount
    second += branch(0xe9, second_va + len(second), 0x08062c3c)  # original loop counter/end comparison
    data += second
    for site, before, opcode, target in [
        (0x08062bfe, bytes.fromhex('e8110c0000'), 0xe8, pool_va),
        (0x08062c1a, bytes.fromhex('e88f0a0000'), 0xe9, second_va),
    ]:
        change(data, elf.offset(site, 5), before, branch(opcode, site, target), patches,
               'advance-before-erase' if opcode == 0xe8 else 'preserve-count-without-double-increment')
    note = notes[0]
    size = len(data) - pool_off
    header = struct.pack('<8I', 1, pool_off, pool_va, pool_va, size, size, 5, 4096)
    change(data, note['header'], source[note['header']:note['header'] + 32], header,
           patches, 'append-RX-adapters-without-overlapping-original-BSS')
    return bytes(data), dict(schema=1, sourceSha256=BRIDGE_PIN, sha256=digest(data),
        releaseReady=False, serverAccessed=False, patches=patches,
        adapterAddress=pool_va, adapterBytes=size, segmentFlags='RE',
        preserved=['original map erase', 'original server count decrement',
                   'original timeout checks and locks', 'normal non-erasing increment'],
        requires='Real bridge survival across game disconnect and subsequent game initialization')


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--source', type=Path, required=True)
    parser.add_argument('--output', type=Path, required=True)
    args = parser.parse_args()
    if args.output.exists() or args.output.is_symlink():
        raise ValueError('Use a new output directory')
    result, report = derive(replacement_source(args.source, BRIDGE_PIN))
    args.output.mkdir(mode=0o700)
    (args.output / 'df_bridge_r').write_bytes(result)
    (args.output / 'derivation.json').write_text(json.dumps(report, indent=2) + '\n')
    print('Derived bridge iterator repair; native acceptance still required')


if __name__ == '__main__':
    main()
