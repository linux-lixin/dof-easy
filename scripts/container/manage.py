#!/usr/bin/env python3
"""One-command installation and local lifecycle management for dof-easy."""
import argparse
import hashlib
from contextlib import contextmanager
from datetime import datetime, timezone
import importlib.util
import ipaddress
import json
import os
from pathlib import Path
import re
import secrets
import shutil
import socket
import subprocess
import sys
import tarfile
import tempfile
import time
import urllib.error
import urllib.request

ROOT = Path(__file__).resolve().parents[2]
STATE = ROOT / '.dof-easy'
MODES = ('dp2-frida', 'dp2', 'frida', 'none')


def module(name, relative):
    spec = importlib.util.spec_from_file_location(name, ROOT / relative)
    value = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(value)
    return value


source = module('dof_source', 'scripts/container/resources.py')
runtime_config = module('dof_config', 'services/game/runtime/ubuntu/config.py')
image_config = module('dof_images', 'scripts/container/image_config.py')
raid_assets = module('dof_raid_assets', 'services/game/runtime/ubuntu/gateway/assets.py')
database = module('dof_database', 'scripts/container/database.py')
deployment = module('dof_deployment', 'scripts/container/deployment.py')


def catalog():
    return {row['level']: row for row in json.loads((ROOT / 'metadata/levels.lock.json').read_text())['levels']}


def run(command, **kwargs):
    return subprocess.run([str(x) for x in command], check=True, cwd=ROOT, **kwargs)


def docker_check():
    local_host = module("dof_host", "scripts/container/host.py")
    local_host.local_docker()
    result = run(['docker', 'info', '--format', '{{.OSType}}/{{.Architecture}}'], capture_output=True, text=True, timeout=30)
    if result.stdout.strip() not in ('linux/x86_64', 'linux/amd64'):
        raise ValueError('服务端部署需要 Linux amd64 Docker Engine 与 Compose v2。')
    run(['docker', 'compose', 'version'], timeout=20)


def instance_path(name):
    if not re.fullmatch('[a-z][a-z0-9-]{0,39}', name):
        raise ValueError('实例名只能使用小写英文字母、数字、连字符，并以字母开头（最多 40 字符）。')
    return STATE / 'instances' / name


def fetch(row, resources, offline=False):
    target = resources / row['id']
    resources.mkdir(parents=True, exist_ok=True)
    if not target.exists():
        if offline:
            raise ValueError('离线缓存缺少 ' + str(target))
        print('下载并校验：' + row['id'], flush=True)
        request = urllib.request.Request(row['url'], headers={'User-Agent': 'dof-easy'})
        with urllib.request.urlopen(request, timeout=90) as stream:
            source.write_verified(stream, target, row)
    source.verify(target, row)
    return target


def extract_game(archive, target, row):
    with tarfile.open(archive, 'r:gz') as stream:
        members = stream.getmembers()
        if len(members) != 1 or members[0].name != 'df_game_r' or not members[0].isfile():
            raise ValueError('等级包必须且只能包含一个普通 df_game_r 文件。')
        if members[0].size != row['game']['bytes']:
            raise ValueError('等级程序大小不匹配。')
        with stream.extractfile(members[0]) as content:
            source.write_verified(content, target, row['game'])
    target.chmod(0o700)


def prepare(resources, offline=False):
    run([sys.executable, ROOT / 'scripts/container/resources.py'])


def build(settings, database_mode='internal'):
    # Docker reuses unchanged layers; all levels share these three images.
    references = image_config.references(settings)
    for tag, context, dockerfile in (
        ('db', 'deploy/database', 'Dockerfile'),
        ('admin', 'services/admin', 'docker/Dockerfile'),
        ('game', 'services/game', 'docker/ubuntu/Dockerfile'),
    ):
        if database_mode == 'external' and tag == 'db':
            continue
        print('构建共享镜像：' + tag, flush=True)
        run(['docker', 'build', '-t', references[tag], '-f', ROOT / context / dockerfile, ROOT / context])


def acquire_images(settings, mode, archive=None, database_mode='internal'):
    references = image_config.references(settings)
    if database_mode == 'external':
        references.pop('db')
    if mode == 'build':
        prepare(None)
        if database_mode == 'external':
            build(settings, database_mode)
        else:
            build(settings)
    elif mode == 'pull':
        for reference in references.values():
            print('拉取镜像：' + reference, flush=True)
            try:
                run(['docker', 'pull', '--platform', 'linux/amd64', reference])
            except subprocess.CalledProcessError as error:
                raise ValueError('镜像拉取失败：' + reference + '；请检查地址、标签及 docker login。不会回退到源码构建。') from error
    elif mode == 'offline':
        if archive:
            run(['docker', 'load', '-i', archive])
    else:
        raise ValueError('未知镜像获取方式。')
    for reference in references.values():
        result = run(['docker', 'image', 'inspect', '--format', '{{.Os}}/{{.Architecture}}', reference], capture_output=True, text=True)
        if result.stdout.strip() != 'linux/amd64':
            raise ValueError('镜像必须为 linux/amd64：' + reference)


def write_json(path, data):
    path.write_text(json.dumps(data, ensure_ascii=False, indent=2) + '\n', encoding='utf-8')


def write_env(path, values):
    # Values are individually validated or generated; no shell is involved.
    for value in values.values():
        if any(c in str(value) for c in '\r\n\x00$#\'"'):
            raise ValueError('配置含不支持的换行、引号或 Compose 插值字符。')
    with path.open('w', encoding='utf-8', newline='\n') as stream:
        stream.write(''.join(f'{k}={v}\n' for k, v in values.items()))
    path.chmod(0o600)


