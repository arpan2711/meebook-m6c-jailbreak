package com.meebook.inkhome;

import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.StrictMode;
import android.provider.Settings;
import android.text.TextUtils;
import android.util.LruCache;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import java.io.File;
import java.io.FileOutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.text.Collator;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * E-ink home screen on plain white: KOReader favourites as book covers, then every app as an icon grid.
 * No search, no wallpaper, no animations (each animation frame is a costly e-ink redraw).
 */
public class MainActivity extends Activity {

    private static final String COLLECTIONS = "/storage/emulated/0/koreader/settings/collection.lua";
    private static final String KOREADER = "org.koreader.launcher";
    private static final int BOOK_COLUMNS = 3;
    private static final int APP_COLUMNS = 4;

    private static final class App {
        final String label;
        final ComponentName component;
        final Drawable icon;

        App(String label, ComponentName component, Drawable icon) {
            this.label = label;
            this.component = component;
            this.icon = icon;
        }
    }

    private LinearLayout content;
    private long collectionsStamp = -1;
    private boolean appsDirty = true;
    private final List<App> apps = new ArrayList<>();
    private final ExecutorService coverLoader = Executors.newSingleThreadExecutor();
    private final Handler ui = new Handler(Looper.getMainLooper());
    private final LruCache<String, Bitmap> covers = new LruCache<String, Bitmap>(16 * 1024 * 1024) {
        @Override
        protected int sizeOf(String key, Bitmap value) {
            return value.getByteCount();
        }
    };

    private final BroadcastReceiver packagesChanged = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            appsDirty = true;
            render();
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        // KOReader opens books from file:// URIs; allow handing it one.
        StrictMode.setVmPolicy(new StrictMode.VmPolicy.Builder().build());

        ScrollView scroll = new ScrollView(this);
        scroll.setBackgroundColor(Color.WHITE);
        scroll.setOverScrollMode(View.OVER_SCROLL_NEVER);
        scroll.setVerticalScrollBarEnabled(false);
        content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(dp(8), dp(16), dp(8), dp(24));
        scroll.addView(content);
        setContentView(scroll);

