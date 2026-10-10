# Meebook M6C: rooted, debloated reading tablet

Notes, scripts and small apps from turning a **Meebook M6C** (Rockchip RK3566, colour e-ink,
Android 11) into a clean, rooted e-reader built around KOReader. Everything here was done on my own
device and is written up so other M6C owners can reuse the parts they want.

> **Use at your own risk.** Rooting and flashing can brick a device or wipe data. Make a full
> backup first (see [docs/project-notes.md](docs/project-notes.md)). Nothing in this repo is
> official, and none of the Meebook firmware is included.

## What's here

| Folder | What it is |
|---|---|
| [`android/inkhome/`](android/inkhome) | **InkHome**, a minimal e-ink launcher: KOReader favourites as cover tiles, an app grid, a Syncthing "sync now" button, a sleep screen showing the current book's cover, and a settings screen (front light, warmth, Wi-Fi, sleep photo). Built with plain SDK tools, no Gradle. |
| [`android/inkfix-overlay/`](android/inkfix-overlay) | A static resource overlay that turns the wallpaper service back on (stock firmware ships it disabled, so third-party launchers show black) and sets a white default wallpaper. |
| [`koreader/`](koreader) | KOReader add-ons: **wordtrail** (saves every looked-up word with book, sentence and a jump-back link), **favsync** (keeps favourites in sync with the launcher), **bottommenus** (one tap at the bottom opens both menus), and a user patch that opens the menus as icon rows only. |
| [`magisk-modules/`](magisk-modules) | Generator for the `clean804` Magisk module: a systemless debloat of Google apps, Haoqing extras and unused overlays. Disable the module to undo it. |
| [`device/`](device) | Boot scripts for Magisk's `service.d` (for example, a small crash logger). |
| [`scripts/`](scripts) | Python and shell tools: A/B OTA `payload.bin` extractor, boot-image tools, a Rockchip rockusb reader, OTA apply/diff helpers, backup scripts, KOReader config helpers, Calibre curation and a StarDict builder. |
| [`docs/`](docs) | The working notes. Start with [`plan-clean-reader.md`](docs/plan-clean-reader.md) (what was done, step by step) and [`project-notes.md`](docs/project-notes.md) (device facts, backups, boot/AVB findings). |

## Key findings about the M6C

- **Unsigned boot images boot.** `ro.boot.flash.locked=0` and the verified-boot state is orange. A
  Magisk-patched boot image flashed to the inactive A/B slot boots fine. Test on the *other* slot first
  so you can always `fastboot set_active` back.
- **Updating while rooted:** apply the official OTA with `update_engine_client`, then Magisk-patch the new
  boot image before rebooting ([docs/plan-clean-reader.md](docs/plan-clean-reader.md), step 1).
- **Don't remove `com.haoqing.wallpaper`.** It draws the e-ink power-off and sleep screens. Without it,
  system_server crashes on every shutdown. Check each OEM app's intents before removing it.
- **Haoqing's background manager kills resumed apps.** Third-party apps default to `0 0 0` in
  `/data/haoqing/haoqing_whitelist.conf`, which makes KOReader open to a blank screen on resume. Set them
  to `pkg 1 1 1`.
- **Sleep screen:** the `path_standby` setting is ignored. The image comes from `/data/haoqing/standby.png`
  and `standby_1.png`.
- **Bottom-edge swipes** (left = recents, centre = home, right = full refresh) are configured in
  `settings global GestureConfig` and handled by Quickstep and SystemUI. A refresh is the broadcast
  `com.haoqing.action.FULL_REFRESH` sent to `com.android.systemui`. `adb shell input swipe` doesn't
  trigger these gestures; only a real finger does.
- **Screenshots** need root (`su -c "screencap -p"`) because the display is FLAG_SECURE.
- **Reading partitions** over Rockchip loader mode stops at 32 MiB. Once you have root, use `dd` instead.

## Building InkHome

You need the Android SDK (build-tools 35, platform 35), JDK 17, and your **own** signing keystore:

```sh
keytool -genkeypair -keystore tools/inkfix.jks -alias key -keyalg RSA -validity 10000
export KS_PASS=your-keystore-password
sh android/inkhome/build.sh
```

Edit the `SDK=` path at the top of `build.sh` for your machine. Paths written as `<you>` or
`<SERIAL>` in the scripts and docs are placeholders: put in your own user folder and adb serial.

## License

MIT, see [LICENSE](LICENSE). KOReader, Magisk, Syncthing and the Meebook firmware are their
authors' work and are not included here.
