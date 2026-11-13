"""Instance-owned MySQL provisioning. Never reset an existing unbound database.

External bootstrap uses an explicit private host mysql defaults file, not argv
passwords. Runtime and backup use the scoped game account, never host root.
"""
import hashlib
import ipaddress
import json
import os
from pathlib import Path
import re
import subprocess
import shutil
import tarfile
import uuid

ROOT = Path(__file__).resolve().parents[2]
SEED = ROOT / 'deploy/database/seed.sql'
COMPAT = ROOT / 'deploy/database/compat-tables.sql'
GAME_DATABASES = tuple(re.findall(r'CREATE DATABASE /\*!32312 IF NOT EXISTS\*/ `([a-z0-9_]+)`', SEED.read_text()))
CHANNEL_SEED = ROOT / 'services/game/assets/home/template/init/init_sql.tgz'
DATABASES = GAME_DATABASES + ('d_channel_cain', 'qk')


def required_databases(values):
    kind(values)
    if values.get('LAUNCHER_MODE') != 'qingkong' or values.get('PLUGIN_MODE', 'none') != 'none' or values.get('RAID_EXTENSION', 'off') != 'off':
        raise ValueError('Only original qingkong with plugins none and raid off is supported')
    return DATABASES


def backup_databases(instance, values):
    selected = required_databases(values)
    marker = instance / 'external-database.json'
    if marker.is_symlink():
        raise ValueError('数据库备份绑定不能是符号链接')
    if marker.exists():
        saved = json.loads(marker.read_text())
        if saved.get('databaseUuid') != values.get('DOF_DATABASE_UUID') or saved.get('instanceId') != 'dof-'+instance.name:
            raise ValueError('数据库备份实例身份不匹配')
    return selected


def kind(values):
    if values.get('DOF_DATABASE_KIND') != 'external':
        raise ValueError('qingkong requires external MySQL; existing internal databases are not migrated')
    return 'external'


def external_options(args):
    mode = getattr(args, 'database', None) or 'external'
    if mode != 'external':
        raise ValueError('qingkong requires --database external')
    host = str(ipaddress.IPv4Address(args.db_host or ''))
    address = ipaddress.IPv4Address(host)
    if address.is_loopback or address.is_unspecified or address.is_multicast or not address.is_private:
        raise ValueError('--db-host 必须是容器可达的宿主机私网 IPv4，不能为 127.0.0.1')
    port = 3306 if args.db_port is None else args.db_port
    if not 1 <= port <= 65535:
        raise ValueError('Invalid MySQL port')
    allow = args.db_allow_host or ''
    # A fixed Docker subnet in MySQL IPv4/netmask notation, never a global %.
    if '/' not in allow:
        raise ValueError('--db-allow-host 需要 Docker 网段/点分子网掩码')
    net = ipaddress.IPv4Network(allow, strict=True)
    private_ranges = [ipaddress.IPv4Network(value) for value in ('10.0.0.0/8','172.16.0.0/12','192.168.0.0/16')]
    if not any(net.subnet_of(value) for value in private_ranges) or net.prefixlen < 16 or net.prefixlen > 28:
        raise ValueError('Docker 数据库网段必须是 /16 至 /28 的私网')
    if address in net:
        raise ValueError('--db-host 不能属于新建的容器网段；使用宿主机既有私网或 docker0 地址')
    if not args.mysql_config:
        raise ValueError('首次外置初始化需要 --mysql-config 私有客户端配置文件')
    return dict(DOF_DATABASE_KIND=mode, DOF_DB_HOST=host, DOF_DB_PORT=str(port),
                DOF_DB_ALLOW_HOST=str(net.network_address)+'/'+str(net.netmask),
                DOF_DB_SUBNET=str(net), DOF_DATABASE_MODE='existing')


def private_json(path, value):
    path.parent.mkdir(parents=True, exist_ok=True, mode=0o700)
    temporary = path.with_suffix('.new')
    fd = os.open(temporary, os.O_WRONLY | os.O_CREAT | os.O_TRUNC, 0o600)
    with os.fdopen(fd, 'w') as stream:
        json.dump(value, stream, indent=2); stream.write('\n')
    temporary.replace(path)


def host_query(binary, defaults, timeout=900):
    binary = Path(binary or shutil.which('mysql') or next((p for p in ('/usr/local/mysql/bin/mysql', '/www/server/mysql/bin/mysql') if Path(p).is_file()), '/usr/bin/mysql'))
    defaults = Path(defaults)
    if not binary.is_absolute() or not binary.is_file():
        raise ValueError('请指定宿主机 MySQL 客户端的完整路径')
    if defaults.is_symlink() or not defaults.is_file() or defaults.stat().st_mode & 0o077:
        raise ValueError('MySQL 配置必须是权限 0600 的普通私有文件')
    def query(sql):
        result = subprocess.run([str(binary), '--defaults-file='+str(defaults.resolve()),
            '--batch', '--raw', '--skip-column-names', '--default-character-set=utf8'],
            input=sql, text=True, capture_output=True, timeout=timeout)
        if result.returncode:
            code = re.search(r'ERROR (\d+)', result.stderr)
            raise ValueError('MySQL 操作失败，错误号 '+(code[1] if code else 'unknown')+'；未输出 SQL 或凭据')
        return result.stdout.strip()
    return query


