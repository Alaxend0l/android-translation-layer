package android.widget;

import android.app.Notification;
import android.app.NotificationManager;
import android.content.Context;
import android.os.Handler;
import android.view.View;

public class Toast {
	public static final int LENGTH_SHORT = 0;
	public static final int LENGTH_LONG = 1;

	private final Context mContext;
	private String text;
	private int duration = LENGTH_SHORT;
	private Integer shownId; /* notification id while visible, AOSP's "isShown" */

	public Toast(Context context) {
		mContext = context;
	}

	public static Toast makeText(Context context, int resId, int duration) {
		Toast toast = makeText(context, context.getString(resId), duration);
		return toast;
	}

	public static Toast makeText(Context context, CharSequence text, int duration) {
		Toast toast = new Toast(context);
		toast.text = String.valueOf(text);
		toast.duration = duration;
		return toast;
	}

	public void show() {
		System.out.println("showing toast: " + text);
		Notification notification = new Notification.Builder(mContext).setContentText(text).build();
		NotificationManager manager = (NotificationManager)mContext.getSystemService("notification");
		final int id = hashCode();
		shownId = Integer.valueOf(id);
		manager.notify(id, notification);
		/* AOSP timings: LENGTH_LONG 3500 ms, LENGTH_SHORT 2000 ms. */
		new Handler().postDelayed(new Runnable() {
			public void run() {
				manager.cancel(id);
				if (shownId != null && shownId.intValue() == id)
					shownId = null;
			}
		}, duration == LENGTH_LONG ? 3500 : 2000);
	}

	public void setText(int resId) {
		setText(mContext.getString(resId));
	}

	public void setText(CharSequence s) {
		text = String.valueOf(s);
	}

	public void setDuration(int duration) {
		this.duration = duration;
	}

	public int getDuration() {
		return duration;
	}

	public View getView() {
		/* ATL renders toasts as notifications, there is no view. */
		return null;
	}

	public void setView(View view) {}

	public void setGravity(int gravity, int xOffset, int yOffset) {}

	public void cancel() {
		if (shownId != null) {
			NotificationManager manager = (NotificationManager)mContext.getSystemService("notification");
			manager.cancel(shownId.intValue());
			shownId = null;
		}
	}
}
