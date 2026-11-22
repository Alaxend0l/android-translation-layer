package android.widget;

import android.content.Context;
import android.util.AttributeSet;
import android.view.View;
import android.view.animation.AnimationUtils;

public class HorizontalScrollView extends FrameLayout {
	// Smooth scrolling, ported from AOSP HorizontalScrollView (frameworks/base,
	// 7.1): the scroller lives in this class, and ATL drives computeScroll() from
	// the GTK size-allocate pass (api-impl-jni/widgets/WrapperWidget.c) while
	// offsetting the child by -getScrollX()/-getScrollY(). ATL has no scrollbars,
	// so the awakenScrollBars() calls of the original are left out.
	private static final long ANIMATED_SCROLL_GAP = 250;
	private long mLastScroll = 0;
	private OverScroller mScroller;

	public HorizontalScrollView(Context context) {
		super(context);
		ensureScroller();
	}

	public HorizontalScrollView(Context context, AttributeSet attributeSet) {
		super(context, attributeSet);
		ensureScroller();
	}

	public void setHorizontalScrollBarEnabled(boolean enabled) {}

	/*
	 * Created on demand: ATL's native code can call computeScroll() from
	 * WrapperWidget.c's size-allocate callback (have_custom_computeScroll ->
	 * J__View__computeScroll) while the Java constructor chain is still running,
	 * so computeScroll() must not depend on the constructor body having
	 * finished.  On aarch64 that ordering is hit by the HorizontalScrollView in
	 * org.akanework.gramophone's now-playing header, which crashed with
	 * NullPointerException @ android.widget.HorizontalScrollView.computeScroll.
	 */
	private OverScroller ensureScroller() {
		if (mScroller == null) {
			mScroller = new OverScroller(getContext());
		}
		return mScroller;
	}

	@Override
	public void computeScroll() {
		if (ensureScroller().computeScrollOffset()) {
			int oldX = getScrollX();
			int oldY = getScrollY();
			int x = mScroller.getCurrX();
			int y = mScroller.getCurrY();

			if (oldX != x || oldY != y) {
				scrollTo(x, y);
				onScrollChanged(x, y, oldX, oldY);
			}

			postInvalidateOnAnimation();
		}
	}

	private int getScrollRange() {
		int scrollRange = 0;
		if (getChildCount() > 0) {
			View child = getChildAt(0);
			scrollRange = Math.max(0,
			    child.getWidth() - (getWidth() - getPaddingLeft() - getPaddingRight()));
		}
		return scrollRange;
	}

	/**
	 * Like {@link View#scrollBy}, but scroll smoothly instead of immediately.
	 *
	 * @param dx the number of pixels to scroll by on the X axis
	 * @param dy the number of pixels to scroll by on the Y axis
	 */
	public final void smoothScrollBy(int dx, int dy) {
		if (getChildCount() == 0) {
			// Nothing to do.
			return;
		}
		long duration = AnimationUtils.currentAnimationTimeMillis() - mLastScroll;
		if (duration > ANIMATED_SCROLL_GAP) {
			final int width = getWidth() - getPaddingRight() - getPaddingLeft();
			final int right = getChildAt(0).getWidth();
			final int maxX = Math.max(0, right - width);
			final int scrollX = getScrollX();
			dx = Math.max(0, Math.min(scrollX + dx, maxX)) - scrollX;

			ensureScroller().startScroll(scrollX, getScrollY(), dx, 0);
			postInvalidateOnAnimation();
		} else {
			if (!ensureScroller().isFinished()) {
				ensureScroller().abortAnimation();
			}
			scrollBy(dx, dy);
		}
		mLastScroll = AnimationUtils.currentAnimationTimeMillis();
	}

	/**
	 * Like {@link #scrollTo}, but scroll smoothly instead of immediately.
	 *
	 * @param x the position where to scroll on the X axis
	 * @param y the position where to scroll on the Y axis
	 */
	public final void smoothScrollTo(int x, int y) {
		smoothScrollBy(x - getScrollX(), y - getScrollY());
	}

	public void setFillViewport(boolean fillViewport) {}

	/* AOSP HorizontalScrollView scrollbar helpers, see ScrollView. */
	@Override
	protected int computeHorizontalScrollRange() {
		final int count = getChildCount();
		final int contentWidth = getWidth() - getPaddingLeft() - getPaddingRight();
		if (count == 0)
			return contentWidth;
		View view = getChildAt(0);
		final int childWidth = (view != null) ? view.getMeasuredWidth() : 0;
		return contentWidth + childWidth;
	}

	@Override
	protected int computeHorizontalScrollOffset() {
		return getScrollX();
	}

	@Override
	protected int computeHorizontalScrollExtent() {
		return getWidth() - getPaddingLeft() - getPaddingRight();
	}
}
