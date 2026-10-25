"""Fail-closed derivation and ELF mapping checks; native behavior has a separate oracle."""
import os
from pathlib import Path
import struct
import tempfile
import unittest
from unittest.mock import patch
from scripts.dof.native_candidates import (Elf, change, digest, replacement_source,
    gateway_candidate, plugin_candidate, inline_candidate, GATEWAY_PIN, PLUGIN_PIN, INLINE_PIN)
from scripts.dof import native_candidates


class DerivationSafety(unittest.TestCase):
    def test_unpinned_inputs_are_rejected(self):
        for derive in (gateway_candidate, plugin_candidate, inline_candidate):
            with self.assertRaisesRegex(ValueError, 'pinned replacement'):
                derive(b'unknown native input')

    def test_source_alias_and_hash_mismatch_are_rejected(self):
        with tempfile.TemporaryDirectory() as tmp:
            source = Path(tmp) / 'input'
            source.write_bytes(b'fixed source')
            alias = Path(tmp) / 'alias'
            alias.symlink_to(source)
            with self.assertRaises(ValueError):
                replacement_source(alias, digest(source.read_bytes()))
            with self.assertRaises(ValueError):
                replacement_source(source, '0' * 64)
            self.assertEqual(source.read_bytes(), b'fixed source')

    def test_bad_preimage_and_length_do_not_mutate_buffer(self):
        data, patches = bytearray(b'abcdefgh'), []
        for before, after in [(b'zzz', b'new'), (b'bcd', b'longer')]:
            with self.assertRaises(ValueError):
                change(data, 1, before, after, patches, 'fixture')
            self.assertEqual(data, b'abcdefgh')
            self.assertEqual(patches, [])

    def test_elf_rejects_invalid_or_truncated_headers(self):
        for raw in (b'', b'x' * 64, b'\x7fELF\x02\x02' + bytes(58)):
            with self.assertRaises(ValueError):
                Elf(raw)

    def test_changed_sql_callsite_evidence_is_rejected(self):
        with patch.object(native_candidates, 'SQL_CALLS_PIN', '0' * 64):
            with self.assertRaisesRegex(ValueError, 'evidence has changed'):
                native_candidates.gateway_sql_calls()
        with patch.object(native_candidates, 'SQL_LITERALS_PIN', '0' * 64):
            with self.assertRaisesRegex(ValueError, 'evidence has changed'):
                native_candidates.gateway_sql_literals()


