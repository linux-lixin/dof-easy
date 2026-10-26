"""Local host preparation. No remote Docker, global rule flush or existing swap removal."""
from contextlib import contextmanager
import fcntl
import hashlib
import json
import math
import os
from pathlib import Path
import platform
import shutil
import subprocess
import tempfile
import time

GIB = 1024 ** 3
STATE = Path('/var/lib/dof-easy/host')
SWAP = Path('/var/lib/dof-easy/swap')
FSTAB = Path('/etc/fstab')


def run(args, check=True):
    result = subprocess.run([str(x) for x in args], text=True, capture_output=True, timeout=900,
                            env={**os.environ, 'LC_ALL': 'C'})
    if check and result.returncode:
        raise ValueError(f'{args[0]} 执行失败（{result.returncode}）；请检查对应系统服务日志。')
    return result.stdout.strip()


def atomic(path, data, mode=0o600):
    path.parent.mkdir(parents=True, exist_ok=True, mode=0o700)
    if path.is_symlink():
        raise ValueError('拒绝覆盖符号链接：' + str(path))
    fd, name = tempfile.mkstemp(prefix='.dof-', dir=path.parent)
    try:
        with os.fdopen(fd, 'w') as stream:
            os.fchmod(stream.fileno(), mode)
            stream.write(data)
            stream.flush(); os.fsync(stream.fileno())
        os.replace(name, path)
    finally:
        Path(name).unlink(missing_ok=True)


def save(path, value):
    atomic(path, json.dumps(value, ensure_ascii=False, indent=2) + '\n')


@contextmanager
def locked():
    if os.geteuid() != 0:
        raise ValueError('主机准备需要 root，请使用 sudo bash install.sh。')
    STATE.mkdir(parents=True, exist_ok=True, mode=0o700)
    with (STATE / 'operation.lock').open('a') as stream:
        try:
            fcntl.flock(stream, fcntl.LOCK_EX | fcntl.LOCK_NB)
        except BlockingIOError:
            raise ValueError('另一个主机准备操作正在运行。') from None
        yield


def local_docker():
    if os.environ.get('DOCKER_CONTEXT') or os.environ.get('DOCKER_HOST'):
        raise ValueError('请取消 DOCKER_HOST / DOCKER_CONTEXT；一键端仅操作本机默认 Docker。')
    if shutil.which('docker'):
        value = json.loads(run(['docker', 'context', 'inspect']))
        endpoint = value[0]['Endpoints']['docker']['Host']
        if value[0]['Name'] != 'default' or endpoint not in ('unix:///var/run/docker.sock', 'unix:///run/docker.sock'):
            raise ValueError('一键端要求本机 default Docker context，拒绝远程或其他 context。')


def memory():
    rows = dict(line.split(':', 1) for line in Path('/proc/meminfo').read_text().splitlines())
    return {key: int(rows[key].split()[0]) * 1024 for key in ('MemTotal','MemAvailable','SwapTotal')}


def recommended_swap(ram):
    if ram < 2 * GIB:
        raise ValueError('物理内存不足 2 GiB，不能部署游戏服务。')
    return (8 if ram < 4 * GIB else 4 if ram < 8 * GIB else 2) * GIB


def swap_missing(total, existing):
    return math.ceil(max(0, total - existing) / (1024 ** 2)) * (1024 ** 2)


def disk_budget(build, pvf_size, swap_bytes):
    return (20 if build else 10) * GIB + 3 * pvf_size + swap_bytes + 2 * GIB


def disk_check(requests):
    """Sum requirements per filesystem, including split Docker/data/swap disks."""
    devices = {}
    for path, size in requests:
        path = Path(path).resolve()
        while not path.exists():
            path = path.parent
        device = path.stat().st_dev
        old = devices.get(device, (path, 0))
        devices[device] = (old[0], old[1] + size)
    for path, required in devices.values():
        if shutil.disk_usage(path).free < required:
            raise ValueError(f'{path} 所在磁盘需至少 {required / GIB:.1f} GiB 可用空间。')


def system_check():
    release = platform.freedesktop_os_release()
    if (release.get('ID'), release.get('VERSION_ID')) not in {
        ('ubuntu','24.04'), ('ubuntu','26.04'), ('debian','12'), ('debian','13')}:
        raise ValueError('支持 Ubuntu 24.04/26.04、Debian 12/13。')
    if platform.machine() != 'x86_64' or not Path('/run/systemd/system').is_dir():
        raise ValueError('主机准备需要 amd64 与运行中的 systemd。')
    local_docker()
    return release


def prepare_dependencies(base_only=False):
    system_check()
    with locked():
        script = Path(__file__).with_name('bootstrap.sh')
        record = STATE / f'dependencies-{time.time_ns()}.json'
        save(record, {'phase':'changing'})
        subprocess.run(['bash', str(script), '--base' if base_only else '--dependencies'], check=True)
        save(record, {'phase':'ready', 'packages':run(['dpkg-query','-W','-f=${Package}=${Version}\n']),
                      'docker':None if base_only else run(['docker','version','--format','{{.Server.Version}}']),
                      'compose':None if base_only else run(['docker','compose','version','--short'])})
    local_docker()


