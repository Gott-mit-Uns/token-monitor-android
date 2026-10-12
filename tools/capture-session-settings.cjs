// Fixed renderer's actual session configuration; public settings and inert persistence.
const fs=require('node:fs'),path=require('node:path'),http=require('node:http');
const {chromium}=require('/Users/bing/.cache/codex-runtimes/codex-primary-runtime/dependencies/node/node_modules/playwright');
const root=path.resolve(__dirname,'..'),ref=path.resolve(root,'../work/token-monitor-reference/desktop-v0.68.0');
const out=path.join(root,'evidence/session-settings/source');fs.mkdirSync(out,{recursive:true});
const app=fs.readFileSync(path.join(ref,'src/electron/renderer/app.js'),'utf8');
const start=app.indexOf('function renderSessionSettingsList(');
if(start<0)throw Error('Source function missing');
const source=app.slice(start,app.indexOf('\n}',start)+2);
const html=fs.readFileSync(path.join(ref,'src/electron/renderer/index.html'),'utf8').replace(/<script\b[^>]*>[\s\S]*?<\/script>/gi,'').replace('<head>','<head><base href="/reference/src/electron/renderer/">');
const server=http.createServer((req,res)=>{
 const url=new URL(req.url,'http://127.0.0.1');
 if(req.method!=='GET'){res.writeHead(405).end();return;}
 if(url.pathname==='/fixture.html'){res.writeHead(200,{'Content-Type':'text/html'}).end(html);return;}
 const relative=url.pathname.replace(/^\/reference\//,'');
 if(!['src/electron/renderer/i18n.js','src/electron/renderer/styles.css'].includes(relative)&&!/^src\/electron\/renderer\/icons\/[a-z0-9/-]+\.svg$/.test(relative)){res.writeHead(404).end();return;}
 const file=path.join(ref,relative);if(!fs.existsSync(file)){res.writeHead(404).end();return;}
 res.writeHead(200,{'Content-Type':relative.endsWith('.js')?'text/javascript':relative.endsWith('.css')?'text/css':'image/svg+xml'}).end(fs.readFileSync(file));
});
(async()=>{
 await new Promise(resolve=>server.listen(0,'127.0.0.1',resolve));let browser;
 try{
  const origin='http://127.0.0.1:'+server.address().port;
  browser=await chromium.launch({executablePath:'/Applications/Microsoft Edge.app/Contents/MacOS/Microsoft Edge',headless:true,args:['--disable-background-networking','--disable-component-update','--disable-sync','--no-first-run']});
  const cases=[];
  for(const locale of ['en','zh-CN']){
   const page=await browser.newPage({viewport:{width:400,height:460},deviceScaleFactor:2});
   await page.route('**/*',r=>new URL(r.request().url()).origin===origin&&r.request().method()==='GET'?r.continue():r.abort());
   await page.goto(origin+'/fixture.html');await page.addScriptTag({url:origin+'/reference/src/electron/renderer/i18n.js'});
   await page.addStyleTag({content:'body{margin:0;height:auto;overflow:auto}.shell{width:360px;padding:20px;height:auto}.settings-panel{max-height:none;overflow:visible}*{transition:none!important;animation:none!important}'});
   for(const titleValue of [null,true,false,'invalid'])for(const metric of ['used','remaining','invalid']){
    const result=await page.evaluate(({source,locale,titleValue,metric})=>{
     const state={settings:{sessionContextMetric:metric}};if(titleValue!==null)state.settings.sessionTitlesEnabled=titleValue;
     const writes=[],saveSettings=value=>{writes.push(value);return Promise.resolve();};
     const t=(key,params)=>TokenMonitorI18n.translate(locale,key,params);
     eval(source+';window.sessionSettingsNode=renderSessionSettingsList();');
     const shell=document.createElement('main');shell.className='shell';const panel=document.createElement('section');panel.className='settings-panel';panel.append(window.sessionSettingsNode);shell.append(panel);document.body.replaceChildren(shell);
     const title=document.querySelector('[name=sessionTitlesEnabled]');
     const checked=document.querySelector('[name=sessionContextMetric]:checked').value;
     const saved=JSON.stringify(state.settings);const enabled=title.checked;
     title.checked=!enabled;title.dispatchEvent(new Event('change'));
     const radio=document.querySelector('[name=sessionContextMetric][value="'+(checked==='used'?'remaining':'used')+'"]');radio.checked=true;radio.dispatchEvent(new Event('change'));
     title.checked=enabled;document.querySelector('[name=sessionContextMetric][value="'+checked+'"]').checked=true;
     const item=document.querySelector('.settings-item-title'),style=getComputedStyle(item);
     return {locale,titleValue,metric,enabled,selected:checked,writes,unchanged:saved===JSON.stringify(state.settings),label:document.querySelector('.trend-settings-row span').textContent,note:document.querySelector('.settings-note').textContent,fontSize:style.fontSize};
    },{source,locale,titleValue,metric});
    if(!result.unchanged||result.writes.length!==2)throw Error('Unexpected source persistence');
    cases.push(result);
    if(titleValue===null&&metric==='used')await page.screenshot({path:path.join(out,'session-settings-'+locale+'.png')});
   }
   await page.close();
  }
  const views=require(path.join(ref,'src/electron/renderer/viewDisplayPreferences.js'));
  const sourceIds=[...app.match(/const VIEW_DISPLAY_OPTIONS = \[([\s\S]*?)\];/)[1].matchAll(/id: '([^']+)'/g)].map(m=>m[1]).filter(id=>id!=='status');
  const mapped={tool:'tools',model:'models',project:'projects',session:'sessions',device:'devices'};
  const ids=sourceIds.map(id=>mapped[id]||id);
  const guards=ids.map(id=>{const hidden=ids.filter(other=>other!==id);return {id,hidden,count:views.visibleViewCount({views:sourceIds.map(id=>({id})),hiddenValue:sourceIds.filter(other=>(mapped[other]||other)!==id),disabledIds:[]})};});
  const fixture={guards,source:'v0.68.0/5d2db368d8313415763860d594de00e46a663418/app.js renderSessionSettingsList',cases};
  fs.writeFileSync(path.join(root,'app/src/test/resources/session-settings-parity.json'),JSON.stringify(fixture,null,2)+'\n');
  fs.writeFileSync(path.join(out,'source-semantics.txt'),fixture.source+'\n24 original DOM cases; 2 languages; source default, invalid fallback, explicit callbacks and unchanged baseline verified.\n');
  process.stdout.write('24 original session configuration cases; 2 original DOM screenshots: PASS\n');
 }finally{if(browser)await browser.close();await new Promise(resolve=>server.close(resolve));}
})().catch(error=>{process.stderr.write(error.message+'\n');process.exitCode=1;});
