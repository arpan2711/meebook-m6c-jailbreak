# START HERE: Meebook M6C project handoff (written 2026-10-10, updated 2026-10-11)

Read this first in any new session. **What's next: `docs/roadmap.md`** (stock-vs-now record + prioritised plan). Details: `docs/plan-clean-reader.md` (step-by-step log with
checkboxes) and `README.md` (device facts, backups, boot/AVB findings, early history).

## Current device state (all working)
- **Slot B = official 804 (MEEBOOK-V2.0.0-2026080614) + Magisk 30.7 root.** Slot A = stock 485.
  Re-root A with `firmware/magisk_patched_485.img`; stock 804 boot is `firmware/boot_804_stock_full.img`.
- **Backups:** full sha256-verified raw dump of every partition except userdata in `backup/raw/full/`;
  logical tars + all APKs in `backup/logical/`; books snapshot `books/device-books.tar`;
  non-book files in `non-books-backup/`. Calibre master library: `C:\Users\<you>\Calibre Library`.
- **Debloat:** Magisk module `clean804` (generator `magisk-modules/make-clean804.sh`). Disable it to undo.
  `inkfix` module v2 = RRO: wallpaper service on + config_unplugTurnsOnScreen=false (`android/inkfix-overlay/build.sh`).
  clean804 also replaces Haoqing's `/system/bin/corelog` (feedback logger) with a no-op.
- **Phone-home block:** Magisk module `hosts` blocks Haoqing/OEM hosts (`magisk-modules/hosts-block.txt`), see `docs/network-audit.md`.
- **Zygisk + Vector v2.2** (LSPosed successor) with our module **InkHooks** (`android/inkhooks/`): charger never wakes the tablet.
- **Boot scripts:** `/data/adb/post-fs-data.d/adb-secure.sh` (ADB key auth on), `/data/adb/service.d/adb-wifi.sh`
  (wireless ADB :5555, only if secure) and `crashwatch.sh`. Copies in `device/`.
- **Home = InkHome 2.12** (`android/inkhome/`, build `sh android/inkhome/build.sh`, key `tools/inkfix.jks`):
  favourite covers, app grid (long-press = App info / Keep in background), device-stats tile between Books and Apps
  (battery, storage, CPU via su, memory, Wi-Fi, uptime; refreshes on resume or tap), cloud sync icon (Syncthing),
  Settings screen (own task), sleep-image composer, Look Up viewer (KOReader define:/Ask AI buttons),
  Home Control (house icon on the header / app grid): Smart Life + Dreo, see `docs/home-control.md`.
- **Sleep screen:** base photo `/sdcard/InkHome/lockscreen-base.png` (from `lockscreen/crop_lockscreen.py`
  + `brighten.py`; crop x 280-1500, bottom 1749 of photo-original.png), bubble placement
  `/sdcard/InkHome/lockscreen-bubble.txt` = `750 420 245 310  285 470 12  345 452 18  415 432 26`
  (cloud CX CY RX RY + trail x y r). Cover drawn big (2.1 x RY). The current book is the first `["file"]` entry of
  `/sdcard/koreader/history.lua` (KOReader rewrites it when a book opens); InkHome watches that file (FileObserver),
  so the sleep cover changes within ~3 s of opening a book, even if the tablet sleeps straight from KOReader.
  **Reading goal:** big number top left = minutes still to read today (goal 120; set in InkHome Settings > Reading goal, changes redraw the sleep image at once via a package-local broadcast, prefs `read_goal`/`lock_goal`),
  from KOReader's statistics.sqlite3 (page_stat_data durations since midnight; read from a private copy because it's WAL).
  White at 120, turning green towards 0. Refreshed every 30 min, on resume and when a book opens. KOReader writes stats
  when it goes to the background, so the number lags a little during a reading session. Result: `lockscreen/standby-generated.png`.
- **KOReader:** Literata, list mode, dictionaries (Oxford Dictionary of English first (modern, concise), Shorter Oxford 6th ed. second, then Wiktionary, GCIDE, Moby, WordNet, Etymonline, Idioms;
  order set by `scripts/koreader_dict_order.py`, source zips in non-books-backup/dictionary-data/; the Kobo zip also has
  Slovenian dicts and an R. Scott Bakker Second Apocalypse glossary, not installed),
  plugins wordtrail / favsync / bottommenus, patches `koreader/patches/2-icons-only-menus.lua`,
  `2-locked-footer-tap.lua`, `2-subprocess-safe-exit.lua` (black-screen fix, see lesson 12) and
  `2-no-double-open.lua` (a request to open the book already on screen is ignored; was 33 duplicate readers). Status bar = page/total + time left in book + progress bar, locked.
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
   which kills them in the background (KOReader opened blank). Set `pkg 1 1 1` (InkHome long-press > Keep in background).
