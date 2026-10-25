"""Offline PVF tag migration preserving file indices and every other resource.

Only the two server plugin tag identifiers change. Client resource paths are not
rewritten by this server-only importer. CRCs use the native file-number seeds.
"""
import array
import hashlib
from pathlib import Path
import os
import shutil
import struct
import sys
import tempfile
import zlib

TAGS = {b'[qk rarity color]': b'[dof rarity color]', b'[qk randombox]': b'[dof randombox]'}


def crypt(raw, crc, encode=False):
    if len(raw) % 4: raise ValueError('Unaligned PVF encrypted block')
    words = array.array('I'); words.frombytes(raw)
    if sys.byteorder != 'little': words.byteswap()
    key = 0x81a79011 ^ crc
    for i, word in enumerate(words):
        if encode:
            words[i] = (((word << 6) | (word >> 26)) & 0xffffffff) ^ key
        else:
            word ^= key; words[i] = ((word >> 6) | (word << 26)) & 0xffffffff
    if sys.byteorder != 'little': words.byteswap()
    return words.tobytes()


def strings(raw):
    if len(raw) < 12: raise ValueError('Truncated PVF string table')
    count, = struct.unpack_from('<I', raw)
    if count > (len(raw)-8)//4: raise ValueError('Invalid PVF string count')
    offsets = struct.unpack_from('<' + str(count + 1) + 'I', raw, 4)
    if offsets[0] != 4*(count+1) or offsets[-1]+4 != len(raw):
        raise ValueError('Unexpected PVF string table bounds')
    if any(a > b for a, b in zip(offsets, offsets[1:])):
        raise ValueError('Unordered PVF string offsets')
    return [raw[4+a:4+b] for a,b in zip(offsets, offsets[1:])]


def pack_strings(values):
    offsets = [4*(len(values)+1)]
    for value in values: offsets.append(offsets[-1]+len(value))
    return struct.pack('<I',len(values)) + struct.pack('<'+str(len(offsets))+'I',*offsets) + b''.join(values)


def sha(path):
    with Path(path).open('rb') as stream: return hashlib.file_digest(stream,'sha256').hexdigest()


def convert(source, destination):
    source, destination = Path(source).absolute(), Path(destination).absolute()
    if source.is_symlink() or not source.is_file() or destination.exists() or destination.is_symlink():
        raise ValueError('PVF requires a regular source and a new destination')
    source_hash = sha(source)
    source_size = source.stat().st_size
    with source.open('rb') as stream:
        raw = stream.read(4)
        if len(raw)!=4: raise ValueError('Missing PVF header')
        guid_length, = struct.unpack('<I',raw)
        if not 1 <= guid_length <= 256: raise ValueError('Unsupported PVF GUID')
        stream.seek(guid_length,1)
        header_at = stream.tell(); header = stream.read(16)
        if len(header)!=16: raise ValueError('Truncated PVF header')
        version, size, crc, count = struct.unpack('<4I',header)
        if not 0 < size <= 200_000_000 or not 0 < count <= 2_000_000:
            raise ValueError('PVF bounds exceeded')
        tree_at = stream.tell(); encrypted = stream.read(size)
        if len(encrypted)!=size: raise ValueError('Truncated PVF tree')
        tree = bytearray(crypt(encrypted,crc)); base = stream.tell()
        if zlib.crc32(tree,count)!=crc: raise ValueError('PVF tree CRC mismatch')
        position, entries, old_resources = 0, [], 0
        for _ in range(count):
            if position+8>len(tree): raise ValueError('Truncated PVF tree entry')
            number, length = struct.unpack_from('<II',tree,position); position+=8
            if length>len(tree)-position-12: raise ValueError('Invalid PVF path length')
            name=bytes(tree[position:position+length]);position+=length
            length, check, offset=struct.unpack_from('<III',tree,position)
            if base+offset+((length+3)&~3)>source_size: raise ValueError('PVF file exceeds input')
            if name.lower().replace(b'\\',b'/')==b'stringtable.bin':entries.append((number,length,check,offset,position))
            if b'qingkong' in name.lower():old_resources+=1
            position+=12
        if len(entries)!=1:raise ValueError('PVF requires one unambiguous string table')
        number,length,check,offset,entry_at=entries[0]
        stream.seek(base+offset); padded=crypt(stream.read((length+3)&~3),check)
        if zlib.crc32(padded,number)!=check:raise ValueError('PVF string table CRC mismatch')
        values=strings(padded[:length]);changed=sum(value in TAGS for value in values)
        converted=[TAGS.get(value,value) for value in values]
    destination.parent.mkdir(parents=True,exist_ok=True)
    temporary=None
    try:
        with tempfile.NamedTemporaryFile(prefix='.dof-pvf-',dir=destination.parent,delete=False) as stream:
            temporary=Path(stream.name);os.fchmod(stream.fileno(),0o600)
        shutil.copyfile(source,temporary)
        if changed:
            new=pack_strings(converted);pad=new+bytes((-len(new))%4);check=zlib.crc32(pad,number)
            with temporary.open('r+b') as stream:
                stream.seek(0,2);padding=(-(stream.tell()-base))%4;stream.write(bytes(padding))
                offset=stream.tell()-base;stream.write(crypt(pad,check,True))
                struct.pack_into('<III',tree,entry_at,len(new),check,offset)
                crc=zlib.crc32(tree,count);stream.seek(header_at);stream.write(struct.pack('<4I',version,size,crc,count))
                stream.seek(tree_at);stream.write(crypt(tree,crc,True));stream.flush();os.fsync(stream.fileno())
        if sha(source)!=source_hash:raise ValueError('PVF source changed during conversion')
        if destination.exists() or destination.is_symlink():raise ValueError('PVF destination appeared')
        result=dict(sourceSha256=source_hash,derivedSha256=sha(temporary),files=count,strings=len(values),
                    changedServerTags=changed,clientResourcePathsNotMigrated=old_resources)
        temporary.rename(destination)
        return result
    finally:
        if temporary is not None:temporary.unlink(missing_ok=True)
