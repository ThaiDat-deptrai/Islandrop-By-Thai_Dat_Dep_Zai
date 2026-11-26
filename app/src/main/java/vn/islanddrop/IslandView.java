package vn.islanddrop;

import android.content.ClipDescription;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.text.TextPaint;
import android.text.TextUtils;
import android.view.Choreographer;
import android.view.DragEvent;
import android.view.HapticFeedbackConstants;
import android.view.MotionEvent;
import android.view.VelocityTracker;
import android.view.View;
import android.view.ViewConfiguration;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Khay chia sẻ hình viên thuốc, mọc ra từ chỗ camera.
 *
 * Trạng thái (giá trị lò xo "expand"):
 *   0 = ẩn (thu vào camera)
 *   1 = viên thuốc gợi ý (vừa bắt đầu kéo ảnh ở bất kỳ đâu)
 *   2 = khay đầy đủ với icon app (ngón tay kéo lên tới vùng đảo)
 * Giữa các trạng thái là nội suy liên tục theo lò xo nên chuyển động mượt, có nảy nhẹ.
 */
final class IslandView extends View implements Choreographer.FrameCallback {

    interface Host {
        List<ShareTargets.Target> targetsFor(ClipDescription d);

        /** Bật/tắt nhận chạm cho cửa sổ (chỉ bật khi khay đang hiện). */
        void setInteractive(boolean on);

        /** Người dùng thả lên một app. Trả về true nếu đã nhận. */
        boolean onDropOnTarget(ShareTargets.Target t, DragEvent e);

        /** Người dùng chọn một app ở chế độ chạm: gửi ảnh src hoặc văn bản text. */
        void onTapTarget(ShareTargets.Target t, Uri src, String text);

        void onDragFinished();

        /** Chạm ô "+": mở màn hình chọn app (launcher = true: chọn app mở nhanh). */
        void onAddApps(boolean launcher);

        /** Chạm một app trong khay mở nhanh. */
        void onLaunchApp(ShareTargets.Target t);
    }

    /** Khay đang ở chế độ "mở nhanh app" (đảo luôn hiện) thay vì gửi nội dung. */
    private boolean launchMode;
    private long lastOutsideDismiss;

    private static final int MODE_NONE = 0, MODE_DRAG = 1, MODE_TAP = 2, MODE_STATUS = 3, MODE_CLIP = 4;
    private static final long TAP_TIMEOUT_MS = 8000;
    private static final long CLIP_TIMEOUT_MS = 6000;

    private int mode = MODE_NONE;
    private Uri tapUri;
    private String tapText;
    private String clipPreview;
    private float touchDownX, touchLastX, flingVel;
    private boolean touchScrolling;
    /** Đang giữ thẻ văn bản và kéo tới app (giống kéo thả, nhưng do chính đảo theo dõi ngón tay). */
    private boolean chipGesture;
    /** Viên trạng thái không tự ẩn và không thu lại khi chạm ra ngoài. */
    private boolean statusSticky;
    private final TextPaint previewPaint = new TextPaint(Paint.ANTI_ALIAS_FLAG);
    private VelocityTracker velocity;
    private final int touchSlop;
    private final Runnable dismissRunnable = this::dismiss;

    private static final int SLOTS = 5;
    private static final float HOVER_SCALE = 1.16f;

    private final Host host;
    private final float dp;

    // Kích thước (px)
    private final float topY, pill0W, pill0H, trayMargin, trayH, trayR;
    /** Kích thước viên gợi ý: mặc định (một dòng) hoặc thẻ văn bản (hai dòng) */
    private final float hintWDefault, hintHDefault, clipW, clipH;
    private float hintW, hintH;
    private final float iconSize, rowTop, labelGap, labelH, pad, edgeZone, scrollSpeed;

    private final Paint bgPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint hlPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint addPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final TextPaint labelPaint = new TextPaint(Paint.ANTI_ALIAS_FLAG);
    private final TextPaint hintPaint = new TextPaint(Paint.ANTI_ALIAS_FLAG);
    private final Drawable shareIcon;
    private final RectF rect = new RectF();
    private final RectF tmp = new RectF();

    private final Spring expand;
    private Spring[] hover = new Spring[0];
    private List<ShareTargets.Target> targets = Collections.emptyList();
    private CharSequence[] labels = new CharSequence[0];
    private float labelsForSlotW = -1f;