        IntentFilter filter = new IntentFilter();
        filter.addAction(Intent.ACTION_PACKAGE_ADDED);
        filter.addAction(Intent.ACTION_PACKAGE_REMOVED);
        filter.addAction(Intent.ACTION_PACKAGE_CHANGED);
        filter.addDataScheme("package");
        registerReceiver(packagesChanged, filter);
        historyWatcher.startWatching();
        registerReceiver(rebuildRequested, new IntentFilter(ACTION_REBUILD_SLEEP_IMAGE));
        ui.postDelayed(goalTick, GOAL_TICK_MS);
        // Load the web engine once in the background, so Look Up (KOReader's search buttons) opens instantly
        ui.postDelayed(() -> android.webkit.WebSettings.getDefaultUserAgent(this), 15000);
    }

    /** Sent (package-local) by SettingsActivity when a sleep-image setting changes. */
    static final String ACTION_REBUILD_SLEEP_IMAGE = "com.meebook.inkhome.REBUILD_SLEEP_IMAGE";
    private final BroadcastReceiver rebuildRequested = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            coverLoader.execute(MainActivity.this::updateLockscreen);
        }
    };

    /** The reading-goal number only needs to be roughly current: refresh it every half hour. */
    private static final long GOAL_TICK_MS = 30 * 60 * 1000;
    private final Runnable goalTick = new Runnable() {
        @Override
        public void run() {
            coverLoader.execute(MainActivity.this::updateLockscreen);
            ui.postDelayed(this, GOAL_TICK_MS);
        }
    };

    /**
     * KOReader rewrites history.lua as soon as a book is opened. Rebuild the sleep image then, so
     * the new cover is ready even if the tablet goes to sleep straight from KOReader (Haoqing
     * draws the sleep screen before we would get any screen-off broadcast).
     */
    private final Runnable historyChanged = () -> coverLoader.execute(this::updateLockscreen);
    private final android.os.FileObserver historyWatcher = new android.os.FileObserver(
            Lockscreen.HISTORY.getParent(), android.os.FileObserver.CLOSE_WRITE | android.os.FileObserver.MOVED_TO) {
        @Override
        public void onEvent(int event, String path) {
            if (path == null || !path.startsWith(Lockscreen.HISTORY.getName())) return;
            ui.removeCallbacks(historyChanged);
            ui.postDelayed(historyChanged, 1500);  // let KOReader finish writing
        }
    };

    private final Runnable recheck = this::refreshIfChanged;

    @Override
    protected void onResume() {
        super.onResume();
        refreshIfChanged();
        refreshStats();
        long until = syncUntil();  // a manual sync window that ended while we were away
        if (until > 0 && System.currentTimeMillis() >= until) followSyncRules();
        else if (until > 0) { ui.removeCallbacks(endSync); ui.postDelayed(endSync, until - System.currentTimeMillis()); }
        updateSyncLabel();
        // KOReader writes collection.lua as it goes to the background, which can be just
        // after we resume; look again shortly so a newly added favourite still appears.
        ui.removeCallbacks(recheck);
        ui.postDelayed(recheck, 1500);
        ui.postDelayed(recheck, 4000);
    }

    @Override
    protected void onPause() {
        ui.removeCallbacks(recheck);
        super.onPause();
    }

    private void refreshIfChanged() {
        long stamp = new File(COLLECTIONS).lastModified();
        if (stamp != collectionsStamp || appsDirty) {
            collectionsStamp = stamp;
            render();
        }
        coverLoader.execute(this::updateLockscreen);
    }

    /** Rebuilds the sleep-screen image when the current book or the base photo changes (runs on coverLoader). */
    private void updateLockscreen() {
        if (!Lockscreen.BASE.isFile()) return;
        File book = Lockscreen.currentBook(new File("/storage/emulated/0/Books"));
        android.content.SharedPreferences prefs = getSharedPreferences("inkhome", MODE_PRIVATE);
        boolean bubble = prefs.getBoolean("lock_bubble", true);
        int goal = prefs.getInt("read_goal", ReadingGoal.DEFAULT_GOAL);
        int read = prefs.getBoolean("lock_goal", true) ? ReadingGoal.minutesToday(this) : -1;
        int left = read < 0 ? -1 : Math.max(0, goal - read);
        String key = (book == null ? "-" : book.getAbsolutePath()) + "|" + Lockscreen.BASE.lastModified()
                + "|" + Lockscreen.GEOMETRY.lastModified() + "|" + bubble + "|" + left;
        File out = new File(getFilesDir(), "standby.png");
        if (key.equals(prefs.getString("lockscreen_key", null)) && out.isFile()) {
            // Haoqing's wallpaper app copies its default over the sleep image at every boot
            // (WallpaperControl.resetStandbyImg), so put ours back if the installed one changed.
            if (installedSleepImageMatches(out)) return;
        } else {
            Bitmap img = Lockscreen.build(book, bubble, left, goal);
            if (img == null) return;
            if (!Lockscreen.save(img, out)) return;
        }
        // Haoqing's sleep screen reads these system-owned files (see FunctionUtil.getStandbyPath)
        String cmd = "for f in standby.png standby_1.png; do t=/data/haoqing/$f; "
                + "cp '" + out.getAbsolutePath() + "' $t && chown system:system $t && chmod 600 $t "
                + "&& chcon u:object_r:datahaoqing_data_file:s0 $t || exit 1; done";
        try {
            Process p = Runtime.getRuntime().exec(new String[]{"su", "-c", cmd});
            if (p.waitFor() == 0) prefs.edit().putString("lockscreen_key", key).apply();
        } catch (Exception ignored) {
            // no root: keep the previous sleep image
        }
    }

    /** True when both system sleep images have the same size as ours (they are root-only, so ask su). */
    private static boolean installedSleepImageMatches(File ours) {
        try {
            Process p = Runtime.getRuntime().exec(new String[]{"su", "-c",
                    "stat -c %s /data/haoqing/standby.png /data/haoqing/standby_1.png"});
            java.io.BufferedReader r = new java.io.BufferedReader(new java.io.InputStreamReader(p.getInputStream()));
            String a = r.readLine(), b = r.readLine();
            p.waitFor();
            String size = String.valueOf(ours.length());
            return size.equals(a) && size.equals(b);
        } catch (Exception e) {
            return true;  // no root: nothing we could do anyway
        }
    }

    @Override
    protected void onDestroy() {
        unregisterReceiver(packagesChanged);
        historyWatcher.stopWatching();
        unregisterReceiver(rebuildRequested);
        ui.removeCallbacks(goalTick);
        ui.removeCallbacks(historyChanged);
        coverLoader.shutdownNow();
        statsLoader.shutdownNow();
        super.onDestroy();
    }

    /**
     * Swipes that start on the bottom edge belong to the system gestures (left = recents,
     * centre = home, right = full e-ink refresh). If our ScrollView sees them too it drops the
     * panel into fast A2 mode, and the refresh then happens without its flash, so keep them out.
     */
    private boolean edgeGesture;

    @Override
    public boolean dispatchTouchEvent(android.view.MotionEvent ev) {
        if (ev.getActionMasked() == android.view.MotionEvent.ACTION_DOWN) {
            edgeGesture = ev.getRawY() >= getResources().getDisplayMetrics().heightPixels - dp(28);
        }
        if (edgeGesture) return true;
        return super.dispatchTouchEvent(ev);
    }

    @Override
    public void onBackPressed() {
        // Home has nowhere to go back to.
    }

    private void render() {
        if (appsDirty) {
            loadApps();
            appsDirty = false;
        }
        content.removeAllViews();
        List<File> books = favourites();
        if (!books.isEmpty()) {
            content.addView(headerWithSync("Books"));
            addRows(books.size(), BOOK_COLUMNS, i -> bookTile(books.get(i)));
            addStatsTile();
            content.addView(header("Apps"));
        } else {
            addStatsTile();
            content.addView(headerWithSync("Apps"));
        }
        addRows(apps.size(), APP_COLUMNS, i -> appCell(apps.get(i)));
    }

    // --- Device stats tile (battery, storage, CPU, memory, Wi-Fi, uptime) ---

    private StatsTile stats;
    private String[][] lastStats;
    private final ExecutorService statsLoader = Executors.newSingleThreadExecutor();

    private void addStatsTile() {
        stats = new StatsTile(this);
        if (lastStats != null) stats.show(lastStats);
        stats.setOnClickListener(v -> refreshStats());
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.setMargins(dp(8), dp(10), dp(8), dp(2));
        content.addView(stats, lp);
    }

    private void refreshStats() {
        statsLoader.execute(() -> {
            String[][] s = StatsTile.sample(this);
            ui.post(() -> {
                lastStats = s;
                if (stats != null) stats.show(s);
            });
        });
    }

    private interface CellFactory {
        View make(int index);
    }

    private void addRows(int count, int columns, CellFactory factory) {
        for (int start = 0; start < count; start += columns) {
            LinearLayout row = new LinearLayout(this);
            row.setOrientation(LinearLayout.HORIZONTAL);
            for (int c = 0; c < columns; c++) {
                int i = start + c;
                View cell = i < count ? factory.make(i) : new View(this);
                row.addView(cell, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
            }
            content.addView(row, new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        }
    }

    // --- Sync (Syncthing runs only while charging; this button forces a short run on battery) ---

    private static final String SYNCTHING = "com.github.catfriend1.syncthingfork";
    private static final long SYNC_WINDOW_MS = 3 * 60 * 1000;
    private TextView syncStatus;
    private final Runnable endSync = this::followSyncRules;

    /** Section title with a tappable sync icon beside it: "Books ⟳". */
    private View headerWithSync(String title) {
        LinearLayout row = new LinearLayout(this);
        row.setGravity(Gravity.CENTER_VERTICAL);
        TextView t = header(title);
        row.addView(t);
        SyncIcon icon = new SyncIcon(this);
        icon.setOnClickListener(v -> startSync());
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(dp(40), dp(30));
        lp.topMargin = dp(4);
        lp.leftMargin = dp(14);
        row.addView(icon, lp);
        syncStatus = new TextView(this);
        syncStatus.setTextColor(Color.DKGRAY);
        syncStatus.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13);
        syncStatus.setPadding(dp(6), dp(4), 0, 0);
        syncStatus.setOnClickListener(v -> startSync());
        row.addView(syncStatus, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        HouseIcon home = new HouseIcon(this);
        home.setOnClickListener(v -> startActivity(new Intent(this, HomeControlActivity.class)));
        LinearLayout.LayoutParams hp = new LinearLayout.LayoutParams(dp(40), dp(30));
        hp.topMargin = dp(4);
        hp.rightMargin = dp(10);
        row.addView(home, hp);
        updateSyncLabel();
        return row;
    }

    private long syncUntil() {
        return getSharedPreferences("inkhome", MODE_PRIVATE).getLong("sync_until", 0);
    }

    private void updateSyncLabel() {
        if (syncStatus != null) {
            syncStatus.setText(System.currentTimeMillis() < syncUntil() ? "syncing…" : "");
        }
    }

    private void sendSyncthing(String action) {
        Intent i = new Intent(SYNCTHING + ".action." + action);
        i.setComponent(new ComponentName(SYNCTHING, "com.nutomic.syncthingandroid.receiver.AppConfigReceiver"));
        sendBroadcast(i);
    }

    private void startSync() {
        sendSyncthing("START");  // run now, ignoring the charging-only rule
        getSharedPreferences("inkhome", MODE_PRIVATE).edit()
                .putLong("sync_until", System.currentTimeMillis() + SYNC_WINDOW_MS).apply();
        updateSyncLabel();
        ui.removeCallbacks(endSync);
        ui.postDelayed(endSync, SYNC_WINDOW_MS);
    }

    private void followSyncRules() {
        sendSyncthing("FOLLOW");  // back to: sync only while charging
        getSharedPreferences("inkhome", MODE_PRIVATE).edit().remove("sync_until").apply();
        updateSyncLabel();
    }

    private TextView header(String text) {
        TextView t = new TextView(this);
        t.setText(text);
        t.setTextColor(Color.BLACK);
        t.setTypeface(Typeface.DEFAULT_BOLD);
        t.setTextSize(TypedValue.COMPLEX_UNIT_SP, 18);
        t.setPadding(dp(8), dp(12), dp(8), dp(8));
        return t;
    }

    // --- Books (KOReader favourites) ---

    /** Files in the "favorites" collection of KOReader's collection.lua, in KOReader's order. */
    private List<File> favourites() {
        List<File> out = new ArrayList<>();
        try {
            String lua = new String(Files.readAllBytes(new File(COLLECTIONS).toPath()), StandardCharsets.UTF_8);
            int start = lua.indexOf("[\"favorites\"]");
            if (start < 0) return out;
            int open = lua.indexOf('{', start);
            int depth = 0, end = lua.length();
            for (int i = open; i < lua.length(); i++) {
                char ch = lua.charAt(i);
                if (ch == '"') {  // skip string literals (paths can contain braces)
                    for (i++; i < lua.length() && lua.charAt(i) != '"'; i++) if (lua.charAt(i) == '\\') i++;
                } else if (ch == '{') depth++;
                else if (ch == '}' && --depth == 0) { end = i; break; }
            }
            String block = lua.substring(open, end);
            Matcher m = Pattern.compile("\\{\\s*\\[\"file\"\\]\\s*=\\s*\"((?:[^\"\\\\]|\\\\.)*)\",\\s*\\[\"order\"\\]\\s*=\\s*(\\d+)").matcher(block);
            List<Object[]> entries = new ArrayList<>();
            while (m.find()) {
                String path = m.group(1).replace("\\\"", "\"").replace("\\\\", "\\");
                entries.add(new Object[]{Integer.parseInt(m.group(2)), path});
            }
            Collections.sort(entries, new Comparator<Object[]>() {
                @Override
                public int compare(Object[] a, Object[] b) {
                    return Integer.compare((Integer) a[0], (Integer) b[0]);
                }
            });
            for (Object[] e : entries) {
                File f = new File((String) e[1]);
                if (f.isFile()) out.add(f);
            }
        } catch (Exception ignored) {
            // no collections file yet, or unreadable: just show apps
        }
        return out;
    }

    private static String titleOf(File f) {
        String name = f.getName();
        int dot = name.lastIndexOf('.');
        if (dot > 0) name = name.substring(0, dot);
        int dash = name.indexOf(" - ");  // library files are named "Author - Title"
        return dash > 0 ? name.substring(dash + 3) : name;
    }

    private View bookTile(final File book) {
        LinearLayout cell = new LinearLayout(this);
        cell.setOrientation(LinearLayout.VERTICAL);
        cell.setGravity(Gravity.CENTER_HORIZONTAL);
        cell.setPadding(dp(8), dp(4), dp(8), dp(12));

        int width = (getResources().getDisplayMetrics().widthPixels - dp(16)) / BOOK_COLUMNS - dp(16);
        int height = width * 3 / 2;
        FrameLayout frame = new FrameLayout(this);
        GradientDrawable border = new GradientDrawable();
        border.setColor(Color.WHITE);
        border.setStroke(dp(1), Color.BLACK);
        frame.setBackground(border);
        frame.setPadding(dp(1), dp(1), dp(1), dp(1));

        TextView placeholder = new TextView(this);
        placeholder.setText(titleOf(book));
        placeholder.setTextColor(Color.BLACK);
        placeholder.setTextSize(TypedValue.COMPLEX_UNIT_SP, 15);
        placeholder.setGravity(Gravity.CENTER);
        placeholder.setPadding(dp(6), dp(6), dp(6), dp(6));
        frame.addView(placeholder, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        final ImageView cover = new ImageView(this);
        cover.setScaleType(ImageView.ScaleType.CENTER_CROP);
        frame.addView(cover, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        cell.addView(frame, new LinearLayout.LayoutParams(width, height));

        TextView label = new TextView(this);
        label.setText(titleOf(book));
        label.setTextColor(Color.BLACK);
        label.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13);
        label.setGravity(Gravity.CENTER_HORIZONTAL);
        label.setMaxLines(2);
        label.setEllipsize(TextUtils.TruncateAt.END);
        label.setPadding(0, dp(6), 0, 0);
        cell.addView(label, new LinearLayout.LayoutParams(width, ViewGroup.LayoutParams.WRAP_CONTENT));

        loadCover(book, width, cover, placeholder);
        cell.setOnClickListener(v -> openBook(book));
        cell.setOnLongClickListener(v -> {
            new android.app.AlertDialog.Builder(this, android.R.style.Theme_DeviceDefault_Light_Dialog_Alert)
                    .setTitle(titleOf(book))
                    .setItems(new CharSequence[]{"Open", "Remove from favourites"}, (d, which) -> {
                        if (which == 0) openBook(book);
                        else if (removeFavourite(book)) render();
                    })
                    .setNegativeButton("Cancel", null)
                    .show();
            return true;
        });
        return cell;
    }

    /**
     * Rewrites the "favorites" block of KOReader's collection.lua without this book. Entries are
     * renumbered because KOReader reads them with ipairs (a gap would drop the rest). The favsync
     * KOReader plugin re-reads the file, so KOReader's in-memory copy will not undo this.
     */
    private boolean removeFavourite(File book) {
        try {
            File file = new File(COLLECTIONS);
            String lua = new String(Files.readAllBytes(file.toPath()), StandardCharsets.UTF_8);
            int key = lua.indexOf("[\"favorites\"]");
            if (key < 0) return false;
            int open = lua.indexOf('{', key);
            int close = matchBrace(lua, open);
            if (close < 0) return false;
            String block = lua.substring(open, close + 1);

            String settings = "{\n            [\"order\"] = 1,\n        }";
            int sKey = block.indexOf("[\"settings\"]");
            if (sKey >= 0) {
                int sOpen = block.indexOf('{', sKey);
                int sClose = matchBrace(block, sOpen);
                if (sClose > 0) settings = block.substring(sOpen, sClose + 1);
            }

            Matcher m = Pattern.compile("\\[\"file\"\\]\\s*=\\s*\"((?:[^\"\\\\]|\\\\.)*)\",\\s*\\[\"order\"\\]\\s*=\\s*(\\d+)").matcher(block);
            String target = book.getCanonicalPath();
            StringBuilder entries = new StringBuilder();
            int index = 0;
            boolean removed = false;
            while (m.find()) {
                String raw = m.group(1);
                String path = raw.replace("\\\"", "\"").replace("\\\\", "\\");
                if (new File(path).getCanonicalPath().equals(target)) {
                    removed = true;
                    continue;
                }
                index++;
                entries.append("        [").append(index).append("] = {\n")
                        .append("            [\"file\"] = \"").append(raw).append("\",\n")
                        .append("            [\"order\"] = ").append(m.group(2)).append(",\n")
                        .append("        },\n");
            }
            if (!removed) return false;
            String newBlock = "{\n" + entries + "        [\"settings\"] = " + settings + ",\n    }";
            String updated = lua.substring(0, open) + newBlock + lua.substring(close + 1);

            File tmp = new File(file.getParentFile(), "collection.lua.inkhome-tmp");
            Files.write(tmp.toPath(), updated.getBytes(StandardCharsets.UTF_8));
            if (!tmp.renameTo(file)) {
                Files.write(file.toPath(), updated.getBytes(StandardCharsets.UTF_8));
                tmp.delete();
            }
            collectionsStamp = file.lastModified();
            return true;
        } catch (Exception e) {
            android.widget.Toast.makeText(this, "Could not update favourites", android.widget.Toast.LENGTH_SHORT).show();
            return false;
        }
    }

    /** Index of the brace closing the one at {@code open}, skipping string literals; -1 if none. */
    private static int matchBrace(String s, int open) {
        int depth = 0;
        for (int i = open; i < s.length(); i++) {
            char ch = s.charAt(i);
            if (ch == '"') {
                for (i++; i < s.length() && s.charAt(i) != '"'; i++) if (s.charAt(i) == '\\') i++;
            } else if (ch == '{') depth++;
            else if (ch == '}' && --depth == 0) return i;
        }
        return -1;
    }

    private void loadCover(final File book, final int width, final ImageView into, final View placeholder) {
        final String key = book.getAbsolutePath() + "|" + book.lastModified();
        Bitmap cached = covers.get(key);
        if (cached != null) {
            into.setImageBitmap(cached);
            placeholder.setVisibility(View.INVISIBLE);
            return;
        }
        coverLoader.execute(() -> {
            File disk = new File(getCacheDir(), Integer.toHexString(key.hashCode()) + ".png");
            Bitmap bmp = null;
            if (disk.isFile()) bmp = android.graphics.BitmapFactory.decodeFile(disk.getPath());
            if (bmp == null) {
                bmp = Covers.load(book, width);
                if (bmp != null) {
                    try (FileOutputStream out = new FileOutputStream(disk)) {
                        bmp.compress(Bitmap.CompressFormat.PNG, 100, out);
                    } catch (Exception ignored) {
                        // cache write failure only costs a re-extract next time
                    }
                }
            }
            if (bmp == null) return;
            final Bitmap result = bmp;
            covers.put(key, result);
            ui.post(() -> {
                into.setImageBitmap(result);
                placeholder.setVisibility(View.INVISIBLE);
            });
        });
    }

    private void openBook(File book) {
        String n = book.getName().toLowerCase();
        String type = n.endsWith(".epub") ? "application/epub+zip"
                : n.endsWith(".pdf") ? "application/pdf"
                : n.endsWith(".djvu") ? "image/vnd.djvu"
                : n.endsWith(".azw3") ? "application/vnd.amazon.ebook"
                : "application/x-mobipocket-ebook";
        Intent intent = new Intent(Intent.ACTION_VIEW);
        intent.setDataAndType(Uri.fromFile(book), type);
        intent.setComponent(new ComponentName(KOREADER, KOREADER + ".MainActivity"));
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        try {
            startActivity(intent);
            overridePendingTransition(0, 0);
        } catch (Exception ignored) {
            // KOReader missing: nothing to open with
        }
    }

    // --- Apps ---

    private void loadApps() {
        PackageManager pm = getPackageManager();
        Intent main = new Intent(Intent.ACTION_MAIN);
        main.addCategory(Intent.CATEGORY_LAUNCHER);
        List<App> loaded = new ArrayList<>();
        for (ResolveInfo ri : pm.queryIntentActivities(main, 0)) {
            // hide the home screen itself and Haoqing's settings (replaced by our Settings screen)
            if (ri.activityInfo.name.equals(MainActivity.class.getName())) continue;
            if (ri.activityInfo.packageName.equals("com.android.settings")) continue;
            // Google's Files stays installed (it is the system file picker) but Fossify Files replaces its icon
            if (ri.activityInfo.packageName.equals("com.android.documentsui")) continue;
            loaded.add(new App(ri.loadLabel(pm).toString(),
                    new ComponentName(ri.activityInfo.packageName, ri.activityInfo.name),
                    ri.loadIcon(pm)));
        }
        final Collator collator = Collator.getInstance();
        Collections.sort(loaded, new Comparator<App>() {
            @Override
            public int compare(App a, App b) {
                return collator.compare(a.label, b.label);
            }
        });
        apps.clear();
        apps.addAll(loaded);
    }

    private View appCell(final App app) {
        LinearLayout cell = new LinearLayout(this);
        cell.setOrientation(LinearLayout.VERTICAL);
        cell.setGravity(Gravity.CENTER_HORIZONTAL);
        cell.setPadding(dp(4), dp(12), dp(4), dp(12));
        ImageView icon = new ImageView(this);
        icon.setImageDrawable(app.icon);
        cell.addView(icon, new LinearLayout.LayoutParams(dp(56), dp(56)));
        TextView label = new TextView(this);
        label.setText(app.label);
        label.setTextColor(Color.BLACK);
        label.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
        label.setGravity(Gravity.CENTER_HORIZONTAL);
        label.setMaxLines(2);
        label.setEllipsize(TextUtils.TruncateAt.END);
        label.setPadding(0, dp(6), 0, 0);
        cell.addView(label, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        cell.setOnClickListener(v -> {
            Intent intent = new Intent(Intent.ACTION_MAIN);
            intent.addCategory(Intent.CATEGORY_LAUNCHER);
            intent.setComponent(app.component);
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED);
            startActivity(intent);
            overridePendingTransition(0, 0);
        });
        cell.setOnLongClickListener(v -> {
            appMenu(app);
            return true;
        });
        return cell;
    }

    /** Long-press menu: App info, and whether Haoqing's manager may keep the app running in the background. */
    private void appMenu(App app) {
        String pkg = app.component.getPackageName();
        coverLoader.execute(() -> {
            boolean kept = BackgroundWhitelist.isKept(pkg);
            ui.post(() -> {
                if (isDestroyed()) return;
                new android.app.AlertDialog.Builder(this, android.R.style.Theme_DeviceDefault_Light_Dialog_Alert)
                        .setTitle(app.label)
                        .setItems(new CharSequence[]{
                                "App info",
                                kept ? "Keep in background: ON  (tap to turn off)"
                                     : "Keep in background: OFF  (tap to turn on)"}, (d, which) -> {
                            if (which == 0) {
                                Intent info = new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                                        Uri.fromParts("package", pkg, null));
                                info.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_NO_ANIMATION);
                                startActivity(info);
                            } else {
                                coverLoader.execute(() -> {
                                    boolean ok = BackgroundWhitelist.set(pkg, !kept);
                                    ui.post(() -> android.widget.Toast.makeText(this,
                                            !ok ? "Couldn't change it (no root?)"
                                                : app.label + (kept ? " may now be closed in the background"
                                                                    : " will be kept in the background"),
                                            android.widget.Toast.LENGTH_SHORT).show());
                                });
                            }
                        })
                        .setNegativeButton("Cancel", null)
                        .show();
            });
        });
    }

    private int dp(int value) {
        return Math.round(TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, value,
                getResources().getDisplayMetrics()));
    }
}
