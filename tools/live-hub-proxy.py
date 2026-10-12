"""Temporary loopback-only GET bridge. Real credentials remain in upstream process memory.

For device integration tests only; not part of the application or a deployment.
No request/response logging, credentials, or private response files.
"""
import http.server
import http.client
import json
import os
from pathlib import Path
import select
import shlex
import socket
import stat
import subprocess
import sys
import threading
import time
from urllib.parse import urlsplit

ALLOWED = {"health", "stats", "stats/stream", "devices", "history", "subscriptions", "sync/content", "sync/settings/modelAliases", "sync/settings/customPricing"}


def read_private_json(path):
    """Read an existing owner file without migration, chmod, symlinks or logging."""
    fd = os.open(path, os.O_RDONLY | os.O_NOFOLLOW)
    with os.fdopen(fd, "rb") as source:
        info = os.fstat(source.fileno())
        if not stat.S_ISREG(info.st_mode) or info.st_uid != os.getuid() or info.st_size > 1024 * 1024:
            raise ValueError("Configured document is unavailable")
        raw = source.read(1024 * 1024 + 1)
        if len(raw) > 1024 * 1024:
            raise ValueError("Configured document is unavailable")
        return json.loads(raw)


def desktop_target(root):
    """Only the explicitly authorized NAS LAN root; no redirects or proxy use."""
    target = urlsplit(root)
    if (target.scheme != "http" or target.hostname != "192.168.31.12" or
            target.username is not None or target.password is not None or
            target.query or target.fragment or target.path not in ("", "/")):
        raise ValueError("Configured target is outside the authorized NAS")
    return target.hostname, target.port or 80


def load_desktop_connection():
    support = Path.home() / "Library/Application Support"
    for name in ("Token Monitor", "token-monitor", "TokenMonitor"):
        settings = support / name / "settings.json"
        if settings.exists():
            config = read_private_json(settings)
            host, port = desktop_target(config.get("hubUrl", ""))
            secrets = read_private_json(settings.with_name("credentials.json"))
            credential = secrets.get("credentials", {}).get("hub", {}).get("clientSecret")
            if not isinstance(credential, str) or not credential or "\r" in credential or "\n" in credential:
                raise ValueError("Configured authentication is unavailable")
            return host, port, credential
    raise ValueError("Configured desktop connection is unavailable")


def desktop_read(route):
    """Child transport: GET only, stdin EOF immediately closes the actual NAS socket."""
    if route not in ALLOWED:
        raise ValueError("Read route unavailable")
    host, port, credential = load_desktop_connection()
    connection = http.client.HTTPConnection(host, port, timeout=45)
    stopped = threading.Event()
    transport = [None]

    def stop():
        stopped.set()
        peer = transport[0]
        if peer is not None:
            try:
                peer.shutdown(socket.SHUT_RDWR)
            except OSError:
                pass
        connection.close()

    def watch_stdin():
        sys.stdin.buffer.read()
        stop()

    threading.Thread(target=watch_stdin, daemon=True).start()
    deadline = threading.Timer(180 if route == "stats/stream" else 45, stop)
    deadline.daemon = True
    deadline.start()
    try:
        headers = {"x-token-monitor-stream": "2"}
        if route != "health":
            headers["Authorization"] = "Bearer " + credential
        connection.connect()
        transport[0] = connection.sock
        if stopped.is_set():
            return
        connection.request("GET", "/api/" + route, headers=headers)
        response = connection.getresponse()
        # HTTPConnection never follows a redirect or consults system proxies.
        metadata = {"status": response.status, "type": (response.getheader("Content-Type") or "application/octet-stream")[:256]}
        sys.stdout.buffer.write((json.dumps(metadata) + "\n").encode())
        sys.stdout.buffer.flush()
        total = 0
        while not stopped.is_set():
            chunk = response.read1(65536)
            if not chunk:
                break
            total += len(chunk)
            if route != "stats/stream" and total > 8 * 1024 * 1024:
                raise ValueError("Read exceeds the application's response bound")
            sys.stdout.buffer.write(chunk)
            sys.stdout.buffer.flush()
    finally:
        deadline.cancel()
        stop()