    private boolean dragging;
    private int hovered = -1;
    private float lastX, lastY;
    /** scrollVel: vận tốc thực, đuổi theo scrollVelTarget có gia tốc mượt (không giật cục). */
    private float scroll, scrollVel, scrollVelTarget;
    private String kindText = "";

    private boolean frameScheduled;
    private long lastFrameNanos;
    private boolean interactive;

    IslandView(Context ctx, Host host) {
        super(ctx);
        this.host = host;
        touchSlop = ViewConfiguration.get(ctx).getScaledTouchSlop();
        dp = ctx.getResources().getDisplayMetrics().density;
        float sp = ctx.getResources().getDisplayMetrics().scaledDensity;

        topY = 4 * dp;
        pill0W = 52 * dp;
        pill0H = 26 * dp;
        hintW = hintWDefault = 200 * dp;
        hintH = hintHDefault = 34 * dp;
        clipW = 300 * dp;
        clipH = 70 * dp;
        trayMargin = 10 * dp;
        trayR = 30 * dp;
        iconSize = 50 * dp;
        rowTop = topY + 46 * dp;   // chừa hàng thanh trạng thái + camera
        labelGap = 6 * dp;
        labelH = 15 * dp;
        pad = 12 * dp;
        trayH = (rowTop - topY) + iconSize + labelGap + labelH + 16 * dp;
        edgeZone = 64 * dp;
        scrollSpeed = 900 * dp;   // tốc độ tối đa khi giữ sát mép (~2700 px/s trên Tecno)

        bgPaint.setColor(Color.BLACK);
        hlPaint.setColor(Color.WHITE);
        labelPaint.setColor(Color.WHITE);
        labelPaint.setTextSize(11 * sp);
        labelPaint.setTextAlign(Paint.Align.CENTER);
        hintPaint.setColor(Color.WHITE);
        hintPaint.setTextSize(13 * sp);
        hintPaint.setFakeBoldText(true);
        previewPaint.setColor(0xFFBDBDBD);
        previewPaint.setTextSize(12.5f * sp);

        shareIcon = ctx.getDrawable(android.R.drawable.ic_menu_share).mutate();
        shareIcon.setTint(Color.WHITE);

        // stiffness 380, damping 0.74: nhanh, nảy nhẹ một nhịp giống Vivo
        expand = new Spring(0f, 380f, 0.74f, 0.001f);
        setLayerType(LAYER_TYPE_HARDWARE, null);
    }

    /** Chiều cao cửa sổ cần thiết (gồm chỗ cho icon phóng to). */
    int windowHeight() {
        return (int) Math.ceil(topY + trayH + 22 * dp);
    }

    // ---------------------------------------------------------------- kéo thả

    @Override
    public boolean onDragEvent(DragEvent e) {
        try {
            return handleDrag(e);
        } catch (RuntimeException ex) {
            L.i(getContext(), "IslandView lỗi: " + ex);
            return false;
        }
    }

    private boolean handleDrag(DragEvent e) {
        switch (e.getAction()) {
            case DragEvent.ACTION_DRAG_STARTED: {
                ClipDescription d = e.getClipDescription();
                if (!accepts(d)) {
                    L.i(getContext(), "Bỏ qua lượt kéo không có nội dung");
                    return false;
                }
                removeCallbacks(dismissRunnable);   // kéo thả luôn được ưu tiên hơn chế độ chạm
                mode = MODE_DRAG;
                launchMode = false;
                useDefaultHint();
                chipGesture = false;
                flingVel = 0f;
                setTargets(host.targetsFor(d));
                kindText = kindOf(d);
                dragging = true;
                hovered = -1;
                scroll = 0f;
                stopScroll();
                setInteractiveSafely(true);
                expand.target = 1f;
                kick();
                return true;
            }
            case DragEvent.ACTION_DRAG_ENTERED:
                if (expand.target != 2f) {
                    expand.target = 2f;
                    performHapticFeedback(HapticFeedbackConstants.CONTEXT_CLICK);
                }
                kick();
                return true;
            case DragEvent.ACTION_DRAG_LOCATION:
                lastX = e.getX();
                lastY = e.getY();
                if (expand.target != 2f) expand.target = 2f;
                updateHover();
                updateAutoScroll();
                kick();
                return true;
            case DragEvent.ACTION_DRAG_EXITED:
                setHovered(-1);
                stopScroll();
                expand.target = dragging ? 1f : 0f;
                kick();
                return true;
            case DragEvent.ACTION_DROP: {
                stopScroll();
                int i = hit(e.getX(), e.getY());
                if (i < 0 || targets.get(i) == ShareTargets.ADD) return false;
                return host.onDropOnTarget(targets.get(i), e);
            }
            case DragEvent.ACTION_DRAG_ENDED:
                mode = MODE_NONE;
                dragging = false;
                setHovered(-1);
                stopScroll();
                expand.target = 0f;
                kick();
                host.onDragFinished();
                return true;
            default:
                return false;
        }
    }

