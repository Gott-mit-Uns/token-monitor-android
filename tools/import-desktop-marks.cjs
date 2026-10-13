// Import only fixed desktop presentation metadata and SVG alpha masks.
// sharp is an existing build-tool dependency, never an Android runtime dependency.
const fs = require('node:fs');
const path = require('node:path');
const crypto = require('node:crypto');
const reference = process.argv[2];
const sharpModule = process.argv[3];
if (!reference || !sharpModule) throw new Error('Provide desktop reference root and installed sharp module');
const sharp = require(sharpModule);
const catalog = require(path.join(reference, 'src/shared/clientCatalog.js'));
const vendors = require(path.join(reference, 'src/shared/vendorPresentation.js'));
const providers = require(path.join(reference, 'src/shared/limits/providers.js'));
const project = path.resolve(__dirname, '..');
const drawables = path.join(project, 'app/src/main/res/drawable-nodpi');
const source = path.join(reference, 'assets/icons');
fs.mkdirSync(drawables, { recursive: true });
function quoted(value) { return JSON.stringify(value).replace(/\$/g, '\\$'); }
function resource(stem) { return 'brand_' + stem.replace(/[^a-z0-9_]/g, '_'); }

(async () => {
  const stems = new Set([...Object.values(vendors.ROW_ICON_MASKS), 'os-apple', 'os-windows', 'os-linux']);
  for (const stem of stems) {
    const svg = fs.readFileSync(path.join(source, stem + '.svg'));
    if (/href\s*=\s*["'](?:https?:|file:)/i.test(svg.toString())) throw new Error('External SVG references are not imported');
    const { data, info } = await sharp(svg, { density: 576 }).resize(192, 192, { fit: 'contain', background: { r: 0, g: 0, b: 0, alpha: 0 } }).ensureAlpha().raw().toBuffer({ resolveWithObject: true });
    if (info.channels !== 4) throw new Error('RGBA mask expected');
    let opaque = 0;
    for (let offset = 0; offset < data.length; offset += 4) {
      opaque += data[offset + 3] > 0 ? 1 : 0;
      data[offset] = data[offset + 1] = data[offset + 2] = 255;
    }
    if (!opaque) throw new Error('Empty desktop mark: ' + stem);
    await sharp(data, { raw: info }).png({ compressionLevel: 9 }).toFile(path.join(drawables, resource(stem) + '.png'));
    const old = path.join(project, 'app/src/main/res/drawable', resource(stem) + '.xml');
    if (fs.existsSync(old)) fs.unlinkSync(old);
  }
  const chartSource = fs.readFileSync(path.join(reference, 'src/electron/renderer/usageCharts.js'), 'utf8');
  const resolver = chartSource.slice(chartSource.indexOf('  function modelVendorFor('), chartSource.indexOf('  function modelColor('));
  const patterns = [...resolver.matchAll(/if \(\/(.+?)\/\.test\(name\)\) return '([^']+)';/g)].map(match => [match[1], match[2]]);
  if (patterns.length !== 20) throw new Error('Review changed desktop model patterns');
  const colors = vendors.vendorColors();
  const lines = [
    '// Generated desktop v0.68.0 presentation data; no collector or Android fork code.',
    '// Source licenses: THIRD_PARTY_NOTICES.md. Regenerate with tools/import-desktop-marks.cjs.',
    '// CatPaw id/label added from fixed desktop v0.70.0 clientCatalog; generic mark fallback.',
    'package dev.bing.tokenmonitor', '',
    'internal val desktopToolLabels = mapOf(',
    ...Object.entries({ ...catalog.CLIENT_LABELS, catpaw: 'CatPaw' }).map(([id, label]) => '    ' + quoted(id) + ' to ' + quoted(label) + ','),
    ')', '', 'internal val desktopManagedToolIds = listOf(',
    ...[...new Set([...catalog.CLIENT_IDS, 'catpaw'])].map(id => '    ' + quoted(id) + ','),
    ')', '', 'internal val desktopProviderLabels = mapOf(',
    ...providers.LIMIT_PROVIDER_CATALOG.map(({ id, label, settingsLabel }) => '    ' + quoted(id) + ' to ' + quoted(settingsLabel || label) + ','),
    ')', '', 'internal val desktopVendorColors = mapOf(',
    ...Object.entries(colors).map(([id, hex]) => {
      const color = hex.length === 4 ? '#' + [...hex.slice(1)].map(c => c + c).join('') : hex;
      return '    ' + quoted(id) + ' to 0xFF' + color.slice(1).toUpperCase() + 'L,';
    }),
    ')', '', 'internal val desktopVendorMarks = mapOf(',
    ...Object.entries(vendors.ROW_ICON_MASKS).map(([id, stem]) => '    ' + quoted(id) + ' to R.drawable.' + resource(stem) + ','),
    ')', '', 'private val desktopModelPatterns = listOf(',
    ...patterns.map(([regex, vendor]) => '    Regex(' + quoted(regex) + ') to ' + quoted(vendor) + ','),
    ')', '',
    'internal fun modelVendor(name: String): String? {',
    '    val value = name.lowercase(java.util.Locale.ROOT)',
    '    return desktopModelPatterns.firstOrNull { it.first.containsMatchIn(value) }?.second',
    '}', ''
  ];
  fs.writeFileSync(path.join(project, 'app/src/main/java/dev/bing/tokenmonitor/DesktopMarks.kt'), lines.join('\n'));
  console.log(JSON.stringify({ marks: stems.size, clientLabels: Object.keys(catalog.CLIENT_LABELS).length, modelPatterns: patterns.length }));
})().catch(error => { console.error(error.message); process.exitCode = 1; });
