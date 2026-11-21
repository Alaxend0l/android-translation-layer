package android.atl;

import android.content.ContentValues;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.net.Uri;
import android.os.Environment;
import android.util.Slog;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * The media library behind content://media (see doc/MediaLibrary.md).
 *
 * Indexes the user's XDG music, pictures, videos and download directories plus
 * the app's external storage into an SQLite database with MediaStore's column
 * names, so apps' queries (including their SQL selections and sort orders) run
 * against it unchanged. Tags come from libavformat. The database lives in the
 * user's cache directory, one per app storage, and only files whose size or
 * modification time changed are probed again on a rescan.
 */
public final class ATLMediaStore {
	private static final String TAG = "ATLMediaStore";
	private static final int SCHEMA_VERSION = 2;
	/** a query rescans (stat only, unchanged files aren't probed) when the index is older than this */
	private static final long RESCAN_AFTER_MS = 10_000;
	public static final String UNKNOWN = "<unknown>"; // MediaStore.UNKNOWN_STRING

	public static final int MEDIA_TYPE_NONE = 0, MEDIA_TYPE_IMAGE = 1, MEDIA_TYPE_AUDIO = 2, MEDIA_TYPE_VIDEO = 3;

	private static ATLMediaStore instance;

	private final SQLiteDatabase db;
	private final List<File> roots;
	private final File home;
	private final File cacheDir;
	private long lastScan = 0;

	public static synchronized ATLMediaStore get() {
		if (instance == null)
			instance = new ATLMediaStore();
		return instance;
	}

	private ATLMediaStore() {
		String[] dirs = split(native_user_dirs());
		home = new File(dirs[4]);
		File appStorage = Environment.getExternalStorageDirectory();
		roots = computeRoots(dirs, appStorage);
		cacheDir = new File(dirs[5], "android_translation_layer/media-" + Long.toHexString(hash64(appStorage.getAbsolutePath()) & 0xffffffffL));
		cacheDir.mkdirs();
		db = SQLiteDatabase.openOrCreateDatabase(new File(cacheDir, "index.db").getPath(), null);
		createSchema();
		Slog.i(TAG, "media library roots: " + roots + ", index: " + cacheDir);
	}

	/* ---- roots ---- */

	private static List<File> computeRoots(String[] userDirs, File appStorage) {
		List<String> candidates = new ArrayList<>();
		String env = System.getenv("ATL_MEDIA_DIRS");
		if (env != null) {
			// explicit list (':'-separated); empty = no media library
			for (String d : env.split(":"))
				if (!d.isEmpty())
					candidates.add(d);
		} else {
			for (int i = 0; i < 4; i++)
				if (!userDirs[i].isEmpty())
					candidates.add(userDirs[i]);
			candidates.add(appStorage.getAbsolutePath());
		}
		List<File> canon = new ArrayList<>();
		for (String c : candidates) {
			File f = new File(c);
			try {
				f = f.getCanonicalFile();
			} catch (IOException e) {
				continue;
			}
			if (f.isDirectory() && !canon.contains(f))
				canon.add(f);
		}
		// drop roots inside other roots (they're walked anyway)
		List<File> out = new ArrayList<>();
		for (File f : canon) {
			boolean nested = false;
			for (File g : canon)
				if (g != f && f.getPath().startsWith(g.getPath() + File.separator))
					nested = true;
			if (!nested)
				out.add(f);
		}
		return out;
	}

	/* ---- schema ---- */

