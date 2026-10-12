"""API37 private peer HTTPS permission checks; ephemeral TLS material stays in process memory."""
import argparse
import base64
import hashlib
import json
import re
import ssl
import subprocess
import threading
import time
from pathlib import Path

parser = argparse.ArgumentParser()
parser.add_argument("--adb", required=True)
parser.add_argument("--serial", required=True)
parser.add_argument("--node", required=True)
parser.add_argument("--openssl", default="/usr/bin/openssl")
parser.add_argument("--output", type=Path, required=True)
args = parser.parse_args()
if not args.serial.startswith("emulator-"):
    raise SystemExit("This check clears the app profile; explicitly select a disposable emulator")
base = [args.adb, "-s", args.serial]
package = "dev.bing.tokenmonitor.nativeclient"
permission = "android.permission.ACCESS_LOCAL_NETWORK"


def adb(*command):
    return subprocess.run(base + list(command), capture_output=True, text=True, check=True, timeout=30).stdout


if int(adb("shell", "getprop", "ro.build.version.sdk").strip()) != 37:
    raise SystemExit("The real permission check requires Android17/API37")
for apk in ("app/build/outputs/apk/debug/app-debug.apk", "app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk"):
    adb("install", "-r", apk)
digest = hashlib.sha256(Path("app/build/outputs/apk/debug/app-debug.apk").read_bytes()).hexdigest()
# Capture only in memory; /dev/stdout is a pipe, no certificate/private-key files are created.
generated = subprocess.run([args.openssl, "req", "-x509", "-newkey", "rsa:2048", "-nodes", "-subj", "/CN=permission-fixture.test",
                            "-addext", "subjectAltName=DNS:permission-fixture.test,IP:10.0.2.2", "-days", "1",
                            "-keyout", "/dev/stdout", "-out", "/dev/stdout"], capture_output=True, check=True, timeout=30).stdout.decode()
key = re.search(r"-----BEGIN (?:RSA )?PRIVATE KEY-----.*?-----END (?:RSA )?PRIVATE KEY-----", generated, re.S)
cert = re.search(r"-----BEGIN CERTIFICATE-----.*?-----END CERTIFICATE-----", generated, re.S)
if key is None or cert is None:
    raise SystemExit("Unable to initialize the ephemeral synthetic TLS fixture")
public_certificate = base64.b64encode(ssl.PEM_cert_to_DER_cert(cert.group())).decode()
fixture = subprocess.Popen([args.node, "tools/fixture-local-tls.cjs"], stdin=subprocess.PIPE, stdout=subprocess.PIPE,
                           stderr=subprocess.DEVNULL, text=True)
lock = threading.Lock()
state = {"port": None, "requests": 0, "streams": 0, "active": 0, "closed": 0, "wrongHeaders": 0}


def read_counts():
    for line in fixture.stdout:
        message = json.loads(line)
        with lock:
            if message.get("type") == "ready":
                state["port"] = message["port"]
            elif message.get("type") == "counts":
                for key in state:
                    if key != "port":
                        state[key] = message[key]


def snapshot():
    with lock:
        return state.copy()


def wait(condition, label):
    deadline = time.monotonic() + 15
    while time.monotonic() < deadline:
        if condition():
            return
        if fixture.poll() is not None:
            raise AssertionError("Synthetic TLS fixture exited")
        time.sleep(0.05)
    raise AssertionError(label)


try:
    fixture.stdin.write(json.dumps({"key": key.group(), "cert": cert.group()}))
    fixture.stdin.close()
    del key, cert, generated
    threading.Thread(target=read_counts, daemon=True).start()
    wait(lambda: snapshot()["port"] is not None, "TLS fixture did not start")
    records = []
    for phase in ("denied", "grant"):
        adb("shell", "pm", "clear", package)
        adb("shell", "pm", "clear-permission-flags", package, permission, "user-set", "user-fixed")
        result = subprocess.run(base + ["shell", "am", "instrument", "-w", "-e", "class", "dev.bing.tokenmonitor.LocalHttpsPermissionTest",
                                      "-e", "permissionPhase", phase, "-e", "fixturePort", str(snapshot()["port"]),
                                      "-e", "fixturePublicCertificate", public_certificate,
                                      package + ".test/androidx.test.runner.AndroidJUnitRunner"], capture_output=True, text=True, timeout=120)
        cleaned = re.sub(r"[0-9a-fA-F]{8}(?:-[0-9a-fA-F]{4}){3}-[0-9a-fA-F]{12}", "[synthetic value omitted]", result.stdout + result.stderr)
        if "OK (1 test)" not in cleaned:
            raise AssertionError("Private HTTPS permission check did not pass:\n" + cleaned)
        if phase == "denied":
            assert snapshot()["requests"] == 0 and snapshot()["streams"] == 0, "Denied peer received HTTP/authentication"
        else:
            wait(lambda: snapshot()["active"] == 0 and snapshot()["closed"] == 2, "Both TLS SSEs must close")
            assert snapshot()["requests"] == 6 and snapshot()["streams"] == 2 and snapshot()["wrongHeaders"] == 0, "Unexpected TLS HTTP/authentication counts"
        records.append(phase.upper() + "\n" + cleaned)
        print(f"API37 private peer HTTPS {phase}: PASS", flush=True)
    report = (f"APK SHA256: {digest}\nAPI37 separate host peer; controlled DNS and literal private address: PASS\n"
                           "Denied: actual TCP timeout, conditional recovery hint, zero HTTP/authentication/SSE: PASS\n"
                           "System grant: both HTTPS forms read/cache/SSE; wrong hostname rejected before HTTP: PASS\n"
                           "Both TLS SSEs closed; synthetic caches cleared; ephemeral listener stopped after check.\n\n" + "\n".join(records))
finally:
    try:
        adb("shell", "am", "force-stop", package)
        adb("shell", "pm", "clear", package)
    finally:
        fixture.terminate()
        try:
            fixture.wait(timeout=5)
        except subprocess.TimeoutExpired:
            fixture.kill(); fixture.wait(timeout=5)
args.output.parent.mkdir(parents=True, exist_ok=True)
args.output.write_text(report)
