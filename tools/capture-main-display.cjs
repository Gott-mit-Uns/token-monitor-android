// Actual immutable desktop functions; public counters only, no user configuration.
const fs=require('fs'),path=require('path');
const root=path.resolve(__dirname,'..'),ref=path.resolve(root,'../work/token-monitor-reference/desktop-v0.68.0');
const rates=require(path.join(ref,'src/electron/renderer/tokenRatePresentation.js'));
const compact=require(path.join(ref,'src/shared/compactTokens.js'));
const views=require(path.join(ref,'src/electron/renderer/viewDisplayPreferences.js'));
const p=(tokens,output,duration,models={})=>({timedTokens:tokens,timedOutputTokens:output,timedDurationMs:duration,modelThroughput:models});
const e=(id,period,name='Public '+id)=>({id,name,period});
const t=(at,entries)=>({at,entries});
const a=p(100,40,1000,{'public-alpha':p(100,40,1000)}),b=p(200,80,2000,{'public-beta':p(200,80,2000)});
const specs=[
 [t(0,[e('a',a),e('b',b)]),t(800,[e('a',p(120,50,1200,{'public-alpha':p(120,50,1200)})),e('b',b)]),t(1800,[e('a',p(120,50,1200,{'public-alpha':p(120,50,1200)})),e('b',p(250,105,2500,{'public-beta':p(250,105,2500)}))]),t(2000,[e('a',p(120,50,1200,{'public-alpha':p(120,50,1200)})),e('b',p(250,105,2500,{'public-beta':p(250,105,2500)}))]),t(8800,null),t(9800,null),t(181799,null),t(181800,null)],
 [t(0,[e('a',a)]),t(100,[e('a',p(101,41,1001))]),t(200,[e('a',p(102,42,1001))]),t(300,[e('a',p(103,43,1002))]),t(301,[e('a',p(1,1,1))]),t(400,[e('a',p(2,2,2))]),t(500,[])],
 [t(0,[e('a',a)]),t(100,[e('a',p(120,50,1200))]),t(200,[e('a',{...p(120,50,1200),capabilities:{throughput:false}})]),t(300,[e('a',p(130,60,1300))]),t(400,[e('a',p(140,70,1400))])],
 [t(0,[e('a',{timedTokens:100,timedOutputTokens:40,timedDurationMs:1000})]),t(100,[e('a',p(120,50,1200,{'public-alpha':p(120,50,1200)}))]),t(200,[e('a',p(140,60,1400,{'public-alpha':p(140,60,1400),'public-retro':p(80,30,800)}))]),t(300,[e('a',p(160,70,1600,{'public-alpha':p(150,65,1500),'public-beta':p(10,5,100)}))])],
 [t(0,[e(' a ',a),e('a',b),e('',a)]),t(100,[e('a',p(120,50,1200),'Public renamed'),e('a',p(150,60,1500)),e('new',a)]),t(200,[e('a',p(120,50,1200)),e('new',p(120,50,1200))]),t(8100,null),t(8200,null)],
 [t(0,[e('a',a),e('b',b)]),t(100,[e('a',p(120,50,1200)),e('b',p(250,105,2500))]),t(200,[e('b',p(250,105,2500))]),t(300,[]),t(8000,null),t(8100,null)],
 [t(0,[e('a',p(0,0,0))]),t(100,[e('a',p(0,0,1000))]),t(200,[e('a',p(100,0,2000))]),t(300,[e('a',p(100,10,2000))]),t(400,[e('a',p(100,10,3000))])],
 [t(0,[e('a',a)]),t(100,[e('a',{timedTokens:120,timedOutputTokens:50})]),t(200,[e('a',p(130,60,1300))]),t(300,[e('a',p(140,70,1400))])],
 [t(0,[e('a',p(0,0,0))]),t(100,[e('a',p(1e15,1e14,0.0001))]),t(101,null)],
 [t(0,[e('a',p(0,0,0))]),t(100,[e('a',p(0.0001,0.00001,1000,{'public-alpha':p(0.0001,0.00001,1000)}))]),t(8100,null)],
 [t(0,[e('a',a)]),t(100,[e('a',p(120,50,1200))]),{at:200,reset:[e('a',p(120,50,1200))]},t(300,[e('a',p(130,60,1300))])],
 [t(0,[e('a',a),e('b',b)]),t(100,[e('a',p(120,50,1200)),e('b',p(250,105,2500))]),t(200,[e('a',p(1,1,1)),e('b',p(250,105,2500))]),t(300,null)]
];
const scenarios=specs.map(events=>{let now=0;const tracker=rates.createLiveTokenRateGroupTracker({now:()=>now});return {events:events.map(event=>{now=event.at;if(event.reset) tracker.reset(event.reset);else if(event.entries) tracker.observe(event.entries);return {...event,expected:tracker.getSample()};})};});
const formats=[];for(const locale of ['en','zh-CN','zh-TW','zh','ja','ko','pt-BR']) for(const units of ['western','localized']) for(const value of [0,1,999,999.49,999.5,1000,9999,10000,999950,999999,1000000,99999999,100000000,999950000,1000000000,5000000123,9007199254740991]) formats.push({locale,units,value,expected:compact.formatCompactTokens(value,units,locale),threshold:compact.compactTokenUnitThreshold(units,locale)});
const liveFormats=[];for(const locale of ['en','zh-CN']) for(const units of ['western','localized']) for(const value of [0,0.0001,0.05,0.0999,0.1,0.15,0.94,0.95,1.49,1.5,999.5,10000,1e12]) {
 const expected=value>0&&value<0.1?'<0.1':value>0&&value<1?value.toLocaleString(locale,{maximumFractionDigits:1}):compact.formatCompactTokens(value,units,locale);liveFormats.push({locale,units,value,expected});
}
const appSource=fs.readFileSync(path.join(ref,'src/electron/renderer/app.js'),'utf8');
const definition=appSource.match(/const VIEW_DISPLAY_OPTIONS = \[([\s\S]*?)\];/);
if(!definition) throw new Error('Original main-view definition not found');
const sourceIds=[...definition[1].matchAll(/id: '([^']+)'/g)].map(m=>m[1]);
if(sourceIds.join(',')!=='home,limits,tool,model,project,session,device,trends,status') throw new Error('Unexpected original main-view catalogue');
const nativeIds={tool:'tools',model:'models',project:'projects',session:'sessions',device:'devices'};
const mapped=id=>nativeIds[id]||id;
const nativeToSource=id=>Object.keys(nativeIds).find(key=>nativeIds[key]===id)||id;
const supported=sourceIds.filter(id=>id!=='status'),ids=supported.map(mapped);
const viewCases=[];for(const order of [[],['limits'],['models','home','limits'],['unknown','MODELS','models'],ids.slice().reverse(),['pricing','sessions','subscriptions','home']]) for(const hidden of [[],['home'],['limits','projects'],ids,['subscriptions','pricing']]) {
 const sourceOrder=order.map(id=>nativeToSource(id.trim().toLowerCase())),sourceHidden=hidden.map(nativeToSource);
 const options={views:supported.map(id=>({id})),orderValue:sourceOrder,hiddenValue:sourceHidden};
 const csv=value=>value.split(',').filter(Boolean).map(mapped).join(',');
 viewCases.push({order,hidden,normalized:views.normalizeViewDisplayOrder(sourceOrder,options.views).map(mapped),hiddenNormalized:csv(views.normalizeHiddenViews(sourceHidden,options.views)),visible:views.visibleViewOrder(options).map(mapped),preferred:mapped(views.preferredViewId({...options,currentId:'home'})),move:csv(views.moveViewDisplayOrder(sourceOrder,options.views,'model','up'))});
}
const data={source:'desktop v0.68.0 immutable original compactTokens/tokenRatePresentation/viewDisplayPreferences',sourceIds,supportedIds:ids,scenarios,formats,liveFormats,viewCases};
const out=path.join(root,'app/src/test/resources/main-display-parity.json');fs.writeFileSync(out,JSON.stringify(data,null,2)+'\n');
console.log(`Original ${scenarios.length} rate streams, ${formats.length} compact formats, ${liveFormats.length} live formats and ${viewCases.length} view preferences`);
