package vn.islanddrop;

import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RadialGradient;
import android.graphics.RectF;
import android.graphics.Shader;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 38 nhân vật trong viên thuốc, chuyển từ Atoll (Views/CharacterView.cs + CharacterView.More.cs).
 * Mỗi nhân vật vẽ trong ô 30×22, tâm (15, 11), bằng đúng toạ độ gốc; canvas được phóng theo dp.
 */
final class Characters {

    private Characters() {}

    // tâm trạng
    static final int IDLE = 0, MUSIC = 1, HAPPY = 2;
    // cảm xúc khi chạm (Atoll: Love, Angry, Surprised, Sad, Dizzy, Laugh, Cool) — 2.4 giây
    static final int LOVE = 0, ANGRY = 1, SURPRISED = 2, SAD = 3, DIZZY = 4, LAUGH = 5, COOL = 6;
    static final float EMOTE_TIME = 2.4f;

    /** Trạng thái chung (PillView cập nhật mỗi khung hình). */
    static final class State {
        float t, blink, lookX, lookY, beat, jump;
        boolean music;
        float happy;                       // giây còn lại của "Cheer" (vui)
        float greet = 0f;                  // giây kể từ lúc chào; < 0 = không
        int emotion = -1;
        float emotionT;

        int mood() {
            if (happy > 0 || emotion == LAUGH || (greet >= 0 && greet < 1.6f)) return HAPPY;
            return music ? MUSIC : IDLE;
        }
    }

    // bảng màu (App.xaml, CharacterView.More.cs)
    static final int FG = Color.WHITE, DARK = Color.BLACK, ACCENT = 0xFF30D158;
    static final int INK = 0xFF1B1B1F, PAPER = 0xFFF6F6F8, PINK = 0xFFFF9FB5, ORANGE = 0xFFFF8A3D,
            YELLOW = 0xFFFFD24A, GREEN = 0xFF5BC26A, BLUE = 0xFF5AC8FA;
    static final int LOVE_C = 0xFFFF5C8A, ANGRY_C = 0xFFFF453A, TEAR = 0xFF6CC4FF, STAR = 0xFFFFD34D,
            SHADES = 0xFF0E0E12, BLUSH = 0x80FF7A9C;

    static final int[] AURORA = {0xFF5B8CFF, 0xFFA066FF, 0xFF3DE1FF};
    static final int[] CLAUDE = {0xFFD97757, 0xFFF0A27A, 0xFFC2552F};

    /** Một nhân vật: vẽ trong ô 30×22 (đơn vị = dp, canvas đã phóng). */
    abstract static class Ch {
        final String id, name;

        Ch(String id, String name) {
            this.id = id;
            this.name = name;
        }

        int[] aura() {
            return null;
        }

        /** true: dùng cy gốc và tự chuyển động (cá, slime, nhịp tim, Claude, Clawd). */
        boolean rawY() {
            return false;
        }

        boolean shades() {
            return true;
        }

        abstract void draw(G g, float cx, float y);
    }

    private static final Map<String, Ch> ALL = new LinkedHashMap<>();

    static Ch get(String id) {
        Ch c = ALL.get(id);
        return c != null ? c : ALL.get("eyes");
    }

    static Map<String, Ch> all() {
        return ALL;
    }

    // ================================================================ bút vẽ

    /** Ngữ cảnh vẽ: canvas + các hàm tiện ích theo đúng helper của Atoll. */
    static final class G {
        Canvas c;
        State s;
        int alpha = 255;
        final Paint f = new Paint(Paint.ANTI_ALIAS_FLAG);
        final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        final RectF r = new RectF();
        final Path path = new Path();

        G() {
            p.setStyle(Paint.Style.STROKE);
            p.setStrokeCap(Paint.Cap.ROUND);
            p.setStrokeJoin(Paint.Join.ROUND);
        }

        int mood() {
            return s.mood();
        }

        Paint fill(int color) {
            f.setShader(null);
            f.setColor(color);
            f.setAlpha(Color.alpha(color) * alpha / 255);
            return f;
        }

        Paint fill(int color, float opacity) {
            fill(color);
            f.setAlpha((int) (Color.alpha(color) * opacity * alpha / 255));
            return f;
        }

        Paint pen(int color, float w) {
            p.setShader(null);
            p.setColor(color);
            p.setAlpha(Color.alpha(color) * alpha / 255);
            p.setStrokeWidth(w);
            return p;
        }

        void oval(float x, float y, float rx, float ry, Paint paint) {
            r.set(x - rx, y - ry, x + rx, y + ry);
            c.drawOval(r, paint);
        }

        void oval(float x, float y, float rx, float ry, int color) {
            oval(x, y, rx, ry, fill(color));
        }

        void circle(float x, float y, float rad, int color) {
            c.drawCircle(x, y, rad, fill(color));
        }

        void rrect(float x, float y, float w, float h, float rad, int color) {
            r.set(x, y, x + w, y + h);
            c.drawRoundRect(r, rad, rad, fill(color));
        }

        void poly(int color, float... pts) {
            path.reset();
            path.moveTo(pts[0], pts[1]);
            for (int i = 2; i < pts.length; i += 2) path.lineTo(pts[i], pts[i + 1]);
            path.close();
            c.drawPath(path, fill(color));
        }

        void line(int color, float w, float x1, float y1, float x2, float y2) {
            c.drawLine(x1, y1, x2, y2, pen(color, w));
        }

        /** Arc của Atoll: Bézier bậc hai, điểm điều khiển ((x1+x2)/2, y1 + 2b). */
        void arc(float x1, float y1, float x2, float y2, float b, int color, float w) {
            path.reset();
            path.moveTo(x1, y1);
            path.quadTo((x1 + x2) / 2f, y1 + 2 * b, x2, y2);
            c.drawPath(path, pen(color, w));
        }

        void rotate(float deg, float px, float py) {
            c.rotate(deg, px, py);
        }

        int save() {
            return c.save();
        }

        void restore(int s) {
            c.restoreToCount(s);
        }

        // ---------------------------------------------------------------- mắt / miệng / má

        void eye(float x, float y, float rx, float ry, int white, int pupil, boolean small) {
            State st = s;
            float side = x < 15f ? -1f : 1f;
            int emo = st.emotion;
            if (emo == LOVE) {
                heart(x, y + 0.4f, 0.95f * rx, LOVE_C);
                return;
            }
            if (emo == DIZZY) {
                float R = 0.95f * Math.max(rx, ry);
                path.reset();
                boolean first = true;
                for (float th = 0; th <= 5 * Math.PI; th += 0.25f) {
                    float rr = R * th / (float) (5 * Math.PI);
                    float a = th + 9 * st.t;
                    float px = x + rr * (float) Math.cos(a), py = y + rr * (float) Math.sin(a);
                    if (first) path.moveTo(px, py);
                    else path.lineTo(px, py);
                    first = false;
                }
                c.drawPath(path, pen(white, small ? 0.9f : 1.2f));
                return;
            }
            if (emo == SURPRISED) {
                oval(x, y, 1.05f * rx, 1.12f * ry, white);
                circle(x, y, 0.3f * rx, pupil);
                return;
            }
            if (emo == ANGRY || emo == SAD) {
                boolean angry = emo == ANGRY;
                oval(x, y + (angry ? 0.6f : 0.3f), rx, ry * (angry ? 0.8f : 0.9f), white);
                if (angry) circle(x - 0.8f * side, y + 1, 0.6f * rx, pupil);
                else circle(x, y + 0.35f * ry, 0.6f * rx, pupil);
                float inner = angry ? y - 0.55f * ry : y - 1.3f * ry;
                float outer = angry ? y - 1.3f * ry : y - 0.75f * ry;
                line(white, small ? 1.1f : 1.5f, x - 1.05f * rx * side, inner, x + 0.95f * rx * side, outer);
                return;
            }
            if (mood() == HAPPY) {
                arc(x - 0.8f * rx, y + 1.5f, x + 0.8f * rx, y + 1.5f, -0.9f * rx, white, small ? 1.3f : 1.8f);
                return;
            }
            float open = 1f - st.blink;
            float ey = y + (1f - open) * ry * 0.35f;
            oval(x, ey, rx * (1f + 0.12f * (1f - open)), Math.max(0.6f, ry * open), white);
            if (open < 0.4f) return;
            float pr = rx * (small ? 0.66f : 0.64f);
            float px = x + st.lookX * (rx - pr) * 0.9f;
            float py = ey + st.lookY * (ry - pr) * 0.85f;
            oval(px, py, pr, pr * Math.min(1f, open + 0.2f), pupil);
            circle(px + 0.32f * pr, py - 0.36f * pr, pr * (small ? 0.34f : 0.36f), Color.WHITE);
            if (!small) circle(px - 0.38f * pr, py + 0.38f * pr, 0.15f * pr, Color.WHITE);
        }

