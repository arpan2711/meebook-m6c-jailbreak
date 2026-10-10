"""File-level diff of old vs new ext4 partition images (output of ota_apply.py).

usage: ota_diff.py <old_dir> <new_dir> <out.tsv> partition [partition ...]
Columns: partition, status(added/removed/changed/meta), path, old_size, new_size, puff
puff=1 means the new file's blocks were (partly) produced by an unimplemented PUFFDIFF op,
so its new content is unknown (size and existence are still exact).
"""
import hashlib, os, stat, struct, sys

sys.path.insert(0, os.path.dirname(__file__))
from ext4ls import Ext4  # noqa: E402


def load_puff(path):
    s = set()
    if os.path.exists(path):
        for line in open(path):
            a, n = map(int, line.split()); s.update(range(a, a + n))
    return s


def index(fs, puff=None):
    out = {}
    for path, ino, raw in fs.walk():
        mode = struct.unpack_from("<H", raw, 0)[0]
        size = fs.size(raw)
        h, p = "", 0
        if stat.S_ISREG(mode) or stat.S_ISLNK(mode):
            h = hashlib.sha256(fs.read_inode(raw)).hexdigest()
            if puff and stat.S_ISREG(mode):
                p = int(any(pb + k in puff for _, pb, ln in fs.extents(raw) for k in range(ln)))
        out[path] = (mode, size, h, p)
    return out


def main():
    old_dir, new_dir, out = sys.argv[1:4]
    with open(out, "w", encoding="utf-8") as o:
        o.write("partition\tstatus\tpath\told_size\tnew_size\tpuff\n")
        for part in sys.argv[4:]:
            puff = load_puff(os.path.join(new_dir, part + ".puffdiff.txt"))
            a = index(Ext4(os.path.join(old_dir, part + ".img")))
            b = index(Ext4(os.path.join(new_dir, part + ".img")), puff)
            counts = {}
            for path in sorted(set(a) | set(b)):
                if path not in b: st = "removed"
                elif path not in a: st = "added"
                elif a[path][2] != b[path][2] or a[path][1] != b[path][1]: st = "changed"
                elif a[path][0] != b[path][0]: st = "meta"
                else: continue
                counts[st] = counts.get(st, 0) + 1
                o.write(f"{part}\t{st}\t{path}\t{a.get(path, (0, ''))[1]}\t{b.get(path, (0, ''))[1]}\t{b.get(path, (0, 0, 0, 0))[3]}\n")
            print(part, counts, "puff-affected:", sum(1 for v in b.values() if v[3]))


if __name__ == "__main__":
    main()
