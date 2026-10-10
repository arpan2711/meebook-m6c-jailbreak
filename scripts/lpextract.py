"""List / extract logical partitions from an Android dynamic-partition super.img (LP metadata v10).

usage: lpextract.py <super.img> [outdir name ...]
Lists partitions (slot-0 metadata). With outdir, writes each named partition (e.g. system_a) as <name>.img.
"""
import os, struct, sys

SECTOR = 512


def read_metadata(f):
    f.seek(4096)
    magic, _size = struct.unpack("<II", f.read(8))
    assert magic == 0x616C4467, "bad LP geometry magic"
    f.seek(4096 + 8 + 32)
    max_size, slots, lbs = struct.unpack("<III", f.read(12))
    base = 4096 + 2 * 4096  # primary metadata, slot 0
    f.seek(base)
    h = f.read(256)
    magic, major, minor, hsize = struct.unpack_from("<IHHI", h, 0)
    assert magic == 0x414C5030, "bad LP header magic"
    tables_size = struct.unpack_from("<I", h, 44)[0]
    desc = [struct.unpack_from("<III", h, 80 + 12 * i) for i in range(4)]  # partitions, extents, groups, block devices
    f.seek(base + hsize)
    t = f.read(tables_size)
    parts = []
    off, n, es = desc[0]
    for i in range(n):
        name, attrs, first, num, group = struct.unpack_from("<36sIIII", t, off + i * es)
        parts.append((name.rstrip(b"\0").decode(), first, num))
    exts = []
    off, n, es = desc[1]
    for i in range(n):
        nsec, ttype, tdata, tsrc = struct.unpack_from("<QIQI", t, off + i * es)
        exts.append((nsec, ttype, tdata))
    return parts, exts


def main():
    f = open(sys.argv[1], "rb")
    parts, exts = read_metadata(f)
    want = set(sys.argv[3:])
    for name, first, num in parts:
        e = exts[first:first + num]
        size = sum(x[0] for x in e) * SECTOR
        print(f"{name:<16} {size:>12} B  extents={num}")
        if name in want:
            with open(os.path.join(sys.argv[2], name + ".img"), "wb") as o:
                for nsec, ttype, tdata in e:
                    if ttype == 0:  # LINEAR
                        f.seek(tdata * SECTOR); left = nsec * SECTOR
                        while left:
                            chunk = f.read(min(left, 1 << 22)); o.write(chunk); left -= len(chunk)
                    else:  # ZERO
                        o.write(b"\0" * (nsec * SECTOR))


if __name__ == "__main__":
    main()
