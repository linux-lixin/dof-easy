"""Rename the pinned plugin's dynamic ABI without changing symbol indices.

Uses its existing SysV hash-table capacity, relocates the string table, and
updates the section description. No protection code or relocation is removed.
The shorter RTTI class spelling preserves all existing typeinfo addresses.
"""
import struct
import re


def elf_hash(value):
    result=0
    for c in value:
        result=((result<<4)+c)&0xffffffff
        high=result&0xf0000000
        if high: result^=high>>24
        result&=~high
    return result


def rename_plugin_symbols(source, data, elf, tail, patches, change):
    if 0x6ffffef5 in elf.tags or 4 not in elf.tags or elf.tags.get(11)!=16:
        raise ValueError('Unexpected pinned plugin symbol/hash layout')
    hash_at=elf.offset(elf.tags[4],8)
    buckets,count=struct.unpack_from('<II',source,hash_at)
    if count!=1192 or not 1<=buckets<=4096:
        raise ValueError('Unexpected pinned plugin dynamic symbol count')
    strings_at=elf.offset(elf.tags[5],elf.tags[10])
    original_strings=source[strings_at:strings_at+elf.tags[10]]
    strings=bytearray(original_strings)
    symbols_at=elf.offset(elf.tags[6],count*16)
    names=[]; renamed=[]
    for index in range(count):
        at=symbols_at+index*16
        name_at,value,size,info,other,section=struct.unpack_from('<IIIBBH',source,at)
        end=original_strings.find(b'\0',name_at)
        if end<0: raise ValueError('Unterminated native symbol')
        old=original_strings[name_at:end];new=old
        new=new.replace(b'8qksocket',b'7DofSock').replace(b'9qk_socket',b'9dofSocket')
        new=new.replace(b'8qk_dinit',b'8dof_init')
        new=new.replace(b'29qk_DisPatcher_SetArea_process',b'15dofDispatchArea')
        new=new.replace(b'21QK_CreateCharac_relay',b'20DofCreateCharacRelay')
        new=new.replace(b'WongWork_function_QK_',b'WongWork_function_Dof_')
        new=new.replace(b'WongWork_function_qk_',b'WongWork_function_dof_')
        method=re.fullmatch(rb'_ZN5DBMgr(\d+)(.*)Ev',old)
        if method and b'Qk' in method[2]:
            if int(method[1])!=len(method[2]):raise ValueError('Unrecognized C++ method encoding')
            renamed_method=method[2].replace(b'Qk',b'Dof')
            new=b'_ZN5DBMgr'+str(len(renamed_method)).encode()+renamed_method+b'Ev'
        names.append(new)
        if new==old:continue
        if not section:raise ValueError('Cannot rename an external provider requirement')
        offset=len(strings);strings.extend(new+b'\0')
        change(data,at,struct.pack('<I',name_at),struct.pack('<I',offset),patches,'dynamic-symbol-'+old.decode())
        renamed.append(dict(index=index,old=old.decode(),new=new.decode()))
        if old.startswith(b'_ZTS'):
            before=old[4:]+b'\0';after=new[4:]+b'\0'
            if len(after)>len(before):raise ValueError('RTTI needs a separate relocation')
            change(data,elf.offset(value,len(before)),before,after.ljust(len(before),b'\0'),patches,'rtti-class-name')
    if len(renamed)!=29:raise ValueError('Native dynamic rename set changed')
    if any(b'qk' in n.lower() or b'qingkong' in n.lower() for n in names):
        raise ValueError('Unaccounted old-brand dynamic symbol')
    # The live table previously copied retired spellings before appending the
    # new names. Preserve offsets of all still-used strings, but clear each
    # old symbol spelling only when no retained symbol shares any of its bytes.
    live=set()
    for index,name in enumerate(names):
        if any(row['index']==index for row in renamed):continue
        at=symbols_at+index*16
        start=struct.unpack_from('<I',source,at)[0]
        live.update(range(start,start+len(name)+1))
    for row in renamed:
        start=struct.unpack_from('<I',source,symbols_at+row['index']*16)[0]
        end=start+len(row['old'])
        if any(at in live for at in range(start,end)):
            raise ValueError('Retired symbol spelling shares a live string')
        strings[start:end]=bytes(end-start)
    if re.search(rb'(?i)qk|qingkong',strings):
        raise ValueError('Live dynamic string table retains old semantic names')
    pool_offset=len(data);pool_address=tail['address']+pool_offset-tail['offset']
    data.extend(strings)
    heads=[0]*buckets;chains=[0]*count
    # Every original index remains stable, including weak/local symbols. The
    # loader still applies binding/visibility/section checks at lookup time.
    for index in range(1,count):
        bucket=elf_hash(names[index])%buckets
        chains[index]=heads[bucket];heads[bucket]=index
    raw=struct.pack('<'+str(2+buckets+count)+'I',buckets,count,*heads,*chains)
    change(data,hash_at,source[hash_at:hash_at+len(raw)],raw,patches,'rebuild-sysv-symbol-hash')
    dynamic,=(p for p in elf.segments if p['type']==2)
    for at in range(dynamic['offset'],dynamic['offset']+dynamic['fileSize'],8):
        tag,value=struct.unpack_from('<II',source,at)
        if tag in (5,10):
            new=pool_address if tag==5 else len(strings)
            change(data,at+4,struct.pack('<I',value),struct.pack('<I',new),patches,'dynamic-string-table')
    section_at=struct.unpack_from('<I',source,32)[0]
    section_size,section_count=struct.unpack_from('<HH',source,46)
    if section_size!=40:raise ValueError('Unexpected ELF32 section headers')
    found=0
    for index in range(section_count):
        at=section_at+index*section_size
        fields=struct.unpack_from('<10I',source,at)
        if fields[1]==3 and fields[3]==elf.tags[5] and fields[4]==strings_at:
            values=list(fields);values[3:6]=[pool_address,pool_offset,len(strings)]
            change(data,at,source[at:at+40],struct.pack('<10I',*values),patches,'dynstr-section-layout')
            found+=1
    if found!=1:raise ValueError('Ambiguous dynamic string section')
    return renamed
