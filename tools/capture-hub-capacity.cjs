'use strict';

// Public in-memory source oracle only. No Hub, server, sockets, configuration or credentials.
// Large payloads are never written. The only write targets are the two small reference reports.
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const vm = require('node:vm');
const crypto = require('node:crypto');
const { EventEmitter } = require('node:events');
const { execFileSync } = require('node:child_process');

const ROOT = path.resolve(__dirname, '..');
const REFERENCE = path.resolve(ROOT, '../work/token-monitor-reference/desktop-v0.68.0');
const OUTPUT = path.join(ROOT, 'evidence/hub-capacity/reference');
const COMMIT = '5d2db368d8313415763860d594de00e46a663418';
const FIXED_ISO = '2026-10-09T08:00:00.000Z';
const TODAY = '2026-10-09';
const FIXED_MS = Date.parse(FIXED_ISO);
const MIB = 1024 * 1024;
const SESSION_TOKENS = 5_000_000_123;
// Binary-exact six-decimal dollars permit strict cost equality, not epsilon comparisons.
const SESSION_COST = 0.015625;
const PERIODS = ['today', 'month', 'allTime'];
const SHAPES = [
  { name: 'transport-16-devices', devices: 16, sessionsPerDevicePerPeriod: 300, modelsPerDevice: 20, days: 31 },
  { name: 'combined-cache-8-devices', devices: 8, sessionsPerDevicePerPeriod: 180, modelsPerDevice: 20, days: 370 }
];
const sha256 = value => crypto.createHash('sha256').update(value).digest('hex');
const sourcePath = file => path.join(REFERENCE, file);
const readSource = file => fs.readFileSync(sourcePath(file), 'utf8');
const RealDate = Date;
class FixedDate extends RealDate {
  constructor(...args) { super(...(args.length ? args : [FIXED_MS])); }
  static now() { return FIXED_MS; }
}

function sourceFunction(file, name, nextName) {
  const text = readSource(file);
  const start = text.indexOf(`  function ${name}(`);
  const end = text.indexOf(`  function ${nextName}(`, start);
  assert.ok(start >= 0 && end > start, `Pinned source function ${name} exists`);
  return { file, name, text: text.slice(start, end).trim(), firstLine: text.slice(0, start).split('\n').length };
}

function modelsFor(device, count) {
  return Array.from({ length: count }, (_, model) => `public-d${String(device).padStart(2, '0')}-m${String(model).padStart(2, '0')}`);
}

function makePeriod(device, shape, multiplier) {
  const models = modelsFor(device, shape.modelsPerDevice);
  const sessions = {};
  const modelTokens = Object.fromEntries(models.map(model => [model, 0]));
  const modelCosts = Object.fromEntries(models.map(model => [model, 0]));
  for (let i = 0; i < shape.sessionsPerDevicePerPeriod; i++) {
    const id = `public-d${String(device).padStart(2, '0')}-s${String(i).padStart(3, '0')}`;
    const model = models[i % models.length];
    const totalTokens = SESSION_TOKENS * multiplier;
    const costUsd = SESSION_COST * multiplier;
    sessions[id] = {
      client: 'codex', sessionId: id, totalTokens, costUsd,
      inputTokens: (SESSION_TOKENS - 31) * multiplier,
      outputTokens: 7 * multiplier, cacheReadTokens: 16 * multiplier, cacheWriteTokens: 8 * multiplier,
      unpricedTokens: 17 * multiplier, messageCount: multiplier,
      projectId: id, projectLabel: `Public project ${id}`,
      models: { [model]: totalTokens }, modelCosts: { [model]: costUsd }
    };
    modelTokens[model] += totalTokens;
    modelCosts[model] += costUsd;
  }
  const tokens = shape.sessionsPerDevicePerPeriod * SESSION_TOKENS * multiplier;
  const cost = shape.sessionsPerDevicePerPeriod * SESSION_COST * multiplier;
  return {
    totalTokens: tokens, costUsd: cost, unpricedTokens: shape.sessionsPerDevicePerPeriod * 17 * multiplier,
    clients: { codex: tokens }, clientCosts: { codex: cost },
    models: modelTokens, modelCosts, clientModels: { codex: modelTokens }, clientModelCosts: { codex: modelCosts },
    cacheReadTokens: shape.sessionsPerDevicePerPeriod * 16 * multiplier,
    cacheWriteTokens: shape.sessionsPerDevicePerPeriod * 8 * multiplier,
    outputTokens: shape.sessionsPerDevicePerPeriod * 7 * multiplier,
    unclassifiedTokens: 0, capabilities: { tokenComponents: true, throughput: false }, sessions
  };
}

