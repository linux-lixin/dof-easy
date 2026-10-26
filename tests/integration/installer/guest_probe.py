#!/usr/bin/env python3
"""Run ONLY inside an explicitly disposable systemd VM; mutates that guest."""
import importlib.util
import json
import os
from pathlib import Path
import subprocess
import sys
import time
import urllib.error
import urllib.request

if os.geteuid() != 0 or not Path('/etc/dof-installer-disposable').is_file():
    raise SystemExit('Refusing: requires disposable-VM marker /etc/dof-installer-disposable')
spec=importlib.util.spec_from_file_location('host',Path(__file__).with_name('host.py'))
host=importlib.util.module_from_spec(spec);spec.loader.exec_module(host)
report=Path('/root/installer-result.json')
result=json.loads(report.read_text()) if report.exists() else {}
def record(key,value=True):
    result[key]=value;report.write_text(json.dumps(result,indent=2)+'\n')
    print(key,value,flush=True)
def call(args):
    return subprocess.check_output(args,text=True).strip()
def http():
    deadline=time.monotonic()+90
    while True:
        try:
            return urllib.request.urlopen('http://127.0.0.1:18089',timeout=5).read().decode()
        except urllib.error.URLError:
            if time.monotonic() >= deadline:raise
            time.sleep(1)

phase=sys.argv[1]
if phase=='first':
    host.prepare_dependencies()
    record('dependencies',call(['docker','version','--format','{{.Server.Version}}']))
    host.prepare_dependencies();record('dependenciesRepeat')
    subprocess.run(['apt-get','install','-y','ufw'],check=True)
    subprocess.run(['docker','pull','busybox:1.36'],check=True)
    record('testImage',json.loads(call(['docker','image','inspect','busybox:1.36']))[0]['Id'])
    subprocess.run(['docker','run','-d','--name','dof-installer-probe','--restart','unless-stopped','-p','127.0.0.1:18089:80','busybox:1.36','sh','-c','mkdir -p /www; echo dof-probe > /www/index.html; httpd -f -p 80 -h /www'],check=True)
    assert 'dof-probe' in http();record('dockerHttpBefore')
    # Establish a foreign swap file to prove that the installer preserves it.
    subprocess.run(['dd','if=/dev/zero','of=/foreign.swap','bs=1M','count=32','status=none'],check=True)
    os.chmod('/foreign.swap',0o600)
    subprocess.run(['mkswap','/foreign.swap'],check=True);subprocess.run(['swapon','/foreign.swap'],check=True)
    with open('/etc/fstab','a') as f:f.write('/foreign.swap none swap sw 0 0\n')
    host.create_swap(128*1024**2)
    entries=host.swap_entries();assert len(entries)==1
    before=Path('/etc/fstab').read_text();host.create_swap(128*1024**2)
    assert Path('/etc/fstab').read_text()==before and '/foreign.swap' in host.active_swap()
    record('swapCreateRepeatForeignPreserved')
    subprocess.run(['ufw','allow','22/tcp'],check=True)
    subprocess.run(['ufw','--force','enable'],check=True)
    host.firewall(True);assert 'dof-probe' in http();record('ufwDisableDockerPreserved')
    host.firewall(False);assert 'dof-probe' in http();record('ufwRestoreDockerPreserved')
    record('firstPhasePassed')
elif phase=='reboot':
    assert result.get('firstPhasePassed')
    assert '/foreign.swap' in host.active_swap()
    assert all(name in host.active_swap() for name in host.swap_entries())
    assert host.firewall_status()['ufw']['active']
    assert 'dof-probe' in http()
    record('rebootSwapFirewallDockerPersisted')
    for name in host.swap_entries():host.remove_swap(name)
    assert '/foreign.swap' in host.active_swap();record('managedSwapRemovalForeignPreserved')
    subprocess.run(['docker','rm','-f','dof-installer-probe'],check=True)
    record('guestAcceptancePassed')
elif phase=='firewalld':
    assert result.get('guestAcceptancePassed')
    # This fixture may restart only the disposable guest's empty/test engine.
    names=set(call(['docker','ps','-a','--format','{{.Names}}']).splitlines())
    assert names <= {'dof-installer-firewalld'}, names
    if names:subprocess.run(['docker','rm','-f','dof-installer-firewalld'],check=True)
    subprocess.run(['ufw','disable'],check=True)
    subprocess.run(['apt-get','install','-y','firewalld'],check=True,env={**os.environ,'DEBIAN_FRONTEND':'noninteractive'})
    subprocess.run(['systemctl','enable','--now','firewalld'],check=True)
    host.firewall(False)  # Recover a recorded earlier test failure, if any.
    subprocess.run(['systemctl','restart','docker'],check=True)
    subprocess.run(['docker','run','-d','--name','dof-installer-firewalld','-p','127.0.0.1:18089:80','busybox:1.36','sh','-c','mkdir -p /www; echo dof-probe > /www/index.html; httpd -f -p 80 -h /www'],check=True)
    assert 'dof-probe' in http()
    before=host.docker_rules()
    try:host.firewall(True)
    except ValueError as error:assert 'legacy' in str(error) or 'iptables' in str(error)
    else:raise AssertionError('Must reject firewalld stop with existing Docker rules')
    assert host.firewall_status()['firewalld']['active']
    assert host.docker_rules()==before and 'dof-probe' in http()
    record('firewalldForeignRulesRejectedBeforeChange')
    subprocess.run(['docker','rm','-f','dof-installer-firewalld'],check=True)
    subprocess.run(['systemctl','stop','docker','docker.socket'],check=True)
    # Fixture-only reload in the empty disposable VM clears stale engine rules.
    # The production installer never reloads firewalld to bypass the guard.
    subprocess.run(['firewall-cmd','--reload'],check=True)
    host.firewalld_legacy_guard()
    host.firewall(True);assert not host.firewall_status()['firewalld']['active']
    record('firewalldEmptyHostDisable')
    host.firewall(False);assert host.firewall_status()['firewalld']['active']
    record('firewalldEmptyHostRestore')
    subprocess.run(['systemctl','start','docker'],check=True)
else:
    raise SystemExit('first, reboot or firewalld')
