package de.robv.android.xposed;

/** Compile-time stub of the Xposed API (the real classes come from Vector at runtime; not packaged). */
public final class XposedHelpers {
    public static XC_MethodHook.Unhook findAndHookMethod(String className, ClassLoader classLoader,
            String methodName, Object... parameterTypesAndCallback) {
        return null;
    }

    public static boolean getBooleanField(Object obj, String fieldName) {
        return false;
    }
}
