"""Record public real-clock Dashboard diagnostics on the one owned disposable emulator."""
from pathlib import Path
import hashlib
import json
import os
import subprocess
from dashboard_evidence import validate_dashboard_runtime

root = Path(__file__).resolve().parent.parent
apk = root / 'app/build/outputs/apk/debug/app-debug.apk'
test = root / 'app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk'
digest = hashlib.sha256(apk.read_bytes()).hexdigest()
ordinary = test.read_bytes()
backup = root / 'work/dashboard-ordinary-test.apk'
backup.write_bytes(ordinary)
backup.with_suffix('.sha256').write_text(hashlib.sha256(ordinary).hexdigest() + '\n')
env = {**os.environ, 'JAVA_HOME': '/Users/bing/android-toolchain/jdk-17.0.20.1+1/Contents/Home',
       'ANDROID_HOME': '/Users/bing/android-toolchain/sdk'}
adb = ['/Users/bing/android-toolchain/sdk/platform-tools/adb', '-s', 'emulator-5556']
pkg = 'dev.bing.tokenmonitor.nativeclient'


def run(*args, binary=False, timeout=180):
    return subprocess.run(adb + list(args), capture_output=True, text=not binary,
                          check=True, timeout=timeout).stdout


assert run('emu', 'avd', 'name').splitlines()[0] == 'TokenMonitor_API36'
old = run('shell', 'settings', 'get', 'global', 'animator_duration_scale').strip()
try:
    with (root / 'work/dashboard-perf-build.txt').open('w') as log:
        subprocess.run(['./gradlew', ':app:assembleDebugAndroidTest', '-PtokenMonitorMotionPerf=true'],
                       cwd=root, env=env, stdout=log, stderr=subprocess.STDOUT, check=True)
    assert hashlib.sha256(apk.read_bytes()).hexdigest() == digest
    run('install', '-r', str(apk))
    run('install', '-r', str(test))
    assert run('shell', 'pm', 'clear', pkg).strip() == 'Success'
    print('Optional benchmark built; current APK installed; real-clock recording started', flush=True)
    run('shell', 'settings', 'put', 'global', 'animator_duration_scale', '1')
    result = run('shell', 'am', 'instrument', '-w', '-e', 'class',
                 'dev.bing.tokenmonitor.DashboardRuntimeBenchmarkTest', '-e', 'expectIsolated', 'true',
                 pkg + '.test/androidx.test.runner.AndroidJUnitRunner')
    if 'OK (1 test)' not in result:
        review = root / 'evidence/dashboard-parity/initial-review'
        review.mkdir(parents=True, exist_ok=True)
        count = len(list(review.glob('dashboard-runtime-failed-*.txt'))) + 1
        (review / f'dashboard-runtime-failed-{count}.txt').write_text('APK SHA256: ' + digest + '\n' + result)
        try:
            partial = run('exec-out', 'cat', '/sdcard/Android/data/' + pkg + '/files/dashboard-runtime-partial-public.json', binary=True)
            decoded = json.loads(partial)
            if decoded['apkSha256'] == digest and decoded['partial'] is True:
                (review / f'dashboard-runtime-failed-{count}.json').write_bytes(partial)
        except (subprocess.CalledProcessError, KeyError, ValueError):
            pass
        raise RuntimeError('Public Dashboard benchmark failed; diagnostic preserved')
    data = run('exec-out', 'cat', '/sdcard/Android/data/' + pkg + '/files/dashboard-runtime-public.json', binary=True)
    report = json.loads(data)
    validate_dashboard_runtime(report, digest)
    final = root / 'evidence/dashboard-parity/final'
    final.mkdir(parents=True, exist_ok=True)
    (final / 'dashboard-window-diagnostics.json').write_bytes(data)
    (final / 'dashboard-window-runtime.txt').write_text('Final application APK SHA256: ' + digest + '\n' + result)
    for scene in report['scenarios']:
        print(scene['name'], [(r['frames'], round(r['windowDrawCadenceHz'], 2),
                              r['framesOverDeadline'], r['dashboardOwnerCompositions'])
                             for r in scene['runs']], flush=True)
finally:
    run('shell', 'settings', 'put', 'global', 'animator_duration_scale', old)
    test.write_bytes(ordinary)
    assert test.read_bytes() == backup.read_bytes()
    run('install', '-r', str(test))
    print('Ordinary test APK restored byte-for-byte; owned emulator scale restored', flush=True)