	private void createSchema() {
		if (db.getVersion() != SCHEMA_VERSION) {
			db.execSQL("DROP VIEW IF EXISTS audio_media");
			db.execSQL("DROP VIEW IF EXISTS images_media");
			db.execSQL("DROP VIEW IF EXISTS video_media");
			db.execSQL("DROP VIEW IF EXISTS audio_albums");
			db.execSQL("DROP VIEW IF EXISTS audio_artists");
			db.execSQL("DROP VIEW IF EXISTS audio_genres");
			db.execSQL("DROP VIEW IF EXISTS visible_files");
			db.execSQL("DROP TABLE IF EXISTS files");
			db.execSQL("DROP TABLE IF EXISTS thumbnails");
		}
		db.execSQL("CREATE TABLE IF NOT EXISTS files ("
			+ "_id INTEGER PRIMARY KEY, _data TEXT UNIQUE NOT NULL, _display_name TEXT, _size INTEGER, mime_type TEXT, "
			+ "media_type INTEGER NOT NULL DEFAULT 0, title TEXT, title_key TEXT, artist TEXT, artist_key TEXT, album TEXT, album_key TEXT, "
			+ "album_artist TEXT, composer TEXT, author TEXT, genre TEXT, track INTEGER, cd_track_number TEXT, disc_number TEXT, "
			+ "year INTEGER, duration INTEGER, bitrate INTEGER, date_added INTEGER, date_modified INTEGER, datetaken INTEGER, "
			+ "width INTEGER, height INTEGER, resolution TEXT, orientation INTEGER DEFAULT 0, "
			+ "bucket_id INTEGER, bucket_display_name TEXT, relative_path TEXT, volume_name TEXT DEFAULT 'external_primary', "
			+ "artist_id INTEGER, album_id INTEGER, genre_id INTEGER, "
			+ "is_music INTEGER DEFAULT 0, is_podcast INTEGER DEFAULT 0, is_ringtone INTEGER DEFAULT 0, is_alarm INTEGER DEFAULT 0, "
			+ "is_notification INTEGER DEFAULT 0, is_audiobook INTEGER DEFAULT 0, is_recording INTEGER DEFAULT 0, "
			+ "is_pending INTEGER DEFAULT 0, is_trashed INTEGER DEFAULT 0, is_favorite INTEGER DEFAULT 0, is_download INTEGER DEFAULT 0, "
			+ "is_drm INTEGER DEFAULT 0, owner_package_name TEXT, parent INTEGER, format INTEGER DEFAULT 0, "
			+ "description TEXT, bookmark INTEGER, has_picture INTEGER DEFAULT 0, "
			+ "_scan_size INTEGER, _scan_mtime INTEGER)");
		// ATL doesn't generate thumbnails: the table is empty and apps decode the full item
		db.execSQL("CREATE TABLE IF NOT EXISTS thumbnails (_id INTEGER PRIMARY KEY, _data TEXT, image_id INTEGER, video_id INTEGER, "
			+ "kind INTEGER, width INTEGER, height INTEGER)");
		// Android hides pending and trashed items from ordinary queries
		db.execSQL("CREATE VIEW IF NOT EXISTS visible_files AS SELECT * FROM files WHERE is_pending=0 AND is_trashed=0");
		db.execSQL("CREATE VIEW IF NOT EXISTS audio_media AS SELECT *, _id AS audio_id FROM visible_files WHERE media_type=2");
		db.execSQL("CREATE VIEW IF NOT EXISTS images_media AS SELECT * FROM visible_files WHERE media_type=1");
		db.execSQL("CREATE VIEW IF NOT EXISTS video_media AS SELECT * FROM visible_files WHERE media_type=3");
		db.execSQL("CREATE VIEW IF NOT EXISTS audio_albums AS SELECT album_id AS _id, album_id, album, album_key, "
			+ "COALESCE(MAX(album_artist), MAX(artist)) AS artist, MIN(artist_id) AS artist_id, "
			+ "COUNT(*) AS numsongs, COUNT(*) AS numsongs_by_artist, MIN(NULLIF(year, 0)) AS minyear, MAX(NULLIF(year, 0)) AS maxyear, "
			+ "NULL AS album_art, MAX(relative_path) AS relative_path, MAX(bucket_display_name) AS bucket_display_name "
			+ "FROM audio_media WHERE is_music=1 GROUP BY album_id");
		db.execSQL("CREATE VIEW IF NOT EXISTS audio_artists AS SELECT artist_id AS _id, artist, artist_key, "
			+ "COUNT(*) AS number_of_tracks, COUNT(DISTINCT album_id) AS number_of_albums "
			+ "FROM audio_media WHERE is_music=1 GROUP BY artist_id");
		db.execSQL("CREATE VIEW IF NOT EXISTS audio_genres AS SELECT genre_id AS _id, genre AS name "
			+ "FROM audio_media WHERE genre IS NOT NULL AND genre != '' GROUP BY genre_id");
		db.setVersion(SCHEMA_VERSION);
	}

	/* ---- scanning ---- */

