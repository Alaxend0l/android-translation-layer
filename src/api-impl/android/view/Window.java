package android.view;

import android.R;
import android.app.ActionBar;
import android.content.Context;
import android.content.res.TypedArray;
import android.graphics.drawable.Drawable;
import android.transition.Transition;
import android.widget.FrameLayout;
import android.widget.Toolbar;

public class Window {
	public static final int FEATURE_OPTIONS_PANEL = 0;
	public static final int FEATURE_NO_TITLE = 1;

	public ViewTreeObserver view_tree_observer = null;
	private WindowManager.LayoutParams params = new WindowManager.LayoutParams(WindowManager.LayoutParams.WRAP_CONTENT, WindowManager.LayoutParams.WRAP_CONTENT, 0, 0, 0);

	public static interface Callback {
		public void onContentChanged();

		public abstract boolean onCreatePanelMenu(int featureId, Menu menu);

		public View onCreatePanelView(int featureId);

		public boolean onPreparePanel(int featureId, View view, Menu menu);

		public boolean onMenuItemSelected(int featureId, MenuItem item);

		public void onPanelClosed(int featureId, Menu menu);

		public boolean onMenuOpened(int featureId, Menu menu);
	}

	public static interface OnFrameMetricsAvailableListener {}

	public long native_window;
	private ViewGroup decorView;
	private Toolbar actionBarView;
	private ActionBar actionBar;

	private Window.Callback callback;
	private Context context;

	public Window(Context context, Window.Callback callback) {
		this.callback = callback;
		this.context = context;
		decorView = new FrameLayout(context);
		// DecorView grabs all touch events and propagates them in Java
		decorView.native_registerTouchEventPropagation(decorView.widget);
		decorView.setId(android.R.id.content);
	}

	public void set_native_window(long native_window) {
		this.native_window = native_window;
		set_jobject(native_window, this);
	}

	public void addFlags(int flags) {
		setFlags(flags, flags);
	}

	public void setFlags(int flags, int mask) {
		// AOSP Window.setFlags updates mWindowAttributes.flags the same way.
		params.flags = (params.flags & ~mask) | (flags & mask);
		decorView.setKeepScreenOn((params.flags & WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON) != 0);
	}

	public void clearFlags(int flags) {
		setFlags(0, flags);
	}

	public final Callback getCallback() {
		return this.callback;
	}
	public void setCallback(Window.Callback callback) {
		this.callback = callback;
	}

	public void setContentView(View view) {
		ensureWindowBackground();
		decorView.removeAllViews();
		decorView.addView(view);
	}

	public void setContentView(int layoutResID) {
		ensureWindowBackground();
		decorView.removeAllViews();
		// AOSP's PhoneWindow inflates the layout into its content ViewGroup with
		// attachToRoot=true. Inflating with a null parent instead, as this used to
		// do, breaks layouts whose root is <merge>: such a layout has no View of
		// its own, its children are only representable once a parent is known.
		// ATL's decor has no action-bar/title slots, so the content ViewGroup is
		// the decor FrameLayout itself (the one with android.R.id.content).
		LayoutInflater.from(context).inflate(layoutResID, decorView, true);
	}

	private void ensureWindowBackground() {
		if (decorView.getBackground() == null) {
			TypedArray ta = context.obtainStyledAttributes(new int[] {R.attr.windowBackground});
			if (ta.hasValue(0))
				setBackgroundDrawable(ta.getDrawable(0));
			ta.recycle();
		}
	}

	public boolean superDispatchKeyEvent(KeyEvent event) {
		/* AOSP PhoneWindow.superDispatchKeyEvent dispatches into the decor
		 * view; the fork's Window is concrete and holds the decor view
		 * directly. ATL's real key input comes from GTK straight to the
		 * focused widget's View.dispatchKeyEvent (api-impl-jni/widgets/
		 * WrapperWidget.c), so this chain matters for apps that route key
		 * events through an Activity themselves. Known gap: the fork's
		 * ViewGroup.dispatchKeyEvent does not fall through to focused
		 * children (AOSP ViewGroup dispatches to mFocused first), so only
		 * the decor view itself sees the event here. */
		return decorView != null && decorView.dispatchKeyEvent(event);
	}

	public View getDecorView() {
		return decorView;
	}

	public void takeInputQueue(InputQueue.Callback callback) {
		take_input_queue(native_window, callback, new InputQueue());
	}

	public boolean requestFeature(int featureId) {
		/* No window feature can be granted: ATL's window is a GTK window
		 * whose decorations, focus ring and title come from GTK/the window
		 * manager, and the fork has no PhoneWindow-style feature flags that
		 * change the decor (FEATURE_NO_TITLE, FEATURE_ACTION_BAR ...). The
		 * action bar that Window.getActionBar() hands out is built
		 * regardless of a request. So "not supported" is the honest answer
		 * and callers fall back the way they do on a window that lacks the
		 * feature (queue item 208, reached from Activity.requestWindowFeature,
		 * which now delegates here instead of answering in the Activity). */
		return false;
	}

	public View findViewById(int id) {
		if (id == com.android.internal.R.id.action_bar)
			return getActionBarView();
		return decorView.findViewById(id);
	}

