package android.app;

import android.os.Bundle;

public class Fragment {

	Activity activity;

	public void onCreate(Bundle savedInstanceState) {
	}

	/* AOSP's framework-fragment callback fired by
	 * Activity.performCreate's mFragments.dispatchActivityCreated() after
	 * the activity's own onCreate() returns. androidx lifecycle's
	 * ReportFragment (the pre-API-29 path, which ATL apps get whenever
	 * their minSdk < 29) dispatches Lifecycle.Event.ON_CREATE here, so an
	 * activity whose fragments never see this callback never gets
	 * ON_CREATE before ON_START - and androidx then replays ON_CREATE
	 * inside the ON_START pass, at which point the LifecycleRegistry is
	 * already STARTED and e.g. ActivityResultRegistry.register() rejects
	 * the late registration (Phonograph Plus intro crash). */
	public void onActivityCreated(Bundle savedInstanceState) {
	}

	public void onStart() {
	}

	public void onResume() {
	}

	public void onPause() {
	}

	public void onStop() {
	}

	public void onDestroy() {
	}

	public Activity getActivity() {
		return activity;
	}
}
