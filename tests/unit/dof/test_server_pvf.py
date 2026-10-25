import importlib.util
from pathlib import Path
import struct
import tempfile
import unittest
import zlib

ROOT=Path(__file__).resolve().parents[3]
spec=importlib.util.spec_from_file_location('pvf',ROOT/'services/game/runtime/ubuntu/gateway/pvf.py')
pvf=importlib.util.module_from_spec(spec);spec.loader.exec_module(pvf)


def fixture():
    data=pvf.pack_strings([b'unchanged', b'[qk rarity color]', b'[qk randombox]', b'private user text qk'])
    pad=data+bytes((-len(data))%4);number=83;crc=zlib.crc32(pad,number)
    name=b'stringtable.bin'
    tree=struct.pack('<II',number,len(name))+name+struct.pack('<III',len(data),crc,0)
    tree+=bytes((-len(tree))%4);tree_crc=zlib.crc32(tree,1)
    return struct.pack('<I',4)+b'test'+struct.pack('<4I',1,len(tree),tree_crc,1)+pvf.crypt(tree,tree_crc,True)+pvf.crypt(pad,crc,True)


class PvfMigration(unittest.TestCase):
    def test_real_format_roundtrip_preserves_indices_and_unrelated_values(self):
        with tempfile.TemporaryDirectory() as td:
            source,target,again=(Path(td)/x for x in ('source','target','again'))
            raw=fixture();source.write_bytes(raw)
            result=pvf.convert(source,target)
            self.assertEqual(result['changedServerTags'],2)
            self.assertEqual(source.read_bytes(),raw)
            second=pvf.convert(target,again)
            self.assertEqual(second['changedServerTags'],0)
            self.assertEqual(target.read_bytes(),again.read_bytes())
            self.assertEqual(second['strings'],4)
            b=target.read_bytes();_,size,crc,count=struct.unpack_from('<4I',b,8)
            tree=pvf.crypt(b[24:24+size],crc)
            length,check,offset=struct.unpack_from('<III',tree,8+len(b'stringtable.bin'))
            data=pvf.crypt(b[24+size+offset:],check)[:length]
            self.assertEqual(pvf.strings(data),[b'unchanged',b'[dof rarity color]',b'[dof randombox]',b'private user text qk'])

    def test_bad_crc_rejected_without_output(self):
        with tempfile.TemporaryDirectory() as td:
            source,target=(Path(td)/x for x in ('source','target'))
            raw=bytearray(fixture());raw[24]^=1;source.write_bytes(raw)
            with self.assertRaises(ValueError):pvf.convert(source,target)
            self.assertFalse(target.exists())

    def test_broken_string_offsets_rejected(self):
        data=bytearray(pvf.pack_strings([b'a',b'b']))
        struct.pack_into('<I',data,8,9999)
        with self.assertRaises(ValueError):pvf.strings(data)
