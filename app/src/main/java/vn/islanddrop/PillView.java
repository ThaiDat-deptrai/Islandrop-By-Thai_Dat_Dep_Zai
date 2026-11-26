package vn.islanddrop;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapShader;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.ComposeShader;
import android.graphics.LinearGradient;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PorterDuff;
import android.graphics.RectF;
import android.graphics.Shader;
import android.view.Choreographer;
import android.view.GestureDetector;
import android.view.MotionEvent;
import android.view.View;
import android.view.animation.PathInterpolator;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.Random;

/**
 * Viên đảo thu gọn — chuyển 1:1 từ Atoll (Dynamic Island cho Windows), theo thông số đọc trong code:
 *  - thân: viên thuốc đen, bán kính = cao/2, viền trên trắng mờ #24FFFFFF → #06FFFFFF (0..70% chiều cao);
 *  - bóng: đen, blur 34, lệch (0, +6), độ mờ 0.38, chỉ ngoài viên thuốc; bài mới: bóng loé màu ảnh bìa 1.3s;
 *  - aura "aurora" #5B8CFF/#A066FF/#3DE1FF (nhân vật cua: cam Claude) trượt 1 chu kỳ = w/2 mỗi 4s,
 *    dâng từ mép dưới (0 tới 50%, α .44 ở 85%, α .69 ở mép), thở 0.45↔0.9 (3s mỗi chiều), viền dưới α .55;
 *  - bề ngang: lò xo k 380 / c 24; chạm: nhún về 96% (k 520 / c 18), mỗi lần đổi trạng thái "đẩy" −160/−100;
 *  - nhạc: mép dưới phồng một bọng đi lang thang (bump = 5.5·level), 4 vạch sóng 3×16 dao động
 *    0.25↔1 với chu kỳ ngẫu nhiên 280–560 ms, màu chủ đạo của ảnh bìa;
 *  - hẹn giờ: vòng 22 dp (nền #38FFFFFF, cung #30D158, nét 2.5, từ 12 giờ theo chiều kim đồng hồ);
 *  - vào lại sau khi đóng bảng: trễ 90 ms, mờ→rõ 350 ms, phóng 0.9→1 trong 600 ms (ease 0.16,1,0.3,1).
 * Điện thoại có lỗ camera giữa viên thuốc nên chữ giữa của Atoll được dời sang bên phải.
 *
 * Cửa sổ chứa view này KHÔNG nhận chạm; chạm đi qua một cửa sổ cảm ứng nhỏ (handleTouch).
 */
final class PillView extends View implements Choreographer.FrameCallback {

    interface Listener {
        void onTap();

        void onSwipeDown();

        void onSwipeHorizontal(int dir);

        /** Bề ngang viên thuốc (px) thay đổi: để chỉnh cửa sổ cảm ứng cho khớp. */
        void onPillWidth(int px);
    }

    static final int K_IDLE = 0, K_MEDIA = 1, K_TIMER = 2, K_CHARGE = 3;
    static final int GREEN = 0xFF30D158;

    private static final float PILL_H = 34f;          // dp (Atoll 36; nhỏ hơn chút cho vừa thanh trạng thái)
    private static final float TOP = 2f;               // dp từ mép trên cửa sổ
    private static final float SIDE = 24f;             // dp lề hai bên cho bóng
    private static final float IDLE_W = 184f, MEDIA_W = 212f, TIMER_W = 192f;

    private final Listener listener;
    private final float dp;
    private final Spring width;
    private final Spring press = new Spring(100f, 520f, 18f / (2f * (float) Math.sqrt(520f)), 0.15f);
    private final Spring jump = new Spring(0f, 260f, 9f / (2f * (float) Math.sqrt(260f)), 0.05f);

    private final Paint body = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint shadow = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint rim = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint aura = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint auraLine = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint artPaint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
    private final Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint stroke = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint text = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path shape = new Path();
    private final RectF tmp = new RectF();
    private final Matrix m = new Matrix();
    private final GestureDetector gestures;
    private final SimpleDateFormat clockFmt = new SimpleDateFormat("HH:mm", Locale.US);
    private final Random rng = new Random();
    private final PathInterpolator cssEase = new PathInterpolator(0.25f, 0.1f, 0.25f, 1f);
    private final PathInterpolator contentEase = new PathInterpolator(0.16f, 1f, 0.3f, 1f);

