// Fixed source markup, original render functions and CSS. Public default settings only.
const fs=require('node:fs'),path=require('node:path'),http=require('node:http');
const {chromium}=require('/Users/bing/.cache/codex-runtimes/codex-primary-runtime/dependencies/node/node_modules/playwright');
const root=path.resolve(__dirname,'..'),ref=path.resolve(root,'../work/token-monitor-reference/desktop-v0.68.0'),out=path.join(root,'evidence/appearance-preview/source');fs.mkdirSync(out,{recursive:true});
const app=fs.readFileSync(path.join(ref,'src/electron/renderer/app.js'),'utf8');
const functions=['applyThemeColors','previewThemeColor','applyVendorColorOverrides','previewVendorColor','renderThemePresetChips','renderThemeColorGrid','renderVendorColorList'].map(name=>{const at=app.indexOf('function '+name+'(');if(at<0)throw Error(name);return app.slice(at,app.indexOf('\n}',at)+2)}).join('\n');
const scripts=['src/shared/vendorPresentation.js','src/electron/renderer/themePresets.js','src/electron/renderer/i18n.js'];
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
  for(const language of ['en','zh-CN']) {
   const page=await browser.newPage({viewport:{width:400,height:900},deviceScaleFactor:2});
   await page.route('**/*',route=>new URL(route.request().url()).origin===origin&&route.request().method()==='GET'?route.continue():route.abort());
   await page.goto(origin+'/fixture.html');for(const script of scripts) await page.addScriptTag({url:origin+'/reference/'+script});
   await page.addStyleTag({content:'body{margin:0;height:auto;overflow:auto;border-radius:0}.shell{width:360px;min-height:0;height:auto;padding:20px;gap:0}.settings-panel{max-height:none;overflow:visible}*{transition:none!important;animation:none!important}'});
   await page.evaluate(({functions,language})=>{
    const group=document.querySelector('.settings-theme-group'),shell=document.createElement('main'),panel=document.createElement('section');shell.className='shell';panel.className='settings-panel';panel.append(group);shell.append(panel);document.body.replaceChildren(shell);
    TokenMonitorI18n.applyTranslations(document,language);
    const themePresetsApi=TokenMonitorThemePresets,BRAND_VENDOR_COLORS=TokenMonitorVendorPresentation.vendorColors(),clientColors={...BRAND_VENDOR_COLORS};
    let appliedThemeOverrides={},appearancePreview={};
    const state={settings:{themeColors:{},vendorColors:{}}},els={themePresetChips:document.getElementById('themePresetChips'),themeColorGrid:document.getElementById('themeColorGrid'),vendorColorList:document.getElementById('vendorColorList')};
    const t=(key,params)=>TokenMonitorI18n.translate(language,key,params),matchingThemePresetId=()=> 'default',resolvedThemeColor=key=>appliedThemeOverrides[key]||themePresetsApi.DEFAULT_THEME[key];
    const selectThemePreset=()=>{},saveThemeColor=()=>{},resetThemeColor=()=>{},saveVendorColor=()=>{},resetVendorColor=()=>{};
    const currentThemeOverrides=()=>({}),currentVendorOverrides=()=>({}),renderFloatingBubbleContent=()=>{},render=()=>{};
    window.tokenMonitor={previewAppearance:()=>({catch:()=>{}})};
    eval(functions+';window.sourcePreview=(key,value)=>previewThemeColor(key,value);window.sourceReset=()=>applyThemeColors(currentThemeOverrides());applyThemeColors({});renderThemePresetChips();renderThemeColorGrid();renderVendorColorList();window.renderSourceColors=()=>renderThemeColorGrid();');
   },{functions,language});
   for(const variant of ['saved','preview','restored']) {
    if(variant==='preview') await page.evaluate(()=>window.sourcePreview('bg','#ffffff'));
    if(variant==='restored') await page.evaluate(()=>window.sourceReset());
    await page.evaluate(()=>window.renderSourceColors());
    const colors=await page.evaluate(()=>({bg:getComputedStyle(document.body).backgroundColor,text:getComputedStyle(document.querySelector('.settings-theme-group')).color,rootBackground:getComputedStyle(document.documentElement).getPropertyValue('--bg')}));
    await page.locator('.shell').screenshot({path:path.join(out,'whole-page-'+variant+'-'+language+'.png')});reports.push({language,variant,colors});
   }
   await page.close();
  }
  fs.writeFileSync(path.join(out,'original-page-preview.json'),JSON.stringify({source:'v0.68.0/5d2db368d8313415763860d594de00e46a663418; original HTML/CSS/applyThemeColors/previewThemeColor; public baseline; inert external callbacks',reports},null,2)+'\n');
  process.stdout.write('Original whole-page preview and restore:two languages/six DOM PNGs\n');
 } finally {if(browser)await browser.close();await new Promise(resolve=>server.close(resolve))}
})().catch(error=>{process.stderr.write(error.message+'\n');process.exitCode=1});
