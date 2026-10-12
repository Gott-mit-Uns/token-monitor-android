// Public-only desktop home graph oracle. No Hub, configuration, or network access.
const fs = require('fs');
const path = require('path');
const root = path.resolve(__dirname, '..');
const ref = path.resolve(root, '../work/token-monitor-reference/desktop-v0.68.0');
const home = require(path.join(ref, 'src/electron/renderer/homeOverview.js'));
const charts = require(path.join(ref, 'src/electron/renderer/usageCharts.js'));
const day = (key, n) => new Date(Date.parse(key+'T00:00:00Z')+n*86400000).toISOString().slice(0,10);
const history = (key, count = 64) => ({daily: Array.from({length:count},(_,n)=>({
  date:day(key,n-count+1),tokens:(n%11)*100,cost:(n%7)/10,
  perClient:{codex:{tokens:(n%11)*100,cost:(n%7)/10}},activeTimeMs:n*1000
})),monthly:[{month:key.slice(0,7),tokens:9000,cost:5}],summary:{activeDays:106,peakDayTokens:9000}});
const cases=[];
function add(name, full, preview, live, now, zone, window='all') {
  process.env.TZ=zone;
  const today=charts.localDayKey(new Date(now));
  const selected=home.pickHomeHistory(full,preview);
  const available=selected.daily.length>0;
  const daily=available?home.patchDailyToday(selected.daily,today,live.totalTokens,live.costUsd):selected.daily;
  const intensity=charts.computeHeatmapIntensities(daily);
  const calendar=available?charts.rollingYearHeatmap(intensity,{endDate:today,cell:9,gap:3}):{cells:[],weeks:0};
  const levels=new Map(intensity.map(row=>[row.date,row]));
  const trend=available?charts.clampDaily(daily,45):[];
  const line=charts.areaLineChart(trend,{width:300,height:70,padTop:4,padRight:3,padBottom:4,padLeft:3,metric:'tokens',curve:true});
  const visibleActive=calendar.cells.filter(cell=>cell.tokens>0).length;
  const retained=preview?.summary?.activeDays;
  const activeDays=window==='year'?visibleActive:Number.isFinite(retained)?retained:visibleActive;
  cases.push({name,full,preview,live,now,zone,window,today,expected:{daily,trend,line:line.linePath,
    peak:available?home.longRangePeakDayTokens({historySummary:selected.summary,daily}):null,
    activeDays:available?activeDays:0,
    months:calendar.monthLabels || [],
    cells:calendar.cells.map(cell=>({date:cell.date,col:cell.col,row:cell.row,tokens:cell.tokens,cost:cell.cost,
      tokenLevel:levels.get(cell.date)?.tokenIntensity||0,costLevel:levels.get(cell.date)?.costIntensity||0}))}});
}
const base=history('2026-10-09'),preview=history('2026-10-09',7),live={totalTokens:12000,costUsd:12.5};
add('live-replaces-frozen-today',base,preview,live,'2026-10-09T12:00:00Z','Asia/Shanghai');
add('live-value-can-decrease',base,preview,{totalTokens:1,costUsd:0},'2026-10-09T12:00:00Z','Asia/Shanghai');
add('new-local-day-appended',base,preview,live,'2026-10-09T17:00:00Z','Asia/Shanghai');
add('same-instant-western-day',base,preview,live,'2026-10-09T17:00:00Z','America/Los_Angeles');
add('day-is-not-UTC-at-midnight',history('2026-10-08'),preview,live,'2026-10-08T16:01:00Z','Asia/Shanghai');
add('cold-start-preview',null,preview,live,'2026-10-09T12:00:00Z','Asia/Shanghai');
add('empty-fetch-keeps-preview',{daily:[],monthly:[]},preview,live,'2026-10-09T12:00:00Z','Asia/Shanghai');
add('no-history-does-not-invent-series',{daily:[],monthly:[]},{daily:[],monthly:[]},live,'2026-10-09T12:00:00Z','Asia/Shanghai');
add('year-active-window',base,preview,live,'2026-10-09T12:00:00Z','Asia/Shanghai','year');
add('retained-peak-beyond-visible-daily',base,preview,{totalTokens:10,costUsd:0.03},'2026-10-09T12:00:00Z','Asia/Shanghai');
add('leap-calendar-window',history('2024-02-29'),null,live,'2024-02-29T06:00:00Z','Asia/Shanghai','year');
add('month-first-sunday-leading-column',history('2026-11-01'),null,live,'2026-11-01T06:00:00Z','Asia/Shanghai','year');
add('single-point-centred',{daily:[{date:'2026-10-09',tokens:1,cost:1}],monthly:[]},null,live,'2026-10-09T12:00:00Z','Asia/Shanghai');
add('two-points-straight-line',{daily:[{date:'2026-10-08',tokens:1,cost:1},{date:'2026-10-09',tokens:2,cost:2}],monthly:[]},null,live,'2026-10-09T12:00:00Z','Asia/Shanghai');
fs.writeFileSync(path.join(root,'app/src/test/resources/home-chart-parity.json'),JSON.stringify(cases));
process.stdout.write(`Captured ${cases.length} public home chart cases from desktop v0.68.0.\n`);