function makeHistory(device, shape, historyApi) {
  assert.equal(shape.sessionsPerDevicePerPeriod % shape.modelsPerDevice, 0);
  const models = modelsFor(device, shape.modelsPerDevice);
  const tokens = shape.sessionsPerDevicePerPeriod * SESSION_TOKENS;
  const cost = shape.sessionsPerDevicePerPeriod * SESSION_COST;
  const daily = Array.from({ length: shape.days }, (_, index) => ({
    date: historyApi.dayKeyAddDays(TODAY, index - shape.days + 1), tokens, cost,
    activeTimeMs: 1000, messages: shape.sessionsPerDevicePerPeriod,
    unpricedTokens: shape.sessionsPerDevicePerPeriod * 17,
    perClient: { codex: { tokens, cost, messages: shape.sessionsPerDevicePerPeriod, unpricedTokens: shape.sessionsPerDevicePerPeriod * 17 } },
    perModel: Object.fromEntries(models.map(model => [model, {
      tokens: tokens / models.length, cost: cost / models.length
    }]))
  }));
  // These are the pinned history core's actual monthly/lifetime production functions.
  return historyApi.normalizeHistory({ contributions: daily }, { todayKey: TODAY });
}

function makeInput(device, shape, historyApi) {
  const history = makeHistory(device, shape, historyApi);
  return {
    deviceId: `public-device-${String(device).padStart(2, '0')}`,
    hostname: `Public device ${device}`, platform: 'public-fixture', updatedAt: FIXED_ISO,
    periodWindows: {
      timeZone: 'Etc/UTC',
      today: { key: TODAY, endsAt: '2026-10-10T00:00:00.000Z' },
      month: { key: '2026-10', endsAt: '2026-11-01T00:00:00.000Z' }
    },
    today: makePeriod(device, shape, 1), month: makePeriod(device, shape, 9),
    allTime: makePeriod(device, shape, shape.days), historyAvailable: true, history
  };
}

// Execute the original byte-counted POST JSON reader without making an HTTP request.
async function admitPublicInput(input, httpApi) {
  const text = JSON.stringify(input);
  const size = Buffer.byteLength(text, 'utf8');
  assert.ok(size < MIB, 'Every synthetic device input must be below the original 1 MiB ingest limit');
  const request = new EventEmitter();
  request.setEncoding = encoding => assert.equal(encoding, 'utf8');
  const parsed = httpApi.readJsonBody(request);
  for (let i = 0; i < text.length; i += 16_384) request.emit('data', text.slice(i, i + 16_384));
  request.emit('end');
  const accepted = await parsed;
  assert.equal(accepted.deviceId, input.deviceId);
  return { accepted, bytes: size };
}

