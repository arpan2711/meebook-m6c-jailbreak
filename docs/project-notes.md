# Meebook M6C — backup & custom-ROM project

Working notes so any session can pick up where we left off. Last updated: 2026-10-10.

## Goal
1. Full backup of the device (priority: don't lose the user's books). — **done** (raw dump of every partition except userdata)
2. A clean, debloated, rooted reading tablet. — **done** on official 804 + Magisk (systemless debloat module,
   own launcher, KOReader setup). Current work and resume point: **`docs/plan-clean-reader.md`**.

Root is a means to an end, not the goal. Device is the user's own; this is ordinary
Android modding (debloat / custom ROM), done on their hardware.

## Device facts
- **Meebook M6C**, Rockchip **RK3566** e-ink tablet. Android 11 (SDK 30).
- Running firmware: **MEEBOOK-V2.0.0-2026080614** (`incremental=804`) on **slot B**, rooted with Magisk 30.7.
  Slot A = stock 485 (originally shipped).
- Brand/OEM: Haoqing (haoqing-meebook). Update model id: `MEEBOOK-M6C-RK3566`.
- **A/B slotted**, **dynamic partitions** (`super`). Currently on **slot B** (804 + Magisk).
- eMMC `mmcblk2`, ~29.1 GiB. GPT captured (see `backup/raw/gpt-layout.txt`).
- ADB serial **<SERIAL>**.
- `adb`/`fastboot`: `C:\Users\<you>\AppData\Local\Android\Sdk\platform-tools\` (NOT on PATH).
  A Samsung phone (`<tablet-ip>:5555`) is also on adb over Wi-Fi — **always pass `-s <SERIAL>`**.

## Lock / verified-boot state  (REVISED — earlier "bootloader is locked, blocked" was wrong)
Observed, to be treated as the current best understanding:
- `ro.boot.flash.locked = 0`  (bootloader reports **not** locked)
- `ro.boot.verifiedbootstate = orange`  (AVB "orange" = unsigned/unverified images permitted)
- fastboot `getvar unlocked = no`, `secure = yes`  (Rockchip's fastboot vars; on Rockchip these
  track efuse secure-boot, not the flash-lock, so they do **not** by themselves mean "cannot flash")
- `ro.oem_unlock_supported = 1`, but `settings global oem_unlock_allowed = null` and there is
  **no `persistent_data_block` service** (Meebook stripped PersistentDataBlockService) and **no `frp`
  partition**. So the Settings "OEM unlocking" toggle likely does nothing / is moot — but there's also
  no FRP gate to fight.

**Conclusion:** whether modified/unsigned images boot is **unconfirmed but promising** (flash.locked=0 +
orange). It needs a *controlled, reversible* test (see Plan). Do NOT state it as blocked.

Earlier confusion: `fastboot boot magisk_patched_483_nopcba.img` returned OKAY/OKAY but the device
then didn't come up rooted and U-Boot later froze. That was likely the stripped test image / loader
state, not proof of a lock. `verifiedbootstate=orange` in the boot cmdline is **hard-coded in the boot
image**, so Android-side "orange/unlocked" readouts are not independent evidence.

## What is backed up (all under `backup/`)
- `backup/logical/tar/*.tar` — full file trees of system, vendor, product, system_ext, odm, **sdcard**
  (user's books/files), + `SHA256SUMS`. sdcard verified file-for-file against device (1366/1366).
  `.err` files list the few root-only files skipped (expected without root).
- `backup/logical/apks/` — all 161 installed APKs (incl. splits), one dir per package (~1.9 GB).
- `backup/logical/info/` — getprop, settings (system/secure/global), package lists, build props,
  by-name map, mounts, dumpsys package, `fastboot-getvar*.txt`.
- `backup/raw/lba0-65536.img` — first 32 MiB raw: GPT, idbloader, **security**, **uboot**, **waveform**
  (e-ink calibration — irreplaceable), etc. + `.sha256`. `gpt-layout.txt` is the parsed partition map.

### NOT backed up (byte-exact)
- Current **485** system/boot/super and the **B** slot (byte-exact). We have the near-identical **483**
  official images instead (see Firmware).
- Raw **userdata** (encrypted; app logins/settings). User's books live on sdcard and ARE backed up.
- Reason: Rockchip U-Boot loader-mode **blocks reads past 32 MiB** (`RKUSB_READ_LIMIT_ADDR`, returns
  0xCC filler). Confirmed in source (`cmd/rockusb.c`) and on-device. Maskrom RAM-loader was attempted
  to bypass this but was unreliable on this PC and the usbplug loader appears to share the same read
  cap. **Writing is not limited** — only reading. So raw dumping the rest is the hard part; flashing is not.

## Official firmware we pulled (all under `firmware/`)  — this is the real backstop
Update server (unauthenticated, HTTP): `POST http://api.haoqingtech.com/update/v1`
body `model=MEEBOOK-M6C-RK3566&packageDate=<d>&packageCode=<code>&serialNumber=<sn>&language=en_US`.
A low/zero packageDate returns the oldest full package.
- `firmware/ota-full-2025042911.zip` — **FULL** OTA, build **483** (md5 a7479c…). A/B payload, all
  partitions are full REPLACE ops (verified). This is our restore/base image set.
- `firmware/ota-2026080614.zip` — incremental 485→**804** (newer build), kept for later.
- `firmware/img-483/*.img` — extracted from the full OTA, **sha256 verified** against payload:
  boot, system, vendor, product, system_ext, odm, dtbo, uboot, logo. **ext4** filesystems
  (system 1.5G, product 233M, system_ext 111M, vendor 202M, odm 3.3M; images are packed ~full).
- `firmware/magisk_patched_483.img` and `…_nopcba.img` — Magisk v30.7 patched boot (483). The nopcba
  one has the 152 MB factory-test ramdisk (`pcba/`) stripped to fit fastboot's 64 MB download limit;
  SHA-1 boot id fixed. (Patched on-device earlier; valid Magisk image.)

## super / dynamic partitions
`super` = `mmcblk2p18`, size 5,372,903,424 B (~5124 MB). Holds slots A+B, each with
system/system_ext/vendor/product/odm (all `readonly`). Used ≈2.1 GB per slot → ~3 GB slack.
Groups: `rockchip_dynamic_partitions_a` / `_b`.

## Toolchain status (Windows, no WSL distro installed)
Present:
- `platform-tools`: adb, fastboot, **mke2fs.exe** (e2fsprogs 1.47.2, android build), make_f2fs, etc1tool.
- **7-Zip** at `C:\Program Files\7-Zip\7z.exe` — reads the ext4 images fine (read-only).
- `magiskboot` (in `tools/magisk/apk/lib/arm64-v8a/`) — unpack/repack boot images. Magisk-v30.7.apk saved.
- Python 3.14 + **pyusb/libusb-package** (for the custom Rockchip reader).
- Rockchip: `tools/DriverAssitant_v5.14` (USB driver), `tools/RKDevTool_Release_v2.86` (+2.96),
  `tools/loader/rk356x_spl_loader_ddr1056_v1.12.109_no_check_todly.bin` (Radxa RK356x RAM loader),
  `tools/google_usb_driver/usb_driver/android_winusb.inf` (for WinUSB/Bootloader iface).
Missing / would need fetching for a clean repack:
- **e2fsdroid** (apply SELinux `security.selinux` + capability xattrs when rebuilding ext4 from a dir).
- **lpunpack / lpmake** (unpack/repack `super.img` dynamic container).
- **debugfs** (e2fsprogs) — enables *in-place* file deletion inside an ext4 image (preserves all
  xattrs/contexts) — the safest debloat method; avoids a full rebuild+resign of contexts.

## Scripts we wrote (`scripts/`)
- `payload.py` — parse/extract Android A/B OTA `payload.bin` (full ops), sha256-verifies output.
- `bootimg.py` — inspect/strip an Android boot image ramdisk (used for nopcba).
- `bootid.py` — compute/fix the boot header SHA-1 id.
- `gpt.py`, `rkloader.py` — parse GPT; parse Rockchip merged loader.
- `rkread.py` — custom Rockchip rockusb client over WinUSB: `probe`, `db <loader>` (load RAM loader),
  `reset <0|3>`, `read <start> <count> <out> [--resume]`. Reads below 32 MiB work; past 32 MiB the
  loader returns 0xCC (same cap as U-Boot). `db` to usbplug kept timing out on this PC's maskrom.
- `force-driver.ps1` — scripted "Have Disk" install of a specific INF/model onto a device instance
  (used to bind WinUSB to loader/maskrom without clicking through Device Manager).
- `logical-backup.ps1`, `tar-backup.sh` — the backup runs.

## USB / driver notes (this PC)
- Maskrom only enumerated cleanly on a **rear, direct motherboard USB port** (Intel xHCI root hub),
  NOT through the front/hub (code 10 / IO-timeout, then descriptor failures). Keep device on rear port.
- Two driver worlds: **Rockusb** (Rockchip, for RKDevTool) vs **WinUSB/Android Bootloader Interface**
  (for our `rkread.py` and fastboot). Switching modes needs the matching driver bound. `force-driver.ps1`
  or Device Manager → Update driver → Have Disk → `google_usb_driver\usb_driver\android_winusb.inf`.
- Rockchip driver pkg `oem161.inf` installed/removed via pnputil during experiments.
- **After the project, offer to restore drivers to stock.**

## Progress log
- 2026-10-10: **Debloat batch 1 applied live** (15 × `pm uninstall -k --user 0`, 2 × `disable-user`) —
  list in `backup/logical/info/debloat-batch1.log`, undo with `scripts/undo-debloat-batch1.sh`.
  Home, Settings, Wi-Fi verified OK, no crashes.
- 2026-10-10: **Slot B = stock 483, previously booted OK** (uboot_b == OTA 483 uboot; misc AvbABData
  B priority 14 successful_boot=1; same 2021-06-05 patch level as 485). Exact test procedure:
  **`docs/slot-b-test.md`**.
- 2026-10-10: **Slot B test PASSED.** `fastboot getvar all` freezes U-Boot (single getvars OK).
  Stock 483 booted on B (data intact). Flashed `magisk_patched_483.img` → boot_b (fastboot sent
  `boot_b_b`; this U-Boot prefix-matches names, landed in boot_b) ⇒ **unsigned boot images ARE
  accepted**, Magisk root works (`/debug_ramdisk/su`; use `MSYS_NO_PATHCONV=1` in Git Bash).
  Device now runs **slot B = 483 + Magisk**; **slot A = untouched stock 485** (`fastboot set_active a`).
  `fastboot reboot` often needs to be sent twice.
- 2026-10-10: **Full raw dump done** via root: `backup/raw/full/` — all 22 partitions except userdata
  (incl. super 5.4 GB with both slots, mmcblk2boot0/1), each sha256-verified vs device
  (`verify.log`, `SHA256SUMS`). Script: `scripts/raw-dump-root.sh`. boot_b.img there is the Magisk one;
  misc/security differ from the earlier 32 MiB dump (expected: A/B metadata + U-Boot state).
- 2026-10-10: **Batch-1 bug found & fixed:** `com.haoqing.wallpaper` provides the e-ink power-off screen
  (`HaoqingShutDownActivity`); removing it made system_server crash on every reboot/shutdown
  (soft-restart, so Magisk's setup reboot never really happened). Restored; real reboot works; Magisk
  30.7 setup complete, `su` on PATH (/system_ext/bin/su). GMS crash-loop seen earlier was only during
  that broken session — 0 crashes after a clean boot. Other batch-1 removals checked: none expose
  intents the framework/SystemUI call.

- 2026-10-10: **Updated to 804 with root** (see docs/plan-clean-reader.md step 1). Slot B = 804 + Magisk;
  slot A = 485 stock boot. Books on device: /sdcard/all_books (527), /sdcard/WifiTransfer (64), Download.

- 2026-10-10: **Step 2 clean-up done**: all Google + user apps + OEM/AOSP extras + 49 overlays removed
  (Magisk module clean804 + pm uninstall), F-Droid + Aurora + Olauncher installed. See docs/plan-clean-reader.md.

- 2026-10-10 evening/late: KOReader typography, sleep screen with book cover, Syncthing, Meebook Settings,
  icon-only KOReader menus, cloud sync icon, bottom-edge gesture chain documented; full refresh broken after
  a session hang was fixed by a reboot. Details: docs/plan-clean-reader.md "Round 2"/"Round 3".

## PLAN — original, kept for history (phases 0-1 done; see docs/plan-clean-reader.md for current state)
(Written for user review before anything irreversible was done.)
Phase 0 (no device writes) — **debloat design**, do now:
- Decide removal list (see `docs/debloat-candidates.md`, to be written). WiFi-only device ⇒ telephony
  stack is dead weight; dozens of theme/iconpack/cutout overlays are removable; Haoqing netdisk/appstore/
  stresstest/devicetest etc. are bloat. Keep launcher, SystemUI, providers, webview, inputmethod, GMS
  (user uses Gmail/Drive/Play Books).
- Two debloat strata: (a) **non-destructive** `pm uninstall --user 0` / `pm disable-user` live on device
  (instant, reversible, no reflash) — good first win; (b) **baked into a rebuilt image** for a true
  stripped ROM.

Phase 1 — **confirm what boots, reversibly** (the key unknown). Use the **A/B safety net**:
- Keep pristine **slot A**. Do experiments on **slot B**. If B fails, `set_active a` restores instantly.
- Smallest first test: get root on B via the Magisk-patched boot, confirm it boots, confirm `flash`
  writes are accepted. Only then try a modified super/system on B.
- If flashing modified images works → build stripped super on B, validate, then mirror to A.
- If it does NOT boot unsigned images → fall back to on-device debloat (Phase 0a) + Magisk modules only.

Phase 2 — **raw-dump the rest** (optional, for a byte-exact 485 restore): needs a working maskrom
usbplug session (try a USB2 hub / another PC) OR an in-session root `dd` over adb once Phase 1 gives root.

## Open questions / risks
- Does slot B accept and boot a Magisk-patched (unsigned) boot image? (Phase 1 test.)
- Does it accept a modified `super`/system? (AVB/verity: `veritymode=enforcing` in cmdline — may need
  verity handled; test empirically on B first, A stays safe.)
- Maskrom usbplug reliability on this PC (for raw dumps) — unresolved; not needed for flashing.
- e2fsdroid/lpmake/debugfs need fetching before an image rebuild.

## Safety rules we've been following
- Never write/erase the device without an explicit go-ahead; recon and reads only so far.
- Books/user data are backed up and verified before anything risky.
- Prefer reversible steps (A/B slots, on-device `pm` disable) over flashing.
- `fastboot boot` / RAM-load / reads do not persist; only `flash`/`erase`/Download-in-RKDevTool write.
