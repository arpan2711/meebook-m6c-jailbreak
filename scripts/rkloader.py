"""Inspect a Rockchip merged loader (boot_merger 'LDR '/'BOOT' format)."""
import struct, sys

d = open(sys.argv[1], "rb").read()
tag, hsize, ver, mver = struct.unpack_from("<4sHII", d, 0)
y, mo, da, h, mi, s = struct.unpack_from("<HBBBBB", d, 14)
chip, = struct.unpack_from("<I", d, 21)
off = 25
tables = []
for kind in ("471", "472", "loader"):
    cnt, eoff, esz = struct.unpack_from("<BIB", d, off); off += 6
    tables.append((kind, cnt, eoff, esz))
sign, rc4 = struct.unpack_from("<BB", d, off)
print(f"tag={tag} hdr={hsize:#x} ver={ver:#x} date={y}-{mo:02}-{da:02} chip={chip.to_bytes(4,'little')[::-1]!r} sign={sign} rc4_disable={rc4}")
for kind, cnt, eoff, esz in tables:
    for i in range(cnt):
        e = d[eoff + i*esz : eoff + (i+1)*esz]
        typ, = struct.unpack_from("<I", e, 1)
        name = e[5:45].decode("utf-16-le").rstrip("\0")
        doff, dsz, delay = struct.unpack_from("<III", e, 45)
        print(f"  [{kind}] {name:<16} off={doff:#x} size={dsz} delay={delay}")
        if len(sys.argv) > 2:
            open(f"{sys.argv[2]}/{kind}_{i}_{name}.bin", "wb").write(d[doff:doff+dsz])
