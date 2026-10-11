package com.meebook.inkhome;

import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.Calendar;

/**
 * Minutes read today, from KOReader's statistics database (page_stat_data: one row per page view
 * with its duration in seconds). KOReader keeps it in WAL mode, so we read a private copy of the
 * database and its -wal file instead of opening the live one.
 */
final class ReadingGoal {
    static final int DEFAULT_GOAL = 120;
    private static final File DB = new File("/storage/emulated/0/koreader/settings/statistics.sqlite3");

    private ReadingGoal() {}

    /** Minutes read since local midnight, or -1 when there are no statistics. */
    static int minutesToday(Context context) {
        if (!DB.isFile()) return -1;
        File copy = new File(context.getCacheDir(), "statistics.sqlite3");
        File wal = new File(DB.getPath() + "-wal"), walCopy = new File(copy.getPath() + "-wal");
        try {
            Files.copy(DB.toPath(), copy.toPath(), StandardCopyOption.REPLACE_EXISTING);
            if (wal.isFile()) Files.copy(wal.toPath(), walCopy.toPath(), StandardCopyOption.REPLACE_EXISTING);
            else walCopy.delete();
            new File(copy.getPath() + "-shm").delete();
        } catch (Exception e) {
            return -1;
        }
        Calendar midnight = Calendar.getInstance();
        midnight.set(Calendar.HOUR_OF_DAY, 0);
        midnight.set(Calendar.MINUTE, 0);
        midnight.set(Calendar.SECOND, 0);
        midnight.set(Calendar.MILLISECOND, 0);
        try (SQLiteDatabase db = SQLiteDatabase.openDatabase(copy.getPath(), null, SQLiteDatabase.OPEN_READWRITE);
             Cursor c = db.rawQuery("SELECT coalesce(sum(duration), 0) FROM page_stat_data WHERE start_time >= ?",
                     new String[]{String.valueOf(midnight.getTimeInMillis() / 1000)})) {
            return c.moveToFirst() ? (int) (c.getLong(0) / 60) : 0;
        } catch (Exception e) {
            return -1;
        }
    }
}
