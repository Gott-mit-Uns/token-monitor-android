#!/usr/bin/env python3
"""Small, dependency-free policy guard. Human review validates evidence and authority."""
import argparse
import hashlib
from pathlib import Path
import re
import subprocess
import sys
from urllib.parse import unquote, urlsplit

ROOT = Path(__file__).resolve().parents[1]
RECORD = re.compile(r'^(?:handoff|validation|cost[_-]validation|cost[_-]model)(?:[_-].+)?\.(?:md|txt|rst)$', re.I)
CURRENT = {'docs/HANDOFF.md', 'docs/VALIDATION.md', 'docs/COST_MODEL.md'}
MIGRATION_BASE = 'cfab5438ed5dac6537c68affb385517ccfa44c0e'
MIGRATION_ARCHIVES = {'docs/archive/PHONE_ACCEPTANCE_0.2.53.md', 'docs/archive/REA_NATIVE_WIDGET_063.md', 'docs/archive/ALIGN_072_SESSIONS.md', 'docs/archive/UI_072_VALIDATION.md', 'docs/archive/WIDGET_LAUNCHER_EVIDENCE.md', 'docs/archive/DESKTOP_071_DIFF.md', 'docs/archive/LIST_INTERACTION_AUDIT.md', 'docs/archive/APK_SIZE_COMPARISON_064.md', 'docs/archive/MAC_ANDROID_WIDGET_COMPARISON_063.md', 'docs/archive/INDEX.md', 'docs/archive/DESKTOP_072_ALIGNMENT.md', 'docs/archive/ALIGN_072_CURSOR.md', 'docs/archive/HUB_CAPACITY_FRESHNESS_REUSE.md', 'docs/archive/HUB_CAPACITY.md', 'docs/archive/DESKTOP_070_DIFF.md', 'docs/archive/ALIGN_072_UPDATE_OPENCODE.md', 'docs/archive/V5_PERFORMANCE_EVIDENCE.md', 'docs/archive/HUB_CAPACITY_PERFORMANCE_BASELINE.md', 'docs/archive/USER_FEEDBACK_048.md', 'docs/archive/FULL_RENDERER_AUDIT.md', 'docs/archive/GIT_BASELINE_0.2.51.md', 'docs/archive/DESKTOP_SOURCE_MOTION_PARITY.md', 'docs/archive/DESKTOP_NUMBER_RUNTIME.md'}
EXTERNAL_EVIDENCE = ('evidence/', 'work/', 'dist/')



def git(root, *args):
    return subprocess.check_output(['git', '-C', str(root), *args], text=True, stderr=subprocess.DEVNULL).splitlines()


def anchors(text):
    found, counts = set(), {}
    for title in re.findall(r'^ {0,3}#{1,6}\s+(.+?)\s*#*$', text, re.M):
        title = re.sub(r'<[^>]+>', '', title).strip().lower()
        title = re.sub(r'[^\w\-\s]', '', title).replace(' ', '-')
        count = counts.get(title, 0)
        counts[title] = count + 1
        found.add(title + (f'-{count}' if count else ''))
    found.update(re.findall(r'\b(?:id|name)=[\"\x27]([^\"\x27]+)', text))
    return found


