package vn.islanddrop;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Outline;
import android.graphics.Paint;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.HapticFeedbackConstants;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewOutlineProvider;
import android.view.animation.DecelerateInterpolator;
import android.view.animation.OvershootInterpolator;
import android.widget.FrameLayout;
import android.widget.GridLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.util.List;
import java.util.Locale;

/**
 * Bảng mở rộng của đảo luôn hiện (kiểu Atoll): 3 tab Nhạc / Ứng dụng / Hẹn giờ.
 * Nở ra từ viên đảo bằng hiệu ứng phóng + nảy nhẹ, nội dung hiện dần.
 */
final class PanelView extends LinearLayout {

    interface Host {
        MediaState media();

        void mediaPlayPause();

        void mediaSkip(int dir);

        void mediaSeek(long ms);

        boolean hasMediaAccess();

        void openMediaAccessSettings();

        List<ShareTargets.Target> quickApps();

        void launch(ShareTargets.Target t);

        void addQuickApps();

        long timerRemaining();   // <= 0: không chạy

        void startTimer(long ms);

        void addTimer(long ms);

        void cancelTimer();

        void closePanel();

        void touched();

        /** Tắt Trợ năng 5 phút rồi tự bật lại (app ngân hàng). */
        void pauseAccessibility();
    }

    static final int TAB_MEDIA = 0, TAB_APPS = 1, TAB_TIMER = 2;
    private static final String[] TAB_NAMES = {"Nhạc", "Ứng dụng", "Hẹn giờ"};

    private final Host host;
    private final float dp;
    private final TextView[] tabs = new TextView[3];
    private final FrameLayout content;
    private int tab;

    // phần tử cập nhật liên tục
    private ProgressLine progress;
    private TextView elapsed, remain, timerBig;
    private ImageView playBtn;
    private MediaState shownMedia;

    PanelView(Context c, Host host, int startTab) {
        super(c);
        this.host = host;
        dp = c.getResources().getDisplayMetrics().density;
        setOrientation(VERTICAL);
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(Color.BLACK);
        bg.setCornerRadius(34 * dp);
        setBackground(bg);
        // hàng trên cùng chừa chỗ cho thanh trạng thái + camera
        setPadding((int) (16 * dp), (int) (42 * dp), (int) (16 * dp), (int) (16 * dp));
        setElevation(18 * dp);
        setOutlineProvider(new ViewOutlineProvider() {
            @Override
            public void getOutline(View v, Outline o) {
                o.setRoundRect(0, 0, v.getWidth(), v.getHeight(), 34 * dp);
            }
        });
        setClipToOutline(true);

        LinearLayout tabRow = new LinearLayout(c);
        tabRow.setGravity(Gravity.CENTER);
        for (int i = 0; i < 3; i++) {
            final int idx = i;
            TextView t = new TextView(c);
            t.setText(TAB_NAMES[i]);
            t.setTextSize(13);
            t.setTypeface(Typeface.DEFAULT_BOLD);
            t.setPadding((int) (14 * dp), (int) (7 * dp), (int) (14 * dp), (int) (7 * dp));
            t.setOnClickListener(v -> {
                v.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK);
                select(idx, true);
            });
            LayoutParams lp = new LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            lp.setMargins((int) (3 * dp), 0, (int) (3 * dp), 0);
            tabRow.addView(t, lp);
            tabs[i] = t;
        }
        addView(tabRow);

