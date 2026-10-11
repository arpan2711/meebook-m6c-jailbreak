package com.meebook.inkhooks;

import java.lang.reflect.Method;

import de.robv.android.xposed.IXposedHookLoadPackage;
import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;
import de.robv.android.xposed.callbacks.XC_LoadPackage;

/**
 * Xposed module (loaded by Vector/LSPosed into system_server, scope "android").
 *
 * Haoqing's PowerManagerService.shouldWakeUpWhenPluggedOrUnpluggedLocked() starts with
 *     if ("1".equals(SystemProperties.get("sys.hq.dreamrun", "0"))) return true;
 * before it looks at config_unplugTurnsOnScreen, and sys.hq.dreamrun is 1 whenever the sleep screen is up.
 * So plugging/unplugging the charger always woke the tablet. This hook makes the config decide again:
 * the inkfix overlay sets config_unplugTurnsOnScreen=false, so the charger never wakes the screen.
 */
public class Hooks implements IXposedHookLoadPackage {

    private static final String PMS = "com.android.server.power.PowerManagerService";

    @Override
    public void handleLoadPackage(XC_LoadPackage.LoadPackageParam lpparam) throws Throwable {
        if (!"android".equals(lpparam.packageName)) return;
        XposedHelpers.findAndHookMethod(PMS, lpparam.classLoader, "shouldWakeUpWhenPluggedOrUnpluggedLocked",
                boolean.class, int.class, boolean.class, new XC_MethodHook() {
                    @Override
                    protected void beforeHookedMethod(MethodHookParam param) {
                        if (!XposedHelpers.getBooleanField(param.thisObject, "mWakeUpWhenPluggedOrUnpluggedConfig")) {
                            param.setResult(false);
                        }
                    }
                });
        // The small hooked method may be inlined into its precompiled caller; run the caller interpreted
        // so the hook is always reached. deoptimizeMethod is an LSPosed/Vector extension, so call it reflectively.
        try {
            Class<?> pms = Class.forName(PMS, false, lpparam.classLoader);
            Method caller = pms.getDeclaredMethod("updateIsPoweredLocked", int.class);
            XposedBridge.class.getMethod("deoptimizeMethod", java.lang.reflect.Member.class).invoke(null, caller);
        } catch (Throwable t) {
            XposedBridge.log("InkHooks: could not deoptimize updateIsPoweredLocked: " + t);
        }
        XposedBridge.log("InkHooks: charger plug/unplug wake now follows config_unplugTurnsOnScreen");
    }
}
