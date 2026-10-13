"""Build offline OFL font derivatives. Requires fonttools==4.66.1; no private data.

Inter supplies Latin, Noto Sans SC supplies GB2312/CJK punctuation plus public
resource strings. Both weights are merged so API26+ uses the same glyphs without
an API29-only fallback builder. Android's system fallback handles absent glyphs.
"""
import argparse
import hashlib
import json
import tempfile
from pathlib import Path

from fontTools import subset
from fontTools.merge import Merger
from fontTools.ttLib import TTFont
from fontTools.ttLib.scaleUpem import scale_upem
from fontTools.varLib.instancer import instantiateVariableFont

ROOT = Path(__file__).resolve().parents[1]
SOURCE = ROOT / 'design/font-preview/fonts'


def chars():
    points = set(range(0x3000, 0x3040)) | set(range(0xff00, 0xfff0))
    for high in range(0xa1, 0xf8):
        for low in range(0xa1, 0xff):
            try:
                points.update(map(ord, bytes((high, low)).decode('gb2312')))
            except UnicodeDecodeError:
                pass
    for path in (ROOT / 'app/src/main/res').glob('values*/strings.xml'):
        points.update(map(ord, path.read_text(encoding='utf-8')))
    return {p for p in points if p >= 0x2e80}


def instance(name, weight, points=None):
    font = TTFont(SOURCE / f'{name}.ttf', recalcTimestamp=False)
    axes = {axis.axisTag: (weight if axis.axisTag == 'wght' else axis.defaultValue)
            for axis in font['fvar'].axes}
    font = instantiateVariableFont(font, axes, inplace=True)
    if points is not None:
        options = subset.Options()
        options.name_IDs = ['*']
        options.name_legacy = True
        options.name_languages = ['*']
        engine = subset.Subsetter(options=options)
        engine.populate(unicodes=points & set(font.getBestCmap()))
        engine.subset(font)
    for table in ('vhea', 'vmtx', 'VORG', 'BASE', 'STAT', 'DSIG'):
        if table in font:
            del font[table]
    return font


def rename(font, family, weight):
    font.recalcTimestamp = False
    style = 'Regular' if weight == 400 else 'Bold'
    names = {1: family, 2: style, 3: f'{family}-{style}-1', 4: f'{family} {style}',
             6: f'{family.replace(" ", "")}-{style}', 16: family, 17: style}
    for name in list(font['name'].names):
        if name.nameID in names:
            font['name'].removeNames(nameID=name.nameID)
    for key, value in names.items():
        font['name'].setName(value, key, 3, 1, 0x409)
    font['OS/2'].usWeightClass = weight
    font['head'].modified = 2082844800  # fixed 1970 timestamp, deterministic derivative


def build(output, manifest_path):
    output.mkdir(parents=True, exist_ok=True)
    records = []
    points = chars()
    with tempfile.TemporaryDirectory() as temporary:
        temporary = Path(temporary)
        for weight, label in ((400, 'regular'), (700, 'bold')):
            inter = instance('Inter', weight)
            noto = instance('NotoSansSC', weight, points - set(inter.getBestCmap()))
            scale_upem(noto, inter['head'].unitsPerEm)
            for name, font in (('inter', inter), ('noto', noto)):
                font.save(temporary / f'{name}.ttf')
            ui = Merger().merge([str(temporary / 'inter.ttf'), str(temporary / 'noto.ttf')])
            copyright_text = '\n'.join(str(font['name'].getDebugName(0) or '') for font in (inter, noto))
            ui['name'].setName(copyright_text, 0, 3, 1, 0x409)
            rename(ui, 'Token UI', weight)
            mono = instance('JetBrainsMono', weight)
            rename(mono, 'Token Data', weight)
            for name, font in ((f'token_ui_{label}', ui), (f'token_data_{label}', mono)):
                path = output / f'{name}.ttf'
                font.save(path)
                raw = path.read_bytes()
                records.append({'file': path.name, 'bytes': len(raw), 'sha256': hashlib.sha256(raw).hexdigest(),
                                'weight': weight, 'unicode_count': len(font.getBestCmap())})
    manifest = {'generator': 'fonttools 4.66.1', 'families': ['Token UI (Inter + Noto Sans SC)',
                'Token Data (JetBrains Mono)'], 'cjk': 'GB2312, CJK/full-width punctuation, public resource strings',
                'fallback': 'Android system fonts for glyphs outside the bundled subset',
                'sources': [{ 'file': p.name, 'sha256': hashlib.sha256(p.read_bytes()).hexdigest() }
                            for p in sorted(SOURCE.glob('*.ttf'))], 'fonts': records}
    manifest_path.parent.mkdir(parents=True, exist_ok=True)
    manifest_path.write_text(json.dumps(manifest, ensure_ascii=False, indent=2) + '\n', encoding='utf-8')
    print(json.dumps(records))


if __name__ == '__main__':
    parser = argparse.ArgumentParser()
    parser.add_argument('--output', type=Path, default=ROOT / 'work/bundled-fonts')
    parser.add_argument('--manifest', type=Path, default=ROOT / 'work/bundled-fonts/manifest.json')
    arguments = parser.parse_args()
    build(arguments.output, arguments.manifest)
