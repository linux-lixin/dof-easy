import struct
import unittest
import os
import hashlib
from pathlib import Path

from scripts.dof.client_reader import Code, LITERALS, Pe32, derive, make_code

try:
    from unicorn import Uc, UC_ARCH_X86, UC_MODE_32, UC_HOOK_CODE
    from unicorn.x86_const import (UC_X86_REG_EAX, UC_X86_REG_EBX, UC_X86_REG_ECX,
                                   UC_X86_REG_EDX, UC_X86_REG_ESI, UC_X86_REG_EDI,
                                   UC_X86_REG_ESP, UC_X86_REG_EBP, UC_X86_REG_EIP)
except ImportError:
    Uc = None


class InputValidationTest(unittest.TestCase):
    def test_rejects_unpinned_and_truncated_images(self):
        with self.assertRaises(ValueError):
            derive(b'unknown executable')
        for value in [b'', b'MZ' + bytes(62), b'PE\0\0']:
            with self.assertRaises(ValueError):
                Pe32(value)

    def test_assembler_refuses_duplicate_labels(self):
        code = Code(0x1000)
        code.label('entry')
        with self.assertRaises(ValueError):
            code.label('entry')

    @unittest.skipUnless(os.environ.get('DOF_PRIVATE_CLIENT_INPUTS'), 'Pinned private client inputs not supplied')
    def test_pinned_images_are_deterministic_and_preserve_loader_directories(self):
        from scripts.protocol.dof.prepare_client_inputs import PINNED_BINARIES
        root = Path(os.environ['DOF_PRIVATE_CLIENT_INPUTS'])
        for name in ['启动器.exe', 'ijl15.dll']:
            with self.subTest(name=name):
                source = (root / name).read_bytes()
                output, report = derive(source)
                repeat, repeat_report = derive(source)
                self.assertEqual(output, repeat)
                self.assertEqual(report, repeat_report)
                self.assertEqual(hashlib.sha256(output).hexdigest(), PINNED_BINARIES[report['file']])
                before, after = Pe32(source), Pe32(output)
                self.assertEqual(after.count, before.count + 2)
                self.assertEqual(output[after.optional + 96:after.table], source[before.optional + 96:before.table])
                self.assertEqual(output[after.optional + 16:after.optional + 20], source[before.optional + 16:before.optional + 20])
                self.assertEqual([s['flags'] for s in after.sections[-2:]], [0x60000020, 0x40000040])
                expected = bytearray(source)
                for patch in report['patches']:
                    value = bytes.fromhex(patch['after']);offset = before.offset(patch['rva'], len(value))
                    expected[offset:offset + len(value)] = value
                for section in before.sections:
                    start, size = section['offset'], section['raw_size']
                    self.assertEqual(output[start:start + size], expected[start:start + size])
                self.assertEqual((root / name).read_bytes(), source)


