"""Synthetic process/socket tests for the temporary NAS bridge; no NAS or credentials used."""
import importlib.util
import http.server
import socket
import subprocess
import sys
import threading
import time
import unittest
from pathlib import Path

spec = importlib.util.spec_from_file_location("live_bridge", Path(__file__).with_name("live-hub-proxy.py"))
bridge = importlib.util.module_from_spec(spec)
spec.loader.exec_module(bridge)


class BridgeCancellationTest(unittest.TestCase):
    def start(self, mode):
        self.processes = []

        def fake_read(route):
            script = """import json,os,sys,threading,time
closed=threading.Event()
def watch():
    sys.stdin.buffer.read(); closed.set()
    if MODE == 'blocked': os._exit(0)
threading.Thread(target=watch,daemon=True).start()
sys.stdout.write(json.dumps({'status':200,'type':'text/event-stream'})+'\\n');sys.stdout.flush()
sys.stdout.buffer.write(b'event: snapshot\\ndata: {}\\n\\n');sys.stdout.flush()
if MODE == 'split':
    for _ in range(2):
        sys.stdout.buffer.write(b': h');sys.stdout.flush();time.sleep(0.05)
        sys.stdout.buffer.write(b'b\\n\\n');sys.stdout.flush();time.sleep(0.05)
    sys.stdout.buffer.write(b': other\\n\\n');sys.stdout.flush()
if MODE == 'blocked':
    for _ in range(1024):
        sys.stdout.buffer.write(b'x'*65536);sys.stdout.flush()
closed.wait(30)
""".replace("MODE", repr(mode))
            process = subprocess.Popen([sys.executable, "-u", "-c", script], stdin=subprocess.PIPE,
                                       stdout=subprocess.PIPE, stderr=subprocess.DEVNULL)
            self.processes.append(process)
            return process

        self.server = bridge.ReadBridgeServer(("127.0.0.1", 0), open_read=fake_read)
        self.serving = threading.Thread(target=self.server.serve_forever, daemon=True)
        self.serving.start()
        self.addCleanup(self.stop)

    def stop(self):
        self.server.close_reads()
        self.server.shutdown(); self.server.server_close()
        self.serving.join(2)
        for process in self.processes:
            if process.poll() is None:
                process.kill(); process.wait(2)

    def await_status(self, condition):
        until = time.monotonic() + 3
        while time.monotonic() < until:
            if condition(self.server.status()):
                return
            time.sleep(0.02)
        self.fail("Expected safe bridge status was not reached")

    def request(self, route):
        peer = socket.create_connection(self.server.server_address, timeout=3)
        peer.sendall(("GET /api/" + route + " HTTP/1.1\r\nHost: localhost\r\n\r\n").encode())
        received = b""
        while b"data: {}\n\n" not in received:
            received += peer.recv(4096)
        return peer

    def test_peer_cancel_ends_waiting_stream_without_next_heartbeat(self):
        self.start("wait")
        peer = self.request("stats/stream")
        self.await_status(lambda status: status["activeStreams"] == 1)
        peer.close()
        self.await_status(lambda status: status["activeReads"] == 0)
        self.assertTrue(all(process.poll() is not None for process in self.processes))
        self.assertEqual(1, self.server.status()["streamEnds"])

    def test_peer_cancel_also_ends_pending_ordinary_read(self):
        self.start("wait")
        peer = self.request("stats")
        self.await_status(lambda status: status["activeReads"] == 1)
        peer.close()
        self.await_status(lambda status: status["activeReads"] == 0)
        self.assertTrue(all(process.poll() is not None for process in self.processes))
        self.assertEqual(0, self.server.status()["streamStarts"])

    def test_history_trace_records_start_and_process_end_without_payload(self):
        self.start("wait")
        peer = self.request("history")
        self.await_status(lambda status: len(status["historyEvents"]) == 1)
        peer.close()
        self.await_status(lambda status: status["activeReads"] == 0)
        events = self.server.status()["historyEvents"]
        self.assertEqual(["STARTED", "ENDED"], [item["phase"] for item in events])
        self.assertEqual([1, 1], [item["sequence"] for item in events])
        self.assertEqual([1, 0], [item["active"] for item in events])
        self.assertLessEqual(events[0]["atNs"], events[1]["atNs"])
        self.assertTrue(all(set(item) == {"phase", "sequence", "atNs", "active"} for item in events))

    def test_split_heartbeats_are_counted_once_and_stream_stays_open(self):
        self.start("split")
        peer = self.request("stats/stream")
        try:
            self.await_status(lambda status: status["heartbeats"] >= 2)
            time.sleep(0.2)
            self.assertEqual(2, self.server.status()["heartbeats"])
            self.assertEqual(1, self.server.status()["activeStreams"])
            self.assertIsNone(self.processes[0].poll())
        finally:
            peer.close()
        self.await_status(lambda status: status["activeReads"] == 0)

    def test_harness_close_unblocks_handler_when_peer_no_longer_reads(self):
        self.start("blocked")
        self.server.socket.setsockopt(socket.SOL_SOCKET, socket.SO_SNDBUF, 4096)
        peer = self.request("stats/stream")
        try:
            self.await_status(lambda status: status["activeStreams"] == 1)
            time.sleep(0.2)
            self.server.close_reads()
            self.assertEqual(0, self.server.status()["activeReads"])
            self.assertEqual(1, self.server.status()["streamEnds"])
            self.assertTrue(all(process.poll() is not None for process in self.processes))
            self.assertNotEqual(-1, peer.fileno())
        finally:
            peer.close()


