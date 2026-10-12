"""Opt-in real Hub read. Credentials stay in upstream memory; archive safe counts/results only."""
import argparse
import hashlib
import importlib.util
import re
import subprocess
import threading
import time
from pathlib import Path

parser = argparse.ArgumentParser()
parser.add_argument("--adb", required=True)
parser.add_argument("--serial", required=True)
parser.add_argument("--observe-seconds", type=int, default=75)
parser.add_argument("--case", choices=("home", "back", "network", "lock", "fixed", "charts", "usage", "details", "appearance", "shared", "vendor", "limits", "lists", "connection"), default="home")
parser.add_argument("--upstream", choices=("nas", "desktop"), default="nas")
parser.add_argument("--output", type=Path, required=True)
args = parser.parse_args()
if not args.serial.startswith("emulator-") or not 65 <= args.observe_seconds <= 110:
    raise SystemExit("Choose an explicit disposable emulator and 65..110 seconds of foreground observation")
base = [args.adb, "-s", args.serial]
package = "dev.bing.tokenmonitor.nativeclient"


def adb(*command):
    return subprocess.run(base + list(command), capture_output=True, text=True, check=True, timeout=30).stdout


sdk = int(adb("shell", "getprop", "ro.build.version.sdk").strip())
wifi_original = None
if args.case == "network":
    wifi = adb("shell", "cmd", "wifi", "status")
    wifi_original = "Wifi is enabled" in wifi or "Wi-Fi is enabled" in wifi
    if not wifi_original or adb("shell", "settings", "get", "global", "mobile_data").strip() != "1":
        raise SystemExit("Network case requires the selected emulator's existing Wi-Fi/mobile data enabled")
if args.case == "back" and sdk < 31:
    raise SystemExit("This actual Back check requires Android12/API31 or higher")
if args.case == "lock" and sdk < 28:
    raise SystemExit("Actual lock check requires Android9/API28 or higher")
if args.case == "lock" and not re.search(r"mWakefulness(?:Raw)?=Awake\b", adb("shell", "dumpsys", "power")):
    raise SystemExit("Actual lock check requires the selected emulator screen initially awake")
for apk in ("app/build/outputs/apk/debug/app-debug.apk", "app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk"):
    adb("install", "-r", apk)
digest = hashlib.sha256(Path("app/build/outputs/apk/debug/app-debug.apk").read_bytes()).hexdigest()
adb("shell", "pm", "clear", package)
if sdk >= 37:
    adb("shell", "pm", "grant", package, "android.permission.ACCESS_LOCAL_NETWORK")
spec = importlib.util.spec_from_file_location("native_live_bridge", Path(__file__).with_name("live-hub-proxy.py"))
module = importlib.util.module_from_spec(spec)
spec.loader.exec_module(module)
if args.upstream == "desktop":
    # Validate the authorized existing configuration before starting the listener.
    # No value is printed, copied or persisted; each read child authenticates itself.
    module.load_desktop_connection()
server = module.ReadBridgeServer(("127.0.0.1", 0), open_read=module.open_desktop_read if args.upstream == "desktop" else module.open_nas_read)
credential_location = "Mac upstream child memory (existing desktop configuration)" if args.upstream == "desktop" else "NAS upstream process memory"
serving = threading.Thread(target=server.serve_forever, daemon=True)
serving.start()
port = server.server_port
instrumentation = None
reader = None
received = []
stages = set()
back_destroyed = None
lock_destroyed = None
lost_transport_closed_peer = None
stage_lock = threading.Lock()


def has_stage(stage):
    with stage_lock:
        return stage in stages


def collect():
    global back_destroyed, lock_destroyed
    for line in instrumentation.stdout:
        with stage_lock:
            received.append(line)
            match = re.search(r"nativeLiveStage=(sustain|observed|fixed_verified|charts_verified|usage_verified|details_verified|appearance_verified|shared_verified|vendor_verified|limits_verified|provider_copy_verified|lists_verified|connection_verified|background|resumed|cell_https|cell_http_blocked|wifi_secure|http_reconnected)\b", line)
            if match:
                stages.add(match.group(1))
            lifecycle = re.search(r"nativeBackDestroyed=(true|false)\b", line)
            if lifecycle:
                back_destroyed = lifecycle.group(1) == "true"
            locked = re.search(r"nativeLockDestroyed=(true|false)\b", line)
            if locked:
                lock_destroyed = locked.group(1) == "true"


