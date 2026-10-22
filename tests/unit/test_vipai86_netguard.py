"""Netguard rule checks and opt-in, isolated Docker networking verification."""
import importlib.util
import json
import os
from pathlib import Path
import subprocess
import tempfile
import time
import unittest
from unittest.mock import Mock, patch
import uuid

ROOT = Path(__file__).resolve().parents[2]
TEMP_ROOT = ROOT / "tests/unit" if os.name == "nt" else None
SCRIPT = ROOT / "deploy/vipai86/netguard/entrypoint.py"
SPEC = importlib.util.spec_from_file_location("vipai86_netguard", SCRIPT)
netguard = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(netguard)


class NetguardRules(unittest.TestCase):
    def test_ipv4_allows_only_database_new_connections(self):
        rules = netguard.render_rules(["172.31.0.2"])
        for chain in ("INPUT", "FORWARD", "OUTPUT"):
            self.assertIn(f":{chain} DROP [0:0]", rules)
        self.assertIn("-A OUTPUT -o lo -j ACCEPT", rules)
        self.assertIn("-A OUTPUT -m conntrack --ctstate ESTABLISHED,RELATED -j ACCEPT", rules)
        self.assertIn("-A OUTPUT -d 172.31.0.2/32 -p tcp --dport 3306 -m conntrack --ctstate NEW -j ACCEPT", rules)
        self.assertEqual(len([line for line in rules.splitlines() if line.startswith("-A OUTPUT")]), 4)
        self.assertNotIn("-A FORWARD", rules)

    def test_ipv6_has_no_database_or_new_external_connection_exception(self):
        rules = netguard.render_rules(ipv6=True)
        self.assertIn(":OUTPUT DROP [0:0]", rules)
        self.assertNotIn("--dport 3306", rules)
        self.assertNotIn("-A OUTPUT -d", rules)
        self.assertEqual(len([line for line in rules.splitlines() if line.startswith("-A OUTPUT")]), 3)

    def test_invalid_or_missing_database_address_fails_closed(self):
        for addresses in ([], ["bad"], ["::1"], ["172.31.0.2\n-A OUTPUT -j ACCEPT"]):
            with self.assertRaises(ValueError):
                netguard.render_rules(addresses)

    def test_atomic_restore_waits_for_lock_for_both_families(self):
        runner = Mock()
        netguard.apply_rules(["172.31.0.2"], runner=runner)
        self.assertEqual([call.args[0] for call in runner.call_args_list],
                         [["iptables-restore", "--wait", "5"], ["ip6tables-restore", "--wait", "5"]])
        for call in runner.call_args_list:
            self.assertEqual(call.kwargs["input"].count("COMMIT"), 1)
            self.assertTrue(call.kwargs["check"])
            self.assertEqual(call.kwargs["timeout"], 10)

    def test_failed_restore_is_not_ignored(self):
        runner = Mock(side_effect=subprocess.CalledProcessError(1, "iptables-restore"))
        with self.assertRaises(subprocess.CalledProcessError):
            netguard.apply_rules(["172.31.0.2"], runner=runner)

    def test_healthcheck_requires_marker_and_drop_policies(self):
        with tempfile.TemporaryDirectory(dir=TEMP_ROOT) as directory:
            marker = Path(directory) / "ready"
            self.assertFalse(netguard.healthcheck(str(marker)))
            marker.write_text("ready\n", encoding="ascii")
            with patch.object(netguard, "policy_is_active", return_value=False):
                self.assertFalse(netguard.healthcheck(str(marker)))
            with patch.object(netguard, "policy_is_active", return_value=True):
                self.assertTrue(netguard.healthcheck(str(marker)))

    def test_dns_change_replaces_allowlist_and_failure_preserves_it(self):
        event = Mock()
        event.is_set.return_value = False
        event.wait.side_effect = [False, False, True]
        with tempfile.TemporaryDirectory(dir=TEMP_ROOT) as directory:
            marker = Path(directory) / "ready"
            with patch.object(Path, "exists", return_value=True), \
                 patch.object(netguard.signal, "signal"), \
                 patch.object(netguard.threading, "Event", return_value=event), \
                 patch.object(netguard, "resolve_database", side_effect=[
                     ["172.31.0.2"], ["172.31.0.3"], OSError("temporary DNS failure")]), \
                 patch.object(netguard, "apply_rules") as apply, \
                 patch.object(netguard, "policy_is_active", return_value=True):
                self.assertEqual(netguard.run("db", str(marker), dns_refresh_interval=0.01), 0)
                self.assertEqual([call.args[0] for call in apply.call_args_list],
                                 [["172.31.0.2"], ["172.31.0.3"]])
            self.assertFalse(marker.exists())


