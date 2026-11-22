/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 *
 * Ported from AOSP (frameworks/base, core/java/android/view/animation/AnticipateOvershootInterpolator.java,
 * android-14), pure math. ATL adaptations: AOSP's NativeInterpolator
 * interface / @HasNativeInterpolator / createNativeInterpolator are
 * Skia-side and do not exist in ATL, so they are not implemented; the
 * XML constructor skips setChangingConfigurations (ATL's TypedArray /
 * BaseInterpolator do not track config changes).
 *
 * Crash witness: indi.dmzz_yyhyy.lightnovelreader (same static-init
 * chain as the BounceInterpolator commit: after that class was added,
 * its clinit failed here, then on the missing <init>(float, float)
 * overload, in fc17).
 */
package android.view.animation;

import android.content.Context;
import android.content.res.TypedArray;
import android.util.AttributeSet;

public class AnticipateOvershootInterpolator extends BaseInterpolator {

	private float mTension;

	public AnticipateOvershootInterpolator() {
		mTension = 2.0f * 1.5f;
	}

	/**
	 * @param tension Amount of anticipation/overshoot. When tension equals
	 *            0.0f, there is no anticipation/overshoot and the
	 *            interpolator becomes a simple acceleration/deceleration
	 *            interpolator.
	 */
	public AnticipateOvershootInterpolator(float tension) {
		mTension = tension * 1.5f;
	}

	/**
	 * @param tension Amount of anticipation/overshoot.
	 * @param extraTension Amount by which to multiply the tension.
	 */
	public AnticipateOvershootInterpolator(float tension, float extraTension) {
		mTension = tension * extraTension;
	}

	public AnticipateOvershootInterpolator(Context context, AttributeSet attrs) {
		TypedArray a = context.obtainStyledAttributes(attrs,
		    com.android.internal.R.styleable.AnticipateOvershootInterpolator);

		mTension = a.getFloat(
		    com.android.internal.R.styleable.AnticipateOvershootInterpolator_tension, 2.0f)
		    * a.getFloat(
		    com.android.internal.R.styleable.AnticipateOvershootInterpolator_extraTension, 1.5f);
		// AOSP also stores the styleable's changing-configurations mask on
		// the interpolator here; ATL has no configuration changes.
		a.recycle();
	}

	private static float a(float t, float s) {
		return t * t * ((s + 1) * t - s);
	}

	private static float o(float t, float s) {
		return t * t * ((s + 1) * t + s);
	}

	public float getInterpolation(float t) {
		// a(t, s) = t * t * ((s + 1) * t - s)
		// o(t, s) = t * t * ((s + 1) * t + s)
		// f(t) = 0.5 * a(t * 2, tension * extraTension), when t < 0.5
		// f(t) = 0.5 * (o(t * 2 - 2, tension * extraTension) + 2), when t <= 1.0
		if (t < 0.5f)
			return 0.5f * a(t * 2.0f, mTension);
		else
			return 0.5f * (o(t * 2.0f - 2.0f, mTension) + 2.0f);
	}
}
