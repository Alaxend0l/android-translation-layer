package android.graphics;

/* The bitmap's pixels are taken when the shader is created (Glide and most
 * apps draw the bitmap first, then make the shader). */
public class BitmapShader extends Shader {
	Bitmap bitmap;

	public BitmapShader(Bitmap bitmap, TileMode tileX, TileMode tileY) {
		if (bitmap == null)
			throw new IllegalArgumentException("Bitmap must be non-null");
		this.bitmap = bitmap;
		init(native_create(bitmap.getTexture(), tileX.ordinal(), tileY.ordinal()));
	}

	private static native long native_create(long texture, int tileX, int tileY);
}
