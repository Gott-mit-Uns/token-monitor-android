'use strict';
// Generate synthetic fixtures through the pinned upstream Hub, never a live Hub.
const assert = require('node:assert/strict');
const fs = require('node:fs/promises');
const os = require('node:os');
const path = require('node:path');
const crypto = require('node:crypto');
const root = path.resolve(__dirname, '..');
const reference = path.resolve(root, '../work/token-monitor-reference/desktop-v0.68.0');
const { createHub } = require(path.join(reference, 'src/hub/server'));
const protocol = require(path.join(reference, 'src/shared/hubProtocol'));

async function main() {
  const temp = await fs.mkdtemp(path.join(os.tmpdir(), 'tm-protocol-'));
  const secret = crypto.randomBytes(32).toString('hex');
  const hub = createHub({ port: 0, host: '127.0.0.1', secret,
    dataFile: path.join(temp, 'devices.json'), logger: { error() {}, warn() {} } });
  const out = path.join(root, 'fixtures/v0.68.0');
  await fs.mkdir(out, { recursive: true });
  const write = (name, value) => fs.writeFile(path.join(out, `${name}.json`), JSON.stringify(value, null, 2) + '\n');
  try {
    await hub.start();
    const base = `http://127.0.0.1:${hub.server.address().port}`;
    const read = async (endpoint, authenticated = true) => {
      const response = await fetch(base + endpoint, { headers: authenticated ? { Authorization: `Bearer ${secret}` } : {}, signal: AbortSignal.timeout(5000) });
      assert.equal(response.status, 200, endpoint);
      return response.json();
    };
    assert.equal((await fetch(base + '/api/stats')).status, 401);
    await write('empty-stats', await read('/api/stats'));
    const now = new Date().toISOString();
    const period = { totalTokens: 5000000123, costUsd: 12.345,
      clients: { codex: 5000000123 }, models: { 'example-model': 5000000123 },
      outputTokens: 123, cacheReadTokens: 5000000000,
      capabilities: { tokenComponents: true, throughput: false } };
    hub.ingest({ deviceId: 'fixture-desktop', hostname: 'Example Desktop', platform: 'darwin-arm64',
      updatedAt: now, today: period, month: period, allTime: period });
    hub.ingest({ deviceId: 'fixture-offline', hostname: 'Example Server', platform: 'linux-x64',
      updatedAt: '2020-01-01T00:00:00.000Z', today: { totalTokens: 0 }, month: { totalTokens: 0 }, allTime: { totalTokens: 0 } });
    const stats = await read('/api/stats');
    assert.equal(stats.periods.allTime.totalTokens, 5000000123);
    assert.equal(stats.devices.length, 2);
    assert.equal(stats.devices.find(d => d.deviceId === 'fixture-offline').periods.allTime.totalTokens, 0);
    for (const [name, endpoint] of Object.entries({ health: '/api/health', devices: '/api/devices', history: '/api/history', subscriptions: '/api/subscriptions', capabilities: '/api/sync/content', aliases: '/api/sync/settings/modelAliases', pricing: '/api/sync/settings/customPricing' })) {
      await write(name, await read(endpoint, name !== 'health'));
    }
    await write('stats', stats);
    const freshness = protocol.freshnessEvent(stats, 'ingest', now);
    await write('freshness', freshness);
    const applied = protocol.applyFreshnessEvent(stats, freshness);
    assert.deepEqual(applied.periods, stats.periods);
    assert.deepEqual(applied.limits.providers, stats.limits.providers);
    const stream = await fetch(base + '/api/stats/stream', { headers: { Authorization: `Bearer ${secret}`, 'x-token-monitor-stream': '2' }, signal: AbortSignal.timeout(5000) });
    const reader = stream.body.getReader();
    let frame = '';
    while (!frame.includes('\n\n')) frame += new TextDecoder().decode((await reader.read()).value);
    await reader.cancel();
    assert.match(frame, /event: snapshot/);
    const event = JSON.parse(frame.split('\n').find(line => line.startsWith('data:')).slice(5));
    assert.equal(event.stats.periods.allTime.totalTokens, 5000000123);
    await write('snapshot-event', event);
    console.log('PASS: authenticated endpoints, large totals, zero-usage devices, freshness preservation and initial SSE snapshot. Synthetic fixtures generated.');
  } finally {
    await hub.stop();
    await fs.rm(temp, { recursive: true, force: true });
  }
}
main().catch(error => { console.error('Protocol capture failed:', error.name, error.code || '', error.message); process.exitCode = 1; });