def active_swap():
    return {line.split()[0] for line in Path('/proc/swaps').read_text().splitlines()[1:]}


def swap_entries():
    path = STATE / 'swap.json'
    return json.loads(path.read_text()) if path.exists() else {}


def persist_swap(path):
    original = FSTAB.read_text()
    entry = f'{path} none swap sw 0 0 # dof-easy\n'
    if any(line.split() and line.split()[0] == str(path) for line in original.splitlines()):
        return
    if not (STATE / 'fstab.before').exists():
        atomic(STATE / 'fstab.before', original)
    atomic(FSTAB, original.rstrip('\n') + '\n' + entry, FSTAB.stat().st_mode & 0o777)


def create_swap(total):
    if not 0 <= total <= 64 * GIB:
        raise ValueError('Swap 目标必须为 0～64 GiB。')
    with locked():
        entries = swap_entries()
        # Recover a swapon/fstab interruption before allocating another file.
        for filename, status in list(entries.items()):
            path = Path(filename)
            if path.parent != SWAP or path.is_symlink():
                raise ValueError('Swap 记录异常，拒绝操作。')
            if status != 'ready':
                if filename in active_swap():
                    persist_swap(path)
                    entries[filename] = 'ready'
                elif path.exists():
                    path.unlink()
                    del entries[filename]
                else:
                    del entries[filename]
                save(STATE / 'swap.json', entries)
        missing = swap_missing(total, memory()['SwapTotal'])
        if not missing:
            print('已有 Swap 已满足目标，无需新增。'); return
        SWAP.mkdir(parents=True, exist_ok=True, mode=0o700)
        if SWAP.is_symlink():
            raise ValueError('Swap 目录不能为符号链接。')
        filesystem = run(['findmnt','-n','-o','FSTYPE','-T',SWAP])
        if filesystem not in ('ext4','xfs'):
            raise ValueError('仅自动创建 ext4/XFS Swap；当前文件系统：' + filesystem)
        disk_check([(SWAP, missing + 2 * GIB)])
        path = SWAP / f'swap-{time.time_ns()}'
        entries[str(path)] = 'creating'
        save(STATE / 'swap.json', entries)
        try:
            with path.open('xb') as stream:
                os.fchmod(stream.fileno(), 0o600)
            run(['dd','if=/dev/zero',f'of={path}','bs=1M',f'count={missing // (1024**2)}','status=none','conv=fsync'])
            run(['mkswap',path]); run(['swapon',path])
            if str(path) not in active_swap():
                raise ValueError('Swap 未出现在内核活动列表中。')
            persist_swap(path)
            entries[str(path)] = 'ready'; save(STATE / 'swap.json', entries)
            print('Swap 已启用并持久化：' + str(path))
        except BaseException:
            if str(path) not in active_swap():
                path.unlink(missing_ok=True)
                entries.pop(str(path), None); save(STATE / 'swap.json', entries)
            raise


def remove_swap(filename):
    with locked():
        entries = swap_entries()
        path = Path(filename)
        if filename not in entries or path.parent != SWAP or path.is_symlink():
            raise ValueError('只能移除本工具记录的普通 Swap 文件。')
        if filename in active_swap():
            run(['swapoff', path])  # Failure leaves fstab/file intact.
        if filename in active_swap():
            raise ValueError('Swap 仍在使用，未删除。')
        content = FSTAB.read_text()
        lines = [line for line in content.splitlines(keepends=True)
                 if not (line.split() and line.split()[0] == filename and '# dof-easy' in line)]
        atomic(FSTAB, ''.join(lines), FSTAB.stat().st_mode & 0o777)
        path.unlink(missing_ok=True)
        del entries[filename]; save(STATE / 'swap.json', entries)


def firewall_status():
    result = {}
    if shutil.which('ufw'):
        result['ufw'] = {'active': run(['ufw','status']).startswith('Status: active')}
    if shutil.which('firewall-cmd'):
        result['firewalld'] = {
            'active': run(['systemctl','is-active','firewalld'], check=False) == 'active',
            'enabled': run(['systemctl','is-enabled','firewalld'], check=False)}
    return result


def public_database_listener(extra_ports=()):
    import ipaddress
    for line in run(['ss','-H','-lntp']).splitlines():
        fields = line.split()
        if len(fields) < 4:
            continue
        address, _, port = fields[3].rpartition(':')
        if port not in {'3306', *map(str, extra_ports)} and not any(x in line for x in ('mysqld','mariadbd')):
            continue
        address = address.strip('[]')
        if address in ('*','0.0.0.0','::'):
            return True
        try:
            ip = ipaddress.ip_address(address)
        except ValueError:
            return True
        if not ip.is_private and not ip.is_loopback:
            return True
    return False


