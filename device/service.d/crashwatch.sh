#!/system/bin/sh
# Records errors + KOReader messages to a small rotating log for diagnosing crashes. Delete to disable.
sleep 30
mkdir -p /data/local/crashwatch
logcat -v time -r 1024 -n 3 -f /data/local/crashwatch/log.txt KOReader:I NativeThread:V NativeGlue:V SurfaceView:E SurfaceComposerClient:E AndroidRuntime:E *:F &
