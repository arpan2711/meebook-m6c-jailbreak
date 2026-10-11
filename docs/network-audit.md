# Network audit (2026-10-11)

**Method.** (1) Static: every `http(s)://host` string in the dex of all system APKs/JARs. (2) Live: a one-shot
boot script (`device/netcap-once.sh`) logged every TCP/UDP connection with its owning UID once a second for
5 min after boot; decoded with `scripts/netcap_report.py`. (3) A Wi-Fi off/on repeat with logcat to name the
component behind a system (uid 1000) connection.

**Live findings.** Apart from Syncthing (PC, phone, relays), the router and time sync, one OEM connection:
Haoqing's Settings (`CertificationUtils.saveUrlCertificationImage`) downloads
`http://files.haoqingtech.com/img_meebook_ncc_certification_q78.png` over plain HTTP on **every Wi-Fi connect**
(China Telecom CDN, e.g. 1.71.157.41 / 42.81.31.41). It leaks IP, model and online times.

**Found in code, not seen live:** Haoqing updater `api.haoqingtech.com` + hard-coded IP `180.149.144.31` and
Skyhook location `sapi.skyhookwireless.com` (app `com.haoqing.systemupdate` is disabled); Settings feedback/log
upload `server.` / `ai-admin.haoqingtech.com`, Qiniu storage `pipeline.qiniu.com`, `uc.qbox.me`; `tob-gw.jd.com`;
`service.ebook.hyread.com.tw`; Xiaomi connectivity check `connect.rom.miui.com` in the network stack.

**Action.** Magisk module `hosts` (systemless `/system/etc/hosts`) sends all of the above to 0.0.0.0:
list `magisk-modules/hosts-block.txt`, builder `magisk-modules/make-hosts-block.sh`. Verified after reboot:
blocked names resolve locally, Brave/DuckDuckGo/Tuya/Dreo resolve normally, Wi-Fi is VALIDATED, no uid 1000
outside connections. Not blockable by name: the updater's hard-coded IP (app disabled). Kept: Google's
`connectivitycheck.gstatic.com` (Wi-Fi validation) and time sync.

**Undo:** `su -c "rm -rf /data/adb/modules/hosts"` and reboot.
