package vn.islanddrop;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.ClipDescription;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.SystemClock;
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
import android.view.View;
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
        applyAlwaysIsland();
        // Nếu HiOS từng giết app, Trợ năng bị đánh dấu "Crashed" và không tự nối lại.
        new Handler(Looper.getMainLooper()).postDelayed(this::checkAccessibility, 6000);
        resumeIfPauseOver();
    }

    static final String ACTION_PAUSE_A11Y = "vn.islanddrop.PAUSE_A11Y";
    static final String ACTION_RESUME_A11Y = "vn.islanddrop.RESUME_A11Y";

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        String a = intent == null ? null : intent.getAction();
        if (ACTION_PAUSE_A11Y.equals(a)) pauseAccessibility();
        else if (ACTION_RESUME_A11Y.equals(a)) resumeAccessibility();
        return START_STICKY;
    }

    // ---------------------------------------------------------------- tạm tắt Trợ năng (app ngân hàng)

    private final Runnable resumeA11y = this::resumeAccessibility;

    /** Tắt Trợ năng 5 phút rồi tự bật lại (để đăng nhập app ngân hàng đòi tắt Trợ năng). */
    void pauseAccessibility() {
        if (!A11yToggle.setEnabled(this, false)) {
            // chưa có quyền tự bật/tắt: mở trang Trợ năng cho người dùng tự tắt
            try {
                startActivity(new Intent(android.provider.Settings.ACTION_ACCESSIBILITY_SETTINGS)
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
            } catch (RuntimeException ignored) {
                // không mở được
            }
            return;
        }
        long at = System.currentTimeMillis() + A11yToggle.PAUSE_MS;
        A11yToggle.setPausedUntil(this, at);
        main.removeCallbacks(resumeA11y);
        main.postDelayed(resumeA11y, A11yToggle.PAUSE_MS);
        android.widget.Toast.makeText(this, "Đã tắt Trợ năng — tự bật lại lúc " + A11yToggle.clock(at),
                android.widget.Toast.LENGTH_LONG).show();
        L.i(this, "Tạm tắt Trợ năng tới " + A11yToggle.clock(at));
        updateNotification();
    }

    void resumeAccessibility() {
        main.removeCallbacks(resumeA11y);
        A11yToggle.setPausedUntil(this, 0);
        A11yToggle.setEnabled(this, true);
        L.i(this, "Bật lại Trợ năng");
        updateNotification();
    }

    /** Sau khi app khởi động lại (máy khởi động lại / bị HiOS giết): hết giờ tạm tắt thì bật lại. */
    private void resumeIfPauseOver() {
        long at = A11yToggle.pausedUntil(this);
        if (at <= 0) return;
        long left = at - System.currentTimeMillis();
        if (left <= 0) resumeAccessibility();
        else main.postDelayed(resumeA11y, left);
    }

    @Override
    public void onDestroy() {
        running = false;
        if (instance == this) instance = null;
        removeFly();
        removePill();
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

    private static final String CHANNEL = "drop2";

    private void startAsForeground() {
        NotificationManager nm = getSystemService(NotificationManager.class);
        nm.deleteNotificationChannel("drop");   // kênh cũ (mức MIN, bị gập nên khó thấy nút)
        nm.createNotificationChannel(new NotificationChannel(
                CHANNEL, "IslandDrop đang chạy", NotificationManager.IMPORTANCE_LOW));
        Notification n = buildNotification();
        if (Build.VERSION.SDK_INT >= 34) {
            startForeground(1, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE);
        } else {
            startForeground(1, n);
        }
    }

    /** Thông báo thường trực, có nút tắt Trợ năng 5 phút / bật lại ngay. */
    private Notification buildNotification() {
        PendingIntent open = PendingIntent.getActivity(this, 0,
                new Intent(this, MainActivity.class), PendingIntent.FLAG_IMMUTABLE);
        Notification.Builder b = new Notification.Builder(this, CHANNEL)
                .setSmallIcon(android.R.drawable.ic_menu_share)
                .setContentTitle("IslandDrop đang chạy")
                .setContentIntent(open)
                .setOngoing(true);
        long at = A11yToggle.pausedUntil(this);
        if (at > System.currentTimeMillis()) {
            b.setContentText("Trợ năng đang tạm tắt — tự bật lại lúc " + A11yToggle.clock(at));
            b.addAction(action("Bật lại ngay", ACTION_RESUME_A11Y, 11));
        } else if (A11yToggle.isEnabled(this)) {
            b.setContentText("Vào app ngân hàng? Bấm \"Tắt Trợ năng 5 phút\"");
            b.addAction(action("Tắt Trợ năng 5 phút", ACTION_PAUSE_A11Y, 10));
        } else {
            b.setContentText("Trợ năng đang tắt (giữ ảnh / thẻ Sao chép tạm nghỉ)");
            b.addAction(action("Bật Trợ năng", ACTION_RESUME_A11Y, 11));
        }
        return b.build();
    }

    private Notification.Action action(String title, String act, int req) {
        PendingIntent pi = PendingIntent.getService(this, req,
                new Intent(this, DropService.class).setAction(act), PendingIntent.FLAG_IMMUTABLE);
        return new Notification.Action.Builder(null, title, pi).build();
    }

    void updateNotification() {
        try {
            getSystemService(NotificationManager.class).notify(1, buildNotification());
        } catch (RuntimeException ignored) {
            // không cập nhật được
        }
    }

    /** Trợ năng đã bật trong Cài đặt nhưng không chạy (bị hệ thống ngắt) -> nhắc người dùng bật lại. */
    static boolean accessibilityBroken(Context c) {
        // tắt bằng nút nhanh => không có trong Cài đặt => không tự bật lại (không "cãi" người dùng)
        return A11yToggle.isEnabled(c) && !ViewerWatcher.connected;
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
        if (!A11yToggle.setEnabled(this, false)) return false;
        new Handler(Looper.getMainLooper()).postDelayed(() -> A11yToggle.setEnabled(this, true), 700);
        L.i(this, "Đang tự bật lại Trợ năng");
        return true;
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
    public void onAddApps(boolean launcher) {
        try {
            startActivity(new Intent(this, AppPickerActivity.class)
                    .putExtra(AppPickerActivity.EXTRA_LAUNCHER, launcher)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
        } catch (RuntimeException ex) {
            L.i(this, "Không mở được màn hình chọn app: " + ex);
        }
    }

    // ---------------------------------------------------------------- đảo luôn hiện (kiểu Atoll)

    private final Handler main = new Handler(Looper.getMainLooper());
    private PillView pill;
    private WindowManager.LayoutParams pillLp;
    /** WindowManager + loại cửa sổ đang giữ viên đảo và bảng (Trợ năng hoặc lớp phủ thường). */
    private WindowManager pillWm;
    private int pillType;
    private PanelView panel;
    private long timerEnd;              // SystemClock.elapsedRealtime(); 0 = không hẹn giờ
    private long chargeUntil, doneUntil;
    private BroadcastReceiver powerReceiver;
    private static final long PANEL_IDLE_MS = 12_000;

    /** Trợ năng vừa kết nối / ngắt: dựng lại viên đảo bằng loại cửa sổ phù hợp. */
    void reattachPill() {
        main.post(() -> {
            if (instance != this) return;
            removePill();
            applyAlwaysIsland();
            updateNotification();   // nút trong thông báo theo trạng thái Trợ năng mới
        });
    }

    /** Nhạc đổi bài / phát / dừng (MediaListener gọi trên luồng chính). */
    void onMediaChanged() {
        updatePill();
        if (panel != null) panel.refresh();
    }

    /** Chọn nội dung viên đảo theo ưu tiên: sạc > hết giờ > hẹn giờ > nhạc > rảnh. */
    private void updatePill() {
        if (pill == null) return;
        long now = SystemClock.elapsedRealtime();
        MediaState m = MediaListener.current;
        int kind;
        String label = null;
        Bitmap art = null;
        boolean playing = false;
        if (now < chargeUntil) {
            kind = PillView.K_CHARGE;
            label = batteryPercent() + "%";
        } else if (now < doneUntil) {
            kind = PillView.K_TIMER;
            label = "Hết giờ";
        } else if (timerEnd > 0) {
            kind = PillView.K_TIMER;
            label = PanelView.fmt(timerEnd - now);
        } else if (m != null) {
            kind = PillView.K_MEDIA;
            art = m.art;
            playing = m.playing;
        } else {
            kind = PillView.K_IDLE;
        }
        // giãn cửa sổ trước để lò xo có chỗ chạy; thu lại khi lò xo đã dừng (onWidthSettled)
        if (kind != PillView.K_IDLE) setPillWindowWidth(PillView.maxWidthPx(this));
        pill.setState(kind, art, playing, label);
    }

    private void setPillWindowWidth(int px) {
        if (pill == null || pillLp == null || pillLp.width == px) return;
        pillLp.width = px;
        try {
            pillWm.updateViewLayout(pill, pillLp);
        } catch (RuntimeException ex) {
            L.i(this, "Đổi cỡ viên đảo lỗi: " + ex);
        }
    }

    private final PillView.Listener pillListener = new PillView.Listener() {
        @Override
        public void onTap() {
            pill.performHapticFeedback(android.view.HapticFeedbackConstants.CONTEXT_CLICK);
            if (panel != null) closePanel();
            else openPanel();
        }

        @Override
        public void onSwipeDown() {
            if (panel == null) {
                pill.performHapticFeedback(android.view.HapticFeedbackConstants.CONTEXT_CLICK);
                openPanel();
            }
        }

        @Override
        public void onSwipeHorizontal(int dir) {
            if (MediaListener.current == null) return;
            pill.performHapticFeedback(android.view.HapticFeedbackConstants.CLOCK_TICK);
            panelHost.mediaSkip(dir);
        }

        @Override
        public void onWidthSettled(int px) {
            if (pill != null && pill.kind() == PillView.K_IDLE) setPillWindowWidth(PillView.idleWidthPx(DropService.this));
        }
    };

    // ---- bảng mở rộng

    private final Runnable panelRefresh = new Runnable() {
        @Override
        public void run() {
            if (panel == null) return;
            panel.refresh();
            main.postDelayed(this, 500);
        }
    };
    private final Runnable panelIdle = this::closePanel;

    private void openPanel() {
        if (panel != null || pillWm == null || pill == null) return;
        int start = MediaListener.current != null ? PanelView.TAB_MEDIA
                : timerEnd > 0 ? PanelView.TAB_TIMER : PanelView.TAB_APPS;
        PanelView v = new PanelView(this, panelHost, start);
        float dp = getResources().getDisplayMetrics().density;
        Rect screen = wm.getMaximumWindowMetrics().getBounds();
        WindowManager.LayoutParams p = new WindowManager.LayoutParams(
                screen.width() - (int) (20 * dp), WindowManager.LayoutParams.WRAP_CONTENT,
                pillType,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                        | WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH
                        | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN
                        | WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
                PixelFormat.TRANSLUCENT);
        p.gravity = Gravity.TOP | Gravity.CENTER_HORIZONTAL;
        p.y = (int) (4 * dp);
        p.layoutInDisplayCutoutMode = Build.VERSION.SDK_INT >= 30
                ? WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS
                : WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES;
        p.setTitle("IslandDrop-panel");
        try {
            pillWm.addView(v, p);
        } catch (RuntimeException ex) {
            L.i(this, "Không mở được bảng đảo: " + ex);
            return;
        }
        panel = v;
        v.animateOpen(pill.getWidth(), pill.getHeight());
        main.removeCallbacks(panelRefresh);
        main.postDelayed(panelRefresh, 500);
        resetPanelIdle();
    }

    private void resetPanelIdle() {
        main.removeCallbacks(panelIdle);
        main.postDelayed(panelIdle, PANEL_IDLE_MS);
    }

    private void closePanel() {
        if (panel == null) return;
        PanelView v = panel;
        panel = null;
        main.removeCallbacks(panelRefresh);
        main.removeCallbacks(panelIdle);
        WindowManager w = pillWm;
        v.animateClose(pill != null ? pill.getWidth() : 300, pill != null ? pill.getHeight() : 90, () -> {
            try {
                if (w != null) w.removeViewImmediate(v);
            } catch (RuntimeException ignored) {
                // đã gỡ
            }
        });
    }

    private void closePanelNow() {
        if (panel == null) return;
        main.removeCallbacks(panelRefresh);
        main.removeCallbacks(panelIdle);
        try {
            if (pillWm != null) pillWm.removeViewImmediate(panel);
        } catch (RuntimeException ignored) {
            // đã gỡ
        }
        panel = null;
    }

    private final PanelView.Host panelHost = new PanelView.Host() {
        @Override
        public MediaState media() {
            return MediaListener.current;
        }

        @Override
        public void mediaPlayPause() {
            MediaState m = MediaListener.current;
            if (m == null) return;
            try {
                if (m.playing) m.controller.getTransportControls().pause();
                else m.controller.getTransportControls().play();
            } catch (RuntimeException ex) {
                L.i(DropService.this, "Điều khiển nhạc lỗi: " + ex);
            }
        }

        @Override
        public void mediaSkip(int dir) {
            MediaState m = MediaListener.current;
            if (m == null) return;
            try {
                if (dir > 0) m.controller.getTransportControls().skipToNext();
                else m.controller.getTransportControls().skipToPrevious();
            } catch (RuntimeException ex) {
                L.i(DropService.this, "Chuyển bài lỗi: " + ex);
            }
        }

        @Override
        public void mediaSeek(long ms) {
            MediaState m = MediaListener.current;
            if (m == null) return;
            try {
                m.controller.getTransportControls().seekTo(ms);
            } catch (RuntimeException ex) {
                L.i(DropService.this, "Tua nhạc lỗi: " + ex);
            }
        }

        @Override
        public boolean hasMediaAccess() {
            String s = android.provider.Settings.Secure.getString(getContentResolver(),
                    "enabled_notification_listeners");
            return s != null && s.contains(getPackageName() + "/");
        }

        @Override
        public void openMediaAccessSettings() {
            closePanel();
            try {
                startActivity(new Intent(android.provider.Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
            } catch (RuntimeException ex) {
                L.i(DropService.this, "Không mở được cài đặt Truy cập thông báo: " + ex);
            }
        }

        @Override
        public List<ShareTargets.Target> quickApps() {
            return QuickApps.targets(DropService.this);
        }

        @Override
        public void launch(ShareTargets.Target t) {
            closePanel();
            QuickApps.launch(DropService.this, t.component);
        }

        @Override
        public void addQuickApps() {
            closePanel();
            onAddApps(true);
        }

        @Override
        public long timerRemaining() {
            return timerEnd > 0 ? Math.max(0, timerEnd - SystemClock.elapsedRealtime()) : 0;
        }

        @Override
        public void startTimer(long ms) {
            timerEnd = SystemClock.elapsedRealtime() + ms;
            doneUntil = 0;
            main.removeCallbacks(timerTick);
            main.post(timerTick);
            if (panel != null) panel.refresh();
            L.i(DropService.this, "Hẹn giờ " + ms / 60000 + " phút");
        }

        @Override
        public void addTimer(long ms) {
            if (timerEnd > 0) timerEnd += ms;
            updatePill();
            if (panel != null) panel.refresh();
        }

        @Override
        public void cancelTimer() {
            timerEnd = 0;
            main.removeCallbacks(timerTick);
            updatePill();
            if (panel != null) panel.refresh();
        }

        @Override
        public void closePanel() {
            DropService.this.closePanel();
        }

        @Override
        public void touched() {
            resetPanelIdle();
        }

        @Override
        public void pauseAccessibility() {
            closePanel();
            DropService.this.pauseAccessibility();
        }
    };

    // ---- hẹn giờ

    private final Runnable timerTick = new Runnable() {
        @Override
        public void run() {
            if (timerEnd == 0) return;
            long rem = timerEnd - SystemClock.elapsedRealtime();
            if (rem <= 0) {
                timerEnd = 0;
                timerDone();
                return;
            }
            updatePill();
            main.postDelayed(this, Math.max(50, rem % 1000 + 5));   // khớp nhịp từng giây
        }
    };

    private void timerDone() {
        L.i(this, "Hết giờ");
        doneUntil = SystemClock.elapsedRealtime() + 6000;
        updatePill();
        main.postDelayed(this::updatePill, 6100);
        if (panel != null) panel.refresh();
        try {
            android.os.Vibrator vib = getSystemService(android.os.Vibrator.class);
            if (vib != null) {
                vib.vibrate(android.os.VibrationEffect.createWaveform(new long[]{0, 350, 180, 350, 180, 600}, -1));
            }
        } catch (RuntimeException ignored) {
            // máy không rung
        }
        NotificationManager nm = getSystemService(NotificationManager.class);
        nm.createNotificationChannel(new NotificationChannel(
                "timer", "Hẹn giờ trên đảo", NotificationManager.IMPORTANCE_HIGH));
        nm.notify(3, new Notification.Builder(this, "timer")
                .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
                .setContentTitle("Hết giờ!")
                .setContentText("Hẹn giờ trên đảo đã kết thúc")
                .setAutoCancel(true)
                .build());
    }

    // ---- sạc pin

    private int batteryPercent() {
        android.os.BatteryManager bm = getSystemService(android.os.BatteryManager.class);
        return bm == null ? 0 : bm.getIntProperty(android.os.BatteryManager.BATTERY_PROPERTY_CAPACITY);
    }

    private void registerPower() {
        if (powerReceiver != null) return;
        powerReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context c, Intent i) {
                if (!Intent.ACTION_POWER_CONNECTED.equals(i.getAction())) return;
                chargeUntil = SystemClock.elapsedRealtime() + 3500;
                updatePill();
                main.postDelayed(DropService.this::updatePill, 3600);
            }
        };
        IntentFilter f = new IntentFilter(Intent.ACTION_POWER_CONNECTED);
        if (Build.VERSION.SDK_INT >= 33) registerReceiver(powerReceiver, f, Context.RECEIVER_NOT_EXPORTED);
        else registerReceiver(powerReceiver, f);
    }

    private void unregisterPower() {
        if (powerReceiver == null) return;
        try {
            unregisterReceiver(powerReceiver);
        } catch (RuntimeException ignored) {
            // chưa đăng ký
        }
        powerReceiver = null;
    }

    /** Bật/tắt viên đảo luôn hiện theo cài đặt (ẩn khi xoay ngang để không che phim/game). */
    void applyAlwaysIsland() {
        boolean portrait = getResources().getConfiguration().orientation
                != android.content.res.Configuration.ORIENTATION_LANDSCAPE;
        boolean want = MainActivity.isAlwaysIsland(this) && portrait;
        if (want && pill == null) addPill();
        else if (!want && pill != null) removePill();
    }

    @Override
    public void onConfigurationChanged(android.content.res.Configuration c) {
        super.onConfigurationChanged(c);
        applyAlwaysIsland();
    }

    private void addPill() {
        float dp = getResources().getDisplayMetrics().density;
        PillView v = new PillView(this, pillListener);
        // Có Trợ năng: dùng lớp phủ Trợ năng, nằm TRÊN thanh trạng thái nên chạm vào camera luôn trúng.
        // Không có: lớp phủ thường (nằm dưới thanh trạng thái HiOS, chạm có thể bị thanh trạng thái nhận).
        ViewerWatcher a11y = ViewerWatcher.instance;
        WindowManager targetWm = a11y != null ? a11y.getSystemService(WindowManager.class) : wm;
        int type = a11y != null ? WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY
                : WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY;
        WindowManager.LayoutParams p = new WindowManager.LayoutParams(
                PillView.idleWidthPx(this), PillView.heightPx(this),
                type,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                        | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN
                        | WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
                PixelFormat.TRANSLUCENT);
        p.gravity = Gravity.TOP | Gravity.CENTER_HORIZONTAL;
        p.y = (int) (5 * dp);
        p.layoutInDisplayCutoutMode = Build.VERSION.SDK_INT >= 30
                ? WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS
                : WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES;
        p.setTitle("IslandDrop-pill");
        try {
            targetWm.addView(v, p);
            pill = v;
            pillLp = p;
            pillWm = targetWm;
            pillType = type;
            registerPower();
            updatePill();
            L.i(this, "Đảo luôn hiện: bật (" + (a11y != null ? "lớp phủ Trợ năng" : "lớp phủ thường") + ")");
        } catch (RuntimeException ex) {
            L.i(this, "Không hiện được đảo luôn hiện: " + ex);
        }
    }

    private void removePill() {
        if (pill == null) return;
        closePanelNow();
        unregisterPower();
        try {
            pillWm.removeViewImmediate(pill);
        } catch (RuntimeException ignored) {
            // đã gỡ (vd dịch vụ Trợ năng đã chết kéo theo cửa sổ)
        }
        pill = null;
        pillLp = null;
        pillWm = null;
        L.i(this, "Đảo luôn hiện: tắt");
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
