"""Hash-checked managed script updates; never take ownership of unmarked volumes."""
import hashlib
import json
from pathlib import Path
import shutil
import sys

MARKER='.dnf-ubuntu.json'

def digest(path):
    return hashlib.sha256(path.read_bytes()).hexdigest()

def synchronize(data, source, history):
    data, source=Path(data),Path(source)
    files={p.relative_to(source).as_posix():digest(p) for p in sorted(source.rglob('*')) if p.is_file()}
    marker=data/MARKER
    if data.is_symlink() or marker.is_symlink():
        raise ValueError('managed runtime must not be a symlink')
    if not marker.exists():
        allowed={'publickey.pem','privatekey.pem','Script.pvf'}
        unexpected=[p.name for p in data.iterdir() if p.name not in allowed] if data.exists() else []
        if unexpected:
            raise ValueError('unmarked runtime directory; use a new Ubuntu directory: '+', '.join(unexpected))
        previous={}
    else:
        previous=json.loads(marker.read_text())
        if previous.get('platform') != 'ubuntu24' or previous.get('schema') != 1:
            raise ValueError('unsupported runtime marker')
        previous=previous['files']
    conflicts=[]
    for name, expected in files.items():
        target=data/name
        if any(p.is_symlink() for p in [target,*target.parents] if p != data.parent):
            conflicts.append(name); continue
        if target.exists():
            actual=digest(target) if target.is_file() else None
            known={expected} | {entry.get(name) for entry in history.values()}
            if actual not in known or (previous.get(name) != actual and actual != expected):
                conflicts.append(name)
    if conflicts:
        raise ValueError('managed script conflicts (nothing overwritten): '+', '.join(conflicts))
    # All conflicts checked before any persistent write. Atomic per-file replacement.
    data.mkdir(parents=True,exist_ok=True)
    for name in files:
        target=data/name; target.parent.mkdir(parents=True,exist_ok=True)
        if not target.exists() or digest(target) != files[name]:
            temp=target.with_name(target.name+'.ubuntu-new')
            shutil.copyfile(source/name,temp); temp.chmod(0o755); temp.replace(target)
    temp=marker.with_suffix('.tmp')
    temp.write_text(json.dumps(dict(schema=1,platform='ubuntu24',files=files),indent=2)+'\n')
    temp.chmod(0o600); temp.replace(marker)

if __name__ == '__main__':
    try:
        synchronize('/data','/opt/dnf/ubuntu/managed',json.loads(Path('/opt/dnf/ubuntu/releases.json').read_text()))
    except (ValueError,OSError) as exc:
        print('Ubuntu runtime: '+str(exc),file=sys.stderr); sys.exit(1)
