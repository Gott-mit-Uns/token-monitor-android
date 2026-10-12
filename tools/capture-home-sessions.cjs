// Immutable original functions and public synthetic records only.
const fs=require('fs'),path=require('path');
const root=path.resolve(__dirname,'..'),ref=path.resolve(root,'../work/token-monitor-reference/desktop-v0.68.0');
const presentation=require(path.join(ref,'src/electron/renderer/edgeDock/presentation.js'));
const live=require(path.join(ref,'src/shared/sessionLive.js'));
const rows=require(path.join(ref,'src/electron/renderer/sessionRows.js'));
const overview=require(path.join(ref,'src/electron/renderer/homeOverview.js'));
const base=Date.parse('2026-10-10T02:00:00Z'),iso=ms=>new Date(ms).toISOString();
let clock=base;const originalNow=Date.now;Date.now=()=>clock;
const make=(index,extra={})=>({sessionId:'public-session-'+index,title:'Public session '+index,projectLabel:'Public project',client:index%2?'codex':'claude',
 totalTokens:1000+index*10,lastUsedAt:iso(base-index*120000),models:{'public-alpha':500,'public-beta':400},...extra});
const inputs=[];
const sample={periods:{month:{sessions:{}},today:{sessions:{}},allTime:{sessions:{ignored:make(99)}}}};
for(let i=0;i<12;i++) sample.periods.month.sessions['client:'+i]=make(i,{turnEnded:i<5,contextTokens:75000,contextWindow:100000});
sample.periods.today.sessions['client:0']=make(0,{title:'Today duplicate loses'});
sample.periods.today.sessions.extra=make(13,{lastUsedAt:iso(base-1000)});
sample.periods.month.sessions.background=make(50,{lastUsedAt:iso(base),sessionKind:'background-review'});
sample.periods.month.sessions.invalid=make(51,{lastUsedAt:'not-a-date'});
sample.periods.today.sessions.invalid=make(51,{lastUsedAt:iso(base-2000)});
inputs.push(sample);
for(let count=0;count<=12;count++) {
 const sessions={};for(let i=0;i<count;i++) sessions['public:'+i]=make(i,{lastUsedAt:iso(base-i*30000),turnEnded:i%3===0,archived:i%7===0});
 inputs.push({periods:{today:{sessions}}});
}
for(const flag of ['archived','deleted','sourceDeleted']) inputs.push({periods:{month:{sessions:{one:make(1,{[flag]:true})}},today:{sessions:{two:make(2)}}}});
inputs.push({periods:{month:{sessions:{one:make(1,{lastUsedAt:null,startedAt:iso(base-500),title:null,sessionId:'abcdefghijklmnop'})}}}});
inputs.push({periods:{month:{sessions:{one:make(1,{lastUsedAt:iso(base+5000),models:{'':20,'public-alpha':1000,'public-beta':1}})}}}});
const cases=[];
for(const input of inputs) for(const delta of [0,600000,600001,3600000]) {
 clock=base+delta;
 const selected=presentation.recentSessionRows(input,5,{includeRunningBeyondCap:true});
 cases.push({input,now:clock,expected:selected.map(row=>({key:row.key,title:row.title||row.projectLabel||row.sessionId.slice(0,12)||'—',
  activity:live.sessionActivityState(row),context:live.sessionContextForRow(row)||null,cache:live.sessionPromptCacheForRow(row)||null,
  models:rows.sessionModelLabel(row),shares:rows.sessionModelTooltipEntries(row,{unattributedLabel:'',formatTokens:n=>String(n)})})),
  next:live.nextSessionStatusChangeAt(selected),running:selected.filter(row=>live.sessionActivityState(row)==='running').length});
}
const individual=[];
for(const age of [-1000,0,29999,30000,60000,599999,600000,600001,1800000,3600000]) for(const flags of [{},{turnEnded:true},{deleted:true}]) {
 for(const context of [{},{contextTokens:25000,contextWindow:100000},{contextTokens:70500,contextWindow:100000},{contextTokens:89500,contextWindow:100000},{contextTokens:120000,contextWindow:100000}]) {
  clock=base;const row=make(1,{...flags,...context,lastUsedAt:iso(base-age),promptCache:{observedAt:iso(base-age),ttlSeconds:1800}});
  individual.push({row,now:clock,activity:live.sessionActivityState(row),context:live.sessionContextForRow(row)||null,cache:live.sessionPromptCacheForRow(row)||null,next:live.nextSessionStatusChangeAt([row])});
 }
}
for(const ttl of [0,299,300,1800,3600,3601]) for(const age of [-1,0,1,ttl*1000-1,ttl*1000,ttl*1000+1]) {
 clock=base;const row=make(1,{client:'claude',promptCache:{observedAt:iso(base-age),ttlSeconds:ttl}});
 individual.push({row,now:clock,activity:live.sessionActivityState(row),context:live.sessionContextForRow(row)||null,cache:live.sessionPromptCacheForRow(row)||null,next:live.nextSessionStatusChangeAt([row])});
}
const usageCases=[];
for(const tool of [true,false]) for(const total of [null,0,1000,1000000]) for(const count of [0,1,5,9]) {
 const input=Array.from({length:count},(_,i)=>({key:'public-'+i,name:'Public '+i,value:i%3?100+i:0})).sort((a,b)=>b.value-a.value);
 const expected=(tool?overview.homeToolRows:overview.homeModelRows)(input,total,5);
 usageCases.push({input,total,tool,expected});
}
Date.now=originalNow;
fs.writeFileSync(path.join(root,'app/src/test/resources/home-sessions-parity.json'),JSON.stringify({source:'desktop v0.68.0 original edgeDock/presentation.js, sessionLive.js and sessionRows.js',cases,individual,usageCases},null,2)+'\n');
console.log(`Original ${cases.length} Home selections and ${individual.length} session status/context/cache cases`);
