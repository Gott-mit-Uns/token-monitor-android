"""Opt-in API37 emulator check: actual Settings revocation across app processes.

Requires the permission-test APK already installed. Clears only this app's emulator
profile. Uses a loopback-only synthetic Hub, no real Hub configuration or credentials.
"""
import argparse
import hashlib
import http.server
import re
import socket
import subprocess
import threading
import time
import xml.etree.ElementTree as ET
from pathlib import Path

parser = argparse.ArgumentParser()
parser.add_argument("--adb", required=True)
parser.add_argument("--serial", required=True)
parser.add_argument("--output", type=Path, required=True)
args = parser.parse_args()
if not args.serial.startswith("emulator-"):
    raise SystemExit("This check is restricted to an explicitly selected disposable emulator")
base = [args.adb, "-s", args.serial]
package = "dev.bing.tokenmonitor.nativeclient"
permission = "android.permission.ACCESS_LOCAL_NETWORK"


def adb(*command):
    return subprocess.run(base + list(command), capture_output=True, text=True, check=True, timeout=30).stdout


if int(adb("shell", "getprop", "ro.build.version.sdk").strip()) != 37:
    raise SystemExit("This check requires the tested Android17/API37 system UI")
for apk in ("app/build/outputs/apk/debug/app-debug.apk", "app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk"):
    adb("install", "-r", apk)
digest = hashlib.sha256(Path("app/build/outputs/apk/debug/app-debug.apk").read_bytes()).hexdigest()

lock = threading.Lock()
counts = {"requests": 0, "streams": 0, "active": 0, "closed": 0}
sockets = set()
stats = b'{"periods":{"today":{"totalTokens":123}},"devices":[]}'


class Hub(http.server.BaseHTTPRequestHandler):
    def log_message(self, *unused):
        pass

    def do_GET(self):
        if self.path not in ("/api/health", "/api/stats", "/api/stats/stream"):
            self.send_error(404)
            return
        with lock:
            counts["requests"] += 1
        stream = self.path == "/api/stats/stream"
        if stream:
            with lock:
                counts["streams"] += 1
                counts["active"] += 1
                sockets.add(self.connection)
        try:
            self.send_response(200)
            self.send_header("Content-Type", "text/event-stream" if stream else "application/json")
            self.send_header("Connection", "close")
            body = b'{"ok":true,"role":"hub"}' if self.path == "/api/health" else stats
            if not stream:
                self.send_header("Content-Length", str(len(body)))
            self.end_headers()
            self.wfile.write(b'event: snapshot\ndata: {"stats":' + stats + b'}\n\n' if stream else body)
            self.wfile.flush()
            if stream:
                self.connection.settimeout(1)
                while True:
                    try:
                        if not self.connection.recv(1):
                            break
                    except socket.timeout:
                        continue
        except (OSError, ValueError):
            pass
        finally:
            if stream:
                with lock:
                    counts["active"] -= 1
                    counts["closed"] += 1
                    sockets.discard(self.connection)


def current(key):
    with lock:
        return counts[key]


def wait(condition, label, seconds=15):
    deadline = time.monotonic() + seconds
    while time.monotonic() < deadline:
        if condition():
            return
        time.sleep(0.1)
    raise AssertionError(label)


def permission_granted():
    # Retain only the one permission line, never other package diagnostics.
    return bool(re.search(r"ACCESS_LOCAL_NETWORK: granted=true", adb("shell", "dumpsys", "package", package)))


