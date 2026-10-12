"""Render only public synthetic desktop fixtures on one explicitly selected emulator."""
import argparse
import hashlib
from pathlib import Path
import re
import subprocess

parser = argparse.ArgumentParser()
parser.add_argument('--serial', required=True)
parser.add_argument('--profile', choices=('dark','light','small-large-text','landscape','tablet','reduced-motion'), required=True)
parser.add_argument('--suite', choices=('desktop','tools','fixed','home','usage','details','appearance','shared','vendor','limits','layout','quotas','subscriptions','main','sessions','projectdevice','headline','settings','edits','homeorder','connection','preview','sessionprefs','transaction','management','limitsettings','sessionparity','statusmotion','averagerate','navigation','menumotion','background','buttoncolor','actionstate','historysummary','trendsmain','dashboard'), default='desktop')
parser.add_argument('--output-dir', type=Path)
parser.add_argument('--language', choices=('zh-CN','en'), default='zh-CN')
args = parser.parse_args()
if not args.serial.startswith('emulator-'):
    raise SystemExit('Visual fixtures are restricted to a disposable emulator')
root = Path(__file__).resolve().parent.parent
adb = ['/Users/bing/android-toolchain/sdk/platform-tools/adb','-s',args.serial]
package = 'dev.bing.tokenmonitor.nativeclient'
evidence = args.output_dir or root / 'evidence/redesign'
evidence.mkdir(parents=True, exist_ok=True)
def run(*command, binary=False, timeout=120):
    return subprocess.run(adb+list(command), capture_output=True, text=not binary, check=True, timeout=timeout).stdout

digest=hashlib.sha256((root/"app/build/outputs/apk/debug/app-debug.apk").read_bytes()).hexdigest()
installed_path=run("shell","pm","path",package).strip().removeprefix("package:")
assert hashlib.sha256(run("exec-out","cat",installed_path,binary=True)).hexdigest()==digest,"Install the current application before running visual interactions"

def override(kind):
    current=run('shell','wm',kind)
    match=re.search(r'Override '+kind+r': (.+)',current)
    return match.group(1).strip() if match else 'reset'
