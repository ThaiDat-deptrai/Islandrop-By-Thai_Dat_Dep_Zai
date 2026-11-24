package vn.islanddrop;

import android.Manifest;
import android.accessibilityservice.AccessibilityService;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.Rect;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.view.Display;
import android.view.WindowManager;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityNodeInfo;

import java.util.Calendar;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Nhận biết khi người dùng GIỮ NGÓN TAY lên một ảnh đang mở trong Thư viện Tecno.
 *
 * Không đụng tới thao tác chạm. Khi giữ ảnh, Thư viện Tecno luôn thử "AI cắt chủ thể" và hiện
 * toast "Không có nội dung nào có thể bị cắt bỏ" nếu không cắt được — đó là tín hiệu ta dùng.
 * (Nếu cắt được chủ thể, Thư viện tự kéo phần đã cắt, khay kéo thả xử lý như bình thường.)
 *
 * Sau đó: đọc ngày giờ trên màn hình (nếu đang hiện), chụp màn hình, tìm đúng ảnh trong máy,
 * rồi bảo đảo hiện khay để người dùng chạm chọn app gửi.
 * Chỉ nhận sự kiện của com.gallery20 (khai báo trong xml), không đọc app khác.
 */
public class ViewerWatcher extends AccessibilityService {

    private static final String GALLERY = "com.gallery20";
    private static final String PHOTO_VIEW_ID = GALLERY + ":id/photo_view";
    private static final Pattern DATE_VI = Pattern.compile("(\\d{1,2}) thg (\\d{1,2}),? (\\d{4})");
    private static final Pattern TIME = Pattern.compile("^(\\d{1,2}):(\\d{2})$");
    private static final long DEBOUNCE_MS = 1500;

