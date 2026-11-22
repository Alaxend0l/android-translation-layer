package android.content;

import android.accounts.Account;
import android.atl.ATLLoadedApp;
import android.content.res.AssetFileDescriptor;
import android.database.ContentObserver;
import android.database.Cursor;
import android.database.MatrixCursor;
import android.net.Uri;
import android.os.Bundle;
import android.os.CancellationSignal;
import android.os.IBinder;
import android.os.ParcelFileDescriptor;
import android.util.Log;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

public class ContentResolver {
	public static final String SCHEME_CONTENT = "content";

	// AOSP name; used by ContentProvider.openTypedAssetFile thumbnail hints.
	public static final String EXTRA_SIZE = "extraSize";

	public static final String SYNC_EXTRAS_IGNORE_SETTINGS = "ignore_settings";

	public static final int SYNC_OBSERVER_TYPE_STATUS = 1 << 0;
	public static final int SYNC_OBSERVER_TYPE_SETTINGS = 1 << 1;
	public static final int SYNC_OBSERVER_TYPE_PENDING = 1 << 2;
	public static final int SYNC_OBSERVER_TYPE_ACTIVE = 1 << 3;
	public static final int SYNC_OBSERVER_TYPE_ANY = Integer.MAX_VALUE;

	// ATL has no sync service: a status-change listener can never fire, so the
	// callback is only kept as the opaque handle AOSP returns.
	public static Object addStatusChangeListener(int mask, SyncStatusObserver callback) {
		return callback;
	}

	public static void removeStatusChangeListener(Object handle) {
	}

	public int getUserId() {
		return 0;
	}

	private static final class ContentObserverEntry {
		final Uri uri;
		final boolean descendants;
		final ContentObserver observer;

		ContentObserverEntry(Uri uri, boolean descendants, ContentObserver observer) {
			this.uri = uri;
			this.descendants = descendants;
			this.observer = observer;
		}
	}

	// ATL runs one app per process, so a per-process static registry matches
	// AOSP's per-process ContentResolver behaviour.
	private static final List<ContentObserverEntry> contentObservers = new ArrayList<>();

	public final void registerContentObserver(Uri uri, boolean notifyForDescendants, ContentObserver observer) {
		if (observer == null)
			throw new NullPointerException("content observer is null");
		synchronized (contentObservers) {
			// AOSP re-registration updates the existing entry.
			for (int i = contentObservers.size() - 1; i >= 0; i--) {
				ContentObserverEntry e = contentObservers.get(i);
				if (e.observer == observer && Objects.equals(e.uri, uri))
					contentObservers.remove(i);
			}
			contentObservers.add(new ContentObserverEntry(uri, notifyForDescendants, observer));
		}
	}

	public final void unregisterContentObserver(ContentObserver observer) {
		synchronized (contentObservers) {
			for (int i = contentObservers.size() - 1; i >= 0; i--) {
				if (contentObservers.get(i).observer == observer)
					contentObservers.remove(i);
			}
		}
	}

	public void notifyChange(Uri uri, ContentObserver observer) {
		notifyChangeInternal(uri, observer, 0);
	}

	public void notifyChange(Uri uri, ContentObserver observer, boolean syncToNetwork) {
		notifyChangeInternal(uri, observer, 0);
	}

	public final void notifyChange(Uri uri, ContentObserver observer, int flags) {
		notifyChangeInternal(uri, observer, flags);
	}

	private void notifyChangeInternal(Uri uri, ContentObserver observer, int flags) {
		List<ContentObserverEntry> snapshot;
		synchronized (contentObservers) {
			snapshot = new ArrayList<>(contentObservers);
		}
		for (int i = 0; i < snapshot.size(); i++) {
			ContentObserverEntry entry = snapshot.get(i);
			boolean selfChange = entry.observer == observer;
			if (selfChange && !entry.observer.deliverSelfNotifications())
				continue;
			if (!uriMatches(entry.uri, entry.descendants, uri))
				continue;
			entry.observer.dispatchChange(selfChange, uri, flags);
		}
	}

