package android.media;

import android.atl.ATLLoadedApp;
import android.content.Context;
import android.content.res.AssetFileDescriptor;
import android.content.res.AssetManager;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import java.io.File;
import java.io.IOException;

public class SoundPool {
	private static final String TAG = "SoundPool";

	private long nativePool;
	private OnLoadCompleteListener onLoadCompleteListener;
	private Handler eventHandler;

	public interface OnLoadCompleteListener {
		/**
		 * Called when a sound has completed loading.
		 *
		 * @param soundPool SoundPool object from the load() method
		 * @param soundPool the sample ID of the sound loaded.
		 * @param status the status of the load operation (0 = success)
		 */
		public void onLoadComplete(SoundPool soundPool, int sampleId, int status);
	}

	public SoundPool(int maxStreams, int streamType, int srcQuality) {
		nativePool = native_constructor();
	}

	public int load(AssetFileDescriptor afd, int priority) throws IOException {
		String packageCodePath = ATLLoadedApp.getPrimaryApplication().pkg.applicationInfo.sourceDir;
		AssetManager.extractFromAPK(packageCodePath, afd.fileName, afd.fileName);
		return loadFile(Environment.getExternalStorageDirectory().getPath() + "/" + afd.fileName);
	}

	public int load(Context context, int resId, int priority) throws IOException {
		String fileName = context.getResources().getString(resId);
		AssetManager.extractFromAPK(context.getPackageCodePath(), fileName, fileName);
		return loadFile(Environment.getExternalStorageDirectory().getPath() + "/" + fileName);
	}

	/**
	 * Load a sound from a file path. As in AOSP, an unreadable file is logged
	 * and returns 0 (no sample, no load-complete callback).
	 */
	public int load(String path, int priority) {
		File file = new File(path);
		if (!file.isFile() || !file.canRead()) {
			Log.e(TAG, "error loading " + path);
			return 0;
		}
		return loadFile(path);
	}

	/**
	 * Sample IDs are 1-based like on Android, where 0 means the load failed.
	 */
	private int loadFile(String path) {
		int sampleId = nativeLoad(nativePool, path) + 1;
		notifyLoadComplete(sampleId);
		return sampleId;
	}

	private void notifyLoadComplete(final int sampleId) {
		final OnLoadCompleteListener listener;
		final Handler handler;
		synchronized (this) {
			listener = onLoadCompleteListener;
			handler = eventHandler;
		}
		if (listener == null)
			return;
		// AOSP delivers EVENT_SAMPLE_LOADED asynchronously on the listener's looper,
		// never from inside load(). GtkMediaFile opens the file lazily, so a sample
		// counts as loaded once its stream exists.
		handler.post(new Runnable() {
			@Override
			public void run() {
				listener.onLoadComplete(SoundPool.this, sampleId, 0);
			}
		});
	}

	/**
	 * Sets the callback hook for the OnLoadCompleteListener. As in AOSP, callbacks
	 * run on the calling thread's looper, or the main looper if it has none.
	 */
	public void setOnLoadCompleteListener(OnLoadCompleteListener listener) {
		synchronized (this) {
			if (listener != null) {
				Looper looper = Looper.myLooper();
				eventHandler = new Handler(looper != null ? looper : Looper.getMainLooper());
			} else {
				eventHandler = null;
			}
			onLoadCompleteListener = listener;
		}
	}

	public final int play(int soundID, float leftVolume, float rightVolume, int priority, int loop, float rate) {
		if (soundID <= 0)
			return 0;
		return nativePlay(nativePool, soundID - 1);
	}

	public void autoResume() {
		System.out.println("WARNING: SoundPool.autoResume not implemented yet");
	}

	public void autoPause() {
		System.out.println("WARNING: SoundPool.autoPause not implemented yet");
	}

	public void setVolume(int streamType, float leftVolume, float rightVolume) {
		System.out.println("WARNING: SoundPool.setVolume not implemented yet");
	}

	public static class Builder {
		public Builder setAudioAttributes(AudioAttributes attributes) {
			return this;
		}

		public Builder setMaxStreams(int maxStreams) {
			return this;
		}

		public SoundPool build() {
			return new SoundPool(0, 0, 0); // FIXME
		}
	}

	public void release() {}

	private static native long native_constructor();
	private static native int nativeLoad(long nativePool, String path);
	public static native int nativePlay(long nativePool, int soundID);
}
