// Public records evaluated by the immutable desktop implementation, never the Android projection.
const fs=require('fs'),path=require('path');
const root=path.resolve(__dirname,'..'),reference=path.resolve(root,'../work/token-monitor-reference/desktop-v0.68.0');
const rows=require(path.join(reference,'src/electron/renderer/sessionRows.js'));
const policy=require(path.join(reference,'src/electron/renderer/breakdownRenderPolicy.js'));
const now=Date.parse('2026-10-10T02:00:00Z');
const iso=offset=>new Date(now+offset).toISOString();
const base={client:'codex',sessionId:'public-session',title:'Public session',totalTokens:1000,costUsd:.125,lastUsedAt:iso(-30000),models:{'public-alpha':500,'public-beta':400},messageCount:12,inputTokens:100,cacheReadTokens:300,cacheWriteTokens:100,outputTokens:500,timedOutputTokens:450,timedDurationMs:9000};
const variants=[{}, {title:null},{models:{}},{models:{'public-alpha':1000}},{archived:true},{deleted:true},{sourceDeleted:true},{turnEnded:true},{lastUsedAt:null,startedAt:iso(-30000)},{lastUsedAt:"",startedAt:iso(-30000)},{lastUsedAt:'invalid',startedAt:iso(0)},
 {lastUsedAt:iso(-86400000)},{lastUsedAt:iso(1000)},{totalTokens:0},{totalTokens:-10},{client:'unknown-tool'},{client:''},{client:'claude'},{client:'reasonix'},
 {sessionId:'reasonix:public'},{sessionId:null,session_id:'reasonix:public'},{sessionId:'reasonix-stats:public'},{sessionKind:'background-review'},{messageCount:1},{messageCount:1.5},{messageCount:0},
 {inputTokens:1000,cacheReadTokens:1,cacheWriteTokens:0},{inputTokens:100,cacheReadTokens:0,cacheWriteTokens:1},{cacheReadTokens:0,cacheWriteTokens:0},
 {inputTokens:-5,cacheReadTokens:-1,cacheWriteTokens:100},{timedDurationMs:0},{timedOutputTokens:10000},{timedOutputTokens:0},{outputTokens:0},{timedDurationMs:1000000}];
const cases=[];
const options={clientLabels:{codex:'Codex',claude:'Claude'},archivedLabel:'Archived',unattributedLabel:'Unclassified'};
for(const change of variants) for(const hidden of [false,true]) for(const delta of [0,600001]) {
 const session={...base,...change};if(hidden) delete session.title;
 const input={sessions:{public:session}};
 const expected=rows.sessionRowsForPeriod(input,{...options,now:new Date(now+delta)}).map(row=>({id:row.key.slice(8),name:row.name,subtitle:row.subtitle,activity:row.activity||'',detail:row.detail,value:row.value,cost:String(row.cost),sortTime:row.sortTime}));
 cases.push({input,hidden,now:now+delta,expected,rate:rows.sessionTokenRate(session),cache:rows.sessionCacheHitPercent(session)});
}
const ids=['','  public  ','reasonix:public','reasonix:reasonix-stats:public','reasonix-stats:public','2026-10-10T12:01:00Z','rollout-2026-10-10T12-01-00-public','rollout-2026-10-10T12:01:00-12345678-1234-1234-1234-123456789abc','one-12345678-1234-1234-1234-123456789abc-87654321-4321-4321-4321-abcdefabcdef'];
const groups=[];
for(const count of [0,1,3,101]) {
 const sessions={};for(let i=0;i<count+3;i++) sessions['public:'+i]={...base,title:'Public '+i,totalTokens:1000+i,costUsd:i/100,lastUsedAt:iso(-i*1000),sessionKind:i<count?'background-review':null};
 const raw=rows.sessionRowsForPeriod({sessions},{...options,now:new Date(now)});
 const grouped=rows.groupBackgroundReviewRows(raw,{label:'Codex Auto Review',countLabel:n=>n+' background runs',summaryLabel:({latestTime,latestValue})=>['Latest '+latestTime,String(latestValue)].filter(Boolean).join(' · '),now:new Date(now)});
 groups.push({input:{sessions},expected:grouped.map(r=>({id:r.key.startsWith('session:')?r.key.slice(8):r.key,name:r.name,subtitle:r.subtitle,detail:r.detail,value:r.value,cost:String(r.cost),sortTime:r.sortTime,review:r.reviewGroup===true,reviews:(r.backgroundReviewRows||[]).map(r=>r.key.slice(8))}))});
}
const pages=[];
for(const count of [0,1,99,100,101,199,200,201,1001]) for(const page of [-1,0,1,2,99]) {
 const source=Array.from({length:count},(_,i)=>({key:String(i)}));const result=policy.breakdownPage(source,{breakdown:'session',page});
 pages.push({count,page,expected:{...result,rows:result.rows.map(r=>r.key)}});
}
fs.writeFileSync(path.join(root,'app/src/test/resources/session-list-parity.json'),JSON.stringify({source:'desktop v0.68.0 sessionRows.js and breakdownRenderPolicy.js; TZ=Asia/Shanghai',cases,ids:ids.map(input=>({input,expected:rows.sessionIdLabel(input)})),groups,pages},null,2)+'\n');
console.log(`Original ${cases.length} rows, ${ids.length} identifiers, ${groups.length} review groups, ${pages.length} pagination cases`);
