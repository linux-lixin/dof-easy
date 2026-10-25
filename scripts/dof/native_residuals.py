"""Reviewed remaining gateway identifiers and retired constant allocations.

Only pinned, relocated literals are retired. Reject any remaining absolute
pointer to their storage; never rewrite opaque keys or arbitrary qk byte pairs.
"""
import re
import struct

GATEWAY_IDENTIFIERS = [
    (0x4dc27e, 'qk', 'dof_native', [0x493770, 0x493c39]),
    (0x4ca9b5, 'zhqkPassword', 'dofPassword', [0x4591f1]),
    (0x4cab37, 'loginSysQKC', 'loginSysDof', [0x459935]),
    (0x4cab43, 'pushOpenTextQKC', 'pushOpenTextDof', [0x459973, 0x47b75f]),
    (0x4cab58, 'titlesQKC', 'titlesDof', [0x4599ef]),
    (0x4cab62, 'listDollQKC', 'listDollDof', [0x459a2d]),
    (0x4cab6e, 'msgCreateQKC', 'msgCreateDof', [0x459a6b]),
    (0x4cab7b, 'msgRemoveQKC', 'msgRemoveDof', [0x459aa9]),
    (0x4cab88, 'msgLoginQKC', 'msgLoginDof', [0x459ae7]),
    (0x4cab94, 'msgLogoutQKC', 'msgLogoutDof', [0x459b25]),
    (0x4caba1, 'msgMoveQKC', 'msgMoveDof', [0x459b63]),
    (0x4cabac, 'msgPublicMsgQKC', 'msgPublicMsgDof', [0x459ba1]),
    (0x4ce2c7, 'qkquanju', 'dofGlobal', [0x46fd18, 0x470c40, 0x471664, 0x4735ec, 0x476129]),
]


def retire_gateway_literals(source, data, elf, spans, patches, change):
    """Erase reviewed obsolete allocations only after their pointers moved.

    The pinned GCC image uses absolute imm32/data addresses for these literals.
    Check every byte-aligned imm32/pointer candidate, including interior string
    addresses. The disassembled-reference oracle independently checks RIP uses.
    """
    # All candidate addresses lie in the gateway's original read-only section.
    section_at=struct.unpack_from('<Q',source,40)[0]
    section_size,section_count,strings_index=struct.unpack_from('<HHH',source,58)
    if section_size!=64:raise ValueError('Unexpected gateway section ABI')
    sections=[struct.unpack_from('<IIQQQQIIQQ',source,section_at+i*64) for i in range(section_count)]
    names=sections[strings_index];names_at=names[4]
    regions=[]
    for section in sections:
        at=names_at+section[0];name=source[at:source.index(b'\0',at)]
        if name in (b'.text',b'.plt',b'.rodata',b'.data',b'.got',b'.ctors',b'.dtors',b'.jcr',b'.data.rel.ro',b'.rela.dyn'):
            regions.append((section[4],section[4]+section[5],bool(section[2]&4)))
    # These byte windows overlap the ModRM byte and displacement of a LEA;
    # neither instruction contains an address of a retired string.
    non_pointers={0x793a4:(0x793a2,bytes.fromhex('488db0c04d0000')),
                  0x93ce6:(0x93ce4,bytes.fromhex('488db8c04d0000'))}
    for at,expected in non_pointers.values():
        if bytes(data[at:at+len(expected)])!=expected:raise ValueError('Reviewed LEA preimage differs')
    references = []
    for m in re.finditer(rb'[\x00-\xff]{2}[\x4a-\x4d]\x00', bytes(data)):
        if m.start() not in non_pointers and any(lo<=m.start()<hi and (code or m.start()%8==0) for lo,hi,code in regions):
            references.append((m.start(), struct.unpack_from('<I', data, m.start())[0]))
    retired=[]
    for address, old in sorted(set(spans)):
        offset=elf.offset(address,len(old)+1)
        if source[offset:offset+len(old)+1] != old+b'\0':
            raise ValueError('Retired literal source allocation differs')
        current=bytes(data[offset:offset+len(old)])
        # Earlier schema edits already removed old names; do not overlap them.
        if current!=old:
            continue
        remaining=[(at,value) for at,value in references if address<=value<address+len(old)]
        # GCC pools suffix strings: e.g. the neutral 'config' suffix of an
        # obsolete protocol name is still used independently. Retain it.
        keep=len(old)
        for at,value in remaining:
            suffix=old[value-address:]
            if re.search(rb'(?i)qk|qingkong',suffix):
                raise ValueError('Retired gateway literal still referenced: '+hex(address)+' at '+hex(at))
            keep=min(keep,value-address)
        if keep:
            change(data,offset,old[:keep],bytes(keep),patches,'retire-gateway-literal-'+hex(address))
        retired.append(dict(address=address,bytes=keep,allocationBytes=len(old),
                            preservedSuffixReferences=[dict(offset=at,address=value) for at,value in remaining],
                            remainingAbsoluteReferencesToRetiredBytes=0))
    return retired


