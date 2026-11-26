package vn.islanddrop;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapShader;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.graphics.Shader;
import android.view.Choreographer;
import android.view.GestureDetector;
import android.view.MotionEvent;
import android.view.View;

/**
 * Viên đảo luôn hiện, kiểu Atoll / ảnh mẫu Windows: [biểu tượng] • (camera) • [sóng / chữ].
 *
 * Trạng thái (ưu tiên từ cao xuống): SẠC (vài giây khi cắm sạc) > HẸN GIỜ > NHẠC > RẢNH.
 * Hiệu ứng:
 *  - bề ngang giãn/co theo lò xo (nảy nhẹ);
 *  - nội dung hiện dần + phóng từ 0.6 lên 1;
 *  - sóng nhạc 4 vạch nhảy theo nhịp, màu lấy từ ảnh bìa;
 *  - viền sáng mờ cùng màu ảnh bìa "thở" chậm khi đang phát.
 * Cử chỉ: chạm / vuốt xuống = mở bảng; vuốt ngang = chuyển bài.
 */
final class PillView extends View implements Choreographer.FrameCallback {

    interface Listener {
        void onTap();

        void onSwipeDown();

        void onSwipeHorizontal(int dir);

        /** Lò xo đã dừng ở bề ngang px (để thu cửa sổ lại cho khỏi chắn chạm). */
        void onWidthSettled(int px);
    }

    static final int K_IDLE = 0, K_MEDIA = 1, K_TIMER = 2, K_CHARGE = 3;

    private final Listener listener;
    private final float dp, idleW, activeW, h;
    private final Spring width, reveal;
    private final Paint bg = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint glow = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint artPaint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
    private final Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint stroke = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint text = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF r = new RectF();
    private final Matrix m = new Matrix();
    private final Path bolt = new Path();
    private final GestureDetector gestures;

    private int kind = K_IDLE;
    private Bitmap art;
    private int accent = 0xFF34C759;
    private boolean playing;
    private String label = "";

    private float t;             // thời gian chạy (giây) cho sóng nhạc / viền thở
    private final float[] level = new float[4];
    private boolean frameScheduled;
    private long lastNs;
    private int lastSettled = -1;

    PillView(Context c, Listener l) {
        super(c);
        listener = l;
        dp = c.getResources().getDisplayMetrics().density;
        idleW = 104 * dp;
        activeW = 168 * dp;   // vừa đủ, không che biểu tượng sóng / pin trên thanh trạng thái
        h = 30 * dp;
        width = new Spring(idleW, 260f, 0.68f, 0.4f);
        reveal = new Spring(0f, 220f, 0.85f, 0.002f);
        bg.setColor(Color.BLACK);
        stroke.setStyle(Paint.Style.STROKE);
        stroke.setStrokeCap(Paint.Cap.ROUND);
        text.setColor(Color.WHITE);
        text.setFakeBoldText(true);
        text.setTextSize(13 * c.getResources().getDisplayMetrics().scaledDensity);
        text.setTextAlign(Paint.Align.RIGHT);
        setLayerType(LAYER_TYPE_HARDWARE, null);

        gestures = new GestureDetector(c, new GestureDetector.SimpleOnGestureListener() {
            @Override
            public boolean onDown(MotionEvent e) {
                return true;
            }

            @Override
            public boolean onSingleTapUp(MotionEvent e) {
                listener.onTap();
                return true;
            }

            @Override
            public boolean onFling(MotionEvent e1, MotionEvent e2, float vx, float vy) {
                if (Math.abs(vy) > Math.abs(vx)) {
                    if (vy > 0) listener.onSwipeDown();
                } else {
                    listener.onSwipeHorizontal(vx > 0 ? 1 : -1);
                }
                return true;
            }
        });
    }

    static int idleWidthPx(Context c) {
        return (int) (104 * c.getResources().getDisplayMetrics().density);
    }

    static int maxWidthPx(Context c) {
        // chừa chỗ cho lò xo vượt quá đích và viền sáng
        return (int) (182 * c.getResources().getDisplayMetrics().density);
    }

    static int heightPx(Context c) {
        return (int) (30 * c.getResources().getDisplayMetrics().density);
    }

