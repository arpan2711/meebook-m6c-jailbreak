"""Inspect / rebuild an Android boot image (header v2) with a gzip newc ramdisk.

  bootimg.py ls <boot.img>                          summarize ramdisk contents
  bootimg.py strip <boot.img> <out.img> <prefix>... drop ramdisk entries under prefixes,
                                                    recompress, repack
"""
import gzip, struct, sys, zlib


def pad(n, a):
    return (n + a - 1) // a * a


def parse(path):
    d = open(path, "rb").read()
    assert d[:8] == b"ANDROID!"
    ks, ka, rs, ra, ss, sa, ta, ps, hv = struct.unpack_from("<9I", d, 8)
    assert hv == 2, hv
    rdo_size, rdo_off, hsize, dtb_size = struct.unpack_from("<IQII", d, 1632)
    off = ps
    kernel = d[off:off + ks]; off += pad(ks, ps)
    ramdisk = d[off:off + rs]; off += pad(rs, ps)
    second = d[off:off + ss]; off += pad(ss, ps)
    rdo = d[off:off + rdo_size]; off += pad(rdo_size, ps)
    dtb = d[off:off + dtb_size]
    return d, ps, kernel, ramdisk, second, rdo, dtb


def cpio_entries(raw):
    i = 0
    while True:
        assert raw[i:i + 6] == b"070701", raw[i:i + 6]
        h = raw[i:i + 110]
        fsize = int(h[54:62], 16); nsize = int(h[94:102], 16)
        name = raw[i + 110:i + 110 + nsize - 1].decode()
        end = pad(pad(i + 110 + nsize, 4) + fsize, 4)
        yield name, fsize, raw[i:end]
        if name == "TRAILER!!!":
            return
        i = end


def main():
    cmd, path = sys.argv[1], sys.argv[2]
    d, ps, kernel, ramdisk, second, rdo, dtb = parse(path)
    raw = gzip.decompress(ramdisk)
    if cmd == "ls":
        print(f"kernel={len(kernel)} ramdisk_gz={len(ramdisk)} ramdisk_raw={len(raw)} second={len(second)} dtb={len(dtb)}")
        tops = {}
        for name, size, _ in cpio_entries(raw):
            top = "/".join(name.split("/")[:2])
            tops[top] = tops.get(top, 0) + size
        for k, v in sorted(tops.items(), key=lambda x: -x[1])[:40]:
            print(f"{v/2**20:9.2f} MB  {k}")
        return
    out, prefixes = sys.argv[3], sys.argv[4:]
    kept = b""; dropped = 0
    for name, size, blob in cpio_entries(raw):
        if name != "TRAILER!!!" and any(name == p or name.startswith(p + "/") for p in prefixes):
            dropped += size; continue
        kept += blob
    kept += b"\0" * (pad(len(kept), 512) - len(kept))
    co = zlib.compressobj(9, zlib.DEFLATED, 31)
    newrd = co.compress(kept) + co.flush()
    hdr = bytearray(d[:ps])
    struct.pack_into("<I", hdr, 16, len(newrd))
    # header v2 id = sha1 over components+sizes; u-boot does not verify it, leave as-is.
    img = bytes(hdr)
    for blob in (kernel, newrd, second, rdo, dtb):
        img += blob + b"\0" * (pad(len(blob), ps) - len(blob))
    open(out, "wb").write(img)
    print(f"dropped {dropped/2**20:.1f} MB raw; ramdisk {len(ramdisk)} -> {len(newrd)}; image {len(d)} -> {len(img)}")


if __name__ == "__main__":
    main()
