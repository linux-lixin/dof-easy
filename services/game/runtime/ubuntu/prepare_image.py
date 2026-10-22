"""Install Ubuntu runtime helpers and managed scripts into the image."""
from pathlib import Path
import shutil

BASE=Path('/opt/dnf/ubuntu')
INIT=Path('/home/template/init')

def main():
    for root in (BASE, INIT):
        for path in root.rglob('*'):
            if path.is_file() and (path.suffix in ('.sh', '.py') or path.name == 'mysql'):
                path.write_text(path.read_text())
                path.chmod(0o755)
    shutil.copyfile(BASE/'mysql','/usr/local/bin/mysql')
    Path('/usr/local/bin/mysql').chmod(0o755)
    shutil.copyfile(BASE/'startup_notice.conf','/etc/supervisor/conf.d/startup_notice.conf')
    # A bundled PVF is only a repository fixture; deployment must supply its own file.
    (INIT/'Script.tgz').unlink(missing_ok=True)
    (INIT/'Script.pvf').unlink(missing_ok=True)
    supervisor=Path('/etc/supervisor/supervisord.conf')
    supervisor.unlink(missing_ok=True)
    supervisor.symlink_to('/etc/supervisord.conf')
    for section in ('run','monitor_ip'):
        destination=BASE/'managed'/section
        destination.mkdir(parents=True,exist_ok=True)
        for path in (INIT/section).glob('*.sh'):
            if section=='monitor_ip' and path.name=='monitor_ip.sh': continue
            shutil.copyfile(path,destination/path.name)
    # user_daily_script.sh, PVF, keys and hooks remain user-owned.

if __name__=='__main__': main()
