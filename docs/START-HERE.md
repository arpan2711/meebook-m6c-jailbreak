# START HERE: Meebook M6C project handoff (written 2026-10-10, end of day 1)

Read this first in any new session. Details: `docs/plan-clean-reader.md` (step-by-step log with
checkboxes) and `README.md` (device facts, backups, boot/AVB findings, early history).

## Current device state (all working)
- **Slot B = official 804 (MEEBOOK-V2.0.0-2026080614) + Magisk 30.7 root.** Slot A = stock 485.
  Re-root A with `firmware/magisk_patched_485.img`; stock 804 boot is `firmware/boot_804_stock_full.img`.
- **Backups:** full sha256-verified raw dump of every partition except userdata in `backup/raw/full/`;
  logical tars + all APKs in `backup/logical/`; books snapshot `books/device-books.tar`;
  non-book files in `non-books-backup/`. Calibre master library: `C:\Users\<you>\Calibre Library`.
- **Debloat:** Magisk module `clean804` (generator `magisk-modules/make-clean804.sh`). Disable it to undo.
  `inkfix` module = RRO turning the wallpaper service on (`android/inkfix-overlay/`).
- **Home = InkHome 2.4** (`android/inkhome/`, build `sh android/inkhome/build.sh`, key `tools/inkfix.jks`):
  favourite covers, app grid, cloud sync icon (Syncthing), Settings screen (own task), sleep-image composer.
- **Sleep screen:** base photo `/sdcard/InkHome/lockscreen-base.png` (from `lockscreen/crop_lockscreen.py`
  + `brighten.py`; crop x 280-1500, bottom 1749 of photo-original.png), bubble placement
  `/sdcard/InkHome/lockscreen-bubble.txt` = `750 420 245 310  285 470 12  345 452 18  415 432 26`
  (cloud CX CY RX RY + trail x y r). Cover drawn big (2.1 x RY). Result: `lockscreen/standby-generated.png`.
- **KOReader:** Literata, list mode, dictionaries (Wiktionary, GCIDE, WordNet, Etymonline, Idioms, Moby),
  plugins wordtrail / favsync / bottommenus, patch `koreader/patches/2-icons-only-menus.lua`.
- **Sync:** Syncthing (Books two-way, WordTrail tablet->PC), tablet syncs only while charging.
- **Public repo:** https://github.com/arpan2711/meebook-m6c-jailbreak. Update with
  `python -I scripts/export_public.py`, then commit + push in `../meebook-m6c-public`.

## How to talk to the device
- adb at `C:\Users\<you>\AppData\Local\Android\Sdk\platform-tools\` (not on PATH). Always `-s <SERIAL>`
  (a Samsung phone is also on adb). Git Bash: `export MSYS_NO_PATHCONV=1`.
- Root: `adb shell 'su -c "..."'`. Screenshots: `adb exec-out 'su -c "screencap -p"' > x.png` (screen awake).
- Write scripts with the Write tool; Git Bash heredocs mangle backslashes and `$`.
- Reverse-engineering kit that worked: pull the APK, `unzip classes*.dex`, `build-tools/35.0.0/dexdump.exe -d`,
  then grep const-strings and method names. Calibre's `calibre-debug -e script.py` gives Qt for image work (no PIL).

## Lessons (don't relearn these)
1. **Unsigned boot images boot** (flash.locked=0, orange). Use the inactive A/B slot to test; `fastboot set_active` back.
   Never `fastboot getvar all` (freezes U-Boot). `fastboot reboot` often needs sending twice.
2. **Loader-mode reads stop at 32 MiB**; dump with root `dd` instead. Maskrom was unreliable on this PC (rear USB port only).
3. **Never remove `com.haoqing.wallpaper`** (shutdown/sleep screen; removing it crash-loops system_server).
   Check every OEM app's intents before removing it.
4. **Haoqing background manager:** new third-party apps get `pkg 0 0 0` in `/data/haoqing/haoqing_whitelist.conf`,
   which kills them in the background (KOReader opened blank). Set `pkg 1 1 1`.
5. **Haoqing resets the sleep image at every boot** (HaoqingWallpaper `resetStandbyImg` copies
   /vendor/media/standby.png over the active standby*.png). InkHome 2.2+ re-installs ours on each resume.
   `path_standby` is ignored.
6. **Haoqing draws the sleep screen ~5 s before Android's SCREEN_OFF**, so anything shown on it must be ready beforehand.
   That's why the screen-time counter was dropped (it needed a re-render every minute).
7. **Bottom-edge swipes** (left recents, centre home, right refresh) = `settings global GestureConfig` ->
   QuickStep HaoqingInputConsumer -> SystemUI HaoqingGestureHandler. Refresh = broadcast
   `com.haoqing.action.FULL_REFRESH` to com.android.systemui. `adb input swipe` does NOT trigger them.
8. **Full refresh stopped flashing after a session hang; a reboot fixed it.** Try a reboot first next time.
9. An activity sharing the home task (singleTask + clearTaskOnLaunch) gets cleared on launch: give it its own taskAffinity.
10. Battery: health Good, 2107 mAh. On the PC's USB port it drains about 300 mA while saying "Charging"; use a wall charger.
11. Haoqing's own Settings app runs boot tasks (HaoqingReceiver BOOT_COMPLETED); look there first when something "comes back".

## Open items (pick up here)
- [ ] **Keeper boot script** (Magisk `/data/adb/service.d/meebook-keeper.sh`): every 5 min re-apply the sleep image
  and `1 1 1` for new apps. Written but blocked by the Claude Code permission system; the user must approve it.
  The old `haoqing-whitelist.sh` is GONE from the tablet, so new apps are not auto-whitelisted until then.
- [ ] Status bar: battery % inside the icon + time left (needs LSPosed/Xposed module; awaiting decision).
- [ ] E-ink contrast control (hq_contrast / fullscreen_flush) in InkHome Settings.
- [ ] Calibre word-list plugin; KOReader <-> Calibre wireless; verify all books show in KOReader; word games (parked).
- [ ] At project end: restore stock USB drivers on the PC.

## Working rule with the user
Show a plan and get an explicit OK before any device or system change; read-only checks are fine.
