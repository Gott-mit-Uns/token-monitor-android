"""Seal a source-backed UI handoff after tests and public renders have completed."""
import argparse
from pathlib import Path
import hashlib
import json
import math
import os
import re
import shutil
import subprocess
import xml.etree.ElementTree as E
import zipfile
from dashboard_evidence import validate_dashboard_component_index, validate_dashboard_runtime, validate_dashboard_visual_index

p=argparse.ArgumentParser()
p.add_argument('--evidence-dir',required=True)
p.add_argument('--version',required=True)
p.add_argument('--code',type=int,required=True)
p.add_argument('--previous-version',required=True)
p.add_argument('--serial',required=True)
p.add_argument('--visual-reports',type=int,default=8)
p.add_argument('--visual-pngs',type=int,default=46)
p.add_argument('--source-captures',type=int,default=26)
p.add_argument('--unit-tests',type=int,default=143)
p.add_argument('--android-tests',type=int,default=129)
p.add_argument('--motion-evidence',choices=('frames','runtime','regression'),default='regression',help='Ordinary behavior regressions (default), legacy frames, or real Window metrics')
p.add_argument('--runtime-evidence-dir',default='desktop-number-runtime',help='Public runtime evidence directory under evidence/')
a=p.parse_args()
assert a.serial=='emulator-5556','Only the owned disposable test emulator is authorized'
root=Path(__file__).resolve().parent.parent
final=root/'evidence'/a.evidence_dir/'final'; source=final.parent/'source'
assert final.resolve().is_relative_to(root/'evidence')
apk=root/'app/build/outputs/apk/debug/app-debug.apk'
previous=root/'dist'/f'token-monitor-native-{a.previous_version}.apk'
digest=hashlib.sha256(apk.read_bytes()).hexdigest()
reports=list((final/'visuals').rglob('api36-*.txt'))
assert len(reports)==a.visual_reports and len(list((final/'visuals').rglob('*.png')))==a.visual_pngs
visual_suite_rules={'historysummary':dict(testClass='HistorySummaryScreenTest',tests=1,prefix='history-summary-',pages=('unknown','partial','free')), 'trendsmain':dict(testClass='TrendsMainScreenTest',tests=3,prefix='trends-main-',pages=('day','month','total','fixed','zero','empty'))}
registered_visual_suites=[]
for report in reports:
    text=report.read_text()
    assert digest in text and re.search(r'OK \([1-9]\d* tests?\)',text)
    match=re.fullmatch(r'api36-([a-z]+)-visual-([a-z-]+)\.txt',report.name)
    if match and match[1] in visual_suite_rules:
        rule=visual_suite_rules[match[1]];profile=match[2]
        assert profile in ('dark','light','small-large-text','landscape','tablet','reduced-motion')
        assert 'dev.bing.tokenmonitor.'+rule['testClass'] in text
        count=int(re.search(r'OK \((\d+) tests?\)',text).group(1));assert count==rule['tests']
        tone='light' if profile=='light' else 'dark'
        suffix='' if profile in ('dark','light') else '-'+profile
        images=[rule['prefix']+page+'-'+tone+suffix+'.png' for page in rule['pages']]
        for image in images:assert (report.parent/image).read_bytes().startswith(b'\x89PNG\r\n\x1a\n')
        registered_visual_suites.append(dict(suite=match[1],testClass=rule['testClass'],tests=count,profile=profile,images=images))
motion=dict(mode=a.motion_evidence)
if a.motion_evidence=='frames':
    assert digest in (final/'motion-frames/rendered-frames.txt').read_text()
    assert len(list((final/'motion-frames').glob('headline-frame-*.png')))==33
    motion['frames']=33
