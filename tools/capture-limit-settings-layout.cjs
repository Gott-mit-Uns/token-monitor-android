// Render the original provider-copy fragment with synthetic Hub rows and original CSS.
const fs=require('node:fs'),path=require('node:path'),http=require('node:http');
const {chromium}=require('/Users/bing/.cache/codex-runtimes/codex-primary-runtime/dependencies/node/node_modules/playwright');
const root=path.resolve(__dirname,'..'),ref=path.resolve(root,'../work/token-monitor-reference/desktop-v0.68.0');
const out=path.join(root,'evidence/limit-settings/source');fs.mkdirSync(out,{recursive:true});
const app=fs.readFileSync(path.join(ref,'src/electron/renderer/app.js'),'utf8');
const render=app.slice(app.indexOf('function renderLimitProviderCheckboxesNow('));
const fragment=render.slice(render.indexOf("const copy = document.createElement('span');"),render.indexOf('wrap.append(cb);'));
const nameStart=app.indexOf('function connectLimitProviderCheckboxName(');
const nameFunction=app.slice(nameStart,app.indexOf('\n}',nameStart)+2);
if(!fragment.includes('limit-provider-status-dot')||!fragment.includes('copy.append(nameLine, tags)'))throw Error('Original fragment changed');
const keyStart=app.indexOf('const LIMIT_CAPABILITY_TAG_KEYS = {');
const keys=app.slice(keyStart,app.indexOf('\n};',keyStart)+3);
const html='<!doctype html><html><head><meta charset="utf-8"><link rel="stylesheet" href="/styles.css"></head><body></body></html>';
const server=http.createServer((req,res)=>{
 if(req.method!=='GET'){res.writeHead(405).end();return}
 if(req.url==='/'){res.writeHead(200,{'Content-Type':'text/html'}).end(html);return}
 if(req.url==='/styles.css'){res.writeHead(200,{'Content-Type':'text/css'}).end(fs.readFileSync(path.join(ref,'src/electron/renderer/styles.css')));return}
 res.writeHead(404).end();
});
(async()=>{
 await new Promise(resolve=>server.listen(0,'127.0.0.1',resolve));let browser;
 try {
  browser=await chromium.launch({executablePath:'/Applications/Microsoft Edge.app/Contents/MacOS/Microsoft Edge',headless:true,args:['--disable-background-networking','--disable-component-update','--disable-sync','--no-first-run']});
  const origin='http://127.0.0.1:'+server.address().port,reports=[];
  for(const locale of ['en','zh-CN']) {
   const page=await browser.newPage({viewport:{width:420,height:350}});
   await page.route('**/*',route=>new URL(route.request().url()).origin===origin&&route.request().method()==='GET'?route.continue():route.abort());
   await page.goto(origin);
   for(const file of ['src/electron/renderer/i18n.js','src/electron/renderer/accountIdentity.js','src/shared/limits/windowLabels.js','src/electron/renderer/limits/providerPresentation.js'])await page.addScriptTag({path:path.join(ref,file)});
   await page.addStyleTag({content:'body{height:auto;min-height:350px;overflow:auto;border-radius:0;padding:20px}.settings-panel{max-height:none;overflow:visible}*{animation:none!important;transition:none!important}'});
   const report=await page.evaluate(({fragment,nameFunction,keys,locale})=>{
    const t=(key,values)=>TokenMonitorI18n.translate(locale,key,values),limitProviderPresentationApi=TokenMonitorLimitProviderPresentation;
    eval(keys+';window.publicTagKeys=LIMIT_CAPABILITY_TAG_KEYS');
    const translatedLimitProviderTag=tag=>tag?.key?t(tag.key,tag.values):(window.publicTagKeys[tag?.label]?t(window.publicTagKeys[tag.label]):tag?.label||'');
    const LIMIT_PROVIDER_CONNECTION_DETAIL_KEYS={},limitProviderAccountGroup=()=>null;
    const limitProviderProvenance=provider=>limitProviderPresentationApi.limitProviderProvenance(provider,{syncActive:true,localDeviceId:'android-viewer',devices:[]});
    const providers=[{provider:'claude',status:'ok',stale:true,source:'web',sourceDeviceId:'Public desktop'},
     {provider:'codex',status:'ok',source:'rpc',sourceDetail:'app',sourceDeviceId:'Public workstation'},
     {provider:'workbuddy',status:'unauthorized',actionRequired:'appSessionEncrypted'},
     {provider:'cline',status:'unauthorized',source:'oauth'}];
    const section=document.createElement('section');section.className='settings-panel client-checkboxes limit-provider-list';document.body.append(section);
    for(const provider of providers) {
     const id=provider.provider,label={claude:'Claude Code',codex:'Codex',workbuddy:'WorkBuddy',cline:'Cline'}[id],settingsLabel=label,isEnabled=true;
     const cb=document.createElement('input');
     const copy=eval('(()=>{'+nameFunction+';'+fragment+';return copy;})()');
     const row=document.createElement('div');row.className='limit-provider-row';row.dataset.provider=id;
     const main=document.createElement('button');main.className='limit-provider-main';main.append(copy);row.append(main);section.append(row);
    }
    return {locale,rows:[...section.querySelectorAll('.limit-provider-row')].map(row=>({id:row.dataset.provider,name:row.querySelector('.limit-provider-name').textContent,
     dot:row.querySelector('.limit-provider-status-dot')?.getAttribute('aria-label')||null,tags:[...row.querySelectorAll('.limit-provider-tag')].map(tag=>({text:tag.textContent,classes:tag.className,font:getComputedStyle(tag).fontSize,padding:getComputedStyle(tag).padding}))}))};
   },{fragment,nameFunction,keys,locale});
   reports.push(report);await page.screenshot({path:path.join(out,'provider-copy-'+locale+'.png')});await page.close();
  }
  fs.writeFileSync(path.join(out,'layout.json'),JSON.stringify({reference:'v0.68.0 original renderer fragment and CSS',reports},null,2)+'\n');
  process.stdout.write('Original provider copy rendered: 2 locales / 4 public rows\n');
 } finally {if(browser)await browser.close();await new Promise(resolve=>server.close(resolve))}
})().catch(error=>{process.stderr.write(error.message+'\n');process.exitCode=1});
