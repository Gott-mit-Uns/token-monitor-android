# -*- coding: utf-8 -*-
"""Gallery of public source captures and actual native renders; no fabricated UI."""
from pathlib import Path
import json
import re
import argparse
import hashlib
import math
import os
import xml.etree.ElementTree as E
from html import escape
from dashboard_evidence import validate_dashboard_component_index, validate_dashboard_runtime, validate_dashboard_visual_index


def validate_current_window_report(report, digest):
    """Bound one current public Window report without requiring a before comparison."""
    def number(value, upper, positive=False):
        assert type(value) in (int, float) and 0 <= value <= upper and math.isfinite(value)
        if positive:
            assert value > 0

    assert isinstance(report['apkSha256'], str) and re.fullmatch(r'[0-9a-f]{64}', report['apkSha256'])
    assert report['apkSha256'] == digest, 'Current Window metrics must come from this APK'
    assert report['clock'] == 'real Android Choreographer; no Compose test clock'
    assert isinstance(report['scope'], str) and 'emulator' in report['scope'].lower()
    number(report['refreshRateHz'], 1000, positive=True)
    assert isinstance(report['runs'], list) and len(report['runs']) == 3
    for run in report['runs']:
        for key in ('frames', 'hubCompositions', 'totalCompositions', 'metricsCallbacksDropped', 'framesOverDeadline'):
            assert type(run[key]) is int and 0 <= run[key] <= 1_000_000
        assert run['frames'] >= 5 and run['framesOverDeadline'] <= run['frames']
        assert run['hubCompositions'] <= 5 and run['totalCompositions'] >= 10
        assert run['metricsComplete'] is True and run['metricsCallbacksDropped'] == 0, 'Window metrics must be complete'
        for key in ('observedVsyncSpanMs', 'sampleWindowMs', 'compositionObservationMs'):
            number(run[key], 60_000, positive=True)
        assert run['observedVsyncSpanMs'] <= run['sampleWindowMs'] <= run['compositionObservationMs']
        number(run['windowDrawCadenceHz'], 1000, positive=True)
        assert math.isclose(run['windowDrawCadenceHz'], (run['frames'] - 1) * 1000 / run['observedVsyncSpanMs'], rel_tol=1e-6)
        for key in ('vsyncGapMs', 'totalMs', 'layoutMs', 'drawMs', 'deadlineMs', 'lateVsyncMs', 'unknownDelayMs'):
            values = [run[key][stat] for stat in ('p50', 'p95', 'max')]
            for value in values:
                number(value, 60_000)
            assert values == sorted(values)


def validate_main_journey_report(report, digest):
    hashes = re.findall(r'^Application APK SHA256: ([0-9a-f]{64})$', report, re.MULTILINE)
    assert hashes == [digest], 'Main journey samples must come from this APK'
    assert re.findall(r'^OK \((\d+) tests?\)$', report, re.MULTILINE) == ['1']
    assert 'dev.bing.tokenmonitor.TrendsMotionNavigationScreenTest:' in report
    assert not re.search(r'^(?:FAILURES!!!|INSTRUMENTATION_FAILED:|Error in )', report, re.MULTILINE)

parser=argparse.ArgumentParser()
parser.add_argument('--evidence-dir',default='full-renderer')
parser.add_argument('--version',default='0.2.33')
parser.add_argument('--previous-version',default='0.2.32')
parser.add_argument('--previous-evidence-dir',default='tool-settings-controls')
parser.add_argument('--compare-page',default='tools')
parser.add_argument('--focused-label',default='工具详情')
parser.add_argument('--motion-evidence',choices=('frames','runtime','regression'),default='regression')
parser.add_argument('--runtime-evidence-dir',default='desktop-number-runtime')
args=parser.parse_args()

root = Path(__file__).resolve().parent.parent
final = root / 'evidence' / args.evidence_dir / 'final'
reference = json.loads((final.parent / 'source/full-renderer-reference.json').read_text())
build=json.loads((final/'build-checks.json').read_text())
digest=build['apkSha256']
reports = list((final / 'visuals').rglob('api36-*.txt'))
pngs = list((final / 'visuals').rglob('*.png'))
for report in reports:
    assert digest in report.read_text() and re.search(r'OK \([1-9]\d* tests?\)',report.read_text())
runtime_reports={};runtime_links={};headline_behavior_methods=[]
if args.motion_evidence=='frames':
    assert digest in (final / 'motion-frames/rendered-frames.txt').read_text()
    assert len(list((final / 'motion-frames').glob('headline-frame-*.png'))) == 33
