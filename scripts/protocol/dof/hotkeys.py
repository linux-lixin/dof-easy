"""Pinned Dof 2.8 Hotkeys codec. No field normalization or server writes."""
from .config import native_rc4

KEY = bytes.fromhex('b7a2c8f6cac7b7f1b0a2c8f8b8c16767646167616568656168616561686261686162')
MAX_BYTES = 1024 * 1024
MAX_FIELDS = 1024


def decode(wire: str) -> tuple[bytes, ...]:
    if not wire or len(wire) > 2 * MAX_BYTES or len(wire) % 2 or any(c not in '0123456789ABCDEF' for c in wire):
        raise ValueError('Incompatible native Hotkeys encoding')
    parts = tuple(native_rc4(bytes.fromhex(wire), KEY).split(b'*'))
    _validate(parts)
    return parts


def _validate(parts):
    if not 66 <= len(parts) <= MAX_FIELDS or sum(map(len, parts)) + len(parts) - 1 > MAX_BYTES:
        raise ValueError('Incompatible native Hotkeys length')
    if any(b'*' in p for p in parts):
        raise ValueError('Native Hotkeys delimiter collision')


def encode(parts: tuple[bytes, ...]) -> str:
    _validate(parts)
    return native_rc4(b'*'.join(parts), KEY).hex().upper()


def replace_text(parts: tuple[bytes, ...], edits: dict[int, str]) -> tuple[bytes, ...]:
    _validate(parts)
    result = list(parts)
    for index, value in edits.items():
        if not isinstance(index, int) or not 0 <= index < len(parts) or '*' in value or '\0' in value:
            raise ValueError('Invalid native Hotkeys field edit')
        result[index] = value.encode('gbk', errors='strict')
    _validate(result)
    return tuple(result)
