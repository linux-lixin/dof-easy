"""Derive native server payloads from the pinned replacement inputs.

No native execution, licensing changes or network access. The formal builder
consumes these deterministic recipes; release acceptance remains independent
and must bind real loading and function checks to the resulting hashes.
"""
import argparse
import hashlib
import json
from pathlib import Path
import re
import struct
import sys
sys.path.insert(0, str(Path(__file__).resolve().parents[2]))

PLUGIN_PIN = '33cd006a364d4c3e0b8738fb2333cf16771fb796443368be4eee7105e40aadfa'
GATEWAY_PIN = '67553a3b45de80ebea5f767f6e3a51d577d2cbb6c6647bd4c97037cb50a41f9b'
INLINE_PIN = 'd413b7df6c928b519986a9ceb3dbed4c78148564cca352d579f18e3650819da8'
SQL_CALLS_PIN = '816e4d0701a16dfea32774fe1b11ddd487a22c5e351e0c925557f911f811b069'
SQL_LITERALS_PIN = '5322de4619448440aebe97fa97a1c3ef69bcd9770634e1765cc714e9f394c8d9'


SQL_TABLE_NAMES = {
    'qk_ai': 'ai', 'qk_ai_hh': 'ai_hh', 'qk_ai_map': 'ai_map',
    'qk_ai_user': 'ai_user', 'qk_ai_user_temp': 'ai_user_temp',
    'v4_qk_user_temp': 'v4_user_temp',
}


def sql_names(text):
    text = re.sub(r'\bqingkong_config(_v2)?\b', lambda m: 'config' + (m[1] or ''), text)
    text = re.sub(r'\bqk\b', 'dof_native', text)
    return re.sub(r'\b(?:' + '|'.join(SQL_TABLE_NAMES) + r')\b',
                  lambda m: SQL_TABLE_NAMES[m[0]], text)


def gateway_sql_calls():
    """Reviewed call sites, including SQL assembled as instruction immediates."""
    path = Path(__file__).resolve().parents[2] / 'metadata/dof/derivation/gateway-sql-calls.json'
    raw = path.read_bytes()
    if digest(raw) != SQL_CALLS_PIN:
        raise ValueError('Gateway SQL call-site evidence has changed')
    record = json.loads(raw)
    if record['sourceSha256'] != GATEWAY_PIN or len(record['calls']) != 33:
        raise ValueError('Unexpected gateway SQL call-site evidence')
    return record['calls']


def gateway_sql_literals():
    path = Path(__file__).resolve().parents[2] / 'metadata/dof/derivation/gateway-sql-literals.json'
    raw = path.read_bytes()
    if digest(raw) != SQL_LITERALS_PIN:
        raise ValueError('Gateway SQL literal evidence has changed')
    record = json.loads(raw)
    if record['sourceSha256'] != GATEWAY_PIN or len(record['literals']) != 97:
        raise ValueError('Unexpected gateway SQL literal evidence')
    return record['literals']


class Elf:
    def __init__(self, data):
        self.data = data
        if len(data) < 64 or data[:4] != b'\x7fELF' or data[4] not in (1, 2) or data[5] != 1:
            raise ValueError('Unsupported ELF')
        self.wide = data[4] == 2
        h = struct.unpack_from('<HHIQQQIHHHHHH' if self.wide else '<HHIIIIIHHHHHH', data, 16)
        self.phoff, self.phsize, self.phnum = h[4], h[8], h[9]
        if self.phsize != (56 if self.wide else 32) or self.phoff + self.phsize * self.phnum > len(data):
            raise ValueError('Invalid ELF program header table')
        self.segments = []
        for i in range(self.phnum):
            at = self.phoff + i * self.phsize
            p = struct.unpack_from('<IIQQQQQQ' if self.wide else '<8I', data, at)
            if self.wide:
                kind, flags, off, va, _, size, mem, align = p
            else:
                kind, off, va, _, size, mem, flags, align = p
            if off + size > len(data):
                raise ValueError('Truncated ELF segment')
            self.segments.append(dict(header=at, type=kind, flags=flags, offset=off,
                                      address=va, fileSize=size, memorySize=mem, align=align))
        self.tags = {}
        for p in self.segments:
            if p['type'] != 2:
                continue
            for off in range(p['offset'], p['offset'] + p['fileSize'], 16 if self.wide else 8):
                tag, value = struct.unpack_from('<QQ' if self.wide else '<II', data, off)
                if not tag:
                    break
                self.tags[tag] = value

    def offset(self, address, size=1):
        matches = [p['offset'] + address - p['address'] for p in self.segments
                   if p['type'] == 1 and p['address'] <= address
                   and address + size <= p['address'] + p['fileSize']]
        if len(matches) != 1:
            raise ValueError('Address is not uniquely file-backed')
        return matches[0]