class DesktopTransportTest(unittest.TestCase):
    """Exercise the actual child transport with a public, loopback-only fixture."""
    def test_configured_target_is_limited_to_authorized_nas_root(self):
        self.assertEqual(("192.168.31.12", 17321), bridge.desktop_target("http://192.168.31.12:17321/"))
        for root in ("https://192.168.31.12", "http://192.168.31.13", "http://localhost",
                     "http://192.168.31.12/api", "http://192.168.31.12?x=1", "http://192.168.31.12#x",
                     "http://fixture@192.168.31.12", "http://192.168.31.12:bad"):
            with self.subTest(root=root), self.assertRaises(ValueError):
                bridge.desktop_target(root)

    def test_child_stdin_eof_closes_idle_upstream_socket_without_heartbeat(self):
        ended = threading.Event()
        requests = []
        class Upstream(http.server.BaseHTTPRequestHandler):
            def log_message(self, *args): pass
            def do_GET(self):
                requests.append((self.path, "Authorization" in self.headers))
                self.send_response(200)
                self.send_header("Content-Type", "text/event-stream")
                self.end_headers()
                self.wfile.write(b"event: snapshot\ndata: {}\n\n"); self.wfile.flush()
                try:
                    self.connection.settimeout(3)
                    self.connection.recv(1)
                except OSError:
                    pass
                ended.set()
        upstream = http.server.ThreadingHTTPServer(("127.0.0.1", 0), Upstream)
        thread = threading.Thread(target=upstream.serve_forever, daemon=True); thread.start()
        script = """import importlib.util,sys
spec=importlib.util.spec_from_file_location('read_bridge',sys.argv[1]); module=importlib.util.module_from_spec(spec);spec.loader.exec_module(module)
module.load_desktop_connection=lambda: ('127.0.0.1',int(sys.argv[2]),'public-fixture')
module.desktop_read(sys.argv[3])
"""
        processes = []
        def open_read(route):
            process = subprocess.Popen([sys.executable, "-u", "-c", script, str(Path(bridge.__file__)), str(upstream.server_port), route],
                                       stdin=subprocess.PIPE, stdout=subprocess.PIPE, stderr=subprocess.DEVNULL)
            processes.append(process)
            return process
        server = bridge.ReadBridgeServer(("127.0.0.1", 0), open_read=open_read)
        serving = threading.Thread(target=server.serve_forever, daemon=True); serving.start()
        try:
            for route, authenticated in (("health", False), ("stats/stream", True)):
                ended.clear()
                peer = socket.create_connection(server.server_address, timeout=3)
                peer.sendall(("GET /api/" + route + " HTTP/1.1\r\nHost: localhost\r\n\r\n").encode())
                received = b""
                while b"data: {}\n\n" not in received: received += peer.recv(4096)
                peer.close()
                self.assertTrue(ended.wait(2), "Real child socket must close before another heartbeat")
                until = time.monotonic() + 2
                while server.status()["activeReads"] and time.monotonic() < until: time.sleep(0.02)
                self.assertEqual(0, server.status()["activeReads"])
                self.assertEqual(("/api/" + route, authenticated), requests[-1])
                self.assertTrue(all(process.poll() is not None for process in processes))
        finally:
            server.close_reads(); server.shutdown(); server.server_close(); serving.join(2)
            upstream.shutdown(); upstream.server_close(); thread.join(2)
            for process in processes:
                if process.poll() is None: process.kill(); process.wait(2)


if __name__ == "__main__":
    unittest.main()
