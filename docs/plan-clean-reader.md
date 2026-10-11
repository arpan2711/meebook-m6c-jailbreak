# Plan: clean reading tablet + word-capture system

Started 2026-10-10. **Decisions made 2026-10-10** (user-approved). Work top to bottom; tick boxes as done.
If resuming: read this file + README "Progress log" first. Device rules: one Claude session only,
single `fastboot getvar` calls only (never `getvar all`), `fastboot reboot` may need sending twice.

## User's goals
1. A clean tablet: remove everything unnecessary, including the user's own apps and Google.
2. KOReader as the fast reader for all formats. The library must show every book on the device.
3. A fast app for PDFs and heavy files (test KOReader first).
4. A dictionary that saves **every** looked-up word with source book, sentence/paragraph, and **jump-back**.
5. A DuckDuckGo search for any word, in a lightweight browser. The word is saved either way.
6. PC side: Calibre (master library) + a Calibre plugin for the word list (open book at passage). Games later.

## Decisions
- **Update to 804, staying rooted**, keeping all progress (data, KOReader, words, debloat).
- **Google: option B.** Remove ALL Google (GMS, GSF, Play Store, Gmail, Drive, Play Books, TTS, sync, ...).
  Install **Aurora Store** (Play apps without Google) + **F-Droid**.
- Remove the user's own apps (Telegram, Reddit, Chrome, Brave, Remote Desktop, ReadEra, Wikipedia, SafetyCore,
  Verifier) and the small apps (Clock, Gallery, Calculator, Haoqing File Manager, Pinyin IME).
  Keep AOSP Files (documentsui) + AOSP keyboard. Keep Magisk and KOReader.
- **Books:** clean up all 599 (530 MOBI, 58 EPUB, 11 PDF) in Calibre on the PC: correct title/author,
  no duplicates. Then all of them appear in KOReader. Preserve KOReader per-book data (.sdr: position, highlights).
- **Plugin:** KOReader word-capture plugin (+ DuckDuckGo), then the Calibre word plugin.
- Show the user the exact removal list before step 2 runs.

## Step 1: 804 update with root
Route: root 485 on slot A → restore stock boot_a → apply official OTA with `update_engine_client`
(Haoqing updater stays disabled) → Magisk-patch the new boot_b before reboot → boot 804 rooted.
- [x] 1a Stock 485 boot = `firmware/boot_485_stock_full.img` (full 128 MiB partition; the first 106532864 B hash 608fc5f3… = OTA source. The trimmed image is "corrupt" to Magisk because the DTB runs past the OTA size)
- [x] 1b `firmware/magisk_patched_485.img` (on-device boot_patch.sh, KEEPVERITY=true KEEPFORCEENCRYPT=true RECOVERYMODE=false)
- [x] 1c dd to boot_a (verified), set_active a, booted 485 rooted
- [x] 1d Stock boot_a restored (verified sha256 6c8f0336… = original dump)
- [x] 1e OTA applied (kSuccess, 9.5 min) at /data/ota_package/update.zip; update_engine_client --offset=694 --size=259125308 (headers from payload_properties.txt). Started 17:01:50
- [x] 1f boot_b verified = official 804 (fdcdb30f…), patched → `firmware/magisk_patched_804.img` (stock: `firmware/boot_804_stock_full.img`), dd + verified
- [x] 1g **DONE 2026-10-10 17:15**: slot B = 804 rooted, marked successful. 599 books, KOReader + 170 words, debloat, disabled updater all kept. WebView 139, Wi-Fi OK, no crashes. Slot A = 485 with STOCK boot (re-root with magisk_patched_485.img if needed)
- Fallback: B fails ⇒ auto-fallback after retries or `fastboot set_active a` (485).

