"""Minimal read-only ext4 image reader: walk the tree, map files to blocks, read file data.

usage: ext4ls.py <image> [path]   -- list all files, or dump one file to stdout
"""
import stat, struct, sys


class Ext4:
    def __init__(self, path):
        self.f = open(path, "rb")
        sb = self._pread(1024, 1024)
        self.bs = 1024 << struct.unpack_from("<I", sb, 24)[0]
        self.ipg = struct.unpack_from("<I", sb, 40)[0]
        self.isz = struct.unpack_from("<H", sb, 88)[0]
        incompat = struct.unpack_from("<I", sb, 96)[0]
        self.is64 = bool(incompat & 0x80)
        self.dsz = struct.unpack_from("<H", sb, 254)[0] if self.is64 else 32
        self.first_data = struct.unpack_from("<I", sb, 20)[0]
        self.gdt = (self.first_data + 1) * self.bs

    def _pread(self, off, n):
        self.f.seek(off); return self.f.read(n)

    def inode(self, ino):
        g, i = divmod(ino - 1, self.ipg)
        d = self._pread(self.gdt + g * self.dsz, self.dsz)
        tbl = struct.unpack_from("<I", d, 8)[0]
        if self.is64 and self.dsz >= 64:
            tbl |= struct.unpack_from("<I", d, 0x28)[0] << 32
        return self._pread(tbl * self.bs + i * self.isz, self.isz)

    def extents(self, raw):
        """Return list of (logical_block, phys_block, length) for an inode."""
        flags = struct.unpack_from("<I", raw, 0x20)[0]
        out = []
        if flags & 0x80000:  # EXTENTS
            self._walk_extents(raw[0x28:0x28 + 60], out)
        elif not flags & 0x10000000:  # legacy block map (direct blocks only, rare on Android)
            for k, b in enumerate(struct.unpack_from("<12I", raw, 0x28)):
                if b: out.append((k, b, 1))
        return out

    def _walk_extents(self, node, out):
        magic, entries, _max, depth = struct.unpack_from("<HHHH", node, 0)
        assert magic == 0xF30A
        for k in range(entries):
            e = node[12 + 12 * k:24 + 12 * k]
            if depth == 0:
                lblk, ln, hi, lo = struct.unpack("<IHHI", e)
                if ln > 32768: ln -= 32768  # uninitialized extent
                out.append((lblk, (hi << 32) | lo, ln))
            else:
                lblk, lo, hi = struct.unpack_from("<IIH", e)
                self._walk_extents(self._pread(((hi << 32) | lo) * self.bs, self.bs), out)

    def size(self, raw):
        return struct.unpack_from("<I", raw, 4)[0] | (struct.unpack_from("<I", raw, 0x6C)[0] << 32)

    def read_inode(self, raw):
        size, flags = self.size(raw), struct.unpack_from("<I", raw, 0x20)[0]
        if flags & 0x10000000 or (stat.S_ISLNK(struct.unpack_from("<H", raw, 0)[0]) and size < 60):
            return raw[0x28:0x28 + size]  # inline data / fast symlink
        buf = bytearray(size)
        for lblk, pblk, ln in self.extents(raw):
            start = lblk * self.bs
            if start >= size: continue
            n = min(ln * self.bs, size - start)
            buf[start:start + n] = self._pread(pblk * self.bs, n)
        return bytes(buf)

    def walk(self, ino=2, prefix=""):
        """Yield (path, ino, raw_inode) for every entry below the root."""
        data = self.read_inode(self.inode(ino))
        i = 0
        while i + 8 <= len(data):
            child, rec, nlen, _ft = struct.unpack_from("<IHBB", data, i)
            if rec < 8: break
            name = data[i + 8:i + 8 + nlen].decode("utf-8", "replace")
            i += rec
            if not child or name in (".", ".."): continue
            path = f"{prefix}/{name}"
            raw = self.inode(child)
            yield path, child, raw
            if stat.S_ISDIR(struct.unpack_from("<H", raw, 0)[0]):
                yield from self.walk(child, path)

    def lookup(self, path):
        for p, ino, raw in self.walk():
            if p == path: return raw
        raise FileNotFoundError(path)


if __name__ == "__main__":
    fs = Ext4(sys.argv[1])
    if len(sys.argv) > 2:
        sys.stdout.buffer.write(fs.read_inode(fs.lookup(sys.argv[2])))
    else:
        for p, ino, raw in fs.walk():
            print(f"{oct(struct.unpack_from('<H', raw, 0)[0])} {fs.size(raw):>12} {p}")
