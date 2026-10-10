"""Fetch online metadata (via Calibre's fetch-ebook-metadata) for library books with no tags,
unknown author or a messy title. Writes books/fetched-meta.json; does NOT change the library.

usage: python fetch_missing_meta.py <books_dir>
"""
import concurrent.futures as cf
import json
import os
import re
import subprocess
import sys
import xml.etree.ElementTree as ET

FETCH = r"C:\Program Files\Calibre2\fetch-ebook-metadata.exe"
NS = {"dc": "http://purl.org/dc/elements/1.1/", "opf": "http://www.idpf.org/2007/opf"}


def clean(s):
    s = re.sub(r"^\d{4} ", "", s or "")
    s = re.sub(r"--.*$|\bliber3\b|anna.?s archive|z-library|\(z-lib.*?\)|\[.*?\]|\(.*?\)", " ", s, flags=re.I)
    s = re.sub(r"[_]+", " ", s)
    return re.sub(r"\s+", " ", s).strip(" -.,")


def needs(b):
    t = b.get("title", "")
    return (not b.get("tags") or "Unknown" in b.get("authors", "")
            or re.match(r"^\d{4} ", t) or re.search(r"anna.?s archive|--|\d{10,}", t, re.I))


def fetch(b):
    title = clean(b["title"])
    args = [FETCH, "-t", title, "-o", "-d", "45"]
    if "Unknown" not in b.get("authors", ""):
        args += ["-a", b["authors"].split("&")[0].strip()]
    isbn = (b.get("identifiers") or {}).get("isbn")
    if isbn:
        args += ["-i", isbn]
    try:
        r = subprocess.run(args, capture_output=True, timeout=120, encoding="utf-8", errors="replace")
        out = r.stdout
        i = out.find("<?xml")
        if i < 0:
            return b["id"], {"query": title, "error": "no result"}
        root = ET.fromstring(out[i:])
        md = root.find("opf:metadata", NS)
        get = lambda tag: [e.text for e in md.findall(tag, NS) if e.text]
        return b["id"], {"query": title, "title": (get("dc:title") or [None])[0],
                         "authors": get("dc:creator"), "tags": get("dc:subject"),
                         "isbn": [x for x in get("dc:identifier") if x and x.replace("-", "").isdigit()]}
    except Exception as e:  # timeouts, parse errors
        return b["id"], {"query": title, "error": str(e)[:200]}


def main():
    books_dir = sys.argv[1]
    lib = json.load(open(os.path.join(books_dir, "calibre-list-1.json"), encoding="utf-8"))
    todo = [b for b in lib if needs(b)]
    print("fetching", len(todo), flush=True)
    results = {}
    with cf.ThreadPoolExecutor(6) as ex:
        for n, (bid, res) in enumerate(ex.map(fetch, todo), 1):
            results[bid] = res
            if n % 20 == 0:
                print(n, flush=True)
    json.dump(results, open(os.path.join(books_dir, "fetched-meta.json"), "w", encoding="utf-8"), indent=1)
    print("done", sum(1 for r in results.values() if "error" not in r), "found /", len(results))


if __name__ == "__main__":
    main()