	/** Rescan before the next query (after an app wrote or deleted a file). */
	public synchronized void invalidate() {
		lastScan = 0;
	}

	public synchronized void rescanIfStale() {
		long now = System.currentTimeMillis();
		if (now - lastScan < RESCAN_AFTER_MS)
			return;
		scan();
		lastScan = System.currentTimeMillis();
	}

	private void scan() {
		long t0 = System.currentTimeMillis();
		Map<String, long[]> known = new HashMap<>();
		try (Cursor c = db.rawQuery("SELECT _data, _scan_size, _scan_mtime FROM files WHERE is_pending=0", null)) {
			while (c.moveToNext())
				known.put(c.getString(0), new long[] {c.getLong(1), c.getLong(2)});
		}
		HashSet<String> seen = new HashSet<>();
		int probed = 0;
		db.beginTransaction();
		try {
			for (File root : roots)
				probed += walk(root, root, 0, known, seen);
			for (String path : known.keySet())
				if (!seen.contains(path))
					db.delete("files", "_data=? AND is_pending=0", new String[] {path});
			db.setTransactionSuccessful();
		} finally {
			db.endTransaction();
		}
		Slog.i(TAG, "scan: " + seen.size() + " media files, " + probed + " probed, " + (System.currentTimeMillis() - t0) + " ms");
	}

	private int walk(File root, File dir, int depth, Map<String, long[]> known, HashSet<String> seen) {
		if (depth > 32 || new File(dir, ".nomedia").exists())
			return 0;
		File[] list = dir.listFiles();
		if (list == null)
			return 0;
		int probed = 0;
		for (File f : list) {
			// hidden files and dirs, incl. .DS_Store and macOS "._name" AppleDouble files
			if (f.getName().startsWith("."))
				continue;
			if (f.isDirectory()) {
				probed += walk(root, f, depth + 1, known, seen);
				continue;
			}
			String mime = mimeForName(f.getName());
			if (mime == null)
				continue;
			String path = f.getAbsolutePath();
			long size = f.length(), mtime = f.lastModified();
			seen.add(path);
			long[] k = known.get(path);
			if (k != null && k[0] == size && k[1] == mtime)
				continue;
			db.insertWithOnConflict("files", null, describe(root, f, mime, size, mtime), SQLiteDatabase.CONFLICT_REPLACE);
			probed++;
		}
		return probed;
	}

