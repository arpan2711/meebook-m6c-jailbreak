# Force-install a model from a specific INF onto a device instance — the scripted
# equivalent of Device Manager > Update driver > Have Disk > pick model.
# Must run elevated.
param(
    [Parameter(Mandatory)] [string]$InstanceId,
    [Parameter(Mandatory)] [string]$Inf,
    [string]$Model = "Android Bootloader Interface",
    [string]$Log = "$PSScriptRoot\force-driver.log"
)
$ErrorActionPreference = "Stop"
Start-Transcript -Path $Log -Append | Out-Null
try {
Add-Type -TypeDefinition @"
using System;
using System.Runtime.InteropServices;
public static class ForceDrv {
    [StructLayout(LayoutKind.Sequential)]
    public struct SP_DEVINFO_DATA { public uint cbSize; public Guid ClassGuid; public uint DevInst; public IntPtr Reserved; }
    [StructLayout(LayoutKind.Sequential, CharSet = CharSet.Unicode)]
    public struct SP_DEVINSTALL_PARAMS {
        public uint cbSize; public uint Flags; public uint FlagsEx; public IntPtr hwndParent;
        public IntPtr InstallMsgHandler; public IntPtr InstallMsgHandlerContext; public IntPtr FileQueue;
        public UIntPtr ClassInstallReserved; public uint Reserved;
        [MarshalAs(UnmanagedType.ByValTStr, SizeConst = 260)] public string DriverPath;
    }
    [StructLayout(LayoutKind.Sequential, CharSet = CharSet.Unicode)]
    public struct SP_DRVINFO_DATA {
        public uint cbSize; public uint DriverType; public UIntPtr Reserved;
        [MarshalAs(UnmanagedType.ByValTStr, SizeConst = 256)] public string Description;
        [MarshalAs(UnmanagedType.ByValTStr, SizeConst = 256)] public string MfgName;
        [MarshalAs(UnmanagedType.ByValTStr, SizeConst = 256)] public string ProviderName;
        public long DriverDate; public ulong DriverVersion;
    }
    [DllImport("setupapi.dll", SetLastError = true)] public static extern IntPtr SetupDiCreateDeviceInfoList(IntPtr g, IntPtr h);
    [DllImport("setupapi.dll", SetLastError = true, CharSet = CharSet.Unicode)] public static extern bool SetupDiOpenDeviceInfo(IntPtr set, string id, IntPtr h, uint flags, ref SP_DEVINFO_DATA d);
    [DllImport("setupapi.dll", SetLastError = true, CharSet = CharSet.Unicode)] public static extern bool SetupDiGetDeviceInstallParams(IntPtr set, ref SP_DEVINFO_DATA d, ref SP_DEVINSTALL_PARAMS p);
    [DllImport("setupapi.dll", SetLastError = true, CharSet = CharSet.Unicode)] public static extern bool SetupDiSetDeviceInstallParams(IntPtr set, ref SP_DEVINFO_DATA d, ref SP_DEVINSTALL_PARAMS p);
    [DllImport("setupapi.dll", SetLastError = true)] public static extern bool SetupDiBuildDriverInfoList(IntPtr set, ref SP_DEVINFO_DATA d, uint type);
    [DllImport("setupapi.dll", SetLastError = true, CharSet = CharSet.Unicode)] public static extern bool SetupDiEnumDriverInfo(IntPtr set, ref SP_DEVINFO_DATA d, uint type, uint idx, ref SP_DRVINFO_DATA drv);
    [DllImport("setupapi.dll", SetLastError = true, CharSet = CharSet.Unicode)] public static extern bool SetupDiSetSelectedDriver(IntPtr set, ref SP_DEVINFO_DATA d, ref SP_DRVINFO_DATA drv);
    [DllImport("newdev.dll", SetLastError = true, CharSet = CharSet.Unicode)] public static extern bool DiInstallDevice(IntPtr hwnd, IntPtr set, ref SP_DEVINFO_DATA d, ref SP_DRVINFO_DATA drv, uint flags, out bool reboot);

    public static string Install(string id, string inf, string model) {
        IntPtr set = SetupDiCreateDeviceInfoList(IntPtr.Zero, IntPtr.Zero);
        var dev = new SP_DEVINFO_DATA(); dev.cbSize = (uint)Marshal.SizeOf(dev);
        if (!SetupDiOpenDeviceInfo(set, id, IntPtr.Zero, 0, ref dev)) return "open failed " + Marshal.GetLastWin32Error();
        var p = new SP_DEVINSTALL_PARAMS(); p.cbSize = (uint)Marshal.SizeOf(p);
        if (!SetupDiGetDeviceInstallParams(set, ref dev, ref p)) return "getparams failed " + Marshal.GetLastWin32Error();
        p.Flags |= 0x00010000;      // DI_ENUMSINGLEINF
        p.FlagsEx |= 0x00000800;    // DI_FLAGSEX_ALLOWEXCLUDEDDRVS
        p.DriverPath = inf;
        if (!SetupDiSetDeviceInstallParams(set, ref dev, ref p)) return "setparams failed " + Marshal.GetLastWin32Error();
        if (!SetupDiBuildDriverInfoList(set, ref dev, 1)) return "build list failed " + Marshal.GetLastWin32Error();  // SPDIT_CLASSDRIVER
        var drv = new SP_DRVINFO_DATA(); drv.cbSize = (uint)Marshal.SizeOf(drv);
        string seen = "";
        for (uint i = 0; SetupDiEnumDriverInfo(set, ref dev, 1, i, ref drv); i++) {
            seen += "[" + drv.Description + "] ";
            if (drv.Description == model) {
                if (!SetupDiSetSelectedDriver(set, ref dev, ref drv)) return "select failed " + Marshal.GetLastWin32Error();
                bool reboot;
                if (!DiInstallDevice(IntPtr.Zero, set, ref dev, ref drv, 0, out reboot)) return "install failed " + Marshal.GetLastWin32Error();
                return "installed '" + model + "' reboot=" + reboot;
            }
        }
        return "model not found; saw: " + seen;
    }
}
"@
    [ForceDrv]::Install($InstanceId, $Inf, $Model)
} catch { "error: $_" } finally { Stop-Transcript | Out-Null }