def read_env(root):
    values = dict(line.split('=', 1) for line in (root / '.env').read_text(encoding='utf-8').splitlines() if line and not line.startswith('#'))
    raid_assets.profile_for(values)
    database.kind(values)
    return values


def initial_values(args, row, root):
    raid = getattr(args, 'raid_extension', None) or 'off'
    launcher = getattr(args, 'launcher', None) or 'dof'
    raid_assets.profile_for({'LAUNCHER_MODE': launcher})
    raid_assets.validate_options(args.plugins, raid)
    channels = runtime_config.channels(args.channels)
    if not 1 <= args.admin_port <= 65535:
        raise ValueError('后台端口必须在 1..65535 范围内。')
    return dict(**database.external_options(args),
                ADMIN_BIND_IP=str(ipaddress.IPv4Address(getattr(args,'admin_bind',None) or '127.0.0.1')),
                PUBLIC_IP=str(ipaddress.IPv4Address(args.ip)), BIND_IP=str(ipaddress.IPv4Address(args.bind)),
                ADMIN_PORT=str(args.admin_port), OPEN_CHANNEL=','.join(map(str, channels)),
                DNF_MAX_LEVEL=str(row['level']), DNF_GAME_SHA256=row['game']['sha256'],
                PLUGIN_MODE=args.plugins, RAID_EXTENSION=raid, LAUNCHER_MODE=launcher, RUNTIME_ROOT=(root / 'runtime').as_posix(),
                DB_ROOT_PASSWORD=secrets.token_hex(24), GAME_DB_PASSWORD=secrets.token_hex(4),
                SUPERVISOR_PASSWORD=secrets.token_hex(16), ADMIN_PASSWORD=secrets.token_hex(16),
                ADMIN_TEMP_SECRET=secrets.token_hex(16))


def ports_override(values):
    ports = []
    if raid_assets.launcher_mode(values) == 'native':
        ports.append(f"{values.get('GAME_BIND_IP', values['BIND_IP'])}:8887:8887/tcp")
    for channel in runtime_config.channels(values['OPEN_CHANNEL']):
        for base, protocol in ((30000, 'tcp'), (31000, 'udp')):
            port = base + channel
            ports.append(f"{values.get('GAME_BIND_IP', values['BIND_IP'])}:{port}:{port}/{protocol}")
    return {'services': {'game': {'ports': ports}}}


def published_ports(values):
    game = values.get('GAME_BIND_IP', values['BIND_IP'])
    items = [(game, port, protocol) for port, protocol in
             ((7001,'tcp'),(7001,'udp'),(7300,'tcp'),(7300,'udp'),(2311,'udp'),(2312,'udp'),(2313,'udp'))]
    items.append((values.get('ADMIN_BIND_IP','127.0.0.1'),int(values['ADMIN_PORT']),'tcp'))
    if raid_assets.launcher_mode(values) == 'native':
        items.append((game,8887,'tcp'))
    for channel in runtime_config.channels(values['OPEN_CHANNEL']):
        items += [(game,30000+channel,'tcp'),(game,31000+channel,'udp')]
    return items


def check_ports(values, previous=None):
    items = published_ports(values)
    for index,(address,port,protocol) in enumerate(items):
        for other in items[:index]:
            if other[1:] == (port,protocol) and (address == other[0] or '0.0.0.0' in (address,other[0])):
                raise ValueError(f'实例端口配置互相冲突：{port}/{protocol}')
    existing = set(published_ports(previous)) if previous else set()
    for address,port,protocol in items:
        if (address,port,protocol) in existing:
            continue
        # A bind-only change of this instance is checked by Docker during recreation;
        # the old container still owns its socket until then.
        if any(old[1:] == (port,protocol) for old in existing):
            continue
        try:
            with socket.socket(socket.AF_INET,socket.SOCK_STREAM if protocol == 'tcp' else socket.SOCK_DGRAM) as probe:
                probe.bind((address,port))
        except OSError as error:
            raise ValueError(f'监听地址/端口不可用：{address}:{port}/{protocol}；请先解决占用。') from error


def check_gateway_port(values, previous=None):
    if raid_assets.launcher_mode(values) != 'native':
        return
    bind = values.get('GAME_BIND_IP', values['BIND_IP'])
    if previous and raid_assets.launcher_mode(previous) == 'native' and bind == previous.get('GAME_BIND_IP', previous['BIND_IP']):
        return
    try:
        with socket.socket(socket.AF_INET, socket.SOCK_STREAM) as probe:
            probe.bind((bind, 8887))
    except OSError as exc:
        raise ValueError('Dof 网关监听地址的 TCP 8887 不可用；请先解决占用或保留端口冲突。') from exc


def apply_configuration(root, values):
    """Keep a local rollback snapshot and restore it if recreation fails."""
    files = (root / '.env', root / 'channels.json')
    previous = {p: p.read_bytes() if p.exists() else None for p in files}
    snapshot = root / 'config-backups' / (datetime.now(timezone.utc).strftime('%Y%m%dT%H%M%S') + '-' + secrets.token_hex(3))
    snapshot.mkdir(parents=True, mode=0o700)
    for path, content in previous.items():
        if content is not None:
            (snapshot / path.name).write_bytes(content)
            (snapshot / path.name).chmod(0o600)
    try:
        temporary = root / '.env.new'
        write_env(temporary, values)
        temporary.replace(root / '.env')
        write_json(root / 'channels.json', ports_override(values))
        start(root, recreate=True)
    except Exception:
        for path, content in previous.items():
            if content is None:
                path.unlink(missing_ok=True)
            else:
                path.write_bytes(content)
                path.chmod(0o600)
        try:
            start(root, recreate=True)
        except Exception:
            print('原配置已恢复，但旧服务自动恢复未通过；请检查日志。', file=sys.stderr)
        raise


