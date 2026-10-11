package com.meebook.inkhome;

import android.app.Activity;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Home Control: the Smart Life lights/plugs and the Dreo fan, as big black-and-white tiles.
 * Same features as the Amazfit watch app, but the tablet talks to the clouds directly.
 * A tap changes the tile at once (one e-ink redraw) and changes it back if the cloud call fails.
 */
public class HomeControlActivity extends Activity {

    /** Kept across launches so reopening the screen shows the last known state straight away. */
    private static HomeCloud cloud;

    private final ExecutorService bg = Executors.newSingleThreadExecutor();
    private final Handler ui = new Handler(Looper.getMainLooper());
    private ScrollView scroll;
    private LinearLayout page;
    private List<HomeCloud.Device> devices = new ArrayList<>();
    private String status = "";
    private String detailId;   // null = device list
    private boolean busy;

    private static final int[] BRIGHTNESS = {25, 50, 75, 100};
    private static final String[] WHITE_LABELS = {"Warm", "Mid", "Cool"};
    private static final int[] WHITE_TEMPS = {0, 500, 1000};
    // Tuya hue (0-360) and the swatch colour shown for it
    private static final int[] HUES = {0, 30, 120, 200, 240, 285};
    private static final int[] SWATCHES = {0xffff3b30, 0xffff9500, 0xff34c759, 0xff32ade6, 0xff3a5bff, 0xffaf52de};

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (cloud == null) cloud = new HomeCloud(getApplicationContext().getFilesDir());
        scroll = new ScrollView(this);
        scroll.setBackgroundColor(Color.WHITE);
        scroll.setOverScrollMode(View.OVER_SCROLL_NEVER);
        scroll.setVerticalScrollBarEnabled(false);
        page = new LinearLayout(this);
        page.setOrientation(LinearLayout.VERTICAL);
        page.setPadding(dp(20), dp(16), dp(20), dp(40));
        scroll.addView(page);
        setContentView(scroll);
        devices = cloud.cached();
    }

    @Override
    protected void onResume() {
        super.onResume();
        refresh();
    }

    @Override
    protected void onStop() {
        super.onStop();
        // Always reopen on the device list, so a tap meant for a tile can't hit a detail button
        detailId = null;
    }

    @Override
    protected void onDestroy() {
        bg.shutdownNow();
        super.onDestroy();
    }

    @Override
    public void onBackPressed() {
        if (detailId != null) {
            detailId = null;
            render();
            scroll.scrollTo(0, 0);
        } else {
            super.onBackPressed();
        }
    }

    // ------------------------------------------------------------------ cloud calls

    private void refresh() {
        if (!cloud.configured()) {
            status = "No Smart Life or Dreo login on this tablet yet. Copy tuya.json / dreo.json into "
                    + "InkHome's private files folder (see docs/home-control.md on the PC).";
            render();
            return;
        }
        status = devices.isEmpty() ? "Loading…" : "Updating…";
        render();
        bg.execute(() -> {
            String next;
            List<HomeCloud.Device> list = null;
            try {
                HomeCloud.Listing l = withRetry(() -> cloud.list());
                list = l.devices;
                next = l.warnings.isEmpty() ? "" : TextUtils.join("\n", l.warnings);
            } catch (Exception e) {
                next = HomeCloud.message(e);
            }
            String s = next;
            List<HomeCloud.Device> got = list;
            ui.post(() -> {
                if (isDestroyed()) return;
                if (got != null) devices = got;
                status = s;
                render();
            });
        });
    }

    private interface Call<T> {
        T run() throws IOException;
    }

    /** Wi-Fi is often still reconnecting right after the tablet wakes: try once more after a moment. */
    private static <T> T withRetry(Call<T> call) throws IOException {
        try {
            return call.run();
        } catch (IOException e) {
            if (!HomeCloud.isNetwork(e)) throw e;
            try {
                Thread.sleep(2500);
            } catch (InterruptedException ie) {
                throw e;
            }
            return call.run();
        }
    }

    /** Applies the change on screen first, then sends it; restores the old state if the call fails. */
    private void act(HomeCloud.Device d, String action, Object value, Runnable optimistic, Runnable undo) {
        if (busy) return;
        busy = true;
        if (optimistic != null) optimistic.run();
        status = "";
        render();
        bg.execute(() -> {
            String error = null;
            try {
                withRetry(() -> {
                    cloud.act(d, action, value);
                    return null;
                });
            } catch (Exception e) {
                error = HomeCloud.message(e);
            }
            String err = error;
            ui.post(() -> {
                busy = false;
                if (isDestroyed()) return;
                if (err != null) {
                    if (undo != null) undo.run();
                    status = d.name + ": " + err;
                }
                render();
            });
        });
    }

    private void toggle(HomeCloud.Device d) {
        if (!d.online) {
            status = d.name + " is offline";
            render();
            return;
        }
        if ("other".equals(d.kind)) {
            status = d.name + " isn't supported yet";
            render();
            return;
        }
        boolean was = d.on;
        act(d, "power", !was, () -> d.on = !was, () -> d.on = was);
    }

    private void allOff() {
        if (busy) return;
        busy = true;
        status = "Turning everything off…";
        render();
        bg.execute(() -> {
            String s;
            try {
                int[] r = withRetry(() -> cloud.allOff());
                s = r[0] == 0 && r[1] == 0 ? "Everything is already off"
                        : "Turned off " + r[0] + (r[1] > 0 ? " · " + r[1] + " failed" : "");
            } catch (Exception e) {
                s = HomeCloud.message(e);
            }
            String msg = s;
            ui.post(() -> {
                busy = false;
                if (isDestroyed()) return;
                devices = cloud.cached();
                status = msg;
                render();
            });
        });
    }

    // ------------------------------------------------------------------ screens

    private void render() {
        page.removeAllViews();
        HomeCloud.Device d = detailId == null ? null : find(detailId);
        if (d == null) {
            detailId = null;
            renderList();
        } else if ("fan".equals(d.kind)) {
            renderFan(d);
        } else {
            renderLight(d);
        }
    }

    private HomeCloud.Device find(String id) {
        for (HomeCloud.Device d : devices) if (d.id.equals(id)) return d;
        return null;
    }

    private void renderList() {
        page.addView(title("Home", null));
        addStatus();
        for (HomeCloud.Device d : devices) page.addView(tile(d), spaced(10));

        if (!devices.isEmpty()) {
            LinearLayout row = new LinearLayout(this);
            row.addView(button("All off", false, v -> allOff()), weighted(0));
            row.addView(button("Refresh", false, v -> refresh()), weighted(12));
            page.addView(row, spaced(24));
        } else if (cloud.configured()) {
            page.addView(button("Retry", false, v -> refresh()), spaced(24));
        }
    }

    private View tile(HomeCloud.Device d) {
        boolean lit = d.online && d.on;
        boolean detail = d.online && ("fan".equals(d.kind) || "light".equals(d.kind));
        LinearLayout row = new LinearLayout(this);

        LinearLayout main = new LinearLayout(this);
        main.setOrientation(LinearLayout.VERTICAL);
        main.setGravity(Gravity.CENTER_VERTICAL);
        main.setPadding(dp(18), dp(12), dp(18), dp(12));
        main.setMinimumHeight(dp(76));
        main.setBackground(box(lit, d.online));
        TextView name = text(d.name, 21, true);
        name.setSingleLine(true);
        name.setEllipsize(TextUtils.TruncateAt.END);
        TextView state = text(tileState(d), 16, false);
        int fg = lit ? Color.WHITE : d.online ? Color.BLACK : Color.GRAY;
        name.setTextColor(fg);
        state.setTextColor(fg);
        main.addView(name);
        main.addView(state);
        main.setOnClickListener(v -> toggle(d));
        row.addView(main, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1f));

        if (detail) {
            TextView more = button("›", false, v -> {
                detailId = d.id;
                render();
                scroll.scrollTo(0, 0);
            });
            more.setTextSize(TypedValue.COMPLEX_UNIT_SP, 34);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(dp(72), ViewGroup.LayoutParams.MATCH_PARENT);
            lp.leftMargin = dp(10);
            row.addView(more, lp);
        }
        return row;
    }

    private static String tileState(HomeCloud.Device d) {
        if (!d.online) return "offline";
        if ("other".equals(d.kind)) return "not supported yet";
        if (!d.on) return "Off";
        if ("fan".equals(d.kind)) return "On · speed " + d.speed + "/" + d.speedMax
                + (TextUtils.isEmpty(d.mode) ? "" : " · " + d.mode);
        if ("plug".equals(d.kind) && d.watts != null) return "On · " + Math.round(d.watts) + " W";
        if ("light".equals(d.kind)) {
            if ("colour".equals(d.mode)) return "On · colour · " + Math.round(d.val / 10f) + "%";
            return "On · " + Math.round(d.bright / 10f) + "% · " + whiteName(d.temp);
        }
        return "On";
    }

    private static String whiteName(int temp) {
        return temp < 250 ? "warm" : temp > 750 ? "cool" : "mid";
    }

    private void renderFan(HomeCloud.Device d) {
        page.addView(title(d.name, d));
        addStatus();
        powerRow(d);

        section("Speed");
        LinearLayout speed = new LinearLayout(this);
        speed.setGravity(Gravity.CENTER_VERTICAL);
        TextView minus = button("−", false, v -> {
            int was = d.speed;
            if (was > d.speedMin) act(d, "speed", was - 1, () -> d.speed = was - 1, () -> d.speed = was);
        });
        TextView plus = button("+", false, v -> {
            int was = d.speed;
            if (was < d.speedMax) act(d, "speed", was + 1, () -> d.speed = was + 1, () -> d.speed = was);
        });
        minus.setTextSize(TypedValue.COMPLEX_UNIT_SP, 30);
        plus.setTextSize(TypedValue.COMPLEX_UNIT_SP, 30);
        TextView value = text(d.speed + " / " + d.speedMax, 26, true);
        value.setGravity(Gravity.CENTER);
        speed.addView(minus, new LinearLayout.LayoutParams(dp(96), dp(64)));
        speed.addView(value, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        speed.addView(plus, new LinearLayout.LayoutParams(dp(96), dp(64)));
        page.addView(speed);

        if (!d.modes.isEmpty()) {
            section("Mode");
            List<View> buttons = new ArrayList<>();
            for (String m : d.modes) {
                buttons.add(button(m, d.on && m.equals(d.mode), v -> {
                    String was = d.mode;
                    act(d, "mode", m, () -> d.mode = m, () -> d.mode = was);
                }));
            }
            grid(buttons, 3);
        }

        if (!d.oscOptions.isEmpty()) {
            section("Oscillation");
            List<View> buttons = new ArrayList<>();
            for (Object o : d.oscOptions) {
                buttons.add(button(d.oscLabel(o), d.on && String.valueOf(o).equals(String.valueOf(d.osc)), v -> {
                    Object was = d.osc;
                    act(d, "osc", o, () -> d.osc = o, () -> d.osc = was);
                }));
            }
            grid(buttons, 3);
        }
    }

    private void renderLight(HomeCloud.Device d) {
        page.addView(title(d.name, d));
        addStatus();
        powerRow(d);

        section("Brightness");
        int current = Math.round(("colour".equals(d.mode) ? d.val : d.bright) / 10f);
        List<View> levels = new ArrayList<>();
        for (int pct : BRIGHTNESS) {
            levels.add(button(pct + "%", d.on && Math.abs(current - pct) < 5, v -> act(d, "brightness", pct, null, null)));
        }
        grid(levels, 4);

        section("White");
        List<View> whites = new ArrayList<>();
        for (int i = 0; i < WHITE_TEMPS.length; i++) {
            int temp = WHITE_TEMPS[i];
            boolean active = d.on && "white".equals(d.mode) && whiteName(d.temp).equals(WHITE_LABELS[i].toLowerCase());
            whites.add(button(WHITE_LABELS[i], active, v -> act(d, "white", temp, null, null)));
        }
        grid(whites, 3);

        section("Colour");
        List<View> swatches = new ArrayList<>();
        for (int i = 0; i < HUES.length; i++) {
            int hue = HUES[i];
            boolean active = d.on && "colour".equals(d.mode) && Math.abs(d.hue - hue) < 10;
            TextView s = button(active ? "●" : "", false, v -> act(d, "colour", hue, null, null));
            GradientDrawable g = new GradientDrawable();
            g.setColor(SWATCHES[i]);
            g.setCornerRadius(dp(10));
            g.setStroke(dp(active ? 4 : 2), Color.BLACK);
            s.setBackground(g);
            s.setTextColor(Color.WHITE);
            swatches.add(s);
        }
        grid(swatches, 3);
    }

    private void powerRow(HomeCloud.Device d) {
        LinearLayout row = new LinearLayout(this);
        boolean was = d.on;
        row.addView(button("On", d.on, v -> {
            if (!was) act(d, "power", true, () -> d.on = true, () -> d.on = false);
        }), weighted(0));
        row.addView(button("Off", !d.on, v -> {
            if (was) act(d, "power", false, () -> d.on = false, () -> d.on = true);
        }), weighted(12));
        page.addView(row, spaced(8));
    }

    // ------------------------------------------------------------------ views

    private View title(String s, HomeCloud.Device detail) {
        LinearLayout row = new LinearLayout(this);
        row.setGravity(Gravity.CENTER_VERTICAL);
        if (detail != null) {
            TextView back = text("‹", 34, false);
            back.setPadding(0, 0, dp(16), dp(4));
            back.setOnClickListener(v -> onBackPressed());
            row.addView(back);
        }
        TextView t = text(s, 26, true);
        t.setSingleLine(true);
        t.setEllipsize(TextUtils.TruncateAt.END);
        row.addView(t, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        row.setPadding(0, 0, 0, dp(8));
        return row;
    }

    private void addStatus() {
        if (status.isEmpty()) return;
        TextView t = text(status, 15, false);
        t.setTextColor(Color.DKGRAY);
        t.setPadding(0, dp(2), 0, dp(6));
        page.addView(t);
    }

    private void section(String name) {
        TextView t = text(name, 15, true);
        t.setTextColor(Color.DKGRAY);
        t.setPadding(0, dp(22), 0, dp(8));
        page.addView(t);
    }

    private void grid(List<View> cells, int columns) {
        for (int start = 0; start < cells.size(); start += columns) {
            LinearLayout row = new LinearLayout(this);
            for (int c = 0; c < columns; c++) {
                int i = start + c;
                View cell = i < cells.size() ? cells.get(i) : new View(this);
                row.addView(cell, weighted(c == 0 ? 0 : 10));
            }
            page.addView(row, spaced(start == 0 ? 0 : 10));
        }
    }

    private TextView button(String label, boolean active, View.OnClickListener click) {
        TextView b = text(label, 18, active);
        b.setGravity(Gravity.CENTER);
        b.setMinHeight(dp(60));
        b.setPadding(dp(6), dp(6), dp(6), dp(6));
        b.setSingleLine(true);
        b.setEllipsize(TextUtils.TruncateAt.END);
        b.setTextColor(active ? Color.WHITE : Color.BLACK);
        b.setBackground(box(active, true));
        b.setOnClickListener(click);
        return b;
    }

    private GradientDrawable box(boolean filled, boolean enabled) {
        GradientDrawable g = new GradientDrawable();
        g.setCornerRadius(dp(10));
        g.setColor(filled ? Color.BLACK : Color.WHITE);
        g.setStroke(dp(2), enabled ? Color.BLACK : Color.LTGRAY);
        return g;
    }

    private TextView text(String s, int sp, boolean bold) {
        TextView t = new TextView(this);
        t.setText(s);
        t.setTextColor(Color.BLACK);
        t.setTextSize(TypedValue.COMPLEX_UNIT_SP, sp);
        if (bold) t.setTypeface(Typeface.DEFAULT_BOLD);
        return t;
    }

    private LinearLayout.LayoutParams weighted(int leftMarginDp) {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        lp.leftMargin = dp(leftMarginDp);
        return lp;
    }

    private LinearLayout.LayoutParams spaced(int topMarginDp) {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.topMargin = dp(topMarginDp);
        return lp;
    }

    private int dp(int v) {
        return Math.round(v * getResources().getDisplayMetrics().density);
    }
}