        /** Hai mắt nhỏ: tròng Ink, đồng tử trắng. */
        void darkEyes(float cx, float y, float apart, float rx, float ry) {
            eye(cx - apart, y, rx, ry, INK, Color.WHITE, true);
            eye(cx + apart, y, rx, ry, INK, Color.WHITE, true);
        }

        void mouth(float x, float y, float size, int color) {
            int md = mood();
            if (md == MUSIC) {
                oval(x, y, 0.7f * size, size * (0.4f + 0.8f * s.beat), color);
                return;
            }
            float wd = md == HAPPY ? 1.6f : 1f;
            arc(x - size * wd, y - 0.4f, x + size * wd, y - 0.4f, 0.55f * size * wd, color, 1.1f);
        }

        void blush(float x, float y, float rad) {
            float op = mood() == HAPPY ? 1f : 0.8f;
            oval(x, y, rad, 0.62f * rad, fill(BLUSH, op));
        }

        /** Trái tim: kích thước "size" (bề ngang ≈ 1.7·size). */
        void heart(float x, float y, float size, int color) {
            float s2 = size / 2f;
            path.reset();
            path.moveTo(x, y + 0.9f * s2);
            path.cubicTo(x - 1.7f * s2, y - 0.15f * s2, x - 0.75f * s2, y - 1.35f * s2, x, y - 0.45f * s2);
            path.cubicTo(x + 0.75f * s2, y - 1.35f * s2, x + 1.7f * s2, y - 0.15f * s2, x, y + 0.9f * s2);
            path.close();
            c.drawPath(path, fill(color));
        }

        void teardrop(float x, float y, float rad, float tipX, float tipY, int color) {
            path.reset();
            path.moveTo(tipX, tipY);
            path.cubicTo(x + 0.3f * rad, y - 0.6f * rad, x + 1.08f * rad, y - 0.2f * rad, x + rad, y + 0.45f * rad);
            r.set(x - rad, y + 0.45f * rad - rad, x + rad, y + 0.45f * rad + rad);
            path.arcTo(r, 0, 180, false);
            path.cubicTo(x - 1.08f * rad, y - 0.2f * rad, x - 0.3f * rad, y - 0.6f * rad, tipX, tipY);
            path.close();
            c.drawPath(path, fill(color));
        }

        void star4(float x, float y, float size, int color) {
            path.reset();
            path.moveTo(x, y - size);
            path.quadTo(x, y, x + size, y);
            path.quadTo(x, y, x, y + size);
            path.quadTo(x, y, x - size, y);
            path.quadTo(x, y, x, y - size);
            path.close();
            c.drawPath(path, fill(color));
        }
    }

    // ================================================================ vẽ một nhân vật trong pill

    private static final G G0 = new G();

    /**
     * Vẽ nhân vật vào ô 30×22 có góc trên-trái (left, top) theo px; dp = mật độ.
     */
    static void draw(Canvas c, Ch ch, float left, float top, float dp, State s, int alpha) {
        G g = G0;
        g.c = c;
        g.s = s;
        g.alpha = alpha;
        int save = c.save();
        c.translate(left, top);
        c.scale(dp, dp);
        float cx = 15f, cy = 11f;
        // biến đổi cả người theo cảm xúc (scale quanh (cx, 17.6), xoay quanh tâm, rồi dịch)
        if (s.emotion >= 0) {
            float t = s.t, et = s.emotionT;
            float sx = 1, sy = 1, rot = 0, dx = 0, dy = 0;
            switch (s.emotion) {
                case ANGRY: dx = 0.9f * (float) Math.sin(55 * t); break;
                case DIZZY: rot = 9 * (float) Math.sin(5 * t); break;
                case LAUGH: dy = -1.6f * (float) Math.abs(Math.sin(16 * t)); break;
                case SURPRISED: {
                    float k = (float) Math.exp(-6 * et);
                    sy = 1 + 0.25f * k;
                    sx = 1 - 0.12f * k;
                    break;
                }
                case SAD: dy = 1.2f; sy = 0.95f; break;
                case LOVE: sx = sy = 1 + 0.04f * (float) Math.sin(8 * t); break;
                case COOL: if (Math.sin(4 * t) > 0.8) dy = 0.6f; break;
                default: break;
            }
            c.translate(dx, dy);
            c.rotate(rot, cx, cy);
            c.scale(sx, sy, cx, 17.6f);
        }
        float dance = s.mood() == MUSIC ? (float) Math.abs(Math.sin(7 * s.t)) * (1.2f + 3.2f * s.beat) : 0f;
        float breathe = (float) Math.sin(s.t * 2.2) * 0.5f;
        float y = ch.rawY() ? cy : cy + 0.1f * s.jump - dance + breathe;
        ch.draw(g, cx, y);
        if (s.emotion == COOL && ch.shades()) shades(g, cx, y);
        c.restoreToCount(save);
        if (s.emotion >= 0) {
            int save2 = c.save();
            c.translate(left, top);
            c.scale(dp, dp);
            float left2 = EMOTE_TIME - s.emotionT;
            g.alpha = (int) (alpha * Math.min(1f, Math.max(0f, left2 / 0.35f)));
            emotionExtras(g, cx, cy);
            g.alpha = alpha;
            c.restoreToCount(save2);
        }
    }

    private static void shades(G g, float cx, float y) {
        float off = -9 * (1 - Math.min(1f, g.s.emotionT / 0.3f));
        float yy = y + off - 0.5f;
        for (int side = -1; side <= 1; side += 2) {
            float ex = cx + side * 6.6f;
            g.r.set(ex - 5.6f, yy - 4.6f, ex + 5.6f, yy + 4f);
            g.c.drawRoundRect(g.r, 3.6f, 3.6f, g.fill(SHADES));
            g.c.drawRoundRect(g.r, 3.6f, 3.6f, g.pen(0xFF9A9CA4, 0.9f));
            g.rrect(ex - 3.6f, yy - 3f, 3f, 1f, 0.5f, Color.WHITE);
        }
        g.line(0xFF9A9CA4, 0.9f, cx - 1.5f, yy - 2.5f, cx + 1.5f, yy - 2.5f);
    }

    private static void emotionExtras(G g, float cx, float cy) {
        State s = g.s;
        float et = s.emotionT, t = s.t, w = 30f;
        switch (s.emotion) {
            case LOVE:
                for (int i = 0; i < 3; i++) {
                    float life = (0.9f * et + 0.33f * i) % 1f;
                    float x = cx + 9 * (i - 1) + 1.5f * (float) Math.sin(7 * life + i);
                    int a = g.alpha;
                    g.alpha = (int) (a * (1 - life));
                    g.heart(x, 2 - 9 * life, 2 + (1 - life), LOVE_C);
                    g.alpha = a;
                }
                break;
            case ANGRY: {
                float k = 1 + 0.15f * (float) Math.sin(14 * t);
                float x = w - 2, y = 1;
                for (int sx = -1; sx <= 1; sx += 2) {
                    for (int sy = -1; sy <= 1; sy += 2) {
                        g.path.reset();
                        g.path.moveTo(x + 0.9f * k * sx, y + 3 * k * sy);
                        g.path.quadTo(x + 0.9f * k * sx, y + 0.9f * k * sy, x + 3 * k * sx, y + 0.9f * k * sy);
                        g.c.drawPath(g.path, g.pen(ANGRY_C, 1.3f));
                    }
                }
                break;
            }
            case SURPRISED: {
                float pop = Math.min(1f, et / 0.15f);
                float y = 3 - 3 * pop;
                g.rrect(w - 3, y - 4, 2.2f, 5.5f * pop, 1f, STAR);
                g.circle(w - 1.9f, y + 3.6f, 1.2f, STAR);
                break;
            }
            case SAD: {
                float life = (0.8f * et) % 1f;
                int a = g.alpha;
                g.alpha = (int) (a * (1 - life * life));
                g.teardrop(cx - 7, cy + 3 + 9 * life + 0.7f, 1.4f, cx - 7, cy + 3 + 9 * life - 1.4f, TEAR);
                g.alpha = a;
                break;
            }
            case DIZZY:
                for (int i = 0; i < 3; i++) {
                    double a = 5 * t + 2 * Math.PI * i / 3;
                    g.star4(cx + 11 * (float) Math.cos(a), 0.5f + 2.5f * (float) Math.sin(a),
                            2.2f + 0.5f * (float) Math.sin(a), STAR);
                }
                break;
            case LAUGH:
                for (int i = 0; i < 2; i++) {
                    float life = (1.6f * et + 0.5f * i) % 1f;
                    float side = i == 0 ? -1 : 1;
                    int a = g.alpha;
                    g.alpha = (int) (a * (1 - life));
                    float x = cx + side * (11 + 6 * life);
                    float y = cy - 2 - 4 * (float) Math.sin(Math.PI * life);
                    g.teardrop(x, y + 0.6f, 1.2f, x, y - 1.2f, TEAR);
                    g.alpha = a;
                }
                break;
            case COOL:
                if (et > 0.3f && et < 0.9f) {
                    g.star4(cx + 9, cy - 4, 2.6f * (float) Math.sin((et - 0.3f) / 0.6f * Math.PI), Color.WHITE);
                }
                break;
            default:
                break;
        }
    }