def create_instance(root, args, row, archive, image_settings=None):
    pvf = Path(args.pvf).resolve()
    if pvf.is_symlink() or not pvf.is_file() or pvf.suffix.lower() != '.pvf' or pvf.stat().st_size < 1024:
        raise ValueError('请提供已下载完成的 .pvf 文件，不能使用下载临时文件。')
    values = initial_values(args, row, root)
    if image_settings is not None:
        values.update(image_config.environment(image_settings))
    root.parent.mkdir(parents=True, exist_ok=True)
    with tempfile.TemporaryDirectory(prefix='.install-', dir=root.parent) as directory:
        stage = Path(directory) / 'instance'
        data = stage / 'runtime/game/data'
        data.mkdir(parents=True)
        stage.chmod(0o700)
        extract_game(archive, data / 'df_game_r', row)
        if raid_assets.required(values):
            raid_assets.validate_game(data / 'df_game_r')
            profile = raid_assets.profile_for(values)
            supplied = getattr(args, 'dof_assets', None)
            raid_assets.install(supplied or default_dof_assets(values), raid_assets.state_for(values, data), profile)
        if raid_assets.required(values):
            converter = module('dof_server_pvf', 'services/game/runtime/ubuntu/gateway/pvf.py')
            record = converter.convert(pvf, data / 'Script.pvf')
            write_json(stage / 'pvf-migration.json', record)
        else:
            shutil.copyfile(pvf, data / 'Script.pvf')
        (data / 'Script.pvf').chmod(0o600)
        write_json(stage / 'instance.json', dict(schema=1, name=args.instance, level=row['level'],
                   game_sha256=row['game']['sha256'], pvf_sha256=source.digest(data / 'Script.pvf'),
                   pvf_bytes=(data / 'Script.pvf').stat().st_size, created_at=datetime.now(timezone.utc).isoformat()))
        write_env(stage / '.env', values)
        write_json(stage / 'channels.json', ports_override(values))
        stage.rename(root)  # Never merge an existing instance or change its credentials.
    return values


def default_dof_assets(values):
    raid_assets.profile_for(values)
    return ROOT / 'services/game/resources/native'


def validate_raid(root, values):
    enabled = values.get('RAID_EXTENSION', 'off')
    raid_assets.validate_options(values['PLUGIN_MODE'], enabled)
    if raid_assets.required(values):
        state = raid_assets.state_for(values, root / 'runtime/game/data')
        raid_assets.verify(state / 'assets', raid_assets.profile_for(values))
        raid_assets.verify_config(state)
        raid_assets.validate_game(root / 'runtime/game/data/df_game_r')


def check_instance(root, requested_level=None, pvf=None, validate_extension=True):
    record = json.loads((root / 'instance.json').read_text(encoding='utf-8'))
    if record.get('schema') != 1 or record['name'] != root.name:
        raise ValueError('实例元数据不匹配。')
    if requested_level is not None and requested_level != record['level']:
        raise ValueError('该实例已有数据，不能通过 install 改等级。请用 --instance 新名称创建独立新服。')
    values = read_env(root)
    row = catalog()[record['level']]
    if values['DNF_MAX_LEVEL'] != str(row['level']) or values['DNF_GAME_SHA256'] != row['game']['sha256']:
        raise ValueError('实例等级配置与资源清单不一致。')
    source.verify(root / 'runtime/game/data/df_game_r', row['game'])
    if validate_extension:
        validate_raid(root, values)
    if pvf and source.digest(Path(pvf)) != source.digest(root / 'runtime/game/data/Script.pvf'):
        raise ValueError('已存在不同 PVF；install 不覆盖现有服的资源。')
    return values


def compose(root, *arguments, **kwargs):
    # Shell variables take priority over --env-file in Compose. Keep the
    # instance's image choices authoritative, including old local instances.
    env = dict(kwargs.pop('env', os.environ))
    values = read_env(root)
    env.update(values)
    env.update({key: image_config.instance_reference(values, role) for role, key in image_config.KEYS.items()})
    env['RAID_EXTENSION'] = values.get('RAID_EXTENSION', 'off')
    env['LAUNCHER_MODE'] = values.get('LAUNCHER_MODE', 'dof')
    env['PLUGIN_MODE'] = values['PLUGIN_MODE']
    for name in list(env):
        if name.startswith('QINGKONG_'):
            del env[name]
    write_json(root / 'channels.json', ports_override(values))
    base = ['docker','compose','--project-directory',ROOT,'-p','dof-'+root.name,
            '--env-file',root/'.env','-f',ROOT/'compose.yaml','-f',root/'channels.json']
    if database.kind(values) == 'external' or database.web_management_enabled(values):
        result = run(base+['config','--format','json'],env=env,capture_output=True,text=True)
        target = root/'deployment.json'
        previous = json.loads(target.read_text()) if target.exists() else None
        model = deployment.configure(json.loads(result.stdout),values,root,database.DATABASES,previous)
        database.private_json(target,deployment.literal_model(model))
        return run(['docker','compose','--project-directory',ROOT,'-p','dof-'+root.name,
                    '-f',target,*arguments],env=env,**kwargs)
    return run(base+list(arguments),env=env,**kwargs)


