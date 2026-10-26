"""Chinese menu for the existing container manager; no separate deployment path."""
import argparse
from contextlib import contextmanager
import getpass
import ipaddress
import json
import os
import re
from pathlib import Path
import subprocess
import sys
import tempfile
import shutil
import time
import hashlib
from datetime import datetime, timezone

from scripts.container import host

from scripts.container import manage as manager


def ask(label, default=''):
    value = input(label + (' [' + str(default) + ']' if default != '' else '') + '：').strip()
    return value or str(default)


def selected_level(value):
    try:
        level = int(value)
    except ValueError:
        raise ValueError('请输入菜单中列出的等级数字。') from None
    if level not in manager.catalog():
        raise ValueError('不支持该等级，请从等级列表选择。')
    return level


def inspect_network():
    """Read local routes and Docker IPAM; never create networks during discovery."""
    def read(args):
        return json.loads(subprocess.check_output(args, text=True, timeout=20))
    routes = read(['ip', '-j', '-4', 'route', 'show', 'table', 'all'])
    addresses = read(['ip', '-j', '-4', 'addr', 'show'])
    ids = subprocess.check_output(['docker', 'network', 'ls', '-q'], text=True, timeout=20).split()
    networks = read(['docker', 'network', 'inspect', *ids]) if ids else []
    used = [ipaddress.ip_network(row['dst'], strict=False) for row in routes
            if row.get('dst') not in (None, 'default', '0.0.0.0/0')]
    for network in networks:
        for row in network.get('IPAM', {}).get('Config') or []:
            if row.get('Subnet'):
                used.append(ipaddress.ip_network(row['Subnet']))
    hosts = []
    for interface in sorted(addresses, key=lambda item: item.get('ifname') != 'docker0'):
        for row in interface.get('addr_info', []):
            address = ipaddress.ip_address(row['local'])
            if address.version == 4 and address.is_private and not address.is_loopback and not address.is_link_local:
                hosts.append(str(address))
    return hosts, used


def free_subnet(used):
    for prefix in ('172.29', '10.253', '192.168'):
        for number in range(86, 255):
            network = ipaddress.ip_network(f'{prefix}.{number}.0/24')
            if not any(item.version == 4 and network.overlaps(item) for item in used):
                return str(network.network_address) + '/' + str(network.netmask)
    raise ValueError('未找到可用的容器网段；请按完整部署文档指定独立网段。')


def quoted_option(value):
    if any(char in value for char in '\x00\r\n'):
        raise ValueError('MySQL 配置不能包含换行或空字符。')
    return '"' + value.replace('\\', '\\\\').replace('"', '\\"').replace('\t', '\\t') + '"'


@contextmanager
def mysql_connection(password, socket):
    # Passwords never enter argv, exported environment, project files or logs.
    with tempfile.TemporaryDirectory(prefix='dof-mysql-') as temporary:
        path = Path(temporary) / 'client.cnf'
        with path.open('x', encoding='utf-8') as stream:
            os.chmod(path, 0o600)
            stream.write('[client]\nuser=root\npassword=' + quoted_option(password)
                         + '\nsocket=' + quoted_option(socket) + '\n')
        yield path


def check_external(binary, defaults, host, port):
    query = manager.database.host_query(binary, defaults)
    manager.database.identity(query)
    if set(query('SHOW DATABASES;').splitlines()).intersection(manager.database.DATABASES):
        raise ValueError('MySQL 已有项目数据库，不能用新装覆盖。已有实例请使用启动菜单。')
    if query("SELECT COUNT(*) FROM mysql.user WHERE User='game';") != '0':
        raise ValueError('MySQL 已有 game 账号，不能自动接管。')
    bind, actual_port, packet, sql_mode = query(
        "SELECT @@global.bind_address,@@global.port,@@global.max_allowed_packet,CONCAT('mode:',@@global.sql_mode);").split('\t')
    if bind not in ('0.0.0.0', '*', '::', host) or int(actual_port) != port:
        raise ValueError('请先让 MySQL 监听所选宿主机地址和端口，再重新安装。')
    if int(packet) < 64 * 1024**2 or set(sql_mode.removeprefix('mode:').split(',')) & {'STRICT_TRANS_TABLES','STRICT_ALL_TABLES','NO_ZERO_DATE','NO_ZERO_IN_DATE'}:
        raise ValueError('请在 MySQL 配置中设置 max_allowed_packet>=64M，sql_mode 使用 NO_ENGINE_SUBSTITUTION，应用后重试。')
    return bind


