package vn.islanddrop;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.ClipDescription;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ServiceInfo;
import android.graphics.Bitmap;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.graphics.RectF;
import android.net.Uri;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.view.DragEvent;
import android.view.Gravity;
import android.view.WindowManager;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

/**
 * Giữ một cửa sổ overlay trong suốt ở mép trên màn hình.
 *  - Bình thường: KHÔNG nhận chạm, độ mờ 0.8 và không vẽ gì -> vô hình, không cản thao tác
 *    (Android cho chạm xuyên qua overlay có độ mờ &lt;= 0.8).
 *  - Khi có app bắt đầu kéo ảnh/tệp: bật nhận chạm, khay mọc ra từ camera.
 *  - Kéo xong: khay thu lại, trả chạm cho app bên dưới.
 */
public class DropService extends Service implements IslandView.Host {

    static volatile boolean running = false;
    /** Để dịch vụ Trợ năng (cùng tiến trình) gọi đảo hiện khay. Chỉ dùng trên luồng chính. */
    static volatile DropService instance;

    private static final float IDLE_ALPHA = 0.8f;

    private WindowManager wm;
    private IslandView island;
    private WindowManager.LayoutParams lp;
    private ShareTargets targets;

    @Override
    public void onCreate() {
        super.onCreate();
        running = true;
        startAsForeground();
        wm = getSystemService(WindowManager.class);
        targets = new ShareTargets(this);
        targets.refreshAsync();
        if (!addIsland()) {
            stopSelf();
            return;
        }
        instance = this;
        L.i(this, "Đã bật IslandDrop");
        // Nếu HiOS từng giết app, Trợ năng bị đánh dấu "Crashed" và không tự nối lại.
        new Handler(Looper.getMainLooper()).postDelayed(this::checkAccessibility, 6000);
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        return START_STICKY;
    }