    /** Đổi trạng thái hiển thị. art/accent chỉ dùng cho K_MEDIA; label cho TIMER/CHARGE. */
    void setState(int kind, Bitmap art, boolean playing, String label) {
        boolean kindChanged = kind != this.kind;
        this.kind = kind;
        this.playing = playing;
        this.label = label == null ? "" : label;
        if (art != this.art) {
            this.art = art;
            if (art != null) {
                artPaint.setShader(new BitmapShader(art, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP));
                accent = accentOf(art);
            } else {
                artPaint.setShader(null);
                accent = 0xFF34C759;
            }
        }
        if (kind == K_TIMER) accent = 0xFFFF9F0A;
        if (kind == K_CHARGE) accent = 0xFF30D158;
        width.target = kind == K_IDLE ? idleW : activeW;
        reveal.target = kind == K_IDLE ? 0f : 1f;
        // đổi loại nội dung: cho nội dung "nháy" vào lại để chuyển cảnh mềm
        if (kindChanged && kind != K_IDLE && reveal.value > 0.5f) reveal.value = 0.35f;
        lastSettled = -1;
        kick();
    }

    int kind() {
        return kind;
    }

    @Override
    public boolean onTouchEvent(MotionEvent e) {
        return gestures.onTouchEvent(e);
    }

    // ---------------------------------------------------------------- khung hình

    private void kick() {
        if (frameScheduled) return;
        frameScheduled = true;
        lastNs = 0;
        Choreographer.getInstance().postFrameCallback(this);
    }

    @Override
    public void doFrame(long now) {
        frameScheduled = false;
        float dt = lastNs == 0 ? 1f / 120f : Math.min((now - lastNs) / 1e9f, 1f / 30f);
        lastNs = now;
        boolean moving = width.step(dt);
        moving |= reveal.step(dt);
        boolean animatedContent = (kind == K_MEDIA && playing) || kind == K_CHARGE;
        if (animatedContent) {
            t += dt;
            moving = true;
        }
        updateLevels(dt);
        invalidate();
        if (moving) {
            frameScheduled = true;
            Choreographer.getInstance().postFrameCallback(this);
        } else {
            lastNs = 0;
            int w = (int) Math.ceil(width.value);
            if (w != lastSettled) {
                lastSettled = w;
                listener.onWidthSettled(w);
            }
        }
    }

    /** Sóng nhạc: mỗi vạch đuổi theo một độ cao "giả nhịp" khác nhau, mượt chứ không giật. */
    private void updateLevels(float dt) {
        float k = 1f - (float) Math.exp(-dt / 0.06f);
        for (int i = 0; i < level.length; i++) {
            float target;
            if (kind == K_MEDIA && playing) {
                double a = Math.sin(t * (5.3 + i * 1.7) + i * 1.9);
                double b = Math.sin(t * (2.1 + i * 0.9) + i);
                target = (float) (0.25 + 0.75 * Math.abs(a * 0.65 + b * 0.35));
            } else {
                target = 0.18f;
            }
            level[i] += (target - level[i]) * k;
        }
    }

    @Override
    protected void onDetachedFromWindow() {
        Choreographer.getInstance().removeFrameCallback(this);
        frameScheduled = false;
        super.onDetachedFromWindow();
    }

    // ---------------------------------------------------------------- vẽ

