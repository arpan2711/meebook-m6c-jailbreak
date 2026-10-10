#!/bin/sh
# Reverses debloat batch 1 (2026-10-10). Run from Git Bash.
ADB="/c/Users/<you>/AppData/Local/Android/Sdk/platform-tools/adb.exe"; S=<SERIAL>
for p in com.haoqing.app.fileCloud com.haoqing.appstore com.haoqing.floatingbutton com.haoqing.accountmanagement com.DeviceTest com.cghs.stresstest android.rk.RockVideoPlayer com.haoqing.wallpaper com.android.browser com.android.egg com.android.dreams.basic com.android.dreams.phototable com.android.printspooler com.android.bips com.android.wallpaper.livepicker; do
  "$ADB" -s $S shell cmd package install-existing --user 0 $p
done
for p in com.android.cellbroadcastreceiver com.android.smspush; do
  "$ADB" -s $S shell pm enable --user 0 $p
done