def database_query(root, sql):
    values = read_env(root)
    if database.kind(values) == 'external':
        command=['run','--rm','--no-deps','-T','db-tools','query']
    else:
        command=['exec','-T','db','sh','-c',
                 'MYSQL_PWD="$MYSQL_ROOT_PASSWORD" mysql -uroot --default-character-set=utf8 -N -B -r']
    result = compose(root,*command,input=sql,text=True,capture_output=True)
    return result.stdout.strip()


def prepare_database(root,args):
    values=read_env(root)
    if database.kind(values) != 'external':
        return
    marker=root/'external-database.json'
    if marker.exists() and json.loads(marker.read_text()).get('state') == 'ready':
        saved=json.loads(marker.read_text())
        if saved['databaseUuid'] != values.get('DOF_DATABASE_UUID') or saved['host'] != values['DOF_DB_HOST'] or saved['port'] != values['DOF_DB_PORT'] or saved['grantHost'] != values['DOF_DB_ALLOW_HOST']:
            raise ValueError('外置数据库绑定改变')
        return
    if not args.mysql_config:
        raise ValueError('外置首次初始化未完成，需要原 --mysql-config；不会重新生成密码')
    query=database.host_query(args.mysql_bin,args.mysql_config)
    identity=database.initialize_external(root,values,query)
    values['DOF_DATABASE_UUID']=identity
    temporary=root/'.env.new'
    write_env(temporary,values)
    temporary.replace(root/'.env')


def retire_legacy_helpers(root):
    """Release old published ports only for this Compose project's helpers.

    No volume/network removal and no host-wide orphan/prune operation. A
    failed cutover leaves the instance files and database intact for retry.
    """
    project = 'dof-' + root.name
    found = run(['docker', 'container', 'ls', '--all', '--filter',
                 'label=com.docker.compose.project=' + project, '--format', '{{.ID}}'],
                capture_output=True, text=True).stdout.split()
    if not found:
        return
    containers = json.loads(run(['docker', 'container', 'inspect', *found],
                                capture_output=True, text=True).stdout)
    owned = []
    for item in containers:
        labels = item.get('Config', {}).get('Labels') or {}
        if labels.get('com.docker.compose.project') != project:
            raise ValueError('容器项目身份不匹配，拒绝拓扑切换')
        owned.append((item, labels.get('com.docker.compose.service')))
    helpers = [item for item, service in owned if service in ('netguard', 'ingress', 'db-check')]
    if not helpers:
        return
    saved = root / 'config-backups' / ('topology-' + datetime.now(timezone.utc).strftime('%Y%m%dT%H%M%S') + '-' + secrets.token_hex(3))
    saved.mkdir(parents=True, mode=0o700)
    for name in ('.env', 'channels.json', 'deployment.json'):
        path = root / name
        if path.is_file():
            target = saved / name
            target.write_bytes(path.read_bytes())
            target.chmod(0o600)
    database.private_json(saved / 'containers.json', [dict(id=item['Id'], service=service, image=item['Image'])
                                                     for item, service in owned])
    # Resolve and validate the new graph before interrupting the old services.
    compose(root, 'config', '--quiet')
    print('切换为直接端口映射：短暂停止当前实例 game/admin，移除旧辅助容器；恢复配置：' + str(saved))
    writers = [item['Id'] for item, service in owned if service in ('game', 'admin') and item['State']['Running']]
    if writers:
        run(['docker', 'container', 'stop', '--time', '30', *writers])
    run(['docker', 'container', 'rm', '--force', *[item['Id'] for item in helpers]])


