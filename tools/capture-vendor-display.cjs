// Fixed public desktop pure modules and provider-color function; no settings/Hub/network.
const fs=require('node:fs'),path=require('node:path'),vm=require('node:vm');
const root=path.resolve(__dirname,'..'),ref=path.resolve(root,'../work/token-monitor-reference/desktop-v0.68.0');
const vendors=require(path.join(ref,'src/shared/vendorPresentation.js'));
const theme=require(path.join(ref,'src/electron/renderer/themePresets.js'));
const charts=require(path.join(ref,'src/electron/renderer/usageCharts.js'));
const policy=require(path.join(ref,'src/electron/renderer/breakdownRenderPolicy.js'));
const app=fs.readFileSync(path.join(ref,'src/electron/renderer/app.js'),'utf8');
const providerSource=app.match(/function limitProviderColor\(providerId\) \{[\s\S]*?\n\}/)?.[0];
if(!providerSource) throw new Error('Fixed pure provider-color function missing');
const brand=vendors.vendorColors(),ids=theme.orderedVendorIds(brand);
const hex=value=>value.length===4?'#'+[...value.slice(1)].map(c=>c+c).join(''):value.toLowerCase();
const models=['auto','claude-sonnet','openai/gpt-5','gemini-pro','grok-4','deepseek-v4','nemotron','llama','codestral','qwen-3','kimi-k3','glm-5','command-r','mimo','minimax','doubao','step-2','hy3','swe-1','big-pickle','unknown-model','未知模型','polygenelubricants'];
const providerIds=['claude','codex','factory','droid','newapi','sub2api','unknown-provider'];
const inputs=[{}, {codex:'#ABCDEF',claude:' #ff0000 ',default:'#010203'}, {grok:'#000000'}, {cursor:'#000',claude:'invalid',bad:'#123456'}, {default:'#fefefe'}, Object.fromEntries(ids.map(id=>[id,'#123456'])), {droid:'#112233',codex:'#445566',gemini:'#abcdef'}];
const palettes=inputs.map(input=>{
 const colors=theme.mergeVendorColors(brand,input);Object.assign(charts.clientColors,colors);
 const scope={clientColors:colors};vm.createContext(scope);vm.runInContext(providerSource,scope);
 return {input,clean:theme.normalizeOverrides(input,Object.keys(brand)),colors:Object.fromEntries(Object.entries(colors).map(([k,v])=>[k,hex(v)])),models:models.map(name=>({name,vendor:charts.modelVendorFor(name),color:hex(charts.modelColor(name))})),providers:providerIds.map(id=>({id,color:hex(scope.limitProviderColor(id))}))};
});
const labels=Object.fromEntries(ids.map(id=>[id,theme.vendorLabel(id)]));
const quoted=v=>JSON.stringify(v).replace(/\$/g,'\\$');
fs.writeFileSync(path.join(root,'app/src/main/java/dev/bing/tokenmonitor/DesktopVendorLabels.kt'),'// Fixed desktop v0.68.0 themePresets/vendorPresentation labels; source license in THIRD_PARTY_NOTICES.md.\npackage dev.bing.tokenmonitor\n\ninternal val desktopVendorLabels = mapOf(\n'+Object.entries(labels).map(([k,v])=>'    '+quoted(k)+' to '+quoted(v)+',').join('\n')+'\n)\n');
fs.writeFileSync(path.join(root,'app/src/test/resources/vendor-display-parity.json'),JSON.stringify({source:'v0.68.0/5d2db368d8313415763860d594de00e46a663418; theme/vendor/charts/provider policy',ids,labels,palettes,icons:[true,false].map(value=>({value,enabled:policy.toolIconsEnabled(value)}))},null,2)+'\n');
process.stdout.write(`Captured ${ids.length} vendor labels, ${palettes.length} palettes, ${palettes.length*models.length} model and ${palettes.length*providerIds.length} provider colors\n`);
