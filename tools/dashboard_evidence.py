"""Validate current public Dashboard Window diagnostics without making a physical FPS claim."""
import math
import hashlib
import re
from pathlib import Path


def validate_dashboard_component_index(index, final, digest):
    """Bind independent public component images to their eight actual successful methods."""
    assert index['schemaVersion'] == 1 and index['apkSha256'] == digest
    assert index['report'] == 'dashboard-components-render.txt'
    report = final / index['report']
    assert report.resolve().is_relative_to(final.resolve())
    text = report.read_text()
    assert re.findall(r'^Application APK SHA256: ([0-9a-f]{64})$', text, re.MULTILINE) == [digest]
    assert re.findall(r'^OK \((\d+) tests?\)$', text, re.MULTILINE) == ['8']
    assert not re.search(r'^(?:FAILURES!!!|INSTRUMENTATION_FAILED:|Error in )', text, re.MULTILINE)
    expected_methods = {
        ('dev.bing.tokenmonitor.DashboardOverviewScreenTest', name) for name in (
            'lifetimeCardsAndAllRetainedTopFiveUseSeparateDenominators',
            'metricRepaintsActualCellsAndDaySelectionPreservesRawTinyCost',
            'summaryUnknownFreePartialAndCurrencyChangesRemainVisible',
            'summaryAdaptsTwoFourEightColumnsAndNarrowLargeFontKeepsBounds',
            'emptyDailyAndMonthlyOnlyKeepNeutralYearAndNoInventedLifetime')
    } | {
        ('dev.bing.tokenmonitor.DashboardOverviewMotionScreenTest', name) for name in (
            'sourceEntryDoubleFrameHeatAnd800RankingPaintWithoutOwnerFrameReads',
            'updateUsesPaintedGeometryAndSettingsReturnDoesNotReplayEntry',
            'unknownLayoutBusyBackgroundAndLiveReduceHaveExplicitBarriers')
    }
    finished, bundle = [], {}
    for line in text.splitlines():
        field = re.fullmatch(r'INSTRUMENTATION_STATUS: (class|test|numtests)=(.*)', line)
        if field:
            bundle[field[1]] = field[2]
        status = re.fullmatch(r'INSTRUMENTATION_STATUS_CODE: (-?\d+)', line)
        if status:
            assert status[1] in ('0', '1'), 'All component methods must complete successfully'
            assert bundle.get('numtests') == '8'
            method = (bundle.get('class'), bundle.get('test'))
            assert method in expected_methods
            if status[1] == '0':
                finished.append(method)
            bundle = {}
    assert len(finished) == 8 and set(finished) == expected_methods
    assert hashlib.sha256(report.read_bytes()).hexdigest() == index['reportSha256']
    expected_names = {
        f'dashboard-overview-{name}-dark.png' for name in
        ('lifetime', 'metric', 'unpriced', 'unknown', 'narrow-large-font', 'monthly-only')
    } | {
        f'dashboard-overview-motion-{name}-dark.png' for name in
        ('entry', 'middle', 'end', 'update-and-reentry', 'background-reduced')
    }
    components = final / 'components'
    assert {path.name for path in components.glob('dashboard-*.png')} == expected_names
    images = index['images']
    assert len(images) == 11
    seen = set()
    for image in images:
        file = final / image['path']
        assert file.parent == components and file.resolve().is_relative_to(components.resolve())
        assert file.name in expected_names and file.resolve() not in seen
        seen.add(file.resolve())
        pixels = file.read_bytes()
        assert pixels.startswith(b'\x89PNG\r\n\x1a\n')
        assert hashlib.sha256(pixels).hexdigest() == image['sha256']
    assert len(seen) == 11