    // trạng thái hiển thị
    private int kind = K_IDLE;
    private Bitmap art;
    private int barColor = GREEN;
    private boolean playing;
    private String label = "";
    private float progress;
    private String trackKey;
    private Characters.Ch character = Characters.get("eyes");
    private int lastEmotion = -1;
    // shader aura/viền được tạo lại chỉ khi kích thước đổi (tiết kiệm RAM, không tạo mới mỗi khung hình)
    private ComposeShader auraShader, auraLineShader;
    private LinearGradient rimShader;
    private float shaderW = -1, shaderL = -1;
    private final Matrix auraM = new Matrix();
    private int[] auraColors = Characters.AURORA;

    // hoạt cảnh
    private final Characters.State cs = new Characters.State();
    private float t;
    private float audioLevel, dripPhase;
    private final float[] barPeriod = new float[4], barPhase = new float[4], barScale = {0.4f, 0.4f, 0.4f, 0.4f};
    private float nextBlink = 2f, nextLook = 1f, lookTX, lookTY;
    private float beatTarget;
    private float auraIn;                 // 0→1 trong 700 ms khi aura xuất hiện
    private float glowT = -1f;            // bài mới: bóng loé màu (0..1.3s)
    private int glowColor = GREEN;
    private float enterT = -1f;           // hiệu ứng vào lại sau khi đóng bảng
    private float emotionT = -1f;
    private boolean frameScheduled;
    private long lastNs;
    private int lastReportedW = -1;

