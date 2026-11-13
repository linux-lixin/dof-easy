"""Original Qingkong facade and shared pinned game ABI validation."""
import hashlib
import json
import os
from pathlib import Path
import struct

BASE = Path(__file__).resolve().parent
GAME_ABI = BASE / 'game-abi.json'

def qingkong_assets():
    import importlib.util
    path = BASE.parent / 'qingkong/assets.py'
    if not path.is_file():
        path = Path('/opt/dnf/ubuntu/qingkong/assets.py')
    spec = importlib.util.spec_from_file_location('qingkong_assets', path)
    module = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(module)
    return module


def launcher_mode(values):
    mode = values.get('LAUNCHER_MODE', 'qingkong')
    if mode != 'qingkong':
        raise ValueError('Only original qingkong is supported; existing modes are not migrated')
    return mode


def validate_options(mode, enabled):
    return qingkong_assets().validate_options(mode, enabled)


def profile_for(values):
    launcher_mode(values)
    validate_options(values.get('PLUGIN_MODE', 'none'), values.get('RAID_EXTENSION', 'off'))
    return 'qingkong'


def required(values):
    profile_for(values)
    return True


def state_for(values, data=Path('/data')):
    profile_for(values)
    return Path(data) / 'qingkong'


def manifest(profile='qingkong'):
    return qingkong_assets().manifest(profile)


def regular(path, root):
    return qingkong_assets().regular(path, root)


def verify(root, profile='qingkong'):
    return qingkong_assets().verify(root, profile)


def verify_config(root, profile='qingkong'):
    if profile != 'qingkong':
        raise ValueError('Expected original Qingkong profile')
    return qingkong_assets().verify_config(root)


def install(source, target, profile='qingkong'):
    return qingkong_assets().install(source, target, profile)


def validate_game(path):
    """Reject incompatible fixed-address calls before loading the adapters.

    Map virtual addresses through executable PT_LOAD segments. Matching source
    bytes is a preflight requirement, not proof of runtime or player behavior.
    """
    raw = Path(path).read_bytes()
    if len(raw) < 52 or raw[:7] != b'\x7fELF\x01\x01\x01':
        raise ValueError('Unsupported game ELF architecture')
    kind, machine, version = struct.unpack_from('<HHI', raw, 16)
    phoff = struct.unpack_from('<I', raw, 28)[0]
    ehsize, phsize, phcount = struct.unpack_from('<HHH', raw, 40)
    if (kind, machine, version, ehsize, phsize) != (2, 3, 1, 52, 32):
        raise ValueError('Unsupported game ELF header')
    if not phcount or phoff < ehsize or phoff + phcount * phsize > len(raw):
        raise ValueError('Invalid game ELF program headers')
    segments = []
    for index in range(phcount):
        kind, offset, address, _, size, memory_size, flags, _ = struct.unpack_from(
            '<8I', raw, phoff + index * phsize)
        if kind == 1:
            if size > memory_size or offset + size > len(raw):
                raise ValueError('Invalid game ELF load segment')
            segments.append((offset, address, size, flags))
    contract = json.loads(GAME_ABI.read_text())
    if (contract.get('schema') != 1 or
            contract.get('architecture') != 'elf32-i386-exec' or
            not contract.get('functions')):
        raise ValueError('Invalid game ABI contract')
    for row in contract['functions']:
        address, size = row['address'], row['bytes']
        matches = [(offset + address - start, flags)
                   for offset, start, length, flags in segments
                   if size > 0 and start <= address and address + size <= start + length]
        if len(matches) != 1 or not matches[0][1] & 1:
            raise ValueError('Game ABI function is not uniquely executable: ' + row['symbols'][0])
        offset = matches[0][0]
        if hashlib.sha256(raw[offset:offset + size]).hexdigest() != row['sha256']:
            raise ValueError('Unsupported game ABI function: ' + row['symbols'][0])


def preflight():
    profile_for(os.environ)
    return qingkong_assets().preflight()


if __name__ == '__main__':
    preflight()
