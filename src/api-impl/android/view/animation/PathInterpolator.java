package android.view.animation;

import android.graphics.Path;
import android.graphics.PathMeasure;

/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 *
 * Real curve sampling (the previous stub returned input unchanged, which
 * made every app-supplied PathInterpolator — Material easing curves via
 * PathInterpolatorCompat — linear; 2026-11-22).
 *
 * The AOSP framework samples Skia's Path.approximate(); ATL has no Skia,
 * so initPath samples a real PathMeasure at the interval androidx's
 * PathInterpolatorCompat used. getInterpolation is AOSP's binary search.
 */
public class PathInterpolator extends BaseInterpolator {

	private static final float DUMP_PATH_INTERVAL = 1f / 100f;

	private float[] mX; // x coordinates along the sampled curve
	private float[] mY; // y coordinates along the sampled curve

	public PathInterpolator(Path path) {
		initPath(path);
	}

	public PathInterpolator(float controlX, float controlY) {
		initQuad(controlX, controlY);
	}

	public PathInterpolator(float controlX1, float controlY1, float controlX2, float controlY2) {
		initCubic(controlX1, controlY1, controlX2, controlY2);
	}

	private void initQuad(float controlX, float controlY) {
		Path path = new Path();
		path.moveTo(0, 0);
		path.quadTo(controlX, controlY, 1f, 1f);
		initPath(path);
	}

	private void initCubic(float controlX1, float controlY1, float controlX2, float controlY2) {
		Path path = new Path();
		path.moveTo(0, 0);
		path.cubicTo(controlX1, controlY1, controlX2, controlY2, 1f, 1f);
		initPath(path);
	}

	private void initPath(Path path) {
		PathMeasure measure = new PathMeasure(path, false);
		float pathLength = measure.getLength();
		// +2: samples at k*interval plus the exact end of the curve.
		int numPoints = (int) (pathLength / DUMP_PATH_INTERVAL) + 2;
		float[] xPoints = new float[numPoints];
		float[] yPoints = new float[numPoints];
		float[] pos = new float[2];
		int i = 0;
		do {
			float distance = 0;
			while (i < numPoints) {
				if (distance > pathLength)
					distance = pathLength;
				measure.getPosTan(distance, pos, null);
				xPoints[i] = pos[0];
				yPoints[i] = pos[1];
				i++;
				if (distance >= pathLength)
					break;
				distance += DUMP_PATH_INTERVAL;
			}
		} while (i < numPoints && measure.nextContour());
		if (i < numPoints) {
			mX = new float[i];
			mY = new float[i];
			System.arraycopy(xPoints, 0, mX, 0, i);
			System.arraycopy(yPoints, 0, mY, 0, i);
		} else {
			mX = xPoints;
			mY = yPoints;
		}
		// AOSP's validation of an interpolator path.
		if (mX[0] != 0 || mY[0] != 0 || mX[mX.length - 1] != 1 || mY[mY.length - 1] != 1)
			throw new IllegalArgumentException("The Path must start at (0,0) and end at (1,1)");
		for (int k = 1; k < mX.length; k++) {
			if (mX[k] < mX[k - 1])
				throw new IllegalArgumentException("The Path cannot loop back on itself.");
		}
	}

	@Override
	public float getInterpolation(float t) {
		if (t <= 0) {
			return 0;
		} else if (t >= 1) {
			return 1;
		}
		// Do a binary search for the correct x to interpolate between.
		int startIndex = 0;
		int endIndex = mX.length - 1;

		while (endIndex - startIndex > 1) {
			int midIndex = (startIndex + endIndex) / 2;
			if (t < mX[midIndex]) {
				endIndex = midIndex;
			} else {
				startIndex = midIndex;
			}
		}

		float xRange = mX[endIndex] - mX[startIndex];
		if (xRange == 0) {
			return mY[startIndex];
		}

		float tInRange = t - mX[startIndex];
		float fraction = tInRange / xRange;

		float startY = mY[startIndex];
		float endY = mY[endIndex];
		return startY + (fraction * (endY - startY));
	}
}
