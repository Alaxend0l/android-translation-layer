/*
 * Copyright (C) 2010 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package android.graphics.drawable;

import android.annotation.NonNull;
import android.annotation.Nullable;
import android.content.res.Resources;
import android.content.res.Resources.Theme;
import android.content.res.TypedArray;
import android.graphics.Canvas;
import android.graphics.Rect;
import android.util.AttributeSet;
import android.util.MathUtils;
import android.util.TypedValue;
import com.android.internal.R;
import java.io.IOException;
import org.xmlpull.v1.XmlPullParser;
import org.xmlpull.v1.XmlPullParserException;

/**
 * A Drawable that rotates another Drawable to a different degree as its level
 * changes.
 *
 * <p>It can be defined in an XML file with the <code>&lt;rotate&gt;</code>
 * element.</p>
 *
 * <p>ATL's Drawable.setLevel()/getLevel() are no-ops (see Drawable), so the
 * level-driven arc animation is not available: the child is drawn at
 * fromDegrees, which is what a statically rotated drawable asks for. setLevel()
 * is honoured as far as ATL can honour it, and setFromDegrees()/setToDegrees()
 * keep the state Android keeps.</p>
 *
 * @attr ref android.R.styleable#RotateDrawable_visible
 * @attr ref android.R.styleable#RotateDrawable_fromDegrees
 * @attr ref android.R.styleable#RotateDrawable_toDegrees
 * @attr ref android.R.styleable#RotateDrawable_pivotX
 * @attr ref android.R.styleable#RotateDrawable_pivotY
 * @attr ref android.R.styleable#RotateDrawable_drawable
 */
public class RotateDrawable extends DrawableWrapper {
	private static final int MAX_LEVEL = 10000;

	private RotateState mState;

	/**
	 * Creates a new rotating drawable with no wrapped drawable.
	 */
	public RotateDrawable() {
		this(new RotateState(null, null), null);
	}

	/**
	 * Creates a new rotating drawable with the specified wrapped drawable and
	 * rotation parameters.
	 *
	 * @param drawable the drawable to rotate
	 * @param fromDegrees starting rotation angle in degrees
	 * @param toDegrees ending rotation angle in degrees
	 * @param pivotX x coordinate of the pivot for the rotation, either absolute
	 *            in pixels or relative to the drawable width
	 * @param pivotY y coordinate of the pivot for the rotation, either absolute
	 *            in pixels or relative to the drawable height
	 */
	public RotateDrawable(Drawable drawable, int fromDegrees, int toDegrees,
	                      float pivotX, float pivotY) {
		this(new RotateState(null, null), null);

		mState.setFromDegrees(fromDegrees);
		mState.setToDegrees(toDegrees);
		mState.setPivotX(pivotX);
		mState.setPivotY(pivotY);

		setDrawable(drawable);
	}

	@Override
	public void inflate(@NonNull Resources r, @NonNull XmlPullParser parser,
	                    @NonNull AttributeSet attrs, @Nullable Theme theme)
	    throws XmlPullParserException, IOException {
		final TypedArray a = obtainAttributes(r, theme, attrs, R.styleable.RotateDrawable);

		// Inflation will advance the XmlPullParser and AttributeSet.
		super.inflate(r, parser, attrs, theme);

		updateStateFromTypedArray(a);
		verifyRequiredAttributes(a);
		a.recycle();
	}

	private void verifyRequiredAttributes(@NonNull TypedArray a) throws XmlPullParserException {
		if (getDrawable() == null) {
			throw new XmlPullParserException(a.getPositionDescription()
			    + ": <rotate> tag requires a 'drawable' attribute or child tag defining a drawable");
		}
	}

	private void updateStateFromTypedArray(@NonNull TypedArray a) {
		final RotateState state = mState;
		if (state == null) {
			return;
		}

		state.mChangingConfigurations |= a.getChangingConfigurations();

		if (a.hasValue(R.styleable.RotateDrawable_pivotX)) {
			final TypedValue tv = a.peekValue(R.styleable.RotateDrawable_pivotX);
			state.mPivotXRel = tv.type == TypedValue.TYPE_FRACTION;
			state.mPivotX = state.mPivotXRel ? tv.getFraction(1.0f, 1.0f) : tv.getFloat();
		}

		if (a.hasValue(R.styleable.RotateDrawable_pivotY)) {
			final TypedValue tv = a.peekValue(R.styleable.RotateDrawable_pivotY);
			state.mPivotYRel = tv.type == TypedValue.TYPE_FRACTION;
			state.mPivotY = state.mPivotYRel ? tv.getFraction(1.0f, 1.0f) : tv.getFloat();
		}

		state.mFromDegrees = a.getFloat(R.styleable.RotateDrawable_fromDegrees, state.mFromDegrees);
		state.mToDegrees = a.getFloat(R.styleable.RotateDrawable_toDegrees, state.mToDegrees);
		state.mCurrentDegrees = state.mFromDegrees;
	}

	@Override
	public void draw(Canvas canvas) {
		final Drawable d = getDrawable();
		if (d == null) {
			return;
		}

		final Rect bounds = d.getBounds();
		final int w = bounds.right - bounds.left;
		final int h = bounds.bottom - bounds.top;
		final RotateState st = mState;

		// ATL cannot drive levels, so this stays at fromDegrees; the lerp is kept
		// as AOSP has it so the arc maths is in one place if levels ever work.
		st.mCurrentDegrees = MathUtils.lerp(st.mFromDegrees, st.mToDegrees,
		                                    getLevel() / (float) MAX_LEVEL);

		final float px = st.mPivotXRel ? (w * st.mPivotX) : st.mPivotX;
		final float py = st.mPivotYRel ? (h * st.mPivotY) : st.mPivotY;

		final int saveCount = canvas.save();
		canvas.rotate(st.mCurrentDegrees, px + bounds.left, py + bounds.top);
		d.draw(canvas);
		canvas.restoreToCount(saveCount);
	}

