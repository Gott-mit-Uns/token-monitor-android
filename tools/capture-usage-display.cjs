// Pure public v0.68.0 display oracle; never reads Hub/configuration or contacts a service.
const fs=require('fs');const path=require('path');
const root=path.resolve(__dirname,'..');const ref=path.resolve(root,'../work/token-monitor-reference/desktop-v0.68.0');
const currency=require(path.join(ref,'src/shared/currency.js'));
const rows=require(path.join(ref,'src/electron/renderer/usageAttributionRows.js'));
const fixed=require(path.join(ref,'src/electron/renderer/fixedPeriodRanges.js'));
const tools=require(path.join(ref,'src/electron/renderer/toolDetails.js'));
const amounts=['0','0.000001','0.000005','0.01','0.1','0.8','1','1.005','1.23445','9.99994','9.999999','10','10.005','90','136.08','1234567.891234','1e20','1e21','1e23'];
const money=[];
for(const rates of [null,{TWD:31.67517,HKD:7.80269,CNY:7.123456},{TWD:0,HKD:-1,CNY:'invalid'}]) {
  currency.configureRates(rates);
  for(const code of currency.CURRENCY_CODES)for(const amount of amounts)money.push({amount,code,rates,converted:currency.convertUsd(amount,code),expected:currency.formatCurrencyFromUsd(amount,code)});
}
currency.configureRates(null);
const costLabels=[];
for(const code of currency.CURRENCY_CODES)for(const amount of ['0','0.8','10'])for(const unpriced of [0,100]) {
  const format=value=>currency.formatCurrencyFromUsd(value,code);
  costLabels.push({code,amount,unpriced,full:rows.usageCostLabel(amount,unpriced,format,value=>new Intl.NumberFormat('zh-CN').format(value),'unpriced tokens'),compact:rows.compactUsageCostLabel(amount,unpriced,format)});
}
const componentInputs=[
  {totalTokens:100,cacheReadTokens:50,cacheWriteTokens:9,outputTokens:10,unclassifiedTokens:0},
  {totalTokens:100,cacheReadTokens:50,cacheWriteTokens:9,outputTokens:10,unclassifiedTokens:20},
  {totalTokens:100,cacheReadTokens:100,outputTokens:10,unclassifiedTokens:20},
  {totalTokens:100,cacheReadTokens:20,outputTokens:100,unclassifiedTokens:10},
  {totalTokens:100,cacheReadTokens:50,outputTokens:10,unclassifiedTokens:100},
  {totalTokens:0,cacheReadTokens:0,outputTokens:0,unclassifiedTokens:0},
  {totalTokens:100},
  {totalTokens:5000000123,cacheReadTokens:4000000000,outputTokens:100000000,unclassifiedTokens:123}
];
const components=componentInputs.map(input=>({input,expected:fixed.tokenComponentBreakdown(input)}));
const attributionInputs=[
  {values:{codex:100},costs:{codex:0.8},options:{totalValue:150,totalCost:1,totalUnpricedTokens:50,unpricedTokens:{codex:20}}},
  {values:{},costs:{'cost-only':0.8},options:{totalValue:0,totalCost:0.8}},
  {values:{},costs:{},options:{totalValue:0,totalCost:0,totalUnpricedTokens:100,unpricedTokens:{'unpriced-only':100}}},
  {values:{codex:100},costs:{codex:1},options:{totalValue:100,totalCost:1.00004}},
  {values:{codex:100},costs:{codex:1},options:{totalValue:100,totalCost:1.00006}},
  {values:{codex:100},costs:{codex:1},options:{totalValue:150,totalCost:1.0000015}},
  {values:{codex:100},costs:{codex:0.375},options:{totalValue:150,totalCost:0.3750005}}
];
const attribution=attributionInputs.map(input=>{
  const all=rows.attributionRows(input.values,input.costs,input.options);
  return{...input,expected:all,visible:rows.visibleAttributionRows(all,value=>currency.formatCurrencyFromUsd(value,'USD'))};
});
const modelInputs=[
  {clients:{codex:100},clientCosts:{codex:0.8},clientUnpricedTokens:{codex:40},clientModels:{codex:{'public-model':60}},clientModelCosts:{codex:{'public-model':0.5}},clientModelUnpricedTokens:{codex:{'public-model':20}}},
  {clients:{codex:0},clientCosts:{codex:0.8},clientModels:{codex:{}},clientModelCosts:{codex:{'cost-only-model':0.8}}}
];
const models=modelInputs.map(input=>({input,expected:tools.modelRowsForTool(input,'codex')}));
const result={money,costLabels,components,attribution,models};
fs.writeFileSync(path.join(root,'app/src/test/resources/usage-display-parity.json'),JSON.stringify(result));
process.stdout.write(`Captured ${money.length} money, ${costLabels.length} labels, ${components.length} component, ${attribution.length} attribution and ${models.length} model cases.\n`);
