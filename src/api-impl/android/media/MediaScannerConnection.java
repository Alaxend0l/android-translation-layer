package android.media;

import android.atl.ATLMediaStore;
import android.content.Context;
import android.net.Uri;
import android.provider.MediaStore;
import java.io.File;

/* The media library (ATLMediaStore) rescans on demand; scanning a file means
 * refreshing the index and telling the app where the file is now. */
public class MediaScannerConnection {

	public interface OnScanCompletedListener {
		void onScanCompleted(String path, Uri uri);
	}

	public interface MediaScannerConnectionClient extends OnScanCompletedListener {
		void onMediaScannerConnected();
	}

	private final MediaScannerConnectionClient client;
	private boolean connected = false;

	public MediaScannerConnection(Context context, MediaScannerConnectionClient client) {
		this.client = client;
	}

	public void connect() {
		connected = true;
		if (client != null)
			client.onMediaScannerConnected();
	}

	public void disconnect() {
		connected = false;
	}

	public boolean isConnected() {
		return connected;
	}

	public void scanFile(String path, String mimeType) {
		Uri uri = scan(path);
		if (client != null)
			client.onScanCompleted(path, uri);
	}

	public static void scanFile(Context context, String[] paths, String[] mimeTypes, OnScanCompletedListener listener) {
		/* Android calls back on a binder thread, after scanning */
		new Thread(new Runnable() {
			@Override
			public void run() {
				for (String path : paths) {
					Uri uri = scan(path);
					if (listener != null)
						listener.onScanCompleted(path, uri);
				}
			}
		}, "MediaScannerConnection").start();
	}

	/** content:// URI of the file in the media library, or null if it isn't media there. */
	private static Uri scan(String path) {
		ATLMediaStore store = ATLMediaStore.get();
		store.invalidate();
		String canonical;
		try {
			canonical = new File(path).getCanonicalPath();
		} catch (java.io.IOException e) {
			return null;
		}
		Uri files = MediaStore.Files.getContentUri("external");
		try (android.database.Cursor c = store.query(ATLMediaStore.route(files), new String[] {"_id"}, "_data=? OR _data=?", new String[] {path, canonical}, null, "1")) {
			if (c.moveToFirst())
				return MediaStore.Files.getContentUri("external", c.getLong(0));
		}
		return null;
	}
}
