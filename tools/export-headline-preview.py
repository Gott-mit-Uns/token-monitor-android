"""Export actual public Android frames; no generated intermediate pixels."""
import argparse
import hashlib
import json
from pathlib import Path
import subprocess
from PIL import Image

parser=argparse.ArgumentParser()
parser.add_argument('--serial',required=True)
parser.add_argument('--output-dir',type=Path,required=True)
args=parser.parse_args()
if not args.serial.startswith('emulator-'):
    raise SystemExit('Public frame capture requires an explicit disposable emulator')
root=Path(__file__).resolve().parent.parent
base=['/Users/bing/android-toolchain/sdk/platform-tools/adb','-s',args.serial]
package='dev.bing.tokenmonitor.nativeclient'
def adb(*command,binary=False):
    return subprocess.run(base+list(command),capture_output=True,text=not binary,check=True,timeout=90).stdout
report=adb('shell','am','instrument','-w','-e','class','dev.bing.tokenmonitor.HeadlineMotionScreenTest#publicRenderedFramesAndBackgroundReturnSettleWithoutReplaying','-e','recordHeadlineFrames','true','-e','visualDark','true',package+'.test/androidx.test.runner.AndroidJUnitRunner')
if 'OK (1 test)' not in report:
    raise SystemExit('Public frame capture or background assertion failed; no preview exported')
digest=hashlib.sha256((root/'app/build/outputs/apk/debug/app-debug.apk').read_bytes()).hexdigest()
installed=adb('shell','pm','path',package).strip().removeprefix('package:')
assert hashlib.sha256(adb('exec-out','cat',installed,binary=True)).hexdigest()==digest
args.output_dir.mkdir(parents=True,exist_ok=True)
frames=[]
for index in range(33):
    name=f'headline-frame-{index:02d}-dark.png'
    pixels=adb('exec-out','cat',f'/sdcard/Android/data/{package}/files/{name}',binary=True)
    assert pixels.startswith(b'\x89PNG\r\n\x1a\n')
    target=args.output_dir/name;target.write_bytes(pixels)
    frames.append(Image.open(target).convert('RGBA'))
# Lossless animated PNG retains the captured pixels. Start/end holds only aid preview reading.
durations=[400]+[32]*31+[600]
frames[0].save(args.output_dir/'headline-count-change.png',save_all=True,append_images=frames[1:],duration=durations,loop=0)
names=[f'headline-frame-{index:02d}-dark.png' for index in range(33)]
html='''<!doctype html><html lang="zh"><meta charset="utf-8"><meta name="viewport" content="width=device-width,initial-scale=1"><title>Token总量动效实录</title><style>body{margin:24px;background:#303438;color:#eef5fb;font:16px system-ui}main{max-width:540px;margin:auto}img{width:100%;height:auto}button,input{margin:8px;min-height:44px}small{display:block;color:#a3adbb;line-height:1.5}</style><main><h1>主页Token变化动效</h1><p>安卓实际渲染33帧：999,999,999 → 2,000,000,000。</p><button id="play">播放</button><label>逐帧 <input id="frame" type="range" min="0" max="32" value="0"></label><small id="time">0ms</small><img id="screen" alt="公开样例主页"><small>原始32ms采样，无补画帧。系统减少动态效果另有专项验证。截图包含公开合成数据，界面来自同一个APK。</small></main><script>const names=NAMES;const image=document.getElementById('screen'),range=document.getElementById('frame');let timer;function show(i){range.value=i;image.src=names[i];document.getElementById('time').textContent=Math.min(i*32,1000)+'ms';}names.forEach(src=>{const p=new Image();p.src=src});range.oninput=()=>{clearInterval(timer);show(+range.value)};document.getElementById('play').onclick=()=>{clearInterval(timer);let i=0;show(i);timer=setInterval(()=>{if(++i>32){clearInterval(timer);return}show(i)},32)};show(0);</script></html>'''.replace('NAMES',json.dumps(names))
(args.output_dir/'preview.html').write_text(html)
(args.output_dir/'rendered-frames.txt').write_text(f'Final application APK SHA256: {digest}\nActual public Android frame renders: 33; samples 32ms; endpoint 1000ms\nLossless APNG with reading holds; no invented frames\n'+report)
print('Captured 33 actual Android frames and background-return assertion; lossless preview exported',flush=True)
