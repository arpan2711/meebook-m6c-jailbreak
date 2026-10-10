package com.meebook.inkhome;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.Rect;
import android.graphics.RectF;

import java.io.File;
import java.io.FileOutputStream;

/**
 * Builds the sleep-screen image: the base photo (/sdcard/InkHome/lockscreen-base.png) with a
 * comic thought bubble rising from the subject's head, holding the cover of the book being read.
 * Geometry is in the 1072x1448 coordinates of the base photo.
 */
final class Lockscreen {
    static final File DIR = new File("/storage/emulated/0/InkHome");
    static final File BASE = new File(DIR, "lockscreen-base.png");

    // thought bubble cloud, upper right
    private static final float CX = 815, CY = 245, RX = 215, RY = 172;
    // trail of small circles from the top-right of the head towards the cloud
    private static final float[][] TRAIL = {{598, 462, 11}, {636, 432, 17}, {683, 395, 25}};

    private Lockscreen() {}

    static Bitmap build(File cover, boolean bubble) {
        Bitmap base = BitmapFactory.decodeFile(BASE.getPath());
        if (base == null) return null;
        Bitmap out = base.copy(Bitmap.Config.ARGB_8888, true);
        Canvas c = new Canvas(out);
        float scale = out.getWidth() / 1072f;
        c.scale(scale, scale);

        if (!bubble) return out;
        Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG);
        fill.setColor(Color.WHITE);
        Paint ink = new Paint(Paint.ANTI_ALIAS_FLAG);
        ink.setColor(Color.BLACK);
        ink.setStyle(Paint.Style.STROKE);
        ink.setStrokeWidth(5);

        for (float[] t : TRAIL) {
            c.drawCircle(t[0], t[1], t[2], fill);
            c.drawCircle(t[0], t[1], t[2], ink);
        }

        // cloud = union of a core ellipse and bumps around its rim
        Path cloud = new Path();
        cloud.addOval(new RectF(CX - RX, CY - RY, CX + RX, CY + RY), Path.Direction.CW);
        int bumps = 11;
        for (int i = 0; i < bumps; i++) {
            double a = 2 * Math.PI * i / bumps + 0.3;
            float bx = (float) (CX + Math.cos(a) * RX * 0.92);
            float by = (float) (CY + Math.sin(a) * RY * 0.92);
            float br = (i % 2 == 0) ? 62 : 50;
            Path bump = new Path();
            bump.addCircle(bx, by, br, Path.Direction.CW);
            cloud.op(bump, Path.Op.UNION);
        }
        c.drawPath(cloud, fill);
        c.drawPath(cloud, ink);

        Bitmap art = cover == null ? null : Covers.load(cover, 320);
        if (art != null) {
            float h = RY * 1.62f;
            float w = h * art.getWidth() / art.getHeight();
            if (w > RX * 1.5f) {  // unusually wide covers
                w = RX * 1.5f;
                h = w * art.getHeight() / art.getWidth();
            }
            RectF dst = new RectF(CX - w / 2, CY - h / 2, CX + w / 2, CY + h / 2);
            Paint smooth = new Paint(Paint.FILTER_BITMAP_FLAG | Paint.ANTI_ALIAS_FLAG);
            c.drawBitmap(art, new Rect(0, 0, art.getWidth(), art.getHeight()), dst, smooth);
            Paint frame = new Paint(ink);
            frame.setStrokeWidth(3);
            c.drawRect(dst, frame);
        }
        return out;
    }

    static boolean save(Bitmap bmp, File to) {
        try (FileOutputStream fos = new FileOutputStream(to)) {
            return bmp.compress(Bitmap.CompressFormat.PNG, 100, fos);
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * The book most recently left in KOReader: KOReader saves a book's sidecar
     * (<book>.sdr/metadata.<ext>.lua) whenever it goes to the background.
     */
    static File currentBook(File booksRoot) {
        File[] best = {null};
        long[] when = {0};
        scan(booksRoot, best, when, 0);
        return best[0];
    }

    private static void scan(File dir, File[] best, long[] when, int depth) {
        File[] list = dir.listFiles();
        if (list == null || depth > 3) return;
        for (File f : list) {
            if (!f.isDirectory()) continue;
            String name = f.getName();
            if (name.endsWith(".sdr")) {
                File[] meta = f.listFiles((d, n) -> n.startsWith("metadata.") && n.endsWith(".lua"));
                if (meta == null) continue;
                for (File m : meta) {
                    if (m.lastModified() > when[0]) {
                        String ext = m.getName().substring("metadata.".length(), m.getName().length() - ".lua".length());
                        File book = new File(dir, name.substring(0, name.length() - 4) + "." + ext);
                        if (book.isFile()) {
                            when[0] = m.lastModified();
                            best[0] = book;
                        }
                    }
                }
            } else if (!name.startsWith(".")) {
                scan(f, best, when, depth + 1);
            }
        }
    }
}
