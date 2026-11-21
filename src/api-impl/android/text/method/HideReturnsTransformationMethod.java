package android.text.method;

/**
 * As in AOSP: hides carriage returns (zero-width no-break space). Apps use it
 * to show a password field's contents ("show password" toggles).
 */
public class HideReturnsTransformationMethod extends ReplacementTransformationMethod {
	private static final char[] ORIGINAL = new char[] {'\r'};
	private static final char[] REPLACEMENT = new char[] {'﻿'};
	private static HideReturnsTransformationMethod sInstance;

	protected char[] getOriginal() {
		return ORIGINAL;
	}

	protected char[] getReplacement() {
		return REPLACEMENT;
	}

	public static HideReturnsTransformationMethod getInstance() {
		if (sInstance == null)
			sInstance = new HideReturnsTransformationMethod();
		return sInstance;
	}
}
