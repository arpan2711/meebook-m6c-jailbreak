"""Read raw eMMC sectors from a Rockchip device running the usbplug RAM loader.

Speaks the rockusb bulk protocol (same READ_LBA command rkdeveloptool's `rl` uses)
over libusb/WinUSB. Never writes storage: commands used are READ_LBA (0x14) and
DEVICE_RESET (0xFF, reboot only).

usage:
  rkread.py probe
  rkread.py db <loader.bin>      load DDR init + usbplug into RAM (maskrom only)
  rkread.py reset <0|3>          0 = normal reboot, 3 = reboot into maskrom
  rkread.py read <start_lba> <count> <out.img> [--resume]
"""
import os, struct, sys, time

import libusb_package
import usb.core
import usb.util

VID, PID = 0x2207, 0x350A
SECTOR = 512
CHUNK = 1024  # sectors per READ_LBA (512 KiB)


class Rockusb:
    def __init__(self):
        backend = libusb_package.get_libusb1_backend()
        self.dev = usb.core.find(idVendor=VID, idProduct=PID, backend=backend)
        if self.dev is None:
            sys.exit("no 2207:350a device visible to libusb (is WinUSB bound?)")
        intf = self.dev.get_active_configuration()[(0, 0)]
        usb.util.claim_interface(self.dev, intf.bInterfaceNumber)
        eps = intf.endpoints()
        self.ep_in = next(e for e in eps if usb.util.endpoint_direction(e.bEndpointAddress) == usb.util.ENDPOINT_IN)
        self.ep_out = next(e for e in eps if usb.util.endpoint_direction(e.bEndpointAddress) == usb.util.ENDPOINT_OUT)
        self.tag = 1

    def _cbw(self, opcode, xfer_len, flags, lba=0, count=0, cb_len=0x06, sub=0):
        # Field layout and per-opcode flags/cb_len mirror rkdeveloptool's InitializeCBW.
        cb = struct.pack(">BBIBH7x", opcode, sub, lba, 0, count)
        self.tag += 1
        return struct.pack("<4sIIBBB", b"USBC", self.tag, xfer_len, flags, 0, cb_len) + cb

    def _csw(self):
        csw = bytes(self.ep_in.read(13, timeout=20000))
        sig, tag, residue, status = struct.unpack("<4sIIB", csw)
        if sig != b"USBS" or tag != self.tag:
            raise IOError(f"bad CSW {csw.hex()}")
        return status

    def test_unit_ready(self):
        self.ep_out.write(self._cbw(0x00, 0, 0x80), timeout=5000)
        return self._csw()

    def reset(self, sub):
        """DEVICE_RESET (0xFF); sub 0 = reboot normally, 3 = reboot into maskrom."""
        self.ep_out.write(self._cbw(0xFF, 0, 0x00, sub=sub), timeout=5000)
        return self._csw()

    def read_lba(self, lba, count):
        n = count * SECTOR
        self.ep_out.write(self._cbw(0x14, n, 0x80, lba, count, 0x0A), timeout=5000)
        data = bytearray()
        while len(data) < n:
            data += self.ep_in.read(n - len(data), timeout=20000)
        status = self._csw()
        if status != 0:
            raise IOError(f"READ_LBA {lba}+{count} status {status}")
        return bytes(data)


def crc_ccitt(data):
    """rkdeveloptool crc.cpp CRC_CCITT / CRC_Calculate, bit for bit."""
    crc = 0xFFFF
    for ch in data:
        i = 0x80
        while i:
            crc = ((crc << 1) ^ 0x1021) & 0xFFFF if crc & 0x8000 else (crc << 1) & 0xFFFF
            if ch & i:
                crc ^= 0x1021
            i >>= 1
    return crc