	/** A row for one file, from its path and tags (as Android's MediaProvider fills it). */
	private ContentValues describe(File root, File f, String mime, long size, long mtime) {
		String path = f.getAbsolutePath();
		String name = f.getName();
		int mediaType = mediaTypeFor(mime);
		File parent = f.getParentFile();
		String[] t = mediaType == MEDIA_TYPE_NONE ? null : probe(path);

		ContentValues v = new ContentValues();
		v.put("_id", idFor(path));
		v.put("_data", path);
		v.put("_display_name", name);
		v.put("_size", size);
		v.put("mime_type", mime);
		v.put("media_type", mediaType);
		v.put("date_added", mtime / 1000);
		v.put("date_modified", mtime / 1000);
		v.put("bucket_id", parent.getAbsolutePath().toLowerCase(Locale.ROOT).hashCode());
		v.put("bucket_display_name", parent.getName());
		v.put("relative_path", relativePath(root, parent));
		v.put("parent", idFor(parent.getAbsolutePath()));
		v.put("is_download", relativePath(root, parent).startsWith("Download/") ? 1 : 0);
		v.put("_scan_size", size);
		v.put("_scan_mtime", mtime);

		String title = t != null && !t[0].isEmpty() ? t[0] : stripExtension(name);
		v.put("title", title);
		v.put("title_key", key(title));
		if (t != null) {
			if (!t[9].isEmpty())
				v.put("duration", Long.parseLong(t[9]));
			if (!t[10].isEmpty()) {
				v.put("width", Integer.parseInt(t[10]));
				v.put("height", Integer.parseInt(t[11]));
				v.put("resolution", t[10] + "×" + t[11]);
			}
			if (!t[15].isEmpty())
				v.put("bitrate", Long.parseLong(t[15]));
			v.put("has_picture", "1".equals(t[12]) ? 1 : 0);
		}
		if (mediaType == MEDIA_TYPE_AUDIO || mediaType == MEDIA_TYPE_VIDEO) {
			String artist = t != null && !t[1].isEmpty() ? t[1] : UNKNOWN;
			// no album tag: Android uses the folder name
			String album = t != null && !t[2].isEmpty() ? t[2] : parent.getName();
			String albumArtist = t != null && !t[3].isEmpty() ? t[3] : null;
			v.put("artist", artist);
			v.put("artist_key", key(artist));
			v.put("artist_id", idFor("artist\0" + artist.toLowerCase(Locale.ROOT)));
			v.put("album", album);
			v.put("album_key", key(album));
			// albums are grouped by name within a folder, as on Android 11+
			v.put("album_id", idFor("album\0" + album.toLowerCase(Locale.ROOT) + "\0" + parent.getAbsolutePath()));
			v.put("album_artist", albumArtist);
			v.put("composer", t != null && !t[8].isEmpty() ? t[8] : null);
			if (t != null && !t[4].isEmpty()) {
				v.put("genre", t[4]);
				v.put("genre_id", idFor("genre\0" + t[4].toLowerCase(Locale.ROOT)));
			}
			Integer track = t != null ? firstNumber(t[5]) : null;
			Integer disc = t != null ? firstNumber(t[6]) : null;
			if (t != null && !t[5].isEmpty())
				v.put("cd_track_number", t[5]);
			if (t != null && !t[6].isEmpty())
				v.put("disc_number", t[6]);
			// MediaProvider: track = disc * 1000 + track
			if (track != null)
				v.put("track", (disc != null ? disc * 1000 : 0) + track % 1000);
			Integer year = t != null ? firstNumber(t[7]) : null;
			if (year != null && year > 999)
				v.put("year", year);
			v.put("is_music", mediaType == MEDIA_TYPE_AUDIO ? 1 : 0);
		}
		return v;
	}

	/* ---- queries ---- */

	/** What a content://media URI selects: a table or view plus a filter. */
	public static final class Target {
		public final String table;
		public final String where;
		public final String[] args;
		public final String kind; // dir mime type suffix: audio, image, video, file, ...
		public final boolean item;

		Target(String table, String where, String[] args, String kind, boolean item) {
			this.table = table;
			this.where = where;
			this.args = args;
			this.kind = kind;
			this.item = item;
		}
	}

	/** content://media/<volume>/<collection>[/<id>[/...]]; null if not a media URI we know. */
	public static Target route(Uri uri) {
		List<String> s = uri.getPathSegments();
		if (s.size() < 2)
			return null;
		String volume = s.get(0);
		boolean internal = "internal".equals(volume);
		// the internal volume (system ringtones etc.) is empty here
		String none = internal ? "0" : "1";
		String c = s.get(1);
		if ("file".equals(c) || "files".equals(c))
			return item("visible_files", none, s, 2, "file");
		if ("downloads".equals(c))
			return item("visible_files", none + " AND is_download=1", s, 2, "file");
		if (s.size() < 3)
			return null;
		String sub = s.get(2);
		switch (c) {
			case "images":
				if ("thumbnails".equals(sub))
					return item("thumbnails", none, s, 3, "image_thumb");
				return "media".equals(sub) ? item("images_media", none, s, 3, "image") : null;
			case "video":
				if ("thumbnails".equals(sub))
					return item("thumbnails", none, s, 3, "video_thumb");
				return "media".equals(sub) ? item("video_media", none, s, 3, "video") : null;
			case "audio":
				switch (sub) {
					case "media":
						if (s.size() == 6 && "genres".equals(s.get(4)))
							return new Target("audio_genres", none + " AND _id=(SELECT genre_id FROM audio_media WHERE _id=?)", new String[] {s.get(3)}, "audio_genre", false);
						return item("audio_media", none, s, 3, "audio");
					case "albums":
						return item("audio_albums", none, s, 3, "audio_album");
					case "artists":
						if (s.size() == 5 && "albums".equals(s.get(4)))
							return new Target("audio_albums", none + " AND _id IN (SELECT album_id FROM audio_media WHERE artist_id=?)", new String[] {s.get(3)}, "audio_album", false);
						return item("audio_artists", none, s, 3, "audio_artist");
					case "genres":
						if (s.size() == 5 && "members".equals(s.get(4)))
							return new Target("audio_media", none + " AND genre_id=?", new String[] {s.get(3)}, "audio", false);
						return item("audio_genres", none, s, 3, "audio_genre");
					default:
						return null;
				}
			default:
				return null;
		}
	}

