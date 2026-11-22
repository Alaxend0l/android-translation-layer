package android.app;

import android.app.Notification.MediaStyle;
import android.atl.ATLLoadedApp;
import android.content.Context;
import android.content.Intent;
import android.os.Handler;
import android.os.Looper;
import android.service.notification.StatusBarNotification;
import java.util.Collections;
import java.util.List;

public class NotificationManager {

	private static int mpris_notification_id = -1;

	/* Channels/groups are process-wide like AOSP (per app process): the
	 * app gets NotificationManager from getSystemService and expects the
	 * channels it created there. */
	private static final java.util.Map<String, NotificationChannel> channels
			= new java.util.HashMap<String, NotificationChannel>();
	private static final java.util.Map<String, NotificationChannelGroup> channelGroups
			= new java.util.HashMap<String, NotificationChannelGroup>();

	public void cancelAll() {}

	public void notify(String tag, int id, Notification notification) {
		if (notification.style instanceof MediaStyle) { // MPRIS content is handled by MediaSession implementation
			if (mpris_notification_id == -1) {
				Application application = ATLLoadedApp.getPrimaryApplication().getApplication();
				nativeShowMPRIS(application.getPackageName(), application.get_app_label());
				mpris_notification_id = id;
			}
			return;
		}

		System.out.println("notify(" + tag + ", " + id + ", " + notification + ") called");
		long builder = nativeInitBuilder();
		for (Notification.Action action : notification.actions) {
			int intentType = -1;
			Intent intent = null;
			if (action.intent != null) {
				intentType = action.intent.type;
				intent = action.intent.intent;
			}
			nativeAddAction(builder, action.title, intentType, intent);
		}
		int intentType = -1;
		Intent intent = null;
		if (notification.intent != null) {
			intentType = notification.intent.type;
			intent = notification.intent.intent;
		}
		String iconPath = notification.iconPath == null || notification.iconPath.endsWith(".xml") ? null : notification.iconPath;
		nativeShowNotification(builder, id, notification.title, notification.text, iconPath, notification.ongoing, intentType, intent);
	}

	public void notify(int id, Notification notification) {
		notify(null, id, notification);
	}

	public void cancel(String tag, final int id) {
		// remove_notification doesn't work reliably when sent directly after add_notification in GNOME session.
		// So we give some extra delay here.
		new Handler(Looper.getMainLooper()).postDelayed(new Runnable() {
			@Override
			public void run() {
				if (mpris_notification_id == id) {
					mpris_notification_id = -1;
					nativeCancelMPRIS();
				} else {
					nativeCancel(id);
				}
			}
		}, 100);
	}

	public void cancel(int id) {
		cancel(null, id);
	}

	public void createNotificationChannel(NotificationChannel channel) {
		/* AOSP keeps the channel (per app) so getNotificationChannel(s)
		 * answer; ATL has no settings UI behind it, but apps create a
		 * channel and read it back (migration screens, importance
		 * checks), so the map matches that contract. */
		if (channel != null && channel.getId() != null)
			channels.put(channel.getId(), channel);
	}

	protected native long nativeInitBuilder();
	protected native void nativeAddAction(long builder, String title, int intentType, Intent intent);
	protected native void nativeShowNotification(long builder, int id, String title, String text, String iconPath, boolean ongoing, int intentType, Intent intent);
	protected native void nativeShowMPRIS(String packageName, String identiy);
	protected native void nativeCancel(int id);
	protected native void nativeCancelMPRIS();

	public void createNotificationChannelGroup(NotificationChannelGroup v) {
		if (v != null && v.getId() != null)
			channelGroups.put(v.getId(), v);
	}

	public List<NotificationChannel> getNotificationChannels() {
		return new java.util.ArrayList<NotificationChannel>(channels.values());
	}

	public List<NotificationChannelGroup> getNotificationChannelGroups() {
		return new java.util.ArrayList<NotificationChannelGroup>(channelGroups.values());
	}

	public boolean areNotificationsEnabled() {
		return true;
	}

	public void deleteNotificationChannel(String channelId) {
		channels.remove(channelId);
	}

	public void createNotificationChannelGroups(List<NotificationChannelGroup> groups) {
		if (groups != null)
			for (NotificationChannelGroup g : groups)
				createNotificationChannelGroup(g);
	}

	public void createNotificationChannels(List<NotificationChannel> channels) {
		if (channels != null)
			for (NotificationChannel c : channels)
				createNotificationChannel(c);
	}

	public StatusBarNotification[] getActiveNotifications() {
		return new StatusBarNotification[0];
	}

	public NotificationChannel getNotificationChannel(String channel) {
		return channels.get(channel); /* null when never created, like AOSP */
	}
}
