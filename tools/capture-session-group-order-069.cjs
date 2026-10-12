// Only public synthetic records. Expectations come from the pinned desktop, not Android.
const fs = require('fs'), path = require('path'), crypto = require('crypto');
const root = path.resolve(__dirname, '..');
const file = path.join(root, 'work/desktop-069-ui/reference/src/electron/renderer/sessionRows.js');
const blob = crypto.createHash('sha1').update(Buffer.concat([
  Buffer.from(`blob ${fs.statSync(file).size}\0`), fs.readFileSync(file)
])).digest('hex');
if (blob !== '939c418a6cfd0aa24b943653bf12167fe126b670') throw Error('Pinned upstream blob mismatch');
const desktop = require(file);
const now = Date.parse('2026-10-12T08:00:00Z');
const row = (name, time = 0, tokens = 100, cost = 1, extra = {}) => ({
  client: 'codex', sessionId: name, title: name, totalTokens: tokens, costUsd: cost,
  lastUsedAt: new Date(now + time).toISOString(), ...extra
});
const review = (...args) => ({...row(...args), sessionKind: 'background-review'});
const cases = [
  ['newest', {normal: row('Normal', -1000), review: review('Review', 0)}],
  ['middle', {new: row('New', 0), review: review('Review', -1000), old: row('Old', -2000)}],
  ['oldest', {normal: row('Normal', 0), review: review('Review', -1000)}],
  ['tokens-tie-break', {normal: row('Normal', 0, 150), a: review('A', 0, 100), b: review('B', -100, 100)}],
  ['cost-tie-break', {normal: row('Normal', 0, 200, 1), a: review('A', 0, 100, 1), b: review('B', -100, 100, 1)}],
  ['name-tie-break', {z: row('Zulu', 0, 200, 2), a: row('alpha', 0, 200, 2), review: review('Review', 0, 200, 2)}],
  ['mixed-groups', {'codex:Root': row('Root', -3000), 'codex:Child': row('Child', -1000, 100, 1,
      {parentSessionId: 'Root', parentClient: 'codex'}), review: review('Review', -500),
      bot: row('Bot', 0, 100, 1, {client: 'cursor', sessionId: 'sand-subagent-public'})}],
  ['no-review', {z: row('Zulu'), a: row('alpha')}],
  ['only-review', {a: review('A'), b: review('B', -1000)}],
  ['missing-time', {normal: {...row('Normal'), lastUsedAt: null}, review: {...review('Review'), lastUsedAt: null}}]
];
const options = {now: new Date(now), backgroundReviews: {label: 'Codex Auto Review'}, grokBot: {label: 'Grok Bot'}};
const id = key => key.startsWith('session:') ? key.slice(8) : key;
const project = sessions => desktop.groupSessionRows(desktop.sessionRowsForPeriod({sessions}, {now: new Date(now)}), options)
  .map(r => ({id: id(r.key), value: r.value, cost: String(r.cost), members: (r.groupRows || []).map(r => id(r.key))}));
const legacy = JSON.parse(fs.readFileSync(path.join(root, 'app/src/test/resources/session-list-parity.json')));
const legacyOrders = legacy.groups.map(c => project(c.input.sessions).map(r => r.id));
fs.writeFileSync(path.join(root, 'app/src/test/resources/session-group-order-069.json'), JSON.stringify({
  source: 'desktop 3f106ae3d4712b26cced8272f0d488eaab0de1e6 groupSessionRows', blob, now,
  legacyOrders, cases: cases.map(([name, sessions]) => ({name, input: {sessions}, expected: project(sessions)}))
}, null, 2) + '\n');
console.log(`Captured ${cases.length} mixed-order cases and ${legacyOrders.length} legacy group orders from pinned desktop`);
