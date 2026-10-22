"""Real loopback tests for the independent standard-library ingress."""
import asyncio
import importlib.util
import os
import pathlib
import signal
import socket
import subprocess
import sys
import time
import unittest
from unittest.mock import AsyncMock, patch

ROOT = pathlib.Path(__file__).resolve().parents[2]
SCRIPT = ROOT / "deploy/vipai86/ingress.py"
SPEC = importlib.util.spec_from_file_location("vipai86_ingress", SCRIPT)
ingress = importlib.util.module_from_spec(SPEC)
sys.modules[SPEC.name] = ingress
SPEC.loader.exec_module(ingress)


class UdpEcho(asyncio.DatagramProtocol):
    def __init__(self):
        self.seen = []
        self.transport = None

    def connection_made(self, transport):
        self.transport = transport

    def datagram_received(self, data, address):
        self.seen.append((data, address))
        self.transport.sendto(b"echo:" + data, address)


class UdpClient(asyncio.DatagramProtocol):
    def __init__(self):
        self.responses = asyncio.Queue()
        self.transport = None

    def connection_made(self, transport):
        self.transport = transport

    def datagram_received(self, data, address):
        self.responses.put_nowait(data)

    async def receive(self, timeout=2):
        return await asyncio.wait_for(self.responses.get(), timeout)


