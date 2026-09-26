import re
import sys
import xml.etree.ElementTree as ET

if len(sys.argv) != 3:
    raise SystemExit("usage: tap_text.py <xml-file> <text>")

xml_file, wanted = sys.argv[1], sys.argv[2]
root = ET.parse(xml_file).getroot()
for node in root.iter("node"):
    if node.attrib.get("text") != wanted:
        continue
    match = re.fullmatch(r"\[(\d+),(\d+)\]\[(\d+),(\d+)\]", node.attrib.get("bounds", ""))
    if match:
        x1, y1, x2, y2 = map(int, match.groups())
        print((x1 + x2) // 2, (y1 + y2) // 2)
        raise SystemExit(0)
raise SystemExit(f"text not found: {wanted}")
