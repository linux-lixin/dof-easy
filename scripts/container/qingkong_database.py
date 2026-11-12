"""Bootstrap the original qk schema from the immutable supplied gateway."""
import hashlib
from pathlib import Path
import re

GATEWAY_PIN='67553a3b45de80ebea5f767f6e3a51d577d2cbb6c6647bd4c97037cb50a41f9b'
SCHEMA_PIN='440604c1e7e786012abab3e69efac077a48da9d7ceae1ab43394a8eed4bc504b'

def statements(raw):
    if hashlib.sha256(raw).hexdigest()!=GATEWAY_PIN:
        raise ValueError('Unrecognized original Qingkong gateway')
    at=raw.index(b'CREATE TABLE `billing`')
    block=raw[raw.rfind(b'\0',0,at)+1:raw.index(b'\0',at)]
    if hashlib.sha256(block).hexdigest()!=SCHEMA_PIN:
        raise ValueError('Unrecognized original Qingkong schema')
    selected=[sql.strip() for sql in block.decode().split(';')
              if re.match(r'^\s*(CREATE TABLE|INSERT INTO)\s',sql)]
    if sum(s.startswith('CREATE TABLE') for s in selected)!=20:
        raise ValueError('Unexpected Qingkong base schema')
    return selected

def initialize(state,query):
    path=Path(state)/'assets/QK'
    if path.is_symlink():
        raise ValueError('Qingkong gateway cannot be a symlink')
    sql=statements(path.read_bytes())
    query('USE qk;\n'+';\n'.join(sql)+';')
