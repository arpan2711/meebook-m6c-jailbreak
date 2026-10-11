package com.meebook.inkhome;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.Typeface;
import android.net.Uri;
import android.os.Bundle;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.webkit.WebResourceRequest;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.LinearLayout;
import android.widget.TextView;

/**
 * Look Up: a bare, e-ink friendly web view for KOReader's dictionary buttons. It opens far faster than a
 * browser because it lives in InkHome's process (the launcher is always running, so the web engine stays
 * loaded). No tabs, no animations; page up/down buttons instead of smooth scrolling.
 *
 *   inklook://search?q=TEXT  ->  DuckDuckGo Lite (no JavaScript, ~25 KB)
 *   inklook://ask?q=TEXT     ->  Ask Brave (free AI answer, no account)
 */
public class LookUpActivity extends Activity {

    private static final String SEARCH = "https://lite.duckduckgo.com/lite/?q=";
    private static final String ASK = "https://search.brave.com/ask?q=";

    /** Scrolls the page, or the biggest scrolling box on it (Brave scrolls a div, not the window). */
    private static final String SCROLL_JS = "(function(d){var b=document.scrollingElement||document.body,best=0;"
            + "if(b.scrollHeight<=b.clientHeight+40){var all=document.querySelectorAll('*');"
            + "for(var i=0;i<all.length;i++){var e=all[i];if(e.scrollHeight>e.clientHeight+40&&e.clientHeight>best"
            + "&&/(auto|scroll)/.test(getComputedStyle(e).overflowY)){best=e.clientHeight;b=e;}}}"
            + "b.scrollBy({top:d*b.clientHeight*0.85,behavior:'instant'});})(%d)";

    private WebView web;
    private TextView title;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(Color.WHITE);

        LinearLayout bar = new LinearLayout(this);
        bar.setGravity(Gravity.CENTER_VERTICAL);
        bar.setPadding(dp(4), 0, dp(4), 0);
        bar.addView(barButton("✕", v -> finishAndRemoveTask()));
        bar.addView(barButton("‹", v -> {
            if (web.canGoBack()) web.goBack();
        }));
        title = new TextView(this);
        title.setTextColor(Color.DKGRAY);
        title.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
        title.setSingleLine(true);
        title.setEllipsize(TextUtils.TruncateAt.END);
        title.setPadding(dp(6), 0, dp(6), 0);
        bar.addView(title, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        bar.addView(barButton("▲", v -> scroll(-1)));
        bar.addView(barButton("▼", v -> scroll(1)));
        bar.addView(barButton("↗", v -> openInBrowser()));
        root.addView(bar, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(52)));
        View line = new View(this);
        line.setBackgroundColor(Color.BLACK);
        root.addView(line, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(1)));

        web = new WebView(this);
        WebSettings s = web.getSettings();
        s.setJavaScriptEnabled(true);   // Ask Brave needs it; DuckDuckGo Lite works without
        s.setDomStorageEnabled(true);
        s.setTextZoom(115);
        web.setOverScrollMode(View.OVER_SCROLL_NEVER);
        web.setVerticalScrollBarEnabled(false);
        web.setWebViewClient(new WebViewClient() {
            @Override
            public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
                String scheme = request.getUrl().getScheme();
                if ("http".equals(scheme) || "https".equals(scheme)) return false;  // stay in Look Up
                try {
                    startActivity(new Intent(Intent.ACTION_VIEW, request.getUrl()));
                } catch (Exception ignored) {
                }
                return true;
            }

            @Override
            public void onPageFinished(WebView view, String url) {
                String t = view.getTitle();
                title.setText(TextUtils.isEmpty(t) ? Uri.parse(url).getHost() : t);
            }
        });
        root.addView(web, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));
        setContentView(root);
        load(getIntent());
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        load(intent);
    }

    @Override
    public void onBackPressed() {
        if (web.canGoBack()) web.goBack();
        else finishAndRemoveTask();
    }

    @Override
    protected void onDestroy() {
        web.destroy();
        super.onDestroy();
    }

    private void load(Intent intent) {
        Uri data = intent.getData();
        if (data == null) {
            finishAndRemoveTask();
            return;
        }
        String q = data.getQueryParameter("q");
        if (q == null) q = "";
        String url = ("ask".equals(data.getHost()) ? ASK : SEARCH) + Uri.encode(q);
        title.setText("Loading…");
        web.clearHistory();
        web.loadUrl(url);
    }

    private void scroll(int direction) {
        web.evaluateJavascript(String.format(java.util.Locale.ROOT, SCROLL_JS, direction), null);
    }

    private void openInBrowser() {
        String url = web.getUrl();
        if (url == null) return;
        try {
            startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(url)).addCategory(Intent.CATEGORY_BROWSABLE));
        } catch (Exception ignored) {
        }
    }

    private TextView barButton(String label, View.OnClickListener click) {
        TextView b = new TextView(this);
        b.setText(label);
        b.setTextColor(Color.BLACK);
        b.setTextSize(TypedValue.COMPLEX_UNIT_SP, 22);
        b.setTypeface(Typeface.DEFAULT_BOLD);
        b.setGravity(Gravity.CENTER);
        b.setMinWidth(dp(48));
        b.setPadding(dp(6), dp(4), dp(6), dp(4));
        b.setOnClickListener(click);
        return b;
    }

    private int dp(int v) {
        return Math.round(v * getResources().getDisplayMetrics().density);
    }
}
