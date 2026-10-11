"""Replace the .mobi files in the synced Books folder with the EPUBs now in Calibre (same name, .epub),
with Calibre's corrected metadata embedded, and carry KOReader reading progress over.

  calibre-debug -e scripts/swap_mobi_for_epub.py -- <library_dir> <books_dir> <synced_books_dir> [--dry-run]

books_dir holds export-map.tsv (calibre id -> relative path); it is updated to the new .epub paths.
For an opened book, <name>.sdr/metadata.mobi.lua is kept and a metadata.epub.lua is written with
last_percent (KOReader reopens at that percentage; xpointers don't carry across formats) and the summary.
Calibre keeps the MOBI originals, so the .mobi files are simply removed from the synced folder.
"""
import csv
import os
import re
import shutil
import sys

from calibre.library import db
from calibre.ebooks.metadata.meta import set_metadata

args = sys.argv[1:]
lib_dir, books_dir, synced = args[0], args[1], args[2]
dry = "--dry-run" in args
api = db(lib_dir).new_api

map_path = os.path.join(books_dir, "export-map.tsv")
with open(map_path, encoding="utf-8") as f:
    rows = list(csv.reader(f, delimiter="\t"))
header, rows = rows[0], rows[1:]


def sidecar(base):
    old = os.path.join(base + ".sdr", "metadata.mobi.lua")
    new = os.path.join(base + ".sdr", "metadata.epub.lua")
    if not os.path.exists(old) or os.path.exists(new):
        return False
    text = open(old, encoding="utf-8").read()
    m = re.search(r'\["percent_finished"\] = ([0-9.eE-]+)', text)
    pct = m.group(1) if m else "0"
    s = re.search(r'(    \["summary"\] = \{.*?\n    \},)', text, re.S)
    out = "-- converted from metadata.mobi.lua (MOBI -> EPUB, 2026-10-11)\nreturn {\n"
    out += f'    ["last_percent"] = {pct},\n    ["percent_finished"] = {pct},\n'
    if s:
        out += s.group(1) + "\n"
    out += "}\n"
    if not dry:
        with open(new, "w", encoding="utf-8", newline="\n") as f:
            f.write(out)
    return True


swapped = missing = kept = progress = 0
for row in rows:
    bid, rel = int(row[0]), row[2]
    if not rel.lower().endswith(".mobi"):
        continue
    if "EPUB" not in api.formats(bid):
        kept += 1
        print("kept as MOBI (no EPUB):", rel)
        continue
    src_mobi = os.path.join(synced, *rel.split("/"))
    base = src_mobi[:-5]
    if not os.path.exists(src_mobi):
        missing += 1
        print("not in synced folder:", rel)
        continue
    dst = base + ".epub"
    if not dry:
        shutil.copy2(api.format_abspath(bid, "EPUB"), dst)
        with open(dst, "r+b") as stream:
            set_metadata(stream, api.get_metadata(bid), "epub")
    progress += sidecar(base)
    if not dry:
        os.remove(src_mobi)
    row[2] = rel[:-5] + ".epub"
    swapped += 1

if not dry:
    with open(map_path, "w", encoding="utf-8", newline="") as f:
        w = csv.writer(f, delimiter="\t")
        w.writerow(header)
        w.writerows(rows)
print(f"{'DRY RUN: ' if dry else ''}swapped {swapped}, reading progress carried {progress}, "
      f"kept as MOBI {kept}, missing {missing}")
