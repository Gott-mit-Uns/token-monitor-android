"""Readonly real Hub evidence; secrets/responses stay in memory, never clears app data."""
import argparse, hashlib, http.client, http.server, importlib.util, json, plistlib, re, subprocess, threading, time, socket, select
from pathlib import Path

p=argparse.ArgumentParser()
p.add_argument('--serial', required=True)
p.add_argument('--mode', choices=('lan','https','https-relay','bridge'),required=True)
p.add_argument('--app-apk',type=Path,required=True)
p.add_argument('--test-apk',type=Path,required=True)
p.add_argument('--output',type=Path,required=True)
a=p.parse_args()
assert a.serial.startswith('emulator-'), 'Owned isolated emulator required'
bridged=a.mode=='bridge'
root=Path(__file__).resolve().parent.parent
spec=importlib.util.spec_from_file_location('v5_safe_bridge',root/'tools/live-hub-proxy.py')
m=importlib.util.module_from_spec(spec);spec.loader.exec_module(m)
host,port,credential=m.load_desktop_connection()
origin=f'http://{host}:{port}' if a.mode not in ('https','https-relay') else 'https://token.799599.xyz:13245'
report={'mode':a.mode,'appSha256':hashlib.sha256(a.app_apk.read_bytes()).hexdigest(),'testSha256':hashlib.sha256(a.test_apk.read_bytes()).hexdigest(),
 'privateBodiesPersisted':False,'credentialsArchivedInEvidence':False,'appDataCleared':False,'physicalDevice':'NOT VERIFIED','sourceCommit':subprocess.run(['git','rev-parse','HEAD'],cwd=root,capture_output=True,text=True,check=True).stdout.strip()}
for base in (Path('/Applications/Token Monitor.app'),Path.home()/'Applications/Token Monitor.app'):
 if (base/'Contents/Info.plist').exists():
  report['desktopVersion']=plistlib.loads((base/'Contents/Info.plist').read_bytes()).get('CFBundleShortVersionString')
# Real LAN contract probe, only field presence and counts archived.
c=http.client.HTTPConnection(host,port,timeout=30)
try:
 for route in ('health','stats','history','sync/content'):
  c.request('GET','/api/'+route,headers={'Authorization':'Bearer '+credential} if route!='health' else {})
  response=c.getresponse();raw=response.read(8*1024*1024+1);assert response.status==200 and len(raw)<=8*1024*1024
  data=json.loads(raw);proof={'httpStatus':response.status}
  if route=='health':proof.update({k:data.get(k) for k in ('ok','role','runtime','version','hubBuild','deviceCount')})
  elif route=='stats':
   devices=data.get('devices',[]);sessions=[s for d in devices for period in d.get('periods',{}).values() if isinstance(period,dict) for s in (period.get('sessions',{}).values() if isinstance(period.get('sessions'),dict) else period.get('sessions',[])) if isinstance(s,dict)]
   proof.update(deviceCount=len(devices),periods=sorted(data.get('periods',{})),sessionRowOccurrences=len(sessions),optionalSessionFields={k:sum(k in s for s in sessions) for k in ('liveActivity','waitingForInput','nativeSessionId','t3ThreadId','parentSessionId','turnEnded','title','timedTokens','timedOutputTokens','timedDurationMs','reasoningTokens')})
   proof['antigravityTitleOccurrences']=sum('title' in s and str(s.get('client','')).lower().startswith('antigravity') for s in sessions)
   proof['periodTimedFieldPresence']={k:sum(k in v for v in data.get('periods',{}).values() if isinstance(v,dict)) for k in ('timedTokens','timedOutputTokens','timedDurationMs','reasoningTokens')}
   proof['periodTotalsMatchDeviceSum']={k:v.get('totalTokens')==sum(d.get('periods',{}).get(k,{}).get('totalTokens',0) for d in devices) for k,v in data.get('periods',{}).items() if isinstance(v,dict) and isinstance(v.get('totalTokens'),int)}
  elif route=='history':proof['dailyRows']=len(data.get('daily',[]));proof['monthlyRows']=len(data.get('monthly',[]))
  else:proof['capabilityFieldsPresent']={k:k in data for k in ('sessionTitles','sharedSettings')};proof['capabilityFieldTypes']={k:type(data.get(k)).__name__ for k in ('sessionTitles','sharedSettings')}
  report[route]=proof
