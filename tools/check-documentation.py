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
RECORD = re.compile(r'^(?:v?\d[\d. _-]*)?(?:handoff|validation|cost[ ._-]?validation|cost[ ._-]?model)(?:[ ._-].+|v?\d.*)?\.(?:md|markdown|txt|rst|adoc|asciidoc|html?|pdf|docx)$', re.I)
CURRENT = {'docs/HANDOFF.md', 'docs/VALIDATION.md', 'docs/COST_MODEL.md'}
MIGRATION_BASE = 'cfab5438ed5dac6537c68affb385517ccfa44c0e'
MIGRATION_ARCHIVES = {'docs/archive/PHONE_ACCEPTANCE_0.2.53.md', 'docs/archive/REA_NATIVE_WIDGET_063.md', 'docs/archive/ALIGN_072_SESSIONS.md', 'docs/archive/UI_072_VALIDATION.md', 'docs/archive/WIDGET_LAUNCHER_EVIDENCE.md', 'docs/archive/DESKTOP_071_DIFF.md', 'docs/archive/LIST_INTERACTION_AUDIT.md', 'docs/archive/APK_SIZE_COMPARISON_064.md', 'docs/archive/MAC_ANDROID_WIDGET_COMPARISON_063.md', 'docs/archive/INDEX.md', 'docs/archive/DESKTOP_072_ALIGNMENT.md', 'docs/archive/ALIGN_072_CURSOR.md', 'docs/archive/HUB_CAPACITY_FRESHNESS_REUSE.md', 'docs/archive/HUB_CAPACITY.md', 'docs/archive/DESKTOP_070_DIFF.md', 'docs/archive/ALIGN_072_UPDATE_OPENCODE.md', 'docs/archive/V5_PERFORMANCE_EVIDENCE.md', 'docs/archive/HUB_CAPACITY_PERFORMANCE_BASELINE.md', 'docs/archive/USER_FEEDBACK_048.md', 'docs/archive/FULL_RENDERER_AUDIT.md', 'docs/archive/GIT_BASELINE_0.2.51.md', 'docs/archive/DESKTOP_SOURCE_MOTION_PARITY.md', 'docs/archive/DESKTOP_NUMBER_RUNTIME.md'}
EXTERNAL_EVIDENCE = ('evidence/', 'work/', 'dist/')



def git(root, *args):
    return subprocess.check_output(['git', '-C', str(root), *args], text=True, stderr=subprocess.DEVNULL).splitlines()


def git_paths(root, *args):
    args = list(args)
    args.insert(args.index('--') if '--' in args else len(args), '-z')
    output = subprocess.check_output(['git', '-C', str(root), *args], text=True, stderr=subprocess.DEVNULL)
    return [path for path in output.split('\0') if path]


def without_fences(text):
    lines, fence = [], None
    indented, blank, in_list, in_html = False, True, False, False
    for line in text.splitlines():
        marker = re.match(r'^ {0,3}(`{3,}|~{3,})(.*)$', line)
        if fence:
            if marker and marker[1][0] == fence[0] and len(marker[1]) >= fence[1] and not marker[2].strip():
                fence = None
        elif marker:
            fence = (marker[1][0], len(marker[1]))
        else:
            stripped = line.strip()
            if re.match(r'^ {0,3}<(?:table|div|pre|section|details)\b', line, re.I):
                in_html = True
            if re.match(r'^ {0,3}(?:[-+*]|\d+[.)])\s', line):
                in_list = True
            elif stripped and not line[0].isspace() and not in_html:
                in_list = False
            has_indent = line.startswith('    ') or line.startswith('\t')
            if indented and stripped and not has_indent:
                indented = False
            if has_indent and (indented or (blank and not in_list and not in_html)):
                indented = True
            else:
                lines.append(line)
            if re.search(r'</(?:table|div|pre|section|details)>', line, re.I):
                in_html = False
            blank = not stripped
    return '\n'.join(lines)