## Step 2: Clean-up (show the list first)
- [x] Removal list drafted + dependency-checked against 804 framework/SystemUI/Settings/Haoqing Settings:
      `docs/removal-list-804.tsv` (107 entries). Floating Ball ref = broadcast (safe); MEE OS ref = refresh-rate
      string compare (safe). KEEP: Haoqing Settings (per-app e-ink refresh), Haoqing Wallpaper (shutdown screen),
      Quickstep/launcher3 (SystemUI gesture recents), Files, AOSP keyboard, navbar gestural+threebutton overlays,
      providers/telephony glue, ext.services/ext.shared, cts shims, System Updates stays disabled (not removed).
- [x] Approved and applied 2026-10-10 17:27
- [x] Magisk module `clean804` (/data/adb/modules/clean804, generator `magisk-modules/make-clean804.sh`): 86 dir .replace + 9 empty-file placeholders. 12 user apps `pm uninstall`ed, Play updates of GMS/Play Store/TTS uninstalled first. 164 → 73 packages. Log: backup/logical/info/clean-step2.log
- [x] Home = **InkHome** (our own launcher, `android/inkhome/`, build with `sh android/inkhome/build.sh`): all apps as an icon grid on white, no search, no animations, long-press = app info. Olauncher/KISS/Fossify tried and removed (user wanted light theme + icons + app grid). NOTE: Haoqing's Quickstep only declares SECONDARY_HOME
- [x] **InkHome 1.1** (2026-10-10): KOReader favourites (settings/collection.lua "favorites") shown as cover tiles in a Books section above Apps; covers extracted from EPUB/MOBI/AZW3/PDF (Covers.java), cached; tap opens the book in KOReader (file:// intent). Needs MANAGE_EXTERNAL_STORAGE (granted via appops). Build uses core-lambda-stubs.jar.
- [x] **favsync plugin** (`koreader/favsync.koplugin`): (1) writes collection.lua immediately on add/remove (KOReader normally waits for the file browser to close); (2) re-reads collection.lua before collection queries/changes/the Favorites view, so removals made in InkHome aren't undone by KOReader's in-memory copy (KOReader otherwise reads it only at startup); (3) adds one-tap "Add to favorites"/"Remove from favorites" to the file browser long-press dialog. InkHome 1.3: long-press a book = Open / Remove from favourites (rewrites the favorites block, renumbered for ipairs); rechecks 1.5 s/4 s after resume.
- [x] **Diagnostics** (2026-10-10): KOReader once opened blank: SurfaceFlinger createSurface failed (FAILED_TRANSACTION / OutOfResourcesException) though layer count was normal (55); a clean restart fixed it. ROOT CAUSE FOUND: happened only when KOReader resumed from background (crash = InsetsAnimations 'Failed to initialize display event receiver'); Haoqing's background manager (persist.haoqing.clearapp) had KOReader as `org.koreader.launcher 0 0 0` in /data/haoqing/haoqing_whitelist.conf. Set to `1 1 1` (backup .bak-koreader) + reboot: 3 home->KOReader resume cycles, 0 surface errors, 0 crashes. Now automatic: /data/adb/service.d/haoqing-whitelist.sh sets every `pm list packages -3` app to `1 1 1` at boot and every 10 min (backup .bak-all). Verified after reboot; FOSS Browser resume clean. Crash recorder: /data/adb/service.d/crashwatch.sh -> /data/local/crashwatch/log.txt (3x1MB rotating; errors + KOReader + NativeGlue/SurfaceView). Wireless ADB: /data/adb/service.d/adb-wifi.sh (port 5555, works only while awake; Haoqing turns Wi-Fi off in sleep).
- [x] **Wallpaper fix**: Haoqing ships `config_enableWallpaperService=false`, so every wallpaper-backed launcher is black. Magisk module `inkfix` = static RRO `android/inkfix-overlay/` (targetSdk 28, so exempt from overlayable policy) sets it true + white default_wallpaper. Signing key: tools/inkfix.jks (password in env KS_PASS). Portable JDK: tools/jdk17
- [x] **Screenshots**: the display has FLAG_SECURE, so `screencap` from adb shell is black; use `adb exec-out 'su -c "screencap -p"'`
- [x] F-Droid 1.23.2 (official cert 43:23:8D…CC:AB verified) + Aurora Store 4.8.4 installed
- [x] Verified: clean reboot, shutdown activity, Wi-Fi, keyboard, KOReader launches, no crashes, 1.48 GB RAM free. Undo: disable the clean804 module in Magisk

## Step 3: Books
Decisions: keep EPUB over MOBI; single MOBIs stay MOBI; tablet folders by **genre** (scripts/genre_map.py).
- [x] Non-books removed from tablet 2026-10-10, backed up + sha256-verified in `non-books-backup/` (personal documents; installers; dictionary-data incl. /sdcard/Dict stardict; pictures; app-leftovers incl. ReadEra backups)
- [x] Found 3 hidden books (Telegram DOC files = The Hobbit, Maus; arXiv 0705.4306) -> added
- [x] All books staged in books/staging (short names; books/manifest.tsv maps device path <-> staged <-> sha256), imported to Calibre library C:/Users/<you>/Calibre Library (601 books)
- [x] Calibre installed (user). Online metadata fetch mostly blocked (13/124), so books were curated by hand: `books/curation.json` (genre overrides, title/author fixes, duplicate groups) applied by `scripts/apply_curation.py` (run via calibre-debug). 18 duplicates removed (backup books/duplicates-backup), 583 books, Calibre column #genre. Full list: docs/library-by-genre.md
- [ ] Fix metadata, merge duplicates (prefer EPUB over MOBI where both exist? ask)
- [ ] KOReader ↔ Calibre wireless connection; send the library; keep the .sdr reading data
- [ ] Verify every book shows in KOReader; test PDFs for speed

## Lock screen
- [x] Sleep screen: `path_standby` setting is IGNORED. HaoqingWallpaper FunctionUtil.getStandbyPath() loads /data/haoqing/standby_1.png (if 'custom standby' selected) else /data/haoqing/standby.png, else /vendor/media/standby.png; book-cover mode via global haoqing_open_book_path=1 (off). Photo (1072x1448) written to both standby.png + standby_1.png (system:system 600, datahaoqing_data_file); originals in lockscreen/original-standby*.png. Resize script lockscreen/make_lockscreen.py (calibre-debug).

## Dictionaries
- [x] Added to /sdcard/koreader/data/dict: Wiktionary en-en (xxyzz 20260928, 798k), Etymonline (46k), English Idioms (8.5k); kept GCIDE + WordNet 1.7. Order set via scripts/koreader_dict_order.py. Verified lookup popup on device.


## Round 2 tweaks (2026-10-10 evening)
- [x] #18 Wireless ADB OFF (service.d/adb-wifi.sh removed, port reset); Wi-Fi sleeps with the screen (Haoqing default).
- [x] #5 Animations: already 0 on this e-ink build (window/transition/animator).
- [x] #6 KOReader lists: bookinfo_cache.sqlite3 config -> filemanager/history/collection_display_mode = list_image_meta, files_per_page 12 (backup books/typo/bookinfo_cache-before.sqlite3). Shelves sort by last-opened (A-Z available: settings collate).
- [x] #7 Typography: Literata 3.103 in /sdcard/koreader/fonts/literata; defaults cre_font=Literata, size 23, line spacing 110, margins 12/10/10, embedded CSS on (publisher layout), embedded fonts off; per-book overrides cleared in 44 books (scripts/koreader_typography.py; snapshot books/typo/before.tar).
- [x] #9 Sleep screen: brighter base /sdcard/InkHome/lockscreen-base.png (lockscreen/brighten.py gamma .72 lift 18); InkHome 1.4+ composes a thought bubble with the current book's cover (Lockscreen.java; current book = newest .sdr metadata) and installs it to /data/haoqing/standby*.png via su (Magisk policy uid 10002).
- [x] #11 Moby Thesaurus II built from Gutenberg #3202 (scripts/build_moby_stardict.py), installed 3rd in dict order. Oxford/Collins: copyrighted, not installed.
- [x] #17 Syncthing-Fork: always_run_in_background, power_source=ac_power (sync only while charging + Wi-Fi), broadcast_service_control on (prefs edited with app DISABLED, else it rewrites them). InkHome 1.5 "Sync now" button: START broadcast, FOLLOW after 3 min. Real unplug test pending (dumpsys battery unplug doesn't trigger it).
- [x] #19 **Meebook Settings** (InkHome 2.0, SettingsActivity, own launcher icon; Haoqing 'User Settings' hidden from the grid): Wi-Fi/Bluetooth toggles (su svc), network name (su cmd wifi status), front light brightness = settings system screen_brightness (cold, /sys/class/backlight/backlight) and warmth = haoqing_warm_light (/sys/class/backlight/backlight_warm), both 0-255 via su; sleep timeout; sleep photo picker (crop 1072x1448 + brighten -> /sdcard/InkHome/lockscreen-base.png); bubble on/off; Sync now; storage/books/battery; about; link to full Android settings. Not yet: e-ink contrast (hq_contrast) / fullscreen_flush, needs eye-testing on the panel.
- [x] Sync icon next to the 'Books' title (SyncIcon.java) instead of a button row; build.sh now uses javac -encoding UTF-8 (the '…' was mojibake) and fails loudly on errors.
- Note: Wi-Fi was found OFF after a Somnambulator dream test (re-enabled with svc wifi enable).


## Round 3 (2026-10-10 late)
- [x] KOReader menus: activate_menu="tap" (no swipe menus; a swipe down from the top only reveals Android's status bar, immersive sticky). defaults.custom.lua moves menu zones to the bottom strip; plugin `koreader/bottommenus.koplugin` makes ONE tap anywhere in the bottom 1/8 open BOTH menus (config panel + main menu). Footer mode restored to 1.
- [x] Files app: Fossify File Manager 13 (F-Droid, signer Fossify/Naveen Singh, apksigner-verified), MANAGE_EXTERNAL_STORAGE granted, light theme written to Prefs.xml (app disabled during the edit), whitelisted `1 1 1`. Google Files (documentsui) hidden from InkHome but kept installed (system file picker).
- [x] KOReader menus open as **icon rows only** (top tab bar + bottom settings bar); tapping an icon expands it. User patch `koreader/patches/2-icons-only-menus.lua` -> /sdcard/koreader/patches/ (wraps TouchMenu init/switchMenuTab/updateItems and ConfigDialog init/onShowConfigPanel/update). Delete the file to undo.
- [x] InkHome: the sync icon is now a **cloud with up/down arrows** (SyncIcon.java, 40x30dp, 14dp gap from "Books"); the old circular arrows looked like the status-bar refresh button.
- [x] **Bottom-edge swipes** explained: GestureConfig (settings global) left=recent, centre=sendhome, right=refresh. Handled by Launcher3QuickStepGo `com.haoqing.HaoqingInputConsumer` -> SystemUiProxy.screenBottom* -> SystemUI `HaoqingGestureHandler.runGestureConf` -> "refresh" = broadcast `com.haoqing.action.FULL_REFRESH` -> `EinkManager.fullRefreshNoDelay()` (bumps sys.eink.one_full_mode_timeline + SurfaceFlinger txn 1008). Logs `hrj screenBottom*` in System.out. `adb shell input swipe` does NOT trigger these; only a real finger does. Manual test: `su -c "am broadcast -a com.haoqing.action.FULL_REFRESH -p com.android.systemui"`.
- [x] **Full refresh stopped flashing everywhere** (swipe and status-bar button) after the previous session hung (tablet restarted ~20:19). The whole chain still ran; the panel just didn't flash. **A reboot fixed it.** Note: if refresh breaks again, reboot first. Also, InkHome now ignores touches that start in the bottom 28dp (MainActivity.dispatchTouchEvent), so its ScrollView can't go into A2 fast mode during an edge swipe.
- [x] Battery check: rk817 gauge, health Good, 31 C, full = design 2107 mAh (gauge doesn't really learn wear). On the PC's USB port it shows "Charging" but drains about 300 mA net; use a wall charger.
- [x] **Settings icon didn't open** (2026-10-10 night): SettingsActivity shared the home task, and MainActivity's singleTask + clearTaskOnLaunch cleared it, so a tap just showed home again. Fixed by giving it its own `android:taskAffinity` (InkHome 2.2).
- [x] **Sleep screen kept going back to the Meebook default.** ROOT CAUSE: HaoqingWallpaper `DataControl.initData` -> `WallpaperControl.resetStandbyImg()` runs at every boot and copies /vendor/media/standby.png over the active standby file (standby.png, or standby_1.png when persist.hq.standbysetting=1) whenever the sizes differ. InkHome only rebuilt when the book changed, so it never noticed. FIX (InkHome 2.2): on every resume it compares the installed standby*.png sizes with its own files/standby.png (via su stat) and reinstalls if they differ. Verified by copying the default over both files: restored on the next home resume.
- [x] **New sleep photo crop** (user's red marks): `lockscreen/crop_lockscreen.py` crops photo-original.png at x 280-1500, bottom 1749 (top follows from the 1072:1448 ratio, so uniform scale 0.879, no stretch), then brighten.py -> lockscreen/lockscreen-base-cropped.png -> /sdcard/InkHome/lockscreen-base.png (previous base: lockscreen/lockscreen-base-before-crop.png). The bubble position now comes from /sdcard/InkHome/lockscreen-bubble.txt `CX CY RX RY` = `730 275 230 180` (copy in lockscreen/); the trail circles follow the cloud. Picking a new photo in Settings deletes the file, which falls back to the default 815 245 215 172. Result: lockscreen/standby-generated.png.
- [x] **Bigger bubble + bigger cover** (InkHome 2.4): cloud bumps scale with the cloud; lockscreen-bubble.txt may add trail circles as `x y r` triples. Now `750 420 245 310  285 470 12  345 452 18  415 432 26`. The cover is deliberately large (height = 2.1 x RY, about 425x650, loaded at 640 px) and spills over the cloud edge, as the user asked. A screen-time counter was tried (2.3) and DROPPED for CPU cost: Haoqing draws the sleep screen about 5 s before Android's SCREEN_OFF, so keeping it current needed a re-render every minute. Usage access was reset to default.
- [x] (superseded 2026-10-11: user chose a manual whitelist toggle, InkHome long-press) Persistent keeper (Magisk service.d script that re-applies the sleep image + `1 1 1` whitelist for new apps after boot and every 5 min): written as a plan; creating the boot script was blocked by the permission system, waiting on the user.
- [x] (resolved 2026-10-11: not restored by choice) **Found 2026-10-10 late:** /data/adb/service.d/haoqing-whitelist.sh is GONE (service.d last changed 20:07, when adb-wifi.sh was removed). Existing apps keep `1 1 1` in /data/haoqing/haoqing_whitelist.conf, but NEW apps won't be whitelisted automatically (KOReader-style resume crashes). Restore the script (ask first). Only crashwatch.sh remains (copy in device/service.d/).
- [ ] Status bar: battery % inside the icon + time remaining beside it: needs SystemUI code changes. Plan: Zygisk + LSPosed + a small custom Xposed module hooking BatteryMeterView (SystemUI is platform-signed, so patching its APK is not possible). Awaiting the user's decision.

## Public repo
- [x] https://github.com/arpan2711/meebook-m6c-jailbreak (public, MIT, created 2026-10-10). Built from an allow-list +
  redaction by `scripts/export_public.py` into ../meebook-m6c-public (its own git repo; commits use the GitHub
  no-reply email). The public README/LICENSE/.gitignore live in `public/`. To update: run the script, then
  `git add -A && git commit && git push` in ../meebook-m6c-public. The script stops if the serial, username,
  PIN or LAN IPs remain. Never publish backup/, firmware/, books/, tools/, lockscreen photos or tools/inkfix.jks.

## Step 4: Plugins
- [x] **WordTrail plugin** (`koreader/wordtrail.koplugin`, on device in /sdcard/koreader/plugins): wraps the vocab builder's "Add" (manual, user's choice) to store word, book, authors, file, xpointer/page, sentence, paragraph in /sdcard/WordTrail/wordtrail.sqlite3 (journal DELETE, sync-friendly). Many sources per word. Imported 171 vocab words once (import guarded by DB, not settings). Menu: search tab > Word trail (list > sources > tap = jump, hold = paragraph). Verified on device.
- [x] "Search DuckDuckGo" button in the dictionary popup -> FOSS Browser (F-Droid, default browser) at duckduckgo.com/?q= (the html. endpoint shows a bot check). REPLACED 2026-10-11 by "define:WORD" (lite.duckduckgo.com) + "Ask AI (in context)" (search.brave.com/ask) opening InkHome's Look Up viewer (inklook://).
- [x] In-reader word list with tap-to-jump (verified)
- [x] **Syncthing sync** (2026-10-10): PC v2.1.6 in tools/syncthing, config C:/Users/<you>/Meebook/.syncthing (PC id <id>), autostart via Startup/Syncthing-Meebook.vbs. Tablet: Syncthing-Fork 2.1.5 (id <id>), whitelisted in /data/haoqing/haoqing_whitelist.conf (`1 1 1`, backup .bak-20261010) + deviceidle whitelist. Folders: `meebook-books` /sdcard/Books <-> C:/Users/<you>/Meebook/Books (send-receive, 30-day trashcan both sides); `wordtrail` /sdcard/WordTrail (send-only) -> C:/Users/<you>/Meebook/WordTrail (receive-only). Setup script: scripts/syncthing_setup.py. Verified add + delete both ways.
- [x] Calibre 'Connect to folder' save template = {#genre}/{authors} - {title} (device_drivers_FOLDER_DEVICE_USER.py.json), so Send to device -> Meebook/Books -> tablet
- [ ] Calibre plugin: word list + passages + open in Calibre viewer at that position
- [ ] Later: word games

## Round 4 (2026-10-11)
- [x] Home Control in InkHome 2.9 (Smart Life + Dreo), see docs/home-control.md.
- [x] Wireless ADB back, now with key auth (firmware had ro.adb.secure=0): post-fs-data.d/adb-secure.sh + adb_keys.
- [x] Audit: standby excellent, nothing core missing; e-ink thread EBookCvtCommit hang found (reboot fixes).
- [x] Haoqing corelog (Feedback upload logger, plain HTTP) disabled via clean804 no-op overlay; logs deleted.
- [x] InkHome 2.10 long-press app menu: App info / Keep in background (whitelist file, re-read by mtime).
- [x] InkHome 2.11 Look Up viewer + KOReader define:/Ask AI buttons.
- [x] Charger no longer wakes the sleep screen: inkfix v2 + Zygisk + Vector v2.2 + InkHooks (scope system/0).
- [x] Ask AI also from the multi-word selection menu; edge swipes fixed (left = white, right = yellow, were warm/both);
  KOReader status bar = page/total + time left + progress bar, locked (lock_tap + 2-locked-footer-tap.lua patch).
- [x] KOReader black screen after sleep: root cause = forked subprocess exit() running Mali eglTerminate on the shared
  surface; fixed with koreader/patches/2-subprocess-safe-exit.lua (_exit in children). 3 sleep/wake cycles clean.
- [x] MOBI -> EPUB for the whole library (514 books, dictionary stays MOBI) + full cover-cache rebuild (2.5 min,
  one-shot patch koreader/patches/2-precache-covers-once.lua, removed from the tablet after use). Browsing is instant.
- [x] Shorter Oxford English Dictionary 6th ed. (StarDict + e-ink CSS, /sdcard/koreader/data/dict/shorter-oxford/) added as dictionary #1.
- [x] Oxford Dictionary of English (StarDict from Apple's macOS dictionary, plain text) added as dictionary #1
  (/sdcard/koreader/data/dict/oxford-english/ODE.*); Shorter Oxford now #2.