function assertBusiness(stats, history, records, shape, historyApi) {
  const sessions = shape.devices * shape.sessionsPerDevicePerPeriod;
  const expectedModels = Array.from({ length: shape.devices }, (_, i) => modelsFor(i, shape.modelsPerDevice)).flat().sort();
  assert.equal(stats.devices.length, shape.devices);
  assert.equal(stats.projectsIncomplete, false);
  assert.equal(stats.sessionDetailsOmitted, undefined);
  assert.equal(stats.periodProjectsOmitted, undefined);
  for (const [periodName, multiplier] of [['today', 1], ['month', 9], ['allTime', shape.days]]) {
    const p = stats.periods[periodName];
    const tokens = sessions * SESSION_TOKENS * multiplier;
    assert.ok(Number.isSafeInteger(tokens), 'All fixture counters remain exact within original JavaScript safe integers');
    assert.equal(BigInt(p.totalTokens), BigInt(sessions) * BigInt(SESSION_TOKENS) * BigInt(multiplier));
    assert.equal(p.costUsd, sessions * SESSION_COST * multiplier);
    assert.equal(p.unpricedTokens, sessions * 17 * multiplier);
    assert.equal(Object.keys(p.sessions).length, sessions);
    assert.equal(Object.keys(p.projects).length, sessions);
    assert.deepEqual(Object.keys(p.models).sort(), expectedModels);
    assert.deepEqual(Object.keys(p.clientModels.codex).sort(), expectedModels);
    assert.deepEqual(Object.keys(p.modelCosts).sort(), expectedModels);
    assert.equal(p.clients.codex, tokens);
    assert.equal(p.clientCosts.codex, p.costUsd);
    for (const device of records) {
      const original = device.periods[periodName];
      assert.equal(Object.keys(original.sessions).length, shape.sessionsPerDevicePerPeriod);
      assert.equal(Object.keys(original.projects).length, shape.sessionsPerDevicePerPeriod);
      for (const [key, row] of Object.entries(original.sessions)) {
        const result = p.sessions[key];
        assert.ok(result, 'Every source top-level session key remains present');
        for (const field of ['sessionId', 'totalTokens', 'costUsd', 'unpricedTokens', 'messageCount', 'projectLabel']) {
          assert.equal(result[field], row[field]);
        }
        assert.deepEqual(result.models, row.models);
        assert.deepEqual(result.modelCosts, row.modelCosts);
      }
      for (const [key, row] of Object.entries(original.projects)) {
        assert.ok(p.projects[key], 'Every source top-level project key remains present');
        assert.deepEqual(p.projects[key], row);
      }
      for (const model of Object.keys(original.models)) {
        assert.equal(p.models[model], original.models[model]);
        assert.equal(p.modelCosts[model], original.modelCosts[model]);
        assert.equal(p.clientModels.codex[model], original.clientModels.codex[model]);
      }
    }
  }
  const dayTokens = sessions * SESSION_TOKENS;
  const dayCost = sessions * SESSION_COST;
  const expectedDates = Array.from({ length: shape.days }, (_, i) => historyApi.dayKeyAddDays(TODAY, i - shape.days + 1));
  assert.deepEqual(history.daily.map(row => row.date), expectedDates);
  for (const row of history.daily) {
    assert.equal(BigInt(row.tokens), BigInt(dayTokens));
    assert.equal(row.cost, dayCost);
    assert.equal(row.unpricedTokens, sessions * 17);
    assert.equal(row.perClient.codex.tokens, dayTokens);
    assert.equal(row.perClient.codex.cost, dayCost);
    assert.deepEqual(Object.keys(row.perModel).sort(), expectedModels);
    for (const model of expectedModels) {
      assert.equal(row.perModel[model].tokens, shape.sessionsPerDevicePerPeriod * SESSION_TOKENS / shape.modelsPerDevice);
      assert.equal(row.perModel[model].cost, shape.sessionsPerDevicePerPeriod * SESSION_COST / shape.modelsPerDevice);
    }
  }
  const daysByMonth = {};
  expectedDates.forEach(date => { const month = date.slice(0, 7); daysByMonth[month] = (daysByMonth[month] || 0) + 1; });
  assert.deepEqual(history.monthly.map(row => row.month), Object.keys(daysByMonth));
  for (const row of history.monthly) {
    assert.equal(BigInt(row.tokens), BigInt(dayTokens) * BigInt(daysByMonth[row.month]));
    assert.equal(row.cost, dayCost * daysByMonth[row.month]);
    assert.equal(row.unpricedTokens, sessions * 17 * daysByMonth[row.month]);
    assert.equal(row.perClient.codex.tokens, dayTokens * daysByMonth[row.month]);
    assert.equal(row.perClient.codex.cost, dayCost * daysByMonth[row.month]);
    assert.deepEqual(Object.keys(row.perModel).sort(), expectedModels);
    for (const model of expectedModels) {
      assert.equal(row.perModel[model].tokens, shape.sessionsPerDevicePerPeriod * SESSION_TOKENS / shape.modelsPerDevice * daysByMonth[row.month]);
      assert.equal(row.perModel[model].cost, shape.sessionsPerDevicePerPeriod * SESSION_COST / shape.modelsPerDevice * daysByMonth[row.month]);
    }
  }
  assert.equal(BigInt(history.summary.totalTokens), BigInt(dayTokens) * BigInt(shape.days));
  assert.equal(history.summary.totalCost, dayCost * shape.days);
  assert.equal(history.summary.unpricedTokens, sessions * 17 * shape.days);
  assert.equal(history.summary.activeDays, shape.days);
  assert.equal(history.summary.messages, sessions * shape.days);
  assert.deepEqual(stats.historyPreview, historyApi.historyPreview(history));
  assert.equal(stats.historyRevision, historyApi.historyRevision(history));
  assert.equal(stats.deviceHistoryRevision, historyApi.deviceHistoryRevision(records));
  return {
    periodsChecked: PERIODS,
    sessionsPerPeriod: sessions, projectsPerPeriod: sessions,
    modelIdsPerPeriodAndHistoryDay: expectedModels.length,
    retainedDailyRows: history.daily.length, retainedMonthlyRows: history.monthly.length,
    firstDay: expectedDates[0], lastDay: TODAY,
    exactAllTimeTokens: String(history.summary.totalTokens), exactAllTimeCost: String(history.summary.totalCost),
    exactTodayTokens: String(dayTokens), exactTodayCost: String(dayCost),
    everySessionAndProjectChecked: true, everyDailyAndMonthlyModelChecked: true,
    incompleteFlagsAbsent: true, previewAndRevisionsMatchOriginalFunctions: true
  };
}

