# What the 485 → 804 update changes

Worked out 2026-10-10 by applying `firmware/ota-2026080614.zip` (incremental, 485→804) to the
byte-exact slot A (485) partitions from `backup/raw/full/`, then diffing the filesystems file by file.
Full per-file list: `docs/update-804-filediff.tsv`.
Tools: `scripts/lpextract.py` (super → partitions), `scripts/ota_apply.py` (delta patcher),
`scripts/ext4ls.py` (ext4 reader), `scripts/ota_diff.py`.

**Accuracy:** uboot, vendor, odm, dtbo and logo rebuild **byte-identical** to official 804 (hash OK).
system/system_ext/product/boot include **PUFFDIFF** ops (not implemented), so the *new contents* of
24 files are unknown. Their existence, size, and every other file are exact. Those 24 files are the
updated Haoqing APKs, Settings.apk, SystemUI.apk, and the boot ramdisk.

Changelog from Haoqing's server: "Optimize some known problems......"

## Headline
- Build **804** (6 Aug 2026), still **Android 11**, **security patch still 2021-06-05** (no security fixes).
- Kernel rebuilt (4.19.193-ab804, same version). New **U-Boot**. Boot ramdisk changed.

## Display / pen / hardware
- E-ink and colour stack: `hwcomposer.rk30board.so` (25% smaller, rewritten), `librkcfa.so` (colour
  filter array, used for colour rendering), `librga.so` (2D blitter), **new `libebook.so`**, updated
  `haoqingdrawserver`, `libinputflinger` (touch/pen input), gralloc.
- Wi-Fi/BT: **new Seekwave SWT6621S chip firmware** (likely a newer hardware revision), updated
  SWT6621/EA6621Q firmware, all Wi-Fi/BT kernel modules rebuilt, `init.rk30board.rc` +1.6 KB.
- SELinux policy updated in every partition.

## Haoqing apps (all updated; contents unknown because of PUFFDIFF)
Launcher/reader "MEE OS" (+0.6 MB, native reader lib changed), **Haoqing Settings (+7.7 MB)**,
SystemUpdate, FileManager, Netdisk, AppStore, AccountManagement, FloatingButton, Wallpaper, WiFiTransfer,
Calculator, DeviceTest, Pinyin IME. AOSP Settings and SystemUI also changed.
**New:** `Haoqing-ToolApp`, `Haoqing-AgingTest` (factory burn-in), `/system/bin/iozone` and
`stressapptest` (factory test binaries). These add bloat.

## Google
- Google apps **move from /system to /product** and pick up an OpenGApps-style permission set.
- Bundled GMS 21.48.15 → 21.48.57. Play Store bundled 45.4 → 29.2 (older, but it self-updates).
  The device already runs Play-updated GMS 26.37 / Play 53.4, so these don't matter.
- **WebView 116 → 139.** This is the one meaningful security gain: the device's WebView is the AOSP-named
  `com.android.webview`, which Play does **not** update, so it is stuck on 116 (2023).
- Google TTS → "Speech Services by Google" 2024 build (same package; yours is already Play-updated to 2026).
- Calendar sync adapter moved to product. **Removed:** Google Contacts Sync, FusedLocation,
  GoogleBackupTransport, StatementService (GMS covers these).

## Framework
`framework.jar`, `services.jar`, the boot image `.oat/.art` files, and `framework-wifi.jar` changed (small diffs).
