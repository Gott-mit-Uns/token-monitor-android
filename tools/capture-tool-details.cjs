// Public, fixed v0.68.0 pure toolDetails oracle; no Hub/configuration or network.
const fs=require('fs'),path=require('path');
const root=path.resolve(__dirname,'..'),ref=path.resolve(root,'../work/token-monitor-reference/desktop-v0.68.0');
const tools=require(path.join(ref,'src/electron/renderer/toolDetails.js'));
const fixed=require(path.join(ref,'src/electron/renderer/fixedPeriodRanges.js'));
const currency=require(path.join(ref,'src/shared/currency.js'));
currency.configureRates(null);
const labels=[0,-1,0.000001,0.29,0.499999,0.99,1,1.49,1.5,95.2,99.49,99.5,100,150,1e100,NaN,Infinity,-Infinity].map(value=>({kind:Number.isFinite(value)?'finite':Number.isNaN(value)?'nan':value>0?'infinity':'negative-infinity',value:Number.isFinite(value)?value:null,expected:tools.detailPercentLabel(value)}));
const inputs=[
 {totalTokens:1000,cacheReadTokens:1,outputTokens:0,unclassifiedTokens:0},
 {totalTokens:1000,cacheReadTokens:999,outputTokens:0,unclassifiedTokens:0},
 {totalTokens:100,cacheReadTokens:50,outputTokens:10,unclassifiedTokens:20},
 {totalTokens:100,cacheReadTokens:100,outputTokens:10,unclassifiedTokens:20},
 {totalTokens:100,cacheReadTokens:0,outputTokens:100,unclassifiedTokens:0},
 {totalTokens:100,cacheReadTokens:0,outputTokens:0,unclassifiedTokens:100},
 {totalTokens:0,cacheReadTokens:0,outputTokens:0,unclassifiedTokens:0},
 {totalTokens:5000000123,cacheReadTokens:4000000000,outputTokens:100000000,unclassifiedTokens:123}
];
const inputsExpected=inputs.map(input=>{const parts=fixed.tokenComponentBreakdown(input),percent=tools.tokenInputPercentages(parts);return {input,parts,percent,labels:{hit:tools.detailPercentLabel(percent.hit),miss:tools.detailPercentLabel(percent.miss)}}});
const periods=[
 {clients:{codex:50},clientCosts:{codex:1}},
 {clients:{codex:50},clientCosts:{codex:1},clientModels:{codex:{}}},
 {clients:{codex:50},clientModels:{codex:null}},
 {clients:{codex:0},clientCosts:{codex:2},clientModelCosts:{codex:{'cost-only':2}}},
 {clients:{codex:50},clientModels:{codex:{negative:-1,valid:75}}},
 {clients:{codex:1000},clientCosts:{codex:2},clientModels:{codex:{shared:250,'public-gpt':700}},clientModelCosts:{codex:{shared:0.25,'public-gpt':1.5}}},
 {clients:{codex:100},clientUnpricedTokens:{codex:80},clientModels:{codex:{unknown:60}},clientModelUnpricedTokens:{codex:{unknown:60}}},
 {clients:{codex:10000},clientModels:{codex:{tiny:1,major:9999}}},
 {clients:{codex:100},clientCosts:{codex:1.000001},clientModels:{codex:{public:100}},clientModelCosts:{codex:{public:1}}},
 {clients:{codex:100},clientCosts:{codex:1.01},clientModels:{codex:{public:100}},clientModelCosts:{codex:{public:1}}},
 {clients:{codex:100},clientModels:{codex:{zulu:50,alpha:50}},clientModelCosts:{codex:{zulu:0.6,alpha:0.4}}},
 {clients:{codex:100},clientModels:{codex:{zulu:50,alpha:50}},clientModelCosts:{codex:{zulu:0.4,alpha:0.4}}}
];
const models=periods.map(input=>({input,expected:tools.visibleModelRowsForTool(input,'codex',value=>currency.formatCurrencyFromUsd(value,'USD')).map(row=>({...row,percentLabel:row.value>0?tools.detailPercentLabel(row.percent):null}))}));
fs.writeFileSync(path.join(root,'app/src/test/resources/tool-detail-parity.json'),JSON.stringify({labels,inputs:inputsExpected,models}));
process.stdout.write(`Captured ${labels.length} percentage labels, ${inputsExpected.length} input shares, ${models.length} model detail cases.\n`);
