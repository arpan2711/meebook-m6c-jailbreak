#!/bin/sh
# Raw-dump all partitions (except userdata) over adb using Magisk root, sha256-verify each against the device.
# Usage (Git Bash): scripts/raw-dump-root.sh [outdir]   (default backup/raw/full)
PT=/c/Users/<you>/AppData/Local/Android/Sdk/platform-tools; S=<SERIAL>
SU=/debug_ramdisk/su
OUT=${1:-backup/raw/full}; mkdir -p "$OUT"
export MSYS_NO_PATHCONV=1
PARTS="security uboot_a uboot_b waveform trust_a trust_b misc dtbo_a dtbo_b vbmeta_a vbmeta_b boot_a boot_b
backup cache metadata baseparameter logo_a logo_b super mmcblk2boot0 mmcblk2boot1"
: > "$OUT/SHA256SUMS"; : > "$OUT/verify.log"
for p in $PARTS; do
  dev=/dev/block/by-name/$p
  "$PT/adb" -s $S exec-out "$SU -c 'dd if=$dev bs=4194304 2>/dev/null'" > "$OUT/$p.img"
  pc=$(sha256sum "$OUT/$p.img" | cut -d' ' -f1)
  dv=$("$PT/adb" -s $S shell "$SU -c 'sha256sum $dev'" | cut -d' ' -f1 | tr -d '\r')
  sz=$(stat -c%s "$OUT/$p.img")
  echo "$pc  $p.img" >> "$OUT/SHA256SUMS"
  if [ "$pc" = "$dv" ]; then r=OK; else r=MISMATCH; fi
  echo "$r $p size=$sz pc=$pc dev=$dv" | tee -a "$OUT/verify.log"
done
