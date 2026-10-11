package com.meebook.inkhome;

import android.app.Activity;
import android.app.AlertDialog;
import android.bluetooth.BluetoothAdapter;
import android.content.ComponentName;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.Typeface;
import android.net.Uri;
import android.os.BatteryManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import android.os.StatFs;
import android.provider.Settings;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.SeekBar;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

import java.io.BufferedReader;
import java.io.File;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Meebook Settings: the handful of settings this e-reader actually needs, on plain white.
 * Haoqing-specific values (front light, sleep timeout) are written through root because
 * Android does not let ordinary apps write Haoqing's custom system settings.
 */
public class SettingsActivity extends Activity {

    private static final int PICK_PHOTO = 1;
    private final ExecutorService bg = Executors.newSingleThreadExecutor();
    private final Handler ui = new Handler(Looper.getMainLooper());
    private LinearLayout page;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        ScrollView scroll = new ScrollView(this);
        scroll.setBackgroundColor(Color.WHITE);
        scroll.setOverScrollMode(View.OVER_SCROLL_NEVER);
        page = new LinearLayout(this);
        page.setOrientation(LinearLayout.VERTICAL);
        page.setPadding(dp(20), dp(16), dp(20), dp(32));
        scroll.addView(page);
        setContentView(scroll);
    }

    @Override
    protected void onResume() {
        super.onResume();
        build();
    }

    @Override
    protected void onDestroy() {
        bg.shutdownNow();
        super.onDestroy();
    }

    // ---------------------------------------------------------------- layout

    private void build() {
        page.removeAllViews();
        TextView title = text("Settings", 26, true);
        title.setPadding(0, 0, 0, dp(8));
        page.addView(title);

        section("Wi-Fi");
        page.addView(toggle("Wi-Fi", wifiOn(), on -> su("svc wifi " + (on ? "enable" : "disable"))));
        page.addView(row("Network", wifiName(), v -> open(new Intent(Settings.ACTION_WIFI_SETTINGS))));

        section("Bluetooth");
        BluetoothAdapter bt = BluetoothAdapter.getDefaultAdapter();
        page.addView(toggle("Bluetooth", bt != null && bt.isEnabled(),
                on -> su("svc bluetooth " + (on ? "enable" : "disable"))));

        section("Front light");
        page.addView(slider("Brightness", setting("system", "screen_brightness", 128), 255,
                v -> su("settings put system screen_brightness " + v)));
        page.addView(slider("Warmth", setting("system", "haoqing_warm_light", 0), 255,
                v -> su("settings put system haoqing_warm_light " + v)));

        section("Sleep");
        page.addView(row("Sleep after", timeoutLabel(setting("system", "screen_off_timeout", 600000)),
                v -> chooseTimeout()));
        page.addView(row("Sleep screen photo", "Choose a photo…", v -> pickPhoto()));
        boolean bubble = getSharedPreferences("inkhome", MODE_PRIVATE).getBoolean("lock_bubble", true);
        page.addView(toggle("Show current book in a thought bubble", bubble, on -> {
            getSharedPreferences("inkhome", MODE_PRIVATE).edit()
                    .putBoolean("lock_bubble", on).remove("lockscreen_key").apply();
            rebuildSleepImage();
        }));

        section("Reading goal");
        android.content.SharedPreferences prefs = getSharedPreferences("inkhome", MODE_PRIVATE);
        page.addView(toggle("Show minutes left to read on the sleep screen", prefs.getBoolean("lock_goal", true),
                on -> {
                    prefs.edit().putBoolean("lock_goal", on).remove("lockscreen_key").apply();
                    rebuildSleepImage();
                }));
        page.addView(row("Daily goal", prefs.getInt("read_goal", ReadingGoal.DEFAULT_GOAL) + " minutes",
                v -> chooseGoal()));
        int read = ReadingGoal.minutesToday(this);
        if (read >= 0) page.addView(info("Read today: " + read + " minutes"));

        section("Sync");
        page.addView(info("Books and words sync with your PC automatically while charging on Wi-Fi."));
        page.addView(row("Sync now", "Runs for 3 minutes, then back to charging-only", v -> {
            sendBroadcast(new Intent("com.github.catfriend1.syncthingfork.action.START")
                    .setComponent(new ComponentName("com.github.catfriend1.syncthingfork",
                            "com.nutomic.syncthingandroid.receiver.AppConfigReceiver")));
            getSharedPreferences("inkhome", MODE_PRIVATE).edit()
                    .putLong("sync_until", System.currentTimeMillis() + 3 * 60 * 1000).apply();
            Toast.makeText(this, "Syncing for 3 minutes", Toast.LENGTH_SHORT).show();
        }));

        section("Storage and battery");
        StatFs fs = new StatFs(Environment.getExternalStorageDirectory().getPath());
        page.addView(info(String.format("%.1f GB free of %.1f GB",
                fs.getAvailableBytes() / 1e9, fs.getTotalBytes() / 1e9)));
        TextView books = info("Books: counting…");
        page.addView(books);
        bg.execute(() -> {
            long[] n = new long[2];
            countBooks(new File("/storage/emulated/0/Books"), n);
            ui.post(() -> books.setText(String.format("Books: %d (%.0f MB)", n[0], n[1] / 1e6)));
        });
        page.addView(info("Battery: " + batteryLabel()));

        section("About");
        page.addView(info(Build.MANUFACTURER + " " + Build.MODEL + " · Android " + Build.VERSION.RELEASE));
        page.addView(info("Build " + Build.DISPLAY));
        page.addView(info("Rooted with Magisk · project notes: meebook-jailbreak/docs on the PC"));

        section("Advanced");
        page.addView(row("All Android settings", "Language, date and time, accessibility…",
                v -> open(new Intent().setClassName("com.android.settings", "com.android.settings.Settings"))));
    }

    private interface OnToggle { void set(boolean on); }
    private interface OnValue { void set(int value); }

    private void section(String name) {
        TextView t = text(name, 15, true);
        t.setTextColor(Color.DKGRAY);
        t.setPadding(0, dp(22), 0, dp(4));
        page.addView(t);
        View line = new View(this);
        line.setBackgroundColor(Color.BLACK);
        page.addView(line, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(1)));
    }

    private TextView text(String s, int sp, boolean bold) {
        TextView t = new TextView(this);
        t.setText(s);
        t.setTextColor(Color.BLACK);
        t.setTextSize(TypedValue.COMPLEX_UNIT_SP, sp);
        if (bold) t.setTypeface(Typeface.DEFAULT_BOLD);
        return t;
    }

    private TextView info(String s) {
        TextView t = text(s, 16, false);
        t.setPadding(0, dp(10), 0, dp(4));
        return t;
    }

    private View row(String label, String detail, View.OnClickListener click) {
        LinearLayout r = new LinearLayout(this);
        r.setOrientation(LinearLayout.VERTICAL);
        r.setPadding(0, dp(12), 0, dp(12));
        r.addView(text(label, 18, false));
        if (detail != null && !detail.isEmpty()) {
            TextView d = text(detail, 14, false);
            d.setTextColor(Color.DKGRAY);
            r.addView(d);
        }
        r.setOnClickListener(click);
        return r;
    }

    private View toggle(String label, boolean on, OnToggle change) {
        Switch s = new Switch(this);
        s.setText(label);
        s.setTextColor(Color.BLACK);
        s.setTextSize(TypedValue.COMPLEX_UNIT_SP, 18);
        s.setChecked(on);
        s.setPadding(0, dp(12), 0, dp(12));
        s.setOnCheckedChangeListener((b, checked) -> bg.execute(() -> change.set(checked)));
        return s;
    }

    private View slider(String label, int value, int max, OnValue change) {
        LinearLayout r = new LinearLayout(this);
        r.setOrientation(LinearLayout.VERTICAL);
        r.setPadding(0, dp(10), 0, dp(6));
        TextView l = text(label + "  " + Math.round(value * 100f / max) + "%", 18, false);
        r.addView(l);
        SeekBar bar = new SeekBar(this);
        bar.setMax(max);
        bar.setProgress(value);
        bar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            private long last;

            @Override
            public void onProgressChanged(SeekBar s, int v, boolean user) {
                l.setText(label + "  " + Math.round(v * 100f / max) + "%");
                long now = System.currentTimeMillis();
                if (user && now - last > 200) {  // throttle root calls while dragging
                    last = now;
                    bg.execute(() -> change.set(v));
                }
            }

            @Override
            public void onStartTrackingTouch(SeekBar s) {}

            @Override
            public void onStopTrackingTouch(SeekBar s) {
                int v = s.getProgress();
                bg.execute(() -> change.set(v));
            }
        });
        r.addView(bar, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(48)));
        return r;
    }

    private int dp(int value) {
        return Math.round(TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, value,
                getResources().getDisplayMetrics()));
    }

    private void open(Intent i) {
        try {
            i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivity(i);
        } catch (Exception e) {
            Toast.makeText(this, "Not available", Toast.LENGTH_SHORT).show();
        }
    }

    // ---------------------------------------------------------------- state

    static String su(String cmd) {
        try {
            Process p = Runtime.getRuntime().exec(new String[]{"su", "-c", cmd});
            StringBuilder out = new StringBuilder();
            try (BufferedReader r = new BufferedReader(new InputStreamReader(p.getInputStream()))) {
                String line;
                while ((line = r.readLine()) != null) out.append(line).append('\n');
            }
            p.waitFor();
            return out.toString();
        } catch (Exception e) {
            return "";
        }
    }

    private int setting(String table, String key, int fallback) {
        try {
            String v = table.equals("system")
                    ? Settings.System.getString(getContentResolver(), key)
                    : Settings.Global.getString(getContentResolver(), key);
            return v == null ? fallback : (int) Double.parseDouble(v.trim());
        } catch (Exception e) {
            return fallback;
        }
    }

    private boolean wifiOn() {
        return setting("global", "wifi_on", 0) == 1;
    }

    private String wifiName() {
        String status = su("cmd wifi status");
        int i = status.indexOf("connected to \"");
        if (i >= 0) {
            int s = i + "connected to \"".length();
            int e = status.indexOf('"', s);
            if (e > s) return "Connected to " + status.substring(s, e);
        }
        return wifiOn() ? "Not connected · tap to choose a network" : "Wi-Fi is off";
    }

    private static final int[] TIMEOUTS = {60000, 120000, 300000, 600000, 1800000, 3600000};

    private static String timeoutLabel(int ms) {
        if (ms >= 3600000) return (ms / 3600000) + " hour";
        return (ms / 60000) + " minutes";
    }

    private void chooseTimeout() {
        String[] labels = new String[TIMEOUTS.length];
        for (int i = 0; i < TIMEOUTS.length; i++) labels[i] = timeoutLabel(TIMEOUTS[i]);
        new AlertDialog.Builder(this, android.R.style.Theme_DeviceDefault_Light_Dialog_Alert)
                .setTitle("Sleep after")
                .setItems(labels, (d, which) -> bg.execute(() -> {
                    su("settings put system screen_off_timeout " + TIMEOUTS[which]);
                    ui.post(this::build);
                }))
                .show();
    }

    /** Ask the home screen (always running) to redraw the sleep image now, not on the next visit home. */
    private void rebuildSleepImage() {
        sendBroadcast(new Intent(MainActivity.ACTION_REBUILD_SLEEP_IMAGE).setPackage(getPackageName()));
    }

    private static final int[] GOALS = {15, 30, 45, 60, 90, 120, 150, 180, 240};

    private void chooseGoal() {
        String[] labels = new String[GOALS.length];
        for (int i = 0; i < GOALS.length; i++) labels[i] = GOALS[i] + " minutes";
        new AlertDialog.Builder(this, android.R.style.Theme_DeviceDefault_Light_Dialog_Alert)
                .setTitle("Daily reading goal")
                .setItems(labels, (d, which) -> {
                    getSharedPreferences("inkhome", MODE_PRIVATE).edit()
                            .putInt("read_goal", GOALS[which]).remove("lockscreen_key").apply();
                    rebuildSleepImage();
                    build();
                })
                .show();
    }

    private String batteryLabel() {
        Intent b = registerReceiver(null, new IntentFilter(Intent.ACTION_BATTERY_CHANGED));
        if (b == null) return "unknown";
        int level = b.getIntExtra(BatteryManager.EXTRA_LEVEL, -1);
        int scale = b.getIntExtra(BatteryManager.EXTRA_SCALE, 100);
        boolean charging = b.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0) != 0;
        return Math.round(level * 100f / scale) + "%" + (charging ? " · charging" : "");
    }

    private static void countBooks(File dir, long[] n) {
        File[] list = dir.listFiles();
        if (list == null) return;
        for (File f : list) {
            if (f.isDirectory()) {
                if (!f.getName().endsWith(".sdr") && !f.getName().startsWith(".")) countBooks(f, n);
            } else if (f.getName().matches("(?i).*\\.(epub|mobi|azw3?|pdf|djvu|fb2|cbz)$")) {
                n[0]++;
                n[1] += f.length();
            }
        }
    }

    // ---------------------------------------------------------------- sleep photo

    private void pickPhoto() {
        Intent i = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        i.addCategory(Intent.CATEGORY_OPENABLE);
        i.setType("image/*");
        startActivityForResult(i, PICK_PHOTO);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode != PICK_PHOTO || resultCode != RESULT_OK || data == null) return;
        Uri uri = data.getData();
        bg.execute(() -> {
            boolean ok = setSleepPhoto(uri);
            ui.post(() -> Toast.makeText(this, ok ? "Sleep photo updated" : "Could not use that photo",
                    Toast.LENGTH_SHORT).show());
        });
    }

    /** Crop/scale to the 1072x1448 panel, lift shadows for e-ink, save as the sleep-screen base. */
    private boolean setSleepPhoto(Uri uri) {
        try (InputStream in = getContentResolver().openInputStream(uri)) {
            Bitmap src = BitmapFactory.decodeStream(in);
            if (src == null) return false;
            int w = 1072, h = 1448;
            float scale = Math.max(w / (float) src.getWidth(), h / (float) src.getHeight());
            int cw = Math.round(w / scale), ch = Math.round(h / scale);
            int cx = (src.getWidth() - cw) / 2, cy = (src.getHeight() - ch) / 2;
            Bitmap out = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
            Canvas c = new Canvas(out);
            c.drawBitmap(src, new Rect(cx, cy, cx + cw, cy + ch), new Rect(0, 0, w, h),
                    new Paint(Paint.FILTER_BITMAP_FLAG));
            brighten(out);
            Lockscreen.DIR.mkdirs();
            boolean saved = Lockscreen.save(out, Lockscreen.BASE);
            if (saved) Lockscreen.GEOMETRY.delete();  // bubble placement was for the old photo
            getSharedPreferences("inkhome", MODE_PRIVATE).edit().remove("lockscreen_key").apply();
            return saved;
        } catch (Exception e) {
            return false;
        }
    }

    /** Same curve as lockscreen/brighten.py: gamma 0.72 with the black point raised to 18. */
    private static void brighten(Bitmap b) {
        int[] lut = new int[256];
        for (int i = 0; i < 256; i++) lut[i] = Math.min(255, Math.round(18 + 237 * (float) Math.pow(i / 255.0, 0.72)));
        int w = b.getWidth(), h = b.getHeight();
        int[] px = new int[w];
        for (int y = 0; y < h; y++) {
            b.getPixels(px, 0, w, 0, y, w, 1);
            for (int x = 0; x < w; x++) {
                int p = px[x];
                px[x] = 0xff000000 | (lut[(p >> 16) & 0xff] << 16) | (lut[(p >> 8) & 0xff] << 8) | lut[p & 0xff];
            }
            b.setPixels(px, 0, w, 0, y, w, 1);
        }
    }
}
