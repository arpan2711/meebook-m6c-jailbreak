# Slot B root test — exact procedure (DRAFT, not yet run)

Written 2026-10-10. Nothing here has been executed. Each step needs a go-ahead.

## What we know about slot B (verified offline, no device writes)
- `uboot_b` (raw dump, LBA 24576) is **byte-identical** to `firmware/img-483/uboot.img`;
  `uboot_a` differs. ⇒ device was OTA'd 483→485 into A; **slot B = stock build 483**.
- `misc` @2048 holds Rockchip `AvbABData` (CRC ok):
  A: priority 15, successful_boot=1 · **B: priority 14, successful_boot=1** ⇒ B has booted before.
- 483 and 485 share Android 11 and security patch **2021-06-05** (system, vendor, boot header)
  ⇒ booting 483 against the current userdata should not trip Keymaster rollback / FBE.
- `firmware/magisk_patched_483.img` is the 483 boot patched with Magisk — **matches slot B exactly**.
  (Use the full one for `flash`; the `_nopcba` variant only existed to fit `fastboot boot`'s 64 MB limit.
  Check `max-download-size` in step 0; if 106 MB is over it, fastboot sparse-splits or use `_nopcba`.)

Residual risks: apps whose data was upgraded under 485 running older 483 system code (minor, usually
fine); Rockchip U-Boot A/B fallback behaviour on a failed B boot is not confirmed — recovery is manual
via fastboot/loader mode (see Recovery).

PATH helper (Git Bash):
```
PT=/c/Users/<you>/AppData/Local/Android/Sdk/platform-tools; S=<SERIAL>
```

## Step 0 — DONE 2026-10-10 (results in backup/logical/info/fastboot-getvar.txt)
- ⚠️ **`fastboot getvar all` FREEZES this U-Boot** (USB write timeouts, needs a 10–15 s power-hold).
  Single `getvar <name>` calls work fine. Never use `getvar all`.
- U-Boot 2017.09, product evb_rk3568, current-slot a, slot-count 2,
  slot-successful a/b = yes/yes, unbootable a/b = no/no, retry-count 0/0,
  max-download-size 0x04000000 (64 MiB), is-userspace no, boot_b size 128 MiB.
- First `fastboot reboot` after the getvars returned OKAY but stayed in fastboot; a second one worked.
- 64 MiB limit: `magisk_patched_483.img` is 102 MiB — fastboot should auto-sparse it for `flash`,
  but if U-Boot rejects sparse, use `magisk_patched_483_nopcba.img` (60 MiB, fits; factory-test
  ramdisk stripped — not needed for normal boot).

Original step-0 commands (minus `getvar all`):
```
$PT/adb -s $S reboot bootloader
$PT/fastboot getvar current-slot     # one var per call — NOT `getvar all`
$PT/fastboot reboot
```
Go/no-go: `current-slot:a`, B shows successful/not unbootable, note `max-download-size`.

## Step 1 — boot stock B, unmodified (writes only the A/B metadata in misc)
```
$PT/adb -s $S reboot bootloader
$PT/fastboot set_active b
$PT/fastboot reboot
# after boot:
$PT/adb -s $S shell getprop ro.boot.slot_suffix        # expect _b
$PT/adb -s $S shell getprop ro.build.version.incremental # expect 483
```
Check: home screen, books open, Wi-Fi. Proves B is healthy **before** we modify anything.
Back out: `adb reboot bootloader && fastboot set_active a && fastboot reboot`.

## Step 2 — flash Magisk boot to B (first real partition write; slot A untouched)
```
$PT/adb -s $S reboot bootloader
$PT/fastboot flash boot_b firmware/magisk_patched_483.img
$PT/fastboot set_active b
$PT/fastboot reboot
$PT/adb -s $S install tools/magisk/Magisk-v30.7.apk   # if not installed (check actual filename)
$PT/adb -s $S shell su -c id      # approve in Magisk app; expect uid=0
```
- Boots + root ⇒ unsigned boot images are accepted. Phase 1 answered.
- Doesn't boot ⇒ `fastboot set_active a` (A is pristine 485), then
  `fastboot flash boot_b firmware/img-483/boot.img` to restore B.

## Step 3 — with root on B: raw-dump everything (closes Phase 2, no maskrom)
```
$PT/adb -s $S shell su -c 'ls -l /dev/block/by-name'
# per partition, streamed to PC (exec-out avoids CRLF mangling):
$PT/adb -s $S exec-out su -c 'dd if=/dev/block/by-name/boot_a bs=4M' > backup/raw/boot_a.img
# ...same for vbmeta_a/b, dtbo_a/b, boot_b, logo_a/b, trust_a/b, baseparameter, metadata, super
# super (5 GB) holds BOTH slots' system/vendor/... — the byte-exact 485 backup.
# then sha256sum on device vs PC for each.
```
userdata raw dump is optional (encrypted; books are already backed up from /sdcard).

## Step 4 — then debloat via Magisk modules on B
Systemless overlays: no super rebuild, no verity issue. Mirror to A only once B is proven over days.

## Recovery cheat-sheet
- Android up: `adb reboot bootloader` → `fastboot set_active a` → `fastboot reboot`.
- Android not up: power off; hold the loader-mode key combo while plugging USB (rear port) → fastboot /
  RKDevTool loader mode → `fastboot set_active a`. (Writes in loader mode are not capped.)
- Worst case: official 483 images in `firmware/img-483/` (sha256-verified) + raw first 32 MiB backup.
