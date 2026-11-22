package android.content;

public class ClipDescription /*implements Parcelable*/ {
	public static final String MIMETYPE_TEXT_PLAIN = "text/plain";
	public static final String MIMETYPE_TEXT_HTML = "text/html";
	public static final String MIMETYPE_TEXT_URILIST = "text/uri-list";
	public static final String MIMETYPE_TEXT_INTENT = "text/vnd.android.intent";
	public static final String MIMETYPE_APPLICATION_ACTIVITY = "application/vnd.android.activity";
	public static final String MIMETYPE_APPLICATION_SHORTCUT = "application/vnd.android.shortcut";
	public static final String MIMETYPE_APPLICATION_TASK = "application/vnd.android.task";
	public static final String MIMETYPE_UNKNOWN = "application/octet-stream";

	/**
	 * Check if two MIME types are identical, allowing "*" wildcards in either
	 * the type or subtype position (AOSP ClipDescription.compareMimeTypes).
	 */
	public static boolean compareMimeTypes(String highType, String subtype) {
		// Ensure we don't pass in null MIME type strings to the parser, which
		// could throw
		if (highType == null || subtype == null) {
			return false;
		}
		if (highType.equalsIgnoreCase(subtype)) {
			return true;
		}
		// Check if one of them is some kind of a wildcard type
		if (highType.endsWith("/*")) {
			if (subtype.regionMatches(true, 0, highType, 0, highType.length() - 1)) {
				return true;
			}
		} else if (subtype.endsWith("/*")) {
			if (highType.regionMatches(true, 0, subtype, 0, subtype.length() - 1)) {
				return true;
			}
		}
		return false;
	}
	public static final String EXTRA_PENDING_INTENT = "android.intent.extra.PENDING_INTENT";
	public static final String EXTRA_ACTIVITY_OPTIONS = "android.intent.extra.ACTIVITY_OPTIONS";
	public static final String EXTRA_LOGGING_INSTANCE_ID = "android.intent.extra.LOGGING_INSTANCE_ID";
	public static final String EXTRA_HIDE_DRAG_SOURCE_TASK_ID = "android.intent.extra.HIDE_DRAG_SOURCE_TASK_ID";
	public static final String EXTRA_IS_SENSITIVE = "android.content.extra.IS_SENSITIVE";
	public static final String EXTRA_IS_REMOTE_DEVICE = "android.content.extra.IS_REMOTE_DEVICE";
	public static final int CLASSIFICATION_NOT_COMPLETE = 1;
	public static final int CLASSIFICATION_NOT_PERFORMED = 2;
	public static final int CLASSIFICATION_COMPLETE = 3;

	public ClipDescription(CharSequence label, String[] mimeTypes) {}

	public int getMimeTypeCount() {
		return 0;
	}

	public String getMimeType(int index) {
		return "FIXME";
	}
}
