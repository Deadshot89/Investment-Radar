#!/usr/bin/env python3
import argparse
import re
import xml.etree.ElementTree as ET

parser = argparse.ArgumentParser()
parser.add_argument("xml")
parser.add_argument("--present", action="append", default=[])
parser.add_argument("--absent", action="append", default=[])
args = parser.parse_args()

root = ET.parse(args.xml).getroot()


def area(bounds: str) -> int:
    match = re.fullmatch(r"\[(\d+),(\d+)\]\[(\d+),(\d+)\]", bounds or "")
    if not match:
        return -1
    x1, y1, x2, y2 = map(int, match.groups())
    return max(0, x2 - x1) * max(0, y2 - y1)


grids = [node for node in root.iter("node") if node.attrib.get("class") == "android.widget.GridView"]
if not grids:
    raise SystemExit("drawer GridView not found")

# The drawer grid fills the main drawer content area. The homescreen favorites
# grid remains in the accessibility tree behind the translucent drawer but is
# substantially smaller, so global text searches would create false positives.
drawer = max(grids, key=lambda node: area(node.attrib.get("bounds", "")))
texts = [node.attrib.get("text", "") for node in drawer.iter("node") if node.attrib.get("text")]

for wanted in args.present:
    if wanted not in texts:
        raise SystemExit(f"expected drawer text missing: {wanted}; drawer={texts!r}")
for forbidden in args.absent:
    if forbidden in texts:
        raise SystemExit(f"forbidden drawer text present: {forbidden}; drawer={texts!r}")

print("PASS: drawer grid contents", texts)
