// Public fixtures and expected values from the fixed desktop reference. No Hub/config access.
const fs = require('fs');
const path = require('path');
const root = path.resolve(__dirname, '..');
const ref = path.resolve(root, '../work/token-monitor-reference/desktop-v0.68.0');
const api = require(path.join(ref, 'src/electron/renderer/fixedPeriodRanges.js'));
const { parseDeviceHistories } = require(path.join(ref, 'src/electron/historySource.js'));
const period = (tokens, client = 'codex', exact = true) => ({
  totalTokens: tokens, costUsd: tokens / 1000, clients: {[client]: tokens}, clientCosts: {[client]: tokens / 1000},
  models: {'demo-model': tokens}, modelCosts: {'demo-model': tokens / 1000},
  cacheReadTokens: Math.floor(tokens / 2), cacheWriteTokens: Math.floor(tokens / 10), outputTokens: Math.floor(tokens / 5), unclassifiedTokens: exact ? 0 : Math.floor(tokens / 10),
  clientCacheReads: {[client]: Math.floor(tokens / 2)}, clientCacheWrites: {[client]: Math.floor(tokens / 10)}, clientOutputs: {[client]: Math.floor(tokens / 5)}, clientUnclassifiedTokens: {[client]: exact ? 0 : Math.floor(tokens / 10)},
  modelCacheReads: {'demo-model': Math.floor(tokens / 2)}, modelCacheWrites: {'demo-model': Math.floor(tokens / 10)}, modelOutputs: {'demo-model': Math.floor(tokens / 5)}, modelUnclassifiedTokens: {'demo-model': exact ? 0 : Math.floor(tokens / 10)},
  capabilities: {tokenComponents: exact}
});
const days = (today, client) => Array.from({length: 36}, (_, i) => {
  const date = api.dayKeyAddDays(today, i - 35), tokens = (i + 1) * 10;
  const value = {tokens, cost: tokens / 1000, cacheReadTokens: tokens / 2, cacheWriteTokens: tokens / 10, outputTokens: tokens / 5, unclassifiedTokens: 0, messages: i, tokenComponentsAvailable: true};
  return {date, ...value, activeTimeMs: 1000, perClient: {[client]: {...value}}, perModel: {'demo-model': {...value}}};
}).filter((_, i) => i % 5 !== 0);
const device = (id, today, zone, ends, client) => ({deviceId:id, hostname:id,
  periodWindows:{timeZone:zone,today:{key:today,endsAt:ends}},
  periods:{today:period(500,client),month:period(2000,client),allTime:period(4000,client)},
  historyAvailable:true,history:{version:1,daily:days(today,client),monthly:[]}
});
const original = [device('public-east','2026-10-09','Asia/Hong_Kong','2026-10-09T16:00:00Z','codex'),device('public-west','2026-10-08','America/Los_Angeles','2026-10-09T07:00:00Z','antigravity-cli')];
const cases = [];
function add(name, devices, selection='last7', now='2026-10-09T01:00:00Z', locale='zh-CN') {
  devices = structuredClone(devices);
  const sources = parseDeviceHistories({devices});
  const expected = api.fixedPeriodSnapshotFromDevices(selection,sources,{now:new Date(now),todayKey:'2026-10-09',locale,historyAvailable:true});
  const statsDevices = devices.map(({history,historyAvailable,...stats}) => stats);
  cases.push({name,selection,now,locale,stats:{periods:{},devices:statsDevices,deviceHistoryRevision:'public-revision'},response:{devices},expected});
}
for (const mode of ['week','last7','last30']) for(const locale of ['zh-CN','en-US','en-GB']) add('two-zones-'+mode+'-'+locale,original,mode,undefined,locale);
for(const locale of ['zh','en','ja','en-840']) add('regional-week-'+locale,original,'week',undefined,locale);
add('expired-zoned',original,'last7','2026-10-10T10:30:00Z');
let changed=structuredClone(original);changed[0].periods.today=period(1);add('live-regression-keeps-history',changed);
changed=structuredClone(original);changed[0].history.daily=changed[0].history.daily.filter(x=>x.date!=='2026-10-09');add('live-inserts-today',changed);
changed=structuredClone(original);changed[0].historyAvailable=false;add('explicit-unavailable',changed);
changed=structuredClone(original);delete changed[0].historyAvailable;add('missing-capability',changed);
changed=structuredClone(original);changed[0].history=null;add('explicit-null',changed);
changed=structuredClone(original);delete changed[0].periodWindows.timeZone;add('expired-unzoned',changed,'last7','2026-10-10T10:30:00Z');
changed=structuredClone(original);delete changed[0].periodWindows.today;add('missing-window',changed);
changed=structuredClone(original);changed[0].periods={today:period(0),month:period(0),allTime:period(0)};add('retained-history-with-zero-native',changed);
changed=structuredClone(original);changed[0].periods.today=period(500,'codex',false);add('partial-components',changed);
changed=structuredClone(original);changed[0].history.daily[3].unpricedTokens=20;changed[0].periods.today.unpricedTokens=30;add('unpriced',changed);
add('empty-inventory',[]);
add('complete-zero',[{deviceId:'zero',historyAvailable:true,history:{daily:[]},periods:{today:period(0),month:period(0),allTime:period(0)}}]);
changed=structuredClone(original);changed[0].history.daily[3].perClient['cost-only']={tokens:0,cost:1,unclassifiedTokens:0};add('cost-only-attribution',changed,'last30');
const out=path.join(root,'app/src/test/resources/fixed-period-parity.json');
fs.mkdirSync(path.dirname(out),{recursive:true});fs.writeFileSync(out,JSON.stringify(cases));
// Pin desktop Intl calendar rules independently of older Android/JDK CLDR data.
const byDay = new Map();
const regions = [];
for (let a=65;a<=90;a++) for(let b=65;b<=90;b++) regions.push(String.fromCharCode(a,b));
for(let n=0;n<1000;n++) regions.push(String(n).padStart(3,'0'));
for (const region of regions) {
  const day=api.weekStartsOn('und-'+region) || 7;
  if(day!==1) { if(!byDay.has(day))byDay.set(day,[]);byDay.get(day).push(region); }
}
const calendar = ['package dev.bing.tokenmonitor','','// Desktop v0.68.0 weekStartsOn/Intl rules; regenerated with capture-fixed-periods.cjs.',
  'internal val desktopRegionWeekStarts = buildMap<String, Int> {',
  ...Array.from(byDay,([day,regions])=>'    "'+regions.join(' ')+'".split(" ").forEach { put(it, '+day+') }'),'}','',
  'internal val desktopLanguageWeekStarts = buildMap<String, Int> {',
  ...[6,7].map(day=>{
    const languages=[];for(let a=97;a<=122;a++)for(let b=97;b<=122;b++){const language=String.fromCharCode(a,b);if((api.weekStartsOn(language)||7)===day)languages.push(language);}
    return '    "'+languages.join(' ')+'".split(" ").forEach { put(it, '+day+') }';
  }),'}',''].join('\n');
fs.writeFileSync(path.join(root,'app/src/main/java/dev/bing/tokenmonitor/DesktopCalendarWeeks.kt'),calendar);
process.stdout.write('Captured '+cases.length+' public cases from desktop v0.68.0 pure range functions.\n');