finally:c.close()
lock=threading.Lock();deliveries=0
payload={'address':origin,'credential':credential};credential=None
class Bootstrap(http.server.BaseHTTPRequestHandler):
 def log_message(self,*args):pass
 def do_GET(self):
  global deliveries
  with lock:
   if self.path=='/target' and payload:body=json.dumps({'address':payload['address']}).encode()
   elif self.path=='/bootstrap' and payload and deliveries==0:
    body=json.dumps(payload).encode();payload.clear();deliveries+=1
   else:self.send_error(404);return
  self.send_response(200);self.send_header('Content-Type','application/json');self.send_header('Cache-Control','no-store');self.send_header('Content-Length',str(len(body)));self.end_headers();self.wfile.write(body)
server=m.ReadBridgeServer(('127.0.0.1',0),m.open_desktop_read) if bridged else http.server.ThreadingHTTPServer(('127.0.0.1',0),Bootstrap)
thread=threading.Thread(target=server.serve_forever,daemon=True);thread.start()
adb=['/Users/bing/android-toolchain/sdk/platform-tools/adb','-s',a.serial]
def run(*args,timeout=200):return subprocess.run(adb+list(args),capture_output=True,text=True,check=True,timeout=timeout).stdout
transcript=''
relay=None;relayThread=None;relayStarts=relayEnds=0;relayLock=threading.Lock()
class TlsPassthrough(http.server.BaseHTTPRequestHandler):
 def log_message(self,*args):pass
 def do_CONNECT(self):
  global relayStarts,relayEnds
  if self.path!='token.799599.xyz:13245':self.send_error(403);return
  with relayLock:relayStarts+=1
  peer=None
  try:
   peer=socket.create_connection(('192.168.31.1',13245),timeout=20)
   self.send_response(200);self.end_headers();self.close_connection=True
   peer.settimeout(None);self.connection.settimeout(None)
   while True:
    readable,_,_=select.select([peer,self.connection],[],[],45)
    if not readable:break
    for source in readable:
     block=source.recv(65536)
     if not block:return
     (self.connection if source is peer else peer).sendall(block)
  except (OSError,ValueError):pass
  finally:
   if peer is not None:peer.close()
   with relayLock:relayEnds+=1
