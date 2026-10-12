// Pinned desktop functions and CSS, evaluated only with public synthetic inputs.
const fs=require('node:fs'),path=require('node:path'),vm=require('node:vm');
const {chromium}=require('/Users/bing/.cache/codex-runtimes/codex-primary-runtime/dependencies/node/node_modules/playwright');
const root=path.resolve(__dirname,'..'),ref=path.resolve(root,'../work/token-monitor-reference/desktop-v0.68.0');
const app=fs.readFileSync(path.join(ref,'src/electron/renderer/app.js'),'utf8');
function fn(name){const at=app.search(new RegExp('function '+name+'\\('));if(at<0)throw Error(name);return app.slice(at,app.indexOf('\n}',at)+2)}
const attribution=require(path.join(ref,'src/electron/renderer/usageAttributionRows.js'));
const alias=require(path.join(ref,'src/electron/modelAliasPresentation.js'));
const sessions=require(path.join(ref,'src/electron/renderer/sessionRows.js'));
const input=[
 {periods:{today:{totalTokens:0,models:{},sessions:{}}}},
 {periods:{today:{totalTokens:1000,costUsd:21,models:{'vendor/shared':800,other:200},modelCosts:{'vendor/shared':1,other:20},modelOutputs:{'vendor/shared':600,other:100},tokenComponentsAvailable:true}}},
 {periods:{today:{totalTokens:1000,costUsd:21,models:{'vendor/shared':800,shared:200},modelCosts:{'vendor/shared':1,shared:20},modelOutputs:{'vendor/shared':600,shared:100},tokenComponentsAvailable:true}}},
 {periods:{today:{totalTokens:1000,models:{'vendor/shared':1000}}},devices:[{deviceId:'public',periods:{today:{modelCosts:{shared:0}}}}]},
 {periods:{today:{totalTokens:1000,models:{'vendor/shared':700,shared:300},sessions:{public:{client:'codex',totalTokens:1000,models:{'vendor/shared':700,shared:300},lastUsedAt:'2026-10-10T00:00:00Z'}}}}},
 {periods:{today:{totalTokens:10000,models:{a:6000,b:2000},sessions:{public:{client:'codex',totalTokens:10000,models:{'vendor/shared':5999,shared:1,b:2000},lastUsedAt:'2026-10-10T00:00:00Z'}}}},nativeProjects:{today:{public:{model:'shared',modelCosts:{b:0}}}},historyPreview:{daily:[],summary:{favoriteModel:'shared'}}}
];
const fallback=[],aliasCases=[];
for(const [index,stats] of input.entries())for(const grouping of ['off','duplicates','prefix']) {
 const aliases=index===2||index===4?{'vendor/shared':'Public model',shared:'Public model'}:index===5?{'vendor/shared':'shared'}:{};
 const projected=alias.projectModelAliasStats(stats,aliases,{grouping});
 aliasCases.push({input:stats,aliases,grouping,models:projected.periods.today.models,sessions:Object.entries(projected.periods.today.sessions||{}).map(([id,row])=>({id,models:row.models,label:sessions.sessionModelLabel(row),shares:sessions.sessionModelTooltipEntries(row,{formatTokens:String,unattributedLabel:'__fixture_unclassified__'})}))});
 for(const metric of ['tokens','cost']) {
  const scope={state:{settings:{modelRankingMetric:metric}},usageAttributionRowsApi:attribution,rawSessionRowsForPeriod:()=>[],sessionRowsApi:sessions,
   formatCost:n=>Number(n).toFixed(2),t:()=> 'Unattributed',modelColor:id=>id,clientColors:{default:'gray'},clientLabels:{},
   clientDisplayPreferencesApi:require(path.join(ref,'src/electron/renderer/clientDisplayPreferences.js')),KNOWN_CLIENTS:[],deviceRowsForPeriod:()=>[],
   toolDetailsApi:require(path.join(ref,'src/electron/renderer/toolDetails.js'))};
  vm.createContext(scope);vm.runInContext(['attributionComponent','periodAttributionRows','toolRowsForPeriod','modelRowsForPeriod','sessionRowsForPeriod'].map(fn).join('\n'),scope);
  const rows=scope.sessionRowsForPeriod(projected.periods.today);
  fallback.push({input:stats,aliases,grouping,metric,rows:rows.map(r=>({id:r.key,tokens:r.value,cost:r.cost,barValue:r.barValue,unpriced:r.unpricedTokens||0}))});
 }
}
const sequences=[
 [[100,'running'],[100,'running'],[101,'running'],[101,'idle'],[102,'running'],[0,'running'],[103,'running']],
 [[0,'running'],[-1,'running'],[100,'idle'],[101,'running'],[50,'running'],[51,'running']],
 [[100,'idle'],[101,'idle'],[102,'running'],[102,'ended'],[103,'ended'],[104,'running']]
].map(steps=>{
 let pulses=0;const classes=new Set(),dot={classList:{toggle:(k,on)=>on?classes.add(k):classes.delete(k),remove:k=>classes.delete(k),add:k=>{classes.add(k);if(k==='pulse')pulses++}},offsetWidth:0};
 const row={dataset:{},querySelector:()=>dot},scope={t:()=> 'Running'};vm.createContext(scope);vm.runInContext(fn('updateRowLive'),scope);
 return steps.map(([at,state])=>{const before=pulses;scope.updateRowLive(row,state,at);return {at,state,pulse:pulses>before,active:classes.has('is-active')}});
});
(async()=>{
 let browser;
 try {
  browser=await chromium.launch({executablePath:'/Applications/Microsoft Edge.app/Contents/MacOS/Microsoft Edge',headless:true,args:['--disable-background-networking','--disable-component-update','--disable-sync','--no-first-run']});
  const page=await browser.newPage();await page.route('**/*',route=>route.abort());
  await page.setContent('<div class="row-name"><span class="row-live is-active pulse"><span class="row-live-dot"></span></span></div>');
  await page.addStyleTag({content:fs.readFileSync(path.join(ref,'src/electron/renderer/styles.css'),'utf8')});
  const frames=await page.evaluate(()=>{
   const dot=document.querySelector('.row-live-dot'),animation=dot.getAnimations()[0];animation.pause();
   return [0,60,150,300,420,600,900,1200,1300].map(ms=>{
    animation.currentTime=ms;const shadow=getComputedStyle(dot).boxShadow;
    const matches=[...shadow.matchAll(/rgba\(([^)]+)\) 0px 0px ([\d.]+)px 0px/g)];const m=matches.at(-1);if(!m)throw Error(shadow);
    return {ms,blur:Number(m[2]),opacity:Number(m[1].split(',').at(-1)),shadow};
   });
  });
  const result={reference:'v0.68.0 original stats alias projection, session fallback, updateRowLive and CSS',fallback,aliasCases,sequences,frames};
  fs.writeFileSync(path.join(root,'app/src/test/resources/session-parity.json'),JSON.stringify(result,null,2)+'\n');
  process.stdout.write(`Original fallback cases: ${fallback.length}; alias contexts: ${aliasCases.length}; timestamp sequences: ${sequences.length}; CSS frames: ${frames.length}\n`);
 } finally {if(browser)await browser.close()}
})().catch(e=>{process.stderr.write(e.message+'\n');process.exitCode=1});