5. **Haoqing resets the sleep image at every boot** (HaoqingWallpaper `resetStandbyImg` copies
   /vendor/media/standby.png over the active standby*.png). InkHome 2.2+ re-installs ours on each resume.
   `path_standby` is ignored.
6. **Haoqing draws the sleep screen ~5 s before Android's SCREEN_OFF**, so anything shown on it must be ready beforehand.
   That's why the screen-time counter was dropped (it needed a re-render every minute).
7. **Bottom-edge swipes** (left recents, centre home, right refresh) = `settings global GestureConfig` ->
   QuickStep HaoqingInputConsumer -> SystemUI HaoqingGestureHandler. Refresh = broadcast
   `com.haoqing.action.FULL_REFRESH` to com.android.systemui. `adb input swipe` does NOT trigger them.
8. **Full refresh stopped flashing after a session hang; a reboot fixed it.** Cause: e-ink thread EBookCvtCommit
   (closed libebook.so) spinning at 100% CPU. Try a reboot first next time.
9. An activity sharing the home task (singleTask + clearTaskOnLaunch) gets cleared on launch: give it its own taskAffinity.
10. Battery: health Good, but the gauge never learns capacity (always reports design 2107 mAh; charge_counter broken).
    2026-10-11 it charged normally on PC USB (+200-245 mA); the earlier ~300 mA drain was likely the e-ink thread hang.
    Standby is excellent: 10 h on battery = 32 min awake.
12. **KOReader black screen on resume (fixed 2026-10-11):** KOReader's forked helper processes ended with exit(); the
    Mali driver's atexit handler (eglTerminate -> Surface::disconnect) killed the window surface shared with the
    parent, so the next resume got createSurface errors (OutOfResourcesException) and a black screen. Patch
    `koreader/patches/2-subprocess-safe-exit.lua` makes children use _exit(). Quick cure if it ever recurs:
    force-stop KOReader. Tombstones in /data/tombstones show the stack.
11. Haoqing's own Settings app runs boot tasks (HaoqingReceiver BOOT_COMPLETED); look there first when something "comes back".

## Open items (pick up here)
- [x] **Library is EPUB now (2026-10-11):** 514 MOBIs converted in Calibre (`scripts/convert_mobi_to_epub.py`, EPUB added
  as an extra format, MOBI originals kept in Calibre), verified (`scripts/check_epub_conversion.py`: 514/514 same word
  count + cover), swapped in Meebook/Books (`scripts/swap_mobi_for_epub.py`: Calibre metadata embedded, 9 reading
  positions carried as last_percent) and synced. Only the Concise Oxford English Dictionary stays MOBI (its conversion
  hung). History + 1 WordTrail row repointed. Cover cache rebuilt for all 585 books (583 covers, ~109 MB db).
  Backups in backup/ (*-before-epub-20261011*, bookinfo_cache-before-prune). Tablet .stversions keeps the old MOBIs 30 days.
- [x] **Ask AI for phrases (2026-10-11):** wordtrail.koplugin adds "Ask AI (in context)" to the highlight (selection) menu
  via `ReaderHighlight:addToHighlightDialog("12_wordtrail_ask_ai")`; builds the question before onClose().
- [x] **Edge-swipe front light (2026-10-11):** these are Haoqing system swipes, `settings global GestureConfig`.
  configLc was "lightwarm" and configRc "lightcoldwarm" (both lights). Now configLc=lightcold (left = white),
  configRc=lightwarm (right = yellow). Values: lightcold/lightwarm/lightcoldwarm/lightopen. Launcher observes it live.
  Old value in backup/GestureConfig-before-20261011.json.
- [x] **KOReader bottom bar (2026-10-11):** settings.reader.lua footer: all_at_once, only page_progress + book_time_to_read,
  progress bar alongside, lock_tap=true (backup/settings.reader-before-footer-20261011.lua). Patch
  `koreader/patches/2-locked-footer-tap.lua` makes a tap on the locked bar do nothing (stock lets it open the menus).
