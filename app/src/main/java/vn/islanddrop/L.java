package vn.islanddrop;

import android.content.Context;
import android.util.Log;

import java.io.File;
import java.io.FileWriter;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/**
 * Ghi log vừa ra logcat vừa ra file (files/log.txt), vì bộ nhớ logcat của HiOS
 * xoay vòng rất nhanh và hay làm mất log. Đọc: adb shell run-as vn.islanddrop cat files/log.txt
 */
final class L {
    static final String TAG = "IslandDrop";
    private static final long MAX_BYTES = 256 * 1024;

    private L() {}

    static synchronized void i(Context ctx, String msg) {
        Log.i(TAG, msg);
        try {
            File f = new File(ctx.getFilesDir(), "log.txt");
            if (f.length() > MAX_BYTES) f.delete();
            try (FileWriter w = new FileWriter(f, true)) {
                w.write(new SimpleDateFormat("HH:mm:ss.SSS", Locale.US).format(new Date()));
                w.write(' ');
                w.write(msg);
                w.write('\n');
            }
        } catch (Exception ignored) {
            // không để lỗi ghi log làm sập app
        }
    }
}
