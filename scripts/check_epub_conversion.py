"""Compare converted EPUBs with their MOBI originals: word counts (text kept?), cover and TOC present.

  calibre-debug -e scripts/check_epub_conversion.py -- <library_dir> <work_dir> [--ids 1,2,3]

Without --ids it checks every book that has both MOBI and EPUB. Writes <work_dir>/check.tsv.
"""
import os
import re
import subprocess
import sys
import zipfile
from concurrent.futures import ThreadPoolExecutor

from calibre.library import db

args = sys.argv[1:]
lib_dir, work = args[0], args[1]
only = {int(x) for x in args[args.index("--ids") + 1].split(",")} if "--ids" in args else None
api = db(lib_dir).new_api
convert = os.path.join(os.path.dirname(sys.executable), "ebook-convert.exe")
tmp = os.path.join(work, "txt")
os.makedirs(tmp, exist_ok=True)


def words(src, name):
    out = os.path.join(tmp, name + ".txt")
    subprocess.run([convert, src, out], capture_output=True)
    try:
        return len(open(out, encoding="utf-8", errors="replace").read().split())
    except OSError:
        return 0


def check(bid):
    mobi, epub = api.format_abspath(bid, "MOBI"), api.format_abspath(bid, "EPUB")
    wm, we = words(mobi, f"{bid}-m"), words(epub, f"{bid}-e")
    z = zipfile.ZipFile(epub)
    names = z.namelist()
    ncx = [n for n in names if n.endswith(".ncx")]
    toc = len(re.findall(rb"<navPoint", z.read(ncx[0]))) if ncx else 0
    cover = any(re.search(r"cover", n, re.I) and n.lower().endswith((".jpg", ".jpeg", ".png")) for n in names)
    ratio = we / wm if wm else 0
    title = api.field_for("title", bid)
    return bid, title, wm, we, round(ratio, 3), cover, toc


ids = [b for b in sorted(api.all_book_ids()) if {"MOBI", "EPUB"} <= set(api.formats(b)) and (only is None or b in only)]
bad = 0
with open(os.path.join(work, "check.tsv"), "w", encoding="utf-8") as f, ThreadPoolExecutor(4) as pool:
    f.write("id\ttitle\tmobi_words\tepub_words\tratio\tcover\ttoc_entries\n")
    for row in pool.map(check, ids):
        f.write("\t".join(map(str, row)) + "\n")
        bid, title, wm, we, ratio, cover, toc = row
        flag = "" if 0.97 <= ratio <= 1.03 and cover else "  <-- CHECK"
        bad += bool(flag)
        print(f"{bid:4} {ratio:6} cover={cover!s:5} toc={toc:3}  {title[:50]}{flag}", flush=True)
print(f"{len(ids)} checked, {bad} flagged")
