package vn.islanddrop;

import android.content.Intent;
import android.os.Build;
import android.provider.Settings;
import android.service.quicksettings.Tile;
import android.service.quicksettings.TileService;

/** Ô cài đặt nhanh "Đảo luôn hiện": bật/tắt viên đảo luôn hiện quanh camera (chạm để mở app nhanh). */
public class IslandTile extends TileService {

    @Override
    public void onStartListening() {
        refresh();
    }

    @Override
    public void onClick() {
        boolean on = !MainActivity.isAlwaysIsland(this);
        MainActivity.setAlwaysIsland(this, on);
        DropService svc = DropService.instance;
        if (svc != null) {
            svc.applyAlwaysIsland();
        } else if (on && Settings.canDrawOverlays(this)) {
            try {
                MainActivity.setEnabledPref(this, true);
                startForegroundService(new Intent(this, DropService.class));
            } catch (RuntimeException ex) {
                L.i(this, "Ô Đảo luôn hiện: không bật được khay: " + ex);
            }
        }
        refresh();
    }

    private void refresh() {
        Tile t = getQsTile();
        if (t == null) return;
        boolean on = MainActivity.isAlwaysIsland(this);
        t.setState(on ? Tile.STATE_ACTIVE : Tile.STATE_INACTIVE);
        t.setLabel("Đảo luôn hiện");
        if (Build.VERSION.SDK_INT >= 29) t.setSubtitle(on ? "Đang bật" : "Đã tắt");
        t.updateTile();
    }
}
