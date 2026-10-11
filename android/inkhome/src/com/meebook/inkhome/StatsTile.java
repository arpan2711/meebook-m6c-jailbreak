package com.meebook.inkhome;

import android.app.ActivityManager;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.BatteryManager;
import android.os.Environment;
import android.os.StatFs;
import android.os.SystemClock;
import android.util.TypedValue;
import android.view.Gravity;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.util.Locale;

/**
 * Home-screen tile with live device stats: battery, storage, CPU, memory, Wi-Fi, uptime.
 * CPU load, clock, temperature and Wi-Fi name need root (apps can't read /proc/stat or the SSID),
 * so they come from one short su call; call {@link #refresh} off the UI thread.
 */
final class StatsTile extends LinearLayout {

    private final TextView[] values = new TextView[6];
    private final TextView[] notes = new TextView[6];
    private static final String[] LABELS = {"Battery", "Storage", "CPU", "Memory", "Wi-Fi", "Uptime"};

    StatsTile(Context c) {
        super(c);
        setOrientation(VERTICAL);
        setPadding(dp(10), dp(8), dp(10), dp(8));
        GradientDrawable box = new GradientDrawable();
        box.setColor(Color.WHITE);
        box.setStroke(dp(2), Color.BLACK);
        box.setCornerRadius(dp(10));
        setBackground(box);
        for (int r = 0; r < 2; r++) {
            LinearLayout row = new LinearLayout(c);
            for (int col = 0; col < 3; col++) {
                int i = r * 3 + col;
                LinearLayout cell = new LinearLayout(c);
                cell.setOrientation(VERTICAL);
                cell.setGravity(Gravity.CENTER_HORIZONTAL);
                cell.setPadding(dp(2), dp(4), dp(2), dp(4));
                cell.addView(text(LABELS[i], 12, false, Color.DKGRAY));
                values[i] = text("…", 18, true, Color.BLACK);
                cell.addView(values[i]);
                notes[i] = text("", 12, false, Color.DKGRAY);
                cell.addView(notes[i]);
                row.addView(cell, new LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f));
            }
            addView(row);
        }
    }

    /** One snapshot of the stats. Blocking (root call samples the CPU for half a second). */
    static String[][] sample(Context c) {
        String[][] s = new String[6][2];
        // Battery (sticky broadcast, no permission needed)
        Intent b = c.registerReceiver(null, new IntentFilter(Intent.ACTION_BATTERY_CHANGED));
        if (b != null) {
            int level = b.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) * 100 / Math.max(1, b.getIntExtra(BatteryManager.EXTRA_SCALE, 100));
            int status = b.getIntExtra(BatteryManager.EXTRA_STATUS, 0);
            int plugged = b.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0);
            float temp = b.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, 0) / 10f;
            String state = status == BatteryManager.BATTERY_STATUS_FULL ? "full"
                    : plugged != 0 ? "charging" : "on battery";
            s[0] = new String[] {level + "%", String.format(Locale.ROOT, "%s · %.0f°C", state, temp)};
        }
        // Storage
        StatFs fs = new StatFs(Environment.getExternalStorageDirectory().getPath());
        s[1] = new String[] {gb(fs.getAvailableBytes()) + " free", "of " + gb(fs.getTotalBytes())};
        // Memory
        ActivityManager.MemoryInfo mi = new ActivityManager.MemoryInfo();
        ((ActivityManager) c.getSystemService(Context.ACTIVITY_SERVICE)).getMemoryInfo(mi);
        s[3] = new String[] {gb(mi.availMem) + " free", "of " + gb(mi.totalMem)};
        // Uptime
        long min = SystemClock.elapsedRealtime() / 60000;
        s[5] = new String[] {min >= 1440 ? (min / 1440) + " d " + (min % 1440 / 60) + " h" : (min / 60) + " h " + (min % 60) + " m",
                "since restart"};
        // CPU load / clock / temperature and Wi-Fi name (root)
        s[2] = new String[] {"?", "needs root"};
        s[4] = new String[] {"?", ""};
        try {
            Process p = Runtime.getRuntime().exec(new String[] {"su", "-c",
                    "head -1 /proc/stat; sleep 0.5; head -1 /proc/stat; "
                            + "cat /sys/devices/system/cpu/cpu0/cpufreq/scaling_cur_freq; "
                            + "cat /sys/class/thermal/thermal_zone0/temp; cmd wifi status | head -3"});
            BufferedReader r = new BufferedReader(new InputStreamReader(p.getInputStream()));
            long[] a = cpu(r.readLine()), z = cpu(r.readLine());
            String freq = r.readLine(), temp = r.readLine();
            String wifi = "off", ssid = "";
            for (String line; (line = r.readLine()) != null; ) {
                if (line.contains("is connected to")) {
                    wifi = "on";
                    int q = line.indexOf('"');
                    ssid = q >= 0 ? line.substring(q + 1, line.lastIndexOf('"')) : "";
                } else if (line.contains("Wifi is enabled") && wifi.equals("off")) {
                    wifi = "on";
                    ssid = "not connected";
                }
            }
            p.waitFor();
            if (a != null && z != null && z[0] > a[0]) {
                long busy = 100 - (z[1] - a[1]) * 100 / (z[0] - a[0]);
                String ghz = freq == null ? "" : String.format(Locale.ROOT, "%.1f GHz", Long.parseLong(freq.trim()) / 1e6);
                String deg = temp == null ? "" : String.format(Locale.ROOT, " · %d°C", Long.parseLong(temp.trim()) / 1000);
                s[2] = new String[] {busy + "%", ghz + deg};
            }
            s[4] = new String[] {wifi, ssid};
        } catch (Exception ignored) {
            // no root or unexpected output: keep the placeholders
        }
        return s;
    }

    /** Shows a snapshot from {@link #sample}. UI thread. */
    void show(String[][] s) {
        for (int i = 0; i < 6; i++) {
            if (s[i] == null || s[i][0] == null) continue;
            values[i].setText(s[i][0]);
            notes[i].setText(s[i][1]);
        }
    }

    /** {total, idle} jiffies from the "cpu" line of /proc/stat. */
    private static long[] cpu(String line) {
        if (line == null || !line.startsWith("cpu")) return null;
        String[] f = line.trim().split("\\s+");
        long total = 0;
        for (int i = 1; i < f.length; i++) total += Long.parseLong(f[i]);
        long idle = Long.parseLong(f[4]) + (f.length > 5 ? Long.parseLong(f[5]) : 0);  // idle + iowait
        return new long[] {total, idle};
    }

    private static String gb(long bytes) {
        return String.format(Locale.ROOT, bytes >= 10e9 ? "%.0f GB" : "%.1f GB", bytes / 1e9);
    }

    private TextView text(String s, int sp, boolean bold, int color) {
        TextView t = new TextView(getContext());
        t.setText(s);
        t.setTextColor(color);
        t.setTextSize(TypedValue.COMPLEX_UNIT_SP, sp);
        t.setGravity(Gravity.CENTER_HORIZONTAL);
        t.setSingleLine(true);
        if (bold) t.setTypeface(Typeface.DEFAULT_BOLD);
        return t;
    }

    private int dp(int v) {
        return Math.round(v * getResources().getDisplayMetrics().density);
    }
}
