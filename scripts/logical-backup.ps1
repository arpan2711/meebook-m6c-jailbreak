# Non-root logical backup of the Meebook M6C over ADB.
param(
    [string]$Serial = "<SERIAL>",
    [string]$Out = "C:\Users\<you>\Desktop\meebook-jailbreak\backup\logical"
)
$adb = "C:\Users\<you>\AppData\Local\Android\Sdk\platform-tools\adb.exe"
$log = Join-Path $Out "pull.log"
New-Item -ItemType Directory -Force "$Out\fs", "$Out\apks", "$Out\sdcard" | Out-Null

function Pull($remote, $local) {
    "=== $remote -> $local" | Add-Content $log
    & $adb -s $Serial pull -a $remote $local 2>&1 | Select-Object -Last 3 | Add-Content $log
}

# Readable parts of the mounted system images (unreadable files are skipped and logged).
foreach ($p in "/system", "/vendor", "/product", "/system_ext", "/odm") {
    $dst = Join-Path "$Out\fs" ($p.TrimStart("/"))
    New-Item -ItemType Directory -Force $dst | Out-Null
    Pull "$p/." $dst
}

# Every installed APK (including splits), one folder per package.
$pkgs = & $adb -s $Serial shell "pm list packages -u" | ForEach-Object { $_ -replace "^package:", "" } | Where-Object { $_ }
foreach ($pkg in $pkgs) {
    $paths = & $adb -s $Serial shell "pm path $pkg" | ForEach-Object { $_ -replace "^package:", "" } | Where-Object { $_ }
    $dst = Join-Path "$Out\apks" $pkg
    New-Item -ItemType Directory -Force $dst | Out-Null
    foreach ($path in $paths) { & $adb -s $Serial pull -a $path $dst 2>&1 | Out-Null }
}

# Internal shared storage.
Pull "/sdcard/." "$Out\sdcard"

"DONE" | Add-Content $log
