package android.media;

import android.os.Handler;

/* AOSP android.media.AudioRouting (API 19): implemented by AudioTrack/AudioRecord;
 * androidx.media3's AudioTrack (1.11) references its listener interface. */
public interface AudioRouting {
    public interface OnRoutingChangedListener {
        void onRoutingChanged(AudioRouting router);
    }

    void addOnRoutingChangedListener(OnRoutingChangedListener listener, Handler handler);

    void removeOnRoutingChangedListener(OnRoutingChangedListener listener);
}
