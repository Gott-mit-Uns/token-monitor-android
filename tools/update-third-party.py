"""Create/check bundled notices from exact runtime artifacts and their cached Maven POMs.

Run :app:writeRuntimeInventory first. This tool is offline and inspects public
dependency artifacts only; it never reads connection files or application data.
"""
import argparse
import csv
import hashlib
import io
import os
from pathlib import Path
import xml.etree.ElementTree as ET
import zipfile

ROOT = Path(__file__).resolve().parents[1]
def local_path(value):
    """Support Windows cache paths beyond MAX_PATH without moving user files."""
    value = os.path.abspath(value)
    if os.name == "nt" and not value.startswith("\\\\?\\"):
        value = "\\\\?\\UNC\\" + value[2:] if value.startswith("\\\\") else "\\\\?\\" + value
    return Path(value)

CACHE = local_path(os.environ.get("GRADLE_USER_HOME", str(Path.home() / ".gradle"))) / "caches/modules-2/files-2.1"
NS = {"m": "http://maven.apache.org/POM/4.0.0"}


def metadata(group, name, version, seen=()):
    coordinate = (group, name, version)
    if coordinate in seen:
        raise ValueError("Cyclic Maven parent metadata")
    poms = sorted((CACHE / group / name / version).glob("*/*.pom"))
    if len(poms) != 1:
        raise ValueError(f"One cached public POM required for {group}:{name}:{version}")
    root = ET.parse(poms[0]).getroot()
    parent = root.find("m:parent", NS)
    inherited = metadata(*(parent.findtext("m:" + field, namespaces=NS) for field in ("groupId", "artifactId", "version")),
                         seen=(*seen, coordinate)) if parent is not None else {}
    licenses = [(item.findtext("m:name", "", NS), item.findtext("m:url", "", NS))
                for item in root.findall("m:licenses/m:license", NS)]
    developers = [item.findtext("m:name", "", NS) for item in root.findall("m:developers/m:developer", NS)]
    return {"licenses": licenses or inherited.get("licenses", []),
            "project": root.findtext("m:url", "", NS) or inherited.get("project", ""),
            "developers": developers or inherited.get("developers", [])}


def archive_notices(archive):
    for name in sorted(archive.namelist()):
        leaf = Path(name).name.upper()
        if name.startswith("META-INF/") and not name.endswith("/") and any(word in leaf for word in ("LICENSE", "NOTICE", "COPYRIGHT")):
            yield name, archive.read(name).decode("utf-8")
        if name == "classes.jar" or (name.startswith("libs/") and name.endswith(".jar")):
            with zipfile.ZipFile(io.BytesIO(archive.read(name))) as nested:
                for nested_name, content in archive_notices(nested):
                    yield name + "/" + nested_name, content


def generate():
    rows = list(csv.reader((ROOT / "app/build/third-party/runtime-artifacts.tsv").open(), delimiter="\t"))
    if not rows:
        raise ValueError("Runtime inventory is empty")
    bundled = {}
    entries = []
    for group, name, version, artifact in rows:
        coordinate = f"{group}:{name}:{version}"
        info = metadata(group, name, version)
        if not info["licenses"] or any("apache.org/licenses/LICENSE-2.0" not in url for _, url in info["licenses"]):
            raise ValueError(f"Review new or unknown license before packaging: {coordinate}")
        entries.append((coordinate, info))
        with zipfile.ZipFile(local_path(artifact)) as archive:
            for filename, content in archive_notices(archive):
                digest = hashlib.sha256(content.encode()).hexdigest()
                record = bundled.setdefault(digest, {"text": content, "sources": []})
                record["sources"].append(f"{coordinate} / {filename}")
    if not any("END OF TERMS AND CONDITIONS" in record["text"] for record in bundled.values()):
        raise ValueError("Complete Apache license text missing from runtime artifacts")
    intro = ("Token Monitor Android — source and open-source notices\n\n"
             "Independently implemented in Kotlin/Compose, using the Token Monitor Hub protocol and display semantics.\n"
             "Upstream: https://github.com/Javis603/token-monitor\n"
             "Hub build registry and comparison semantics: Token Monitor upstream (MIT).\n"
             "No business source code from the previous Android fork is included.\n\n"
             "Token Monitor upstream MIT license\n\n")
    text = intro + (ROOT / "app/src/main/assets/token-monitor-upstream-MIT.txt").read_text()
    text += "\n\n" + (ROOT / "app/src/main/assets/desktop-asset-notices.txt").read_text()
    text += "\n\n" + (ROOT / "app/src/main/assets/font-notices.txt").read_text() + "\n\nRuntime dependencies\n\n"
    for coordinate, info in entries:
        attribution = ", ".join(info["developers"])
        text += coordinate + "\nApache License 2.0\n"
        if info["project"]:
            text += info["project"] + "\n"
        if attribution:
            text += "Publisher attribution: " + attribution + "\n"
        text += "\n"
    for number, record in enumerate(bundled.values(), start=1):
        text += f"\nIncluded license or notice text {number}\nProvided by:\n" + "\n".join(record["sources"]) + "\n\n" + record["text"] + "\n"
    doc = ("# 随包运行依赖许可\n\n"
           "由精确解析的 debug/release 运行依赖生成，不包括 Gradle 构建工具、测试库或 Android 系统本身。"
           "各依赖的许可来自对应 Maven POM（含父 POM 继承）；发布者名称来自 POM。"
           "二进制中提供的 LICENSE/NOTICE/COPYRIGHT 原文按内容去重后保留，未改写。\n\n"
           "完整文字随 APK 保存在 `assets/open-source-notices.txt`，设置页可离线阅读；"
           "上游 MIT 原文另存于 `assets/token-monitor-upstream-MIT.txt`。本表不为本项目原创代码指定许可证。\n\n"
           "| 运行依赖 | 许可 | 项目来源 |\n|---|---|---|\n")
    for coordinate, info in entries:
        doc += f"| {coordinate} | Apache-2.0 | {info['project']} |\n"
    doc += ("\n另随包提供 Inter、JetBrains Mono、Noto Sans SC 的 SIL OFL 1.1 字体派生资源。"
            "界面字体 Token UI 合并 Inter 与常用中文子集，数据字体 Token Data 来自 JetBrains Mono；"
            "原始许可、版权和来源随 `assets/font-notices.txt` 及完整离线许可清单提供。"
            "字体不是 Maven 运行依赖，不计入上表数量。指纹与覆盖见 `design/font-preview/bundled-manifest.json`。\n")
    doc += ("\n更新依赖后重新运行：\n\n```\n./gradlew :app:writeRuntimeInventory --offline\n"
            "python3 tools/update-third-party.py\npython3 tools/update-third-party.py --check\n```\n")
    return {ROOT / "app/src/main/assets/open-source-notices.txt": text, ROOT / "docs/DEPENDENCY_LICENSES.md": doc}, len(entries), len(bundled)


if __name__ == "__main__":
    parser = argparse.ArgumentParser()
    parser.add_argument("--check", action="store_true")
    arguments = parser.parse_args()
    files, count, notices = generate()
    for path, content in files.items():
        if arguments.check:
            if not path.exists() or path.read_text() != content:
                raise SystemExit("Bundled dependency notices are stale; regenerate before packaging")
        else:
            path.write_text(content)
    print(f"{'Validated' if arguments.check else 'Generated'} notices for {count} runtime artifacts; {notices} distinct provided license/notice texts")
