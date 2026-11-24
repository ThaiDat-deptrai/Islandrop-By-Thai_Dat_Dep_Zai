package vn.islanddrop;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.provider.Settings;

/** Tự bật lại khay sau khi khởi động máy hoặc cập nhật app (nếu người dùng đã bật trước đó). */
public class BootReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context ctx, Intent intent) {
        String a = intent.getAction();
        if (!Intent.ACTION_BOOT_COMPLETED.equals(a) && !Intent.ACTION_MY_PACKAGE_REPLACED.equals(a)) {
            return;
        }
        if (!MainActivity.isEnabled(ctx) || !Settings.canDrawOverlays(ctx)) return;
        try {
            ctx.startForegroundService(new Intent(ctx, DropService.class));
        } catch (RuntimeException ex) {
            L.i(ctx, "Không tự bật được sau khởi động: " + ex);
        }
    }
}