        content = new FrameLayout(c);
        LayoutParams clp = new LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        clp.topMargin = (int) (14 * dp);
        addView(content, clp);
        select(startTab, false);
    }

    // ---------------------------------------------------------------- hiệu ứng mở / đóng

    /** Nở ra từ viên đảo (pillW x pillH ở giữa mép trên). */
    void animateOpen(float pillW, float pillH) {
        post(() -> {
            setPivotX(getWidth() / 2f);
            setPivotY(0f);
            setScaleX(Math.max(0.05f, pillW / Math.max(1, getWidth())));
            setScaleY(Math.max(0.05f, pillH / Math.max(1, getHeight())));
            for (int i = 0; i < getChildCount(); i++) {
                View ch = getChildAt(i);
                ch.setAlpha(0f);
                ch.setTranslationY(-8 * dp);
                ch.animate().alpha(1f).translationY(0).setStartDelay(110 + i * 40L).setDuration(260)
                        .setInterpolator(new DecelerateInterpolator()).start();
            }
            animate().scaleX(1f).scaleY(1f).setDuration(440)
                    .setInterpolator(new OvershootInterpolator(0.9f)).start();
        });
    }

    /** Thu về viên đảo rồi gọi done. */
    void animateClose(float pillW, float pillH, Runnable done) {
        for (int i = 0; i < getChildCount(); i++) getChildAt(i).animate().alpha(0f).setStartDelay(0).setDuration(110).start();
        animate().scaleX(Math.max(0.05f, pillW / Math.max(1, getWidth())))
                .scaleY(Math.max(0.05f, pillH / Math.max(1, getHeight())))
                .setDuration(240).setInterpolator(new DecelerateInterpolator(1.6f))
                .withEndAction(done).start();
    }

    @Override
    public boolean dispatchTouchEvent(MotionEvent e) {
        host.touched();
        if (e.getActionMasked() == MotionEvent.ACTION_OUTSIDE) {
            host.closePanel();
            return true;
        }
        return super.dispatchTouchEvent(e);
    }

    // ---------------------------------------------------------------- tab

    void select(int t, boolean animate) {
        tab = t;
        for (int i = 0; i < 3; i++) {
            GradientDrawable d = new GradientDrawable();
            d.setCornerRadius(16 * dp);
            d.setColor(i == t ? 0xFF2C2C2E : 0x00000000);
            tabs[i].setBackground(d);
            tabs[i].setTextColor(i == t ? Color.WHITE : 0xFF8E8E93);
        }
        progress = null;
        elapsed = remain = timerBig = null;
        playBtn = null;
        shownMedia = null;
        content.removeAllViews();
        View v = t == TAB_MEDIA ? buildMedia() : t == TAB_APPS ? buildApps() : buildTimer();
        content.addView(v);
        if (animate) {
            v.setAlpha(0f);
            v.setTranslationX(12 * dp);
            v.animate().alpha(1f).translationX(0).setDuration(200).start();
        }
    }

    int tab() {
        return tab;
    }

    /** Gọi định kỳ (~2 lần/giây) khi bảng đang mở. */
    void refresh() {
        if (tab == TAB_MEDIA) {
            MediaState m = host.media();
            boolean sameSong = m != null && shownMedia != null
                    && TextUtils.equals(m.title, shownMedia.title) && TextUtils.equals(m.pkg, shownMedia.pkg);
            if ((m == null) != (shownMedia == null) || (m != null && !sameSong)) {
                select(TAB_MEDIA, false);   // đổi bài: dựng lại
                return;
            }
            if (m != null) {
                shownMedia = m;
                if (playBtn != null) playBtn.setImageResource(m.playing ? R.drawable.ic_pause : R.drawable.ic_play);
                updateProgress(m);
            }
        } else if (tab == TAB_TIMER) {
            long rem = host.timerRemaining();
            if ((rem > 0) != (timerBig != null)) {
                select(TAB_TIMER, false);
            } else if (timerBig != null) {
                timerBig.setText(fmt(rem));
            }
        }
    }

    // ---------------------------------------------------------------- Nhạc

    private View buildMedia() {
        Context c = getContext();
        if (!host.hasMediaAccess()) {
            return hint("Cho phép IslandDrop đọc nhạc đang phát để điều khiển ngay trên đảo.",
                    "Cho phép", host::openMediaAccessSettings);
        }
        MediaState m = host.media();
        if (m == null) {
            return hint("Chưa có nhạc đang phát.\nMở Spotify, YouTube Music, Zing MP3… rồi phát một bài.", null, null);
        }
        shownMedia = m;
        LinearLayout col = new LinearLayout(c);
        col.setOrientation(VERTICAL);

        LinearLayout top = new LinearLayout(c);
        top.setGravity(Gravity.CENTER_VERTICAL);
        ImageView art = new ImageView(c);
        art.setScaleType(ImageView.ScaleType.CENTER_CROP);
        if (m.art != null) art.setImageBitmap(m.art);
        else art.setBackgroundColor(0xFF2C2C2E);
        art.setOutlineProvider(new ViewOutlineProvider() {
            @Override
            public void getOutline(View v, Outline o) {
                o.setRoundRect(0, 0, v.getWidth(), v.getHeight(), 12 * dp);
            }
        });
        art.setClipToOutline(true);
        int as = (int) (62 * dp);
        top.addView(art, new LayoutParams(as, as));

        LinearLayout texts = new LinearLayout(c);
        texts.setOrientation(VERTICAL);
        texts.setPadding((int) (14 * dp), 0, 0, 0);
        TextView title = new TextView(c);
        title.setText(m.title);
        title.setTextColor(Color.WHITE);
        title.setTextSize(16);
        title.setTypeface(Typeface.DEFAULT_BOLD);
        title.setSingleLine(true);
        title.setEllipsize(TextUtils.TruncateAt.MARQUEE);
        title.setMarqueeRepeatLimit(-1);
        title.setSelected(true);           // chữ dài tự chạy ngang
        texts.addView(title);
        TextView artist = new TextView(c);
        artist.setText(m.artist);
        artist.setTextColor(0xFFAEAEB2);
        artist.setTextSize(13);
        artist.setSingleLine(true);
        artist.setEllipsize(TextUtils.TruncateAt.END);
        texts.addView(artist);
        top.addView(texts, new LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        col.addView(top);

        progress = new ProgressLine(c);
        LayoutParams plp = new LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, (int) (22 * dp));
        plp.topMargin = (int) (12 * dp);
        col.addView(progress, plp);

        LinearLayout times = new LinearLayout(c);
        elapsed = smallText();
        remain = smallText();
        remain.setGravity(Gravity.END);
        times.addView(elapsed, new LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        times.addView(remain, new LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        col.addView(times);

        LinearLayout ctl = new LinearLayout(c);
        ctl.setGravity(Gravity.CENTER);
        ctl.addView(ctlButton(R.drawable.ic_prev, 34, () -> host.mediaSkip(-1)));
        playBtn = ctlButton(m.playing ? R.drawable.ic_pause : R.drawable.ic_play, 44, host::mediaPlayPause);
        ctl.addView(playBtn);
        ctl.addView(ctlButton(R.drawable.ic_next, 34, () -> host.mediaSkip(1)));
        LayoutParams clp = new LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        clp.topMargin = (int) (4 * dp);
        col.addView(ctl, clp);

        updateProgress(m);
        return col;
    }

    private void updateProgress(MediaState m) {
        if (progress == null) return;
        long pos = m.currentPosition();
        progress.set(m.duration > 0 ? pos / (float) m.duration : 0f);
        if (elapsed != null) elapsed.setText(fmt(pos));
        if (remain != null) remain.setText(m.duration > 0 ? "-" + fmt(m.duration - pos) : "");
    }

    private ImageView ctlButton(int icon, int sizeDp, Runnable action) {
        ImageView b = new ImageView(getContext());
        b.setImageResource(icon);
        int pad = (int) (10 * dp);
        b.setPadding(pad, pad, pad, pad);
        b.setOnClickListener(v -> {
            v.performHapticFeedback(HapticFeedbackConstants.CONTEXT_CLICK);
            v.animate().scaleX(0.82f).scaleY(0.82f).setDuration(80)
                    .withEndAction(() -> v.animate().scaleX(1f).scaleY(1f).setDuration(160)
                            .setInterpolator(new OvershootInterpolator(2f)).start()).start();
            action.run();
        });
        int s = (int) ((sizeDp + 20) * dp);
        LayoutParams lp = new LayoutParams(s, s);
        lp.setMargins((int) (10 * dp), 0, (int) (10 * dp), 0);
        b.setLayoutParams(lp);
        return b;
    }

    /** Thanh tua: chạm / kéo để tua. */
    private final class ProgressLine extends View {
        private final Paint track = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint fillP = new Paint(Paint.ANTI_ALIAS_FLAG);
        private float frac;
        private boolean dragging;

        ProgressLine(Context c) {
            super(c);
            track.setColor(0xFF3A3A3C);
            fillP.setColor(Color.WHITE);
        }

        void set(float f) {
            if (dragging) return;
            frac = Math.max(0f, Math.min(1f, f));
            invalidate();
        }

        @Override
        protected void onDraw(Canvas c) {
            float hh = (dragging ? 7 : 4.5f) * dp;
            float cy = getHeight() / 2f;
            c.drawRoundRect(0, cy - hh / 2, getWidth(), cy + hh / 2, hh / 2, hh / 2, track);
            c.drawRoundRect(0, cy - hh / 2, getWidth() * frac, cy + hh / 2, hh / 2, hh / 2, fillP);
        }

        @Override
        public boolean onTouchEvent(MotionEvent e) {
            frac = Math.max(0f, Math.min(1f, e.getX() / Math.max(1, getWidth())));
            switch (e.getActionMasked()) {
                case MotionEvent.ACTION_DOWN:
                    dragging = true;
                    getParent().requestDisallowInterceptTouchEvent(true);
                    break;
                case MotionEvent.ACTION_UP:
                    dragging = false;
                    MediaState m = host.media();
                    if (m != null && m.duration > 0) host.mediaSeek((long) (frac * m.duration));
                    break;
                case MotionEvent.ACTION_CANCEL:
                    dragging = false;
                    break;
                default:
                    break;
            }
            invalidate();
            return true;
        }
    }

    // ---------------------------------------------------------------- Ứng dụng

    private View buildApps() {
        Context c = getContext();
        GridLayout g = new GridLayout(c);
        g.setColumnCount(5);
        List<ShareTargets.Target> apps = host.quickApps();
        for (ShareTargets.Target t : apps) g.addView(appCell(t.icon, t.label, () -> host.launch(t), false));
        g.addView(appCell(null, "Thêm", host::addQuickApps, true));
        LinearLayout col = new LinearLayout(c);
        col.setOrientation(VERTICAL);
        col.setGravity(Gravity.CENTER_HORIZONTAL);
        col.addView(g, new LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        // vào app ngân hàng (vd MB Bank) đòi tắt Trợ năng: tắt 5 phút rồi tự bật lại
        col.addView(chip("Tắt Trợ năng 5 phút (app ngân hàng)", 0xFF3A2A10, host::pauseAccessibility));
        return col;
    }

    private View appCell(android.graphics.drawable.Drawable icon, CharSequence label, Runnable action, boolean plus) {
        Context c = getContext();
        LinearLayout cell = new LinearLayout(c);
        cell.setOrientation(VERTICAL);
        cell.setGravity(Gravity.CENTER_HORIZONTAL);
        cell.setPadding(0, (int) (4 * dp), 0, (int) (8 * dp));
        int s = (int) (46 * dp);
        if (plus) {
            TextView p = new TextView(c);
            p.setText("+");
            p.setTextSize(24);
            p.setTextColor(Color.WHITE);
            p.setGravity(Gravity.CENTER);
            GradientDrawable d = new GradientDrawable();
            d.setShape(GradientDrawable.OVAL);
            d.setColor(0xFF2C2C2E);
            p.setBackground(d);
            cell.addView(p, new LayoutParams(s, s));
        } else {
            ImageView iv = new ImageView(c);
            iv.setImageDrawable(icon);
            cell.addView(iv, new LayoutParams(s, s));
        }
        TextView l = new TextView(c);
        l.setText(label);
        l.setTextColor(0xFFE5E5EA);
        l.setTextSize(11);
        l.setSingleLine(true);
        l.setEllipsize(TextUtils.TruncateAt.END);
        l.setGravity(Gravity.CENTER);
        l.setMaxWidth((int) (62 * dp));
        LayoutParams llp = new LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        llp.topMargin = (int) (5 * dp);
        cell.addView(l, llp);
        cell.setOnClickListener(v -> {
            v.performHapticFeedback(HapticFeedbackConstants.CONTEXT_CLICK);
            v.animate().scaleX(0.88f).scaleY(0.88f).setDuration(70)
                    .withEndAction(() -> {
                        v.animate().scaleX(1f).scaleY(1f).setDuration(120).start();
                        action.run();
                    }).start();
        });
        GridLayout.LayoutParams glp = new GridLayout.LayoutParams(
                GridLayout.spec(GridLayout.UNDEFINED), GridLayout.spec(GridLayout.UNDEFINED, 1f));
        glp.width = 0;
        cell.setLayoutParams(glp);
        return cell;
    }

    // ---------------------------------------------------------------- Hẹn giờ

    private View buildTimer() {
        Context c = getContext();
        long rem = host.timerRemaining();
        LinearLayout col = new LinearLayout(c);
        col.setOrientation(VERTICAL);
        col.setGravity(Gravity.CENTER_HORIZONTAL);
        if (rem > 0) {
            timerBig = new TextView(c);
            timerBig.setText(fmt(rem));
            timerBig.setTextSize(44);
            timerBig.setTypeface(Typeface.create("sans-serif-light", Typeface.NORMAL));
            timerBig.setFontFeatureSettings("tnum");
            timerBig.setTextColor(0xFFFF9F0A);
            timerBig.setGravity(Gravity.CENTER);
            col.addView(timerBig);
            LinearLayout row = new LinearLayout(c);
            row.setGravity(Gravity.CENTER);
            row.addView(chip("+1 phút", 0xFF2C2C2E, () -> host.addTimer(60_000)));
            row.addView(chip("Hủy", 0xFF5A1D1D, host::cancelTimer));
            col.addView(row);
        } else {
            GridLayout g = new GridLayout(c);
            g.setColumnCount(3);
            int[] mins = {1, 3, 5, 10, 15, 30};
            for (int mm : mins) {
                View ch = chip(mm + " phút", 0xFF2C2C2E, () -> host.startTimer(mm * 60_000L));
                GridLayout.LayoutParams glp = new GridLayout.LayoutParams(
                        GridLayout.spec(GridLayout.UNDEFINED), GridLayout.spec(GridLayout.UNDEFINED, 1f));
                glp.width = 0;
                glp.setMargins((int) (4 * dp), (int) (4 * dp), (int) (4 * dp), (int) (4 * dp));
                ch.setLayoutParams(glp);
                g.addView(ch);
            }
            col.addView(g, new LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        }
        return col;
    }

    private TextView chip(String text, int color, Runnable action) {
        TextView t = new TextView(getContext());
        t.setText(text);
        t.setTextColor(Color.WHITE);
        t.setTextSize(14);
        t.setTypeface(Typeface.DEFAULT_BOLD);
        t.setGravity(Gravity.CENTER);
        t.setPadding((int) (16 * dp), (int) (11 * dp), (int) (16 * dp), (int) (11 * dp));
        GradientDrawable d = new GradientDrawable();
        d.setCornerRadius(18 * dp);
        d.setColor(color);
        t.setBackground(d);
        LayoutParams lp = new LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.setMargins((int) (6 * dp), (int) (10 * dp), (int) (6 * dp), 0);
        t.setLayoutParams(lp);
        t.setOnClickListener(v -> {
            v.performHapticFeedback(HapticFeedbackConstants.CONTEXT_CLICK);
            action.run();
        });
        return t;
    }

    // ---------------------------------------------------------------- tiện ích

    private View hint(String msg, String button, Runnable action) {
        LinearLayout col = new LinearLayout(getContext());
        col.setOrientation(VERTICAL);
        col.setGravity(Gravity.CENTER_HORIZONTAL);
        TextView t = new TextView(getContext());
        t.setText(msg);
        t.setTextColor(0xFFAEAEB2);
        t.setTextSize(14);
        t.setGravity(Gravity.CENTER);
        t.setPadding(0, (int) (6 * dp), 0, (int) (6 * dp));
        col.addView(t);
        if (button != null) col.addView(chip(button, 0xFF0A84FF, action));
        return col;
    }

    private TextView smallText() {
        TextView t = new TextView(getContext());
        t.setTextColor(0xFF8E8E93);
        t.setTextSize(11);
        t.setFontFeatureSettings("tnum");
        return t;
    }

    static String fmt(long ms) {
        long s = Math.max(0, (ms + 999) / 1000);
        long h = s / 3600, m = (s % 3600) / 60, sec = s % 60;
        return h > 0 ? String.format(Locale.US, "%d:%02d:%02d", h, m, sec)
                : String.format(Locale.US, "%d:%02d", m, sec);
    }
}
