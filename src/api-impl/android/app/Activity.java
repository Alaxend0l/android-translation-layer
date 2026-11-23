package android.app;

import android.app.ActionBar;
import android.atl.ATLLoadedApp;
import android.atl.annotation.Export;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.ActivityInfo;
import android.content.pm.PackageManager;
import android.content.pm.PackageParser;
import android.content.res.Configuration;
import android.content.res.Resources;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.Parcelable;
import android.util.AttributeSet;
import android.util.Slog;
import android.view.ContextMenu;
import android.view.ContextThemeWrapper;
import android.view.Display;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.view.WindowManagerImpl;
import java.io.IOException;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/* KeyEvent.Callback: the fork's Window.Callback does not extend it (AOSP's
 * does), so Activity declares it directly to get the AOSP
 * onKeyDown/onKeyUp/onKeyLongPress/onKeyMultiple defaults used by
 * dispatchKeyEvent. */
public class Activity extends ContextThemeWrapper implements Window.Callback, LayoutInflater.Factory2, KeyEvent.Callback, View.OnCreateContextMenuListener {
	private final static String TAG = "Activity";

	public static final int RESULT_CANCELED = 0;
	public static final int RESULT_OK = -1;

	Window window;
	int requested_orientation = -1 /*ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED*/; // dummy
	public Intent intent;
	private Activity resultActivity;
	private int resultRequestCode;
	private boolean paused = false;
	private CharSequence title = null;
	List<Fragment> fragments = new ArrayList<>();
	boolean destroyed = false;
	private boolean finishing = false;

	public static Activity internalCreateActivity(String className, long native_window, Intent intent) throws ReflectiveOperationException {
		Activity activity = ATLLoadedApp.getPrimaryApplication().createActivity(className, intent);
		activity.window.set_native_window(native_window);
		return activity;
	}

