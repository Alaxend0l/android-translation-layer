package android.graphics.drawable;

import android.content.res.ColorStateList;
import android.graphics.Canvas;
import com.android.internal.R;

public class RippleDrawable extends LayerDrawable {

	public RippleDrawable(ColorStateList colorStateList, Drawable drawable, Drawable drawable2) {
		super(drawable == null ? new Drawable[] {} : new Drawable[] {drawable});
	}

	RippleDrawable() {}

	public void setColor(ColorStateList colorStateList) {}

	/* AOSP: ripple radius in px, RADIUS_AUTO (-1) = from the bounds. Stored; ATL
	 * draws the pressed state without the ripple animation. */
	private int radius = -1;

	public void setRadius(int radius) {
		this.radius = radius;
		invalidateSelf();
	}

	/* the pre-API-23 hidden name, still called through reflection by Material */
	public void setMaxRadius(int maxRadius) {
		setRadius(maxRadius);
	}

	public int getRadius() {
		return radius;
	}

	@Override
	public void draw(Canvas canvas) {
		final ChildDrawable[] array = mLayerState.mChildren;
		final int N = mLayerState.mNum;
		for (int i = 0; i < N; i++) {
			if (array[i].mId != R.id.mask)
				array[i].mDrawable.draw(canvas);
		}
	}
}
