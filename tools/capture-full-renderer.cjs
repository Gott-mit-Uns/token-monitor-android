// Runs the complete immutable desktop index, app and preload, with public data only.
// No Electron main process, host configuration, Hub, credentials or external requests.
'use strict';
const fs = require('node:fs'), path = require('node:path'), http = require('node:http'), crypto = require('node:crypto');
const { chromium } = require('/Users/bing/.cache/codex-runtimes/codex-primary-runtime/dependencies/node/node_modules/playwright');
const root = path.resolve(__dirname, '..');
const ref = path.resolve(root, '../work/token-monitor-reference/desktop-v0.68.0');
const output = path.resolve(root,process.argv[2] || 'evidence/full-renderer/source');
if(!output.startsWith(path.join(root,'evidence')+path.sep))throw Error('Capture output must stay inside project evidence');
const commit='5d2db368d8313415763860d594de00e46a663418';
const now = Date.parse('2026-10-09T08:00:00Z');
const backgroundPng=process.argv.includes('--background') ? [...fs.readFileSync(path.join(root,'evidence/background-image/source/public-pattern.png'))] : [];
const period = {
  totalTokens:165164772,costUsd:136.08,capabilities:{tokenComponents:true},
  cacheReadTokens:140000000,cacheWriteTokens:1000000,outputTokens:4000000,unclassifiedTokens:0,
  clients:{codex:110000000,claude:55164772},clientCosts:{codex:88.12,claude:47.96},
  clientCacheReads:{codex:100000000,claude:40000000},clientCacheWrites:{codex:0,claude:1000000},
  clientOutputs:{codex:2000000,claude:2000000},clientUnclassifiedTokens:{codex:0,claude:0},
  models:{'gpt-5.5':110000000,'claude-sonnet-4.6':55164772},modelCosts:{'gpt-5.5':88.12,'claude-sonnet-4.6':47.96},
  clientModels:{codex:{'gpt-5.5':110000000},claude:{'claude-sonnet-4.6':55164772}},
  clientModelCosts:{codex:{'gpt-5.5':88.12},claude:{'claude-sonnet-4.6':47.96}},projects:{},sessions:{}
};
const history = {daily:Array.from({length:84},(_,n)=>({
  date:new Date(now-(83-n)*86400000).toISOString().slice(0,10),tokens:(n%11+1)*1430000,cost:1,
  perClient:{codex:{tokens:(n%11+1)*1000000,cost:.7},claude:{tokens:(n%11+1)*430000,cost:.3}},
  perModel:{'gpt-5.5':{tokens:(n%11+1)*1000000,cost:.7},'claude-sonnet-4.6':{tokens:(n%11+1)*430000,cost:.3}}
})),monthly:[],summary:{totalTokens:165164772,totalCost:136.08,activeDays:106,currentStreak:14,longestStreak:40,activeTimeMs:198000000,peakDayTokens:22000000,favoriteModel:'gpt-5.5',messages:1600}};
const stats = {updatedAt:new Date(now).toISOString(),periods:{today:period,month:period,allTime:period},historyPreview:history,
  devices:[{deviceId:'studio',deviceName:'Studio Mac',platform:'macOS',updatedAt:new Date(now).toISOString(),periods:{today:{totalTokens:145164772,costUsd:116.08,clients:{codex:110000000}}}},
    {deviceId:'server',deviceName:'Home Server',platform:'Linux',updatedAt:new Date(now).toISOString(),periods:{today:{totalTokens:20000000,costUsd:20,clients:{claude:20000000}}}}],
  limits:{providers:[{provider:'claude',status:'ok',accountLabel:'Demo account',accountKey:'public-claude',updatedAt:new Date(now).toISOString(),windows:[
    {kind:'session',remainingPercent:99,resetsAt:'2026-10-10T10:00:00Z'},
    {kind:'weekly',remainingPercent:35,resetsAt:'2026-10-12T10:00:00Z'}]},
  {provider:'codex',status:'ok',accountLabel:'Demo account',accountKey:'public-codex',updatedAt:new Date(now).toISOString(),windows:[{kind:'weekly',remainingPercent:88,resetsAt:'2026-10-15T10:00:00Z'}]}]}
};
// Explicit presentation fixture. Fields outside the viewer scope are inert.
const settings = {hubMode:'client',hubUrl:'',hubSyncSessionTitles:false,refreshMs:15000,
  glassOpacity:100,glassBlur:0,backgroundImageOpacity:28,systemGlass:false,reduceMotion:'on',
  showLiveDot:true,showToolIcons:true,titleIconOnly:true,showCompactTotalTokens:false,showLiveTokenRate:false,
  liveTokenRateScope:'all',compactTokenUnits:'western',tokenRateMode:'speed',heatmapMetric:'cost',modelRankingMetric:'tokens',
  homeActiveDaysWindow:'all',sessionContextMetric:'used',sessionTitlesEnabled:true,periodMonthMode:'month',
  themeColors:{},vendorColors:{},textSize:'standard',interfaceFontFamily:'',displayFontFamily:require(path.join(ref,'src/shared/fontSettings.js')).DEFAULT_DISPLAY_FONT,
  lastViewState:{period:'today',breakdown:'home'},clients:'codex,claude',clientDisplayOrder:'',hiddenClients:'',pinnedClients:'',
  viewDisplayOrder:'',hiddenViews:'status',homeModuleOrder:'',hiddenHomeModules:'',showHomeLimitBars:false,
  homeLimitDisplayMode:'text',showHomeLimitProviderNames:false,projectsEnabled:true,historyEnabled:true,
  customScanPaths:{},archivedClientUsage:{version:1,clients:{}},allTimeSince:'2024-01-01',customModelPricing:[],
  modelAliases:{},modelAliasGrouping:'off',limitsEnabled:true,limitProviders:'claude,codex',limitProviderOrder:'',
  homeLimitProviderOrder:'',hiddenHomeLimitProviders:'',limitProviderHiddenItems:{},homeLimitAccountCount:3,
  limitsRefreshMode:'auto',limitsRefreshMs:60000,maskLimitAccountEmails:false,showLimitUsed:false,
  subscriptions:[],subscriptionsOrphaned:{hubUrl:'',records:[]},subscriptionsCacheHub:'',currency:'USD',currencyRates:{},
  language:'zh-CN',zoomFactor:1,collectionMode:'live',collectionIntervalMs:300000,syncUploadIntervalMs:15000,
  syncContentState:{},automaticAppUpdates:false,serviceProviderDisplayOrder:'',hiddenServiceProviders:''};
