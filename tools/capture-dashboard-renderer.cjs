// Complete fixed desktop Dashboard and preload, using only public, in-memory IPC data.
'use strict';
const fs=require('node:fs'),path=require('node:path'),http=require('node:http'),crypto=require('node:crypto');
const {execFileSync}=require('node:child_process');
const {chromium}=require('/Users/bing/.cache/codex-runtimes/codex-primary-runtime/dependencies/node/node_modules/playwright');
const root=path.resolve(__dirname,'..'),ref=path.resolve(root,'../work/token-monitor-reference/desktop-v0.68.0');
const output=path.resolve(root,process.argv[2]||'evidence/dashboard-parity/source');
if(!output.startsWith(path.join(root,'evidence')+path.sep))throw Error('Output must be project evidence');
const commit='5d2db368d8313415763860d594de00e46a663418',now=Date.parse('2026-10-09T08:00:00Z');
const hash=bytes=>crypto.createHash('sha256').update(bytes).digest('hex');
const history={daily:Array.from({length:84},(_,n)=>({date:new Date(now-(83-n)*86400000).toISOString().slice(0,10),tokens:(n%11+1)*1430000,cost:1,
  perClient:{codex:{tokens:(n%11+1)*1000000,cost:.7},claude:{tokens:(n%11+1)*430000,cost:.3}},
  perModel:{'gpt-5.5':{tokens:(n%11+1)*1000000,cost:.7},'claude-sonnet-4.6':{tokens:(n%11+1)*430000,cost:.3}}})),monthly:[],
  summary:{totalTokens:165164772,totalCost:136.08,activeDays:106,currentStreak:14,longestStreak:40,activeTimeMs:198000000,peakDayTokens:22000000,favoriteModel:'gpt-5.5',messages:1600}};
const presets=require(path.join(ref,'src/electron/renderer/themePresets.js'));
const fonts=require(path.join(ref,'src/shared/fontSettings.js'));
const baseSettings={language:'en',currency:'USD',compactTokenUnits:'western',heatmapMetric:'cost',dashboardFlat:true,
  reduceMotion:'on',glassOpacity:100,glassBlur:0,textSize:'standard',interfaceFontFamily:'',displayFontFamily:fonts.DEFAULT_DISPLAY_FONT,
  themeColors:{},vendorColors:{}};

