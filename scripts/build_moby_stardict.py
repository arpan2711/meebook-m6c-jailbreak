"""Build a StarDict dictionary from the Moby Thesaurus II (public domain, Project Gutenberg #3202).

usage: python build_moby_stardict.py <mthesaur.txt> <out_dir>
Each line of the source is "headword,syn1,syn2,...". Output: moby-thesaurus.{ifo,idx,dict}
"""
import html
import os
import struct
import sys

src, out = sys.argv[1], sys.argv[2]
os.makedirs(out, exist_ok=True)
entries = {}
with open(src, encoding="latin-1") as f:
    for line in f:
        parts = [p.strip() for p in line.rstrip("\r\n").split(",") if p.strip()]
        if len(parts) < 2:
            continue
        head, syns = parts[0], sorted(set(parts[1:]), key=str.lower)
        body = ('<p><i>%d synonyms</i></p><p>%s</p>'
                % (len(syns), ", ".join(html.escape(s) for s in syns)))
        entries[head] = entries.get(head, "") + body


def stardict_key(word):
    # StarDict sorts by g_ascii_strcasecmp, then by plain byte order
    b = word.encode("utf-8")
    return (b.lower(), b)


dict_data, idx = bytearray(), bytearray()
for word in sorted(entries, key=stardict_key):
    data = entries[word].encode("utf-8")
    idx += word.encode("utf-8") + b"\0" + struct.pack(">II", len(dict_data), len(data))
    dict_data += data

base = os.path.join(out, "moby-thesaurus")
open(base + ".dict", "wb").write(dict_data)
open(base + ".idx", "wb").write(idx)
with open(base + ".ifo", "w", encoding="utf-8", newline="\n") as f:
    f.write("StarDict's dict ifo file\nversion=2.4.2\n")
    f.write("wordcount=%d\nidxfilesize=%d\n" % (len(entries), len(idx)))
    f.write("bookname=Moby Thesaurus II\n")
    f.write("description=Grady Ward's Moby Thesaurus II, public domain (Project Gutenberg #3202).\n")
    f.write("sametypesequence=h\n")
print(len(entries), "headwords,", len(dict_data) // 1024, "KB")