    PillView(Context c, Listener l) {
        super(c);
        listener = l;
        dp = c.getResources().getDisplayMetrics().density;
        width = new Spring(IDLE_W * dp, 380f, 24f / (2f * (float) Math.sqrt(380f)), 0.15f * dp);

        body.setColor(Color.BLACK);
        shadow.setColor(Color.BLACK);
        rim.setStyle(Paint.Style.STROKE);
        rim.setStrokeWidth(1f * dp);
        auraLine.setStyle(Paint.Style.STROKE);
        auraLine.setStrokeWidth(2f * dp);
        stroke.setStyle(Paint.Style.STROKE);
        stroke.setStrokeCap(Paint.Cap.ROUND);
        text.setColor(Color.WHITE);
        text.setTextSize(13.5f * c.getResources().getDisplayMetrics().scaledDensity);
        text.setTypeface(android.graphics.Typeface.create("sans-serif-medium", android.graphics.Typeface.NORMAL));
        text.setFontFeatureSettings("tnum");
        for (int i = 0; i < 4; i++) {
            barPeriod[i] = 0.28f + rng.nextFloat() * 0.28f;   // 280–560 ms
            barPhase[i] = rng.nextFloat() * 6.28f;
        }
        setLayerType(LAYER_TYPE_HARDWARE, null);
        cs.greet = 0f;                                         // chào khi vừa hiện

        gestures = new GestureDetector(c, new GestureDetector.SimpleOnGestureListener() {
            @Override
            public boolean onDown(MotionEvent e) {
                press.target = 96f;
                kick();
                return true;
            }

            @Override
            public boolean onSingleTapUp(MotionEvent e) {
                // như Atoll: chạm trúng nhân vật (±4) thì nhân vật thể hiện cảm xúc, chạm chỗ khác thì mở đảo
                if (touchOnCharacter) emote();
                else listener.onTap();
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

    // ---------------------------------------------------------------- kích thước cửa sổ

    static int windowWidthPx(Context c) {
        float d = c.getResources().getDisplayMetrics().density;
        return (int) ((MEDIA_W + 2 * SIDE) * d);
    }

    static int windowHeightPx(Context c) {
        float d = c.getResources().getDisplayMetrics().density;
        return (int) ((TOP + PILL_H + 30f) * d);
    }

    static int pillHeightPx(Context c) {
        float d = c.getResources().getDisplayMetrics().density;
        return (int) ((TOP + PILL_H) * d);
    }

    static int topPx(Context c) {
        return 0;
    }

    float pillWidth() {
        return width.value;
    }

    // ---------------------------------------------------------------- trạng thái

    void setCharacter(String id) {
        character = Characters.get(id);
        auraColors = character.aura() != null ? character.aura() : Characters.AURORA;
        shaderW = -1;                                // màu aura đổi: dựng lại shader
        cs.greet = 0f;
        kick();
    }

    /** art cho K_MEDIA; label cho TIMER/CHARGE; progress 0..1 cho vòng hẹn giờ; trackKey để biết bài mới. */
    void setState(int kind, Bitmap art, boolean playing, String label, float progress, String trackKey) {
        boolean kindChanged = kind != this.kind;
        float oldTarget = width.target;
        this.kind = kind;
        this.playing = playing;
        this.label = label == null ? "" : label;
        this.progress = Math.max(0f, Math.min(0.9999f, progress));
        if (art != this.art) {
            this.art = art;
            artPaint.setShader(art == null ? null
                    : new BitmapShader(art, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP));
            barColor = art == null ? GREEN : dominantColor(art);
        }
        // bài mới (đổi từ bài này sang bài khác): bóng loé màu ảnh bìa + nhân vật "Cheer"
        if (kind == K_MEDIA && trackKey != null && this.trackKey != null && !trackKey.equals(this.trackKey)) {
            glowT = 0f;
            glowColor = barColor;
            jump.velocity -= 70f;          // Cheer: nhảy + vui 1.3s
            cs.happy = 1.3f;
        }
        this.trackKey = kind == K_MEDIA ? trackKey : this.trackKey;

        width.target = (kind == K_MEDIA ? MEDIA_W : kind == K_IDLE ? IDLE_W : TIMER_W) * dp;
        if (kindChanged) {
            // MorphPush: cú "đẩy" nhỏ mỗi lần đổi trạng thái
            press.velocity -= width.target > oldTarget ? 160f : 100f;
        }
        reportWidth();
        kick();
    }

    int kind() {
        return kind;
    }

    /** Hiệu ứng vào lại khi bảng mở rộng thu về (MorphIn của Atoll). */
    void playEnter() {
        enterT = 0f;
        kick();
    }

    /** Chạm từ cửa sổ cảm ứng; offsetX = toạ độ x của cửa sổ cảm ứng trong view này. */
    boolean handleTouch(MotionEvent e, float offsetX) {
        int a = e.getActionMasked();
        if (a == MotionEvent.ACTION_DOWN) {
            // mắt nhìn theo ngón tay
            float x = e.getX() + offsetX;
            float boxL = getWidth() / 2f - width.value / 2f + 15 * dp;
            touchOnCharacter = (kind == K_IDLE || kind == K_MEDIA) && !"none".equals(character.id)
                    && x >= boxL - 4 * dp && x <= boxL + 34 * dp;
            lookTX = Math.max(-1f, Math.min(1f, (x - getWidth() / 2f) / (85f * dp)));
            lookTY = 0.6f;
            nextLook = t + 2.5f;
        }
        boolean r = gestures.onTouchEvent(e);
        if (a == MotionEvent.ACTION_UP || a == MotionEvent.ACTION_CANCEL) {
            press.target = 100f;
            kick();
        }
        return r;
    }

    private boolean touchOnCharacter;

    /** Emote của Atoll: cảm xúc ngẫu nhiên (không lặp lại cái trước), 2.4s; ngạc nhiên nhảy cao hơn. */
    private void emote() {
        int e;
        do {
            e = rng.nextInt(7);
        } while (e == lastEmotion);
        lastEmotion = e;
        cs.emotion = e;
        emotionT = 0f;
        cs.emotionT = 0f;
        jump.velocity -= e == Characters.SURPRISED ? 66f : 33f;
        performHapticFeedback(android.view.HapticFeedbackConstants.CONTEXT_CLICK);
        kick();
    }

    private void reportWidth() {
        int w = (int) Math.ceil(Math.max(width.value, width.target));
        if (w != lastReportedW) {
            lastReportedW = w;
            listener.onPillWidth(w);
        }
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
        float dt = lastNs == 0 ? 1f / 60f : Math.min((now - lastNs) / 1e9f, 0.05f);
        lastNs = now;
        t += dt;
        boolean moving = width.step(dt);
        moving |= press.step(dt);
        moving |= jump.step(dt);
        if (!moving && lastReportedW != (int) Math.ceil(width.value)) reportWidth();
        stepCharacter(dt);
        stepMusic(dt);
        auraIn = Math.min(1f, auraIn + dt / 0.7f);
        if (glowT >= 0f) glowT = glowT + dt > 1.3f ? -1f : glowT + dt;
        if (enterT >= 0f) enterT = enterT + dt > 0.7f ? -1f : enterT + dt;
        invalidate();
        if (isAttachedToWindow()) {
            frameScheduled = true;
            // aura luôn trượt/thở; khi không có gì động mạnh thì 30 fps cho nhẹ máy
            boolean lively = moving || playing || glowT >= 0 || enterT >= 0 || cs.emotion >= 0 || cs.greet >= 0
                    || cs.happy > 0 || cs.blink > 0;
            if (lively) {
                Choreographer.getInstance().postFrameCallback(this);
            } else {
                lastNs = 0;
                t += 1f / 30f;
                Choreographer.getInstance().postFrameCallbackDelayed(this, 33);
            }
        }
    }

    private void stepCharacter(float dt) {
        cs.t = t;
        // chớp mắt: lần đầu ở 2s, sau đó mỗi 2.2–6.2s; mở lại với tốc độ 9/s
        if (t >= nextBlink) {
            cs.blink = 1f;
            nextBlink = t + 2.2f + rng.nextFloat() * 4f;
        }
        cs.blink = Math.max(0f, cs.blink - 9f * dt);
        // không có chuột: thỉnh thoảng đảo mắt nhìn quanh
        if (t >= nextLook) {
            lookTX = (rng.nextFloat() * 2f - 1f) * 0.7f;
            lookTY = (rng.nextFloat() * 2f - 1f) * 0.45f;
            nextLook = t + 1.5f + rng.nextFloat() * 3f;
        }
        float k = Math.min(1f, 12f * dt);
        cs.lookX += (lookTX - cs.lookX) * k;
        cs.lookY += (lookTY - cs.lookY) * k;
        cs.music = kind == K_MEDIA && playing;
        float bt = cs.music ? beatTarget : 0f;
        cs.beat += (bt - cs.beat) * Math.min(1f, dt * (bt > cs.beat ? 30f : 6f));
        cs.jump = jump.value;
        if (cs.greet >= 0f) cs.greet = cs.greet + dt > 1.6f ? -1f : cs.greet + dt;
        if (cs.happy > 0f) cs.happy = Math.max(0f, cs.happy - dt);
        if (cs.emotion >= 0) {
            emotionT += dt;
            cs.emotionT = emotionT;
            if (emotionT > Characters.EMOTE_TIME) cs.emotion = -1;
        }
    }

    /** Mức âm "giả" (Android không cho đọc âm thanh app khác nếu không xin ghi âm) để bọng + nhân vật nhún. */
    private void stepMusic(float dt) {
        float peak = 0f;
        if (kind == K_MEDIA && playing) {
            double n = 0.5 + 0.25 * Math.sin(t * 9.1) + 0.15 * Math.sin(t * 15.7 + 1.3) + 0.1 * Math.sin(t * 3.3);
            peak = (float) Math.max(0, Math.min(1, n));
        }
        float a = peak > audioLevel ? 1f - (float) Math.pow(0.55, dt * 60) : 1f - (float) Math.pow(0.92, dt * 60);
        audioLevel += (peak - audioLevel) * a;
        beatTarget = Math.min(1f, peak * 1.6f) * 0.8f;
        dripPhase += dt * (2.2f + audioLevel * 5f);
        for (int i = 0; i < 4; i++) {
            float target;
            if (kind == K_MEDIA && playing) {
                // DoubleAnimation 0.25 → 1, SineEase, auto-reverse, chu kỳ ngẫu nhiên
                target = 0.625f - 0.375f * (float) Math.cos(Math.PI * (t + barPhase[i]) / barPeriod[i]);
                barScale[i] = target;
            } else {
                barScale[i] += (0.4f - barScale[i]) * Math.min(1f, dt * 10f);
            }
        }
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        kick();
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
        float w = Math.min(width.value, getWidth() - 2 * SIDE * dp);
        float h = PILL_H * dp;
        float cx = getWidth() / 2f;
        float top = TOP * dp;
        float r = h / 2f;
        float l = cx - w / 2f;

        // hiệu ứng vào lại (MorphIn): trễ 90 ms, rõ dần 350 ms, phóng 0.9 → 1 trong 600 ms
        float enterAlpha = 1f, enterScale = 1f;
        if (enterT >= 0f) {
            float e = Math.max(0f, enterT - 0.09f);
            enterAlpha = cssEase.getInterpolation(Math.min(1f, e / 0.35f));
            enterScale = 0.9f + 0.1f * contentEase.getInterpolation(Math.min(1f, e / 0.6f));
        }
        float sc = press.value / 100f * enterScale;
        int save = c.save();
        c.scale(sc, sc, cx, top);   // gốc phóng: giữa mép trên

        float bump = audioLevel > 0.02f ? audioLevel * 5.5f * dp : 0f;
        float bumpX = cx + (float) Math.sin(dripPhase * 0.6f) * w * 0.22f;
        buildShape(shape, l, top, w, h, r, bump, bumpX);

        // ---- bóng (ngoài viên thuốc) — bài mới thì loé màu ảnh bìa rồi về đen
        float g = glowT >= 0f ? 1f - (float) Math.pow(1f - glowT / 1.3f, 3) : 1f;
        int shCol = glowT >= 0f ? lerpColor(glowColor, Color.BLACK, g) : Color.BLACK;
        float shA = glowT >= 0f ? 1f + (0.38f - 1f) * g : 0.38f;
        float shBlur = (glowT >= 0f ? 26f + (17f - 26f) * g : 17f) * dp;
        float shDy = (glowT >= 0f ? 6f * g : 6f) * dp;
        shadow.setShadowLayer(shBlur, 0, shDy, withAlpha(shCol, (int) (255 * shA * enterAlpha)));
        c.drawPath(shape, shadow);

        // ---- thân + viền trên trắng mờ
        body.setAlpha((int) (255 * enterAlpha));
        c.drawPath(shape, body);
        ensureShaders(l, top, w, h);
        rim.setAlpha((int) (255 * enterAlpha));
        c.drawPath(shape, rim);

        // ---- aura
        c.save();
        c.clipPath(shape);
        drawAura(c, l, top, w, h, enterAlpha);

        // ---- nội dung (cắt theo viên thuốc)
        drawContent(c, l, top, w, h, r, enterAlpha);
        c.restore();
        c.restoreToCount(save);
    }

    /** Viên thuốc; mép dưới có một bọng phồng khi có nhạc (PillGeometry của Atoll). */
    private void buildShape(Path p, float l, float top, float w, float h, float r, float bump, float bumpX) {
        float b = top + h;
        p.reset();
        p.moveTo(l + r, top);
        p.lineTo(l + w - r, top);
        tmp.set(l + w - 2 * r, top, l + w, top + 2 * r);
        p.arcTo(tmp, -90, 90, false);
        tmp.set(l + w - 2 * r, b - 2 * r, l + w, b);
        p.arcTo(tmp, 0, 90, false);
        float half = Math.min(40 * dp, Math.max(6 * dp, (w - 2 * r) / 2f));
        if (bump > 0.2f * dp && w - 2 * r > 14 * dp) {
            float bx = Math.max(l + r + half, Math.min(l + w - r - half, bumpX));
            p.lineTo(bx + half, b);
            p.cubicTo(bx + 0.5f * half, b, bx + 0.45f * half, b + bump, bx, b + bump);
            p.cubicTo(bx - 0.45f * half, b + bump, bx - 0.5f * half, b, bx - half, b);
        }
        p.lineTo(l + r, b);
        tmp.set(l, b - 2 * r, l + 2 * r, b);
        p.arcTo(tmp, 90, 90, false);
        tmp.set(l, top, l + 2 * r, top + 2 * r);
        p.arcTo(tmp, 180, 90, false);
        p.close();
    }

    /**
     * Shader của aura / viền chỉ dựng lại khi bề ngang hoặc vị trí đổi (sau khi lò xo dừng thì không đổi nữa),
     * không tạo object mới mỗi khung hình. Hiệu ứng trượt dùng ma trận dịch trên chính ComposeShader:
     * mặt nạ là gradient dọc nên dịch ngang không ảnh hưởng.
     */
    private void ensureShaders(float l, float top, float w, float h) {
        if (Math.abs(w - shaderW) < 0.5f && Math.abs(l - shaderL) < 0.5f && auraShader != null) return;
        shaderW = w;
        shaderL = l;
        float band = Math.max(1f, w / 2f);
        int[] cols = {auraColors[0], auraColors[1], auraColors[2], auraColors[0]};
        LinearGradient colors = new LinearGradient(l, 0, l + band, 0, cols, null, Shader.TileMode.REPEAT);
        LinearGradient mask = new LinearGradient(0, top, 0, top + h,
                new int[]{0x00000000, 0x00000000, 0x70000000, 0xB0000000},
                new float[]{0f, 0.5f, 0.85f, 1f}, Shader.TileMode.CLAMP);
        auraShader = new ComposeShader(colors, mask, PorterDuff.Mode.DST_IN);
        LinearGradient colors2 = new LinearGradient(l, 0, l + band, 0, cols, null, Shader.TileMode.REPEAT);
        LinearGradient lineMask = new LinearGradient(0, top, 0, top + h,
                new int[]{0x00000000, 0x00000000, 0xFF000000}, new float[]{0f, 0.45f, 1f}, Shader.TileMode.CLAMP);
        auraLineShader = new ComposeShader(colors2, lineMask, PorterDuff.Mode.DST_IN);
        rimShader = new LinearGradient(0, top, 0, top + h * 0.7f, 0x24FFFFFF, 0x06FFFFFF, Shader.TileMode.CLAMP);
        aura.setShader(auraShader);
        auraLine.setShader(auraLineShader);
        rim.setShader(rimShader);
    }

    private void drawAura(Canvas c, float l, float top, float w, float h, float enterAlpha) {
        float band = Math.max(1f, w / 2f);
        auraM.setTranslate((t % 4f) / 4f * band, 0);   // trượt sang phải 1 chu kỳ mỗi 4s
        auraShader.setLocalMatrix(auraM);
        auraLineShader.setLocalMatrix(auraM);
        // thở: 0.45 ↔ 0.9, 3s mỗi chiều, sine in-out
        float breath = 0.675f - 0.225f * (float) Math.cos(Math.PI * t / 3.0);
        aura.setAlpha((int) (255 * breath * auraIn * enterAlpha));
        c.drawRect(l, top, l + w, top + h + 6 * dp, aura);
        auraLine.setAlpha((int) (255 * 0.55f * auraIn * enterAlpha));
        c.drawPath(shape, auraLine);
    }

    private void drawContent(Canvas c, float l, float top, float w, float h, float r, float enterAlpha) {
        int alpha = (int) (255 * enterAlpha);
        float cy = top + h / 2f;
        float x = l + 15 * dp;                       // trái: cách mép 15 (Margin 15,0 của CompactView)
        fill.setAlpha(alpha);
        stroke.setAlpha(alpha);

        if (kind == K_TIMER || kind == K_CHARGE) {
            // LiveSlot 22×22 thay cho nhân vật + ảnh bìa
            if (kind == K_TIMER) drawRing(c, x + 11 * dp, cy, alpha);
            else drawBolt(c, x + 11 * dp, cy, alpha);
        } else {
            Characters.draw(c, character, x, cy - 11 * dp, dp, cs, alpha);
            x += ("none".equals(character.id) ? 0 : 30) * dp;
            if (kind == K_MEDIA) drawArt(c, x + 6 * dp, cy, alpha);
        }

        // phải: cách mép 15; chữ 13 SemiBold (dời từ giữa sang phải vì lỗ camera)
        float right = l + w - 15 * dp;
        if (kind == K_MEDIA) {
            right = drawBars(c, right, cy, alpha) - 9 * dp;
        }
        String txt = (kind == K_IDLE || kind == K_MEDIA) ? clockFmt.format(new Date()) : label;
        text.setColor(kind == K_CHARGE ? GREEN : Color.WHITE);
        text.setAlpha(alpha);
        text.setTextAlign(Paint.Align.RIGHT);
        c.drawText(txt, right, cy - (text.descent() + text.ascent()) / 2f, text);
    }

    private void drawArt(Canvas c, float x, float cy, int alpha) {
        float s = 22 * dp, rad = 7 * dp;
        tmp.set(x, cy - s / 2f, x + s, cy + s / 2f);
        if (art != null) {
            float k = Math.max(s / art.getWidth(), s / art.getHeight());
            m.reset();
            m.setScale(k, k);
            m.postTranslate(tmp.centerX() - art.getWidth() * k / 2f, tmp.centerY() - art.getHeight() * k / 2f);
            artPaint.getShader().setLocalMatrix(m);
            artPaint.setAlpha(alpha);
            c.drawRoundRect(tmp, rad, rad, artPaint);
        } else {
            fill.setColor(0x26FFFFFF);
            fill.setAlpha(alpha * 0x26 / 255);
            c.drawRoundRect(tmp, rad, rad, fill);
        }
    }

    /** 4 vạch 3×16, bo 1.5, bước 5 — trả về mép trái của cụm vạch. */
    private float drawBars(Canvas c, float right, float cy, int alpha) {
        float bw = 3 * dp, pitch = 5 * dp, maxH = 16 * dp;
        float left = right - 4 * pitch + 2 * dp;
        fill.setColor(barColor);
        fill.setAlpha(alpha);
        for (int i = 0; i < 4; i++) {
            float bx = left + i * pitch;
            float bh = maxH * barScale[i];
            c.drawRoundRect(bx, cy - bh / 2f, bx + bw, cy + bh / 2f, 1.5f * dp, 1.5f * dp, fill);
        }
        return left;
    }

    private void drawRing(Canvas c, float x, float y, int alpha) {
        float rr = 9.75f * dp;
        stroke.setStrokeWidth(2.5f * dp);
        stroke.setColor(0x38FFFFFF);
        stroke.setAlpha(alpha * 0x38 / 255);
        c.drawCircle(x, y, rr, stroke);
        if (progress > 0f) {
            stroke.setColor(GREEN);
            stroke.setAlpha(alpha);
            tmp.set(x - rr, y - rr, x + rr, y + rr);
            c.drawArc(tmp, -90, 360 * progress, false, stroke);
        }
        // biểu tượng hẹn giờ 11 dp ở giữa
        stroke.setStrokeWidth(1.2f * dp);
        stroke.setColor(Color.WHITE);
        stroke.setAlpha(alpha);
        c.drawCircle(x, y + 0.6f * dp, 4.3f * dp, stroke);
        c.drawLine(x, y + 0.6f * dp, x, y - 2f * dp, stroke);
        c.drawLine(x - 1.5f * dp, y - 4.9f * dp, x + 1.5f * dp, y - 4.9f * dp, stroke);
    }

    private final Path bolt = new Path();

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
        fill.setColor(GREEN);
        fill.setAlpha((int) (alpha * (0.75f + 0.25f * (float) Math.sin(t * 4))));
        c.drawPath(bolt, fill);
    }

    // ---------------------------------------------------------------- màu

    /** DominantColor của Atoll: thu ảnh còn cạnh dài 12 px, trọng số theo độ rực, đẩy kênh cao nhất lên 235. */
    static int dominantColor(Bitmap b) {
        try {
            int w = b.getWidth(), h = b.getHeight();
            float k = 12f / Math.max(w, h);
            Bitmap s = Bitmap.createScaledBitmap(b, Math.max(1, (int) (w * k)), Math.max(1, (int) (h * k)), true);
            double rr = 0, gg = 0, bb = 0, ws = 0;
            for (int y = 0; y < s.getHeight(); y++) {
                for (int x = 0; x < s.getWidth(); x++) {
                    int p = s.getPixel(x, y);
                    int R = (p >> 16) & 255, G = (p >> 8) & 255, B = p & 255;
                    int mx = Math.max(R, Math.max(G, B)), mn = Math.min(R, Math.min(G, B));
                    double wt = 0.05 + (mx - mn) / 255.0 * mx / 255.0;
                    rr += R * wt;
                    gg += G * wt;
                    bb += B * wt;
                    ws += wt;
                }
            }
            if (s != b) s.recycle();
            rr /= ws;
            gg /= ws;
            bb /= ws;
            double top = Math.max(rr, Math.max(gg, bb));
            double boost = top > 0 ? 235 / top : 1;
            return Color.rgb((int) Math.min(255, rr * boost), (int) Math.min(255, gg * boost), (int) Math.min(255, bb * boost));
        } catch (RuntimeException ex) {
            return GREEN;
        }
    }

    private static int lerpColor(int a, int b, float f) {
        int ar = (a >> 16) & 255, ag = (a >> 8) & 255, ab = a & 255;
        int br = (b >> 16) & 255, bg = (b >> 8) & 255, bb = b & 255;
        return Color.rgb((int) (ar + (br - ar) * f), (int) (ag + (bg - ag) * f), (int) (ab + (bb - ab) * f));
    }

    private static int withAlpha(int c, int a) {
        return (Math.max(0, Math.min(255, a)) << 24) | (c & 0xFFFFFF);
    }
}
