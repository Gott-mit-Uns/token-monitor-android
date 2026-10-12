"""Check source resource completeness and formatting without reading private application data."""
from pathlib import Path
import re
import xml.etree.ElementTree as ET

root = Path(__file__).resolve().parent.parent / "app/src/main/res"

def catalog(folder):
    entries = {}
    for element in ET.parse(root / folder / "strings.xml").getroot():
        name = element.attrib["name"]
        assert name not in entries, f"Duplicate resource: {name}"
        entries[name] = element
    return entries

def formats(value):
    return sorted(re.findall(r"%[0-9]+\$[a-zA-Z]", value or ""))

en, zh = catalog("values"), catalog("values-zh")
assert en.keys() == zh.keys(), "English and Chinese resource keys differ"
for name, item in en.items():
    other = zh[name]
    assert item.tag == other.tag, f"Resource kinds differ: {name}"
    if item.tag == "string":
        assert item.text and other.text, f"Empty text: {name}"
        assert formats(item.text) == formats(other.text), f"Format placeholders differ: {name}"
        # The desktop font preview deliberately includes CJK glyphs in every language.
        if name == "appearance_font_sample":
            assert item.text == "Aa 中文 0123", "Font preview must remain the fixed multilingual sample"
        else:
            assert name == "language_chinese" or not re.search(r"[\u3400-\u9fff]", item.text), f"Untranslated English UI text: {name}"
    elif item.tag == "plurals":
        assert any(v.attrib["quantity"] == "other" for v in item), f"Missing other plural: {name}"
        assert any(v.attrib["quantity"] == "other" for v in other), f"Missing Chinese plural: {name}"
        assert all(formats(v.text) == formats(item[0].text) for v in list(item) + list(other)), f"Plural formats differ: {name}"
print(f"Validated {len(en)} paired English/Chinese resources")
