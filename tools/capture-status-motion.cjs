// Public original SVG/CSS measurements; no user data or external requests.
const fs=require('node:fs'),path=require('node:path');
const {chromium}=require('/Users/bing/.cache/codex-runtimes/codex-primary-runtime/dependencies/node/node_modules/playwright');
const root=path.resolve(__dirname,'..'),ref=path.resolve(root,'../work/token-monitor-reference/desktop-v0.68.0/src/electron/renderer');
(async()=>{
 let browser;
 try {
  browser=await chromium.launch({executablePath:'/Applications/Microsoft Edge.app/Contents/MacOS/Microsoft Edge',headless:true,args:['--disable-background-networking','--disable-component-update','--disable-sync','--no-first-run']});
  const page=await browser.newPage();await page.route('**/*',r=>r.abort());
  await page.setContent(fs.readFileSync(path.join(ref,'icons/actions/spinner.svg'),'utf8')+'<div class="live-token-rate is-fresh"><span class="live-token-rate-icon"></span></div>');
  await page.addStyleTag({content:fs.readFileSync(path.join(ref,'styles.css'),'utf8')});
  const result=await page.evaluate(async()=>{
   const svg=document.querySelector('svg');svg.pauseAnimations();const spinner=[];
   for(const ms of [0,56.25,112.5,225,450,675,899,900,1012.5]) {
    svg.setCurrentTime(ms/1000);await new Promise(requestAnimationFrame);
    spinner.push({ms,alpha:[...svg.querySelectorAll('rect')].map(n=>Number(getComputedStyle(n).opacity))});
   }
   const icon=document.querySelector('.live-token-rate-icon'),anim=icon.getAnimations()[0];anim.pause();const flash=[];
   for(const ms of [0,31,100,200,260.4,400,550,620,650]) {
    anim.currentTime=ms;const style=getComputedStyle(icon),matrix=new DOMMatrixReadOnly(style.transform==='none'?undefined:style.transform);
    const match=style.filter.match(/rgba\(([^)]+)\) 0px 0px ([\d.]+)px/);
    flash.push({ms,scale:matrix.a,blur:match?Number(match[2]):0,opacity:match?Number(match[1].split(',').at(-1)):0});
   }
   return {spinner,flash};
  });
  fs.writeFileSync(path.join(root,'app/src/test/resources/status-motion-parity.json'),JSON.stringify({source:'desktop v0.68.0 original animated spinner SVG and live-token-rate-flash CSS',...result},null,2)+'\n');
  console.log(`Original spinner frames: ${result.spinner.length}; flash frames: ${result.flash.length}`);
 } finally {if(browser)await browser.close()}
})().catch(e=>{console.error(e.message);process.exitCode=1});