def loader_entries(path):
    """Yield (request, name, data, delay_ms) for the 471/472 entries of a merged loader."""
    d = open(path, "rb").read()
    assert d[:4] in (b"LDR ", b"BOOT"), d[:4]
    off = 25
    for req in (0x471, 0x472):
        cnt, eoff, esz = struct.unpack_from("<BIB", d, off); off += 6
        for i in range(cnt):
            e = d[eoff + i * esz: eoff + (i + 1) * esz]
            name = e[5:45].decode("utf-16-le").rstrip("\0")
            doff, dsz, delay = struct.unpack_from("<III", e, 45)
            yield req, name, d[doff:doff + dsz], delay


def download_boot(dev, path):
    """rkdeveloptool `db`: push DDR init (0x471) then usbplug (0x472) into RAM via the boot ROM."""
    for req, name, data, delay in loader_entries(path):
        size = len(data)
        pend = False
        if size % 4096 == 4095:
            data += b"\0"; size += 1
        elif size % 4096 == 4094:
            pend = True
        crc = crc_ccitt(data[:size])
        buf = data[:size] + bytes([crc >> 8, crc & 0xFF])
        print(f"sending {hex(req)} {name} ({size} bytes + crc {crc:04x})", flush=True)
        for i in range(0, len(buf), 4096):
            chunk = buf[i:i + 4096]
            try:
                n = dev.ctrl_transfer(0x40, 0x0C, 0, req, chunk, timeout=10000)
            except usb.core.USBError as e:
                sys.exit(f"  failed at offset {i}/{len(buf)}: {e}")
            assert n == len(chunk), (name, i, n)
        if pend:
            dev.ctrl_transfer(0x40, 0x0C, 0, req, b"\0", timeout=10000)
        # DDR init must finish before the usbplug upload starts; the loader's own
        # delays are 0, so give the boot ROM a generous settle time after each entry.
        time.sleep(max(delay / 1000, 1.0))
        print(f"  ok", flush=True)


def main():
    if sys.argv[1] == "db":
        dev = usb.core.find(idVendor=VID, idProduct=PID, backend=libusb_package.get_libusb1_backend())
        if dev is None:
            sys.exit("no maskrom device")
        download_boot(dev, sys.argv[2])
        print("download boot done")
        return
    rk = Rockusb()
    if sys.argv[1] == "reset":
        print("reset status:", rk.reset(int(sys.argv[2])))
        return
    if sys.argv[1] == "probe":
        s0 = rk.read_lba(0, 2)
        print("LBA0-1:", s0[:2].hex(), "GPT sig:", s0[512:520])
        hi = rk.read_lba(65536, 8)
        print("LBA65536 first bytes:", hi[:16].hex(), "all 0xCC:", hi == b"\xcc" * len(hi))
        return

    start, count, out = int(sys.argv[2]), int(sys.argv[3]), sys.argv[4]
    done = 0
    mode = "wb"
    if "--resume" in sys.argv and os.path.exists(out):
        done = os.path.getsize(out) // SECTOR // CHUNK * CHUNK
        mode = "r+b"
    with open(out, mode) as f:
        f.seek(done * SECTOR); f.truncate()
        t0, last = time.time(), 0
        while done < count:
            n = min(CHUNK, count - done)
            for attempt in range(5):
                try:
                    f.write(rk.read_lba(start + done, n))
                    break
                except Exception as e:
                    print(f"\nretry {attempt+1} at lba {start+done}: {e}", flush=True)
                    time.sleep(1)
            else:
                sys.exit(f"giving up at lba {start+done}; rerun with --resume")
            done += n
            now = time.time()
            if now - last > 5 or done == count:
                mb = done * SECTOR / 2**20
                rate = mb / max(now - t0, 1e-6)
                eta = (count - done) * SECTOR / 2**20 / max(rate, 1e-6)
                print(f"{done}/{count} sectors  {mb:,.0f} MiB  {rate:.1f} MiB/s  eta {eta/60:.1f} min", flush=True)
                last = now
    print("done")


if __name__ == "__main__":
    main()