    @Override
    public void onDestroy() {
        running = false;
        if (instance == this) instance = null;
        removeFly();
        if (island != null) {
            try {
                wm.removeViewImmediate(island);
            } catch (RuntimeException ignored) {
                // đã bị gỡ
            }
            island = null;
        }
        L.i(this, "Đã tắt IslandDrop");
        super.onDestroy();
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    private void startAsForeground() {
        NotificationManager nm = getSystemService(NotificationManager.class);
        nm.createNotificationChannel(new NotificationChannel(
                "drop", "Khay kéo thả", NotificationManager.IMPORTANCE_MIN));
        PendingIntent open = PendingIntent.getActivity(this, 0,
                new Intent(this, MainActivity.class), PendingIntent.FLAG_IMMUTABLE);
        Notification n = new Notification.Builder(this, "drop")
                .setSmallIcon(android.R.drawable.ic_menu_share)
                .setContentTitle("IslandDrop đang chạy")
                .setContentText("Giữ ảnh rồi kéo lên đảo để gửi sang app khác")
                .setContentIntent(open)
                .setOngoing(true)
                .build();
        if (Build.VERSION.SDK_INT >= 34) {
            startForeground(1, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE);
        } else {
            startForeground(1, n);
        }
    }

    /** Trợ năng đã bật trong Cài đặt nhưng không chạy (bị hệ thống ngắt) -> nhắc người dùng bật lại. */
    static boolean accessibilityBroken(Context c) {
        String s = android.provider.Settings.Secure.getString(c.getContentResolver(),
                android.provider.Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES);
        boolean enabled = s != null && s.contains(c.getPackageName() + "/");
        return enabled && !ViewerWatcher.connected;
    }

    private static final long A11Y_CHECK_MS = 5 * 60 * 1000;

    private void checkAccessibility() {
        if (instance != this) return;
        Handler h = new Handler(Looper.getMainLooper());
        h.postDelayed(this::checkAccessibility, A11Y_CHECK_MS);   // kiểm tra định kỳ
        if (!accessibilityBroken(this)) return;
        if (tryReEnableAccessibility()) {
            // đợi hệ thống kết nối lại rồi xem đã ổn chưa
            h.postDelayed(() -> {
                if (accessibilityBroken(this)) notifyAccessibilityBroken();
                else L.i(this, "Đã tự bật lại Trợ năng thành công");
            }, 4000);
            return;
        }
        notifyAccessibilityBroken();
    }

    /**
     * Tự bật lại Trợ năng của chính IslandDrop: tắt rồi bật lại mục của mình trong
     * ENABLED_ACCESSIBILITY_SERVICES để hệ thống kết nối lại. Chỉ chạy được khi người dùng đã
     * cấp quyền WRITE_SECURE_SETTINGS qua máy tính (adb). Không đụng tới dịch vụ của app khác.
     */
    private boolean tryReEnableAccessibility() {
        if (checkSelfPermission(android.Manifest.permission.WRITE_SECURE_SETTINGS)
                != android.content.pm.PackageManager.PERMISSION_GRANTED) return false;
        try {
            android.content.ContentResolver cr = getContentResolver();
            String key = android.provider.Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES;
            String mine = new android.content.ComponentName(this, ViewerWatcher.class).flattenToString();
            String cur = android.provider.Settings.Secure.getString(cr, key);
            List<String> others = new ArrayList<>();
            if (cur != null) {
                for (String s : cur.split(":")) {
                    if (!s.isEmpty() && !s.startsWith(getPackageName() + "/")) others.add(s);
                }
            }
            android.provider.Settings.Secure.putString(cr, key, android.text.TextUtils.join(":", others));
            List<String> withMine = new ArrayList<>(others);
            withMine.add(mine);
            new Handler(Looper.getMainLooper()).postDelayed(() -> {
                try {
                    android.provider.Settings.Secure.putString(cr, key, android.text.TextUtils.join(":", withMine));
                    android.provider.Settings.Secure.putString(cr,
                            android.provider.Settings.Secure.ACCESSIBILITY_ENABLED, "1");
                } catch (RuntimeException ex) {
                    L.i(this, "Tự bật lại Trợ năng lỗi: " + ex);
                }
            }, 700);
            L.i(this, "Đang tự bật lại Trợ năng");
            return true;
        } catch (RuntimeException ex) {
            L.i(this, "Không tự bật lại được Trợ năng: " + ex);
            return false;
        }
    }

    private void notifyAccessibilityBroken() {
        L.i(this, "Trợ năng đã bật nhưng bị hệ thống ngắt -> nhắc bật lại");
        NotificationManager nm = getSystemService(NotificationManager.class);
        nm.createNotificationChannel(new NotificationChannel(
                "warn", "Cảnh báo", NotificationManager.IMPORTANCE_DEFAULT));
        PendingIntent open = PendingIntent.getActivity(this, 1,
                new Intent(android.provider.Settings.ACTION_ACCESSIBILITY_SETTINGS)
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                PendingIntent.FLAG_IMMUTABLE);
        Notification n = new Notification.Builder(this, "warn")
                .setSmallIcon(android.R.drawable.stat_sys_warning)
                .setContentTitle("Giữ ảnh / Sao chép đang không chạy")
                .setContentText("Hệ thống đã ngắt Trợ năng của IslandDrop. Bấm để tắt rồi bật lại.")
                .setStyle(new Notification.BigTextStyle().bigText(
                        "Hệ thống đã ngắt Trợ năng của IslandDrop (thường do dọn dẹp đa nhiệm). "
                                + "Bấm vào đây, TẮT rồi BẬT lại \"IslandDrop: giữ ảnh để gửi\"."))
                .setContentIntent(open)
                .setAutoCancel(true)
                .build();
        nm.notify(2, n);
    }

    private boolean addIsland() {
        island = new IslandView(this, this);
        lp = new WindowManager.LayoutParams(
                WindowManager.LayoutParams.MATCH_PARENT,
                island.windowHeight(),
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                        | WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
                        | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN
                        | WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS
                        | WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED,
                PixelFormat.TRANSLUCENT);
        lp.gravity = Gravity.TOP | Gravity.START;
        lp.x = 0;
        lp.y = 0;
        lp.alpha = IDLE_ALPHA;
        lp.layoutInDisplayCutoutMode = Build.VERSION.SDK_INT >= 30
                ? WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS
                : WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES;
        lp.setTitle("IslandDrop");
        try {
            wm.addView(island, lp);
            return true;
        } catch (RuntimeException ex) {
            L.i(this, "Không thêm được overlay: " + ex);
            island = null;
            return false;
        }
    }

    // ---------------------------------------------------------------- IslandView.Host

    @Override
    public List<ShareTargets.Target> targetsFor(ClipDescription d) {
        return targets.get(ShareTargets.keyFor(d));
    }

    @Override
    public void setInteractive(boolean on) {
        if (island == null || lp == null) return;
        if (on) {
            lp.flags &= ~WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE;
            lp.flags |= WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH; // chạm ra ngoài -> thu khay
            lp.alpha = 1f;
        } else {
            lp.flags |= WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE;
            lp.flags &= ~WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH;
            lp.alpha = IDLE_ALPHA;
        }
        try {
            wm.updateViewLayout(island, lp);
        } catch (RuntimeException ex) {
            L.i(this, "updateViewLayout lỗi: " + ex);
        }
    }

    @Override
    public boolean onDropOnTarget(ShareTargets.Target t, DragEvent e) {
        L.i(this, "Thả vào " + t.component.flattenToShortString());
        try {
            // DragEvent bị tái sử dụng sau khi trả về, startActivity đóng gói nó ngay lập tức
            startActivity(new Intent(this, DropActivity.class)
                    .putExtra(DropActivity.EXTRA_EVENT, e)
                    .putExtra(DropActivity.EXTRA_TARGET, t.component)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_NO_ANIMATION));
            ShareTargets.recordUse(this, t.component);
            return true;
        } catch (RuntimeException ex) {
            L.i(this, "Không mở được DropActivity: " + ex);
            return false;
        }
    }

