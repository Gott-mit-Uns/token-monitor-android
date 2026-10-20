// Fixed public desktop cases for count normalization and quota visibility.
const fs=require('fs'),path=require('path'),cp=require('child_process');
const root=path.resolve(process.argv[2]||'');
const sourceCommit=cp.execFileSync('git',['-C',root,'rev-parse','HEAD'],{encoding:'utf8'}).trim();
if(sourceCommit!=='fbbe4c1cb04e3277b73499016d432338c1d2ab4e') throw Error('Expected fixed v0.76.0');
const counts=require(path.join(root,'src/electron/renderer/homeModulePreferences'));
const quotas=require(path.join(root,'src/electron/renderer/limits/providerPresentation'));
const samples=[null,{},[],{tool:'8.9',model:-2,session:999,device:'bad'},{tool:null,model:'',session:true,device:0},{tool:50,model:1,session:2,device:7},{tool:1e100,model:-1e100,session:'NaN',device:'Infinity'}];
const windows=[
 {kind:'session',label:'Gemini 5-hour',remainingPercent:90},
 {kind:'weekly',label:'Gemini weekly',remainingPercent:95},
 {kind:'weekly',label:'Gemini 4 Argon weekly',remainingPercent:74},
 {kind:'session',label:'Claude/GPT 5-hour',remainingPercent:null,showMeter:false},
 {kind:'weekly',label:'Claude/GPT weekly',remainingPercent:0}
];
const legacy=[{kind:'weekly',label:'Unavailable pool',remainingPercent:null,showMeter:false},{kind:'weekly',label:'Real zero',remainingPercent:0},{kind:'weekly',label:'Used zero',usedPercent:0}];
const quotaCases=[windows,legacy,windows.slice(0,3),[...windows,...legacy]].map(windows=>({windows,visible:windows.map(quotas.antigravityQuotaWindowVisible),compact:quotas.limitProviderCompactWindows('antigravity',windows).map(w=>w.label)}));
const old=JSON.parse(fs.readFileSync(path.resolve(__dirname,'../app/src/test/resources/limit-display-parity.json'),'utf8'));
const compactLegacy=old.compactInputs.filter(x=>x.input.provider==='antigravity').map(x=>({input:x.input,output:quotas.limitProviderCompactWindows('antigravity',x.input.windows).map(w=>({label:w.label,value:w.value??null,resetDescription:w.resetDescription??null}))}));
const data={sourceCommit,compactLegacy,counts:samples.map(input=>({input,expected:counts.normalizeHomeModuleCounts(input)})),quotaCases};
fs.writeFileSync(path.resolve(__dirname,'../app/src/sharedTest/resources/desktop-076.json'),JSON.stringify(data,null,2)+'\n');
console.log('Captured '+data.counts.length+' count and '+quotaCases.length+' quota cases');