def check(root, base):
    errors = []
    files = git(root, 'ls-files', '--cached', '--others', '--exclude-standard')
    added = git(root, 'diff', '--no-renames', '--name-only', '--diff-filter=A', base, '--')
    added += git(root, 'ls-files', '--others', '--exclude-standard')
    for name in set(added):
        if name.startswith('docs/archive/'):
            actual_base = git(root, 'rev-parse', base)[0]
            if actual_base != MIGRATION_BASE or name not in MIGRATION_ARCHIVES:
                errors.append(f'{name}: frozen archive; explain unique evidence and obtain specific approval')
            continue
        # Governance covers documentation, not business code, fixtures or releases.
        if name in CURRENT or name.startswith(('app/src/test/', 'app/src/androidTest/', 'app/src/sharedTest/')):
            continue
        if RECORD.match(Path(name).name) or re.match(r'^(?:release[_-]summary|new[_-]readme|新版介绍|发布总结)', Path(name).stem, re.I):
            errors.append(f'{name}: use the current document instead of a duplicate record')
    for name in sorted(set(files)):
        p = root / name
        if p.suffix.lower() != '.md' or not p.is_file():
            continue
        text = p.read_text(encoding='utf-8')
        text = re.sub(r'^```[^\n]*\n.*?^```\s*$', '', text, flags=re.M | re.S)
        refs = dict(re.findall(r'^\s*\[([^]]+)\]:\s*(\S+)', text, re.M))
        links = re.findall(r'!?\[[^]]*\]\(([^)]+)\)', text)
        links += re.findall(r'<(?:img|a)\b[^>]*?\b(?:src|href)=[\"\x27]([^\"\x27]+)', text)
        links += [refs[key] for key in re.findall(r'\[[^]]+\]\[([^]]+)\]', text) if key in refs]
        for target in links:
            target = target.strip().split(' "', 1)[0].strip('<>')
            parsed = urlsplit(target)
            if parsed.scheme or parsed.netloc:
                historic = re.match(r'https://github.com/Gott-mit-Uns/token-monitor-android/blob/([0-9a-f]{40})/(.+)', target)
                if historic:
                    historical_path = unquote(historic[2].split('#', 1)[0])
                    try:
                        content = '\n'.join(git(root, 'show', historic[1] + ':' + historical_path))
                        if parsed.fragment and historical_path.endswith('.md') and unquote(parsed.fragment) not in anchors(content):
                            errors.append(f'{name}: missing historical anchor {target}')
                    except subprocess.CalledProcessError:
                        errors.append(f'{name}: missing historical target {target}')
                continue
            dest = p if not parsed.path else (root / unquote(parsed.path).lstrip('/') if parsed.path.startswith('/') else p.parent / unquote(parsed.path))
            if name in MIGRATION_ARCHIVES and not dest.exists() and any(str(dest.resolve()).startswith(str((root / prefix).resolve()) + '/') for prefix in EXTERNAL_EVIDENCE):
                continue  # Existing local evidence pointers are not delivered or verified here.
            if not dest.exists():
                errors.append(f'{name}: missing local link {target}')
            elif parsed.fragment and dest.suffix.lower() == '.md' and unquote(parsed.fragment) not in anchors(dest.read_text(encoding='utf-8')):
                errors.append(f'{name}: missing anchor {target}')
    changed = set(git(root, 'diff', '--name-only', base, '--')) | set(git(root, 'ls-files', '--others', '--exclude-standard'))
    for name in sorted(changed):
        if not re.match(r'^README(?:[.\-_].+)?$', Path(name).name, re.I):
            continue
        marker = 'README_SHA256' if name == 'README.md' else 'README_SHA256 ' + name
        if name not in changed:
            continue
        handoff = root / 'docs/HANDOFF.md'
        record = handoff.read_text(encoding='utf-8') if handoff.exists() else ''
        digest = hashlib.sha256((root / name).read_bytes()).hexdigest() if (root / name).exists() else ''
        try:
            old = '\n'.join(git(root, 'show', f'{base}:docs/HANDOFF.md'))
        except subprocess.CalledProcessError:
            old = ''
        if not digest or f'{marker}: {digest}' not in record or '授权来源：' not in record or '适用基线：`' + git(root, 'rev-parse', base)[0] + '`' not in record or (old and re.findall(r'授权来源：.*', record) == re.findall(r'授权来源：.*', old)):
            errors.append(f'{name}: current HANDOFF must record this request, scope and final SHA256; human review verifies authority')
    return errors