elif a.motion_evidence=='runtime':
    runtime_path=root/'evidence'/a.runtime_evidence_dir/'after.json'
    assert runtime_path.resolve().is_relative_to(root/'evidence')
    runtime=json.loads(runtime_path.read_text())
    assert runtime['apkSha256']==digest,'Runtime metrics must come from the final APK'
    assert runtime['clock']=='real Android Choreographer; no Compose test clock'
    assert 'emulator' in runtime['scope'].lower()
    assert isinstance(runtime['refreshRateHz'],(int,float)) and math.isfinite(runtime['refreshRateHz']) and runtime['refreshRateHz']>0
    assert isinstance(runtime['runs'],list) and len(runtime['runs'])==3
    for run in runtime['runs']:
        for key in ('frames','hubCompositions','totalCompositions','metricsCallbacksDropped','framesOverDeadline'):
            assert type(run[key]) is int and run[key]>=0
        assert run['frames']>=5 and run['hubCompositions']<=5 and run['totalCompositions']>=10
        assert run['framesOverDeadline']<=run['frames']
        assert run['metricsComplete'] is True and run['metricsCallbacksDropped']==0,'Window metrics must be complete'
        for key in ('observedVsyncSpanMs','sampleWindowMs','compositionObservationMs'):
            assert isinstance(run[key],(int,float)) and math.isfinite(run[key]) and run[key]>0
        assert run['observedVsyncSpanMs']<=run['sampleWindowMs']<=run['compositionObservationMs']
        assert isinstance(run['windowDrawCadenceHz'],(int,float)) and math.isfinite(run['windowDrawCadenceHz']) and run['windowDrawCadenceHz']>0
        assert math.isclose(run['windowDrawCadenceHz'],(run['frames']-1)*1000/run['observedVsyncSpanMs'],rel_tol=1e-6)
        for key in ('vsyncGapMs','totalMs','layoutMs','drawMs','deadlineMs','lateVsyncMs','unknownDelayMs'):
            values=[run[key][stat] for stat in ('p50','p95','max')]
            assert all(isinstance(value,(int,float)) and math.isfinite(value) and value>=0 for value in values)
            assert values==sorted(values)
    motion.update(runtimeRuns=3,runtimeEvidence=str(runtime_path.relative_to(root)),runtimeEvidenceSha256=hashlib.sha256(runtime_path.read_bytes()).hexdigest(),scope='Owned emulator Window metrics; not physical-device presentation FPS')
else:
    expected={
        'homeCountChangesInterruptAndPeriodChangeUsesDesktopTiming',
        'androidReducedMotionSettlesMidAnimationAndExactCountsNeverLoseLastDigit',
        'publicRenderedFramesAndBackgroundReturnSettleWithoutReplaying',
        'actualAndroidAnimationScaleIsRespectedWithoutAnOverride',
    }
    cases=[]
    for file in (root/'app/build/outputs/androidTest-results/connected').rglob('TEST-*.xml'):
        for case in E.parse(file).getroot().iter('testcase'):
            if case.get('classname','').rsplit('.',1)[-1]=='HeadlineMotionScreenTest':
                assert all(case.find(tag) is None for tag in ('failure','error','skipped'))
                cases.append(case.get('name'))
    assert len(cases)==4 and set(cases)==expected,'All four ordinary headline behavior methods must pass'
    motion.update(behaviorTests=4,behaviorMethods=sorted(expected),behaviorReport='android-tests.xml',fpsMeasured=False,scope='Ordinary Android behavior regressions; no FPS measurement in this build')
reference=json.loads((source/'full-renderer-reference.json').read_text())
assert not reference['errors'] and not reference['failedRequests']
assert len(reference['captures'])==a.source_captures and len(list(source.glob('desktop-full-*.png')))==a.source_captures
ref=root/'../work/token-monitor-reference/desktop-v0.68.0'
for entry in reference['sourceFiles']:assert hashlib.sha256((ref/entry['file']).read_bytes()).hexdigest()==entry['sha256']
dashboard_source=None
dashboard_reference=source/'dashboard-renderer-reference.json'
if dashboard_reference.is_file():
    dashboard_source=json.loads(dashboard_reference.read_text())
    assert dashboard_source['sourceCommit']==reference['sourceCommit']
    assert not dashboard_source['errors'] and not dashboard_source['blocked']
    assert len(dashboard_source['captures'])==40
    assert len({entry['image'] for entry in dashboard_source['captures']})==40
    for entry in dashboard_source['captures']:
        file=source/entry['image']
        assert file.resolve().is_relative_to(source.resolve())
        assert file.read_bytes().startswith(b'\x89PNG\r\n\x1a\n')
        assert hashlib.sha256(file.read_bytes()).hexdigest()==entry['sha256']
        if entry['view']=='kline':
            style=entry['candleBodyStyle']
            assert style['strokeWidth']=='1px' and style['stroke']==style['fill'] and style['transformBox']=='fill-box'
    for entry in dashboard_source['sourceFiles']:
        assert hashlib.sha256((ref/entry['file']).read_bytes()).hexdigest()==entry['sha256']
    visual_index=json.loads((final/'dashboard-visual-index.json').read_text())
    validate_dashboard_visual_index(visual_index,final,digest)
    assert visual_index['apkSha256']==digest
    assert len(visual_index['runs'])==8
    assert {(run['language'],run['profile'],run['tone']) for run in visual_index['runs']} == {
        ('zh-CN',profile,'light' if profile=='light' else 'dark')
        for profile in ('dark','light','small-large-text','landscape','tablet','reduced-motion')
    } | {('en','dark','dark'),('en','light','light')}
    for entry in visual_index['runs']:
        report=final/entry['report']
        assert report.resolve().is_relative_to(final.resolve())
        text=report.read_text()
        assert digest in text and 'dev.bing.tokenmonitor.DashboardParityScreenTest' in text
        assert re.findall(r'^OK \((\d+) tests?\)$',text,re.MULTILINE)==['3']
        assert hashlib.sha256(report.read_bytes()).hexdigest()==entry['reportSha256']
        assert len(entry['images'])==5
        assert {image['view'] for image in entry['images']} == {'overview','overview-tokens','bars-tool','bars-model','kline'}
        for image in entry['images']:
            file=final/image['path']
            assert file.resolve().is_relative_to(final.resolve()) and file.read_bytes().startswith(b'\x89PNG\r\n\x1a\n')
            assert hashlib.sha256(file.read_bytes()).hexdigest()==image['sha256']
    dashboard_runtime_path=final/'dashboard-window-diagnostics.json'
    dashboard_runtime=json.loads(dashboard_runtime_path.read_text())
    validate_dashboard_runtime(dashboard_runtime,digest)
    runtime_report=(final/'dashboard-window-runtime.txt').read_text()
    assert digest in runtime_report and 'dev.bing.tokenmonitor.DashboardRuntimeBenchmarkTest' in runtime_report
    assert re.findall(r'^OK \((\d+) tests?\)$',runtime_report,re.MULTILINE)==['1']
    component_index_path=final/'dashboard-component-index.json'
    component_index=json.loads(component_index_path.read_text())
    validate_dashboard_component_index(component_index,final,digest)
