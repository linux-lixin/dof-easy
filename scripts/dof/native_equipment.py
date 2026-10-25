"""Bind the missing equipment ABI to the source-owned runtime implementation.

The dispatcher pointer lives in RW data, the tail-call bridge in RX code. An
unregistered bridge traps instead of pretending the feature succeeded. Gadget
installs the implementation before gameplay and verifies the native ABI.
"""
import struct
from scripts.dof.native_candidates import Elf

SYMBOL = '_ZN5DBMgr19Equipment_BroadcastEP5CUserii'


def bind_equipment(source, data, slot, patches, change):
    elf = Elf(data)
    tail = max((p for p in elf.segments if p['type'] == 1), key=lambda p:p['address'])
    if tail['flags'] != 5 or tail['offset'] + tail['fileSize'] != len(data):
        raise ValueError('Equipment bridge requires the appended RX segment')
    data.extend(bytes((-len(data)) % 16))
    offset = len(data); address = tail['address'] + offset - tail['offset']
    # Save eax, find the RW dispatch slot using a PIC base, then tail-call the
    # registered cdecl implementation without changing arguments/return address.
    code = bytes.fromhex('50e800000000588b80') + struct.pack('<i', slot-(address+6))
    code += bytes.fromhex('85c07404870424c30f0b')
    data.extend(code)
    shoff = struct.unpack_from('<I',data,32)[0]
    shsize,shnum = struct.unpack_from('<HH',data,46)
    if shsize != 40:raise ValueError('Unexpected section ABI')
    sections = bytes(data[shoff:shoff+shsize*shnum])
    data.extend(bytes((-len(data))%4));new_shoff=len(data)
    data.extend(sections)
    # An unnamed, allocated executable section describes the new bridge; every
    # prior section index and relocation stays stable.
    data.extend(struct.pack('<10I',0,1,6,address,offset,len(code),0,0,16,0))
    change(data,32,struct.pack('<I',shoff),struct.pack('<I',new_shoff),patches,'equipment-section-table')
    change(data,48,struct.pack('<H',shnum),struct.pack('<H',shnum+1),patches,'equipment-code-section')
    sym=elf.offset(elf.tags[6]+839*16,16)
    name,value,size,info,other,section=struct.unpack_from('<IIIBBH',data,sym)
    string=elf.offset(elf.tags[5]+name)
    if data[string:string+len(SYMBOL)+1] != SYMBOL.encode()+b'\0' or (value,size,info,other,section)!=(0,0,16,0,0):
        raise ValueError('Unexpected missing equipment ABI')
    change(data,sym+4,bytes(data[sym+4:sym+16]),struct.pack('<IIBBH',address,len(code),18,0,shnum),patches,'equipment-defined-function')
    length=len(data)-tail['offset']
    for field in (16,20):struct.pack_into('<I',data,tail['header']+field,length)
    return dict(symbol=SYMBOL,entry=address,dispatchSlot=slot,code=code.hex(),
                symbolIndex=839,sectionIndex=shnum,unregisteredBehavior='ud2',
                implementation='runtime/gateway/equipment.js',addedMappingPermissions='R-X')
