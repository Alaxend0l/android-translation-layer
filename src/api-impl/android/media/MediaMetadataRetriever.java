package android.media;

import android.atl.ATLMediaStore;
import android.content.Context;
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.ParcelFileDescriptor;
import java.io.FileDescriptor;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.Map;

/* Tags and stream info via libavformat (ATLMediaStore.probe). Frame extraction
 * (getFrameAtTime) is not implemented and returns null, as Android does when
 * no frame can be retrieved. */
public class MediaMetadataRetriever implements AutoCloseable {
	public static final int METADATA_KEY_CD_TRACK_NUMBER = 0;
	public static final int METADATA_KEY_ALBUM = 1;
	public static final int METADATA_KEY_ARTIST = 2;
	public static final int METADATA_KEY_AUTHOR = 3;
	public static final int METADATA_KEY_COMPOSER = 4;
	public static final int METADATA_KEY_DATE = 5;
	public static final int METADATA_KEY_GENRE = 6;
	public static final int METADATA_KEY_TITLE = 7;
	public static final int METADATA_KEY_YEAR = 8;
	public static final int METADATA_KEY_DURATION = 9;
	public static final int METADATA_KEY_NUM_TRACKS = 10;
	public static final int METADATA_KEY_WRITER = 11;
	public static final int METADATA_KEY_MIMETYPE = 12;
	public static final int METADATA_KEY_ALBUMARTIST = 13;
	public static final int METADATA_KEY_DISC_NUMBER = 14;
	public static final int METADATA_KEY_COMPILATION = 15;
	public static final int METADATA_KEY_HAS_AUDIO = 16;
	public static final int METADATA_KEY_HAS_VIDEO = 17;
	public static final int METADATA_KEY_VIDEO_WIDTH = 18;
	public static final int METADATA_KEY_VIDEO_HEIGHT = 19;
	public static final int METADATA_KEY_BITRATE = 20;
	public static final int METADATA_KEY_SAMPLERATE = 38;

	public static final int OPTION_PREVIOUS_SYNC = 0;
	public static final int OPTION_NEXT_SYNC = 1;
	public static final int OPTION_CLOSEST_SYNC = 2;
	public static final int OPTION_CLOSEST = 3;

	private String path;
	private String[] tags;
	private ParcelFileDescriptor pfd; // keeps a content:// source open while we read it

	public void setDataSource(String path) {
		if (path == null)
			throw new IllegalArgumentException("null path");
		if (path.startsWith("file://"))
			path = Uri.parse(path).getPath();
		load(path);
	}

	public void setDataSource(String uri, Map<String, String> headers) {
		setDataSource(uri);
	}

	public void setDataSource(Context context, Uri uri) {
		if (uri == null)
			throw new IllegalArgumentException("null uri");
		if (uri.getScheme() == null || "file".equals(uri.getScheme())) {
			load(uri.getPath());
			return;
		}
		try {
			pfd = context.getContentResolver().openFileDescriptor(uri, "r");
		} catch (FileNotFoundException e) {
			throw new IllegalArgumentException(e);
		}
		if (pfd == null)
			throw new IllegalArgumentException("can't open " + uri);
		load("/proc/self/fd/" + pfd.getFd());
	}

	public void setDataSource(FileDescriptor fd) {
		setDataSource(fd, 0, 0x7ffffffffffffffL);
	}

	public void setDataSource(FileDescriptor fd, long offset, long length) {
		if (offset != 0)
			throw new IllegalArgumentException("FileDescriptor with an offset is not supported");
		load("/proc/self/fd/" + fd.getInt$());
	}

	private void load(String path) {
		this.path = path;
		this.tags = ATLMediaStore.probe(path);
		if (tags == null)
			throw new IllegalArgumentException("can't read media from " + path);
	}

	public String extractMetadata(int key) {
		if (tags == null)
			throw new IllegalStateException("no data source");
		switch (key) {
			case METADATA_KEY_TITLE: return tag(0);
			case METADATA_KEY_ARTIST: return tag(1);
			case METADATA_KEY_ALBUM: return tag(2);
			case METADATA_KEY_ALBUMARTIST: return tag(3);
			case METADATA_KEY_GENRE: return tag(4);
			case METADATA_KEY_CD_TRACK_NUMBER: return tag(5);
			case METADATA_KEY_DISC_NUMBER: return tag(6);
			case METADATA_KEY_DATE: return tag(7);
			case METADATA_KEY_YEAR: {
				String d = tag(7);
				return d != null && d.length() >= 4 && d.substring(0, 4).matches("\\d{4}") ? d.substring(0, 4) : null;
			}
			case METADATA_KEY_COMPOSER: return tag(8);
			case METADATA_KEY_DURATION: return tag(9);
			case METADATA_KEY_VIDEO_WIDTH: return "1".equals(tag(14)) ? tag(10) : null;
			case METADATA_KEY_VIDEO_HEIGHT: return "1".equals(tag(14)) ? tag(11) : null;
			case METADATA_KEY_HAS_AUDIO: return "1".equals(tag(13)) ? "yes" : null;
			case METADATA_KEY_HAS_VIDEO: return "1".equals(tag(14)) ? "yes" : null;
			case METADATA_KEY_BITRATE: return tag(15);
			case METADATA_KEY_SAMPLERATE: return tag(16);
			case METADATA_KEY_MIMETYPE: return ATLMediaStore.mimeForName(path);
			default: return null;
		}
	}

	private String tag(int i) {
		return i < tags.length && !tags[i].isEmpty() ? tags[i] : null;
	}

	public byte[] getEmbeddedPicture() {
		if (path == null)
			throw new IllegalStateException("no data source");
		return ATLMediaStore.embeddedPicture(path);
	}

	public Bitmap getFrameAtTime(long timeUs, int option) {
		return null;
	}

	public Bitmap getFrameAtTime(long timeUs) {
		return null;
	}

	public Bitmap getFrameAtTime() {
		return null;
	}

	public Bitmap getScaledFrameAtTime(long timeUs, int option, int dstWidth, int dstHeight) {
		return null;
	}

	public void release() throws IOException {
		path = null;
		tags = null;
		if (pfd != null) {
			pfd.close();
			pfd = null;
		}
	}

	@Override
	public void close() throws IOException {
		release();
	}
}
