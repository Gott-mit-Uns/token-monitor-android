// Public pure-function oracle from the immutable desktop reference. No application state.
const fs=require('node:fs'),path=require('node:path');
const root=path.resolve(__dirname,'..'),ref=path.resolve(root,'../work/token-monitor-reference/desktop-v0.68.0');
const source=name=>require(path.join(ref,name));
const order=source('src/electron/renderer/limits/providerOrder.js'),usage=source('src/shared/limits/usageItems.js'),labels=source('src/shared/limits/windowLabels.js');
const providers=source('src/shared/limits/providers.js').LIMIT_PROVIDER_CATALOG;
const selections=[[],['codex','claude'],[' MIMO ','codex','CODEx','future','zed'],providers.map(p=>p.id).reverse()];
const orders=selections.map(input=>({input,selection:order.normalizeLimitProviderSelection(input,providers),order:order.normalizeLimitProviderOrder(input,providers),moves:providers.flatMap(p=>[-1,0,1].map(direction=>({id:p.id,direction,value:order.moveLimitProvider(input,providers,p.id,direction===-1?'up':direction===1?'down':'invalid').split(',')})))}));
const keys=['credits',' spend ','resets','Credits','', '[]','["session","","",false]', '["weekly","模型 A","",true]','["id"," weekly.a ","weekly","",true,10080]','["id","a","session","credits",false,null]','["id","a","session","",false,1.25]','["id","a","session","",false,0]','["id","a","session","",false,"5"]','["session",null,"",false]','["session","","","false"]','["","","",false]',JSON.stringify(['billing','a'.repeat(373),'',false]),JSON.stringify(['billing','a'.repeat(401),'',false])];
const items=keys.map(input=>({input,normalized:usage.normalizeLimitProviderHiddenItems({codex:[input]}).codex?.[0]||null,labels:Object.fromEntries(providers.map(p=>[p.id,usage.usageItemFallbackLabel(p.id,usage.normalizeLimitProviderHiddenItems({codex:[input]}).codex?.[0]||'')]))}));
const windows=[{kind:'billing',metric:'spend'},{kind:'billing',label:'Usage credits'},{kind:'billing',label:'Credits'},{kind:'weekly',label:'Changed title',limitId:'backend-a',windowMinutes:10080,additional:true},{kind:'session'},{kind:'session',metric:' Credits ',limitId:'a'}];
const windowItems=providers.flatMap(p=>windows.map(window=>({provider:p.id,window,key:usage.limitUsageItemId(window,p.id)})));
const rawMaps=[{}, {codex:['credits','credits','invalid','resets']},{' CODEX ':keys,unknown:['credits'],mimo:[]},{codex:Array.from({length:70},(_,i)=>JSON.stringify(['session','public-'+i,'',false]))}];
const hidden=rawMaps.map(input=>({input,normalized:usage.normalizeLimitProviderHiddenItems(input)}));
const accounts=[
 [{accountName:'Public · MiMo abc1234'},{accountName:'Second · MiMo ABC1234'},{accountName:'Public · MiMo abc5678'}],
 [{accountEmail:'Public@example.test',accountLabel:'Console'},{accountEmail:'public@example.test',accountLabel:'Desktop Membership'},{accountEmail:'second@example.test'}],
 [{accountName:'Public profile'},{accountName:'public PROFILE'},{accountName:'Second profile'}],
 [{accountKey:'public-a'},{accountKey:'public-a'},{},{}],
 [{email:'public@example.test'},{email:'public@example.test'}]
].map(input=>({input,groups:labels.mimoAccountGroups(input).map(group=>group.map(row=>input.indexOf(row)))}));
const file=path.join(root,'app/src/test/resources/limit-preferences-parity.json');fs.writeFileSync(file,JSON.stringify({source:'v0.68.0/5d2db368d8313415763860d594de00e46a663418; original providerOrder, usageItems and windowLabels modules',orders,items,windowItems,hidden,accounts},null,2)+'\n');
process.stdout.write('Captured desktop provider ordering, hidden item identity and logical account cases\n');
