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
DATABASES = GAME_DATABASES + ('d_channel_cain', 'dof_native', 'dnf_service')


def kind(values):
    result = values.get('DOF_DATABASE_KIND', 'internal')
    if result not in ('internal', 'external'):
        raise ValueError('Invalid database mode')
    return result


def external_options(args):
    mode = getattr(args, 'database', None) or 'internal'
    if mode == 'internal':
        if any(getattr(args, name, None) is not None for name in
               ('db_host', 'db_port', 'db_allow_host', 'mysql_config', 'mysql_bin')):
            raise ValueError('外置 MySQL 参数需要 --database external')
        return {'DOF_DATABASE_KIND': mode}
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


def native_schema(state, query):
    # Import only verified CREATE definitions and known seeds, never embedded DROP.
    import sys
    gateway = ROOT / 'services/game/runtime/ubuntu'
    sys.path.insert(0, str(gateway))
    try:
        from gateway.schema import initialize
        initialize(state / 'assets/runtime/gateway/schema.sql', query)
    finally:
        sys.path.remove(str(gateway))


def web_management_enabled(values):
    return (values.get('LAUNCHER_MODE', 'dof') == 'native'
            or values.get('RAID_EXTENSION', 'off') == 'on'
            or values.get('DOF_ADMIN_ENABLED') == 'true')


def bind_plugin_files(instance, name, db_uuid):
    root = instance / 'runtime/game/data/dof-easy/config'
    if not root.is_dir() or root.resolve() != root.absolute():
        raise ValueError('插件配置目录缺失或通过符号链接重定向')
    marker = root / '.admin-instance.json'
    expected = dict(instanceId=name, databaseUuid=db_uuid, environment='isolated')
    if marker.is_symlink():
        raise ValueError('插件配置实例标记不能是符号链接')
    if marker.exists():
        if json.loads(marker.read_text()) != expected:
            raise ValueError('插件配置目录已绑定其他实例，拒绝覆盖')
        return
    fd = os.open(marker, os.O_WRONLY | os.O_CREAT | os.O_EXCL | os.O_NOFOLLOW, 0o600)
    with os.fdopen(fd, 'w') as stream:
        json.dump(expected, stream); stream.write('\n'); stream.flush(); os.fsync(stream.fileno())