class IngressLoopback(unittest.IsolatedAsyncioTestCase):
    async def asyncSetUp(self):
        self.gateways = []
        self.servers = []
        self.transports = []
        self.writers = set()
        self.handlers = set()
        self.raw_sockets = []

    async def asyncTearDown(self):
        for gateway in self.gateways:
            await asyncio.wait_for(gateway.close(), 2)
        for transport in self.transports:
            transport.close()
        for sock in self.raw_sockets:
            sock.close()
        for server in self.servers:
            server.close()
        for writer in list(self.writers):
            writer.close()
        tasks = list(self.handlers)
        for task in tasks:
            task.cancel()
        if tasks:
            await asyncio.gather(*tasks, return_exceptions=True)
        for server in self.servers:
            await asyncio.wait_for(server.wait_closed(), 2)
        await asyncio.sleep(0.02)

    async def tcp_server(self, handler):
        async def tracked(reader, writer):
            task = asyncio.current_task()
            self.handlers.add(task)
            self.writers.add(writer)
            try:
                await handler(reader, writer)
            finally:
                self.writers.discard(writer)
                self.handlers.discard(task)
                await ingress.close_writer(writer)
        server = await asyncio.start_server(tracked, "127.0.0.1", 0)
        self.servers.append(server)
        return server.sockets[0].getsockname()[1]

    async def gateway(self, tcp=(), udp=(), **kwargs):
        gateway = ingress.Gateway(
            [ingress.Route(0, "127.0.0.1", port) for port in tcp],
            [ingress.Route(0, "127.0.0.1", port) for port in udp],
            listen_host="127.0.0.1", **kwargs)
        self.gateways.append(gateway)
        await gateway.start()
        return gateway

    async def udp_server(self):
        transport, protocol = await asyncio.get_running_loop().create_datagram_endpoint(
            UdpEcho, local_addr=("127.0.0.1", 0))
        self.transports.append(transport)
        return transport.get_extra_info("sockname")[1], protocol

    async def udp_client(self, port):
        transport, protocol = await asyncio.get_running_loop().create_datagram_endpoint(
            UdpClient, remote_addr=("127.0.0.1", port))
        self.transports.append(transport)
        return protocol

    async def test_tcp_streams_large_payload_and_half_close_response(self):
        async def response_after_fin(reader, writer):
            request = await reader.read()
            writer.write(b"response-after-fin:" + request)
            await writer.drain()
        target = await self.tcp_server(response_after_fin)
        proxy = (await self.gateway(tcp=[target])).forwarders[0]
        reader, writer = await asyncio.open_connection("127.0.0.1", proxy.port)
        try:
            payload = bytes(range(256)) * 1024
            writer.write(payload)
            await writer.drain()
            writer.write_eof()
            response = await asyncio.wait_for(reader.read(), 3)
            self.assertEqual(response, b"response-after-fin:" + payload)
        finally:
            await ingress.close_writer(writer)

    async def test_tcp_reverse_half_close_keeps_upload_alive(self):
        received = asyncio.Future()

        async def upstream_fin_first(reader, writer):
            writer.write(b"server-finished")
            await writer.drain()
            writer.write_eof()
            received.set_result(await reader.read())

        target = await self.tcp_server(upstream_fin_first)
        proxy = (await self.gateway(tcp=[target])).forwarders[0]
        reader, writer = await asyncio.open_connection("127.0.0.1", proxy.port)
        try:
            self.assertEqual(await asyncio.wait_for(reader.read(), 2), b"server-finished")
            writer.write(b"client-upload-after-server-fin")
            await writer.drain()
            writer.write_eof()
            self.assertEqual(await asyncio.wait_for(received, 2), b"client-upload-after-server-fin")
        finally:
            await ingress.close_writer(writer)

    async def test_tcp_upstream_failure_does_not_stop_other_routes(self):
        async def echo(reader, writer):
            while True:
                data = await reader.read(65536)
                if not data:
                    return
                writer.write(data)
                await writer.drain()
        target = await self.tcp_server(echo)
        # Reserve a bound, non-listening port so the proxy cannot reuse it and connect to itself.
        unavailable = socket.socket(socket.AF_INET, socket.SOCK_STREAM)
        unavailable.bind(("127.0.0.1", 0))
        self.raw_sockets.append(unavailable)
        unused_port = unavailable.getsockname()[1]
        gateway = await self.gateway(tcp=[unused_port, target])
        gateway.forwarders[0].connect_timeout = 0.1
        failed_reader, failed_writer = await asyncio.open_connection("127.0.0.1", gateway.forwarders[0].port)
        try:
            self.assertEqual(await asyncio.wait_for(failed_reader.read(), 2), b"")
        finally:
            await ingress.close_writer(failed_writer)
        reader, writer = await asyncio.open_connection("127.0.0.1", gateway.forwarders[1].port)
        try:
            writer.write(b"healthy")
            await writer.drain()
            self.assertEqual(await asyncio.wait_for(reader.readexactly(7), 2), b"healthy")
        finally:
            await ingress.close_writer(writer)

    async def test_udp_clients_have_separate_connected_sockets_and_replies(self):
        target, echo = await self.udp_server()
        proxy = (await self.gateway(udp=[target])).forwarders[0]
        first = await self.udp_client(proxy.port)
        second = await self.udp_client(proxy.port)
        first.transport.sendto(b"client-A")
        second.transport.sendto(b"client-B")
        self.assertEqual(await first.receive(), b"echo:client-A")
        self.assertEqual(await second.receive(), b"echo:client-B")
        peers = {data: address for data, address in echo.seen}
        self.assertNotEqual(peers[b"client-A"], peers[b"client-B"])
        first.transport.sendto(b"client-A-again")
        self.assertEqual(await first.receive(), b"echo:client-A-again")
        self.assertEqual(echo.seen[-1][1], peers[b"client-A"])
        self.assertTrue(second.responses.empty())

    async def test_udp_mapping_limit_and_idle_expiry_release_slots(self):
        target, echo = await self.udp_server()
        proxy = (await self.gateway(udp=[target], max_udp_clients=1, udp_idle_timeout=0.15)).forwarders[0]
        first = await self.udp_client(proxy.port)
        second = await self.udp_client(proxy.port)
        first.transport.sendto(b"occupy")
        self.assertEqual(await first.receive(), b"echo:occupy")
        second.transport.sendto(b"over-limit")
        with self.assertRaises(asyncio.TimeoutError):
            await second.receive(timeout=0.04)
        self.assertEqual(len(proxy.sessions), 1)
        self.assertNotIn(b"over-limit", [data for data, _ in echo.seen])
        await asyncio.sleep(0.25)
        self.assertEqual(len(proxy.sessions), 0)
        second.transport.sendto(b"after-expiry")
        self.assertEqual(await second.receive(), b"echo:after-expiry")

    async def test_shutdown_closes_active_tcp_and_udp_resources(self):
        async def hold(reader, writer):
            await reader.read()
        tcp_target = await self.tcp_server(hold)
        udp_target, _ = await self.udp_server()
        gateway = await self.gateway(tcp=[tcp_target], udp=[udp_target])
        reader, writer = await asyncio.open_connection("127.0.0.1", gateway.forwarders[0].port)
        client = await self.udp_client(gateway.forwarders[1].port)
        client.transport.sendto(b"active")
        self.assertEqual(await client.receive(), b"echo:active")
        await asyncio.wait_for(gateway.close(), 2)
        self.assertEqual(await asyncio.wait_for(reader.read(), 2), b"")
        self.assertFalse(gateway.forwarders[0].tasks)
        self.assertFalse(gateway.forwarders[1].sessions)
        self.assertFalse(gateway.forwarders[1].creation_tasks)
        self.assertTrue(gateway.forwarders[1].transport.is_closing())
        await ingress.close_writer(writer)

    async def test_udp_initial_burst_is_forwarded_in_order(self):
        target, _ = await self.udp_server()
        proxy = (await self.gateway(udp=[target])).forwarders[0]
        client = await self.udp_client(proxy.port)
        for message in (b"one", b"two", b"three"):
            client.transport.sendto(message)
        self.assertEqual([await client.receive() for _ in range(3)],
                         [b"echo:one", b"echo:two", b"echo:three"])

    async def test_shutdown_when_dns_completion_races_cancellation(self):
        target, _ = await self.udp_server()
        gateway = await self.gateway(udp=[target], udp_dns_refresh_interval=0.01)
        proxy = gateway.forwarders[0]
        entered = asyncio.Event()

        async def resolve_at_cancellation():
            entered.set()
            try:
                await asyncio.Event().wait()
            except asyncio.CancelledError:
                return proxy.family, proxy.destination

        with patch.object(proxy, "_resolve_destination", resolve_at_cancellation):
            await asyncio.wait_for(entered.wait(), 1)
            await asyncio.wait_for(gateway.close(), 2)
        self.assertTrue(proxy.dns_refresher.done())

    async def test_udp_dns_change_replaces_active_mappings_and_recovers(self):
        first_target, first_echo = await self.udp_server()
        second_target, second_echo = await self.udp_server()
        proxy = (await self.gateway(udp=[first_target], udp_dns_refresh_interval=0.03)).forwarders[0]
        client = await self.udp_client(proxy.port)
        client.transport.sendto(b"original-target")
        self.assertEqual(await client.receive(), b"echo:original-target")
        old_session = next(iter(proxy.sessions.values()))
        new_destination = (socket.AF_INET, ("127.0.0.1", second_target))
        with patch.object(proxy, "_resolve_destination", AsyncMock(return_value=new_destination)):
            await asyncio.sleep(0.08)
            self.assertTrue(old_session.closed)
            client.transport.sendto(b"replacement-target")
            self.assertEqual(await client.receive(), b"echo:replacement-target")
        self.assertEqual([data for data, _ in first_echo.seen], [b"original-target"])
        self.assertEqual([data for data, _ in second_echo.seen], [b"replacement-target"])

    async def test_udp_dns_failure_keeps_working_mappings_and_retries(self):
        target, echo = await self.udp_server()
        proxy = (await self.gateway(udp=[target], udp_dns_refresh_interval=0.03)).forwarders[0]
        client = await self.udp_client(proxy.port)
        client.transport.sendto(b"before-dns-failure")
        self.assertEqual(await client.receive(), b"echo:before-dns-failure")
        session = next(iter(proxy.sessions.values()))
        with patch.object(proxy, "_resolve_destination", AsyncMock(side_effect=OSError("temporary DNS failure"))) as resolver:
            await asyncio.sleep(0.08)
            client.transport.sendto(b"during-dns-failure")
            self.assertEqual(await client.receive(), b"echo:during-dns-failure")
            self.assertGreaterEqual(resolver.await_count, 2)
        self.assertIs(next(iter(proxy.sessions.values())), session)
        self.assertEqual(len(echo.seen), 2)


