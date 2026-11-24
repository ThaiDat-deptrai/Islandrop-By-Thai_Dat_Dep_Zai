package vn.islanddrop;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapShader;
import android.graphics.Canvas;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Shader;
import android.view.View;
import android.view.animation.PathInterpolator;

/**
 * Hiệu ứng "nhấc ảnh lên đảo": bản sao của ảnh đang xem thu nhỏ, bo góc dần,
 * bay từ vị trí ảnh trên màn hình lên chỗ camera rồi tan vào đảo.
 * Chạy trong cửa sổ toàn màn hình KHÔNG nhận chạm, độ mờ 0.8 (chạm vẫn xuyên qua).
 */
final class FlyView extends View {

    interface Listener {
        /** Gọi khi ảnh gần tới đảo (để khay bắt đầu giãn ra, chuyển động liền mạch). */
        void onArriving();

        /** Gọi khi kết thúc, để gỡ cửa sổ. */
        void onFinished();
    }

    private static final long DURATION_MS = 460;

    private final Bitmap bmp;
    private final RectF from, to;
    private final RectF cur = new RectF();
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
    private final Paint shade = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Matrix m = new Matrix();
    private final float maxRadius, dp;
    private float t;
    private boolean arrived;
    private ValueAnimator anim;

    FlyView(Context ctx, Bitmap bmp, RectF from, RectF to) {
        super(ctx);
        this.bmp = bmp;
        this.from = from;
        this.to = to;
        dp = ctx.getResources().getDisplayMetrics().density;
        maxRadius = to.height() / 2f;
        BitmapShader sh = new BitmapShader(bmp, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP);
        paint.setShader(sh);
        shade.setColor(0xFF000000);
        setLayerType(LAYER_TYPE_HARDWARE, null);
    }

    void start(Listener l) {
        anim = ValueAnimator.ofFloat(0f, 1f);
        anim.setDuration(DURATION_MS);
        // chậm-nhanh-chậm, phần cuối rất êm giống đảo Vivo
        anim.setInterpolator(new PathInterpolator(0.32f, 0.72f, 0.12f, 1f));
        anim.addUpdateListener(a -> {
            t = (float) a.getAnimatedValue();
            if (!arrived && t > 0.62f) {
                arrived = true;
                l.onArriving();
            }
            invalidate();
        });
        anim.addListener(new android.animation.AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(android.animation.Animator a) {
                l.onFinished();
            }
        });
        anim.start();
    }

    void cancel() {
        if (anim != null) anim.cancel();
    }

    @Override
    protected void onDraw(Canvas c) {
        // vị trí: nội suy giữa khung ảnh ban đầu và viên đảo, kéo nhẹ lên trên theo đường cong
        float k = t;
        float lift = (float) Math.sin(Math.PI * k) * 18 * dp;   // nhấc lên một chút giữa đường
        cur.set(lerp(from.left, to.left, k), lerp(from.top, to.top, k) - lift,
                lerp(from.right, to.right, k), lerp(from.bottom, to.bottom, k) - lift);
        float r = lerp(6 * dp, maxRadius, k);

        // ảnh lấp đầy khung (center-crop) để khi thu thành viên thuốc vẫn đẹp
        float sx = cur.width() / bmp.getWidth(), sy = cur.height() / bmp.getHeight();
        float s = Math.max(sx, sy);
        m.reset();
        m.setScale(s, s);
        m.postTranslate(cur.centerX() - bmp.getWidth() * s / 2f, cur.centerY() - bmp.getHeight() * s / 2f);
        paint.getShader().setLocalMatrix(m);

        // gần tới đảo thì ảnh tan dần, phủ đen để hòa vào màu đảo
        float fade = clamp01((k - 0.55f) / 0.45f);
        paint.setAlpha((int) (255 * (1f - fade * 0.85f)));
        c.drawRoundRect(cur, r, r, paint);
        if (fade > 0f) {
            shade.setAlpha((int) (255 * fade));
            c.drawRoundRect(cur, r, r, shade);
        }
    }

    @Override
    protected void onDetachedFromWindow() {
        cancel();
        super.onDetachedFromWindow();
    }

    private static float lerp(float a, float b, float t) {
        return a + (b - a) * t;
    }

    private static float clamp01(float v) {
        return v < 0f ? 0f : (v > 1f ? 1f : v);
    }
}