	/**
	 * Helper function to be called from native code to construct main activity
	 *
	 * @param className  class name of activity or null
	 * @return  instance of main activity class
	 * @throws Exception
	 */
	@Export
	private static Activity createMainActivity(String className, long native_window, String uriString) throws ReflectiveOperationException {
		Uri uri = uriString != null ? Uri.parse(uriString) : null;
		if (className == null) {
			for (PackageParser.Activity activity : ATLLoadedApp.getPrimaryApplication().pkg.activities) {
				if (!activity.info.enabled)
					continue;
				boolean done = false;
				for (PackageParser.IntentInfo intent : activity.intents) {
					Slog.i(TAG, intent.toString());
					if ((uri == null && intent.hasCategory("android.intent.category.LAUNCHER") && intent.hasAction("android.intent.action.MAIN")) ||        // NOLINT
					    (uri != null && intent.hasDataScheme(uri.getScheme())                  && intent.hasCategory("android.intent.category.DEFAULT"))) { // NOLINT
						className = activity.info.targetActivity != null ? activity.info.targetActivity : activity.className;
						done = true;
						break;
					}
				}
				if (done)
					break;
			}
		} else {
			className = className.replace('/', '.');
		}
		if (className == null) {
			if (uri != null)
				System.err.println("Failed to find Activity to launch URI: " + uri);
			else
				System.err.println("Failed to find main Activity");
			System.exit(1);
		}
		// A launcher start on Android carries action=MAIN and category=LAUNCHER;
		// apps switch on getIntent().getAction() (org.billthefarmer.editor does at
		// Editor.onCreate), so an empty Intent here made them throw
		// NullPointerException on the switch selector.
		return internalCreateActivity(className, native_window,
		                              uri != null ? new Intent(Intent.ACTION_VIEW, uri)
		                                          : new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER));
	}

	public Activity() {
		super(null);
	}

	public View root_view;

	public final Application getApplication() {
		return (Application)getApplicationContext();
	}

	/* Window manager (queue item 165): AOSP keeps one WindowManager per
	 * activity (created in attach()); the fork built a fresh
	 * WindowManagerImpl on every call, so identity comparisons and any
	 * state the caller expects on it (apps keep it as a field and
	 * compare windows through it) broke. The fork's WindowManagerImpl is
	 * stateless (natives only), so one instance per activity is safe. */
	private WindowManager atl_window_manager = null;

	public WindowManager getWindowManager() {
		if (atl_window_manager == null)
			atl_window_manager = new WindowManagerImpl();
		return atl_window_manager;
	}

	public String getCallingPackage() {
		/* AOSP asks the activity task manager for the package of the
		 * activity that started this one *for a result*; null when it was
		 * not started with startActivityForResult (the documented case,
		 * queue item 240). ATL launches activities in-process and keeps
		 * that caller as `resultActivity` (set in startActivityForResult
		 * above, used by getCallingActivity and setResult), so the
		 * caller's package is that activity's package. */
		return resultActivity == null ? null : resultActivity.getPackageName();
	}

	public ComponentName getComponentName() {
		return intent.getComponent();
	}

	public Intent getIntent() {
		return intent;
	}

	public int getRequestedOrientation() {
		return requested_orientation;
	}

	/* Lock screen and screen power belong to the host desktop: ATL has no keyguard
	 * (KeyguardManager.isKeyguardLocked() is always false), so an activity is always
	 * shown over "the lock screen" and never needs to turn the screen on itself. The
	 * requests are recorded but intentionally have no further effect. */
	private boolean showWhenLocked;
	private boolean turnScreenOn;

	public void setShowWhenLocked(boolean showWhenLocked) {
		this.showWhenLocked = showWhenLocked;
	}

	public void setTurnScreenOn(boolean turnScreenOn) {
		this.turnScreenOn = turnScreenOn;
	}

	public void setRequestedOrientation(int orientation) {
		requested_orientation = orientation;
	}

	public boolean isFinishing() {
		return finishing;
	}

	public final boolean requestWindowFeature(int featureId) {
		/* AOSP asks its Window (PhoneWindow.requestFeature), and that is
		 * where the answer belongs, so the call is routed there rather
		 * than answered in Activity. The answer is still no, and for the
		 * same reason as before: ATL's window chrome is GTK's — the fork
		 * has no feature negotiation to grant (see Window.requestFeature).
		 * Route 208: the point is that the Activity no longer decides. */
		return getWindow().requestFeature(featureId);
	}

	public final void setVolumeControlStream(int streamType) {
		/* AOSP routes both halves through the window (PhoneWindow keeps
		 * the value); the fork had the setter empty and no getter at all,
		 * so an app telling the volume keys which stream it owns was
		 * ignored and could not read its own setting back (queue item
		 * 266). ATL has no hardware volume keys routed through Android's
		 * AudioManager — see the gap noted in Window below. */
		getWindow().setVolumeControlStream(streamType);
	}

	public final int getVolumeControlStream() {
		return getWindow().getVolumeControlStream();
	}

	@Export
	protected void onCreate(Bundle savedInstanceState) {
		Slog.i(TAG, "- onCreate - yay!");

		for (Fragment fragment : fragments) {
			fragment.onCreate(savedInstanceState);
			fragments_created.add(fragment);
		}

		return;
	}

	/* Fragments whose onCreate() the loop above has already run. The loop
	 * runs mid-way through the app's onCreate chain, so fragments added
	 * during that chain (androidx lifecycle's ReportFragment, added by
	 * FragmentActivity/LifecycleDispatcher.onCreate) are not in it yet and
	 * still need onCreate() before onActivityCreated() - AOSP brings a
	 * fragment added during onCreate all the way to ACTIVITY_CREATED in
	 * the dispatchActivityCreated() pass. */
	private final List<Fragment> fragments_created = new ArrayList<>();

	/* Called from api-impl-jni (activity_start) right after the app's
	 * onCreate chain returns, mirroring Activity.performCreate()'s
	 * mFragments.dispatchActivityCreated() (which in AOSP runs after the
	 * onCreate callback and before onActivityPostCreated). Delivers
	 * onActivityCreated() to framework fragments - the only way androidx
	 * lifecycle's pre-API-29 ReportFragment dispatches ON_CREATE to the
	 * activity's LifecycleRegistry; without it the registry jumps
	 * INITIALIZED->STARTED at onStart and replays ON_CREATE inside the
	 * ON_START pass, breaking late ActivityResultRegistry registrations
	 * (Phonograph Plus intro, "LifecycleOwners must call register before
	 * they are STARTED"). */
	@Export
	private void dispatchFragmentsActivityCreated(Bundle savedInstanceState) {
		/* iterate a snapshot: a fragment's onActivityCreated() may add more */
		for (Fragment fragment : new ArrayList<>(fragments)) {
			if (!fragments_created.contains(fragment)) {
				fragments_created.add(fragment);
				fragment.onCreate(savedInstanceState);
			}
			fragment.onActivityCreated(savedInstanceState);
		}
	}

	@Export
	protected void onPostCreate(Bundle savedInstanceState) {
		Slog.i(TAG, "- onPostCreate - yay!");
		return;
	}

	@Export
	protected void onStart() {
		Slog.i(TAG, "- onStart - yay!");
		window.setTitle(title);

		for (Fragment fragment : fragments) {
			fragment.onStart();
		}

		return;
	}

	protected void onRestart() {
		Slog.i(TAG, "- onRestart - yay!");

		return;
	}

	@Export
	protected void onResume() {
		Slog.i(TAG, "- onResume - yay!");

		for (Fragment fragment : fragments) {
			fragment.onResume();
		}

		paused = false;
		return;
	}

	@Export
	protected void onPostResume() {
		Slog.i(TAG, "- onPostResume - yay!");
		return;
	}

	@Export
	protected void onPause() {
		Slog.i(TAG, "- onPause - yay!");

		for (Fragment fragment : fragments) {
			fragment.onPause();
		}

		paused = true;
		return;
	}

	@Export
	protected void onStop() {
		Slog.i(TAG, "- onStop - yay!");

		for (Fragment fragment : fragments) {
			fragment.onStop();
		}

		return;
	}

	@Export
	protected void onDestroy() {
		Slog.i(TAG, "- onDestroy - yay!");

		for (Fragment fragment : fragments) {
			fragment.onDestroy();
		}

		destroyed = true;
		return;
	}

	@Export
	public void onWindowFocusChanged(boolean hasFocus) {
		Slog.i(TAG, "- onWindowFocusChanged - yay! (hasFocus: " + hasFocus + ")");

		return;
	}

	protected void onSaveInstanceState(Bundle outState) {
	}

	public void onConfigurationChanged(Configuration newConfig) {
	}

	public void onLowMemory() {
	}

	/* --- */

	public void setContentView(int layoutResID) throws Exception {
		Slog.i(TAG, "- setContentView - yay!");

		// Window inflates into its content ViewGroup with attachToRoot=true, like
		// AOSP's PhoneWindow: layouts with a <merge> root have no View of their
		// own and only work that way.
		window.setContentView(layoutResID);
		root_view = window.getDecorView();

		onContentChanged();
	}

	public void setContentView(View view, ViewGroup.LayoutParams layoutParams) {
		setContentView(view);
	}

	public void setContentView(View view) {
		window.setContentView(view);
		onContentChanged();
	}

	public <T extends android.view.View> T findViewById(int id) {
		View view = window.findViewById(id);

		return (T)view;
	}

	public void invalidateOptionsMenu() {
		Slog.i(TAG, "invalidateOptionsMenu() called, should we do something?");
	}

	public Window getWindow() {
		return this.window;
	}

	public Display getDisplay() {
		/* Same Display object every call, as AOSP (identity is compared
		 * by some apps); one window per ATL process. */
		return singleDisplay;
	}

	private static final Display singleDisplay = new Display();

	public final void runOnUiThread(Runnable action) {
		if (Looper.myLooper() == Looper.getMainLooper()) {
			action.run();
		} else {
			new Handler(Looper.getMainLooper()).post(action);
		}
	}

	protected void onActivityResult(int requestCode, int resultCode, Intent data) {}

	// the order must match GtkFileChooserAction enum
	private static final List<String> FILE_CHOOSER_ACTIONS = Arrays.asList(
	    "android.intent.action.OPEN_DOCUMENT",     // (0) GTK_FILE_CHOOSER_ACTION_OPEN
	    "android.intent.action.CREATE_DOCUMENT",   // (1) GTK_FILE_CHOOSER_ACTION_SAVE
	    "android.intent.action.OPEN_DOCUMENT_TREE" // (2) GTK_FILE_CHOOSER_ACTION_SELECT_FOLDER
	);

	// callback from native code
	@Export
	protected void fileChooserResultCallback(int requestCode, int resultCode, int action, String uri) {
		onActivityResult(requestCode, resultCode, new Intent(FILE_CHOOSER_ACTIONS.get(action), uri != null ? Uri.parse(uri) : null));
	}

	public void startActivityForResult(Intent intent, int requestCode, Bundle options) {
		Slog.i(TAG, "startActivityForResult(" + intent + ", " + requestCode + "," + options + ") called");
		if (intent.getComponent() != null) {
			try {
				final Activity activity = internalCreateActivity(intent.getComponent().getClassName(), getWindow().native_window, intent);
				activity.resultRequestCode = requestCode;
				activity.resultActivity = this;
				runOnUiThread(new Runnable() {
					@Override
					public void run() {
						nativeStartActivity(activity);
					}
				});
			} catch (ReflectiveOperationException e) {
				onActivityResult(requestCode, 0 /*RESULT_CANCELED*/, new Intent());
			}
		} else if (FILE_CHOOSER_ACTIONS.contains(intent.getAction())) {
			nativeFileChooser(FILE_CHOOSER_ACTIONS.indexOf(intent.getAction()), intent.getType(), intent.getStringExtra("android.intent.extra.TITLE"), requestCode);
		} else if (Intent.ACTION_GET_CONTENT.equals(intent.getAction())) {
			nativeFileChooser(0 /*GTK_FILE_CHOOSER_ACTION_OPEN*/, intent.getType(), intent.getStringExtra(Intent.EXTRA_TITLE), requestCode);
		} else if ("android.intent.action.INSTALL_PACKAGE".equals(intent.getAction())) {
			try {
				Process p = new ProcessBuilder("/usr/bin/env", "android-translation-layer", "--install", intent.getData().getPath()).start();
				int exitValue = p.waitFor();
				if (exitValue == 0) {
					onActivityResult(requestCode, -1 /*RESULT_OK*/, new Intent());
				} else {
					onActivityResult(requestCode, 0 /*RESULT_CANCELED*/, new Intent());
				}
			} catch (IOException | InterruptedException e) {
				e.printStackTrace();
				onActivityResult(requestCode, 0 /*RESULT_CANCELED*/, new Intent());
			}
		} else {
			Slog.i(TAG, "startActivityForResult: intent was not handled. Calling onActivityResult(RESULT_CANCELED).");
			onActivityResult(requestCode, 0 /*RESULT_CANCELED*/, new Intent());
		}
	}
	public void startActivityForResult(Intent intent, int requestCode) {
		startActivityForResult(intent, requestCode, null);
	}

	public void setResult(int resultCode, Intent data) {
		if (resultActivity != null) {
			resultActivity.onActivityResult(resultRequestCode, resultCode, data);
		}
	}

	public void setResult(int resultCode) {
		setResult(resultCode, null);
	}

	protected Dialog onCreateDialog(int id) {
		Slog.i(TAG, "Activity.onCreateDialog(" + id + ") called");
		return null;
	}

	protected void onPrepareDialog(int id, Dialog dialog) {
		Slog.i(TAG, "Activity.onPrepareDialog(" + id + ") called");
	}

	/* AOSP's args-carrying hook pair (queue item 272): the defaults call
	 * through to the plain ones, so apps that override either shape get
	 * AOSP's flow. */
	protected Dialog onCreateDialog(int id, Bundle args) {
		return onCreateDialog(id);
	}

	protected void onPrepareDialog(int id, Dialog dialog, Bundle args) {
		onPrepareDialog(id, dialog);
	}

	private Map<Integer, Dialog> dialogs = new HashMap<Integer, Dialog>();

	public final void showDialog(int id) {
		/* AOSP's body is this one-liner; the fork kept a full copy of the
		 * flow here instead (queue item 272). */
		showDialog(id, null);
	}

	public final boolean showDialog(int id, Bundle args) {
		/* AOSP (queue item 272): create the dialog through
		 * onCreateDialog(id, args) unless it is already managed, prepare
		 * it with onPrepareDialog(id, dialog, args), show it, and answer
		 * whether it could be created. The fork had the map and the
		 * no-args flow working but this overload — the one apps pass
		 * their arguments through — returned false without doing
		 * anything, so deprecated-but-used dialog flows (ProgressDialog
		 * confirmations and the like) silently showed nothing. */
		Dialog dialog = dialogs.get(id);
		if (dialog == null) {
			dialog = onCreateDialog(id, args);
			if (dialog == null) {
				Slog.w(TAG, "Dialog " + id + " was not created");
				return false;
			}
			dialogs.put(id, dialog);
		}
		onPrepareDialog(id, dialog, args);
		dialog.show();
		return true;
	}

	public void removeDialog(int id) {
		Dialog dialog = dialogs.remove(id);
		if (dialog != null)
			dialog.dismiss();
	}

	public void finish() {
		finishing = true;
		new Handler(Looper.getMainLooper()).post(new Runnable() {
			@Override
			public void run() {
				if (window != null && window.native_window != 0) {
					nativeFinish(getWindow().native_window);
					window.native_window = 0;
				}
			}
		});
	}

	public Object getLastNonConfigurationInstance() {
		return null;
	}

	public FragmentManager getFragmentManager() {
		return new FragmentManager(this);
	}

	public LayoutInflater getLayoutInflater() {
		return (LayoutInflater)getSystemService("layout_inflater");
	}

	public boolean isChangingConfigurations() { return false; }

	@Override
	public void onContentChanged() {
		Slog.i(TAG, "- onContentChanged - yay!");
	}

	public boolean onCreateOptionsMenu(Menu menu) {
		return true;
	}

	@Override
	public boolean onCreatePanelMenu(int featureId, Menu menu) {
		if (featureId == Window.FEATURE_OPTIONS_PANEL) {
			// HACK: catch non critical error occuring in Open Sudoku app
			try {
				return onCreateOptionsMenu(menu);
			} catch (Exception e) {
				e.printStackTrace();
				return false;
			}
		}
		return false;
	}

	@Override
	public View onCreatePanelView(int featureId) {
		return null;
	}

	public MenuInflater getMenuInflater() {
		return new MenuInflater(this);
	}

	public boolean onPrepareOptionsMenu(Menu menu) {
		return true;
	}

	@Override
	public boolean onPreparePanel(int featureId, View view, Menu menu) {
		if (featureId == Window.FEATURE_OPTIONS_PANEL && menu != null) {
			return onPrepareOptionsMenu(menu);
		}
		return true;
	}

	@Override
	public boolean onMenuItemSelected(int featureId, MenuItem item) {
		if (featureId == Window.FEATURE_OPTIONS_PANEL) {
			return onOptionsItemSelected(item);
		}
		return false;
	}

	public boolean onOptionsItemSelected(MenuItem item) {
		return false;
	}

	public void onOptionsMenuClosed(Menu menu) {}

	@Override
	public void onPanelClosed(int featureId, Menu menu) {
		if (featureId == Window.FEATURE_OPTIONS_PANEL) {
			onOptionsMenuClosed(menu);
		}
	}

	public void setTitle(CharSequence title) {
		this.title = title;
	}

	public void setTitle(int titleId) {
		this.title = getText(titleId);
	}

	public CharSequence getTitle() {
		return title;
	}

	@Export
	public void onBackPressed() {
		finish();
	}

	public void setIntent(Intent newIntent) {
		this.intent = newIntent;
	}

	public Intent getParentActivityIntent() {
		/* AOSP reads mActivityInfo.parentActivityName (the manifest's
		 * android:parentActivityName); the fork's Activity keeps no
		 * ActivityInfo, but the value does exist — the PackageManager
		 * answers from the parsed manifest and PackageParser fills
		 * parentActivityName fully qualified (buildClassName), so ask
		 * it for this activity's info (queue item 209). */
		ComponentName me = getComponentName();
		if (me == null)
			me = new ComponentName(getPackageName(), getClass().getName());
		String parentName;
		try {
			parentName = getPackageManager().getActivityInfo(me, 0).parentActivityName;
		} catch (PackageManager.NameNotFoundException e) {
			return null;
		}
		if (parentName == null || parentName.isEmpty())
			return null;
		/* AOSP: if the parent itself has no parent, hand back a
		 * main-activity intent (that is what Up should launch); else a
		 * plain component intent for it. */
		final ComponentName target = new ComponentName(this, parentName);
		try {
			final ActivityInfo parentInfo = getPackageManager().getActivityInfo(target, 0);
			if (parentInfo.parentActivityName == null)
				return Intent.makeMainActivity(target);
		} catch (PackageManager.NameNotFoundException e) {
			Slog.e(TAG, "getParentActivityIntent: bad parentActivityName '" + parentName +
			           "' in manifest");
			return null;
		}
		return new Intent().setComponent(target);
	}

	@Override
	public boolean onMenuOpened(int featureId, Menu menu) {
		Slog.i(TAG, "onMenuOpened(" + featureId + ", " + menu + ") called");
		return false;
	}

	public void recreate() {
		finishing = true;
		try {
			/* TODO: check if this is a toplevel activity */
			Activity activity = internalCreateActivity(this.getClass().getName(), getWindow().native_window, intent);
			new Handler().post(new Runnable() {
				@Override
				public void run() {
					nativeFinish(0);
					nativeStartActivity(activity);
				}
			});
		} catch (ReflectiveOperationException e) {
			Slog.i(TAG, "exception in Activity.recreate, this is kinda sus");
			e.printStackTrace();
		}
	}

	public String getLocalClassName() {
		final String pkg = getPackageName();
		final String cls = this.getClass().getName();
		int packageLen = pkg.length();
		if (!cls.startsWith(pkg) || cls.length() <= packageLen || cls.charAt(packageLen) != '.') {
			return cls;
		}
		return cls.substring(packageLen + 1);
	}

	public SharedPreferences getPreferences(int mode) {
		return getSharedPreferences(getLocalClassName(), mode);
	}

	@Export
	protected void onNewIntent(Intent intent) {}

	public final Activity getParent() {
		return null;
	}

	/* Queue item 131: was `return true; // FIXME?`. ATL's window-focus
	 * signal is onWindowFocusChanged(hasFocus), fired by
	 * api-impl-jni/app/android_app_Activity.c when an activity becomes
	 * or stops being the visible one in the app window's stack (ATL's
	 * focus model is per-activity-in-window, not GTK window
	 * activation). JNI sets this field directly on every callback
	 * because apps override onWindowFocusChanged and often skip
	 * super(). Initial state is true, unlike AOSP's false: ATL's first
	 * focus callback fires only after onResume, and AOSP-accurate
	 * false-before-first-focus regressed me.zhanghai.android.files in
	 * gate fc42 (a bundled androidx path reads it during startup and
	 * re-ran its loader chain against uninitialized members; the
	 * caller is not in the app's own source). The queue item flagged
	 * "constant instead of state": the field is real state now, it
	 * just starts at the value the fork already guaranteed. */
	private boolean windowFocused = true;

	public boolean hasWindowFocus() {
		return windowFocused;
	}

	public boolean isDestroyed() {
		return destroyed;
	}

	public void finishAffinity() {
		finish();
	}

	public void overridePendingTransition(int enterAnim, int exitAnim) {
		/* AOSP hands the animation resource pair to the window manager
		 * for the activity transition just started (queue item 219). ATL
		 * transitions activities in-process with no window-manager
		 * animation stage — there is no transition to decorate (same gap
		 * as the postpone-enter-transition stubs nearby), so the
		 * request has nothing to attach to. */
	}

	public native boolean isTaskRoot();

	public void postponeEnterTransition() {
		/* AOSP defers the enter transition through the activity's
		 * transition state (mActivityTransitionState), so the app can
		 * load content before the animation runs (queue items 243, 248).
		 * ATL has no activity transition state at all — activities are
		 * swapped in-process with nothing to defer or start (same gap as
		 * overridePendingTransition and the getSharedElement* stubs) —
		 * so there is no state machine to hold the transition open. */
	}

	public void startPostponedEnterTransition() {
		/* queue items 243, 248: releases a postponement that could never
		 * have been taken; see postponeEnterTransition above. */
	}

	public boolean isChild() {
		/* AOSP: mParent != null, i.e. this is an embedded (child)
		 * activity of a LocalActivityManager host (queue item 223). The
		 * fork never sets an activity parent — ATL has no embedded
		 * activity support at all — so false is not a dropped value, it
		 * is the state. */
		return false;
	}

	public void setTaskDescription(ActivityManager.TaskDescription description) {
		/* AOSP hands the label/icon/colors to the window manager for this
		 * activity's *task* entry (what the recents screen shows) —
		 * ActivityTaskManager.setTaskDescription (queue item 259). ATL has
		 * no task concept: there is no recents list or task host to label;
		 * the only visible label is the window title, which setTitle sets
		 * directly. Nothing to attach this to. */
	}

	private native void nativeFinish(long native_window);
	public static native void nativeStartActivity(Activity activity);
	public static native boolean nativeResumeActivity(Class<? extends Activity> activityClass, Intent intent);
	public static native void nativeOpenURI(String uri);
	public native void nativeFileChooser(int action, String type, String title, int requestCode);
	public void reportFullyDrawn() {}
	public void setVisible(boolean visible) {
		/* AOSP Activity.setVisible sets mVisible (old API predating the
		 * Window callbacks it now backs); ATL has no visibility backend
		 * to drive, but apps (and frameworks) read state back, so the
		 * value is kept; see onWindowFocusChanged for the real signal. */
		mVisible = visible;
	}

	private boolean mVisible = false;

	public boolean isVisible() {
		return mVisible;
	}
	public Uri getReferrer() {
		/* AOSP (queue item 256): an explicit EXTRA_REFERRER wins, then
		 * EXTRA_REFERRER_NAME parsed, then android-app://<calling
		 * package> when the caller is known, else null. ATL's known
		 * caller is the one startActivityForResult recorded
		 * (getCallingPackage above), so the fallback is reachable for
		 * in-process launches. */
		Intent intent = getIntent();
		if (intent != null) {
			Parcelable referrer = intent.getParcelableExtra(Intent.EXTRA_REFERRER);
			if (referrer instanceof Uri)
				return (Uri) referrer;
			String referrerName = intent.getStringExtra(Intent.EXTRA_REFERRER_NAME);
			if (referrerName != null)
				return Uri.parse(referrerName);
		}
		String callingPackage = getCallingPackage();
		if (callingPackage != null)
			return new Uri.Builder().scheme("android-app").authority(callingPackage).build();
		return null;
	}
	public void setDefaultKeyMode(int flag) {}
	public void registerForContextMenu(View view) {
		/* AOSP's body is literally this: the activity becomes the view's
		 * context-menu listener (unregister clears it) (queue item 251).
		 * The view now stores that listener (queue item 244); nothing
		 * calls it yet — context-menu panel machinery is a gap (see
		 * View.setOnCreateContextMenuListener). */
		view.setOnCreateContextMenuListener(this);
	}

	public void unregisterForContextMenu(View view) {
		view.setOnCreateContextMenuListener(null);
	}

	/* AOSP's Activity implements OnCreateContextMenuListener and its own
	 * implementation is empty — apps override it to fill the menu. Same
	 * here; with no context-menu machinery in the fork this is never
	 * called (queue items 244, 251). */
	@Override
	public void onCreateContextMenu(ContextMenu menu, View v, ContextMenu.ContextMenuInfo menuInfo) {}

	public native boolean isInMultiWindowMode();

	// we never put an activity into picture-in-picture, so this is always false
	public boolean isInPictureInPictureMode() { return false; }

	private final List<Application.ActivityLifecycleCallbacks> lifecycleCallbacks = new ArrayList<>();

	public void registerActivityLifecycleCallbacks(Application.ActivityLifecycleCallbacks callback) {
		lifecycleCallbacks.add(callback);
	}

	public void unregisterActivityLifecycleCallbacks(Application.ActivityLifecycleCallbacks callback) {
		lifecycleCallbacks.remove(callback);
	}

	private Application.ActivityLifecycleCallbacks[] getLifecycleCallbacks() {
		return lifecycleCallbacks.toArray(new Application.ActivityLifecycleCallbacks[0]);
	}

	/* called from native code around the lifecycle methods, like the ones in AOSP's Activity */
	@Export
	private void dispatchActivityPostCreated() {
		for (Application.ActivityLifecycleCallbacks callback : getLifecycleCallbacks())
			callback.onActivityPostCreated(this, null);
	}

	@Export
	private void dispatchActivityPostStarted() {
		for (Application.ActivityLifecycleCallbacks callback : getLifecycleCallbacks())
			callback.onActivityPostStarted(this);
	}

	@Export
	private void dispatchActivityPostResumed() {
		for (Application.ActivityLifecycleCallbacks callback : getLifecycleCallbacks())
			callback.onActivityPostResumed(this);
	}

	@Export
	private void dispatchActivityPrePaused() {
		for (Application.ActivityLifecycleCallbacks callback : getLifecycleCallbacks())
			callback.onActivityPrePaused(this);
	}

	@Export
	private void dispatchActivityPreStopped() {
		for (Application.ActivityLifecycleCallbacks callback : getLifecycleCallbacks())
			callback.onActivityPreStopped(this);
	}

	@Export
	private void dispatchActivityPreDestroyed() {
		for (Application.ActivityLifecycleCallbacks callback : getLifecycleCallbacks())
			callback.onActivityPreDestroyed(this);
	}

	public void setDisablePreviewScreenshots(boolean disable) {}
	public final View requireViewById(int id) {
		View view = findViewById(id);
		if (view == null)
			throw new IllegalArgumentException("ID does not reference a View inside this View");
		return view;
	}

	public View onCreateView(View parent, String name, Context context, AttributeSet attrs) {
		return null;
	}

	public boolean onSearchRequested() {
		/* AOSP calls startSearch(null, false, null, false) and returns
		 * true (false only on TV/watch UI modes) (queue item 215). The
		 * fork has no startSearch at all, no SearchManager service and
		 * no search dialog, so no search would start and answering true
		 * would be a lie — false is what AOSP returns when search is
		 * unavailable. **Gap: search subsystem** (startSearch +
		 * SearchManager). */
		return false;
	}

	public View getCurrentFocus() {
		/* AOSP returns the view in this activity's window that holds
		 * focus (queue item 238). Focus is GTK's (queue item 88's
		 * finding) but the fork answers focus per view now
		 * (isFocused/findFocus — queue item 114's work), so walk the
		 * content tree. */
		View root = root_view != null ? root_view : getWindow().getDecorView();
		return root == null ? null : root.findFocus();
	}

	public void setProgressBarIndeterminateVisibility(boolean indeterminate) {}

	public int getChangingConfigurations() {
		return 0;
	}

	public int getTaskId() {
		/* we don't support multiple activity stacks, so this is probably fine? */
		return System.identityHashCode(this.getApplicationContext());
	}

	public boolean moveTaskToBack(boolean nonroot) {
		return true;
	}

	public void setFinishOnTouchOutside(boolean finish) {
	}

	public void closeOptionsMenu() {
		/* AOSP gates on mWindow.hasFeature(FEATURE_OPTIONS_PANEL) and
		 * then closes the panel (queue item 217). That feature can never
		 * be granted in ATL — Window.requestFeature answers no for
		 * everything (queue item 208's finding) and Window.hasFeature is
		 * false — so AOSP's own closeOptionsMenu would do nothing here
		 * either; the fork has no openPanel/closePanel machinery at all.
		 * The empty body is the faithful answer, not dropped state. */
	}

	public void finishActivity(int requestCode) {
		/* TODO: track started activities so we can finish the right one here */
		Slog.w(TAG, "finishActivity: stub");
	}

	public void finishAfterTransition() {
		finish();
	}

	public boolean dispatchKeyEvent(KeyEvent event) {
		/* AOSP hands the event to the window's view hierarchy first
		 * (Window.superDispatchKeyEvent -> DecorView), and only an
		 * unhandled event falls back to the Activity's own
		 * onKeyDown/onKeyUp through KeyEvent.dispatch(). The fork
		 * returned false without consulting anything, so apps that
		 * route key events through their Activity (custom key
		 * routing, re-dispatch from an OnKeyListener) saw every key
		 * as unhandled even when a view would have taken it.
		 * Honest limitation: ATL's real key input comes from GTK
		 * straight to the focused widget's View.dispatchKeyEvent
		 * (api-impl-jni/widgets/WrapperWidget.c) - nothing calls
		 * this method in the normal path - and the fork's ViewGroup
		 * has no focused-child propagation (AOSP ViewGroup
		 * dispatches to mFocused first), so the window half only
		 * reaches views that themselves override dispatchKeyEvent. */
		Window w = getWindow();
		if (w != null && w.superDispatchKeyEvent(event))
			return true;
		return event.dispatch(this);
	}

	/* AOSP Activity key callbacks, the KeyEvent.Callback half of the
	 * Window.Callback pair. In AOSP these forward to mDefaultKeySrv, the
	 * legacy default key service, which is null unless the app used
	 * getDefaultKeyDialog - i.e. the effective framework default is
	 * false (unhandled) and apps override these.
	 * AOSP's Activity.onKeyDown/onKeyUp also carry the legacy BACK and
	 * ESCAPE handling (queue items 166, 176): startTracking() on the way
	 * down and onBackPressed() on a tracked, uncancelled key up. ATL does
	 * not need that path and must not duplicate it: the desktop back
	 * action is delivered by api-impl-jni/app/android_app_Activity.c
	 * (current_activity_back_pressed -> onBackPressed) rather than as a
	 * KEYCODE_BACK KeyEvent, and the DEFAULT_KEYS_* half of onKeyDown
	 * needs the window panel shortcuts (Window.performPanelShortcut)
	 * that ATL does not implement. Recorded with the key-input gaps
	 * rather than half-implemented. */
	public boolean onKeyDown(int keyCode, KeyEvent event) {
		return false;
	}

	public boolean onKeyUp(int keyCode, KeyEvent event) {
		return false;
	}

	public boolean onKeyLongPress(int keyCode, KeyEvent event) {
		return false;
	}

	public boolean onKeyMultiple(int keyCode, int repeatCount, KeyEvent event) {
		return false;
	}

	public void requestPermissions(String[] permissions, int requestCode) {
		Slog.w(TAG, "requestPermissions(" + Arrays.toString(permissions) + "): not handled");
	}

	public boolean shouldShowRequestPermissionRationale(String permission) {
		/* AOSP delegates to PackageManager, and that answers true only
		 * after the user has *denied* the permission at least once
		 * (queue item 230). ATL never asks the user: there is no runtime
		 * permission dialog, checkPermission grants what the sandbox
		 * provides, and requestPermissions above is a logged no-op — no
		 * denial can ever have happened, so false is the faithful
		 * answer. The old constant true pushed every permission-helper
		 * app into its rationale-then-retry loop for a denial that
		 * cannot exist. */
		return false;
	}

	public ActionBar getActionBar() {
		return window.getActionBar();
	}

	@Override
	public void atl_attach_base_context(Context baseContext) {
		super.atl_attach_base_context(baseContext);
		this.window = new Window(this, this);
	}

	public void applyOverrideConfiguration(Configuration overrideConfiguration) {
		/* AOSP applies the override to the *embedded* activity config
		 * state (mOverrideConfiguration, merged into the activity's
		 * Configuration by the activity launcher) — the feature belongs
		 * to embedded activities, which ATL does not have (see isChild)
		 * (queue item 218). The fork also has no config-override path:
		 * Configuration comes from the ATL process, not per-activity.
		 * No-op rather than AOSP's IllegalStateException, because ATL
		 * cannot tell embedded from non-embedded callers; a caller
		 * (androidx embedded-window helpers) proceeds with the process
		 * config, which is all ATL could give it. Recorded with the
		 * embedded-activity gap. */
	}

	public ComponentName getCallingActivity() {
		return resultActivity == null ? null : resultActivity.getComponentName();
	}

	public boolean onNavigateUp() {
		return false;
	}

	/* Shared-element transition callbacks (queue items 249, 250): AOSP's
	 * body is a store (null becomes its inert NULL_CALLBACK singleton)
	 * into the fields its activity-transition state machine reads back
	 * when a scene transition runs. ATL has no activity transitions (gap
	 * recorded at postponeEnterTransition and overridePendingTransition),
	 * so nothing reads these back; the fork's SharedElementCallback is an
	 * empty stand-in class with no NULL_CALLBACK to substitute, and
	 * substituting null is the same for anything that could consume it. */
	private SharedElementCallback atl_exit_shared_element_callback = null;
	private SharedElementCallback atl_enter_shared_element_callback = null;

	public void setExitSharedElementCallback(SharedElementCallback callback) {
		atl_exit_shared_element_callback = callback;
	}

	public void setEnterSharedElementCallback(SharedElementCallback callback) {
		atl_enter_shared_element_callback = callback;
	}
}
