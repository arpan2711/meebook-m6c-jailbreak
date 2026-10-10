"""Inspect / extract an Android A/B OTA payload.bin without external deps.

usage: payload.py <ota.zip|payload.bin> [outdir partition ...]
Lists every partition with its op types and old/new sizes+hashes. With an
outdir, extracts the named partitions if they consist only of full ops
(REPLACE / REPLACE_BZ / REPLACE_XZ / ZERO / DISCARD).
"""
import bz2, hashlib, lzma, os, struct, sys, zipfile

OPS = {0: "REPLACE", 1: "REPLACE_BZ", 2: "MOVE", 3: "BSDIFF", 4: "SOURCE_COPY",
       5: "SOURCE_BSDIFF", 6: "ZERO", 7: "DISCARD", 8: "REPLACE_XZ", 9: "PUFFDIFF",
       10: "BROTLI_BSDIFF", 11: "ZUCCHINI", 12: "LZ4DIFF_BSDIFF", 13: "LZ4DIFF_PUFFDIFF"}
FULL = {0, 1, 6, 7, 8}


def varint(b, i):
    r = s = 0
    while True:
        c = b[i]; i += 1
        r |= (c & 0x7F) << s; s += 7
        if c < 0x80:
            return r, i


def fields(b):
    """Yield (field_no, value) for a protobuf message; length-delimited -> bytes."""
    i = 0
    while i < len(b):
        k, i = varint(b, i)
        f, t = k >> 3, k & 7
        if t == 0:
            v, i = varint(b, i)
        elif t == 2:
            n, i = varint(b, i); v = b[i:i + n]; i += n
        elif t == 1:
            v = b[i:i + 8]; i += 8
        elif t == 5:
            v = b[i:i + 4]; i += 4
        else:
            raise ValueError(f"wire type {t}")
        yield f, v


def info(b):
    d = dict(fields(b)) if b else {}
    return d.get(1), (d.get(2) or b"").hex()


def extents(b):
    d = dict(fields(b))
    return d.get(1, 0), d.get(2, 0)


def open_payload(path):
    if zipfile.is_zipfile(path):
        z = zipfile.ZipFile(path)
        zi = z.getinfo("payload.bin")
        assert zi.compress_type == zipfile.ZIP_STORED
        f = open(path, "rb"); f.seek(zi.header_offset)
        h = f.read(30); n, m = struct.unpack_from("<HH", h, 26)
        base = zi.header_offset + 30 + n + m
    else:
        f, base = open(path, "rb"), 0
    f.seek(base)
    magic, ver, msize = struct.unpack(">4sQQ", f.read(20))
    assert magic == b"CrAU", magic
    sigsize = struct.unpack(">I", f.read(4))[0] if ver >= 2 else 0
    manifest = f.read(msize)
    return f, base + 24 + msize + sigsize, manifest


def main():
    f, data_off, manifest = open_payload(sys.argv[1])
    m = list(fields(manifest))
    bs = next((v for k, v in m if k == 3), 4096)
    parts = []
    for k, v in m:
        if k != 13:
            continue
        p = {"ops": []}
        for pk, pv in fields(v):
            if pk == 1: p["name"] = pv.decode()
            elif pk == 6: p["old"] = info(pv)
            elif pk == 7: p["new"] = info(pv)
            elif pk == 8:
                op = {"src": [], "dst": []}
                for ok, ov in fields(pv):
                    if ok == 1: op["type"] = ov
                    elif ok == 2: op["off"] = ov
                    elif ok == 3: op["len"] = ov
                    elif ok == 4: op["src"].append(extents(ov))
                    elif ok == 6: op["dst"].append(extents(ov))
                    elif ok == 9: op["sha"] = ov
                p["ops"].append(op)
        parts.append(p)

    print(f"block_size={bs}")
    for p in parts:
        types = {}
        for op in p["ops"]:
            t = OPS.get(op.get("type", 0), op.get("type"))
            types[t] = types.get(t, 0) + 1
        old = p.get("old", (None, ""))
        new = p.get("new", (None, ""))
        full = all(op.get("type", 0) in FULL for op in p["ops"])
        print(f"{p['name']:<14} full={str(full):<5} old={old[0]} {old[1][:16]} new={new[0]} {new[1][:16]}  {types}")

    if len(sys.argv) > 3:
        out = sys.argv[2]; os.makedirs(out, exist_ok=True)
        for p in parts:
            if p["name"] not in sys.argv[3:]:
                continue
            if not all(op.get("type", 0) in FULL for op in p["ops"]):
                print(f"skip {p['name']}: needs source data"); continue
            size = p["new"][0]
            with open(os.path.join(out, p["name"] + ".img"), "wb+") as o:
                o.truncate(size)
                for op in p["ops"]:
                    t = op.get("type", 0)
                    if t in (6, 7):
                        continue
                    f.seek(data_off + op.get("off", 0)); raw = f.read(op["len"])
                    data = raw if t == 0 else bz2.decompress(raw) if t == 1 else lzma.decompress(raw)
                    pos = 0
                    for start, n in op["dst"]:
                        o.seek(start * bs); o.write(data[pos:pos + n * bs]); pos += n * bs
                o.seek(0); h = hashlib.sha256()
                for chunk in iter(lambda: o.read(1 << 20), b""):
                    h.update(chunk)
            ok = h.hexdigest() == p["new"][1]
            print(f"extracted {p['name']}.img sha256 {'OK' if ok else 'MISMATCH'}")


if __name__ == "__main__":
    main()
