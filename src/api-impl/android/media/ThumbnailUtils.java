package android.media;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Rect;

/*
 * ATL version of AOSP's media/java/android/media/ThumbnailUtils
 * (Nougat-era, the shape apps on this classpath expect). The fork's
 * previous version only scaled, stretching the source into the target
 * box (queue item 50: gallery grids showed squashed thumbnails); the
 * AOSP shape scales uniformly and centre-crops to the exact size.
 */
public class ThumbnailUtils {

	private static final int OPTIONS_NONE = 0x0;
	private static final int OPTIONS_SCALE_UP = 0x1;
	public static final int OPTIONS_RECYCLE_INPUT = 0x2;
	private static final int OPTIONS_SCALE_UNIFORM = 0x4;
	private static final int OPTIONS_CENTER_CROP = 0x8;

	public static Bitmap extractThumbnail(Bitmap source, int width, int height) {
		return extractThumbnail(source, width, height, OPTIONS_NONE);
	}

	/**
	 * AOSP's "kindOfCenterCrop" variant, deprecated there since Honeycomb
	 * but still called by apps; when the flag is true the source is scaled
	 * to FILL the target box (max ratio) and the overflow is cropped in
	 * the middle.
	 */
	@Deprecated
	public static Bitmap extractThumbnail(Bitmap source, int width, int height,
	                                      boolean kindOfCenterCrop, int options) {
		if (source == null) {
			return null;
		}

		float scale;
		if (kindOfCenterCrop) {
			// Scale up so the source covers the target, then crop.
			float scaleX = width / (float) source.getWidth();
			float scaleY = height / (float) source.getHeight();
			scale = Math.max(scaleX, scaleY);
		} else {
			if (source.getWidth() < source.getHeight()) {
				scale = width / (float) source.getWidth();
			} else {
				scale = height / (float) source.getHeight();
			}
		}
		Matrix matrix = new Matrix();
		matrix.setScale(scale, scale);
		int transformOptions = kindOfCenterCrop
				? (OPTIONS_SCALE_UP | OPTIONS_CENTER_CROP |
				   ((options & OPTIONS_RECYCLE_INPUT) != 0 ? OPTIONS_RECYCLE_INPUT : 0))
				: (OPTIONS_SCALE_UNIFORM | options);
		Bitmap thumbnail = transform(matrix, source, width, height, transformOptions);
		return thumbnail;
	}

	public static Bitmap extractThumbnail(Bitmap source, int width, int height, int options) {
		if (source == null) {
			return null;
		}

		float scale;
		if (source.getWidth() < source.getHeight()) {
			scale = width / (float) source.getWidth();
		} else {
			scale = height / (float) source.getHeight();
		}
		Matrix matrix = new Matrix();
		matrix.setScale(scale, scale);
		Bitmap thumbnail = transform(matrix, source, width, height,
				OPTIONS_SCALE_UNIFORM | options);
		return thumbnail;
	}

	/**
	 * Transform the bitmap contained in the source bitmap into the target
	 * size. AOSP MediaStore/ThumbnailUtils transform (Nougat), with the
	 * native parts done through the Canvas the ATL snapshot path already
	 * implements.
	 */
	private static Bitmap transform(Matrix scaler, Bitmap source, int targetWidth,
	                                int targetHeight, int options) {
		boolean scaleUp = (options & OPTIONS_SCALE_UP) != 0;
		boolean centerCrop = (options & OPTIONS_CENTER_CROP) != 0;
		boolean recycle = (options & OPTIONS_RECYCLE_INPUT) != 0;

		int iw = source.getWidth();
		int ih = source.getHeight();

		if (scaleUp && iw < targetWidth && ih < targetHeight) {
			// AOSP: leave small sources alone (no scale, no crop).
			options = 0;
			scaleUp = false;
			scaler = null;
		}

		if (scaler != null) {
			Bitmap output = Bitmap.createBitmap(source, 0, 0, iw, ih, scaler, true);
			if (recycle) {
				source.recycle();
			}
			source = output;
		}

		if (options != 0) {
			int deltaW = targetWidth - iw;
			int deltaH = targetHeight - ih;
			Rect src = new Rect();
			Rect dst = new Rect();

			if (deltaW > 0 || deltaH > 0) { // pad: centre the source
				if (deltaW > 0) {
					dst.left = (deltaW / 2);
					dst.right = dst.left + iw;
				} else {
					dst.right = iw;
				}
				if (deltaH > 0) {
					dst.top = (deltaH / 2);
					dst.bottom = dst.top + ih;
				} else {
					dst.bottom = ih;
				}
			} else if (centerCrop) { // crop: take the middle of the source
				if (deltaW < 0) {
					src.left = (-deltaW / 2);
					src.right = src.left + targetWidth;
				} else {
					src.right = iw;
				}
				if (deltaH < 0) {
					src.top = (-deltaH / 2);
					src.bottom = src.top + targetHeight;
				} else {
					src.bottom = ih;
				}
			}

			Bitmap output = Bitmap.createBitmap(targetWidth, targetHeight,
					source.getConfig());
			Canvas c = new Canvas(output);
			Paint paint = new Paint();
			paint.setFilterBitmap(true);
			paint.setDither(true);
			c.drawBitmap(source, src, dst, paint);
			if (recycle) {
				source.recycle();
			}
			source = output;
		}
		return source;
	}
}
