package vn.islanddrop;

import android.Manifest;
import android.content.ComponentName;
import android.content.ContentResolver;
import android.content.Context;
import android.content.pm.PackageManager;
import android.provider.Settings;
import android.text.TextUtils;

import java.util.ArrayList;
import java.util.List;

/**
 * Bật/tắt dịch vụ Trợ năng của CHÍNH IslandDrop (cần quyền WRITE_SECURE_SETTINGS cấp qua adb).
 * Không đụng tới dịch vụ Trợ năng của app khác.
 */
final class A11yToggle {

    private A11yToggle() {}

    static boolean canToggle(Context c) {
        return c.checkSelfPermission(Manifest.permission.WRITE_SECURE_SETTINGS)
                == PackageManager.PERMISSION_GRANTED;
    }

    /** Đã bật trong Cài đặt (có thể đang bị hệ thống ngắt). */
    static boolean isEnabled(Context c) {
        String s = Settings.Secure.getString(c.getContentResolver(),
                Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES);
        return s != null && s.contains(c.getPackageName() + "/");
    }

    /** @return false nếu chưa có quyền hoặc ghi lỗi */
    static boolean setEnabled(Context c, boolean on) {
        if (!canToggle(c)) return false;
        try {
            ContentResolver cr = c.getContentResolver();
            String key = Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES;
            String cur = Settings.Secure.getString(cr, key);
            List<String> list = new ArrayList<>();
            if (cur != null) {
                for (String s : cur.split(":")) {
                    if (!s.isEmpty() && !s.startsWith(c.getPackageName() + "/")) list.add(s);
                }
            }
            if (on) list.add(new ComponentName(c, ViewerWatcher.class).flattenToString());
            Settings.Secure.putString(cr, key, TextUtils.join(":", list));
            if (on) Settings.Secure.putString(cr, Settings.Secure.ACCESSIBILITY_ENABLED, "1");
            L.i(c, "Trợ năng IslandDrop -> " + (on ? "BẬT" : "TẮT"));
            return true;
        } catch (RuntimeException ex) {
            L.i(c, "Không đổi được Trợ năng: " + ex);
            return false;
        }
    }
}
