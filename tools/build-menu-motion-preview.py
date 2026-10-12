"""Show actual public popup owner captures; no invented frames or playback timing."""
from pathlib import Path
import argparse,json
p=argparse.ArgumentParser();p.add_argument('--evidence-dir',required=True);a=p.parse_args()
root=Path(__file__).resolve().parent.parent;final=root/'evidence'/a.evidence_dir/'final'
build=json.loads((final/'build-checks.json').read_text())
profiles=['dark','light','small-large-text','landscape','tablet','reduced-motion']
phases=[('start','进入起始'),('middle','进入途中'),('open','完成展开'),('exit','退出途中'),('reopen','快速重开完成'),('reduced','关闭动画'),('period','周期菜单进入')]
items=[]
for profile in profiles:
 for name,label in phases:
  suffix=('light' if profile=='light' else 'dark')+('' if profile in ['dark','light'] else '-'+profile)
  file='visuals/menumotion/'+profile+'/menu-motion-'+name+'-'+suffix+'.png'
  assert (final/file).is_file(),file
  items.append(dict(profile=profile,label=label,file=file))
html='''<!doctype html><html lang="zh"><meta charset="utf-8"><meta name="viewport" content="width=device-width,initial-scale=1"><title>实际菜单动效状态</title><style>body{background:#303438;color:#eef5fb;font:16px/1.6 system-ui;margin:24px}main{max-width:800px;margin:auto}select{font:inherit;min-height:48px;margin:8px}img{display:block;max-width:100%;max-height:70vh;margin:20px auto}small{color:#a3adbb}a{color:#a9dfcb}</style><main><h1>实际菜单过渡状态</h1><p>来自同一APK的实际弹窗画面。可按屏幕配置查看进入、退出、快速重开和关闭动画；这些是不同状态的采样，未编造中间像素或播放时间。</p><select id="profile"></select><select id="phase"></select><img id="image" alt="公开示例菜单实际渲染"><small>展开190ms、关闭130ms；中途反向按原始CSS规则缩短。桌面曲线来自实际原始渲染采样，Android 12及以上使用模糊，旧系统保留透明度和变换。</small><p><a href="preview.html">整页对照</a> · <a href="motion-frames/preview.html">主页Token实际动画</a></p><small>APK SHA256: DIGEST</small></main><script>const items=DATA,p=document.getElementById('profile'),s=document.getElementById('phase');for(const name of [...new Set(items.map(x=>x.profile))])p.add(new Option(name,name));function options(){s.innerHTML='';for(const x of items.filter(x=>x.profile===p.value))s.add(new Option(x.label,x.file));show()}function show(){document.getElementById('image').src=s.value}p.onchange=options;s.onchange=show;options()</script></html>'''.replace('DATA',json.dumps(items,ensure_ascii=False)).replace('DIGEST',build['apkSha256'])
(final/'menu-motion-preview.html').write_text(html)
print('42 actual menu state images linked; no synthesized frames')
