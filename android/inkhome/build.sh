#!/bin/sh
# Build InkHome.apk with plain SDK tools (no Gradle). Run from Git Bash: sh android/inkhome/build.sh
set -e
cd "$(dirname "$0")"
SDK=/c/Users/<you>/AppData/Local/Android/Sdk
BT=$SDK/build-tools/35.0.0
AJ=$SDK/platforms/android-35/android.jar
export JAVA_HOME=$(cd ../../tools/jdk17 && pwd)
export PATH="$JAVA_HOME/bin:$PATH"
rm -rf build InkHome.apk && mkdir -p build/classes build/dex
javac -encoding UTF-8 -source 8 -target 8 -bootclasspath "$AJ" -classpath "$BT/core-lambda-stubs.jar" -d build/classes $(find src -name '*.java') 2> build/javac.log || { grep -v "warning: \[options\]" build/javac.log; exit 1; }
"$BT/d8.bat" --lib "$AJ" --classpath "$BT/core-lambda-stubs.jar" --min-api 28 --output build/dex $(find build/classes -name '*.class')
"$BT/aapt2.exe" link -o build/unsigned.apk --manifest AndroidManifest.xml -I "$AJ" --min-sdk-version 28 --target-sdk-version 30
python -I -c "import zipfile; zipfile.ZipFile('build/unsigned.apk','a',zipfile.ZIP_DEFLATED).write('build/dex/classes.dex','classes.dex')"
"$BT/zipalign.exe" -f 4 build/unsigned.apk build/aligned.apk
"$BT/apksigner.bat" sign --ks ../../tools/inkfix.jks --ks-pass env:KS_PASS --out InkHome.apk build/aligned.apk
"$BT/apksigner.bat" verify InkHome.apk && ls -l InkHome.apk
