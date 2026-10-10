"""Compute (and optionally rewrite) the SHA-1 'id' of a v2 Android boot image, as mkbootimg does."""
import hashlib, struct, sys
sys.path.insert(0, __file__.rsplit("/", 1)[0].rsplit("\\", 1)[0])
from bootimg import parse

path = sys.argv[1]
d, ps, kernel, ramdisk, second, rdo, dtb = parse(path)
h = hashlib.sha1()
for blob in (kernel, ramdisk, second, rdo, dtb):
    h.update(blob); h.update(struct.pack("<I", len(blob)))
new = h.digest().ljust(32, b"\0")
print("stored  :", d[576:608].hex())
print("computed:", new.hex())
if len(sys.argv) > 2 and sys.argv[2] == "--fix":
    with open(path, "r+b") as f:
        f.seek(576); f.write(new)
    print("rewritten")
