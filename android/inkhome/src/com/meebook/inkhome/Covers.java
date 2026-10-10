package com.meebook.inkhome;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.graphics.pdf.PdfRenderer;
import android.os.ParcelFileDescriptor;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.InputStream;
import java.io.RandomAccessFile;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/** Extracts a cover image from EPUB, MOBI/AZW3 and PDF files. Returns null when none is found. */
final class Covers {
    private Covers() {}

    static Bitmap load(File f, int targetWidth) {
        String name = f.getName().toLowerCase();
        try {
            byte[] img = null;
            if (name.endsWith(".epub")) img = epub(f);
            else if (name.endsWith(".mobi") || name.endsWith(".azw3") || name.endsWith(".azw")) img = mobi(f);
            else if (name.endsWith(".pdf")) return pdf(f, targetWidth);
            if (img == null) return null;
            BitmapFactory.Options o = new BitmapFactory.Options();
            o.inJustDecodeBounds = true;
            BitmapFactory.decodeByteArray(img, 0, img.length, o);
            int sample = 1;
            while (o.outWidth / (sample * 2) >= targetWidth) sample *= 2;
            o = new BitmapFactory.Options();
            o.inSampleSize = sample;
            return BitmapFactory.decodeByteArray(img, 0, img.length, o);
        } catch (Throwable t) {
            return null;
        }
    }

    // --- EPUB: container.xml -> OPF -> cover item ---
    private static byte[] epub(File f) throws Exception {
        try (ZipFile zip = new ZipFile(f)) {
            String container = text(zip, "META-INF/container.xml");
            if (container == null) return null;
            Matcher m = Pattern.compile("full-path=\"([^\"]+)\"").matcher(container);
            if (!m.find()) return null;
            String opfPath = m.group(1);
            String opf = text(zip, opfPath);
            if (opf == null) return null;
            String base = opfPath.contains("/") ? opfPath.substring(0, opfPath.lastIndexOf('/') + 1) : "";

            String href = null;
            for (String item : items(opf)) {  // EPUB 3
                if (item.contains("cover-image")) { href = attr(item, "href"); break; }
            }
            if (href == null) {  // EPUB 2: <meta name="cover" content="id">
                Matcher meta = Pattern.compile("<meta[^>]*name=\"cover\"[^>]*content=\"([^\"]+)\"|<meta[^>]*content=\"([^\"]+)\"[^>]*name=\"cover\"").matcher(opf);
                if (meta.find()) {
                    String id = meta.group(1) != null ? meta.group(1) : meta.group(2);
                    for (String item : items(opf)) {
                        if (id.equals(attr(item, "id"))) { href = attr(item, "href"); break; }
                    }
                }
            }
            if (href == null) {  // any image whose id/href mentions "cover", else the first image
                String first = null;
                for (String item : items(opf)) {
                    String type = attr(item, "media-type");
                    if (type == null || !type.startsWith("image/")) continue;
                    if (first == null) first = attr(item, "href");
                    if (item.toLowerCase().contains("cover")) { href = attr(item, "href"); break; }
                }
                if (href == null) href = first;
            }
            if (href == null) return null;
            String path = normalize(base + URLDecoder.decode(href, "UTF-8"));
            ZipEntry e = zip.getEntry(path);
            return e == null ? null : bytes(zip.getInputStream(e));
        }
    }

    private static Iterable<String> items(String opf) {
        java.util.List<String> out = new java.util.ArrayList<>();
        Matcher m = Pattern.compile("<item\\s[^>]*>").matcher(opf);
        while (m.find()) out.add(m.group());
        return out;
    }

    private static String attr(String tag, String name) {
        Matcher m = Pattern.compile("\\s" + name + "=\"([^\"]*)\"").matcher(tag);
        return m.find() ? m.group(1) : null;
    }

    private static String normalize(String path) {
        java.util.LinkedList<String> parts = new java.util.LinkedList<>();
        for (String p : path.split("/")) {
            if (p.equals("..")) { if (!parts.isEmpty()) parts.removeLast(); }
            else if (!p.isEmpty() && !p.equals(".")) parts.add(p);
        }
        return android.text.TextUtils.join("/", parts);
    }

    private static String text(ZipFile zip, String name) throws Exception {
        ZipEntry e = zip.getEntry(name);
        return e == null ? null : new String(bytes(zip.getInputStream(e)), StandardCharsets.UTF_8);
    }

    private static byte[] bytes(InputStream in) throws Exception {
        try (InputStream s = in) {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            byte[] buf = new byte[16384];
            int n;
            while ((n = s.read(buf)) > 0) out.write(buf, 0, n);
            return out.toByteArray();
        }
    }

    // --- MOBI/AZW3: PalmDB records, EXTH 201 (cover offset) relative to the first image record ---
    private static byte[] mobi(File f) throws Exception {
        try (RandomAccessFile raf = new RandomAccessFile(f, "r")) {
            raf.seek(76);
            int count = raf.readUnsignedShort();
            long[] offsets = new long[count + 1];
            for (int i = 0; i < count; i++) {
                raf.seek(78 + i * 8L);
                offsets[i] = raf.readInt() & 0xffffffffL;
            }
            offsets[count] = raf.length();
            long r0 = offsets[0];
            raf.seek(r0 + 16);
            byte[] magic = new byte[4];
            raf.readFully(magic);
            if (!new String(magic, StandardCharsets.US_ASCII).equals("MOBI")) return null;
            int headerLen = raf.readInt();
            raf.seek(r0 + 16 + 92);
            int firstImage = raf.readInt();
            raf.seek(r0 + 16 + 112);
            int exthFlags = raf.readInt();
            if ((exthFlags & 0x40) == 0 || firstImage <= 0) return null;
            long exth = r0 + 16 + headerLen;
            raf.seek(exth);
            raf.readFully(magic);
            if (!new String(magic, StandardCharsets.US_ASCII).equals("EXTH")) return null;
            raf.readInt();  // EXTH length
            int records = raf.readInt();
            int cover = -1, thumb = -1;
            for (int i = 0; i < records; i++) {
                int type = raf.readInt();
                int len = raf.readInt();
                if ((type == 201 || type == 202) && len == 12) {
                    int v = raf.readInt();
                    if (type == 201) cover = v; else thumb = v;
                } else {
                    raf.skipBytes(len - 8);
                }
            }
            int off = cover >= 0 ? cover : thumb;
            if (off < 0) return null;
            int rec = firstImage + off;
            if (rec >= count) return null;
            byte[] data = new byte[(int) (offsets[rec + 1] - offsets[rec])];
            raf.seek(offsets[rec]);
            raf.readFully(data);
            return data;
        }
    }

    // --- PDF: render the first page ---
    private static Bitmap pdf(File f, int targetWidth) throws Exception {
        try (ParcelFileDescriptor fd = ParcelFileDescriptor.open(f, ParcelFileDescriptor.MODE_READ_ONLY);
             PdfRenderer r = new PdfRenderer(fd)) {
            if (r.getPageCount() == 0) return null;
            try (PdfRenderer.Page page = r.openPage(0)) {
                int w = targetWidth;
                int h = Math.max(1, (int) ((long) page.getHeight() * w / Math.max(1, page.getWidth())));
                Bitmap bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
                bmp.eraseColor(Color.WHITE);
                page.render(bmp, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY);
                return bmp;
            }
        }
    }
}
