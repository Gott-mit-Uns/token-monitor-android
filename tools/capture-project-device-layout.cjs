// Original DOM row functions and stylesheet. Public fixture; cost text and hover listeners are adapters.
const fs=require('node:fs'),path=require('node:path'),http=require('node:http');
const {chromium}=require('/Users/bing/.cache/codex-runtimes/codex-primary-runtime/dependencies/node/node_modules/playwright');
const root=path.resolve(__dirname,'..'),ref=path.resolve(root,'../work/token-monitor-reference/desktop-v0.68.0'),output=path.join(root,'evidence/project-device');fs.mkdirSync(output,{recursive:true});
const source=fs.readFileSync(path.join(ref,'src/electron/renderer/app.js'),'utf8');
const names=['rowTemplate','updateRow','updateRowContext','updateRowLive','iconKindFor','osIconFor','renderDeviceAccordion','deviceRowsForPeriod','deviceLabel','deviceColor','deviceRuntimeLabel','deviceSyncedLabel','relativeAgeLabel','stableColor'];
const functions=names.map(name=>{const at=source.indexOf('function '+name+'(');if(at<0)throw Error(name);return source.slice(at,source.indexOf('\n}',at)+2)}).join('\n');
const cssVars=require(path.join(ref,'src/electron/renderer/themePresets.js')).themeCssVarEntries({});
const clock=Date.parse('2026-10-10T02:00:00Z');
const stats={periods:{today:{projects:{priority:{label:'Priority',tokens:1000,costUsd:20,clients:{codex:1000}},big:{label:'Public project',tokens:60000,costUsd:1,clients:{codex:36000,claude:18000}},merged:{label:' public PROJECT ',tokens:40000,costUsd:1,clients:{codex:24000,claude:12000}},last:{label:'Last',tokens:2000,costUsd:.1,clients:{codex:2000}}}}},devices:[
 {deviceId:'mac',platform:'darwin-arm64',osName:'macOS',osVersion:'27.0',updatedAt:new Date(clock).toISOString(),agentRuntime:'headless-agent',agentVersion:'0.68.0',periods:{today:{totalTokens:1000,costUsd:1,clients:{codex:600,claude:300},clientModels:{codex:{'public-alpha':500,'public-beta':100}}}}},
 {deviceId:'stale',stale:true,platform:'openbsd',periods:{today:{totalTokens:1000,clients:{codex:1000}}}},
 {deviceId:'zero',platform:'linux',periods:{today:{totalTokens:0}}}]};
