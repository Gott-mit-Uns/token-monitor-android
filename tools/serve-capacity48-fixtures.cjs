'use strict';

// Public runtime fixtures only. Large bodies stay in memory and are served on loopback.
// No real Hub, stored connection, credential, device name or response is accessed.
const fs = require('node:fs');
const path = require('node:path');
const crypto = require('node:crypto');
const Module = require('node:module');
const vm = require('node:vm');
const http = require('node:http');
const zlib = require('node:zlib');
const assert = require('node:assert/strict');
const ROOT = path.resolve(__dirname, '..');
const original = path.join(ROOT, 'tools/capture-hub-capacity.cjs');
const reference = JSON.parse(fs.readFileSync(path.join(ROOT, 'evidence/hub-capacity/reference/source-capacity.json'), 'utf8'));
const digest = value => crypto.createHash('sha256').update(value).digest('hex');
const bodies = new Map();

async function prepare() {
  const source = fs.readFileSync(original, 'utf8');
  assert.equal(digest(source), reference.generator.sha256, 'Frozen public generator has not changed');
  const marker = '      reports.push({ shape, sizes, businessChecks: checks,';
  assert.equal(source.split(marker).length, 2);
  const serverFile = path.join(ROOT, '../work/token-monitor-reference/desktop-v0.68.0/src/hub/server.js');
  const server = fs.readFileSync(serverFile, 'utf8');
  const from = server.indexOf('  function getDevices() {');
  const to = server.indexOf('  const sseClients', from);
  assert.ok(from >= 0 && to > from);
  const getter = server.slice(from, to).trim();
  globalThis.__capacity48Capture = ({ shape, statsText, historyText, sseText, records }) => {
    const store = { devices: Object.fromEntries(records.map(record => [record.deviceId, record])) };
    const devices = vm.runInNewContext(`(${getter})`, { store })();
    const devicesText = JSON.stringify(devices);
    bodies.set(shape.name, { stats: statsText, history: historyText, sse: sseText, devices: devicesText,
      deviceIds: devices.map(device => device.deviceId),
      status: { activeStreams: 0, totalStreams: 0, closedStreams: 0, completedFrames: 0, requestsByResource: {} } });
  };
  const patched = source.replace(marker,
    '      globalThis.__capacity48Capture({ shape, statsText, historyText, sseText, records });\n' + marker);
  const loaded = new Module(original, module);
  loaded.filename = original;
  loaded.paths = Module._nodeModulePaths(path.dirname(original));
  loaded._compile(patched, original);
  let report;
  try { report = await loaded.exports.generate(); } finally { delete globalThis.__capacity48Capture; }
  assert.deepEqual(report.scenarios, reference.scenarios, 'Original source assertions, byte sizes and payload hashes replay exactly');
  assert.deepEqual(report.sourceFiles, reference.sourceFiles, 'Every original source fingerprint matches');
  for (const scenario of report.scenarios) bodies.get(scenario.shape.name).expected = scenario;
  return { schemaVersion: 1, sourceCommit: report.sourceCommit, fixedClock: report.fixedClock,
    sourceGeneratorSha256: reference.generator.sha256, fixtureServerSha256: digest(fs.readFileSync(__filename)),
    originalGetDevicesSha256: digest(getter), originalSourceScenariosReplayed: true, originalSourceFingerprintsReplayed: true,
    networkScope: '127.0.0.1 only; public synthetic fixture bodies generated at runtime', largeBodiesWrittenToDisk: false,
    scenarios: report.scenarios.map(scenario => ({ ...scenario, devicesUtf8Bytes: Buffer.byteLength(bodies.get(scenario.shape.name).devices) })) };
}

async function writeChunks(response, value) {
  const bytes = Buffer.isBuffer(value) ? value : Buffer.from(value, 'utf8');
  for (let offset = 0; offset < bytes.length && !response.destroyed; offset += 8191) {
    if (!response.write(bytes.subarray(offset, offset + 8191))) await new Promise(resolve => {
      const done = () => { response.off('drain', done); response.off('close', done); resolve(); };
      response.once('drain', done); response.once('close', done);
    });
  }
  return !response.destroyed;
}

