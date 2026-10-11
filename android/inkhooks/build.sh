#!/bin/sh
# Build InkHooks.apk (Xposed module for Vector/LSPosed) with plain SDK tools. Run from Git Bash:
#   sh android/inkhooks/build.sh
# The Xposed API stubs in stubs/ are compile-only: the real classes come from Vector, so they are not dexed.
set -e
cd "$(dirname "$0")"
SDK=/c/Users/<you>/AppData/Local/Android/Sdk
BT=$SDK/build-tools/35.0.0
AJ=$SDK/platforms/android-35/android.jar
export JAVA_HOME=$(cd ../../tools/jdk17 && pwd)
export PATH="$JAVA_HOME/bin:$PATH"
rm -rf build InkHooks.apk && mkdir -p build/stubs build/classes build/dex
javac -encoding UTF-8 -source 8 -target 8 -bootclasspath "$AJ" -d build/stubs $(find stubs -name '*.java') 2> build/javac.log || { grep -v "warning: \[options\]" build/javac.log; exit 1; }
javac -encoding UTF-8 -source 8 -target 8 -bootclasspath "$AJ" -classpath build/stubs -d build/classes $(find src -name '*.java') 2> build/javac.log || { grep -v "warning: \[options\]" build/javac.log; exit 1; }
"$BT/d8.bat" --lib "$AJ" --classpath build/stubs --min-api 28 --output build/dex $(find build/classes -name '*.class')
"$BT/aapt2.exe" compile --dir res -o build/res.zip
"$BT/aapt2.exe" link -o build/unsigned.apk --manifest AndroidManifest.xml -I "$AJ" -A assets build/res.zip
python -I -c "import zipfile; zipfile.ZipFile('build/unsigned.apk','a',zipfile.ZIP_DEFLATED).write('build/dex/classes.dex','classes.dex')"
"$BT/zipalign.exe" -f 4 build/unsigned.apk build/aligned.apk
"$BT/apksigner.bat" sign --ks ../../tools/inkfix.jks --ks-pass env:KS_PASS --out InkHooks.apk build/aligned.apk
"$BT/apksigner.bat" verify InkHooks.apk && ls -l InkHooks.apk
