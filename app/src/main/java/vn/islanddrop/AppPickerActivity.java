package vn.islanddrop;

import android.app.Activity;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;

/**
 * Màn hình "Thêm app" mở từ ô "+" trên khay.
 * Chạm một app để đổi trạng thái: Bình thường -> Ghim lên đầu -> Ẩn khỏi khay -> Bình thường.
 * App mới cài sau này tự có trong danh sách (đọc lại mỗi lần mở).
 */
public class AppPickerActivity extends Activity {

    private static final class Row {
        final String key;
        final CharSequence label, appName;
        final Drawable icon;

        Row(String key, CharSequence label, CharSequence appName, Drawable icon) {
            this.key = key;
            this.label = label;
            this.appName = appName;
            this.icon = icon;
        }
    }

    private final List<Row> rows = new ArrayList<>();
    private List<String> pins;
    private Set<String> hidden;
    private BaseAdapter adapter;
    private TextView header;
    private float dp;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        dp = getResources().getDisplayMetrics().density;
        pins = ShareTargets.getPins(this);
        hidden = ShareTargets.getHidden(this);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding((int) (16 * dp), (int) (48 * dp), (int) (16 * dp), 0);

        TextView title = new TextView(this);
        title.setText("Thêm app vào khay");
        title.setTextSize(24);
        root.addView(title);

        header = new TextView(this);
        header.setText("Đang tải danh sách app…");
        header.setTextSize(14);
        header.setPadding(0, (int) (6 * dp), 0, (int) (10 * dp));
        root.addView(header);

        ListView list = new ListView(this);
        adapter = new BaseAdapter() {
            @Override
            public int getCount() {
                return rows.size();
            }

            @Override
            public Object getItem(int i) {
                return rows.get(i);
            }

            @Override
            public long getItemId(int i) {
                return i;
            }

            @Override
            public View getView(int i, View convert, ViewGroup parent) {
                return bind(rows.get(i), convert);
            }
        };
        list.setAdapter(adapter);
        list.setOnItemClickListener((p, v, i, id) -> cycle(rows.get(i)));
        root.addView(list, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));
        setContentView(root);

        new Thread(this::load, "IslandDrop-picker").start();
    }

    private void load() {
        PackageManager pm = getPackageManager();
        List<Row> out = new ArrayList<>();
        for (ResolveInfo ri : ShareTargets.allCandidates(this)) {
            CharSequence app = ri.activityInfo.applicationInfo.loadLabel(pm);
            out.add(new Row(ShareTargets.key(ri), ri.loadLabel(pm), app, ri.loadIcon(pm)));
        }
        Collections.sort(out, (a, b) -> a.label.toString().compareToIgnoreCase(b.label.toString()));
        runOnUiThread(() -> {
            rows.clear();
            rows.addAll(out);
            sortRows();
            header.setText("Chạm để đổi: Bình thường → Ghim lên đầu → Ẩn khỏi khay.\n"
                    + rows.size() + " lựa chọn chia sẻ. App cài sau này cũng sẽ có ở đây.");
            adapter.notifyDataSetChanged();
        });
    }

    /** Ghim lên đầu (theo thứ tự ghim), rồi bình thường, ẩn ở cuối. */
    private void sortRows() {
        Collections.sort(rows, (a, b) -> {
            int ra = rank(a), rb = rank(b);
            if (ra != rb) return Integer.compare(ra, rb);
            if (ra == 0) return Integer.compare(pins.indexOf(a.key), pins.indexOf(b.key));
            return a.label.toString().compareToIgnoreCase(b.label.toString());
        });
    }

    private int rank(Row r) {
        if (pins.contains(r.key)) return 0;
        if (hidden.contains(r.key)) return 2;
        return 1;
    }

    private void cycle(Row r) {
        if (pins.contains(r.key)) {
            pins.remove(r.key);
            hidden.add(r.key);
        } else if (hidden.contains(r.key)) {
            hidden.remove(r.key);
        } else {
            pins.add(r.key);
        }
        ShareTargets.setPins(this, pins);
        ShareTargets.setHidden(this, hidden);
        DropService svc = DropService.instance;
        if (svc != null) svc.refreshTargets();
        adapter.notifyDataSetChanged();   // giữ nguyên vị trí cho dễ bấm tiếp; sắp xếp lại khi mở lại
    }

    private View bind(Row r, View convert) {
        LinearLayout row;
        ImageView icon;
        TextView label, state;
        if (convert instanceof LinearLayout) {
            row = (LinearLayout) convert;
            icon = (ImageView) row.getChildAt(0);
            LinearLayout texts = (LinearLayout) row.getChildAt(1);
            label = (TextView) texts.getChildAt(0);
            state = (TextView) row.getChildAt(2);
        } else {
            row = new LinearLayout(this);
            row.setGravity(Gravity.CENTER_VERTICAL);
            row.setPadding(0, (int) (10 * dp), 0, (int) (10 * dp));
            icon = new ImageView(this);
            int s = (int) (40 * dp);
            row.addView(icon, new LinearLayout.LayoutParams(s, s));
            LinearLayout texts = new LinearLayout(this);
            texts.setOrientation(LinearLayout.VERTICAL);
            texts.setPadding((int) (14 * dp), 0, (int) (8 * dp), 0);
            label = new TextView(this);
            label.setTextSize(16);
            texts.addView(label);
            TextView sub = new TextView(this);
            sub.setTextSize(12);
            sub.setTextColor(0xFF888888);
            texts.addView(sub);
            row.addView(texts, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
            state = new TextView(this);
            state.setTextSize(13);
            row.addView(state);
        }
        icon.setImageDrawable(r.icon);
        label.setText(r.label);
        ((TextView) ((LinearLayout) row.getChildAt(1)).getChildAt(1)).setText(
                r.label.toString().equals(r.appName.toString()) ? "" : r.appName);
        int rank = rank(r);
        if (rank == 0) {
            state.setText("Ghim lên đầu");
            state.setTextColor(0xFF1E88E5);
        } else if (rank == 2) {
            state.setText("Ẩn");
            state.setTextColor(0xFFE53935);
        } else {
            state.setText("Bình thường");
            state.setTextColor(0xFF777777);
        }
        row.setAlpha(rank == 2 ? 0.5f : 1f);
        return row;
    }
}
