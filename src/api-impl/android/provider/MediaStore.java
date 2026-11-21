package android.provider;

import android.content.ContentResolver;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.ParcelFileDescriptor;
import java.io.IOException;

public class MediaStore {
	public static final String AUTHORITY = "media";
	public static final String VOLUME_INTERNAL = "internal";
	public static final String VOLUME_EXTERNAL = "external";
	public static final String VOLUME_EXTERNAL_PRIMARY = "external_primary";
	public static final String UNKNOWN_STRING = "<unknown>";

	private static Uri uri(String volume, String path) {
		return Uri.parse("content://media/" + volume + "/" + path);
	}

	private static Uri item(Uri collection, long id) {
		return collection.buildUpon().appendPath(String.valueOf(id)).build();
	}

	public static java.util.Set<String> getExternalVolumeNames(android.content.Context context) {
		return java.util.Collections.singleton(VOLUME_EXTERNAL_PRIMARY);
	}

	public static String getVolumeName(Uri uri) {
		return uri.getPathSegments().get(0);
	}

	public static String getVersion(android.content.Context context) {
		return "atl-1";
	}

	public static String getVersion(android.content.Context context, String volumeName) {
		return getVersion(context);
	}

	public static class Downloads {
		public static final Uri EXTERNAL_CONTENT_URI = uri(VOLUME_EXTERNAL, "downloads");
		public static final Uri INTERNAL_CONTENT_URI = uri(VOLUME_INTERNAL, "downloads");
		public static Uri getContentUri(String volume) {
			return uri(volume, "downloads");
		}
		public static Uri getContentUri(String volume, long id) {
			return item(getContentUri(volume), id);
		}
	}

	public static class Images {

		public static class Media {

			public static final Uri EXTERNAL_CONTENT_URI = Uri.parse("content://media/external/images/media");
			public static final Uri INTERNAL_CONTENT_URI = Uri.parse("content://media/internal/images/media");
			public static Uri getContentUri(String volume) {
				return uri(volume, "images/media");
			}
			public static Uri getContentUri(String volume, long id) {
				return item(getContentUri(volume), id);
			}
		}

		public static class Thumbnails {

			public static final Uri EXTERNAL_CONTENT_URI = Uri.parse("content://media/external/images/thumbnails");

			public static final int MINI_KIND = 1;
			public static final int FULL_SCREEN_KIND = 2;
			public static final int MICRO_KIND = 3;

			/* ATL generates no thumbnails: the thumbnails table is empty (as for an item
			 * whose thumbnail doesn't exist yet), and getThumbnail decodes the image */
			public static Cursor queryMiniThumbnail(ContentResolver contentResolver, long id, int kind, String[] projection) {
				return contentResolver.query(EXTERNAL_CONTENT_URI, projection, "image_id = ? AND kind = ?", new String[] {String.valueOf(id), String.valueOf(kind)}, null);
			}

			public static Cursor queryMiniThumbnails(ContentResolver contentResolver, Uri uri, int kind, String[] projection) {
				return contentResolver.query(uri, projection, "kind = ?", new String[] {String.valueOf(kind)}, null);
			}

			public static Bitmap getThumbnail(ContentResolver contentResolver, long imageId, int kind, BitmapFactory.Options options) {
				return decodeThumbnail(contentResolver, Media.EXTERNAL_CONTENT_URI.buildUpon().appendPath(String.valueOf(imageId)).build(), kind, options);
			}

			public static Bitmap getThumbnail(ContentResolver contentResolver, long imageId, long groupId, int kind, BitmapFactory.Options options) {
				return getThumbnail(contentResolver, imageId, kind, options);
			}

			public static void cancelThumbnailRequest(ContentResolver contentResolver, long origId) {}

			public static void cancelThumbnailRequest(ContentResolver contentResolver, long origId, long groupId) {}
		}
	}

