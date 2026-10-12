// Fixed public identity fixtures. Original desktop functions are the oracle.
const fs=require('node:fs'),path=require('node:path');
const root=path.resolve(__dirname,'..'),ref=path.resolve(root,'../work/token-monitor-reference/desktop-v0.68.0');
const identity=require(path.join(ref,'src/electron/renderer/accountIdentity.js'));
const subscription=require(path.join(ref,'src/shared/subscriptionDisplay.js'));
const variants=[
 [],[{provider:'codex',accountKey:'public-a',accountName:'Original profile',accountEmail:'first@example.test'}],
 [{provider:'codex',accountKey:'public-a',accountName:'Same profile',accountEmail:'first@example.test'},{provider:'codex',accountKey:'public-b',accountName:'Same profile',accountEmail:'second@example.test'}],
 [{provider:'codex',accountKey:'public-a',accountEmail:'member@example.test'},{provider:'codex',accountKey:'public-b',accountEmail:'member@example.test'},{provider:'codex',accountEmail:'member@example.test'}],
 [{provider:'opencode',accountKey:'public-old',accountName:'Go'},{provider:'opencode',accountKey:'public-new',accountKeyAliases:['public-old'],accountName:'Renamed'},{provider:'opencode',webAccountKey:'public-web',accountKeyAliases:['public-new'],accountName:'Third copy'}],
 [{provider:'openrouter',accountKey:'public-new',webAccountKey:'public-web',accountKeyAliases:[' public-old '],accountName:'API profile'}],
 [{provider:'grok'},{provider:'grok'},{provider:'grok',accountName:'A name without key'}],
 [{provider:'codex',accountName:'Same name'},{provider:'codex',accountName:'Same name',accountKey:'public-a'},{provider:'codex',accountName:'Different name',accountKey:'public-b'}],
 [{provider:'mimo',accountKey:'public-console',accountEmail:'member@example.test',accountLabel:'Console'},{provider:'mimo',accountKey:'public-desktop',accountEmail:'member@example.test',accountLabel:'Desktop Membership'}],
 [{provider:'codex',accountKey:'public-a',accountEmail:'binding@example.test',email:'display@example.test',accountName:'Public name'},{provider:'claude',accountKey:'public-a',accountName:'Different provider'}]
];
function permutations(rows){if(rows.length<=1)return [rows];return rows.flatMap((row,i)=>permutations(rows.filter((_,j)=>i!==j)).map(rest=>[row,...rest]));}
const inputs=variants.flatMap(permutations);
const bindings=[{}, {accountKey:'public-a'},{accountKey:'public-b',profileName:'Same profile'},{accountKey:'public-old'},{accountKey:'public-web'},{accountKey:'not-found',accountEmail:'SECOND@example.test',profileName:'Same profile'},{accountEmail:'member@example.test'},{accountEmail:'binding@example.test'},{profileName:'Same profile'},{profileName:'Same name'},{profileName:'Original profile'},{profileName:'unknown profile'}];
const cases=inputs.map(input=>{
 const records=input.map((row,index)=>({...row,fixtureIndex:index}));const deduped=identity.dedupeAccounts(records);
 const pairs=records.flatMap((a,i)=>records.map((b,j)=>({i,j,same:identity.sameAccount(a,b)})));
 const matches=['codex','claude','opencode','openrouter','grok','mimo'].flatMap(provider=>bindings.map(binding=>{
  const record=subscription.normalizeSubscription({id:'public-binding',provider,binding,startDate:'2026-10-01'});const found=subscription.matchProviderAccount(record,deduped);
  return {record,index:found?.fixtureIndex??null,ambiguous:subscription.needsRebinding(record,deduped),labels:[false,true].map(mask=>{
   const account=found||{accountName:record.binding.profileName,accountEmail:record.binding.accountEmail};return identity.accountTitleLabel(account,[account],{maskEmail:mask});
  })};
 }));
 return {input:records,deduped,pairs,matches};
});
fs.writeFileSync(path.join(root,'app/src/test/resources/subscription-accounts-parity.json'),JSON.stringify({source:'v0.68.0/5d2db368d8313415763860d594de00e46a663418; original accountIdentity and subscriptionDisplay functions',cases},null,2)+'\n');
process.stdout.write(`Captured ${cases.length} identity-list cases, ${cases.reduce((n,c)=>n+c.matches.length,0)} original subscription bindings\n`);