def validate_dashboard_visual_index(index, final, digest):
    assert index['apkSha256'] == digest
    runs = index['runs']
    assert len(runs) == 8
    assert {(run['language'], run['profile'], run['tone']) for run in runs} == {
        ('zh-CN', profile, 'light' if profile == 'light' else 'dark')
        for profile in ('dark', 'light', 'small-large-text', 'landscape', 'tablet', 'reduced-motion')
    } | {('en', 'dark', 'dark'), ('en', 'light', 'light')}
    reports, images = set(), set()
    views = {'overview', 'overview-tokens', 'bars-tool', 'bars-model', 'kline'}
    for run in runs:
        report = final / run['report']
        assert report.resolve().is_relative_to(final.resolve())
        assert report.resolve() not in reports
        reports.add(report.resolve())
        assert report.name == f"api36-dashboard-visual-{run['profile']}.txt"
        text = report.read_text()
        assert digest in text and 'dev.bing.tokenmonitor.DashboardParityScreenTest' in text
        assert re.findall(r'^OK \((\d+) tests?\)$', text, re.MULTILINE) == ['3']
        assert hashlib.sha256(report.read_bytes()).hexdigest() == run['reportSha256']
        assert len(run['images']) == 5 and {image['view'] for image in run['images']} == views
        suffix = 'phone' if run['profile'] in ('dark', 'light') else run['profile']
        for image in run['images']:
            file = final / image['path']
            assert file.resolve().is_relative_to(final.resolve()) and file.parent == report.parent
            assert file.resolve() not in images
            images.add(file.resolve())
            assert file.name == f"dashboard-{image['view']}-{run['language']}-{run['tone']}-{suffix}.png"
            assert file.read_bytes().startswith(b'\x89PNG\r\n\x1a\n')
            assert hashlib.sha256(file.read_bytes()).hexdigest() == image['sha256']
    assert len(reports) == 8 and len(images) == 40


def validate_dashboard_runtime(report, digest):
    def number(value, upper=60_000, positive=False):
        assert type(value) in (int, float) and math.isfinite(value) and 0 <= value <= upper
        if positive:
            assert value > 0

    assert report['schemaVersion'] == 1 and report['apkSha256'] == digest
    assert report['clock'] == 'real Android Choreographer; no Compose test clock'
    assert report['monotonicClock'] == 'System.nanoTime' and report['emulatorBuildObserved'] is True
    assert report['actualRootNavigationMeasured'] is False and report['physicalFpsMeasured'] is False
    assert report['dashboardOverviewMotionMeasured'] is False and 'Owned emulator' in report['scope']
    number(report['refreshRateHz'], 1000, positive=True)
    scenes = report['scenarios']
    assert len(scenes) == 3 and len({scene['name'] for scene in scenes}) == 3
    assert sorted((scene['retainedDailyRows'], scene['attributionKeysPerDimension'], scene['mode'])
                  for scene in scenes) == [(30, 8, 'BARS'), (366, 80, 'BARS'), (366, 80, 'KLINE')]
    for scene in scenes:
        assert scene['warmupUpdates'] == 2 and scene['recordedUpdates'] == 3 and scene['durationScale'] == 1
        assert scene['animationBaseMs'] == (800 if scene['mode'] == 'BARS' else 560)
        assert len(scene['runs']) == 3
        for run in scene['runs']:
            for field in ('frames', 'framesOverDeadline', 'dashboardOwnerCompositions',
                          'metricsCallbacksDropped', 'invalidMetricSamples'):
                assert type(run[field]) is int and 0 <= run[field] <= 1_000_000
            assert run['frames'] >= 5 and run['framesOverDeadline'] <= run['frames']
            assert run['dashboardOwnerCompositions'] <= 5
            assert run['metricsComplete'] is True and run['metricsCallbacksDropped'] == run['invalidMetricSamples'] == 0
            assert run['animationObservedRunning'] is True and run['animationSettled'] is True
            for field in ('observedVsyncSpanMs', 'sampleWindowMs', 'compositionObservationMs'):
                number(run[field], positive=True)
            assert run['observedVsyncSpanMs'] <= run['sampleWindowMs'] <= run['compositionObservationMs']
            number(run['windowDrawCadenceHz'], 1000, positive=True)
            assert math.isclose(run['windowDrawCadenceHz'],
                                (run['frames'] - 1) * 1000 / run['observedVsyncSpanMs'], rel_tol=1e-6)
            number(run['targetCommitDelayMs'])
            assert run['targetCommitDelayMs'] < run['sampleWindowMs']
            assert type(run['postCommitFrames']) is int and 5 <= run['postCommitFrames'] <= run['frames']
            number(run['postCommitVsyncSpanMs'], positive=True)
            assert run['postCommitVsyncSpanMs'] <= run['observedVsyncSpanMs']
            number(run['postCommitWindowDrawCadenceHz'], 1000, positive=True)
            assert math.isclose(run['postCommitWindowDrawCadenceHz'],
                                (run['postCommitFrames'] - 1) * 1000 / run['postCommitVsyncSpanMs'], rel_tol=1e-6)
            for field in ('vsyncGapMs', 'lateVsyncMs', 'totalMs', 'deadlineMs',
                          'unknownDelayMs', 'layoutMs', 'drawMs', 'postCommitVsyncGapMs'):
                values = [run[field][stat] for stat in ('p50', 'p95', 'max')]
                for value in values:
                    number(value)
                assert values == sorted(values)