elif args.motion_evidence=='runtime':
    runtime_dir=root/'evidence'/args.runtime_evidence_dir
    assert runtime_dir.resolve().is_relative_to(root/'evidence')
    for phase in ('before','after'):
        path=runtime_dir/(phase+'.json')
        report=json.loads(path.read_text())
        assert re.fullmatch(r'[0-9a-f]{64}',report['apkSha256'])
        assert report['clock']=='real Android Choreographer; no Compose test clock'
        assert 'emulator' in report['scope'].lower()
        assert isinstance(report['refreshRateHz'],(int,float)) and math.isfinite(report['refreshRateHz']) and report['refreshRateHz']>0
        assert isinstance(report['runs'],list) and len(report['runs'])==3
        for run in report['runs']:
            for key in ('frames','hubCompositions','totalCompositions','metricsCallbacksDropped','framesOverDeadline'):
                assert type(run[key]) is int and run[key]>=0
            assert run['frames']>=5 and run['framesOverDeadline']<=run['frames']
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
            if phase=='after':assert run['hubCompositions']<=5 and run['totalCompositions']>=10
        runtime_reports[phase]=report
        runtime_links[phase]=Path(os.path.relpath(path,final)).as_posix()
    assert runtime_reports['after']['apkSha256']==digest,'Runtime metrics must come from this APK'
    assert runtime_reports['before']['fixture']==runtime_reports['after']['fixture'],'Compare the same public workload'
    if build.get('motionEvidence'):
        assert build['motionEvidence']['mode']=='runtime'
        assert build['motionEvidence']['runtimeEvidenceSha256']==hashlib.sha256((runtime_dir/'after.json').read_bytes()).hexdigest()
else:
    report=E.parse(final/'android-tests.xml').getroot()
    assert report.get('apkSha256')==digest,'Behavior regression report must be sealed for this APK'
    cases=list(report.iter('testcase'))
    assert len(cases)==build['androidTests']
    for case in cases:assert all(case.find(tag) is None for tag in ('failure','error','skipped'))
    headline_behavior_methods=[case.get('name') for case in cases if case.get('classname','').rsplit('.',1)[-1]=='HeadlineMotionScreenTest']
    expected={
        'homeCountChangesInterruptAndPeriodChangeUsesDesktopTiming',
        'androidReducedMotionSettlesMidAnimationAndExactCountsNeverLoseLastDigit',
        'publicRenderedFramesAndBackgroundReturnSettleWithoutReplaying',
        'actualAndroidAnimationScaleIsRespectedWithoutAnOverride',
    }
    assert len(headline_behavior_methods)==4 and set(headline_behavior_methods)==expected
    assert build['motionEvidence']['mode']=='regression' and build['motionEvidence']['behaviorTests']==4
current_window_report=None
current_window_hash=None
current_window_path=final/'window-diagnostics.json'
if current_window_path.is_file():
    current_window_report=json.loads(current_window_path.read_text())
    validate_current_window_report(current_window_report,digest)
    current_window_hash=hashlib.sha256(current_window_path.read_bytes()).hexdigest()
main_journey_images=[]
main_journey_hash=None
main_journey_report=final/'main-journey/actual-navigation.txt'
main_journey_stages=[('entry','进入：柱形过渡中'),('steady','进入完成：稳定柱形'),('update','公开数据更新：高度续接中'),('end','更新完成：稳定目标')]
main_journey_paths=[final/f'main-journey/source-motion-{stage}-dark.png' for stage,_ in main_journey_stages]
if main_journey_report.is_file() and all(path.is_file() for path in main_journey_paths):
    validate_main_journey_report(main_journey_report.read_text(),digest)
    main_journey_hash=hashlib.sha256(main_journey_report.read_bytes()).hexdigest()
    for path in main_journey_paths:
        assert path.read_bytes().startswith(b'\x89PNG\r\n\x1a\n'),path
pairs = [
    ('主页', 'home', 'home'), ('工具（Claude Code 展开）', 'tool-expanded', 'tools'),
    ('模型（共享别名）', 'model-shared', 'models'), ('项目', 'project-shared', 'projects'),
    ('会话', 'session-shared', 'sessions'), ('设备', 'device', 'devices'),
    ('额度', 'limits', 'limits'), ('趋势', 'trends', 'trends'),
    ('设置（桌面收起、安卓展开主界面）', 'settings', 'settings'),
]
data = [dict(label=label, source=f'../source/desktop-full-{source}-zh-CN.png',
             native=f'visuals/desktop/dark/desktop-{native}-dark.png')
        for label, source, native in pairs]