async function generate() {
  assert.equal(execFileSync('git', ['-C', REFERENCE, 'rev-parse', 'HEAD'], { encoding: 'utf8' }).trim(), COMMIT);
  global.Date = FixedDate;
  try {
    const usage = require(sourcePath('src/shared/usage.js'));
    const historyApi = require(sourcePath('src/shared/history.js'));
    const httpApi = require(sourcePath('src/shared/http.js'));
    const { SHARED_SYNC_KINDS } = require(sourcePath('src/shared/syncContent.js'));
    const functions = [sourceFunction('src/hub/server.js', 'getStats', 'getHistory'),
      sourceFunction('src/hub/server.js', 'getHistory', 'getDevices'),
      sourceFunction('src/hub/server.js', 'sseFormat', 'writeSse')];
    const reports = [];
    for (const shape of SHAPES) {
      const records = [], deviceBytes = [];
      for (let i = 0; i < shape.devices; i++) {
        const admitted = await admitPublicInput(makeInput(i, shape, historyApi), httpApi);
        deviceBytes.push(admitted.bytes);
        // Same pure ingress normalization as server.ingest(), with title sharing disabled.
        records.push(usage.mergeSyncDeviceRecord(undefined, { ...admitted.accepted, receivedAt: FIXED_ISO }, { preserveSessionTitles: false }));
      }
      const store = { devices: Object.fromEntries(records.map(record => [record.deviceId, record])), subscriptions: { updatedAt: '' } };
      const sandbox = { aggregateDevices: usage.aggregateDevices, aggregateHistory: usage.aggregateHistory,
        historyPreview: historyApi.historyPreview, historyRevision: historyApi.historyRevision,
        deviceHistoryRevision: historyApi.deviceHistoryRevision, SHARED_SYNC_KINDS,
        getSyncSettings: () => ({ revision: 0 }), staleAfterMs: 600_000, store, Date: FixedDate };
      const [getStats, getHistory, sseFormat] = functions.map(fn => vm.runInNewContext(`(${fn.text})`, sandbox));
      const stats = getStats();
      const history = getHistory();
      const checks = assertBusiness(stats, history, records, shape, historyApi);
      const statsText = JSON.stringify(stats), historyText = JSON.stringify(history);
      const event = { type: 'stats', reason: 'snapshot', stats, at: FIXED_ISO };
      const sseText = sseFormat('snapshot', event);
      const dataLine = sseText.split('\n').find(line => line.startsWith('data: '));
      assert.equal(JSON.parse(dataLine.slice(6)).stats.periods.allTime.totalTokens, stats.periods.allTime.totalTokens);
      // Match the current v1 JSON envelope field order. The empty credential field contains
      // no credential; excluding its real length gives a conservative minimum record size.
      const envelope = JSON.stringify({ address: 'https://capacity.example.invalid', credential: '', snapshot: statsText,
        savedAt: FIXED_MS, history: historyText, deviceAliases: {} });
      const restored = JSON.parse(envelope);
      assert.equal(restored.snapshot, statsText); assert.equal(restored.history, historyText);
      const sizes = {
        inputMinBytes: Math.min(...deviceBytes), inputMaxBytes: Math.max(...deviceBytes),
        eachInputBelowOriginal1MiB: deviceBytes.every(size => size < MIB), originalJsonReaderAccepted: deviceBytes.length,
        statsUtf8Bytes: Buffer.byteLength(statsText), historyUtf8Bytes: Buffer.byteLength(historyText),
        topLevelPeriodsUtf8Bytes: Buffer.byteLength(JSON.stringify(stats.periods)),
        sseDataLineUtf8Bytes: Buffer.byteLength(dataLine), sseEventUtf8Bytes: Buffer.byteLength(sseText),
        savedHubV1PlaintextUtf8Bytes: Buffer.byteLength(envelope),
        savedHubV1CiphertextBytesWith29ByteOverhead: Buffer.byteLength(envelope) + 29
      };
      if (shape.name === 'transport-16-devices') {
        assert.ok(sizes.statsUtf8Bytes > 8 * MIB, 'Legal device inputs reproduce the old HTTP 8 MiB rejection');
        assert.ok(sizes.sseDataLineUtf8Bytes > 8 * MIB, 'A full source SSE data line exceeds the old 8 MiB reader');
      } else {
        assert.ok(sizes.statsUtf8Bytes < 8 * MIB && sizes.historyUtf8Bytes < 8 * MIB,
          'The combined-cache case must independently fit both old HTTP response limits');
        assert.ok(sizes.savedHubV1CiphertextBytesWith29ByteOverhead > 10 * MIB,
          'The joint v1 cache must independently exceed its old 10 MiB limit');
      }
      reports.push({ shape, sizes, businessChecks: checks,
        publicPayloadDigests: { statsSha256: sha256(statsText), historySha256: sha256(historyText), sseSha256: sha256(sseText), v1PlaintextSha256: sha256(envelope) } });
    }
    const files = new Set(Object.keys(require.cache).filter(file => file.startsWith(REFERENCE + path.sep)).map(file => path.relative(REFERENCE, file)));
    files.add('src/hub/server.js'); files.add('src/electron/statsPublisher.js');
    const sourceFiles = [...files].sort().map(file => {
      const bytes = fs.readFileSync(sourcePath(file));
      const pinned = execFileSync('git', ['-C', REFERENCE, 'show', `${COMMIT}:${file}`], { maxBuffer: 8 * MIB });
      assert.equal(sha256(bytes), sha256(pinned), `Executed source matches pinned commit: ${file}`);
      return { file, bytes: bytes.length, sha256: sha256(bytes) };
    });
    const nativeBoundaryFiles = ['HubHttp.kt', 'HubStream.kt', 'HubStore.kt'].map(name => {
      const file = `app/src/main/java/dev/bing/tokenmonitor/${name}`;
      return { file, sha256: sha256(fs.readFileSync(path.join(ROOT, file))) };
    });
    return {
      schemaVersion: 1, sourceCommit: COMMIT, fixedClock: FIXED_ISO,
      generator: { file: path.relative(ROOT, __filename), sha256: sha256(fs.readFileSync(__filename)) },
      methodology: 'Original pure readJsonBody, ingress normalization, getStats/getHistory closure bodies, aggregate and history functions, and sseFormat; no server or transport is started.',
      adaptations: ['Only public synthetic device/model/session/project IDs.', 'Pinned fixed clock; source JavaScript Number counters stay within safe integers.',
        'Empty subscription update and shared-settings revision 0 supplied to getStats closure.',
        'SavedHub v1 envelope measured in memory with empty non-secret credential field; 29-byte framing overhead calculated, not Android encryption executed.'],
      claims: { reproducesOldCapacityBoundaries: true, capacityFixImplemented: false, androidHttpOrSseExecuted: false,
        androidEncryptionExecuted: false, lowHeapMeasured: false, physicalDeviceMeasured: false,
        realHubAccessed: false, networkOpened: false, largePayloadWritten: false },
      limits: { originalHubInputBytes: MIB, oldAndroidHttpAndSseLineBytes: 8 * MIB, oldSavedHubV1CiphertextBytes: 10 * MIB },
      sourceFiles, originalFunctions: functions.map(({ file, name, text, firstLine }) => ({ file, name, firstLine, sha256: sha256(text) })),
      nativeBoundaryFiles, scenarios: reports
    };
  } finally { global.Date = RealDate; }
}