def digest(data):
    return hashlib.sha256(data).hexdigest()


def replacement_source(path, pin):
    path = Path(path)
    if path.is_symlink() or not path.is_file():
        raise ValueError('Expected a regular local replacement input')
    raw = path.read_bytes()
    if digest(raw) != pin:
        raise ValueError('Only the pinned replacement is supported')
    return raw


def change(data, offset, expected, value, patches, purpose):
    if len(expected) != len(value) or bytes(data[offset:offset + len(expected)]) != expected:
        raise ValueError('Unexpected or overlapping patch site: ' + purpose)
    data[offset:offset + len(expected)] = value
    patches.append(dict(offset=offset, before=expected.hex(), after=value.hex(), purpose=purpose))


def plugin_candidate(source):
    if digest(source) != PLUGIN_PIN:
        raise ValueError('Only the pinned replacement plugin is supported')
    elf = Elf(source)
    if elf.wide or elf.tags[3] != 0x397d40:
        raise ValueError('Unexpected plugin ABI')
    data = bytearray(source)
    loads = [p for p in elf.segments if p['type'] == 1]
    tail = max(loads, key=lambda p: p['address'])
    if tail['flags'] != 6 or tail['fileSize'] != tail['memorySize']:
        raise ValueError('Cannot extend a segment with executable code or a BSS tail')
    # Retain every existing file offset. Extend only the final RW mapping to a
    # private string pool; no initialization/authentication branch is disabled.
    data.extend(bytes((-len(data)) % 16))
    patches, names = [], []
    sites = [
        ('main-config', 0x318a3, bytes.fromhex('8db3efdcedff'), 0x275a2f,
         b'/root/qk_plugin_config.ini', b'/data/dof-easy/config/plugin.ini'),
        ('anton-config', 0x9ee1f, bytes.fromhex('8d86e10af0ff'), 0x298821,
         b'/root/qk_plugin_antonRaid.ini', b'/data/dof-easy/config/anton-raid.ini'),
        ('luke-config', 0xaf82f, bytes.fromhex('8d86f80df0ff'), 0x298b38,
         b'/root/qk_plugin_lukeRaid.ini', b'/data/dof-easy/config/luke-raid.ini'),
        ('rarity-tag', 0x30a0a, bytes.fromhex('8d80a6dcedff'), 0x2759e6,
         b'[qk rarity color]', b'[dof rarity color]'),
        ('randombox-tag', 0x30a2a, bytes.fromhex('8d80b8dcedff'), 0x2759f8,
         b'[qk randombox]', b'[dof randombox]'),
        ('control-frame-end', 0xe11d1, bytes.fromhex('8d96e1ebf0ff'), 0x2a6921,
         b'</qk>', b'</dof>'),
        ('database', 0x740a54, bytes.fromhex('8db8bafdedff'), 0x277afa,
         b'qk', b'dof_native'),
    ]
    for purpose, address, instruction, old_address, old, new in sites:
        old_off = elf.offset(old_address, len(old) + 1)
        if source[old_off:old_off + len(old) + 1] != old + b'\0':
            raise ValueError('Unexpected original string: ' + purpose)
        pointer = tail['address'] + len(data) - tail['offset']
        data.extend(new + b'\0')
        value = instruction[:2] + struct.pack('<I', (pointer - elf.tags[3]) & 0xffffffff)
        change(data, elf.offset(address, 6), instruction, value, patches, purpose)
        names.append(dict(purpose=purpose, pointer=pointer, value=new.decode(), callSite=address))
    closing = next(row['pointer'] for row in names if row['purpose'] == 'control-frame-end')
    change(data, elf.offset(0xe11cb, 6), bytes.fromhex('8d8ee6ebf0ff'),
           b'\x8d\x8e' + struct.pack('<I', (closing + 6 - elf.tags[3]) & 0xffffffff),
           patches, 'control-frame-end-range')
    # Both tags fit the existing 16-byte small-string buffers. Keep the entire
    # instruction window size, update both lengths, and terminate each buffer.
    opening = (bytes.fromhex('c785a8dfffff') + b'<dof' +
               bytes.fromhex('c785a4dfffff05000000') +
               bytes.fromhex('66c785acdfffff3e006a055a'))
    expected = bytes.fromhex('c785a8dfffff3c716b3ec785a4dfffff04000000c685acdfffff00ba04000000')
    change(data, elf.offset(0xe1292, len(expected)), expected, opening, patches, 'control-frame-opening-buffer')
    closing_buffer = (bytes.fromhex('c785c0dfffff') + b'</do' +
                      bytes.fromhex('c785c4dfffff663e0000c785bcdfffff06000000') + b'\x90' * 4)
    expected = bytes.fromhex('c785c0dfffff3c2f716bc685c4dfffff3ec785bcdfffff05000000c685c5dfffff00')
    change(data, elf.offset(0xe12d2, len(expected)), expected, closing_buffer, patches, 'control-frame-closing-buffer')
    old = b'select reDCdkeyOpt from qingkong_config where switch_reCdkey=1'
    new = old.replace(b'qingkong_config', b'config')
    change(data, elf.offset(0x2864dc, len(old)), old, new.ljust(len(old), b'\0'), patches, 'config-query')
    old_brand = '晴空启动器服务端插件'.encode('gbk')
    new_brand = 'Dof服务端插件'.encode('gbk')
    change(data, elf.offset(0x275bf3, len(old_brand)), old_brand,
           new_brand.ljust(len(old_brand), b'\0'), patches, 'plugin-console-brand')
    old_error = b'dj Server Plugin Open DB_Qk'
    change(data, elf.offset(0x289924, len(old_error)), old_error,
           b'Dof native database'.ljust(len(old_error), b' '), patches, 'database-error-brand')
    old_header = b'# qk verify client config\n# ServerIP and Port are built into qk_plugin.so.\nEnable=1\nAccount=\nPassword=\n'
    new_header = b'# Dof verify client config\n# Endpoint is built into libdof-plugin.so.\nEnable=1\nAccount=\nPassword=\n'
    change(data, elf.offset(0x275fb0, len(old_header)), old_header,
           new_header.ljust(len(old_header), b'\0'), patches, 'verification-header-brand')
    diagnostic = b'Invalid qksocket token len=%zu expected_len=%zu\n'
    change(data, elf.offset(0x2a68f0, len(diagnostic)), diagnostic,
           b'Invalid DofSock token len=%zu expected_len=%zu\n'.ljust(len(diagnostic), b'\0'),
           patches, 'socket-token-diagnostic')
    from scripts.dof.native_symbols import rename_plugin_symbols
    dynamic_names = rename_plugin_symbols(source, data, elf, tail, patches, change)
    data.extend(bytes((-len(data)) % 4))
    equipment_slot = tail['address'] + len(data) - tail['offset']
    data.extend(bytes(4))
    new_size = len(data) - tail['offset']
    for field in (16, 20):
        change(data, tail['header'] + field, struct.pack('<I', tail['fileSize']),
               struct.pack('<I', new_size), patches, 'extend-string-pool-mapping')
    from scripts.dof.native_sql import adapt_random_box
    random_box = adapt_random_box(source, data, elf, patches, change)
    from scripts.dof.native_equipment import bind_equipment
    equipment = bind_equipment(source, data, equipment_slot, patches, change)
    from scripts.dof.native_residuals import retire_plugin_literals
    retired = retire_plugin_literals(source, data, elf, patches, change)
    return bytes(data), dict(sourceSha256=PLUGIN_PIN, patches=patches, names=names, dynamicNames=dynamic_names, randomBox=random_box, equipment=equipment, retiredLiterals=retired,
                            originalStringsRetained=True, releaseReady=False)


