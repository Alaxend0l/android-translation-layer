package android.os.storage;

import android.content.Context;
import android.os.Environment;
import android.provider.MediaStore;
import java.io.File;

/**
 * A persistent storage volume that can be read from and written to.
 *
 * <p>ATL presents one volume: the app data directory returned by
 * Environment.getExternalStorageDirectory(), which is always readable and
 * writable (Environment.getExternalStorageState() reports MEDIA_MOUNTED). That
 * is the same shape Android reports for a phone with only emulated internal
 * storage, so the getters below answer with those values.</p>
 */
public class StorageVolume {
	private final File path;

	public StorageVolume() {
		this.path = Environment.getExternalStorageDirectory();
	}

	public boolean isPrimary() { return true; }

	/** The volume is emulated (a directory in the container), like internal storage on Android. */
	public boolean isEmulated() { return true; }

	/** Emulated internal storage is not removable. */
	public boolean isRemovable() { return false; }

	public String getPath() { return path.getPath(); }

	/** {@hide} */
	public File getPathFile() { return path; }

	/**
	 * Returns the directory where this volume is mounted, or null if it is not
	 * mounted. ATL's volume is always mounted.
	 */
	public File getDirectory() { return path; }

	/** Returns MEDIA_MOUNTED: ATL's storage is a plain directory it can always read. */
	public String getState() { return Environment.MEDIA_MOUNTED; }

	/**
	 * Gets the volume UUID. ATL's StorageManager.getUuidForPath() reports
	 * StorageManager.UUID_DEFAULT for every path, so this is the uuid of the
	 * single volume Android would report for it.
	 */
	public String getUuid() { return StorageManager.UUID_DEFAULT.toString(); }

	/** The MediaStore name for this volume, which is the primary external volume. */
	public String getMediaStoreVolumeName() {
		if (isPrimary()) {
			return MediaStore.VOLUME_EXTERNAL_PRIMARY;
		} else {
			return getUuid().toLowerCase(java.util.Locale.US);
		}
	}

	/** {@hide} */
	public String getUserLabel() { return null; }

	public String getDescription(Context context) { return ""; }
}
