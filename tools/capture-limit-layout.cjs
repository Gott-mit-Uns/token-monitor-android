// Public fixtures rendered by the fixed desktop DOM builder and actual stylesheet.
// Temporary independent browser profile; no application settings, Hub, or credentials.
const fs=require('node:fs'),path=require('node:path'),http=require('node:http');
const {chromium}=require('/Users/bing/.cache/codex-runtimes/codex-primary-runtime/dependencies/node/node_modules/playwright');
const root=path.resolve(__dirname,'..'),ref=path.resolve(root,'../work/token-monitor-reference/desktop-v0.68.0');
const evidence=path.join(root,'evidence/limit-preferences');fs.mkdirSync(evidence,{recursive:true});
const now='2026-10-10T12:00:00Z';
const window=(kind,remainingPercent=75,extra={})=>({kind,remainingPercent,usedPercent:100-remainingPercent,resetsAt:'2026-10-10T16:26:00Z',...extra});
const providers=[
 {provider:'codex',accountLabel:'Plus',windows:[window('weekly',65,{limitId:'codex.weekly',windowMinutes:10080}),window('session'),window('billing',80),window('weekly',90,{additional:true,label:'GPT Reserve Weekly',limitName:'GPT Reserve'})]},
 {provider:'cursor',accountLabel:'Pro',windows:[window('billing',70,{label:'Included usage'}),window('billing',50,{label:'Usage credits',metric:'spend',used:12.4,limit:20,currency:'USD'})]},
 {provider:'antigravity',windows:[window('weekly',30,{label:'Gemini weekly'}),window('session',75,{label:'Claude 5-hour'}),window('session',55,{label:'Gemini 5-hour'}),window('weekly',12,{label:'Claude weekly'})]},
 {provider:'opencode',accountName:'Go profile',accountLabel:'Go',windows:[window('billing',90),window('weekly',80),window('session',75)]},
 {provider:'opencode',accountLabel:'Zen',balanceUsd:12,windows:[window('billing',80,{metric:'credits',remaining:12,currency:'USD'})]},
 {provider:'openrouter',balance:{amount:680,currency:'USD',todaySpend:2,weekSpend:9,monthSpend:20,allTimeSpend:90},windows:[window('billing',80,{metric:'credits',remaining:680,currency:'USD'}),window('billing',60,{label:'Daily key limit',remaining:6,limit:10,currency:'USD'})]},
 {provider:'thirdparty',accountName:'Demo relay',adapterId:'sub2api',balance:{amount:12,currency:'USD',monthSpend:3,allTimeSpend:80,requestCount:400},windows:[window('billing',75,{metric:'credits',label:'Balance',remaining:12,currency:'USD',limit:16})],usageSummary:{requests:30,totalTokens:15000,inputTokens:10000,outputTokens:5000,averageDurationMs:1520,standardCost:2}},
 {provider:'deepseek',balance:{amount:28,currency:'CNY',todaySpend:1,weekSpend:4,monthSpend:12,allTimeSpend:50},windows:[window('billing',70,{metric:'credits',remaining:28,currency:'CNY'})]},
 {provider:'mimo',accountLabel:'Console',balance:{amount:20,currency:'CNY',giftBalance:5,cashBalance:15,planUsed:25,planLimit:100,monthSpend:3},windows:[]},
 {provider:'grok',accountLabel:'SuperGrok',windows:[window('billing',60)]},
 {provider:'copilot',windows:[window('billing',75,{label:'Premium requests'}),window('billing',35,{label:'Chat'})]},
 {provider:'zed',windows:[window('billing',50,{label:'Token Spend',used:10,limit:20,currency:'USD'}),window('billing',100,{label:'Edit predictions',limitId:'zed.edit-predictions',detail:'unlimited',showMeter:false})]},
 {provider:'zai',windows:[window('weekly',80),window('daily',75,{label:'Daily model A',remaining:750,limit:1000}),window('session',50),window('billing',90,{limitId:'zcode.start',label:'Start Plan',remaining:900,limit:1000}),window('billing',70,{label:'MCP',detail:'7/10'}),window('billing',80,{metric:'credits',remaining:12,currency:'CNY'})]},
 {provider:'volcengine',windows:[window('billing',40),window('weekly',50),window('daily',60),window('session',70)]},
 {provider:'devin',windows:[window('weekly',50),window('daily',75),window('billing',90,{metric:'credits',remaining:10,currency:'USD'})]},
 {provider:'factory',windows:[window('session',80,{label:'Standard 5-hour'}),window('session',20,{label:'Core 5-hour',additional:true}),window('weekly',70,{label:'Standard Weekly'}),window('weekly',10,{label:'Core Weekly',additional:true}),window('billing',65,{label:'Standard Monthly'}),window('billing',5,{label:'Core Monthly',additional:true}),window('billing',90,{metric:'credits',label:'Extra usage balance',remaining:12,currency:'USD'})]},
 {provider:'kiro',windows:[window('billing',75,{label:'Credits',used:25,limit:100}),window('billing',50,{label:'Bonus',used:10,limit:20}),{kind:'billing',label:'Overage',used:12.5,remaining:3.2,showMeter:false}]},
 {provider:'qoder',windows:[window('billing',75,{used:25,limit:100})]},
 {provider:'workbuddy',balance:{amount:680,currency:'CREDITS',monthSpend:100},windows:[window('billing',75,{metric:'credits',remaining:680,currency:'CREDITS'})]},
 {provider:'trae',windows:[window('billing',75,{metric:'credits',remaining:680,currency:'CREDITS'})]},
 {provider:'commandcode',windows:[window('billing',68,{metric:'credits',remaining:47.42,limit:70,currency:'USD'}),window('weekly',80),window('session',90)]},
 {provider:'kimi',windows:[window('billing',75,{detail:'Kimi 10% · Code 15%'}),window('weekly',70),window('session',80)]},
 {provider:'alibaba',windows:[window('weekly',75),window('session',70),window('billing',65)]},
 {provider:'stepfun',windows:[window('weekly',75),window('session',70),window('billing',65)]},
 {provider:'ollama',windows:[window('weekly',75),window('session',70)]},
 {provider:'claude',accountLabel:'Pro',windows:[window('weekly',75),window('session',70),window('weekly',65,{label:'Fable only'}),window('billing',50,{metric:'spend',used:2.35,limit:20,currency:'USD',label:'Usage credits'})]},
 {provider:'cline',windows:[window('billing',60),window('weekly',70),window('session',80),window('billing',90,{metric:'credits',remaining:10,currency:'USD',label:'Credits'}),window('billing',90,{metric:'spend',used:2,currency:'USD'})]},
 {provider:'minimax',windows:[window('weekly',75),window('session',70)]},
 {provider:'unknown-provider',windows:[window('session',70),window('weekly',75)]},
 {provider:'typesafe',balance:{amount:35,currency:'USD',monthSpend:15,tranches:[{amount:5,currency:'USD',expiresAt:'2026-10-12T12:00:00Z'}]},windows:[window('billing',70,{metric:'credits',boundaryKind:'expiry'})],usageSummary:{period:'month',todayTokens:4500,weekTokens:16000,totalTokens:28000,inputTokens:20000,outputTokens:8000,requests:48,standardCost:1.25}},
 {provider:'thirdparty',adapterId:'newapi-token',windows:[{kind:'billing',metric:'credits',showMeter:false,detail:'unlimited'}]},
 ...['disabled','notConfigured','noSyncedData','unauthorized','rateLimited','sourceRateLimited','unavailable','error'].map(status=>({provider:'codex',status,windows:[]})),
 {provider:'opencode',accountName:'Named profile',accountLabel:'Legacy profile',stale:true,status:'unauthorized',updatedAt:'2026-10-10T10:00:00Z',windows:[window('session')]},
 {provider:'codex',status:'ok',updatedAt:null,checkedAt:'2026-10-10T11:45:00Z',windows:[window('session')]},
 {provider:'codex',windows:[window('session')],resetCredits:{availableCount:4.8,expirations:['2026-10-13T12:00:00Z','2026-10-10T11:00:00Z','2026-10-11T12:00:00Z','invalid','2026-10-12T12:00:00Z']}},
 {provider:'codex',windows:[],resetCredits:{availableCount:1,nextExpiresAt:'2026-10-10T16:26:00Z'}},
 {provider:'claude',windows:[window('session'),window('weekly')],balance:{amount:15,currency:'USD',tranches:[{amount:5,expiresAt:'2026-10-12T12:00:00Z'},{amount:10}]},resetCredits:{availableCount:2,expirations:['2026-10-11T12:00:00Z'],grants:[{label:'Public session reset',resetsLeft:1,endsAt:'2026-10-11T12:00:00Z',clears:['five_hour','seven_day','seven_day_overage_included'],useRequiresLimit:true},{label:'Public scoped reset',resetsLeft:1,paused:true,clears:['seven_day_opus','future_scope'],usableNow:false}]}},
 {provider:'claude',windows:[],balance:{amount:5,currency:'USD',tranches:[{amount:5,expiresAt:'2026-10-09T12:00:00Z'}]},resetCredits:{availableCount:0,grants:[{resetsLeft:0}]}},
 {provider:'codex',windows:[],resetCredits:{availableCount:-1,expirations:['invalid']}}


].map((p,index)=>({status:'ok',updatedAt:now,source:'api',accountKey:'public-fixture-'+index,...p}));
const scripts=['src/shared/clientCatalog.js','src/shared/vendorPresentation.js','src/electron/renderer/rowIconMasks.js','src/shared/compactTokens.js','src/shared/limits/providers.js','src/shared/limits/windowLabels.js','src/shared/limits/usageItems.js','src/shared/limits/balanceDisplay.js','src/shared/limits/windowText.js','src/shared/currency.js','src/shared/subscriptionDisplay.js','src/shared/subscriptionText.js','src/electron/renderer/accountIdentity.js','src/electron/renderer/i18n.js','src/electron/renderer/limits/providerPresentation.js','src/electron/renderer/limits/resetMotion.js','src/electron/renderer/limits/displayMode.js','src/electron/renderer/limits/windowsView.js'];
(async()=>{
 const html='<!doctype html><meta charset="utf-8"><base href="/reference/src/electron/renderer/"><link rel="stylesheet" href="styles.css"><style>body{margin:0;background:rgb(48,52,56);height:auto;overflow:auto;border-radius:0}#fixture{padding:14px;width:360px}.limit-row{margin-bottom:16px}</style><main id="fixture"></main>';
 const files=new Set([...scripts,'src/electron/renderer/styles.css']);
 const server=http.createServer((req,res)=>{
  const name=decodeURIComponent(new URL(req.url,'http://127.0.0.1').pathname);
  if(req.method!=='GET') {res.writeHead(405).end();return;}
  if(name==='/fixture.html') {res.writeHead(200,{'Content-Type':'text/html; charset=utf-8'}).end(html);return;}
  const relative=name.replace(/^\/reference\//,'');
  if(!name.startsWith('/reference/') || !(files.has(relative)||/^assets\/icons\/[a-z0-9-]+\.svg$/.test(relative))) {res.writeHead(404).end();return;}
  const file=path.join(ref,relative);
  if(!fs.existsSync(file)) {res.writeHead(404).end();return;}
  res.writeHead(200,{'Content-Type':relative.endsWith('.css')?'text/css':relative.endsWith('.svg')?'image/svg+xml':'text/javascript'}).end(fs.readFileSync(file));
 });
 await new Promise(resolve=>server.listen(0,'127.0.0.1',resolve));
 const origin='http://127.0.0.1:'+server.address().port;
 let browser;
 try {
  browser=await chromium.launch({executablePath:'/Applications/Microsoft Edge.app/Contents/MacOS/Microsoft Edge',headless:true,args:['--disable-background-networking','--disable-component-update','--disable-sync','--no-first-run']});
  const page=await browser.newPage({viewport:{width:360,height:640},deviceScaleFactor:2,timezoneId:'Asia/Shanghai'});
  await page.route('**/*',route=>new URL(route.request().url()).origin===origin && route.request().method()==='GET'?route.continue():route.abort());
  await page.goto(origin+'/fixture.html');
  for(const file of scripts) await page.addScriptTag({url:origin+'/reference/'+file});
  const vendor=require(path.join(ref,'src/shared/vendorPresentation.js')).vendorColors();
  const providerColors=Object.fromEntries(providers.map(p=>[p.provider,vendor[p.provider==='factory'?'droid':p.provider]||vendor.default]));
  const captured=await page.evaluate(({providers,now,providerColors})=>{
   Date.now=()=>Date.parse(now);
   const balance=TokenMonitorLimitBalanceDisplay,labels=TokenMonitorLimitWindowLabels,mode=TokenMonitorLimitDisplayMode,structure=TokenMonitorLimitProviderPresentation;
   function readWindows(rendered) {
return [...rendered.querySelectorAll('.limit-window')].map(node=>{
      const text=node.querySelector('.limit-window-text'),meter=node.querySelector('.limit-meter-fill'),reset=node.querySelector('.limit-reset');
      const group=node.closest('.limit-window-group');
      const visible=text?.children[1]?.cloneNode(true);visible?.querySelectorAll('[role=tooltip],.limit-detail-tooltip-trigger').forEach(el=>el.remove());
      const resetVisible=reset?.cloneNode(true);resetVisible?.querySelector('.limit-detail')?.remove();
      return {label:text?.children[0]?.textContent??'',value:node.querySelector('.limit-reset-credits-value')?.textContent??visible?.textContent??'',group:group?.querySelector('.limit-window-group-title')?.textContent??null,
       wide:node.classList.contains('limit-window-wide')||node.parentElement?.children.length===1,noReset:node.classList.contains('limit-window-no-reset'),
       percent:meter?Number(meter.style.getPropertyValue('--bar-scale'))*100:null,tone:meter?Number(meter.style.opacity||1):null,
       reset:node.classList.contains('limit-window-no-reset')?'':resetVisible?.textContent??'',detail:node.querySelector('.limit-detail')?.textContent??'',item:node.dataset.usageItem??null,
       resetSummary:node.querySelector('.limit-reset-credits-timeline')?.textContent??(node.classList.contains('limit-reset-credits')?'':null),
       details:[...node.querySelectorAll('.limit-detail-tooltip-row,.limit-detail-tooltip-full')].map(row=>row.classList.contains('limit-detail-tooltip-full')?[row.textContent]:[...row.children].map(cell=>cell.textContent))};
     });
   }
   const results=[];const hiddenCases=[];
   for(const locale of ['en','zh-CN']) for(const used of [false,true]) {
    const settings={showLimitUsed:used,claudePrepaidBalanceEnabled:true};
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
    for(const provider of providers) {
     if(!used) {
      const all=view.renderProviderWindows(provider,providerColors[provider.provider],{showAllUsageItems:true});
      const ids=[...new Set(readWindows(all).map(row=>row.item).filter(Boolean))];
      const selections=[ids.slice(0,1),ids.slice(1,2),ids.filter((_,i)=>i%2===0),ids];
      for(const hidden of selections.filter(ids=>ids.length)) {
       settings.limitProviderHiddenItems={[provider.provider]:hidden};
       hiddenCases.push({locale,provider,hidden,windows:readWindows(view.renderProviderWindows(provider,providerColors[provider.provider]))});
      }
      delete settings.limitProviderHiddenItems;
     }
     const rendered=view.renderProviderWindows(provider,providerColors[provider.provider],{showAllUsageItems:true});
     const windows=readWindows(rendered);
     results.push({locale,used,provider,color:providerColors[provider.provider],windows,plan:view.limitAccountPlan(provider),meta:view.limitProviderMeta(provider)});
     if(locale==='en' && !used && ['codex','factory','antigravity','openrouter','kiro'].includes(provider.provider)) {
      const row=view.renderLimitProviderSolo(provider.provider,(TokenMonitorLimitProviders.LIMIT_PROVIDER_CATALOG.find(row=>row.id===provider.provider)?.settingsLabel || TokenMonitorLimitProviders.LIMIT_PROVIDER_LABELS[provider.provider] || provider.provider),provider,providerColors[provider.provider]);row.dataset.fixtureProvider=provider.provider;document.getElementById('fixture').append(row);
     }
    }
   }
   return {cases:results,hiddenCases};
  },{providers,now,providerColors});
  const {cases,hiddenCases}=captured;
  fs.mkdirSync(path.join(root,'app/src/sharedTest/resources'),{recursive:true});
  fs.writeFileSync(path.join(root,'app/src/sharedTest/resources/limit-layout-parity.json'),JSON.stringify({source:'v0.68.0/5d2db368d8313415763860d594de00e46a663418; actual windowsView DOM builder; full numeric formatter',now,zone:'Asia/Shanghai',cases,hiddenCases},null,2)+'\n');
  if(await page.locator('.limit-icon').first().evaluate(node=>getComputedStyle(node).maskImage)==='none') throw new Error('Original icon mask stylesheet not installed');
  await page.screenshot({path:path.join(evidence,'desktop-provider-reference-dark.png'),fullPage:true});
  for(const id of ['codex','factory','antigravity','openrouter','kiro']) await page.locator(`[data-fixture-provider="${id}"]`).first().screenshot({path:path.join(evidence,`desktop-${id}-reference-dark.png`)});
  fs.writeFileSync(path.join(evidence,'source-reference-summary.txt'),`Actual fixed desktop DOM render: ${cases.length} provider/mode/locale cases\nPublic fixtures only; separate temporary browser profile; remote requests blocked\nOriginal windowsView DOM and stylesheet used; numeric compaction disabled by requirement\n`);
  process.stdout.write(`Captured actual desktop DOM for ${cases.length} provider/mode/locale cases\n`);
 } finally {try {await browser?.close();} finally {await new Promise(resolve=>server.close(resolve));} if(fs.existsSync(path.join(evidence,'source-fixture.html'))) fs.unlinkSync(path.join(evidence,'source-fixture.html'));}
})().catch(error=>{process.stderr.write(error.stack+'\n');process.exitCode=1});
