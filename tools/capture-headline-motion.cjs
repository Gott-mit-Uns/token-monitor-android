// Run the immutable desktop headline animator with a controlled public frame clock.
const fs=require('fs'),path=require('path'),vm=require('vm');
const root=path.resolve(__dirname,'..');
const source=fs.readFileSync(path.join(root,'../work/token-monitor-reference/desktop-v0.68.0/src/electron/renderer/app.js'),'utf8');
const start=source.indexOf('function easeOutQuart(t)');
const end=source.indexOf('function animateTotalNumber(',start);
if(start<0 || end<0) throw Error('Pinned headline source missing');
function harness(reduce=false) {
 let now=0,id=0;const pending=new Map();
 const el={textContent:''};
 const context=vm.createContext({performance:{now:()=>now},requestAnimationFrame:callback=>{pending.set(++id,callback);return id},cancelAnimationFrame:id=>pending.delete(id),prefersReducedMotion:()=>reduce,formatNumber:n=>Math.round(Number(n||0)).toLocaleString('en-US')});
 vm.runInContext(source.slice(start,end),context);
 return {el,animate:(from,to,duration)=>context.animateNumber(el,from,to,duration),value:()=>vm.runInContext('numberAnimValue',context),frame:time=>{now=time;const frames=[...pending.values()];pending.clear();frames.forEach(f=>f(time));return {atMs:time,display:el.textContent}},pending:()=>pending.size};
}
const cases=[];
for(const [from,to,duration] of [[1000,2000,1000],[2000,1000,1000],[3000000000,0,800],[0,1000,1000],[999999999,2000000000,1000]]) {
 const h=harness();h.animate(from,to,duration);
 cases.push({from,to,durationMs:duration,samples:[0,.25,.5,.75,1].map(p=>h.frame(p*duration)),pendingAtEnd:h.pending()});
}
const interrupted=harness();interrupted.animate(1000,2000,1000);const before=interrupted.frame(250);
interrupted.animate(interrupted.value(),3000,1000);
const interruption={before,samples:[250,750,1250].map(t=>interrupted.frame(t)),pendingAtEnd:interrupted.pending()};
const reduced=harness(true);reduced.animate(1000,2000,1000);
const evidence={referenceCommit:'5d2db368d8313415763860d594de00e46a663418',source:'src/electron/renderer/app.js:1656-1701',method:'Original animateNumber function; synthetic clock/DOM text sink, no real Hub or user values',cases,interruption,reducedMotion:{display:reduced.el.textContent,pending:reduced.pending()}};
const output=path.join(root,'evidence/headline-motion/original-headline-motion.json');
fs.mkdirSync(path.dirname(output),{recursive:true});fs.writeFileSync(output,JSON.stringify(evidence,null,2)+'\n');
console.log('Original headline animation: 5 transitions, interruption and reduced-motion captured');
