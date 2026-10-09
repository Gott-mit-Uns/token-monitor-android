import assert from 'node:assert/strict';
import { readFileSync } from 'node:fs';
import { execFileSync } from 'node:child_process';
import { dirname, resolve } from 'node:path';
import { fileURLToPath } from 'node:url';
const root = resolve(dirname(fileURLToPath(import.meta.url)), '..');
const metadata = JSON.parse(readFileSync(resolve(root,'upstream.json'),'utf8'));
assert.equal(metadata.schemaVersion,2);
assert.equal(metadata.androidUpstream.repository,'The-Minion-oOo/token-monitor-android');
assert.equal(metadata.desktopCompatibility.repository,'Javis603/token-monitor');
for (const source of [metadata.androidUpstream,metadata.desktopCompatibility]) assert.match(source.commit,/^[a-f0-9]{40}$/);
const properties = Object.fromEntries(readFileSync(resolve(root,'gradle.properties'),'utf8').split('\n').filter(x=>x.includes('=')).map(x=>{const i=x.indexOf('=');return [x.slice(0,i),x.slice(i+1)]}));
assert.equal(properties.tokenMonitorAndroidBaseTag,metadata.androidUpstream.tag);
assert.equal(properties.tokenMonitorUpstreamTag,metadata.desktopCompatibility.tag);
assert.equal(properties.tokenMonitorUpstreamCommit,metadata.desktopCompatibility.commit);
assert.equal(properties.tokenMonitorUpstreamVersion,metadata.desktopCompatibility.version);
const prefix = 'app/src/main/java/io/github/theminionooo/tokenmonitor/';
const shared = ['domain/UsageHistory.kt','ui/TrendPresentation.kt','widget/WidgetDeckData.kt'];
function upstream(file) { return execFileSync('git',['show',`${metadata.androidUpstream.commit}:${prefix}${file}`],{cwd:root,encoding:'utf8'}); }
for (const file of shared) assert.equal(readFileSync(resolve(root,prefix+file),'utf8'),upstream(file),`Shared upstream core drift: ${file}; review the change rather than cloning a second implementation`);
function period(source) { const start=source.indexOf('    private fun HubPeriodDto.toDomain()'),end=source.indexOf('    private fun HubProjectDto.toDomain()'); assert.ok(start>=0 && end>start,'Period conversion boundary must be explicitly reviewed'); return source.slice(start,end); }
assert.equal(period(readFileSync(resolve(root,prefix+'data/protocol/HubProtocolParser.kt'),'utf8')),period(upstream('data/protocol/HubProtocolParser.kt')),'Period conversion must use the reviewed Android upstream implementation');
console.log('Separate source/compatibility pins match; 3 shared files and period conversion match reviewed Android upstream.');
