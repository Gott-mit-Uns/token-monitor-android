// Fixed source markup, original render functions and CSS. Public default settings only.
const fs=require('node:fs'),path=require('node:path'),http=require('node:http');
const {chromium}=require('/Users/bing/.cache/codex-runtimes/codex-primary-runtime/dependencies/node/node_modules/playwright');
const root=path.resolve(__dirname,'..'),ref=path.resolve(root,'../work/token-monitor-reference/desktop-v0.68.0'),out=path.join(root,'evidence/connection-controls/source');fs.mkdirSync(out,{recursive:true});
const app=fs.readFileSync(path.join(ref,'src/electron/renderer/app.js'),'utf8');
const functions=['syncHubConnectionUi','renderSyncPanelConnection'].map(name=>{const at=app.indexOf('function '+name+'(');if(at<0)throw Error(name);return app.slice(at,app.indexOf('\n}',at)+2)}).join('\n');
const scripts=['src/shared/vendorPresentation.js','src/electron/renderer/themePresets.js','src/electron/renderer/i18n.js','src/electron/renderer/syncDevicePanel.js'];
const css='src/electron/renderer/styles.css';const allowed=new Set([...scripts,css]);
const index=fs.readFileSync(path.join(ref,'src/electron/renderer/index.html'),'utf8').replace(/<script\b[^>]*>[\s\S]*?<\/script>/gi,'').replace('<head>','<head><base href="/reference/src/electron/renderer/">');
const server=http.createServer((req,res)=>{
 const name=decodeURIComponent(new URL(req.url,'http://127.0.0.1').pathname);
 if(req.method!=='GET'){res.writeHead(405).end();return}
 if(name==='/fixture.html'){res.writeHead(200,{'Content-Type':'text/html'}).end(index);return}
 const relative=name.replace(/^\/reference\//,'');
 if(!name.startsWith('/reference/') || !(allowed.has(relative)||/^src\/electron\/renderer\/icons\/[a-z0-9/-]+\.svg$/.test(relative))){res.writeHead(404).end();return}
 const file=path.join(ref,relative);if(!fs.existsSync(file)){res.writeHead(404).end();return}
 res.writeHead(200,{'Content-Type':relative.endsWith('.css')?'text/css':relative.endsWith('.svg')?'image/svg+xml':'text/javascript'}).end(fs.readFileSync(file));
});
(async()=>{
 await new Promise(resolve=>server.listen(0,'127.0.0.1',resolve));let browser;
 try {
  const origin='http://127.0.0.1:'+server.address().port;
  browser=await chromium.launch({executablePath:'/Applications/Microsoft Edge.app/Contents/MacOS/Microsoft Edge',headless:true,args:['--disable-background-networking','--disable-component-update','--disable-sync','--no-first-run']});
  const reports=[];
  for(const language of ['en','zh-CN']) for(const preset of ['default','porcelain']) for(const variant of ['initial','connected','editing','pending','failed']) {
   const page=await browser.newPage({viewport:{width:400,height:1000},deviceScaleFactor:2});
   await page.route('**/*',route=>new URL(route.request().url()).origin===origin&&route.request().method()==='GET'?route.continue():route.abort());
   await page.goto(origin+'/fixture.html');for(const script of scripts)await page.addScriptTag({url:origin+'/reference/'+script});
   await page.addStyleTag({content:'body{margin:0;height:auto;overflow:auto;border-radius:0}.shell{width:360px;min-height:0;height:auto;padding:20px;gap:0}.settings-panel{max-height:none;overflow:visible}*{transition:none!important;animation:none!important}'});
   const data=await page.evaluate(({functions,language,preset,variant})=>{
    const els=Object.fromEntries([...document.querySelectorAll('[id]')].map(node=>[node.id,node]));
    const group=document.querySelector('.settings-sync-group'),shell=document.createElement('main'),panel=document.createElement('section');shell.className='shell';panel.className='settings-panel';panel.append(group);shell.append(panel);document.body.replaceChildren(shell);
    const api=TokenMonitorThemePresets,chosen=api.THEME_PRESETS.find(p=>p.id===preset);api.themeCssVarEntries(chosen.colors).forEach(entry=>document.documentElement.style.setProperty(entry.name,entry.value));document.documentElement.classList.toggle('is-light',api.isLightHex(chosen.colors.bg));
    const state={settings:{hubMode:'client',hubUrl:variant==='initial'?'':'https://public.example.invalid:17321'},mode:'sync',streamConnected:variant==='connected',streamFailure:variant==='failed'?'public fixture unavailable':''};
    const syncDevicePanelApi=TokenMonitorSyncDevicePanel,t=(key,params)=>TokenMonitorI18n.translate(language,key,params),hubDraftDirty={},CLIENT_CONNECTION_FIELDS=['hubUrl','secret','deviceId'];
    const clientConnectionEditing=variant==='editing',hubSaveBusy=false,hubSaveError=false;
    const clientConnectionHasDraft=()=>false,isSettingsSurfaceVisible=()=>false,isRendererWindowHidden=()=>false,streamFailureText=value=>value,setHoverMarqueeText=(node,value)=>{node.textContent=value};
    els.syncSettingsDetails.classList.remove('hidden');els.hubClientFields.classList.remove('hidden');els.hubHostFields.classList.add('hidden');
    eval(functions+';syncHubConnectionUi();renderSyncPanelConnection("client");');
    els.syncUploadIntervalRow.hidden=true;els.syncDeviceSettings.hidden=true;
    TokenMonitorI18n.applyTranslations(document,language);els.hubModeOptions.value='client';els.hubUrlInput.value=state.settings.hubUrl;els.secretInput.value='';
    const selectors=['.sync-panel-state strong','.sync-connection-edit','.sync-connection-identity','#hubUrlInput','#secretPasteButton'];
    return {language,preset,variant,editing:!els.syncConnectionEditor.hidden,identity:els.syncConnectionEndpoint.textContent,status:els.syncPanelConnection.dataset.state,styles:selectors.map(selector=>{const node=document.querySelector(selector),s=getComputedStyle(node);return {selector,width:s.width,height:s.height,fontSize:s.fontSize,fontWeight:s.fontWeight,padding:s.padding,borderRadius:s.borderRadius}})};
   },{functions,language,preset,variant});
   await page.locator('.settings-sync-group').screenshot({path:path.join(out,variant+'-'+preset+'-'+language+'.png')});reports.push(data);await page.close();
  }
  fs.writeFileSync(path.join(out,'original-connection-controls.json'),JSON.stringify({source:'v0.68.0/5d2db368d8313415763860d594de00e46a663418; original markup/CSS and two original render functions; public fields only',reports},null,2)+'\n');
  process.stdout.write('Original connection DOM: two languages, two palettes, five states;20 PNGs\n');
 } finally {if(browser)await browser.close();await new Promise(resolve=>server.close(resolve))}
})().catch(error=>{process.stderr.write(error.message+'\n');process.exitCode=1});
