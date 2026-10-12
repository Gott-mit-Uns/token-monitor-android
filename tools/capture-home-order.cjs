// Execute pinned desktop preference functions with public order/membership cases.
const fs=require('fs'),path=require('path');
const root=path.resolve(__dirname,'..'),ref=path.resolve(root,'../work/token-monitor-reference/desktop-v0.68.0');
const api=require(path.join(ref,'src/electron/renderer/homeModulePreferences.js'));
const source=['limits','tool','model','session','device','trends'],map={limits:'limits',tool:'tools',model:'models',session:'sessions',device:'devices',trends:'activity'};
const options=source.map(id=>({id}));const native=list=>list.map(id=>map[id]);
const cases=[];
for(let shift=0;shift<6;shift++)for(const reverse of [false,true]) {
 const order=source.slice(shift).concat(source.slice(0,shift));if(reverse)order.reverse();
 for(const mask of [0,1,3,15,31,63]) {
  const hidden=new Set(api.normalizeHiddenHomeModules(source.filter((_,i)=>mask&(1<<i)),options).split(',').filter(Boolean));
  const input={order:native(order),visible:native(order.filter(id=>!hidden.has(id)))};
  for(const id of source) {
   const changed=new Set(hidden);if(changed.has(id))changed.delete(id);else changed.add(id);
   const normalized=new Set(api.normalizeHiddenHomeModules([...changed],options).split(',').filter(Boolean));
   cases.push({input,action:'toggle',id:map[id],expectedOrder:native(order),expectedVisible:native(order.filter(id=>!normalized.has(id)))});
   for(const direction of ['up','down']) {
    const moved=api.moveHomeModuleOrder(order,options,id,direction).split(',');
    cases.push({input,action:'move',id:map[id],direction:direction==='up'?-1:1,expectedOrder:native(moved),expectedVisible:native(moved.filter(id=>!hidden.has(id)))});
   }
  }
  cases.push({input,action:'reset',expectedOrder:native(source),expectedVisible:native(source.filter(id=>!hidden.has(id)))});
  cases.push({input,action:'all',expectedOrder:native(order),expectedVisible:native(order)});
 }
}
const defaults=api.defaultHomeModulePreferences();const hidden=new Set(defaults.hiddenHomeModules.split(','));
const data={source:'v0.68.0/5d2db368d8313415763860d594de00e46a663418; original homeModulePreferences.js; app.js handlers and main.js defaults confirmed',defaults:{order:native(defaults.homeModuleOrder.split(',')),visible:native(source.filter(id=>!hidden.has(id)))},cases};
const out=path.join(root,'app/src/test/resources/home-order-parity.json');fs.writeFileSync(out,JSON.stringify(data)+'\n');
console.log('Captured '+cases.length+' original order/visibility action cases and source defaults');