    // ================================================================ 38 nhân vật

    static {
        add(new Ch("eyes", "Đôi mắt") {
            @Override
            void draw(G g, float cx, float y) {
                g.eye(cx - 6.4f, y - 0.5f, 5.9f, 6.5f, FG, DARK, false);
                g.eye(cx + 6.4f, y - 0.5f, 5.9f, 6.5f, FG, DARK, false);
            }
        });
        add(new Ch("cat", "Mèo") {
            @Override
            void draw(G g, float cx, float y) {
                float tw = Math.max(0, Math.sin(1.3 * g.s.t + 1)) > 0.97 ? 1.2f : 0f;
                g.poly(FG, cx - 9, y - 2, cx - 8.5f, y - 10 - tw, cx - 2.5f, y - 6);
                g.poly(FG, cx + 9, y - 2, cx + 8.5f, y - 10, cx + 2.5f, y - 6);
                g.oval(cx, y + 1, 10, 7.6f, FG);
                g.eye(cx - 4.2f, y, 2.6f, 3.0f, DARK, FG, true);
                g.eye(cx + 4.2f, y, 2.6f, 3.0f, DARK, FG, true);
                g.oval(cx, y + 3.4f, 1.1f, 0.8f, 0xFFFF8FA3);
                g.line(DARK, 0.6f, cx - 9.5f, y + 3, cx - 5.5f, y + 3.6f);
                g.line(DARK, 0.6f, cx + 9.5f, y + 3, cx + 5.5f, y + 3.6f);
            }
        });
        add(new Ch("ghost", "Ma") {
            @Override
            void draw(G g, float cx, float y) {
                y += (float) Math.sin(2.4 * g.s.t) * 1.2f;
                float l = cx - 8, r = cx + 8, top = y - 9, bottom = y + 8;
                Path p = g.path;
                p.reset();
                p.moveTo(l, bottom);
                p.lineTo(l, top + 8);
                g.r.set(l, top, r, top + 16);
                p.arcTo(g.r, 180, 180, false);
                p.lineTo(r, bottom);
                for (int i = 0; i <= 8; i++) {
                    p.lineTo(r - 16f * i / 8f, bottom + (float) Math.sin(1.6 * i + 6 * g.s.t) * 1.5f - 1.5f);
                }
                p.close();
                g.c.drawPath(p, g.fill(FG, 0.94f));
                g.eye(cx - 3.4f, y - 2, 2.4f, 3.1f, DARK, FG, true);
                g.eye(cx + 3.4f, y - 2, 2.4f, 3.1f, DARK, FG, true);
                if (g.mood() == MUSIC) g.oval(cx, y + 3, 1.6f, 1 + 2 * g.s.beat, DARK);
            }
        });
        add(new Ch("robot", "Robot") {
            @Override
            void draw(G g, float cx, float y) {
                State s = g.s;
                int md = g.mood();
                g.line(FG, 1.2f, cx, y - 7, cx, y - 10);
                boolean lit = Math.sin(s.t * (md == MUSIC ? 12 : 3)) > 0;
                g.circle(cx, y - 10.5f, 1.6f, lit ? ACCENT : FG);
                g.rrect(cx - 10, y - 7, 20, 15, 4.5f, FG);
                g.rrect(cx - 8, y - 5, 16, 8, 2.5f, DARK);
                float eh = md == HAPPY ? 1.2f : Math.max(0.8f, 3.6f * (1 - s.blink));
                float ox = 1.3f * s.lookX, oy = 0.8f * s.lookY;
                g.rrect(cx - 5.6f + ox, y - 1 - eh / 2 + oy, 3.2f, eh, 1f, ACCENT);
                g.rrect(cx + 2.4f + ox, y - 1 - eh / 2 + oy, 3.2f, eh, 1f, ACCENT);
                for (int i = 0; i < 4; i++) {
                    float h = md == MUSIC ? 0.8f + (float) Math.abs(Math.sin(9 * s.t + 1.3 * i)) * (1 + 2.5f * s.beat) : 0.9f;
                    g.rrect(cx - 4.4f + 2.4f * i, y + 6.4f - h, 1.5f, h, 0.4f, DARK);
                }
            }

            @Override
            boolean shades() {
                return true;
            }
        });
        add(new Ch("fish", "Cá") {
            @Override
            boolean rawY() {
                return true;
            }

            @Override
            void draw(G g, float cx, float cy) {
                State s = g.s;
                float speed = g.mood() == MUSIC ? 1.5f + s.beat : 0.8f;
                float x = cx + (float) Math.sin(s.t * speed) * 5;
                float dir = Math.cos(s.t * speed) > 0 ? 1 : -1;
                float y = cy + (float) Math.sin(3 * s.t) * 1.2f + 0.1f * s.jump;
                float wag = (float) Math.sin(s.t * (9 + 8 * s.beat)) * 2.4f;
                g.poly(0xFFFF9F0A, x - 5.5f * dir, y, x - 11 * dir, y - 4.5f + wag, x - 11 * dir, y + 4.5f + wag);
                g.oval(x, y, 7, 4.8f, 0xFFFF9F0A);
                g.oval(x + dir, y - 1.6f, 4, 1.6f, 0x66FFFFFF);
                g.eye(x + 3.6f * dir, y - 0.8f, 1.9f, 1.9f, Color.WHITE, Color.BLACK, true);
                for (int i = 0; i < 2; i++) {
                    float life = (0.7f * s.t + 0.5f * i) % 1f;
                    g.c.drawCircle(x + dir * (8 + 2 * i), y - 2 - 9 * life, 1 + 0.8f * life, g.pen(FG, 0.6f));
                }
            }
        });
        add(new Ch("planet", "Hành tinh") {
            @Override
            void draw(G g, float cx, float y) {
                State s = g.s;
                float orbit = s.t * (g.mood() == MUSIC ? 3 + 4 * s.beat : 1.4f);
                float mx = cx + 12 * (float) Math.cos(orbit), my = y + 3.6f * (float) Math.sin(orbit);
                boolean behind = Math.sin(orbit) < 0;
                if (behind) g.circle(mx, my, 1.8f, FG);
                Paint pl = g.fill(0xFF7B79F7);
                pl.setShader(new LinearGradient(cx - 7.4f, y + 7.4f, cx + 7.4f, y - 7.4f, 0xFF7B79F7, 0xFF3A9BFF, Shader.TileMode.CLAMP));
                g.c.drawCircle(cx, y, 7.4f, pl);
                g.eye(cx - 2.6f, y - 0.6f, 1.9f, 2.4f, Color.WHITE, Color.BLACK, true);
                g.eye(cx + 2.6f, y - 0.6f, 1.9f, 2.4f, Color.WHITE, Color.BLACK, true);
                int sv = g.save();
                g.rotate(-14, cx, y);
                g.r.set(cx - 12, y + 0.5f - 3.2f, cx + 12, y + 0.5f + 3.2f);
                g.c.drawOval(g.r, g.pen(0xD0FFD60A, 1.3f));
                g.restore(sv);
                Paint cover = g.fill(0xFF7B79F7);
                cover.setShader(new LinearGradient(cx - 7.4f, y + 7.4f, cx + 7.4f, y - 7.4f, 0xFF7B79F7, 0xFF3A9BFF, Shader.TileMode.CLAMP));
                g.r.set(cx - 6.2f, y - 3.2f - 4.4f, cx + 6.2f, y - 3.2f + 4.4f);
                g.c.drawOval(g.r, cover);
                g.f.setShader(null);
                g.eye(cx - 2.6f, y - 0.6f, 1.9f, 2.4f, Color.WHITE, Color.BLACK, true);
                g.eye(cx + 2.6f, y - 0.6f, 1.9f, 2.4f, Color.WHITE, Color.BLACK, true);
                if (!behind) g.circle(mx, my, 1.8f, FG);
            }
        });
        add(new Ch("slime", "Slime") {
            @Override
            boolean rawY() {
                return true;
            }

            @Override
            void draw(G g, float cx, float cy) {
                State s = g.s;
                boolean m = g.mood() == MUSIC;
                float sq = (float) Math.sin(s.t * (m ? 7 : 2.4)) * (m ? 0.1f + 0.22f * s.beat : 0.05f)
                        + Math.max(-0.25f, Math.min(0.25f, -0.012f * s.jump));
                float rx = 10 * (1 + sq), ry = 8 * (1 - sq), base = cy + 8;
                Path p = g.path;
                p.reset();
                p.moveTo(cx - rx, base);
                p.cubicTo(cx - rx, base - 2.3f * ry, cx + rx, base - 2.3f * ry, cx + rx, base);
                p.close();
                Paint pl = g.fill(0xFF7CF08D);
                pl.setShader(new LinearGradient(0, base - 1.75f * ry, 0, base, 0xFF7CF08D, 0xFF22B348, Shader.TileMode.CLAMP));
                g.c.drawPath(p, pl);
                g.f.setShader(null);
                g.oval(cx - 0.4f * rx, base - 1.25f * ry, 2.2f, 1.2f, 0x70FFFFFF);
                g.eye(cx - 3.4f, base - 0.85f * ry, 2.3f, 2.7f, Color.WHITE, Color.BLACK, true);
                g.eye(cx + 3.4f, base - 0.85f * ry, 2.3f, 2.7f, Color.WHITE, Color.BLACK, true);
            }
        });
        add(new Ch("pulse", "Nhịp tim") {
            @Override
            boolean rawY() {
                return true;
            }

            @Override
            boolean shades() {
                return false;
            }

            @Override
            void draw(G g, float cx, float cy) {
                State s = g.s;
                int md = g.mood();
                float speed = md == MUSIC ? 2.4f : 1.2f;
                float amp = md == HAPPY ? 9 : md == MUSIC ? 5 + 5 * s.beat : 6;
                Path p = g.path;
                p.reset();
                for (int i = 0; i <= 30; i++) {
                    float x = 1 + 28f * i / 30f;
                    float ph = ((2f * i / 30f + s.t * speed) % 1f + 1f) % 1f;
                    float off = ph < 0.12f ? 0 : ph < 0.18f ? -0.25f * amp : ph < 0.24f ? 0.3f * amp
                            : ph < 0.32f ? -amp : ph < 0.40f ? 0.55f * amp : 0;
                    if (i == 0) p.moveTo(x, cy + off);
                    else p.lineTo(x, cy + off);
                }
                g.c.drawPath(p, g.pen(ACCENT, 1.7f));
            }
        });
        add(new Ch("bunny", "Thỏ") {
            @Override
            void draw(G g, float cx, float y) {
                State s = g.s;
                float flop = g.mood() == MUSIC ? (float) Math.sin(7 * s.t) * (6 + 14 * s.beat) : (float) Math.sin(1.4 * s.t) * 3;
                for (int side = -1; side <= 1; side += 2) {
                    int sv = g.save();
                    g.rotate(side * 12 + flop * side, cx + 3.4f * side, y - 3);
                    g.oval(cx + 3.4f * side, y - 9, 2.6f, 6.2f, FG);
                    g.oval(cx + 3.4f * side, y - 9, 1.2f, 4.4f, 0xFFFFB3C6);
                    g.restore(sv);
                }
                g.oval(cx, y + 1.5f, 9, 7, FG);
                g.eye(cx - 3.6f, y + 0.8f, 2.3f, 2.7f, DARK, FG, true);
                g.eye(cx + 3.6f, y + 0.8f, 2.3f, 2.7f, DARK, FG, true);
                g.oval(cx, y + 4, 1, 0.7f, 0xFFFFB3C6);
            }
        });
        add(new Ch("owl", "Cú mèo") {
            @Override
            void draw(G g, float cx, float y) {
                int sv = g.save();
                g.rotate(g.s.lookX * 9, cx, y + 4);
                Paint grad = g.fill(0xFF9C7B5B);
                LinearGradient lg = new LinearGradient(0, y - 10, 0, y + 9.5f, 0xFF9C7B5B, 0xFF6E523A, Shader.TileMode.CLAMP);
                grad.setShader(lg);
                for (int side = -1; side <= 1; side += 2) {
                    g.path.reset();
                    g.path.moveTo(cx + 9 * side, y - 4);
                    g.path.lineTo(cx + 9.5f * side, y - 10);
                    g.path.lineTo(cx + 4 * side, y - 7);
                    g.path.close();
                    g.c.drawPath(g.path, grad);
                }
                g.r.set(cx - 10, y + 1 - 8.5f, cx + 10, y + 1 + 8.5f);
                g.c.drawOval(g.r, grad);
                g.f.setShader(null);
                g.oval(cx, y + 5.5f, 5.5f, 4, 0xFFE9D8BF);
                for (int side = -1; side <= 1; side += 2) {
                    g.circle(cx + 4.4f * side, y - 0.5f, 4.2f, 0xFFFFB340);
                    g.eye(cx + 4.4f * side, y - 0.5f, 3.4f, 3.4f, Color.WHITE, Color.BLACK, true);
                }
                g.poly(0xFFFFB340, cx - 1.4f, y + 2.4f, cx + 1.4f, y + 2.4f, cx, y + 4.6f);
                g.restore(sv);
            }
        });
        add(new Ch("dino", "Khủng long") {
            @Override
            void draw(G g, float cx, float y) {
                State s = g.s;
                boolean m = g.mood() == MUSIC;
                float stomp = m ? (float) Math.abs(Math.sin(7 * s.t)) * (1 + 2 * s.beat) : 0;
                float base = y + 6 - 0.4f * stomp;
                float sway = (float) Math.sin(s.t * (m ? 8 : 2.2)) * 2;
                Paint gr = g.fill(0xFF7CE07A);
                gr.setShader(new LinearGradient(0, base - 10, 0, base + 3, 0xFF7CE07A, 0xFF3CA84E, Shader.TileMode.CLAMP));
                Path p = g.path;
                p.reset();
                p.moveTo(cx - 5, base - 1);
                p.quadTo(cx - 11, base - 1 + sway, cx - 14, base - 4 + sway);
                p.quadTo(cx - 10, base + 2, cx - 4, base + 2);
                p.close();
                g.c.drawPath(p, gr);
                g.f.setShader(null);
                for (int i = 0; i < 3; i++) {
                    float px = cx - 4 + 3.6f * i, py = base - 8.2f + 1.2f * Math.abs(i - 1);
                    g.poly(0xFF2E8B3E, px - 1.6f, py + 2, px + 1.6f, py + 2, px, py - 1.6f);
                }
                gr = g.fill(0xFF7CE07A);
                gr.setShader(new LinearGradient(0, base - 10, 0, base + 3, 0xFF7CE07A, 0xFF3CA84E, Shader.TileMode.CLAMP));
                g.r.set(cx - 6, base - 8, cx + 8, base + 2);
                g.c.drawRoundRect(g.r, 5, 5, gr);
                g.r.set(cx + 7 - 4.6f, base - 6 - 3.8f, cx + 7 + 4.6f, base - 6 + 3.8f);
                g.c.drawOval(g.r, gr);
                g.r.set(cx - 4, base + 1, cx - 1.6f, base + 1 + 2.4f + 0.4f * stomp);
                g.c.drawRect(g.r, gr);
                g.r.set(cx + 3, base + 1, cx + 5.4f, base + 1 + 2.4f + 0.4f * (1 - 0.2f * stomp));
                g.c.drawRect(g.r, gr);
                g.f.setShader(null);
                g.eye(cx + 8, base - 7, 1.7f, 1.9f, Color.WHITE, Color.BLACK, true);
            }
        });
        add(new Ch("star", "Ngôi sao") {
            @Override
            void draw(G g, float cx, float y) {
                State s = g.s;
                int md = g.mood();
                float spin = md == MUSIC ? s.t * (60 + 160 * s.beat) : (float) Math.sin(1.2 * s.t) * 10;
                float pulse = 1 + (md == MUSIC ? 0.18f * s.beat : 0.03f * (float) Math.sin(2.4 * s.t));
                Path p = g.path;
                p.reset();
                for (int i = 0; i < 10; i++) {
                    double a = Math.toRadians(spin + i * 36 - 90);
                    float rr = (i % 2 == 0 ? 10.5f : 5.2f) * pulse;
                    float px = cx + rr * (float) Math.cos(a), py = y + 1 + rr * (float) Math.sin(a);
                    if (i == 0) p.moveTo(px, py);
                    else p.lineTo(px, py);
                }
                p.close();
                Paint gr = g.fill(0xFFFFE67A);
                gr.setShader(new RadialGradient(cx, y + 1, 10.5f, 0xFFFFE67A, 0xFFFFB81C, Shader.TileMode.CLAMP));
                g.c.drawPath(p, gr);
                Paint st = g.pen(0xFFFFB81C, 1.6f);
                g.c.drawPath(p, st);
                g.f.setShader(null);
                g.eye(cx - 2.6f, y + 1.2f, 1.5f, 1.9f, Color.BLACK, Color.WHITE, true);
                g.eye(cx + 2.6f, y + 1.2f, 1.5f, 1.9f, Color.BLACK, Color.WHITE, true);
                for (int i = 0; i < 2; i++) {
                    float life = (0.8f * s.t + 0.5f * i) % 1f;
                    float side = i == 0 ? -1 : 1;
                    g.c.drawCircle(cx + 12 * side, y - 6 + 9 * i, 0.9f,
                            g.fill(Color.WHITE, (float) Math.sin(Math.PI * life)));
                }
            }
        });
        add(new Ch("cloud", "Mây") {
            @Override
            void draw(G g, float cx, float y) {
                State s = g.s;
                y += (float) Math.sin(1.8 * s.t);
                g.oval(cx - 5.5f, y + 2, 5.5f, 4.6f, FG);
                g.oval(cx + 5.5f, y + 2, 5.5f, 4.6f, FG);
                g.oval(cx - 1, y - 2, 6.4f, 6, FG);
                g.oval(cx + 4, y - 0.5f, 4.4f, 4.4f, FG);
                g.eye(cx - 3, y + 1, 1.7f, 2, DARK, FG, true);
                g.eye(cx + 3, y + 1, 1.7f, 2, DARK, FG, true);
                if (g.mood() == MUSIC) {
                    for (int i = 0; i < 3; i++) {
                        float life = (1.3f * s.t + 0.33f * i) % 1f;
                        g.c.drawCircle(cx - 5 + 5 * i, y + 8 + 6 * life, 0.9f, g.fill(ACCENT, 1 - life));
                    }
                }
            }
        });
        add(new Ch("claude", "Claude") {
            private final float[] lens = {9.8f, 8.6f, 9.4f, 8.2f, 10.0f, 8.8f, 9.2f, 8.0f, 9.8f, 8.6f, 9.4f, 8.4f};
            private final float[] skew = {0, 4, -3, 2, -2, 5, -4, 1, 3, -5, 2, -1};

            @Override
            boolean rawY() {
                return true;
            }

            @Override
            int[] aura() {
                return CLAUDE;
            }

            @Override
            void draw(G g, float cx, float cy) {
                State s = g.s;
                int md = g.mood();
                float speed = 2.0f;
                float depth = md == HAPPY ? 0.3f : md == MUSIC ? 0.14f + 0.32f * s.beat : 0.17f;
                Paint pen = g.pen(0xFFD97757, 2.5f);
                for (int i = 0; i < 12; i++) {
                    double phase = i * 2 * Math.PI / 12;
                    double wave = 0.7 * Math.sin(s.t * speed - 2 * phase) + 0.3 * Math.sin(0.43 * s.t * speed + 2.3 * i);
                    float len = lens[i] * (float) (1 + depth * wave);
                    double ang = Math.toRadians(i * 30 + skew[i] + 2 * Math.sin(0.5 * s.t * speed + i) - 90);
                    float dx = (float) Math.cos(ang), dy = (float) Math.sin(ang);
                    g.c.drawLine(cx + 1.5f * dx, cy + 1.5f * dy, cx + len * dx, cy + len * dy, pen);
                }
                g.circle(cx, cy, 3.0f, 0xFFD97757);
            }

            @Override
            boolean shades() {
                return true;
            }
        });
        add(new Ch("clawd", "Cua Clawd") {
            @Override
            boolean rawY() {
                return true;
            }

            @Override
            int[] aura() {
                return CLAUDE;
            }

            @Override
            boolean shades() {
                return false;
            }

            @Override
            void draw(G g, float cx, float cy) {
                drawClawd(g, cx, cy);
            }
        });
        add(new Ch("dog", "Cún") {
            @Override
            void draw(G g, float cx, float y) {
                State s = g.s;
                int md = g.mood();
                float flop = md == MUSIC ? (float) Math.sin(7 * s.t) * (8 + 16 * s.beat) : 3 * (float) Math.sin(1.5 * s.t);
                for (int side = -1; side <= 1; side += 2) {
                    int sv = g.save();
                    g.rotate(side * 16 + flop * side, cx + 7 * side, y - 5);
                    g.oval(cx + 8.6f * side, y - 0.5f, 3.1f, 6.2f, 0xFF9A6238);
                    g.restore(sv);
                }
                g.oval(cx, y + 1, 9.4f, 7.6f, 0xFFE8B87A);
                g.oval(cx, y + 4, 4.6f, 3.2f, PAPER);
                g.darkEyes(cx, y - 0.8f, 4.1f, 2.1f, 2.5f);
                g.oval(cx, y + 2.7f, 1.5f, 1.05f, INK);
                if (md == HAPPY || md == MUSIC) g.oval(cx, y + 6 + s.beat, 1.5f, 1.7f + 1.2f * s.beat, PINK);
            }
        });
        add(new Ch("fox", "Cáo") {
            @Override
            void draw(G g, float cx, float y) {
                float tw = Math.sin(1.1 * g.s.t + 2) > 0.96 ? 1.3f : 0;
                g.poly(ORANGE, cx - 10, y - 1, cx - 9, y - 10.5f - tw, cx - 2.5f, y - 5.5f);
                g.poly(ORANGE, cx + 10, y - 1, cx + 9, y - 10.5f, cx + 2.5f, y - 5.5f);
                g.poly(0xFF5A3422, cx - 9, y - 10.5f - tw, cx - 9.6f, y - 6.5f, cx - 6.4f, y - 8.4f);
                g.poly(0xFF5A3422, cx + 9, y - 10.5f, cx + 9.6f, y - 6.5f, cx + 6.4f, y - 8.4f);
                g.oval(cx, y + 1, 10, 7.4f, ORANGE);
                int sv = g.save();
                g.path.reset();
                g.r.set(cx - 10, y + 1 - 7.4f, cx + 10, y + 1 + 7.4f);
                g.path.addOval(g.r, Path.Direction.CW);
                g.c.clipPath(g.path);
                g.oval(cx - 5.2f, y + 6, 5.6f, 4.6f, PAPER);
                g.oval(cx + 5.2f, y + 6, 5.6f, 4.6f, PAPER);
                g.restore(sv);
                g.darkEyes(cx, y - 0.4f, 4.2f, 2.1f, 2.5f);
                g.oval(cx, y + 4.2f, 1.25f, 0.95f, INK);
            }
        });
        add(new Ch("panda", "Gấu trúc") {
            @Override
            void draw(G g, float cx, float y) {
                g.circle(cx - 7.2f, y - 6, 3.4f, INK);
                g.circle(cx + 7.2f, y - 6, 3.4f, INK);
                g.oval(cx, y + 1, 9.6f, 7.8f, PAPER);
                g.r.set(cx - 9.6f, y + 1 - 7.8f, cx + 9.6f, y + 1 + 7.8f);
                g.c.drawOval(g.r, g.pen(INK, 0.7f));
                for (int side = -1; side <= 1; side += 2) {
                    int sv = g.save();
                    g.rotate(side * 22, cx + 4.2f * side, y);
                    g.oval(cx + 4.2f * side, y, 2.9f, 3.6f, INK);
                    g.restore(sv);
                    g.eye(cx + 4.1f * side, y - 0.2f, 1.6f, 1.9f, Color.WHITE, INK, true);
                }
                g.oval(cx, y + 3.4f, 1.3f, 0.9f, INK);
                if (g.mood() == MUSIC) g.oval(cx, y + 5.6f, 1.1f, 0.5f + 1.4f * g.s.beat, INK);
            }
        });
        add(new Ch("bear", "Gấu") {
            @Override
            void draw(G g, float cx, float y) {
                for (int side = -1; side <= 1; side += 2) {
                    g.circle(cx + 7.2f * side, y - 5.8f, 3.4f, 0xFFA9714B);
                    g.circle(cx + 7.2f * side, y - 5.8f, 1.7f, 0xFFE6C3A1);
                }
                g.oval(cx, y + 1, 9.6f, 7.8f, 0xFFA9714B);
                g.oval(cx, y + 3.8f, 4.2f, 3.1f, 0xFFE6C3A1);
                g.darkEyes(cx, y - 0.8f, 4.1f, 2, 2.4f);
                g.oval(cx, y + 2.7f, 1.5f, 1.05f, INK);
                g.mouth(cx, y + 5.1f, 1.3f, INK);
            }
        });
        add(new Ch("frog", "Ếch") {
            @Override
            void draw(G g, float cx, float y) {
                float puff = g.mood() == MUSIC ? 1.6f * g.s.beat : 0.5f * (float) Math.max(0, Math.sin(2.2 * g.s.t));
                g.circle(cx - 5.2f, y - 4.4f, 3.8f, GREEN);
                g.circle(cx + 5.2f, y - 4.4f, 3.8f, GREEN);
                g.oval(cx, y + 2, 10, 6.6f + puff, GREEN);
                g.eye(cx - 5.2f, y - 4.6f, 2.7f, 2.9f, Color.WHITE, INK, true);
                g.eye(cx + 5.2f, y - 4.6f, 2.7f, 2.9f, Color.WHITE, INK, true);
                g.arc(cx - 5.5f, y + 2.4f, cx + 5.5f, y + 2.4f, 1.6f, 0xFF3E9E4E, 1.1f);
                g.blush(cx - 7, y + 3, 1.6f);
                g.blush(cx + 7, y + 3, 1.6f);
            }
        });
        add(new Ch("penguin", "Chim cánh cụt") {
            @Override
            void draw(G g, float cx, float y) {
                State s = g.s;
                int md = g.mood();
                float flap = md == MUSIC ? (float) Math.sin(9 * s.t) * (14 + 26 * s.beat) : md == HAPPY ? 22 * (float) Math.sin(14 * s.t) : 0;
                for (int side = -1; side <= 1; side += 2) {
                    int sv = g.save();
                    g.rotate(side * (24 + flap), cx + 7 * side, y - 1);
                    g.oval(cx + 8 * side, y + 2.4f, 2.1f, 4.8f, 0xFF2E3852);
                    g.restore(sv);
                    g.oval(cx + 3.2f * side, y + 9.2f, 2.5f, 1.2f, ORANGE);
                }
                g.oval(cx, y + 0.4f, 8.4f, 9, 0xFF2E3852);
                g.oval(cx, y + 2.6f, 5.7f, 6.1f, PAPER);
                g.eye(cx - 2.9f, y - 2.4f, 1.8f, 2.1f, INK, Color.WHITE, true);
                g.eye(cx + 2.9f, y - 2.4f, 1.8f, 2.1f, INK, Color.WHITE, true);
                g.poly(ORANGE, cx - 1.7f, y + 0.2f, cx + 1.7f, y + 0.2f, cx, y + 2.6f);
            }
        });
        add(new Ch("chick", "Gà con") {
            @Override
            void draw(G g, float cx, float y) {
                State s = g.s;
                int md = g.mood();
                float flap = md == MUSIC ? (float) Math.sin(12 * s.t) * (12 + 24 * s.beat) : md == HAPPY ? 20 * (float) Math.sin(16 * s.t) : 0;
                for (int side = -1; side <= 1; side += 2) {
                    int sv = g.save();
                    g.rotate(side * (30 + flap), cx + 7 * side, y + 1);
                    g.oval(cx + 8.6f * side, y + 2, 2.2f, 3.6f, ORANGE);
                    g.restore(sv);
                }
                g.line(YELLOW, 1.5f, cx, y - 7, cx, y - 10.4f);
                g.line(YELLOW, 1.5f, cx - 1.4f, y - 7, cx - 2.8f, y - 9.6f);
                g.line(YELLOW, 1.5f, cx + 1.4f, y - 7, cx + 2.8f, y - 9.6f);
                g.oval(cx, y + 1, 8.4f, 8.2f, YELLOW);
                g.darkEyes(cx, y - 0.8f, 3.5f, 1.9f, 2.3f);
                float open = md == MUSIC ? 0.6f + 1.6f * s.beat : 0;
                g.poly(ORANGE, cx - 2, y + 2, cx + 2, y + 2, cx, y + 4 + open);
                g.blush(cx - 5.6f, y + 3, 1.5f);
                g.blush(cx + 5.6f, y + 3, 1.5f);
            }
        });
        add(new Ch("pig", "Heo") {
            @Override
            void draw(G g, float cx, float y) {
                float sniff = 0.25f * (float) Math.sin(3 * g.s.t);
                g.poly(0xFFF27F9C, cx - 9.5f, y - 2, cx - 8, y - 9.5f, cx - 3, y - 5.5f);
                g.poly(0xFFF27F9C, cx + 9.5f, y - 2, cx + 8, y - 9.5f, cx + 3, y - 5.5f);
                g.oval(cx, y + 1, 9.8f, 7.6f, 0xFFFFB0C2);
                g.oval(cx, y + 3 + sniff, 4.2f, 2.9f, 0xFFF27F9C);
                g.oval(cx - 1.5f, y + 3 + sniff, 0.75f, 1.05f, INK);
                g.oval(cx + 1.5f, y + 3 + sniff, 0.75f, 1.05f, INK);
                g.darkEyes(cx, y - 1.4f, 4.4f, 1.9f, 2.3f);
            }
        });
        add(new Ch("koala", "Koala") {
            @Override
            void draw(G g, float cx, float y) {
                for (int side = -1; side <= 1; side += 2) {
                    g.circle(cx + 8.6f * side, y - 3.2f, 4.7f, 0xFFA7AEBA);
                    g.circle(cx + 8.8f * side, y - 3, 2.6f, 0xFFE3E6EC);
                }
                g.oval(cx, y + 1, 8.6f, 7.6f, 0xFFA7AEBA);
                g.darkEyes(cx, y - 0.6f, 4.4f, 1.7f, 2.1f);
                g.oval(cx, y + 2, 2, 2.8f, INK);
            }
        });
        add(new Ch("mouse", "Chuột") {
            @Override
            void draw(G g, float cx, float y) {
                float wig = 0.3f * (float) Math.sin(9 * g.s.t);
                for (int side = -1; side <= 1; side += 2) {
                    g.circle(cx + 7.2f * side, y - 4.8f, 5, 0xFFC9CDD6);
                    g.circle(cx + 7.2f * side, y - 4.6f, 3, PINK);
                }
                g.oval(cx, y + 2, 8.2f, 6.8f, 0xFFC9CDD6);
                g.darkEyes(cx, y + 0.6f, 3.6f, 1.9f, 2.3f);
                g.oval(cx, y + 4.4f + wig, 1.1f, 0.85f, PINK);
                g.line(INK, 0.7f, cx - 8.6f, y + 3.6f, cx - 4.6f, y + 4.4f + wig);
                g.line(INK, 0.7f, cx + 8.6f, y + 3.6f, cx + 4.6f, y + 4.4f + wig);
            }
        });
        add(new Ch("bee", "Ong") {
            @Override
            void draw(G g, float cx, float y) {
                State s = g.s;
                y += 1.1f * (float) Math.sin(5 * s.t);
                float b = (float) Math.abs(Math.sin(38 * s.t));
                for (int side = -1; side <= 1; side += 2) {
                    int sv = g.save();
                    g.rotate(side * (24 + 20 * b), cx + 2 * side, y - 4);
                    g.c.drawOval(cx + 4.6f * side - 3.1f, y - 8 - 4.6f, cx + 4.6f * side + 3.1f, y - 8 + 4.6f, g.fill(PAPER, 0.75f));
                    g.restore(sv);
                }
                g.oval(cx, y + 1.8f, 8.8f, 6.4f, YELLOW);
                int sv = g.save();
                g.path.reset();
                g.r.set(cx - 8.8f, y + 1.8f - 6.4f, cx + 8.8f, y + 1.8f + 6.4f);
                g.path.addOval(g.r, Path.Direction.CW);
                g.c.clipPath(g.path);
                g.c.drawRect(cx + 1.6f, y - 6, cx + 4, y + 9, g.fill(INK));
                g.c.drawRect(cx + 5.6f, y - 6, cx + 8, y + 9, g.fill(INK));
                g.restore(sv);
                g.darkEyes(cx - 3.2f, y + 1.4f, 2.5f, 1.7f, 2.1f);
                g.line(INK, 0.7f, cx - 4.6f, y - 3.8f, cx - 6.2f, y - 7.4f);
                g.line(INK, 0.7f, cx - 1.8f, y - 4.4f, cx - 1.4f, y - 8);
                g.circle(cx - 6.2f, y - 7.4f, 0.8f, INK);
                g.circle(cx - 1.4f, y - 8, 0.8f, INK);
            }
        });
        add(new Ch("octopus", "Bạch tuộc") {
            @Override
            void draw(G g, float cx, float y) {
                State s = g.s;
                float speed = g.mood() == MUSIC ? 8 : 3.2f;
                Paint pen = g.pen(0xFFB07CF5, 2.7f);
                for (int i = 0; i < 4; i++) {
                    float x = cx - 6 + 4 * i;
                    float sway = (float) Math.sin(s.t * speed + 1.4 * i) * (1.6f + 2 * s.beat);
                    g.path.reset();
                    g.path.moveTo(x, y + 2);
                    g.path.quadTo(x + sway, y + 6, x - 0.7f * sway, y + 9.4f);
                    g.c.drawPath(g.path, pen);
                }
                g.oval(cx, y - 1.4f, 8.4f, 7.2f, 0xFFB07CF5);
                g.eye(cx - 3.3f, y - 1.4f, 2.2f, 2.6f, Color.WHITE, INK, true);
                g.eye(cx + 3.3f, y - 1.4f, 2.2f, 2.6f, Color.WHITE, INK, true);
                g.blush(cx - 5.8f, y + 1.4f, 1.4f);
                g.blush(cx + 5.8f, y + 1.4f, 1.4f);
            }
        });
        add(new Ch("mushroom", "Nấm") {
            @Override
            void draw(G g, float cx, float y) {
                State s = g.s;
                g.oval(cx, y + 4.6f, 5.4f, 5, 0xFFFFF1DC);
                float tilt = g.mood() == MUSIC ? (float) Math.sin(7 * s.t) * (3 + 6 * s.beat) : 1.5f * (float) Math.sin(1.6 * s.t);
                int sv = g.save();
                g.rotate(tilt, cx, y + 2);
                g.path.reset();
                g.path.moveTo(cx - 10.6f, y + 1.6f);
                g.r.set(cx - 10.6f, y + 1.6f - 10.4f, cx + 10.6f, y + 1.6f + 10.4f);
                g.path.arcTo(g.r, 180, 180, false);
                g.path.quadTo(cx, y + 4, cx - 10.6f, y + 1.6f);
                g.path.close();
                g.c.drawPath(g.path, g.fill(0xFFFF5A5F));
                g.oval(cx - 5.4f, y - 3, 1.9f, 1.7f, PAPER);
                g.oval(cx + 4.4f, y - 4.4f, 2.2f, 1.9f, PAPER);
                g.oval(cx - 0.4f, y - 7, 1.4f, 1.2f, PAPER);
                g.oval(cx + 7.6f, y - 0.6f, 1.2f, 1.1f, PAPER);
                g.restore(sv);
                g.darkEyes(cx, y + 5, 2.3f, 1.4f, 1.8f);
            }
        });
        add(new Ch("cactus", "Xương rồng") {
            @Override
            void draw(G g, float cx, float y) {
                State s = g.s;
                int md = g.mood();
                float lift = md == MUSIC ? 3 * s.beat + 1.5f * (float) Math.abs(Math.sin(7 * s.t)) : md == HAPPY ? 2 : 0;
                Paint pen = g.pen(0xFF4CB86B, 3.5f);
                g.path.reset();
                g.path.moveTo(cx - 4, y + 3.4f);
                g.path.lineTo(cx - 8.6f, y + 3.4f);
                g.path.lineTo(cx - 8.6f, y - 1 - lift);
                g.c.drawPath(g.path, pen);
                g.path.reset();
                g.path.moveTo(cx + 4, y + 0.6f);
                g.path.lineTo(cx + 8.6f, y + 0.6f);
                g.path.lineTo(cx + 8.6f, y - 4 - lift);
                g.c.drawPath(g.path, pen);
                g.rrect(cx - 4.8f, y - 8.6f, 9.6f, 18, 4.8f, 0xFF4CB86B);
                g.oval(cx + 1.6f, y - 9, 2, 1.7f, PINK);
                g.circle(cx + 1.6f, y - 9, 0.8f, YELLOW);
                g.darkEyes(cx, y - 1.6f, 2.1f, 1.4f, 1.8f);
                g.mouth(cx, y + 1.9f, 1.1f, INK);
            }
        });
        add(new Ch("alien", "Người ngoài hành tinh") {
            @Override
            void draw(G g, float cx, float y) {
                State s = g.s;
                y += 0.8f * (float) Math.sin(2 * s.t);
                float sway = 1.4f * (float) Math.sin(3 * s.t) + (g.mood() == MUSIC ? 3 * s.beat * (float) Math.sin(9 * s.t) : 0);
                g.line(0xFF7BE08A, 1.2f, cx, y - 7, cx + sway, y - 10.6f);
                g.circle(cx + sway, y - 10.8f, 1.3f, 0xFFD6FFDC);
                g.path.reset();
                g.path.moveTo(cx, y - 8);
                g.path.cubicTo(cx + 11.4f, y - 8, cx + 9, y + 5.6f, cx, y + 8.6f);
                g.path.cubicTo(cx - 9, y + 5.6f, cx - 11.4f, y - 8, cx, y - 8);
                g.path.close();
                g.c.drawPath(g.path, g.fill(0xFF7BE08A));
                for (int side = -1; side <= 1; side += 2) {
                    int sv = g.save();
                    g.rotate(side * -18, cx + 3.8f * side, y - 0.8f);
                    g.eye(cx + 3.8f * side, y - 0.8f, 2.7f, 3.5f, INK, 0xFFD6FFDC, true);
                    g.restore(sv);
                }
                g.oval(cx, y + 5.2f, 0.9f, 0.5f + (g.mood() == MUSIC ? 1.2f * s.beat : 0), INK);
            }
        });
        add(new Ch("bat", "Dơi") {
            @Override
            void draw(G g, float cx, float y) {
                State s = g.s;
                float flap = (float) Math.sin(s.t * (g.mood() == MUSIC ? 14 : 8));
                y += 0.9f * flap;
                for (int side = -1; side <= 1; side += 2) {
                    float k = side;
                    g.poly(0xFF6B57A8,
                            cx + 4 * k, y - 1, cx + 13 * k, y - 4 - 4.5f * flap, cx + 10.6f * k, y + 0.8f,
                            cx + 12.4f * k, y + 3.6f - 2 * flap, cx + 8 * k, y + 2.6f,
                            cx + 7.6f * k, y + 5.6f - flap, cx + 4 * k, y + 4);
                    g.poly(0xFF6B57A8, cx + 5.4f * k, y - 3, cx + 4.6f * k, y - 9, cx + 1.2f * k, y - 5);
                }
                g.oval(cx, y + 0.6f, 5.8f, 5.6f, 0xFF6B57A8);
                g.eye(cx - 2.3f, y - 0.2f, 1.6f, 2, Color.WHITE, INK, true);
                g.eye(cx + 2.3f, y - 0.2f, 1.6f, 2, Color.WHITE, INK, true);
                g.poly(PAPER, cx - 1.6f, y + 3, cx - 0.5f, y + 3, cx - 1.05f, y + 4.5f);
                g.poly(PAPER, cx + 1.6f, y + 3, cx + 0.5f, y + 3, cx + 1.05f, y + 4.5f);
            }
        });
        add(new Ch("moon", "Mặt trăng") {
            private final Path a = new Path(), b = new Path();   // dùng lại, không cấp phát mỗi khung hình

            @Override
            void draw(G g, float cx, float y) {
                State s = g.s;
                boolean m = g.mood() == MUSIC;
                float rock = (float) Math.sin(s.t * (m ? 6 : 1.3)) * (m ? 8 + 10 * s.beat : 5);
                int sv = g.save();
                g.rotate(rock, cx, y + 6);
                a.reset();
                a.addCircle(cx - 1, y, 9.4f, Path.Direction.CW);
                b.reset();
                b.addCircle(cx + 4.4f, y - 2.2f, 8, Path.Direction.CW);
                a.op(b, Path.Op.DIFFERENCE);
                g.c.drawPath(a, g.fill(0xFFFFE082));
                g.eye(cx - 5.6f, y + 0.4f, 1.5f, 1.9f, INK, Color.WHITE, true);
                g.blush(cx - 5.2f, y + 4, 1.3f);
                g.restore(sv);
                for (int i = 0; i < 2; i++) {
                    float life = (0.6f * s.t + 0.5f * i) % 1f;
                    g.c.drawCircle(cx + 8 + 3.4f * i, y - 5 + 8 * i, 1 - 0.2f * i,
                            g.fill(Color.WHITE, 0.25f + 0.75f * (float) Math.sin(Math.PI * life)));
                }
            }
        });
        add(new Ch("sun", "Mặt trời") {
            @Override
            void draw(G g, float cx, float y) {
                State s = g.s;
                boolean m = g.mood() == MUSIC;
                float spin = s.t * (m ? 50 + 140 * s.beat : 16);
                float grow = m ? 1.8f * s.beat : 0.4f * (float) Math.sin(2.4 * s.t);
                Paint pen = g.pen(0xFFFFC23A, 1.8f);
                for (int i = 0; i < 8; i++) {
                    double a = Math.toRadians(spin + i * 45);
                    float dx = (float) Math.cos(a), dy = (float) Math.sin(a);
                    g.c.drawLine(cx + 8.2f * dx, y + 0.5f + 8.2f * dy, cx + (10.2f + grow) * dx, y + 0.5f + (10.2f + grow) * dy, pen);
                }
                g.circle(cx, y + 0.5f, 6.6f, 0xFFFFC23A);
                g.darkEyes(cx, y, 2.5f, 1.5f, 1.9f);
                g.mouth(cx, y + 3.2f, 1.2f, INK);
                g.blush(cx - 4.4f, y + 2.4f, 1.2f);
                g.blush(cx + 4.4f, y + 2.4f, 1.2f);
            }
        });
        add(new Ch("flame", "Ngọn lửa") {
            @Override
            void draw(G g, float cx, float y) {
                State s = g.s;
                float lean = 1.5f * (float) Math.sin(6.3 * s.t) + 0.7f * (float) Math.sin(11.1 * s.t);
                float tall = 0.9f * (float) Math.sin(9.7 * s.t) + 3.4f * s.beat;
                g.teardrop(cx, y + 1.6f, 7.2f, cx + lean, y - 9.4f - tall, 0xFFFF742B);
                g.teardrop(cx, y + 3.4f, 4.9f, cx + 0.5f * lean, y - 4.4f - 0.5f * tall, 0xFFFFD35A);
                g.darkEyes(cx, y + 3, 2.1f, 1.3f, 1.7f);
            }
        });
        add(new Ch("drop", "Giọt nước") {
            @Override
            void draw(G g, float cx, float y) {
                State s = g.s;
                float sx = 1 + 0.035f * (float) Math.sin(4 * s.t) + 0.07f * s.beat;
                float sy = 1 - 0.035f * (float) Math.sin(4 * s.t) - 0.05f * s.beat;
                int sv = g.save();
                g.c.scale(sx, sy, cx, y + 8);
                g.teardrop(cx, y + 1.6f, 7.4f, cx, y - 9.6f, BLUE);
                g.oval(cx - 3.6f, y - 0.6f, 1.2f, 2.1f, g.fill(Color.WHITE, 0.7f));
                g.darkEyes(cx, y + 2.6f, 2.6f, 1.5f, 1.9f);
                g.blush(cx - 4.6f, y + 5, 1.2f);
                g.blush(cx + 4.6f, y + 5, 1.2f);
                g.restore(sv);
            }
        });
        add(new Ch("heart", "Trái tim") {
            @Override
            void draw(G g, float cx, float y) {
                State s = g.s;
                float phase = (s.t * 1.3f) % 1f;
                float beat = g.mood() == MUSIC ? 0.2f * s.beat
                        : (float) Math.max(0, Math.sin(6 * Math.PI * phase)) * (phase < 0.34f ? 0.1f : 0);
                int sv = g.save();
                g.c.scale(1 + beat, 1 + beat, cx, y + 1);
                g.heart(cx, y + 1, 9.4f * 2f, LOVE_C);
                g.oval(cx - 4.4f, y - 3.4f, 1.4f, 1, g.fill(Color.WHITE, 0.55f));
                g.darkEyes(cx, y + 0.4f, 2.7f, 1.5f, 1.9f);
                g.mouth(cx, y + 3.6f, 1.1f, INK);
                g.restore(sv);
            }
        });
        add(new Ch("pumpkin", "Bí ngô") {
            @Override
            void draw(G g, float cx, float y) {
                State s = g.s;
                g.rrect(cx - 1.3f, y - 9.6f, 2.6f, 4, 1, 0xFF5A9E4B);
                g.oval(cx - 5, y + 1.4f, 5.4f, 6.6f, ORANGE);
                g.oval(cx + 5, y + 1.4f, 5.4f, 6.6f, ORANGE);
                g.oval(cx, y + 1.4f, 5.4f, 7, 0xFFFFA845);
                int a = g.alpha;
                g.alpha = (int) (a * Math.max(0, Math.min(1, 0.8f + 0.1f * (float) Math.sin(9 * s.t) + 0.08f * (float) Math.sin(14.3 * s.t))));
                g.eye(cx - 3.6f, y - 0.4f, 2, 2.3f, INK, 0xFFFFE87A, true);
                g.eye(cx + 3.6f, y - 0.4f, 2, 2.3f, INK, 0xFFFFE87A, true);
                g.alpha = a;
                g.path.reset();
                float gx = cx - 4.6f;
                g.path.moveTo(gx, y + 3.6f);
                for (int i = 1; i <= 6; i++) g.path.lineTo(gx + 1.53f * i, y + 3.6f + (i % 2 == 1 ? 1.5f : 0));
                g.c.drawPath(g.path, g.pen(INK, 1.1f));
            }
        });
        add(new Ch("none", "Không có") {
            @Override
            boolean shades() {
                return false;
            }

            @Override
            void draw(G g, float cx, float y) {
                // không vẽ gì
            }
        });
    }