- [x] **Charger no longer wakes the tablet (2026-10-11).** Haoqing's PowerManagerService.shouldWakeUpWhenPluggedOrUnpluggedLocked
  returns true whenever `sys.hq.dreamrun=1` (sleep screen up) before checking config_unplugTurnsOnScreen. Fix =
  (1) inkfix overlay v2 sets config_unplugTurnsOnScreen=false (`android/inkfix-overlay/build.sh`), and
  (2) Zygisk ON + **Vector v2.2** (JingMatrix, LSPosed successor; zip in tools/vector/, Magisk module `zygisk_vector`) +
  our Xposed module **InkHooks** (`android/inkhooks/`, build.sh; API stubs compile-only) hooking that method to obey
  the config. Scope MUST be `system/0` (not `android`): `/data/adb/lspd/cli scope set com.meebook.inkhooks system/0`.
  CLI: `/data/adb/lspd/cli modules ls|enable|disable`, logs in /data/adb/lspd/log/modules_*.log.
  Undo: `cli modules disable com.meebook.inkhooks` (or `touch /data/adb/modules/zygisk_vector/disable`) + reboot.
  If it ever boot-loops: adb still comes up early -> `su -c 'touch /data/adb/modules/zygisk_vector/disable'`, reboot.
- [x] **Look Up (2026-10-11):** KOReader dictionary popup buttons "define:WORD" and "Ask AI (in context)" (wordtrail.koplugin)
  open `inklook://search?q=` / `inklook://ask?q=` in InkHome's LookUpActivity (bare WebView, ~0.5 s, web engine
  pre-warmed by MainActivity). Search = lite.duckduckgo.com (no JS); Ask = search.brave.com/ask?q= (free, no
  account), asked "What does "<word as printed>" mean in this passage from "<title>" by <author>? "<sentence>"".
  Swap the AI site by editing ASK in LookUpActivity.java. FOSS Browser stays for normal browsing (cold start 2.2 s).
- [x] **2026-10-11 ADB:** Haoqing ships `ro.adb.secure=0` (no key check). Now `/data/adb/post-fs-data.d/adb-secure.sh`
  resetprops it to 1 and `/data/misc/adb/adb_keys` holds this PC's key; `/data/adb/service.d/adb-wifi.sh` (port 5555)
  only starts if secure=1. Copies in `device/`. Another PC gets the "Allow USB debugging?" prompt on the tablet.
- [ ] **E-ink driver hang:** composer thread `EBookCvtCommit` (pid of android.hardware.graphics.composer@2.1-service)
  got stuck at 100% CPU even asleep (130 CPU-min by 2026-10-11 11:00; normal is ~1%). Same night: KOReader
  createSurface errors at 00:24. A reboot cures it. Check: per-thread utime in /proc/<pid>/task/*/stat.
- [x] **Background whitelist is manual by choice (2026-10-11):** long-press an app in InkHome > "Keep in background"
  toggles `pkg 1 1 1` / `0 0 0` in /data/haoqing/haoqing_whitelist.conf (BackgroundWhitelist.java). Haoqing's
  manager = HaoqingReceiver.clearApp in com.android.settings (on CLEAR_MEMORY): keeps the 4 newest apps
  (sys.hq.HaoQingLimit), force-stops older non-whitelisted ones, re-reads the file when its mtime changes.
  No auto-whitelist script (user's decision). Backup: haoqing_whitelist.conf.bak-20261011.
- [x] **Haoqing corelog removed (2026-10-11):** it logged everything to /data/haoqing/corelog only for Settings >
  Help > Feedback, which zips it and uploads over plain HTTP to ai-admin.haoqingtech.com. init.rc forces it on,
  so clean804 overlays /system/bin/corelog with a no-op script (make-clean804.sh); old logs deleted.
- [ ] Status bar: battery % inside the icon + time left. Now feasible: add a hook to InkHooks (Vector is installed).
- [ ] E-ink contrast control (hq_contrast / fullscreen_flush) in InkHome Settings.
- [ ] Calibre word-list plugin; KOReader <-> Calibre wireless; verify all books show in KOReader; word games (parked).
- [ ] At project end: restore stock USB drivers on the PC.

## Working rule with the user
Show a plan and get an explicit OK before any device or system change; read-only checks are fine.
