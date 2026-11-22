package android.graphics;

/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 *
 * Path over polylines: native_flatten walks the GskPath and subdivides
 * curves; the answers below are computed from that flattening. The
 * subdivision is ~100 steps per unit of chord length, finer than every
 * caller we have (PathInterpolator, VectorDrawable trim path).
 */
public class PathMeasure {

	private float[][] mContours; // flattened x,y pairs per contour
	private float[] mPts;        // current contour (may include closing point)
	private float[] mCum;        // cumulative segment lengths of mPts
	private int mIndex = -1;
	private boolean mForceClosed;

	public PathMeasure() {}

	public PathMeasure(Path path, boolean forceClosed) {
		setPath(path, forceClosed);
	}

	public void setPath(Path path, boolean forceClosed) {
		mContours = null;
		mPts = null;
		mCum = null;
		mIndex = -1;
		mForceClosed = forceClosed;
		if (path == null)
			return;
		float[] flat = native_flatten(path.getGskPath());
		if (flat == null || flat.length == 0)
			return;
		int count = 0;
		for (int i = 0; i < flat.length;) {
			int n = (int) flat[i];
			count++;
			i += 1 + 2 * n;
		}
		mContours = new float[count][];
		int ci = 0;
		for (int i = 0; i < flat.length;) {
			int n = (int) flat[i];
			i++;
			float[] pts = new float[2 * n];
			System.arraycopy(flat, i, pts, 0, 2 * n);
			i += 2 * n;
			mContours[ci++] = pts;
		}
		mIndex = 0;
		loadContour();
	}

	private void loadContour() {
		float[] pts = mContours[mIndex];
		int n = pts.length / 2;
		if (mForceClosed && n >= 2 && (pts[2 * n - 2] != pts[0] || pts[2 * n - 1] != pts[1])) {
			float[] closed = new float[2 * n + 2];
			System.arraycopy(pts, 0, closed, 0, 2 * n);
			closed[2 * n] = pts[0];
			closed[2 * n + 1] = pts[1];
			pts = closed;
			n++;
		}
		mPts = pts;
		mCum = new float[n];
		float total = 0;
		for (int i = 1; i < n; i++) {
			float dx = pts[2 * i] - pts[2 * i - 2];
			float dy = pts[2 * i + 1] - pts[2 * i - 1];
			total += (float) Math.sqrt(dx * dx + dy * dy);
			mCum[i] = total;
		}
	}

	public float getLength() {
		return mCum == null ? 0 : mCum[mCum.length - 1];
	}

	public boolean getPosTan(float distance, float[] pos, float[] tan) {
		if (mPts == null)
			return false;
		int n = mPts.length / 2;
		float d = distance;
		if (d < 0)
			d = 0;
		else if (d > mCum[n - 1])
			d = mCum[n - 1];
		int seg = 1;
		while (seg < n - 1 && mCum[seg] < d)
			seg++;
		float segStart = mCum[seg - 1];
		float segLen = mCum[seg] - segStart;
		float f = segLen > 0 ? (d - segStart) / segLen : 0;
		float x = mPts[2 * (seg - 1)] + f * (mPts[2 * seg] - mPts[2 * (seg - 1)]);
		float y = mPts[2 * (seg - 1) + 1] + f * (mPts[2 * seg + 1] - mPts[2 * (seg - 1) + 1]);
		if (pos != null && pos.length >= 2) {
			pos[0] = x;
			pos[1] = y;
		}
		if (tan != null && tan.length >= 2) {
			float dx = mPts[2 * seg] - mPts[2 * (seg - 1)];
			float dy = mPts[2 * seg + 1] - mPts[2 * (seg - 1) + 1];
			float len = (float) Math.sqrt(dx * dx + dy * dy);
			tan[0] = len > 0 ? dx / len : 0;
			tan[1] = len > 0 ? dy / len : 0;
		}
		return true;
	}

	/*
	 * Copies the portion of the current contour between the two distances
	 * into dst as line segments. The forceClosed parameter is ignored: every
	 * ATL caller (VectorDrawable trim path) wants the open subpath, like
	 * Android renders it for a trim.
	 */
	public boolean getSegment(float start, float end, Path dst, boolean forceClosed) {
		if (mPts == null || dst == null || end <= start)
			return false;
		float length = mCum[mCum.length - 1];
		if (start >= length)
			return false;
		if (end > length)
			end = length;
		float[] p = new float[2];
		getPosTan(start, p, null);
		dst.moveTo(p[0], p[1]);
		int n = mPts.length / 2;
		int i = 1;
		while (i < n && mCum[i] <= start)
			i++;
		while (i < n && mCum[i] < end) {
			dst.lineTo(mPts[2 * i], mPts[2 * i + 1]);
			i++;
		}
		getPosTan(end, p, null);
		dst.lineTo(p[0], p[1]);
		return true;
	}

	public boolean nextContour() {
		if (mContours == null || mIndex + 1 >= mContours.length)
			return false;
		mIndex++;
		loadContour();
		return true;
	}

	private static native float[] native_flatten(long gskPath);
}
