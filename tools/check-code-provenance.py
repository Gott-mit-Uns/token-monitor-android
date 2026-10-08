#!/usr/bin/env python3
"""Reproducible same-path text retention; never an authorship or copyright estimate."""
import argparse
import difflib
import subprocess
from pathlib import Path
parser = argparse.ArgumentParser()
parser.add_argument('--base', default='65e6b04')
parser.add_argument('--output', default='docs/reviews/upstream-code-retention.md')
args = parser.parse_args()
base = subprocess.check_output(['git', 'rev-parse', args.base], text=True).strip()
files = list(Path('app/src/main').rglob('*.kt'))
def lines(text):
    return [line.strip() for line in text.splitlines() if line.strip() and not line.strip().startswith(('//', '*', '/*'))]
current_total = matched = original = unchanged = compared = 0
for file in files:
    text = file.read_text()
    current = lines(text)
    current_total += len(current)
    result = subprocess.run(['git', 'show', f'{base}:{file}'], capture_output=True, text=True)
    if result.returncode:
        continue
    compared += 1
    prior = lines(result.stdout)
    original += len(prior)
    matched += sum(block.size for block in difflib.SequenceMatcher(None, prior, current, autojunk=False).get_matching_blocks())
    unchanged += text == result.stdout
report = f'''# 安卓上游代码保留情况

比较基准：`The-Minion-oOo/token-monitor-android`，提交 `{base}`（0.67.0 r1）。
统计范围：`app/src/main` 下 Kotlin 生产代码；不包括测试、文档、图片及其他资源。

| 指标 | 数量 |
|---|---:|
| 当前文件 | {len(files)} |
| 与基准同路径的文件 | {compared} |
| 完全相同的文件 | {unchanged} |
| 当前有效行 | {current_total} |
| 可顺序匹配到基准的行 | {matched} |
| 匹配行占当前有效行 | {matched/current_total:.1%} |
| 纳入比较的基准有效行 | {original} |

方法：去除空行与整行注释，修剪首尾空白，同路径使用 SequenceMatcher 匹配。
该比例描述文本保留程度，不是著作权归属比例。跨文件移动、格式变化和重构可能降低匹配率，导入与通用语句也可能提高匹配率。资源、构建和依赖的继承没有包含在比例中。

本项目仍大量使用安卓上游基础，应继续称为基于上游的衍生项目并保留署名和 MIT 许可。
“从 0.68 起直接对齐桌面端”表示版本与功能更新依据改变，不表示脱离 fork 或完全重写。

重新计算：`python3 tools/check-code-provenance.py`。
'''
Path(args.output).write_text(report)
print(f'{len(files)} Kotlin files; {unchanged} unchanged; {matched/current_total:.1%} same-path text retention')
