import assert from 'node:assert/strict';
import { readFileSync, existsSync } from 'node:fs';
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
const shared = ['data/protocol/HubDtos.kt','data/protocol/HubProtocolParser.kt','domain/HubModels.kt','domain/UsageHistory.kt','ui/TrendPresentation.kt','widget/WidgetDeckData.kt'];
function upstream(file) { return execFileSync('git',['show',`${metadata.androidUpstream.commit}:${prefix}${file}`],{cwd:root,encoding:'utf8'}); }
for (const file of shared) assert.equal(readFileSync(resolve(root,prefix+file),'utf8'),upstream(file),`Shared upstream core drift: ${file}; review the change rather than cloning a second implementation`);
const removed = ['data/storage/DesktopPreferences.kt','fork/PresentationPreferencesCodec.kt','ui/DesktopSettingsPanel.kt','ui/DesktopPresentation.kt','ui/LiveTokenRate.kt','ui/DragOrderHandle.kt','ui/SubscriptionPresentation.kt','ui/UsageExport.kt'];
for (const file of removed) assert.equal(existsSync(resolve(root,prefix+file)),false,`Withdrawn desktop extension returned: ${file}`);
const base=metadata.forkBase;
assert.equal(base.tag,'v0.67.0-hub.2');
assert.match(base.commit,/^[a-f0-9]{40}$/);
for (const file of ['data/storage/DisplayPreferences.kt','data/storage/SecureConnectionStore.kt','data/storage/SnapshotCache.kt','data/network/HubAddressValidator.kt']) {
    const original=execFileSync('git',['show',`${base.commit}:${prefix}${file}`],{cwd:root,encoding:'utf8'});
    assert.equal(readFileSync(resolve(root,prefix+file),'utf8'),original,`Preserved settings/security/cache/address core drift: ${file}`);
}
console.log('6 shared core files match Android upstream; withdrawn desktop extensions are absent; base display settings, security, cache and Hub addresses preserved.');