RESPONDER = r"""
import os, signal, socket, threading
def tcp(family, host, port):
    listener = socket.socket(family, socket.SOCK_STREAM)
    listener.setsockopt(socket.SOL_SOCKET, socket.SO_REUSEADDR, 1)
    if family == socket.AF_INET6:
        listener.setsockopt(socket.IPPROTO_IPV6, socket.IPV6_V6ONLY, 1)
    listener.bind((host, port)); listener.listen()
    def handle(client, peer):
        with client:
            data = client.recv(1024)
            client.sendall(peer[0].encode() + b"|" + data)
    def accept():
        while True:
            client, peer = listener.accept()
            threading.Thread(target=handle, args=(client, peer), daemon=True).start()
    threading.Thread(target=accept, daemon=True).start()
for port in map(int, os.environ["PROBE_PORTS"].split(",")):
    tcp(socket.AF_INET, "0.0.0.0", port)
    if os.environ.get("PROBE_IPV6") == "1":
        tcp(socket.AF_INET6, "::", port)
if os.environ.get("PROBE_UDP"):
    udp = socket.socket(socket.AF_INET, socket.SOCK_DGRAM)
    udp.bind(("0.0.0.0", int(os.environ["PROBE_UDP"])))
    def reply():
        while True:
            data, peer = udp.recvfrom(65535)
            udp.sendto(peer[0].encode() + b"|" + data, peer)
    threading.Thread(target=reply, daemon=True).start()
print("probe ready", flush=True)
signal.pause()
"""


@unittest.skipUnless(os.environ.get("VIPAI86_NETGUARD_DOCKER_TESTS") == "1",
                     "Set VIPAI86_NETGUARD_DOCKER_TESTS=1 for isolated NET_ADMIN Docker tests")