async function main() {
  const args = process.argv.slice(2);
  assert.equal(args.length, 4, 'Use --port-file PATH --metadata-file PATH');
  assert.equal(args[0], '--port-file'); assert.equal(args[2], '--metadata-file');
  const portFile = path.resolve(ROOT, args[1]);
  const metadataFile = path.resolve(ROOT, args[3]);
  for (const file of [portFile, metadataFile]) assert.ok(file.startsWith(ROOT + path.sep + 'work' + path.sep));
  const metadata = await prepare();
  const server = http.createServer(async (request, response) => {
    try {
      if (request.method !== 'GET') { response.writeHead(405); response.end(); return; }
      const url = new URL(request.url, 'http://127.0.0.1');
      const match = /^\/capacity\/([^/]+)\/(.+)$/.exec(url.pathname);
      const fixture = match && bodies.get(match[1]);
      if (!fixture) { response.writeHead(404); response.end(); return; }
      const resource = match[2];
      fixture.status.requestsByResource[resource] = (fixture.status.requestsByResource[resource] || 0) + 1;
      if (resource === 'sse' || resource === 'sse-burst') {
        response.writeHead(200, { 'content-type': 'text/event-stream', 'cache-control': 'no-cache' });
        fixture.status.activeStreams++; fixture.status.totalStreams++;
        let timer;
        response.once('close', () => {
          fixture.status.activeStreams--; fixture.status.closedStreams++;
          if (timer) clearInterval(timer);
        });
        const repeats = resource === 'sse-burst' ? 4 : 1;
        for (let i = 0; i < repeats; i++) {
          // Burst revisions change public protocol metadata only; source business data stays complete.
          const updatedAt = new Date(Date.parse(metadata.fixedClock) + (i + 1) * 1000).toISOString();
          const frame = resource === 'sse-burst'
            ? fixture.sse.replace(/"updatedAt":"[^"]*"/, '"updatedAt":"' + updatedAt + '"')
            : fixture.sse;
          assert.ok(resource !== 'sse-burst' || frame !== fixture.sse);
          if (!await writeChunks(response, frame)) return;
          fixture.status.completedFrames++;
        }
        let ticks = resource === 'sse-burst' ? repeats : 0;
        timer = setInterval(() => {
          if (response.destroyed) { clearInterval(timer); return; }
          const updatedAt = new Date(Date.parse(metadata.fixedClock) + ++ticks * 1000).toISOString();
          const devices = fixture.deviceIds.map(deviceId => ({ deviceId, updatedAt, receivedAt: updatedAt, ageMs: 0, stale: false }));
          response.write('event: freshness\ndata: ' + JSON.stringify({ stats: { updatedAt, devices } }) + '\n\n');
          fixture.status.completedFrames++;
        }, 500);
        return;
      }
      let body;
      if (resource === 'expected') body = JSON.stringify(fixture.expected);
      else if (resource === 'status') body = JSON.stringify(fixture.status);
      else if (resource === 'health') body = '{"ok":true,"role":"hub"}';
      else if (resource === 'subscriptions') body = '{"subscriptions":[]}';
      else if (resource === 'sync/settings/modelAliases') body = '{"ok":true,"revision":0,"value":{"modelAliases":{},"modelAliasGrouping":"off"}}';
      else if (resource === 'sync/settings/customPricing') body = '{"ok":true,"revision":0,"value":[]}';
      else body = fixture[resource === 'stats-gzip' ? 'stats' : resource];
      if (typeof body !== 'string') { response.writeHead(404); response.end(); return; }
      const gzip = resource === 'stats-gzip';
      const bytes = gzip ? zlib.gzipSync(body) : Buffer.from(body, 'utf8');
      response.writeHead(200, { 'content-type': 'application/json', 'content-length': bytes.length,
        ...(gzip ? { 'content-encoding': 'gzip' } : {}) });
      await writeChunks(response, bytes);
      response.end();
    } catch (_) {
      response.destroy(); // Only public status is exposed; never log request headers or bodies.
    }
  });
  await new Promise(resolve => server.listen(0, '127.0.0.1', resolve));
  const port = server.address().port;
  fs.writeFileSync(portFile, String(port) + '\n');
  fs.writeFileSync(metadataFile, JSON.stringify({ ...metadata, port }, null, 2) + '\n');
  process.stdout.write(`Public capacity fixtures ready on loopback port ${port}; source replay PASS\n`);
  const stop = () => { server.closeAllConnections(); server.close(() => process.exit(0)); };
  process.once('SIGINT', stop); process.once('SIGTERM', stop);
}

if (require.main === module) main().catch(() => { process.stderr.write('Public capacity fixture startup failed\n'); process.exitCode = 1; });
module.exports = { prepare };