	/** An image decoded at about the size of a thumbnail of `kind`; null if it can't be read. */
	static Bitmap decodeThumbnail(ContentResolver contentResolver, Uri uri, int kind, BitmapFactory.Options options) {
		int target = kind == Images.Thumbnails.MICRO_KIND ? 96 : kind == Images.Thumbnails.FULL_SCREEN_KIND ? 1024 : 512;
		try {
			BitmapFactory.Options bounds = new BitmapFactory.Options();
			bounds.inJustDecodeBounds = true;
			try (ParcelFileDescriptor fd = contentResolver.openFileDescriptor(uri, "r")) {
				if (fd == null)
					return null;
				BitmapFactory.decodeFileDescriptor(fd.getFileDescriptor(), null, bounds);
			}
			BitmapFactory.Options opts = options != null ? options : new BitmapFactory.Options();
			int sample = 1;
			while (bounds.outWidth / (sample * 2) >= target && bounds.outHeight / (sample * 2) >= target)
				sample *= 2;
			opts.inSampleSize = Math.max(opts.inSampleSize, sample);
			try (ParcelFileDescriptor fd = contentResolver.openFileDescriptor(uri, "r")) {
				return fd == null ? null : BitmapFactory.decodeFileDescriptor(fd.getFileDescriptor(), null, opts);
			}
		} catch (IOException | RuntimeException e) {
			return null;
		}
	}

	public static class Video {

		public static class Media {

			public static final Uri EXTERNAL_CONTENT_URI = Uri.parse("content://media/external/video/media");
			public static final Uri INTERNAL_CONTENT_URI = Uri.parse("content://media/internal/video/media");
			public static Uri getContentUri(String volume) {
				return uri(volume, "video/media");
			}
			public static Uri getContentUri(String volume, long id) {
				return item(getContentUri(volume), id);
			}
		}

		public static class Thumbnails {

			public static final Uri EXTERNAL_CONTENT_URI = Uri.parse("content://media/external/video/thumbnails");

			public static final int MINI_KIND = 1;
			public static final int FULL_SCREEN_KIND = 2;
			public static final int MICRO_KIND = 3;

			/* no video frame extraction in ATL yet: null, as Android returns when
			 * no thumbnail can be made */
			public static Bitmap getThumbnail(ContentResolver contentResolver, long videoId, int kind, BitmapFactory.Options options) {
				return null;
			}

			public static Bitmap getThumbnail(ContentResolver contentResolver, long videoId, long groupId, int kind, BitmapFactory.Options options) {
				return null;
			}

			public static void cancelThumbnailRequest(ContentResolver contentResolver, long origId) {}

			public static void cancelThumbnailRequest(ContentResolver contentResolver, long origId, long groupId) {}
		}
	}

	public static class Audio {

		public static class Media {

			public static final Uri EXTERNAL_CONTENT_URI = Uri.parse("content://media/external/audio/media");
			public static final Uri INTERNAL_CONTENT_URI = Uri.parse("content://media/internal/audio/media");
			public static Uri getContentUri(String volume) {
				return uri(volume, "audio/media");
			}
			public static Uri getContentUri(String volume, long id) {
				return item(getContentUri(volume), id);
			}
			public static Uri getContentUriForPath(String path) {
				return EXTERNAL_CONTENT_URI;
			}
		}

		public static class Artists {
			public static final Uri EXTERNAL_CONTENT_URI = Uri.parse("content://media/external/audio/artists");
			public static final Uri INTERNAL_CONTENT_URI = Uri.parse("content://media/internal/audio/artists");
			public static Uri getContentUri(String volume) {
				return uri(volume, "audio/artists");
			}
			public static class Albums {
				public static Uri getContentUri(String volume, long artistId) {
					return uri(volume, "audio/artists/" + artistId + "/albums");
				}
			}
		}

		public static class Albums {
			public static final Uri EXTERNAL_CONTENT_URI = Uri.parse("content://media/external/audio/albums");
			public static final Uri INTERNAL_CONTENT_URI = Uri.parse("content://media/internal/audio/albums");
			public static Uri getContentUri(String volume) {
				return uri(volume, "audio/albums");
			}
		}

		public static class Genres {
			public static final Uri EXTERNAL_CONTENT_URI = Uri.parse("content://media/external/audio/genres");
			public static final Uri INTERNAL_CONTENT_URI = Uri.parse("content://media/internal/audio/genres");
			public static Uri getContentUri(String volume) {
				return uri(volume, "audio/genres");
			}
			public static Uri getContentUriForAudioId(String volume, int audioId) {
				return uri(volume, "audio/media/" + audioId + "/genres");
			}
			public static class Members {
				public static Uri getContentUri(String volume, long genreId) {
					return uri(volume, "audio/genres/" + genreId + "/members");
				}
			}
		}
	}

	public static class Files {

		public static Uri getContentUri(String volume) {
			return uri(volume, "file");
		}
		public static Uri getContentUri(String volume, long id) {
			return item(getContentUri(volume), id);
		}
	}
}
