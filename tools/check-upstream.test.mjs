import { test } from 'node:test';
import assert from 'node:assert/strict';
import { updateStatus } from './check-upstream.mjs';
const metadata = { schemaVersion:2, androidUpstream:{ tag:'android-v0.68.0-r1' }, desktopCompatibility:{tag:'v0.68.0'} };
test('new desktop version does not request Android update',()=>assert.equal(updateStatus({...metadata,desktopCompatibility:{tag:'v0.69.0'}},{tag_name:'android-v0.68.0-r1',html_url:'https://github.com/The-Minion-oOo/token-monitor-android/releases/tag/stable'}).available,false));
test('new Android version requests review',()=>assert.equal(updateStatus(metadata,{tag_name:'android-v0.69.0-r1',html_url:'https://github.com/The-Minion-oOo/token-monitor-android/releases/tag/stable'}).available,true));
test('stable version check does not silently accept prereleases',()=>assert.throws(()=>updateStatus(metadata,{tag_name:'rc',prerelease:true})));
test('old ambiguous metadata fails instead of watching wrong source',()=>assert.throws(()=>updateStatus({repository:'desktop',tag:'v0.68.0'},{})));
test('rejects unexpected release identity',()=>assert.throws(()=>updateStatus(metadata,{tag_name:'v0.69.0',html_url:'https://example.com'})));
