"""Opt-in actual API37 permission UI checks; clears only the selected emulator app profile."""
import argparse
import hashlib
import re
import subprocess
from pathlib import Path

parser = argparse.ArgumentParser()
parser.add_argument("--adb", required=True)
parser.add_argument("--serial", required=True)
parser.add_argument("--scenario", choices=("normal", "permanent", "all"), default="all")
parser.add_argument("--output-dir", type=Path, required=True)
args = parser.parse_args()
if not args.serial.startswith("emulator-"):
    raise SystemExit("Use an explicitly selected disposable emulator; this check clears the app profile")
base = [args.adb, "-s", args.serial]
package = "dev.bing.tokenmonitor.nativeclient"
permission = "android.permission.ACCESS_LOCAL_NETWORK"


def adb(*command):
    return subprocess.run(base + list(command), check=True, capture_output=True, text=True, timeout=30).stdout


if int(adb("shell", "getprop", "ro.build.version.sdk").strip()) != 37:
    raise SystemExit("The permission UI check requires Android17/API37")
for apk in ("app/build/outputs/apk/debug/app-debug.apk", "app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk"):
    adb("install", "-r", apk)
version = re.search(r"\bversionName=(\d+\.\d+\.\d+)-dev\b", adb("shell", "dumpsys", "package", package))
if version is None:
    raise SystemExit("Expected a native development APK")
version = version.group(1)
digest = hashlib.sha256(Path("app/build/outputs/apk/debug/app-debug.apk").read_bytes()).hexdigest()
scenarios = ("normal", "permanent") if args.scenario == "all" else (args.scenario,)
for scenario in scenarios:
    try:
        adb("shell", "pm", "clear", package)
        adb("shell", "pm", "clear-permission-flags", package, permission, "user-set", "user-fixed")
        result = subprocess.run(base + ["shell", "am", "instrument", "-w", "-e", "class", "dev.bing.tokenmonitor.LocalPermissionTest",
                                      "-e", "permanentDenial", str(scenario == "permanent").lower(),
                                      package + ".test/androidx.test.runner.AndroidJUnitRunner"], capture_output=True, text=True, timeout=150)
        cleaned = re.sub(r"[0-9a-fA-F]{8}(?:-[0-9a-fA-F]{4}){3}-[0-9a-fA-F]{12}", "[synthetic value omitted]", result.stdout + result.stderr)
        if "OK (1 test)" not in cleaned:
            raise AssertionError("Permission check did not pass:\n" + cleaned)
    finally:
        adb("shell", "am", "force-stop", package)
        adb("shell", "pm", "clear", package)
    args.output_dir.mkdir(parents=True, exist_ok=True)
    name = "permanent-denial" if scenario == "permanent" else "permission"
    (args.output_dir / f"results-api37-{name}-{version}.txt").write_text(f"Version: {version}-dev; Android17/API37\nAPK SHA256: {digest}\n\n" + cleaned)
    print(f"API37 {scenario} denial/system grant/foreground lifecycle: PASS", flush=True)
