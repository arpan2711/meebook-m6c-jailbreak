#!/system/bin/sh
# Haoqing ships ro.adb.secure=0 (ADB without key authorisation). Turn authorisation on before adbd starts,
# so only PCs listed in /data/misc/adb/adb_keys can connect (USB or Wi-Fi). Delete to go back to the stock behaviour.
resetprop ro.adb.secure 1