def open_desktop_read(route):
    # The child reads the existing desktop configuration itself. No credential,
    # private address, response body or config contents enter argv or temp files.
    return subprocess.Popen([sys.executable, "-u", str(Path(__file__).resolve()), "--desktop-read", route],
                            stdin=subprocess.PIPE, stdout=subprocess.PIPE, stderr=subprocess.DEVNULL)


def open_nas_read(route):
    script = """const route = ROUTE;
const controller = new AbortController();
setTimeout(() => controller.abort(), route === 'stats/stream' ? 180000 : 45000).unref();
process.stdin.on('end', () => controller.abort());
process.stdin.resume();
process.stdout.on('error', () => { controller.abort(); process.exit(0); });
(async () => {
  const headers = {'x-token-monitor-stream': '2'};
  if (route !== 'health') {
    if (!process.env.TOKEN_MONITOR_SECRET) throw new Error('configured credential unavailable');
    headers.Authorization = 'Bearer ' + process.env.TOKEN_MONITOR_SECRET;
  }
  const response = await fetch('http://127.0.0.1:17321/api/' + route, {headers, signal: controller.signal});
  process.stdout.write(JSON.stringify({status: response.status, type: response.headers.get('content-type')}) + '\\n');
  for await (const chunk of response.body) {
    if (!process.stdout.write(chunk)) await new Promise(resolve => process.stdout.once('drain', resolve));
  }
})().catch(() => process.exitCode = 1).finally(() => process.stdin.destroy());
""".replace("ROUTE", json.dumps(route))
    command = "docker exec -i token-monitor-hub node -e " + shlex.quote(script)
    return subprocess.Popen(["ssh", "-o", "BatchMode=yes", "-o", "ConnectTimeout=8", "BING@192.168.31.12", command],
                            stdin=subprocess.PIPE, stdout=subprocess.PIPE, stderr=subprocess.DEVNULL)


class ReadBridgeServer(http.server.ThreadingHTTPServer):
    daemon_threads = True

    def __init__(self, address, open_read=open_nas_read):
        super().__init__(address, ReadBridge)
        self.open_read = open_read
        self.reads_lock = threading.Lock()
        self.reads_condition = threading.Condition(self.reads_lock)
        self.reads = {}
        self.route_requests = {}
        self.route_attempts = {}
        # Monotonic offset in seconds for each accepted route request; count-only evidence
        # cannot tell a four-second receipt retry from a duplicate dispatch.
        self.route_times = {}
        self.history_events = []
        self.started_monotonic = time.monotonic()
        self.requests = self.stream_starts = self.stream_ends = self.heartbeats = 0

    def status(self):
        with self.reads_lock:
            return {"requests": self.requests, "activeReads": len(self.reads),
                    "streamStarts": self.stream_starts, "streamEnds": self.stream_ends,
                    "activeStreams": sum(route == "stats/stream" for route, _ in self.reads.values()),
                    "heartbeats": self.heartbeats, "routes": dict(self.route_requests), "attempts": dict(self.route_attempts),
                    "readTimes": {route: list(times) for route, times in self.route_times.items()},
                    "historyEvents": [dict(event) for event in self.history_events]}

    def close_reads(self):
        with self.reads_lock:
            stops = [stop for _, stop in self.reads.values()]
        for stop in stops:
            stop()
        with self.reads_condition:
            self.reads_condition.wait_for(lambda: not self.reads, timeout=3)


