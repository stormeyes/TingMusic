#!/usr/bin/env python3
"""Convert all Japanese .lrc files under a directory so their text is fully
in hiragana (kanji + katakana → hiragana). Time-stamped headers like
[mm:ss.xx] and metadata tags like [ti:..] / [ar:..] are preserved verbatim.

Usage:
    python3 scripts/lrc_to_hiragana.py [dir]

Default dir = ~/Music/TingMusic. Backs up each modified file as <name>.bak.
"""

from pathlib import Path
import re
import shutil
import sys
import pykakasi

SRC_DIR = Path(sys.argv[1] if len(sys.argv) > 1 else "~/Music/TingMusic").expanduser()

# Match a single timestamp or metadata tag at the start of a line
# (eg. "[00:23.50]" or "[ti:Song]") so we leave the bracket content alone.
TAG_RE = re.compile(r"^(\[[^\]]*\])+")


def has_japanese(text: str) -> bool:
    """True if text contains hiragana (U+3040..U+309F) or katakana (U+30A0..U+30FF)."""
    for ch in text:
        cp = ord(ch)
        if 0x3040 <= cp <= 0x309F or 0x30A0 <= cp <= 0x30FF:
            return True
    return False


def to_hiragana(kks, text: str) -> str:
    """Convert all kanji + katakana segments in text to hiragana, preserving
    spacing, punctuation, and any non-Japanese characters."""
    if not text.strip():
        return text
    parts = kks.convert(text)
    return "".join(p.get("hira", "") or p.get("orig", "") for p in parts)


def process_lrc(path: Path, kks) -> bool:
    raw = path.read_text(encoding="utf-8")
    if not has_japanese(raw):
        return False  # English / Chinese / other — skip

    out_lines = []
    for line in raw.splitlines():
        m = TAG_RE.match(line)
        if m:
            head = m.group(0)
            body = line[m.end():]
            out_lines.append(head + to_hiragana(kks, body))
        else:
            out_lines.append(to_hiragana(kks, line))

    new_text = "\n".join(out_lines)
    if raw.endswith("\n"):
        new_text += "\n"

    backup = path.with_suffix(path.suffix + ".bak")
    if not backup.exists():
        shutil.copy2(path, backup)
    path.write_text(new_text, encoding="utf-8")
    return True


def main():
    if not SRC_DIR.is_dir():
        print(f"not a directory: {SRC_DIR}")
        sys.exit(1)

    kks = pykakasi.kakasi()
    converted = 0
    skipped = 0
    for path in sorted(SRC_DIR.rglob("*.lrc")):
        if path.suffix == ".bak" or path.name.endswith(".lrc.bak"):
            continue
        if process_lrc(path, kks):
            print(f"converted: {path.name}")
            converted += 1
        else:
            print(f"skipped (not japanese): {path.name}")
            skipped += 1
    print(f"\ndone — converted {converted}, skipped {skipped}.")


if __name__ == "__main__":
    main()