def start(root, recreate=False):
    values = check_instance(root)
    args = ['up', '-d', '--wait', '--wait-timeout', '900', '--no-build', '--pull', 'never']
    if recreate:
        args.append('--force-recreate')
    if database.kind(values) == 'external':
        marker=root/'external-database.json'
        saved=json.loads(marker.read_text()) if marker.exists() else {}
        if saved.get('state') != 'ready' or any(saved.get(k) != values.get(v) for k,v in [('databaseUuid','DOF_DATABASE_UUID'),('host','DOF_DB_HOST'),('port','DOF_DB_PORT'),('grantHost','DOF_DB_ALLOW_HOST')]):
            raise ValueError('外置数据库初始化未完成或绑定不符，拒绝启动')
    retire_legacy_helpers(root)
    write_json(root / 'channels.json', ports_override(values))
    if values.get('ALLOWED_CLIENT_CIDRS'):
        print('旧实例玩家来源规则不再由容器执行；当前访问范围由云安全组控制。')
    if database.web_management_enabled(values):
        # The web adapter is provisioned only after the actual native game is ready.
        compose(root, *args, 'game')
        identity=database.prepare_web(root,values,lambda sql:database_query(root,sql))
        values['DOF_DATABASE_UUID']=identity
        values['DOF_ADMIN_ENABLED']='true'
        write_env(root/'.env',values)
    compose(root, *args)
    if raid_assets.launcher_mode(values) == 'native':
        # Older cached images ignore LAUNCHER_MODE. Do not accept their generic
        # health status as a successful launcher migration.
        compose(root, 'exec', '-T', 'game', 'python3', '-c',
                "import sys,os; sys.path[:0]=['/opt/dnf/ubuntu','/opt/dof-easy/runtime']; "
                "from gateway import assets as raid_assets; import health; "
                "assert raid_assets.launcher_mode(os.environ)=='native'; "
                "raid_assets.preflight(); health.check()")
    # Probe the actual Java API through the directly published admin port.
    admin_bind = values.get('ADMIN_BIND_IP', values['BIND_IP'])
    host = '127.0.0.1' if admin_bind == '0.0.0.0' else admin_bind
    url = f"http://{host}:{values['ADMIN_PORT']}/api/v1/admin/assist/config-text"
    deadline = time.monotonic() + 180
    while True:
        try:
            with urllib.request.urlopen(url, timeout=5) as response:
                payload = response.read(16384)
                if response.status == 200 and '角色等级上限'.encode('utf-8') in payload:
                    break
        except (OSError, urllib.error.URLError):
            pass
        if time.monotonic() >= deadline:
            raise ValueError('后台 API 未就绪；运行 dof.sh logs 查看日志，实例数据已保留。')
        time.sleep(3)
    client = root / 'client'
    client.mkdir(exist_ok=True)
    # Container-owned key files are 0600 on Linux. Export only the public key
    # through Docker, without weakening runtime permissions for a non-root host.
    with (client / 'publickey.pem').open('wb') as stream:
        compose(root, 'exec', '-T', 'game', 'cat', '/data/publickey.pem', stdout=stream)
    admin_host = '127.0.0.1' if ipaddress.ip_address(admin_bind).is_loopback else values['PUBLIC_IP']
    if raid_assets.launcher_mode(values) == 'dof':
        (client / 'login.ini.example').write_text(
            '[Login]\nBaseUrl=http://' + admin_host + ':' + values['ADMIN_PORT']
            + '\nWindowTitle=地下城与勇士 ' + values['DNF_MAX_LEVEL'] + '\nGameVersion=1.0.0\n', encoding='utf-8')
    else:
        write_json(client / 'native-endpoints.json', {
            'launcherMode': 'native', 'serverAddress': values['PUBLIC_IP'], 'gatewayPort': 8887,
            'channels': runtime_config.channels(values['OPEN_CHANNEL']),
            'adminUrl': f"http://{admin_host}:{values['ADMIN_PORT']}",
            'note': '本文件仅提供服务端端口信息，不是客户端配置容器。'})
    print(f"部署完成：{values['DNF_MAX_LEVEL']} 级，插件 {values['PLUGIN_MODE']}，团本扩展 {values.get('RAID_EXTENSION', 'off')}，频道 {values['OPEN_CHANNEL']}")
    print(f"后台：http://{admin_host}:{values['ADMIN_PORT']}；账号：admin")
    print('登录方式：' + raid_assets.launcher_mode(values))
    if ipaddress.ip_address(admin_bind).is_loopback:
        print('后台仅本机访问；通过宝塔 Nginx HTTPS 反向代理或 SSH 转发访问。')
    print('密码保存在 ' + str(root / '.env') + ' 的 ADMIN_PASSWORD；公钥在 ' + str(client))


@contextmanager
def locked(root):
    root.parent.mkdir(parents=True, exist_ok=True)
    path = root.parent / ('.' + root.name + '.lock')
    try:
        with path.open('x') as stream:
            stream.write(str(os.getpid()))
    except FileExistsError:
        raise ValueError('该实例有正在执行或中断的操作；检查进程后再处理锁文件：' + str(path))
    try:
        yield
    finally:
        path.unlink()


def backup(root):
    values = read_env(root)
    output = STATE / 'backups' / (root.name + '-' + datetime.now().strftime('%Y%m%d-%H%M%S') + '.tar.gz')
    output.parent.mkdir(parents=True, exist_ok=True)
    output.parent.chmod(0o700)
    running = compose(root, 'ps', '--status', 'running', '--services', capture_output=True, text=True).stdout.split()
    external = database.kind(values) == 'external'
    if not external and 'db' not in running:
        raise ValueError('请先启动数据库再备份。')
    if external:
        compose(root,'run','--rm','--no-deps','-T','db-tools','check')
    stopped = [s for s in ('admin', 'game') if s in running]
    with tempfile.TemporaryDirectory(prefix='.backup-', dir=STATE) as directory:
        sql = Path(directory) / 'databases.sql'
        try:
            if stopped:
                compose(root, 'stop', *stopped)
            with sql.open('wb') as stream:
                if external:
                    compose(root,'run','--rm','--no-deps','-T','db-tools','dump',stdout=stream)
                else:
                    compose(root, 'exec', '-T', 'db', 'sh', '-c',
                        'MYSQL_PWD="$MYSQL_ROOT_PASSWORD" mysqldump -uroot --all-databases --single-transaction --routines --events --ignore-table=mysql.innodb_index_stats --ignore-table=mysql.innodb_table_stats', stdout=stream)
            if sql.stat().st_size < 100:
                raise ValueError('数据库备份为空；不发布归档')
            partial = output.with_suffix('.partial')
            with partial.open('xb'):
                pass
            partial.chmod(0o600)
            # Read root-owned runtime keys/plugins in an isolated, read-only
            # helper; the host user owns the output archive, even without sudo.
            with partial.open('wb') as stream:
                run(['docker', 'run', '--rm', '--network', 'none', '--read-only', '--cap-drop', 'ALL', '--cap-add', 'DAC_OVERRIDE',
                     '--security-opt', 'no-new-privileges', '--entrypoint', '/bin/tar',
                     '--mount', 'type=bind,source=' + str(root) + ',target=/payload/instance,readonly',
                     '--mount', 'type=bind,source=' + directory + ',target=/backup-input,readonly',
                     image_config.instance_reference(values, 'game'), '-czf', '-', '-C', '/backup-input', 'databases.sql',
                     '-C', '/payload', 'instance'], stdout=stream)
            partial.chmod(0o600)
            partial.rename(output)
            with output.open('rb') as stream:
                digest = hashlib.file_digest(stream, 'sha256').hexdigest()
            checksum = output.with_name(output.name + '.sha256')
            checksum.write_text(digest + '  ' + output.name + '\n')
            checksum.chmod(0o600)
        finally:
            if stopped:
                compose(root, 'start', *stopped)
    print('本地备份完成（含数据库、密钥、密码、PVF、插件和日志）：' + str(output))


