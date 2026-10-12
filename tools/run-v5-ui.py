"""Actual system-window Compact matrix on the runner-owned API36 emulator; no data clear."""
import argparse
import hashlib
import itertools
from pathlib import Path
import subprocess

p = argparse.ArgumentParser()
p.add_argument('--serial', required=True)
p.add_argument('--output', type=Path, required=True)
p.add_argument('--app-apk', type=Path, required=True)
p.add_argument('--test-apk', type=Path, required=True)
p.add_argument('--smoke', action='store_true')
p.add_argument('--large', action='store_true')
p.add_argument('--largest', action='store_true')
a = p.parse_args()
assert a.serial == 'emulator-5588', 'Only this task-owned V5 emulator is allowed'
adb = ['/Users/bing/android-toolchain/sdk/platform-tools/adb', '-s', a.serial]
def run(*cmd):
    return subprocess.run(adb + list(cmd), capture_output=True, text=True, check=True, timeout=120).stdout
a.output.mkdir(parents=True, exist_ok=True)
(a.output/'fingerprints.txt').write_text(''.join(f'{hashlib.sha256(path.read_bytes()).hexdigest()}  {path.name}\n' for path in (a.app_apk,a.test_apk)))
for path in (a.app_apk,a.test_apk): run('install','-r',str(path))
cases = [(320,2.0,False,True),(411,1.3,True,False)] if a.largest else ([(320,2.0,True,False)] if a.large else ([(393,1.0,True,False)] if a.smoke else itertools.product((320,393,411),(1.0,1.3,2.0),(True,False),(False,True))))
summary=[]
try:
    for width,scale,dark,english in cases:
        name=f'{width}-{scale}-{"dark" if dark else "light"}-{"en" if english else "zh"}{"-largest" if a.largest else ""}'
        directory=a.output/name; directory.mkdir(exist_ok=True)
        # Integer pixels/dpi are Android's real window inputs, not a LocalDensity substitute.
        run('shell','wm','size',f'{ {320:880,393:1080,411:1130}[width]}x2400')
        run('shell','wm','density','440')
        run('shell','settings','put','system','font_scale',str(scale))
        (directory/'window.txt').write_text(run('shell','wm','size')+run('shell','wm','density')+run('shell','settings','get','system','font_scale'))
        run('shell','rm','-f','/sdcard/Android/data/dev.bing.tokenmonitor.nativeclient/files/v5-layout.tsv')
        log=run('shell','am','instrument','-w','-e','class','dev.bing.tokenmonitor.CompactV5ScreenTest',
                '-e','visualDark',str(dark).lower(),'-e','v5English',str(english).lower(),'-e','v5Largest',str(a.largest).lower(),
                'dev.bing.tokenmonitor.nativeclient.test/androidx.test.runner.AndroidJUnitRunner')
        (directory/'result.txt').write_text(log)
        # Only explicitly public V5 screenshots/measurements; never fetch the app's whole directory.
        for filename in ('v5-layout.tsv','v5-main-tall.png','v5-dashboard-bars.png','v5-dashboard-kline.png',
                         'v5-overview.png','v5-record-bars.png','v5-record-kline.png','v5-record-line.png','v5-record-row.png'):
            run('pull',f'/sdcard/Android/data/dev.bing.tokenmonitor.nativeclient/files/{filename}',str(directory/filename))
        passed='OK (2 tests)' in log
        summary.append(f'{name}\t{"PASS" if passed else "FAIL"}\t2\n')
        (a.output/'summary.tsv').write_text(''.join(summary))
        print(name, 'PASS' if passed else 'FAIL', flush=True)
        if not passed: raise SystemExit('Matrix stopped at first failure; evidence retained')
finally:
    run('shell','wm','size','1080x2400')
    run('shell','wm','density','440')
    run('shell','settings','put','system','font_scale','1.0')
