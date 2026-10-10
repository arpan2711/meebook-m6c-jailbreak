#!/usr/bin/env bash
# Stream readable trees off the Meebook as tar archives (no root needed).
# Unreadable files are skipped by toybox tar; errors go to <name>.err.
set -u
SERIAL="${SERIAL:-<SERIAL>}"
ADB="/c/Users/<you>/AppData/Local/Android/Sdk/platform-tools/adb.exe"
OUT="${1:-/c/Users/<you>/Desktop/meebook-jailbreak/backup/logical/tar}"
mkdir -p "$OUT"

dump() {
  local name="$1" path="$2"
  echo "== $name ($path)"
  "$ADB" -s "$SERIAL" exec-out "tar -cf - -C / ${path#/} 2>/dev/null" > "$OUT/$name.tar"
  "$ADB" -s "$SERIAL" shell "tar -cf /dev/null -C / ${path#/} 2>&1 >/dev/null" > "$OUT/$name.err"
  ls -l "$OUT/$name.tar"
}

dump system     /system
dump vendor     /vendor
dump product    /product
dump system_ext /system_ext
dump odm        /odm
dump sdcard     /storage/emulated/0

( cd "$OUT" && sha256sum *.tar > SHA256SUMS )
echo DONE
