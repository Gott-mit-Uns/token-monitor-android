from pathlib import Path
import urllib.request
from PIL import Image, ImageDraw, ImageFont

ROOT = Path(__file__).resolve().parent
ASSETS = ROOT / 'fonts'
ASSETS.mkdir(exist_ok=True)
URLS = {
    'Inter.ttf': 'https://raw.githubusercontent.com/google/fonts/main/ofl/inter/Inter%5Bopsz,wght%5D.ttf',
    'JetBrainsMono.ttf': 'https://raw.githubusercontent.com/google/fonts/main/ofl/jetbrainsmono/JetBrainsMono%5Bwght%5D.ttf',
    'NotoSansSC.ttf': 'https://raw.githubusercontent.com/google/fonts/main/ofl/notosanssc/NotoSansSC%5Bwght%5D.ttf',
}
for filename, url in URLS.items():
    target = ASSETS / filename
    if not target.exists():
        urllib.request.urlretrieve(url, target)

CN = Path('C:/Windows/Fonts/msyh.ttc')
BG = '#f1f4f8'
INK = '#162238'
MUTED = '#627089'
BLUE = '#2563eb'

def font(path, size, weight=400):
    f = ImageFont.truetype(str(path), size)
    try:
        axes = f.get_variation_axes()
        values = [weight if b'weight' in a['name'].lower() else a['default'] for a in axes]
        f.set_variation_by_axes(values)
    except (OSError, AttributeError):
        pass
    return f

def text(d, xy, s, path=CN, size=26, color=INK, weight=400):
    d.text(xy, s, font=font(path, size, weight), fill=color, anchor='lt')

def canvas(title, subtitle, height):
    im = Image.new('RGB', (1440, height), BG)
    d = ImageDraw.Draw(im)
    text(d, (64, 45), title, size=42)
    text(d, (64, 110), subtitle, size=25, color=MUTED)
    return im, d

families = [
    ('Consolas', Path('C:/Windows/Fonts/consola.ttf'), '桌面界面参考 · 等宽'),
    ('JetBrains Mono', ASSETS/'JetBrainsMono.ttf', 'Android 候选 · 等宽'),
    ('Segoe UI', Path('C:/Windows/Fonts/segoeui.ttf'), '桌面总量参考 · 无衬线'),
    ('Inter', ASSETS/'Inter.ttf', 'Android 候选 · 无衬线'),
]

im, d = canvas('01  英文字形对比', '同一字号 40 px / 常规 400；标签使用独立中文字体，样本文字使用标注字体。', 1170)
for i, (name, path, description) in enumerate(families):
    y = 180 + i*235
    d.rounded_rectangle((48, y, 1392, y+213), radius=20, fill='white')
    text(d, (78,y+23), name, size=27, color=BLUE)
    text(d, (430,y+25), description, size=24, color=MUTED)
    text(d, (78,y+80), 'Token Monitor  |  Dashboard  |  Sessions', path, 40)
    text(d, (78,y+145), 'Aa Bb Gg Rr  0O  1Il  {} []  ->  @ # %', path, 40)
im.save(ROOT/'01-english.png')

im, d = canvas('02  数字与金额对比', '同一字号 52 px / 常规 400；使用各字体默认数字特性，未额外开启表格数字。', 1170)
for i, (name, path, description) in enumerate(families):
    y = 180 + i*235
    d.rounded_rectangle((48,y,1392,y+213), radius=20, fill='white')
    text(d,(78,y+23),name,size=27,color=BLUE)
    text(d,(430,y+25),description,size=24,color=MUTED)
    text(d,(78,y+76),'1,234,567     89.42%     $12.80',path,52)
    text(d,(78,y+146),'0123456789   11:11   88:88',path,46)
im.save(ROOT/'02-numbers.png')

im, d = canvas('03  中文与中英混排', '微软雅黑是本机中文参考；Noto Sans SC 是候选中文字库。四款西文字体不提供完整中文。', 1070)
rows = [
    ('微软雅黑 / Regular',CN,400),
    ('Noto Sans SC / Regular 400',ASSETS/'NotoSansSC.ttf',400),
    ('Noto Sans SC / Medium 500',ASSETS/'NotoSansSC.ttf',500),
    ('Noto Sans SC / Bold 700',ASSETS/'NotoSansSC.ttf',700),
]
for i,(name,path,weight) in enumerate(rows):
    y=180+i*210
    d.rounded_rectangle((48,y,1392,y+188),radius=20,fill='white')
    text(d,(78,y+22),name,size=27,color=BLUE)
    text(d,(78,y+75),'概览  会话记录  今日用量  字体设置',path,42,weight=weight)
    text(d,(78,y+132),'总 Token：1,234,567    费用：$12.80',path,36,weight=weight)
im.save(ROOT/'03-chinese.png')
for p in ROOT.glob('*.png'):
    print(p)
