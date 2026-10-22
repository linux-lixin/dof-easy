#!/usr/bin/env python3
"""Bounded TCP/UDP ingress for the otherwise isolated game and admin networks."""
import argparse
import asyncio
import contextlib
import logging
import signal
import socket
import time
from collections import deque
from dataclasses import dataclass

LOG = logging.getLogger("vipai86.ingress")
DEFAULT_TCP = [(7001, "game", 7001), (7300, "game", 7300),
               (30015, "game", 30015), (30052, "game", 30052),
               (18080, "admin", 80)]
DEFAULT_UDP = [(port, "game", port) for port in (7001, 7300, 31015, 31052, 2311, 2312, 2313)]


@dataclass(frozen=True)
class Route:
    listen_port: int
    target_host: str
    target_port: int


async def close_writer(writer):
    writer.close()
    with contextlib.suppress(Exception):
        await asyncio.wait_for(writer.wait_closed(), timeout=1)


class TcpForwarder:
    def __init__(self, route, listen_host="0.0.0.0", max_clients=1024, connect_timeout=10):
        self.route = route
        self.listen_host = listen_host
        self.max_clients = max_clients
        self.connect_timeout = connect_timeout
        self.server = None
        self.tasks = set()

    @property
    def port(self):
        return self.server.sockets[0].getsockname()[1]

    async def start(self):
        self.server = await asyncio.start_server(self._accept, self.listen_host, self.route.listen_port)
        LOG.info("TCP %s:%s -> %s:%s", self.listen_host, self.port,
                 self.route.target_host, self.route.target_port)

    def _accept(self, reader, writer):
        if len(self.tasks) >= self.max_clients:
            writer.close()
            return
        task = asyncio.create_task(self._forward(reader, writer))
        self.tasks.add(task)
        task.add_done_callback(self.tasks.discard)

    @staticmethod
    async def _relay(reader, writer):
        while True:
            data = await reader.read(65536)
            if not data:
                # Keep the reverse stream alive: requests can finish before responses begin.
                if writer.can_write_eof():
                    writer.write_eof()
                    await writer.drain()
                return
            writer.write(data)
            await writer.drain()

    async def _forward(self, client_reader, client_writer):
        upstream_writer = None
        relays = []
        try:
            upstream_reader, upstream_writer = await asyncio.wait_for(
                asyncio.open_connection(self.route.target_host, self.route.target_port),
                self.connect_timeout,
            )
            relays = [
                asyncio.create_task(self._relay(client_reader, upstream_writer)),
                asyncio.create_task(self._relay(upstream_reader, client_writer)),
            ]
            await asyncio.gather(*relays)
        except (OSError, asyncio.TimeoutError) as exc:
            LOG.debug("TCP connection ended: %s", type(exc).__name__)
        except Exception:
            LOG.exception("TCP connection failed")
        finally:
            for task in relays:
                if not task.done():
                    task.cancel()
            if relays:
                await asyncio.gather(*relays, return_exceptions=True)
            await close_writer(client_writer)
            if upstream_writer is not None:
                await close_writer(upstream_writer)

    async def close(self):
        if self.server is not None:
            self.server.close()
        tasks = list(self.tasks)
        for task in tasks:
            task.cancel()
        if tasks:
            await asyncio.gather(*tasks, return_exceptions=True)
        # Python 3.12+ waits for active client transports here, so close those first.
        if self.server is not None:
            await self.server.wait_closed()


class _UdpSession:
    def __init__(self, client):
        self.client = client
        self.last_activity = time.monotonic()
        self.pending = deque()
        self.transport = None
        self.task = None
        self.closed = False


class _UdpReply(asyncio.DatagramProtocol):
    def __init__(self, owner, session):
        self.owner = owner
        self.session = session

    def datagram_received(self, data, address):
        if not self.session.closed and not self.owner.closing:
            self.session.last_activity = time.monotonic()
            self.owner.transport.sendto(data, self.session.client)

    def error_received(self, exc):
        self.owner.drop(self.session)

    def connection_lost(self, exc):
        self.owner.drop(self.session)


