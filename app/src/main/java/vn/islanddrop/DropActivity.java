package vn.islanddrop;

import android.app.Activity;
import android.content.ClipData;
import android.content.ClipDescription;
import android.content.ComponentName;
import android.net.Uri;
import android.os.Bundle;
import android.view.DragAndDropPermissions;
import android.view.DragEvent;

import java.io.File;
import java.util.ArrayList;

/**
 * Activity trong suốt, không giao diện, chạy trong tích tắc sau khi thả:
 *  1. Xin quyền đọc mà app nguồn (Thư viện) cấp kèm lượt kéo — chỉ Activity mới xin được.
 *  2. Chép ảnh/tệp vào cache/share ở luồng nền (không làm đơ máy với video lớn).
 *  3. Mở app đích bằng ACTION_SEND / ACTION_SEND_MULTIPLE với quyền đọc tạm thời.
 */
public class DropActivity extends Activity {

    static final String EXTRA_EVENT = "event";
    static final String EXTRA_TARGET = "target";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        overridePendingTransition(0, 0);

        DragEvent e;
        ComponentName target;
        DragAndDropPermissions perms;
        try {
            e = getIntent().getParcelableExtra(EXTRA_EVENT, DragEvent.class);
            target = getIntent().getParcelableExtra(EXTRA_TARGET, ComponentName.class);
            perms = e == null ? null : requestDragAndDropPermissions(e);
        } catch (RuntimeException ex) {
            L.i(this, "DropActivity: lỗi đọc dữ liệu thả: " + ex);
            done();
            return;
        }
        if (e == null || target == null || e.getClipData() == null) {
            L.i(this, "DropActivity: thiếu dữ liệu");
            done();
            return;
        }

        final ClipData clip = e.getClipData();
        final ClipDescription desc = e.getClipDescription();
        final String fallbackMime = desc != null && desc.getMimeTypeCount() > 0 ? desc.getMimeType(0) : null;
        new Thread(() -> {
            ArrayList<Uri> uris = new ArrayList<>();
            ArrayList<String> mimes = new ArrayList<>();
            StringBuilder text = new StringBuilder();
            try {
                File dir = Sharer.prepareDir(this);
                for (int i = 0; i < clip.getItemCount(); i++) {
                    ClipData.Item it = clip.getItemAt(i);
                    Uri src = it.getUri();
                    if (src == null) {
                        if (it.getText() != null) text.append(it.getText()).append('\n');
                        continue;
                    }
                    try {
                        File f = Sharer.copy(this, src, dir, i, fallbackMime);
                        uris.add(ShareProvider.uriFor(this, f));
                        mimes.add(ShareProvider.mimeOf(f.getName()));
                    } catch (Exception ex) {
                        L.i(this, "  không chép được " + src + ": " + ex);
                    }
                }
            } catch (Exception ex) {
                L.i(this, "DropActivity lỗi: " + ex);
            } finally {
                if (perms != null) {
                    try {
                        perms.release();
                    } catch (RuntimeException ignored) {
                        // quyền đã hết hạn
                    }
                }
            }
            runOnUiThread(() -> {
                Sharer.launch(this, target, uris, mimes, text.toString().trim());
                done();
            });
        }, "IslandDrop-copy").start();
    }

    private void done() {
        finish();
        overridePendingTransition(0, 0);
    }
}
