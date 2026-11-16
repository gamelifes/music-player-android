"""Dump tappable nodes (text / content-desc + bounds) from a uiautomator xml.

  adb shell uiautomator dump /sdcard/ui.xml && adb pull /sdcard/ui.xml design/splash/ui.xml
  python design/splash/dump_ui.py
"""
import re
import sys
from pathlib import Path

p = Path(sys.argv[1] if len(sys.argv) > 1 else "design/splash/ui.xml")
s = p.read_text(encoding="utf-8", errors="replace")
print("xml bytes:", len(s))

pat = re.compile(r'<node[^>]*>')
for node in pat.findall(s):
    text = re.search(r'text="([^"]*)"', node)
    desc = re.search(r'content-desc="([^"]*)"', node)
    bounds = re.search(r'bounds="\[(\d+),(\d+)\]\[(\d+),(\d+)\]"', node)
    label = (text.group(1) if text else "") or (desc.group(1) if desc else "")
    if not label or not bounds:
        continue
    x1, y1, x2, y2 = map(int, bounds.groups())
    print(f"{label!r:24} center=({(x1 + x2) // 2},{(y1 + y2) // 2})  bounds=[{x1},{y1}][{x2},{y2}]")
