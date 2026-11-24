package vn.islanddrop;

/**
 * Lò xo vật lý đơn giản (khối lượng 1) để tạo chuyển động co giãn có quán tính,
 * hơi nảy nhẹ giống đảo của Vivo. Tự chia nhỏ bước thời gian để ổn định ở 60–120Hz.
 */
final class Spring {
    float value;
    float velocity;
    float target;
    private final float stiffness;
    private final float damping;
    private final float restDelta;

    /**
     * @param stiffness    độ cứng (càng lớn càng nhanh)
     * @param dampingRatio tỉ lệ giảm chấn (&lt;1 là có nảy, 1 là không nảy)
     * @param restDelta    sai số coi như đã đứng yên
     */
    Spring(float initial, float stiffness, float dampingRatio, float restDelta) {
        this.value = initial;
        this.target = initial;
        this.stiffness = stiffness;
        this.damping = 2f * dampingRatio * (float) Math.sqrt(stiffness);
        this.restDelta = restDelta;
    }

    /** Tiến lò xo thêm dt giây. Trả về true nếu vẫn đang chuyển động. */
    boolean step(float dt) {
        if (isAtRest()) return false;
        int n = Math.max(1, (int) Math.ceil(dt / 0.004f));
        float h = dt / n;
        for (int i = 0; i < n; i++) {
            float a = -stiffness * (value - target) - damping * velocity;
            velocity += a * h;
            value += velocity * h;
        }
        if (Math.abs(value - target) < restDelta && Math.abs(velocity) < restDelta * 10f) {
            value = target;
            velocity = 0f;
            return false;
        }
        return true;
    }

    boolean isAtRest() {
        return value == target && velocity == 0f;
    }

    void snapTo(float v) {
        value = target = v;
        velocity = 0f;
    }
}