def retire_plugin_literals(source,data,elf,patches,change):
    """Retire redirected literals and obsolete compiler symbol-table copies."""
    records=[]
    for address in (0x275a2f,0x298821,0x298b38,0x2759e6,0x2759f8,0x2a6921,0x277afa):
        offset=elf.offset(address);old=source[offset:source.index(b'\0',offset)]
        for value in (address,(address-elf.tags[3])&0xffffffff):
            if struct.pack('<I',value) in data:
                raise ValueError('Retired plugin literal still has an encoded pointer: '+hex(address))
        change(data,offset,old,bytes(len(old)),patches,'retire-plugin-literal-'+hex(address))
        records.append(dict(address=address,offset=offset,bytes=len(old),kind='redirected-literal'))
    tail=max((p for p in elf.segments if p['type']==1),key=lambda p:p['address'])
    for m in re.finditer(rb'[A-Za-z0-9_.$]{4,}\0',source):
        value=m[0][:-1]
        if not re.search(rb'(?i)qk',value):continue
        if not (value.startswith((b'_Z',b'WongWork_function_')) or value==b'qksocket.cpp'):continue
        if not tail['offset']<=m.start()<len(source):
            raise ValueError('Compiler-name retirement escaped metadata/overlay storage')
        if bytes(data[m.start():m.end()-1])!=value:continue
        change(data,m.start(),value,bytes(len(value)),patches,'retire-plugin-symbol-copy-'+hex(m.start()))
        records.append(dict(address=tail['address']+m.start()-tail['offset'],offset=m.start(),bytes=len(value),kind='obsolete-symbol-metadata'))
    return records


def retire_gateway_stack_constants(source,data,elf,patches,change):
    """Retire unused stack SQL text, preserving every MOV, store and boundary.

    Exact call adapters supply the complete new SQL. Only printable immediates
    belonging to the reviewed old SQL are cleared; no executable branch moves.
    """
    import hashlib
    import json
    from pathlib import Path
    from scripts.dof.native_candidates import GATEWAY_PIN,gateway_sql_calls
    path=Path(__file__).resolve().parents[2]/'metadata/dof/source-audit/gateway-retired-stack-constants.json'
    raw=path.read_bytes()
    if hashlib.sha256(raw).hexdigest()!='048a40e9b039f5b64a389936a94b3666afcf98fca5d793910e02b8d9e10b756e':
        raise ValueError('Retired SQL immediate evidence changed')
    record=json.loads(raw);calls={r['call']:r for r in gateway_sql_calls()}
    if record['sourceSha256']!=GATEWAY_PIN or len(record['instructions'])!=303:
        raise ValueError('Unexpected retired SQL immediate evidence')
    for row in record['instructions']:
        old,new=bytes.fromhex(row['before']),bytes.fromhex(row['after'])
        call=calls[row['call']]
        if not (call['blockStart']<=row['address']<row['call'] and len(old)==10
                and old[:2] in (b'\x48\xb8',b'\x48\xb9',b'\x48\xbe')
                and old[2:] in call['text'].encode() and new==old[:2]+b' '*8):
            raise ValueError('Unreviewed stack SQL immediate')
        change(data,elf.offset(row['address'],10),old,new,patches,'retire-stack-sql-'+hex(row['address']))
    return record['instructions']
