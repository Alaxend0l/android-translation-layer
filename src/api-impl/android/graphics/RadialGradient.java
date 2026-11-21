package android.graphics;

public class RadialGradient extends Shader {
	public RadialGradient(float centerX, float centerY, float radius, int[] colors, float[] stops, TileMode tileMode) {
		if (radius <= 0)
			throw new IllegalArgumentException("radius must be > 0");
		if (colors.length < 2)
			throw new IllegalArgumentException("needs >= 2 number of colors");
		if (stops != null && colors.length != stops.length)
			throw new IllegalArgumentException("color and position arrays must be of equal length");
		init(native_create(centerX, centerY, radius, colors, stops, tileMode.ordinal()));
	}

	public RadialGradient(float centerX, float centerY, float radius, long[] colors, float[] stops, TileMode tileMode) {
		this(centerX, centerY, radius, colorLongsToArgb(colors), stops, tileMode);
	}

	public RadialGradient(float centerX, float centerY, float radius, int centerColor, int edgeColor, TileMode tileMode) {
		this(centerX, centerY, radius, new int[] {centerColor, edgeColor}, null, tileMode);
	}

	public RadialGradient(float centerX, float centerY, float radius, long centerColor, long edgeColor, TileMode tileMode) {
		this(centerX, centerY, radius, new int[] {colorLongToArgb(centerColor), colorLongToArgb(edgeColor)}, null, tileMode);
	}

	private static native long native_create(float cx, float cy, float radius, int[] colors, float[] positions, int tile);
}