def wait(condition, label, seconds, allow_terminal=False):
    until = time.monotonic() + seconds
    while time.monotonic() < until:
        if condition():
            return
        if not allow_terminal and instrumentation is not None and instrumentation.poll() is not None:
            if reader is not None:
                reader.join(1)
            if condition():
                return
            raise AssertionError(label + "; instrumented run ended (private diagnostics withheld)")
        time.sleep(0.1)
    raise AssertionError(label + "; private diagnostics withheld")


try:
    test_class = {"back": "LiveBackTest", "home": "LiveHubTest", "network": "LiveNetworkTest", "lock": "LiveLockTest", "fixed": "LiveFixedPeriodTest", "charts": "LiveHomeChartTest", "usage": "LiveCurrencyTest", "details": "LiveToolDetailsTest", "appearance": "LiveAppearanceTest", "shared": "LiveSharedDataTest", "vendor": "LiveVendorTest", "limits": "LiveLimitDisplayTest", "lists": "LiveListSettingsTest", "connection": "LiveConnectionSettingsTest"}[args.case]
    instrumentation = subprocess.Popen(base + ["shell", "am", "instrument", "-w", "-e", "class", "dev.bing.tokenmonitor." + test_class,
                                                "-e", "liveHubPort", str(port), "-e", "observeLiveSeconds", str(args.observe_seconds),
                                                package + ".test/androidx.test.runner.AndroidJUnitRunner"],
                                       stdout=subprocess.PIPE, stderr=subprocess.STDOUT, text=True)
    reader = threading.Thread(target=collect, daemon=True)
    reader.start()
    wait(lambda: has_stage("sustain"), "Real Hub foreground snapshot unavailable", 30)
    if args.case == "home":
        print(f"Real NAS Hub foreground observation started ({args.observe_seconds}s); authentication stays in upstream memory", flush=True)
        wait(lambda: has_stage("observed"), "Sustained real Hub observation did not finish", args.observe_seconds + 20)
        observed = server.status()
        assert observed["streamStarts"] == 1 and observed["heartbeats"] >= 2, "Expected one stream spanning two real Hub heartbeats"
        print("Foreground sustained across multiple real heartbeats without reconnect: PASS", flush=True)
    if args.case == "network":
        wait(lambda: has_stage("cell_https"), "Actual cellular HTTPS policy unavailable", 30)
        # Native stage asserts actual old Call canceled and gone from running/queued calls.
        # Wi-Fi loss may prevent FIN reaching this host; do not equate that with native activity.
        until = time.monotonic() + 3
        while server.status()["activeReads"] and time.monotonic() < until:
            time.sleep(0.05)
        lost_transport_closed_peer = server.status()["activeReads"] == 0
        assert server.status()["requests"] == 5, "Cellular reached local Hub"
        if not lost_transport_closed_peer:
            server.close_reads()
            assert server.status()["activeReads"] == 0, "Abandoned bridge read cleanup unavailable"
        wait(lambda: has_stage("cell_http_blocked"), "Explicit HTTP was not blocked on cellular", 30)
        assert server.status()["requests"] == 5, "Explicit cellular local selection reached Hub"
        wait(lambda: has_stage("wifi_secure"), "Wi-Fi did not retain secure preference", 30)
        assert server.status()["requests"] == 5, "Wi-Fi auto-downgraded after HTTPS failure"
        wait(lambda: has_stage("http_reconnected"), "Manual LAN selection after Wi-Fi return unavailable", 30)
        assert server.status()["requests"] == 8 and server.status()["streamStarts"] == 2
        print("Actual Wi-Fi/cellular change: secure-only outside, no downgrade, manual LAN return: PASS", flush=True)
    if args.case == "fixed":
        wait(lambda: has_stage("fixed_verified"), "Actual Hub period menu/history/cache proof unavailable", 45)
        print("Real device History, actual WEEK/7D/30D menus and rendered headline/device sums: PASS", flush=True)
    if args.case == "charts":
        wait(lambda: has_stage("charts_verified"), "Actual home chart/source/cache proof unavailable", 45)
        print("Actual home chart values, source preview/history and retained encrypted cache: PASS", flush=True)
    if args.case == "usage":
        wait(lambda: has_stage("usage_verified"), "Actual Hub currency/control/cache proof unavailable", 45)
        print("Actual currency/rate controls, real headline and offline widget, retained USD source: PASS", flush=True)
    if args.case == "limits":
        wait(lambda: has_stage("limits_verified"), "Actual quota/source/privacy proof unavailable", 60)
        wait(lambda: has_stage("provider_copy_verified"), "Reported provider/account proof unavailable", 10)
        print("Actual quota mode/privacy/source and reported provider/account controls; foreground SSE/cache retained: PASS", flush=True)
    if args.case == "vendor":
        wait(lambda: has_stage("vendor_verified"), "Actual vendor/visibility/source proof unavailable", 60)
        print("Actual vendor RGB/visibility controls and foreground SSE/source retained: PASS", flush=True)
    if args.case == "shared":
        wait(lambda: has_stage("shared_verified"), "Actual shared pages/cache proof unavailable", 60)
        print("Actual shared price/subscription/model rules pages, scoped refresh and encrypted source: PASS", flush=True)
    if args.case == "lists":
        wait(lambda: has_stage("lists_verified"), "Actual list/activity/source proof unavailable", 60)
        print("Actual tool filter/pin/visibility and nested activity controls retain raw sources: PASS", flush=True)
    if args.case == "connection":
        wait(lambda: has_stage("connection_verified"), "Actual connection edit/source proof unavailable", 60)
        print("Actual first connection, rejected replacement, cancel and saved-credential editing retain raw sources: PASS", flush=True)
    if args.case == "appearance":
        wait(lambda: has_stage("appearance_verified"), "Actual appearance/source/cache proof unavailable", 45)
        print("Actual theme/independent fonts/text/chart controls and encrypted source: PASS", flush=True)
    if args.case == "details":
        wait(lambda: has_stage("details_verified"), "Actual tool footer/source/cache proof unavailable", 45)
        print("Actual tool footer modes, input/model shares and encrypted source: PASS", flush=True)
    wait(lambda: has_stage("background"), "Real Hub optional reads/background checkpoint unavailable", 30)
    wait(lambda: server.status()["activeReads"] == 0, "Background did not close upstream read processes", 3)
    paused = server.status()["requests"]
    time.sleep(0.8)
    assert server.status()["requests"] == paused and server.status()["activeStreams"] == 0, "Background issued new reads"
    print("Background closes upstream immediately and issues no reads: PASS", flush=True)
    wait(lambda: has_stage("resumed"), "Foreground Hub restore unavailable", 30)
    instrumentation.wait(timeout=30)
    reader.join(2)
    with stage_lock:
        transcript = "".join(received)
    if instrumentation.returncode != 0 or "OK (1 test)" not in transcript:
        raise AssertionError("Real Hub instrumentation did not pass; private diagnostics withheld")
    wait(lambda: server.status()["activeReads"] == 0, "Final real Hub read processes did not stop", 3, allow_terminal=True)
    final = server.status()
    expected_streams = 4 if args.case == "connection" else 2
    assert final["streamStarts"] == expected_streams and final["streamEnds"] == expected_streams
    # A destroyed Activity restores/validates the saved connection before its new SSE.
    destroyed = lock_destroyed if args.case == "lock" else back_destroyed
    # Current desktop Home also reads History once. These native checkpoints wait
    # for that foreground read before Back/lock/network actions, so it is counted.
    expected_reads = 10 if args.case == "shared" else (7 if args.case == "limits" else (6 if args.case in ("fixed", "details", "vendor", "appearance", "usage", "lists") else (7 if args.case == "network" else (8 if args.case == "home" else (7 if destroyed else 5)))))
    if args.case == "connection": expected_reads = 10
    if args.case in ("back","lock","network"):
        assert final["routes"].get("sync/settings/modelAliases")==1, "Default Home loads and retains its aliases once"
        expected_reads += 1
    history_reads=final["routes"].get("history",0)
    history_revision_changes=0
    if args.case == "lists":
        observed_changes=re.findall(r"nativeHistoryRevisionChanges=(\d+)\b",transcript)
        assert len(observed_changes)==1, "History revision observation unavailable"
        history_revision_changes=int(observed_changes[0])
        assert 1 <= history_reads <= 1+history_revision_changes, "History reread without an observed source revision change"
        expected_reads += history_reads-1
    if args.case == "home":
        # Home may read history more than once: 0.2.47 allows the bounded receipt retry, and a
        # real source revision change starts a fresh cycle at any time (this Mac keeps collecting
        # usage). Both are legitimate, so the count is bounded at 1..4; what must never happen is
        # a burst of reads for the same cycle, which the observed minimum spacing guards. Only
        # aggregate offsets are recorded, never the signature or any response content.
        assert 1 <= history_reads <= 4, f"Home history reads must stay inside the bounded receipt retry: {history_reads}"
        history_times=final.get("readTimes",{}).get("history",[])
        assert len(history_times)==history_reads, "Per-route read times unavailable for the Home history check"
        for earlier,later in zip(history_times,history_times[1:]):
            assert later-earlier>=3.5, f"Two Home history reads closer than the policy's four-second retry spacing: {history_times}"
        expected_reads += history_reads-1
    safe_routes={key:final["routes"].get(key,0) for key in ("health","stats","history","subscriptions","sync/settings/modelAliases","stats/stream")}
    assert final["requests"] == expected_reads, f"Unexpected real Hub read counts: expected={expected_reads}, actual={final['requests']}, routes={safe_routes}"
    if args.case not in ("lists","home"):
        assert final["routes"].get("history") == 1, "Expected exactly one foreground Home history read"
    duration = re.search(r"Time: ([0-9.]+)", transcript)
    if args.case == "details":
        assert final["routes"].get("sync/settings/modelAliases") == 1, "Expected one shared model rule read"
        report = (f"Android API{sdk}; final application APK SHA256: {digest}\n"
                  "Actual MainActivity/default network/client and real NAS Hub: PASS\n"
                  "Actual contextual footer Token/model switches and selected states: PASS\n"
                  "Real input shares, model grouping/counts/shares and encrypted original source: PASS\n"
                  "Home closes reads within 3s; 6s background no reads; Launcher keeps active modes: PASS\n"
                  "Exactly 6 GETs: health/stats/history/modelAliases and two streams, both ended.\n"
                  "No private values, names, responses, screenshots or UI trees archived.\n\n"
                  "dev.bing.tokenmonitor.LiveToolDetailsTest.actualFooterModesMatchRealToolSharesAndStopAllReadsInBackground\n"
                  + ("Time: " + duration.group(1) + "\n" if duration else "") + "OK (1 test)\n")
    elif args.case == "limits":
        assert expected_reads == 7
        assert final["routes"].get("sync/settings/modelAliases") == 1, "Default Home reads its model aliases once"
        assert final["routes"].get("subscriptions") == 1
        report = (f"Android API{sdk}; final application APK SHA256: {digest}\n"
                  "Actual MainActivity/default client/network and real NAS Hub: PASS\n"
                  "Quota mode/email mask/source and nested Home highlight controls, local V16 preferences and foreground SSE: PASS\n"
                  "Reported provider selection/status and masked account copy verified in actual Activity: PASS\n"
                  "Existing first metered quota complement conditional on source; visible email mask: PASS\n"
                  "Original encrypted snapshot/history/accounts/subscriptions retained without rewriting: PASS\n"
                  "Home closes reads within 3s; 6s no reads; Launcher restores local choices: PASS\n"
                  "Exactly 7 GETs: health/stats/history1/subscriptions1/aliases1, streams2; both ended. Main/rate preferences persist; rate display adds no read.\n"
                  "Private values/names/pixels/responses/screenshots/trees not archived.\n\n"
                  "dev.bing.tokenmonitor.LiveLimitDisplayTest.realQuotaControlsKeepOriginalAccountsEncryptedCacheAndSseAndRestoreAfterHome\n"
                  + ("Time: " + duration.group(1) + "\n" if duration else "") + "OK (1 test)\n")
    elif args.case == "vendor":
        assert final["routes"].get("sync/settings/modelAliases") == 1
        report = (f"Android API{sdk}; final application APK SHA256: {digest}\n"
                  "Actual MainActivity/default client/network and real NAS Hub: PASS\n"
                  "Actual vendor RGB and icon/connection-dot controls persist; SSE remains live: PASS\n"
                  "Existing real Codex bar pixels/dot conditional on source; no fabricated usage: PASS\n"
                  "Original encrypted snapshot/history/model rules retained: PASS\n"
                  "Home closes reads within 3s; 6s no reads; Launcher retains local choices: PASS\n"
                  "Exactly 6 GETs: health/stats/history/aliases1, streams2; both ended.\n"
                  "Private values/names/pixels/responses/screenshots/trees not archived.\n\n"
                  "dev.bing.tokenmonitor.LiveVendorTest.realVendorAndVisibilityControlsKeepSseSourcesAndRestoreAfterHome\n"
                  + ("Time: " + duration.group(1) + "\n" if duration else "") + "OK (1 test)\n")
    elif args.case == "shared":
        assert final["routes"].get("subscriptions") == 2 and final["routes"].get("sync/settings/customPricing") == 2 and final["routes"].get("sync/settings/modelAliases") == 1
        report = (f"Android API{sdk}; final application APK SHA256: {digest}\n"
                  "Actual MainActivity/default network/client and real NAS Hub: PASS\n"
                  "Loaded price/subscription/rule pages and optional existing record details: PASS\n"
                  "Price/subscription footer refresh reads the current shared data only: PASS\n"
                  "Original encrypted snapshot/history/prices/subscriptions/rules retained: PASS\n"
                  "Home closes reads within 3s; 6s background no reads; Launcher/cache restore: PASS\n"
                  "Exactly 10 GETs: health/stats/history, prices2, subscriptions2, aliases1, streams2.\n"
                  "Both streams ended; private names/values/responses/screenshots/trees not archived.\n\n"
                  "dev.bing.tokenmonitor.LiveSharedDataTest.realSharedPagesRefreshTheirOwnDataRetainEncryptedSourcesAndStopInBackground\n"
                  + ("Time: " + duration.group(1) + "\n" if duration else "") + "OK (1 test)\n")
    elif args.case == "connection":
        assert final["routes"].get("health")==2 and final["routes"].get("stats")==2
        assert final["routes"].get("sync/settings/modelAliases")==1
        assert final["routes"].get("subscriptions",0)==0
        report = (f"Android API{sdk}; final application APK SHA256: {digest}\n"
                  "Actual MainActivity/default network/client and real NAS Hub: PASS\n"
                  "First connection controls, status, cancel and saved-credential editor: PASS\n"
                  "Rejected public health peer receives no authorization; old binding/cache and failed draft retained: PASS\n"
                  "Credential retained inside model/store; original snapshot/history agree with real sources: PASS\n"
                  "Home closes reads within3s;6s no new reads;Launcher restores connection: PASS\n"
                  "Exactly10 GETs: health2/stats2/history1/modelAliases1/streams4;all streams ended.\n"
                  "No private value/name/response/pixels/screenshots/tree archived.\n\n"
                  "dev.bing.tokenmonitor.LiveConnectionSettingsTest.actualConnectionEditorRetainsCredentialAndRawSourcesAcrossBackground\n"
                  + ("Time: " + duration.group(1) + "\n" if duration else "") + "OK (1 test)\n")
    elif args.case == "lists":
        assert expected_reads == 5+history_reads
        assert final["routes"].get("sync/settings/modelAliases") == 1
        assert final["routes"].get("subscriptions", 0) == 0
        report = (f"Android API{sdk}; final application APK SHA256: {digest}\n"
                  "Actual MainActivity/default network/client and real NAS Hub: PASS\n"
                  "Tool pin/visibility, source combined search, clear and nested activity controls: PASS\n"
                  "Original encrypted snapshot/history and real total retained; local choices persist: PASS\n"
                  "Home closes reads within 3s; 6s no new reads; Launcher restores choices: PASS\n"
                  f"Exactly {expected_reads} GETs: health1/stats1/history{history_reads}/modelAliases1, streams2; both ended.\n"
                  f"Observed source history revision changes: {history_revision_changes}; no unaccounted history rereads.\n"
                  "No private value/name/response/pixels/screenshots/tree archived.\n\n"
                  "dev.bing.tokenmonitor.LiveListSettingsTest.realToolAndActivityControlsKeepRawSourcesAndRestoreAfterBackground\n"
                  + ("Time: " + duration.group(1) + "\n" if duration else "") + "OK (1 test)\n")
    elif args.case == "appearance":
        assert expected_reads == 6
        assert final["routes"].get("sync/settings/modelAliases") == 1
        assert final["routes"].get("subscriptions",0) == 0
        report = (f"Android API{sdk}; final application APK SHA256: {digest}\n"
                  "Actual MainActivity/default network/client and real NAS Hub: PASS\n"
                  "Actual Obsidian background pixels, independent fonts, largest text and chart colour: PASS\n"
                  "Whole-page preview/cancel, unchanged saved bytes, live SSE and background draft disposal: PASS\nSession titles/context local persistence and foreground restoration: PASS\n"
                  "Hub total, original encrypted snapshot/history and local appearance retained: PASS\n"
                  "Home closes reads within 3s; 6s background no reads; Launcher restores settings: PASS\n"
                  "Exactly 6 GETs: health/stats/history/modelAliases and two streams, both ended; settings adds no read.\n"
                  "Private values and pixels stay in device memory; no real screenshot/tree archived.\n\n"
                  "dev.bing.tokenmonitor.LiveAppearanceTest.actualAppearanceControlsKeepRealHubSourcesAndRestoreAfterBackground\n"
                  + ("Time: " + duration.group(1) + "\n" if duration else "") + "OK (1 test)\n")
    elif args.case == "usage":
        assert expected_reads == 6
        assert final["routes"].get("sync/settings/modelAliases") == 1
        assert final["routes"].get("subscriptions", 0) == 0
        report = (f"Android API{sdk}; final application APK SHA256: {digest}\n"
                  "Actual MainActivity/default network/client and real NAS Hub: PASS\n"
                  "Actual USD/CNY controls and manual rate update real usage headline: PASS\n"
                  "Offline widget uses same currency; encrypted Hub snapshot/history retains USD: PASS\n"
                  "Local currency/rate survives Home/Launcher; 6s background no reads: PASS\n"
                  "Exactly 6 GETs: health/stats/history/modelAliases and two streams, both ended; rate editing adds no read.\n"
                  "No private value, name, response, screenshot or UI tree archived.\n\n"
                  "dev.bing.tokenmonitor.LiveCurrencyTest.actualCurrencyControlsConvertRealHubAndRetainUsdCacheAcrossBackground\n"
                  + ("Time: " + duration.group(1) + "\n" if duration else "") + "OK (1 test)\n")
    elif args.case == "charts":
        assert expected_reads == 5
        report = (f"Android API{sdk}; final application APK SHA256: {digest}\n"
                  "Actual MainActivity/default network/client and real NAS Hub: PASS\n"
                  "Source preview parsed; actual activity chart today agrees with live total: PASS\n"
                  "Actual trend long-range peak agrees with source/history/live projection: PASS\n"
                  "Actual month axis and selected-day Token/cost/unpriced agree with live source: PASS\n"
                  "Presentation overlay does not rewrite retained encrypted history: PASS\n"
                  "Home closes reads within 3s; 6s background no reads; Launcher restores charts/cache: PASS\n"
                  "Exactly 5 GETs: health/stats/history and two streams, both ended.\n"
                  "No private value, name, response, screenshot or UI tree archived.\n\n"
                  "dev.bing.tokenmonitor.LiveHomeChartTest.realPreviewHistoryLiveTodayAndBackgroundCacheAgreeOnTheActualScreen\n"
                  + ("Time: " + duration.group(1) + "\n" if duration else "") + "OK (1 test)\n")
    elif args.case == "fixed":
        assert final["routes"].get("devices") == 1, "Expected one shared retained-device history read"
        report = (f"Android API{sdk}; final application APK SHA256: {digest}\n"
                  "Actual MainActivity/default network/client and real NAS Hub: PASS\n"
                  "Actual 7D/WEEK/30D menu controls, selected states and rendered totals: PASS\n"
                  "Per-device retained History/windows, headline equals device sums, projected encrypted cache: PASS\n"
                  "Home closes reads within 3s; 6s background hold has no requests: PASS\n"
                  "Launcher restores 30D preference and cached History, one new SSE: PASS\n"
                  "Exactly 6 GETs including one device History read; two streams both ended.\n"
                  "No real response, count, name, screenshot or accessibility tree archived.\n\n"
                  "dev.bing.tokenmonitor.LiveFixedPeriodTest.realDeviceHistoryAndActualMenuShareTotalsAndStopInBackground\n"
                  + ("Time: " + duration.group(1) + "\n" if duration else "") + "OK (1 test)\n")
    elif args.case == "network":
        report = (f"Android API{sdk}; final application APK SHA256: {digest}\n"
                  "Actual MainActivity/default Network callbacks, real Wi-Fi to CELLULAR transition: PASS\n"
                  "Actual old SSE Call canceled and absent from running/queued calls; cache retained: PASS\n"
                  f"Lost-transport peer closure observed within 3s: {lost_transport_closed_peer}.\n"
                  "If peer closure was unobservable, harness stopped the abandoned bridge read before reconnect.\n"
                  "Explicit local HTTP selection on cellular refused, zero extra Hub GET: PASS\n"
                  "Wi-Fi return retains HTTPS; only explicit manual HTTP reconnect reads Hub: PASS\n"
                  "Home/Launcher return prefers HTTPS again; no fallback after HTTPS failure: PASS\n"
                  f"Exactly {expected_reads} GETs (including Home history and one modelAliases read)/2 streams, both ended; original Wi-Fi and app profile restored.\n"
                  "HTTPS target is reserved .invalid failure fixture; actual Lucky/public success is unverified.\n\n"
                  "dev.bing.tokenmonitor.LiveNetworkTest.actualNetworkChangeRequiresHttpsAndOnlyManualLocalReturn\n"
                  + ("Time: " + duration.group(1) + "\n" if duration else "") + "OK (1 test)\n")
    elif args.case == "lock":
        assert lock_destroyed is not None, "Screen lock lifecycle state unavailable"
        report = (f"Android API{sdk}; final application APK SHA256: {digest}\n"
                  "Actual MainActivity/default network/client; system lock and non-interactive screen: PASS\n"
                  "Screen off closes upstream within 3s; 6s screen-off hold, zero extra GET: PASS\n"
                  "Encrypted snapshot retained; actual wake/unlock restores live connection: PASS\n"
                  f"Lock destroyed initial Activity: {lock_destroyed}; actual resumed instance verified.\n"
                  f"Exactly {expected_reads} GETs/2 streams; both ended; initially awake emulator restored.\n"
                  "No credential/PIN/security policy modified; unsecured test emulator only.\n\n"
                  "dev.bing.tokenmonitor.LiveLockTest.actualScreenLockStopsRealHubAndWakeRestoresCachedConnection\n"
                  + ("Time: " + duration.group(1) + "\n" if duration else "") + "OK (1 test)\n")
    elif args.case == "back":
        assert back_destroyed is not None, "Actual Back lifecycle state unavailable"
        report = (f"Android API{sdk}; final application APK SHA256: {digest}\n"
                  "Actual MainActivity/default network/client; system Back stops real Hub SSE: PASS\n"
                  "Background: upstream stopped within 3 seconds; 6s hold with no extra GET: PASS\n"
                  "Encrypted connection/snapshot retained; actual Launcher returns live: PASS\n"
                  f"Back destroyed initial Activity: {back_destroyed}; actual resumed instance verified.\n"
                  f"Exactly {expected_reads} GETs and 2 streams; both ended; test profile/listener cleared: PASS\n\n"
                  "dev.bing.tokenmonitor.LiveBackTest.actualBackClosesRealHubAndLauncherRestoresEncryptedConnection\n"
                  + ("Time: " + duration.group(1) + "\n" if duration else "") + "OK (1 test)\n")
    else:
        report = (f"Android API{sdk}; final application APK SHA256: {digest}\n"
              "Actual MainActivity/default network/socket factory/client; Home and Launcher return: PASS\n"
              f"Real NAS Hub foreground observation: {args.observe_seconds} seconds; PASS\n"
              "One continuous stream across at least two upstream heartbeats; no reconnect: PASS\n"
              "Real stats/device periods/history/subscriptions/modelAliases/pricing: PASS\n"
              "Background: upstream processes closed within 3 seconds; no new reads: PASS\n"
              "Foreground return: exactly one new stream; cache/widget projection matches: PASS\n"
              "Final: both streams ended, zero active read processes, test cache cleared: PASS\n"
              + (f"Home history read offsets (seconds, aggregate only): {final.get('readTimes',{}).get('history',[])}\n"
                 if args.case == "home" else "")
              + f"Real authentication remains in {credential_location}; no private responses, labels or diagnostics archived.\n\n"
              "dev.bing.tokenmonitor.LiveHubTest.readsRealNasHubThroughCredentialPreservingBridge\n"
              + ("Time: " + duration.group(1) + "\n" if duration else "") + "OK (1 test)\n")