for base,name,count in [(root/'app/build/test-results/testDebugUnitTest','unit-tests.xml',a.unit_tests),(root/'app/build/outputs/androidTest-results/connected','android-tests.xml',a.android_tests)]:
    cases=[]
    for file in base.rglob('TEST-*.xml'):
        for case in E.parse(file).getroot().iter('testcase'):
            assert case.find('failure') is None and case.find('error') is None and case.find('skipped') is None
            cases.append({k:v for k,v in case.attrib.items() if k in ['name','classname','time']})
    assert len(cases)==count
    report=E.Element('testsuite',dict(tests=str(count),failures='0',errors='0',apkSha256=digest))
    for case in cases:E.SubElement(report,'testcase',case)
    E.indent(report);E.ElementTree(report).write(final/name,encoding='utf-8',xml_declaration=True)
bt=Path('/Users/bing/android-toolchain/sdk/build-tools/37.0.0')
env={**os.environ,'JAVA_HOME':'/Users/bing/android-toolchain/jdk-17.0.20.1+1/Contents/Home'}
def signer(file):
    result=subprocess.run([str(bt/'apksigner'),'verify','--print-certs',str(file)],capture_output=True,text=True,check=True,env=env)
    return re.findall(r'certificate SHA-256 digest: (\w+)',result.stdout)
assert signer(apk)==signer(previous) and signer(apk)
def aapt(kind,file):return subprocess.run([str(bt/'aapt'),'dump',kind,str(file)],capture_output=True,text=True,check=True).stdout
manifest=[line for line in aapt('badging',apk).splitlines() if line.startswith(('package:','sdkVersion:','targetSdkVersion:'))]
assert f"versionCode='{a.code}'" in manifest[0] and f"versionName='{a.version}'" in manifest[0]
assert aapt('permissions',apk)==aapt('permissions',previous)
with zipfile.ZipFile(apk) as current,zipfile.ZipFile(previous) as old:
    assets=sorted(n for n in current.namelist() if n.startswith('assets/'))
    assert len(assets)==4 and assets==sorted(n for n in old.namelist() if n.startswith('assets/'))
    for name in assets:assert current.read(name)==old.read(name)
resources=[{(n.tag,n.get('name')) for n in E.parse(root/'app/src/main/res'/folder/'strings.xml').getroot() if n.get('name')} for folder in ['values','values-zh']]
assert resources[0]==resources[1]
test=root/'app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk'
optional=[]; optional_by_source={}
for folder in ['liveTest','permissionTest','perfTest']:
    names=[]
    for file in (root/'app/src'/folder).rglob('*.kt'):names+=re.findall(r'\bclass\s+(\w+Test)\b',file.read_text())
    optional_by_source[folder]=names;optional+=names
