// Execute pinned renderer functions using public fixtures, without app state or external reads.
const fs=require('node:fs'),path=require('node:path'),http=require('node:http');
const {chromium}=require('/Users/bing/.cache/codex-runtimes/codex-primary-runtime/dependencies/node/node_modules/playwright');
const root=path.resolve(__dirname,'..'),ref=path.resolve(root,'../work/token-monitor-reference/desktop-v0.68.0');
const out=path.join(root,'evidence/settings-management/source');fs.mkdirSync(out,{recursive:true});
const app=fs.readFileSync(path.join(ref,'src/electron/renderer/app.js'),'utf8');
const names=['renderHomeLimitProviderList','limitProviderUsageItemsList','renderHomeLimitModule','visibilityIcon','createPreferenceOrderHandle','resetViewDisplayOrder','showAllViews'];
const source=names.map(name=>{const re=new RegExp('(?:async )?function '+name+'\\(');const at=app.search(re);if(at<0)throw Error(name);return app.slice(at,app.indexOf('\n}',at)+2)}).join('\n');
const catalog=require(path.join(ref,'src/shared/limits/providers.js')).LIMIT_PROVIDER_CATALOG;
const filter=require(path.join(ref,'src/electron/renderer/settingsListFilter.js'));
const queries=['','  CODEX  ','Claude Code','Z.ai / GLM','GLM zai','factory droid','cod ex','no-public-match'];
const search=queries.map(query=>({query,ids:filter.filterListItems(catalog,query,({id,label,settingsLabel})=>`${settingsLabel||label} ${label} ${id}`).map(p=>p.id)}));
const scripts=['src/electron/renderer/i18n.js','src/electron/renderer/limits/providerOrder.js','src/electron/renderer/homeModulePreferences.js','src/electron/renderer/limits/displayMode.js'];
const allowed=new Set([...scripts,'src/electron/renderer/styles.css']);
const html=fs.readFileSync(path.join(ref,'src/electron/renderer/index.html'),'utf8').replace(/<script\b[^>]*>[\s\S]*?<\/script>/gi,'').replace('<head>','<head><base href="/reference/src/electron/renderer/">');
const server=http.createServer((req,res)=>{
 const name=decodeURIComponent(new URL(req.url,'http://127.0.0.1').pathname);
 if(req.method!=='GET'){res.writeHead(405).end();return}
 if(name==='/fixture.html'){res.writeHead(200,{'Content-Type':'text/html; charset=utf-8'}).end(html);return}
 const relative=name.replace(/^\/reference\//,'');
 if(!name.startsWith('/reference/')||!allowed.has(relative)){res.writeHead(404).end();return}
 res.writeHead(200,{'Content-Type':relative.endsWith('.css')?'text/css':'text/javascript'}).end(fs.readFileSync(path.join(ref,relative)));
});
(async()=>{
 await new Promise(resolve=>server.listen(0,'127.0.0.1',resolve));let browser;
 try {
  const origin='http://127.0.0.1:'+server.address().port;
  browser=await chromium.launch({executablePath:'/Applications/Microsoft Edge.app/Contents/MacOS/Microsoft Edge',headless:true,args:['--disable-background-networking','--disable-component-update','--disable-sync','--no-first-run']});
  const reports=[];
  for(const locale of ['en','zh-CN']) {
   const page=await browser.newPage({viewport:{width:420,height:900},deviceScaleFactor:2});
   await page.route('**/*',route=>new URL(route.request().url()).origin===origin&&route.request().method()==='GET'?route.continue():route.abort());
   await page.goto(origin+'/fixture.html');for(const script of scripts)await page.addScriptTag({url:origin+'/reference/'+script});
   await page.addStyleTag({content:'body{margin:0;height:auto;overflow:auto;border-radius:0}.shell{width:380px;min-height:0;height:auto;padding:20px;gap:12px}.settings-panel{max-height:none;overflow:visible}*{transition:none!important;animation:none!important}'});
   const report=await page.evaluate(({source,catalog,locale})=>{
    const t=(key,params)=>TokenMonitorI18n.translate(locale,key,params);
    const state={settings:{homeLimitDisplayMode:'text',showHomeLimitBars:true,showHomeLimitProviderNames:false,homeLimitAccountCount:3,homeLimitProviderOrder:'codex,claude',hiddenHomeLimitProviders:'claude',showToolIcons:false}};
    const LIMIT_PROVIDERS=catalog,limitProviderOrderApi=TokenMonitorLimitProviderOrder,homeModulePreferencesApi=TokenMonitorHomeModulePreferences;
    const hiddenHomeLimitProviderSet=()=>new Set(state.settings.hiddenHomeLimitProviders.split(','));
    const enabledLimitProviderSet=()=>new Set(['claude','codex','zai']);
    const homeLimitProviderOrderValue=()=>state.settings.homeLimitProviderOrder;
    const writes=[];const saveSettings=value=>{writes.push(value);return Promise.resolve()};
    const onHomeLimitProviderVisibilityToggle=()=>{},resetHomeLimitProviderOrder=()=>{},showAllHomeLimitProviders=()=>{},onPreferenceOrderKeydown=()=>{};
    const header=document.getElementById('resetViewDisplayOrderButton').closest('.settings-note-row');
    const headerBeforeViews=header.nextElementSibling?.id==='viewDisplayList';
    header.querySelectorAll('[data-i18n]').forEach(n=>n.textContent=t(n.dataset.i18n));
    header.querySelectorAll('[data-i18n-title]').forEach(n=>{n.title=t(n.dataset.i18nTitle);n.setAttribute('aria-label',n.title)});
    eval(source+';window.homeQuotaOptions=renderHomeLimitProviderList();window.usageOptions=limitProviderUsageItemsList("codex",[{id:"public-first",label:"Public first",hidden:false,available:true},{id:"public-old",label:"Public old",hidden:true,available:false}]);resetViewDisplayOrder();showAllViews();');
    const shell=document.createElement('main');shell.className='shell';const panel=document.createElement('section');panel.className='settings-panel';
    const usageWrap=document.createElement('div');usageWrap.className='client-checkboxes limit-provider-list';usageWrap.append(window.usageOptions);
    panel.append(header,window.homeQuotaOptions,usageWrap);shell.append(panel);document.body.replaceChildren(shell);
    const home=window.homeQuotaOptions;
    const checks=home.querySelectorAll('input[type=checkbox]');
    const semantics={locale,headerBeforeViews,buttonTitles:[...header.querySelectorAll('button')].map(n=>n.title),headerSize:getComputedStyle(header.querySelector('button')).width,
     mode:home.querySelector('select').value,highlight:checks[0].checked,names:checks[1].checked,namesDisabled:checks[1].disabled,
     count:{min:home.querySelector('input[type=number]').min,max:home.querySelector('input[type=number]').max},providerNames:[...home.querySelectorAll('.tool-preference-name')].map(n=>n.textContent),
     unavailable:window.usageOptions.querySelectorAll('.client-checkbox')[1].textContent,writes};
    return semantics;
   },{source,catalog,locale});reports.push(report);
   await page.screenshot({path:path.join(out,'management-'+locale+'.png')});
   const highlights=await page.evaluate(({source,locale})=>{
    const state={settings:{}};let rows=[];
    const t=key=>TokenMonitorI18n.translate(locale,key);
    const homeModuleShell=()=>{const module=document.createElement('section'),body=document.createElement('div');module.append(body);return {module,body}};
    const homeLimitRows=()=>rows,applyHomeListMark=()=>{},iconKindFor=()=>'',homeLimitWindowLabel=w=>w.label,formatHomeLimitWindowValue=w=>w.value;
    const optionalFiniteNumber=value=>value==null?null:Number.isFinite(Number(value))?Number(value):null;
    const {limitFillPercent}=TokenMonitorLimitDisplayMode;const isCreditsWindow=w=>w.metric==='credits';
    const limitWindowsView={limitMeterNode:()=>{const node=document.createElement('div');node.className='public-meter';return node}};
    const cases=[];
    for(const mode of ['text','bars'])for(const enabled of [false,true])for(const remaining of [null,-1,0,19.999,20,49.999,50,100,101]) {
     state.settings={homeLimitDisplayMode:mode,showHomeLimitBars:enabled};
     rows=[{name:'Public account',providerId:'codex',color:'#49a3b0',windows:[{kind:'session',label:'Public session',value:'Public value',remainingPercent:remaining}]}];
     eval(source+';window.highlightNode=renderHomeLimitModule();');
     const node=window.highlightNode,value=node.querySelector('.home-list-value');
     cases.push({mode,enabled,remaining,critical:value.classList.contains('home-limit-value-critical'),low:value.classList.contains('home-limit-value-low'),meter:Boolean(node.querySelector('.public-meter'))});
    }
    return cases;
   },{source,locale});report.highlights=highlights;await page.close();
  }
  const fixture={referenceCommit:'5d2db368d8313415763860d594de00e46a663418',sourceFunctions:names,catalog,search,reports};
  fs.writeFileSync(path.join(root,'app/src/test/resources/settings-management-parity.json'),JSON.stringify(fixture,null,2)+'\n');
  fs.writeFileSync(path.join(out,'source-semantics.txt'),'Pinned original HTML/CSS/functions; public fixtures; two languages; no application state or external requests.\n'+JSON.stringify(reports.map(({highlights,...r})=>({...r,highlightCases:highlights.length})),null,2)+'\n');
  process.stdout.write('Original settings headers, Home quota/usage controls, 72 highlight cases and provider search: PASS\n');
 }finally {if(browser)await browser.close();await new Promise(resolve=>server.close(resolve));}
})().catch(e=>{process.stderr.write(e.message+'\n');process.exitCode=1});
