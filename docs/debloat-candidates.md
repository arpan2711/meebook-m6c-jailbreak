# Debloat candidates — Meebook M6C (build 485)

Source: 161 system packages (`backup/logical/info/packages-system.txt`) + 13 user apps.
WiFi-only e-ink reader, no cellular, no cameras of note. User actively uses Google
(Gmail, Drive, Play Books installed) and reading apps (KOReader, ReadEra, Wikipedia).

Two ways to apply each item:
- **Live, reversible (Phase 0a):** `adb shell pm uninstall -k --user 0 <pkg>` (removes for user 0,
  keeps APK so a factory reset / `pm install-existing` brings it back), or `pm disable-user <pkg>`.
- **Baked into image (Phase 1+):** delete the APK dir from the rebuilt system/product image.

Start with live disable to shake out breakage, then bake the proven set into the image.

Legend: ✅ safe  ⚠️ test first  ⛔ keep

---

## ✅ Haoqing/OEM bloat — safe to remove
| pkg | what | note |
|---|---|---|
| com.haoqing.app.fileCloud | Netdisk | cloud storage client |
| com.haoqing.appstore | App Store | 3rd-party store |
| com.haoqing.floatingbutton | floating button | optional UI gimmick |
| com.haoqing.accountmanagement | Haoqing account | OEM account |
| com.android.lemonread.calculator | Haoqing Calculator | replaceable |
| com.DeviceTest | Haoqing DeviceTest | factory test |
| com.cghs.stresstest | StressTest | factory test |
| android.rk.RockVideoPlayer | Rockchip 4K video player | useless on e-ink |
| com.haoqing.app.fileCloud, …Netdisk | duplicate of above | |

⚠️ **com.haoqing.wifitransfer** (WiFiTransfer) — user has a `/sdcard/WifiTransfer` folder full of books;
they may use this to sideload. Keep unless they confirm otherwise.

## ⛔ Haoqing — keep (core to the device UX)
- **com.haoqing.wallpaper** — NOT a wallpaper toy: provides `HaoqingShutDownActivity` (`com.haoqing.action.ShutDown`),
  the e-ink power-off screen. Framework `ShutdownThread` starts it; if missing, system_server crashes on every
  reboot/shutdown (soft-restart instead). Removed in batch 1 by mistake, restored 2026-10-10.
- **com.haoqing.app.hqreader** — this is the **Launcher/home**. Removing with no replacement = no home screen.
- **com.haoqing.settings** — Meebook's Settings. Verify which Settings is active before touching
  (AOSP `com.android.settings` also present). Likely the primary.
- com.haoqing.systemupdate — OTA updater (keep if we still want official updates; else removable).
- com.haoqing.filemanager — file manager (keep, useful).

## ✅ Telephony stack — safe (no cellular hardware)
- com.android.providers.telephony ⚠️ (some framework glue expects it; **disable** rather than delete first)
- com.android.cellbroadcastreceiver, com.android.cellbroadcastservice
- com.android.smspush (WAPPushManager / com.android.smspush)
- com.android.providers.blockednumber

## ✅ AOSP apps — safe (unused / duplicated by user apps)
- com.android.browser (AOSP Browser — user has Chrome/Brave)
- com.android.music, com.android.musicfx (no real audio use)
- com.android.soundrecorder
- com.android.egg (Easter egg)
- com.android.dreams.basic (BasicDreams), com.android.dreams.phototable (screensavers)
- com.android.wallpaper.livepicker, com.android.wallpaperbackup, com.android.wallpapercropper,
  com.android.wallpaperpicker (e-ink, no live wallpaper)
- com.android.printspooler, com.android.bips (BuiltInPrint), com.android.printservice.recommendation
- com.android.traceur (perf tracing / dev)
- com.android.bookmarkprovider
- com.google.android.syncadapters.calendar/contacts ⚠️ (only if not syncing Google calendar/contacts)

## ✅ Theme/overlay clutter — safe, cosmetic, many files
Dozens of RRO overlays. Removing them just reduces theming options; the active ones can stay.
- AccentColor* (14): amethyst, aquamarine, black, carbon, cinnamon, green, ocean, orchid, palette,
  purple, sand, space, tangerine (+ default)
- IconShape* (7): pebble, roundedrect, squircle, taperedrect, teardrop, vessel (+roundedrect)
- IconPack_* (5 families × 5 targets = ~35): circular, filled, kai, rounded, sam, victor
- DisplayCutoutEmulation* (5): corner/double/hole/tall/waterfall — no cutout on this panel
- NavigationBarMode* (6 gestural variants) — keep the one in use; rest removable
- com.android.theme.font.notoserifsource (optional font overlay)

→ These are in `product/overlay/`. Bulk-removable; biggest file-count win, low risk.

## ⚠️ Google — keep (user depends on Google)
Keep: com.google.android.gms (GMS), com.android.vending (Play Store), com.google.android.gsf,
com.google.android.gsf.login, com.google.android.tts (TTS — useful for an e-reader),
com.google.android.backuptransport, com.google.android.onetimeinitializer.
(Only strip these for a deliberately Google-free build — not this user's case.)

## ⛔ Critical — never remove
framework-res (android), com.android.systemui, the active Settings, com.android.providers.{media,
media.module, contacts, calendar, downloads, settings, userdictionary}, com.android.permissioncontroller,
com.android.packageinstaller, com.android.documentsui, com.android.externalstorage,
com.android.networkstack.*, com.android.wifi.resources, com.android.mtp (USB file transfer),
com.android.webview (many apps embed it), com.android.keychain, com.android.certinstaller,
com.android.inputmethod.latin (keyboard — keep at least one IME),
com.android.inputmethod.pinyin ⚠️ (Chinese IME; removable if user doesn't type Chinese),
com.android.se, com.android.bluetooth + bluetoothmidiservice ⚠️ (device has BT; keep if used).

---

## Rough first "live disable" batch (reversible, no reflash)
```
# OEM/factory bloat
pm uninstall -k --user 0 com.haoqing.app.fileCloud
pm uninstall -k --user 0 com.haoqing.appstore
pm uninstall -k --user 0 com.haoqing.floatingbutton
pm uninstall -k --user 0 com.haoqing.accountmanagement
pm uninstall -k --user 0 com.DeviceTest
pm uninstall -k --user 0 com.cghs.stresstest
pm uninstall -k --user 0 android.rk.RockVideoPlayer
# AOSP unused
pm uninstall -k --user 0 com.android.browser
pm uninstall -k --user 0 com.android.egg
pm uninstall -k --user 0 com.android.dreams.basic
pm uninstall -k --user 0 com.android.dreams.phototable
pm uninstall -k --user 0 com.android.printspooler
pm uninstall -k --user 0 com.android.bips
pm uninstall -k --user 0 com.android.wallpaper.livepicker
# telephony (wifi-only) — disable, don't uninstall, in case of framework glue
pm disable-user com.android.cellbroadcastreceiver
pm disable-user com.android.smspush
```
Reverse any item with `pm install-existing <pkg>` (or re-enable). Verify home screen, Settings,
reading apps, Wi-Fi, Bluetooth after each batch.
