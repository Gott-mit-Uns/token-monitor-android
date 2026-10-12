const fs=require('node:fs'),path=require('node:path');
const root=path.resolve(__dirname,'..'),ref=path.resolve(root,'../work/token-monitor-reference/desktop-v0.68.0');
const rates=require(path.join(ref,'src/electron/renderer/tokenRatePresentation.js'));
const compact=require(path.join(ref,'src/shared/compactTokens.js'));
const periods=[{}, {timedOutputTokens:100,timedDurationMs:1000},{timedTokens:100,timedDurationMs:1000},
 {timedTokens:100,timedOutputTokens:40,timedDurationMs:1000},
 {timedTokens:100,timedOutputTokens:40,timedDurationMs:0},{timedTokens:100,timedOutputTokens:40,timedDurationMs:-1},
 {timedTokens:-100,timedOutputTokens:-40,timedDurationMs:1000},
 {timedTokens:1e15,timedOutputTokens:1e15,timedDurationMs:.001},
 {timedTokens:'100',timedOutputTokens:'40',timedDurationMs:'1000'},
 {timedTokens:null,timedOutputTokens:null,timedDurationMs:null},
 {timedTokens:100,timedOutputTokens:.49,timedDurationMs:1000},
 {timedTokens:100,timedOutputTokens:.5,timedDurationMs:1000},
 {timedTokens:'NaN',timedOutputTokens:'Infinity',timedDurationMs:'1e1000'},
 {timedTokens:100,timedOutputTokens:40,timedDurationMs:1000,capabilities:{throughput:false}},
 {timedTokens:1e308,timedOutputTokens:1e308,timedDurationMs:1e-100}];
const cases=[];
for(const period of periods)for(const locale of ['en','zh-CN','zh-TW'])for(const units of ['western','localized'])for(const mode of ['speed','burn']) {
 const speed=rates.tokenRatePerSecond(period),burn=rates.tokenBurnPerMinute(period),value=mode==='burn'?burn:speed;
 cases.push({period,locale,units,mode,speed,burn,text:Math.round(value)>0?'≈ '+compact.formatCompactTokens(value,units,locale)+(mode==='burn'?' tok/min':' tok/s'):null});
}
fs.writeFileSync(path.join(root,'app/src/test/resources/average-rate-parity.json'),JSON.stringify({source:'desktop v0.68.0 original tokenRatePerSecond/tokenBurnPerMinute and title formatting',cases},null,2)+'\n');
console.log(`Original average-rate cases: ${cases.length}`);
