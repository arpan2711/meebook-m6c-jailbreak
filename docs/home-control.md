# Home Control on the Meebook

InkHome 2.9 adds **Home Control**: the Smart Life (Tuya) lights and plug and the Dreo fan, with the
same features as the Amazfit watch app in `C:\Users\<you>\Desktop\amazfit-mods\home-control`.
The tablet talks to both clouds directly over Wi-Fi; no phone needed.

- Open it from the house icon on the right of the home-screen header, or the "Home Control" app icon.
- Tap a tile to switch a device on/off (black tile = on). `›` opens the detail screen:
  fan = on/off, speed −/+, mode, oscillation; light = on/off, brightness, warm/mid/cool, 6 colours.
- "All off" turns off everything that is on. The list refreshes each time the screen opens; no background polling.
- If Wi-Fi is still reconnecting after wake, it retries once after 2.5 s.

Code: `android/inkhome/src/com/meebook/inkhome/HomeCloud.java` (Java port of the watch app's
`app-side/tuya.js`, `dreo.js`, `actions.js`), `HomeControlActivity.java`, `HouseIcon.java`.

## Credentials

Kept only in InkHome's private storage, never on /sdcard (so Syncthing and other apps can't see them),
and never in this repo:

    /data/data/com.meebook.inkhome/files/tuya.json   {"accessId", "accessSecret", "region"}
    /data/data/com.meebook.inkhome/files/dreo.json   {"email", "passwordMd5"}

They are copies of `amazfit-mods/.tuya.json` and `amazfit-mods/.dreo.jason`. To update them (Git Bash,
`MSYS_NO_PATHCONV=1`, use `C:/...` paths for adb push):

    adb push C:/Users/<you>/Desktop/amazfit-mods/.tuya.json /data/local/tmp/tuya.json
    adb push C:/Users/<you>/Desktop/amazfit-mods/.dreo.jason /data/local/tmp/dreo.json
    adb shell 'su -c "D=/data/data/com.meebook.inkhome/files; U=\$(stat -c %u /data/data/com.meebook.inkhome);
      for f in tuya.json dreo.json; do cp /data/local/tmp/\$f \$D/\$f && chown \$U:\$U \$D/\$f && chmod 600 \$D/\$f;
      rm /data/local/tmp/\$f; done; restorecon -R \$D"'

They survive `adb install -r` updates but are lost if InkHome is uninstalled or its data cleared.
