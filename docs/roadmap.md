# Meebook M6C: what has changed from stock, and what's next

Written 2026-10-11 after a full review (docs, tablet state, PC side). Part 1 is the record; Part 2 is the plan.
Effort: XS < 15 min, S < 1 h, M = a session. Risk is for the tablet.

## Part 1: what we changed from the stock device

Almost all of it is **systemless**: Magisk modules and files in /data. The system partitions are untouched, so
the full raw dump in `backup/raw/full/` can still return the tablet to factory state.

| Area | Stock | Now |
|---|---|---|
| Firmware / root | 485, no root | Official 804 OTA with Magisk 30.7 kept through the update. Slot A = 485 fallback |
| Clean-up | 164 packages, Google, Haoqing extras | 73 packages. No Google (Aurora + F-Droid instead), Haoqing store/cloud/reader/test apps gone (`clean804`) |
| Privacy | Haoqing `corelog` logs everything for an unencrypted-HTTP "feedback" upload | Disabled (no-op overlay), logs deleted. Haoqing updater disabled |
| ADB | No key check (`ro.adb.secure=0`) | Key-only (`adb-secure.sh`), wireless ADB only when secure |
| Power | Charger plug/unplug woke the sleep screen | Fixed (inkfix overlay v2 + Zygisk + Vector + InkHooks). Standby measured: 32 min awake in 10 h |
| Stability | KOReader black screen after sleep; apps killed in background | Root cause fixed (`2-subprocess-safe-exit.lua`); whitelist toggle on long-press; crash recorder |
| Home screen | Haoqing launcher, no wallpaper service | InkHome 2.11: favourite covers, app grid, sync icon, Meebook Settings, sleep screen with current-book bubble + reading goal, Home Control, Look Up viewer |
| Gestures | Left = warm, right = warm + white | Left = white, right = warm. Bottom edge: recents / home / refresh |
| Reading | Stock KOReader | Literata, list view, icon menus, locked status bar (page/total + time left), WordTrail, define: / Ask AI for words and phrases |
| Dictionaries | GCIDE, WordNet | Oxford Dictionary of English, Shorter Oxford, Wiktionary, GCIDE, Moby, WordNet, Etymonline, Idioms |
| Library | 599 files, messy names, mostly MOBI, duplicates | 585 curated books in Calibre (genres, fixed metadata), all EPUB, covers cached, Syncthing to PC |
| Extras | none | Smart Life + Dreo control, wireless ADB, project docs |

## Part 2: what's next

### A. Reliability and backup (do first; protects everything above)
| # | Item | Why | Effort / risk |
|---|---|---|---|
| A1 | Back up `/sdcard/koreader/settings` (104 MB) | Reading statistics, settings, vocabulary builder, history and the cover cache exist **only on the tablet**. Add a send-only Syncthing folder (skip the cover cache) | S / none |
| A2 | Put the project under private git (exclude backup/, firmware/, books/ binaries) | No history today; one bad edit can't be undone | S / none |
| A3 | ~~Refresh the public repo~~ DONE 2026-10-11 | Export allow-list lacks InkHooks, overlay build, `device/`, new scripts and patches | S / none |
| A5 | Wi-Fi keeps dropping | Twice on 2026-10-11 the tablet showed "Wi-Fi on, not connected" with the saved network in range (fixed by toggling Wi-Fi). Find out why (Haoqing Wi-Fi sleep controller?) and auto-reconnect | S / low |
| A4 | E-ink driver watchdog | `EBookCvtCommit` spins at 100% at times (seen again today: 52 CPU-min in 2 h, idle when checked). Log it + notify "restart recommended"; add EBook/hwcomposer tags to crashwatch | S / low |

### B. Speed and efficiency
| # | Item | Why | Effort / risk |
|---|---|---|---|
| B1 | ~~Don't re-open the book KOReader already has open~~ DONE 2026-10-11 (`2-no-double-open.lua`) | 33 "ReaderUI instance mismatch" warnings: tapping the current book's cover makes KOReader build a second reader. Bring KOReader to the front instead. Faster, fewer failure paths | S / low |
| B2 | PDF / heavy-file test (original goal 3, never done) | 9 PDFs + 1 DjVu. Test KOReader speed and settings; decide if a second app is needed | S / none |
| B3 | Calibre: EPUB by default | So new books never arrive as MOBI (auto-convert on add) | XS / none |
| B4 | Storage tidy | 631 MB of old MOBIs in Syncthing's tablet trash (auto-deletes in 30 days), leftover `/sdcard/Download` | XS / none |
| B5 | Tidy ODE entries | Oxford Dictionary of English entries are one run-on paragraph; reformat to clean HTML (sense per line) like the Shorter Oxford | S / none |
| B6 | Optional: Syncthing also on USB charging | Today it syncs only on AC; PC-USB needs "Sync now". Convenience vs battery | XS / none |

### C. Security and privacy
| # | Item | Why | Effort / risk |
|---|---|---|---|
| C1 | Network audit, then block Haoqing telemetry via Magisk systemless hosts | `persist.haoqing.hasnewversion=1` shows the OTA checker still phones home. See every host the system contacts for a day, then block what isn't needed | S-M / low |
| C2 | Keep exposure low on an old patch level | Security patch is 2021-06-05 (Android 11) and can't be updated. WebView is current (139). Install only F-Droid/Aurora apps; add a wireless-ADB on/off switch in Meebook Settings | XS-S / none |
| C3 | Root hygiene (no action) | Only InkHome and the ADB shell have root; Vector is scoped to the system framework only. Keep it that way | none |

### D. Screen quality
| # | Item | Why | Effort / risk |
|---|---|---|---|
| D1 | E-ink tuning page in Meebook Settings (open item) | Haoqing exposes contrast (`hq_contrast`=33), full-refresh interval (`fullscreen_flush`=20 pages, launcher 10) and 256-grey mode. The e-ink library reads `persist.ebook.*` image controls (contrast/luma/saturation gain = 64, black/white stretch, sharpening, false-colour removal). A live before/after test page to pick the best text and cover rendering | M / low (properties, reversible) |
| D2 | KOReader text rendering | Font weight, hinting and gamma for Literata on this panel; side-by-side test pages | S / none |
| D3 | Per-app refresh modes | Haoqing Settings has per-app e-ink modes: quality mode for KOReader, fast mode for Look Up and InkHome scrolling | S / none |

### E. Features and loose ends
| # | Item | Effort |
|---|---|---|
| E1 | Battery % in the status-bar icon + time left (one more InkHooks hook) | M |
| E2 | Calibre word-list plugin / PC word view: open the book at the passage (original goal 6) | M |
| E3 | Word games (parked) | M |
| E4 | Waiting on you: test Home Control lights/plug, the new edge swipes, and the black-screen fix over a few days | - |
| E5 | At project end: restore the stock USB drivers on the PC | XS |

**Suggested order:** A1 → A2 → A5 → A4 → D1 → C1 → B2 → the rest as you like.

Done since writing: B1 (no double-open), device-stats tile on the home screen, public repo refreshed (A3).
