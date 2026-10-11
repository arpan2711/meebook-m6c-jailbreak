"""Convert every Calibre book that has MOBI but no EPUB to EPUB, and add the EPUB as an extra format
(the MOBI stays in the library as the original). Run with Calibre closed:

  calibre-debug -e scripts/convert_mobi_to_epub.py -- <library_dir> <work_dir> [--limit N] [--ids 1,2,3]

Conversions run 4 at a time with ebook-convert. Results: <work_dir>/convert-log.tsv (id, status, seconds, note).
"""
import os
import subprocess
import sys
import time
from concurrent.futures import ThreadPoolExecutor

from calibre.library import db

args = sys.argv[1:]
lib_dir, work = args[0], args[1]
limit = int(args[args.index("--limit") + 1]) if "--limit" in args else None
only = {int(x) for x in args[args.index("--ids") + 1].split(",")} if "--ids" in args else None
os.makedirs(work, exist_ok=True)
api = db(lib_dir).new_api
convert = os.path.join(os.path.dirname(sys.executable), "ebook-convert.exe")

todo = []
for bid in sorted(api.all_book_ids()):
    fmts = api.formats(bid)
    if "MOBI" in fmts and "EPUB" not in fmts and (only is None or bid in only):
        todo.append((bid, api.format_abspath(bid, "MOBI")))
if limit:
    todo = todo[:limit]
print(f"{len(todo)} books to convert", flush=True)


def run(item):
    bid, src = item
    out = os.path.join(work, f"{bid}.epub")
    t = time.time()
    p = subprocess.run([convert, src, out], capture_output=True, text=True, encoding="utf-8", errors="replace")
    ok = p.returncode == 0 and os.path.exists(out) and os.path.getsize(out) > 1000
    note = "" if ok else (p.stderr or p.stdout).strip().splitlines()[-1][:200] if (p.stderr or p.stdout) else "no output"
    return bid, ok, round(time.time() - t, 1), note


done = 0
with open(os.path.join(work, "convert-log.tsv"), "a", encoding="utf-8") as log, ThreadPoolExecutor(4) as pool:
    for bid, ok, secs, note in pool.map(run, todo):
        done += 1
        if ok:
            api.add_format(bid, "EPUB", os.path.join(work, f"{bid}.epub"), replace=False)
        log.write(f"{bid}\t{'ok' if ok else 'FAIL'}\t{secs}\t{note}\n")
        log.flush()
        print(f"[{done}/{len(todo)}] {bid} {'ok' if ok else 'FAIL ' + note} {secs}s", flush=True)
print("done", flush=True)