def assets_directory(value, instance):
    source = Path(value).expanduser().resolve(strict=True)
    if not (source / 'manifest.json').is_file():
        raise ValueError('请选择正式原生资源目录；原始材料只允许通过 scripts/dof/import_assets.py 显式导入')
    manager.raid_assets.verify(source)
    return source


def choose(label, options, default):
    while True:
        value = ask(label, default)
        if value in options:
            return value
        print('请输入：' + ' / '.join(options))


def integer(label, default, low, high):
    while True:
        try:
            value = int(ask(label, default))
            if low <= value <= high:
                return value
        except ValueError:
            pass
        print(f'请输入 {low}～{high} 的整数。')


def confirm(label='确认执行以上操作？'):
    return choose(label + ' y 执行 / n 取消', ('y','n'), 'n') == 'y'


def plugin_selection(previous=None):
    previous = previous or {'PLUGIN_MODE':'dp2-frida', 'RAID_EXTENSION':'on'}
    choices = {'1':'dp2-frida','2':'dp2','3':'frida','4':'none'}
    if previous['PLUGIN_MODE'] not in manager.MODES:
        raise ValueError('已有插件配置无效，请先检查实例配置。')
    default = next(key for key,value in choices.items() if value == previous['PLUGIN_MODE'])
    selected = choose('普通插件：1 DP2＋Frida / 2 仅 DP2 / 3 仅 Frida / 4 关闭', (*choices,*manager.MODES), default)
    plugins = choices.get(selected,selected)
    if plugins not in ('dp2', 'dp2-frida'):
        print('此组合不包含 DP2，团本扩展将关闭；原生登录必需组件保留。')
        return plugins, 'off'
    choice = choose('团本扩展：1 开启 / 2 关闭', ('1','2','on','off'), '1' if previous.get('RAID_EXTENSION','off') == 'on' else '2')
    return plugins, {'1':'on','2':'off'}.get(choice,choice)


def mysql_binary():
    for name in (shutil.which('mysql'), '/usr/local/mysql/bin/mysql', '/www/server/mysql/bin/mysql'):
        if name and Path(name).is_file():
            return str(Path(name).resolve())
    return '/usr/bin/mysql'


def mysql_socket():
    for name in ('/run/mysqld/mysqld.sock','/var/lib/mysql/mysql.sock','/tmp/mysql.sock'):
        if Path(name).exists():
            return name
    return '/run/mysqld/mysqld.sock'


def ports(values):
    tcp = [7001, 7300]
    udp = [7001, 7300, 2311, 2312, 2313]
    if values.get('LAUNCHER_MODE') == 'native':
        tcp.append(8887)
    for channel in manager.runtime_config.channels(values['OPEN_CHANNEL']):
        tcp.append(30000 + channel); udp.append(31000 + channel)
    return tcp, udp


def web_instructions(instance):
    root = manager.instance_path(instance)
    values = manager.read_env(root)
    port = int(values['ADMIN_PORT'])
    target = root / 'nginx-location.conf.example'
    template = (manager.ROOT / 'deploy/baota/nginx-location.conf.example').read_text()
    if not target.exists():
        with target.open('x') as stream:
            os.chmod(target, 0o600)
            stream.write(template.replace('127.0.0.1:18080', '127.0.0.1:' + str(port)))
    address = values.get('ADMIN_BIND_IP','127.0.0.1')
    if address == '0.0.0.0':
        address = values['PUBLIC_IP']
    print(f'网页：http://{address}:{port}（公网使用建议配置 HTTPS）')
    print('普通 Nginx / 宝塔可参考：' + str(target) + '；未修改已有站点。')
    print('后台账号：admin；密码查看 ' + str(root / '.env') + ' 中的 ADMIN_PASSWORD。')
    tcp, udp = ports(values)
    print('游戏云安全组 TCP：' + ','.join(map(str,tcp)) + '；UDP：' + ','.join(map(str,udp)))
    print(f'网页 TCP {port} 按实际访问方式放行；数据库无需公网开放。')
    print('服务就绪不等于进游戏通过；下一步使用配套客户端验证登录、选角和进入游戏。')


