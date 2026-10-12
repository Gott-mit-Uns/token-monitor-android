// Fixed public pure renderer/domain functions. No Hub, configuration or network access.
const fs=require('node:fs'),path=require('node:path'),vm=require('node:vm');
const root=path.resolve(__dirname,'..'),ref=path.resolve(root,'../work/token-monitor-reference/desktop-v0.68.0');
const subscriptions=require(path.join(ref,'src/shared/subscriptionDisplay.js'));
const text=require(path.join(ref,'src/shared/subscriptionText.js'));
const currency=require(path.join(ref,'src/shared/currency.js'));
const i18n=require(path.join(ref,'src/electron/renderer/i18n.js'));
const app=fs.readFileSync(path.join(ref,'src/electron/renderer/app.js'),'utf8');
const source=app.match(/function customPricingMeta\(ov\) \{[\s\S]*?\n\}/)?.[0];
if(!source) throw new Error('Fixed pure pricing summary function missing');
function pricingMeta(locale,input){const scope={t:(key,params)=>i18n.translate(locale,key,params)};vm.createContext(scope);vm.runInContext(source,scope);return scope.customPricingMeta(input);}
const prices=[
 '{"modelId":"standard","inputPerM":2.5,"outputPerM":15,"cacheReadPerM":0,"cacheWrite1hPerM":3.75}',
 '{"modelId":"zero","inputPerM":0,"outputPerM":0}',
 '{"modelId":"precision","inputPerM":1.23456789,"cacheReadPerM":0.01,"cacheWritePerM":2.75,"cacheWrite1hPerM":3.125}',
 '{"modelId":"tiny","inputPerM":0.0000001}',
 '{"modelId":"small","inputPerM":0.000001}',
 '{"modelId":"large","inputPerM":100000000000000000000}',
 '{"modelId":"scientific","inputPerM":1e21}',
 '{"modelId":"roundtrip","inputPerM":1e23}',
 '{"modelId":"subnormal","inputPerM":5e-324}',
 '{"modelId":"underflow","inputPerM":1e-400}',
 '{"modelId":"negative-zero","inputPerM":-0}',
 '{"modelId":"nullable","inputPerM":1,"outputPerM":null,"cacheReadPerM":""}'
].map(raw=>{const input=JSON.parse(raw);return {raw,fields:Object.fromEntries(Object.entries(input).filter(([key,v])=>key!=='modelId'&&typeof v==='number').map(([key,v])=>[key,String(v)])),meta:{en:pricingMeta('en',input),zh:pricingMeta('zh-CN',input)}};});
const base={id:'public',provider:'codex',kind:'subscription',binding:{profileName:'Public account'},planName:'Public plan',amountMinor:2000,currency:'USD',interval:'month',intervalCount:1,startDate:'2026-01-31',autoRenew:true,updatedAt:'2026-10-10T00:00:00Z'};
const rows=[
 ['2026-02-28',{}],['2026-03-01',{}],['2026-10-10',{}],['2026-02-27',{autoRenew:false}],['2026-02-28',{autoRenew:false}],
 ['2026-10-10',{interval:'year',startDate:'2024-02-29'}],['2027-03-01',{interval:'year',startDate:'2024-02-29'}],
 ['2026-10-10',{intervalCount:3}],['2026-10-10',{interval:'year',intervalCount:2}],
 ['2026-10-10',{nextRenewalOverride:'2026-10-12'}],['2026-10-10',{nextRenewalOverride:'2026-10-09'}],
 ['2026-10-10',{endDate:'2026-11-15'}],['2026-10-10',{endDate:'2026-09-30'}],
 ['2026-10-10',{startDate:'2026-11-01'}],['2026-10-10',{startDate:'2026-10-01'}],['2026-10-10',{startDate:'2026-10-10'}],
 ...['USD','TWD','HKD','CNY'].map(currency=>['2026-10-10',{currency,amountMinor:1015}]),
 ['2026-10-10',{amountMinor:9007199254740901}],
 ['2026-10-10',{kind:'topup',provider:'openrouter',currency:'HKD',topUps:[{date:'2026-09-30',amountMinor:101},{date:'2026-10-01',amountMinor:200},{date:'2026-10-12',amountMinor:300}]}]
].map(([today,overrides],index)=>{
 const input=subscriptions.normalizeSubscription({...base,...overrides,id:'public-'+index},{currencyApi:currency});
 const stop=subscriptions.coverageStopDate(input),asOf=stop&&stop<today?stop:today;
 const days=subscriptions.daysBetween(input.startDate,asOf),months=subscriptions.subscribedMonths(input,asOf);
 return {input,today,expected:{renewal:subscriptions.nextRenewalDate(input,today),coverage:subscriptions.coverageEndDate(input,today),stop,
  recorded:input.kind==='topup'?subscriptions.topUpTotalMinor({...input,topUps:input.topUps.filter(t=>t.date<=today)})/100:subscriptions.paidToDateMinor(input,today)/100,
  month:subscriptions.topUpMonthMinor(input,today)/100,amount:text.amountText(currency,input),
  elapsed:input.kind==='topup'||days===null||days<0?null:{value:months>=1?months:Math.max(0,days),months:months>=1}}};
});
const monthly=[
 {indexes:[2],rates:{}},{indexes:[4,2],rates:{}},{indexes:[5,7,8],rates:{}},
 {indexes:[16,17,18,19],rates:{}},{indexes:[16,17,18,19],rates:{TWD:30,HKD:7,CNY:7}},
 {indexes:[12],rates:{}},{indexes:[13],rates:{}},{indexes:[21],rates:{}},{indexes:[],rates:{}}
].map(({indexes,rates})=>{currency.configureRates(rates);const input=indexes.map(index=>rows[index].input);return {input,rates,today:'2026-10-10',expected:subscriptions.monthlyTotalUsd(input,currency,'2026-10-10')};});
currency.configureRates(null);
const labels=Object.fromEntries(['cacheRead','input','output','cacheWrite','cacheWrite1h'].map(key=>[key,{en:i18n.translate('en','settings.customPricing.'+key),zh:i18n.translate('zh-CN','settings.customPricing.'+key)}]));
fs.writeFileSync(path.join(root,'app/src/test/resources/shared-display-parity.json'),JSON.stringify({source:'v0.68.0/5d2db368d8313415763860d594de00e46a663418; pure pricing/subscription functions',labels,prices,subscriptions:rows,monthly},null,2)+'\n');
process.stdout.write(`Captured ${prices.length} pricing, ${rows.length} subscription and ${monthly.length} monthly total cases\n`);