markup = '''<!doctype html><html lang="zh"><meta charset="utf-8"><meta name="viewport" content="width=device-width,initial-scale=1"><title>桌面原始整页与安卓 0.2.33 对照</title>
<style>body{margin:24px;background:#303438;color:#eef5fb;font:16px/1.6 system-ui}main{max-width:1000px;margin:auto}a{color:#a9dfcb}select,button{min-height:44px;font:inherit;padding:4px 12px}section{display:grid;grid-template-columns:1fr 1fr;gap:24px}figure{margin:0}figcaption{margin:8px 0}img{width:100%;height:auto;border-radius:12px}small{color:#a3adbb;display:block}details{margin:24px 0}@media(max-width:640px){section{grid-template-columns:1fr}}</style>
<main><h1>桌面原始整页与安卓 0.2.33</h1><p>桌面列来自固定 v0.68.0 的完整原始页面和渲染脚本，通过公开样例运行；安卓列来自本次安装包的实际渲染。</p>
<p>用于核对信息顺序、字体、控件形态和展开结构。桌面窗口背景改为不透明；两端日期、系统安全区、滚动和展开状态部分不同，尚未进行像素等价验收。实体手机与实际 Lucky HTTPS 仍待验证。</p>
<label>查看页面 <select id="page"></select></label><section><figure><figcaption>桌面原始渲染</figcaption><img id="desktop" alt="公开样例桌面原始页面"></figure><figure><figcaption>安卓 0.2.33 实际渲染</figcaption><img id="android" alt="公开样例安卓页面"></figure></section>
<details><summary>工具列表调整前后</summary><section><figure><figcaption>0.2.32</figcaption><img src="../../tool-settings-controls/final/visuals/desktop/dark/desktop-tools-dark.png" alt="旧版公开工具列表"></figure><figure><figcaption>0.2.33</figcaption><img src="visuals/desktop/dark/desktop-tools-dark.png" alt="当前公开工具列表"></figure></section></details>
<p><a href="motion-frames/preview.html">播放 / 逐帧查看本包主页 Token 动效</a></p><small>143 项单元、129 项普通安卓回归；工具详情六种配置共 12 项交互与 48dp 触控断言通过。公开安卓页面 46 张、原始桌面页面 26 张，另有 33 帧实际动效。</small><small>APK SHA256: DIGEST</small>
<script>const pairs=DATA;const selector=document.getElementById('page');for(const [i,p] of pairs.entries()){const o=document.createElement('option');o.value=i;o.textContent=p.label;selector.append(o)}function show(){const p=pairs[selector.value];document.getElementById('desktop').src=p.source;document.getElementById('android').src=p.native}selector.onchange=show;show();</script></main></html>'''
markup = markup.replace('0.2.33',args.version).replace('0.2.32',args.previous_version).replace('../../tool-settings-controls/', '../../'+args.previous_evidence_dir+'/')
markup = markup.replace('desktop-tools-dark.png','desktop-'+args.compare_page+'-dark.png')
markup = markup.replace('工具列表调整前后','页面调整前后').replace('六种配置共 12 项交互与 48dp 触控断言通过','六种配置交互检查通过').replace('46 张',str(len(pngs))+' 张')
markup = markup.replace('工具详情六种配置',args.focused_label+'六种配置')
markup = markup.replace('页面调整前后','同页版本对照')
interaction_count=sum(int(re.search(r'OK \((\d+) tests?\)',report.read_text()).group(1)) for report in reports)
markup = re.sub(r'<small>143 项单元、129 项普通安卓回归；.*?</small>',
    f"<small>{build['unitTests']} 项单元、{build['androidTests']} 项普通安卓回归；{len(reports)} 份本包视觉报告共 {interaction_count} 项交互检查通过。公开安卓页面 {len(pngs)} 张、原始桌面页面 {len(reference['captures'])} 张，"+('另有 33 帧实际动效。' if args.motion_evidence=='frames' else '另有本包 3 次真实 Android Window 指标检查。' if args.motion_evidence=='runtime' else '其中包含 4 项数字变化行为回归；本轮未测量 FPS。')+'</small>',markup)