def gateway_candidate(source):
    if digest(source) != GATEWAY_PIN:
        raise ValueError('Only the pinned replacement gateway is supported')
    elf = Elf(source)
    if not elf.wide:
        raise ValueError('Unexpected gateway ABI')
    data = bytearray(source)
    loads = [p for p in elf.segments if p['type'] == 1]
    notes = [p for p in elf.segments if p['type'] == 4]
    if len(notes) != 1 or notes[0]['offset'] != 0x254 or notes[0]['fileSize'] != 32:
        raise ValueError('Unexpected gateway NOTE allocation')
    pool_off = (len(data) + 4095) & ~4095
    pool_va = (max(p['address'] + p['memorySize'] for p in loads) + 4095) & ~4095
    data.extend(bytes(pool_off - len(data)))
    patches, names = [], []
    rpath = elf.offset(elf.tags[5] + elf.tags[15], 16)
    change(data, rpath, b'/root/qingkong/\0', b'$ORIGIN/../lib\0\0',
           patches, 'origin-relative-library-search')
    sites = [
        (b'/root/config.ini', b'/data/dof-easy/config/gateway.ini', 0x4c9077, [0x437b3b, 0x437e0c], 0xbf),
        (b'use qk', b'use dof_native', 0x4dc27a,
         [0x496a29, 0x496bc9, 0x49708e, 0x4976d8, 0x497973,
          0x497cb1, 0x497cf9, 0x497d39, 0x497d69, 0x497d99], 0xbe),
        (b'qk_qk_config', b'dof_config', 0x4ca9ce, [0x45926d], 0xbe),
        (b'qk_CID', b'dof_CID', 0x4caa6f, [0x459555], 0xbe),
        (b'/root/dnfgj/%i/', b'/data/dof-easy/robots/%i/', 0x4cc8b9, [0x46b382], 0xba),
        (b'rm -rf /root/publickey.pem', b'rm -f publickey.pem', 0x4dc243, [0x498794, 0x4987e4], 0xbf),
        (b'rm -rf /root/privatekey.pem', b'rm -f privatekey.pem', 0x4dc25e, [0x49879e, 0x4987ee], 0xbf),
        (b'qkRobotc', b'dofRobotc', 0x4ca971, [0x457c5f, 0x459bdf], 0xbe),
        (b'/home/neople/game/privatekey.pem', b'/data/dof-easy/keys/privatekey.pem',
         0x4c9190, [0x436ebb], 0xbf),
    ]
    from scripts.dof.native_residuals import GATEWAY_IDENTIFIERS, retire_gateway_literals, retire_gateway_stack_constants
    sites.extend((old.encode(), new.encode(), address, refs, 0xbe)
                 for address, old, new, refs in GATEWAY_IDENTIFIERS)
    # These fragments feed std::string construction rather than the direct
    # MySQL wrapper, so the earlier direct-SQL inventory did not include them.
    for old_va, reference in [(0x4d0338,0x48b4a3),(0x4d0a28,0x48d9a0),
                              (0x4d0b68,0x48da65),(0x4d9d68,0x497d0c),
                              (0x4d9f10,0x497d1f),(0x4da6b0,0x4976fe),
                              (0x4da8e8,0x497711),(0x4db280,0x497cd7)]:
        at=elf.offset(old_va);old=source[at:source.index(b'\0',at)]
        if b'qk' not in old:raise ValueError('Unexpected gateway SQL fragment')
        sites.append((old,sql_names(old.decode()).encode(),old_va,[reference],0xbe))
    for old_va, reference in [(0x4d8a58, 0x4970a4), (0x4d8e28, 0x4970ba),
                              (0x4d8fd0, 0x4970d0), (0x4d9368, 0x4970e6),
                              (0x4d94e0, 0x4970fc)]:
        at = elf.offset(old_va)
        old = source[at:source.index(b'\0', at)]
        if not old.startswith(b'CREATE TABLE `qk`.`qk_ai'):
            raise ValueError('Unexpected robot schema source')
        sites.append((old, sql_names(old.decode()).encode(), old_va, [reference], 0xbe))
    for old, new, old_va, references, opcode in sites:
        old_off = elf.offset(old_va, len(old) + 1)
        if source[old_off:old_off + len(old) + 1] != old + b'\0':
            raise ValueError('Unexpected gateway source string')
        pointer = pool_va + len(data) - pool_off
        data.extend(new + b'\0')
        for address in references:
            change(data, elf.offset(address, 5), bytes([opcode]) + struct.pack('<I', old_va),
                   bytes([opcode]) + struct.pack('<I', pointer), patches, new.decode())
        names.append(dict(pointer=pointer, value=new.decode(), references=references, opcode=opcode))
    note = notes[0]
    # All occurrences are in fixed NUL-terminated SQL literals. Bound each to
    # the original string allocation; never change arbitrary binary substrings.
    spans = set()
    for match in re.finditer(b'qingkong_config|CREATE DATABASE.*?qk', source):
        start = source.rfind(b'\0', 0, match.start()) + 1
        end = source.find(b'\0', match.end())
        if end < 0:
            raise ValueError('Unterminated SQL literal')
        spans.add((start, end))
    for start, end in sorted(spans):
        old = source[start:end]
        if not re.search(rb'(?i)\b(select|create|alter|update)\b', old):
            raise ValueError('Name occurs outside reviewed SQL literals')
        new = sql_names(old.decode('utf-8')).encode('utf-8')
        if len(new) > len(old):
            raise ValueError('SQL literal needs its own relocation')
        change(data, start, old, new.ljust(len(old), b'\0'), patches, 'native-schema-literal')
    # This filename is assembled byte-by-byte in code, not stored as a C
    # string. Preserve the original search and write boundary in the game's
    # existing 19-byte allocation; the shorter new name is NUL padded.
    old_loader = b'libantisvrinline.so'
    new_loader = b'libdof-inline.so'.ljust(len(old_loader), b'\0')
    for index, (old_byte, new_byte) in enumerate(zip(old_loader, new_loader)):
        address = 0x42e629 if index == 0 else 0x42e62c + (index - 1) * 4
        prefix = b'\xc6\x00' if index == 0 else b'\xc6\x40' + bytes([index])
        change(data, elf.offset(address, len(prefix) + 1), prefix + bytes([old_byte]),
               prefix + bytes([new_byte]), patches, 'game-inline-loader-byte-' + str(index))
    # These SQL statements are assembled into stack buffers by MOV immediates.
    # Their original consumer takes a const char* in RSI and passes it to
    # mysql_query. Redirect each exact CALL to a tiny adapter which supplies a
    # new immutable literal, then tail-jumps to the same consumer. No stack
    # allocation is enlarged, query result is altered, or general SQL rewrite
    # hook is installed. The original return address and other registers stay
    # unchanged, and the adapter mapping is RX, never writable/executable.
    sql_calls = []
    for row in gateway_sql_calls():
        address, target = row['call'], row['target']
        if target not in (0x490210, 0x490990):
            raise ValueError('Unreviewed native SQL consumer')
        expected = b'\xe8' + struct.pack('<i', target - address - 5)
        if row['before'] != expected.hex():
            raise ValueError('Unexpected SQL CALL encoding')
        text = sql_names(row['text'])
        if text == row['text'] or '\0' in text:
            raise ValueError('Unexpected native SQL literal')
        pointer = pool_va + len(data) - pool_off
        data.extend(text.encode('utf-8') + b'\0')
        data.extend(bytes((-len(data)) % 16))
        adapter = pool_va + len(data) - pool_off
        data.extend(b'\x48\xbe' + struct.pack('<Q', pointer))  # MOV RSI, literal
        data.extend(b'\xe9' + struct.pack('<i', target - adapter - 15))
        change(data, elf.offset(address, 5), expected,
               b'\xe8' + struct.pack('<i', adapter - address - 5), patches,
               'native-sql-consumer-' + hex(address))
        sql_calls.append(dict(callSite=address, consumer=target, adapter=adapter,
                              pointer=pointer, value=text))
    sql_literals = []
    for row in gateway_sql_literals():
        original = row['text'].encode('utf-8') + b'\0'
        offset = elf.offset(row['address'], len(original))
        if source[offset:offset + len(original)] != original:
            raise ValueError('Unexpected native SQL literal preimage')
        text = sql_names(row['text'])
        pointer = pool_va + len(data) - pool_off
        data.extend(text.encode('utf-8') + b'\0')
        for reference in row['references']:
            # Only the reviewed MOV ESI/EDX,imm32 instructions are accepted.
            before = bytes.fromhex(reference['before'])
            if (len(before) != 5 or before[0] not in (0xbe, 0xba)
                    or before[1:] != struct.pack('<I', row['address'])):
                raise ValueError('Unexpected native SQL literal reference')
            change(data, elf.offset(reference['address'], 5), before,
                   before[:1] + struct.pack('<I', pointer), patches,
                   'native-sql-literal-reference-' + hex(reference['address']))
        sql_literals.append(dict(pointer=pointer, value=text, references=row['references']))
    old_brand = '\n>>>>>>>晴空v2.X<<<<<<<\n'.encode()
    new_brand = b'\n>>>>>>>Dof native<<<<<<<\n'
    change(data, elf.offset(0x4ca6a8, len(old_brand)), old_brand,
           new_brand.ljust(len(old_brand), b'\0'), patches, 'gateway-console-brand')
    for match in re.finditer(rb'/root/projects/QK/[^\x00]+', source):
        old = match[0]
        if any(c > 127 for c in old) or len(old) > 512:
            raise ValueError('Unexpected compiled source-path diagnostic')
        new = old.replace(b'/root/projects/QK/QKcon/', b'/native/dof/gateway/')
        new = new.replace(b'/root/projects/QK/QKv1/', b'/native/dof/gateway/')
        if new == old or len(new) > len(old):
            raise ValueError('Unreviewed compiled source-path diagnostic')
        change(data, match.start(), old, new.ljust(len(old), b'\0'), patches, 'compiled-source-diagnostic')
    retired_stack = retire_gateway_stack_constants(source,data,elf,patches,change)
    retired = retire_gateway_literals(source, data, elf,
        [(address, old) for old, _, address, _, _ in sites
         if re.search(rb'(?i)qk|qingkong', old)] +
        [(row['address'], row['text'].encode()) for row in gateway_sql_literals()],
        patches, change)
    header = struct.pack('<IIQQQQQQ', 1, 5, pool_off, pool_va, pool_va,
                         len(data) - pool_off, len(data) - pool_off, 4096)
    change(data, note['header'], source[note['header']:note['header'] + 56], header,
           patches, 'replace-ABI-NOTE-with-RX-literals-and-SQL-call-adapters')
    return bytes(data), dict(sourceSha256=GATEWAY_PIN, patches=patches, names=names,
                            originalStringsRetained=True, releaseReady=False,
                            sqlCalls=sql_calls, sqlLiterals=sql_literals, retiredLiterals=retired, retiredStackConstants=retired_stack,
                            protocolIdentifiers=[dict(old=old,new=new,references=refs) for _,old,new,refs in GATEWAY_IDENTIFIERS],
                            gameLoader=dict(start=0x42e629, end=0x42e674,
                                            allocationBytes=19, value='libdof-inline.so'))


