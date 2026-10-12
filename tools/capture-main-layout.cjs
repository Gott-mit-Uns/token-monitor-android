// Actual original Home DOM builder, title markup, view-switcher builder and stylesheet.
// A separate temporary browser sees only immutable source and public synthetic records.
const fs=require('node:fs'),path=require('node:path'),http=require('node:http');
const {chromium}=require('/Users/bing/.cache/codex-runtimes/codex-primary-runtime/dependencies/node/node_modules/playwright');
const root=path.resolve(__dirname,'..'),ref=path.resolve(root,'../work/token-monitor-reference/desktop-v0.68.0');
const evidence=path.join(root,'evidence/main-display');fs.mkdirSync(evidence,{recursive:true});
const app=fs.readFileSync(path.join(ref,'src/electron/renderer/app.js'),'utf8'),index=fs.readFileSync(path.join(ref,'src/electron/renderer/index.html'),'utf8');
const functions=['homeModuleShell','applyHomeListMark','iconKindFor','homeSessionAgo','homeSessionContext','renderHomeSessionModule','viewSwitcherIcon','renderViewSwitcher','totalNumberFontScale','fitTotalNumber'].map(name=>{
 const at=app.indexOf('function '+name+'(');if(at<0)throw Error('Missing original '+name);return app.slice(at,app.indexOf('\n}',at)+2);
}).join('\n');
const header=index.slice(index.indexOf('<header class="titlebar">'),index.indexOf('</header>')+9);
const total=index.slice(index.indexOf('<section class="total-panel">'),index.indexOf('</section>',index.indexOf('<section class="total-panel">'))+10);
const now=Date.parse('2026-10-10T12:00:00Z'),stats={periods:{month:{sessions:{}}}};
for(let i=0;i<=8;i++)stats.periods.month.sessions['public:'+i]={sessionId:'public-session-'+i,title:'Public title '+i,projectLabel:'Public fallback '+i,client:'codex',
 lastUsedAt:new Date(now-i*5000).toISOString(),turnEnded:i<6,totalTokens:1000,models:{'public-alpha':500,'public-beta':400},...(i>=6?{contextTokens:75000,contextWindow:100000,promptCache:{observedAt:new Date(now-20000).toISOString(),ttlSeconds:1800}}:{})};
