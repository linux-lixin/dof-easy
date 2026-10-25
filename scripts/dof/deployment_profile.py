"""Load explicit private isolation targets; no live infrastructure defaults in Git."""
import json
from pathlib import Path
import re
import uuid

FIELDS = {'environment','root','databaseContainer','databaseDirectory','databaseUuid',
          'webContainer','gameContainer','network','gamePvf','instanceId',
          'databaseUser','adminAccount','databaseClientHost','publicKeySha256','protectedContainers'}
MAP = {'ROOT':'root','DB':'databaseContainer','DB_DIRECTORY':'databaseDirectory','UUID':'databaseUuid',
       'WEB':'webContainer','GAME':'gameContainer','NETWORK':'network','GAME_PVF':'gamePvf',
       'INSTANCE':'instanceId','DB_USER':'databaseUser','ADMIN_ACCOUNT':'adminAccount',
       'DB_HOST':'databaseClientHost','PUBLIC_PIN':'publicKeySha256','PROTECTED':'protectedContainers'}
PATHS = {'ROOT','DB_DIRECTORY','GAME_PVF'}

def load(path):
    path=Path(path)
    if path.is_symlink() or not path.is_file() or path.stat().st_size>16384 or path.stat().st_mode & 0o077:
        raise ValueError('Deployment profile must be a private regular file (0600)')
    p=json.loads(path.read_text())
    if not isinstance(p,dict) or set(p)!=FIELDS or p['environment']!='isolated':raise ValueError('Explicit isolated deployment profile required')
    for key in ('root','databaseDirectory','gamePvf'):
        value=p[key]
        if not isinstance(value,str) or '\x00'in value or not value.startswith('/') or '..'in Path(value).parts or len(Path(value).parts)<3 or ','in value:
            raise ValueError('Invalid deployment path: '+key)
    if p['root']==p['databaseDirectory']:raise ValueError('Web and database directories must be separate')
    for key in ('databaseContainer','webContainer','gameContainer','network','instanceId'):
        if not isinstance(p[key],str)or not re.fullmatch(r'[a-z][a-z0-9_-]{2,63}',p[key]):raise ValueError('Invalid target identity: '+key)
    if len({p[k]for k in ('databaseContainer','webContainer','gameContainer')})!=3:raise ValueError('Container targets must differ')
    if not isinstance(p['databaseUuid'],str):raise ValueError('Invalid database UUID')
    value=uuid.UUID(p['databaseUuid'])
    if value.int==0 or str(value)!=p['databaseUuid']:raise ValueError('Supply the verified database UUID')
    for key in ('databaseUser','adminAccount'):
        if not isinstance(p[key],str)or not re.fullmatch(r'[a-z][a-z0-9_]{2,31}',p[key]):raise ValueError('Invalid local account name')
    host=p['databaseClientHost']
    if not isinstance(host,str)or not re.fullmatch(r'[0-9.]+%?',host)or not (host.startswith(('10.','192.168.','127.'))or any(host.startswith('172.'+str(n)+'.')for n in range(16,32))):
        raise ValueError('Database grant host must be a private network pattern')
    if not isinstance(p['publicKeySha256'],str)or not re.fullmatch('[0-9a-f]{64}',p['publicKeySha256'])or p['publicKeySha256']=='0'*64:raise ValueError('Supply the existing public-key SHA-256')
    protected=p['protectedContainers']
    if not isinstance(protected,list)or not protected or any(not isinstance(n,str)or not re.fullmatch(r'[a-z][a-z0-9_-]{2,63}',n)for n in protected)or len(set(protected))!=len(protected):raise ValueError('Invalid protected container list')
    if p['webContainer']in protected or not {p['gameContainer'],p['databaseContainer']}.issubset(protected):raise ValueError('Protect game/database; only web may be replaced')
    return p

def bind(namespace, profile):
    for name,key in MAP.items():
        if name in namespace:
            value=profile[key]
            namespace[name]=Path(value)if name in PATHS else tuple(value)if name=='PROTECTED'else value
