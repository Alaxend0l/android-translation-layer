package android.widget;

import android.content.Context;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.AnimationUtils;

public class ScrollView extends FrameLayout {
	// Smooth scrolling, ported from AOSP ScrollView (frameworks/base, 7.1).
	// The scroller belongs to ScrollView, not to View: AOSP's public
	// smoothScrollTo/smoothScrollBy are declared here. ATL drives computeScroll()
	// from the GTK size-allocate pass (api-impl-jni/widgets/WrapperWidget.c) and
	// offsets the child by -getScrollX()/-getScrollY(), so stepping the scroller
	// and asking for a redraw is what animates the scroll. ATL has no scrollbars,
	// so the awakenScrollBars() calls of the original are left out.
	private static final long ANIMATED_SCROLL_GAP = 250;
	private long mLastScroll = 0;
	private OverScroller mScroller;

	public ScrollView(Context context, AttributeSet attrs) {
		super(context, attrs);
		ensureScroller();
	}

	public ScrollView(Context context) {
		super(context);
		ensureScroller();
	}

	/*
	 * Created on demand: ATL's native code can call computeScroll() from
	 * WrapperWidget.c's size-allocate callback (have_custom_computeScroll ->
	 * J__View__computeScroll) while the Java constructor chain is still
	 * running, so computeScroll() must not depend on the constructor body
	 * having finished.  On aarch64 that ordering is hit by
	 * org.akanework.gramophone's HorizontalScrollView.
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
			    child.getHeight() - (getHeight() - getPaddingBottom() - getPaddingTop()));
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
			final int height = getHeight() - getPaddingBottom() - getPaddingTop();
			final int bottom = getChildAt(0).getHeight();
			final int maxY = Math.max(0, bottom - height);
			final int scrollY = getScrollY();
			dy = Math.max(0, Math.min(scrollY + dy, maxY)) - scrollY;

			ensureScroller().startScroll(getScrollX(), scrollY, 0, dy);
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

	@Override
	protected native long native_constructor(Context context, AttributeSet attrs);
	@Override
	protected native void native_addView(long widget, long child, int index, ViewGroup.LayoutParams params);
	@Override
	protected native void native_removeView(long widget, long child);

	@Override
	public boolean onInterceptTouchEvent(MotionEvent event) {
		return native_gtkEventPropagation();
	}

	@Override
	public boolean onTouchEvent(MotionEvent event) {
		return native_gtkEventPropagation();
	}

	@Override
	protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
		int width = 0;
		int height = 0;
		if (getChildCount() > 0) {
			View child = getChildAt(0);
			ViewGroup.LayoutParams lp = child.getLayoutParams();
			int childWidthMeasureSpec = getChildMeasureSpec(widthMeasureSpec, 0, lp.width);
			int childHeightMeasureSpec = MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED);
			child.measure(childWidthMeasureSpec, childHeightMeasureSpec);
			width = child.getMeasuredWidth();
			height = child.getMeasuredHeight();
		}
		setMeasuredDimension(resolveSize(width, widthMeasureSpec), resolveSize(height, heightMeasureSpec));
	}

	public void setFillViewport(boolean fillViewport) {}

	/* AOSP ScrollView's scrollbar-helper overrides (the shape
	 * View.canScrollVertically reads). ATL's ScrollView really scrolls its
	 * child through scrollTo/computeScroll (see the port note at the top),
	 * so these values are real, not sentinels. */
	@Override
	protected int computeVerticalScrollRange() {
		final int count = getChildCount();
		final int contentHeight = getHeight() - getPaddingTop() - getPaddingBottom();
		if (count == 0)
			return contentHeight;
		View view = getChildAt(0);
		final int childHeight = (view != null) ? view.getMeasuredHeight() : 0;
		return contentHeight + childHeight;
	}

	@Override
	protected int computeVerticalScrollOffset() {
		return getScrollY();
	}

	@Override
	protected int computeVerticalScrollExtent() {
		return getHeight() - getPaddingTop() - getPaddingBottom();
	}

	public boolean fullScroll(int direction) {
		return true;
	}
}