    // ---------------------------------------------------------------- chế độ chạm / trạng thái

    /** Hiện viên thông báo ngắn (vd "Đang tìm ảnh…"). autoHideMs = 0: giữ tới khi có lệnh khác. */
    void showStatus(String text, long autoHideMs) {
        if (mode == MODE_DRAG) return;
        removeCallbacks(dismissRunnable);
        mode = MODE_STATUS;
        statusSticky = autoHideMs <= 0;
        useDefaultHint();
        kindText = text;
        setHovered(-1);
        setInteractiveSafely(true);
        expand.target = 1f;
        kick();
        if (autoHideMs > 0) postDelayed(dismissRunnable, autoHideMs);
    }

    /** Mở khay đầy đủ để chạm chọn app gửi ảnh src. */
    void showTap(List<ShareTargets.Target> t, Uri src) {
        if (mode == MODE_DRAG) return;
        removeCallbacks(dismissRunnable);
        mode = MODE_TAP;
        launchMode = false;
        useDefaultHint();
        tapUri = src;
        tapText = null;
        setTargets(t);
        kindText = "Gửi ảnh";
        scroll = 0f;
        stopScroll();
        flingVel = 0f;
        setInteractiveSafely(true);
        expand.target = 2f;
        performHapticFeedback(HapticFeedbackConstants.CONTEXT_CLICK);
        kick();
        postDelayed(dismissRunnable, TAP_TIMEOUT_MS);
    }

    /**
     * Hiện thẻ "Đã sao chép" (2 dòng: tiêu đề + xem trước). Giữ thẻ rồi kéo tới app để gửi,
     * hoặc chạm thẻ để mở khay rồi chạm app.
     */
    void showClip(List<ShareTargets.Target> t, String text) {
        if (mode == MODE_DRAG) return;
        removeCallbacks(dismissRunnable);
        mode = MODE_CLIP;
        launchMode = false;
        tapUri = null;
        tapText = text;
        String oneLine = text.replaceAll("\\s+", " ").trim();
        clipPreview = oneLine.length() > 200 ? oneLine.substring(0, 200) : oneLine;
        hintW = clipW;
        hintH = clipH;
        kindText = "Đã sao chép";
        setTargets(t);
        scroll = 0f;
        stopScroll();
        flingVel = 0f;
        chipGesture = false;
        setInteractiveSafely(true);
        expand.target = 1f;
        kick();
        postDelayed(dismissRunnable, CLIP_TIMEOUT_MS);
    }

    /** Mở khay app nhanh (chạm viên đảo luôn hiện). Đang mở thì thu lại. */
    void toggleLauncher(List<ShareTargets.Target> apps) {
        if (mode == MODE_DRAG) return;
        // chạm viên đảo khi khay đang mở: khay đã tự đóng do "chạm ra ngoài" -> đừng mở lại
        if (android.os.SystemClock.uptimeMillis() - lastOutsideDismiss < 450) return;
        if (mode == MODE_TAP && launchMode) {
            dismiss();
            return;
        }
        removeCallbacks(dismissRunnable);
        mode = MODE_TAP;
        launchMode = true;
        useDefaultHint();
        tapUri = null;
        tapText = null;
        setTargets(apps);
        kindText = "Mở nhanh";
        scroll = 0f;
        stopScroll();
        flingVel = 0f;
        setInteractiveSafely(true);
        expand.target = 2f;
        kick();
        postDelayed(dismissRunnable, TAP_TIMEOUT_MS);
    }

    private void useDefaultHint() {
        hintW = hintWDefault;
        hintH = hintHDefault;
        clipPreview = null;
    }

    /** Thu khay về camera (không ảnh hưởng lượt kéo thả đang diễn ra). */
    void dismiss() {
        if (mode == MODE_DRAG) return;
        removeCallbacks(dismissRunnable);
        mode = MODE_NONE;
        launchMode = false;
        tapUri = null;
        tapText = null;
        chipGesture = false;
        setHovered(-1);
        stopScroll();
        flingVel = 0f;
        expand.target = 0f;
        kick();
    }