const originalNow=Date.now;Date.now=()=>now;
const projected=require(path.join(ref,'src/electron/renderer/edgeDock/presentation.js')).recentSessionRows(stats,5,{includeRunningBeyondCap:true});Date.now=originalNow;
const scripts=['src/shared/vendorPresentation.js','src/electron/renderer/rowIconMasks.js','src/shared/sessionLive.js','src/shared/compactTokens.js','src/electron/renderer/sessionRows.js','src/electron/renderer/i18n.js'];
(async()=>{
 const html=`<!doctype html><meta charset="utf-8"><base href="/reference/src/electron/renderer/"><link rel="stylesheet" href="styles.css"><style>body{margin:0;background:rgb(48,52,56);height:auto;overflow:auto;border-radius:0}.shell{width:360px;min-height:600px;height:auto;padding:20px;gap:18px}.footer{margin-top:18px}</style><main class="shell title-icon-only">${header}${total}<section id="homePanel" class="home-panel"></section><footer class="footer"><div id="viewSwitcher" class="view-switcher"></div></footer></main>`;
 const allowed=new Set([...scripts,'src/electron/renderer/styles.css']);
 const server=http.createServer((req,res)=>{
  const name=decodeURIComponent(new URL(req.url,'http://127.0.0.1').pathname);
  if(req.method!=='GET') {res.writeHead(405).end();return;}
  if(name==='/fixture.html'){res.writeHead(200,{'Content-Type':'text/html; charset=utf-8'}).end(html);return;}
  const relative=name.replace(/^\/reference\//,'');
  if(!name.startsWith('/reference/') || !(allowed.has(relative)||/^src\/electron\/renderer\/icons\/[a-z0-9/-]+\.svg$/.test(relative)||/^assets\/icons\/[a-z0-9-]+\.svg$/.test(relative))){res.writeHead(404).end();return;}
  const file=path.join(ref,relative);if(!fs.existsSync(file)){res.writeHead(404).end();return;}
  res.writeHead(200,{'Content-Type':relative.endsWith('.css')?'text/css':relative.endsWith('.svg')?'image/svg+xml':'text/javascript'}).end(fs.readFileSync(file));
 });
 await new Promise(resolve=>server.listen(0,'127.0.0.1',resolve));const origin='http://127.0.0.1:'+server.address().port;let browser;
 try {
  browser=await chromium.launch({executablePath:'/Applications/Microsoft Edge.app/Contents/MacOS/Microsoft Edge',headless:true,args:['--disable-background-networking','--disable-component-update','--disable-sync','--no-first-run']});
  const page=await browser.newPage({viewport:{width:400,height:760},deviceScaleFactor:2,timezoneId:'Asia/Shanghai'});
  await page.route('**/*',route=>new URL(route.request().url()).origin===origin && route.request().method()==='GET'?route.continue():route.abort());
  await page.goto(origin+'/fixture.html');for(const script of scripts)await page.addScriptTag({url:origin+'/reference/'+script});
  const report=await page.evaluate(({functions,now,stats,projected})=>{
   Date.now=()=>now;const state={stats,settings:{showToolIcons:true,sessionContextMetric:'used'},breakdown:'home',viewSwitcherOpen:false};
   const els={homePanel:document.getElementById('homePanel'),viewSwitcher:document.getElementById('viewSwitcher'),totalTokens:document.getElementById('totalTokens')};
   const sessionRowsApi=TokenMonitorSessionRows,clientColors=TokenMonitorVendorPresentation.vendorColors(),clientsWithIcon=new Set(TokenMonitorVendorPresentation.VENDOR_IDS),limitMarksWithIcon=new Set();
   const VIEW_ICON_CLASSES=Object.fromEntries(['home','limits','tool','model','project','session','device','trends'].map(id=>[id,'view-icon-'+id]));
   const visibleBreakdownOrder=()=>Object.keys(VIEW_ICON_CLASSES),nextBreakdown=id=>visibleBreakdownOrder()[(visibleBreakdownOrder().indexOf(id)+1)%visibleBreakdownOrder().length];
   const viewLabelById=id=>TokenMonitorI18n.translate('en','views.'+id),t=(key,params)=>TokenMonitorI18n.translate('en',key,params);
   const formatCompact=value=>TokenMonitorCompactTokens.formatCompactTokens(value,'western','en');
   const clearViewSwitcherLongPress=()=>{},clearViewSwitcherHoverClose=()=>{},renderBreakdownChange=()=>{},bindHoverMarquee=()=>{},sessionTooltipShouldHoldRender=()=>false,toolIconsEnabled=value=>value!==false;
   const limitWindowsView={setDetailTooltip:(node,entries)=>node.dataset.publicTooltip=JSON.stringify(entries)};
   const stableColor=()=>clientColors.default,fallbackModelColors=[];
   window.TokenMonitorEdgeDockPresentation={recentSessionRows:()=>projected};
   // Projection above came from the actual original function; the renderer below is unmodified.
   eval(functions+`;els.homePanel.append(renderHomeSessionModule());renderViewSwitcher();window.fixtureFitTotal=fitTotalNumber;`);
   document.getElementById('totalTokens').textContent='1,000';document.getElementById('cost').textContent='—';
   const css=selector=>{const el=document.querySelector(selector),s=getComputedStyle(el);return {font:s.fontSize,weight:s.fontWeight,height:s.height,gap:s.gap,color:s.color}};
   return {source:'Original renderHomeSessionModule/renderViewSwitcher/index.html/styles.css, fixed v0.68.0',rows:document.querySelectorAll('.home-session-row').length,
    title:css('.app-title'),period:css('.tab'),sessionName:css('.home-list-name'),sessionMeta:css('.home-list-sub'),header:css('.home-module-label'),viewIcon:css('.view-switcher-icon'),
    states:[...document.querySelectorAll('.home-session-state')].map(el=>el.dataset.state)};
  },{functions,now,stats,projected});
  if(report.rows!==8 || report.states.filter(s=>s==='running').length!==3)throw Error('Original Home fixture differs');
  await page.locator('.shell').screenshot({path:path.join(evidence,'original-main-home-en.png')});
  await page.evaluate(()=>{document.querySelector('.shell').classList.remove('title-icon-only');document.getElementById('totalTokens').textContent='5,000,000,123';const compact=document.getElementById('totalTokensCompact');compact.textContent='≈ 5B';compact.classList.remove('hidden');document.getElementById('cost').textContent='$1.0000';document.getElementById('homePanel').replaceChildren();window.fixtureFitTotal();});
  await page.locator('.shell').screenshot({path:path.join(evidence,'original-main-header-en.png')});
  fs.writeFileSync(path.join(evidence,'original-main-layout.json'),JSON.stringify(report,null,2)+'\n');console.log('Original main header, view switcher and 8-row Home DOM captured');
 } finally {if(browser)await browser.close();await new Promise(resolve=>server.close(resolve));}
})().catch(error=>{console.error(error);process.exitCode=1;});
