// Immutable desktop functions; only public synthetic records.
const fs=require('fs'),path=require('path');
const root=path.resolve(__dirname,'..'),ref=path.resolve(root,'../work/token-monitor-reference/desktop-v0.68.0');
const projects=require(path.join(ref,'src/electron/renderer/projectRows.js'));
const devices=require(path.join(ref,'src/electron/renderer/deviceBreakdown.js'));
const home=require(path.join(ref,'src/electron/renderer/homeOverview.js'));
const labels={codex:'Codex',claude:'Claude Code',gemini:'Gemini CLI'},colors={codex:'codex',claude:'claude',gemini:'gemini'};
const cases=[];const base={label:'Public',tokens:1000,costUsd:1,clients:{codex:600,claude:300},unpricedTokens:200};
for(const values of [{},{label:''},{label:'   '},{tokens:0},{tokens:-1},{costUsd:0},{clients:{}},{clients:{codex:1200}},{clients:{'':200,codex:200}},{clients:{unknown:500}},{unpricedTokens:2000},{unpricedTokens:-1},{label:'\ufeffPublic\ufeff'}]) {
 for(const peers of [false,true]) {
  const p={projects:{one:{...base,...values}},sessions:{fallback:{projectLabel:'Should not fallback',totalTokens:123}}};
  if(peers){p.projects.two={label:' public ',tokens:500,costUsd:2,clients:{gemini:500}};p.projects.other={label:'Other',tokens:5000,costUsd:.2,clients:{codex:5000}}}
  cases.push(p);
 }
}
cases.push({projects:{a:{label:'Café',tokens:100,costUsd:1},b:{label:'CAFE\u0301',tokens:200,costUsd:2}}});
cases.push({projects:{bad:null},sessions:{s:{projectLabel:'Must not fallback',totalTokens:100}}});
cases.push({sessions:{s:{projectLabel:' Public ',totalTokens:100,costUsd:.5,client:'codex'},s2:{projectLabel:'PUBLIC',totalTokens:200,costUsd:.2,client:'claude'},s3:{projectLabel:'Other',totalTokens:1,costUsd:2,client:'codex'},s4:{totalTokens:5000}}});
cases.push({projects:{},sessions:{s:{projectLabel:'Public',totalTokens:100,client:'codex'}}});
cases.push({projects:{a:{label:'Zulu',tokens:100,costUsd:1},b:{label:'Alpha',tokens:100,costUsd:1}}});
const projectCases=cases.map(input=>({input,expected:projects.projectRowsForPeriod(input,{clientLabels:labels,clientColors:colors,stableColor:()=>'<unknown>',unknownClientLabel:'Unknown tool'}).map(row=>({id:row.key,name:row.name,tokens:row.value,cost:String(row.cost),unpriced:row.unpricedTokens||0,clients:row.accordionRows.map(r=>({id:r.key,label:r.name,tokens:r.value,percent:r.percent})),gradient:row.barBackground}))}));
const deviceCases=[];
for(const total of [0,100,1000]) for(const clients of [{},{codex:50},{codex:500,claude:400},{codex:1200},{codex:0,claude:-3}]) for(const models of [{},{codex:{'public-alpha':30,'public-beta':20,'zero':0}}]) {
 const input={deviceId:'public',periods:{today:{totalTokens:total,clients,clientModels:models}}};
 const expected=devices.deviceBreakdownForPeriod(input,'today',{clientLabels:labels,unattributedLabel:'Unclassified'});
 deviceCases.push({input,expected:expected.tools.map(r=>({id:r.key,label:r.name,tokens:r.value,percent:r.percent,models:r.models.map(m=>({name:m.name,tokens:m.value}))}))});
}
const homeCases=[];
for(const count of [0,1,4,7]) for(const local of ['', 'public:0','public:1']) for(const cap of [0,4,10]) {
 const input=Array.from({length:count},(_,i)=>({deviceId:'public:'+i,displayName:i%2?'Display '+i:'',hostname:'Host '+i,platform:i%2?'darwin-arm64':'linux',stale:i%3===0,periods:{today:{totalTokens:i%5===4?0:1000}}}));
 homeCases.push({input,local,cap,expected:home.homeDeviceRows(input,{period:'today',localDeviceId:local,limit:cap})});
}
const platforms=[];for(const platform of ['darwin-arm64','win32-x64','linux-x64','freebsd','openbsd','android','',null]) for(const os of [[],['Public OS','1.2'],[' ',' ']]) platforms.push({platform,os,expected:devices.devicePlatformLabel(platform,...os)});
const incomplete=[];for(const period of ['today','month','allTime']) for(const global of [false,true]) for(const count of [0,1,5]) {const input={projectsIncomplete:global,periodProjectsOmitted:{today:count,month:count}};incomplete.push({input,period,expected:projects.projectBreakdownIncomplete(input,period)})}
const app=fs.readFileSync(path.join(ref,'src/electron/renderer/app.js'),'utf8');const at=app.indexOf('function relativeAgeLabel(');const body=app.slice(at,app.indexOf('\n}',at)+2);
const translate=(key,params)=>({unit:({'settings.age.justNow':'now','settings.age.minutesAgo':'minutes','settings.age.hoursAgo':'hours','settings.age.daysAgo':'days'})[key],count:Object.values(params||{})[0]||0});
const relative=new Function('t',body+';return relativeAgeLabel;')(translate),originalNow=Date.now,clock=Date.parse('2026-10-10T02:00:00Z');Date.now=()=>clock;
const ages=[];for(const elapsed of [-1000,0,44999,45000,89999,90000,3569999,3570000,5369999,5370000,84569999,84570000,127769999,127770000,86400000*100]) {
 const value=new Date(clock-elapsed).toISOString();const expected=relative(value);let low=clock+1,high=clock+172800000;
 while(low<high) {const mid=Math.floor((low+high)/2);Date.now=()=>mid;if(JSON.stringify(relative(value))===JSON.stringify(expected))low=mid+1;else high=mid}
 Date.now=()=>clock;ages.push({value,now:clock,expected,next:low});
}
Date.now=originalNow;
fs.writeFileSync(path.join(root,'app/src/test/resources/project-device-parity.json'),JSON.stringify({source:'v0.68.0 projectRows.js, projectKey.js, deviceBreakdown.js and homeOverview.js',projectCases,deviceCases,homeCases,platforms,incomplete,ages},null,2)+'\n');
console.log(`Original ${projectCases.length} project rollups, ${deviceCases.length} device breakdowns, ${homeCases.length} Home selections, ${platforms.length} OS labels, ${incomplete.length} omission flags`);