	/*
	 * ATL's decor is a single content FrameLayout: it has no action-bar slot, so
	 * the bar is represented by the framework Toolbar kept here (Window used to
	 * hand out a new Toolbar per findViewById call instead).
	 */
	private Toolbar getActionBarView() {
		if (actionBarView == null) {
			actionBarView = new Toolbar(context);
		}
		return actionBarView;
	}

	public ActionBar getActionBar() {
		if (actionBar == null) {
			// AOSP only creates an action bar for themes with windowActionBar set.
			// ATL follows that, but treats "not set by the theme" as "yes", which is
			// what every platform theme does; only an explicit windowActionBar=false
			// turns the bar off.
			TypedArray ta = context.obtainStyledAttributes(new int[] {R.attr.windowActionBar});
			boolean enabled = !ta.hasValue(0) || ta.getBoolean(0, true);
			ta.recycle();
			if (!enabled)
				return null;
			actionBar = new ActionBar(context, getActionBarView());
		}
		return actionBar;
	}

	public View peekDecorView() {
		// AOSP returns the decor view once it exists; ATL builds it in the
		// Window constructor, so it always does.
		return decorView;
	}

	public WindowManager.LayoutParams getAttributes() {
		return params;
	}

	public void setBackgroundDrawable(Drawable drawable) {
		// HACK: disable transparent background for WhatsApp dialogs
		// For some unknown reason, the language picker in WhatsApp doesn't render the BottomSheet background currently.
		if (!"com.whatsapp".equals(context.getPackageName()))
			remove_gtk_background(native_window);
		decorView.setBackgroundDrawable(drawable);
	}

	public void setAttributes(WindowManager.LayoutParams params) {
		if (params.screenBrightness != -1)
			set_screen_brightness(params.screenBrightness);
		this.params = params;
		setLayout(params.width, params.height);
	}

	public void takeSurface(SurfaceHolder.Callback2 callback) {}

	public void setStatusBarColor(int color) {}

	public void setNavigationBarColor(int color) {}

	public void setFormat(int format) {}

	public void setLayout(int width, int height) {
		params.width = width;
		params.height = height;
		set_layout(native_window, width, height);
	}

	public WindowManager getWindowManager() {
		return new WindowManagerImpl();
	}

	public void setSoftInputMode(int dummy) {}

	public int getNavigationBarColor() {
		return 0xFF888888; // gray
	}

	public void setBackgroundDrawableResource(int resId) {
		setBackgroundDrawable(context.getDrawable(resId));
	}

	public int getStatusBarColor() { return 0xFFFF0000; }

	public Context getContext() {
		return context;
	}

	public boolean hasFeature(int featureId) {
		return false;
	}

	/* Volume-control stream (queue item 266): AOSP's PhoneWindow keeps
	 * this and its key handling routes the hardware volume keys to it.
	 * Stored and read back now, so the pair round-trips; **gap: ATL has
	 * no Android-side volume-key routing** (audio comes from the host, so
	 * nothing consumes the stream type). AOSP's default is
	 * AudioManager.USE_DEFAULT_STREAM_TYPE (-1), which the fork does not
	 * declare as a constant. */
	private int atl_volume_control_stream = -1 /* USE_DEFAULT_STREAM_TYPE */;

	public void setVolumeControlStream(int streamType) {
		atl_volume_control_stream = streamType;
	}

	public int getVolumeControlStream() {
		return atl_volume_control_stream;
	}

	public void setTitle(CharSequence title) {
		set_title(native_window, title != null ? title.toString() : context.getPackageName());
	}

	public Transition getSharedElementEnterTransition() {
		return new Transition();
	}

	public void setSharedElementExitTransition(Transition transition) {}

	public void setSharedElementReenterTransition(Transition transition) {}

	public void setSharedElementReturnTransition(Transition transition) {}

	public Transition getSharedElementExitTransition() {
		return new Transition();
	}

	public Transition getSharedElementReenterTransition() {
		return new Transition();
	}

	public void setReturnTransition(Transition transition) {}

	public void setEnterTransition(Transition transition) {}

	public void setGravity(int gravity) {}

	public void setDecorFitsSystemWindows(boolean fits) {}

	public WindowInsetsController getInsetsController() {
		return new InsetsController();
	}

	public void setStatusBarContrastEnforced(boolean enforced) {}

	public void setNavigationBarContrastEnforced(boolean enforced) {}

	public void setSharedElementEnterTransition(Transition transition) {}

	public Transition getEnterTransition() {
		return null;
	}

	public void setWindowAnimations(int resId) {}

	public void setDimAmount(float dimAmount) {}

	/* Close-on-touch-outside (queue item 289): AOSP keeps it on the
	 * window (mCloseOnTouchOutside, default false) for its decor's
	 * outside-touch handler to finish the activity;
	 * Activity.setFinishOnTouchOutside is a pass-through to this. ATL
	 * receives no outside-window touches at all (**gap:
	 * outside-touch dispatch**), so the flag is stored as API state with
	 * no consumer. */
	private boolean atl_close_on_touch_outside = false;

	public void setCloseOnTouchOutside(boolean close) {
		atl_close_on_touch_outside = close;
	}

	private native void set_title(long native_window, String title);
	public native void take_input_queue(long native_window, InputQueue.Callback callback, InputQueue queue);
	public native void set_layout(long native_window, int width, int height);
	private static native void set_jobject(long ptr, Window obj);
	private native void remove_gtk_background(long native_window);
	private native void set_screen_brightness(float brightness);
}
