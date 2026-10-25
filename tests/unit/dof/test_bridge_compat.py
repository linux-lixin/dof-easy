"""ABI/iterator lifetime oracle; real bridge restart is a separate acceptance test."""
import os
from pathlib import Path
import struct
import unittest

from scripts.dof.bridge_compat import BRIDGE_PIN, derive
from scripts.dof.native_candidates import Elf, replacement_source
try:
    from unicorn import Uc, UC_ARCH_X86, UC_MODE_32, UC_HOOK_CODE
    from unicorn.x86_const import UC_X86_REG_ESP, UC_X86_REG_EBP, UC_X86_REG_EAX, UC_X86_REG_EIP
except ImportError:
    Uc = None


class BridgeInputSafety(unittest.TestCase):
    def test_other_bridge_versions_are_rejected(self):
        with self.assertRaisesRegex(ValueError, 'pinned bridge'):
            derive(b'unrecognized bridge')


@unittest.skipUnless(Uc and os.environ.get('DOF_BRIDGE_SAMPLE'), 'Explicit pinned bridge and Unicorn required')
class BridgeIteratorABI(unittest.TestCase):
    def test_erase_receives_old_node_after_local_iterator_advances_exactly_once(self):
        source = replacement_source(Path(os.environ['DOF_BRIDGE_SAMPLE']), BRIDGE_PIN)
        raw, report = derive(source)
        self.assertFalse(report['releaseReady'])
        elf = Elf(raw)
        for successor in [0x71002100, 0x71002200, 0x71001004]:  # first/middle/end sentinel
            u = Uc(UC_ARCH_X86, UC_MODE_32)
            for segment in elf.segments:
                if segment['type'] != 1:
                    continue
                start = segment['address'] & ~4095
                end = (segment['address'] + segment['memorySize'] + 4095) & ~4095
                u.mem_map(start, end - start)
                u.mem_write(segment['address'], raw[segment['offset']:segment['offset'] + segment['fileSize']])
            u.mem_map(0x70000000, 0x10000)
            u.mem_map(0x71000000, 0x10000)
            sp, frame, container, old = 0x70007000, 0x70008000, 0x71001000, 0x71002000
            def put(address, value): u.mem_write(address, struct.pack('<I', value))
            def word(address): return struct.unpack('<I', u.mem_read(address, 4))[0]
            put(sp, container); put(sp + 4, old); put(frame - 0x58, old)
            u.reg_write(UC_X86_REG_ESP, sp); u.reg_write(UC_X86_REG_EBP, frame)
            calls = []
            def hook(u, address, size, unused):
                if address not in [0x0804fe64, 0x08063814, 0x080636ae]:
                    return
                stack = u.reg_read(UC_X86_REG_ESP)
                if address == 0x0804fe64:
                    self.assertEqual(calls, [])
                    self.assertEqual(word(stack + 4), old)
                    self.assertEqual(word(frame - 0x58), old)
                    u.reg_write(UC_X86_REG_EAX, successor)
                    calls.append('advance')
                elif address == 0x08063814:
                    self.assertEqual(calls, ['advance'])
                    self.assertEqual((word(stack + 4), word(stack + 8)), (container, old))
                    self.assertEqual(word(frame - 0x58), successor)
                    calls.append('erase')
                    u.mem_write(old, b'freed node data!')
                else:
                    self.assertEqual(calls, ['advance', 'erase'])
                    self.assertEqual(word(stack + 4), container + 0x100)
                    calls.append('decrement count')
                u.reg_write(UC_X86_REG_EIP, word(stack))
                u.reg_write(UC_X86_REG_ESP, stack + 4)
            u.hook_add(UC_HOOK_CODE, hook)
            u.emu_start(0x08062bfe, 0x08062c03, count=1000, timeout=1000000)
            self.assertEqual(u.reg_read(UC_X86_REG_ESP), sp)
            self.assertEqual(u.reg_read(UC_X86_REG_EIP), 0x08062c03)
            # The unmodified native code sets this argument before the second site.
            put(sp, container + 0x100)
            u.emu_start(0x08062c1a, 0x08062c3c, count=1000, timeout=1000000)
            self.assertEqual(u.reg_read(UC_X86_REG_ESP), sp)
            self.assertEqual(u.reg_read(UC_X86_REG_EIP), 0x08062c3c)
            self.assertEqual(word(frame - 0x58), successor)
            self.assertEqual(calls, ['advance', 'erase', 'decrement count'])


if __name__ == '__main__':
    unittest.main()
