#!/usr/bin/env python3
"""Verify that every current Serif UI string has a glyph in editor_serif.ttf."""

from __future__ import annotations

import argparse
import hashlib
import json
import re
from pathlib import Path

from fontTools.ttLib import TTFont


ROOT = Path(__file__).resolve().parents[1]
SOURCE_ROOT = ROOT / "app/src/main/java/studio/pianyu/app"
FONT_PATH = ROOT / "app/src/main/res/font/editor_serif.ttf"
METADATA_PATH = ROOT / "asset-provenance/font-coverage.json"


def kotlin_strings(pattern: str, text: str) -> list[str]:
    return re.findall(pattern, text, flags=re.MULTILINE)


def collect_strings() -> tuple[list[str], list[str], list[str]]:
    serif_strings: set[str] = set()
    look_names: set[str] = set()
    unknown_calls: list[str] = []
    files = list(SOURCE_ROOT.rglob("*.kt"))
    dynamic_look_render = False
    for path in files:
        text = path.read_text(encoding="utf-8")
        # Keep this deliberately small and source-shaped: Serif calls in this
        # project use a string literal on the same Text(...) expression line.
        for value in kotlin_strings(r'Text\(\s*"([^"\\]*(?:\\.[^"\\]*)*)"[^\n]*fontFamily\s*=\s*Serif', text):
            serif_strings.add(value)
        for line_no, line in enumerate(text.splitlines(), 1):
            if re.search(r"fontFamily\s*=\s*Serif", line):
                if re.search(r"Text\(\s*look\.name", line):
                    dynamic_look_render = True
                elif not re.search(r'Text\(\s*"([^"\\]*(?:\\.[^"\\]*)*)"', line):
                    unknown_calls.append(f"{path.relative_to(ROOT)}:{line_no}")
    # Collect names after finding the renderer so filesystem order cannot hide names.
    if dynamic_look_render:
        for path in files:
            text = path.read_text(encoding="utf-8")
            look_names.update(kotlin_strings(r'Look\(\s*"[^"]+"\s*,\s*"([^"]+)"', text))
    return sorted(serif_strings), sorted(look_names), sorted(unknown_calls)


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--font", type=Path, default=FONT_PATH)
    args = parser.parse_args()
    static_strings, look_names, unknown_calls = collect_strings()
    required = sorted(set(static_strings + look_names))
    font = TTFont(str(args.font), lazy=False)
    cmap = font.getBestCmap() or {}
    metadata = json.loads(METADATA_PATH.read_text(encoding="utf-8"))
    old_chars = metadata["old_font_characters"]
    old_missing = sorted(set(old_chars) - {chr(codepoint) for codepoint in cmap})
    weight_ok = font["OS/2"].usWeightClass == 600
    missing = sorted({char for value in required for char in value if ord(char) not in cmap})
    names = {record.toUnicode() for record in font["name"].names if record.nameID in (1, 2)}
    family_ok = any("Noto Serif SC" in name for name in names)
    result = {
        "font": str(args.font),
        "font_sha256": hashlib.sha256(args.font.read_bytes()).hexdigest(),
        "font_bytes": args.font.stat().st_size,
        "family_names": sorted(names),
        "family_ok": family_ok,
        "weight": font["OS/2"].usWeightClass,
        "weight_ok": weight_ok,
        "static_serif_strings": static_strings,
        "look_names": look_names,
        "required_string_count": len(required),
        "required_codepoint_count": len({ord(char) for value in required for char in value}),
        "old_font_codepoint_count": len(set(old_chars)),
        "old_font_missing_characters": old_missing,
        "missing_characters": missing,
        "unrecognized_serif_calls": unknown_calls,
        "status": "PASS" if family_ok and weight_ok and not missing and not unknown_calls and not old_missing else "FAIL",
    }
    print(json.dumps(result, ensure_ascii=False, indent=2))
    if missing:
        print("MISSING: " + " ".join(missing))
    if not family_ok:
        print("FAIL: font family does not contain Noto Serif SC")
    if not weight_ok:
        print(f"FAIL: expected weight 600, got {font['OS/2'].usWeightClass}")
    if unknown_calls:
        print("UNRECOGNIZED_SERIF_CALLS: " + ", ".join(unknown_calls))
    if old_missing:
        print("OLD_FONT_GLYPHS_MISSING: " + " ".join(old_missing))
    return 0 if result["status"] == "PASS" else 1


if __name__ == "__main__":
    raise SystemExit(main())