    @Override
    public boolean onTouchEvent(MotionEvent e) {
        if (mode != MODE_TAP && mode != MODE_STATUS && mode != MODE_CLIP) return false;
        float x = e.getX(), y = e.getY();
        switch (e.getActionMasked()) {
            case MotionEvent.ACTION_OUTSIDE:
                // viên chờ (vd "Kéo ảnh lên đây") giữ nguyên khi chạm vào ảnh nổi bên dưới
                if (!(mode == MODE_STATUS && statusSticky)) {
                    if (launchMode) lastOutsideDismiss = android.os.SystemClock.uptimeMillis();
                    dismiss();
                }
                return true;
            case MotionEvent.ACTION_DOWN:
                if (mode == MODE_STATUS || !rect.contains(x, y)) {
                    dismiss();
                    return true;
                }
                removeCallbacks(dismissRunnable);
                if (mode == MODE_CLIP) {
                    // giữ thẻ văn bản: mở khay, ngón tay giờ "kéo" nội dung tới app
                    mode = MODE_TAP;
                    chipGesture = true;
                    lastX = x;
                    lastY = y;
                    expand.target = 2f;
                    performHapticFeedback(HapticFeedbackConstants.CONTEXT_CLICK);
                    kick();
                    return true;
                }
                chipGesture = false;
                flingVel = 0f;
                touchDownX = touchLastX = x;
                touchScrolling = false;
                if (velocity != null) velocity.recycle();
                velocity = VelocityTracker.obtain();
                velocity.addMovement(e);
                lastX = x;
                lastY = y;
                setHovered(hit(x, y));
                return true;
            case MotionEvent.ACTION_MOVE:
                if (chipGesture) {
                    lastX = x;
                    lastY = y;
                    updateHover();
                    updateAutoScroll();
                    kick();
                    return true;
                }
                if (velocity != null) velocity.addMovement(e);
                if (!touchScrolling && Math.abs(x - touchDownX) > touchSlop && maxScroll() > 0f) {
                    touchScrolling = true;
                    setHovered(-1, false);
                }
                if (touchScrolling) {
                    scroll = Math.max(0f, Math.min(maxScroll(), scroll - (x - touchLastX)));
                    invalidate();
                } else {
                    lastX = x;
                    lastY = y;
                    setHovered(hit(x, y));
                }
                touchLastX = x;
                return true;
            case MotionEvent.ACTION_UP: {
                if (chipGesture) {
                    chipGesture = false;
                    scrollVelTarget = 0f;
                    int i = hit(x, y);
                    if (i >= 0) {
                        selectTarget(i);
                    } else {
                        // thả ngoài icon: khay vẫn mở để chạm chọn
                        setHovered(-1);
                        postDelayed(dismissRunnable, TAP_TIMEOUT_MS);
                    }
                    return true;
                }
                if (touchScrolling) {
                    if (velocity != null) {
                        velocity.addMovement(e);
                        velocity.computeCurrentVelocity(1000);
                        flingVel = -velocity.getXVelocity();
                    }
                    kick();
                } else {
                    int i = hit(x, y);
                    if (i >= 0 && i == hovered) {
                        selectTarget(i);
                        recycleVelocity();
                        return true;
                    }
                    setHovered(-1);
                }
                recycleVelocity();
                if (mode == MODE_TAP) postDelayed(dismissRunnable, TAP_TIMEOUT_MS);
                return true;
            }
            case MotionEvent.ACTION_CANCEL:
                chipGesture = false;
                scrollVelTarget = 0f;
                setHovered(-1);
                recycleVelocity();
                return true;
            default:
                return true;
        }
    }

    /** Gửi nội dung đang giữ (ảnh hoặc văn bản) tới app thứ i, nhún icon rồi thu khay. */
    private void selectTarget(int i) {
        ShareTargets.Target t = targets.get(i);
        if (t == ShareTargets.ADD) {
            host.onAddApps(launchMode);
            dismiss();
            return;
        }
        if (launchMode) {
            hover[i].velocity = -3f;      // nhún nhẹ khi chọn
            kick();
            host.onLaunchApp(t);
            removeCallbacks(dismissRunnable);
            postDelayed(dismissRunnable, 140);
            return;
        }
        if (tapUri == null && tapText == null) return;
        Uri src = tapUri;
        String text = tapText;
        hover[i].velocity = -3f;          // nhún nhẹ khi chọn
        kick();
        host.onTapTarget(t, src, text);
        removeCallbacks(dismissRunnable);
        postDelayed(dismissRunnable, 140);
    }

