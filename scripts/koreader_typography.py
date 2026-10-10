"""Set KOReader reading defaults and clear per-book overrides of the same options.

usage: python koreader_typography.py <settings.reader.lua> <sdr_root_dir>
- settings.reader.lua: sets the global defaults below (adds keys if missing).
- every metadata.*.lua under <sdr_root_dir>: removes per-book font/size/spacing/margin keys so
  the new defaults apply. Reading position, highlights, bookmarks and stats are untouched.
"""
import glob
import os
import re
import sys

DEFAULTS = {
    "cre_font": '"Literata"',
    "copt_font_size": "23",
    "copt_line_spacing": "110",
    "copt_h_page_margins": "{\n        [1] = 12,\n        [2] = 12,\n    }",
    "copt_t_page_margin": "10",
    "copt_b_page_margin": "10",
    "copt_sync_t_b_page_margins": "0",
    "copt_embedded_css": "1",    # keep the publisher's layout: indents, alignment, headings
    "copt_embedded_fonts": "0",  # but read everything in Literata
}
PER_BOOK = ["font_face", "copt_font_size", "copt_line_spacing", "copt_h_page_margins",
            "copt_t_page_margin", "copt_b_page_margin", "copt_embedded_fonts"]


def key_pattern(key):
    # a top-level entry: scalar value, or a one-level table spanning lines
    return re.compile(r'^    \["%s"\] = (?:\{\n(?:.*\n)*?    \}|[^\n]*),\n' % re.escape(key), re.M)


def set_defaults(path):
    s = open(path, encoding="utf-8").read()
    for key, value in DEFAULTS.items():
        entry = '    ["%s"] = %s,\n' % (key, value)
        s, n = key_pattern(key).subn(lambda m: entry, s)
        if n == 0:  # insert just after "return {"
            s = s.replace("return {\n", "return {\n" + entry, 1)
    open(path, "w", encoding="utf-8", newline="\n").write(s)


def clear_overrides(root):
    changed = 0
    for path in glob.glob(os.path.join(root, "**", "*.sdr", "metadata.*.lua"), recursive=True):
        if path.endswith(".old"):
            continue
        s = open(path, encoding="utf-8").read()
        new = s
        for key in PER_BOOK:
            new = key_pattern(key).sub("", new)
        if new != s:
            open(path, "w", encoding="utf-8", newline="\n").write(new)
            changed += 1
    return changed


if __name__ == "__main__":
    set_defaults(sys.argv[1])
    print("per-book overrides cleared in", clear_overrides(sys.argv[2]), "books")
