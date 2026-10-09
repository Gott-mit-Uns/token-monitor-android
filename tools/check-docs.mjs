import fs from 'node:fs';
import path from 'node:path';
import { fileURLToPath } from 'node:url';
const root = fileURLToPath(new URL('../', import.meta.url));
function walk(dir) {
  return fs.readdirSync(dir, {withFileTypes:true}).flatMap(e => {
    if (['.git','node_modules','build','.gradle','.kotlin','.idea','dist'].includes(e.name)) return [];
    const p = path.join(dir,e.name);
    return e.isDirectory() ? walk(p) : p.endsWith('.md') ? [p] : [];
  });
}
let errors = 0;
for (const file of walk(root)) {
  const body = fs.readFileSync(file,'utf8').replace(/```[\s\S]*?```/g,'');
  const links = [...body.matchAll(/\]\(([^)]+)\)|(?:src|href)="([^"]+)"/g)];
  for (const match of links) {
    let target = (match[1] || match[2]).split(/\s+"/)[0].replace(/^<|>$/g,'');
    if (/^(?:https?:|mailto:|data:|#)/i.test(target)) continue;
    target = decodeURIComponent(target.split(/[?#]/)[0]);
    if (!fs.existsSync(path.resolve(path.dirname(file),target))) {console.error(`${path.relative(root,file)}: missing ${target}`);errors++;}
  }
}
if(errors) process.exitCode=1; else console.log('All local documentation links resolve.');
