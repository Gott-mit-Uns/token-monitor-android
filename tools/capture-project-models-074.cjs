const fs=require('fs');
const path=require('path');
if(!process.argv[2]) throw new Error('Pass the fixed desktop v0.74.0 source directory');
const base=path.resolve(process.argv[2]);
const output=path.resolve(__dirname,'../app/src/test/resources/desktop-074-project-models.json');
// Refuse a floating or different producer/renderer while retaining the fixed fixture identity.
const crypto=require('crypto');
const pins={"src/electron/renderer/projectRows.js": "bd11523c866003aaf7c17d97e1667f5a551ae34c0d4f4356517c8c78a623cb20", "src/shared/usage.js": "5f0a6a130d40d9e0a165064695d27569c803ac4b6e99f930b8ee0066a3407ece", "src/shared/history.js": "4dc6bfb0ba97136cd3626b4156ab0e4786dd00d4d794f4f528daae0f9d9f013f"};
for(const [file,sha] of Object.entries(pins)) {
  if(crypto.createHash('sha256').update(fs.readFileSync(path.join(base,file))).digest('hex')!==sha) throw new Error('Not the fixed v0.74.0 source: '+file);
}

const {projectRowsForPeriod}=require(base+'/src/electron/renderer/projectRows');
const {projectRollupFromSessions,aggregateDevices}=require(base+'/src/shared/usage');
const cases=[
 {id:'rollup',period:{projects:{a:{label:'App',tokens:100,costUsd:1,clients:{codex:100},models:{alpha:60,beta:20}},b:{label:'APP',tokens:50,costUsd:2,clients:{claude:50},models:{beta:30}}}}},
 {id:'old-rollup-no-models',period:{projects:{a:{label:'App',tokens:100,clients:{codex:100}}},sessions:{s:{client:'codex',projectLabel:'App',totalTokens:100,models:{alpha:100}}}}},
 {id:'bounded',period:{projects:{a:{label:'App',tokens:100,models:{alpha:90,beta:90,negative:-1,zero:0}},b:{label:'App',tokens:50,models:{beta:100}}}}},
 {id:'sessions',period:{sessions:{s:{client:'codex',projectLabel:'App',totalTokens:100,models:{alpha:60}},t:{client:'claude',projectLabel:'App',totalTokens:50,models:{beta:50}}}}},
 {id:'incomplete-sessions',incomplete:true,period:{sessions:{s:{client:'codex',projectLabel:'App',totalTokens:100,models:{alpha:100}}}}},
 {id:'zero',period:{projects:{a:{label:'App',tokens:0,models:{alpha:100}}}}},
 {id:'opaque-models',period:{projects:{a:{label:'App',tokens:100,models:{'__proto__':20,'constructor':30,'provider/alpha':10}}}}},
 {id:'malformed-models',period:{projects:{a:{label:'App',tokens:100,models:['alpha',20]}}}}
];
for(const c of cases){c.expected=projectRowsForPeriod(c.period,{shareBy:'model',allowSessionModelAttribution:!c.incomplete}).map(r=>({id:r.key,tokens:r.value,models:r.accordionRows.map(x=>({id:x.key,label:x.name,tokens:x.value,percent:x.percent}))}));c.wire={periods:{today:c.period},...(c.incomplete?{periodProjectsOmitted:{today:1}}:{})};delete c.period;}
fs.writeFileSync(output,JSON.stringify({sourceCommit:'cbed5915e294abb4a210f7ebb385e9415f706821',cases},null,2)+'\n');
const {extractUsageFromTokscale}=require(base+'/src/shared/usage');
const {mergeHistories}=require(base+'/src/shared/history');
const grok=extractUsageFromTokscale({groupBy:'client,session,model',entries:[{client:'Grok Build',sessionId:'s',model:'grok-4',input:10,output:5,reasoning:2,cacheRead:100,cost:0.25}]});
const exact={daily:[{date:'2026-10-18',tokens:100,cost:1,cacheReadTokens:60,cacheWriteTokens:10,outputTokens:20,unclassifiedTokens:0,tokenComponentsAvailable:true,perClient:{grok:{tokens:100,cost:1,cacheReadTokens:60,cacheWriteTokens:10,outputTokens:20,unclassifiedTokens:0}},perModel:{'grok-4':{tokens:100,cost:1,cacheReadTokens:60,cacheWriteTokens:10,outputTokens:20,unclassifiedTokens:0}}}],monthly:[],summary:{}};
const history=mergeHistories([exact,exact]);
const sessions={a:{client:'codex',projectLabel:'App',totalTokens:100,models:{alpha:70,beta:30}},b:{client:'claude',projectLabel:'App',totalTokens:50,models:{beta:20}}};
const projects=projectRollupFromSessions(sessions);
const aggregate=aggregateDevices([{deviceId:'public',updatedAt:'2026-10-18T10:00:00Z',today:{totalTokens:150,projects,sessions}}],60000);
fs.writeFileSync(path.resolve(__dirname,'../app/src/test/resources/desktop-074-producer.json'),JSON.stringify({sourceCommit:'cbed5915e294abb4a210f7ebb385e9415f706821',grok,history,projectWire:aggregate},null,2)+'\n');
