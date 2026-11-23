package android.media.session;

import android.annotation.NonNull;
import android.annotation.Nullable;
import android.app.PendingIntent;
import android.atl.annotation.Export;
import android.content.Context;
import android.content.Intent;
import android.media.AudioAttributes;
import android.media.MediaDescription;
import android.media.MediaMetadata;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.ResultReceiver;
import android.view.KeyEvent;
import java.util.ArrayList;
import java.util.List;

public class MediaSession {

	private List<QueueItem> queue;

	/* ATL has no system-server MediaSessionService (AOSP routes media
	 * buttons there, see PhoneWindowManager), so live sessions register
	 * here and atlDispatchMediaKey delivers to them in-process. ATL runs
	 * one app per process, so the sessions a process can see are exactly
	 * its own. */
	private static final ArrayList<MediaSession> atlSessions = new ArrayList<>();

	/* AOSP PlaybackState.STATE_PLAYING / STATE_BUFFERING (the class in
	 * api-impl has no state constants to reference; AOSP values are
	 * NONE=0 STOPPED=1 PAUSED=2 PLAYING=3 BUFFERING=4 ERROR=5
	 * CONNECTING=6). */
	private static final int ATL_STATE_PLAYING = 3;
	private static final int ATL_STATE_BUFFERING = 4;
	/* androidx.media3's vendored legacy PlaybackStateCompat (apps on
	 * androidx.media3.session publish through it) renumbers the states:
	 * BUFFERING=6, ERROR=7, CONNECTING=8. */
	private static final int ATL_STATE_BUFFERING_LEGACY = 6;

	private Callback callback;
	private PlaybackState lastPlaybackState;

	public static final class Token {}

	public static class QueueItem {
		long id;
		MediaDescription description;

		public QueueItem(MediaDescription description, long id) {
			this.description = description;
			this.id = id;
		}
	}

	public MediaSession(Context context, String tag) {
		synchronized (atlSessions) {
			atlSessions.add(this);
		}
	}

	public Token getSessionToken() {
		return new Token();
	}

	public void setFlags(int flags) {}

	public void setCallback(Callback callback, Handler handler) {
		this.callback = callback;
		nativeSetCallback(callback);
	}

	public void setCallback(Callback callback) {
		this.callback = callback;
		nativeSetCallback(callback);
	}

	public void setMediaButtonReceiver(PendingIntent pendingIntent) {}

	public void setActive(boolean active) {}

	public void setPlaybackState(PlaybackState state) {
		lastPlaybackState = state;
		/* Witness line for the media-key investigation: media3's player
		 * state machine as published to the session (in the app's own
		 * state numbering, see the legacy renumbering note above). */
		System.out.println("MediaSession.setPlaybackState: state=" + state.state
			+ " position=" + state.position + " updateTime=" + state.updateTime
			+ " queueItemId=" + state.activeQueueItemId);
		String title = null;
		String subTitle = null;
		String artUrl = null;
		if (queue != null)
			for (QueueItem item : queue) {
				if (item.id == state.activeQueueItemId) {
					title = item.description.title.toString();
					subTitle = item.description.subtitle.toString();
					artUrl = item.description.iconUri == null ? null : item.description.iconUri.toString();
					break;
				}
			}
		nativeSetState(state.state, state.actions, state.position, state.updateTime, title, subTitle, artUrl);
	}

	public void setMetadata(MediaMetadata metadata) {}

	public void setQueue(List<QueueItem> queue) {
		this.queue = queue;
	}

	public void release() {
		synchronized (atlSessions) {
			atlSessions.remove(this);
		}
	}

	/**
	 * ATL-internal entry for media-transport key codes (mapped from the
	 * host's XF86Audio* keysyms in WrapperWidget.c). AOSP consumes those
	 * keys in the system server and feeds MediaSessionManager.
	 * dispatchMediaKeyEvent(); ATL routes the equivalent MEDIA_BUTTON
	 * intent straight to this process's live sessions. WrapperWidget.c
	 * consumes the key when this returns true (AOSP consumes these keys
	 * in the system server); with no session holding it the key falls
	 * through to the view tree, which on a desktop host is friendlier
	 * than dropping it. Returns whether any session handled it.
	 */
	@Export
	public static boolean atlDispatchMediaKey(KeyEvent event) {
		ArrayList<MediaSession> snapshot;
		synchronized (atlSessions) {
			if (atlSessions.isEmpty())
				return false;
			snapshot = new ArrayList<>(atlSessions);
		}
		boolean handled = false;
		for (MediaSession session : snapshot) {
			if (session.callback == null)
				continue;
			int keyCode = event.getKeyCode();
			/* AOSP's Callback default expands KEYCODE_MEDIA_PLAY_PAUSE
			 * using the session's playback state, on key-down only; do it
			 * per session so the nested Callback (static in AOSP) needs
			 * no outer state. Note apps publish the state in their own
			 * numbering: media3's vendored legacy PlaybackStateCompat
			 * renumbers (BUFFERING=6, not the platform 4). */
			if (keyCode == KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE
					&& event.getAction() == KeyEvent.ACTION_DOWN) {
				boolean playing = session.lastPlaybackState != null
					&& (session.lastPlaybackState.state == ATL_STATE_PLAYING
					    || session.lastPlaybackState.state == ATL_STATE_BUFFERING
					    || session.lastPlaybackState.state == ATL_STATE_BUFFERING_LEGACY);
				keyCode = playing ? KeyEvent.KEYCODE_MEDIA_PAUSE : KeyEvent.KEYCODE_MEDIA_PLAY;
			}
			Intent intent = new Intent(Intent.ACTION_MEDIA_BUTTON);
			intent.putExtra(Intent.EXTRA_KEY_EVENT,
				new KeyEvent(event.getAction(), keyCode));
			boolean step = session.callback.onMediaButtonEvent(intent);
			System.out.println("atlDispatchMediaKey: action=" + event.getAction()
				+ " origKey=" + event.getKeyCode() + " sentKey=" + keyCode
				+ " lastState=" + (session.lastPlaybackState == null ? -1 : session.lastPlaybackState.state)
				+ " handled=" + step);
			if (step)
				handled = true;
		}
		/* AOSP posts the callback on the handler passed to setCallback;
		 * ATL calls it inline on the input thread (single app process,
		 * same process that owns the session). */
		return handled;
	}

