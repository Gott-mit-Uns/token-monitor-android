"""Archive passing JUnit cases/counts without private diagnostics or runner properties."""
import argparse
import hashlib
from pathlib import Path
import xml.etree.ElementTree as ET

parser = argparse.ArgumentParser()
parser.add_argument("--source", type=Path, required=True)
parser.add_argument("--output", type=Path, required=True)
parser.add_argument("--expected-tests", type=int, required=True)
parser.add_argument("--scope", required=True)
parser.add_argument("--apk", type=Path)
args = parser.parse_args()
sources = sorted(args.source.glob("TEST-*.xml"))
if not sources:
    raise SystemExit("No current JUnit reports to archive")
reports = [ET.parse(path).getroot() for path in sources]
counts = {key: sum(int(root.get(key, "0")) for root in reports) for key in ("tests", "failures", "errors", "skipped")}
if counts != {"tests": args.expected_tests, "failures": 0, "errors": 0, "skipped": 0}:
    raise SystemExit("Current reports do not prove the expected passing suite")
out = ET.Element("testsuites", {**{key: str(value) for key, value in counts.items()}, "evidence": args.scope})
if args.apk is not None:
    out.set("apkSha256", hashlib.sha256(args.apk.read_bytes()).hexdigest())
for root in reports:
    # Android Gradle Plugin wraps per-class suites inside a testsuites root.
    for original in root.iter("testsuite"):
        cases = original.findall("testcase")
        if not cases:
            continue
        suite = ET.SubElement(out, "testsuite", {key: value for key, value in original.attrib.items()
                              if key in ("name", "tests", "failures", "errors", "skipped", "time")})
        for case in cases:
            if any(child.tag in ("failure", "error", "skipped") for child in case):
                raise SystemExit("A case is not passing; do not archive it as successful")
            ET.SubElement(suite, "testcase", {key: value for key, value in case.attrib.items()
                          if key in ("classname", "name", "time")})
if len(list(out.iter("testcase"))) != args.expected_tests:
    raise SystemExit("Case list does not match the report counts")
ET.indent(out)
args.output.parent.mkdir(parents=True, exist_ok=True)
ET.ElementTree(out).write(args.output, encoding="utf-8", xml_declaration=True)
print(f"Archived {args.expected_tests} passing cases for {args.scope}")
