package android.graphics;

/* Native shaders: android_graphics_Shader.c (GSK gradient/texture nodes),
 * drawn by every Canvas draw call whose Paint has the shader. */
public class Shader {
	public enum TileMode {
		CLAMP,
		MIRROR,
		REPEAT
	}

	/** native struct AndroidShader (a reference owned by this object), 0 = none */
	long native_shader;
	private Matrix localMatrix;

	protected void init(long ni) {
		if (native_shader != 0)
			native_unref(native_shader);
		native_shader = ni;
		if (localMatrix != null)
			native_set_local_matrix(native_shader, localMatrix.native_instance);
	}

	public boolean getLocalMatrix(Matrix localM) {
		if (localMatrix == null || localMatrix.isIdentity())
			return false;
		localM.set(localMatrix);
		return true;
	}

	public void setLocalMatrix(Matrix matrix) {
		localMatrix = (matrix == null || matrix.isIdentity()) ? null : new Matrix(matrix);
		if (native_shader != 0)
			native_set_local_matrix(native_shader, localMatrix != null ? localMatrix.native_instance : 0);
	}

	protected void copyLocalMatrix(Shader dest) {
		dest.setLocalMatrix(localMatrix);
	}

	/** android ColorLong -> ARGB int (sRGB colors exactly; other color spaces by their components) */
	static int colorLongToArgb(long color) {
		if ((color & 0x3f) == 0)
			return (int)(color >> 32);
		float r = halfToFloat((short)((color >> 48) & 0xffff));
		float g = halfToFloat((short)((color >> 32) & 0xffff));
		float b = halfToFloat((short)((color >> 16) & 0xffff));
		float a = ((color >> 6) & 0x3ff) / 1023.0f;
		return Color.argb(Math.round(a * 255), clamp255(r), clamp255(g), clamp255(b));
	}

	static int[] colorLongsToArgb(long[] colors) {
		int[] out = new int[colors.length];
		for (int i = 0; i < colors.length; i++)
			out[i] = colorLongToArgb(colors[i]);
		return out;
	}

	private static int clamp255(float v) {
		return Math.max(0, Math.min(255, Math.round(v * 255)));
	}

	private static float halfToFloat(short h) {
		int bits = h & 0xffff;
		int sign = (bits & 0x8000) << 16;
		int exp = (bits >> 10) & 0x1f;
		int mant = bits & 0x3ff;
		if (exp == 0) // subnormal
			return (sign != 0 ? -1 : 1) * mant / 1024.0f / 16384.0f;
		if (exp == 31)
			return Float.intBitsToFloat(sign | 0x7f800000 | (mant << 13));
		return Float.intBitsToFloat(sign | ((exp + 112) << 23) | (mant << 13));
	}

	@Override
	protected void finalize() throws Throwable {
		try {
			if (native_shader != 0)
				native_unref(native_shader);
			native_shader = 0;
		} finally {
			super.finalize();
		}
	}

	private static native void native_unref(long shader);
	private static native void native_set_local_matrix(long shader, long matrix);
}
