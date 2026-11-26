package vn.islanddrop;

import android.app.PendingIntent;
import android.content.Intent;
import android.os.Build;
import android.provider.Settings;
import android.service.quicksettings.Tile;
import android.service.quicksettings.TileService;

/**
 * Ô cài đặt nhanh "Trợ năng đảo": chạm để tắt/bật Trợ năng của IslandDrop
 * (vd tắt trước khi mở app ngân hàng đòi tắt Trợ năng).
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
        A11yToggle.setEnabled(this, !A11yToggle.isEnabled(this));
        refresh();
    }

    private void refresh() {
        Tile t = getQsTile();
        if (t == null) return;
        boolean on = A11yToggle.isEnabled(this);
        t.setState(on ? Tile.STATE_ACTIVE : Tile.STATE_INACTIVE);
        t.setLabel("Trợ năng đảo");
        if (Build.VERSION.SDK_INT >= 29) t.setSubtitle(on ? "Đang bật" : "Đã tắt");
        t.updateTile();
    }
}