def identity(query):
    version, identity = query('SELECT VERSION(),@@server_uuid;').split('\t')
    if not re.fullmatch(r'5\.6\.[0-9]+(?:[-.].*)?', version):
        raise ValueError('外置数据库仅支持已适配的 MySQL 5.6.x')
    if str(uuid.UUID(identity)) != identity.lower():
        raise ValueError('Invalid MySQL server UUID')
    return identity.lower()


def account_sql(user, password, host):
    if not re.fullmatch(r'[a-z][a-z0-9_]{0,15}', user) or not re.fullmatch(r'[a-zA-Z0-9]+', password):
        raise ValueError('Invalid generated database account')
    if not re.fullmatch(r'[0-9./%]+', host):
        raise ValueError('Invalid grant host')
    return "'"+user+"'@'"+host+"'"


def initialize_external(instance, values, query):
    """Only a fresh schema set can be provisioned; retries never replay a dump."""
    required_databases(values)
    db_uuid = identity(query)
    marker = instance / 'external-database.json'
    expected = dict(databaseUuid=db_uuid,host=values['DOF_DB_HOST'],port=values['DOF_DB_PORT'],
                    grantHost=values['DOF_DB_ALLOW_HOST'],instanceId='dof-'+instance.name)
    if marker.exists():
        saved = json.loads(marker.read_text())
        if any(saved.get(k) != v for k,v in expected.items()):
            raise ValueError('外置数据库身份改变；拒绝继续初始化')
        if saved.get('state') != 'ready':
            raise ValueError('上次初始化中断；保留现场，禁止自动重放种子。请按恢复文档处理')
        return db_uuid
    existing = set(query('SHOW DATABASES;').splitlines())
    if existing.intersection(required_databases(values)):
        raise ValueError('外置数据库已有项目库；新安装拒绝覆盖或接管')
    host = values['DOF_DB_ALLOW_HOST']
    if query("SELECT COUNT(*) FROM mysql.user WHERE User='game';") != '0':
        raise ValueError('数据库已有 game 账号；拒绝接管')
    if not any('GRANT ALL PRIVILEGES ON *.*' in line and 'WITH GRANT OPTION' in line
               for line in query('SHOW GRANTS FOR CURRENT_USER();').splitlines()):
        raise ValueError('首次初始化需要有建库及授权权限的本机数据库管理员')
    # Record ownership before the first write. Failed imports must be reviewed,
    # never mistaken for a clean DB or automatically resumed over player data.
    locked = {item['path']: item['sha256'] for item in json.loads((ROOT/'metadata/build-inputs.lock.json').read_text())['files']}
    for source in (SEED, COMPAT, CHANNEL_SEED):
        if hashlib.sha256(source.read_bytes()).hexdigest() != locked.get(source.relative_to(ROOT).as_posix()):
            raise ValueError('Database seed hash mismatch')
    private_json(marker, dict(expected,state='initializing',databases=required_databases(values)))
    # An exclusive CREATE arbitrates concurrent first installs before any dump
    # can run. No IF NOT EXISTS: only the installer that claims it may seed.
    query('CREATE DATABASE d_channel CHARACTER SET utf8;')
    query(SEED.read_text())
    query(COMPAT.read_text())
    with tarfile.open(CHANNEL_SEED) as archive:
        channel = archive.extractfile('d_channel.sql').read().decode('utf-8')
    query('CREATE DATABASE d_channel_cain CHARACTER SET utf8; USE d_channel_cain;' + channel)
    query('CREATE DATABASE qk CHARACTER SET utf8;')
    game = account_sql('game',values['GAME_DB_PASSWORD'],host)
    query('CREATE USER '+game+" IDENTIFIED BY '"+values['GAME_DB_PASSWORD']+"';")
    for schema in DATABASES:
        query('GRANT ALL PRIVILEGES ON `'+schema.replace('_', r'\_')+'`.* TO '+game+';')
    import importlib.util
    spec = importlib.util.spec_from_file_location('qingkong_database', ROOT / 'scripts/container/qingkong_database.py')
    original = importlib.util.module_from_spec(spec); spec.loader.exec_module(original)
    original.initialize(instance / 'runtime/game/data/qingkong', query)
    # Update encrypted game connection credentials in the game container at
    # startup; no legacy init/grant script is allowed on this external DB.
    private_json(marker,dict(expected,state='ready',databases=required_databases(values)))
    return db_uuid