	private static Target item(String table, String where, List<String> s, int idIndex, String kind) {
		if (s.size() > idIndex)
			return new Target(table, where + " AND _id=?", new String[] {s.get(idIndex)}, kind, true);
		return new Target(table, where, new String[0], kind, false);
	}

	public Cursor query(Target t, String[] projection, String selection, String[] selectionArgs, String sortOrder, String limit) {
		rescanIfStale();
		StringBuilder sql = new StringBuilder("SELECT ");
		sql.append(projection == null || projection.length == 0 ? "*" : String.join(", ", projection));
		sql.append(" FROM ").append(t.table).append(" WHERE (").append(t.where).append(')');
		if (selection != null && !selection.trim().isEmpty())
			sql.append(" AND (").append(selection).append(')');
		if (sortOrder != null && !sortOrder.trim().isEmpty())
			sql.append(" ORDER BY ").append(sortOrder);
		if (limit != null && !limit.trim().isEmpty())
			sql.append(" LIMIT ").append(limit);
		String[] args = new String[t.args.length + (selectionArgs == null ? 0 : selectionArgs.length)];
		System.arraycopy(t.args, 0, args, 0, t.args.length);
		if (selectionArgs != null)
			System.arraycopy(selectionArgs, 0, args, t.args.length, selectionArgs.length);
		return db.rawQuery(sql.toString(), args);
	}

	/** The file behind an item URI (any visible or pending row), or null. */
	public synchronized File fileFor(Target t) {
		if (t == null || !t.item)
			return null;
		rescanIfStale();
		try (Cursor c = db.rawQuery("SELECT _data FROM files WHERE _id=?", t.args)) {
			return c.moveToFirst() ? new File(c.getString(0)) : null;
		}
	}

	public String mimeFor(Target t) {
		if (t == null)
			return null;
		if (!t.item)
			return "vnd.android.cursor.dir/" + t.kind;
		try (Cursor c = db.rawQuery("SELECT mime_type FROM files WHERE _id=?", t.args)) {
			return c.moveToFirst() ? c.getString(0) : null;
		}
	}

	/** Album art: an image in the album's folder (cover.jpg, folder.jpg, ...), else the first embedded picture. */
	public File albumArt(long albumId) {
		rescanIfStale();
		List<String> tracks = new ArrayList<>();
		try (Cursor c = db.rawQuery("SELECT _data FROM audio_media WHERE album_id=? ORDER BY track", new String[] {Long.toString(albumId)})) {
			while (c.moveToNext())
				tracks.add(c.getString(0));
		}
		if (tracks.isEmpty())
			return null;
		File folder = new File(tracks.get(0)).getParentFile();
		File[] images = folder.listFiles();
		File single = null;
		int imageCount = 0;
		if (images != null) {
			for (String want : new String[] {"cover", "folder", "front", "album", "albumart"})
				for (File f : images)
					if (isImage(f) && stripExtension(f.getName()).equalsIgnoreCase(want))
						return f;
			for (File f : images)
				if (isImage(f)) {
					single = f;
					imageCount++;
				}
		}
		if (imageCount == 1)
			return single;
		File cached = new File(cacheDir, "albumart-" + albumId);
		if (cached.exists())
			return cached;
		for (String track : tracks) {
			byte[] pic = embeddedPicture(track);
			if (pic != null) {
				try (FileOutputStream out = new FileOutputStream(cached)) {
					out.write(pic);
					return cached;
				} catch (IOException e) {
					Slog.w(TAG, "can't cache album art: " + e);
					return null;
				}
			}
		}
		return null;
	}

	/* ---- writes from apps ---- */

