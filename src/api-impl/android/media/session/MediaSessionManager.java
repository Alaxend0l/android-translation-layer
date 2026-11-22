package android.media.session;

import android.content.ComponentName;
import android.content.Context;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Manages the media sessions currently active on the device.
 *
 * <p>ATL has no media session broker: nothing publishes sessions and there is no
 * media button routing. The set of active sessions is therefore always empty,
 * which is exactly what Android reports when no app holds an active session, so
 * getActiveSessions() returns an empty list and a registered listener never
 * receives a callback. The listener interface is part of the API surface apps
 * implement, so it must exist for them to link.
 */
public class MediaSessionManager {
	/**
	 * OnActiveSessionsChangedListener is used to listen to active
	 * media sessions changes for a given app.
	 */
	public interface OnActiveSessionsChangedListener {
		/**
		 * Called when the active sessions for the listening app change.
		 * @param controllers The currently active session controllers.
		 */
		void onActiveSessionsChanged(List<MediaController> controllers);
	}

	private final Context context;
	private final List<OnActiveSessionsChangedListener> listeners = new ArrayList<>();

	public MediaSessionManager(Context context) {
		this.context = context;
	}

	/**
	 * Returns the media controllers for the sessions that are currently active
	 * on the device, filtered by the notification listener that is watching
	 * them. ATL publishes no sessions, so the list is empty.
	 */
	@SuppressWarnings("deprecation")
	public List<MediaController> getActiveSessions(ComponentName notificationListener) {
		return Collections.emptyList();
	}

	/**
	 * Returns the media controllers for the sessions that are currently active
	 * on the device. ATL publishes no sessions, so the list is empty.
	 */
	public List<MediaController> getActiveSessions() {
		return Collections.emptyList();
	}

	/**
	 * Registers a listener to be notified when the active sessions for the apps
	 * started by the given component change. ATL has no sessions to report, so
	 * the listener is kept for symmetry with remove and is never called back.
	 */
	@SuppressWarnings("deprecation")
	public void addOnActiveSessionsChangedListener(OnActiveSessionsChangedListener l,
	                                               ComponentName cn) {
		if (l != null && !listeners.contains(l)) {
			listeners.add(l);
		}
	}

	/** Removes a previously registered listener. */
	public void removeOnActiveSessionsChangedListener(OnActiveSessionsChangedListener l) {
		listeners.remove(l);
	}

	/**
	 * Returns the media controller set on this device (e.g. by a TV input service).
	 * Android returns null when no media controller is set, which is always the
	 * case under ATL.
	 */
	public MediaController getMediaController() {
		return null;
	}
}
