// Fixed source markup, original render functions and CSS. Public default settings only.
const fs=require('node:fs'),path=require('node:path'),http=require('node:http');
const {chromium}=require('/Users/bing/.cache/codex-runtimes/codex-primary-runtime/dependencies/node/node_modules/playwright');
const root=path.resolve(__dirname,'..'),ref=path.resolve(root,'../work/token-monitor-reference/desktop-v0.68.0'),out=path.join(root,'evidence/edit-controls/source');fs.mkdirSync(out,{recursive:true});
const app=fs.readFileSync(path.join(ref,'src/electron/renderer/app.js'),'utf8');
const functions=['renderThemePresetChips','renderThemeColorGrid','renderVendorColorList'].map(name=>{const at=app.indexOf('function '+name+'(');if(at<0)throw Error(name);return app.slice(at,app.indexOf('\n}',at)+2)}).join('\n');
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
  for(const preset of ['default','porcelain']) {
   const page=await browser.newPage({viewport:{width:400,height:900},deviceScaleFactor:2});
   await page.route('**/*',route=>new URL(route.request().url()).origin===origin&&route.request().method()==='GET'?route.continue():route.abort());
   await page.goto(origin+'/fixture.html');for(const script of scripts) await page.addScriptTag({url:origin+'/reference/'+script});
   await page.addStyleTag({content:'body{margin:0;height:auto;overflow:auto;border-radius:0}.shell{width:360px;min-height:0;height:auto;padding:20px;gap:0}.settings-panel{max-height:none;overflow:visible}*{transition:none!important;animation:none!important}'});
   const data=await page.evaluate(({functions,preset})=>{
    const group=document.querySelector('.settings-theme-group');const shell=document.createElement('main');shell.className='shell';const panel=document.createElement('section');panel.className='settings-panel';panel.append(group);shell.append(panel);document.body.replaceChildren(shell);
    const themePresetsApi=TokenMonitorThemePresets,chosen=themePresetsApi.THEME_PRESETS.find(p=>p.id===preset),state={settings:{themeColors:chosen.colors,vendorColors:{}}},appliedThemeOverrides=chosen.colors;
    themePresetsApi.themeCssVarEntries(chosen.colors).forEach(entry=>document.documentElement.style.setProperty(entry.name,entry.value));document.documentElement.classList.toggle('is-light',themePresetsApi.isLightHex(chosen.colors.bg));
    const els={themePresetChips:document.getElementById('themePresetChips'),themeColorGrid:document.getElementById('themeColorGrid'),vendorColorList:document.getElementById('vendorColorList')};
    const BRAND_VENDOR_COLORS=TokenMonitorVendorPresentation.vendorColors(),t=(key,params)=>TokenMonitorI18n.translate('en',key,params);
    const matchingThemePresetId=()=>preset,resolvedThemeColor=key=>appliedThemeOverrides[key]||themePresetsApi.DEFAULT_THEME[key];
    const selectThemePreset=()=>{},previewThemeColor=()=>{},saveThemeColor=()=>{},resetThemeColor=()=>{},previewVendorColor=()=>{},saveVendorColor=()=>{},resetVendorColor=()=>{};
    eval(functions+';renderThemePresetChips();renderThemeColorGrid();renderVendorColorList();');TokenMonitorI18n.applyTranslations(document,'en');
    document.getElementById('themeCodeInput').value=themePresetsApi.encodeThemeCode(chosen.colors);
    document.getElementById('interfaceFontPreset').value='app';document.getElementById('displayFontPreset').value='app';
    const selectors=['.theme-preset-chip','.theme-preset-dot','.theme-code-input','.hub-secret-row .icon-button','.color-picker-input','.reset-inline','.settings-font-input-row select','.settings-group-header'];
    return {preset,code:document.getElementById('themeCodeInput').value,style:selectors.map(selector=>{const node=document.querySelector(selector),s=getComputedStyle(node);return {selector,width:s.width,height:s.height,fontSize:s.fontSize,padding:s.padding,borderRadius:s.borderRadius,background:s.backgroundColor,color:s.color}}),vendors:els.vendorColorList.children.length};
   },{functions,preset});
   await page.locator('.settings-theme-group').screenshot({path:path.join(out,'theme-'+preset+'.png')});
   await page.locator('#themeAdvancedDetails').evaluate(node=>{node.classList.remove('hidden');node.removeAttribute('inert')});
   await page.locator('.settings-theme-group').screenshot({path:path.join(out,'advanced-'+preset+'.png')});
   reports.push(data);await page.close();
  }
  fs.writeFileSync(path.join(out,'original-controls.json'),JSON.stringify({source:'v0.68.0/5d2db368d8313415763860d594de00e46a663418; original HTML/CSS/three render functions; default public palettes',reports},null,2)+'\n');
  process.stdout.write('Original settings controls: two palettes, four source DOM PNGs, eight computed style selectors\n');
 } finally {if(browser)await browser.close();await new Promise(resolve=>server.close(resolve))}
})().catch(error=>{process.stderr.write(error.message+'\n');process.exitCode=1});