class NetguardDocker(unittest.TestCase):
    def setUp(self):
        self.prefix = "vipai86-ng-test-" + uuid.uuid4().hex[:10]
        self.containers = []
        self.networks = []

    def tearDown(self):
        for name in reversed(self.containers):
            self.docker("rm", "-f", name, check=False)
        for name in reversed(self.networks):
            self.docker("network", "rm", name, check=False)

    def docker(self, *args, check=True):
        result = subprocess.run(["docker", *args], capture_output=True, text=True, timeout=30)
        if check:
            self.assertEqual(result.returncode, 0, result.stdout + result.stderr)
        return result

    def network(self, suffix, *args):
        name = self.prefix + "-" + suffix
        self.docker("network", "create", "--label", "vipai86.netguard-test=" + self.prefix, *args, name)
        self.networks.append(name)
        return name

    def start_probe(self, suffix, network, ports, udp=None, ipv6=False, alias=None):
        name = self.prefix + "-" + suffix
        args = ["run", "-d", "--name", name, "--network", network, "--read-only",
                "--cap-drop", "ALL", "--security-opt", "no-new-privileges", "--no-healthcheck",
                "-e", "PROBE_PORTS=" + ports]
        if alias:
            args += ["--network-alias", alias]
        if udp:
            args += ["-e", "PROBE_UDP=" + str(udp)]
        if ipv6:
            args += ["-e", "PROBE_IPV6=1"]
        args += ["--entrypoint", "python3", "vipai86-game:local", "-u", "-c", RESPONDER]
        self.docker(*args)
        self.containers.append(name)
        deadline = time.monotonic() + 10
        while time.monotonic() < deadline:
            if "probe ready" in self.docker("logs", name).stdout:
                return name
            time.sleep(0.1)
        self.fail("Probe did not start: " + self.docker("logs", name).stderr)

    def address(self, container, network, field="IPAddress"):
        data = json.loads(self.docker("inspect", container).stdout)[0]
        return data["NetworkSettings"]["Networks"][network][field]

    def python(self, container, code, *args):
        return self.docker("exec", container, "python3", "-c", code, *args).stdout.strip()

    def test_scoped_firewall_preserves_player_source_and_blocks_other_egress(self):
        private = self.network("private", "--internal")
        ipv6_subnet = "fd86:" + uuid.uuid4().hex[:4] + ":" + uuid.uuid4().hex[:4] + "::/64"
        public = self.network("public", "--ipv6", "--subnet", ipv6_subnet)
        database = self.start_probe("db", private, "3306,3307", alias="db")
        outside = self.start_probe("outside", public, "3306,8080", ipv6=True)
        outside_ip = self.address(outside, public)
        outside_v6 = self.address(outside, public, "GlobalIPv6Address")
        self.assertTrue(outside_v6)

        guard = self.prefix + "-guard"
        self.docker("create", "--name", guard, "--network", private,
                    "--read-only", "--cap-drop", "ALL", "--cap-add", "NET_ADMIN",
                    "--security-opt", "no-new-privileges",
                    "--tmpfs", "/run/netguard:rw,noexec,nosuid,size=64k",
                    "-e", "XTABLES_LOCKFILE=/run/netguard/xtables.lock",
                    "vipai86-netguard:local", "--dns-refresh-interval", "0.2")
        self.containers.append(guard)
        self.docker("network", "connect", public, guard)
        self.docker("start", guard)
        deadline = time.monotonic() + 15
        while time.monotonic() < deadline:
            health = self.docker("exec", guard, "python3", "-B",
                                 "/usr/local/bin/vipai86-netguard.py", "--healthcheck", check=False)
            if health.returncode == 0:
                break
            time.sleep(0.2)
        else:
            self.fail("Guard did not become healthy: " + self.docker("logs", guard).stderr)
        game = self.start_probe("game", "container:" + guard, "7001,8081", udp=7001)
        guard_ip = self.address(guard, public)

        # Validate responder availability independently before asserting firewall rejection.
        available = r"""
import socket
for host, port in (("127.0.0.1",3306), ("127.0.0.1",8080), ("::1",8080)):
    with socket.create_connection((host,port),2) as client:
        client.sendall(b"available")
        assert client.recv(1024).endswith(b"|available")
print("outside responders available")
"""
        self.assertIn("available", self.python(outside, available))
        db_ip = self.address(database, private)
        allowed_and_blocked = r"""
import socket, sys
db, outside, ipv6 = sys.argv[1:]
for host, port in (("127.0.0.1",7001), (db,3306)):
    with socket.create_connection((host,port),2) as client:
        client.sendall(b"allowed")
        assert client.recv(1024).endswith(b"|allowed")
for host, port in ((db,3307),(outside,3306),(outside,8080),(ipv6,8080)):
    try:
        client = socket.create_connection((host,port),1)
    except OSError:
        continue
    client.close()
    raise AssertionError("Unexpected permitted egress: " + host + ":" + str(port))
caps = next(line for line in open("/proc/self/status") if line.startswith("CapEff:"))
assert int(caps.split()[1],16) == 0, caps
print("loopback/database allowed; unrelated IPv4/IPv6 blocked; game has no capabilities")
"""
        self.assertIn("blocked", self.python(game, allowed_and_blocked, db_ip, outside_ip, outside_v6))
        replacement = self.start_probe("db-replacement", private, "3306", alias="db")
        replacement_ip = self.address(replacement, private)
        self.assertNotEqual(db_ip, replacement_ip)
        self.docker("rm", "-f", database)
        self.containers.remove(database)
        deadline = time.monotonic() + 8
        while time.monotonic() < deadline:
            output = self.docker("exec", guard, "iptables", "--wait", "2", "-S", "OUTPUT").stdout
            if replacement_ip + "/32" in output and db_ip + "/32" not in output:
                break
            time.sleep(0.1)
        else:
            self.fail("DNS replacement did not update the live database allowlist")
        replacement_probe = r"""
import socket, sys
with socket.create_connection(("db",3306),2) as client:
    assert client.getpeername()[0] == sys.argv[1]
    client.sendall(b"database-replaced")
    assert client.recv(1024).endswith(b"|database-replaced")
print("replacement database reachable")
"""
        self.assertIn("reachable", self.python(game, replacement_probe, replacement_ip))
        source_preserved = r"""
import socket, sys
target, own_ip = sys.argv[1:]
with socket.create_connection((target,7001),2) as client:
    client.sendall(b"tcp-player")
    assert client.recv(1024) == own_ip.encode() + b"|tcp-player"
with socket.socket(socket.AF_INET,socket.SOCK_DGRAM) as client:
    client.settimeout(2)
    client.sendto(b"udp-player",(target,7001))
    data, _ = client.recvfrom(1024)
    assert data == own_ip.encode() + b"|udp-player", data
try:
    client = socket.create_connection((target,8081),0.5)
except OSError:
    pass
else:
    client.close()
    raise AssertionError("Unlisted inbound port accepted")
print("TCP/UDP original player source preserved; inbound replies allowed; unlisted INPUT blocked")
"""
        self.assertIn("source preserved", self.python(outside, source_preserved, guard_ip, outside_ip))
        for binary in ("iptables", "ip6tables"):
            forward = self.docker("exec", guard, binary, "--wait", "2", "-S", "FORWARD").stdout
            self.assertEqual(forward.strip(), "-P FORWARD DROP")
        self.docker("stop", "--time", "5", guard)
        state = json.loads(self.docker("inspect", guard).stdout)[0]["State"]
        self.assertEqual(state["ExitCode"], 0)
        self.assertIn("fail-closed rules retained", self.docker("logs", guard).stderr)


if __name__ == "__main__":
    unittest.main()

