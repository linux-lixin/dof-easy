"""Adapt the VM-protected random-box query at its verified native consumer.

The pinned mode-5 branch passes the literal RVA 0x2982a8 to MySQL::set_query.
Keep its VM bytecode intact; redirect only this literal pointer before native
formatting. New code, literal and program headers occupy one added RX mapping.
"""
import struct


def adapt_random_box(source, data, elf, patches, change):
    entry, resume, old = 0x40ac0, 0x40ac9, 0x2982a8
    expected = b'SELECT DISTINCT item_id FROM qk.randombox'
    old_at = elf.offset(old, len(expected) + 1)
    if source[old_at:old_at + len(expected) + 1] != expected + b'\0':
        raise ValueError('Unrecognized random-box literal')
    if elf.phnum != 8 or any(p['type'] == 6 for p in elf.segments):
        raise ValueError('Unexpected plugin program-header layout')
    # Existing headers include the already-extended RW strings mapping.
    headers = bytearray(data[elf.phoff:elf.phoff + elf.phnum * 32])
    extent = max(struct.unpack_from('<8I', headers, i * 32)[2] +
                 struct.unpack_from('<8I', headers, i * 32)[5]
                 for i, p in enumerate(elf.segments) if p['type'] == 1)
    offset = (len(data) + 4095) & ~4095
    address = (extent + 4095) & ~4095
    code_at, query_at = address + 512, address + 768
    query = b'SELECT DISTINCT item_id FROM dof_native.randombox\0'
    code = bytearray(b'\x9c\x50\xe8\0\0\0\0\x58')  # flags/eax, PIC base
    code += b'\x2d' + struct.pack('<I', (code_at + 7 - old) & 0xffffffff)
    code += b'\x39\x44\x24\x10'  # compare format argument with the one original literal
    code += b'\x75\x09'
    code += b'\x05' + struct.pack('<I', query_at - old)
    code += b'\x89\x44\x24\x10'
    code += b'\x58\x9d\x55\x57\x56\x53'
    # Replay the displaced PIC prologue without changing its logical return PC.
    code += b'\xe8\0\0\0\0'
    pc = code_at + len(code)
    code += b'\x5b\x8d\x9b' + struct.pack('<i', resume - pc)
    code += b'\xe9' + struct.pack('<i', resume - (code_at + len(code) + 5))
    length = 768 + len(query)
    headers += struct.pack('<8I', 1, offset, address, address, length, length, 5, 4096)
    data.extend(bytes(offset - len(data)) + bytes(length))
    data[offset:offset + len(headers)] = headers
    data[offset + 512:offset + 512 + len(code)] = code
    data[offset + 768:offset + length] = query
    change(data, 28, struct.pack('<I', elf.phoff), struct.pack('<I', offset), patches, 'plugin-mapped-program-headers')
    change(data, 44, struct.pack('<H', elf.phnum), struct.pack('<H', elf.phnum + 1), patches, 'plugin-query-rx-segment')
    before = bytes.fromhex('55575653e8578ffeff')
    after = b'\xe9' + struct.pack('<i', code_at - entry - 5) + b'\x90' * 4
    change(data, elf.offset(entry, 9), before, after, patches, 'random-box-native-query-consumer')
    # Consumers of the old pointer are redirected before formatting. Keep the
    # allocation and every other address stable, with no old database spelling.
    change(data, old_at, expected, b'dof query relocated'.ljust(len(expected), b'\0'), patches, 'retire-random-box-old-literal')
    return dict(entry=entry, resume=resume, oldLiteral=old, pointer=query_at,
                adapter=code_at, programHeaders=offset, segmentFlags=5,
                value=query[:-1].decode(), callerReturn=0x7480b9, operation=5)