class IngressCli(unittest.TestCase):
    def test_default_routes_match_required_ingress_ports(self):
        self.assertEqual(ingress.DEFAULT_TCP, [
            (7001, "game", 7001), (7300, "game", 7300), (30015, "game", 30015),
            (30052, "game", 30052), (18080, "admin", 80)])
        self.assertEqual([route[0] for route in ingress.DEFAULT_UDP],
                         [7001, 7300, 31015, 31052, 2311, 2312, 2313])
        self.assertEqual(ingress.parse_route("0:127.0.0.1:8080"), ingress.Route(0, "127.0.0.1", 8080))
        self.assertEqual(ingress.parse_route("7001:[::1]:7002"), ingress.Route(7001, "::1", 7002))

    @unittest.skipIf(os.name == "nt", "POSIX SIGTERM semantics required")
    def test_sigterm_exits_cleanly(self):
        process = subprocess.Popen(
            [sys.executable, str(SCRIPT), "--listen-host", "127.0.0.1",
             "--tcp", "0:127.0.0.1:9", "--no-udp"],
            stdout=subprocess.PIPE, stderr=subprocess.PIPE)
        try:
            deadline = time.monotonic() + 5
            ready = False
            startup_output = b""
            # Logging emits readiness only after the socket has been bound.
            import select
            while time.monotonic() < deadline:
                if select.select([process.stderr], [], [], 0.1)[0]:
                    startup_output += os.read(process.stderr.fileno(), 4096)
                    if b"Ingress ready" in startup_output:
                        ready = True
                        break
            self.assertTrue(ready, "ingress did not become ready")
            process.send_signal(signal.SIGTERM)
            stdout, stderr = process.communicate(timeout=5)
            self.assertEqual(process.returncode, 0, stdout + stderr)
            self.assertIn(b"Ingress stopped", stderr)
        finally:
            if process.poll() is None:
                process.kill()
                process.wait(timeout=5)
            process.stdout.close()
            process.stderr.close()


if __name__ == "__main__":
    unittest.main()

