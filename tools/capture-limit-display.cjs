// Fixed public desktop pure modules. No desktop settings, real Hub, or network reads.
const fs=require('node:fs'),path=require('node:path'),vm=require('node:vm');
const root=path.resolve(__dirname,'..'),ref=path.resolve(root,'../work/token-monitor-reference/desktop-v0.68.0');
const text=require(path.join(ref,'src/shared/limits/windowText.js'));
const balance=require(path.join(ref,'src/shared/limits/balanceDisplay.js'));
const mode=require(path.join(ref,'src/electron/renderer/limits/displayMode.js'));
const identity=require(path.join(ref,'src/electron/renderer/accountIdentity.js'));
const presentation=require(path.join(ref,'src/electron/renderer/limits/providerPresentation.js'));
const attribution=require(path.join(ref,'src/electron/renderer/usageAttributionRows.js'));
const windowsSource=fs.readFileSync(path.join(ref,'src/electron/renderer/limits/windowsView.js'),'utf8');
// Extract complete fixed functions by balanced braces. These functions only resolve labels.
function fn(name) {
 const start=windowsSource.indexOf('function '+name+'(');if(start<0) throw Error(name);
 const opening=windowsSource.indexOf('{',start);let depth=0;
 for(let i=opening;i<windowsSource.length;i++) {if(windowsSource[i]==='{') depth++;if(windowsSource[i]==='}' && --depth===0) return windowsSource.slice(start,i+1);}
 throw Error('Unclosed '+name);
}
const scope={accountIdentity:identity,settings:()=>({maskLimitAccountEmails:scope.mask}),mimoProductLabel:require(path.join(ref,'src/shared/limits/windowLabels.js')).mimoProductLabel,t:()=> 'Environment'};
vm.createContext(scope);
vm.runInContext(['limitAccountEmailsMasked','legacyOpencodeProfileLabel','limitAccountDefaultTitle','codexAccountTitle','opencodeAccountTitle','namedApiAccountTitle','volcenginePlanAccountTitle','mimoAccountTitle','limitAccountTitle'].map(fn).join('\n')+'\n'+windowsSource.match(/const LIMIT_ACCOUNT_TITLES = \{[\s\S]*?\n  \};/)[0],scope);
const windowInputs=[
 {provider:'codex',kind:'weekly',remainingPercent:75,usedPercent:99},
 {provider:'claude',kind:'session',remainingPercent:66.5},
 {provider:'cursor',kind:'billing',metric:'spend',used:12.4,limit:20,currency:'USD',remainingPercent:38},
 {provider:'cursor',kind:'billing',metric:'spend',used:12.4,currency:'USD'},
 {provider:'commandcode',kind:'billing',metric:'credits',remaining:47.42,limit:70,currency:'USD',remainingPercent:68},
 {provider:'commandcode',kind:'session',remaining:13.9,limit:14,currency:'USD',remainingPercent:99},
 {provider:'kiro',kind:'billing',used:120,limit:500,remainingPercent:76},
 {provider:'kiro',kind:'billing',used:12.5,remaining:3.2,currency:'USD',showMeter:false},
 {provider:'kiro',kind:'billing',used:12.5,showMeter:false},
 {provider:'qoder',kind:'billing',used:15.375,limit:100,remainingPercent:84.625},
 {provider:'qoder',kind:'session',used:20,limit:100,remainingPercent:80},
 {provider:'zed',kind:'billing',limitId:'zed.edit-predictions',used:25,limit:100,remainingPercent:75},
 {provider:'zed',kind:'billing',used:2.005,limit:20,currency:'USD',remainingPercent:90},
 {provider:'zai',kind:'daily',remaining:124000000,limit:305000000,remainingPercent:40.7},
 {provider:'zaiteam',kind:'billing',limitId:'pool',remaining:120,limit:500,remainingPercent:24},
 {provider:'zai',kind:'billing',remaining:120,limit:500,detail:'not a token pool'},
 {provider:'kimi',kind:'billing',remainingPercent:82,detail:'Kimi 12% · Code 6%'},
 {provider:'kimi',kind:'weekly',remainingPercent:82,detail:'not a billing detail'},
 {provider:'openrouter',kind:'billing',metric:'credits',remaining:680,currency:'CREDITS',balance:{amount:680,monthSpend:320}},
 {provider:'openrouter',kind:'balance',metric:'credits',remaining:7.5,currency:'EUR'},
 {provider:'deepseek',kind:'billing',metric:'credits',remaining:102345.675,currency:'CNY',usedPercent:10,remainingPercent:70},
 {provider:'thirdparty',kind:'billing',metric:'credits',currency:'USD',balance:{amount:12,monthSpend:3}},
 {provider:'thirdparty',kind:'billing',metric:'credits',remaining:0,currency:'USD',balance:{monthSpend:0}},
 {provider:'thirdparty',kind:'billing',metric:'credits',remaining:-2,currency:'USD'},
 {provider:'openrouter',kind:'billing',metric:'credits',detail:'unlimited',showMeter:false},
 {provider:'unknown-provider',kind:'billing',remaining:12.25,detail:'opaque supplied note'},
];
const windowCases=windowInputs.flatMap(input=>[false,true].map(showUsed=>{
 const {provider,balance:funds,...window}=input;
 const providerRow={provider,balance:funds};
 const output=text.limitWindowText(providerRow,window,{showLimitUsed:showUsed,formatCompact:n=>Number(n.toFixed(2)).toString()});
 return {input,showUsed,output,percent:Number.isFinite(window.remainingPercent)?mode.limitFillPercent(window.remainingPercent,window.usedPercent,showUsed):null,
  creditsPercent:window.metric==='credits'?balance.creditsMeterPercent(providerRow,window):null,
  creditsMoney:window.metric==='credits' && balance.creditsAmount(providerRow,window)!==null?balance.formatMoney(balance.creditsAmount(providerRow,window),balance.creditsCurrency(providerRow,window)):null};
}));
const accountSets=[
 [{provider:'codex',accountEmail:'javis@example.com',accountLabel:'Plus',workspaceKind:'personal',accountKey:'email|private-account'}],
 [{provider:'codex',accountLabel:'Plus'},{provider:'codex',accountLabel:'Plus'}],
 [{provider:'codex',accountEmail:'javis@example.com',accountName:'Workspace',accountKey:'sha256:abcdef01'},{provider:'codex',accountEmail:'jonas@example.com',accountName:'Workspace',accountKey:'sha256:abcdef02'}],
 [{provider:'claude',accountEmail:'javis@example.com',accountName:'Team A',accountKey:'javis@example.com|team-a'},{provider:'claude',accountEmail:'jonas@example.com',accountName:'Team B',accountKey:'jonas@example.com|team-b'}],
 [{provider:'claude',accountEmail:'same@example.com',accountName:'Team',accountKey:'abc'},{provider:'claude',accountEmail:'same@example.com',accountName:'Team',accountKey:'abc'}],
 [{provider:'codex',accountEmail:'one@example.com',workspaceLabel:'Research',accountKey:'one'},{provider:'codex',accountEmail:'one@example.com',workspaceLabel:'Personal',accountKey:'two'}],
 [{provider:'opencode',accountLabel:'Go'},{provider:'opencode',accountLabel:'Work profile'},{provider:'opencode',accountName:'Custom',accountLabel:'Zen'}],
 [{provider:'openrouter',accountName:'environment'},{provider:'thirdparty',accountName:'Relay'}],
 [{provider:'volcengine',accountLabel:'Pro'}],
 [{provider:'mimo',accountEmail:'javis@example.com',accountLabel:'Console'},{provider:'mimo',accountEmail:'javis@example.com',accountLabel:'Desktop Membership'}],
 [{provider:'mimo',accountLabel:'Legacy plan'}],
 [{provider:'cursor',accountEmail:'a@example.com',accountLabel:'a@example.com'}]
];
const accounts=accountSets.flatMap(input=>[false,true].map(mask=>{scope.mask=mask;return {input,mask,output:input.map((row,index)=>scope.limitAccountTitle(row.provider,row,index,input))};}));
const periods=[{models:{unknown:100},clientModels:{codex:{unknown:100}}},{models:{unknown:100},clientModels:{codex:{unknown:100},claude:{unknown:0}}},{models:{unknown:100},clientModels:{codex:{unknown:100},claude:{unknown:1}}},{models:{unknown:100},clientModels:{codex:{unknown:90}}},{models:{unknown:100},clientModels:{codex:{unknown:100},claude:{unknown:'bad'}}},{models:{unknown:0},clientModels:{codex:{unknown:0}}},{models:{unknown:100}},{models:{unknown:100},clientModels:{codex:{unknown:100},claude:{unknown:-1}}}].map(input=>({input,source:attribution.unknownModelSource(input)}));
const compactInputs=[
 {provider:'codex',windows:[{kind:'session',label:'Session',remainingPercent:75},{kind:'weekly',label:'Weekly',remainingPercent:65},{kind:'weekly',label:'Reserve',remainingPercent:90,additional:true}]},
 {provider:'antigravity',windows:[{kind:'session',label:'Gemini 5-hour',remainingPercent:75},{kind:'weekly',label:'Gemini weekly',remainingPercent:50},{kind:'session',label:'Claude 5-hour',remainingPercent:55},{kind:'weekly',label:'Claude weekly',remainingPercent:10},{kind:'session',label:'Other 5-hour',remainingPercent:80}]},
 {provider:'antigravity',windows:[{kind:'session',label:'Gemini 5-hour'},{kind:'weekly',label:'Gemini weekly',remainingPercent:50}]},
 {provider:'antigravity',windows:[{kind:'session',label:'Unrecognized'},{kind:'weekly',label:'Gemini weekly',remainingPercent:50}]},
 {provider:'zed',windows:[{kind:'billing',label:'Edit predictions',limitId:'zed.edit-predictions',detail:'unlimited',resetDescription:'old note'},{kind:'billing',label:'Tokens'}]},
 {provider:'qoder',windows:[{kind:'session',label:'Session'},{kind:'weekly',label:'Weekly'},{kind:'billing',label:'Monthly'}]}
].map(input=>({input,output:presentation.limitProviderCompactWindows(input,input.windows).map(row=>({label:row.label,value:row.value??null,resetDescription:row.resetDescription??null}))}));
const providerIds=['codex','grok','volcengine','trae','claude','opencode','unknown-provider'];
const sources=['rpc','oauth','cli','web','api','local','unknown'];
const details=['','app','cli','managed','unknown'];
const sourceCases=providerIds.flatMap(provider=>sources.flatMap(source=>details.map(sourceDetail=>({provider,source,sourceDetail,label:presentation.limitProviderSourceLabel({provider,source,sourceDetail})}))));
const labels={};for(const item of sourceCases) if(item.label) labels[`${item.provider}:${item.source}:${item.sourceDetail}`]=item.label;
for(const source of sources) {const label=presentation.limitProviderSourceLabel({provider:'unknown-provider',source});if(label) labels[`*:${source}:`]=label;}
const quote=s=>JSON.stringify(s).replace(/\$/g,'\\$');
fs.writeFileSync(path.join(root,'app/src/main/java/dev/bing/tokenmonitor/DesktopLimitSourceLabels.kt'),'// Fixed desktop providerPresentation.js; source license in THIRD_PARTY_NOTICES.md.\npackage dev.bing.tokenmonitor\n\ninternal val desktopLimitSourceLabels=mapOf(\n'+Object.entries(labels).map(([key,value])=>'    '+quote(key)+' to '+quote(value)+',').join('\n')+'\n)\n');
fs.writeFileSync(path.join(root,'app/src/test/resources/limit-display-parity.json'),JSON.stringify({source:'v0.68.0/5d2db368d8313415763860d594de00e46a663418; limits displayMode/windowText/balanceDisplay/accountIdentity/providerPresentation/usageAttributionRows',windowCases,accounts,periods,sourceCases,compactInputs},null,2)+'\n');
process.stdout.write(`Captured ${windowCases.length} window cases, ${accounts.length} account sets, ${periods.length} unknown-model cases, ${sourceCases.length} source labels, ${compactInputs.length} compact quota sets\n`);
