// Read-only fixed reference modules and public synthetic cases. No live app or Hub data.
const fs=require('node:fs'),path=require('node:path');
const root=path.resolve(__dirname,'..'),ref=path.resolve(root,'../work/token-monitor-reference/desktop-v0.68.0');
const presentation=require(path.join(ref,'src/electron/renderer/limits/providerPresentation.js'));
const i18n=require(path.join(ref,'src/electron/renderer/i18n.js'));
const catalog=require(path.join(ref,'src/shared/limits/providers.js')).LIMIT_PROVIDER_CATALOG;
const app=fs.readFileSync(path.join(ref,'src/electron/renderer/app.js'),'utf8');
const keys=Object.fromEntries([...app.slice(app.indexOf('const LIMIT_CAPABILITY_TAG_KEYS = {'),app.indexOf('const deviceAccent')).matchAll(/(?:'([^']+)'|(\w+)): '([^']+)'/g)].map(m=>[m[1]||m[2],m[3]]));
keys['Open Antigravity to verify']='settings.antigravity.verificationRequired';
keys['Encrypted by app']='settings.limits.status.appSessionEncrypted';
keys['From {device}']='settings.limits.device.from';
const wording=Object.fromEntries(['en','zh-CN'].map(locale=>[locale,Object.fromEntries(Object.entries(keys).map(([label,key])=>[label,i18n.translate(locale,key,{device:'{device}'})]))]));
const statuses=['','ok','disabled','notConfigured','noSyncedData','unauthorized','rateLimited','sourceRateLimited','unavailable','error','unknown'];
const cases=catalog.flatMap(({id:provider})=>statuses.flatMap(status=>['api','web','rpc','oauth'].flatMap(source=>[false,true].map(stale=>{
 const input={provider,status,source,stale,windows:[]};return {input,output:presentation.limitProviderStatusLabel(input),source:presentation.limitProviderSourceLabel(input)};
}))));
for(const [provider,actionRequired] of [['antigravity','accountVerification'],['workbuddy','appSessionEncrypted']])for(const stale of [false,true]) {
 const input={provider,status:'ok',actionRequired,stale,windows:[]};cases.push({input,output:presentation.limitProviderStatusLabel(input),source:presentation.limitProviderSourceLabel(input)});
}
const selections=[
 [{provider:'codex',status:'ok',accountName:'Public first'},{provider:'codex',status:'unauthorized',accountName:'Public last'}],
 [{provider:'mimo',status:'error',accountLabel:'Console'},{provider:'mimo',status:'ok',accountLabel:'Membership'}],
 [{provider:'mimo',status:'ok',accountLabel:'Console'},{provider:'mimo',status:'ok',accountLabel:'Membership'}],
 [{provider:'mimo',status:'error',accountLabel:'Membership'},{provider:'mimo',status:'unauthorized',accountLabel:'Console'}],
 [{provider:'mimo',status:'error',accountLabel:'Membership'}]
].map(input=>({input,index:input.indexOf(presentation.limitProviderSettingsRecord(input,input[0].provider))}));
fs.writeFileSync(path.join(root,'app/src/test/resources/limit-settings-parity.json'),JSON.stringify({reference:'v0.68.0',cases,selections,wording},null,2)+'\n');
const quote=value=>JSON.stringify(value).replace(/\$/g,'\\$');
fs.writeFileSync(path.join(root,'app/src/main/java/dev/bing/tokenmonitor/DesktopLimitSettingsWording.kt'),
 '// Generated from pinned desktop v0.68.0 i18n by tools/capture-limit-settings.cjs.\npackage dev.bing.tokenmonitor\n\ninternal val desktopLimitSettingsWording=mapOf(\n'+
 Object.entries(wording).map(([locale,entries])=>'    '+quote(locale)+' to mapOf(\n'+Object.entries(entries).map(([label,value])=>'        '+quote(label)+' to '+quote(value)).join(',\n')+'\n    )').join(',\n')+'\n)\n');
process.stdout.write('Pinned provider status/source cases: '+cases.length+'; record-selection cases: '+selections.length+'\n');