@unittest.skipIf(Uc is None, 'Install Unicorn to execute i386 adapter regression checks')
class ConsumerAbiTest(unittest.TestCase):
    def setUp(self):
        self.base, self.code_rva, self.data_rva = 0x400000, 0x70000, 0x71000
        self.uc = Uc(UC_ARCH_X86, UC_MODE_32)
        self.uc.mem_map(self.base, 0x2000000)
        self.stack = self.base + 0x1f00000
        self.rows = [dict(sourceRva=old, targetRva=self.data_rva + 0x100 + index * 0x80, kind=kind)
                     for index, (old, _, new, kind) in enumerate(LITERALS)]
        for index, row in enumerate(self.rows):
            self.uc.mem_write(self.base + self.data_rva + index * 12,
                              struct.pack('<III', row['sourceRva'], row['targetRva'], row['kind']))
            self.uc.mem_write(self.base + row['targetRva'], LITERALS[index][2] + b'\0')
        self.marker = self.data_rva + 0xa00
        self.uc.mem_write(self.base + self.marker, b'token_dof_version\0')
        code, self.labels = make_code(self.code_rva, self.data_rva, self.rows)
        self.uc.mem_write(self.base + self.code_rva, code)
        self.sentinel = self.base + 0x72000
        self.uc.reg_write(UC_X86_REG_ESP, self.stack)
        self.write(self.stack, self.sentinel)

    def write(self, address, value):
        self.uc.mem_write(address, struct.pack('<I', value))

    def read(self, address):
        return struct.unpack('<I', self.uc.mem_read(address, 4))[0]

    def run_to(self, label, stop):
        self.uc.emu_start(self.base + self.labels[label], self.base + stop, count=20000)
        self.assertEqual(self.uc.reg_read(UC_X86_REG_EIP), self.base + stop)

    def test_file_exists_long_name_preserves_stack_and_flags(self):
        self.write(self.stack + 4, self.base + self.rows[0]['sourceRva'])
        self.uc.reg_write(UC_X86_REG_EBX, 0x12345678)
        self.run_to('file-exists', 0x5333a)
        self.assertEqual(self.uc.reg_read(UC_X86_REG_ECX), self.base + self.rows[0]['targetRva'])
        self.assertEqual(self.uc.reg_read(UC_X86_REG_ESP), self.stack - 0x140)
        self.assertEqual(self.uc.reg_read(UC_X86_REG_EBX), 0x12345678)

    def test_compare_redirects_both_operands_and_leaves_heap_pointer(self):
        for side in [0, 1]:
            with self.subTest(side=side):
                self.uc.reg_write(UC_X86_REG_ESP, self.stack)
                native = self.base + self.rows[6]['sourceRva']
                heap = self.base + 0x100000
                args = [heap, heap]
                args[side] = native
                for i, value in enumerate(args):
                    self.write(self.stack + 4 + i * 4, value)
                self.run_to('compare', 0x11d2)
                expected = [heap, heap]
                expected[side] = self.base + self.rows[6]['targetRva']
                self.assertEqual([self.uc.reg_read(UC_X86_REG_EDX), self.uc.reg_read(UC_X86_REG_ECX)], expected)
                self.assertEqual(self.uc.reg_read(UC_X86_REG_ESP), self.stack)

    def test_length_updates_copy_source_and_concat_source(self):
        old, new = self.base + self.rows[5]['sourceRva'], self.base + self.rows[5]['targetRva']
        slot = self.base + 0x100004
        self.uc.reg_write(UC_X86_REG_EBX, old)
        self.uc.reg_write(UC_X86_REG_ESI, slot)
        self.write(self.stack, self.base + 0x12f6)
        self.write(self.stack + 4, old)
        self.write(slot - 4, old)
        self.run_to('length', 0x126e)
        self.assertEqual(self.uc.reg_read(UC_X86_REG_EBX), new)
        self.assertEqual(self.read(self.stack + 4), new)
        self.assertEqual(self.read(slot - 4), new)
        self.assertEqual(self.uc.reg_read(UC_X86_REG_ESI), slot)

    def test_getter_has_no_version_query_and_preserves_arguments(self):
        from scripts.dof.client_reader import LAUNCHER_ABI, DLL_ABI
        for abi in (LAUNCHER_ABI, DLL_ABI):
            self.setUp()
            code, labels = make_code(self.code_rva, self.data_rva, self.rows, abi)
            self.uc.mem_write(self.base + self.code_rva, code)
            self.uc.reg_write(UC_X86_REG_ESP, self.stack)
            self.uc.reg_write(UC_X86_REG_EBP, 444)
            self.uc.reg_write(UC_X86_REG_EBX, 111)
            self.write(self.stack + 4, 123)
            self.write(self.stack + 8, 456)
            self.uc.emu_start(self.base + labels['get-field'], self.base + abi['getter'] + 9, count=100)
            self.assertEqual(self.uc.reg_read(UC_X86_REG_EBX), 111)
            self.assertEqual(self.read(self.stack + 4), 123)
            self.assertEqual(self.read(self.stack + 8), 456)
            self.assertEqual(self.uc.reg_read(UC_X86_REG_EBP), self.stack - 4)
            self.assertEqual(self.uc.reg_read(UC_X86_REG_ESP), self.stack - 4 - 12)


if __name__ == '__main__': unittest.main()