def docker_rules():
    result = {}
    for command in ('iptables-save','ip6tables-save'):
        if shutil.which(command):
            result[command] = sorted(line for line in run([command]).splitlines() if 'DOCKER' in line)
    return result


def firewalld_legacy_guard():
    # Debian 12 firewalld stop/start flushes legacy tables even with nft backend.
    # Refuse BEFORE touching its service if doing so could erase foreign rules.
    for command in ('iptables-save','ip6tables-save'):
        if not shutil.which(command):
            raise ValueError('无法检查 firewalld 的 legacy 规则；请保留防火墙并按端口放行。')
        for line in run([command]).splitlines():
            if line.startswith('-'):
                raise ValueError('firewalld 启停会清理现有 iptables 规则（包括 Docker）；请保留防火墙并按端口放行。未修改服务。')
            if line.startswith(':'):
                name, policy, *_ = line.split()
                if name not in (':INPUT',':OUTPUT',':FORWARD',':PREROUTING',':POSTROUTING') or policy != 'ACCEPT':
                    raise ValueError('检测到自定义或 Docker legacy 链；保留 firewalld，避免清除既有规则。')


def restore_firewall_state(before):
    fw = before.get('firewalld')
    start_fw = bool(fw and fw['active'] and run(['systemctl','is-active','firewalld'],check=False) != 'active')
    if start_fw:
        firewalld_legacy_guard()  # Preflight before changing enablement or other managers.
    if before.get('ufw', {}).get('active'):
        run(['ufw','--force','enable'])
    fw = before.get('firewalld')
    if fw:
        if fw['enabled'] in ('enabled','enabled-runtime'):
            run(['systemctl','enable',*(['--runtime'] if fw['enabled'] == 'enabled-runtime' else []),'firewalld'])
        if start_fw:
            run(['systemctl','start','firewalld'])


def firewall(disable=True, extra_ports=()):
    system_check()
    with locked():
        path = STATE / 'firewall.json'
        if not disable:
            if not path.exists():
                print('没有本工具关闭防火墙的记录。'); return
            record = json.loads(path.read_text())
            if record['phase'] == 'restored':
                print('已恢复，无需重复操作。'); return
            restore_firewall_state(record['before'])
            now = firewall_status()
            for name, previous in record['before'].items():
                if previous['active'] and not now.get(name, {}).get('active'):
                    raise ValueError('防火墙恢复未通过，请检查系统日志。')
                if previous.get('enabled') in ('enabled','enabled-runtime') and now.get(name,{}).get('enabled') != previous['enabled']:
                    raise ValueError('防火墙开机状态未恢复，请检查 systemctl。')
            record['phase'] = 'restored'; save(path, record)
            print('已恢复关闭前的服务状态；其他自定义规则未重写。'); return
        if public_database_listener(extra_ports):
            raise ValueError('数据库监听公网或所有接口；请先改为私网监听，或选择保留防火墙。')
        before = firewall_status()
        if sum(item['active'] for item in before.values()) > 1:
            raise ValueError('多个防火墙同时活动，请先人工明确管理方式；未修改。')
        enabled_fw = before.get('firewalld', {}).get('enabled') in ('enabled','enabled-runtime')
        if not any(item['active'] for item in before.values()) and not enabled_fw:
            print('没有活动的 UFW/firewalld；自定义 nftables/iptables 规则保留，不能据此认定全网放通。'); return
        if path.exists() and json.loads(path.read_text())['phase'] != 'restored':
            raise ValueError('已有未恢复的防火墙记录；先选择恢复，再重新关闭。')
        if before.get('firewalld',{}).get('active'):
            firewalld_legacy_guard()
        rules = docker_rules()
        snapshot = STATE / f'firewall-{time.time_ns()}'
        snapshot.mkdir(mode=0o700)
        for name in ('ufw','firewalld'):
            config = Path('/etc') / name
            if config.is_dir():
                shutil.copytree(config, snapshot / name, symlinks=True)
        record = {'before': before, 'phase':'changing', 'snapshot':str(snapshot)}
        save(path, record)
        try:
            if before.get('ufw', {}).get('active'):
                run(['ufw','disable'])
            if before.get('firewalld', {}).get('active') or enabled_fw:
                run(['systemctl','disable','--now','firewalld'])
            after = firewall_status()
            if any(item['active'] for item in after.values()) or after.get('firewalld', {}).get('enabled') in ('enabled','enabled-runtime'):
                raise ValueError('防火墙服务仍活动。')
            if docker_rules() != rules:
                raise ValueError('防火墙关闭影响了 Docker 规则，正在恢复防火墙服务；请核对 Docker 网络。')
            record['phase'] = 'disabled'; save(path, record)
        except BaseException:
            restore_firewall_state(before)
            # Keep changing state: a failed restoration can be retried via menu.
            raise
        print('已关闭检测到的防火墙服务；Docker 规则保持，自定义规则与云安全组未修改。')