	public void setPlaybackToLocal(AudioAttributes audioAttributes) {}

	public void setExtras(Bundle extras) {}

	public void sendSessionEvent(String event, Bundle extras) {
		// AOSP forwards the event over the session binder to every connected
		// MediaController (hidden sendSessionEvent(Long, Bundle)); once a
		// session is abandoned AOSP just logs and returns. ATL has no
		// cross-app media session bus (the native bridge only mirrors
		// playback state to MPRIS, which has no session-event concept), so
		// there is never a controller to deliver to: log and return, the
		// AOSP abandoned-session behaviour. androidx.media3's MediaSessionStub
		// posts events (e.g. its custom commands) on every play, so apps
		// like Gramophone hit this the moment playback starts.
		System.out.println("MediaSession.sendSessionEvent(" + event + ") dropped: no session bus in ATL");
	}

	public void setSessionActivity(PendingIntent pendingIntent) {}

	public boolean isActive() {
		return true;
	}

	public void setRatingType(int ratingType) {}

	public void setQueueTitle(CharSequence title) {}
	protected native void nativeSetState(int state, long actions, long position, long updateTime, String title, String subTitle, String artUrl);
	protected native void nativeSetCallback(Callback callback);

	public abstract static class Callback {
		public Callback() {}

		public void onCommand(@NonNull String command, @Nullable Bundle args, @Nullable ResultReceiver cb) {}

		/**
		 * AOSP's default implementation translates the standard media
		 * key codes into the matching onXxx() callbacks (AOSP
		 * MediaSession.Callback.onMediaButtonEvent); apps that do not
		 * override it — androidx.media3's MediaSessionStub among them —
		 * rely on exactly this behaviour. KEYCODE_MEDIA_PLAY_PAUSE was
		 * already expanded to PLAY/PAUSE by atlDispatchMediaKey.
		 */
		public boolean onMediaButtonEvent(@NonNull Intent mediaButtonIntent) {
			KeyEvent event = (KeyEvent) mediaButtonIntent.getParcelableExtra(Intent.EXTRA_KEY_EVENT);
			if (event == null || event.getAction() != KeyEvent.ACTION_DOWN)
				return false;
			switch (event.getKeyCode()) {
			case KeyEvent.KEYCODE_MEDIA_PLAY:
				onPlay();
				return true;
			case KeyEvent.KEYCODE_MEDIA_PAUSE:
				onPause();
				return true;
			case KeyEvent.KEYCODE_MEDIA_NEXT:
				onSkipToNext();
				return true;
			case KeyEvent.KEYCODE_MEDIA_PREVIOUS:
				onSkipToPrevious();
				return true;
			case KeyEvent.KEYCODE_MEDIA_STOP:
				onStop();
				return true;
			case KeyEvent.KEYCODE_MEDIA_FAST_FORWARD:
				onFastForward();
				return true;
			case KeyEvent.KEYCODE_MEDIA_REWIND:
				onRewind();
				return true;
			default:
				return false;
			}
		}

		public void onPrepare() {}

		public void onPrepareFromMediaId(String mediaId, Bundle extras) {}

		public void onPrepareFromSearch(String query, Bundle extras) {}

		public void onPrepareFromUri(Uri uri, Bundle extras) {}

		@Export
		public void onPlay() {}

		public void onPlayFromSearch(String query, Bundle extras) {}

		public void onPlayFromMediaId(String mediaId, Bundle extras) {}

		public void onPlayFromUri(Uri uri, Bundle extras) {}

		public void onSkipToQueueItem(long id) {}

		@Export
		public void onPause() {}

		public void onSkipToNext() {}

		public void onSkipToPrevious() {}

		public void onFastForward() {}

		public void onRewind() {}

		public void onStop() {}

		@Export
		public void onSeekTo(long pos) {}

		//public void onSetRating(@NonNull Rating rating) {}

		public void onSetPlaybackSpeed(float speed) {}

		public void onCustomAction(@NonNull String action, @Nullable Bundle extras) {}
	}
}
