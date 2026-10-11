#!/system/bin/sh
M=/data/adb/modules/clean804; rm -rf $M; mkdir -p $M
cat > $M/module.prop <<EOF
id=clean804
name=Clean tablet (804 debloat)
version=1
versionCode=1
author=meebook-jailbreak
description=Systemless removal of Google, OEM extras, small apps and unused overlays. Disable to restore.
EOF
# Haoqing "corelog": logs everything to /data/haoqing/corelog for the Settings > Feedback upload (plain HTTP to
# ai-admin.haoqingtech.com). init.rc forces persist.sys.corelog.enable=1 every boot, so replace the binary with a no-op.
mkdir -p $M/system/bin && printf '#!/system/bin/sh
# corelog disabled by clean804
exit 0
' > $M/system/bin/corelog && chmod 755 $M/system/bin/corelog
mkdir -p $M/system/product/priv-app/GmsCore && touch $M/system/product/priv-app/GmsCore/.replace  # com.google.android.gms
mkdir -p $M/system/product/priv-app/GoogleServicesFramework && touch $M/system/product/priv-app/GoogleServicesFramework/.replace  # com.google.android.gsf
mkdir -p $M/system/product/priv-app/Phonesky && touch $M/system/product/priv-app/Phonesky/.replace  # com.android.vending
mkdir -p $M/system/product/app/SpeechServicesByGoogle && touch $M/system/product/app/SpeechServicesByGoogle/.replace  # com.google.android.tts
mkdir -p $M/system/product/app/GoogleCalendarSyncAdapter && touch $M/system/product/app/GoogleCalendarSyncAdapter/.replace  # com.google.android.syncadapters.calendar
mkdir -p $M/system/product/priv-app/GoogleOneTimeInitializer && touch $M/system/product/priv-app/GoogleOneTimeInitializer/.replace  # com.google.android.onetimeinitializer
mkdir -p $M/system/product/priv-app/OneTimeInitializer && touch $M/system/product/priv-app/OneTimeInitializer/.replace  # com.android.onetimeinitializer
mkdir -p $M/system/product/priv-app/ConfigUpdater && touch $M/system/product/priv-app/ConfigUpdater/.replace  # com.google.android.configupdater
mkdir -p $M/system/product/priv-app/GoogleFeedback && touch $M/system/product/priv-app/GoogleFeedback/.replace  # com.google.android.feedback
mkdir -p $M/system/product/priv-app/GooglePartnerSetup && touch $M/system/product/priv-app/GooglePartnerSetup/.replace  # com.google.android.partnersetup
mkdir -p $M/system/product/app/DeskClock && touch $M/system/product/app/DeskClock/.replace  # com.android.deskclock
mkdir -p $M/system/product/app/Gallery2 && touch $M/system/product/app/Gallery2/.replace  # com.android.gallery3d
mkdir -p $M/system/app && : > $M/system/app/Haoqing-Calculator.apk  # com.android.lemonread.calculator
mkdir -p $M/system/app && : > $M/system/app/Haoqing-FileManager.apk  # com.haoqing.filemanager
mkdir -p $M/system/app/HaoqingPinyinIME && touch $M/system/app/HaoqingPinyinIME/.replace  # com.android.inputmethod.pinyin
mkdir -p $M/system/app/Music && touch $M/system/app/Music/.replace  # com.android.music
mkdir -p $M/system/priv-app/MusicFX && touch $M/system/priv-app/MusicFX/.replace  # com.android.musicfx
mkdir -p $M/system/app/SoundRecorder && touch $M/system/app/SoundRecorder/.replace  # com.android.soundrecorder
mkdir -p $M/system/app/Haoqing-Launcher && touch $M/system/app/Haoqing-Launcher/.replace  # com.haoqing.app.hqreader
mkdir -p $M/system/app && : > $M/system/app/Haoqing-WiFiTransfer.apk  # com.haoqing.wifitransfer
mkdir -p $M/system/app/Haoqing-AgingTest && touch $M/system/app/Haoqing-AgingTest/.replace  # com.haoqing.agingtest
mkdir -p $M/system/app && : > $M/system/app/Haoqing-ToolApp.apk  # com.haoqing.openfile
mkdir -p $M/system/app && : > $M/system/app/Haoqing-Netdisk.apk  # com.haoqing.app.fileCloud
mkdir -p $M/system/app && : > $M/system/app/Haoqing-AppStore.apk  # com.haoqing.appstore
mkdir -p $M/system/app && : > $M/system/app/Haoqing-FloatingButton.apk  # com.haoqing.floatingbutton
mkdir -p $M/system/app && : > $M/system/app/Haoqing-AccountManagement.apk  # com.haoqing.accountmanagement
mkdir -p $M/system/app && : > $M/system/app/Haoqing-DeviceTest.apk  # com.DeviceTest
mkdir -p $M/system/priv-app/StressTest && touch $M/system/priv-app/StressTest/.replace  # com.cghs.stresstest
mkdir -p $M/system/vendor/app/Rk4kVideoPlayer && touch $M/system/vendor/app/Rk4kVideoPlayer/.replace  # android.rk.RockVideoPlayer
mkdir -p $M/system/app/Browser && touch $M/system/app/Browser/.replace  # com.android.browser
mkdir -p $M/system/app/EasterEgg && touch $M/system/app/EasterEgg/.replace  # com.android.egg
mkdir -p $M/system/app/BasicDreams && touch $M/system/app/BasicDreams/.replace  # com.android.dreams.basic
mkdir -p $M/system/product/app/PhotoTable && touch $M/system/product/app/PhotoTable/.replace  # com.android.dreams.phototable
mkdir -p $M/system/app/PrintSpooler && touch $M/system/app/PrintSpooler/.replace  # com.android.printspooler
mkdir -p $M/system/priv-app/BuiltInPrintService && touch $M/system/priv-app/BuiltInPrintService/.replace  # com.android.bips
mkdir -p $M/system/app/PrintRecommendationService && touch $M/system/app/PrintRecommendationService/.replace  # com.android.printservice.recommendation
mkdir -p $M/system/app/LiveWallpapersPicker && touch $M/system/app/LiveWallpapersPicker/.replace  # com.android.wallpaper.livepicker
mkdir -p $M/system/product/app/WallpaperPicker && touch $M/system/product/app/WallpaperPicker/.replace  # com.android.wallpaperpicker
mkdir -p $M/system/system_ext/priv-app/WallpaperCropper && touch $M/system/system_ext/priv-app/WallpaperCropper/.replace  # com.android.wallpapercropper
mkdir -p $M/system/app/Traceur && touch $M/system/app/Traceur/.replace  # com.android.traceur
mkdir -p $M/system/app/BookmarkProvider && touch $M/system/app/BookmarkProvider/.replace  # com.android.bookmarkprovider
mkdir -p $M/system/app/HTMLViewer && touch $M/system/app/HTMLViewer/.replace  # com.android.htmlviewer
mkdir -p $M/system/system_ext/priv-app/CellBroadcastAppPlatform && touch $M/system/system_ext/priv-app/CellBroadcastAppPlatform/.replace  # com.android.cellbroadcastreceiver
mkdir -p $M/system/priv-app/CellBroadcastServiceModulePlatform && touch $M/system/priv-app/CellBroadcastServiceModulePlatform/.replace  # com.android.cellbroadcastservice
mkdir -p $M/system/system_ext/app/WAPPushManager && touch $M/system/system_ext/app/WAPPushManager/.replace  # com.android.smspush
mkdir -p $M/system/priv-app/DynamicSystemInstallationService && touch $M/system/priv-app/DynamicSystemInstallationService/.replace  # com.android.dynsystem
mkdir -p $M/system/product/overlay/DisplayCutoutEmulationCorner && touch $M/system/product/overlay/DisplayCutoutEmulationCorner/.replace  # com.android.internal.display.cutout.emulation.corner
mkdir -p $M/system/product/overlay/DisplayCutoutEmulationDouble && touch $M/system/product/overlay/DisplayCutoutEmulationDouble/.replace  # com.android.internal.display.cutout.emulation.double
mkdir -p $M/system/product/overlay/AccentColorAmethyst && touch $M/system/product/overlay/AccentColorAmethyst/.replace  # com.android.theme.color.amethyst
mkdir -p $M/system/product/overlay/IconShapePebble && touch $M/system/product/overlay/IconShapePebble/.replace  # com.android.theme.icon.pebble
mkdir -p $M/system/product/overlay/NavigationBarModeGesturalWideBack && touch $M/system/product/overlay/NavigationBarModeGesturalWideBack/.replace  # com.android.internal.systemui.navbar.gestural_wide_back
mkdir -p $M/system/product/overlay/AccentColorSand && touch $M/system/product/overlay/AccentColorSand/.replace  # com.android.theme.color.sand
mkdir -p $M/system/product/overlay/IconShapeVessel && touch $M/system/product/overlay/IconShapeVessel/.replace  # com.android.theme.icon.vessel
mkdir -p $M/system/product/overlay/AccentColorCinnamon && touch $M/system/product/overlay/AccentColorCinnamon/.replace  # com.android.theme.color.cinnamon
mkdir -p $M/system/product/overlay/IconShapeTaperedRect && touch $M/system/product/overlay/IconShapeTaperedRect/.replace  # com.android.theme.icon.taperedrect
mkdir -p $M/system/product/overlay/IconPackRoundedAndroid && touch $M/system/product/overlay/IconPackRoundedAndroid/.replace  # com.android.theme.icon_pack.rounded.android
mkdir -p $M/system/product/overlay/AccentColorTangerine && touch $M/system/product/overlay/AccentColorTangerine/.replace  # com.android.theme.color.tangerine
mkdir -p $M/system/product/overlay/AccentColorAquamarine && touch $M/system/product/overlay/AccentColorAquamarine/.replace  # com.android.theme.color.aquamarine
mkdir -p $M/system/product/overlay/DisplayCutoutEmulationHole && touch $M/system/product/overlay/DisplayCutoutEmulationHole/.replace  # com.android.internal.display.cutout.emulation.hole
mkdir -p $M/system/product/overlay/DisplayCutoutEmulationTall && touch $M/system/product/overlay/DisplayCutoutEmulationTall/.replace  # com.android.internal.display.cutout.emulation.tall
mkdir -p $M/system/product/overlay/AccentColorBlack && touch $M/system/product/overlay/AccentColorBlack/.replace  # com.android.theme.color.black
mkdir -p $M/system/product/overlay/AccentColorGreen && touch $M/system/product/overlay/AccentColorGreen/.replace  # com.android.theme.color.green
mkdir -p $M/system/product/overlay/AccentColorOcean && touch $M/system/product/overlay/AccentColorOcean/.replace  # com.android.theme.color.ocean
mkdir -p $M/system/product/overlay/AccentColorSpace && touch $M/system/product/overlay/AccentColorSpace/.replace  # com.android.theme.color.space
mkdir -p $M/system/product/overlay/NavigationBarModeGesturalExtraWideBack && touch $M/system/product/overlay/NavigationBarModeGesturalExtraWideBack/.replace  # com.android.internal.systemui.navbar.gestural_extra_wide_back
mkdir -p $M/system/product/overlay/IconShapeTeardrop && touch $M/system/product/overlay/IconShapeTeardrop/.replace  # com.android.theme.icon.teardrop
mkdir -p $M/system/product/overlay/IconPackSamAndroid && touch $M/system/product/overlay/IconPackSamAndroid/.replace  # com.android.theme.icon_pack.sam.android
mkdir -p $M/system/product/overlay/FontNotoSerifSource && touch $M/system/product/overlay/FontNotoSerifSource/.replace  # com.android.theme.font.notoserifsource
mkdir -p $M/system/product/overlay/IconPackFilledAndroid && touch $M/system/product/overlay/IconPackFilledAndroid/.replace  # com.android.theme.icon_pack.filled.android
mkdir -p $M/system/product/overlay/DisplayCutoutEmulationWaterfall && touch $M/system/product/overlay/DisplayCutoutEmulationWaterfall/.replace  # com.android.internal.display.cutout.emulation.waterfall
mkdir -p $M/system/product/overlay/IconPackKaiAndroid && touch $M/system/product/overlay/IconPackKaiAndroid/.replace  # com.android.theme.icon_pack.kai.android
mkdir -p $M/system/product/overlay/IconShapeSquircle && touch $M/system/product/overlay/IconShapeSquircle/.replace  # com.android.theme.icon.squircle
mkdir -p $M/system/product/overlay/IconPackVictorAndroid && touch $M/system/product/overlay/IconPackVictorAndroid/.replace  # com.android.theme.icon_pack.victor.android
mkdir -p $M/system/product/overlay/AccentColorPalette && touch $M/system/product/overlay/AccentColorPalette/.replace  # com.android.theme.color.palette
mkdir -p $M/system/product/overlay/AccentColorCarbon && touch $M/system/product/overlay/AccentColorCarbon/.replace  # com.android.theme.color.carbon
mkdir -p $M/system/product/overlay/AccentColorOrchid && touch $M/system/product/overlay/AccentColorOrchid/.replace  # com.android.theme.color.orchid
mkdir -p $M/system/product/overlay/AccentColorPurple && touch $M/system/product/overlay/AccentColorPurple/.replace  # com.android.theme.color.purple
mkdir -p $M/system/product/overlay/IconShapeRoundedRect && touch $M/system/product/overlay/IconShapeRoundedRect/.replace  # com.android.theme.icon.roundedrect
mkdir -p $M/system/product/overlay/NavigationBarModeGesturalNarrowBack && touch $M/system/product/overlay/NavigationBarModeGesturalNarrowBack/.replace  # com.android.internal.systemui.navbar.gestural_narrow_back
mkdir -p $M/system/product/overlay/IconPackCircularAndroid && touch $M/system/product/overlay/IconPackCircularAndroid/.replace  # com.android.theme.icon_pack.circular.android
mkdir -p $M/system/product/overlay/IconPackRoundedLauncher && touch $M/system/product/overlay/IconPackRoundedLauncher/.replace  # com.android.theme.icon_pack.rounded.launcher
mkdir -p $M/system/product/overlay/IconPackFilledLauncher && touch $M/system/product/overlay/IconPackFilledLauncher/.replace  # com.android.theme.icon_pack.filled.launcher
mkdir -p $M/system/product/overlay/IconPackCircularLauncher && touch $M/system/product/overlay/IconPackCircularLauncher/.replace  # com.android.theme.icon_pack.circular.launcher
mkdir -p $M/system/product/overlay/IconPackVictorSettings && touch $M/system/product/overlay/IconPackVictorSettings/.replace  # com.android.theme.icon_pack.victor.settings
mkdir -p $M/system/product/overlay/IconPackKaiSettings && touch $M/system/product/overlay/IconPackKaiSettings/.replace  # com.android.theme.icon_pack.kai.settings
mkdir -p $M/system/product/overlay/IconPackSamSettings && touch $M/system/product/overlay/IconPackSamSettings/.replace  # com.android.theme.icon_pack.sam.settings
mkdir -p $M/system/product/overlay/IconPackFilledSettings && touch $M/system/product/overlay/IconPackFilledSettings/.replace  # com.android.theme.icon_pack.filled.settings
mkdir -p $M/system/product/overlay/IconPackCircularSettings && touch $M/system/product/overlay/IconPackCircularSettings/.replace  # com.android.theme.icon_pack.circular.settings
mkdir -p $M/system/product/overlay/IconPackRoundedSettings && touch $M/system/product/overlay/IconPackRoundedSettings/.replace  # com.android.theme.icon_pack.rounded.settings
mkdir -p $M/system/product/overlay/IconPackRoundedSystemUI && touch $M/system/product/overlay/IconPackRoundedSystemUI/.replace  # com.android.theme.icon_pack.rounded.systemui
mkdir -p $M/system/product/overlay/IconPackVictorSystemUI && touch $M/system/product/overlay/IconPackVictorSystemUI/.replace  # com.android.theme.icon_pack.victor.systemui
mkdir -p $M/system/product/overlay/IconPackKaiSystemUI && touch $M/system/product/overlay/IconPackKaiSystemUI/.replace  # com.android.theme.icon_pack.kai.systemui
mkdir -p $M/system/product/overlay/IconPackSamSystemUI && touch $M/system/product/overlay/IconPackSamSystemUI/.replace  # com.android.theme.icon_pack.sam.systemui
mkdir -p $M/system/product/overlay/IconPackFilledSystemUI && touch $M/system/product/overlay/IconPackFilledSystemUI/.replace  # com.android.theme.icon_pack.filled.systemui
mkdir -p $M/system/product/overlay/IconPackCircularSystemUI && touch $M/system/product/overlay/IconPackCircularSystemUI/.replace  # com.android.theme.icon_pack.circular.systemui
chcon -R u:object_r:system_file:s0 $M/system; find $M -type d | wc -l; find $M -name .replace | wc -l; find $M -type f -size 0 ! -name .replace | wc -l