const sharedStats={updatedAt:new Date(now).toISOString(),periods:{today:{totalTokens:1000,costUsd:1.25,
  clients:{codex:1000},clientCosts:{codex:1.25},models:{'vendor/gpt-5.5':800,'gpt-5.5':200},
  modelCosts:{'vendor/gpt-5.5':1,'gpt-5.5':.25},
  projects:{demo:{label:'Token Monitor Android',clients:{codex:1000},tokens:1000,costUsd:1.25}},
  sessions:{demo:{sessionId:'demo-session',title:'Align Android with desktop',client:'codex',totalTokens:1000,costUsd:1.25,lastUsedAt:new Date(now).toISOString(),turnEnded:true}}}},devices:[],limits:{providers:[]}};
const sharedSettings={modelAliases:{'vendor/gpt-5.5':'GPT 5.5 Standard','gpt-5.5':'GPT 5.5 Standard'},modelAliasGrouping:'off'};

async function main() {
  fs.mkdirSync(output,{recursive:true});
  const git=require('node:child_process').execFileSync;
  if(git('git',['rev-parse','HEAD'],{cwd:ref,encoding:'utf8'}).trim()!==commit)throw Error('Reference commit differs');
  git('git',['diff','--quiet',commit,'--','src','assets'],{cwd:ref});
  // main.js normally projects aliases before delivering stats to the renderer.
  // Use its actual pure presentation module, never execute the host main process.
  const aliasProjection=require(path.join(ref,'src/electron/modelAliasPresentation.js'));
  const sharedPresentation=aliasProjection.projectModelAliasStats(sharedStats,sharedSettings.modelAliases,{grouping:sharedSettings.modelAliasGrouping});
  const preload = fs.readFileSync(path.join(ref,'src/electron/preload.js'),'utf8');
  const errors=[],failedRequests=[],served=new Set(),captures=[];
  const server = http.createServer((req,res)=>{
    let relative;
    try { relative=decodeURIComponent(new URL(req.url,'http://127.0.0.1').pathname).replace(/^\/reference\//,''); }
    catch {res.writeHead(400).end();return;}
    const file=path.resolve(ref,relative);
    const allowed=/^src\/(electron|shared)\/.*\.(html|js|css|svg)$/.test(relative)||/^assets\/.*\.(svg|png|woff2?)$/.test(relative);
    if(req.method!=='GET'||!req.url.startsWith('/reference/')||!allowed||!file.startsWith(ref+path.sep)||!fs.existsSync(file)||!fs.statSync(file).isFile()) {
      res.writeHead(404).end();return;
    }
    served.add(relative);
    const mime={'.html':'text/html','.js':'text/javascript','.css':'text/css','.svg':'image/svg+xml','.png':'image/png','.woff':'font/woff','.woff2':'font/woff2'};
    res.writeHead(200,{'Content-Type':mime[path.extname(file)]+'; charset=utf-8','Cache-Control':'no-store'}).end(fs.readFileSync(file));
  });
  await new Promise(resolve=>server.listen(0,'127.0.0.1',resolve));
  const origin='http://127.0.0.1:'+server.address().port;
  let browser;
  try {
    browser=await chromium.launch({executablePath:'/Applications/Microsoft Edge.app/Contents/MacOS/Microsoft Edge',headless:true,
      args:['--disable-background-networking','--disable-component-update','--disable-sync','--no-first-run']});
    for(const language of ['zh-CN','en']) {
      const page=await browser.newPage({viewport:{width:400,height:780},deviceScaleFactor:2,timezoneId:'Asia/Shanghai',locale:language});
      page.on('pageerror',e=>errors.push({language,message:e.message}));
      await page.route('**/*',route=>{
        const request=route.request();
        if(new URL(request.url()).origin===origin&&request.method()==='GET')return route.continue();
        failedRequests.push({language,path:new URL(request.url()).pathname,reason:'external or mutation blocked'});
        return route.abort();
      });
      page.on('response',r=>{if(r.status()>=400)failedRequests.push({language,path:new URL(r.url()).pathname,status:r.status()});});
      await page.addInitScript(({preload,settings,stats,history,now,language,backgroundPng})=>{
        const NativeDate=Date;
        window.Date=class extends NativeDate {
          constructor(...args){super(...(args.length?args:[now]));}
          static now(){return now;}
        };
        window.__TOKEN_MONITOR_SUPPRESS_INITIAL_NUMBER_ANIMATION__=true;
        const config={...settings,language},listeners=new Map(),channels=[];
        let selectedImage=null;
        const clone=value=>structuredClone(value);
        const ipcRenderer={
          async invoke(channel,...args) {
            channels.push(channel);
            if(channel==='settings:get')return clone(config);
            if(channel==='settings:update'){Object.assign(config,args[0]);return clone(config);}
            if(channel==='stats:get')return clone(stats);
            if(channel==='dashboard:getHistory')return clone(history);
            if(channel==='app:getInfo')return {version:'0.68.0',platform:'darwin',systemDarkUi:true,loginItemSupported:false};
            if(channel==='appearance:getNativeMaterial')return null;
            if(channel==='appearance:getBackgroundImage')return selectedImage;
            if(channel==='appearance:chooseBackgroundImage' && backgroundPng.length){selectedImage=new Uint8Array(backgroundPng);return {bytes:selectedImage};}
            if(channel==='appearance:clearBackgroundImage'){selectedImage=null;return null;}
            if(channel==='stream:status')return {connected:true,mode:'sync'};
            if(channel==='syncContent:status')return {supported:true,identity:'public-fixture',destination:'Public Hub',enabled:{},revisions:{},serverTitlesEnabled:true};
            if(channel==='hub:getInfo')return {mode:'client',connected:true};
            if(channel==='hub:getBuildStatus')return {supported:false};
            if(channel==='appUpdate:getState')return {status:'idle',currentVersion:'0.68.0'};
            if(channel==='tokscale:getStatus')return {available:false};
            if(channel==='serviceStatus:get')return {providers:[]};
            if(channel.endsWith(':accounts')||channel.endsWith(':profiles'))return [];
            // Host actions and collector/account operations cannot run in this renderer.
            return {ok:false,supported:false,status:'notConfigured'};
          },
          on(channel,callback){if(!listeners.has(channel))listeners.set(channel,new Set());listeners.get(channel).add(callback);},
          removeListener(channel,callback){listeners.get(channel)?.delete(callback);},
          send(channel){channels.push(channel);}
        };
        window.publicRendererFixture={channels,emit(channel,value){for(const callback of listeners.get(channel)||[])callback({},clone(value));},
          replacePublicStats(next,patch){stats=clone(next);Object.assign(config,patch);this.emit('settings:push',config);this.emit('stats:push',{event:'stats',data:{stats,reason:'presentation'}});}};
        const require=name=>{if(name!=='electron')throw Error('Unsupported preload dependency '+name);return {ipcRenderer,contextBridge:{exposeInMainWorld(name,value){window[name]=value;}}};};
        // Exact original preload executed in an isolated lexical scope, without Node.
        new Function('require',preload)(require);
      },{preload,settings,stats,history,now,language,backgroundPng});
      await page.goto(origin+'/reference/src/electron/renderer/index.html',{waitUntil:'load'});
      await page.waitForFunction(()=>document.getElementById('totalTokens')?.textContent==='165,164,772',{},{timeout:15000});
      await page.waitForFunction(()=>document.querySelector('.home-module'),{},{timeout:10000});
      for(const view of ['home','limits','tool','model','project','session','device','trends']) {
        if(view!=='home')await page.evaluate(view=>window.publicRendererFixture.emit('view:open',view),view);
        await page.evaluate(()=>new Promise(resolve=>requestAnimationFrame(()=>requestAnimationFrame(resolve))));
        const record=await page.evaluate(()=>({title:document.title,total:document.getElementById('totalTokens')?.textContent,
          panelText:[...document.querySelectorAll('#homePanel,#breakdown,#limitsPanel,#trendsPanel')].filter(n=>!n.classList.contains('hidden')).map(n=>({id:n.id,text:n.innerText})),
          expanded:[...document.querySelectorAll('[aria-expanded=true]')].map(n=>n.id),
          modules:[...document.querySelectorAll('.home-module-label')].map(n=>n.textContent),
          moduleGeometry:[...document.querySelectorAll('.home-module')].map(n=>{
            const css=el=>{const s=getComputedStyle(el),r=el.getBoundingClientRect();return {width:r.width,height:r.height,gap:s.gap,paddingBottom:s.paddingBottom,borderBottom:s.borderBottom};};
            return {label:n.querySelector('.home-module-label')?.textContent,module:css(n),head:css(n.querySelector('.home-module-head')),body:css(n.querySelector('.home-module-body'))};
          }),
          rowStyles:[...document.querySelectorAll('#breakdown .row')].slice(0,2).map(n=>{
            const style=selector=>{const el=n.querySelector(selector);if(!el)return null;const s=getComputedStyle(el);return {fontSize:s.fontSize,fontWeight:s.fontWeight,width:s.width,height:s.height};};
            return {head:style('.row-head'),name:style('.row-name'),value:style('.row-value'),cost:style('.row-cost'),icon:style('.row-icon'),bar:style('.bar')};
          }),
          channels:[...new Set(window.publicRendererFixture.channels)]}));
        const name=`desktop-full-${view}-${language}.png`;
        await page.screenshot({path:path.join(output,name)});
        captures.push({language,view,image:name,...record});
        if(view==='home') {
          await page.locator('#viewSwitcher .view-switcher-disclosure').click();
          await page.locator('#viewSwitcher .view-switcher-menu').waitFor({state:'visible'});
          await page.evaluate(()=>new Promise(resolve=>requestAnimationFrame(()=>requestAnimationFrame(resolve))));
          const menuName=`desktop-full-menu-${language}.png`;
          await page.screenshot({path:path.join(output,menuName)});
          const regionName=`desktop-menu-region-${language}.png`;
          await page.locator('#viewSwitcher .view-switcher-menu').screenshot({path:path.join(output,regionName)});
          const menuRecord=await page.evaluate(()=>{
            const measure=n=>{const c=getComputedStyle(n),r=n.getBoundingClientRect();return {width:r.width,height:r.height,padding:c.padding,gap:c.gap,borderRadius:c.borderRadius,fontSize:c.fontSize,color:c.color,background:c.background};};
            const menu=document.querySelector('#viewSwitcher .view-switcher-menu');
            return {menu:measure(menu),items:[...menu.children].map(n=>({label:n.textContent.trim(),checked:n.getAttribute('aria-checked'),role:n.getAttribute('role'),...measure(n)}))};
          });
          captures.push({language,view:'menu',image:menuName,regionImage:regionName,...menuRecord});
          await page.keyboard.press('Escape');
          if(process.argv.includes('--menu-motion') && language==='en') {
            const motion=await page.evaluate(async()=>{
              document.documentElement.dataset.reduceMotion='off';
              const read=(el,animations)=>{const c=getComputedStyle(el),m=new DOMMatrix(c.transform);return {opacity:Number(c.opacity),scale:m.a,translateY:m.f,blur:Number(c.filter.match(/[\d.]+/)?.[0]||0),duration:animations.find(a=>a.transitionProperty==='opacity')?.effect.getTiming().duration||0};};
              const sample=async(selector)=>{
                const el=document.querySelector(selector),steps=[];
                el.classList.add('hidden');getComputedStyle(el).opacity;el.getAnimations().forEach(a=>a.finish());
                const change=open=>{el.classList.toggle('hidden',!open);getComputedStyle(el).opacity;const animations=el.getAnimations();animations.forEach(a=>{a.pause();a.currentTime=0});steps.push({action:open?'open':'close',...read(el,animations)});return animations;};
                const frame=(animations,time)=>{animations.forEach(a=>a.currentTime=time);steps.push({action:'frame',time,...read(el,animations)});};
                let animations=change(true);for(const time of [0,32,64,95,130,190])frame(animations,time);
                animations=change(false);for(const time of [0,32,65,100,130])frame(animations,time);
                animations=change(true);frame(animations,32);animations=change(false);frame(animations,20);animations=change(true);frame(animations,20);frame(animations,190);
                el.classList.add('hidden');el.getAnimations().forEach(a=>a.finish());return steps;
              };
              const result={navigation:await sample('#viewSwitcher .view-switcher-menu'),period:await sample('#monthPeriodMenu')};
              document.documentElement.dataset.reduceMotion='on';return result;
            });
            fs.writeFileSync(path.join(output,'menu-motion-reference.json'),JSON.stringify(motion,null,2)+'\n');
          }
          const restoreSlot=await page.locator('.tabs .tab.active').getAttribute('data-period-slot');
          await page.locator('#monthPeriodTab').click();
          if(await page.locator('#monthPeriodMenu').evaluate(n=>n.classList.contains('hidden'))) await page.locator('#monthPeriodTab').click();
          await page.locator('#monthPeriodMenu').waitFor({state:'visible'});
          const periodName=`desktop-full-period-menu-${language}.png`,periodRegion=`desktop-period-menu-region-${language}.png`;
          await page.screenshot({path:path.join(output,periodName)});
          await page.locator('#monthPeriodMenu').screenshot({path:path.join(output,periodRegion)});
          captures.push({language,view:'period-menu',image:periodName,regionImage:periodRegion});
          await page.keyboard.press('Escape');
          await page.locator(`.tabs [data-period-slot="${restoreSlot}"]`).click();
        }
        if(view==='tool') {
          await page.locator('#breakdown .row').filter({hasText:'Claude Code'}).first().click();
          await page.evaluate(()=>new Promise(resolve=>requestAnimationFrame(()=>requestAnimationFrame(resolve))));
          const expandedName=`desktop-full-tool-expanded-${language}.png`;
          await page.screenshot({path:path.join(output,expandedName)});
          captures.push({language,view:'tool-expanded',image:expandedName,panelText:await page.locator('#breakdown').innerText(),
            computed:await page.evaluate(()=>{
              const css=selector=>{const n=document.querySelector(selector);const s=getComputedStyle(n),r=n.getBoundingClientRect();return {fontSize:s.fontSize,lineHeight:s.lineHeight,gap:s.gap,marginLeft:s.marginLeft,paddingLeft:s.paddingLeft,paddingTop:s.paddingTop,paddingBottom:s.paddingBottom,borderLeft:s.borderLeft,opacity:s.opacity,width:r.width,height:r.height,x:r.x};};
              return Object.fromEntries(['.row.expanded .row-accordion-inner','.row.expanded .accordion-content','.row.expanded .accordion-row','.row.expanded .accordion-label','.row.expanded .accordion-pct','.row.expanded .accordion-value','#toolDetailFooter','#toolDetailFooterTokens','#settingsButton','#refreshButton'].map(selector=>[selector,css(selector)]));
            })});
        }
      }
      // Match the second existing native public fixture for alias/project/session pages.
      await page.evaluate(({sharedPresentation,sharedSettings})=>window.publicRendererFixture.replacePublicStats(sharedPresentation,sharedSettings),{sharedPresentation,sharedSettings});
      for(const view of ['model','project','session']) {
        await page.evaluate(view=>window.publicRendererFixture.emit('view:open',view),view);
        const expected={model:'GPT 5.5 Standard',project:'Token Monitor Android',session:'Align Android with desktop'}[view];
        try {await page.waitForFunction(text=>document.getElementById('breakdown')?.innerText.includes(text),expected,{timeout:10000});}
        catch(error) {console.error('Public shared fixture failed for '+view+': '+await page.locator('#breakdown').innerText());throw error;}
        const name=`desktop-full-${view}-shared-${language}.png`;
        await page.screenshot({path:path.join(output,name)});
        captures.push({language,view:view+'-shared',image:name,panelText:await page.locator('#breakdown').innerText()});
      }
      await page.evaluate(()=>window.publicRendererFixture.emit('settings:open'));
      await page.evaluate(()=>new Promise(resolve=>requestAnimationFrame(()=>requestAnimationFrame(resolve))));
      await page.screenshot({path:path.join(output,`desktop-full-settings-${language}.png`)});
      captures.push({language,view:'settings',image:`desktop-full-settings-${language}.png`,...await page.evaluate(()=>({groups:[...document.querySelectorAll('.settings-section-toggle')].map(n=>n.textContent.trim()),expanded:[...document.querySelectorAll('[aria-expanded=true]')].map(n=>n.id)}))});
      // These are original native <select> controls. Capture the closed control and
      // option structure; renderer screenshots cannot represent their OS popup.
      for(const [section,id,kind] of [['appearance','interfaceFontPreset','font-control'],['general','currencyInput','currency-control']]) {
        const toggle=page.locator(`.settings-section-toggle[data-settings-section="${section}"]`);
        if(await toggle.getAttribute('aria-expanded')!=='true') await toggle.click();
        const select=page.locator(`#${id}`);await select.scrollIntoViewIfNeeded();
        const image=`desktop-full-${kind}-${language}.png`,regionImage=`desktop-${kind}-region-${language}.png`;
        await page.screenshot({path:path.join(output,image)});
        await select.screenshot({path:path.join(output,regionImage)});
        captures.push({language,view:kind,image,regionImage,nativeSelect:true,options:await select.evaluate(n=>[...n.options].map(o=>({value:o.value,label:o.label,disabled:o.disabled})))});
      }

      if(backgroundPng.length) {
        const toggle=page.locator('.settings-section-toggle[data-settings-section="appearance"]');
        if(await toggle.getAttribute('aria-expanded')!=='true')await toggle.click();
        await page.locator('#chooseBackgroundImageButton').scrollIntoViewIfNeeded();
        await page.locator('#chooseBackgroundImageButton').click();
        await page.waitForFunction(()=>document.querySelector('.shell.has-custom-background'));
        for(const [value,kind] of [[28,'background-normal'],[100,'background-full'],[0,'background-zero']]) {
          await page.evaluate(value=>{const input=document.getElementById('backgroundImageOpacityInput');input.value=value;input.dispatchEvent(new Event('input',{bubbles:true}));},value);
          await page.waitForFunction(value=>Math.abs(Number(getComputedStyle(document.querySelector('.shell'),'::before').opacity)-value/100)<.001,value);
          const image=`desktop-full-${kind}-${language}.png`;
          await page.screenshot({path:path.join(output,image)});
          const style=await page.evaluate(()=>{const s=getComputedStyle(document.querySelector('.shell'),'::before');return {opacity:s.opacity,backgroundSize:s.backgroundSize,backgroundPosition:s.backgroundPosition,pointerEvents:s.pointerEvents,backgroundImage:s.backgroundImage.replace(/blob:[^)]+/g,'public-blob')};});
          captures.push({language,view:kind,image,style});
        }
        await page.locator('#clearBackgroundImageButton').click();
        await page.waitForFunction(()=>!document.querySelector('.shell.has-custom-background'));
        const image=`desktop-full-background-removed-${language}.png`;
        await page.screenshot({path:path.join(output,image)});
        captures.push({language,view:'background-removed',image});
      }
      await page.close();
    }
    const report={sourceCommit:commit,
      methodology:'Complete unmodified index.html, app.js, stylesheet, scripts and preload.js. In-memory public IPC fixture; loopback GET-only file server; external and mutating requests blocked.',
      adaptations:{viewport:'400x780 CSS px',deviceScaleFactor:2,platform:'darwin',nativeBackdrop:false,glassOpacity:100,reduceMotion:'on',fixedDateNow:new Date(now).toISOString()},
      sourceFiles:[...new Set([...served,'src/electron/preload.js',...Object.keys(require.cache).filter(file=>file.startsWith(ref+path.sep)).map(file=>path.relative(ref,file))])].sort().map(file=>({file,sha256:crypto.createHash('sha256').update(fs.readFileSync(path.join(ref,file))).digest('hex')})),
      fixtures:{settings,stats,history,sharedStats,sharedSettings,sharedPresentation},errors,failedRequests,captures};
    fs.writeFileSync(path.join(output,'full-renderer-reference.json'),JSON.stringify(report,null,2)+'\n');
    if(errors.length||failedRequests.length)throw Error(`Original renderer diagnostics: ${errors.length} errors, ${failedRequests.length} failed/blocked requests; inspect public report`);
    console.log(`Captured ${captures.length} complete original pages in two languages; ${served.size} original files; no browser errors or failed requests.`);
  } finally {if(browser)await browser.close();await new Promise(resolve=>server.close(resolve));}
}
main().catch(error=>{console.error(error.message);process.exitCode=1;});