def update_admin_address(root, address):
    config=json.loads(database_query(root,'SELECT CONFIG_JSON FROM dnf_service.ASSIST_CONFIG WHERE ID=1;'))
    config['服务器地址']=address
    encoded=json.dumps(config,ensure_ascii=False).encode().hex()
    database_query(root,'UPDATE dnf_service.ASSIST_CONFIG SET CONFIG_JSON=CONVERT(0x'+encoded+' USING utf8),UPDATE_TIME=NOW() WHERE ID=1;')


def wizard():
    print('dof-easy 通用容器管理\n1 拉取镜像安装 / 继续安装\n2 启动\n3 停止\n4 重建并重启容器\n5 状态\n6 日志\n7 本地备份\n8 等级列表\n9 修改插件\n10 配置镜像仓库\n0 退出')
    choices = {'1': 'install', '2': 'start', '3': 'stop', '4': 'restart', '5': 'status', '6': 'logs', '7': 'backup', '8': 'versions', '9': 'config', '10': 'images'}
    choice = input('选择 [1]：').strip() or '1'
    if choice == '0':
        return None
    command = choices.get(choice)
    if not command:
        raise ValueError('无效菜单选项。')
    if command == 'versions':
        return [command]
    if command == 'images':
        current = image_config.load(STATE)
        return [command, '--registry', input('镜像仓库/命名空间：').strip() or current.get('registry', ''),
                '--tag', input('镜像标签 [' + current.get('tag', 'latest') + ']：').strip() or current.get('tag', 'latest')]
    name = input('实例名 [main]：').strip() or 'main'
    arguments = [command, '--instance', name]
    if command == 'install' and not instance_path(name).exists():
        print('可选等级：' + ', '.join(map(str, catalog())))
        arguments += ['--level', input('等级 [86]：').strip() or '86',
                      '--ip', input('客户端可访问的服务器 IPv4：').strip(),
                      '--pvf', input('配套 PVF 文件路径：').strip().strip('"'),
                      '--plugins', input('插件 dp2-frida / dp2 / frida / none [dp2-frida]：').strip() or 'dp2-frida',
                      '--raid-extension', input('团本扩展 on / off [off]：').strip() or 'off']
        database_mode=input('数据库 internal 内置 / external 宝塔 [internal]：').strip() or 'internal'
        arguments += ['--database',database_mode]
        if database_mode == 'external':
            arguments += ['--db-host',input('容器可达的宿主机私网 IPv4：').strip(),
                          '--db-port',input('MySQL 端口 [3306]：').strip() or '3306',
                          '--db-allow-host',input('专用 Docker 网段/点分掩码：').strip(),
                          '--mysql-config',input('本机管理员 MySQL 配置文件路径：').strip()]
        if arguments[arguments.index('--raid-extension')+1] == 'on':
            supplied = input('团本扩展已导入资源目录 [默认本地缓存]：').strip().strip('\"')
            if supplied:
                arguments += ['--dof-assets', supplied]
    elif command == 'config':
        selected = input('插件 dp2-frida / dp2 / frida / none [保留]：').strip()
        if selected:
            arguments += ['--plugins', selected]
        raid = input('团本扩展 on / off [保留]：').strip()
        if raid:
            arguments += ['--raid-extension', raid]
        if raid == 'on':
            supplied = input('团本扩展已导入资源目录 [默认本地缓存]：').strip().strip('\"')
            if supplied:
                arguments += ['--dof-assets', supplied]
    if command == 'config' or (command == 'install' and not instance_path(name).exists()):
        launcher = input('登录器 dof / native [' + ('保留' if command == 'config' else 'dof') + ']：').strip()
        if launcher:
            arguments += ['--launcher', launcher]
        if launcher == 'native':
            supplied = input('原生服务端产物目录 [默认本地缓存]：').strip().strip('"')
            if supplied:
                arguments += ['--dof-assets', supplied]
    if command == 'install':
        root = instance_path(name)
        existing = read_env(root) if root.exists() else {}
        defaults = image_config.load(STATE)
        if not existing.get('IMAGE_REGISTRY', defaults.get('registry')):
            arguments += ['--registry', input('镜像仓库/命名空间（如 ghcr.io/你的用户名）：').strip(),
                          '--tag', input('镜像标签 [latest]：').strip() or 'latest']
    return arguments


