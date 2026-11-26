package vn.islanddrop;

import android.Manifest;
import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Bundle;
import android.os.PowerManager;
import android.provider.Settings;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;

/** Màn hình cài đặt: xin quyền, bật/tắt khay kéo thả. */
public class MainActivity extends Activity {

    static final String PREFS = "settings";
    static final String KEY_ENABLED = "enabled";

    private TextView status;

    static boolean isEnabled(Context c) {
        return c.getSharedPreferences(PREFS, MODE_PRIVATE).getBoolean(KEY_ENABLED, false);
    }

    private void setEnabled(boolean on) {
        setEnabledPref(this, on);
    }

    static void setEnabledPref(Context c, boolean on) {
        SharedPreferences p = c.getSharedPreferences(PREFS, MODE_PRIVATE);
        p.edit().putBoolean(KEY_ENABLED, on).apply();
    }

    static final String KEY_ALWAYS = "always_island";

    /** Chế độ "đảo luôn hiện": viên đen luôn hiện quanh camera, chạm để mở app nhanh. */
    static boolean isAlwaysIsland(Context c) {
        return c.getSharedPreferences(PREFS, MODE_PRIVATE).getBoolean(KEY_ALWAYS, false);
    }

    static void setAlwaysIsland(Context c, boolean on) {
        c.getSharedPreferences(PREFS, MODE_PRIVATE).edit().putBoolean(KEY_ALWAYS, on).apply();
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        int pad = (int) (24 * getResources().getDisplayMetrics().density);
        root.setPadding(pad, pad * 3, pad, pad);

        TextView title = new TextView(this);
        title.setText("IslandDrop");
        title.setTextSize(26);
        root.addView(title);

        TextView help = new TextView(this);
        help.setText("Giữ một ảnh trong Thư viện, kéo lên chỗ camera, rồi thả vào app muốn gửi.\n");
        help.setTextSize(15);
        root.addView(help);

        status = new TextView(this);
        status.setTextSize(16);
        root.addView(status);

        addButton(root, "Cấp quyền hiển thị trên ứng dụng khác", () -> startActivity(
                new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                        Uri.parse("package:" + getPackageName()))));

        addButton(root, "Cho phép thông báo", () -> requestPermissions(new String[]{
                Manifest.permission.POST_NOTIFICATIONS}, 1));

        addButton(root, "Giữ ảnh đang xem để gửi: cho phép đọc ảnh", () -> requestPermissions(
                new String[]{Manifest.permission.READ_MEDIA_IMAGES}, 2));

