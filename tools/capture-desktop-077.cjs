// Immutable v0.77.0 source, public fixtures, isolated browser with outbound requests blocked.
const fs=require('fs'),path=require('path'),cp=require('child_process');
const {chromium}=require('/Users/bing/.cache/codex-runtimes/codex-primary-runtime/dependencies/node/node_modules/playwright');
const ref=path.resolve(process.argv[2]||'');
const sourceCommit=cp.execFileSync('git',['-C',ref,'rev-parse','HEAD'],{encoding:'utf8'}).trim();
if(sourceCommit!=='c83ee3aeb6d23505257113c29ef010040deb52f7') throw Error('Expected desktop v0.77.0');
const rate=require(path.join(ref,'src/electron/renderer/tokenRatePresentation.js'));
const previous=JSON.parse(fs.readFileSync(path.join(__dirname,'../app/src/test/resources/main-display-parity.json')));
const p=(tokens,output,duration)=>({timedTokens:tokens,timedOutputTokens:output,timedDurationMs:duration});
const period=models=>({...Object.values(models).reduce((a,b)=>p(a.timedTokens+b.timedTokens,a.timedOutputTokens+b.timedOutputTokens,a.timedDurationMs+b.timedDurationMs),p(0,0,0)),modelThroughput:models});
const entry=period=>[{id:'device:public',name:'Public device',period}];
const a=p(100,40,1000),b=p(200,20,2000);
const fresh=events=>{let now=0;const tracker=rate.createLiveTokenRateGroupTracker({now:()=>now});return {events:events.map(({expected,...event})=>{now=event.at;if(event.reset) tracker.reset(event.reset);else if(event.entries) tracker.observe(event.entries);return {...event,expected:tracker.getSample()};})};};
const scenarios=previous.scenarios.map(s=>fresh(s.events));
scenarios.push(fresh([{at:0,reset:entry(period({}))},{at:100,entries:entry(period({alpha:a}))},{at:200,entries:entry(period({alpha:a,beta:b}))},{at:8100},{at:8200},{at:180200}]));
// Retained independent legacy remainder, attribution regression, and duplicate ticks.
scenarios.push(fresh([{at:0,reset:entry({...p(0,0,0),modelThroughput:{}})},{at:100,entries:entry({...p(300,60,3000),modelThroughput:{alpha:a}})},{at:200,entries:entry({...p(400,100,4000),modelThroughput:{alpha:p(200,80,2000)}})},{at:8100},{at:8200},{at:8300,entries:entry({...p(500,120,5000),modelThroughput:{alpha:a}})}]));
const providers=[];
for(const adapterId of ['newapi-account','newapi-token','sub2api','custom']) for(const balance of [
 {requestCount:5,todaySpend:1,weekSpend:7,monthSpend:12,allTimeSpend:40},
 {todaySpend:0,weekSpend:0,monthSpend:0,allTimeSpend:0},
 {weekSpend:7,allTimeSpend:40},{todaySpend:null,monthSpend:12},{}
]) providers.push({provider:'thirdparty',adapterId,status:'ok',windows:[],balance:{currency:'USD',...balance}});
const scripts=['src/shared/clientCatalog.js','src/shared/vendorPresentation.js','src/shared/compactTokens.js','src/shared/limits/providers.js','src/shared/limits/windowLabels.js','src/shared/limits/usageItems.js','src/shared/limits/balanceDisplay.js','src/shared/limits/windowText.js','src/shared/currency.js','src/shared/subscriptionDisplay.js','src/shared/subscriptionText.js','src/electron/renderer/accountIdentity.js','src/electron/renderer/i18n.js','src/shared/providers/workbuddy/identity.js','src/electron/renderer/limits/providerPresentation.js','src/electron/renderer/limits/resetMotion.js','src/electron/renderer/limits/displayMode.js','src/electron/renderer/limits/windowsView.js'];
(async()=>{let browser;try {
 browser=await chromium.launch({executablePath:'/Applications/Microsoft Edge.app/Contents/MacOS/Microsoft Edge',headless:true});
 const page=await browser.newPage({timezoneId:'Asia/Shanghai'});await page.route('**/*',route=>route.request().url()==='https://example.invalid/'?route.fulfill({body:'<main></main>',contentType:'text/html'}):route.abort());await page.goto('https://example.invalid/');
 page.on('pageerror',e=>{throw e;});await page.setContent('<base href="https://example.invalid/"><main></main>');for(const file of scripts) await page.addScriptTag({path:path.join(ref,file)});
 const cases=await page.evaluate(({providers})=>{
  const balance=TokenMonitorLimitBalanceDisplay,labels=TokenMonitorLimitWindowLabels,mode=TokenMonitorLimitDisplayMode,structure=TokenMonitorLimitProviderPresentation;
  const all=[];
  for(const locale of ['en','zh-CN']) {const settings={};
    const view=TokenMonitorLimitWindowsView.createLimitWindowsView({document,t:(key,params)=>TokenMonitorI18n.translate(locale,key,params),settings:()=>settings,currentLocale:()=>locale,
     presentation:structure,motion:TokenMonitorLimitResetMotion,tooltip:{hasOpened:()=>false,markOpened(){},release(){}},
     formatCompact:value=>String(value),compactTokenThreshold:()=>1e3,formatMoney:balance.formatMoney,formatCompactMoney:balance.formatMoney,
     formatPercent:value=>Number.isFinite(Number(value))?`${Math.round(Number(value))}%`:'--',formatDuration:structure.limitDurationText,formatLimitBoundary:structure.limitBoundaryText,
     ...mode,optionalFiniteNumber:value=>value==null||value===''?null:Number.isFinite(Number(value))?Number(value):null,
     colorWithAlpha:(hex,alpha)=>`rgba(${[1,3,5].map(i=>parseInt(hex.slice(i,i+2),16)).join(',')},${alpha})`,applyBarScale:(node,scale)=>node.style.setProperty('--bar-scale',String(scale)),
     ...balance,...labels,limitWindowText:TokenMonitorLimitWindowText.limitWindowText,accountIdentity:TokenMonitorAccountIdentity,
     provenanceContext:()=>({syncActive:true,localDeviceId:'public-viewer'}),accountControl:{render:options=>options.titleNode},codexAccounts:{matchesActive:()=>false,switchTarget:()=>null,canSwitchSystemAccount:()=>false},hasMark:id=>TokenMonitorVendorPresentation.MARK_IDS.includes(id),
     formatAgo:ms=>`${Math.round(ms/60000)}m ago`,openExternal(){},subscriptionApi:TokenMonitorSubscriptionDisplay,subscriptionText:TokenMonitorSubscriptionText,currencyApi:TokenMonitorCurrency,
     formatCost:value=>'$'+Number(value).toFixed(2),subscriptions:()=>[],subscriptionAccounts:()=>[],monthClientCosts:()=>({})});

   for(const provider of providers) {const rendered=view.renderProviderWindows(provider,'#abcdef',{showAllUsageItems:true});
    all.push({locale,provider,rows:[...rendered.querySelectorAll('.limit-window')].map(node=>{const text=node.querySelector('.limit-window-text');const visible=text?.children[1]?.cloneNode(true);visible?.querySelectorAll('[role=tooltip],.limit-detail-tooltip-trigger').forEach(e=>e.remove());return {item:node.dataset.usageItem,label:text?.children[0]?.textContent??'',value:visible?.textContent??'',details:[...node.querySelectorAll('.limit-detail-tooltip-row,.limit-detail-tooltip-full')].map(row=>row.classList.contains('limit-detail-tooltip-full')?[row.textContent]:[...row.children].map(cell=>cell.textContent))};})});
   }
  } return all;
 },{providers});
 const protocol=require(path.join(ref,'src/shared/hubProtocol'));
 const stats={periods:{today:{}},devices:[],limits:{providers:[providers[0],{provider:'cursor',status:'unavailable',windows:[]},...['public-a','public-b'].map(accountKey=>({provider:'cline',accountKey,email:'same@example.invalid',status:'ok',windows:[{kind:'billing',metric:'credits',remaining:10,currency:'USD'}]}))]}};
 const fanout=protocol.prepareSseFanout({reason:'ingest',stats,at:'2026-10-22T00:00:00Z'});
 const fresh=protocol.prepareSseFanout({reason:'ingest',stats,at:'2026-10-22T00:01:00Z',lastContentKey:fanout.contentKey,hasFreshnessClients:true,hasLegacyClients:true});
 const transport={stats,full:fanout.stats,legacy:fresh.stats,freshness:fresh.freshness};
 fs.writeFileSync(path.join(__dirname,'../app/src/sharedTest/resources/desktop-077.json'),JSON.stringify({sourceCommit,scenarios,cases,transport,registry:require(path.join(ref,'src/shared/hubBuildRegistry.json'))},null,2)+'\n');
 console.log(`Captured ${scenarios.length} rate streams and ${cases.length} actual DOM cases`);
}finally{await browser?.close();}})().catch(e=>{console.error(e);process.exitCode=1});