@unittest.skipUnless(os.environ.get('DOF_NATIVE_SAMPLE_DIR'), 'Explicit local replacement fixture required')
class PinnedNativeStructure(unittest.TestCase):
    def setUp(self):
        self.server = Path(os.environ['DOF_NATIVE_SAMPLE_DIR'])

    def check_derivation(self, name, pin, derive):
        source = replacement_source(self.server / name, pin)
        candidate, report = derive(source)
        self.assertEqual((self.server / name).read_bytes(), source)
        self.assertFalse(report['releaseReady'])
        self.assertTrue(report['originalStringsRetained'])
        accounted = set()
        for patch in report['patches']:
            start = patch['offset']
            before, after = bytes.fromhex(patch['before']), bytes.fromhex(patch['after'])
            self.assertEqual(source[start:start + len(before)], before)
            self.assertEqual(candidate[start:start + len(after)], after)
            accounted.update(range(start, start + len(after)))
        changed = {i for i in range(len(source)) if source[i] != candidate[i]}
        self.assertLessEqual(changed, accounted)
        elf = Elf(candidate)
        loads = sorted((p for p in elf.segments if p['type'] == 1), key=lambda p: p['address'])
        for p in loads:
            self.assertEqual(p['offset'] % p['align'], p['address'] % p['align'])
            self.assertLessEqual(p['fileSize'], p['memorySize'])
        for a, b in zip(loads, loads[1:]):
            self.assertLessEqual(a['address'] + a['memorySize'], b['address'])
        for item in report['names']:
            value = item['value'].encode() + b'\0'
            at = elf.offset(item['pointer'], len(value))
            self.assertEqual(candidate[at:at + len(value)], value)
        return candidate, elf, report

    def test_gateway_maps_new_pool_without_overwriting_bss_and_preserves_rpath(self):
        data, elf, report = self.check_derivation('QK', GATEWAY_PIN, gateway_candidate)
        at = elf.offset(elf.tags[5] + elf.tags[15], 16)
        self.assertEqual(data[at:at + 16], b'$ORIGIN/../lib\0\0')
        self.assertEqual(sum(len(item['references']) for item in report['names']), 54)
        self.assertNotIn(b'qingkong_config', data)
        self.assertIn(b'dof_native.config', data)

    def test_retirement_preserves_live_pooled_suffix_and_rejects_a_dangling_reference(self):
        from scripts.dof.native_residuals import retire_gateway_literals
        source=replacement_source(self.server/'QK',GATEWAY_PIN)
        data,plan=gateway_candidate(source);elf=Elf(source)
        offset=elf.offset(0x4ca9d4,7)
        self.assertEqual(data[offset:offset+7],b'config\0')
        damaged=bytearray(data)
        at=elf.offset(0x4dc27e,3);damaged[at:at+3]=b'qk\0'
        at=elf.offset(0x493770,5);damaged[at:at+5]=source[at:at+5]
        with self.assertRaisesRegex(ValueError,'still referenced'):
            retire_gateway_literals(source,damaged,elf,[(0x4dc27e,b'qk')],[],change)

    def test_plugin_maps_each_new_string_and_shortens_only_reviewed_query(self):
        data, elf, report = self.check_derivation('qk_plugin.so', PLUGIN_PIN, plugin_candidate)
        self.assertEqual(len(report['names']), 7)
        query = b'select reDCdkeyOpt from config where switch_reCdkey=1\0'
        at = elf.offset(0x2864dc, len(query))
        self.assertEqual(data[at:at + len(query)], query)
        self.assertNotIn(b'/root/qk_plugin_config.ini\0', data)
        self.assertNotIn(b'Invalid qksocket', data)
        table=data[elf.offset(elf.tags[5]):elf.offset(elf.tags[5])+elf.tags[10]]
        self.assertNotIn(b'qk',table.lower())
        for row in report['retiredLiterals']:
            self.assertEqual(data[row['offset']:row['offset']+row['bytes']],bytes(row['bytes']))

    def test_random_box_query_adapter_preserves_virtualized_code_and_adds_only_rx(self):
        data, elf, report = self.check_derivation('qk_plugin.so', PLUGIN_PIN, plugin_candidate)
        adapter=report['randomBox']
        self.assertEqual(elf.phnum,9)
        self.assertEqual(adapter['callerReturn'],0x7480b9)
        self.assertEqual(adapter['operation'],5)
        self.assertNotIn(b'qk.randombox',data)
        self.assertIn(b'SELECT DISTINCT item_id FROM dof_native.randombox\0',data)
        segment,=(p for p in elf.segments if p['type']==1 and p['address']<=adapter['adapter']<p['address']+p['memorySize'])
        self.assertEqual(segment['flags'],5)
        original=(self.server/'qk_plugin.so').read_bytes()
        # VM opcode area containing the operation-5 call remains byte-identical.
        old=Elf(original);at=old.offset(0x748000,512)
        self.assertEqual(original[at:at+512],data[at:at+512])

    def test_plugin_sysv_lookup_uses_new_names_without_renumbering_symbols(self):
        from scripts.dof.native_symbols import elf_hash
        data, elf, report = self.check_derivation('qk_plugin.so', PLUGIN_PIN, plugin_candidate)
        self.assertEqual(len(report['dynamicNames']),29)
        at=elf.offset(elf.tags[4],8)
        buckets,count=struct.unpack_from('<II',data,at)
        table=struct.unpack_from('<'+str(buckets+count)+'I',data,at+8)
        def lookup(name):
            index=table[elf_hash(name)%buckets];seen=set()
            while index:
                self.assertNotIn(index,seen);seen.add(index)
                start,=struct.unpack_from('<I',data,elf.offset(elf.tags[6]+index*16))
                off=elf.offset(elf.tags[5]+start)
                if data[off:data.index(b'\0',off)]==name:return index
                index=table[buckets+index]
            return None
        for row in report['dynamicNames']:
            self.assertEqual(lookup(row['new'].encode()),row['index'])
            self.assertIsNone(lookup(row['old'].encode()))

    def test_equipment_export_points_to_rx_code_and_rw_dispatch_slot(self):
        data, elf, report = self.check_derivation('qk_plugin.so', PLUGIN_PIN, plugin_candidate)
        binding = report['equipment']
        _, address, size, info, _, section = struct.unpack_from(
            '<IIIBBH', data, elf.offset(elf.tags[6] + 839 * 16))
        self.assertEqual((address, size, info, section),
            (binding['entry'], len(bytes.fromhex(binding['code'])), 18, binding['sectionIndex']))
        for address, flags in [(binding['entry'], 5), (binding['dispatchSlot'], 6)]:
            segment, = (p for p in elf.segments if p['type'] == 1
                        and p['address'] <= address < p['address'] + p['memorySize'])
            self.assertEqual(segment['flags'], flags)
        self.assertEqual(data[elf.offset(binding['dispatchSlot']):elf.offset(binding['dispatchSlot'])+4], bytes(4))
        shoff = struct.unpack_from('<I', data, 32)[0]
        self.assertEqual(struct.unpack_from('<10I', data, shoff + section * 40)[3:6],
                         (binding['entry'], elf.offset(binding['entry']), size))

    def test_gateway_sql_adapters_use_new_literals_and_original_consumer(self):
        data, elf, report = self.check_derivation('QK', GATEWAY_PIN, gateway_candidate)
        self.assertEqual(len(report['sqlCalls']), 33)
        for row in report['sqlCalls']:
            call = elf.offset(row['callSite'], 5)
            self.assertEqual(data[call], 0xe8)
            self.assertEqual(row['callSite'] + 5 + struct.unpack_from('<i', data, call + 1)[0], row['adapter'])
            at = elf.offset(row['adapter'], 15)
            self.assertEqual(data[at:at + 2], b'\x48\xbe')
            self.assertEqual(struct.unpack_from('<Q', data, at + 2)[0], row['pointer'])
            self.assertEqual(data[at + 10], 0xe9)
            self.assertEqual(row['adapter'] + 15 + struct.unpack_from('<i', data, at + 11)[0], row['consumer'])
            value = row['value'].encode() + b'\0'
            text = elf.offset(row['pointer'], len(value))
            self.assertEqual(data[text:text + len(value)], value)
            self.assertNotRegex(row['value'], r'\bqk\b|qingkong_config')
            segment, = (p for p in elf.segments if p['type'] == 1
                        and p['address'] <= row['adapter'] < p['address'] + p['fileSize'])
            self.assertEqual(segment['flags'], 5)
        self.assertEqual(len(report['sqlLiterals']), 97)
        self.assertEqual(sum(len(r['references']) for r in report['sqlLiterals']), 108)
        for row in report['sqlLiterals']:
            value = row['value'].encode() + b'\0'
            text = elf.offset(row['pointer'], len(value))
            self.assertEqual(data[text:text + len(value)], value)
            self.assertNotRegex(row['value'], r'\bqk\b|qingkong_config')
            for reference in row['references']:
                at = elf.offset(reference['address'], 5)
                self.assertIn(data[at], (0xbe, 0xba))
                self.assertEqual(struct.unpack_from('<I', data, at + 1)[0], row['pointer'])

    def test_inline_adds_post_init_callback_without_replacing_protected_initializer(self):
        data, elf, report = self.check_derivation('libantisvrinline.so', INLINE_PIN, inline_candidate)
        self.assertEqual(elf.tags[12], 0x25cdd4)
        self.assertEqual(elf.tags[25], report['initArray'])
        self.assertEqual(elf.tags[27], 4)
        array = elf.offset(elf.tags[25], 4)
        self.assertEqual(struct.unpack_from('<I', data, array)[0], report['callback'])
        last_rel = elf.offset(elf.tags[17], elf.tags[18]) + elf.tags[18] - 8
        self.assertEqual(struct.unpack_from('<II', data, last_rel), (report['initArray'], 8))
        self.assertEqual(len(report['memoryOperands']), 5)
        self.assertEqual(elf.phnum, 10)
        original = Elf((self.server / 'libantisvrinline.so').read_bytes())
        previous_end = max(p['address'] + p['memorySize'] for p in original.segments if p['type'] == 1)
        additions = [p for p in elf.segments if p['type'] == 1 and p['address'] >= previous_end]
        self.assertEqual([p['flags'] for p in additions], [5, 6])
        self.assertTrue(additions[0]['address'] <= report['callback'] < additions[0]['address'] + additions[0]['memorySize'])
        self.assertTrue(additions[1]['address'] <= report['initArray'] < additions[1]['address'] + additions[1]['memorySize'])


if __name__ == '__main__':
    unittest.main()