        addButton(root, "Giữ ảnh đang xem để gửi: bật Trợ năng", () -> startActivity(
                new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)));

        addButton(root, "Cho chạy nền (không bị tắt khi tiết kiệm pin)", () -> {
            try {
                startActivity(new Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS,
                        Uri.parse("package:" + getPackageName())));
            } catch (RuntimeException ex) {
                startActivity(new Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS));
            }
        });

        addButton(root, "BẬT khay kéo thả", () -> {
            if (!Settings.canDrawOverlays(this)) {
                status.setText("Chưa có quyền hiển thị trên ứng dụng khác.");
                return;
            }
            setEnabled(true);
            startForegroundService(new Intent(this, DropService.class));
            status.postDelayed(this::refresh, 400);
        });

        addButton(root, "TẮT khay kéo thả", () -> {
            setEnabled(false);
            stopService(new Intent(this, DropService.class));
            status.postDelayed(this::refresh, 400);
        });

        addButton(root, "Đảo luôn hiện: bật / tắt", () -> {
            setAlwaysIsland(this, !isAlwaysIsland(this));
            if (DropService.instance != null) DropService.instance.applyAlwaysIsland();
            refresh();
        });

        addButton(root, "Nhạc trên đảo: cho phép đọc nhạc đang phát", () -> startActivity(
                new Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)));

        addButton(root, "Chọn app mở nhanh trên đảo", () -> startActivity(
                new Intent(this, AppPickerActivity.class).putExtra(AppPickerActivity.EXTRA_LAUNCHER, true)));

        addButton(root, "Thêm nút \"Trợ năng đảo\" vào Cài đặt nhanh", () ->
                requestTile(A11yTile.class, "Trợ năng đảo"));

        addButton(root, "Thêm nút \"Đảo luôn hiện\" vào Cài đặt nhanh", () ->
                requestTile(IslandTile.class, "Đảo luôn hiện"));

        android.widget.ScrollView scroll = new android.widget.ScrollView(this);
        scroll.addView(root);
        setContentView(scroll);

        // Bật nhanh từ máy tính: adb shell am start -n vn.islanddrop/.MainActivity --ez start true
        if (getIntent().getBooleanExtra("start", false) && Settings.canDrawOverlays(this)) {
            setEnabled(true);
            startForegroundService(new Intent(this, DropService.class));
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        refresh();
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        refresh();
    }

    private void refresh() {
        boolean overlay = Settings.canDrawOverlays(this);
        boolean notif = checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)
                == PackageManager.PERMISSION_GRANTED;
        boolean battery = getSystemService(PowerManager.class)
                .isIgnoringBatteryOptimizations(getPackageName());
        boolean images = checkSelfPermission(Manifest.permission.READ_MEDIA_IMAGES)
                == PackageManager.PERMISSION_GRANTED;
        String enabledA11y = Settings.Secure.getString(getContentResolver(),
                Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES);
        boolean a11y = enabledA11y != null && enabledA11y.contains(getPackageName() + "/");
        status.setText("Hiển thị trên app khác: " + (overlay ? "có" : "CHƯA")
                + "\nĐọc ảnh (giữ ảnh đang xem): " + (images ? "có" : "chưa")
                + "\nTrợ năng (giữ ảnh đang xem): " + (!a11y ? "tắt"
                        : ViewerWatcher.connected ? "đang chạy"
                        : "BỊ HỆ THỐNG NGẮT — bấm \"bật Trợ năng\", tắt rồi bật lại")
                + "\nThông báo: " + (notif ? "có" : "chưa (không bắt buộc)")
                + "\nChạy nền không giới hạn: " + (battery ? "có" : "chưa (nên bật)")
                + "\nKhay kéo thả: " + (DropService.running ? "ĐANG BẬT" : "tắt")
                + "\nĐảo luôn hiện: " + (isAlwaysIsland(this) ? "bật" : "tắt")
                + "\nNhạc trên đảo: " + (hasMediaAccess() ? "có" : "chưa cho phép") + "\n");
    }

    private boolean hasMediaAccess() {
        String s = Settings.Secure.getString(getContentResolver(), "enabled_notification_listeners");
        return s != null && s.contains(getPackageName() + "/");
    }

    /** Hỏi hệ thống thêm ô vào thanh Cài đặt nhanh (Android 13+); máy cũ thì tự thêm bằng tay. */
    private void requestTile(Class<?> tile, String label) {
        if (android.os.Build.VERSION.SDK_INT < 33) {
            status.setText("Kéo thanh thông báo xuống → bút chì (sửa) → kéo ô \"" + label + "\" lên.");
            return;
        }
        android.app.StatusBarManager sbm = getSystemService(android.app.StatusBarManager.class);
        sbm.requestAddTileService(new android.content.ComponentName(this, tile), label,
                android.graphics.drawable.Icon.createWithResource(this,
                        tile == A11yTile.class ? R.drawable.ic_tile_a11y : R.drawable.ic_tile_island),
                getMainExecutor(), r -> L.i(this, "Thêm ô " + label + ": kết quả " + r));
    }

    private void addButton(LinearLayout root, String text, Runnable action) {
        Button b = new Button(this);
        b.setText(text);
        b.setAllCaps(false);
        b.setOnClickListener(v -> action.run());
        root.addView(b, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
    }
}