size=override('size')
density=override('density')
font=run('shell','settings','get','system','font_scale').strip()
motionKeys=('window_animation_scale','transition_animation_scale','animator_duration_scale')
motion={key:run('shell','settings','get','global',key).strip() for key in motionKeys}
dark=args.profile != 'light'
try:
    if args.profile == 'small-large-text':
        run('shell','wm','size','990x1980')
        run('shell','wm','density','440')
        run('shell','settings','put','system','font_scale','1.3')
    elif args.profile == 'landscape':
        run('shell','wm','size','2340x1080')
        run('shell','wm','density','440')
    elif args.profile == 'tablet':
        run('shell','wm','size','1600x2560')
        run('shell','wm','density','240')
    elif args.profile == 'reduced-motion':
        for key in motionKeys:
            run('shell','settings','put','global',key,'0')
    suite = {'desktop':'DesktopParityScreenTest','tools':'ToolListScreenTest','fixed':'FixedPeriodScreenTest','home':'HomeChartScreenTest','usage':'CurrencyScreenTest','details':'ToolDetailScreenTest','appearance':'AppearanceScreenTest','shared':'SharedDataScreenTest','vendor':'VendorScreenTest','limits':'LimitDisplayScreenTest','layout':'LimitLayoutScreenTest','quotas':'HomeQuotaScreenTest','subscriptions':'SubscriptionScreenTest','main':'MainDisplayScreenTest,dev.bing.tokenmonitor.HomeSessionScreenTest','sessions':'SessionListScreenTest','projectdevice':'ProjectDeviceScreenTest','headline':'HeadlineMotionScreenTest','settings':'SettingsStructureScreenTest','edits':'EditControlsScreenTest','homeorder':'HomeOrderScreenTest','connection':'ConnectionSettingsScreenTest','preview':'AppearancePreviewScreenTest','sessionprefs':'SessionSettingsScreenTest','transaction':'ConnectionTransactionScreenTest','management':'SettingsManagementScreenTest','limitsettings':'LimitSettingsScreenTest','sessionparity':'SessionParityScreenTest','statusmotion':'StatusMotionScreenTest','averagerate':'AverageRateScreenTest','navigation':'MainNavigationScreenTest','menumotion':'MenuMotionScreenTest','background':'BackgroundImageScreenTest','buttoncolor':'ButtonColorScreenTest','actionstate':'ActionStateScreenTest','historysummary':'HistorySummaryScreenTest','trendsmain':'TrendsMainScreenTest','dashboard':'DashboardParityScreenTest'}[args.suite]
    capture_suffix='phone' if args.profile in ('dark','light') else args.profile
    extra=['-e','visualLanguage',args.language,'-e','visualSuffix',capture_suffix] if args.suite=='dashboard' else []
    report=run('shell','am','instrument','-w','-e','class','dev.bing.tokenmonitor.'+suite,'-e','visualDark',str(dark).lower(),*extra,package+'.test/androidx.test.runner.AndroidJUnitRunner')
    output=evidence / ('api36-'+args.suite+'-visual-'+args.profile+'.txt')
    if ('OK (1 test)' if args.suite in ('usage','homeorder','limitsettings','buttoncolor','actionstate','historysummary') else 'OK (3 tests)' if args.suite in ('connection','sessionprefs','management','sessionparity','background','trendsmain','dashboard') else 'OK (4 tests)' if args.suite in ('main','headline') else 'OK (2 tests)') not in report:
        frames=re.findall(r'dev\.bing\.tokenmonitor\.\w+\.\w+\([A-Za-z0-9_.]+:\d+\)',report)
        output.write_text('FAILED; private diagnostics withheld\n'+'\n'.join(dict.fromkeys(frames))+'\n')
        raise SystemExit('Visual interaction failed for '+args.profile)
    digest=hashlib.sha256((root/"app/build/outputs/apk/debug/app-debug.apk").read_bytes()).hexdigest()
    installed_path=run("shell","pm","path",package).strip().removeprefix("package:")
    assert hashlib.sha256(run("exec-out","cat",installed_path,binary=True)).hexdigest()==digest,"Installed application differs from current APK"
    output.write_text("Final application APK SHA256: "+digest+"\n"+report)
    suffix='' if args.profile in ('dark','light') else '-'+args.profile
    pages = {'desktop':('home','home-charts','tools','trends','history','chart-detail','kline','devices','limits','models','projects','sessions','pricing','subscriptions','settings'), 'tools':('settings','list-pinned','list-manual','home-manual','list-hidden'), 'fixed':('range','menu','tools','devices','unavailable'), 'home':('live','day','settings'), 'usage':('models','history'),'details':('tokens','models','cost'),'appearance':('palette','chart','fonts'),'shared':('pricing','price-detail','subscriptions','subscription-detail','topup-detail','rules'),'vendor':('settings','tools','history','dots'),'limits':('settings','accounts','money','home','models','grok'),'layout':('codex','factory','antigravity','openrouter','kiro','typesafe','thirdparty','status'),'quotas':('text','bars','items','hidden','mimo','grants'),'subscriptions':('list','masked','group','account','factory','ledger'),'main':('header','live-rate','home-sessions','session-models','settings'),'sessions':('list','models','context','reviews','pages'),'projectdevice':('projects','projects-zh','home','devices'),'headline':('start','middle','end','reduced'),'settings':('overview','main','appearance','tools','shared-headers','subscriptions'),'edits':('theme','fonts','font-menu','picker','colors','currency','currency-menu'),'homeorder':('defaults','menu','hidden','moved','all','restored'),'connection':('initial','connected','editing','pending','failed','permission','host-connected','host-editing'),'preview':('page','dialog','cancel','applied','vendor'),'sessionprefs':('controls','home-hidden','list-hidden','list-restored','privacy','last-view'),'transaction':('failed','old-live','cancel','new-source'),'sessionparity':('fallback-cost','fallback-detail','fallback-tokens','aliases-list','aliases-home','motion-start','motion-middle','motion-end','motion-reduced'),'limitsettings':('overview','accounts','masked','chinese'),'background':('settings','removed','full','home'),'buttoncolor':('settings','picker','disabled'),'actionstate':('enabled','disabled','connection','background'),'historysummary':('unknown','partial','free'),'trendsmain':('day','month','total','fixed','zero','empty'),'dashboard':('overview','overview-tokens','bars-tool','bars-model','kline'),'menumotion':('start','middle','open','exit','reopen','reduced','period'),'navigation':('menu','settings','pricing','subscriptions'),'averagerate':('speed','burn','month','chinese','unavailable','updated'),'statusmotion':('spinner-start','spinner-middle','spinner-reduced','rate-start','rate-middle','rate-end','rate-reduced'),'management':('views-default','views-custom','provider-filter','provider-empty','home-options','home-critical','home-low')}[args.suite]
    for page in pages:
        name={'desktop':'desktop-','tools':'tool-','fixed':'fixed-','home':'home-','usage':'usage-','details':'detail-','appearance':'appearance-','shared':'shared-','vendor':'vendor-','limits':'limits-','layout':'layout-','quotas':'quotas-','subscriptions':'subscription-','main':'main-','sessions':'session-','projectdevice':'project-device-','headline':'headline-','settings':'settings-','edits':'edit-','homeorder':'home-order-','connection':'connection-','preview':'preview-','sessionprefs':'session-settings-','transaction':'transaction-','sessionparity':'session-parity-','statusmotion':'status-motion-','averagerate':'average-rate-','background':'background-','buttoncolor':'button-color-','actionstate':'action-state-','historysummary':'history-summary-','trendsmain':'trends-main-','dashboard':'dashboard-','menumotion':'menu-motion-','navigation':'navigation-','limitsettings':'limit-settings-','management':'management-'}[args.suite]+page+'-'+('dark' if dark else 'light')+'.png'
        if args.suite=='dashboard': name=f'dashboard-{page}-{args.language}-'+('dark' if dark else 'light')+f'-{capture_suffix}.png'
        pixels=run('exec-out','cat','/sdcard/Android/data/'+package+'/files/'+name,binary=True)
        if not pixels.startswith(b'\x89PNG\r\n\x1a\n'):
            raise SystemExit('Synthetic PNG missing')
        (evidence/(name if args.suite=='dashboard' else name.removesuffix('.png')+suffix+'.png')).write_bytes(pixels)
    print('Synthetic desktop pages and interactions: '+args.profile+' PASS',flush=True)
finally:
    run('shell','wm','size',size)
    run('shell','wm','density',density)
    if font not in ('null',''):
        run('shell','settings','put','system','font_scale',font)
    for key,value in motion.items():
        if value not in ('null',''):
            run('shell','settings','put','global',key,value)
