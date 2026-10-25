"""Independent instruction/reference audit of the fixed server naming recipe.

Byte collisions in binary tables or protected code are recorded individually,
with offsets and hashes, rather than hidden by a directory-wide allowlist.
Real decoded memory and feature acceptance are separate mandatory evidence.
"""
import argparse
import hashlib
import json
from pathlib import Path
import re
import struct
import sys

ROOT = Path(__file__).resolve().parents[3]
sys.path.insert(0, str(ROOT))
from capstone import Cs, CS_ARCH_X86, CS_MODE_64
from capstone.x86 import X86_OP_IMM, X86_OP_MEM, X86_REG_RIP
from scripts.dof.native_candidates import Elf, GATEWAY_PIN, PLUGIN_PIN, INLINE_PIN, replacement_source


def sha(raw):
    return hashlib.sha256(raw).hexdigest()


def sections(raw):
    wide = raw[4] == 2
    offset = struct.unpack_from('<Q' if wide else '<I', raw, 40 if wide else 32)[0]
    size, count, names = struct.unpack_from('<HHH', raw, 58 if wide else 46)
    rows = [struct.unpack_from('<IIQQQQIIQQ' if wide else '<10I', raw, offset+i*size) for i in range(count)]
    strings = rows[names][4]
    return [dict(name=raw[strings+r[0]:raw.index(b'\0', strings+r[0])].decode(),
                 type=r[1], flags=r[2], address=r[3], offset=r[4], size=r[5]) for r in rows]


def verify(server, runtime):
    audit = json.loads((runtime/'source-audit/derivation.json').read_text())['derivations']
    raw = (runtime/'bin/dof-gateway').read_bytes()
    ranges = [(r['address'], r['address']+r['bytes']) for r in audit['bin/dof-gateway']['retiredLiterals']]
    elf = Elf(raw)
    for lo, hi in ranges:
        assert raw[elf.offset(lo):elf.offset(lo)+hi-lo] == bytes(hi-lo)
    decoder = Cs(CS_ARCH_X86, CS_MODE_64)
    decoder.detail = True
    count = total = 0
    for section in sections(raw):
        if section['name'] not in ('.text', '.plt', '.init', '.fini'):
            continue
        covered = 0
        for ins in decoder.disasm(raw[section['offset']:section['offset']+section['size']], section['address']):
            assert ins.address == section['address']+covered
            covered += ins.size
            count += 1
            for op in ins.operands:
                target = op.imm if op.type == X86_OP_IMM else (
                    ins.address+ins.size+op.mem.disp if op.type == X86_OP_MEM and op.mem.base == X86_REG_RIP else None)
                if target is not None and any(lo <= target < hi for lo, hi in ranges):
                    raise AssertionError('Instruction still references retired allocation: '+hex(ins.address))
        assert covered == section['size'], ('Incomplete instruction coverage', section['name'], covered)
        total += covered
    # Validate the exact consumer prefixes. Neither takes incoming RAX/RCX as
    # an argument; both call mysql_ping before reading RAX. Original prefixes
    # must be unchanged; the SQL oracle checks every other ABI value and byte.
    source = replacement_source(server/'QK', GATEWAY_PIN)
    for lo, end in ((0x490210, 0x490237), (0x490990, 0x4909ae)):
        off = Elf(source).offset(lo)
        assert raw[off:off+end-lo] == source[off:off+end-lo]
        for ins in decoder.disasm(raw[off:off+end-lo], lo):
            reads, _ = ins.regs_access()
            assert not {'rax','eax','rcx','ecx'}.intersection(ins.reg_name(r) for r in reads)
    collisions = []
    bindings = {}
    for name, old, pin in [('bin/dof-gateway','QK',GATEWAY_PIN),
                           ('lib/libdof-plugin.so','qk_plugin.so',PLUGIN_PIN),
                           ('lib/libdof-inline.so','libantisvrinline.so',INLINE_PIN)]:
        data = (runtime/name).read_bytes()
        original = replacement_source(server/old, pin)
        bindings[name] = sha(data)
        for marker in (b'qingkong', '晴空'.encode(), '晴空'.encode('gbk'), b'/root/QK', b'qk.randombox'):
            assert marker not in data, (name, 'Semantic marker remains')
        for match in re.finditer(rb'[\x20-\x7e]{4,}', data):
            if not re.search(rb'(?i)qk|qingkong', match[0]):
                continue
            offset = match.start()
            assert original[offset:match.end()] == match[0]
            regions = [s for s in sections(data) if s['type'] != 8 and s['offset'] <= offset < s['offset']+s['size']]
            if name == 'bin/dof-gateway':
                assert offset in (0xb0fd3, 0xc7ef1, 0xc8421, 0xca98a)
                kind = 'opaque-protocol-value-preserved' if offset == 0xca98a else 'binary-table-byte-collision'
                if offset != 0xca98a:
                    lo = data.rfind(b'\0', 0, offset)+1
                    hi = data.index(b'\0', offset)
                    assert any(b < 32 or b > 126 for b in data[lo:hi])
            elif name == 'lib/libdof-plugin.so':
                assert offset == 0x11f29 or any(s['name'] in ('.text','.xg/') for s in regions)
                kind = 'original-binary-hash-table' if offset == 0x11f29 else 'instruction-or-protected-byte-collision'
            else:
                assert any(s['name'] == '.Zw6' for s in regions)
                kind = 'protected-file-byte-collision-decoded-memory-checked-separately'
            collisions.append(dict(file=name, offset=offset, bytes=len(match[0]), sha256=sha(match[0]), kind=kind))
    return dict(schema=1, passed=True, serverAccessed=False, hashes=bindings,
                gatewayInstructions=count, gatewayCodeBytes=total, retiredAllocations=len(ranges),
                remainingReferencesToRetiredGatewayBytes=0, classifiedByteCollisions=collisions,
                limitations=['Protected regions are not exhaustively disassembled; exact loaded-memory checks are required.',
                             'Client resource paths and client protocol consumers are the next phase, not server acceptance.'])


if __name__ == '__main__':
    parser=argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--server', type=Path, required=True)
    parser.add_argument('--runtime', type=Path, required=True)
    parser.add_argument('--report', type=Path, required=True)
    args=parser.parse_args()
    result=verify(args.server,args.runtime)
    with args.report.open('x') as stream:
        json.dump(result,stream,indent=2);stream.write('\n')
    print('Independent gateway reference audit passed:',result['gatewayInstructions'],'instructions')
