// Original renderer functions/CSS with public settings; no desktop application state.
const fs=require('node:fs'),path=require('node:path'),http=require('node:http');
const {chromium}=require('/Users/bing/.cache/codex-runtimes/codex-primary-runtime/dependencies/node/node_modules/playwright');
const root=path.resolve(__dirname,'..'),ref=path.resolve(root,'../work/token-monitor-reference/desktop-v0.68.0'),out=path.join(root,'evidence/list-controls/source');fs.mkdirSync(out,{recursive:true});
const app=fs.readFileSync(path.join(ref,'src/electron/renderer/app.js'),'utf8');
const names=['renderToolPreferencesNow','renderHomeSettingsList','renderHomeActivitySettings','visibilityIcon','pinIcon','createPreferenceOrderHandle','toolPreferenceRows','toolPreferenceQuery','renderSettingsListEmptyState'];
const functions=names.map(name=>{const start=app.indexOf('function '+name+'(');if(start<0)throw Error(name);return app.slice(start,app.indexOf('\n}',start)+2)}).join('\n');
const at=app.indexOf('const HOME_MODULE_OPTIONS = [');const options=app.slice(at,app.indexOf('\n];',at)+3);
const scripts=['src/electron/renderer/i18n.js','src/electron/renderer/homeModulePreferences.js','src/electron/renderer/clientDisplayPreferences.js','src/electron/renderer/settingsListFilter.js'];
const allowed=new Set([...scripts,'src/electron/renderer/styles.css']);
const html=fs.readFileSync(path.join(ref,'src/electron/renderer/index.html'),'utf8').replace(/<script\b[^>]*>[\s\S]*?<\/script>/gi,'').replace('<head>','<head><base href="/reference/src/electron/renderer/">');
const server=http.createServer((req,res)=>{
 const name=decodeURIComponent(new URL(req.url,'http://127.0.0.1').pathname);
 if(req.method!=='GET'){res.writeHead(405).end();return}
 if(name==='/fixture.html'){res.writeHead(200,{'Content-Type':'text/html'}).end(html);return}
 const relative=name.replace(/^\/reference\//,'');
 if(!name.startsWith('/reference/')||!(allowed.has(relative)||/^src\/electron\/renderer\/icons\/[a-z0-9/-]+\.svg$/.test(relative))){res.writeHead(404).end();return}
 const file=path.join(ref,relative);if(!fs.existsSync(file)){res.writeHead(404).end();return}
 res.writeHead(200,{'Content-Type':relative.endsWith('.css')?'text/css':relative.endsWith('.svg')?'image/svg+xml':'text/javascript'}).end(fs.readFileSync(file));
});
(async()=>{
 await new Promise(resolve=>server.listen(0,'127.0.0.1',resolve));let browser;
 try {
  const origin='http://127.0.0.1:'+server.address().port;
  browser=await chromium.launch({executablePath:'/Applications/Microsoft Edge.app/Contents/MacOS/Microsoft Edge',headless:true,args:['--disable-background-networking','--disable-component-update','--disable-sync','--no-first-run']});
  const reports=[];
  for(const locale of ['en','zh-CN']) {
   const page=await browser.newPage({viewport:{width:400,height:760},deviceScaleFactor:2});
   await page.route('**/*',route=>new URL(route.request().url()).origin===origin&&route.request().method()==='GET'?route.continue():route.abort());
   await page.goto(origin+'/fixture.html');for(const script of scripts)await page.addScriptTag({url:origin+'/reference/'+script});
   await page.addStyleTag({content:'body{margin:0;height:auto;overflow:auto;border-radius:0}.shell{width:360px;min-height:0;height:auto;padding:20px;gap:0}.settings-panel{max-height:none;overflow:visible}*{transition:none!important;animation:none!important}'});
   const report=await page.evaluate(({functions,options,locale})=>{
    const group=document.querySelector('.settings-tools-subgroup');const shell=document.createElement('main');shell.className='shell';const panel=document.createElement('section');panel.className='settings-panel';panel.append(group);shell.append(panel);document.body.replaceChildren(shell);
    const state={settings:{hiddenClients:'claude',pinnedClients:'hermes',heatmapMetric:'tokens',homeActiveDaysWindow:'year'},toolSearchQuery:'',homeActivitySettingsExpanded:true};
    const els={clientDisplayList:document.getElementById('clientDisplayList'),resetClientDisplayOrderButton:document.getElementById('resetClientDisplayOrderButton'),showAllClientsButton:document.getElementById('showAllClientsButton')};
    const KNOWN_CLIENTS=[{id:'claude',label:'Claude Code'},{id:'codex',label:'Codex'},{id:'opencode',label:'OpenCode'},{id:'hermes',label:'Hermes Agent'}];
    const clientDisplayPreferencesApi=TokenMonitorClientDisplayPreferences,homeModulePreferencesApi=TokenMonitorHomeModulePreferences,settingsListFilterApi=TokenMonitorSettingsListFilter;
    const t=(key,params)=>TokenMonitorI18n.translate(locale,key,params),localClientHealth=()=>null,localDevice=()=>null,clientSourcesIdentity=()=>'',clientSourceCacheApi={clientSourceRequestKey:()=>''};
    const toolPreferenceRenderSignature=()=>locale,enabledClientSet=()=>new Set(),hiddenClientSet=()=>new Set(['claude']),pinnedClientSet=()=>new Set(['hermes']),localClientStatus=()=>({}),clientHealthDetailFor=()=>null;
    const hiddenHomeModuleSet=()=>new Set(),renderHomeLimitProviderList=()=>document.createDocumentFragment();
    const onToolTrackingToggle=()=>{},onClientVisibilityToggle=()=>{},onClientPinnedToggle=()=>{},onPreferenceOrderKeydown=()=>{},resetHomeModuleOrder=()=>{},showAllHomeModules=()=>{},onHomeModuleVisibilityToggle=()=>{},togglePreferenceSubgroup=()=>{},saveSettings=()=>Promise.resolve(),renderHomeIfVisible=()=>{};
    const homeModulePreferenceRowDrag={startRowDrag:()=>{}},clientPreferenceRowDrag={startRowDrag:()=>{}},HOME_MODULE_SUBGROUPS={};
    eval(options+';'+functions+';renderToolPreferencesNow();window.publicHome=renderHomeSettingsList();');TokenMonitorI18n.applyTranslations(document,locale);
    const selectors=['.tool-preference-row','.tool-preference-name','.tool-visibility-button','.tool-pin-button','.settings-list-search-input','.tool-header-action'];
    return {locale,rows:[...els.clientDisplayList.children].map(n=>({id:n.dataset.client,hidden:n.classList.contains('is-hidden'),pinned:n.classList.contains('is-pinned')})),styles:selectors.map(selector=>{const s=getComputedStyle(document.querySelector(selector));return {selector,fontSize:s.fontSize,padding:s.padding,gap:s.gap,width:s.width,height:s.height,borderRadius:s.borderRadius}})};
   },{functions,options,locale});
   if(report.rows.length!==4||report.rows[0].id!=='hermes')throw Error('Original tool ordering mismatch');
   await page.locator('.settings-tools-subgroup').screenshot({path:path.join(out,'tools-'+locale+'.png')});
   const home=await page.evaluate(()=>{const panel=document.querySelector('.settings-panel');panel.replaceChildren(window.publicHome);return {rows:panel.querySelectorAll('.home-module-preference-row').length,labels:[...panel.querySelectorAll('.home-activity-settings > span')].map(n=>n.textContent),selected:[...panel.querySelectorAll('.home-activity-settings input:checked')].map(n=>n.value),styles:['.home-activity-settings','.inline-option','.inline-option:has(input:checked)','.view-subgroup-toggle','.view-subgroup-icon'].map(selector=>{const s=getComputedStyle(panel.querySelector(selector));return {selector,fontSize:s.fontSize,padding:s.padding,gap:s.gap,borderRadius:s.borderRadius,color:s.color,background:s.backgroundColor,width:s.width,height:s.height}})}});
   if(home.rows!==6||home.selected.join(',')!=='tokens,year')throw Error('Original activity controls mismatch');
   await page.locator('#homeSettingsList').screenshot({path:path.join(out,'home-'+locale+'.png')});reports.push({...report,home});await page.close();
  }
  fs.writeFileSync(path.join(out,'original-list-controls.json'),JSON.stringify({source:'v0.68.0/5d2db368d8313415763860d594de00e46a663418; original functions and CSS; public data; callbacks inert',reports},null,2)+'\n');
  console.log('Original tool/home controls: four DOM PNGs; source order, six modules and selected activity values checked');
 } finally {if(browser)await browser.close();await new Promise(resolve=>server.close(resolve))}
})().catch(error=>{process.stderr.write(error.message+'\n');process.exitCode=1});
