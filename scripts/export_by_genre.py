"""Export the Calibre library as <out>/<Genre>/<Author> - <Title>.<ext>, embedding the corrected
metadata in each file, and write <books_dir>/export-map.tsv (calibre id, original device path, new path).

  calibre-debug -e scripts/export_by_genre.py -- <library_dir> <books_dir>
"""
import csv
import hashlib
import os
import re
import shutil
import sys

from calibre.library import db
from calibre.ebooks.metadata.meta import set_metadata

lib_dir, books_dir = sys.argv[1], sys.argv[2]
out = os.path.join(books_dir, "export")
if os.path.exists(out):
    shutil.rmtree(out)
api = db(lib_dir).new_api

orig = {}
with open(os.path.join(books_dir, "manifest.tsv"), encoding="utf-8") as f:
    for row in csv.DictReader(f, delimiter="\t"):
        orig[row["sha256"]] = row["device_path"]


def safe(s, n):
    s = re.sub(r'[\\/:*?"<>|]+', " ", s)
    s = re.sub(r"\s+", " ", s).strip(" .")
    return s[:n].rstrip(" .")


RANK = {"EPUB": 0, "MOBI": 1, "AZW3": 2, "PDF": 3, "DJVU": 4}
rows, used = [], set()
for bid in sorted(api.all_book_ids()):
    fmt = min(api.formats(bid), key=lambda x: RANK.get(x, 9))
    src = api.format_abspath(bid, fmt)
    digest = hashlib.sha256(open(src, "rb").read()).hexdigest()
    mi = api.get_metadata(bid)
    genre = api.field_for("#genre", bid) or "Other"
    author = " & ".join(mi.authors[:2]) if mi.authors else "Unknown"
    name = safe(f"{safe(author, 40)} - {mi.title}", 110)
    rel = f"{genre}/{name}.{fmt.lower()}"
    k = 2
    while rel.lower() in used:
        rel = f"{genre}/{name} ({k}).{fmt.lower()}"
        k += 1
    used.add(rel.lower())
    dst = os.path.join(out, *rel.split("/"))
    os.makedirs(os.path.dirname(dst), exist_ok=True)
    shutil.copy2(src, dst)
    if fmt in ("EPUB", "MOBI", "AZW3"):
        try:
            with open(dst, "r+b") as stream:
                set_metadata(stream, mi, fmt.lower())
        except Exception as e:  # keep the file as-is if metadata writing fails
            print("metadata not embedded:", rel, e)
    rows.append([bid, orig.get(digest, ""), rel])

with open(os.path.join(books_dir, "export-map.tsv"), "w", encoding="utf-8", newline="") as f:
    w = csv.writer(f, delimiter="\t")
    w.writerow(["calibre_id", "device_path", "new_rel_path"])
    w.writerows(rows)
print("exported", len(rows), "unmapped:", sum(1 for r in rows if not r[1]))