	// AOSP URI matching: exact; or uri is under entry.uri (honouring the
	// descendants flag); or entry.uri is under uri - an observer registered
	// below a changed URI is always notified.
	private static boolean uriMatches(Uri entryUri, boolean descendants, Uri changedUri) {
		if (entryUri == null || changedUri == null)
			return true;
		if (entryUri.equals(changedUri))
			return true;
		if (uriUnder(changedUri, entryUri, descendants))
			return true;
		return uriUnder(entryUri, changedUri, true);
	}

	private static boolean uriUnder(Uri uri, Uri base, boolean descendants) {
		if (!Objects.equals(uri.getScheme(), base.getScheme())
				|| !Objects.equals(uri.getAuthority(), base.getAuthority()))
			return false;
		if (descendants || uri.getPathSegments().size() == base.getPathSegments().size()) {
			return uri.getEncodedPath().startsWith(base.getEncodedPath());
		}
		return false;
	}

	public final void registerContentObserver(Uri uri, boolean notifyForDescendants, ContentObserver observer, int userHandle) {
		// ATL is single-user.
		registerContentObserver(uri, notifyForDescendants, observer);
	}

	public ParcelFileDescriptor openFileDescriptor(Uri uri, String mode) throws FileNotFoundException {
		if ("file".equals(uri.getScheme())) {
			return ParcelFileDescriptor.open(new File(uri.getPath()), ParcelFileDescriptor.parseMode(mode));
		} else {
			ContentProvider provider = ContentProvider.atl_get_content_provider(uri.getAuthority());
			if (provider != null)
				return provider.openFile(uri, mode);
			else
				return null;
		}
	}

	public ParcelFileDescriptor openFileDescriptor(Uri uri, String mode, CancellationSignal signal) throws FileNotFoundException {
		return openFileDescriptor(uri, mode);
	}

	public AssetFileDescriptor openAssetFileDescriptor(Uri uri, String mode) throws FileNotFoundException {
		if ("file".equals(uri.getScheme())) {
			return new AssetFileDescriptor(ParcelFileDescriptor.open(new File(uri.getPath()), ParcelFileDescriptor.parseMode(mode)), 0, AssetFileDescriptor.UNKNOWN_LENGTH);
		} else {
			ContentProvider provider = ContentProvider.atl_get_content_provider(uri.getAuthority());
			if (provider != null)
				return provider.openAssetFile(uri, mode);
			else
				return null;
		}
	}

	public AssetFileDescriptor openAssetFileDescriptor(Uri uri, String mode, CancellationSignal signal) throws FileNotFoundException {
		return openAssetFileDescriptor(uri, mode);
	}

	public final AssetFileDescriptor openTypedAssetFileDescriptor(Uri uri, String mimeType, Bundle opts) throws FileNotFoundException {
		/* FIXME */
		return openAssetFileDescriptor(uri, "r");
	}

	public final AssetFileDescriptor openTypedAssetFileDescriptor(Uri uri, String mimeType, Bundle opts, CancellationSignal cancellationSignal) throws FileNotFoundException {
		return openTypedAssetFileDescriptor(uri, mimeType, opts);
	}

	public InputStream openInputStream(Uri uri) throws FileNotFoundException {
		ParcelFileDescriptor fd = openFileDescriptor(uri, "r");
		return fd != null ? new ParcelFileDescriptor.AutoCloseInputStream(fd) : null;
	}

	public OutputStream openOutputStream(Uri uri) throws FileNotFoundException {
		ParcelFileDescriptor fd = openFileDescriptor(uri, "w");
		return fd != null ? new ParcelFileDescriptor.AutoCloseOutputStream(fd) : null;
	}

