package vn.islanddrop;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.graphics.drawable.Drawable;
import android.text.TextUtils;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** App mở nhanh trên đảo (chế độ "đảo luôn hiện"). Lưu theo tên gói, theo thứ tự người dùng chọn. */
final class QuickApps {

    private static final String PREFS = "picker";
    private static final String KEY = "quick";
    private static final int DEFAULT_COUNT = 6;

    /** Gợi ý mặc định khi người dùng chưa chọn (chỉ lấy app đã cài). */
    private static final List<String> DEFAULTS = Arrays.asList(
            "com.zing.zalo", "com.facebook.orca", "com.facebook.katana", "com.android.chrome",
            "com.google.android.youtube", "com.ss.android.ugc.trill", "com.google.android.gm",
            "com.instagram.android");

    private QuickApps() {}

    static List<String> getPackages(Context c) {
        SharedPreferences p = c.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        if (p.contains(KEY)) {
            List<String> out = new ArrayList<>();
            for (String s : p.getString(KEY, "").split("\n")) if (!s.isEmpty()) out.add(s);
            return out;
        }
        PackageManager pm = c.getPackageManager();
        List<String> out = new ArrayList<>();
        for (String pkg : DEFAULTS) {
            if (out.size() >= DEFAULT_COUNT) break;
            if (pm.getLaunchIntentForPackage(pkg) != null) out.add(pkg);
        }
        return out;
    }

    static void setPackages(Context c, List<String> pkgs) {
        c.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
                .putString(KEY, TextUtils.join("\n", pkgs)).apply();
    }

    /** Danh sách để vẽ trên khay. Bỏ qua app đã gỡ. */
    static List<ShareTargets.Target> targets(Context c) {
        PackageManager pm = c.getPackageManager();
        List<ShareTargets.Target> out = new ArrayList<>();
        for (String pkg : getPackages(c)) {
            Intent li = pm.getLaunchIntentForPackage(pkg);
            if (li == null || li.getComponent() == null) continue;
            try {
                ApplicationInfo ai = pm.getApplicationInfo(pkg, 0);
                Drawable icon = pm.getApplicationIcon(ai).mutate();
                out.add(new ShareTargets.Target(li.getComponent(), pm.getApplicationLabel(ai), icon));
            } catch (PackageManager.NameNotFoundException ignored) {
                // vừa bị gỡ
            }
        }
        return out;
    }

    /** Mọi app mở được từ màn hình chính (cho màn hình chọn app). Chạy ở luồng nền. */
    static List<ResolveInfo> allLaunchable(Context c) {
        PackageManager pm = c.getPackageManager();
        Intent main = new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER);
        Map<String, ResolveInfo> seen = new LinkedHashMap<>();
        for (ResolveInfo ri : pm.queryIntentActivities(main, 0)) {
            if (ri.activityInfo == null || c.getPackageName().equals(ri.activityInfo.packageName)) continue;
            seen.putIfAbsent(ri.activityInfo.packageName, ri);
        }
        return new ArrayList<>(seen.values());
    }

    static void launch(Context c, ComponentName cn) {
        Intent li = c.getPackageManager().getLaunchIntentForPackage(cn.getPackageName());
        if (li == null) return;
        li.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED);
        try {
            c.startActivity(li);
        } catch (RuntimeException ex) {
            L.i(c, "Không mở được " + cn.getPackageName() + ": " + ex);
        }
    }
}