async function main(){
  if(execFileSync('git',['rev-parse','HEAD'],{cwd:ref,encoding:'utf8'}).trim()!==commit)throw Error('Reference differs');
  execFileSync('git',['diff','--quiet',commit,'--','src','assets'],{cwd:ref});
  fs.mkdirSync(output,{recursive:true});
  const preload=fs.readFileSync(path.join(ref,'src/electron/preload.js'),'utf8');
  const served=new Set(),errors=[],blocked=[],captures=[];
  const server=http.createServer((req,res)=>{
    const relative=decodeURIComponent(new URL(req.url,'http://127.0.0.1').pathname).replace(/^\/reference\//,'');
    const file=path.resolve(ref,relative);
    const allowed=/^src\/(electron|shared)\/.*\.(html|js|css|svg)$/.test(relative)||/^assets\/.*\.(svg|png|woff2?)$/.test(relative);
    if(req.method!=='GET'||!req.url.startsWith('/reference/')||!allowed||!file.startsWith(ref+path.sep)||!fs.existsSync(file)||!fs.statSync(file).isFile()){
      res.writeHead(404).end();return;
    }
    served.add(relative);
    const mime={'.html':'text/html','.js':'text/javascript','.css':'text/css','.svg':'image/svg+xml','.png':'image/png','.woff':'font/woff','.woff2':'font/woff2'};
    res.writeHead(200,{'Content-Type':mime[path.extname(file)],'Cache-Control':'no-store'}).end(fs.readFileSync(file));
  });
  await new Promise(resolve=>server.listen(0,'127.0.0.1',resolve));
  const origin='http://127.0.0.1:'+server.address().port;
  let browser;
  try{
    browser=await chromium.launch({executablePath:'/Applications/Microsoft Edge.app/Contents/MacOS/Microsoft Edge',headless:true,
      args:['--disable-background-networking','--disable-component-update','--disable-sync','--no-first-run']});
    for(const language of ['zh-CN','en'])for(const theme of ['dark','light'])for(const profile of ['phone','wide']){
      const viewport=profile==='phone'?{width:400,height:780}:{width:1200,height:820};
      const settings={...baseSettings,language,themeColors:theme==='light'?presets.THEME_PRESETS.find(p=>p.id==='porcelain').colors:{}};
      const page=await browser.newPage({viewport,deviceScaleFactor:2,timezoneId:'Asia/Shanghai',locale:language});
      page.on('pageerror',e=>errors.push({language,theme,profile,error:e.message}));
      await page.route('**/*',route=>{
        const request=route.request();
        if(new URL(request.url()).origin===origin && request.method()==='GET')return route.continue();
        blocked.push({language,theme,profile,path:new URL(request.url()).pathname});return route.abort();
      });
      page.on('response',response=>{if(response.status()>=400)blocked.push({language,theme,profile,path:new URL(response.url()).pathname,status:response.status()});});
      await page.addInitScript(({preload,history,settings,now})=>{
        const NativeDate=Date;
        window.Date=class extends NativeDate{constructor(...args){super(...(args.length?args:[now]));}static now(){return now;}};
        const listeners=new Map(),channels=[],config=structuredClone(settings);
        let ready=false;
        const ipcRenderer={async invoke(channel,...args){
          channels.push(channel);
          if(channel==='settings:get')return structuredClone(config);
          if(channel==='settings:update'){Object.assign(config,args[0]);return structuredClone(config);}
          if(channel==='dashboard:getHistory')return structuredClone(history);
          if(channel==='appearance:getNativeMaterial')return null;
          throw Error('Unexpected public Dashboard IPC '+channel);
        },on(channel,callback){if(!listeners.has(channel))listeners.set(channel,new Set());listeners.get(channel).add(callback);},
          removeListener(channel,callback){listeners.get(channel)?.delete(callback);},send(channel){channels.push(channel);if(channel==='dashboard:ready')ready=true;else throw Error('Unexpected Dashboard send '+channel);}};
        window.publicDashboardFixture={channels,get ready(){return ready;},settings:config};
        const require=name=>{if(name!=='electron')throw Error('Unsupported preload dependency');return {ipcRenderer,contextBridge:{exposeInMainWorld(name,value){window[name]=value;}}};};
        new Function('require',preload)(require);
      },{preload,history,settings,now});
      await page.goto(origin+'/reference/src/electron/renderer/dashboard.html',{waitUntil:'load'});
      await page.waitForFunction(()=>window.publicDashboardFixture?.ready && document.querySelectorAll('.dash-card').length===8);
      const settle=async()=>{await page.evaluate(()=>new Promise(resolve=>requestAnimationFrame(()=>requestAnimationFrame(resolve))));await page.waitForTimeout(150);};
      const capture=async(view)=>{
        await settle();
        const image=`desktop-dashboard-${view}-${language}-${theme}-${profile}.png`;
        await page.screenshot({path:path.join(output,image)});
        const record=await page.evaluate(()=>{
          const computed=selector=>{const node=document.querySelector(selector);if(!node)return null;const s=getComputedStyle(node),r=node.getBoundingClientRect();return {x:r.x,y:r.y,width:r.width,height:r.height,fontSize:s.fontSize,fontFamily:s.fontFamily,fontWeight:s.fontWeight,color:s.color,background:s.backgroundColor,gap:s.gap,padding:s.padding,opacity:s.opacity};};
          return {title:document.querySelector('.dash-title').textContent,tabs:[...document.querySelectorAll('.dash-tab')].map(n=>({text:n.textContent,selected:n.classList.contains('active')})),
            pane:document.querySelector('.dash-pane:not(.hidden)').id,
            summary:[...document.querySelectorAll('.dash-card')].map(n=>({value:n.querySelector('.dash-card-v').textContent,label:n.querySelector('.dash-card-k').textContent})),
            ranking:[...document.querySelectorAll('.dash-breakdown-col')].map(n=>n.innerText),legend:document.querySelector('#dashLegend').innerText,
            ranges:[...document.querySelectorAll('.range-btn')].map(n=>({value:n.dataset.val,text:n.textContent,selected:n.classList.contains('active')})),
            chartBars:document.querySelectorAll('.bar-stack').length,candles:document.querySelectorAll('.candle-body').length,
            candleBodyStyle:(()=>{const node=document.querySelector('.candle-body');if(!node)return null;const css=getComputedStyle(node),bounds=node.getBBox();return {stroke:css.stroke,strokeWidth:css.strokeWidth,fill:css.fill,transformBox:css.transformBox,geometry:{x:bounds.x,y:bounds.y,width:bounds.width,height:bounds.height}};})(),
            heatCells:document.querySelectorAll('.heat-base-layer .heat').length,months:[...document.querySelectorAll('.heat-month')].map(n=>n.textContent),
            computed:Object.fromEntries(['body','.dash-header','.dash-title','.dash-tabs','.dash-cards','.dash-card-v','.dash-card-k','.dash-heatmap-wrap','.dash-breakdown','#dashChart','#dashLegend'].map(selector=>[selector,computed(selector)])),channels:[...new Set(window.publicDashboardFixture.channels)]};
        });
        captures.push({language,theme,profile,view,viewport,image,sha256:hash(fs.readFileSync(path.join(output,image))),...record});
      };
      await capture('overview');
      await page.locator('[data-control="heatmapMetric"] [data-val="tokens"]').click();await capture('overview-tokens');
      await page.locator('#trendsTab').click();await capture('bars-tool');
      await page.locator('[data-stack="model"]').click();await capture('bars-model');
      await page.locator('[data-mode="kline"]').click();await capture('kline');
      await page.close();
    }
    const sourceFiles=[...new Set([...served,'src/electron/preload.js',...Object.keys(require.cache).filter(f=>f.startsWith(ref+path.sep)).map(f=>path.relative(ref,f))])]
      .sort().map(file=>({file,sha256:hash(fs.readFileSync(path.join(ref,file)))}));
    const report={sourceCommit:commit,methodology:'Unmodified complete Dashboard HTML/CSS/JS and preload. Fixed public history and date. GET-only loopback static server; all external requests blocked. Static captures with reduced motion; no FPS claim.',
      adaptations:{deviceScaleFactor:2,nativeBackdrop:false,glassOpacity:100,dashboardFlat:true,reduceMotion:'on',fixedDateNow:new Date(now).toISOString()},
      fixtures:{history,baseSettings},sourceFiles,captures,errors,blocked};
    fs.writeFileSync(path.join(output,'dashboard-renderer-reference.json'),JSON.stringify(report,null,2)+'\n');
    if(errors.length||blocked.length)throw Error(`Dashboard diagnostics: ${errors.length} errors, ${blocked.length} blocked/failed requests`);
    console.log(`Captured ${captures.length} original Dashboard pages; ${sourceFiles.length} source fingerprints; no browser errors or external requests.`);
  }finally{await browser?.close();await new Promise(resolve=>server.close(resolve));}
}
main().catch(error=>{console.error(error.stack);process.exitCode=1;});