class ReadBridge(http.server.BaseHTTPRequestHandler):
    def log_message(self, *args):
        pass

    def do_GET(self):
        route = self.path.removeprefix("/api/")
        if not self.path.startswith("/api/") or route not in ALLOWED:
            self.send_error(404)
            return
        with self.server.reads_lock:
            self.server.route_attempts[route] = self.server.route_attempts.get(route, 0) + 1
        process = self.server.open_read(route)
        shutdown_lock = threading.Lock()
        stopping = False

        def stop_read():
            nonlocal stopping
            with shutdown_lock:
                if stopping:
                    return
                stopping = True
                # A lost transport can leave the handler blocked writing to the abandoned peer.
                try:
                    self.connection.shutdown(socket.SHUT_RDWR)
                except OSError:
                    pass
                # Closing stdin aborts fetch inside NAS before waiting for the next heartbeat.
                if process.stdin is not None:
                    try:
                        process.stdin.close()
                    except OSError:
                        pass
                try:
                    process.wait(timeout=2)
                except subprocess.TimeoutExpired:
                    process.terminate()
                    try:
                        process.wait(timeout=2)
                    except subprocess.TimeoutExpired:
                        process.kill(); process.wait(timeout=2)

        with self.server.reads_lock:
            self.server.requests += 1
            self.server.route_requests[route] = self.server.route_requests.get(route, 0) + 1
            self.server.route_times.setdefault(route, []).append(round(time.monotonic() - self.server.started_monotonic, 3))
            if route == "stats/stream":
                self.server.stream_starts += 1
            self.server.reads[id(process)] = (route, stop_read)
            history_sequence = self.server.route_requests.get("history", 0)
            if route == "history":
                self.server.history_events.append({"sequence": history_sequence, "phase": "STARTED",
                    "atNs": time.monotonic_ns(),
                    "active": sum(value[0] == "history" for value in self.server.reads.values())})
        watcher_stop = threading.Event()

        def watch_peer():
            while not watcher_stop.is_set():
                try:
                    readable, _, _ = select.select([self.connection], [], [], 0.1)
                    if readable:
                        # One GET per connection; EOF or unexpected pipelining ends the upstream read.
                        self.connection.recv(1, socket.MSG_PEEK)
                        stop_read()
                        return
                except (OSError, ValueError):
                    stop_read()
                    return

        watcher = threading.Thread(target=watch_peer, daemon=True)
        watcher.start()
        try:
            header = process.stdout.readline(4096)
            metadata = json.loads(header)
            self.send_response(metadata["status"])
            self.send_header("Content-Type", metadata.get("type") or "application/octet-stream")
            self.send_header("Connection", "close")
            self.end_headers()
            tail = b""
            while True:
                chunk = process.stdout.read1(65536)
                if not chunk:
                    break
                if route == "stats/stream":
                    framed = tail + chunk
                    count = framed.count(b": hb\n\n")
                    tail = framed[-5:]
                    if count:
                        with self.server.reads_lock:
                            self.server.heartbeats += count
                self.wfile.write(chunk)
                self.wfile.flush()
        except (OSError, json.JSONDecodeError):
            pass
        finally:
            watcher_stop.set()
            stop_read()
            watcher.join(timeout=1)
            process.stdout.close()
            with self.server.reads_lock:
                self.server.reads.pop(id(process), None)
                if route == "history":
                    self.server.history_events.append({"sequence": history_sequence, "phase": "ENDED",
                        "atNs": time.monotonic_ns(),
                        "active": sum(value[0] == "history" for value in self.server.reads.values())})
                if route == "stats/stream":
                    self.server.stream_ends += 1
                self.server.reads_condition.notify_all()


if __name__ == "__main__":
    if len(sys.argv) == 3 and sys.argv[1] == "--desktop-read":
        try:
            desktop_read(sys.argv[2])
        except BaseException:
            sys.exit(1)
        sys.exit(0)
    server = ReadBridgeServer(("127.0.0.1", 18174))
    print("Loopback read bridge ready; credentials remain on NAS.", flush=True)
    try:
        server.serve_forever()
    except KeyboardInterrupt:
        pass
    finally:
        server.close_reads()
        server.server_close()