    @Override
    protected void onDraw(Canvas c) {
        float w = Math.min(width.value, getWidth());
        float cx = getWidth() / 2f;
        r.set(cx - w / 2f, 0, cx + w / 2f, h);
        float rad = h / 2f;
        float a = clamp01(reveal.value);

        // viền sáng màu ảnh bìa, "thở" chậm khi đang phát (giống Atoll)
        if (a > 0.05f && kind != K_IDLE) {
            float breath = (kind == K_MEDIA && playing) ? 0.55f + 0.45f * (float) Math.sin(t * 1.6) : 0.6f;
            glow.setStyle(Paint.Style.STROKE);
            glow.setStrokeWidth(1.4f * dp);
            glow.setColor(accent);
            glow.setAlpha((int) (110 * a * breath));
            c.drawRoundRect(r, rad, rad, glow);
        }
        c.drawRoundRect(r, rad, rad, bg);
        if (a < 0.02f || kind == K_IDLE) return;

        float s = 0.6f + 0.4f * a;              // nội dung phóng dần vào
        int alpha = (int) (255 * a);
        float lcx = r.left + rad + 3 * dp;     // tâm ô trái
        float rcx = r.right - rad - 3 * dp;    // tâm ô phải
        float cy = h / 2f;

        // ---- ô trái: biểu tượng
        c.save();
        c.scale(s, s, lcx, cy);
        if (kind == K_MEDIA) {
            float ar = 10.5f * dp;
            if (art != null) {
                float sc = Math.max(2 * ar / art.getWidth(), 2 * ar / art.getHeight());
                m.reset();
                m.setScale(sc, sc);
                m.postTranslate(lcx - art.getWidth() * sc / 2f, cy - art.getHeight() * sc / 2f);
                artPaint.getShader().setLocalMatrix(m);
                artPaint.setAlpha(alpha);
                c.drawRoundRect(lcx - ar, cy - ar, lcx + ar, cy + ar, 6 * dp, 6 * dp, artPaint);
            } else {
                fill.setColor(accent);
                fill.setAlpha(alpha);
                c.drawCircle(lcx, cy, ar, fill);
            }
        } else if (kind == K_TIMER) {
            stroke.setColor(accent);
            stroke.setAlpha(alpha);
            stroke.setStrokeWidth(2 * dp);
            c.drawCircle(lcx, cy, 8.5f * dp, stroke);
            c.drawLine(lcx, cy, lcx, cy - 5 * dp, stroke);
            c.drawLine(lcx, cy, lcx + 3.5f * dp, cy + 2 * dp, stroke);
        } else if (kind == K_CHARGE) {
            drawBolt(c, lcx, cy, alpha);
        }
        c.restore();

        // chấm ngăn cách như ảnh mẫu
        fill.setColor(Color.WHITE);
        fill.setAlpha((int) (150 * a));
        c.drawCircle(lcx + 18 * dp, cy, 1.6f * dp, fill);
        // chấm phải nằm ngay trước sóng nhạc / chữ (chữ dài như "Hết giờ" thì lùi theo)
        float rightStart = kind == K_MEDIA ? rcx - 11 * dp : rcx + 9 * dp - text.measureText(label);
        c.drawCircle(rightStart - 7 * dp, cy, 1.6f * dp, fill);

        // ---- ô phải: sóng nhạc hoặc chữ
        c.save();
        c.scale(s, s, rcx, cy);
        if (kind == K_MEDIA) {
            fill.setColor(accent);
            fill.setAlpha(alpha);
            float bw = 3 * dp, gap = 2.6f * dp, maxH = 15 * dp;
            float total = level.length * bw + (level.length - 1) * gap;
            float x = rcx - total / 2f;
            for (float lv : level) {
                float bh = Math.max(3 * dp, lv * maxH);
                c.drawRoundRect(x, cy - bh / 2f, x + bw, cy + bh / 2f, bw / 2f, bw / 2f, fill);
                x += bw + gap;
            }
        } else {
            text.setColor(accent);
            text.setAlpha(alpha);
            c.drawText(label, rcx + 9 * dp, cy - (text.descent() + text.ascent()) / 2f, text);
        }
        c.restore();
    }

    private void drawBolt(Canvas c, float x, float y, int alpha) {
        float u = dp;
        bolt.reset();
        bolt.moveTo(x + 1.5f * u, y - 9 * u);
        bolt.lineTo(x - 5 * u, y + 1.5f * u);
        bolt.lineTo(x - 0.5f * u, y + 1.5f * u);
        bolt.lineTo(x - 1.5f * u, y + 9 * u);
        bolt.lineTo(x + 5 * u, y - 1.5f * u);
        bolt.lineTo(x + 0.5f * u, y - 1.5f * u);
        bolt.close();
        // nhịp sáng nhẹ khi đang sạc
        float pulse = 0.75f + 0.25f * (float) Math.sin(t * 4);
        fill.setColor(accent);
        fill.setAlpha((int) (alpha * pulse));
        c.drawPath(bolt, fill);
    }

    /** Màu nhấn từ ảnh bìa: màu trung bình, đẩy độ sáng/bão hòa lên cho nổi trên nền đen. */
    private static int accentOf(Bitmap b) {
        try {
            Bitmap s = Bitmap.createScaledBitmap(b, 8, 8, true);
            long rr = 0, gg = 0, bb = 0;
            int n = 0;
            for (int y = 0; y < 8; y++) {
                for (int x = 0; x < 8; x++) {
                    int p = s.getPixel(x, y);
                    rr += (p >> 16) & 255;
                    gg += (p >> 8) & 255;
                    bb += p & 255;
                    n++;
                }
            }
            if (s != b) s.recycle();
            float[] hsv = new float[3];
            Color.RGBToHSV((int) (rr / n), (int) (gg / n), (int) (bb / n), hsv);
            hsv[1] = Math.max(hsv[1], 0.45f);
            hsv[2] = Math.max(hsv[2], 0.85f);
            return Color.HSVToColor(hsv);
        } catch (RuntimeException ex) {
            return 0xFF34C759;
        }
    }

    private static float clamp01(float v) {
        return v < 0f ? 0f : (v > 1f ? 1f : v);
    }
}
