package vn.islanddrop;

import android.graphics.Bitmap;
import android.media.session.MediaController;
import android.os.SystemClock;

/** Ảnh chụp trạng thái bài nhạc đang phát (bất biến, trừ controller dùng để điều khiển). */
final class MediaState {
    final MediaController controller;
    final String pkg, title, artist;
    final Bitmap art;
    final boolean playing;
    final long duration, position, positionTime;
    final float speed;

    MediaState(MediaController controller, String pkg, String title, String artist, Bitmap art,
               boolean playing, long duration, long position, long positionTime, float speed) {
        this.controller = controller;
        this.pkg = pkg;
        this.title = title;
        this.artist = artist;
        this.art = art;
        this.playing = playing;
        this.duration = duration;
        this.position = position;
        this.positionTime = positionTime;
        this.speed = speed;
    }

    /** Vị trí hiện tại (ms), tự cộng thời gian trôi khi đang phát. */
    long currentPosition() {
        long p = position;
        if (playing && positionTime > 0) {
            p += (long) ((SystemClock.elapsedRealtime() - positionTime) * (speed == 0f ? 1f : speed));
        }
        if (duration > 0) p = Math.min(p, duration);
        return Math.max(0, p);
    }
}