    private void recycleVelocity() {
        if (velocity != null) {
            velocity.recycle();
            velocity = null;
        }
    }

    /** Hiện khay cho mọi lượt kéo có nội dung: ảnh, video, tệp và cả chữ đã bôi đen. */
    private static boolean accepts(ClipDescription d) {
        return d != null && d.getMimeTypeCount() > 0;
    }

    private static String kindOf(ClipDescription d) {
        String k = ShareTargets.keyFor(d);
        if ("image/*".equals(k)) return "Gửi ảnh";
        if ("video/*".equals(k)) return "Gửi video";
        if ("text/plain".equals(k)) return "Gửi chữ";
        return "Gửi tệp";
    }

    private void setTargets(List<ShareTargets.Target> t) {
        List<ShareTargets.Target> all = new ArrayList<>(t == null ? Collections.emptyList() : t);
        all.add(ShareTargets.ADD);   // ô "+ Thêm app" luôn ở cuối
        targets = all;
        hover = new Spring[targets.size()];
        for (int i = 0; i < hover.length; i++) hover[i] = new Spring(1f, 620f, 0.55f, 0.0005f);
        labels = new CharSequence[targets.size()];
        labelsForSlotW = -1f;
    }

    private void setInteractiveSafely(boolean on) {
        if (interactive == on) return;
        interactive = on;
        host.setInteractive(on);
    }

    // ---------------------------------------------------------------- hình học

    private float trayLeft() {
        return trayMargin;
    }

    private float trayRight() {
        return getWidth() - trayMargin;
    }

    private float slotW() {
        return (trayRight() - trayLeft() - 2 * pad) / SLOTS;
    }

    /** Vị trí x bắt đầu hàng icon (căn giữa nếu ít hơn số ô). */
    private float rowStartX() {
        int n = targets.size();
        float extra = n < SLOTS ? (SLOTS - n) * slotW() / 2f : 0f;
        return trayLeft() + pad + extra - scroll;
    }

    private float maxScroll() {
        return Math.max(0f, targets.size() * slotW() - (trayRight() - trayLeft() - 2 * pad));
    }

    private int hit(float x, float y) {
        if (expand.value < 1.5f || targets.isEmpty()) return -1;
        if (y < rowTop - 14 * dp || y > rowTop + iconSize + labelGap + labelH + 16 * dp) return -1;
        if (x < trayLeft() + pad / 2 || x > trayRight() - pad / 2) return -1;
        int i = (int) Math.floor((x - rowStartX()) / slotW());
        return (i >= 0 && i < targets.size()) ? i : -1;
    }

    private void updateHover() {
        setHovered(hit(lastX, lastY), true);
    }

    private void setHovered(int i) {
        setHovered(i, true);
    }

