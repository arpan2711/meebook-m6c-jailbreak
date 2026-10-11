"""Set KOReader's dictionary order (settings.reader.lua ["dicts_order"], keyed by .ifo path).

usage: python koreader_dict_order.py <settings.reader.lua>
"""
import re
import sys

D = "/storage/emulated/0/koreader/data/dict/"
ORDER = [
    "oxford-english/ODE.ifo",
    "shorter-oxford/SOEDrich.ifo",
    "wiktionary-en/en-en.ifo",
    "GNU Collaborative International Dictionary of English/stardict.ifo",
    "moby-thesaurus/moby-thesaurus.ifo",
    "WordNet (r) 1.7/comn_dictd03_wn.ifo",
    "etymology-harper/stardict.ifo",
    "english-idioms/idioms_eng_eng.ifo",
]
block = '["dicts_order"] = {\n' + "".join(
    '        ["%s"] = %d,\n' % (D + p, i) for i, p in enumerate(ORDER, 1)) + "    },"

path = sys.argv[1]
s = open(path, encoding="utf-8").read()
s, n = re.subn(r'\["dicts_order"\]\s*=\s*\{[^{}]*\},', block, s)
if not n:
    raise SystemExit("dicts_order not found")
open(path, "w", encoding="utf-8", newline="\n").write(s)
print(block)
