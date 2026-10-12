/* Verify all choices in a generated, public-fixture gallery without network access. */
const fs=require('node:fs/promises'),path=require('node:path');
const {pathToFileURL}=require('node:url');
const {chromium}=require('/Users/bing/.cache/codex-runtimes/codex-primary-runtime/dependencies/node/node_modules/playwright');
(async()=>{
  const root=path.resolve(__dirname,'..'),dir=path.resolve(root,process.argv[2]||'');
  if(!dir.startsWith(path.join(root,'evidence')+path.sep))throw Error('Expected project evidence directory');
  const build=JSON.parse(await fs.readFile(path.join(dir,'build-checks.json'),'utf8'));
  const browser=await chromium.launch({headless:true,executablePath:'/Applications/Microsoft Edge.app/Contents/MacOS/Microsoft Edge'});
  const errors=[],blocked=[];
  try{
    const page=await browser.newPage({viewport:{width:1100,height:900}});
    page.on('pageerror',e=>errors.push(e.message));
    await page.route('**/*',r=>{if(r.request().url().startsWith('file:'))return r.continue();blocked.push(r.request().url().split(':')[0]);return r.abort()});
    await page.goto(pathToFileURL(path.join(dir,'preview.html')).href);
    const choices=await page.locator('#page option').count();
    for(let i=0;i<choices;i++){
      await page.locator('#page').selectOption(String(i));
      await page.waitForFunction(()=>[...document.images].every(n=>n.complete&&n.naturalWidth>0));
    }
    await page.locator('#page').selectOption(String(choices-1));
    await page.screenshot({path:path.join(dir,'gallery-browser.png'),fullPage:true});
    if(errors.length||blocked.length)throw Error('Gallery had errors or attempted external requests');
    await fs.writeFile(path.join(dir,'gallery-browser-checks.json'),JSON.stringify({apkSha256:build.apkSha256,choices,imagesLoaded:true,errors,externalRequests:blocked},null,2)+'\n');
    console.log(`Verified ${choices} local gallery choices; all images loaded; no external requests.`);
  }finally{await browser.close()}
})().catch(e=>{console.error(e.message);process.exitCode=1});