def anchors(text):
    text = re.sub(r'<!--[\s\S]*?-->', '', without_fences(text))
    found, counts = set(), {}
    headings = []
    lines = text.splitlines()
    for index, line in enumerate(lines):
        atx = re.match(r'^ {0,3}#{1,6}\s+(.+?)\s*#*$', line)
        if atx:
            headings.append(atx[1])
        elif line.strip() and index + 1 < len(lines) and re.match(r'^ {0,3}(?:=+|-+)\s*$', lines[index + 1]):
            headings.append(line.strip())
    for title in headings:
        title = re.sub(r'!?\[([^]]*)\]\([^)]*\)', r'\1', title)
        title = re.sub(r'\[([^]]*)\]\[[^]]*\]', r'\1', title)
        title = re.sub(r'<[^>]+>', '', title).strip().lower()
        title = re.sub(r'[^\w\-\s]', '', title).replace(' ', '-')
        count = counts.get(title, 0)
        candidate = title + (f'-{count}' if count else '')
        while candidate in found:
            count += 1
            candidate = title + f'-{count}'
        counts[title] = count + 1
        found.add(candidate)
    found.update(re.findall(r'\b(?:id|name)=[\"\x27]([^\"\x27]+)', text, re.I))
    return found


def check(root, base):
    errors = []
    for required in ('README.md', 'AGENTS.md', 'docs/HANDOFF.md', 'docs/VALIDATION.md', 'docs/COST_MODEL.md'):
        if not (root / required).is_file():
            errors.append(f'{required}: required current entry must exist')
    files = git_paths(root, 'ls-files', '--cached', '--others', '--exclude-standard')
    added = git_paths(root, 'diff', '--no-renames', '--name-only', '--diff-filter=A', base, '--')
    added += git_paths(root, 'ls-files', '--others', '--exclude-standard')
    actual_base = git(root, 'rev-parse', base)[0]
    changed_archives = set(git_paths(root, 'diff', '--no-renames', '--name-only', base, '--', 'docs/archive/'))
    changed_archives.update(name for name in added if name.startswith('docs/archive/'))
    legacy_archives = set(git_paths(root, 'ls-tree', '-r', '--name-only', MIGRATION_BASE, '--', 'docs/archive/')) if actual_base == MIGRATION_BASE else set()
    for name in changed_archives:
        sanctioned_migration = actual_base == MIGRATION_BASE and (name in MIGRATION_ARCHIVES or (name in legacy_archives and not (root / name).exists()))
        if not sanctioned_migration:
            errors.append(f'{name}: frozen archive; additions, edits, deletions and renames require specific approval')
    for name in set(added):
        if name.startswith('docs/archive/'):
            continue
        # Governance covers documentation, not business code, fixtures or releases.
        if name in CURRENT or name.startswith(('app/src/test/', 'app/src/androidTest/', 'app/src/sharedTest/')):
            continue
        document_name = Path(name).name if Path(name).suffix else Path(name).name + '.md'
        parts = Path(name).parts
        directory_records = parts[1:-1] if parts[0] in ('docs', 'reports', 'notes') and Path(name).suffix.lower() in ('.md', '.markdown', '.txt', '.rst', '.adoc', '.asciidoc', '.html', '.htm', '.pdf', '.docx', '') else ()
        if RECORD.match(document_name) or any(RECORD.match(part + '.md') for part in directory_records) or re.match(r'^(?:release[_-]summary|new[_-]readme|新版介绍|发布总结)', Path(name).stem, re.I):
            errors.append(f'{name}: use the current document instead of a duplicate record')
    for name in sorted(set(files)):
        p = root / name
        if p.suffix.lower() not in ('.md', '.markdown') or not p.is_file():
            continue
        text = p.read_text(encoding='utf-8')
        text = without_fences(text)
        text = re.sub(r'<!--[\s\S]*?-->', '', text)
        text = re.sub(r'(`+)(?!`)(.*?)(?<!`)\1(?!`)', '', text, flags=re.S)
        refs = dict(re.findall(r'^\s*\[([^]]+)\]:\s*(\S+)', text, re.M))
        links = re.findall(r'!?\[[^]]*\]\(([^)]+)\)', text)
        links += re.findall(r'<(?:img|a)\b[^>]*?\b(?:src|href)=[\"\x27]([^\"\x27]+)', text, re.I)
        # Validate definitions directly: full, collapsed and shortcut references share these targets.
        links += list(refs.values())
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
            try:
                dest.resolve().relative_to(root.resolve())
            except ValueError:
                errors.append(f'{name}: local link escapes repository {target}')
                continue
            if name in MIGRATION_ARCHIVES and not dest.exists() and any(str(dest.resolve()).startswith(str((root / prefix).resolve()) + '/') for prefix in EXTERNAL_EVIDENCE):
                continue  # Existing local evidence pointers are not delivered or verified here.
            if not dest.exists():
                errors.append(f'{name}: missing local link {target}')
            elif parsed.fragment and dest.suffix.lower() in ('.md', '.markdown') and unquote(parsed.fragment) not in anchors(dest.read_text(encoding='utf-8')):
                errors.append(f'{name}: missing anchor {target}')
    changed = set(git_paths(root, 'diff', '--no-renames', '--name-only', base, '--')) | set(git_paths(root, 'ls-files', '--others', '--exclude-standard'))
    for name in sorted(changed):
        if not re.match(r'^README(?:[.\-_].+)?$', Path(name).name, re.I):
            continue
        exists = (root / name).is_file()
        marker = ('README_SHA256' if name == 'README.md' else 'README_SHA256 ' + name) if exists else 'README_DELETED_SHA256 ' + name
        if name not in changed:
            continue
        handoff = root / 'docs/HANDOFF.md'
        record = handoff.read_text(encoding='utf-8') if handoff.exists() else ''
        try:
            content = (root / name).read_bytes() if exists else subprocess.check_output(['git', '-C', str(root), 'show', f'{base}:{name}'], stderr=subprocess.DEVNULL)
            digest = hashlib.sha256(content).hexdigest()
        except (OSError, subprocess.CalledProcessError):
            digest = ''
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
        (root / 'AGENTS.md').write_text('# Rules\n')
        (root / 'docs').mkdir()
        for name in ['HANDOFF', 'VALIDATION', 'COST_MODEL']:
            (root / f'docs/{name}.md').write_text('# Current\n')
        run('add', '.'); run('commit', '-m', 'baseline')
        (root / 'docs').mkdir(exist_ok=True)
        (root / 'docs/HANDOFF.md').write_text('# Current\n')
        assert not check(root, 'HEAD')
        (root / 'docs/VALIDATION.md').unlink()
        assert any('required current entry' in e for e in check(root, 'HEAD'))
        (root / 'docs/VALIDATION.md').write_text('# Current\n')
        (root / 'reports').mkdir()
        (root / 'reports/README.md').write_text('# Auxiliary guide\n')
        run('add', '.'); run('commit', '-m', 'auxiliary readme')
        run('mv', 'reports/README.md', 'reports/notes.md')
        assert any('reports/README.md' in e and 'SHA256' in e for e in check(root, 'HEAD'))
        prior = hashlib.sha256(subprocess.check_output(['git', '-C', str(root), 'show', 'HEAD:reports/README.md'])).hexdigest()
        handoff = root / 'docs/HANDOFF.md'
        handoff.write_text(f'授权来源：synthetic rename; 适用基线：`{git(root, "rev-parse", "HEAD")[0]}`\nREADME_DELETED_SHA256 reports/README.md: {prior}\n')
        assert not check(root, 'HEAD')
        handoff.write_text(handoff.read_text().replace(prior, '0' * 64))
        assert any('SHA256' in e for e in check(root, 'HEAD'))
        run('mv', 'reports/notes.md', 'reports/README.md')
        handoff.write_text('# Current\n')
        (root / '中文目录').mkdir()
        (root / '中文目录/HANDOFF_2026.md').write_text('# Report\n')
        run('add', '.')
        assert any('中文目录/HANDOFF_2026.md' in e and 'duplicate' in e for e in check(root, 'HEAD'))
        run('rm', '--cached', '中文目录/HANDOFF_2026.md')
        (root / '中文目录/HANDOFF_2026.md').unlink()
        (root / '中文目录/指引.md').write_text('# Guide\n[bad](missing.md)\n')
        run('add', '.')
        assert any('中文目录/指引.md' in e and 'missing local link' in e for e in check(root, 'HEAD'))
        (root / '中文目录/指引.md').write_text('# Guide\n')
        (root / 'docs/HANDOFF.md').write_text('# Current\n[bad](#pretend)\n```md\n# pretend\n```\n')
        assert any('missing anchor' in e for e in check(root, 'HEAD'))
        (root / 'docs/HANDOFF.md').write_text('# Current\n[bad](#pretend)\n~~~md\n# pretend\n~~~\n')
        assert any('missing anchor' in e for e in check(root, 'HEAD'))
        (root / 'docs/HANDOFF.md').write_text('# Current\n')
        outside = root.parent / (root.name + '-outside.md')
        outside.write_text('# Outside\n')
        try:
            (root / 'docs/HANDOFF.md').write_text(f'[outside](../../{outside.name})\n')
            assert any('escapes repository' in e for e in check(root, 'HEAD'))
        finally:
            outside.unlink()
        for reference in ['[Guide][]', '[Guide]', '[text][guide]']:
            (root / 'docs/HANDOFF.md').write_text(reference + '\n[Guide]: missing.md\n')
            assert any('missing local link' in e for e in check(root, 'HEAD'))
            (root / 'docs/HANDOFF.md').write_text('# Current\n' + reference + '\n[Guide]: #current\n')
            assert not check(root, 'HEAD')
        (root / 'docs/HANDOFF.md').write_text('# Current\n')
        (root / 'docs/HANDOFF.md').write_text('# Current\n\n    [example](missing.md)\n')
        assert not check(root, 'HEAD')
        (root / 'docs/HANDOFF.md').write_text('# Current\n\n- Parent\n\n    - [real](missing.md)\n')
        assert any('missing local link' in e for e in check(root, 'HEAD'))
        (root / 'docs/HANDOFF.md').write_text('# Current\n\n<table>\n    <img src=\"missing.png\">\n</table>\n')
        assert any('missing local link' in e for e in check(root, 'HEAD'))
        (root / 'docs/HANDOFF.md').write_text("# Current\n<a href='missing.md'>real</a>\n")
        assert any('missing local link' in e for e in check(root, 'HEAD'))
        (root / 'docs/HANDOFF.md').write_text('# Current\n')
        (root / 'docs/HANDOFF.md').write_text('Guide\n=====\n\n# Guide\n\n[one](#guide) [two](#guide-1)\n')
        assert not check(root, 'HEAD')
        (root / 'docs/HANDOFF.md').write_text('Guide\n-----\n\n[bad](#guide-1)\n')
        assert any('missing anchor' in e for e in check(root, 'HEAD'))
        (root / 'docs/HANDOFF.md').write_text('# Current\n')
        (root / 'docs/HANDOFF.md').write_text('# Current\n`[example](missing.md)` and ``[example](missing.md)``\n')
        assert not check(root, 'HEAD')
        (root / 'docs/HANDOFF.md').write_text('# Current\n<A HREF=\"missing.md\">real</A>\n')
        assert any('missing local link' in e for e in check(root, 'HEAD'))
        (root / 'docs/HANDOFF.md').write_text('# Current\n')
        (root / 'reports/example.markdown').write_text('# Example\n[broken](missing.md)\n')
        assert any('example.markdown' in e for e in check(root, 'HEAD'))
        (root / 'reports/example.markdown').write_text('# Example\n')
        (root / 'docs/HANDOFF.md').write_text('# Current\n<!-- [example](missing.md) -->\n')
        assert not check(root, 'HEAD')
        (root / 'reports/file(1).md').write_text('# Encoded target\n')
        (root / 'docs/HANDOFF.md').write_text('# Current\n[encoded](../reports/file%281%29.md)\n')
        assert not check(root, 'HEAD')
        (root / 'docs/HANDOFF.md').write_text('# Current\n')
        (root / 'docs/HANDOFF.md').write_text('# foo\n# foo-1\n# foo\n[valid](#foo-2)\n')
        assert not check(root, 'HEAD')
        (root / 'docs/HANDOFF.md').write_text('# Current\n<!--\n# Hidden\n-->\n[bad](#hidden)\n')
        assert any('missing anchor' in e for e in check(root, 'HEAD'))
        (root / 'docs/HANDOFF.md').write_text('# Current\n')
        for duplicate in ['HANDOFF 2027.md', '2027.HANDOFF.md', 'VALIDATION.2.md', 'v2.0HANDOFF.md', 'HANDOFFv2.md', 'HANDOFF_2027', 'VALIDATION_2027.adoc', 'HANDOFF_2027.html']:
            candidate = root / 'reports' / duplicate
            candidate.write_text('# Report\n')
            assert any(duplicate in e and 'duplicate' in e for e in check(root, 'HEAD'))
            candidate.unlink()
        (root / 'docs/VALIDATION_2027').mkdir()
        (root / 'docs/VALIDATION_2027/index.md').write_text('# Annual record\n')
        assert any('duplicate' in e for e in check(root, 'HEAD'))
        (root / 'docs/VALIDATION_2027/index.md').unlink()
        (root / 'docs/VALIDATION_2027').rmdir()
        (root / 'docs/HANDOFF.md').write_text('# [Guide](../README.md)\n\n[valid](#guide)\n')
        assert not check(root, 'HEAD')
        (root / 'docs/HANDOFF.md').write_text('# [Guide][ref]\n\n[ref]: ../README.md\n[valid](#guide)\n[bad](#guide-1)\n')
        assert any('missing anchor' in e for e in check(root, 'HEAD'))
        (root / 'docs/HANDOFF.md').write_text('# Current\n')
        (root / 'docs/HANDOFF_1.md').write_text('old')
        assert any('duplicate' in e for e in check(root, 'HEAD'))
        (root / 'docs/HANDOFF_1.md').unlink()
        (root / 'docs/HANDOFF.md').write_text('[bad](missing.md)\n[bad](../README.md#absent)')
        assert len(check(root, 'HEAD')) == 2
        (root / 'docs/HANDOFF.md').write_text('# Current\n[bad](#absent)')
        assert any('missing anchor' in e for e in check(root, 'HEAD'))
        (root / 'reports').mkdir(exist_ok=True)
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
        (root / 'docs/archive/audit.md').write_text('# Original evidence\n')
        run('add', '.'); run('commit', '-m', 'frozen audit')
        (root / 'docs/archive/audit.md').write_text('# Overwritten evidence\n')
        assert any('frozen archive' in e for e in check(root, 'HEAD'))
        (root / 'docs/archive/audit.md').unlink()
        assert any('frozen archive' in e for e in check(root, 'HEAD'))
        (root / 'docs/archive/audit.md').write_text('# Original evidence\n')
        run('mv', 'docs/archive/audit.md', 'docs/archive/renamed.md')
        assert any('frozen archive' in e for e in check(root, 'HEAD'))
        run('mv', 'docs/archive/renamed.md', 'docs/archive/audit.md')
        (root / 'RELEASE.md').write_text('# Release\n')
        (root / 'reports/validation.py').write_text('# Business helper\n')
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