const scripts=['src/shared/clientCatalog.js','src/shared/vendorPresentation.js','src/electron/renderer/rowIconMasks.js','src/shared/sessionLive.js','src/electron/renderer/sessionRows.js','src/electron/renderer/breakdownRenderPolicy.js','src/electron/renderer/i18n.js','src/shared/projectKey.js','src/electron/renderer/projectRows.js','src/electron/renderer/deviceBreakdown.js'];
(async()=>{
 const html='<!doctype html><meta charset="utf-8"><base href="/reference/src/electron/renderer/"><link rel="stylesheet" href="styles.css"><style>body{margin:0;height:auto;overflow:auto;border-radius:0}.shell{width:360px;min-height:0;height:auto;padding:20px;gap:0}.breakdown{overflow:visible}</style><main class="shell"><section class="breakdown"></section></main>';
 const allowed=new Set([...scripts,'src/electron/renderer/styles.css']);
 const server=http.createServer((req,res)=>{const name=decodeURIComponent(new URL(req.url,'http://127.0.0.1').pathname);if(req.method!=='GET'){res.writeHead(405).end();return}if(name==='/fixture.html'){res.writeHead(200,{'Content-Type':'text/html'}).end(html);return}const relative=name.replace(/^\/reference\//,'');if(!name.startsWith('/reference/') || !(allowed.has(relative)||/^src\/electron\/renderer\/icons\/[a-z0-9/-]+\.svg$/.test(relative)||/^assets\/icons\/[a-z0-9-]+\.svg$/.test(relative))){res.writeHead(404).end();return}const file=path.join(ref,relative);if(!fs.existsSync(file)){res.writeHead(404).end();return}res.writeHead(200,{'Content-Type':relative.endsWith('.css')?'text/css':relative.endsWith('.svg')?'image/svg+xml':'text/javascript'}).end(fs.readFileSync(file))});
 await new Promise(resolve=>server.listen(0,'127.0.0.1',resolve));const origin='http://127.0.0.1:'+server.address().port;let browser;
 try {
  browser=await chromium.launch({executablePath:'/Applications/Microsoft Edge.app/Contents/MacOS/Microsoft Edge',headless:true,args:['--disable-background-networking','--disable-component-update','--disable-sync','--no-first-run']});
  const page=await browser.newPage({viewport:{width:400,height:850},deviceScaleFactor:2,timezoneId:'Asia/Shanghai'});
  await page.route('**/*',route=>new URL(route.request().url()).origin===origin&&route.request().method()==='GET'?route.continue():route.abort());
  await page.goto(origin+'/fixture.html');for(const script of scripts)await page.addScriptTag({url:origin+'/reference/'+script});
  const report=await page.evaluate(({functions,clock,stats,cssVars})=>{
   cssVars.forEach(entry=>document.documentElement.style.setProperty(entry.name,entry.value));Date.now=()=>clock;const state={stats,period:'today',breakdown:'project',settings:{showToolIcons:true,sessionContextMetric:'used'}};
   const deviceBreakdownApi=TokenMonitorDeviceBreakdown,fixedPeriodDevices=()=>stats.devices,deviceAccent='#73bdf5',deviceStaleColor='#8c97a7';const sessionRowsApi=TokenMonitorSessionRows,clientColors=TokenMonitorVendorPresentation.vendorColors(),clientsWithIcon=new Set(TokenMonitorVendorPresentation.VENDOR_IDS),limitMarksWithIcon=new Set();
   const rowLiveMarkup='<span class="row-live-dot"></span>',{rowWidth}=TokenMonitorBreakdownRenderPolicy;
   const clientLabels=TokenMonitorClientCatalog.CLIENT_LABELS;const formatCompact=n=>new Intl.NumberFormat('en',{notation:'compact',maximumFractionDigits:1}).format(n);const formatNumber=n=>Math.round(Number(n)||0).toLocaleString('en-US'),formatCost=n=>'$'+Number(n).toFixed(4),t=(key,params)=>TokenMonitorI18n.translate('en',key,params);
   const bindHoverMarquee=()=>{},setHoverMarqueeText=(el,text)=>el.textContent=text||'',overflowText={update:()=>{}},toolIconsEnabled=value=>value!==false;
   const limitWindowsView={setDetailTooltip:(node,entries)=>node.dataset.publicTooltip=JSON.stringify(entries)};
   const applyBarScale=(node,scale)=>node.style.setProperty('--bar-scale',String(scale)),cancelRowNumberAnimation=()=>{},openSessionDetail=()=>{};
   const fallbackColors=['#6ab4f0','#5fbf8a','#a57df0','#d97bc4','#f0d66a','#f06a7b'];let rows;
   eval(functions+`;rows=TokenMonitorProjectRows.projectRowsForPeriod(stats.periods.today,{clientLabels,clientColors,stableColor,fallbackColors,unknownClientLabel:'Unknown tool'});const host=document.querySelector('.breakdown');for(const value of rows){const node=rowTemplate(value);updateRow(node,{...value,max:100000,barValue:value.value});node.classList.add('expanded');host.append(node)};window.fixtureDevices=()=>{host.replaceChildren();state.breakdown='device';for(const value of deviceRowsForPeriod()){const node=rowTemplate(value);updateRow(node,{...value,max:1000,barValue:value.value});node.classList.add('expanded');host.append(node)};};`);
   const css=selector=>{const el=document.querySelector(selector),s=getComputedStyle(el);return {font:s.fontSize,lineHeight:s.lineHeight,color:s.color,width:s.width,height:s.height,gap:s.gap}};
   return {source:'Immutable original rowTemplate/updateRow/renderDeviceAccordion/deviceRowsForPeriod/projectRowsForPeriod/styles.css',adapters:'Public fixed clock, event/hover wiring, cost formatter; raw today device collection only',projects:rows.length,title:css('.row-title'),value:css('.row-value'),bar:css('.bar'),tool:css('.project-tool-row'),percentage:css('.accordion-pct')};
  },{functions,clock,stats,cssVars});
  if(report.projects!==3)throw Error('Source project projection differs');
  await page.locator('.shell').screenshot({path:path.join(output,'original-project-list-en.png')});await page.evaluate(()=>window.fixtureDevices());
  await page.locator('.shell').screenshot({path:path.join(output,'original-device-list-en.png')});
  fs.writeFileSync(path.join(output,'original-project-device-layout.json'),JSON.stringify(report,null,2)+'\n');console.log('Original project/device DOM and expanded attributions captured');
 } finally {if(browser)await browser.close();await new Promise(resolve=>server.close(resolve))}
})().catch(error=>{console.error(error);process.exitCode=1});
