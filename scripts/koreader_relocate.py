"""Point KOReader at the new genre library: rewrite history.lua file paths and set home_dir/lastdir/lastfile.

usage: python koreader_relocate.py <books_dir> <cfg_dir>
<cfg_dir>/orig/{settings.reader.lua,history.lua} are the pulled originals; fixed copies go to <cfg_dir>/.
"""
import csv
import json
import os
import re
import sys

books_dir, cfg = sys.argv[1], sys.argv[2]
SD, EMU, NEWROOT = "/sdcard/", "/storage/emulated/0/", "/storage/emulated/0/Books/"


def tsv(name):
    with open(os.path.join(books_dir, name), encoding="utf-8") as f:
        return list(csv.DictReader(f, delimiter="\t"))


rows, man = tsv("export-map.tsv"), tsv("manifest.tsv")
sha = {r["device_path"]: r["sha256"] for r in man}
kept_by_sha = {}
for r in rows:
    kept_by_sha.setdefault(sha.get(r["device_path"]), r)

new = {}
for r in man:  # duplicates (same content) point at the kept copy
    k = kept_by_sha.get(r["sha256"])
    if k:
        new[r["device_path"].replace(SD, EMU, 1)] = NEWROOT + k["new_rel_path"]
for r in rows:
    new[r["device_path"].replace(SD, EMU, 1)] = NEWROOT + r["new_rel_path"]


def norm(s):
    return re.sub(r"[^A-Za-z0-9]+", "", s)


new_norm = {norm(k): v for k, v in new.items()}
LUA_STR = r'"((?:[^"\\]|\\.)*)"'
stats = {"mapped": 0, "unmapped": 0}


def lookup(path):
    return new.get(path) or new_norm.get(norm(path))


def fix_file(m):
    target = lookup(m.group(2))
    if target:
        stats["mapped"] += 1
        return m.group(1) + json.dumps(target, ensure_ascii=False)
    stats["unmapped"] += 1
    return m.group(0)


with open(os.path.join(cfg, "orig", "history.lua"), encoding="utf-8") as f:
    hist = f.read()
hist = re.sub(r'(\["file"\]\s*=\s*)' + LUA_STR, fix_file, hist)
with open(os.path.join(cfg, "history.lua"), "w", encoding="utf-8", newline="\n") as f:
    f.write(hist)

with open(os.path.join(cfg, "orig", "settings.reader.lua"), encoding="utf-8") as f:
    st = f.read()
for key in ("home_dir", "lastdir"):
    st = re.sub(r'(\["%s"\]\s*=\s*)' % key + LUA_STR, lambda m: m.group(1) + '"/storage/emulated/0/Books"', st)
st = re.sub(r'(\["lastfile"\]\s*=\s*)' + LUA_STR,
            lambda m: m.group(1) + json.dumps(lookup(m.group(2)) or m.group(2), ensure_ascii=False), st)
with open(os.path.join(cfg, "settings.reader.lua"), "w", encoding="utf-8", newline="\n") as f:
    f.write(st)
print("history entries mapped:", stats["mapped"], "unmapped:", stats["unmapped"])
