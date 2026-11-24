package vn.islanddrop;

import android.content.ClipDescription;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.graphics.drawable.Drawable;
import android.text.TextUtils;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Danh sách app nhận chia sẻ (ACTION_SEND), có bộ nhớ đệm theo loại nội dung để khi bắt đầu
 * kéo, khay mở ra ngay mà không phải đợi PackageManager.
 * Thứ tự: app dùng nhiều nhất trước, sau đó các app nhắn tin quen thuộc, rồi theo tên.
 */
final class ShareTargets {

    static final class Target {
        final ComponentName component;
        final CharSequence label;
        final Drawable icon;

        Target(ComponentName component, CharSequence label, Drawable icon) {
            this.component = component;
            this.label = label;
            this.icon = icon;
        }
    }

    private static final int MAX_TARGETS = 24;
    private static final String PREFS = "usage";
    private static final String PICKER = "picker";

    /** Ô "+ Thêm app" luôn nằm cuối khay (component = null). */
    static final Target ADD = new Target(null, "Thêm app", null);

    // ---------------------------------------------------------------- ghim / ẩn (do người dùng chọn)

    static List<String> getPins(Context ctx) {
        String s = ctx.getSharedPreferences(PICKER, Context.MODE_PRIVATE).getString("pins", "");
        List<String> out = new ArrayList<>();
        for (String p : s.split("\n")) if (!p.isEmpty()) out.add(p);
        return out;
    }

    static void setPins(Context ctx, List<String> pins) {
        ctx.getSharedPreferences(PICKER, Context.MODE_PRIVATE).edit()
                .putString("pins", TextUtils.join("\n", pins)).apply();
    }

    static Set<String> getHidden(Context ctx) {
        return new HashSet<>(ctx.getSharedPreferences(PICKER, Context.MODE_PRIVATE)
                .getStringSet("hidden", Collections.emptySet()));
    }

    static void setHidden(Context ctx, Set<String> hidden) {
        ctx.getSharedPreferences(PICKER, Context.MODE_PRIVATE).edit()
                .putStringSet("hidden", new HashSet<>(hidden)).apply();
    }

    /** App ưu tiên khi chưa có lịch sử dùng (càng đầu danh sách càng ưu tiên). */
    private static final List<String> PREFERRED = Arrays.asList(
            "com.zing.zalo",
            "com.facebook.orca",
            "com.facebook.katana",
            "org.telegram.messenger",
            "com.whatsapp",
            "com.google.android.gm",
            "com.google.android.apps.docs",
            "com.ss.android.ugc.trill",
            "com.zhiliaoapp.musically",
            "com.instagram.android",
            "com.google.android.apps.photos");

    private final Context app;
    private final Map<String, List<Target>> cache = new ConcurrentHashMap<>();

    ShareTargets(Context ctx) {
        this.app = ctx.getApplicationContext();
    }

    /** Khóa bộ nhớ đệm theo nhóm nội dung: ảnh, video, hoặc tệp bất kỳ. */
    static String keyFor(ClipDescription d) {
        boolean allImage = d != null && d.getMimeTypeCount() > 0;
        boolean allVideo = allImage, allText = allImage;
        if (d != null) {
            for (int i = 0; i < d.getMimeTypeCount(); i++) {
                String m = d.getMimeType(i);
                allImage &= m.startsWith("image/");
                allVideo &= m.startsWith("video/");
                allText &= m.startsWith("text/");
            }
        }
        if (allImage) return "image/*";
        if (allVideo) return "video/*";
        if (allText) return "text/plain";
        return "*/*";
    }

    /** Lấy danh sách cho loại nội dung; nếu chưa có trong bộ đệm thì nạp ngay (chỉ lần đầu). */
    List<Target> get(String key) {
        List<Target> t = cache.get(key);
        if (t == null) {
            t = load(key);
            cache.put(key, t);
        }
        return t;
    }