def inline_candidate(source):
    """Add a post-DT_INIT name adapter; retain the packed decoder and its checks.

    The dynamic loader invokes INIT_ARRAY after INIT. A new mapped program
    header table and dynamic/REL tables describe the callback without consuming
    existing code or GNU unwind/stack headers. Real loader acceptance is separate.
    """
    if digest(source) != INLINE_PIN:
        raise ValueError('Only the pinned replacement inline companion is supported')
    elf = Elf(source)
    if elf.wide or elf.tags[12] != 0x25cdd4 or 25 in elf.tags or 27 in elf.tags:
        raise ValueError('Unexpected inline initializer layout')
    dynamic, = (p for p in elf.segments if p['type'] == 2)
    mprotect_slot = None
    for off in range(elf.offset(elf.tags[23]), elf.offset(elf.tags[23]) + elf.tags[2], 8):
        where, info = struct.unpack_from('<II', source, off)
        symbol = elf.offset(elf.tags[6] + (info >> 8) * 16, 16)
        name_index = struct.unpack_from('<I', source, symbol)[0]
        name = elf.offset(elf.tags[5] + name_index)
        if source[name:source.find(b'\0', name)] == b'mprotect':
            if info & 255 != 7 or mprotect_slot is not None:
                raise ValueError('Unexpected mprotect relocation')
            mprotect_slot = where
    if mprotect_slot is None:
        raise ValueError('No reviewed mprotect import')
    data = bytearray(source)
    pool_off = (len(data) + 4095) & ~4095
    pool_va = (max(p['address'] + p['memorySize'] for p in elf.segments if p['type'] == 1) + 4095) & ~4095
    data.extend(bytes(pool_off - len(data)))
    def append(value, align=4):
        data.extend(bytes((-len(data)) % align))
        offset = len(data)
        data.extend(value)
        return offset, pool_va + offset - pool_off
    headers_off, _ = append(bytes((elf.phnum + 2) * 32))
    names, operands = [], []
    for role, old, new, sites in [
        ('database', 0x388a8, b'dof_native', [0x6316]),
        ('gateway-config', 0x38a8e, b'/data/dof-easy/config/gateway.ini', [0x8080, 0x85f7]),
        ('robot-command', 0x38b4d, b'<qt><c>dofRobotc</c><key>', [0x8a8d]),
        ('private-key', 0x38ab4, b'/data/dof-easy/keys/privatekey.pem', [0x8141]),
    ]:
        _, pointer = append(new + b'\0')
        names.append(dict(purpose=role, pointer=pointer, value=new.decode(), references=sites))
        operands.extend((site, (old - 0x45360) & 0xffffffff,
                         (pointer - 0x45360) & 0xffffffff) for site in sites)
    code_off, code_va = append(b'', 16)
    code, failures = bytearray(b'\x9c\x60\xe8\0\0\0\0\x5b\x81\xeb'), []
    code.extend(struct.pack('<I', code_va + 7))  # EBX = actual load base.
    def fail_if_nonzero():
        code.extend(b'\x0f\x85'); failures.append(len(code)); code.extend(bytes(4))
    retired_inline=[(0x388a8,b'qk'),(0x38b4d,b'<qt><c>qkRobotc</c><key>')]
    for address,value in retired_inline:
        for i,byte in enumerate(value):
            code.extend(b'\x80\xbb'+struct.pack('<I',address+i)+bytes([byte]))
            fail_if_nonzero()
    for site, before, _ in operands:
        code.extend(b'\x81\xbb' + struct.pack('<II', site, before))
        fail_if_nonzero()
    def protect(flags, start=0x6000, size=0x3000):
        code.extend(b'\x6a' + bytes([flags]) + b'\x68' + struct.pack('<I',size))
        code.extend(b'\x8d\x83' + struct.pack('<I',start) + b'\x50')
        code.extend(b'\xff\x93' + struct.pack('<I', mprotect_slot))
        code.extend(b'\x83\xc4\x0c\x85\xc0')
        fail_if_nonzero()
    protect(7)
    for site, _, after in operands:
        code.extend(b'\xc7\x83' + struct.pack('<II', site, after))
    protect(5)
    protect(3,0x38000,0x1000)
    for address,value in retired_inline:
        for i in range(len(value)):
            code.extend(b'\xc6\x83'+struct.pack('<I',address+i)+b'\0')
    protect(5,0x38000,0x1000)
    code.extend(b'\x61\x9d\xc3')
    failure = len(code)
    code.extend(b'\x0f\x0b')  # Stop rather than silently run with old operands.
    for offset in failures:
        struct.pack_into('<i', code, offset, failure - offset - 4)
    data.extend(code)
    # The callback and strings remain RX. Relocation targets and the dynamic
    # table occupy a separate RW page; do not add a permanent RWX mapping.
    rw_off, rw_va = append(b'', 4096)
    array_off, array_va = append(struct.pack('<I', code_va))
    old_rel = elf.offset(elf.tags[17], elf.tags[18])
    rel_off, rel_va = append(source[old_rel:old_rel + elf.tags[18]] + struct.pack('<II', array_va, 8))
    entries = []
    for offset in range(dynamic['offset'], dynamic['offset'] + dynamic['fileSize'], 8):
        tag, value = struct.unpack_from('<II', source, offset)
        if not tag:
            break
        if tag == 17: value = rel_va
        if tag == 18: value += 8
        entries.append((tag, value))
    entries.extend([(25, array_va), (27, 4), (0, 0)])
    dyn_bytes = b''.join(struct.pack('<II', *entry) for entry in entries)
    dyn_off, dyn_va = append(dyn_bytes)
    # Preserve the eight original program headers except the dynamic table's
    # location. Add RX and RW mappings for the adapter/metadata; no original segment is
    # enlarged across another segment or silently made writable.
    headers = bytearray(source[elf.phoff:elf.phoff + elf.phnum * 32])
    at = dynamic['header'] - elf.phoff
    headers[at:at + 32] = struct.pack('<8I', 2, dyn_off, dyn_va, dyn_va,
                                     len(dyn_bytes), len(dyn_bytes), 6, 4)
    headers.extend(struct.pack('<8I', 1, pool_off, pool_va, pool_va,
                               rw_off - pool_off, rw_off - pool_off, 5, 4096))
    headers.extend(struct.pack('<8I', 1, rw_off, rw_va, rw_va,
                               len(data) - rw_off, len(data) - rw_off, 6, 4096))
    data[headers_off:headers_off + len(headers)] = headers
    patches = []
    change(data, 28, struct.pack('<I', elf.phoff), struct.pack('<I', headers_off), patches, 'mapped-program-header-table')
    change(data, 44, struct.pack('<H', elf.phnum), struct.pack('<H', elf.phnum + 2), patches, 'separate-adapter-code-and-data-segments')
    # Keep inspection/linker metadata consistent with the loader's program
    # headers. Otherwise readelf would display the obsolete dynamic section.
    shoff = struct.unpack_from('<I', source, 32)[0]
    shsize, shnum = struct.unpack_from('<HH', source, 46)
    if shsize != 40 or shoff + shnum * shsize > len(source):
        raise ValueError('Unexpected inline section table')
    moved_sections = set()
    for section in range(shoff, shoff + shnum * shsize, shsize):
        _, kind, _, address, _, size = struct.unpack_from('<6I', source, section)
        replacement = None
        if kind == 6 and address == dynamic['address']:
            replacement = (dyn_va, dyn_off, len(dyn_bytes)); moved_sections.add('dynamic')
        elif kind == 9 and address == elf.tags[17] and size == elf.tags[18]:
            replacement = (rel_va, rel_off, size + 8); moved_sections.add('rel')
        if replacement:
            change(data, section + 12, source[section + 12:section + 24],
                   struct.pack('<III', *replacement), patches, 'relocated-section-metadata')
    if moved_sections != {'dynamic', 'rel'}:
        raise ValueError('Missing inline dynamic/REL section metadata')
    return bytes(data), dict(sourceSha256=INLINE_PIN, patches=patches, names=names,
                            originalStringsRetained=True, releaseReady=False,
                            originalInit=elf.tags[12], callback=code_va, callbackBytes=len(code),
                            retiredLiterals=[dict(address=a,bytes=len(v),kind='post-decoder-literal') for a,v in retired_inline],
                            initArray=array_va, mprotectSlot=mprotect_slot,
                            memoryOperands=[dict(rva=s, before=b, after=a) for s, b, a in operands],
                            adapterSegmentFlags=['RE', 'RW'],
                            limitation='Post-initializer adapter; full loader/game acceptance still required')


