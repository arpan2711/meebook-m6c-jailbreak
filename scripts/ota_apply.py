"""Apply an incremental (delta) A/B OTA payload to source partition images.

usage: ota_apply.py <ota.zip|payload.bin> <old_dir> <new_dir> [partition ...]
old_dir holds <partition>.img source images (verified against the payload's old hash first).
Supports REPLACE/_BZ/_XZ, ZERO/DISCARD, SOURCE_COPY, SOURCE_BSDIFF/BROTLI_BSDIFF (BSDIFF40 + BSDF2).
PUFFDIFF is NOT implemented: those destination blocks are left zeroed and listed in
<new_dir>/<partition>.puffdiff.txt (block ranges), so the output hash will not match for
partitions that use it.
"""
import bz2, hashlib, lzma, os, struct, sys

import brotli
import numpy as np

sys.path.insert(0, os.path.dirname(__file__))
from payload import OPS, extents, fields, info, open_payload  # noqa: E402


def offtin(b):
    v = struct.unpack("<Q", b)[0]
    return -(v & ~(1 << 63)) if v >> 63 else v


def decomp(kind, data):
    return data if kind == 0 else bz2.decompress(data) if kind == 1 else brotli.decompress(data)


def bspatch(old, patch):
    if patch[:8] == b"BSDIFF40":
        kinds, hdr = (1, 1, 1), 8
    elif patch[:5] == b"BSDF2":
        kinds, hdr = tuple(patch[5:8]), 8
    else:
        raise ValueError(f"unknown bsdiff magic {patch[:8]!r}")
    clen, dlen, nsize = (offtin(patch[hdr + 8 * i:hdr + 8 * i + 8]) for i in range(3))
    p = hdr + 24
    ctrl = decomp(kinds[0], patch[p:p + clen]); p += clen
    diff = decomp(kinds[1], patch[p:p + dlen]); p += dlen
    extra = decomp(kinds[2], patch[p:])
    oldv = np.frombuffer(old, np.uint8)
    new = np.zeros(nsize, np.uint8)
    newpos = oldpos = ci = di = ei = 0
    while newpos < nsize:
        x, y, z = (offtin(ctrl[ci + 8 * i:ci + 8 * i + 8]) for i in range(3)); ci += 24
        d = np.frombuffer(diff, np.uint8, x, di); di += x
        seg = d.copy()
        lo, hi = max(oldpos, 0), min(oldpos + x, len(oldv))
        if hi > lo:
            seg[lo - oldpos:hi - oldpos] += oldv[lo:hi]
        new[newpos:newpos + x] = seg
        newpos += x; oldpos += x
        new[newpos:newpos + y] = np.frombuffer(extra, np.uint8, y, ei); ei += y
        newpos += y; oldpos += z
    return new.tobytes()


def parse(manifest):
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
                op = {"src": [], "dst": [], "type": 0, "off": 0, "len": 0}
                for ok, ov in fields(pv):
                    if ok == 1: op["type"] = ov
                    elif ok == 2: op["off"] = ov
                    elif ok == 3: op["len"] = ov
                    elif ok == 4: op["src"].append(extents(ov))
                    elif ok == 6: op["dst"].append(extents(ov))
                p["ops"].append(op)
        parts.append(p)
    return bs, parts


def sha256_file(path):
    h = hashlib.sha256()
    with open(path, "rb") as f:
        for chunk in iter(lambda: f.read(1 << 22), b""):
            h.update(chunk)
    return h.hexdigest()


def main():
    f, data_off, manifest = open_payload(sys.argv[1])
    old_dir, new_dir = sys.argv[2], sys.argv[3]
    os.makedirs(new_dir, exist_ok=True)
    bs, parts = parse(manifest)
    for p in parts:
        name = p["name"]
        if sys.argv[4:] and name not in sys.argv[4:]:
            continue
        full = all(op["type"] in (0, 1, 6, 7, 8) for op in p["ops"])
        src = None
        if not full:
            sp = os.path.join(old_dir, name + ".img")
            if sha256_file(sp) != p["old"][1]:
                print(f"{name}: source hash mismatch, skipping"); continue
            src = open(sp, "rb")
        puff = []
        out = os.path.join(new_dir, name + ".img")
        with open(out, "wb+") as o:
            o.truncate(p["new"][0])
            for op in p["ops"]:
                t = op["type"]
                if t in (6, 7):
                    continue
                if t == 9:  # PUFFDIFF
                    puff += op["dst"]; continue
                blob = b""
                if op["len"]:
                    f.seek(data_off + op["off"]); blob = f.read(op["len"])
                if t in (0, 1, 8):
                    data = blob if t == 0 else bz2.decompress(blob) if t == 1 else lzma.decompress(blob)
                else:
                    old = b"".join((src.seek(s * bs), src.read(n * bs))[1] for s, n in op["src"])
                    if t == 4:
                        data = old
                    elif t in (3, 5, 10):
                        data = bspatch(old, blob)
                    else:
                        raise NotImplementedError(f"{name}: op {OPS.get(t, t)}")
                pos = 0
                for s, n in op["dst"]:
                    o.seek(s * bs); o.write(data[pos:pos + n * bs]); pos += n * bs
        if puff:
            with open(os.path.join(new_dir, name + ".puffdiff.txt"), "w") as pf:
                for s, n in puff:
                    pf.write(f"{s} {n}\n")
        ok = sha256_file(out) == p["new"][1]
        nblk = sum(n for _, n in puff)
        print(f"{name:<12} new sha256 {'OK' if ok else 'MISMATCH'}"
              + (f"  (PUFFDIFF blocks left unpatched: {nblk})" if puff else ""))


if __name__ == "__main__":
    main()
