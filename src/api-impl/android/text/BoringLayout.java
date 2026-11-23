package android.text;

import android.graphics.Paint;

public class BoringLayout extends Layout {

	public BoringLayout(CharSequence source, TextPaint paint, int outerwidth, Layout.Alignment align, float spacingMult, float spacingAdd, BoringLayout.Metrics metrics, boolean includePad) {
		super(source, paint, outerwidth, align, spacingMult, spacingAdd);
	}

	/* AOSP: BoringLayout.Metrics extends Paint.FontMetricsInt (queue
	 * item 119). The fork's was an empty class and both isBoring
	 * variants lied in *opposite* directions: (source,paint) always
	 * returned null ("never boring", safe but disables the fast path)
	 * while (source,paint,metrics) returned its argument unconditionally
	 * ("always boring"), which would lay multi-line text on one line for
	 * apps that trust the answer. */
	public static class Metrics extends Paint.FontMetricsInt {}

	private static boolean isBoringText(CharSequence source) {
		/* AOSP boring = single paragraph with no tabs, no newlines, no
		 * control characters, no bidi reordering. ATL has no bidi/shaper
		 * state to consult, so the character scan is the honest subset;
		 * text the scan calls boring is exactly text ATL could never lay
		 * out differently across lines anyway. */
		for (int i = 0; i < source.length(); i++) {
			char c = source.charAt(i);
			if (c == '\n' || c == '\r' || c == '\t' || c == '\b' || c == '\u0000')
				return false;
		}
		return true;
	}

	public static Metrics isBoring(CharSequence source, TextPaint paint, Metrics metrics) {
		if (!isBoringText(source))
			return null;
		/* AOSP fills the metrics from the paint when it says "boring". */
		paint.getFontMetricsInt(metrics);
		return metrics;
	}

	public static Metrics isBoring(CharSequence source, TextPaint paint) {
		return isBoring(source, paint, new Metrics());
	}
}