if args.motion_evidence=='runtime':
    rows=[]
    for phase,label in [('before','调整前'),('after','当前包')]:
        for index,run in enumerate(runtime_reports[phase]['runs'],1):
            rows.append(f"<tr><td>{label}</td><td>{index}</td><td>{run['frames']}</td><td>{run['observedVsyncSpanMs']:.1f}</td><td>{run['windowDrawCadenceHz']:.1f}</td><td>{run['totalMs']['p95']:.2f}</td><td>{run['framesOverDeadline']}</td><td>{run['hubCompositions']}</td><td>{run['totalCompositions']}</td><td>{run['metricsCallbacksDropped']}</td></tr>")
    runtime_markup='''<details open><summary>数字区域真实运行时检查（自有模拟器）</summary>
<p>公开样例使用真实 Android 帧时钟，记录 Window 指标和整页、数字区域的重组次数。窗口绘制频率不等于实体手机显示 FPS；这些结果不代表真机体验验收。当前包每次整页重组不超过 5 次，数字区域至少更新 10 次。重组次数下降不直接证明帧率提高或卡顿消失。</p>
<div style="overflow-x:auto"><table><thead><tr><th>状态</th><th>轮次</th><th>窗口样本</th><th>样本跨度（ms）</th><th>窗口绘制频率（Hz）</th><th>总耗时 p95（ms）</th><th>超出 Window 截止时间</th><th>整页重组</th><th>数字区重组</th><th>指标回调丢失</th></tr></thead><tbody>'''+''.join(rows)+'''</tbody></table></div>
<p><a href="'''+runtime_links['before']+'''">调整前公开指标</a> · <a href="'''+runtime_links['after']+'''">当前包公开指标</a></p></details>'''
    markup=markup.replace('<p><a href="motion-frames/preview.html">播放 / 逐帧查看本包主页 Token 动效</a></p>',runtime_markup)
    markup=markup.replace('details{margin:24px 0}','details{margin:24px 0}table{width:100%;border-collapse:collapse;font-variant-numeric:tabular-nums}th,td{padding:8px;text-align:right;border-bottom:1px solid #59616a}th:first-child,td:first-child{text-align:left}')
elif args.motion_evidence=='regression':
    regression_markup='''<details><summary>本包数字变化行为回归</summary><p>完整普通安卓回归包含 4 项数字变化检查，覆盖更新时序、连续更新、减少动画和后台返回等行为。该数字行为回归未测窗口绘制频率或实体手机 FPS；其他场景的运行诊断另列，未使用旧安装包运行时数据作为本包结果。</p><p><a href="android-tests.xml">查看本包完整行为回归报告</a></p></details>'''
    markup=markup.replace('<p><a href="motion-frames/preview.html">播放 / 逐帧查看本包主页 Token 动效</a></p>',regression_markup)
if any(c['view']=='menu' for c in reference['captures']) and (final/'visuals/navigation/dark/navigation-menu-dark.png').is_file():
    data.append(dict(label='导航菜单',source='../source/desktop-menu-region-en.png',native='visuals/navigation/dark/navigation-menu-dark.png'))
if any(c['view']=='period-menu' for c in reference['captures']) and (final/'visuals/fixed/dark/fixed-menu-dark.png').is_file():
    data.append(dict(label='周期菜单',source='../source/desktop-period-menu-region-en.png',native='visuals/fixed/dark/fixed-menu-dark.png'))
if (final/'visuals/background/dark/background-settings-dark.png').is_file():
    for label, source, native in [('背景图片（28%）','normal','settings'),('背景图片（移除）','removed','removed')]:
        data.append(dict(label=label,source=f'../source/desktop-full-background-{source}-zh-CN.png',native=f'visuals/background/dark/background-{native}-dark.png'))
    markup=markup.replace('尚未进行像素等价验收。','尚未进行像素等价验收。背景图片使用同一公开样例，但两端统计样例值和滚动位置不同；图片合成像素另由原生独立图层测试验证。')
    markup=markup.replace('公开安卓页面','公开安卓图像（含独立图片层检查）')
    if (root/'evidence'/args.previous_evidence_dir/'final/visuals/background/dark/background-settings-dark.png').is_file():
        markup=markup.replace('../../'+args.previous_evidence_dir+'/final/visuals/desktop/dark/desktop-'+args.compare_page+'-dark.png','../../'+args.previous_evidence_dir+'/final/visuals/background/dark/background-settings-dark.png')
        markup=markup.replace('src="visuals/desktop/dark/desktop-'+args.compare_page+'-dark.png"','src="visuals/background/dark/background-settings-dark.png"')
