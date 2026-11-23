package android.app;

import android.R;
import android.atl.annotation.Export;
import android.content.Context;
import android.content.DialogInterface;
import android.content.res.TypedArray;
import android.graphics.Rect;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager.LayoutParams;

public class Dialog implements Window.Callback, DialogInterface {
	protected long nativePtr;

	protected native long nativeInit(long decor_view);
	private native void nativeSetTitle(long ptr, String title);
	private native void nativeSetContentView(long ptr, long widget);
	private native void nativeShow(long ptr);
	private native void nativeClose(long ptr);
	private native boolean nativeIsShowing(long ptr);
	private native void nativeSetCanceledOnTouchOutside(long ptr, boolean cancel);

	private Context context;
	private Window window;
	private OnDismissListener onDismissListener;
	private OnShowListener onShowListener;

	public Dialog(Context context, int themeResId) {
		this.context = context;
		window = new Window(context, this);
		nativePtr = nativeInit(window.getDecorView().widget);

		window.set_native_window(nativePtr);
	}

	public Dialog(Context context) {
		this(context, 0);
	}

	public final boolean requestWindowFeature(int featureId) {
		/* AOSP asks the dialog's Window (return mWindow.requestFeature(
		 * featureId)); do the same, like Activity.requestWindowFeature.
		 * Window.requestFeature answers no for every feature in this fork
		 * (GTK-backed window, no PhoneWindow feature flags - queue item
		 * 208), so the answer is unchanged; the point is one path. */
		return getWindow().requestFeature(featureId);
	}

	public Context getContext() {
		return context;
	}

	public void setContentView(View view) {
		getWindow().setContentView(view);
	}

	public void setContentView(int layoutResId) {
		setContentView(LayoutInflater.from(context).inflate(layoutResId, null));
	}

	public void setContentView(View view, ViewGroup.LayoutParams lp) {
		view.setLayoutParams(lp);
		setContentView(view);
	}

	public void setTitle(CharSequence title) {
		nativeSetTitle(nativePtr, String.valueOf(title));
	}

	public void setTitle(int titleId) {
		nativeSetTitle(nativePtr, context.getString(titleId));
	}

	public void setOwnerActivity(Activity activity) {}

	private boolean mCancelable = true; // AOSP default (oreo-release Dialog)

	public void setCancelable(boolean cancelable) {
		/* AOSP stores it and re-arms the window (updateWindowForCancelable:
		 * close-on-touch-outside follows cancelability, and the back key only
		 * cancels a cancelable dialog). This fork's dialog windows are native;
		 * the native side has no cancelable hook (only setCanceledOnTouchOutside),
		 * so the flag is kept here as the state the getters/subclasses see. */
		mCancelable = cancelable;
	}

	private OnCancelListener onCancelListener = null;

	public void setOnCancelListener(OnCancelListener onCancelListener) {
		/* AOSP stores it and fires it from the dismiss handler when the
		 * dialog was cancelled (cancel(), back press, touch-outside), not on
		 * plain dismiss(). AOSP also rejects this if setCancelMessage() was
		 * used (IllegalStateException); setCancelMessage is an empty stub
		 * here, so there is nothing to conflict with. */
		this.onCancelListener = onCancelListener;
	}

	public void setOnDismissListener(OnDismissListener onDismissListener) {
		this.onDismissListener = onDismissListener;
	}

	public View findViewById(int id) {
		return window.findViewById(id);
	}

	public void show() {
		Runnable action = new Runnable() {
			@Override
			public void run() {
				onCreate(null);
				// Read the size of the main window. Floating dialogs should be smaller as specified in the windowMinWidth* attributes.
				// Non-floating are typically constructed with MATCH_PARENT layout params and thus get the exact size of the main window.
				// Most non-floating dialogs are technically dialogs, but are expected to behave more like full size activities.
				Rect displayFrame = new Rect();
				getWindow().getDecorView().getWindowVisibleDisplayFrame(displayFrame);

				TypedArray a = context.obtainStyledAttributes(R.styleable.Window);
				if (a.hasValue(R.styleable.Window_windowCloseOnTouchOutside))
					setCanceledOnTouchOutside(a.getBoolean(R.styleable.Window_windowCloseOnTouchOutside, true));
				float windowWidthFraction = 1;
				if (a.getBoolean(R.styleable.Window_windowIsFloating, false)) {
					if (displayFrame.width() > displayFrame.height())
						windowWidthFraction = a.getFraction(R.styleable.Window_windowMinWidthMajor, 1, 1, 1);
					else
						windowWidthFraction = a.getFraction(R.styleable.Window_windowMinWidthMinor, 1, 1, 1);
				}
				a.recycle();

				LayoutParams lp = getWindow().getAttributes();
				int widthMeasureSpec = View.MeasureSpec.makeMeasureSpec(lp.width >= 0 ? lp.width : (int)(displayFrame.width() * windowWidthFraction),
				                                                        lp.width == LayoutParams.WRAP_CONTENT ? View.MeasureSpec.AT_MOST : View.MeasureSpec.EXACTLY);

				int heightMeasureSpec = View.MeasureSpec.makeMeasureSpec(lp.height >= 0 ? lp.height : (int)(displayFrame.height()),
				                                                         lp.height == LayoutParams.WRAP_CONTENT ? View.MeasureSpec.AT_MOST : View.MeasureSpec.EXACTLY);

				getWindow().getDecorView().internalSetDefaultMeasureSpec(widthMeasureSpec, heightMeasureSpec);

				nativeShow(nativePtr);
				if (onShowListener != null)
					onShowListener.onShow(Dialog.this);
			}
		};
		if (Looper.myLooper() == Looper.getMainLooper()) {
			action.run();
		} else {
			new Handler(Looper.getMainLooper()).post(action);
		}
	}