	public Cursor query(Uri uri, String[] projection, Bundle queryArgs, CancellationSignal cancellationSignal) {
		ContentProvider provider = ContentProvider.atl_get_content_provider(uri.getAuthority());
		if (provider != null) {
			/* as ContentProvider.query(Uri, String[], Bundle, CancellationSignal) in AOSP: the
			 * SQL-style arguments; a limit/offset is passed on as the "limit" query parameter
			 * (honoured by the media provider) */
			String selection = null, sortOrder = null;
			String[] selectionArgs = null;
			if (queryArgs != null) {
				selection = queryArgs.getString("android:query-arg-sql-selection");
				selectionArgs = queryArgs.getStringArray("android:query-arg-sql-selection-args");
				sortOrder = queryArgs.getString("android:query-arg-sql-sort-order");
				if (sortOrder == null) {
					String[] columns = queryArgs.getStringArray("android:query-arg-sort-columns");
					if (columns != null && columns.length > 0) {
						sortOrder = String.join(", ", columns);
						if (queryArgs.getInt("android:query-arg-sort-direction", 0) == 1)
							sortOrder += " DESC";
					}
				}
				if (queryArgs.containsKey("android:query-arg-limit")) {
					String limit = String.valueOf(queryArgs.getInt("android:query-arg-limit"));
					if (queryArgs.containsKey("android:query-arg-offset"))
						limit = queryArgs.getInt("android:query-arg-offset") + ", " + limit;
					uri = uri.buildUpon().appendQueryParameter("limit", limit).build();
				}
			}
			return provider.query(uri, projection, selection, selectionArgs, sortOrder);
		} else if ("file".equals(uri.getScheme())) {
			MatrixCursor cursor = new MatrixCursor(projection);
			Object[] row = new Object[projection.length];
			native_query_file_info(uri.getPath(), projection, row);
			cursor.addRow(row);
			return cursor;
		} else {
			return null;
		}
	}

	public Cursor query(Uri uri, String[] projection, String selection, String[] selectionArgs, String sortOrder) {
		ContentProvider provider = ContentProvider.atl_get_content_provider(uri.getAuthority());
		if (provider != null) {
			return provider.query(uri, projection, selection, selectionArgs, sortOrder);
		} else if ("file".equals(uri.getScheme())) {
			if (projection == null)
				projection = new String[] {"_display_name", "mime_type"};
			MatrixCursor cursor = new MatrixCursor(projection);
			Object[] row = new Object[projection.length];
			native_query_file_info(uri.getPath(), projection, row);
			cursor.addRow(row);
			return cursor;
		} else {
			return null;
		}
	}

	public Cursor query(Uri uri, String[] projection, String selection, String[] selectionArgs, String sortOrder, CancellationSignal cancellationSignal) {
		return query(uri, projection, selection, selectionArgs, sortOrder);
	}

	public int delete(Uri uri, String selection, String[] selectionArgs) {
		ContentProvider provider = ContentProvider.atl_get_content_provider(uri.getAuthority());
		if (provider != null)
			return provider.delete(uri, selection, selectionArgs);
		else
			return 0;
	}

	public Uri insert(Uri uri, ContentValues values) {
		ContentProvider provider = ContentProvider.atl_get_content_provider(uri.getAuthority());
		if (provider != null)
			return provider.insert(uri, values);
		else
			return null;
	}

	public String getType(Uri uri) {
		ContentProvider provider = ContentProvider.atl_get_content_provider(uri.getAuthority());
		if (provider != null)
			return provider.getType(uri);
		else
			return null;
	}

	public int update(Uri uri, ContentValues values, String selection, String[] selectionArgs) {
		ContentProvider provider = ContentProvider.atl_get_content_provider(uri.getAuthority());
		if (provider != null)
			return provider.update(uri, values, selection, selectionArgs);
		else
			return 0;
	}

