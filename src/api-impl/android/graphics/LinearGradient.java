package android.graphics;

public class LinearGradient extends Shader {
	public LinearGradient(float x0, float y0, float x1, float y1, int[] colors, float[] positions, TileMode mode) {
		if (colors.length < 2)
			throw new IllegalArgumentException("needs >= 2 number of colors");
		if (positions != null && colors.length != positions.length)
			throw new IllegalArgumentException("color and position arrays must be of equal length");
		init(native_create(x0, y0, x1, y1, colors, positions, mode.ordinal()));
	}

	public LinearGradient(float x0, float y0, float x1, float y1, long[] colors, float[] positions, TileMode tile) {
		this(x0, y0, x1, y1, colorLongsToArgb(colors), positions, tile);
	}

	public LinearGradient(float x0, float y0, float x1, float y1, int color0, int color1, TileMode tile) {
		this(x0, y0, x1, y1, new int[] {color0, color1}, null, tile);
	}

	public LinearGradient(float x0, float y0, float x1, float y1, long color0, long color1, TileMode tile) {
		this(x0, y0, x1, y1, new int[] {colorLongToArgb(color0), colorLongToArgb(color1)}, null, tile);
	}

	private static native long native_create(float x0, float y0, float x1, float y1, int[] colors, float[] positions, int tile);
}
