package android.atl;

import android.content.ContentProvider;
import android.content.ContentResolver;
import android.content.ContentValues;
import android.database.Cursor;
import android.net.Uri;
import android.os.ParcelFileDescriptor;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.List;

/**
 * content://media: MediaStore backed by the media library (ATLMediaStore,
 * doc/MediaLibrary.md). Picking a single file is ACTION_GET_CONTENT /
 * ACTION_OPEN_DOCUMENT (file chooser), not a MediaStore query.
 */
public class ATLMediaContentProvider extends ContentProvider {

	private static ATLMediaStore store() {
		return ATLMediaStore.get();
	}

	@Override
	public Cursor query(Uri uri, String[] projection, String selection, String[] selectionArgs, String sortOrder) {
		ATLMediaStore.Target t = ATLMediaStore.route(uri);
		if (t == null)
			throw new IllegalArgumentException("Unknown URL " + uri);
		return store().query(t, projection, selection, selectionArgs, sortOrder, uri.getQueryParameter("limit"));
	}

	@Override
	public String getType(Uri uri) {
		return store().mimeFor(ATLMediaStore.route(uri));
	}

	@Override
	public ParcelFileDescriptor openFile(Uri uri, String mode) throws FileNotFoundException {
		List<String> s = uri.getPathSegments();
		File f;
		// content://media/external/audio/albumart/<album id>
		if (s.size() == 4 && "audio".equals(s.get(1)) && "albumart".equals(s.get(2))) {
			try {
				f = store().albumArt(Long.parseLong(s.get(3)));
			} catch (NumberFormatException e) {
				f = null;
			}
		} else {
			f = store().fileFor(ATLMediaStore.route(uri));
		}
		if (f == null)
			throw new FileNotFoundException("No item at " + uri);
		if (mode.contains("w"))
			store().invalidate();
		return ParcelFileDescriptor.open(f, ParcelFileDescriptor.parseMode(mode));
	}

	@Override
	public Uri insert(Uri uri, ContentValues values) {
		try {
			Uri item = store().insert(uri, values == null ? new ContentValues() : values);
			notifyStoreChanged(item != null ? item : uri);
			return item;
		} catch (IOException e) {
			throw new IllegalStateException("Failed to create item for " + uri, e);
		}
	}

	@Override
	public int update(Uri uri, ContentValues values, String selection, String[] selectionArgs) {
		ATLMediaStore.Target t = ATLMediaStore.route(uri);
		if (t == null)
			throw new IllegalArgumentException("Unknown URL " + uri);
		int n = store().update(t, values, selection, selectionArgs);
		if (n > 0)
			notifyStoreChanged(uri);
		return n;
	}

	@Override
	public int delete(Uri uri, String selection, String[] selectionArgs) {
		ATLMediaStore.Target t = ATLMediaStore.route(uri);
		if (t == null)
			throw new IllegalArgumentException("Unknown URL " + uri);
		int n = store().delete(t, selection, selectionArgs);
		if (n > 0)
			notifyStoreChanged(uri);
		return n;
	}

	private void notifyStoreChanged(Uri uri) {
		// MediaStore mutates must wake the app's observers (cursors set up
		// with setNotificationUri, registered ContentObservers); AOSP sends
		// notifyChange from the provider side the same way.
		ContentResolver resolver = getContext().getContentResolver();
		if (resolver != null)
			resolver.notifyChange(uri, null);
	}
}
