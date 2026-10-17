// Public fixtures rendered by the fixed desktop DOM builder and actual stylesheet.
// Temporary independent browser profile; no application settings, Hub, or credentials.
const fs=require('node:fs'),path=require('node:path'),http=require('node:http');
const {chromium}=require('/Users/bing/.cache/codex-runtimes/codex-primary-runtime/dependencies/node/node_modules/playwright');
const root=path.resolve(__dirname,'..'),ref=path.resolve(root,'../work/token-monitor-reference/desktop-v0.68.0');
const evidence=path.join(root,'evidence/subscription-layout');fs.mkdirSync(evidence,{recursive:true});
const now='2026-10-10T12:00:00Z';
const window=(kind,remainingPercent=75,extra={})=>({kind,remainingPercent,usedPercent:100-remainingPercent,resetsAt:'2026-10-10T16:26:00Z',...extra});
const pure=JSON.parse(fs.readFileSync(path.join(root,'app/src/test/resources/shared-display-parity.json'),'utf8'));
const identityFixtures=JSON.parse(fs.readFileSync(path.join(root,'app/src/test/resources/subscription-accounts-parity.json'),'utf8'));
const appSource=fs.readFileSync(path.join(ref,'src/electron/renderer/app.js'),'utf8');
const appFunctions=['subscriptionRowTitle', 'subscriptionRowAccountLabel', 'subscriptionRowMeta', 'subscriptionProviderIconClass', 'renderSubscriptionRows', 'clearSubscriptionListChildren', 'positionSubscriptionEditor'].map(name=>{const start=appSource.indexOf('function '+name+'(');if(start<0)throw new Error('Missing original '+name);return appSource.slice(start,appSource.indexOf('\n}',start)+2)}).join('\n');
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
  const captured=await page.evaluate(({pure,identityFixtures,appFunctions,now})=>{
   const RealDate=Date;Date=class extends RealDate {constructor(...args){super(...(args.length?args:['2026-10-10T04:00:00Z']))} static now(){return RealDate.parse('2026-10-10T04:00:00Z')}};
   const subscriptionApi=TokenMonitorSubscriptionDisplay,subscriptionText=TokenMonitorSubscriptionText,currencyApi=TokenMonitorCurrency,accountIdentityApi=TokenMonitorAccountIdentity;
   const LIMIT_PROVIDERS=TokenMonitorLimitProviders.LIMIT_PROVIDER_CATALOG;
   const balance=TokenMonitorLimitBalanceDisplay,labels=TokenMonitorLimitWindowLabels,mode=TokenMonitorLimitDisplayMode,structure=TokenMonitorLimitProviderPresentation;
   const results=[],cards=[];const scenarios=[pure.subscriptions.map(c=>c.input),pure.subscriptions.filter(c=>c.today==='2026-10-10').map(c=>c.input)];
   const bound=[{id:'public-exact',provider:'codex',kind:'subscription',binding:{accountKey:'public-b',profileName:'Old profile'},planName:'Plus',amountMinor:2000,currency:'USD',interval:'month',intervalCount:1,startDate:'2026-01-31',autoRenew:true},
    {id:'public-ambiguous',provider:'codex',kind:'subscription',binding:{profileName:'Same profile'},planName:'Plus',amountMinor:2000,currency:'USD',interval:'month',intervalCount:1,startDate:'2026-01-31',autoRenew:true},
    {id:'public-unknown-provider',provider:'future-provider',kind:'subscription',binding:{profileName:'Public future account'},planName:'Future plan',amountMinor:1200,currency:'HKD',interval:'year',intervalCount:1,startDate:'2026-01-31',autoRenew:false}];
   scenarios.push(bound);
   const providersCases=[[],identityFixtures.cases.find(c=>c.input.some(p=>p.accountKey==='public-b') && c.input.some(p=>p.accountName==='Same profile')).input,
    [{provider:'codex',accountKey:'public-a',accountName:'Public current name',accountEmail:'first@example.test',accountLabel:'Plus',status:'ok',updatedAt:now,windows:[{kind:'session',remainingPercent:70}]},
     {provider:'codex',accountKey:'public-b',accountName:'Public second name',accountEmail:'second@example.test',accountLabel:'Plus',status:'ok',updatedAt:now,windows:[{kind:'session',remainingPercent:30}]}]];
   for(const locale of ['en','zh-CN']) for(const input of scenarios) for(const providerInput of providersCases) for(const mask of [false,true]) for(const icons of [false,true]) {
    const settings={subscriptions:input,maskLimitAccountEmails:mask,showToolIcons:icons};const state={settings,subscriptionEditingId:null};
    const providers=accountIdentityApi.dedupeAccounts(providerInput);
    const els={subscriptionList:document.getElementById('fixture'),subscriptionAddDetails:null};
    const t=(key,params)=>TokenMonitorI18n.translate(locale,key,params),currentLocale=()=>locale,toolIconsEnabled=value=>value!==false;
    const subscriptionList=()=>subscriptionApi.normalizeSubscriptions(input,{currencyApi}),limitProvidersForSubscriptions=()=>providers;
    const original=new Function('state','els','subscriptionApi','subscriptionText','currencyApi','accountIdentityApi','LIMIT_PROVIDERS','t','currentLocale','toolIconsEnabled','subscriptionList','limitProvidersForSubscriptions','beginSubscriptionEdit','closeSubscriptionEditor','saveSubscriptions','subscriptionSettingsVersion','setSubscriptionError',appFunctions+';renderSubscriptionRows();');
    original(state,els,subscriptionApi,subscriptionText,currencyApi,accountIdentityApi,LIMIT_PROVIDERS,t,currentLocale,toolIconsEnabled,subscriptionList,limitProvidersForSubscriptions,()=>{},()=>{},()=>{},()=>{},()=>{});
    const dom=[...els.subscriptionList.querySelectorAll('.subscription-row')].map(row=>({id:row.dataset.subscriptionId,title:row.querySelector('.subscription-row-title').textContent,meta:row.querySelector('.subscription-row-meta').textContent,warning:row.querySelector('.subscription-row-warn')?.textContent||'',icon:!!row.querySelector('.subscription-row-icon')}));
    results.push({locale,input,providers:providerInput,mask,icons,dom});
    if(locale==='en'&&input===bound&&providerInput===providersCases[1]&&!mask&&icons) {
     const copy=els.subscriptionList.cloneNode(true);copy.id='subscription-reference';copy.querySelectorAll('.subscription-row-actions').forEach(node=>node.remove());document.body.append(copy);
    }
   }
   const quota=(id,key,name,windows=[],balance)=>({provider:id,accountKey:key,accountName:name,accountLabel:'Public plan',status:'ok',windows,...(balance?{balance}:{})});
   const monthPlan=(provider,id,key,extra={})=>({id,provider,kind:'subscription',binding:{accountKey:key},planName:'Public plan',amountMinor:2000,currency:'USD',interval:'month',intervalCount:1,startDate:'2026-01-31',autoRenew:true,...extra});
   const ca=quota('codex','public-a','Public A',[{kind:'session',remainingPercent:70}]),cb=quota('codex','public-b','Public B',[{kind:'session',remainingPercent:30}]);
   const ledger={id:'public-ledger',provider:'openrouter',kind:'topup',binding:{accountKey:'public-relay'},currency:'USD',topUps:[{id:'t1',date:'2026-09-01',amountMinor:2000},{id:'t2',date:'2026-10-01',amountMinor:1500}]};
   const relay=quota('openrouter','public-relay','Public relay',[{kind:'billing',metric:'credits',remaining:10,currency:'USD'}],{amount:10,currency:'USD'});
   const specs=[
    {accounts:[ca],input:[monthPlan('codex','s-a','public-a')],costs:{codex:100}},
    {accounts:[ca,cb],input:[monthPlan('codex','s-a','public-a'),monthPlan('codex','s-b','public-b')],costs:{codex:150}},
    {accounts:[ca,cb],input:[monthPlan('codex','s-b','public-b')],costs:{codex:150}},
    {accounts:[ca],input:[monthPlan('codex','s-a','public-a',{autoRenew:false,startDate:'2026-09-01'})],costs:{codex:100}},
    {accounts:[ca],input:[monthPlan('codex','s-a','public-a',{startDate:'2026-11-01'})],costs:{}},
    {accounts:[relay],input:[ledger],costs:{}},
    {accounts:[{...relay,balance:{amount:5,currency:'CNY'},windows:[{kind:'billing',metric:'credits',remaining:5,currency:'CNY'}]}],input:[ledger],costs:{}},
    {accounts:[{...relay,balance:{amount:40,currency:'USD'},windows:[{kind:'billing',metric:'credits',remaining:40,currency:'USD'}]}],input:[ledger],costs:{}},
    {accounts:[quota('factory','public-factory','Public Factory',[{kind:'session',remainingPercent:80}])],input:[monthPlan('factory','s-f','public-factory')],costs:{droid:75}},
    {accounts:[{...relay,provider:'zai'}],input:[{...ledger,provider:'zai'}],costs:{zcode:42,zai:7}}
   ];
   for(const locale of ['en','zh-CN']) for(const spec of specs) for(const currency of ['USD','CNY']) {
    const {input,costs}=spec;const accounts=accountIdentityApi.dedupeAccounts(spec.accounts);const settings={currency};
    const currentLocale=()=>locale,t=(key,params)=>TokenMonitorI18n.translate(locale,key,params);
    const view=TokenMonitorLimitWindowsView.createLimitWindowsView({document,t:(key,params)=>TokenMonitorI18n.translate(locale,key,params),settings:()=>settings,currentLocale:()=>locale,
     presentation:structure,motion:TokenMonitorLimitResetMotion,tooltip:{hasOpened:()=>false,markOpened(){},release(){}},
     formatCompact:value=>String(value),compactTokenThreshold:()=>1e3,formatMoney:balance.formatMoney,formatCompactMoney:balance.formatMoney,
     formatPercent:value=>Number.isFinite(Number(value))?`${Math.round(Number(value))}%`:'--',formatDuration:structure.limitDurationText,formatLimitBoundary:structure.limitBoundaryText,
     ...mode,optionalFiniteNumber:value=>value==null||value===''?null:Number.isFinite(Number(value))?Number(value):null,
     colorWithAlpha:(hex,alpha)=>`rgba(${[1,3,5].map(i=>parseInt(hex.slice(i,i+2),16)).join(',')},${alpha})`,applyBarScale:(node,scale)=>node.style.setProperty('--bar-scale',String(scale)),
     ...balance,...labels,limitWindowText:TokenMonitorLimitWindowText.limitWindowText,accountIdentity:TokenMonitorAccountIdentity,
     provenanceContext:()=>({syncActive:true,localDeviceId:'public-viewer'}),accountControl:{render:options=>options.titleNode},codexAccounts:{matchesActive:()=>false,switchTarget:()=>null,canSwitchSystemAccount:()=>false},hasMark:id=>TokenMonitorVendorPresentation.MARK_IDS.includes(id),
     formatAgo:ms=>`${Math.round(ms/60000)}m ago`,openExternal(){},subscriptionApi:TokenMonitorSubscriptionDisplay,subscriptionText:TokenMonitorSubscriptionText,currencyApi:TokenMonitorCurrency,
     formatCost:value=>currencyApi.formatCurrencyFromUsd(value,settings.currency||'USD'),subscriptions:()=>input,subscriptionAccounts:()=>accounts,monthClientCosts:()=>costs});

    const targets=[...spec.accounts.map(provider=>({provider,includeRollup:false})),...spec.accounts.map(provider=>({provider,includeRollup:true})),{provider:{provider:spec.accounts[0].provider,accountGroup:true,groupAccounts:spec.accounts,windows:[],status:'ok'},includeRollup:true}];
    if(spec.accounts.length>1) targets.push({provider:{provider:spec.accounts[0].provider,accountGroup:true,groupAccounts:[spec.accounts[1]],windows:[],status:'ok'},includeRollup:true});
    for(const target of targets) {
     const node=view.renderLimitProviderHead(target.provider.provider,'Public provider',target.provider,'#73bdf5',{showIcon:false,planText:'Public plan',accountRow:!target.includeRollup});
     const card=node.querySelector('.subscription-tooltip');
     const rows=card?[...card.children].map(row=>row.classList.contains('subscription-tooltip-rule')?{separator:true}:{label:row.children[0].textContent,value:row.children[1].textContent,...(row.children[1].title?{title:row.children[1].title}:{}),...(row.children[1].classList.contains('subscription-tooltip-warn')?{warn:true}:{})}):[];
     cards.push({locale,currency,input,accounts:spec.accounts,costs,provider:target.provider,includeRollup:target.includeRollup,rows,decorated:!!card});
     if(card && locale==='en' && currency==='USD') {
      const name=target.provider.accountGroup===true && input.length===2 && spec.accounts.length===2 && target.provider.groupAccounts.length===2?'group':
       target.provider.provider==='codex' && input.length===2 && !target.provider.accountGroup && !target.includeRollup?'account':
       target.provider.provider==='factory' && !target.provider.accountGroup && target.includeRollup?'factory':
       target.provider.provider==='openrouter' && !target.provider.accountGroup && target.includeRollup && rows.some(row=>row.value==='$0.64/day')?'ledger':null;
      if(name && !document.getElementById('subscription-card-reference-'+name)) {
       const copy=card.cloneNode(true);copy.id='subscription-card-reference-'+name;
       Object.assign(copy.style,{position:'static',display:'grid',opacity:'1',transform:'none',margin:'16px',fontSize:'10px'});document.body.append(copy);
      }
     }

    }
   }
   const labelKeys=['settings.subscriptions.unitMonth','settings.subscriptions.unitYear','settings.subscriptions.everyN','settings.subscriptions.renewsOn','settings.subscriptions.endsOn','settings.subscriptions.needsRebind','settings.subscriptions.topUpMonthMeta','settings.subscriptions.topUpLastMeta',
    ...['price','nextCharge','validUntil','expired','daysLeft','today','autoRenew','autoRenewOff','subscribed','months','daysCount','notStarted','paidTotal','providerTotal','providerTotalValue','monthUsage','monthUsageNote','allAccounts','valueMultiple','lastTopUp','topUpMonth','topUpTotal','topUpCount','balance','burnRate','perDay','exhausts'].map(key=>'subscription.tooltip.'+key)];
   const wording=Object.fromEntries(['en','zh-CN'].map(locale=>[locale,Object.fromEntries(labelKeys.map(key=>[key,TokenMonitorI18n.translate(locale,key,Object.fromEntries(['count','unit','date','total','days','months','provider','amount'].map(name=>[name,'{'+name+'}'])))]))]));
   return {cases:results,cards,wording};
  },{pure,identityFixtures,appFunctions,now});
  const data={source:'v0.68.0/5d2db368d8313415763860d594de00e46a663418; original subscription row DOM, account matching and CSS',today:'2026-10-10',...captured};
  fs.mkdirSync(path.join(root,'app/src/sharedTest/resources'),{recursive:true});
  const file=path.join(root,'app/src/sharedTest/resources/subscription-layout-parity.json');fs.writeFileSync(file,JSON.stringify(data,null,2)+'\n');
  const literal=value=>JSON.stringify(value).replaceAll('$','\\$');
  const maps=Object.entries(captured.wording).map(([lang,labels])=>literal(lang)+' to mapOf(\n'+Object.entries(labels).map(([key,value])=>'        '+literal(key)+' to '+literal(value)).join(',\n')+'\n    )').join(',\n    ');
  fs.writeFileSync(path.join(root,'app/src/main/java/dev/bing/tokenmonitor/DesktopSubscriptionWording.kt'),'// Fixed desktop v0.68.0 subscription wording; upstream MIT notice is retained.\npackage dev.bing.tokenmonitor\n\ninternal val desktopSubscriptionWording=mapOf(\n    '+maps+'\n)\n');
  await page.locator('#subscription-reference').screenshot({path:path.join(evidence,'desktop-subscription-reference-dark.png')});
  for(const name of ['group','account','factory','ledger']) await page.locator('#subscription-card-reference-'+name).screenshot({path:path.join(evidence,'desktop-subscription-'+name+'-reference-dark.png')});
  fs.writeFileSync(path.join(evidence,'source-reference-summary.txt'),`Actual original subscription DOM: ${captured.cases.length} input/account/options/locale lists; ${captured.cards.length} original plan cards\nPublic fixtures; remote requests blocked; independent browser profile\n`);
  process.stdout.write(`Captured ${captured.cases.length} actual desktop subscription list cases and ${captured.cards.length} plan tooltip cases\n`);
 } finally {try {await browser?.close();} finally {await new Promise(resolve=>server.close(resolve));} if(fs.existsSync(path.join(evidence,'source-fixture.html'))) fs.unlinkSync(path.join(evidence,'source-fixture.html'));}
})().catch(error=>{process.stderr.write(error.stack+'\n');process.exitCode=1});