class UdpForwarder(asyncio.DatagramProtocol):
    def __init__(self, route, listen_host="0.0.0.0", idle_timeout=120,
                 max_clients=1024, pending_limit=4, dns_refresh_interval=30):
        if idle_timeout <= 0 or max_clients < 1 or pending_limit < 1 or dns_refresh_interval <= 0:
            raise ValueError("UDP limits must be positive")
        self.route = route
        self.listen_host = listen_host
        self.idle_timeout = idle_timeout
        self.max_clients = max_clients
        self.pending_limit = pending_limit
        self.dns_refresh_interval = dns_refresh_interval
        self.sessions = {}
        self.transport = None
        self.reaper = None
        self.dns_refresher = None
        self.creation_tasks = set()
        self.closing = False
        self.destination = None
        self.family = None

    @property
    def port(self):
        return self.transport.get_extra_info("sockname")[1]

    async def start(self):
        loop = asyncio.get_running_loop()
        self.family, self.destination = await self._resolve_destination()
        self.transport, _ = await loop.create_datagram_endpoint(
            lambda: self, local_addr=(self.listen_host, self.route.listen_port))
        self.reaper = asyncio.create_task(self._expire())
        self.dns_refresher = asyncio.create_task(self._refresh_dns())
        LOG.info("UDP %s:%s -> %s:%s", self.listen_host, self.port,
                 self.route.target_host, self.route.target_port)

    async def _resolve_destination(self):
        addresses = await asyncio.wait_for(
            asyncio.get_running_loop().getaddrinfo(
                self.route.target_host, self.route.target_port, type=socket.SOCK_DGRAM),
            timeout=5)
        family, _, _, _, address = addresses[0]
        return family, address

    async def _refresh_dns(self):
        # A completed resolver can race with cancellation (notably wait_for on
        # older Python). The explicit close state prevents restarting the loop.
        while not self.closing:
            await asyncio.sleep(self.dns_refresh_interval)
            try:
                family, destination = await self._resolve_destination()
            except (OSError, asyncio.TimeoutError):
                # Keep healthy mappings when Docker DNS is temporarily unavailable.
                LOG.debug("UDP target DNS refresh failed; retaining current destination")
                continue
            if (family, destination) != (self.family, self.destination):
                self.family, self.destination = family, destination
                for session in list(self.sessions.values()):
                    self.drop(session)
                LOG.info("UDP target changed for port %s; refreshed client mappings", self.port)

    def datagram_received(self, data, address):
        if self.closing:
            return
        session = self.sessions.get(address)
        if session is None:
            # Reject excess clients instead of evicting an active player's mapping.
            if len(self.sessions) >= self.max_clients:
                return
            session = _UdpSession(address)
            self.sessions[address] = session
            session.task = asyncio.create_task(self._connect(session))
            self.creation_tasks.add(session.task)
            session.task.add_done_callback(self.creation_tasks.discard)
        session.last_activity = time.monotonic()
        if session.transport is not None:
            try:
                session.transport.sendto(data)
            except (OSError, RuntimeError):
                self.drop(session)
        elif len(session.pending) < self.pending_limit:
            session.pending.append(data)

    async def _connect(self, session):
        transport = None
        try:
            # A separate connected socket per client preserves reply isolation and source ports.
            transport, _ = await asyncio.get_running_loop().create_datagram_endpoint(
                lambda: _UdpReply(self, session), remote_addr=self.destination,
                family=self.family)
            if session.closed or self.closing:
                transport.close()
                return
            session.transport = transport
            while session.pending:
                transport.sendto(session.pending.popleft())
        except asyncio.CancelledError:
            if transport is not None:
                transport.close()
            raise
        except Exception as exc:
            LOG.debug("UDP mapping failed: %s", type(exc).__name__)
            if transport is not None:
                transport.close()
            self.drop(session)

    def drop(self, session):
        if session.closed:
            return
        session.closed = True
        if self.sessions.get(session.client) is session:
            del self.sessions[session.client]
        session.pending.clear()
        if session.transport is not None:
            session.transport.close()
        # This method can also run from the creation task's own failure handler.
        if session.task is not None and session.task is not asyncio.current_task() and not session.task.done():
            session.task.cancel()

    async def _expire(self):
        while True:
            await asyncio.sleep(min(self.idle_timeout / 2, 30))
            cutoff = time.monotonic() - self.idle_timeout
            for session in list(self.sessions.values()):
                if session.last_activity <= cutoff:
                    self.drop(session)

    def error_received(self, exc):
        LOG.debug("UDP listener error: %s", type(exc).__name__)

    async def close(self):
        self.closing = True
        if self.transport is not None:
            self.transport.close()
        for session in list(self.sessions.values()):
            self.drop(session)
        tasks = list(self.creation_tasks)
        if self.reaper is not None:
            self.reaper.cancel()
            tasks.append(self.reaper)
        if self.dns_refresher is not None:
            self.dns_refresher.cancel()
            tasks.append(self.dns_refresher)
        if tasks:
            await asyncio.gather(*tasks, return_exceptions=True)
        await asyncio.sleep(0)