if (final/'visuals/settings/dark/settings-appearance-dark.png').is_file() and (final.parent/'source/desktop-full-background-removed-en.png').is_file():
    data.append(dict(label='外观设置排列（英文、无图片）',source='../source/desktop-full-background-removed-en.png',native='visuals/settings/dark/settings-appearance-dark.png'))
button_images=[]
if (final/'visuals/buttoncolor/dark/button-color-settings-dark.png').is_file():
    for page,label in [('settings','订阅设置：普通操作按钮'),('picker','有效颜色预览：弹窗正文色'),('disabled','无效输入：应用按钮禁用')]:
        image=f'visuals/buttoncolor/dark/button-color-{page}-dark.png'
        assert (final/image).is_file(),image
        button_images.append(f'<figure><figcaption>{label}</figcaption><img src="{image}" alt="当前安卓包公开样例，{label}"></figure>')
    button_markup='''<details open><summary>当前安卓包操作颜色与弹窗验证</summary>
<p>三张图来自当前安装包的公开样例。红色强调色下，普通操作使用桌面正文色；颜色选择器保留原主题正文色，无效输入时应用按钮使用 50% 透明度。这里展示安卓实际操作状态，颜色规则由交互断言验证。</p>
<section class="button-color-evidence">'''+''.join(button_images)+'''</section></details>'''
    markup=markup.replace('<script>const pairs=DATA;',button_markup+'<script>const pairs=DATA;')
    markup=markup.replace('details{margin:24px 0}','details{margin:24px 0}.button-color-evidence{grid-template-columns:repeat(auto-fit,minmax(220px,1fr))}')
action_images=[]
if (final/'visuals/actionstate/dark/action-state-enabled-dark.png').is_file():
    for page,label in [('enabled','设置读取操作：可用'),('disabled','设置读取操作：加载中禁用'),('connection','连接编辑：忙碌时保存与取消'),('background','背景图片：忙碌时选择与移除')]:
        image=f'visuals/actionstate/dark/action-state-{page}-dark.png'
        assert (final/image).is_file(),image
        action_images.append(f'<figure><figcaption>{label}</figcaption><img src="{image}" alt="当前安卓包公开样例，{label}"></figure>')
    action_markup='''<details open><summary>本包操作完整状态验证</summary><p>截图来自当前安装包的公开样例。设置操作的文字、边框和背景整体采用 38% 禁用透明度，背景图片操作采用 50%。连接编辑还检查无改动时无法保存、加载时操作禁用，以及取消不提交。</p><section class="action-state-evidence">'''+''.join(action_images)+'''</section></details>'''
    markup=markup.replace('<script>const pairs=DATA;',action_markup+'<script>const pairs=DATA;')
    markup=markup.replace('details{margin:24px 0}','details{margin:24px 0}.action-state-evidence{grid-template-columns:repeat(auto-fit,minmax(220px,1fr))}')
trends_images=[]
if (final/'visuals/trendsmain/dark/trends-main-day-dark.png').is_file():
    for page,label in [('day','DAY：近七日与今日实时柱'),('month','MONTH：本月每日'),('total','TOTAL：历年每月'),('fixed','7D：长期图与周期时间/峰值'),('zero','有记录的零值'),('empty','没有历史的空状态')]:
        image=f'visuals/trendsmain/dark/trends-main-{page}-dark.png'
        assert (final/image).is_file(),image
        trends_images.append(f'<figure><figcaption>{label}</figcaption><img src="{image}" alt="本包公开趋势组件，{label}"></figure>')
    trends_markup='''<details open><summary>本包趋势主窗口与独立历史</summary><p>上方“趋势”对照是两端相同公开 DAY 样例的主窗口；原始 SVG 的 300×120 是绘图坐标，原生柱图也随可用空间伸展。源图中的灰色图表底是鼠标悬停时的 .05 蒙层，手机截图使用无悬停的基础状态。以下六张为实际独立组件场景，TOTAL/固定周期另加公开月记录，不把它们说成与源整页同值。完整历史从图表或 ↗ 进入；没有图或固定周期不可用时，安卓保留独立历史入口，适配桌面托盘入口。</p><section class="trends-main-evidence">'''+''.join(trends_images)+'''</section><figure><figcaption>通过主窗口入口打开的详细历史（本包实际页面）</figcaption><img src="visuals/desktop/dark/desktop-history-dark.png" alt="本包独立详细历史，公开样例"></figure></details>'''
    markup=markup.replace('<script>const pairs=DATA;',trends_markup+'<script>const pairs=DATA;')
    markup=markup.replace('details{margin:24px 0}','details{margin:24px 0}.trends-main-evidence{grid-template-columns:repeat(auto-fit,minmax(220px,1fr))}')
