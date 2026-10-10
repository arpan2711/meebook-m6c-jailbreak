"""Parse the primary GPT from a raw eMMC dump starting at LBA 0."""
import struct, sys, uuid

img = open(sys.argv[1], "rb").read()
hdr = img[512:1024]
assert hdr[:8] == b"EFI PART", "no GPT header at LBA1"
(cur, backup, first, last) = struct.unpack_from("<QQQQ", hdr, 24)
(ent_lba, n_ent, ent_sz) = struct.unpack_from("<QII", hdr, 72)
print(f"disk_guid={uuid.UUID(bytes_le=hdr[56:72])}")
print(f"backup_lba={backup} first_usable={first} last_usable={last} -> disk ~{(backup+1)*512/2**30:.2f} GiB")
print(f"{'#':>2} {'name':<14} {'start':>10} {'end':>10} {'sectors':>10} {'size':>10}")
for i in range(n_ent):
    e = img[ent_lba*512 + i*ent_sz : ent_lba*512 + (i+1)*ent_sz]
    if e[:16] == b"\0"*16:
        continue
    s, t = struct.unpack_from("<QQ", e, 32)
    name = e[56:128].decode("utf-16-le").rstrip("\0")
    n = t - s + 1
    print(f"{i+1:>2} {name:<14} {s:>10} {t:>10} {n:>10} {n*512/2**20:>8.1f}MB")