    /** @param haptic rung nhẹ khi đổi icon (tắt trong lúc tự cuộn để không rung liên tục) */
    private void setHovered(int i, boolean haptic) {
        if (i == hovered) return;
        if (hovered >= 0 && hovered < hover.length) hover[hovered].target = 1f;
        hovered = i;
        if (i >= 0 && i < hover.length) {
            hover[i].target = HOVER_SCALE;
            if (haptic) performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK);
        }
        kick();
    }

    private void updateAutoScroll() {
        if (maxScroll() <= 0f || lastY > rowTop + iconSize + labelGap + labelH + 30 * dp) {
            scrollVelTarget = 0f;
            return;
        }
        float l = trayLeft() + pad, r = trayRight() - pad;
        float t;
        if (lastX < l + edgeZone) {
            t = clamp01(1f - (lastX - l) / edgeZone);
            scrollVelTarget = -scrollSpeed * ease(t);
        } else if (lastX > r - edgeZone) {
            t = clamp01(1f - (r - lastX) / edgeZone);
            scrollVelTarget = scrollSpeed * ease(t);
        } else {
            scrollVelTarget = 0f;
        }
    }

    /** Càng sát mép càng nhanh, nhưng vừa chạm vùng mép đã có tốc độ để cảm giác nhạy. */
    private static float ease(float t) {
        return 0.22f + 0.78f * t * t;
    }

    private void stopScroll() {
        scrollVel = 0f;
        scrollVelTarget = 0f;
    }

    // ---------------------------------------------------------------- khung hình

    private void kick() {
        if (frameScheduled) return;
        frameScheduled = true;
        lastFrameNanos = 0L;
        Choreographer.getInstance().postFrameCallback(this);
    }

    @Override
    public void doFrame(long frameTimeNanos) {
        frameScheduled = false;
        float dt = lastFrameNanos == 0L ? 1f / 120f
                : Math.min((frameTimeNanos - lastFrameNanos) / 1e9f, 1f / 30f);
        lastFrameNanos = frameTimeNanos;

        boolean moving = expand.step(dt);
        for (Spring s : hover) moving |= s.step(dt);
        if (scrollVel != 0f || scrollVelTarget != 0f) {
            // vận tốc đuổi theo mục tiêu với hằng số thời gian 70ms -> tăng/giảm tốc êm
            float k = 1f - (float) Math.exp(-dt / 0.07f);
            scrollVel += (scrollVelTarget - scrollVel) * k;
            if (scrollVelTarget == 0f && Math.abs(scrollVel) < 5f) scrollVel = 0f;
            float ns = Math.max(0f, Math.min(maxScroll(), scroll + scrollVel * dt));
            if (ns != scroll) {
                scroll = ns;
                setHovered(hit(lastX, lastY), false);
                moving = true;
            } else if (scrollVelTarget == 0f) {
                scrollVel = 0f;           // chạm đầu/cuối danh sách
            } else {
                moving = true;            // vẫn giữ ở mép, chờ người dùng rời ra
            }
        }
        if (flingVel != 0f) {
            // quán tính sau khi vuốt khay ở chế độ chạm
            float ns = Math.max(0f, Math.min(maxScroll(), scroll + flingVel * dt));
            flingVel *= (float) Math.exp(-dt / 0.33f);
            if (ns == scroll || Math.abs(flingVel) < 20f) flingVel = 0f;
            scroll = ns;
            moving |= flingVel != 0f;
        }
        invalidate();

        if (moving) {
            frameScheduled = true;
            Choreographer.getInstance().postFrameCallback(this);
        } else if (!dragging && mode == MODE_NONE && expand.value == 0f) {
            // đã thu gọn hẳn: trả lại chạm cho app bên dưới
            setInteractiveSafely(false);
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
        float p = expand.value;
        if (p <= 0.0005f && expand.target == 0f) return;
        float w = getWidth();

        // Nội suy hình dạng: viên nhỏ ở camera -> viên gợi ý -> khay
        float trayW = w - 2 * trayMargin;
        float bw, bh, br;
        if (p <= 1f) {
            bw = lerp(pill0W, hintW, p);
            bh = lerp(pill0H, hintH, p);
            br = bh / 2f;
        } else {
            float q = p - 1f;
            bw = lerp(hintW, trayW, q);
            bh = lerp(hintH, trayH, q);
            br = Math.min(bh / 2f, lerp(hintH / 2f, trayR, clamp01(q)));
        }
        bw = Math.max(0f, Math.min(bw, w));
        bh = Math.max(0f, bh);
        rect.set((w - bw) / 2f, topY, (w + bw) / 2f, topY + bh);

        bgPaint.setAlpha((int) (255 * clamp01(p * 3f)));
        c.drawRoundRect(rect, br, br, bgPaint);

        // Nội dung viên gợi ý: icon chia sẻ bên trái camera, chữ bên phải
        float hintA = clamp01(1f - Math.abs(p - 1f) * 2.4f);
        if (hintA > 0.01f) {
            int a = (int) (255 * hintA);
            // thẻ văn bản: dòng 1 nằm ngang camera, dòng 2 là đoạn xem trước
            float cy = clipPreview != null ? rect.top + 17 * dp : rect.centerY();
            if (clipPreview != null) {
                float maxW = rect.width() - 32 * dp;
                if (maxW > 0) {
                    CharSequence pv = TextUtils.ellipsize(clipPreview, previewPaint, maxW,
                            TextUtils.TruncateAt.END);
                    previewPaint.setAlpha((int) (230 * hintA));
                    float pw = previewPaint.measureText(pv, 0, pv.length());
                    c.drawText(pv, 0, pv.length(), rect.centerX() - pw / 2f,
                            rect.top + 54 * dp, previewPaint);
                }
            }
            int s = (int) (18 * dp);
            int il = (int) (rect.left + 14 * dp);
            shareIcon.setBounds(il, (int) (cy - s / 2f), il + s, (int) (cy + s / 2f));
            shareIcon.setAlpha(a);
            shareIcon.draw(c);
            hintPaint.setAlpha(a);
            float tw = hintPaint.measureText(kindText);
            c.drawText(kindText, rect.right - 16 * dp - tw,
                    cy - (hintPaint.descent() + hintPaint.ascent()) / 2f, hintPaint);
        }

        // Nội dung khay: hàng icon app
        float ca = clamp01((p - 1.45f) / 0.55f);
        if (ca <= 0.01f) return;

        c.save();
        c.clipRect(rect.left + 2 * dp, rect.top, rect.right - 2 * dp, rect.bottom);
        float slide = (1f - ca) * 10 * dp;

        if (targets.isEmpty()) {
            labelPaint.setAlpha((int) (200 * ca));
            c.drawText("Không có app nào nhận được nội dung này", w / 2f,
                    rowTop + iconSize / 2f + slide, labelPaint);
            c.restore();
            return;
        }

        float slotW = slotW();
        ensureLabels(slotW);
        float x0 = rowStartX();
        for (int i = 0; i < targets.size(); i++) {
            float sx = x0 + i * slotW;
            if (sx + slotW < rect.left || sx > rect.right) continue;
            float scale = hover[i].value * lerp(0.86f, 1f, ca);
            float cx = sx + slotW / 2f;
            float cy = rowTop + iconSize / 2f + slide;

            float hl = clamp01((hover[i].value - 1f) / (HOVER_SCALE - 1f));
            if (hl > 0.01f) {
                hlPaint.setAlpha((int) (40 * hl * ca));
                tmp.set(sx + 3 * dp, rowTop - 9 * dp + slide, sx + slotW - 3 * dp,
                        rowTop + iconSize + labelGap + labelH + 7 * dp + slide);
                c.drawRoundRect(tmp, 18 * dp, 18 * dp, hlPaint);
            }

            Drawable ic = targets.get(i).icon;
            if (targets.get(i) == ShareTargets.ADD) {
                // ô "+": vòng tròn xám đậm có dấu cộng
                float rr = iconSize * scale / 2f;
                addPaint.setStyle(Paint.Style.FILL);
                addPaint.setColor(0xFF2C2C2E);
                addPaint.setAlpha((int) (255 * ca));
                c.drawCircle(cx, cy, rr, addPaint);
                addPaint.setStyle(Paint.Style.STROKE);
                addPaint.setStrokeWidth(2.5f * dp * scale);
                addPaint.setStrokeCap(Paint.Cap.ROUND);
                addPaint.setColor(Color.WHITE);
                addPaint.setAlpha((int) (255 * ca));
                float pl = rr * 0.42f;
                c.drawLine(cx - pl, cy, cx + pl, cy, addPaint);
                c.drawLine(cx, cy - pl, cx, cy + pl, addPaint);
            } else if (ic != null) {
                // Đặt icon bằng translate/scale (số thực) thay vì setBounds (số nguyên)
                // để icon trượt mượt theo từng phần pixel, không nhảy cóc khi cuộn.
                int s = (int) iconSize;
                ic.setBounds(-s / 2, -s / 2, s - s / 2, s - s / 2);
                ic.setAlpha((int) (255 * ca));
                int save = c.save();
                c.translate(cx, cy);
                c.scale(scale, scale);
                ic.draw(c);
                c.restoreToCount(save);
            }
            labelPaint.setAlpha((int) (235 * ca));
            c.drawText(labels[i], 0, labels[i].length(), cx,
                    rowTop + iconSize + labelGap + labelH * 0.8f + slide, labelPaint);
        }
        c.restore();
    }

    private void ensureLabels(float slotW) {
        if (slotW == labelsForSlotW) return;
        labelsForSlotW = slotW;
        for (int i = 0; i < targets.size(); i++) {
            CharSequence l = targets.get(i).label;
            labels[i] = TextUtils.ellipsize(l == null ? "" : l, labelPaint, slotW - 6 * dp,
                    TextUtils.TruncateAt.END);
        }
    }

    private static float lerp(float a, float b, float t) {
        return a + (b - a) * t;
    }

    private static float clamp01(float v) {
        return v < 0f ? 0f : (v > 1f ? 1f : v);
    }
}