except BaseException:
    # Only class/file/line and aggregate counters: never echo exception messages or responses.
    native_frames = re.findall(r"dev\.bing\.tokenmonitor\.\w+\.\w+\([A-Za-z0-9_.]+:\d+\)", "".join(received))
    args.output.parent.mkdir(parents=True, exist_ok=True)
    safe_durations = re.findall(r"(?m)^Time: ([0-9.]+)\r?$", "".join(received))
    safe_probes = re.findall(r"nativeConnectionProbe=([A-Za-z_,:]+)", "".join(received)) if args.case=="connection" else []
    if args.case=="network":
        safe_probes += re.findall(r"nativeOldStreamProbe=(cancelled:(?:true|false),running:(?:true|false),queued:(?:true|false))", "".join(received))
    args.output.write_text("FAILED; private diagnostics withheld\n" + "\n".join(dict.fromkeys(native_frames + safe_probes + ["JUnit seconds: "+value for value in safe_durations])) + "\n" + str(server.status()) + "\n")
    print("Live check stopped; sanitized native frames and aggregate counters saved.", flush=True)
    raise
finally:
    try:
        adb("shell", "am", "force-stop", package)
        adb("shell", "pm", "clear", package)
    finally:
        try:
            if wifi_original is not None:
                adb("shell", "svc", "wifi", "enable" if wifi_original else "disable")
            if args.case == "lock":
                adb("shell", "input", "keyevent", "KEYCODE_WAKEUP")
                if has_stage("sustain"):
                    # Native setup already confirmed this emulator has no secure keyguard.
                    adb("shell", "wm", "dismiss-keyguard")
        finally:
            try:
                if instrumentation is not None and instrumentation.poll() is None:
                    instrumentation.terminate()
                    try:
                        instrumentation.wait(timeout=3)
                    except subprocess.TimeoutExpired:
                        instrumentation.kill(); instrumentation.wait(timeout=3)
            finally:
                server.close_reads()
                server.shutdown(); server.server_close()
                serving.join(2)
                if reader is not None:
                    reader.join(2)
    assert server.status()["activeReads"] == 0, "Read process registry did not clear"
args.output.parent.mkdir(parents=True, exist_ok=True)
network_probes = re.findall(r"nativeOldStreamProbe=(cancelled:(?:true|false),running:(?:true|false),queued:(?:true|false))", "".join(received)) if args.case=="network" else []
args.output.write_text(report + f"Safe route counts: {final['routes']}\nUpstream authentication location: {credential_location}.\n" + "".join("Old stream probe: "+probe+"\n" for probe in network_probes))
print(f"Real NAS Hub {args.case} Activity/read/cache/foreground restore: PASS; temporary listener stopped, no reverse mapping used", flush=True)