history_images=[]
if (final/'visuals/historysummary/dark/history-summary-unknown-dark.png').is_file():
    for page,label in [('unknown','全部缺少价格：成本未知'),('partial','部分定价：换算成本与未定价数量'),('free','明确免费：零成本')]:
        image=f'visuals/historysummary/dark/history-summary-{page}-dark.png'
        assert (final/image).is_file(),image
        history_images.append(f'<figure><figcaption>{label}</figcaption><img src="{image}" alt="当前安卓包公开历史样例，{label}"></figure>')
    history_markup='''<details open><summary>本包历史费用状态验证</summary><p>截图来自当前安装包的公开历史样例，分别展示成本未知、部分定价并转换为人民币，以及明确零成本。未定价 Token 数量单独保留。</p><section class="history-summary-evidence">'''+''.join(history_images)+'''</section></details>'''
    markup=markup.replace('<script>const pairs=DATA;',history_markup+'<script>const pairs=DATA;')
    markup=markup.replace('details{margin:24px 0}','details{margin:24px 0}.history-summary-evidence{grid-template-columns:repeat(auto-fit,minmax(220px,1fr))}')
if main_journey_hash is not None:
    for stage,label in main_journey_stages:
        image=f'main-journey/source-motion-{stage}-dark.png'
        main_journey_images.append(f'<figure><figcaption>{label}</figcaption><img src="{image}" alt="本包真实 HubScreen 公开样例，{label}"></figure>')
    journey_markup='''<details open><summary>当前包实际主窗口：进入与更新阶段</summary>
<p>四张图来自当前安装包的实际 HubScreen 导航测试，使用公开模拟数据，与上方桌面整页对照是不同样例。它们展示进入、稳定、数据更新和目标完成的静态阶段；静态图片不能证明 FPS、帧呈现或实体手机流畅度。导航测试同时检查详情/设置返回及无额外 Hub 读取。</p><section class="main-journey-evidence">'''+''.join(main_journey_images)+'''</section><p><a href="main-journey/actual-navigation.txt">查看绑定本包的实际导航报告</a></p></details>'''
    markup=markup.replace('<script>const pairs=DATA;',journey_markup+'<script>const pairs=DATA;')
    markup=markup.replace('details{margin:24px 0}','details{margin:24px 0}.main-journey-evidence{grid-template-columns:repeat(auto-fit,minmax(220px,1fr))}')
if current_window_report is not None:
    rows=[]
    for index,run in enumerate(current_window_report['runs'],1):
        rows.append(f"<tr><td>{index}</td><td>{run['frames']}</td><td>{run['observedVsyncSpanMs']:.1f}</td><td>{run['windowDrawCadenceHz']:.1f}</td><td>{run['drawMs']['p95']:.2f}</td><td>{run['totalMs']['p95']:.2f}</td><td>{run['layoutMs']['p95']:.2f}</td><td>{run['deadlineMs']['p95']:.2f}</td><td>{run['framesOverDeadline']}</td><td>{run['hubCompositions']}</td><td>{run['totalCompositions']}</td><td>{run['metricsCallbacksDropped']}</td></tr>")
    window_markup='''<details open><summary>当前包窗口指标（自有模拟器，三轮）</summary>
<p>报告使用真实 Android Choreographer 帧时钟，记录当前包公开负载的 Window 指标。三轮均要求指标完整、回调无丢失、整页提交不超过 5 次、数字区域至少提交 10 次。窗口绘制频率与区域提交次数不能代替物理显示 FPS，也不证明实体手机卡顿已解决；这里没有旧包对比。</p>
<p>报告范围：'''+escape(current_window_report['scope'])+f"；报告刷新频率 {current_window_report['refreshRateHz']:.1f}Hz。"+'''</p>
<div style="overflow-x:auto"><table><thead><tr><th>轮次</th><th>窗口样本</th><th>样本跨度（ms）</th><th>窗口绘制频率（Hz）</th><th>绘制 p95（ms）</th><th>总耗时 p95（ms）</th><th>布局 p95（ms）</th><th>Window 截止时间 p95（ms）</th><th>超出截止时间</th><th>整页提交</th><th>数字区提交</th><th>回调丢失</th></tr></thead><tbody>'''+''.join(rows)+'''</tbody></table></div>
<p><a href="window-diagnostics.json">查看绑定当前包的公开窗口指标 JSON</a></p></details>'''
    markup=markup.replace('<script>const pairs=DATA;',window_markup+'<script>const pairs=DATA;')
    markup=markup.replace('details{margin:24px 0}','details{margin:24px 0}table{width:100%;border-collapse:collapse;font-variant-numeric:tabular-nums}th,td{padding:8px;text-align:right;border-bottom:1px solid #59616a}th:first-child,td:first-child{text-align:left}')
    markup=markup.replace('本轮没有 FPS 或窗口绘制频率测量；未使用旧安装包运行时数据作为本包结果。',
        '下方另有绑定当前安装包的窗口指标；窗口绘制频率不是物理显示 FPS，未使用旧安装包运行时数据作为本包结果。')