def main():
    p = argparse.ArgumentParser(description=__doc__)
    p.add_argument('--server', type=Path, required=True)
    p.add_argument('--output', type=Path, required=True)
    args = p.parse_args()
    if args.output.exists() or args.output.is_symlink():
        raise ValueError('Use a new local candidate directory')
    results = []
    for name, source_name, pin, derive in [
        ('libdof-plugin.so', 'qk_plugin.so', PLUGIN_PIN, plugin_candidate),
        ('dof-gateway', 'QK', GATEWAY_PIN, gateway_candidate),
        ('libdof-inline.so', 'libantisvrinline.so', INLINE_PIN, inline_candidate),
    ]:
        raw = replacement_source(args.server / source_name, pin)
        data, report = derive(raw)
        report.update(file=name, sha256=digest(data), bytes=len(data))
        results.append((name, data, report))
    args.output.mkdir(mode=0o700, parents=True)
    for name, data, _ in results:
        target = args.output / name
        with target.open('xb') as stream:
            stream.write(data)
        target.chmod(0o600)  # Lab input only; not an installed executable.
    report = dict(schema=1, releaseReady=False, hostNativeExecution=False,
                  serverAccessed=False, files=[r for _, _, r in results])
    (args.output / 'derivation.json').write_text(json.dumps(report, indent=2) + '\n')
    print('Created local candidates only: ' + str(args.output))


if __name__ == '__main__':
    main()