	public boolean isShowing() {
		return nativeIsShowing(nativePtr);
	}

	@Export
	public void dismiss() {
		System.out.println("dismissing the Dialog " + Dialog.this);
		// HACK: dismissing the Dialog takes some time in AOSP, as the request goes back and forth between the application
		// and the system server. We replicate this behavior by adding 10 ms delay.
		// This Hack is required for NewPipe RouterActivity which has a race condition. It subscribes an rxJava observable
		// and immediately calls Dialog.dismiss(). The OnDismissListener would unsubscribes the observable again.
		new Handler(Looper.getMainLooper()).postDelayed(new Runnable() {
			@Override
			public void run() {
				nativeClose(nativePtr);
				if (onDismissListener != null)
					onDismissListener.onDismiss(Dialog.this);
			}
		}, 10);
	}

	public Window getWindow() {
		return window;
	}

	public void setCanceledOnTouchOutside(boolean cancel) {
		nativeSetCanceledOnTouchOutside(nativePtr, cancel);
	}

	public class Builder {
		public Builder(Context context) {}
	}

	@Override
	public void onContentChanged() {
	}
	/* Window.Callback menu hooks, AOSP's Dialog defaults (oreo-release
	 * Dialog.java). They threw UnsupportedOperationException: a live crash
	 * class if anything ever routed a dialog window's menu events here
	 * (nothing does yet - the fork's menu path only drives the Activity's
	 * callbacks - so this is compile + gate verified, not witnessed).
	 * AOSP's onPanelClosed/onMenuOpened only act for FEATURE_ACTION_BAR,
	 * which this fork does not use, so they reduce to do-nothing/true. */
	@Override
	public boolean onCreatePanelMenu(int featureId, Menu menu) {
		if (featureId == Window.FEATURE_OPTIONS_PANEL) {
			return onCreateOptionsMenu(menu);
		}
		return false;
	}
	@Override
	public View onCreatePanelView(int featureId) {
		return null;
	}
	@Override
	public boolean onPreparePanel(int featureId, View view, Menu menu) {
		if (featureId == Window.FEATURE_OPTIONS_PANEL && menu != null) {
			return onPrepareOptionsMenu(menu) && menu.hasVisibleItems();
		}
		return true;
	}
	@Override
	public boolean onMenuItemSelected(int featureId, MenuItem item) {
		return false;
	}
	@Override
	public void onPanelClosed(int featureId, Menu menu) {
	}

	@Override
	public boolean onMenuOpened(int featureId, Menu menu) {
		return true;
	}

	/* AOSP's overridable dialog-menu hooks (oreo-release Dialog: both
	 * return true by default); apps subclass Dialog and override these. */
	public boolean onCreateOptionsMenu(Menu menu) {
		return true;
	}

	public boolean onPrepareOptionsMenu(Menu menu) {
		return true;
	}

	protected void onCreate(Bundle savedInstanceState) {
		/* Empty, as in AOSP (oreo-release Dialog.onCreate) - apps
		 * override this. */
	}

	public void hide() {
		nativeClose(nativePtr);
	}

	@Override
	public void cancel() {
		/* AOSP's cancel() dismisses with cancel = true, and the dialog's
		 * handler then dispatches OnCancelListener.onCancel on the main
		 * looper; a plain dismiss() never fires it. Post the callback
		 * before dismiss()'s delayed close runs so cancel is seen first. */
		if (onCancelListener != null) {
			new Handler(Looper.getMainLooper()).post(new Runnable() {
				@Override
				public void run() {
					onCancelListener.onCancel(Dialog.this);
				}
			});
		}
		dismiss();
	}

	public void setOnShowListener(OnShowListener onShowListener) {
		this.onShowListener = onShowListener;
	}

	public void setCancelMessage(Message msg) {}

	public void setDismissMessage(Message msg) {}

	public boolean onTouchEvent(MotionEvent event) {
		/* AOSP's default (oreo-release Dialog.onTouchEvent) cancels the
		 * dialog when mWindow.shouldCloseOnTouch() says the touch was outside
		 * the window bounds, and returns false otherwise. This fork has no
		 * Window.shouldCloseOnTouch, and its native window never dispatches
		 * Window.Callback.onTouchEvent (touches go to the view tree), so this
		 * is unreachable; false - "not consumed" - is what AOSP answers when
		 * the dialog is not cancelable or the touch is inside. */
		return false;
	}

	private OnKeyListener mOnKeyListener = null;

	public void setOnKeyListener(OnKeyListener onKeyListener) {
		/* AOSP stores it (oreo-release Dialog:1346) and dispatchKeyEvent runs
		 * it first (Dialog:776: mOnKeyListener.onKey(this, keyCode, event)).
		 * This fork's native dialog window has no key routing yet, so nothing
		 * calls it - storing the listener completes the API surface; the
		 * DialogInterface.OnKeyListener.onKey declaration was also missing
		 * (empty marker) and is added in DialogInterface.java. */
		mOnKeyListener = onKeyListener;
	}

	public View getCurrentFocus() {
		return null;
	}
}