markup=markup.replace('143 项单元、129 项普通安卓回归',str(build['unitTests'])+' 项单元、'+str(build['androidTests'])+' 项普通安卓回归')
markup = markup.replace('原始桌面页面 26 张','原始桌面页面 '+str(len(reference['captures']))+' 张')
markup = markup.replace('两端日期、系统安全区、滚动和展开状态部分不同','两端演示日期相同，系统安全区、滚动和展开状态部分不同')
dashboard_reference_path=final.parent/'source/dashboard-renderer-reference.json'
dashboard_data=None
dashboard_runtime=None
if dashboard_reference_path.is_file():
    dashboard_data=json.loads(dashboard_reference_path.read_text())
    index=json.loads((final/'dashboard-visual-index.json').read_text())
    validate_dashboard_visual_index(index,final,digest)
    assert index['apkSha256']==digest
    labels={'overview':'仪表板总览','overview-tokens':'Token 活动','bars-tool':'按工具趋势','bars-model':'按模型趋势','kline':'K 线'}
    for run in index['runs']:
        for entry in run['images']:
            source_profile='wide' if run['profile']=='tablet' else 'phone'
            source_name=f"desktop-dashboard-{entry['view']}-{run['language']}-{run['tone']}-{source_profile}.png"
            data.append(dict(label=f"{labels[entry['view']]} · {run['language']} · {run['profile']}",
                             source='../source/'+source_name,native=entry['path']))
    dashboard_runtime_path=final/'dashboard-window-diagnostics.json'
    dashboard_runtime=json.loads(dashboard_runtime_path.read_text())
    validate_dashboard_runtime(dashboard_runtime,digest)
    component_index_path=final/'dashboard-component-index.json'
    component_index=json.loads(component_index_path.read_text())
    validate_dashboard_component_index(component_index,final,digest)
    assert build['dashboardComponents']['captures']==len(component_index['images'])==11
    assert build['dashboardComponents']['behaviorTests']==8
    assert build['dashboardComponents']['reportSha256']==component_index['reportSha256']
    assert build['dashboardComponents']['indexSha256']==hashlib.sha256(component_index_path.read_bytes()).hexdigest()
    rows=[]
    for scene in dashboard_runtime['scenarios']:
        for run in scene['runs']:
            rows.append('<tr><td>'+escape(scene['name'])+'</td><td>'+str(run['index']+1)+'</td><td>'+str(run['frames'])+
                        '</td><td>'+format(run['windowDrawCadenceHz'],'.2f')+
                        '</td><td>'+format(run['targetCommitDelayMs'],'.1f')+
                        '</td><td>'+format(run['postCommitWindowDrawCadenceHz'],'.2f')+'</td><td>'+str(run['framesOverDeadline'])+
                        '</td><td>'+str(run['dashboardOwnerCompositions'])+'</td></tr>')
    overview_component_images=[]
    for image in sorted(component_index['images'],key=lambda entry:entry['path']):
        path=Path(image['path'])
        overview_component_images.append('<figure><figcaption>'+escape(path.stem)+'</figcaption><img src="'+image['path']+'" alt="本包公开组件验证"></figure>')
    dashboard_markup='<details open><summary>独立仪表板整页</summary><p>上方新增仪表板对照使用相同的公开84日日记录、终身摘要和固定本地日期。手机摘要换列、触点与滚动属于原生适配；平板对照原1200px宽窗口，其他配置对照原400px窗口，不宣称尺寸像素完全相同。记录明细保留覆盖说明和完整数值。</p><p>当前包另外使用真实 Android Choreographer 和 Window 回调记录三个趋势场景各三轮。以下 cadence 是窗口绘制回调节奏，不能当成实体手机呈现FPS；普通整页导航另有实际回归。该运行诊断没有测量总览热图动画。</p><table><thead><tr><th>公开场景</th><th>轮次</th><th>样本</th><th>Window cadence Hz</th><th>超过 deadline</th><th>指定 owner 提交</th></tr></thead><tbody>'+''.join(rows)+'</tbody></table><p><a href="dashboard-window-diagnostics.json">窗口原始报告</a> · <a href="dashboard-window-runtime.txt">绑定当前包的诊断执行结果</a> · <a href="dashboard-visual-index.json">整页视觉索引</a></p><section>'+''.join(overview_component_images)+'</section></details>'
    markup=markup.replace('</main>',dashboard_markup+'</main>')
    markup=markup.replace('<th>Window cadence Hz</th>', '<th>完整更新 Hz</th><th>目标提交等待 ms</th><th>提交后绘制 Hz</th>')
    markup=markup.replace('该运行诊断没有测量总览热图动画。', '完整更新包含布局与准备等待；目标提交等待计到页面首次观察到新目标，提交后绘制仅统计之后的窗口回调，不能掩盖更新响应时间。该运行诊断没有测量总览热图动画。')
    markup=markup.replace('本轮未测量 FPS。','未测量实体手机 FPS。')
