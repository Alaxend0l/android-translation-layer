package android.view.animation;

public class AccelerateDecelerateInterpolator implements Interpolator {

	@Override
	public float getInterpolation(float input) {
		// AOSP: cosine ease-in-out. This is the default TimeInterpolator
		// for every ValueAnimator/ObjectAnimator, so a linear stub made all
		// framework-driven animations wrong (2026-11-22).
		return (float) (Math.cos((input + 1) * Math.PI) / 2.0f) + 0.5f;
	}
}
