package vn.islanddrop;

import android.app.Activity;
import android.content.ClipData;
import android.content.ClipDescription;
import android.content.ClipboardManager;
import android.os.Bundle;
import android.os.PersistableBundle;

/**
 * Màn hình trong suốt, mở trong tích tắc ngay sau khi người dùng bấm "Sao chép".
 * Từ Android 10, chỉ app đang được focus mới đọc được bộ nhớ tạm, nên phải đợi
 * onWindowFocusChanged(true) rồi mới đọc, sau đó đóng ngay.
 * Bỏ qua nội dung được đánh dấu nhạy cảm (mật khẩu, mã OTP...). Không ghi nội dung vào log.
 */
public class ClipReadActivity extends Activity {

    private static final int MAX_CHARS = 20_000;
    private boolean done;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        overridePendingTransition(0, 0);
        // nếu không nhận được focus (hiếm), tự đóng để không kẹt màn hình
        getWindow().getDecorView().postDelayed(this::finishQuietly, 1200);
    }

    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        if (!hasFocus || done) return;
        done = true;
        try {
            readClipboard();
        } catch (RuntimeException ex) {
            L.i(this, "ClipRead lỗi: " + ex);
        }
        finishQuietly();
    }

    private void readClipboard() {
        ClipboardManager cm = getSystemService(ClipboardManager.class);
        if (cm == null || !cm.hasPrimaryClip()) {
            L.i(this, "ClipRead: bộ nhớ tạm trống");
            return;
        }
        ClipDescription d = cm.getPrimaryClipDescription();
        if (d != null) {
            PersistableBundle ex = d.getExtras();
            if (ex != null && ex.getBoolean(ClipDescription.EXTRA_IS_SENSITIVE, false)) {
                L.i(this, "ClipRead: nội dung nhạy cảm, bỏ qua");
                return;
            }
        }
        ClipData clip = cm.getPrimaryClip();
        if (clip == null || clip.getItemCount() == 0) return;
        CharSequence cs = clip.getItemAt(0).coerceToText(this);
        String text = cs == null ? "" : cs.toString().trim();
        if (text.isEmpty()) {
            L.i(this, "ClipRead: không có chữ");
            return;
        }
        if (text.length() > MAX_CHARS) text = text.substring(0, MAX_CHARS);
        L.i(this, "ClipRead: đọc được " + text.length() + " ký tự");
        DropService svc = DropService.instance;
        if (svc != null) svc.showClipText(text);
    }

    private void finishQuietly() {
        if (isFinishing()) return;
        done = true;
        finish();
        overridePendingTransition(0, 0);
    }
}