markup = markup.replace('DIGEST', digest).replace('DATA', json.dumps(data, ensure_ascii=False))
(final / 'preview.html').write_text(markup)
links = re.findall(r'(?:src|href)="([^"]+)"', markup) + [p[k] for p in data for k in ['source', 'native']]
for link in links:
    assert (final / link).is_file(), link
visual_checks=dict(
    apkSha256=digest, nativeReports=len(reports), nativePngs=len(pngs),
    sourceCaptures=len(reference['captures']), sourceFiles=len(reference['sourceFiles']),
    sourceErrors=reference['errors'], sourceFailedRequests=reference['failedRequests'],
    motionEvidence=args.motion_evidence,motionFrames=33 if args.motion_evidence=='frames' else 0,
    runtimeRuns=3 if args.motion_evidence=='runtime' else 0,
    headlineBehaviorTests=len(headline_behavior_methods),
    fpsMeasured=False,windowMetricsMeasured=args.motion_evidence=='runtime',
    runtimeScope='Owned emulator Window metrics; not physical-device presentation FPS' if args.motion_evidence=='runtime' else None,
    runtimeEvidenceHashes={phase:hashlib.sha256((root/'evidence'/args.runtime_evidence_dir/(phase+'.json')).read_bytes()).hexdigest() for phase in runtime_reports},
    buttonColorImages=len(button_images),
    actionStateImages=len(action_images),
    historySummaryImages=len(history_images),
    trendsMainImages=len(trends_images),
    localGalleryLinksChecked=len(links))
if main_journey_images:
    visual_checks.update(mainJourneySamples=len(main_journey_images),
        mainJourneyEvidenceHash=main_journey_hash,
        mainJourneyScope='Public actual HubScreen; different fixture from desktop comparison; static stages, not FPS')
if dashboard_data is not None:
    visual_checks.update(windowMetricsMeasured=True)
    visual_checks.update(dashboardSourceCaptures=len(dashboard_data['captures']),dashboardSourceFiles=len(dashboard_data['sourceFiles']),dashboardNativeReports=len(index['runs']),dashboardNativeCaptures=sum(len(run['images']) for run in index['runs']),dashboardWindowMetricsMeasured=True,dashboardWindowMetricsRuns=9,dashboardWindowMetricsHash=hashlib.sha256(dashboard_runtime_path.read_bytes()).hexdigest(),dashboardWindowMetricsScope=dashboard_runtime['scope'])
    visual_checks.update(dashboardComponentImages=len(component_index['images']),dashboardComponentBehaviorTests=8,
        dashboardComponentReportHash=component_index['reportSha256'],dashboardComponentIndexHash=hashlib.sha256(component_index_path.read_bytes()).hexdigest())
if current_window_report is not None:
    visual_checks.update(currentWindowMetricsMeasured=True,currentWindowMetricsHash=current_window_hash,
        currentWindowMetricsScope=current_window_report['scope'],currentWindowMetricsRuns=3,
        windowMetricsMeasured=True,runtimeRuns=3,runtimeScope=current_window_report['scope'])
    visual_checks['runtimeEvidenceHashes']['current']=current_window_hash
(final / 'visual-checks.json').write_text(json.dumps(visual_checks, indent=2) + '\n')
print('Same-APK reports, images, '+args.motion_evidence+' evidence and local gallery links verified')
