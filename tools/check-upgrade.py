"""Actual development APK replacement on one explicit disposable emulator; synthetic data only."""
import argparse
import hashlib
import re
import subprocess
from pathlib import Path

parser = argparse.ArgumentParser()
parser.add_argument("--adb", required=True)
parser.add_argument("--serial", required=True)
parser.add_argument("--output", type=Path, required=True)
parser.add_argument("--from-version", choices=("0.1.7", "0.2.0", "0.2.1", "0.2.2", "0.2.3", "0.2.4", "0.2.5", "0.2.6", "0.2.7", "0.2.8", "0.2.9", "0.2.10", "0.2.11", "0.2.12", "0.2.13", "0.2.14", "0.2.15", "0.2.16", "0.2.17", "0.2.18", "0.2.19", "0.2.20", "0.2.21", "0.2.22", "0.2.23", "0.2.24", "0.2.27"), default="0.1.7")
parser.add_argument("--current-apk", type=Path, default=Path("dist/token-monitor-native-0.2.38-dev.apk"))
parser.add_argument("--current-version", default="0.2.38-dev")
args = parser.parse_args()
if not args.serial.startswith("emulator-"):
    raise SystemExit("Choose an explicit disposable emulator; this check clears this app's test profile")
base = [args.adb, "-s", args.serial]
package = "dev.bing.tokenmonitor.nativeclient"
old = Path("dist/token-monitor-native-" + args.from_version + "-dev.apk")
current = args.current_apk
test = Path("app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk")


def adb(*command, binary=False, seconds=30):
    return subprocess.run(base + list(command), capture_output=True, text=not binary, check=True, timeout=seconds).stdout


def run(method):
    transcript = adb("shell", "am", "instrument", "-w", "-e", "class", "dev.bing.tokenmonitor.UpgradeTest#" + method,
                     "-e", "upgradeFrom", args.from_version, "-e", "upgradedVersion", args.current_version,
                     package + ".test/androidx.test.runner.AndroidJUnitRunner", seconds=75)
    if "OK (1 test)" not in transcript:
        frames = re.findall(r"dev\.bing\.tokenmonitor\.\w+\.\w+\([A-Za-z0-9_.]+:\d+\)", transcript)
        types = re.findall(r"(?m)^([A-Za-z0-9_.$]+(?:Error|Exception))(?=:|$)", transcript)
        args.output.parent.mkdir(parents=True, exist_ok=True)
        args.output.write_text("FAILED; private diagnostics withheld\n" + "\n".join(dict.fromkeys(types + frames)) + "\n")
        raise AssertionError("Upgrade phase did not pass: " + method + "; diagnostics withheld")
    duration = re.search(r"Time: ([0-9.]+)", transcript)
    return method + "\n" + ("Time: " + duration.group(1) + "\n" if duration else "") + "OK (1 test)\n"


def profile():
    # Bytes remain in memory; archive boolean preservation only, no credential/cache file.
    return tuple(hashlib.sha256(adb("exec-out", "run-as", package, "cat", "no_backup/" + name, binary=True)).digest()
                 for name in ("hub-state.bin", "display-preferences.bin"))


sdk = int(adb("shell", "getprop", "ro.build.version.sdk").strip())
latest_installed = False
test_installed = False
results = []
try:
    adb("shell", "am", "force-stop", package)
    adb("install", "-r", "-d", str(old))
    adb("shell", "pm", "clear", package)
    uid_before = adb("shell", "run-as", package, "id", "-u").strip()
    adb("install", "-r", str(test))
    test_installed = True
    results.append(run("seedProfileUsingInstalled" + args.from_version.replace(".", "") + "Storage"))
    saved_profile = profile()
    adb("install", "-r", str(current))
    latest_installed = True
    assert uid_before == adb("shell", "run-as", package, "id", "-u").strip(), "APK replacement changed application UID"
    assert saved_profile == profile(), "Package replacement did not preserve profile bytes"
    if sdk >= 37:
        adb("shell", "pm", "grant", package, "android.permission.ACCESS_LOCAL_NETWORK")
    results.append(run("upgradedNativeRestoresOldKeystoreAndActualActivityCache"))
    assert saved_profile == profile(), "Failed HTTPS changed the old saved profile"
    print("Actual " + args.from_version + " to " + args.current_version + " APK replacement, Keystore/profile and Activity restoration: PASS", flush=True)
finally:
    try:
        if not latest_installed:
            adb("install", "-r", str(current))
    finally:
        try:
            adb("shell", "am", "force-stop", package)
            adb("shell", "pm", "clear", package)
        finally:
            if test_installed:
                adb("uninstall", package + ".test")
report = (f"Android API{sdk}; final application APK SHA256: {hashlib.sha256(current.read_bytes()).hexdigest()}\n"
          f"Original {args.from_version} APK SHA256: {hashlib.sha256(old.read_bytes()).hexdigest()}\n"
          "Old installed APK's storage implementation wrote a synthetic encrypted profile: PASS\n"
          "Actual APK replacement preserved UID and exact encrypted cache/preference bytes: PASS\n"
          "New APK decrypts using retained Keystore; actual MainActivity restores all cached details: PASS\n"
          "Large Token integer, history, subscriptions, aliases, model rules and zero price retained: PASS\n"
          "Dark theme, hidden labels, English retained; unavailable HTTPS leaves saved data intact: PASS\n"
          "Latest APK restored, synthetic profile cleared, optional test package removed: PASS\n"
          "Development signing only; no formal release/signing-key transition or old-fork migration tested.\n\n"
          + "\n".join(results))
args.output.parent.mkdir(parents=True, exist_ok=True)
args.output.write_text(report)