    @Override
    public void onDragFinished() {
        targets.refreshAsync();
    }

    @Override
    public void onAddApps() {
        try {
            startActivity(new Intent(this, AppPickerActivity.class)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
        } catch (RuntimeException ex) {
            L.i(this, "Không mở được màn hình chọn app: " + ex);
        }
    }

    /** Gọi sau khi người dùng đổi ghim/ẩn app. */
    void refreshTargets() {
        targets.invalidate();
    }

    // ---------------------------------------------------------------- giữ ảnh đang xem

    /** Viên thông báo ngắn trên đảo (gọi trên luồng chính). */
    void showStatus(String text, long autoHideMs) {
        if (island != null) island.showStatus(text, autoHideMs);
    }

    private FlyView fly;

    /**
     * Mở khay chạm để gửi ảnh đang xem (gọi trên luồng chính).
     * Nếu có ảnh thu nhỏ: chạy hiệu ứng ảnh bay từ màn hình lên đảo trước, khay giãn ra khi ảnh tới nơi.
     */
    void showViewerShare(Uri image, Bitmap thumb) {
        if (island == null) return;
        Runnable openTray = () -> {
            if (island != null) island.showTap(targets.get("image/*"), image);
        };
        if (thumb == null || thumb.getWidth() == 0 || thumb.getHeight() == 0) {
            openTray.run();
            return;
        }
        removeFly();
        Rect screen = wm.getMaximumWindowMetrics().getBounds();
        float sw = screen.width(), sh = screen.height();
        // ảnh đang hiện "vừa màn hình" (fit-center) trong Thư viện
        float s = Math.min(sw / thumb.getWidth(), sh / thumb.getHeight());
        float w = thumb.getWidth() * s, h = thumb.getHeight() * s;
        RectF from = new RectF((sw - w) / 2f, (sh - h) / 2f, (sw + w) / 2f, (sh + h) / 2f);
        float dp = getResources().getDisplayMetrics().density;
        RectF to = new RectF(sw / 2f - 100 * dp, 4 * dp, sw / 2f + 100 * dp, 38 * dp);

        FlyView v = new FlyView(this, thumb, from, to);
        WindowManager.LayoutParams p = new WindowManager.LayoutParams(
                WindowManager.LayoutParams.MATCH_PARENT, WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                        | WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
                        | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN
                        | WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS
                        | WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED,
                PixelFormat.TRANSLUCENT);
        p.gravity = Gravity.TOP | Gravity.START;
        p.alpha = IDLE_ALPHA;   // <= 0.8: chạm luôn xuyên qua, không làm Thư viện mất thao tác
        p.layoutInDisplayCutoutMode = Build.VERSION.SDK_INT >= 30
                ? WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS
                : WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES;
        p.setTitle("IslandDrop-fly");
        try {
            wm.addView(v, p);
        } catch (RuntimeException ex) {
            L.i(this, "Không chạy được hiệu ứng bay: " + ex);
            openTray.run();
            return;
        }
        fly = v;
        v.start(new FlyView.Listener() {
            @Override
            public void onArriving() {
                openTray.run();
            }

            @Override
            public void onFinished() {
                removeFly();
                thumb.recycle();
            }
        });
    }

    private void removeFly() {
        if (fly == null) return;
        FlyView v = fly;
        fly = null;
        try {
            wm.removeViewImmediate(v);
        } catch (RuntimeException ignored) {
            // đã gỡ
        }
    }

    /** Hiện thẻ "Đã sao chép" để gửi văn bản (gọi trên luồng chính). */
    void showClipText(String text) {
        if (island == null || text == null || text.isEmpty()) return;
        island.showClip(targets.get("text/plain"), text);
    }

    @Override
    public void onTapTarget(ShareTargets.Target t, Uri src, String text) {
        L.i(this, "Chạm gửi tới " + t.component.flattenToShortString());
        ShareTargets.recordUse(this, t.component);
        final Context app = getApplicationContext();
        if (src == null) {
            // văn bản: gửi thẳng, không cần chép tệp
            Sharer.launch(app, t.component, new ArrayList<>(), new ArrayList<>(), text);
            return;
        }
        new Thread(() -> {
            ArrayList<Uri> uris = new ArrayList<>();
            ArrayList<String> mimes = new ArrayList<>();
            try {
                File f = Sharer.copy(app, src, Sharer.prepareDir(app), 0, "image/jpeg");
                uris.add(ShareProvider.uriFor(app, f));
                mimes.add(ShareProvider.mimeOf(f.getName()));
            } catch (Exception ex) {
                L.i(app, "Không chép được ảnh đang xem: " + ex);
            }
            new Handler(Looper.getMainLooper()).post(
                    () -> Sharer.launch(app, t.component, uris, mimes, null));
        }, "IslandDrop-tap").start();
    }
}
