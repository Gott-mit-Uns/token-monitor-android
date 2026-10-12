"""Direct emulator/NAS client check. Bootstrap only carries an existing credential in memory."""
import argparse
import hashlib
import http.server
import importlib.util
import json
from pathlib import Path
import re
import subprocess
import threading

parser=argparse.ArgumentParser()
parser.add_argument('--serial',required=True)
parser.add_argument('--output',type=Path,required=True)
args=parser.parse_args()
if not args.serial.startswith('emulator-'):
    raise SystemExit('Explicit disposable emulator required; this clears only the app test profile')
root=Path(__file__).resolve().parent.parent
spec=importlib.util.spec_from_file_location('direct_bootstrap_source',root/'tools/live-hub-proxy.py')
source=importlib.util.module_from_spec(spec);spec.loader.exec_module(source)
host,port,credential=source.load_desktop_connection()
payload={'address':f'http://{host}:{port}','credential':credential}
credential=None
lock=threading.Lock();deliveries=0
class Bootstrap(http.server.BaseHTTPRequestHandler):
    def log_message(self,*args):pass
    def do_GET(self):
        global deliveries
        with lock:
            if self.path=='/target' and payload:
                body=json.dumps({'address':payload['address']}).encode()
            elif self.path=='/bootstrap' and not deliveries and payload:
                body=json.dumps(payload).encode();payload.clear();deliveries+=1
            else:
                self.send_error(404);return
        self.send_response(200);self.send_header('Content-Type','application/json');self.send_header('Cache-Control','no-store')
        self.send_header('Content-Length',str(len(body)));self.end_headers();self.wfile.write(body)
server=http.server.ThreadingHTTPServer(('127.0.0.1',0),Bootstrap)
serving=threading.Thread(target=server.serve_forever,daemon=True);serving.start()
adb=['/Users/bing/android-toolchain/sdk/platform-tools/adb','-s',args.serial]
package='dev.bing.tokenmonitor.nativeclient'
def run(*command,check=True):
    return subprocess.run(adb+list(command),capture_output=True,text=True,check=check,timeout=120).stdout
transcript=''
try:
    for apk in ('app/build/outputs/apk/debug/app-debug.apk','app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk'):
        run('install','-r',str(root/apk))
    run('shell','pm','clear',package)
    if int(run('shell','getprop','ro.build.version.sdk').strip())>=37:
        run('shell','pm','grant',package,'android.permission.ACCESS_LOCAL_NETWORK')
    transcript=run('shell','am','instrument','-w','-e','class','dev.bing.tokenmonitor.LiveDirectLanTest','-e','bootstrapPort',str(server.server_port),package+'.test/androidx.test.runner.AndroidJUnitRunner')
    assert 'OK (1 test)' in transcript and deliveries==1
    assert all('nativeDirectStage='+stage in transcript for stage in ('connected','background','resumed'))
    digest=hashlib.sha256((root/'app/build/outputs/apk/debug/app-debug.apk').read_bytes()).hexdigest()
    duration=re.search(r'Time: ([0-9.]+)',transcript)
    report=f'Final application APK SHA256: {digest}\nDirect MainActivity/native client/default LAN socket to authorized NAS: PASS\nGET/SSE directly to NAS, proxy bypass; no data bridge/reverse mapping: PASS\nCredential bootstrap delivered once over emulator host loopback, remains in memory/encrypted store: PASS\nOriginal encrypted snapshot/history match current model; credential absent from plaintext file bytes: PASS\nHome cancels actual stream/clears running and queued calls within3s;6s no calls/retry task: PASS\nLauncher resumes one new direct NAS stream and preserves cache: PASS\nPrivate values/responses/credentials/pixels/tree never archived.\nLucky HTTPS and physical device are not covered.\n'+('Time: '+duration.group(1)+'\n' if duration else '')+'OK (1 test)\n'
except BaseException:
    frames=re.findall(r'dev\.bing\.tokenmonitor\.\w+\.\w+\([A-Za-z0-9_.]+:\d+\)',transcript)
    probes=re.findall(r'nativeDirectProbe=([a-z:,]+)\b',transcript)
    args.output.parent.mkdir(parents=True,exist_ok=True);args.output.write_text('FAILED; private diagnostics withheld\n'+'\n'.join(dict.fromkeys(frames+probes))+f'\nCredential handoffs: {deliveries}\n')
    raise SystemExit('Direct LAN check did not pass; private diagnostics withheld') from None
finally:
    payload.clear();server.shutdown();server.server_close();serving.join(2)
    run('shell','am','force-stop',package,check=False);run('shell','pm','clear',package,check=False)
args.output.parent.mkdir(parents=True,exist_ok=True);args.output.write_text(report)
print('Direct native LAN/SSE/cache/background/Launcher verification: PASS')
