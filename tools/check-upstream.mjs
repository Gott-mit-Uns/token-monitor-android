import { readFile, appendFile } from 'node:fs/promises';
import { fileURLToPath } from 'node:url';
import { resolve, dirname } from 'node:path';

export function updateStatus(metadata, latest) {
    const base = metadata.androidUpstream;
    if (!base || metadata.schemaVersion !== 2) throw new Error('Separate Android source and desktop compatibility metadata are required');
    if (!/^android-v\d+\.\d+\.\d+-r\d+$/.test(latest.tag_name) || typeof latest.html_url !== 'string' || !latest.html_url.startsWith('https://github.com/The-Minion-oOo/token-monitor-android/releases/tag/')) throw new Error('Unexpected Android release identity');
    if (latest.draft || latest.prerelease) throw new Error('Expected a stable Android release');
    return { available: latest.tag_name !== base.tag, current: base.tag, latest: latest.tag_name, url: latest.html_url };
}
export async function checkUpstream() {
    const root = resolve(dirname(fileURLToPath(import.meta.url)), '..');
    const metadata = JSON.parse(await readFile(resolve(root, 'upstream.json'), 'utf8'));
    const repository = metadata.androidUpstream?.repository;
    if (repository !== 'The-Minion-oOo/token-monitor-android') throw new Error('Unexpected Android upstream repository');
    const headers = { 'User-Agent': 'token-monitor-android-upstream-check', Accept: 'application/vnd.github+json' };
    if (process.env.GH_TOKEN) headers.Authorization = `Bearer ${process.env.GH_TOKEN}`;
    const response = await fetch(`https://api.github.com/repos/${repository}/releases/latest`, { headers, signal: AbortSignal.timeout(20000) });
    if (!response.ok) throw new Error(`GitHub release check failed (HTTP ${response.status}); no baseline was changed`);
    const status = updateStatus(metadata, await response.json());
    const report = `Android reviewed release: ${status.current}\nLatest Android release: ${status.latest}\n${status.available ? 'Review Android changes before selecting patches.' : 'Android release baseline is current.'}\nDesktop Hub compatibility: ${metadata.desktopCompatibility.tag}; desktop releases do not automatically request an app upgrade.\n`;
    console.log(report);
    if (process.env.GITHUB_STEP_SUMMARY) await appendFile(process.env.GITHUB_STEP_SUMMARY, report);
    if (process.env.GITHUB_OUTPUT) await appendFile(process.env.GITHUB_OUTPUT, `update_available=${status.available}\ncurrent_tag=${status.current}\nlatest_tag=${status.latest}\nrelease_url=${status.url}\n`);
}
if (process.argv[1] && resolve(process.argv[1]) === fileURLToPath(import.meta.url)) checkUpstream().catch(() => { console.error('Android upstream check failed; metadata remains unchanged.'); process.exitCode = 1; });