originalProxy=None
try:
 if a.mode=='https-relay':
  originalProxy=run('shell','settings','get','global','http_proxy').strip()
  assert originalProxy in ('null',':0',''), 'Owned fresh emulator proxy must be unset'
  relay=http.server.ThreadingHTTPServer(('127.0.0.1',0),TlsPassthrough)
  relayThread=threading.Thread(target=relay.serve_forever,daemon=True);relayThread.start()
  run('shell','settings','put','global','http_proxy','10.0.2.2:'+str(relay.server_port))
  report['route']='real Lucky through local TCP CONNECT diagnostic route; Android retains original hostname/system TLS/authentication; relay does not terminate TLS'

 for apk in (a.app_apk,a.test_apk):run('install','-r',str(apk))
 if int(run('shell','getprop','ro.build.version.sdk').strip())>=37:run('shell','pm','grant','dev.bing.tokenmonitor.nativeclient','android.permission.ACCESS_LOCAL_NETWORK')
 cls='LiveHubTest' if bridged else 'LiveDirectLanTest'
 opts=['-e','liveHubPort' if bridged else 'bootstrapPort',str(server.server_port)]
 if bridged:opts+=['-e','observeLiveSeconds','65']
 else:opts+=['-e','directEntryMode','https' if a.mode=='https-relay' else a.mode]
 process=subprocess.Popen(adb+['shell','am','instrument','-w','-e','class','dev.bing.tokenmonitor.'+cls,*opts,'dev.bing.tokenmonitor.nativeclient.test/androidx.test.runner.AndroidJUnitRunner'],stdout=subprocess.PIPE,stderr=subprocess.STDOUT,text=True)
 lines=[];background=threading.Event()
 def collect():
  for line in process.stdout:
   lines.append(line)
   if 'nativeLiveStage=background' in line:background.set()
 reader=threading.Thread(target=collect,daemon=True);reader.start()
 if bridged:
  assert background.wait(150), 'Background checkpoint unavailable'
  until=time.monotonic()+3
  while server.status()['activeReads'] and time.monotonic()<until:time.sleep(.05)
  assert server.status()['activeReads']==0
  paused=server.status()['requests'];time.sleep(.8)
  assert server.status()['requests']==paused and server.status()['activeStreams']==0
  report['hostBackgroundZeroReads']=True
 process.wait(timeout=60);reader.join(2);transcript=''.join(lines)
 assert 'OK (1 test)' in transcript
 if not bridged:assert deliveries==1 and all('nativeDirectStage='+stage in transcript for stage in ('connected','background','cold_restored','resumed'))
 proofs=re.findall(r'nativeV5Contract=(\{[^\r\n]+\})',transcript)
 assert len(proofs)==1
 proof=json.loads(proofs[0]);assert all(isinstance(v,int) and v>=0 for v in proof.values())
 report['androidContract']=proof
 report.update(result='PASS',durationSeconds=float(re.search(r'Time: ([0-9.]+)',transcript).group(1)),credentialHandoffs=deliveries)
 if bridged:
  status=server.status();assert status['activeReads']==0 and status['streamStarts']==status['streamEnds']==2
  if a.mode=='bridge':assert status['heartbeats']>=2
  report['bridge']=status
 else:report['assertions']=['authenticated direct GET/SSE to selected real entry','system TLS trust for HTTPS','snapshot/history parser and encrypted cache roundtrip','credential absent from plaintext storage','actual Home closes running/queued requests within 3 seconds','six seconds background no requests/reconnect','fresh background ViewModel restores encrypted snapshot/history without calls','launcher resumes one new stream and retains cache']
except BaseException:
 transcript=''.join(lines) if 'lines' in locals() else transcript
 report.update(result='FAILED',credentialHandoffs=deliveries,probes=re.findall(r'nativeDirectProbe=([a-z:,]+)\b',transcript),stateProbes=re.findall(r'nativeDirectState=([a-z0-9:,]+)\b',transcript),frames=sorted(set(re.findall(r'dev\.bing\.tokenmonitor\.\w+\.\w+\([A-Za-z0-9_.]+:\d+\)',transcript))))
finally:
 if 'process' in locals() and process.poll() is None:
  process.terminate()
  try:process.wait(timeout=3)
  except subprocess.TimeoutExpired:process.kill();process.wait(timeout=3)
 payload.clear()
 if originalProxy is not None:run('shell','settings','put','global','http_proxy',':0')
 if relay is not None:
  relay.shutdown();relay.server_close();relayThread.join(2)
  report['tlsRelayCounts']={'starts':relayStarts,'ends':relayEnds,'active':relayStarts-relayEnds,'payloadLogged':False,'tlsTerminated':False}
 if bridged:server.close_reads()
 server.shutdown();server.server_close();thread.join(2)
 a.output.parent.mkdir(parents=True,exist_ok=True);a.output.write_text(json.dumps(report,indent=2)+'\n')
print('V5 '+a.mode+' verification: '+report['result'])
if report['result']!='PASS':raise SystemExit(1)
