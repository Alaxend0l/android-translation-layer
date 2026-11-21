package android.atl;

import android.atl.annotation.Export;
import android.util.Log;

/**
 * What Android does when an exception escapes app code on the main thread:
 * log "FATAL EXCEPTION", run the thread's uncaught-exception handlers (the
 * pre-handler, then the app's own handler or the default one), then end the
 * process with exit code 10 like Android's KillApplicationHandler.
 * Called from native code for exceptions escaping lifecycle methods
 * (ATL_LIFECYCLE_CHECK in util.h).
 */
public final class ATLUncaught {
	private ATLUncaught() {}

	@Export
	public static void dispatch(Throwable e) {
		Thread t = Thread.currentThread();
		Log.e("AndroidRuntime", "FATAL EXCEPTION: " + t.getName(), e);
		try {
			t.dispatchUncaughtException(e);
		} catch (Throwable inner) {
			Log.e("AndroidRuntime", "Error in uncaught exception handler", inner);
		} finally {
			System.exit(10);
		}
	}
}
