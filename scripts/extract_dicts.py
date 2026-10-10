"""Safely extract StarDict archives (.tar.gz / .tar.zst) into an output folder.

usage: python extract_dicts.py <out_dir> <archive> [archive ...]
"""
import os
import sys
import tarfile
from compression import zstd

out = sys.argv[1]
for f in sys.argv[2:]:
    t = tarfile.open(fileobj=zstd.open(f)) if f.endswith(".zst") else tarfile.open(f)
    names = t.getnames()
    bad = [n for n in names if os.path.isabs(n) or ".." in n.replace("\\", "/").split("/")]
    if bad:
        print("UNSAFE, skipped:", f, bad[:3])
        continue
    t.extractall(out, filter="data")
    print(f, "->", sorted({n.split("/")[0] for n in names}))
