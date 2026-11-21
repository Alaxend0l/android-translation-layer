package android.text.method;

import android.graphics.Rect;
import android.view.View;

/**
 * As in AOSP: a TransformationMethod that replaces each character in
 * getOriginal() with the one at the same index in getReplacement().
 * (AOSP returns a live view over the source; ATL's TextView renders a
 * snapshot, so the transformed copy is built eagerly.)
 */
public abstract class ReplacementTransformationMethod implements TransformationMethod {
	protected abstract char[] getOriginal();
	protected abstract char[] getReplacement();

	@Override
	public CharSequence getTransformation(CharSequence source, View view) {
		if (source == null)
			return null;
		char[] original = getOriginal();
		char[] replacement = getReplacement();
		String s = source.toString();
		boolean found = false;
		for (char c : original) {
			if (s.indexOf(c) >= 0) {
				found = true;
				break;
			}
		}
		if (!found)
			return source;
		char[] out = s.toCharArray();
		for (int i = 0; i < out.length; i++) {
			for (int j = 0; j < original.length; j++) {
				if (out[i] == original[j]) {
					out[i] = replacement[j];
					break;
				}
			}
		}
		return new String(out);
	}

	@Override
	public void onFocusChanged(View view, CharSequence sourceText, boolean focused, int direction, Rect previouslyFocusedRect) {}
}
