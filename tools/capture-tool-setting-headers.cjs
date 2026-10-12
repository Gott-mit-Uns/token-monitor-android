// Execute the fixed renderer's actual status expressions and translations.
const fs=require('fs'),path=require('path'),vm=require('vm');
const root=path.resolve(__dirname,'..'),ref=path.resolve(root,'../work/token-monitor-reference/desktop-v0.68.0');
const sandbox={window:{}};vm.createContext(sandbox);vm.runInContext(fs.readFileSync(path.join(ref,'src/electron/renderer/i18n.js'),'utf8'),sandbox);
const i18n=sandbox.window.TokenMonitorI18n;
const aliases=fs.readFileSync(path.join(ref,'src/electron/renderer/modelAliasForm.js'),'utf8');
const app=fs.readFileSync(path.join(ref,'src/electron/renderer/app.js'),'utf8');
const aliasExpr=aliases.match(/el\('Status'\)\.textContent = ([\s\S]*?);/)[1];
const priceStart=app.indexOf('function renderCustomPricing()');
const priceExpr=app.slice(priceStart).match(/statusEl\.textContent = ([\s\S]*?);/)[1];
const aliasStatus=new Function('entries','grouping','t','return '+aliasExpr+';');
const priceStatus=new Function('overrides','t','return '+priceExpr+';');
const cases=[];
for(const locale of ['en','zh-CN']) {
 const t=(key,params)=>i18n.translate(locale,key,params);
 cases.push({locale,kind:'alias-title',text:t('settings.modelAliases.title')},{locale,kind:'price-title',text:t('settings.customPricing.title')});
 for(const count of [0,1,205]) for(const grouping of ['off','duplicates','prefix','invalid']) cases.push({locale,kind:'alias-status',count,grouping,text:aliasStatus(Array(count),grouping,t)});
 for(const count of [0,1,4096]) cases.push({locale,kind:'price-status',count,text:priceStatus(Array(count),t)});
}
const css=fs.readFileSync(path.join(ref,'src/electron/renderer/styles.css'),'utf8');
const selectors=['.cursor-settings-toggle','.settings-group-header .cursor-settings-summary','.cursor-status-pill'];
const styles=Object.fromEntries(selectors.map(selector=>{const start=css.indexOf(selector+' {');if(start<0)throw Error('Source selector missing');return [selector,css.slice(start,css.indexOf('}',start)+1)];}));
const out=path.join(root,'evidence/tool-settings-controls/source');fs.mkdirSync(out,{recursive:true});fs.writeFileSync(path.join(out,'settings-control-reference.json'),JSON.stringify({source:'desktop v0.68.0 immutable original renderer expressions, i18n and CSS',aliasExpr,priceExpr,styles,cases},null,2)+'\n');
process.stdout.write('Original '+cases.length+' header/status captions and three original CSS declarations captured.\n');