function textReport(report) {
  return [
    'PUBLIC SOURCE CAPACITY BOUNDARY REPRODUCTION',
    `Pinned desktop commit: ${report.sourceCommit}`,
    'Only in-memory synthetic inputs. No Hub, network, credential, Android runtime, or large payload file.',
    'This reproduces old boundaries; it does not implement or certify a capacity fix.',
    `Source fingerprints: ${report.sourceFiles.length}; exact original closure bodies: ${report.originalFunctions.length}`,
    ...report.scenarios.flatMap(({ shape, sizes, businessChecks: b }) => [
      '', shape.name,
      `Devices ${shape.devices}; sessions/device/period ${shape.sessionsPerDevicePerPeriod}; models/device ${shape.modelsPerDevice}; days ${shape.days}`,
      `Original input reader accepted ${sizes.originalJsonReaderAccepted}; largest input ${sizes.inputMaxBytes} bytes (< 1048576)`,
      `Stats ${sizes.statsUtf8Bytes}; history ${sizes.historyUtf8Bytes}; SSE data line ${sizes.sseDataLineUtf8Bytes}; SSE event ${sizes.sseEventUtf8Bytes} bytes`,
      `Required top-level periods subtree alone: ${sizes.topLevelPeriodsUtf8Bytes} bytes (before any device fields)`,
      `SavedHub v1 plaintext ${sizes.savedHubV1PlaintextUtf8Bytes}; calculated ciphertext minimum ${sizes.savedHubV1CiphertextBytesWith29ByteOverhead} bytes`,
      `Each period retains ${b.sessionsPerPeriod} sessions, ${b.projectsPerPeriod} projects, ${b.modelIdsPerPeriodAndHistoryDay} model IDs`,
      `History retains ${b.retainedDailyRows} daily and ${b.retainedMonthlyRows} monthly rows; every date/model checked`,
      `Exact lifetime tokens ${b.exactAllTimeTokens}; exact lifetime USD ${b.exactAllTimeCost}`,
      'PASS: complete per-period sessions/projects/models, exact counters/costs/unpriced, history dates/months/models, source preview and revisions.'
    ]), ''
  ].join('\n');
}

async function main() {
  assert.ok(process.argv.length === 3 && ['--write', '--check'].includes(process.argv[2]), 'Usage: node tools/capture-hub-capacity.cjs --write|--check');
  const report = await generate();
  const outputs = { 'source-capacity.json': JSON.stringify(report, null, 2) + '\n', 'source-capacity.txt': textReport(report) };
  if (process.argv[2] === '--write') {
    fs.mkdirSync(OUTPUT, { recursive: true });
    for (const [name, text] of Object.entries(outputs)) {
      assert.ok(Buffer.byteLength(text) < 64 * 1024, 'Reference reports remain small');
      fs.writeFileSync(path.join(OUTPUT, name), text);
    }
  } else for (const [name, text] of Object.entries(outputs)) assert.equal(fs.readFileSync(path.join(OUTPUT, name), 'utf8'), text, `Deterministic replay: ${name}`);
  process.stdout.write(textReport(report));
  process.stdout.write(`${process.argv[2] === '--check' ? 'Byte-identical replay' : 'Small reports written'}: PASS\n`);
}

if (require.main === module) main().catch(error => { process.stderr.write(`Public capacity source check failed: ${error.message}\n`); process.exitCode = 1; });
module.exports = { generate, SHAPES, makeInput };
