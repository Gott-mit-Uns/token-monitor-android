// Public synthetic records; run against the exact exported desktop release tree.
const fs=require('node:fs'),path=require('node:path'),os=require('node:os'),crypto=require('node:crypto');
const ref=process.argv[2]; if(!ref)throw Error('Pass an exported desktop v0.78.0 source tree');
const sourceCommit='d52e0b9cf970a03a30a5f5a1e9139693bb211cd8';
const pinnedBlobs={"src/shared/providers/zcode/sessionMetadata.js": "73128a6b24443eed5b3855a821cadaa80e17b07dd9d26727938cb5ea90a4b25f", "src/electron/renderer/sessionRows.js": "cf3113db68942ca016912f1964aff33e17e92cfcf5784ae46d76e3ce59a0badf", "src/shared/hubProtocol.js": "797c9a51c47d637ad33a8130c94318f4a6f6ffe4837ec4ec6697072a90615be7"};
for(const [file,expected] of Object.entries(pinnedBlobs))if(crypto.createHash('sha256').update(fs.readFileSync(path.join(ref,file))).digest('hex')!==expected)throw Error('Pinned desktop source mismatch: '+file);
const rows=require(path.join(ref,'src/electron/renderer/sessionRows'));
const meta=require(path.join(ref,'src/shared/providers/zcode/sessionMetadata'));
const protocol=require(path.join(ref,'src/shared/hubProtocol'));
const sqlite=require('node:sqlite'),home=fs.mkdtempSync(path.join(os.tmpdir(),'desktop078-'));
try {
 const dbPath=meta.zcodeDatabasePath({home});fs.mkdirSync(path.dirname(dbPath),{recursive:true});
 const db=new sqlite.DatabaseSync(dbPath);db.exec('CREATE TABLE session(id TEXT PRIMARY KEY,title TEXT,task_type TEXT,parent_id TEXT)');
 const insert=db.prepare('INSERT INTO session VALUES(?,?,?,?)');
 for(const entry of [['parent','Saved ZCode title','interactive',null],['child','Child task','subagent_child','parent'],['workflow','Workflow task','workflow_child','parent'],['fork','Independent fork','fork','parent'],['side','Independent side chat','selection_side_chat','parent']])insert.run(...entry);
 db.close();const metadata=meta.resolveSessionMetadata(new Set(['parent','child','workflow','fork','side']),{home});
 const now=Date.parse('2026-10-24T00:00:00Z');
 const sessions=Object.fromEntries([...metadata].map(([id,m],i)=>['zcode:'+id,{client:'zcode',sessionId:id,...m,totalTokens:(i+1)*100,costUsd:(i+1)/8,lastUsedAt:new Date(now-i*1000).toISOString(),models:{'public-model':(i+1)*100}}]));
 const variants=[['complete',sessions],['missing-parent',Object.fromEntries(Object.entries(sessions).filter(([key])=>key!=='zcode:parent'))],['legacy-no-metadata',{'zcode:legacy':{client:'zcode',sessionId:'legacy',totalTokens:1,costUsd:0}}]];
 const cases=variants.map(([name,sessions])=>{
  const stats={periods:{today:{totalTokens:Object.values(sessions).reduce((n,r)=>n+r.totalTokens,0),costUsd:Object.values(sessions).reduce((n,r)=>n+r.costUsd,0),sessions}},devices:[]};
  const expected=rows.groupSessionRows(rows.sessionRowsForPeriod(stats.periods.today,{now:new Date(now)}),{now:new Date(now)}).map(r=>({id:r.key.replace(/^session:/,''),tokens:r.value,cost:String(r.cost),members:(r.groupRows||[]).map(m=>m.key.replace(/^session:/,''))}));
  const full=protocol.prepareSseFanout({reason:'ingest',stats,at:new Date(now).toISOString()});
  const fresh=protocol.prepareSseFanout({reason:'ingest',stats,at:new Date(now+1000).toISOString(),lastContentKey:full.contentKey,hasFreshnessClients:true,hasLegacyClients:true});
  return {name,stats,expected,full:full.stats,freshness:fresh.freshness};
 });
 const sourceBlobs=Object.fromEntries(['src/shared/providers/zcode/sessionMetadata.js','src/electron/renderer/sessionRows.js','src/shared/hubProtocol.js'].map(file=>[file,crypto.createHash('sha256').update(fs.readFileSync(path.join(ref,file))).digest('hex')]));
 fs.writeFileSync(path.join(__dirname,'../app/src/sharedTest/resources/desktop-078.json'),JSON.stringify({sourceCommit,sourceBlobs,now,cases,registry:require(path.join(ref,'src/shared/hubBuildRegistry.json'))},null,2)+'\n');
 console.log('Captured three ZCode transport/grouping cases from fixed desktop v0.78.0');
} finally {fs.rmSync(home,{recursive:true,force:true});}
