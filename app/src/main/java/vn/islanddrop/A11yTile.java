package vn.islanddrop;

import android.app.PendingIntent;
import android.content.Intent;
import android.os.Build;
import android.provider.Settings;
import android.service.quicksettings.Tile;
import android.service.quicksettings.TileService;

/**
 * Ô cài đặt nhanh "Trợ năng đảo": đang bật -> TẠM TẮT 5 PHÚT rồi tự bật lại
 * (vd để đăng nhập app ngân hàng đòi tắt Trợ năng); đang tắt -> bật lại ngay.
 * Chưa có quyền WRITE_SECURE_SETTINGS thì mở trang Trợ năng để người dùng tự bật/tắt.
 */
public class A11yTile extends TileService {

    @Override
    public void onStartListening() {
        refresh();
    }

    @Override
    public void onClick() {
        if (!A11yToggle.canToggle(this)) {
            Intent i = new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            if (Build.VERSION.SDK_INT >= 34) {
                startActivityAndCollapse(PendingIntent.getActivity(this, 0, i, PendingIntent.FLAG_IMMUTABLE));
            } else {
                startActivityAndCollapse(i);
            }
            return;
        }
        DropService svc = DropService.instance;
        if (A11yToggle.isEnabled(this)) {
            if (svc != null) {
                svc.pauseAccessibility();
            } else {
                A11yToggle.setEnabled(this, false);
                A11yToggle.setPausedUntil(this, System.currentTimeMillis() + A11yToggle.PAUSE_MS);
            }
        } else {
            if (svc != null) {
                svc.resumeAccessibility();
            } else {
                A11yToggle.setPausedUntil(this, 0);
                A11yToggle.setEnabled(this, true);
            }
        }
        refresh();
    }

    private void refresh() {
        Tile t = getQsTile();
        if (t == null) return;
        boolean on = A11yToggle.isEnabled(this);
        long at = A11yToggle.pausedUntil(this);
        t.setState(on ? Tile.STATE_ACTIVE : Tile.STATE_INACTIVE);
        t.setLabel("Trợ năng đảo");
        if (Build.VERSION.SDK_INT >= 29) {
            t.setSubtitle(on ? "Chạm: tắt 5 phút"
                    : at > System.currentTimeMillis() ? "Tự bật lúc " + A11yToggle.clock(at) : "Đã tắt");
        }
        t.updateTile();
    }
}
