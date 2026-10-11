#!/system/bin/sh
# Wireless ADB on port 5555 (adb connect <tablet-ip>:5555). Works only while the tablet is awake:
# Haoqing turns Wi-Fi off in sleep. Safe only with post-fs-data.d/adb-secure.sh (key authorisation). Delete to disable.
sleep 20
[ "$(getprop ro.adb.secure)" = 1 ] || exit 0
setprop service.adb.tcp.port 5555
stop adbd
start adbd