def settings_click(text):
    deadline = time.monotonic() + 12
    while time.monotonic() < deadline:
        temporary = "/data/local/tmp/tokenmonitor-system-settings.xml"
        adb("shell", "uiautomator", "dump", temporary)
        try:
            raw = adb("shell", "cat", temporary)
        finally:
            adb("shell", "rm", "-f", temporary)
        start, end = raw.find("<hierarchy"), raw.rfind("</hierarchy>")
        if start < 0 or end < 0:
            continue
        root = ET.fromstring(raw[start:end + len("</hierarchy>")])
        parents = {child: parent for parent in root.iter() for child in parent}
        for node in root.iter("node"):
            owner = node.get("package", "")
            if node.get("text") != text or owner not in ("com.android.settings", "com.android.permissioncontroller", "com.google.android.permissioncontroller"):
                continue
            while node.get("clickable") != "true" and node in parents:
                node = parents[node]
            numbers = [int(value) for value in re.findall(r"\d+", node.get("bounds", ""))]
            if node.get("clickable") == "true" and len(numbers) == 4:
                left, top, right, bottom = numbers
                adb("shell", "input", "tap", str((left + right) // 2), str((top + bottom) // 2))
                return
        time.sleep(0.1)
    raise AssertionError("Expected system Settings control unavailable")


def instrument(phase, port):
    result = subprocess.run(base + ["shell", "am", "instrument", "-w", "-e", "class", "dev.bing.tokenmonitor.LocalRevocationTest",
                                  "-e", "permissionPhase", phase, "-e", "fixturePort", str(port),
                                  package + ".test/androidx.test.runner.AndroidJUnitRunner"], capture_output=True, text=True, timeout=120)
    text = re.sub(r"[0-9a-fA-F]{8}(?:-[0-9a-fA-F]{4}){3}-[0-9a-fA-F]{12}", "[synthetic value omitted]", result.stdout + result.stderr)
    if "OK (1 test)" not in text:
        raise AssertionError("Revocation phase did not pass:\n" + text)
    return text


server = http.server.ThreadingHTTPServer(("127.0.0.1", 0), Hub)
server.daemon_threads = True
threading.Thread(target=server.serve_forever, daemon=True).start()
try:
    adb("shell", "pm", "clear", package)
    adb("shell", "pm", "grant", package, permission)
    seed_result = instrument("seed", server.server_port)
    wait(lambda: current("active") == 0, "Seed must close its foreground stream")
    prior_streams = current("streams")
    adb("shell", "am", "start", "-n", package + "/dev.bing.tokenmonitor.MainActivity")
    wait(lambda: current("active") == 1 and current("streams") > prior_streams, "Saved foreground connection did not restore")
    old_pid = adb("shell", "pidof", package).strip()
    assert old_pid and permission_granted()
    adb("shell", "am", "start", "-a", "android.settings.APPLICATION_DETAILS_SETTINGS", "-d", "package:" + package, "-f", "0x10008000")
    wait(lambda: current("active") == 0, "Entering system Settings must close SSE")
    settings_click("Permissions")
    settings_click("Nearby devices")
    settings_click("Don’t allow")
    wait(lambda: not permission_granted(), "System Settings did not revoke local access")
    # Runtime revocation may kill the app; observe that boundary outside its UID.
    wait(lambda: subprocess.run(base + ["shell", "pidof", package], capture_output=True, text=True).stdout.strip() != old_pid,
         "Expected revoked app process to end")
    paused = current("requests")
    adb("shell", "am", "start", "-n", package + "/dev.bing.tokenmonitor.MainActivity")
    time.sleep(2)
    assert not permission_granted() and current("requests") == paused and current("active") == 0, "Denied relaunch issued network requests"
    prior_streams = current("streams")
    recover_result = instrument("recover", server.server_port)
    wait(lambda: current("active") == 0, "Recovery check must close its stream")
    assert permission_granted() and current("streams") > prior_streams
    stopped = current("requests")
    time.sleep(0.5)
    assert stopped == current("requests")
    # Never archive package dumps, system UI XML, request headers, or response bodies.
    report = (f"APK SHA256: {digest}\nAndroid17/API37 actual Settings revocation: PASS\n"
                           "Saved foreground SSE restored, entering Settings closed SSE: PASS\n"
                           "System revocation ended prior app process: PASS\n"
                           "Denied relaunch retained encrypted cache and made no HTTP/SSE requests: PASS\n"
                           "App Settings link, system grant, foreground reconnect, final cache cleanup: PASS\n\n"
                           "SEED\n" + seed_result + "\nRECOVERY\n" + recover_result)
finally:
    try:
        adb("shell", "am", "force-stop", package)
        adb("shell", "pm", "clear", package)
    finally:
        server.shutdown()
        with lock:
            for connection in tuple(sockets):
                try:
                    connection.shutdown(socket.SHUT_RDWR)
                    connection.close()
                except OSError:
                    pass
        server.server_close()
args.output.parent.mkdir(parents=True, exist_ok=True)
args.output.write_text(report)
print("API37 system Settings revocation/cache/recovery: PASS (2 separate instrumentation processes)")