	/** MediaStore insert: create the (empty) file the app will write, as Android does. */
	public synchronized Uri insert(Uri collection, ContentValues values) throws IOException {
		Target t = route(collection);
		if (t == null || t.item)
			throw new IllegalArgumentException("Unsupported URI for insert: " + collection);
		String name = values.getAsString("_display_name");
		if (name == null)
			name = "file-" + System.currentTimeMillis();
		String rel = values.getAsString("relative_path");
		File dir;
		if (rel != null) {
			dir = new File(home, rel);
		} else {
			String std = "image".equals(t.kind) ? Environment.DIRECTORY_PICTURES
			           : "video".equals(t.kind) ? Environment.DIRECTORY_MOVIES
			           : t.kind.startsWith("audio") ? Environment.DIRECTORY_MUSIC
			                                         : Environment.DIRECTORY_DOWNLOADS;
			dir = new File(home, std);
		}
		dir.mkdirs();
		File f = new File(dir, name);
		for (int n = 1; f.exists(); n++)
			f = new File(dir, stripExtension(name) + " (" + n + ")" + name.substring(stripExtension(name).length()));
		if (!f.createNewFile())
			throw new IOException("can't create " + f);
		String mime = values.getAsString("mime_type");
		if (mime == null)
			mime = mimeForName(f.getName());
		File root = rootFor(f);
		ContentValues v = describe(root != null ? root : home, f, mime == null ? "application/octet-stream" : mime, 0, f.lastModified());
		Integer pending = values.getAsInteger("is_pending");
		v.put("is_pending", pending != null ? pending : 0);
		// pending rows aren't probed again until the app clears is_pending
		db.insertWithOnConflict("files", null, v, SQLiteDatabase.CONFLICT_REPLACE);
		return Uri.withAppendedPath(collection, Long.toString(v.getAsLong("_id")));
	}

	public synchronized int update(Target t, ContentValues values, String selection, String[] selectionArgs) {
		ContentValues v = new ContentValues(values);
		Integer pending = v.getAsInteger("is_pending");
		int n = db.update("files", v, where(t, selection), args(t, selectionArgs));
		if (pending != null && pending == 0)
			invalidate(); // probe the now-written file on the next query
		return n;
	}

	/** Deletes the files themselves, as MediaStore does for an app allowed to. */
	public synchronized int delete(Target t, String selection, String[] selectionArgs) {
		List<String> paths = new ArrayList<>();
		try (Cursor c = db.query("files", new String[] {"_data"}, where(t, selection), args(t, selectionArgs), null, null, null)) {
			while (c.moveToNext())
				paths.add(c.getString(0));
		}
		int n = 0;
		for (String p : paths) {
			File f = new File(p);
			if (!f.exists() || f.delete()) {
				db.delete("files", "_data=?", new String[] {p});
				n++;
			}
		}
		return n;
	}

	private static String where(Target t, String selection) {
		// writes go to the table, so the view's filters are restated by media type
		String w = t.where;
		switch (t.table) {
			case "audio_media": w += " AND media_type=2"; break;
			case "images_media": w += " AND media_type=1"; break;
			case "video_media": w += " AND media_type=3"; break;
			case "visible_files": break;
			default: throw new IllegalArgumentException("can't modify " + t.table);
		}
		return selection == null || selection.trim().isEmpty() ? w : "(" + w + ") AND (" + selection + ")";
	}

	private static String[] args(Target t, String[] selectionArgs) {
		String[] a = new String[t.args.length + (selectionArgs == null ? 0 : selectionArgs.length)];
		System.arraycopy(t.args, 0, a, 0, t.args.length);
		if (selectionArgs != null)
			System.arraycopy(selectionArgs, 0, a, t.args.length, selectionArgs.length);
		return a;
	}

	private File rootFor(File f) {
		for (File r : roots)
			if (f.getAbsolutePath().startsWith(r.getPath() + File.separator))
				return r;
		return null;
	}

	/* ---- helpers ---- */

	/** Tags for a file (fields as documented on native_probe), or null if unreadable. */
	public static String[] probe(String path) {
		byte[] b = native_probe(path.getBytes(StandardCharsets.UTF_8));
		return b == null ? null : split(b);
	}

	public static byte[] embeddedPicture(String path) {
		return native_embedded_picture(path.getBytes(StandardCharsets.UTF_8));
	}

	private static String[] split(byte[] b) {
		List<String> out = new ArrayList<>();
		int start = 0;
		for (int i = 0; i < b.length; i++)
			if (b[i] == 0) {
				out.add(new String(b, start, i - start, StandardCharsets.UTF_8));
				start = i + 1;
			}
		return out.toArray(new String[0]);
	}

