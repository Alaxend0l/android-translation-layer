package android.media;

import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.provider.MediaStore;

/*
 * ATL version of android.media.Ringtone. AOSP keeps the context, the URI and
 * a MediaPlayer handle; getTitle() resolves the track name through the
 * MediaStore ("artist - title") and falls back to the URI display name for
 * non-media URIs. The fork returned a literal "FIXME" string from getTitle,
 * which apps then displayed as a ringtone name (queue item 51);
 * RingtoneManager.getDefaultUri already hands out real Settings URIs.
 */
public class Ringtone {
	private final Context mContext;
	private final Uri mUri;

	/* AOSP's constructor is package-private: Ringtone instances only come
	 * from RingtoneManager.getRingtone(). */
	Ringtone(Context context, Uri uri) {
		mContext = context;
		mUri = uri;
	}

	public String getTitle(Context context) {
		if (mUri == null)
			return null; // AOSP: no URI, no title

		String title = queryColumn(context, MediaStore.Audio.Media.TITLE);
		String artist = queryColumn(context, MediaStore.Audio.Media.ARTIST);
		if (title != null) {
			/* AOSP joins with " - " unless the artist is unknown. */
			if (artist != null && !"<unknown>".equals(artist) && !artist.isEmpty())
				return artist + " - " + title;
			return title;
		}

		/* AOSP's fallback reads OpenableColumns.DISPLAY_NAME; ATL's
		 * ContentResolver may not back the provider the URI points at, so
		 * degrade to the file name in the URI, without the extension. */
		String segment = mUri.getLastPathSegment();
		if (segment == null)
			return mUri.toString();
		int dot = segment.lastIndexOf('.');
		return dot > 0 ? segment.substring(0, dot) : segment;
	}

	private String queryColumn(Context context, String column) {
		try {
			Cursor c = context.getContentResolver().query(mUri, new String[] { column },
			                                              null, null, null);
			if (c == null)
				return null;
			try {
				if (c.moveToFirst()) {
					int idx = c.getColumnIndex(column);
					if (idx >= 0)
						return c.getString(idx);
				}
			} finally {
				c.close();
			}
		} catch (Exception ignored) {
			/* no/failing provider for this URI: fall through to the name */
		}
		return null;
	}
}
