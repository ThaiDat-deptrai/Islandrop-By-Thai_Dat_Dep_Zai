package vn.islanddrop;

import android.content.ContentProvider;
import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.MatrixCursor;
import android.net.Uri;
import android.os.ParcelFileDescriptor;
import android.provider.OpenableColumns;
import android.webkit.MimeTypeMap;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.List;

/**
 * Cung cấp file đã chép (cache/share/...) cho app nhận, CHỈ ĐỌC, qua quyền URI tạm thời.
 * Không export: app khác chỉ đọc được khi IslandDrop cấp FLAG_GRANT_READ_URI_PERMISSION.
 */
public class ShareProvider extends ContentProvider {

    static final String AUTHORITY = "vn.islanddrop.share";

    static File root(Context ctx) {
        return new File(ctx.getCacheDir(), "share");
    }

    /** Uri dạng content://vn.islanddrop.share/<thư mục>/<tên file> */
    static Uri uriFor(Context ctx, File f) {
        File dir = f.getParentFile();
        return new Uri.Builder().scheme("content").authority(AUTHORITY)
                .appendPath(dir.getName()).appendPath(f.getName()).build();
    }

    private File fileFor(Uri uri) throws FileNotFoundException {
        List<String> seg = uri.getPathSegments();
        if (seg.size() != 2) throw new FileNotFoundException("Uri không hợp lệ");
        try {
            File base = root(getContext()).getCanonicalFile();
            File f = new File(new File(base, seg.get(0)), seg.get(1)).getCanonicalFile();
            // chống truy cập ra ngoài thư mục share (path traversal)
            if (!f.getPath().startsWith(base.getPath() + File.separator) || !f.isFile()) {
                throw new FileNotFoundException("Không có file");
            }
            return f;
        } catch (IOException e) {
            throw new FileNotFoundException(e.getMessage());
        }
    }

    @Override
    public boolean onCreate() {
        return true;
    }

    @Override
    public ParcelFileDescriptor openFile(Uri uri, String mode) throws FileNotFoundException {
        if (!"r".equals(mode)) throw new SecurityException("Chỉ cho phép đọc");
        return ParcelFileDescriptor.open(fileFor(uri), ParcelFileDescriptor.MODE_READ_ONLY);
    }

    @Override
    public String getType(Uri uri) {
        return mimeOf(uri.getLastPathSegment());
    }

    static String mimeOf(String name) {
        String ext = MimeTypeMap.getFileExtensionFromUrl(Uri.encode(name == null ? "" : name));
        if (ext == null || ext.isEmpty()) {
            int dot = name == null ? -1 : name.lastIndexOf('.');
            ext = dot >= 0 ? name.substring(dot + 1) : "";
        }
        String m = MimeTypeMap.getSingleton().getMimeTypeFromExtension(ext.toLowerCase());
        return m != null ? m : "application/octet-stream";
    }

    @Override
    public Cursor query(Uri uri, String[] projection, String selection, String[] selectionArgs,
                        String sortOrder) {
        File f;
        try {
            f = fileFor(uri);
        } catch (FileNotFoundException e) {
            return null;
        }
        String[] cols = projection != null ? projection
                : new String[]{OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE};
        MatrixCursor c = new MatrixCursor(cols, 1);
        Object[] row = new Object[cols.length];
        for (int i = 0; i < cols.length; i++) {
            if (OpenableColumns.DISPLAY_NAME.equals(cols[i])) row[i] = f.getName();
            else if (OpenableColumns.SIZE.equals(cols[i])) row[i] = f.length();
        }
        c.addRow(row);
        return c;
    }

    @Override
    public Uri insert(Uri uri, ContentValues values) {
        return null;
    }

    @Override
    public int delete(Uri uri, String selection, String[] selectionArgs) {
        return 0;
    }

    @Override
    public int update(Uri uri, ContentValues values, String selection, String[] selectionArgs) {
        return 0;
    }
}
