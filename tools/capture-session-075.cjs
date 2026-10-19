// Public synthetic cases; expected activity is executed by fixed desktop v0.75.0.
const fs=require('fs'),path=require('path'),crypto=require('crypto');
const root=path.resolve(process.argv[2]||'');
for(const [file,sha] of Object.entries({'src/shared/sessionLive.js':'eebd86b3acbcd29a50e3423170eb2b2c300a2d3e','src/electron/renderer/sessionRows.js':'e6ff761dd8fe312311c53c4652fce2f7137dd6ad'})) {
 const data=fs.readFileSync(path.join(root,file));const header=Buffer.from(`blob ${data.length}\0`);
 if(crypto.createHash('sha1').update(header).update(data).digest('hex')!==sha) throw Error('Pinned source mismatch: '+file);
}
const rows=require(path.join(root,'src/electron/renderer/sessionRows.js'));
const now=Date.parse('2026-10-20T01:00:00Z'),lastUsedAt=new Date(now-1000).toISOString();
const cases=[];
for(const [name,extra] of Object.entries({model:{models:{'grok-bot-default':10}},sandbox:{sessionId:'sand-subagent-run'},ordinary:{},zero:{models:{'grok-bot-default':0}},prefixOnly:{sessionId:'sand-subagent-',models:{'grok-bot-':10}},otherClient:{client:'codex',models:{'grok-bot-default':10}}})) {
 for(const boundary of ['missing','open','ended','expired','archived']) {
  const session={client:'cursor',sessionId:name,totalTokens:10,costUsd:0.1,models:{claude:10},lastUsedAt,...extra};
  if(boundary==='open') session.turnEnded=false;
  if(boundary==='ended') session.turnEnded=true;
  if(boundary==='expired') session.lastUsedAt=new Date(now-600001).toISOString();
  if(boundary==='archived') session.archived=true;
  const wire={periods:{today:{totalTokens:10,costUsd:0.1,sessions:{[`${session.client}:${session.sessionId}`]:session}}}};
  const expected=rows.sessionRowsForPeriod(wire.periods.today,{now:new Date(now)})[0];
  cases.push({id:name+'-'+boundary,wire,activity:expected.activityState,bot:!!expected.grokBot});
 }
}
const current={client:'cursor',sessionId:'cross-period',totalTokens:10,costUsd:0.1,models:{claude:10},lastUsedAt};
const wire={periods:{today:{sessions:{'cursor:cross-period':current}},month:{sessions:{'cursor:cross-period':{...current,models:{'grok-bot-default':10}}}}}};
const expected=rows.sessionRowsForPeriod(wire.periods.today,{now:new Date(now),sourcePeriods:wire.periods})[0];
cases.push({id:'cross-period',wire,activity:expected.activityState,bot:!!expected.grokBot});
fs.writeFileSync(path.resolve(__dirname,'../app/src/test/resources/desktop-075-sessions.json'),JSON.stringify({sourceCommit:'877fef6002a5fee84aa502fc3454b4f700b55813',now,cases},null,2)+'\n');
console.log(`Captured ${cases.length} fixed desktop activity/identity cases`);