def self_test():
    import tempfile
    with tempfile.TemporaryDirectory() as tmp:
        root = Path(tmp)
        def run(*args):
            subprocess.run(['git', '-C', str(root), *args], check=True, stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL)
        run('init'); run('config', 'user.name', 'Synthetic'); run('config', 'user.email', 'synthetic@example.invalid')
        (root / 'README.md').write_text('# Product\n')
        run('add', '.'); run('commit', '-m', 'baseline')
        (root / 'docs').mkdir()
        (root / 'docs/HANDOFF.md').write_text('# Current\n')
        assert not check(root, 'HEAD')
        (root / 'docs/HANDOFF_1.md').write_text('old')
        assert any('duplicate' in e for e in check(root, 'HEAD'))
        (root / 'docs/HANDOFF_1.md').unlink()
        (root / 'docs/HANDOFF.md').write_text('[bad](missing.md)\n[bad](../README.md#absent)')
        assert len(check(root, 'HEAD')) == 2
        (root / 'docs/HANDOFF.md').write_text('# Current\n[bad](#absent)')
        assert any('missing anchor' in e for e in check(root, 'HEAD'))
        (root / 'reports').mkdir()
        (root / 'reports/HANDOFF_3.md').write_text('# Report\n')
        assert any('duplicate' in e for e in check(root, 'HEAD'))
        (root / 'reports/HANDOFF_3.md').unlink()
        (root / 'reports/notes.md').write_text('# Notes\n')
        (root / 'docs/HANDOFF.md').write_text('# Current\n')
        run('add', '.'); run('commit', '-m', 'ordinary note')
        run('mv', 'reports/notes.md', 'reports/VALIDATION_4.md')
        assert any('duplicate' in e for e in check(root, 'HEAD'))
        run('mv', 'reports/VALIDATION_4.md', 'reports/notes.md')
        (root / 'docs/HANDOFF.md').write_text('# Current\n')
        (root / 'README.md').write_text('# Changed\n')
        assert any('SHA256' in e for e in check(root, 'HEAD'))
        digest = hashlib.sha256((root / 'README.md').read_bytes()).hexdigest()
        (root / 'docs/HANDOFF.md').write_text(f'授权来源：synthetic; 适用基线：`{git(root, "rev-parse", "HEAD")[0]}`\nREADME_SHA256: {digest}\n')
        # A later change cannot reuse an old authorization, even with a refreshed hash.
        run('add', '.'); run('commit', '-m', 'authorized')
        (root / 'README.md').write_text('# Later change\n')
        newer = hashlib.sha256((root / 'README.md').read_bytes()).hexdigest()
        handoff = root / 'docs/HANDOFF.md'
        handoff.write_text(handoff.read_text().replace(digest, newer))
        assert any('SHA256' in e for e in check(root, 'HEAD'))
        (root / 'README.md').write_text('# Changed\n')
        handoff.write_text(handoff.read_text().replace(newer, digest))
        (root / 'docs/archive').mkdir()
        (root / 'docs/archive/HANDOFF_2.md').write_text('# New archive\n')
        assert any('frozen archive' in e for e in check(root, 'HEAD'))
        (root / 'docs/archive/HANDOFF_2.md').unlink()
        (root / 'RELEASE.md').write_text('# Release\n')
        (root / 'app/src/test/fixtures').mkdir(parents=True); (root / 'app/src/test/fixtures/VALIDATION_fixture.md').write_text('# Fixture\n')
        assert not check(root, 'HEAD')
    print('Document guard self-test passed (duplicates, links, anchors, README authority and narrow exceptions)')


if __name__ == '__main__':
    parser = argparse.ArgumentParser()
    parser.add_argument('--base', default='origin/main')
    parser.add_argument('--self-test', action='store_true')
    args = parser.parse_args()
    if args.self_test:
        self_test()
    else:
        findings = check(ROOT, args.base)
        for finding in findings:
            print(finding)
        print(f'Document findings: {len(findings)}')
        sys.exit(bool(findings))
