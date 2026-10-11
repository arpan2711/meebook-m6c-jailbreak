package com.meebook.inkhome;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;

/**
 * Haoqing's background-app manager (HaoqingReceiver.clearApp in com.android.settings, run on CLEAR_MEMORY)
 * keeps the 4 most recent apps (sys.hq.HaoQingLimit) and force-stops older ones unless they are listed as
 * "pkg 1 1 1" in /data/haoqing/haoqing_whitelist.conf (columns: exempt from cleanup, auto-start, background).
 * Third-party apps default to "0 0 0", which made KOReader crash on resume. The file is system-owned, so it
 * is read and written through su, rewritten in place (cat >) to keep its owner and SELinux label. The manager
 * re-reads the file whenever its modified time changes, so no reload signal is needed.
 */
final class BackgroundWhitelist {
    private static final String FILE = "/data/haoqing/haoqing_whitelist.conf";

    private BackgroundWhitelist() {
    }

    /** True if the package is listed with background activity allowed. Blocking (runs su). */
    static boolean isKept(String pkg) {
        String file = read();
        if (file == null) return false;
        for (String line : file.split("\n")) {
            String[] f = line.trim().split("\\s+");
            if (f.length >= 4 && f[0].equals(pkg)) return "1".equals(f[1]) && "1".equals(f[3]);
        }
        return false;
    }

    /** Sets the package to "1 1 1" (keep) or "0 0 0". Blocking. */
    static boolean set(String pkg, boolean keep) {
        String file = read();
        if (file == null) return false;
        String entry = pkg + (keep ? " 1 1 1" : " 0 0 0");
        StringBuilder out = new StringBuilder();
        boolean found = false;
        for (String line : file.split("\n", -1)) {
            String[] f = line.trim().split("\\s+");
            if (!line.startsWith("#") && f.length >= 3 && f[0].equals(pkg)) {
                if (!found) out.append(entry).append('\n');
                found = true;
            } else {
                out.append(line).append('\n');
            }
        }
        String text = out.toString().replaceAll("\n+$", "\n");
        if (!found) text += entry + "\n";
        try {
            Process p = Runtime.getRuntime().exec(new String[]{"su", "-c",
                    "cat > " + FILE});
            try (OutputStream o = p.getOutputStream()) {
                o.write(text.getBytes(StandardCharsets.UTF_8));
            }
            return p.waitFor() == 0;
        } catch (Exception e) {
            return false;
        }
    }

    private static String read() {
        try {
            Process p = Runtime.getRuntime().exec(new String[]{"su", "-c", "cat " + FILE});
            StringBuilder sb = new StringBuilder();
            try (BufferedReader r = new BufferedReader(new InputStreamReader(p.getInputStream(), StandardCharsets.UTF_8))) {
                for (String line; (line = r.readLine()) != null; ) sb.append(line).append('\n');
            }
            return p.waitFor() == 0 && sb.length() > 0 ? sb.toString() : null;
        } catch (Exception e) {
            return null;
        }
    }
}
