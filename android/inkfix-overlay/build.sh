#!/bin/sh
# Build InkFixOverlay.apk (static framework RRO for the inkfix Magisk module). Run from Git Bash:
#   sh android/inkfix-overlay/build.sh
# Install: copy to /data/adb/modules/inkfix/system/product/overlay/InkFix/ and reboot (static overlays load at boot).
set -e
cd "$(dirname "$0")"
SDK=/c/Users/<you>/AppData/Local/Android/Sdk
BT=$SDK/build-tools/35.0.0
AJ=$SDK/platforms/android-35/android.jar
export JAVA_HOME=$(cd ../../tools/jdk17 && pwd)
export PATH="$JAVA_HOME/bin:$PATH"
rm -rf build InkFixOverlay.apk && mkdir -p build
"$BT/aapt2.exe" compile --dir res -o build/res.zip
"$BT/aapt2.exe" link -o build/unsigned.apk --manifest AndroidManifest.xml -I "$AJ" build/res.zip
"$BT/zipalign.exe" -f 4 build/unsigned.apk build/aligned.apk
"$BT/apksigner.bat" sign --ks ../../tools/inkfix.jks --ks-pass env:KS_PASS --out InkFixOverlay.apk build/aligned.apk
"$BT/apksigner.bat" verify InkFixOverlay.apk && ls -l InkFixOverlay.apk
