"""Apply books/curation.json to the Calibre library. Run with Calibre's interpreter:

  calibre-debug -e scripts/apply_curation.py -- <library_dir> <books_dir>

1. backs up duplicate copies to <books_dir>/duplicates-backup, then removes them from the library
   (keeper per group: EPUB > MOBI > AZW3 > PDF, then the larger file)
2. fixes titles/authors
3. creates a #genre column and sets every book's shelf (rules from genre_map.py + manual overrides)
"""
import json
import os
import shutil
import sys

lib_dir, books_dir = sys.argv[1], sys.argv[2]
sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from genre_map import genre_for  # noqa: E402
from calibre.library import db  # noqa: E402

cur = json.load(open(os.path.join(books_dir, "curation.json"), encoding="utf-8"))
fetched = json.load(open(os.path.join(books_dir, "fetched-meta.json"), encoding="utf-8"))
api = db(lib_dir).new_api
RANK = {"EPUB": 0, "MOBI": 1, "AZW3": 2, "PDF": 3}


def best_format_size(book_id):
    fmts = api.formats(book_id)
    best = min(fmts, key=lambda f: RANK.get(f, 9))
    return RANK.get(best, 9), -api.format_metadata(book_id, best).get("size", 0)


# 1. duplicates
dup_dir = os.path.join(books_dir, "duplicates-backup")
os.makedirs(dup_dir, exist_ok=True)
removed = []
for group in cur["duplicates"]:
    group = [g for g in group if g in api.all_book_ids()]
    if len(group) < 2:
        continue
    keep = min(group, key=best_format_size)
    for bid in group:
        if bid == keep:
            continue
        for fmt in api.formats(bid):
            src = api.format_abspath(bid, fmt)
            shutil.copy2(src, os.path.join(dup_dir, f"{bid} - {os.path.basename(src)}"))
        removed.append(bid)
    print("dup group", group, "-> keep", keep, api.formats(keep))
api.remove_books(removed, permanent=True)
print("removed duplicates:", removed)

# 2. title / author fixes
titles, authors = {}, {}
for sid, f in cur["fix"].items():
    bid = int(sid)
    if bid not in api.all_book_ids():
        continue
    if "title" in f:
        titles[bid] = f["title"]
    if "authors" in f:
        authors[bid] = [a.strip() for a in f["authors"].split("&")]
api.set_field("title", titles)
api.set_field("authors", authors)
print("fixed titles:", len(titles), "authors:", len(authors))

# 3. genre column
if "#genre" not in api.field_metadata.custom_field_keys():
    api.create_custom_column("genre", "Genre", "text", False)
    api = db(lib_dir).new_api  # reopen so the new column is usable
genres = {}
for bid in api.all_book_ids():
    tags = list(api.field_for("tags", bid) or [])
    tags += fetched.get(str(bid), {}).get("tags") or []
    g = genre_for(tags, api.field_for("title", bid))
    g = cur["rename_genres"].get(g, g)
    genres[bid] = cur["genre"].get(str(bid), g)
api.set_field("#genre", genres)
counts = {}
for g in genres.values():
    counts[g] = counts.get(g, 0) + 1
for g, n in sorted(counts.items(), key=lambda x: -x[1]):
    print(f"{n:4d}  {g}")
print("total books:", len(genres))
