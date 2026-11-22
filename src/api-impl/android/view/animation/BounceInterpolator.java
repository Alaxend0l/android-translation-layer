/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 *
 * Ported from AOSP (frameworks/base, core/java/android/view/animation/BounceInterpolator.java),
 * pure math. Missing-class crash witness: indi.dmzz_yyhyy.lightnovelreader
 * (NoClassDefFoundError from a static initializer during Compose animation).
 */
package android.view.animation;

import android.content.Context;
import android.util.AttributeSet;

public class BounceInterpolator extends BaseInterpolator {

	public BounceInterpolator() {
	}

	// AOSP has this for XML inflation; ATL's AnimatorInflater does not use
	// it yet, but apps resolving the class expect the constructor too.
	public BounceInterpolator(Context context, AttributeSet attrs) {
	}

	private static float bounce(float t) {
		return t * t * 8.0f;
	}

	public float getInterpolation(float t) {
		t *= 1.1226f;
		if (t < 0.3535f) {
			return bounce(t);
		} else if (t < 0.7408f) {
			return bounce(t - 0.54719f) + 0.7f;
		} else if (t < 0.9644f) {
			return bounce(t - 0.8526f) + 0.9f;
		} else {
			return bounce(t - 1.0435f) + 0.95f;
		}
	}
}
