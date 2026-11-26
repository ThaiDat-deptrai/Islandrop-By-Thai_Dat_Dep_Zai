package vn.islanddrop;

import android.content.ComponentName;
import android.graphics.Bitmap;
import android.media.MediaMetadata;
import android.media.session.MediaController;
import android.media.session.MediaSessionManager;
import android.media.session.PlaybackState;
import android.os.Handler;
import android.os.Looper;
import android.service.notification.NotificationListenerService;

import java.util.List;

/**
 * Theo dõi bài nhạc đang phát (Spotify, YouTube Music, Zing MP3...) để hiện trên đảo.
 * Android chỉ cho đọc phiên phát nhạc khi app có quyền "Truy cập thông báo", nên dịch vụ này
 * là NotificationListenerService — nhưng KHÔNG đọc nội dung thông báo nào, chỉ dùng MediaSession.
 */
public class MediaListener extends NotificationListenerService {

    /** Trạng thái mới nhất (null = không có nhạc). Chỉ đọc/ghi trên luồng chính. */
    static MediaState current;

    private final Handler main = new Handler(Looper.getMainLooper());
    private MediaSessionManager msm;
    private MediaController controller;
    private ComponentName me;

    private final MediaSessionManager.OnActiveSessionsChangedListener sessionsListener =
            this::onSessions;

    private final MediaController.Callback callback = new MediaController.Callback() {
        @Override
        public void onPlaybackStateChanged(PlaybackState state) {
            // app khác có thể vừa bắt đầu phát: chọn lại phiên phù hợp nhất
            refreshSessions();
        }

        @Override
        public void onMetadataChanged(MediaMetadata metadata) {
            publish();
        }

        @Override
        public void onSessionDestroyed() {
            refreshSessions();
        }
    };

    @Override
    public void onListenerConnected() {
        try {
            msm = getSystemService(MediaSessionManager.class);
            me = new ComponentName(this, MediaListener.class);
            msm.addOnActiveSessionsChangedListener(sessionsListener, me, main);
            refreshSessions();
            L.i(this, "Nhạc: đã kết nối");
        } catch (RuntimeException ex) {
            L.i(this, "Nhạc: không đọc được phiên phát: " + ex);
        }
    }

    @Override
    public void onListenerDisconnected() {
        try {
            if (msm != null) msm.removeOnActiveSessionsChangedListener(sessionsListener);
        } catch (RuntimeException ignored) {
            // đã gỡ
        }
        setController(null);
        current = null;
        notifyService();
    }

    private void refreshSessions() {
        try {
            if (msm != null) onSessions(msm.getActiveSessions(me));
        } catch (RuntimeException ex) {
            L.i(this, "Nhạc: lỗi đọc phiên: " + ex);
        }
    }

    /** Ưu tiên phiên đang phát; không có thì phiên đầu danh sách (mới dùng nhất). */
    private void onSessions(List<MediaController> list) {
        MediaController pick = null;
        if (list != null) {
            for (MediaController c : list) {
                PlaybackState s = c.getPlaybackState();
                if (s != null && s.getState() == PlaybackState.STATE_PLAYING) {
                    pick = c;
                    break;
                }
            }
            if (pick == null && !list.isEmpty()) pick = list.get(0);
        }
        setController(pick);
        publish();
    }

    private void setController(MediaController c) {
        if (controller != null && (c == null || !controller.getSessionToken().equals(c.getSessionToken()))) {
            controller.unregisterCallback(callback);
            controller = null;
        }
        if (c != null && controller == null) {
            controller = c;
            controller.registerCallback(callback, main);
        }
    }

    private void publish() {
        MediaController c = controller;
        MediaState s = null;
        if (c != null) {
            MediaMetadata md = c.getMetadata();
            PlaybackState ps = c.getPlaybackState();
            String title = md == null ? null : md.getString(MediaMetadata.METADATA_KEY_TITLE);
            // chỉ hiện khi có bài thật (bỏ phiên rỗng của app đã dừng)
            boolean hasState = ps != null && ps.getState() != PlaybackState.STATE_NONE
                    && ps.getState() != PlaybackState.STATE_STOPPED
                    && ps.getState() != PlaybackState.STATE_ERROR;
            if (title != null && hasState) {
                String artist = md.getString(MediaMetadata.METADATA_KEY_ARTIST);
                if (artist == null) artist = md.getString(MediaMetadata.METADATA_KEY_ALBUM_ARTIST);
                Bitmap art = md.getBitmap(MediaMetadata.METADATA_KEY_ALBUM_ART);
                if (art == null) art = md.getBitmap(MediaMetadata.METADATA_KEY_ART);
                if (art == null) art = md.getBitmap(MediaMetadata.METADATA_KEY_DISPLAY_ICON);
                s = new MediaState(c, c.getPackageName(), title, artist == null ? "" : artist, art,
                        ps.getState() == PlaybackState.STATE_PLAYING,
                        md.getLong(MediaMetadata.METADATA_KEY_DURATION),
                        ps.getPosition(), ps.getLastPositionUpdateTime(), ps.getPlaybackSpeed());
            }
        }
        current = s;
        notifyService();
    }

    private void notifyService() {
        DropService svc = DropService.instance;
        if (svc != null) svc.onMediaChanged();
    }
}