    private final Handler main = new Handler(Looper.getMainLooper());
    private final ExecutorService worker = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "IslandDrop-viewer");
        t.setPriority(Thread.NORM_PRIORITY - 1);
        return t;
    });
    private ViewerMatcher matcher;
    private long lastTrigger;
    private boolean busy;

    @Override
    protected void onServiceConnected() {
        Rect b = getSystemService(WindowManager.class).getMaximumWindowMetrics().getBounds();
        matcher = new ViewerMatcher(this, b.width(), b.height());
        L.i(this, "Watcher: đã kết nối, màn hình " + b.width() + "x" + b.height());
    }

    @Override
    public void onAccessibilityEvent(AccessibilityEvent ev) {
        try {
            String pkg = String.valueOf(ev.getPackageName());
            int type = ev.getEventType();
            if (type == AccessibilityEvent.TYPE_VIEW_CLICKED) {
                if (getPackageName().equals(pkg)) return;
                if (isCopyClick(ev)) {
                    onCopyClicked(pkg);
                } else {
                    logCopyLike(ev, pkg);
                }
                return;
            }
            if (type == AccessibilityEvent.TYPE_NOTIFICATION_STATE_CHANGED && !GALLERY.equals(pkg)
                    && isCopiedToast(ev)) {
                onCopyClicked(pkg);      // dự phòng: app hiện thông báo "Đã sao chép"
                return;
            }
            if (!GALLERY.equals(pkg)) return;
            if (type == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) {
                // vừa mở Thư viện: tính trước đặc trưng ảnh gần đây để lần giữ đầu tiên nhanh
                if (hasImagePermission() && matcher != null) matcher.prewarm();
            } else if (type == AccessibilityEvent.TYPE_NOTIFICATION_STATE_CHANGED
                    && String.valueOf(ev.getText()).contains("cắt bỏ")) {
                onLongPressInViewer();
            }
        } catch (RuntimeException ex) {
            L.i(this, "Watcher lỗi: " + ex);
        }
    }

    // ---------------------------------------------------------------- sao chép văn bản

    private static final String[] COPY_LABELS = {"sao chép", "copy", "sao chép văn bản", "copy text"};
    private long lastCopy;

    private static boolean isCopyClick(AccessibilityEvent ev) {
        for (CharSequence c : ev.getText()) {
            if (c != null && isCopyLabel(c.toString())) return true;
        }
        CharSequence d = ev.getContentDescription();
        return d != null && isCopyLabel(d.toString());
    }

    /** Thông báo ngắn kiểu "Đã sao chép", "Copied to clipboard". */
    private static boolean isCopiedToast(AccessibilityEvent ev) {
        for (CharSequence c : ev.getText()) {
            if (c == null || c.length() > 60) continue;
            String t = c.toString().toLowerCase(java.util.Locale.ROOT);
            if (t.contains("đã sao chép") || t.contains("copied")) return true;
        }
        return false;
    }

    /** Chẩn đoán: ghi lại nút có nhãn ngắn chứa "sao chép"/"copy" mà chưa khớp (không ghi nội dung khác). */
    private void logCopyLike(AccessibilityEvent ev, String pkg) {
        for (CharSequence c : ev.getText()) {
            if (c == null || c.length() > 30) continue;
            String t = c.toString().toLowerCase(java.util.Locale.ROOT);
            if (t.contains("chép") || t.contains("copy")) {
                L.i(this, "Watcher: nút gần giống Sao chép [" + c + "] class=" + ev.getClassName() + " app=" + pkg);
                return;
            }
        }
    }

    private static boolean isCopyLabel(String s) {
        String t = s.trim().toLowerCase(java.util.Locale.ROOT);
        for (String l : COPY_LABELS) if (t.equals(l)) return true;
        return false;
    }

    /** Người dùng vừa bấm "Sao chép": mở màn hình trong suốt để đọc bộ nhớ tạm. */
    private void onCopyClicked(String fromPkg) {
        long now = SystemClock.uptimeMillis();
        if (now - lastCopy < 800) return;
        lastCopy = now;
        if (DropService.instance == null) {
            L.i(this, "Watcher: bấm Sao chép nhưng khay đang tắt");
            return;
        }
        L.i(this, "Watcher: bấm Sao chép trong " + fromPkg);
        // đợi app nguồn ghi xong vào bộ nhớ tạm
        main.postDelayed(() -> {
            try {
                startActivity(new android.content.Intent(this, ClipReadActivity.class)
                        .addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK
                                | android.content.Intent.FLAG_ACTIVITY_NO_ANIMATION));
            } catch (RuntimeException ex) {
                L.i(this, "Watcher: không mở được ClipReadActivity: " + ex);
            }
        }, 150);
    }

    // ---------------------------------------------------------------- giữ ảnh đang xem

    private void onLongPressInViewer() {
        long now = SystemClock.uptimeMillis();
        if (busy || now - lastTrigger < DEBOUNCE_MS) {
            L.i(this, "Watcher: bỏ qua (đang xử lý)");
            return;
        }
        DropService svc = DropService.instance;
        if (svc == null) {
            L.i(this, "Watcher: khay đang tắt");
            return;
        }
        AccessibilityNodeInfo root = getRootInActiveWindow();
        if (root == null) {
            L.i(this, "Watcher: không đọc được cửa sổ hiện tại");
            return;
        }
        List<AccessibilityNodeInfo> photo = root.findAccessibilityNodeInfosByViewId(PHOTO_VIEW_ID);
        if (photo == null || photo.isEmpty()) {
            L.i(this, "Watcher: không phải màn hình xem ảnh (" + root.getPackageName() + ")");
            return;
        }
        lastTrigger = now;

        if (!hasImagePermission()) {
            svc.showStatus("Cần quyền đọc ảnh", 2500);
            return;
        }
        Long minute = readMinute(root);
        busy = true;
        L.i(this, "Watcher: giữ ảnh, phút=" + minute);

        // Chụp màn hình TRƯỚC khi đảo hiện ra để ảnh chụp không dính khay
        takeScreenshot(Display.DEFAULT_DISPLAY, worker, new TakeScreenshotCallback() {
            @Override
            public void onSuccess(ScreenshotResult r) {
                Uri found = null;
                Bitmap thumb = null;
                // chỉ hiện "Đang tìm ảnh…" nếu tìm lâu (thường chỉ mất ~0,1 giây)
                Runnable slow = () -> svc.showStatus("Đang tìm ảnh…", 0);
                main.postDelayed(slow, 350);
                try {
                    Bitmap hw = Bitmap.wrapHardwareBuffer(r.getHardwareBuffer(), r.getColorSpace());
                    Bitmap sw = hw == null ? null : hw.copy(Bitmap.Config.ARGB_8888, false);
                    r.getHardwareBuffer().close();
                    if (sw != null) {
                        Bitmap small = Bitmap.createScaledBitmap(sw,
                                sw.getWidth() / ViewerMatcher.SCALE, sw.getHeight() / ViewerMatcher.SCALE, true);
                        sw.recycle();
                        found = matcher.find(small, minute);
                        small.recycle();
                    }
                    if (found != null) {
                        // ảnh thu nhỏ cho hiệu ứng bay lên đảo
                        try {
                            thumb = getContentResolver().loadThumbnail(found,
                                    new android.util.Size(720, 720), null);
                            if (thumb != null && thumb.getConfig() == Bitmap.Config.HARDWARE) {
                                Bitmap swThumb = thumb.copy(Bitmap.Config.ARGB_8888, false);
                                thumb.recycle();
                                thumb = swThumb;
                            }
                        } catch (Exception ex) {
                            thumb = null;
                        }
                    }
                } catch (RuntimeException ex) {
                    L.i(ViewerWatcher.this, "Watcher: lỗi tìm ảnh: " + ex);
                }
                final Uri result = found;
                final Bitmap fly = thumb;
                main.post(() -> {
                    main.removeCallbacks(slow);
                    busy = false;
                    DropService s = DropService.instance;
                    if (s == null) return;
                    if (result != null) {
                        L.i(ViewerWatcher.this, "Watcher: tìm thấy " + result);
                        s.showViewerShare(result, fly);
                    } else {
                        s.showStatus("Không nhận ra ảnh", 1800);
                    }
                });
            }

            @Override
            public void onFailure(int code) {
                L.i(ViewerWatcher.this, "Watcher: chụp màn hình lỗi " + code);
                main.post(() -> {
                    busy = false;
                    DropService s = DropService.instance;
                    if (s != null) s.showStatus("Không chụp được màn hình", 1800);
                });
            }
        });
    }

    /** Đọc "23 thg 11, 2026" và "19:02" trên thanh trên của màn hình xem ảnh (nếu đang hiện). */
    private Long readMinute(AccessibilityNodeInfo root) {
        String[] dt = new String[2];
        scan(root, dt, 0);
        if (dt[0] == null || dt[1] == null) return null;
        Matcher d = DATE_VI.matcher(dt[0]);
        Matcher t = TIME.matcher(dt[1]);
        if (!d.find() || !t.find()) return null;
        Calendar c = Calendar.getInstance();
        c.clear();
        c.set(Integer.parseInt(d.group(3)), Integer.parseInt(d.group(2)) - 1,
                Integer.parseInt(d.group(1)), Integer.parseInt(t.group(1)), Integer.parseInt(t.group(2)), 0);
        return c.getTimeInMillis();
    }

    private static void scan(AccessibilityNodeInfo n, String[] dt, int depth) {
        if (n == null || depth > 40 || (dt[0] != null && dt[1] != null)) return;
        CharSequence t = n.getText();
        if (t != null) {
            String s = t.toString().trim();
            if (dt[0] == null && DATE_VI.matcher(s).find()) dt[0] = s;
            else if (dt[1] == null && TIME.matcher(s).find()) dt[1] = s;
        }
        for (int i = 0; i < n.getChildCount(); i++) scan(n.getChild(i), dt, depth + 1);
    }

    private boolean hasImagePermission() {
        return checkSelfPermission(Manifest.permission.READ_MEDIA_IMAGES) == PackageManager.PERMISSION_GRANTED;
    }

    @Override
    public void onInterrupt() {
    }

    @Override
    public void onDestroy() {
        main.removeCallbacksAndMessages(null);
        worker.shutdownNow();
        if (matcher != null) matcher.shutdown();
        super.onDestroy();
    }
}