    private static void add(Ch c) {
        ALL.put(c.id, c);
    }

    // ================================================================ Clawd (pixel)

    private static void drawClawd(G g, float cx, float cy) {
        State s = g.s;
        float p = 1.9f, t = s.t;
        int md = s.mood();
        boolean greeting = s.greet >= 0 && s.greet < 1.6f;
        boolean happy = md == HAPPY && !greeting;
        float walk = (md == IDLE && !greeting) ? (float) Math.sin(0.7 * t) * 2.2f : 0f;
        float bob = md == MUSIC ? -(float) Math.abs(Math.sin(7 * t)) * (2 + 4 * s.beat) : (float) Math.sin(2.4 * t) * 0.5f;
        float ox = cx - 7.4f * p + walk;
        float oy = cy + 7.3f - 9.5f * p + bob + 0.1f * s.jump;
        int body = 0xFFD97757, eye = 0xFF14100E;

        // thân
        cell(g, ox, oy, p, 1.5f, 0, 12, 7, body);
        // chân
        float[] legs = {2.9f, 4.7f, 9.3f, 11.1f};
        boolean walking = walk != 0 && Math.abs(Math.cos(0.7 * t)) > 0.25;
        for (int i = 0; i < 4; i++) {
            float lift = 0;
            if (md == MUSIC && Math.sin(7 * t + 1.6 * i) > 0.3) lift = 0.6f;
            else if (walking && Math.sin(9 * t + i * Math.PI) > 0) lift = 0.7f;
            cell(g, ox, oy, p, legs[i], 7, 1, 2.5f - lift, body);
        }
        // càng
        float armL = 0, armR = 0;
        if (md == MUSIC) {
            if (Math.sin(7 * t) > 0) armL = -1; else armR = -1;
        }
        if (happy) armL = armR = -1.4f;
        if (md == IDLE && Math.sin(0.9 * t) > 0.93) armR = -1 + 0.4f * (float) Math.sin(14 * t);
        if (greeting) armR = Math.sin(s.greet * 18) > 0 ? -1.7f : -0.8f;
        cell(g, ox, oy, p, 0.2f, 2.2f + armL, 1.4f, 1.7f, body);
        cell(g, ox, oy, p, 13.4f, 2.2f + armR, 1.4f, 1.7f, body);

        // mắt
        float look = Math.round(s.lookX * 0.6f * 2) / 2f;
        float lookUp = Math.round(s.lookY * 0.4f * 2) / 2f;
        float row = 2.6f + lookUp;
        boolean wink = greeting && s.greet > 0.2f && s.greet < 1.3f;
        if (s.emotion == LOVE) {
            pixelHeart(g, ox, oy, p, 3.4f + look, row - 0.3f);
            pixelHeart(g, ox, oy, p, 10.2f + look, row - 0.3f);
        } else if (happy) {
            chevron(g, ox, oy, p, 3.6f + look, row, 1, eye);
            chevron(g, ox, oy, p, 10.4f + look, row, -1, eye);
        } else {
            float eh = s.emotion == SURPRISED ? 1.8f : 1.3f * (1 - 0.8f * s.blink);
            float ew = s.emotion == SURPRISED ? 1.8f : 1.3f;
            cell(g, ox, oy, p, 3.6f + look, row + (1.3f - eh) / 2 - 0.1f, ew, eh, eye);
            if (wink) chevron(g, ox, oy, p, 10.4f + look, row, -1, eye);
            else cell(g, ox, oy, p, 10.4f + look, row + (1.3f - eh) / 2 - 0.1f, ew, eh, eye);
        }
        if (s.emotion == COOL) {
            float drop = -3 * (1 - Math.min(1f, s.emotionT / 0.3f));
            cell(g, ox, oy, p, 2.6f, row - 0.4f + drop, 10, 1.9f, SHADES);
            cell(g, ox, oy, p, 3.2f, row - 0.1f + drop, 0.6f, 0.5f, Color.WHITE);
            cell(g, ox, oy, p, 9.9f, row - 0.1f + drop, 0.6f, 0.5f, Color.WHITE);
        }
    }

    private static void cell(G g, float ox, float oy, float p, float col, float row, float w, float h, int color) {
        g.c.drawRect(ox + col * p, oy + row * p, ox + col * p + w * p + 0.15f, oy + row * p + h * p + 0.15f, g.fill(color));
    }

    /** Mắt "> <" ba ô 0.55 px. */
    private static void chevron(G g, float ox, float oy, float p, float col, float row, int dir, int color) {
        float u = 0.55f;
        float a = dir > 0 ? 0 : 2 * u, b = u;
        cell(g, ox, oy, p, col + a, row - 0.2f, u, u, color);
        cell(g, ox, oy, p, col + b, row - 0.2f + u, u, u, color);
        cell(g, ox, oy, p, col + a, row - 0.2f + 2 * u, u, u, color);
    }

    private static void pixelHeart(G g, float ox, float oy, float p, float col, float row) {
        String pat = "#.####.#.";
        float u = 0.55f;
        for (int i = 0; i < 9; i++) {
            if (pat.charAt(i) != '#') continue;
            cell(g, ox, oy, p, col + (i % 3) * u, row + (i / 3) * u, u, u, LOVE_C);
        }
    }
}