def prepare_web(instance, values, query):
    """Shared native web preparation for internal and external MySQL."""
    db_uuid = identity(query)
    native = web_management_enabled(values)
    name = 'dof-' + instance.name
    marker = instance / 'database-web.json'
    if marker.exists():
        saved = json.loads(marker.read_text())
        expected = dict(databaseUuid=db_uuid, instanceId=name, native=native, state='ready')
        # Enabling plugin management is additive. An existing generic instance
        # keeps its accounts, passwords and settings; only the binding is added.
        upgrade = native and saved == dict(expected, native=False)
        if saved != expected and not upgrade:
            raise ValueError('网页数据库身份或模式改变；拒绝覆盖绑定')
        if native and not upgrade:
            binding = query("SELECT CONCAT(database_uuid,'|',adapter_version) FROM dnf_service.DOF_INSTANCE_BINDING WHERE instance_id='"+name+"';")
            if binding != db_uuid+'|dof-native-1':
                raise ValueError('原生网页数据库绑定不符')
            bind_plugin_files(instance, name, db_uuid)
        if not upgrade:
            return db_uuid
    # Explicitly additive schema extraction, preserving previous UI and accounts.
    init = (ROOT / 'services/admin/database/init.sql').read_text()
    definitions = re.findall(r'CREATE TABLE `([A-Za-z_]+)` (\(.*?\) ENGINE=.*?);', init, re.S)
    if len(definitions) != 7:
        raise ValueError('Unexpected service schema')
    columns = set(query("SELECT COLUMN_NAME FROM information_schema.COLUMNS WHERE TABLE_SCHEMA='d_taiwan' AND TABLE_NAME='accounts';").splitlines())
    for column, ddl in [('admin','INT(10)'),('parent_uid','INT(10)'),('isRobot','TINYINT(3) DEFAULT 0')]:
        if column not in columns:
            query('ALTER TABLE d_taiwan.accounts ADD COLUMN '+column+' '+ddl+';')
    query('CREATE DATABASE IF NOT EXISTS dnf_service CHARACTER SET utf8;')
    for table, body in definitions:
        query('CREATE TABLE IF NOT EXISTS dnf_service.`'+table+'` '+body+';')
    seed = re.search(r"INSERT INTO `ASSIST_CONFIG` .*? VALUES \(1, '(.*?)', '", init)
    if not seed:
        raise ValueError('Missing default web configuration')
    config_value = json.loads(seed[1].replace('\\"', '"').replace('${DNF_PUBLIC_IP}', values['PUBLIC_IP']).replace('${DNF_MAX_LEVEL}', values['DNF_MAX_LEVEL']))
    config = json.dumps(config_value, ensure_ascii=False).encode().hex()
    query('INSERT INTO dnf_service.ASSIST_CONFIG (ID,CONFIG_JSON,UPDATE_TIME) SELECT 1,CONVERT(0x'+config+' USING utf8),NOW() FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM dnf_service.ASSIST_CONFIG WHERE ID=1);')
    query("INSERT INTO dnf_service.CLIENT_LAUNCHER_CONFIG (ID,VERSION,DOWNLOAD_URL,MD5,REMARK,UPDATE_TIME,TITLE) SELECT 1,'1.0.0','','','',NOW(),CONVERT(0x"+'地下城与勇士'.encode().hex()+" USING utf8) FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM dnf_service.CLIENT_LAUNCHER_CONFIG WHERE ID=1);")
    admin_hash = hashlib.md5(values['ADMIN_PASSWORD'].encode()).hexdigest()
    existing = query("SELECT CONCAT(password,'|',COALESCE(admin,0)) FROM d_taiwan.accounts WHERE accountname='admin';")
    if existing and existing != admin_hash+'|1':
        raise ValueError('已有 admin 账号与实例凭据不符；不会重置密码')
    if not existing:
        query("INSERT INTO d_taiwan.accounts (accountname,password,qq,VIP,admin,parent_uid,isRobot) VALUES ('admin','"+admin_hash+"','','',1,0,0);")
    query('INSERT INTO dnf_service.ADMIN_TEMP_PASSWORD (id,PASSWORD) SELECT 1,CONVERT(0x'+values['ADMIN_TEMP_SECRET'].encode().hex()+' USING utf8) FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM dnf_service.ADMIN_TEMP_PASSWORD WHERE id=1);')
    if native:
        native_schema(instance / 'runtime/game/data/dof-easy', query)
        migration = (ROOT/'services/admin/database/dof-admin.sql').read_text()
        query(migration)
        other = query("SELECT instance_id FROM dnf_service.DOF_INSTANCE_BINDING WHERE instance_id <> '"+name+"';")
        if other:
            raise ValueError('数据库已绑定其他原生实例')
        expected = 'isolated|'+db_uuid+'|dof-native-1'
        existing = query("SELECT CONCAT(environment,'|',database_uuid,'|',adapter_version) FROM dnf_service.DOF_INSTANCE_BINDING WHERE instance_id='"+name+"';")
        if existing and existing != expected:
            raise ValueError('原生网页绑定冲突')
        if not existing:
            query("INSERT INTO dnf_service.DOF_INSTANCE_BINDING VALUES ('"+name+"','isolated','"+db_uuid+"','dof-native-1');")
    data = instance/'runtime/admin/data'; data.mkdir(parents=True, exist_ok=True, mode=0o700)
    if native:
        private_json(data/'dof-isolated.json', dict(instanceId=name,databaseUuid=db_uuid,environment='isolated'))
        bind_plugin_files(instance, name, db_uuid)
    (data/'.db_initialized').touch(mode=0o600)
    private_json(marker, dict(databaseUuid=db_uuid,instanceId=name,native=native,state='ready'))
    return db_uuid


def initialize_external(instance, values, query):
    """Only a fresh schema set can be provisioned; retries never replay a dump."""
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
    if existing.intersection(DATABASES):
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
    private_json(marker, dict(expected,state='initializing',databases=DATABASES))
    # An exclusive CREATE arbitrates concurrent first installs before any dump
    # can run. No IF NOT EXISTS: only the installer that claims it may seed.
    query('CREATE DATABASE d_channel CHARACTER SET utf8;')
    query(SEED.read_text())
    query(COMPAT.read_text())
    with tarfile.open(CHANNEL_SEED) as archive:
        channel = archive.extractfile('d_channel.sql').read().decode('utf-8')
    query('CREATE DATABASE d_channel_cain CHARACTER SET utf8; USE d_channel_cain;' + channel)
    for schema in ('dof_native','dnf_service'):
        query('CREATE DATABASE '+schema+' CHARACTER SET utf8;')
    game = account_sql('game',values['GAME_DB_PASSWORD'],host)
    query('CREATE USER '+game+" IDENTIFIED BY '"+values['GAME_DB_PASSWORD']+"';")
    for schema in DATABASES:
        query('GRANT ALL PRIVILEGES ON `'+schema.replace('_', r'\_')+'`.* TO '+game+';')
    db_uuid = prepare_web(instance,values,query)
    # Update encrypted game connection credentials in the game container at
    # startup; no legacy init/grant script is allowed on this external DB.
    private_json(marker,dict(expected,state='ready',databases=DATABASES))
    return db_uuid
