package vn.islanddrop;

import android.content.ActivityNotFoundException;
import android.content.ClipData;
import android.content.ComponentName;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.database.Cursor;
import android.net.Uri;
import android.provider.OpenableColumns;
import android.webkit.MimeTypeMap;
import android.widget.Toast;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;

/** Dùng chung cho kéo thả và chạm: chép tệp vào cache/share rồi mở app đích để gửi. */
final class Sharer {

    private static final long KEEP_MS = 24L * 60 * 60 * 1000; // giữ tệp chia sẻ 24 giờ

    private Sharer() {}

    /** Tạo thư mục mới cho lần gửi này, đồng thời dọn các lần gửi cũ hơn 24 giờ. */
    static File prepareDir(Context ctx) {
        File root = ShareProvider.root(ctx);
        File[] old = root.listFiles();
        long now = System.currentTimeMillis();
        if (old != null) {
            for (File d : old) {
                if (now - d.lastModified() > KEEP_MS) deleteRecursive(d);
            }
        }
        File dir = new File(root, Long.toString(now));
        //noinspection ResultOfMethodCallIgnored
        dir.mkdirs();
        return dir;
    }

    /** Chép một nội dung (content://) vào dir. Chạy ở luồng nền. */
    static File copy(Context ctx, Uri src, File dir, int index, String fallbackMime) throws IOException {
        ContentResolver cr = ctx.getContentResolver();
        String name = displayName(cr, src);
        String mime = cr.getType(src);
        if (mime == null) mime = fallbackMime;
        name = sanitize(name, index);
        if (name.indexOf('.') < 0 && mime != null) {
            String ext = MimeTypeMap.getSingleton().getExtensionFromMimeType(mime);
            if (ext != null) name = name + "." + ext;
        }
        File out = new File(dir, name);
        long total = 0;
        try (InputStream in = cr.openInputStream(src);
             OutputStream os = new FileOutputStream(out)) {
            if (in == null) throw new IOException("openInputStream = null");
            byte[] buf = new byte[128 * 1024];
            int n;
            while ((n = in.read(buf)) > 0) {
                os.write(buf, 0, n);
                total += n;
            }
        }
        L.i(ctx, "  đã chép " + name + " (" + total + " byte)");
        return out;
    }

    /** Mở app đích. Gọi trên luồng chính. */
    static void launch(Context ctx, ComponentName target, ArrayList<Uri> uris,
                       ArrayList<String> mimes, String text) {
        if (uris.isEmpty() && (text == null || text.isEmpty())) {
            Toast.makeText(ctx, "Không đọc được nội dung để gửi", Toast.LENGTH_SHORT).show();
            return;
        }
        Intent send;
        if (uris.size() <= 1) {
            send = new Intent(Intent.ACTION_SEND);
            if (uris.isEmpty()) {
                send.setType("text/plain");
            } else {
                send.setType(mimes.get(0));
                send.putExtra(Intent.EXTRA_STREAM, uris.get(0));
            }
        } else {
            send = new Intent(Intent.ACTION_SEND_MULTIPLE);
            send.setType(commonMime(mimes));
            send.putParcelableArrayListExtra(Intent.EXTRA_STREAM, uris);
        }
        if (text != null && !text.isEmpty()) send.putExtra(Intent.EXTRA_TEXT, text);
        if (!uris.isEmpty()) {
            ClipData cd = ClipData.newRawUri("IslandDrop", uris.get(0));
            for (int i = 1; i < uris.size(); i++) cd.addItem(new ClipData.Item(uris.get(i)));
            send.setClipData(cd);
            send.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        }
        send.setComponent(target);
        send.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        try {
            ctx.startActivity(send);
            L.i(ctx, "Đã mở " + target.flattenToShortString() + " với " + uris.size() + " tệp");
        } catch (ActivityNotFoundException | SecurityException ex) {
            L.i(ctx, "Không mở được app đích: " + ex);
            Toast.makeText(ctx, "Không mở được app này", Toast.LENGTH_SHORT).show();
        }
    }

    private static String displayName(ContentResolver cr, Uri uri) {
        try (Cursor c = cr.query(uri, new String[]{OpenableColumns.DISPLAY_NAME}, null, null, null)) {
            if (c != null && c.moveToFirst() && !c.isNull(0)) return c.getString(0);
        } catch (RuntimeException ignored) {
            // nhà cung cấp không hỗ trợ query
        }
        return uri.getLastPathSegment();
    }

    /** Chỉ giữ ký tự an toàn cho tên file. */
    private static String sanitize(String name, int index) {
        if (name == null || name.trim().isEmpty()) name = "file_" + index;
        int slash = name.lastIndexOf('/');
        if (slash >= 0) name = name.substring(slash + 1);
        name = name.replaceAll("[^\\p{L}\\p{N}._ -]", "_");
        if (name.startsWith(".")) name = "_" + name;
        if (name.length() > 120) name = name.substring(name.length() - 120);
        return index == 0 ? name : index + "_" + name;
    }

    private static String commonMime(ArrayList<String> mimes) {
        String first = mimes.get(0);
        String top = first.substring(0, Math.max(0, first.indexOf('/')));
        boolean same = true, sameTop = true;
        for (String m : mimes) {
            same &= m.equals(first);
            sameTop &= m.startsWith(top + "/");
        }
        if (same) return first;
        if (sameTop && !top.isEmpty()) return top + "/*";
        return "*/*";
    }

    private static void deleteRecursive(File f) {
        File[] kids = f.listFiles();
        if (kids != null) for (File k : kids) deleteRecursive(k);
        //noinspection ResultOfMethodCallIgnored
        f.delete();
    }
}
