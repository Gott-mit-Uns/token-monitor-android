// Execute the fixed desktop preview functions with public data and inert service callbacks.
const fs=require('node:fs'),path=require('node:path');
const root=path.resolve(__dirname,'..'),ref=path.resolve(root,'../work/token-monitor-reference/desktop-v0.68.0');
const text=fs.readFileSync(path.join(ref,'src/electron/renderer/app.js'),'utf8');
const funcs=['previewThemeColor','previewVendorColor'].map(name=>{const start=text.indexOf('function '+name+'(');if(start<0)throw Error(name);return text.slice(start,text.indexOf('\n}',start)+2)}).join('\n');
const themePresetsApi=require(path.join(ref,'src/electron/renderer/themePresets.js'));
const cases=[];
for(const base of [{colors:{},vendors:{}},{colors:{accent:'#112233',bg:'#303438'},vendors:{codex:'#445566'}}]) {
 for(const vendor of [false,true]) for(const key of vendor?['codex','claude','droid','default']:['accent','chart','bg','text','muted']) for(const input of ['#FF0000',' #12ab34 ','#ffffff','#000000','#abc','invalid','#ff000000']) {
  let applied=null,appearancePreview={},calls=0;
  const currentThemeOverrides=()=>({...base.colors}),currentVendorOverrides=()=>({...base.vendors});
  const applyThemeColors=value=>{applied=value;calls++},applyVendorColorOverrides=value=>{applied=value;calls++},render=()=>{};
  const window={tokenMonitor:{previewAppearance:()=>({catch:()=>{}})}};
  eval(funcs+';'+(vendor?'previewVendorColor':'previewThemeColor')+'(key,input);');
  cases.push({vendor,key,input,base,expected:applied,calls});
 }
}
const fixture={source:'v0.68.0/5d2db368d8313415763860d594de00e46a663418/app.js; actual previewThemeColor/previewVendorColor',cases};
fs.writeFileSync(path.join(root,'app/src/test/resources/appearance-preview-parity.json'),JSON.stringify(fixture,null,2)+'\n');
const out=path.join(root,'evidence/appearance-preview/source');fs.mkdirSync(out,{recursive:true});
fs.writeFileSync(path.join(out,'source-semantics.txt'),fixture.source+'\n126 public cases; saved baseline maps retained; no network/storage callbacks\n');
process.stdout.write('126 original desktop preview cases generated\n');