def main(argv=None):
    if sys.platform != 'linux':
        raise ValueError('服务端部署仅支持 Linux 主机，请在 Linux 上运行 bash dof.sh。')
    parser = argparse.ArgumentParser(prog='dof.sh', description='通用一键容器端：默认拉取自有仓库镜像；源码构建必须显式选择。')
    sub = parser.add_subparsers(dest='command', required=True)
    sub.add_parser('versions', help='列出原安装器支持的等级')
    resources = sub.add_parser('resources', help='下载全部等级包并校验仓库内公共资源')
    resources.add_argument('--resources', type=Path, default=STATE / 'resources')
    for command in ('images', 'build'):
        p = sub.add_parser(command, help='配置/查看默认镜像仓库' if command == 'images' else '显式构建所选数据库模式的镜像，不上传')
        p.add_argument('--registry', help='仓库地址及命名空间，例如 ghcr.io/用户名')
        p.add_argument('--tag', help='镜像使用的版本标签')
        if command == 'build':
            p.add_argument('--database', choices=('internal','external'), default='internal', help='external 不构建数据库镜像')
    for command in ('install', 'start', 'stop', 'restart', 'status', 'logs', 'backup', 'config'):
        p = sub.add_parser(command)
        p.add_argument('--instance', default='main')
        if command in ('install', 'config'):
            p.add_argument('--launcher', choices=('dof', 'native'), help='玩家登录方式；旧实例默认 dof')
            p.add_argument('--dof-assets', type=Path, help='已派生并校验的正式服务端资源目录')
            p.add_argument('--raid-extension', choices=('on', 'off'), help='团本扩展，默认关闭；启用需要 DP2')
        if command == 'install':
            p.add_argument('--database',choices=('internal','external'),help='数据库模式；新实例默认 internal，已有实例不可切换')
            p.add_argument('--db-host',help='外置 MySQL 的宿主机私网 IPv4，不是容器内 127.0.0.1')
            p.add_argument('--db-port',type=int,help='外置 MySQL 端口，默认 3306')
            p.add_argument('--db-allow-host',help='专用 Docker 网段/点分掩码，如 172.29.86.0/255.255.255.0')
            p.add_argument('--mysql-config',type=Path,help='首次初始化使用的本机 MySQL 管理员客户端配置，权限 0600')
            p.add_argument('--mysql-bin',help='本机 mysql 客户端，默认探测系统及宝塔安装位置')
            p.add_argument('--admin-bind',help='网页监听 IPv4，默认 127.0.0.1，供 Nginx 反代')
            p.add_argument('--level', type=int, choices=list(catalog()))
            p.add_argument('--ip')
            p.add_argument('--pvf', type=Path)
            p.add_argument('--plugins', choices=MODES, default='dp2-frida')
            p.add_argument('--channels', default='15,52')
            p.add_argument('--bind', default='0.0.0.0')
            p.add_argument('--admin-port', type=int, default=18080, metavar='PORT')
            p.add_argument('--resources', type=Path, default=STATE / 'resources')
            mode = p.add_mutually_exclusive_group()
            mode.add_argument('--offline', action='store_true', help='仅使用本地资源及镜像，不拉取、不构建')
            mode.add_argument('--build', action='store_true', help='显式从源码构建，不拉取发布镜像')
            p.add_argument('--registry', help='覆盖默认镜像仓库及命名空间')
            p.add_argument('--tag', help='覆盖默认镜像版本标签')
            p.add_argument('--images', type=Path, help='离线安装时自动导入 docker save 镜像包')
        if command == 'config':
            p.add_argument('--ip')
            p.add_argument('--plugins', choices=MODES)
            p.add_argument('--game-bind', help='游戏监听 IPv4；与后台监听独立')
            p.add_argument('--admin-bind', help='后台监听 IPv4；联调建议 127.0.0.1')
            p.add_argument('--channels', help='替换频道列表，如 15,52；保留其他配置')
        if command == 'logs':
            p.add_argument('--follow', action='store_true')
            p.add_argument('--service', choices=('db', 'game', 'admin'))
    argv = list(sys.argv[1:] if argv is None else argv)
    if not argv:
        if not sys.stdin.isatty():
            parser.print_help()
            return 0
        argv = wizard()
        if argv is None:
            return 0
    args = parser.parse_args(argv)
    if args.command == 'versions':
        print('默认 86；可选：' + ', '.join(map(str, catalog())))
        return 0
    if args.command == 'images':
        settings = image_config.load(STATE)
        if args.registry is not None or args.tag is not None:
            settings = image_config.resolve(args, settings)
            image_config.save(STATE, settings)
        if not settings:
            print('尚未配置镜像仓库：dof.sh images --registry 仓库地址/命名空间 --tag 版本')
        else:
            print('默认镜像（新安装使用；已有实例保留自己的版本）：')
            for reference in image_config.references(settings).values():
                print(reference)
        return 0
    if args.command == 'build':
        settings = image_config.resolve(args, image_config.load(STATE), allow_local=True)
        docker_check()
        acquire_images(settings, 'build', database_mode=args.database)
        print('所选数据库模式的镜像已构建；未上传。使用 docker push 发布到你自己的仓库。')
        return 0
    if args.command == 'resources':
        prepare(args.resources)
        for row in catalog().values():
            fetch(row, args.resources)
        print('全部等级包已归档；公共构建输入随代码提供：' + str(args.resources.resolve()))
        return 0
    root = instance_path(args.instance)
    STATE.mkdir(parents=True, exist_ok=True)
    STATE.chmod(0o700)
    docker_check()
    with locked(root):
        if args.command == 'install':
            if args.images and (not args.offline or not args.images.is_file()):
                raise ValueError('--images 需要配合 --offline，并提供已完成的镜像归档文件。')
            if root.exists():
                values = check_instance(root, args.level, args.pvf)
                if args.database and args.database != database.kind(values):
                    raise ValueError('已有实例不能切换数据库模式；请创建新的空实例')
                for argument,key in [('db_host','DOF_DB_HOST'),('db_port','DOF_DB_PORT'),('db_allow_host','DOF_DB_ALLOW_HOST')]:
                    requested=getattr(args,argument,None)
                    if requested is not None and str(requested) != values.get(key):
                        raise ValueError('已有实例不能改数据库连接身份')
                if args.launcher is not None and args.launcher != values.get('LAUNCHER_MODE', 'dof'):
                    raise ValueError('已有实例切换登录方式请使用 config --launcher。')
                if args.dof_assets:
                    raise ValueError('已有实例导入原生资源请使用 config --dof-assets。')
                if args.raid_extension is not None and args.raid_extension != values.get('RAID_EXTENSION', 'off'):
                    raise ValueError('已有实例请使用 config --raid-extension 修改团本扩展开关。')
                if args.ip and args.ip != values['PUBLIC_IP']:
                    raise ValueError('已有实例改 IP 请使用 config --ip；install 保留现有配置。')
            else:
                values = None
                args.level = args.level or 86
                if not args.ip or not args.pvf:
                    raise ValueError('新服需要 --ip 和 --pvf；不带参数运行可使用交互菜单。')
                row = catalog()[args.level]
                initial = initial_values(args, row, root)  # Validate all inputs before downloading.
                check_ports(initial)
                check_gateway_port(initial)
                if raid_assets.required(initial):
                    raid_assets.verify(args.dof_assets or default_dof_assets(initial))
                elif args.dof_assets:
                    raise ValueError('--dof-assets 需要原生登录或团本扩展')
                if args.pvf.is_symlink() or not args.pvf.is_file() or args.pvf.suffix.lower() != '.pvf' or args.pvf.stat().st_size < 1024:
                    raise ValueError('找不到完整的 .pvf 文件：' + str(args.pvf))
            settings = image_config.resolve(args, image_config.load(STATE), values, allow_local=args.build or args.offline)
            selected_mode=database.kind(values or initial)
            image_args=(settings,'offline' if args.offline else 'build' if args.build else 'pull',args.images)
            if selected_mode == 'external':
                acquire_images(*image_args,database_mode='external')
            else:
                acquire_images(*image_args)
            if values is None:
                archive = fetch(row, args.resources, args.offline)
                create_instance(root, args, row, archive, settings)
            else:
                # Change the instance only after all four requested images are
                # available. A failed pull preserves its previous configuration.
                values.update(image_config.environment(settings))
                temporary = root / '.env.new'
                write_env(temporary, values)
                temporary.replace(root / '.env')
            prepare_database(root,args)
            start(root)
        else:
            values = read_env(root)
            if args.command == 'start':
                start(root)
            elif args.command == 'restart':
                start(root, recreate=True)
            elif args.command == 'stop':
                compose(root, 'stop')
            elif args.command == 'status':
                print('登录方式：' + raid_assets.launcher_mode(values))
                print(f"实例 {root.name}：{values['DNF_MAX_LEVEL']} 级，{values['PLUGIN_MODE']}，团本扩展 {values.get('RAID_EXTENSION', 'off')}，频道 {values['OPEN_CHANNEL']}")
                print('数据库模式：'+database.kind(values))
                compose(root, 'ps', '-a')
            elif args.command == 'logs':
                if args.service == 'db' and database.kind(values) == 'external':
                    raise ValueError('外置 MySQL 日志请在宿主机查看；项目不管理该服务')
                compose(root, 'logs', '--tail', '150', *(['-f'] if args.follow else []), *([args.service] if args.service else []))
            elif args.command == 'backup':
                backup(root)
            elif args.command == 'config':
                check_instance(root, validate_extension=False)
                previous = dict(values)
                if args.launcher is not None:
                    if database.kind(values) == 'external' and args.launcher != values.get('LAUNCHER_MODE','dof'):
                        raise ValueError('外置数据库实例不自动切换登录协议；请使用新实例')
                    values['LAUNCHER_MODE'] = args.launcher
                if args.ip:
                    values['PUBLIC_IP'] = str(ipaddress.IPv4Address(args.ip))
                if args.plugins:
                    values['PLUGIN_MODE'] = args.plugins
                if args.game_bind is not None:
                    values['GAME_BIND_IP'] = str(ipaddress.IPv4Address(args.game_bind))
                if args.admin_bind is not None:
                    values['ADMIN_BIND_IP'] = str(ipaddress.IPv4Address(args.admin_bind))
                if args.channels is not None:
                    values['OPEN_CHANNEL'] = ','.join(map(str, runtime_config.channels(args.channels)))
                if args.raid_extension is not None:
                    values['RAID_EXTENSION'] = args.raid_extension
                raid_assets.validate_options(values['PLUGIN_MODE'], values.get('RAID_EXTENSION', 'off'))
                check_ports(values, previous)
                check_gateway_port(values, previous)
                profile = raid_assets.profile_for(values)
                if args.dof_assets and not raid_assets.required(values):
                    raise ValueError('--dof-assets 需要原生登录或团本扩展')
                target = raid_assets.state_for(values, root / 'runtime/game/data')
                if raid_assets.required(values):
                    raid_assets.validate_game(root / 'runtime/game/data/df_game_r')
                    supplied = args.dof_assets
                    if supplied or not target.exists():
                        was_missing = not target.exists()
                        raid_assets.install(supplied or default_dof_assets(values), target, profile)
                        # On a profile transition preserve the active instance's gameplay settings.
                        old = raid_assets.state_for(previous, root / 'runtime/game/data')
                        if was_missing and old != target and (old / 'config').is_dir():
                            raid_assets.verify_config(old)
                            for config in (old / 'config').glob('*.ini'):
                                shutil.copyfile(raid_assets.regular(config, old), target / 'config' / config.name)
                validate_raid(root, values)
                apply_configuration(root, values)
                if args.ip:
                    update_admin_address(root, values['PUBLIC_IP'])
    return 0


if __name__ == '__main__':
    try:
        raise SystemExit(main())
    except (ValueError, OSError, KeyError, subprocess.SubprocessError, tarfile.TarError) as error:
        print('操作未完成：' + str(error), file=sys.stderr)
        print('已有数据和日志已保留；修正原因后可重试同一命令。', file=sys.stderr)
        raise SystemExit(1)
