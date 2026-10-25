#!/usr/bin/env python3
"""Install fail-closed rules inside this container's shared game network namespace."""
import argparse
import ipaddress
import logging
import os
import re
from pathlib import Path
import signal
import socket
import subprocess
import threading

LOG = logging.getLogger("vipai86.netguard")
TCP_PORTS = (7001, 7300, 30015, 30052)
UDP_PORTS = (7001, 7300, 31015, 31052, 2311, 2312, 2313)
DEFAULT_MARKER = "/run/netguard/ready"


def selected_ports():
    raw = os.environ.get('OPEN_CHANNEL', '15,52')
    if not re.fullmatch(r'[0-9]+(?:,[0-9]+)*', raw):
        raise ValueError('OPEN_CHANNEL must be a normalized channel list')
    channels = sorted({int(x) for x in raw.split(',')})
    if not set(channels) <= ({1, 6, 7} | set(range(11, 40)) | set(range(52, 57))):
        raise ValueError('Unsupported OPEN_CHANNEL')
    launcher = os.environ.get('LAUNCHER_MODE', 'dof')
    if launcher not in ('dof', 'native'):
        raise ValueError('Invalid LAUNCHER_MODE')
    tcp = [7001, 7300] + [30000+c for c in channels]
    if launcher == 'native':
        tcp.append(8887)
    return tcp, [7001, 7300, 2311, 2312, 2313] + [31000+c for c in channels]


def render_rules(db_addresses=(), ipv6=False):
    addresses = sorted({str(ipaddress.IPv4Address(value)) for value in db_addresses})
    if not ipv6 and not addresses:
        raise ValueError("At least one database IPv4 address is required")
    tcp, udp = selected_ports()
    raw_clients = os.environ.get('NETGUARD_ALLOWED_CLIENTS', '').strip()
    clients = [ipaddress.ip_network(x.strip(), strict=False) for x in raw_clients.split(',')] if raw_clients else []
    # An explicit IPv4 list must not accidentally allow all IPv6 clients.
    sources = [str(x) for x in clients if x.version == (6 if ipv6 else 4)] if clients else [None]
    lines = ["*filter", ":INPUT DROP [0:0]", ":FORWARD DROP [0:0]", ":OUTPUT DROP [0:0]",
             "-A INPUT -i lo -j ACCEPT",
             "-A INPUT -m conntrack --ctstate ESTABLISHED,RELATED -j ACCEPT",
             "-A OUTPUT -o lo -j ACCEPT",
             "-A OUTPUT -m conntrack --ctstate ESTABLISHED,RELATED -j ACCEPT"]
    # xt_multiport permits at most 15 ports per rule.
    for protocol, ports in (('tcp', tcp), ('udp', udp)):
        for start in range(0, len(ports), 15):
            for source in sources:
                lines.append('-A INPUT ' + ('-s ' + source + ' ' if source else '') + '-p ' + protocol + ' -m multiport --dports '
                             + ','.join(map(str, ports[start:start+15])) + ' -j ACCEPT')
    if not ipv6:
        for address in addresses:
            lines.append(f"-A OUTPUT -d {address}/32 -p tcp --dport 3306 -m conntrack --ctstate NEW -j ACCEPT")
    # Deterministic failure for blocked callers; policies remain DROP if a rule is removed.
    lines += ["-A OUTPUT -j REJECT", "COMMIT", ""]
    return "\n".join(lines)


def resolve_database(host):
    addresses = socket.getaddrinfo(host, 3306, socket.AF_INET, socket.SOCK_STREAM)
    return sorted({str(ipaddress.IPv4Address(item[4][0])) for item in addresses})


def apply_rules(db_addresses, runner=subprocess.run):
    for program, rules in (("iptables-restore", render_rules(db_addresses)),
                           ("ip6tables-restore", render_rules(ipv6=True))):
        # One atomic COMMIT per address family, with the xtables lock respected.
        runner([program, "--wait", "5"], input=rules, text=True, check=True,
               stdout=subprocess.DEVNULL, stderr=subprocess.PIPE, timeout=10)


def policy_is_active(runner=subprocess.run):
    for program in ("iptables", "ip6tables"):
        for chain in ("INPUT", "FORWARD", "OUTPUT"):
            result = runner([program, "--wait", "2", "-S", chain], check=True,
                            text=True, stdout=subprocess.PIPE, stderr=subprocess.PIPE, timeout=3)
            if f"-P {chain} DROP" not in result.stdout.splitlines():
                return False
    return True


def healthcheck(marker):
    try:
        return Path(marker).read_text(encoding="ascii") == "ready\n" and policy_is_active()
    except (OSError, subprocess.SubprocessError):
        return False


def run(db_host, marker, resolve_timeout=60, dns_refresh_interval=30):
    if not (Path("/.dockerenv").exists() or Path("/run/.containerenv").exists()):
        raise RuntimeError("Netguard must only run in its dedicated container")
    ready = Path(marker)
    ready.parent.mkdir(parents=True, exist_ok=True)
    ready.unlink(missing_ok=True)
    stopped = threading.Event()
    for signum in (signal.SIGTERM, signal.SIGINT):
        signal.signal(signum, lambda *_: stopped.set())
    try:
        attempts = max(1, int(resolve_timeout))
        addresses = []
        for _ in range(attempts):
            if stopped.is_set():
                return 0
            try:
                addresses = resolve_database(db_host)
            except OSError:
                addresses = []
            if addresses:
                break
            stopped.wait(1)
        if not addresses:
            raise RuntimeError("Database IPv4 resolution failed; game startup remains blocked")
        apply_rules(addresses)
        if not policy_is_active():
            raise RuntimeError("Firewall verification failed")
        temporary = ready.with_suffix(".tmp")
        temporary.write_text("ready\n", encoding="ascii")
        temporary.chmod(0o600)
        os.replace(temporary, ready)
        LOG.info("Network guard ready: only loopback, database TCP/3306, and connection replies may leave")
        while not stopped.wait(dns_refresh_interval):
            try:
                refreshed = resolve_database(db_host)
            except OSError:
                LOG.warning("Database DNS refresh failed; retaining the active allowlist")
                continue
            if refreshed and refreshed != addresses:
                # Retain the old rules until a valid replacement is ready to commit.
                apply_rules(refreshed)
                if not policy_is_active():
                    raise RuntimeError("Firewall verification failed after DNS refresh")
                addresses = refreshed
                LOG.info("Database address changed; refreshed the TCP/3306 allowlist")
        return 0
    finally:
        ready.unlink(missing_ok=True)
        # Never flush rules on shutdown: the game may still share this network namespace.
        LOG.info("Network guard stopped; fail-closed rules retained")


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--db-host", default=os.environ.get("NETGUARD_DB_HOST", "db"))
    parser.add_argument("--health-marker", default=DEFAULT_MARKER)
    parser.add_argument("--healthcheck", action="store_true")
    parser.add_argument("--dns-refresh-interval", type=float, default=30)
    args = parser.parse_args()
    if not (0 < args.dns_refresh_interval <= 30):
        parser.error("--dns-refresh-interval must be positive and at most 30 seconds")
    logging.basicConfig(level=logging.INFO, format="%(asctime)s %(levelname)s %(message)s")
    if args.healthcheck:
        return 0 if healthcheck(args.health_marker) else 1
    try:
        return run(args.db_host, args.health_marker, dns_refresh_interval=args.dns_refresh_interval)
    except (OSError, ValueError, RuntimeError, subprocess.SubprocessError) as exc:
        LOG.error("Network guard failed: %s", type(exc).__name__)
        return 1


if __name__ == "__main__":
    raise SystemExit(main())
