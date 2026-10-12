// Original DOM row functions and stylesheet. Public fixture; cost text and hover listeners are adapters.
const fs=require('node:fs'),path=require('node:path'),http=require('node:http');
const {chromium}=require('/Users/bing/.cache/codex-runtimes/codex-primary-runtime/dependencies/node/node_modules/playwright');
const root=path.resolve(__dirname,'..'),ref=path.resolve(root,'../work/token-monitor-reference/desktop-v0.68.0'),output=path.join(root,'evidence/session-list');fs.mkdirSync(output,{recursive:true});
const source=fs.readFileSync(path.join(ref,'src/electron/renderer/app.js'),'utf8');
const names=['rowTemplate','updateRow','updateRowContext','updateRowLive','iconKindFor','backgroundReviewRunNode'];
const functions=names.map(name=>{const at=source.indexOf('function '+name+'(');if(at<0)throw Error(name);return source.slice(at,source.indexOf('\n}',at)+2)}).join('\n');
const cssVars=require(path.join(ref,'src/electron/renderer/themePresets.js')).themeCssVarEntries({});
const clock=Date.parse('2026-10-10T02:00:00Z'),sessions={};
for(let i=0;i<8;i++) sessions['public:'+i]={sessionId:'public-session-'+i,title:'Public session '+i,client:'codex',totalTokens:1000,costUsd:.125,lastUsedAt:new Date(clock-i*3000).toISOString(),turnEnded:i!==0,messageCount:12,inputTokens:100,cacheReadTokens:300,cacheWriteTokens:100,outputTokens:500,timedOutputTokens:450,timedDurationMs:9000,models:{'public-alpha':500,'public-beta':400},...(i===0?{contextTokens:75000,contextWindow:100000,promptCache:{observedAt:new Date(clock-20000).toISOString(),ttlSeconds:1800}}:{})};
for(let i=8;i<11;i++)sessions['review:'+i]={...sessions['public:1'],sessionId:'public-session-'+i,title:'Public session '+i,lastUsedAt:new Date(clock-i*3000).toISOString(),sessionKind:'background-review'};
const scripts=['src/shared/vendorPresentation.js','src/electron/renderer/rowIconMasks.js','src/shared/sessionLive.js','src/electron/renderer/sessionRows.js','src/electron/renderer/breakdownRenderPolicy.js','src/electron/renderer/i18n.js'];
(async()=>{
 const html='<!doctype html><meta charset="utf-8"><base href="/reference/src/electron/renderer/"><link rel="stylesheet" href="styles.css"><style>body{margin:0;height:auto;overflow:auto;border-radius:0}.shell{width:360px;min-height:0;height:auto;padding:20px;gap:0}.breakdown{overflow:visible}</style><main class="shell session-mode"><section class="breakdown"></section></main>';
 const allowed=new Set([...scripts,'src/electron/renderer/styles.css']);
 const server=http.createServer((req,res)=>{const name=decodeURIComponent(new URL(req.url,'http://127.0.0.1').pathname);if(req.method!=='GET'){res.writeHead(405).end();return}if(name==='/fixture.html'){res.writeHead(200,{'Content-Type':'text/html'}).end(html);return}const relative=name.replace(/^\/reference\//,'');if(!name.startsWith('/reference/') || !(allowed.has(relative)||/^src\/electron\/renderer\/icons\/[a-z0-9/-]+\.svg$/.test(relative)||/^assets\/icons\/[a-z0-9-]+\.svg$/.test(relative))){res.writeHead(404).end();return}const file=path.join(ref,relative);if(!fs.existsSync(file)){res.writeHead(404).end();return}res.writeHead(200,{'Content-Type':relative.endsWith('.css')?'text/css':relative.endsWith('.svg')?'image/svg+xml':'text/javascript'}).end(fs.readFileSync(file))});
 await new Promise(resolve=>server.listen(0,'127.0.0.1',resolve));const origin='http://127.0.0.1:'+server.address().port;let browser;
 try {
  browser=await chromium.launch({executablePath:'/Applications/Microsoft Edge.app/Contents/MacOS/Microsoft Edge',headless:true,args:['--disable-background-networking','--disable-component-update','--disable-sync','--no-first-run']});
  const page=await browser.newPage({viewport:{width:400,height:850},deviceScaleFactor:2,timezoneId:'Asia/Shanghai'});
  await page.route('**/*',route=>new URL(route.request().url()).origin===origin&&route.request().method()==='GET'?route.continue():route.abort());
  await page.goto(origin+'/fixture.html');for(const script of scripts)await page.addScriptTag({url:origin+'/reference/'+script});
  const report=await page.evaluate(({functions,clock,sessions,cssVars})=>{
   cssVars.forEach(entry=>document.documentElement.style.setProperty(entry.name,entry.value));Date.now=()=>clock;const state={breakdown:'session',settings:{showToolIcons:true,sessionContextMetric:'used'}};
   const sessionRowsApi=TokenMonitorSessionRows,clientColors=TokenMonitorVendorPresentation.vendorColors(),clientsWithIcon=new Set(TokenMonitorVendorPresentation.VENDOR_IDS),limitMarksWithIcon=new Set();
   const rowLiveMarkup='<span class="row-live-dot"></span>',{rowWidth}=TokenMonitorBreakdownRenderPolicy;
   const formatNumber=n=>Math.round(Number(n)||0).toLocaleString('en-US'),formatCost=n=>'$'+Number(n).toFixed(4),t=(key,params)=>TokenMonitorI18n.translate('en',key,params);
   const bindHoverMarquee=()=>{},setHoverMarqueeText=(el,text)=>el.textContent=text||'',overflowText={update:()=>{}},toolIconsEnabled=value=>value!==false;
   const limitWindowsView={setDetailTooltip:(node,entries)=>node.dataset.publicTooltip=JSON.stringify(entries)};
   const applyBarScale=(node,scale)=>node.style.setProperty('--bar-scale',String(scale)),cancelRowNumberAnimation=()=>{},openSessionDetail=()=>{};
   const raw=sessionRowsApi.sessionRowsForPeriod({sessions},{clientLabels:{codex:'Codex'},clientColors,now:new Date(clock)});const rows=sessionRowsApi.groupBackgroundReviewRows(raw,{label:'Codex Auto Review',countLabel:n=>n+' background runs',summaryLabel:({latestTime,latestValue})=>'Latest '+latestTime+' · 1K',now:new Date(clock)});
   eval(functions+`;const host=document.querySelector('.breakdown');for(const value of rows){const node=rowTemplate(value);updateRow(node,{...value,max:3000,barValue:value.value});host.append(node)};window.fixtureReview=()=>{host.replaceChildren();for(const row of raw.filter(r=>r.backgroundReview))host.append(backgroundReviewRunNode(row,1000,{}));};`);
   const css=selector=>{const el=document.querySelector(selector),s=getComputedStyle(el);return {font:s.fontSize,lineHeight:s.lineHeight,color:s.color,width:s.width,height:s.height,gap:s.gap}};
   return {source:'Immutable original rowTemplate/updateRow/updateRowContext/updateRowLive/backgroundReviewRunNode/styles.css',adapters:'Fixed public clock, tooltip/event plumbing, cost text placeholder; no private desktop or Hub values',rows:rows.length,labels:[...document.querySelectorAll('.row-title')].map(n=>n.textContent),title:css('.row-title'),subtitle:css('.row-subtitle'),value:css('.row-value'),context:css('.row-context'),bar:css('.bar'),live:document.querySelectorAll('.row-live.is-active').length};
  },{functions,clock,sessions,cssVars});
  if(report.rows!==9||report.live!==1)throw Error('Source row projection differs');
  await page.locator('.shell').screenshot({path:path.join(output,'original-session-list-en.png')});await page.evaluate(()=>window.fixtureReview());
  await page.locator('.shell').screenshot({path:path.join(output,'original-session-reviews-en.png')});
  fs.writeFileSync(path.join(output,'original-session-layout.json'),JSON.stringify(report,null,2)+'\n');console.log('Original 9-row session/group DOM and 3-run review DOM captured');
 } finally {if(browser)await browser.close();await new Promise(resolve=>server.close(resolve))}
})().catch(error=>{console.error(error);process.exitCode=1});
