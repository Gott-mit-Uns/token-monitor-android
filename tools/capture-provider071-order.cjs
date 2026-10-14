// Refresh only the provider ordering oracle from fixed v0.71.0 pure functions.
const fs=require('node:fs'),path=require('node:path');
const root=path.resolve(__dirname,'..');
const ref=path.resolve(root,'../token-monitor-reference/desktop-v0.71.0');
const order=require(path.join(ref,'src/electron/renderer/limits/providerOrder.js'));
const providers=require(path.join(ref,'src/shared/limits/providers.js')).LIMIT_PROVIDER_CATALOG;
const file=path.join(root,'app/src/test/resources/limit-preferences-parity.json');
const fixture=JSON.parse(fs.readFileSync(file,'utf8'));
const inputs=[[],['codex','claude'],[' MIMO ','codex','CODEx','future','zed'],providers.map(p=>p.id).reverse()];
fixture.orders=inputs.map(input=>({input,selection:order.normalizeLimitProviderSelection(input,providers),order:order.normalizeLimitProviderOrder(input,providers),moves:providers.flatMap(p=>[-1,0,1].map(direction=>({id:p.id,direction,value:order.moveLimitProvider(input,providers,p.id,direction===-1?'up':direction===1?'down':'invalid').split(',')})))}));
fixture.source='Provider orders: v0.71.0/a514e206; unchanged legacy window/hidden/account assertions retained from v0.68.0';
fs.writeFileSync(file,JSON.stringify(fixture,null,2)+'\n');