	/** Stable across rescans and restarts (playlists and "last played" keep working); never 0. */
	static long idFor(String s) {
		long h = hash64(s) & ((1L << 52) - 1);
		return h == 0 ? 1 : h;
	}

	private static long hash64(String s) {
		long h = 0xcbf29ce484222325L; // FNV-1a
		for (byte b : s.getBytes(StandardCharsets.UTF_8)) {
			h ^= b & 0xff;
			h *= 0x100000001b3L;
		}
		return h;
	}

	private String relativePath(File root, File dir) {
		// relative to $HOME when possible ("Music/Album/"), like Android's relative to /sdcard
		File base = dir.getAbsolutePath().startsWith(home.getAbsolutePath() + File.separator) ? home : root.getParentFile();
		String rel = dir.getAbsolutePath().substring(Math.min(dir.getAbsolutePath().length(), base.getAbsolutePath().length() + 1));
		return rel.isEmpty() ? "" : rel + "/";
	}

	private static String key(String s) {
		return s == null ? null : s.toLowerCase(Locale.ROOT).trim();
	}

	private static Integer firstNumber(String s) {
		if (s == null)
			return null;
		int i = 0;
		while (i < s.length() && !Character.isDigit(s.charAt(i)))
			i++;
		int j = i;
		while (j < s.length() && Character.isDigit(s.charAt(j)) && j - i < 9)
			j++;
		return i == j ? null : Integer.parseInt(s.substring(i, j));
	}

	private static String stripExtension(String name) {
		int dot = name.lastIndexOf('.');
		return dot > 0 ? name.substring(0, dot) : name;
	}

	private static boolean isImage(File f) {
		if (f.getName().startsWith(".")) // .DS_Store, macOS "._name" AppleDouble files
			return false;
		String m = mimeForName(f.getName());
		return m != null && m.startsWith("image/");
	}

	static int mediaTypeFor(String mime) {
		if (mime == null)
			return MEDIA_TYPE_NONE;
		if (mime.startsWith("audio/"))
			return MEDIA_TYPE_AUDIO;
		if (mime.startsWith("image/"))
			return MEDIA_TYPE_IMAGE;
		if (mime.startsWith("video/"))
			return MEDIA_TYPE_VIDEO;
		return MEDIA_TYPE_NONE;
	}

	private static final Map<String, String> MIME = new HashMap<>();
	static {
		String[][] m = {
			{"mp3", "audio/mpeg"}, {"flac", "audio/flac"}, {"ogg", "audio/ogg"}, {"oga", "audio/ogg"},
			{"opus", "audio/ogg"}, {"m4a", "audio/mp4"}, {"m4b", "audio/mp4"}, {"aac", "audio/aac"},
			{"wav", "audio/x-wav"}, {"wma", "audio/x-ms-wma"}, {"mka", "audio/x-matroska"},
			{"amr", "audio/amr"}, {"mid", "audio/midi"}, {"midi", "audio/midi"},
			{"jpg", "image/jpeg"}, {"jpeg", "image/jpeg"}, {"png", "image/png"}, {"gif", "image/gif"},
			{"webp", "image/webp"}, {"bmp", "image/bmp"}, {"heic", "image/heic"}, {"heif", "image/heif"},
			{"avif", "image/avif"}, {"dng", "image/x-adobe-dng"},
			{"mp4", "video/mp4"}, {"m4v", "video/mp4"}, {"mkv", "video/x-matroska"}, {"webm", "video/webm"},
			{"3gp", "video/3gpp"}, {"mov", "video/quicktime"}, {"avi", "video/avi"}, {"ts", "video/mp2ts"},
		};
		for (String[] e : m)
			MIME.put(e[0], e[1]);
	}

	/** Media MIME type from the file name, or null for files that aren't media. */
	public static String mimeForName(String name) {
		int dot = name.lastIndexOf('.');
		return dot < 0 ? null : MIME.get(name.substring(dot + 1).toLowerCase(Locale.ROOT));
	}

	private static native byte[] native_probe(byte[] path);
	private static native byte[] native_embedded_picture(byte[] path);
	private static native byte[] native_user_dirs();
}