    /** Danh sách mọi app nhận chia sẻ (ảnh, chữ, video, tệp), cho màn hình chọn app. Chạy ở luồng nền. */
    static List<ResolveInfo> allCandidates(Context ctx) {
        PackageManager pm = ctx.getPackageManager();
        Map<String, ResolveInfo> seen = new java.util.LinkedHashMap<>();
        for (String mime : new String[]{"image/*", "text/plain", "video/*", "*/*"}) {
            Intent send = new Intent(Intent.ACTION_SEND).setType(mime);
            for (ResolveInfo ri : pm.queryIntentActivities(send, PackageManager.MATCH_DEFAULT_ONLY)) {
                if (ri.activityInfo == null || !ri.activityInfo.exported) continue;
                if (ctx.getPackageName().equals(ri.activityInfo.packageName)) continue;
                seen.putIfAbsent(cn(ri).flattenToShortString(), ri);
            }
        }
        return new ArrayList<>(seen.values());
    }

    static String key(ResolveInfo ri) {
        return cn(ri).flattenToShortString();
    }

    /** Xóa bộ đệm và nạp lại (sau khi người dùng đổi ghim/ẩn). */
    void invalidate() {
        cache.clear();
        refreshAsync();
    }

    /** Nạp lại bộ đệm ở luồng nền (sau mỗi lần kéo, để cập nhật app mới cài và thứ tự dùng). */
    void refreshAsync() {
        Thread th = new Thread(() -> {
            for (String k : new String[]{"image/*", "video/*", "*/*", "text/plain"}) {
                try {
                    cache.put(k, load(k));
                } catch (RuntimeException ignored) {
                    // giữ bản cũ nếu PackageManager lỗi
                }
            }
        }, "IslandDrop-targets");
        th.setPriority(Thread.MIN_PRIORITY);
        th.start();
    }

    static void recordUse(Context ctx, ComponentName cn) {
        SharedPreferences p = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        String k = cn.flattenToShortString();
        p.edit().putInt(k, p.getInt(k, 0) + 1).apply();
    }

    private List<Target> load(String mime) {
        PackageManager pm = app.getPackageManager();
        Intent send = new Intent(Intent.ACTION_SEND).setType(mime);
        List<ResolveInfo> found = pm.queryIntentActivities(send, PackageManager.MATCH_DEFAULT_ONLY);
        SharedPreferences usage = app.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        List<String> pins = getPins(app);
        Set<String> hidden = getHidden(app);

        List<ResolveInfo> list = new ArrayList<>();
        for (ResolveInfo ri : found) {
            if (ri.activityInfo == null) continue;
            if (app.getPackageName().equals(ri.activityInfo.packageName)) continue;
            if (!ri.activityInfo.exported) continue;
            if (hidden.contains(cn(ri).flattenToShortString())) continue;
            list.add(ri);
        }

        Collections.sort(list, (a, b) -> {
            // app được ghim luôn đứng đầu, theo thứ tự ghim
            int ia = pins.indexOf(cn(a).flattenToShortString());
            int ib = pins.indexOf(cn(b).flattenToShortString());
            if (ia != ib) {
                if (ia < 0) return 1;
                if (ib < 0) return -1;
                return Integer.compare(ia, ib);
            }
            int ua = usage.getInt(cn(a).flattenToShortString(), 0);
            int ub = usage.getInt(cn(b).flattenToShortString(), 0);
            if (ua != ub) return Integer.compare(ub, ua);
            int pa = rank(a.activityInfo.packageName);
            int pb = rank(b.activityInfo.packageName);
            if (pa != pb) return Integer.compare(pa, pb);
            return a.loadLabel(pm).toString().compareToIgnoreCase(b.loadLabel(pm).toString());
        });

        List<Target> out = new ArrayList<>();
        for (ResolveInfo ri : list) {
            boolean pinned = pins.contains(cn(ri).flattenToShortString());
            if (out.size() >= MAX_TARGETS && !pinned) continue;
            Drawable icon = ri.loadIcon(pm);
            if (icon != null) icon = icon.mutate();
            out.add(new Target(cn(ri), ri.loadLabel(pm), icon));
        }
        return Collections.unmodifiableList(out);
    }

    private static int rank(String pkg) {
        int i = PREFERRED.indexOf(pkg);
        return i < 0 ? Integer.MAX_VALUE : i;
    }

    private static ComponentName cn(ResolveInfo ri) {
        return new ComponentName(ri.activityInfo.packageName, ri.activityInfo.name);
    }
}