	/**
	 * Sets the start angle for rotation.
	 *
	 * @param fromDegrees starting angle in degrees
	 * @see #getFromDegrees()
	 * @attr ref android.R.styleable#RotateDrawable_fromDegrees
	 */
	public void setFromDegrees(float fromDegrees) {
		if (mState.mFromDegrees != fromDegrees) {
			mState.setFromDegrees(fromDegrees);
			invalidateSelf();
		}
	}

	/**
	 * @return starting angle for rotation in degrees
	 * @see #setFromDegrees(float)
	 * @attr ref android.R.styleable#RotateDrawable_fromDegrees
	 */
	public float getFromDegrees() {
		return mState.mFromDegrees;
	}

	/**
	 * Sets the end angle for rotation.
	 *
	 * @param toDegrees ending angle in degrees
	 * @see #getToDegrees()
	 * @attr ref android.R.styleable#RotateDrawable_toDegrees
	 */
	public void setToDegrees(float toDegrees) {
		if (mState.mToDegrees != toDegrees) {
			mState.setToDegrees(toDegrees);
			invalidateSelf();
		}
	}

	/**
	 * @return ending angle for rotation in degrees
	 * @see #setToDegrees(float)
	 * @attr ref android.R.styleable#RotateDrawable_toDegrees
	 */
	public float getToDegrees() {
		return mState.mToDegrees;
	}

	/**
	 * Sets the X position around which the drawable is rotated.
	 *
	 * @param pivotX X position around which to rotate
	 * @see #setPivotXRelative(boolean)
	 * @attr ref android.R.styleable#RotateDrawable_pivotX
	 */
	public void setPivotX(float pivotX) {
		if (mState.mPivotX != pivotX) {
			mState.setPivotX(pivotX);
			invalidateSelf();
		}
	}

	/**
	 * @return X position around which to rotate
	 * @see #setPivotX(float)
	 * @attr ref android.R.styleable#RotateDrawable_pivotX
	 */
	public float getPivotX() {
		return mState.mPivotX;
	}

	/**
	 * Sets whether the X pivot value represents a fraction of the drawable
	 * width or an absolute value in pixels.
	 */
	public void setPivotXRelative(boolean relative) {
		if (mState.mPivotXRel != relative) {
			mState.mPivotXRel = relative;
			invalidateSelf();
		}
	}

	/**
	 * @return true if the X pivot represents a fraction of the drawable width,
	 *         or false if it represents an absolute value in pixels
	 * @see #setPivotXRelative(boolean)
	 */
	public boolean isPivotXRelative() {
		return mState.mPivotXRel;
	}

	/**
	 * Sets the Y position around which the drawable is rotated.
	 *
	 * @param pivotY Y position around which to rotate
	 * @see #setPivotYRelative(boolean)
	 * @attr ref android.R.styleable#RotateDrawable_pivotY
	 */
	public void setPivotY(float pivotY) {
		if (mState.mPivotY != pivotY) {
			mState.setPivotY(pivotY);
			invalidateSelf();
		}
	}

	/**
	 * @return Y position around which to rotate
	 * @see #setPivotY(float)
	 * @attr ref android.R.styleable#RotateDrawable_pivotY
	 */
	public float getPivotY() {
		return mState.mPivotY;
	}

	/**
	 * Sets whether the Y pivot value represents a fraction of the drawable
	 * height or an absolute value in pixels.
	 */
	public void setPivotYRelative(boolean relative) {
		if (mState.mPivotYRel != relative) {
			mState.mPivotYRel = relative;
			invalidateSelf();
		}
	}

	/**
	 * @return true if the Y pivot represents a fraction of the drawable height,
	 *         or false if it represents an absolute value in pixels
	 * @see #setPivotYRelative(boolean)
	 */
	public boolean isPivotYRelative() {
		return mState.mPivotYRel;
	}

	@Override
	DrawableWrapperState mutateConstantState() {
		mState = new RotateState(mState, null);
		return mState;
	}

	static final class RotateState extends DrawableWrapper.DrawableWrapperState {
		boolean mPivotXRel = true;
		float mPivotX = 0.5f;
		boolean mPivotYRel = true;
		float mPivotY = 0.5f;
		float mFromDegrees = 0.0f;
		float mToDegrees = 360.0f;
		float mCurrentDegrees = 0.0f;

		RotateState(RotateState orig, Resources res) {
			super(orig, res);

			if (orig != null) {
				mPivotXRel = orig.mPivotXRel;
				mPivotX = orig.mPivotX;
				mPivotYRel = orig.mPivotYRel;
				mPivotY = orig.mPivotY;
				mFromDegrees = orig.mFromDegrees;
				mToDegrees = orig.mToDegrees;
				mCurrentDegrees = orig.mCurrentDegrees;
			}
		}

		void setFromDegrees(float fromDegrees) {
			mFromDegrees = fromDegrees;
			mCurrentDegrees = fromDegrees;
		}

		void setToDegrees(float toDegrees) {
			mToDegrees = toDegrees;
		}

		void setPivotX(float pivotX) {
			mPivotX = pivotX;
		}

		void setPivotY(float pivotY) {
			mPivotY = pivotY;
		}

		@Override
		public Drawable newDrawable(Resources res) {
			return new RotateDrawable(this, res);
		}
	}

	private RotateDrawable(RotateState state, Resources res) {
		super(state, res);

		mState = state;
	}
}