with zipfile.ZipFile(test) as z:dex=b''.join(z.read(n) for n in z.namelist() if re.fullmatch(r'classes\d*\.dex',n))
assert len(optional_by_source['liveTest'])+len(optional_by_source['permissionTest'])==19
assert len(optional)==19+len(optional_by_source['perfTest'])
for name in optional:assert ('Ldev/bing/tokenmonitor/'+name+';').encode() not in dex
base=['/Users/bing/android-toolchain/sdk/platform-tools/adb','-s',a.serial];pkg='dev.bing.tokenmonitor.nativeclient'
def adb(*args,check=True):return subprocess.run(base+list(args),capture_output=True,check=check)
installed=adb('shell','pm','path',pkg).stdout.decode().strip().removeprefix('package:')
assert hashlib.sha256(adb('exec-out','cat',installed).stdout).hexdigest()==digest
assert 'Success' in adb('shell','pm','clear',pkg).stdout.decode()
adb('shell','am','force-stop',pkg);adb('uninstall',pkg+'.test')
assert not adb('shell','pidof',pkg,check=False).stdout.strip()
assert not adb('exec-out','run-as',pkg,'sh','-c','if [ -d no_backup ]; then find no_backup -type f; fi').stdout.strip()
settings={}
for namespace,key,expected in [('system','font_scale',1),('global','window_animation_scale',1),('global','transition_animation_scale',1),('global','animator_duration_scale',0)]:
    value=float(adb('shell','settings','get',namespace,key).stdout.decode().strip());assert value==expected;settings[key]=value
for kind in ['size','density']:assert 'Override' not in adb('shell','wm',kind).stdout.decode()
out=root/'dist'/f'token-monitor-native-{a.version}.apk'
assert not out.exists() or hashlib.sha256(out.read_bytes()).hexdigest()==digest,'Refusing to replace a different sealed APK'
shutil.copy2(apk,out);out.with_suffix('.apk.sha256').write_text(digest+'  '+out.name+'\n')
sums=root/'dist/SHA256SUMS';lines=[line for line in sums.read_text().splitlines() if not line.endswith('  '+out.name)];lines.append(digest+'  '+out.name)
for line in lines:
    expected,name=line.split('  ',1);assert hashlib.sha256((root/'dist'/name).read_bytes()).hexdigest()==expected
sums.write_text('\n'.join(lines)+'\n')
build=dict(apkSha256=digest,version=a.version,code=a.code,bytes=apk.stat().st_size,unitTests=a.unit_tests,androidTests=a.android_tests,pairedResources=len(resources[0]),assetsUnchanged=assets,archives=len(lines),motionEvidence=motion,registeredVisualSuites=registered_visual_suites)
if dashboard_source is not None:
    build['dashboardSource']=dict(captures=40,sourceFiles=len(dashboard_source['sourceFiles']),
        referenceSha256=hashlib.sha256(dashboard_reference.read_bytes()).hexdigest(),
        nativeRuns=len(visual_index['runs']),nativeCaptures=sum(len(entry['images']) for entry in visual_index['runs']))
    build['dashboardWindowMetrics']=dict(scenarios=3,runs=9,physicalFpsMeasured=False,
        reportSha256=hashlib.sha256(dashboard_runtime_path.read_bytes()).hexdigest(),scope=dashboard_runtime['scope'])
    build['dashboardComponents']=dict(captures=len(component_index['images']),behaviorTests=8,
        reportSha256=component_index['reportSha256'],indexSha256=hashlib.sha256(component_index_path.read_bytes()).hexdigest())
(final/'build-checks.json').write_text(json.dumps(build,indent=2)+'\n')
package=dict(apkSha256=digest,signatureVerified=True,sameSigningCertificate=True,manifest=manifest,permissionsUnchangedFrom=a.previous_version,optionalClassesExcluded=optional,optionalClassesBySource=optional_by_source,ordinaryTestApkSha256=hashlib.sha256(test.read_bytes()).hexdigest(),installedFinalApkHashMatched=True,ownedEmulatorCleared=True,testPackageRemoved=True,noBackupFiles=0,restoredSettings=settings,physicalDevice='NOT VERIFIED',directNAS='NOT VERIFIED',LuckyHTTPS='NOT VERIFIED',networkAndUpgradeNotRerun=True,motionEvidence=motion)
(final/'package-verification.json').write_text(json.dumps(package,indent=2)+'\n')
(final/'archive-checks.txt').write_text('APK SHA256: '+digest+'\n'+str(len(lines))+' APK archives independently checked\n')
print(json.dumps(build))