class Gateway:
    def __init__(self, tcp_routes, udp_routes, listen_host="0.0.0.0",
                 udp_idle_timeout=120, max_udp_clients=1024, max_tcp_clients=1024,
                 udp_dns_refresh_interval=30):
        self.forwarders = [
            TcpForwarder(route, listen_host, max_tcp_clients) for route in tcp_routes
        ] + [
            UdpForwarder(route, listen_host, udp_idle_timeout, max_udp_clients,
                         dns_refresh_interval=udp_dns_refresh_interval) for route in udp_routes
        ]

    async def start(self):
        try:
            for forwarder in self.forwarders:
                await forwarder.start()
        except BaseException:
            await self.close()
            raise

    async def close(self):
        await asyncio.gather(*(forwarder.close() for forwarder in self.forwarders),
                             return_exceptions=False)


def parse_route(value):
    try:
        local, remainder = value.split(":", 1)
        host, target = remainder.rsplit(":", 1)
        route = Route(int(local), host.strip("[]"), int(target))
        if not (0 <= route.listen_port <= 65535 and 1 <= route.target_port <= 65535 and route.target_host):
            raise ValueError()
        return route
    except ValueError as exc:
        raise argparse.ArgumentTypeError("expected LISTEN_PORT:TARGET_HOST:TARGET_PORT") from exc


def positive_int(value):
    result = int(value)
    if result < 1:
        raise argparse.ArgumentTypeError("must be positive")
    return result


def positive_float(value):
    result = float(value)
    if not (0 < result < float("inf")):
        raise argparse.ArgumentTypeError("must be finite and positive")
    return result


async def serve(args):
    gateway = Gateway(
        [] if args.no_tcp else args.tcp or [Route(*route) for route in DEFAULT_TCP],
        [] if args.no_udp else args.udp or [Route(*route) for route in DEFAULT_UDP],
        args.listen_host, args.udp_idle_timeout, args.max_udp_clients, args.max_tcp_clients,
        args.udp_dns_refresh_interval)
    stop = asyncio.Event()
    loop = asyncio.get_running_loop()
    for name in ("SIGINT", "SIGTERM"):
        sig = getattr(signal, name)
        try:
            loop.add_signal_handler(sig, stop.set)
        except NotImplementedError:  # Windows test/dev hosts.
            signal.signal(sig, lambda *_: loop.call_soon_threadsafe(stop.set))
    try:
        await gateway.start()
        LOG.info("Ingress ready")
        await stop.wait()
    finally:
        await gateway.close()
        LOG.info("Ingress stopped")


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--listen-host", default="0.0.0.0")
    parser.add_argument("--tcp", type=parse_route, action="append",
                        help="replace default TCP routes; repeat LISTEN_PORT:TARGET_HOST:TARGET_PORT")
    parser.add_argument("--udp", type=parse_route, action="append",
                        help="replace default UDP routes; repeat LISTEN_PORT:TARGET_HOST:TARGET_PORT")
    parser.add_argument("--no-tcp", action="store_true")
    parser.add_argument("--no-udp", action="store_true")
    parser.add_argument("--udp-idle-timeout", type=positive_float, default=120)
    parser.add_argument("--udp-dns-refresh-interval", type=positive_float, default=30)
    parser.add_argument("--max-udp-clients", type=positive_int, default=1024,
                        help="maximum mappings per UDP listening port")
    parser.add_argument("--max-tcp-clients", type=positive_int, default=1024,
                        help="maximum connections per TCP listening port")
    args = parser.parse_args()
    logging.basicConfig(level=logging.INFO, format="%(asctime)s %(levelname)s %(message)s")
    try:
        asyncio.run(serve(args))
    except (OSError, ValueError) as exc:
        LOG.error("Ingress startup failed: %s", exc)
        return 1
    return 0


if __name__ == "__main__":
    raise SystemExit(main())

