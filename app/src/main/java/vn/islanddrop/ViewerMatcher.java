package vn.islanddrop;

import android.content.ContentResolver;
import android.content.ContentUris;
import android.content.Context;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.Bundle;
import android.provider.MediaStore;
import android.util.Size;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

/**
 * Tìm ảnh trong máy khớp với ảnh đang hiện toàn màn hình trong Thư viện.
 *
 * Cách làm: với mỗi ảnh ứng viên, tính nơi ảnh sẽ được vẽ khi "vừa màn hình" (fit-center),
 * lấy một lưới điểm màu từ ảnh thu nhỏ, rồi so với các điểm tương ứng trên ảnh chụp màn hình.
 * Dùng trung bình của 70% điểm khớp nhất nên toast, hiệu ứng AI hay thanh công cụ che một phần
 * ảnh cũng không làm sai. Chỉ so trong dải giữa màn hình (bỏ thanh trên/dưới của Thư viện).
 *
 * Tất cả chạy trong máy, không gửi gì ra ngoài.
 */
final class ViewerMatcher {

    /** Điểm lấy mẫu của một ảnh ứng viên, tính theo tọa độ màn hình. */
    private static final class Feature {
        final float[] xs, ys;
        final int[] rgb;

        Feature(float[] xs, float[] ys, int[] rgb) {
            this.xs = xs;
            this.ys = ys;
            this.rgb = rgb;
        }
    }

    static final int SCALE = 4;                 // ảnh chụp màn hình được thu nhỏ 1/4 trước khi so
    private static final float ACCEPT = 16f;     // sai khác màu trung bình chấp nhận (0..255)
    private static final int COLS = 14, ROWS = 28;
    private static final int PAGE = 300;
    private static final long MAX_SEARCH_MS = 15_000;

    private final Context ctx;
    private final int screenW, screenH;
    private final float bandTop, bandBottom;
    private final ConcurrentHashMap<Long, Feature> cache = new ConcurrentHashMap<>();
    private final ExecutorService pool = Executors.newFixedThreadPool(3, r -> {
        Thread t = new Thread(r, "IslandDrop-match");
        t.setPriority(Thread.MIN_PRIORITY + 1);
        return t;
    });

    ViewerMatcher(Context ctx, int screenW, int screenH) {
        this.ctx = ctx.getApplicationContext();
        this.screenW = screenW;
        this.screenH = screenH;
        this.bandTop = screenH * 0.16f;
        this.bandBottom = screenH * 0.86f;
    }

    /**
     * Tìm ảnh khớp. Chạy ở luồng nền (có thể mất vài giây ở lần đầu).
     *
     * @param shot         ảnh chụp màn hình đã thu nhỏ 1/SCALE, kiểu ARGB_8888 phần mềm
     * @param minuteMillis thời điểm đầu phút đọc được trên màn hình, hoặc null
     */
    Uri find(Bitmap shot, Long minuteMillis) {
        long start = System.currentTimeMillis();
        Best best = new Best();

        if (minuteMillis != null) {
            long from = minuteMillis - 60_000, to = minuteMillis + 120_000;
            List<Long> ids = queryIds(
                    "(" + MediaStore.Images.Media.DATE_TAKEN + " >= ? AND "
                            + MediaStore.Images.Media.DATE_TAKEN + " < ?) OR ("
                            + MediaStore.Images.Media.DATE_MODIFIED + " >= ? AND "
                            + MediaStore.Images.Media.DATE_MODIFIED + " < ?)",
                    new String[]{Long.toString(from), Long.toString(to),
                            Long.toString(from / 1000), Long.toString(to / 1000)}, 0, 200);
            score(ids, shot, best);
            L.i(ctx, "Matcher: theo phút " + ids.size() + " ứng viên, tốt nhất " + best);
            if (best.score <= ACCEPT) return best.uri();
        }

        for (int offset = 0; System.currentTimeMillis() - start < MAX_SEARCH_MS; offset += PAGE) {
            List<Long> ids = queryIds(null, null, offset, PAGE);
            if (ids.isEmpty()) break;
            score(ids, shot, best);
            L.i(ctx, "Matcher: trang " + offset + " (" + ids.size() + "), tốt nhất " + best);
            if (best.score <= ACCEPT) return best.uri();
        }
        return null;
    }

    /** Tính trước đặc trưng cho các ảnh gần đây, để lần giữ ảnh đầu tiên nhận ra ngay. */
    void prewarm() {
        pool.execute(() -> {
            for (Long id : queryIds(null, null, 0, PAGE)) {
                if (!cache.containsKey(id)) {
                    Feature f = feature(id);
                    if (f != null) cache.put(id, f);
                }
            }
        });
    }

    void shutdown() {
        pool.shutdownNow();
    }

    // ------------------------------------------------------------------ nội bộ

    private static final class Best {
        long id = -1;
        float score = Float.MAX_VALUE;

        Uri uri() {
            return id < 0 ? null
                    : ContentUris.withAppendedId(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, id);
        }

        @Override
        public String toString() {
            return "id=" + id + " sai khác=" + (score == Float.MAX_VALUE ? "-" : String.format("%.1f", score));
        }
    }