@contextmanager
def stage(label):
    directory = manager.STATE / 'operations'
    directory.mkdir(parents=True, exist_ok=True, mode=0o700)
    directory.chmod(0o700)
    path = directory / (str(time.time_ns()) + '.json')
    started = time.monotonic()
    record = {'stage':label, 'started':datetime.now(timezone.utc).isoformat(), 'status':'running'}
    host.save(path, record)
    print('开始：' + label + '；记录：' + str(path), flush=True)
    try:
        yield
    except BaseException as error:
        record.update(status='failed', error_type=type(error).__name__)
        raise
    else:
        record['status'] = 'passed'
    finally:
        record['seconds'] = round(time.monotonic() - started, 2)
        host.save(path, record)
        print(f"阶段 {label}：{record['status']}，耗时 {record['seconds']} 秒。", flush=True)


def install(instance):
    root = manager.instance_path(instance)
    if root.exists():
        print('该实例已存在，沿用原配置启动；初始化未完成时管理器会拒绝启动，请按恢复指南处理。')
        manager.main(['start', '--instance', instance])
        web_instructions(instance)
        return
    host.system_check()
    mem = host.memory()
    recommended = host.recommended_swap(mem['MemTotal'])
    print('可选等级：' + ' / '.join(map(str, manager.catalog())))
    while True:
        try:
            level = selected_level(ask('选择等级', '86')); break
        except ValueError as error:
            print(error)
    public_ip = str(ipaddress.IPv4Address(ask('客户端可访问的服务器 IPv4')))
    pvf = Path(ask('配套 Script.pvf 完整路径', f'/opt/dof-inputs/{level}/Script.pvf')).expanduser().resolve(strict=True)
    if not pvf.is_file() or pvf.suffix.lower() != '.pvf' or pvf.stat().st_size < 1024:
        raise ValueError('请准备完整的配套 PVF 文件。')
    supplied = manager.default_dof_assets({'LAUNCHER_MODE':'native'})
    plugins, raid = plugin_selection()
    channels = ','.join(map(str, manager.runtime_config.channels(ask('频道，逗号分隔', '15,52'))))
    mode = {'1':'internal','2':'external'}[choose('数据库：1 内置容器 / 2 同机 MySQL 5.6', ('1','2'), '1')]
    image_choice = choose('镜像：1 本机源码构建 / 2 自己的仓库拉取', ('1','2'), '1')
    if image_choice == '1':
        if mem['MemTotal'] < 4 * host.GIB:
            raise ValueError('内存不足 4 GiB，请选择拉取已构建镜像。')
        image_args = ['--build', '--registry=', '--tag', 'local']
    else:
        registry, tag = ask('镜像仓库/命名空间'), ask('镜像版本标签', 'latest')
        manager.image_config.validate(registry, tag)
        image_args = ['--registry', registry, '--tag', tag]
    admin_port = integer('网页端口',18080,1,65535)
    admin_bind = '0.0.0.0' if choose('网页：1 直接访问 / 2 仅本机供反代',('1','2'),'1') == '1' else '127.0.0.1'
    swap_gib = integer('目标总 Swap GiB，0 跳过；保留已有 Swap',recommended // host.GIB,0,64)
    disable_firewall = choose('防火墙：1 关闭检测到的 UFW/firewalld / 2 保留',('1','2'),'1') == '1'
    # Collect external parameters before any host mutation. Network allocation follows Docker setup.
    db = None
    if mode == 'external':
        db_host = str(ipaddress.IPv4Address(ask('MySQL 已配置的宿主机私网 IPv4（不能填回环地址）')))
        db_port = integer('MySQL 端口',3306,1,65535)
        binary = str(Path(ask('MySQL 客户端完整路径',mysql_binary())).expanduser().resolve())
        sock = ask('MySQL socket 路径',mysql_socket())
        db = (db_host, db_port, binary, sock)
    missing = host.swap_missing(swap_gib * host.GIB, mem['SwapTotal'])
    docker_root = '/var/lib/docker'
    if shutil.which('docker'):
        docker_root = host.run(['docker','info','--format','{{.DockerRootDir}}'])
    requests = [(docker_root,(20 if image_choice == '1' else 10)*host.GIB),
                (manager.STATE,3*pvf.stat().st_size+2*host.GIB),(host.SWAP,missing)]
    host.disk_check(requests)
    manager.check_ports({'BIND_IP':'0.0.0.0','ADMIN_BIND_IP':admin_bind,'ADMIN_PORT':str(admin_port),'LAUNCHER_MODE':'native','OPEN_CHANNEL':channels})
    print(f'\n实例 {instance} / {level} 级 / native / 频道 {channels}')
    print(f'插件 {plugins} / 团本 {raid} / 数据库 {mode} / 网页 {admin_bind}:{admin_port}')
    if db:
        print(f'外置 MySQL {db[0]}:{db[1]} / 客户端 {db[2]} / socket {db[3]}')
    print(f'磁盘预算 {sum(size for _,size in requests)/host.GIB:.1f} GiB；新增 Swap {missing/host.GIB:.2f} GiB')
    print('补齐缺少依赖；防火墙：' + ('关闭已识别管理器并保留恢复记录' if disable_firewall else '保留'))
    if mem['MemTotal'] < 8 * host.GIB:
        print('内存少于 8 GiB，建议减少频道；Swap 不代表性能达标。')
    if not confirm():
        print('已取消，未修改主机或实例。'); return
    arguments = ['install','--instance',instance,'--level',str(level),'--ip',public_ip,
        '--pvf',str(pvf),'--launcher','native','--plugins',plugins,'--raid-extension',raid,
        '--database',mode,'--channels',channels,'--admin-bind',admin_bind,'--admin-port',str(admin_port),*image_args]
    def deploy(extra, bind=None):
        if disable_firewall and bind in ('0.0.0.0','*','::'):
            raise ValueError('外置 MySQL 监听所有接口；请改私网监听或重试并选择保留防火墙。')
        with stage('正式资源哈希校验'):
            assets = assets_directory(supplied,instance)
        with stage('主机基础依赖'):
            host.prepare_dependencies(base_only=True)
        with stage('主机 Swap 与防火墙'):
            if disable_firewall:
                host.firewall(extra_ports=[db[1]] if db else [])
            if swap_gib:
                host.create_swap(swap_gib * host.GIB)
        # A fresh firewalld host must be handled BEFORE Docker creates its chains.
        with stage('Docker 依赖'):
            host.prepare_dependencies(); manager.docker_check()
        if db:
            hosts, used = inspect_network()
            if db[0] not in hosts:
                raise ValueError('MySQL 地址不是本机已配置的私网 IPv4。')
            subnet = free_subnet(used)
            print('外置数据库容器网段：' + subnet + '；保留防火墙时需允许此网段访问数据库私网端口。')
            extra += ['--db-allow-host',subnet]
        with stage('容器构建与实例部署'):
            manager.main([*arguments,'--dof-assets',str(assets),*extra])
        web_instructions(instance)
    if db:
        password = getpass.getpass('本机 MySQL root 密码（隐藏输入，仅用于此次初始化）：')
        with mysql_connection(password,db[3]) as defaults:
            del password
            bind = check_external(db[2],defaults,db[0],db[1])
            deploy(['--db-host',db[0],'--db-port',str(db[1]),'--mysql-bin',db[2],
                    '--mysql-config',str(defaults)],bind)
    else:
        deploy([])


def configure(instance, plugins_only=False):
    previous = manager.read_env(manager.instance_path(instance))
    arguments = ['config','--instance',instance]
    choice = 'plugins' if plugins_only else choose('1 修改 IP / 2 修改频道 / 3 玩家来源 / 4 网页绑定 / 0 返回',('0','1','2','3','4'),'0')
    if choice == 'plugins':
        plugins,raid = plugin_selection(previous)
        arguments += ['--plugins',plugins,'--raid-extension',raid]
    elif choice == '1':
        arguments += ['--ip',str(ipaddress.IPv4Address(ask('新的 IPv4',previous['PUBLIC_IP'])))]
    elif choice == '2':
        channels = manager.runtime_config.channels(ask('频道，逗号分隔',previous['OPEN_CHANNEL']))
        arguments += ['--channels',','.join(map(str,channels))]
    elif choice == '3':
        arguments += ['--client-cidrs',ask('允许的玩家 IP/CIDR，逗号分隔；留空不限来源')]
    elif choice == '4':
        arguments += ['--admin-bind',str(ipaddress.IPv4Address(ask('网页绑定地址',previous.get('ADMIN_BIND_IP','127.0.0.1'))))]
    else:
        return
    print('将应用当前实例配置并重建受影响服务；保留配置恢复点。')
    if confirm():
        manager.main(arguments)


def docker_summary(instance):
    if not shutil.which('docker'):
        return '待安装', '尚未部署'
    try:
        host.local_docker()
        engine = subprocess.run(['docker','info','--format','{{.ServerVersion}}'],
                                capture_output=True,text=True,timeout=3)
        if engine.returncode:
            return '不可连接，请检查服务或权限', '未取得状态'
        services = subprocess.run(['docker','ps','-a','--filter',
            'label=com.docker.compose.project=dof-'+instance,
            '--format','{{.Label "com.docker.compose.service"}}: {{.Status}}'],
            capture_output=True,text=True,timeout=3)
        health = services.stdout.strip().replace('\n',' / ') if services.returncode == 0 else '状态读取失败'
        return '运行中 '+engine.stdout.strip(), health or '当前实例无容器'
    except (ValueError,OSError,subprocess.TimeoutExpired):
        return '本机状态不可用或 context 不符', '未取得状态'


def show_header(instance):
    mem = host.memory()
    print('\n========== dof-easy 容器一键端 ==========')
    print(f"{os.uname().sysname} {os.uname().release} / CPU {os.cpu_count()} 核 / 内存 {mem['MemTotal']/host.GIB:.1f} GiB / 可用 {mem['MemAvailable']/host.GIB:.1f} GiB")
    print(f"Swap {mem['SwapTotal']/host.GIB:.1f} GiB / 项目磁盘可用 {shutil.disk_usage(manager.ROOT).free/host.GIB:.1f} GiB / 当前实例 {instance}")
    root = manager.instance_path(instance)
    if (root / '.env').is_file():
        try:
            values = manager.read_env(root)
            if not {'DNF_MAX_LEVEL','PLUGIN_MODE','ADMIN_PORT'} <= values.keys():
                raise ValueError('Incomplete instance')
        except (OSError,ValueError,KeyError):
            print('实例配置不完整；可切换实例或查看恢复指南，未自动修复或建库。')
            return
        print(f"等级 {values['DNF_MAX_LEVEL']} / 数据库 {manager.database.kind(values)} / 插件 {values['PLUGIN_MODE']} / 团本 {values.get('RAID_EXTENSION','off')}")
        print(f"网页绑定 {values.get('ADMIN_BIND_IP','127.0.0.1')}:{values['ADMIN_PORT']}；服务健康见菜单 3")
    engine, health = docker_summary(instance)
    print('Docker：' + engine + '；服务：' + health)


def backups(instance):
    choice = choose('1 备份（暂时停止本实例写入服务）/ 2 查看备份 / 0 返回',('0','1','2'),'0')
    if choice == '1' and confirm():
        manager.main(['backup','--instance',instance])
    elif choice == '2':
        for path in sorted((manager.STATE/'backups').glob(instance+'-*.tar.gz'),reverse=True):
            if not re.fullmatch(re.escape(instance)+r'-[0-9]{8}-[0-9]{6}\.tar\.gz',path.name):
                continue
            with path.open('rb') as stream:
                digest = hashlib.file_digest(stream,'sha256').hexdigest()
            manifest = path.with_name(path.name+'.sha256')
            expected = manifest.read_text().split() if manifest.is_file() else []
            status = '匹配' if expected and expected[0] == digest else '无原始摘要' if not expected else '不匹配'
            print(path.name, time.strftime('%Y-%m-%d %H:%M:%S',time.localtime(path.stat().st_mtime)), path.stat().st_size,'字节',status,digest)


def swap_menu():
    print('当前 Swap：',host.memory()['SwapTotal']/host.GIB,'GiB；本工具记录：',host.swap_entries())
    choice = choose('1 补足总 Swap / 2 移除本工具创建的文件 / 0 返回',('0','1','2'),'0')
    if choice == '1':
        total = integer('总 Swap GiB，0 不新增',host.recommended_swap(host.memory()['MemTotal'])//host.GIB,0,64)
        if confirm():
            host.system_check(); host.create_swap(total*host.GIB)
    elif choice == '2':
        filename = ask('从上方记录复制完整文件路径')
        if confirm('将 swapoff 并移除该受管文件，确认？'):
            host.system_check(); host.remove_swap(filename)


def main(argv=None):
    argparse.ArgumentParser(description='dof-easy 中文容器一键端；等级可选，回车默认 86。').parse_args(argv)
    if sys.platform != 'linux' or sys.version_info < (3,11):
        raise ValueError('请在 Linux 使用 Python 3.11+。')
    instance = 'main'
    while True:
        try:
            show_header(instance)
            print('1 安装服务端\n2 启动 / 停止 / 重启\n3 状态与日志\n4 地址、频道与访问范围\n5 插件与团本扩展\n6 备份与备份列表\n7 环境检查与依赖安装\n8 Swap 管理\n9 防火墙设置与恢复\n10 网页入口与端口\n11 切换实例\n0 退出')
            choice = choose('请选择',tuple(map(str,range(12))),'0')
            if choice == '0': return 0
            host.local_docker()
            if choice == '1': install(instance)
            elif choice == '2':
                action = choose('1 启动 / 2 停止 / 3 重启 / 0 返回',('0','1','2','3'),'0')
                if action != '0' and confirm():
                    manager.main([{'1':'start','2':'stop','3':'restart'}[action],'--instance',instance])
            elif choice == '3':
                action = choose('1 状态 / 2 游戏日志 / 3 网页日志',('1','2','3'),'1')
                manager.main(['status','--instance',instance] if action == '1' else ['logs','--instance',instance,'--service','game' if action == '2' else 'admin'])
            elif choice in ('4','5'): configure(instance,choice == '5')
            elif choice == '6': backups(instance)
            elif choice == '7':
                print(host.system_check())
                print('安装缺少依赖，复用已有完整 Docker；不自动替换冲突包。')
                if confirm():
                    with stage('主机依赖'): host.prepare_dependencies()
            elif choice == '8': swap_menu()
            elif choice == '9':
                print(host.firewall_status())
                action = choose('1 关闭检测到的防火墙 / 2 恢复本工具关闭前状态 / 0 返回',('0','1','2'),'0')
                if action != '0' and confirm():
                    db_ports = [manager.read_env(p).get('DOF_DB_PORT','3306') for p in (manager.STATE/'instances').glob('*') if (p/'.env').is_file()]
                    with stage('防火墙'): host.firewall(action == '1',db_ports)
            elif choice == '10': web_instructions(instance)
            elif choice == '11':
                print('已有实例：' + ', '.join(sorted(p.name for p in (manager.STATE/'instances').glob('*') if (p/'instance.json').is_file())))
                selected = ask('实例名',instance); manager.instance_path(selected); instance = selected
        except EOFError: return 0
        except KeyboardInterrupt: print('\n已中断，返回菜单；已完成的主机变更见恢复指南。')
        except (ValueError,OSError,KeyError,subprocess.SubprocessError) as error:
            print('操作未完成：' + str(error),file=sys.stderr)
            print('日志：.dof-easy/operations；主机恢复记录：/var/lib/dof-easy/host。实例数据保留。')


if __name__ == '__main__':
    sys.exit(main())