	public final Bundle call(Uri uri, String method, String arg, Bundle extras) {
		return call(uri.getAuthority(), method, arg, extras);
	}

	public final Bundle call(String authority, String method, String arg, Bundle extras) {
		ContentProvider provider = ContentProvider.atl_get_content_provider(authority);
		if (provider == null)
			throw new IllegalArgumentException("Unknown authority " + authority);
		return provider.call(authority, method, arg, extras);
	}

	public ContentProviderResult[] applyBatch(String authority, ArrayList<ContentProviderOperation> operations) throws OperationApplicationException {
		ContentProvider provider = ContentProvider.atl_get_content_provider(authority);
		if (provider == null)
			throw new IllegalArgumentException("Unknown authority " + authority);
		return provider.applyBatch(authority, operations);
	}

	public List getPersistedUriPermissions() {
		return Collections.emptyList();
	}

	public final ContentProviderClient acquireContentProviderClient(Uri uri) {
		return acquireContentProviderClient(uri.getAuthority());
	}

	public final ContentProviderClient acquireContentProviderClient(String name) {
		ContentProvider provider = ContentProvider.atl_get_content_provider(name);
		return provider != null ? new ContentProviderClient(provider) : null;
	}

	public final ContentProviderClient acquireUnstableContentProviderClient(Uri uri) {
		return acquireContentProviderClient(uri);
	}

	public final ContentProviderClient acquireUnstableContentProviderClient(String name) {
		return acquireContentProviderClient(name);
	}

	public static void requestSync(final Account account, final String authority, final Bundle extras) {
		// bind the app's sync adapter service and run the sync on a separate thread, like the SyncManager would
		ATLLoadedApp app = ATLLoadedApp.getPrimaryApplication();
		Intent intent = new Intent("android.content.SyncAdapter");
		intent.setPackage(app.pkg.packageName);
		ComponentName component = app.startOrBindService(intent, new ServiceConnection() {
			@Override
			public void onServiceConnected(ComponentName name, IBinder service) {
				if (!(service instanceof AbstractThreadedSyncAdapter.SyncAdapterBinder)) {
					Log.w("ContentResolver", "requestSync: sync adapter service returned " + service);
					return;
				}
				final AbstractThreadedSyncAdapter adapter = ((AbstractThreadedSyncAdapter.SyncAdapterBinder)service).getSyncAdapter();
				new Thread(new Runnable() {
					@Override
					public void run() {
						ContentProvider provider = ContentProvider.atl_get_content_provider(authority);
						SyncResult syncResult = new SyncResult();
						try {
							adapter.onPerformSync(account, extras != null ? extras : new Bundle(), authority,
							                      provider != null ? new ContentProviderClient(provider) : null, syncResult);
							Log.i("ContentResolver", "requestSync: sync for " + authority + " finished: " + syncResult);
						} catch (Throwable t) {
							Log.e("ContentResolver", "requestSync: sync for " + authority + " failed", t);
						}
					}
				}, "SyncAdapterThread").start();
			}
		});
		if (component == null)
			Log.w("ContentResolver", "requestSync: no sync adapter service found in " + app.pkg.packageName);
	}

	public static void cancelSync(Account account, String authority) {
	}

	public static void setMasterSyncAutomatically(boolean sync) {
	}

	public static boolean getMasterSyncAutomatically() {
		return true;
	}

	public static int getIsSyncable(Account account, String authority) {
		return 1;
	}

	public static boolean getSyncAutomatically(Account account, String authority) {
		return false;
	}

	public static void setSyncAutomatically(Account account, String authority, boolean sync) {
	}

	public static void addPeriodicSync(Account account, String authority, Bundle extras, long pollFrequency) {
	}

	public static void setIsSyncable(Account account, String authority, int syncable) {
	}

	public static boolean isSyncActive(Account account, String authority) {
		return false;
	}

	private static native void native_query_file_info(String path, String[] attributes, Object[] result);
}
