// Reference behavior from pinned desktop modules, evaluated with public values only.
const fs=require('fs'),path=require('path');
const root=path.resolve(__dirname,'..'),ref=path.resolve(root,'../work/token-monitor-reference/desktop-v0.68.0');
const motion=require(path.join(ref,'src/electron/motionPreference.js'));
const ranking=require(path.join(ref,'src/electron/renderer/usageAttributionRows.js'));
const motions=[];
for(const value of ['system','on','off','','invalid',' on ']) for(const system of [false,true]) motions.push({value,system,normalized:motion.normalize(value),reduced:motion.shouldReduceMotion(value,system)});
const cases=[];
for(const costs of [[0,0,0],[.1,2,1],[2,2,2],[-1,.5,0],[0,0,3]]) for(const metric of ['tokens','cost','invalid']) {
 const input=[{key:'public-low',value:100,cost:costs[0]},{key:'public-high',value:1000,cost:costs[1]},{key:'__unattributed',value:50,cost:costs[2],unattributed:true}];
 cases.push({input,metric,expected:ranking.rankRowsWithValues(input,metric).map(row=>({id:row.key,tokens:row.value,barValue:row.barValue}))});
}
const ties=[{key:'zulu',value:100,cost:2},{key:'alpha',value:100,cost:2},{key:'Zulu',value:100,cost:2}];
for(const metric of ['tokens','cost']) cases.push({input:ties,metric,expected:ranking.rankRowsWithValues(ties,metric).map(row=>({id:row.key,tokens:row.value,barValue:row.barValue}))});
fs.writeFileSync(path.join(root,'app/src/test/resources/settings-semantics-parity.json'),JSON.stringify({reference:'desktop v0.68.0 motionPreference.js and usageAttributionRows.js',motions,cases},null,2)+'\n');
console.log('Captured 12 motion decisions and 17 ranking cases from original desktop functions');