    private void score(List<Long> ids, Bitmap shot, Best best) {
        List<Future<Feature>> fs = new ArrayList<>(ids.size());
        for (Long id : ids) {
            Feature cached = cache.get(id);
            if (cached != null) {
                fs.add(null);
            } else {
                fs.add(pool.submit(() -> feature(id)));
            }
        }
        for (int i = 0; i < ids.size(); i++) {
            long id = ids.get(i);
            Feature f = cache.get(id);
            if (f == null && fs.get(i) != null) {
                try {
                    f = fs.get(i).get();
                } catch (Exception ignored) {
                    f = null;
                }
                if (f != null) cache.put(id, f);
            }
            if (f == null) continue;
            float s = compare(f, shot);
            if (s < best.score) {
                best.score = s;
                best.id = id;
            }
        }
    }

    private List<Long> queryIds(String selection, String[] args, int offset, int limit) {
        List<Long> out = new ArrayList<>();
        Bundle q = new Bundle();
        if (selection != null) {
            q.putString(ContentResolver.QUERY_ARG_SQL_SELECTION, selection);
            q.putStringArray(ContentResolver.QUERY_ARG_SQL_SELECTION_ARGS, args);
        }
        q.putString(ContentResolver.QUERY_ARG_SQL_SORT_ORDER,
                MediaStore.Images.Media.DATE_TAKEN + " DESC, " + MediaStore.Images.Media._ID + " DESC");
        q.putInt(ContentResolver.QUERY_ARG_LIMIT, limit);
        q.putInt(ContentResolver.QUERY_ARG_OFFSET, offset);
        try (Cursor c = ctx.getContentResolver().query(MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                new String[]{MediaStore.Images.Media._ID}, q, null)) {
            while (c != null && c.moveToNext()) out.add(c.getLong(0));
        } catch (RuntimeException ex) {
            L.i(ctx, "Matcher: lỗi truy vấn ảnh: " + ex);
        }
        return out;
    }

    /** Lấy mẫu màu của ảnh ứng viên tại các vị trí nó sẽ hiện trên màn hình. */
    private Feature feature(long id) {
        Bitmap t;
        try {
            Uri uri = ContentUris.withAppendedId(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, id);
            t = ctx.getContentResolver().loadThumbnail(uri, new Size(256, 256), null);
        } catch (Exception ex) {
            return null;
        }
        if (t == null) return null;
        try {
            if (t.getConfig() == Bitmap.Config.HARDWARE) {
                Bitmap sw = t.copy(Bitmap.Config.ARGB_8888, false);
                t.recycle();
                t = sw;
                if (t == null) return null;
            }
            int tw = t.getWidth(), th = t.getHeight();
            float sc = Math.min(screenW / (float) tw, screenH / (float) th);
            float dw = tw * sc, dh = th * sc;
            float left = (screenW - dw) / 2f, top = (screenH - dh) / 2f;

            float[] xs = new float[COLS * ROWS], ys = new float[COLS * ROWS];
            int[] rgb = new int[COLS * ROWS];
            int n = 0;
            for (int j = 0; j < ROWS; j++) {
                float y = top + (j + 0.5f) / ROWS * dh;
                if (y < bandTop || y > bandBottom) continue;
                for (int i = 0; i < COLS; i++) {
                    float x = left + (i + 0.5f) / COLS * dw;
                    xs[n] = x;
                    ys[n] = y;
                    rgb[n] = avg(t, (x - left) / sc, (y - top) / sc);
                    n++;
                }
            }
            if (n < 20) return null;
            return new Feature(Arrays.copyOf(xs, n), Arrays.copyOf(ys, n), Arrays.copyOf(rgb, n));
        } finally {
            t.recycle();
        }
    }

    /** Sai khác màu: trung bình 70% điểm khớp nhất. */
    private float compare(Feature f, Bitmap shot) {
        int n = f.rgb.length;
        float[] d = new float[n];
        for (int k = 0; k < n; k++) {
            int a = f.rgb[k];
            int b = avg(shot, f.xs[k] / SCALE, f.ys[k] / SCALE);
            d[k] = (Math.abs(((a >> 16) & 255) - ((b >> 16) & 255))
                    + Math.abs(((a >> 8) & 255) - ((b >> 8) & 255))
                    + Math.abs((a & 255) - (b & 255))) / 3f;
        }
        Arrays.sort(d);
        int keep = Math.max(1, (int) (n * 0.7f));
        float sum = 0;
        for (int k = 0; k < keep; k++) sum += d[k];
        return sum / keep;
    }

    /** Màu trung bình 3x3 quanh (x, y), có kẹp biên. */
    private static int avg(Bitmap b, float fx, float fy) {
        int w = b.getWidth(), h = b.getHeight();
        int cx = Math.round(fx), cy = Math.round(fy);
        int r = 0, g = 0, bl = 0, c = 0;
        for (int dy = -1; dy <= 1; dy++) {
            int y = Math.min(h - 1, Math.max(0, cy + dy));
            for (int dx = -1; dx <= 1; dx++) {
                int x = Math.min(w - 1, Math.max(0, cx + dx));
                int p = b.getPixel(x, y);
                r += (p >> 16) & 255;
                g += (p >> 8) & 255;
                bl += p & 255;
                c++;
            }
        }
        return ((r / c) << 16) | ((g / c) << 8) | (bl / c);
    }
}
