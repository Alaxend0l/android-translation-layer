package android.media;

import android.content.Context;
import android.net.Uri;

public class RingtoneManager {
	public static final int TYPE_ALL = 0;
	public static final int TYPE_RINGTONE = 1;
	public static final int TYPE_NOTIFICATION = 2;
	public static final int TYPE_ALARM = 3;

	public static Ringtone getRingtone(Context context, Uri uri) {
		/* AOSP keeps the context and URI in the Ringtone; getTitle() reads
		 * them back through the resolver. */
		return new Ringtone(context, uri);
	}

	public static Uri getDefaultUri(int type) {
		// Same mapping as AOSP (the two classes reference each other there);
		// ATL's Settings URIs are what its own provider resolves.
		switch (type) {
		case TYPE_NOTIFICATION:
			return android.provider.Settings.System.DEFAULT_NOTIFICATION_URI;
		case TYPE_ALARM:
			return android.provider.Settings.System.DEFAULT_ALARM_ALERT_URI;
		case TYPE_RINGTONE:
		default:
			return android.provider.Settings.System.DEFAULT_RINGTONE_URI;
		}
	}
}
