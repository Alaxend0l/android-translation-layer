package android.view;

import android.R;
import android.animation.AnimatorInflater;
import android.animation.StateListAnimator;
import android.annotation.NonNull;
import android.annotation.Nullable;
import android.atl.GskCanvas;
import android.atl.annotation.Export;
import android.content.Context;
import android.content.ContextWrapper;
import android.content.res.ColorStateList;
import android.content.res.Configuration;
import android.content.res.Resources;
import android.content.res.TypedArray;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Point;
import android.graphics.PorterDuff;
import android.graphics.Rect;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.os.Binder;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.Parcelable;
import android.os.SystemClock;
import android.os.Vibrator;
import android.util.AttributeSet;
import android.util.FloatProperty;
import android.util.LayoutDirection;
import android.util.Property;
import android.util.Slog;
import android.util.SparseArray;
import android.util.StateSet;
import android.util.TypedValue;
import android.view.WindowInsets;
import android.view.accessibility.AccessibilityNodeInfo;
import android.view.animation.Animation;
import android.view.autofill.AutofillId;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputConnection;
import android.view.translation.ViewTranslationCallback;
import java.lang.CharSequence;
import java.lang.ref.WeakReference;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;

class WindowId {}

public class View implements Drawable.Callback {

	// --- constants from android source

	/**
	 * The logging tag used by this class with android.util.Log.
	 */
	protected static final String TAG = "View";

	/**
	 * When set to true, apps will draw debugging information about their layouts.
	 *
	 * @hide
	 */
	public static final String DEBUG_LAYOUT_PROPERTY = "debug.layout";

	/**
	 * Used to mark a View that has no ID.
	 */
	public static final int NO_ID = -1;

	/**
	 * Signals that compatibility booleans have been initialized according to
	 * target SDK versions.
	 */
	private static boolean sCompatibilityDone = false;

	/**
	 * Use the old (broken) way of building MeasureSpecs.
	 */
	private static boolean sUseBrokenMakeMeasureSpec = false;

	/**
	 * Ignore any optimizations using the measure cache.
	 */
	private static boolean sIgnoreMeasureCache = false;

	/**
	 * This view does not want keystrokes. Use with TAKES_FOCUS_MASK when
	 * calling setFlags.
	 */
	private static final int NOT_FOCUSABLE = 0x00000000;

	/**
	 * This view wants keystrokes. Use with TAKES_FOCUS_MASK when calling
	 * setFlags.
	 */
	private static final int FOCUSABLE = 0x00000001;

	/**
	 * Mask for use with setFlags indicating bits used for focus.
	 */
	private static final int FOCUSABLE_MASK = 0x00000001;

	/**
	 * This view will adjust its padding to fit sytem windows (e.g. status bar)
	 */
	private static final int FITS_SYSTEM_WINDOWS = 0x00000002;

	/**
	 * This view is visible.
	 * Use with {@link #setVisibility} and <a href="#attr_android:visibility">{@code
	 * android:visibility}.
	 */
	public static final int VISIBLE = 0x00000000;

	/**
	 * This view is invisible, but it still takes up space for layout purposes.
	 * Use with {@link #setVisibility} and <a href="#attr_android:visibility">{@code
	 * android:visibility}.
	 */
	public static final int INVISIBLE = 0x00000004;

	/**
	 * This view is invisible, and it doesn't take any space for layout
	 * purposes. Use with {@link #setVisibility} and <a href="#attr_android:visibility">{@code
	 * android:visibility}.
	 */
	public static final int GONE = 0x00000008;

	/**
	 * Mask for use with setFlags indicating bits used for visibility.
	 * {@hide}
	 */
	static final int VISIBILITY_MASK = 0x0000000C;

	private static final int[] VISIBILITY_FLAGS = {VISIBLE, INVISIBLE, GONE};

	/**
	 * This view is enabled. Interpretation varies by subclass.
	 * Use with ENABLED_MASK when calling setFlags.
	 * {@hide}
	 */
	static final int ENABLED = 0x00000000;

	/**
	 * This view is disabled. Interpretation varies by subclass.
	 * Use with ENABLED_MASK when calling setFlags.
	 * {@hide}
	 */
	static final int DISABLED = 0x00000020;

	/**
	 * Mask for use with setFlags indicating bits used for indicating whether
	 * this view is enabled
	 * {@hide}
	 */
	static final int ENABLED_MASK = 0x00000020;

	/**
	 * This view won't draw. {@link #onDraw(android.graphics.Canvas)} won't be
	 * called and further optimizations will be performed. It is okay to have
	 * this flag set and a background. Use with DRAW_MASK when calling setFlags.
	 * {@hide}
	 */
	static final int WILL_NOT_DRAW = 0x00000080;

	/**
	 * Mask for use with setFlags indicating bits used for indicating whether
	 * this view is will draw
	 * {@hide}
	 */
	static final int DRAW_MASK = 0x00000080;

	/**
	 * <p>This view doesn't show scrollbars.</p>
	 * {@hide}
	 */
	static final int SCROLLBARS_NONE = 0x00000000;

	/**
	 * <p>This view shows horizontal scrollbars.</p>
	 * {@hide}
	 */
	static final int SCROLLBARS_HORIZONTAL = 0x00000100;

	/**
	 * <p>This view shows vertical scrollbars.</p>
	 * {@hide}
	 */
	static final int SCROLLBARS_VERTICAL = 0x00000200;

	/**
	 * <p>Mask for use with setFlags indicating bits used for indicating which
	 * scrollbars are enabled.</p>
	 * {@hide}
	 */
	static final int SCROLLBARS_MASK = 0x00000300;

	/**
	 * Indicates that the view should filter touches when its window is obscured.
	 * Refer to the class comments for more information about this security feature.
	 * {@hide}
	 */
	static final int FILTER_TOUCHES_WHEN_OBSCURED = 0x00000400;

	/**
	 * Set for framework elements that use FITS_SYSTEM_WINDOWS, to indicate
	 * that they are optional and should be skipped if the window has
	 * requested system UI flags that ignore those insets for layout.
	 */
	static final int OPTIONAL_FITS_SYSTEM_WINDOWS = 0x00000800;

	/**
	 * <p>This view doesn't show fading edges.</p>
	 * {@hide}
	 */
	static final int FADING_EDGE_NONE = 0x00000000;

	/**
	 * <p>This view shows horizontal fading edges.</p>
	 * {@hide}
	 */
	static final int FADING_EDGE_HORIZONTAL = 0x00001000;

	/**
	 * <p>This view shows vertical fading edges.</p>
	 * {@hide}
	 */
	static final int FADING_EDGE_VERTICAL = 0x00002000;

	/**
	 * <p>Mask for use with setFlags indicating bits used for indicating which
	 * fading edges are enabled.</p>
	 * {@hide}
	 */
	static final int FADING_EDGE_MASK = 0x00003000;

	/**
	 * <p>Indicates this view can be clicked. When clickable, a View reacts
	 * to clicks by notifying the OnClickListener.<p>
	 * {@hide}
	 */
	static final int CLICKABLE = 0x00004000;

	/**
	 * <p>Indicates this view is caching its drawing into a bitmap.</p>
	 * {@hide}
	 */
	static final int DRAWING_CACHE_ENABLED = 0x00008000;

	/**
	 * <p>Indicates that no icicle should be saved for this view.<p>
	 * {@hide}
	 */
	static final int SAVE_DISABLED = 0x000010000;

	/**
	 * <p>Mask for use with setFlags indicating bits used for the saveEnabled
	 * property.</p>
	 * {@hide}
	 */
	static final int SAVE_DISABLED_MASK = 0x000010000;

	/**
	 * <p>Indicates that no drawing cache should ever be created for this view.<p>
	 * {@hide}
	 */
	static final int WILL_NOT_CACHE_DRAWING = 0x000020000;

	/**
	 * <p>Indicates this view can take / keep focus when int touch mode.</p>
	 * {@hide}
	 */
	static final int FOCUSABLE_IN_TOUCH_MODE = 0x00040000;

	/**
	 * <p>Enables low quality mode for the drawing cache.</p>
	 */
	public static final int DRAWING_CACHE_QUALITY_LOW = 0x00080000;

	/**
	 * <p>Enables high quality mode for the drawing cache.</p>
	 */
	public static final int DRAWING_CACHE_QUALITY_HIGH = 0x00100000;

	/**
	 * <p>Enables automatic quality mode for the drawing cache.</p>
	 */
	public static final int DRAWING_CACHE_QUALITY_AUTO = 0x00000000;

	private static final int[] DRAWING_CACHE_QUALITY_FLAGS = {
		DRAWING_CACHE_QUALITY_AUTO, DRAWING_CACHE_QUALITY_LOW, DRAWING_CACHE_QUALITY_HIGH
	};

	/**
	 * <p>Mask for use with setFlags indicating bits used for the cache
	 * quality property.</p>
	 * {@hide}
	 */
	static final int DRAWING_CACHE_QUALITY_MASK = 0x00180000;

	/**
	 * <p>
	 * Indicates this view can be long clicked. When long clickable, a View
	 * reacts to long clicks by notifying the OnLongClickListener or showing a
	 * context menu.
	 * </p>
	 * {@hide}
	 */
	static final int LONG_CLICKABLE = 0x00200000;

	/**
	 * <p>Indicates that this view gets its drawable states from its direct parent
	 * and ignores its original internal states.</p>
	 *
	 * @hide
	 */
	static final int DUPLICATE_PARENT_STATE = 0x00400000;

	/**
	 * The scrollbar style to display the scrollbars inside the content area,
	 * without increasing the padding. The scrollbars will be overlaid with
	 * translucency on the view's content.
	 */
	public static final int SCROLLBARS_INSIDE_OVERLAY = 0;

	/**
	 * The scrollbar style to display the scrollbars inside the padded area,
	 * increasing the padding of the view. The scrollbars will not overlap the
	 * content area of the view.
	 */
	public static final int SCROLLBARS_INSIDE_INSET = 0x01000000;

	/**
	 * The scrollbar style to display the scrollbars at the edge of the view,
	 * without increasing the padding. The scrollbars will be overlaid with
	 * translucency.
	 */
	public static final int SCROLLBARS_OUTSIDE_OVERLAY = 0x02000000;

	/**
	 * The scrollbar style to display the scrollbars at the edge of the view,
	 * increasing the padding of the view. The scrollbars will only overlap the
	 * background, if any.
	 */
	public static final int SCROLLBARS_OUTSIDE_INSET = 0x03000000;

	/**
	 * Mask to check if the scrollbar style is overlay or inset.
	 * {@hide}
	 */
	static final int SCROLLBARS_INSET_MASK = 0x01000000;

	/**
	 * Mask to check if the scrollbar style is inside or outside.
	 * {@hide}
	 */
	static final int SCROLLBARS_OUTSIDE_MASK = 0x02000000;

	/**
	 * Mask for scrollbar style.
	 * {@hide}
	 */
	static final int SCROLLBARS_STYLE_MASK = 0x03000000;

	/**
	 * View flag indicating that the screen should remain on while the
	 * window containing this view is visible to the user.  This effectively
	 * takes care of automatically setting the WindowManager's
	 * {@link WindowManager.LayoutParams#FLAG_KEEP_SCREEN_ON}.
	 */
	public static final int KEEP_SCREEN_ON = 0x04000000;

	/**
	 * View flag indicating whether this view should have sound effects enabled
	 * for events such as clicking and touching.
	 */
	public static final int SOUND_EFFECTS_ENABLED = 0x08000000;

	/**
	 * View flag indicating whether this view should have haptic feedback
	 * enabled for events such as long presses.
	 */
	public static final int HAPTIC_FEEDBACK_ENABLED = 0x10000000;

	/* performHapticFeedback flags (AOSP values; were absent from the
	 * fork, so apps referencing them hit NoSuchFieldError). */
	public static final int FLAG_SUPPRESS_HAPTIC = 0x00000001;
	public static final int FLAG_TACTILE_FEEDBACK_DISABLED = 0x00000002;

	/**
	 * <p>Indicates that the view hierarchy should stop saving state when
	 * it reaches this view.  If state saving is initiated immediately at
	 * the view, it will be allowed.
	 * {@hide}
	 */
	static final int PARENT_SAVE_DISABLED = 0x20000000;

	/**
	 * <p>Mask for use with setFlags indicating bits used for PARENT_SAVE_DISABLED.</p>
	 * {@hide}
	 */
	static final int PARENT_SAVE_DISABLED_MASK = 0x20000000;

	/**
	 * View flag indicating whether {@link #addFocusables(ArrayList, int, int)}
	 * should add all focusable Views regardless if they are focusable in touch mode.
	 */
	public static final int FOCUSABLES_ALL = 0x00000000;

	/**
	 * View flag indicating whether {@link #addFocusables(ArrayList, int, int)}
	 * should add only Views focusable in touch mode.
	 */
	public static final int FOCUSABLES_TOUCH_MODE = 0x00000001;

	/**
	 * Use with {@link #focusSearch(int)}. Move focus to the previous selectable
	 * item.
	 */
	public static final int FOCUS_BACKWARD = 0x00000001;

	/**
	 * Use with {@link #focusSearch(int)}. Move focus to the next selectable
	 * item.
	 */
	public static final int FOCUS_FORWARD = 0x00000002;

	/**
	 * Use with {@link #focusSearch(int)}. Move focus to the left.
	 */
	public static final int FOCUS_LEFT = 0x00000011;

	/**
	 * Use with {@link #focusSearch(int)}. Move focus up.
	 */
	public static final int FOCUS_UP = 0x00000021;

	/**
	 * Use with {@link #focusSearch(int)}. Move focus to the right.
	 */
	public static final int FOCUS_RIGHT = 0x00000042;

	/**
	 * Use with {@link #focusSearch(int)}. Move focus down.
	 */
	public static final int FOCUS_DOWN = 0x00000082;

	/**
	 * Bits of {@link #getMeasuredWidthAndState()} and
	 * {@link #getMeasuredWidthAndState()} that provide the actual measured size.
	 */
	public static final int MEASURED_SIZE_MASK = 0x00ffffff;

	/**
	 * Bits of {@link #getMeasuredWidthAndState()} and
	 * {@link #getMeasuredWidthAndState()} that provide the additional state bits.
	 */
	public static final int MEASURED_STATE_MASK = 0xff000000;

	/**
	 * Bit shift of {@link #MEASURED_STATE_MASK} to get to the height bits
	 * for functions that combine both width and height into a single int,
	 * such as {@link #getMeasuredState()} and the childState argument of
	 * {@link #resolveSizeAndState(int, int, int)}.
	 */
	public static final int MEASURED_HEIGHT_STATE_SHIFT = 16;

	/**
	 * Bit of {@link #getMeasuredWidthAndState()} and
	 * {@link #getMeasuredWidthAndState()} that indicates the measured size
	 * is smaller that the space the view would like to have.
	 */
	public static final int MEASURED_STATE_TOO_SMALL = 0x01000000;

	// --- and some flags

	/**
	 * Indicates that this view has reported that it can accept the current drag's content.
	 * Cleared when the drag operation concludes.
	 * @hide
	 */
	static final int PFLAG2_DRAG_CAN_ACCEPT = 0x00000001;

	/**
	 * Indicates that this view is currently directly under the drag location in a
	 * drag-and-drop operation involving content that it can accept.  Cleared when
	 * the drag exits the view, or when the drag operation concludes.
	 * @hide
	 */
	static final int PFLAG2_DRAG_HOVERED = 0x00000002;

	/**
	 * Horizontal layout direction of this view is from Left to Right.
	 * Use with {@link #setLayoutDirection}.
	 */
	public static final int LAYOUT_DIRECTION_LTR = LayoutDirection.LTR;

	/**
	 * Horizontal layout direction of this view is from Right to Left.
	 * Use with {@link #setLayoutDirection}.
	 */
	public static final int LAYOUT_DIRECTION_RTL = LayoutDirection.RTL;

	/**
	 * Horizontal layout direction of this view is inherited from its parent.
	 * Use with {@link #setLayoutDirection}.
	 */
	public static final int LAYOUT_DIRECTION_INHERIT = LayoutDirection.INHERIT;

	/**
	 * Horizontal layout direction of this view is from deduced from the default language
	 * script for the locale. Use with {@link #setLayoutDirection}.
	 */
	public static final int LAYOUT_DIRECTION_LOCALE = LayoutDirection.LOCALE;

	/**
	 * Bit shift to get the horizontal layout direction. (bits after DRAG_HOVERED)
	 * @hide
	 */
	static final int PFLAG2_LAYOUT_DIRECTION_MASK_SHIFT = 2;

	/**
	 * Mask for use with private flags indicating bits used for horizontal layout direction.
	 * @hide
	 */
	static final int PFLAG2_LAYOUT_DIRECTION_MASK = 0x00000003 << PFLAG2_LAYOUT_DIRECTION_MASK_SHIFT;

	/**
	 * Indicates whether the view horizontal layout direction has been resolved and drawn to the
	 * right-to-left direction.
	 * @hide
	 */
	static final int PFLAG2_LAYOUT_DIRECTION_RESOLVED_RTL = 4 << PFLAG2_LAYOUT_DIRECTION_MASK_SHIFT;

	/**
	 * Indicates whether the view horizontal layout direction has been resolved.
	 * @hide
	 */
	static final int PFLAG2_LAYOUT_DIRECTION_RESOLVED = 8 << PFLAG2_LAYOUT_DIRECTION_MASK_SHIFT;

	/**
	 * Mask for use with private flags indicating bits used for resolved horizontal layout direction.
	 * @hide
	 */
	static final int PFLAG2_LAYOUT_DIRECTION_RESOLVED_MASK = 0x0000000C << PFLAG2_LAYOUT_DIRECTION_MASK_SHIFT;

	/**
	 * Indicates no axis of view scrolling.
	 */
	public static final int SCROLL_AXIS_NONE = 0;

	/**
	 * Indicates scrolling along the horizontal axis.
	 */
	public static final int SCROLL_AXIS_HORIZONTAL = 1 << 0;

	/**
	 * Indicates scrolling along the vertical axis.
	 */
	public static final int SCROLL_AXIS_VERTICAL = 1 << 1;

	/**
	 * Always allow a user to over-scroll this view, provided it is a
	 * view that can scroll.
	 *
	 * @see #getOverScrollMode()
	 * @see #setOverScrollMode(int)
	 */
	public static final int FOCUS_BEFORE_DESCENDANTS = 0x00020000;
	public static final int FOCUS_AFTER_DESCENDANTS = 0x00040000;
	public static final int FOCUS_BLOCK_DESCENDANTS = 0x00010000;

	public static final int OVER_SCROLL_ALWAYS = 0;

	/**
	 * Allow a user to over-scroll this view only if the content is large
	 * enough to meaningfully scroll, provided it is a view that can scroll.
	 *
	 * @see #getOverScrollMode()
	 * @see #setOverScrollMode(int)
	 */
	public static final int OVER_SCROLL_IF_CONTENT_SCROLLS = 1;

	/**
	 * Never allow a user to over-scroll this view.
	 *
	 * @see #getOverScrollMode()
	 * @see #setOverScrollMode(int)
	 */
	public static final int OVER_SCROLL_NEVER = 2;

	// --- apparently there's more...

	@Deprecated
	public static final int STATUS_BAR_HIDDEN = 1; // 0x1

	@Deprecated
	public static final int STATUS_BAR_VISIBLE = 0; // 0x0

	public static final int SYSTEM_UI_FLAG_FULLSCREEN = 4; // 0x4

	public static final int SYSTEM_UI_FLAG_HIDE_NAVIGATION = 2; // 0x2

	public static final int SYSTEM_UI_FLAG_IMMERSIVE = 2048; // 0x800

	public static final int SYSTEM_UI_FLAG_IMMERSIVE_STICKY = 4096; // 0x1000

	public static final int SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN = 1024; // 0x400

	public static final int SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION = 512; // 0x200

	public static final int SYSTEM_UI_FLAG_LAYOUT_STABLE = 256; // 0x100

	public static final int SYSTEM_UI_FLAG_LOW_PROFILE = 1; // 0x1

	public static final int SYSTEM_UI_FLAG_VISIBLE = 0; // 0x0

	public static final int SYSTEM_UI_LAYOUT_FLAGS = 1536; // 0x600

	public static final int TEXT_ALIGNMENT_CENTER = 4; // 0x4

	public static final int TEXT_ALIGNMENT_GRAVITY = 1; // 0x1

	public static final int TEXT_ALIGNMENT_INHERIT = 0; // 0x0

	public static final int TEXT_ALIGNMENT_TEXT_END = 3; // 0x3

	public static final int TEXT_ALIGNMENT_TEXT_START = 2; // 0x2

	public static final int TEXT_ALIGNMENT_VIEW_END = 6; // 0x6

	public static final int TEXT_ALIGNMENT_VIEW_START = 5; // 0x5

	public static final int TEXT_DIRECTION_ANY_RTL = 2; // 0x2

	public static final int TEXT_DIRECTION_FIRST_STRONG = 1; // 0x1

	public static final int TEXT_DIRECTION_INHERIT = 0; // 0x0

	public static final int TEXT_DIRECTION_LOCALE = 5; // 0x5

	public static final int TEXT_DIRECTION_LTR = 3; // 0x3

	public static final int TEXT_DIRECTION_RTL = 4; // 0x4

	public static final Property<View, Float> SCALE_X = new FloatProperty<View>("scaleX") {
		@Override
		public void setValue(View object, float value) {
			object.setScaleX(value);
		}
		@Override
		public Float get(View object) {
			return object.getScaleX();
		}
	};

	public static final Property<View, Float> SCALE_Y = new FloatProperty<View>("scaleY") {
		@Override
		public void setValue(View object, float value) {
			object.setScaleY(value);
		}
		@Override
		public Float get(View object) {
			return object.getScaleY();
		}
	};

	// --- end of constants from android source

	// --- interfaces from android source
	public interface OnTouchListener {
		/**
		 * Called when a touch event is dispatched to a view. This allows listeners to
		 * get a chance to respond before the target view.
		 *
		 * @param v The view the touch event has been dispatched to.
		 * @param event The MotionEvent object containing full information about
		 *        the event.
		 * @return True if the listener has consumed the event, false otherwise.
		 */
		boolean onTouch(View v, MotionEvent event);
	}

	public interface OnScrollChangeListener {
		void onScrollChange(View v, int scrollX, int scrollY, int oldScrollX, int oldScrollY);
	}

	public interface OnClickListener {
		void onClick(View v);
	}

	public interface OnAttachStateChangeListener {
		public void onViewAttachedToWindow(View v);
		public void onViewDetachedFromWindow(View v);
	}

	public interface OnGenericMotionListener {
		/* AOSP's shape (queue item 295) — apps implement this interface,
		 * it had been a TODO stub with no method here. */
		boolean onGenericMotion(View v, MotionEvent event);
	}

	public interface OnSystemUiVisibilityChangeListener {
		/* AOSP's shape (queue item 277) — apps implement this interface,
		 * it had been a TODO stub here. Fires when the system bars'
		 * visibility changes; ATL has no system bars (noted at
		 * setSystemUiVisibility), so it can never fire — see
		 * setOnSystemUiVisibilityChangeListener. */
		void onSystemUiVisibilityChange(int visibility);
	}

	public static interface OnKeyListener {
		boolean onKey(View v, int keyCode, KeyEvent event);
	}

	public interface OnLongClickListener {
		public boolean onLongClick(View v);
	}

	public interface OnHoverListener {
		/* AOSP signature (was `// TODO`, so the listener a hover chain
		 * should call had no method to call - queue item 158). */
		boolean onHover(View v, MotionEvent event);
	}

	public interface OnApplyWindowInsetsListener {
		/* AOSP callback (the body here used to be `// TODO`, so apps
		 * registering a listener had it stored nowhere and called
		 * nowhere). */
		WindowInsets onApplyWindowInsets(View v, WindowInsets insets);
	}

	public interface OnLayoutChangeListener {
		public void onLayoutChange(View view, int left, int top, int right, int bottom, int oldLeft, int oldTop, int oldRight, int oldBottom);
	}

	public interface OnUnhandledKeyEventListener {
		// TODO
	}

	public interface OnFocusChangeListener {
		/* AOSP signature (was `// TODO`, i.e. no method at all, so a
		 * stored listener had nothing to be called on - queue item 187).
		 * App classes implement this shape already. */
		void onFocusChange(View v, boolean hasFocus);
	}

	public interface OnCreateContextMenuListener {
		/**
		 * Called when the context menu for this view is being built. It is not
		 * safe to hold onto the menu after this method returns.
		 *
		 * @param menu The context menu that is being built
		 * @param v The view for which the context menu is being built
		 * @param menuInfo Extra information about the item for which the
		 *            context menu should be shown. This information will vary
		 *            depending on the class of v.
		 */
		/* Declared with AOSP's shape (queue item 244) — the parameter type
		 * here is ContextMenu.ContextMenuInfo, where AOSP names its own
		 * View.ContextMenuInfo (a class the fork does not declare because
		 * it has no context-menu machinery to fill one; see
		 * setOnCreateContextMenuListener below). */
		void onCreateContextMenu(ContextMenu menu, View v, ContextMenu.ContextMenuInfo menuInfo);
	}

	public interface OnDragListener {}
	// --- end of interfaces

	// --- subclasses

	/**
	 * A MeasureSpec encapsulates the layout requirements passed from parent to child.
	 * Each MeasureSpec represents a requirement for either the width or the height.
	 * A MeasureSpec is comprised of a size and a mode. There are three possible
	 * modes:
	 * <dl>
	 * <dt>UNSPECIFIED</dt>
	 * <dd>
	 * The parent has not imposed any constraint on the child. It can be whatever size
	 * it wants.
	 * </dd>
	 *
	 * <dt>EXACTLY</dt>
	 * <dd>
	 * The parent has determined an exact size for the child. The child is going to be
	 * given those bounds regardless of how big it wants to be.
	 * </dd>
	 *
	 * <dt>AT_MOST</dt>
	 * <dd>
	 * The child can be as large as it wants up to the specified size.
	 * </dd>
	 * </dl>
	 *
	 * MeasureSpecs are implemented as ints to reduce object allocation. This class
	 * is provided to pack and unpack the &lt;size, mode&gt; tuple into the int.
	 */
	public static class MeasureSpec {
		private static final int MODE_SHIFT = 30;
		private static final int MODE_MASK = 0x3 << MODE_SHIFT;

		/**
		 * Measure specification mode: The parent has not imposed any constraint
		 * on the child. It can be whatever size it wants.
		 */
		public static final int UNSPECIFIED = 0 << MODE_SHIFT;

		/**
		 * Measure specification mode: The parent has determined an exact size
		 * for the child. The child is going to be given those bounds regardless
		 * of how big it wants to be.
		 */
		public static final int EXACTLY = 1 << MODE_SHIFT;

		/**
		 * Measure specification mode: The child can be as large as it wants up
		 * to the specified size.
		 */
		public static final int AT_MOST = 2 << MODE_SHIFT;

		/**
		 * Creates a measure specification based on the supplied size and mode.
		 *
		 * The mode must always be one of the following:
		 * <ul>
		 *  <li>{@link android.view.View.MeasureSpec#UNSPECIFIED}</li>
		 *  <li>{@link android.view.View.MeasureSpec#EXACTLY}</li>
		 *  <li>{@link android.view.View.MeasureSpec#AT_MOST}</li>
		 * </ul>
		 *
		 * <p><strong>Note:</strong> On API level 17 and lower, makeMeasureSpec's
		 * implementation was such that the order of arguments did not matter
		 * and overflow in either value could impact the resulting MeasureSpec.
		 * {@link android.widget.RelativeLayout} was affected by this bug.
		 * Apps targeting API levels greater than 17 will get the fixed, more strict
		 * behavior.</p>
		 *
		 * @param size the size of the measure specification
		 * @param mode the mode of the measure specification
		 * @return the measure specification based on size and mode
		 */
		public static int makeMeasureSpec(int size, int mode) {
			if (sUseBrokenMakeMeasureSpec) {
				return size + mode;
			} else {
				return (size & ~MODE_MASK) | (mode & MODE_MASK);
			}
		}

		/**
		 * Extracts the mode from the supplied measure specification.
		 *
		 * @param measureSpec the measure specification to extract the mode from
		 * @return {@link android.view.View.MeasureSpec#UNSPECIFIED},
		 *		 {@link android.view.View.MeasureSpec#AT_MOST} or
		 *		 {@link android.view.View.MeasureSpec#EXACTLY}
		 */
		public static int getMode(int measureSpec) {
			return (measureSpec & MODE_MASK);
		}

		/**
		 * Extracts the size from the supplied measure specification.
		 *
		 * @param measureSpec the measure specification to extract the size from
		 * @return the size in pixels defined in the supplied measure specification
		 */
		public static int getSize(int measureSpec) {
			return (measureSpec & ~MODE_MASK);
		}

		static int adjust(int measureSpec, int delta) {
			return makeMeasureSpec(getSize(measureSpec + delta), getMode(measureSpec));
		}

		/**
		 * Returns a String representation of the specified measure
		 * specification.
		 *
		 * @param measureSpec the measure specification to convert to a String
		 * @return a String with the following format: "MeasureSpec: MODE SIZE"
		 */
		public static String toString(int measureSpec) {
			int mode = getMode(measureSpec);
			int size = getSize(measureSpec);

			StringBuilder sb = new StringBuilder("MeasureSpec: ");

			if (mode == UNSPECIFIED)
				sb.append("UNSPECIFIED ");
			else if (mode == EXACTLY)
				sb.append("EXACTLY ");
			else if (mode == AT_MOST)
				sb.append("AT_MOST ");
			else
				sb.append(mode).append(" ");

			sb.append(size);
			return sb.toString();
		}
	}

	/* Copyright (C) 2006 The Android Open Source Project */
	public static class DragShadowBuilder {
		private final WeakReference<View> mView;

		public DragShadowBuilder(View view) {
			mView = new WeakReference<View>(view);
		}

		public DragShadowBuilder() {
			mView = new WeakReference<View>(null);
		}

		final public View getView() {
			return mView.get();
		}

		public void onProvideShadowMetrics(Point outShadowSize, Point outShadowTouchPoint) {
			final View view = mView.get();
			if (view != null) {
				outShadowSize.set(view.getWidth(), view.getHeight());
				outShadowTouchPoint.set(outShadowSize.x / 2, outShadowSize.y / 2);
			} else {
				Slog.e("View", "Asked for drag thumb metrics but no view");
			}
		}

		public void onDrawShadow(Canvas canvas) {
			final View view = mView.get();
			if (view != null) {
				view.draw(canvas);
			} else {
				Slog.e("View", "Asked to draw drag shadow but no view");
			}
		}
	}

	// --- end of subclasses

	public int id = NO_ID;
	private int system_ui_visibility = 0;
	public ViewParent parent;
	public AttributeSet attrs;
	protected ViewGroup.LayoutParams layout_params;
	private Context context;
	private Map<Integer, Object> tags = new HashMap<>();
	private Object tag;
	int gravity = -1; // fallback gravity for layout children

	int measuredWidth = 0;
	int measuredHeight = 0;

	private int left = 0;
	private int top = 0;
	private int right = 0;
	private int bottom = 0;

	private float translationX = 0;
	private float translationY = 0;

	private int scrollX = 0;
	private int scrollY = 0;

	ViewTreeObserver floating_observer = null;

	public long widget; // pointer

	private int oldWidthMeasureSpec = -1;
	private int oldHeightMeasureSpec = -1;
	private boolean layoutRequested = true;
	private int oldWidth;
	private int oldHeight;
	protected boolean haveCustomMeasure = true;
	public boolean disallowIntercept = false;

	private int visibility = View.VISIBLE;
	private float alpha = 1.0f;
	private static final int PFLAG_SELECTED = 0x00000004;
	private static final int PFLAG_DRAWABLE_STATE_DIRTY = 0x00000400;
	private static final int PFLAG_PRESSED = 0x00004000;
	private static final int PFLAG_HOVERED = 0x10000000;
	private static final int PFLAG_ACTIVATED = 0x40000000;
	private int privateFlags;
	private int[] drawableState;
	private boolean duplicateParentState = false;

	// android:fitsSystemWindows / setFitsSystemWindows(). ATL has no system
	// windows (no status or navigation bar to inset), so this only records the
	// state apps ask for; it used to be hardcoded true, which made apps that
	// assert !getFitsSystemWindows() before installing a window insets listener
	// (e.g. Gramophone's enableEdgeToEdgePaddingListener) abort at startup.
	private boolean fitsSystemWindows = false;

	private Drawable background;
	private CharSequence contentDescription;
	private AccessibilityDelegate accessibilityDelegate;
	private boolean willNotDraw = false;
	private int layoutDirection = LAYOUT_DIRECTION_LTR;
	private int backgroundTint = 0;
	private int mForegroundGravity = Gravity.START | Gravity.TOP;

	private int minWidth = 0;
	private int minHeight = 0;

	protected int paddingLeft = 0;
	protected int paddingTop = 0;
	protected int paddingRight = 0;
	protected int paddingBottom = 0;

	private boolean atl_enabled = true;
	private boolean atl_focusable = true;
	/* AOSP default is false; ATL keeps the state so hasFocusable and the
	 * API-26 getters answer truthfully (queue item 108). */
	private boolean atl_focusable_in_touch_mode = false;

	private Handler handler;

	private CopyOnWriteArrayList<OnLayoutChangeListener> on_layout_change_listeners;

	private boolean clickable = false;
	private boolean longclickable = false;

	public static final Property<View, Float> TRANSLATION_X = new Property<View, Float>(Float.class, "translationX") {
		@Override
		public Float get(View view) {
			return view.getTranslationX();
		}
		@Override
		public void set(View view, Float value) {
			view.setTranslationX(value);
		}
	};

	public static final Property<View, Float> TRANSLATION_Y = new Property<View, Float>(Float.class, "translationY") {
		@Override
		public Float get(View view) {
			return view.getTranslationY();
		}
		@Override
		public void set(View view, Float value) {
			view.setTranslationY(value);
		}
	};

	public static final Property<View, Float> TRANSLATION_Z = new Property<View, Float>(Float.class, "translationZ") {
		@Override
		public Float get(View view) {
			return view.getTranslationZ();
		}
		@Override
		public void set(View view, Float value) {
			view.setTranslationZ(value);
		}
	};

	public static final Property<View, Float> ALPHA = new Property<View, Float>(Float.class, "alpha") {
		@Override
		public Float get(View view) {
			return view.getAlpha();
		}
		@Override
		public void set(View view, Float value) {
			view.setAlpha(value);
		}
	};

	public static final Property<View, Float> ROTATION = new FloatProperty<View>("rotation") {
		@Override
		public void setValue(View object, float value) {
			object.setRotation(value);
		}
		@Override
		public Float get(View object) {
			return object.getRotation();
		}
	};

	public View(Context context, AttributeSet attrs) {
		this(context, attrs, 0);
	}

	public View(Context context, AttributeSet attrs, int defStyle) {
		this(context, attrs, defStyle, 0);
	}

	public View(Context context, AttributeSet attrs, int defStyle, int defStyleRes) {
		this.context = context;

		widget = native_constructor(context, attrs);

		TypedArray a = context.obtainStyledAttributes(attrs, com.android.internal.R.styleable.View, defStyle, defStyleRes);
		this.id = a.getResourceId(com.android.internal.R.styleable.View_id, View.NO_ID);
		if (a.hasValue(com.android.internal.R.styleable.View_backgroundTint))
			backgroundTint = a.getColor(com.android.internal.R.styleable.View_backgroundTint, 0);
		if (a.hasValue(com.android.internal.R.styleable.View_background)) {
			try {
				Drawable background = a.getDrawable(com.android.internal.R.styleable.View_background);

				if (background != null) {
					setBackground(background);
				}
			} catch (Exception e) {
				e.printStackTrace();
			}
		}
		if (a.hasValue(com.android.internal.R.styleable.View_focusable)) {
			atl_focusable = a.getBoolean(com.android.internal.R.styleable.View_focusable, true);
		}
		if (a.hasValue(com.android.internal.R.styleable.View_visibility)) {
			visibility = VISIBILITY_FLAGS[a.getInt(com.android.internal.R.styleable.View_visibility, 0)];
			native_setVisibility(widget, visibility, alpha, atl_enabled);
		}

		if (a.hasValue(com.android.internal.R.styleable.View_minWidth))
			minWidth = a.getDimensionPixelSize(com.android.internal.R.styleable.View_minWidth, 0);

		if (a.hasValue(com.android.internal.R.styleable.View_minHeight))
			minHeight = a.getDimensionPixelSize(com.android.internal.R.styleable.View_minHeight, 0);

		int padding = a.getDimensionPixelSize(com.android.internal.R.styleable.View_padding, -1);

		int paddingVertical = a.getDimensionPixelSize(com.android.internal.R.styleable.View_paddingVertical, -1);
		int paddingHorizontal = a.getDimensionPixelSize(com.android.internal.R.styleable.View_paddingHorizontal, -1);

		int paddingStart = a.getDimensionPixelSize(com.android.internal.R.styleable.View_paddingStart, -1);
		int paddingEnd = a.getDimensionPixelSize(com.android.internal.R.styleable.View_paddingEnd, -1);

		if (padding >= 0) {
			paddingLeft = padding;
			paddingTop = padding;
			paddingRight = padding;
			paddingBottom = padding;
		} else {
			if (paddingVertical >= 0) {
				paddingTop = paddingVertical;
				paddingBottom = paddingVertical;
			} else {
				paddingTop = a.getDimensionPixelSize(com.android.internal.R.styleable.View_paddingTop, 0);
				paddingBottom = a.getDimensionPixelSize(com.android.internal.R.styleable.View_paddingBottom, 0);
			}

			if (paddingHorizontal >= 0) {
				paddingLeft = paddingHorizontal;
				paddingRight = paddingHorizontal;
			} else {
				paddingLeft = a.getDimensionPixelSize(com.android.internal.R.styleable.View_paddingLeft, 0);
				paddingRight = a.getDimensionPixelSize(com.android.internal.R.styleable.View_paddingRight, 0);

				if (paddingStart >= 0)
					paddingLeft = paddingStart;

				if (paddingEnd >= 0)
					paddingRight = paddingEnd;
			}
		}

		native_setPadding(widget, paddingLeft, paddingTop, paddingRight, paddingBottom);

		if (a.hasValue(com.android.internal.R.styleable.View_tag))
			tag = a.getText(com.android.internal.R.styleable.View_tag);

		if (a.hasValue(com.android.internal.R.styleable.View_textAlignment)) {
			int textAlignment = a.getInt(com.android.internal.R.styleable.View_textAlignment, 0);
			setTextAlignment(textAlignment);
		}

		String handlerName = a.getString(com.android.internal.R.styleable.View_onClick);
		if (handlerName != null)
			setOnClickListener(new DeclaredOnClickListener(this, handlerName));

		if (a.hasValue(com.android.internal.R.styleable.View_stateListAnimator)) {
			int resId = a.getResourceId(com.android.internal.R.styleable.View_stateListAnimator, 0);
			setStateListAnimator(AnimatorInflater.loadStateListAnimator(context, resId));
		}
		duplicateParentState = a.getBoolean(com.android.internal.R.styleable.View_duplicateParentState, false);
		fitsSystemWindows = a.getBoolean(com.android.internal.R.styleable.View_fitsSystemWindows, false);
		a.recycle();
	}

	/* Copyright (C) 2006 The Android Open Source Project */
	protected int[] onCreateDrawableState(int extraSpace) {
		if (duplicateParentState && parent instanceof View) {
			return ((View)parent).onCreateDrawableState(extraSpace);
		}

		int[] drawableState;

		int viewStateIndex = 0;
		if (isPressed())
			viewStateIndex |= StateSet.VIEW_STATE_PRESSED;
		if (isEnabled())
			viewStateIndex |= StateSet.VIEW_STATE_ENABLED;
		if (isFocused())
			viewStateIndex |= StateSet.VIEW_STATE_FOCUSED;
		if (isSelected())
			viewStateIndex |= StateSet.VIEW_STATE_SELECTED;
		if (hasWindowFocus())
			viewStateIndex |= StateSet.VIEW_STATE_WINDOW_FOCUSED;
		if (isActivated())
			viewStateIndex |= StateSet.VIEW_STATE_ACTIVATED;
		if (isHovered())
			viewStateIndex |= StateSet.VIEW_STATE_HOVERED;

		drawableState = StateSet.get(viewStateIndex);

		if (extraSpace == 0) {
			return drawableState;
		}

		final int[] fullState;
		if (drawableState != null) {
			fullState = new int[drawableState.length + extraSpace];
			System.arraycopy(drawableState, 0, fullState, 0, drawableState.length);
		} else {
			fullState = new int[extraSpace];
		}

		return fullState;
	}

	/* Copyright (C) 2006 The Android Open Source Project */
	protected static int[] mergeDrawableStates(int[] baseState, int[] additionalState) {
		final int N = baseState.length;
		int i = N - 1;
		while (i >= 0 && baseState[i] == 0) {
			i--;
		}
		System.arraycopy(additionalState, 0, baseState, i + 1, additionalState.length);
		return baseState;
	}

	public View findViewById(int id) {
		if (this.id == id)
			return this;
		else
			return null;
	}

	@Export
	public void onDraw(Canvas canvas) {
		if (canvas instanceof GskCanvas)
			native_drawContent(widget, ((GskCanvas)canvas).snapshot);
	}

	@Export
	protected void dispatchDraw(Canvas canvas) {
		if (canvas instanceof GskCanvas)
			native_drawChildren(widget, ((GskCanvas)canvas).snapshot);
	}

	@Export
	public void draw(Canvas canvas) {
		/* queue item 257: AOSP clears its dirty flags as the view draws;
		 * this is ATL's draw entry (called from GTK's draw pass). */
		atl_dirty = false;
		if (canvas instanceof GskCanvas)
			native_drawBackground(widget, ((GskCanvas)canvas).snapshot);
		onDraw(canvas);
		// HACK: catch non critical exceptions happening in some composeUI apps
		try {
			dispatchDraw(canvas);
		} catch (IllegalArgumentException e) {
			e.printStackTrace();
		}
	}

	public View(Context context) {
		this(context, null);
	}

	public final ViewParent getParent() {
		return parent;
	}

	@Export
	public void setLayoutParams(ViewGroup.LayoutParams params) {
		if (params == null) {
			throw new NullPointerException("Layout parameters cannot be null");
		}
		params.resolveLayoutDirection(getLayoutDirection());

		int gravity = params.gravity;
		if (gravity == -1 && parent instanceof View)
			gravity = ((View)parent).gravity;

		int leftMargin = 0;
		int topMargin = 0;
		int rightMargin = 0;
		int bottomMargin = 0;
		if (params instanceof ViewGroup.MarginLayoutParams) {
			leftMargin = ((ViewGroup.MarginLayoutParams)params).leftMargin;
			topMargin = ((ViewGroup.MarginLayoutParams)params).topMargin;
			rightMargin = ((ViewGroup.MarginLayoutParams)params).rightMargin;
			bottomMargin = ((ViewGroup.MarginLayoutParams)params).bottomMargin;
		}

		native_setLayoutParams(widget, params.width, params.height, gravity, params.weight, leftMargin, topMargin, rightMargin, bottomMargin);

		layout_params = params;
	}

	public ViewGroup.LayoutParams getLayoutParams() {
		return layout_params;
	}

	@Export
	protected final void setMeasuredDimension(int measuredWidth, int measuredHeight) {
		this.measuredWidth = measuredWidth;
		this.measuredHeight = measuredHeight;
	}

	public Resources getResources() {
		return this.context.getResources();
	}

	public void setGravity(int gravity) {
		this.gravity = gravity;
	}

	private OnTouchListener on_touch_listener = null;

	@Export
	public boolean onTouchEventInternal(MotionEvent event, boolean handle_gestures) {
		boolean handled = false;
		if (on_touch_listener != null)
			handled = on_touch_listener.onTouch(this, event);

		if (!handled)
			handled = onTouchEvent(event);

		if (handle_gestures && !handled && event.getAction() == MotionEvent.ACTION_UP)
			handled = performClick();

		return handled;
	}

	private class LongClickRunnable implements Runnable {
		private boolean long_click_done = false;

		@Override
		public void run() {
			long_click_done = performLongClick();
		}
	}

	private LongClickRunnable pendingLongClick = null;

	@Export
	public boolean onTouchEvent(MotionEvent event) {
		if (isClickable() || isLongClickable()) {
			switch (event.getActionMasked()) {
				case MotionEvent.ACTION_DOWN:
					if (isLongClickable()) {
						cancelLongPress();
						pendingLongClick = new LongClickRunnable();
						postDelayed(pendingLongClick, ViewConfiguration.getLongPressTimeout());
					}
					return true;
				case MotionEvent.ACTION_MOVE:
					if (event.getX() < 0 || event.getY() < 0 || event.getX() > getWidth() || event.getY() > getHeight())
						cancelLongPress();
					return true;
				case MotionEvent.ACTION_UP:
					boolean long_click_done = pendingLongClick != null && pendingLongClick.long_click_done;
					cancelLongPress();
					if (isClickable() && !long_click_done && event.getX() >= 0 && event.getY() >= 0 && event.getX() <= getWidth() && event.getY() <= getHeight())
						return performClick();
					return true;
				case MotionEvent.ACTION_CANCEL:
					cancelLongPress();
					return true;
			}
		}

		return false;
	}

	public void setOnTouchListener(OnTouchListener l) {
		nativeSetOnTouchListener(widget);
		on_touch_listener = l;
	}
	protected native void nativeSetOnTouchListener(long widget);
	private OnClickListener on_click_listener = null;
	public void setOnClickListener(OnClickListener l) {
		if (!isClickable())
			setClickable(true);
		nativeSetOnClickListener(widget);
		on_click_listener = l;
	}
	protected native void nativeSetOnClickListener(long widget);

	private OnScrollChangeListener on_scroll_change_listener = null;
	public void setOnScrollChangeListener(OnScrollChangeListener l) {
		on_scroll_change_listener = l;
	}

	/* System-UI visibility listener (queue item 277): AOSP stores it in
	 * its ListenerInfo and fires it whenever the system bars' visibility
	 * changes. ATL has no system bars (noted at setSystemUiVisibility),
	 * so the visibility is always 0 and can never change — the listener
	 * is kept as real API state (callers and androidx read listener pairs
	 * back), with nothing that could fire it. */
	private OnSystemUiVisibilityChangeListener on_system_ui_visibility_listener = null;

	public void setOnSystemUiVisibilityChangeListener(OnSystemUiVisibilityChangeListener l) {
		on_system_ui_visibility_listener = l;
	}
	public final int getWidth() {
		return right - left;
	}
	public final int getHeight() {
		return bottom - top;
	}

	protected native long native_constructor(Context context, AttributeSet attrs); // will create a custom GtkWidget with a custom drawing function
	public native void native_setLayoutParams(long widget, int width, int height, int gravity, float weight, int leftMargin, int topMargin, int rightMargin, int bottomMargin);
	protected native void native_destructor(long widget);
	/**
	 * We decide between simple widgets which handles MEASURE_SPEC_AT_MOST the same way as
	 * MEASURE_SPEC_EXACTLY, and complex widgets which handles MEASURE_SPEC_AT_MOST by measuring the content
	 */
	protected native void native_measure(long widget, int widthMeasureSpec, int heightMeasureSpec);
	protected native void native_layout(long widget, int l, int t, int r, int b);
	protected native void native_requestLayout(long widget);
	protected native void native_setBackgroundDrawable(long widget, long paintable);
	protected native void native_queueAllocate(long widget);

	protected native void native_addClass(long widget, String className);
	protected native void native_removeClass(long widget, String className);

	protected native void native_addClasses(long widget, String[] classNames);
	protected native void native_removeClasses(long widget, String[] classNames);

	protected native void native_drawBackground(long widget, long snapshot);
	protected native void native_drawContent(long widget, long snapshot);
	protected void native_drawChildren(long widget, long snapshot) {} // override in ViewGroup

	/* should be called from onTouchEvent() of GtkWidget implementations */
	protected native boolean native_gtkEventPropagation();
	/* used by the DecorView to register a GtkEventController and wire it to dispatchTouchEvent() */
	protected native void native_registerTouchEventPropagation(long widget);

	// --- stubs

	public void setContentDescription(CharSequence contentDescription) {
		/* AOSP keeps it for a11y; ATL has no a11y backend, but apps and
		 * libraries read getContentDescription() back (state save/restore,
		 * custom a11y code), so the value is kept. */
		this.contentDescription = contentDescription;
	}

	public void setId(int id) {
		this.id = id;
	}

	/* Key listener (queue item 191): the setter was empty, so a listener
	 * registered for keyboard input was stored nowhere and called
	 * nowhere. AOSP's dispatchKeyEventNoClient asks mOnKeyListener for
	 * the event before the view handles it itself. */
	private OnKeyListener on_key_listener = null;

	public void setOnKeyListener(OnKeyListener l) {
		on_key_listener = l;
	}

	public void setFocusable(boolean focusable) {
		atl_focusable = focusable;
	}
	public void setFocusable(int focusable) {
		/* AOSP ViewFocusFlags: NOT_FOCUSABLE=0, FOCUSABLE=1,
		 * FOCUSABLE_AUTO=-1 (framework decides); ATL has no automatic
		 * focus policy, AUTO falls back to ATL's default (focusable). */
		if (focusable == -1) /* FOCUSABLE_AUTO */
			atl_focusable = true;
		else
			atl_focusable = focusable == 1; /* FOCUSABLE */
	}
	public void setFocusableInTouchMode(boolean focusableInTouchMode) {
		atl_focusable_in_touch_mode = focusableInTouchMode;
	}

	/* AOSP API 26 getter pair for the flags above; isFocusable() already
	 * returns ATL's focusable state. */
	public boolean getFocusable() {
		return atl_focusable;
	}

	public boolean getFocusableInTouchMode() {
		return atl_focusable_in_touch_mode;
	}
	public final boolean requestFocus() {
		return requestFocus(View.FOCUS_DOWN);
	}
	public final boolean requestFocus(int direction) {
		return requestFocus(direction, null);
	}
	public boolean requestFocus(int direction, Rect previouslyFocusedRect) {
		if (atl_enabled && atl_focusable && visibility == VISIBLE) {
			nativeRequestFocus(widget, direction);
			return true;
		}
		return false;
	}
	public final boolean requestFocusFromTouch() {
		/* AOSP leaves touch mode first (ViewRootImpl.ensureVisible +
		 * clearGlobalFocusCount) and then takes focus with FOCUS_DOWN.
		 * ATL has no touch-mode state to leave: isInTouchMode() is false
		 * here because focus is owned by GTK (queue item 88's finding),
		 * which is exactly the branch AOSP takes when not in touch mode,
		 * so the fork's requestFocus(FOCUS_DOWN) is the faithful path
		 * (queue item 205's TODO resolved, not left dangling). */
		return requestFocus(View.FOCUS_DOWN);
	}
	private native void nativeRequestFocus(long widget, int direction);

	private native void nativeSetFullscreen(long widget, boolean fullscreen);

	public void setSystemUiVisibility(int visibility) {
		/* LAYOUT_FULLSCREEN mode would make the app window continue behind semi-transparent status bar.
		   Such a mode doesn't exist on desktop Linux, so we just use normal fullscreen for both modes. */
		nativeSetFullscreen(widget, (visibility & (View.SYSTEM_UI_FLAG_FULLSCREEN | SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN)) != 0);
		system_ui_visibility = visibility;
	}
	public int getSystemUiVisibility() {
		return system_ui_visibility;
	};

	@Export
	protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
		if (haveCustomMeasure) // calling native_measure here would create infinite loop
			setMeasuredDimension(getDefaultSize(getSuggestedMinimumWidth(), widthMeasureSpec), getDefaultSize(getSuggestedMinimumHeight(), heightMeasureSpec));
		else
			native_measure(widget, widthMeasureSpec, heightMeasureSpec);
	}

	/* Copyright (C) 2006 The Android Open Source Project */
	public void setPressed(boolean pressed) {
		final boolean needsRefresh = pressed != ((privateFlags & PFLAG_PRESSED) == PFLAG_PRESSED);

		if (pressed) {
			privateFlags |= PFLAG_PRESSED;
		} else {
			privateFlags &= ~PFLAG_PRESSED;
		}

		if (needsRefresh) {
			refreshDrawableState();
		}
		dispatchSetPressed(pressed);
	}

	/* The overridable hook AOSP's setPressed() calls (ViewGroup overrides
	 * it to propagate, and the fork's ViewGroup already does). AOSP's own
	 * body re-applies the flag, refreshes the drawable state and
	 * invalidates — that last part was missing here, so a pressed state
	 * changed Android-side state without ever scheduling the repaint that
	 * shows it (queue item 260). A subclass that overrides this and calls
	 * super now gets AOSP's behaviour, which setPressed alone did not
	 * provide. */
	protected void dispatchSetPressed(boolean pressed) {
		if (pressed) {
			privateFlags |= PFLAG_PRESSED;
		} else {
			privateFlags &= ~PFLAG_PRESSED;
		}
		refreshDrawableState();
		invalidate();
	}

	public boolean isPressed() {
		return (privateFlags & PFLAG_PRESSED) == PFLAG_PRESSED;
	}

	/* Copyright (C) 2006 The Android Open Source Project */
	private void resetPressedState() {
		if (!isEnabled())
			return;

		if (isPressed())
			setPressed(false);
	}

	/* Copyright (C) 2006 The Android Open Source Project */
	public void setSelected(boolean selected) {
		if (((privateFlags & PFLAG_SELECTED) != 0) != selected) {
			privateFlags = (privateFlags & ~PFLAG_SELECTED) | (selected ? PFLAG_SELECTED : 0);
			if (!selected)
				resetPressedState();
			invalidate();
			refreshDrawableState();
			dispatchSetSelected(selected);
		}
	}

	protected void dispatchSetSelected(boolean selected) {}

	public boolean isSelected() {
		return (privateFlags & PFLAG_SELECTED) != 0;
	}

	/* Copyright (C) 2006 The Android Open Source Project */
	public void setActivated(boolean activated) {
		if (((privateFlags & PFLAG_ACTIVATED) != 0) != activated) {
			privateFlags = (privateFlags & ~PFLAG_ACTIVATED) | (activated ? PFLAG_ACTIVATED : 0);
			invalidate();
			refreshDrawableState();
			dispatchSetActivated(activated);
		}
	}

	protected void dispatchSetActivated(boolean activated) {}

	public boolean isActivated() {
		return (privateFlags & PFLAG_ACTIVATED) != 0;
	}

	/* Copyright (C) 2006 The Android Open Source Project */
	public void setHovered(boolean hovered) {
		if (hovered) {
			if ((privateFlags & PFLAG_HOVERED) == 0) {
				privateFlags |= PFLAG_HOVERED;
				refreshDrawableState();
				onHoverChanged(true);
			}
		} else {
			if ((privateFlags & PFLAG_HOVERED) != 0) {
				privateFlags &= ~PFLAG_HOVERED;
				refreshDrawableState();
				onHoverChanged(false);
			}
		}
	}

	public void onHoverChanged(boolean hovered) {}

	public boolean isHovered() {
		return (privateFlags & PFLAG_HOVERED) != 0;
	}

	public native Window native_get_window(long widget);
	public ViewTreeObserver getViewTreeObserver() {
		Window window = native_get_window(widget);
		if (window != null) {
			if (window.view_tree_observer == null)
				window.view_tree_observer = new ViewTreeObserver(window);
			return window.view_tree_observer;
		} else {
			floating_observer = new ViewTreeObserver(null);
			return floating_observer;
		}
	}

	protected void onFinishInflate() {}

	public void invalidateDrawable(Drawable drawable) {
		invalidate();
	}

	public void scheduleDrawable(Drawable drawable, Runnable runnable, long time) {
		/* AOSP posts the frame callbacks with the drawable as the handler
		 * token so unscheduleDrawable(drawable) can drop all of them. */
		if (runnable != null)
			getHandler().postAtTime(runnable, drawable, time - SystemClock.uptimeMillis());
	}

	public void unscheduleDrawable(Drawable drawable, Runnable runnable) {
		if (runnable != null && drawable != null)
			getHandler().removeCallbacks(runnable, drawable);
	}

	public void unscheduleDrawable(Drawable drawable) {
		if (drawable != null)
			getHandler().removeCallbacksAndMessages(drawable);
	}

	public void invalidate(Rect dirty) {
		invalidate();
	}
	public void invalidate(int l, int t, int r, int b) {
		invalidate();
	}
	public void invalidate() {
		propagateInvalidation();
		nativeInvalidate(widget);
	}
	private static native void nativeInvalidate(long widget);

	// called from native code for GTK widgets or from invalidate() for Java widgets
	@Export
	private void propagateInvalidation() {
		/* queue item 257: this is the fork's invalidation funnel, so it is
		 * where AOSP's PFLAG_DIRTY would be raised. */
		atl_dirty = true;
		for (View child = this; child.parent instanceof View; child = (View)child.parent)
			child.parent.onDescendantInvalidated(child, this);
	}

	protected native void native_setBackgroundColor(long widget, int color);
	public void setBackgroundColor(int color) {
		background = new ColorDrawable(color);
		native_setBackgroundColor(widget, color);
	}

	native void native_setVisibility(long widget, int visibility, float alpha, boolean sensitive);

	protected void onVisibilityChanged(View changedView, int visibility) {
	}

	protected void dispatchVisibilityChanged(View changedView, int visibility) {
		onVisibilityChanged(changedView, visibility);
	}

	public void setVisibility(int visibility) {
		if (visibility == this.visibility)
			return;
		if ((visibility == View.GONE) != (this.visibility == View.GONE) && parent instanceof ViewGroup) {
			((ViewGroup)parent).requestLayout();
		}
		this.visibility = visibility;
		native_setVisibility(widget, visibility, alpha, atl_enabled);
		dispatchVisibilityChanged(this, visibility);
	}

	public void setPadding(int left, int top, int right, int bottom) {
		paddingLeft = left;
		paddingTop = top;
		paddingRight = right;
		paddingBottom = bottom;
		native_setPadding(widget, left, top, right, bottom);
	}
	public native void native_setPadding(long widget, int left, int top, int right, int bottom);

	public void setBackgroundResource(int resid) {
		setBackground(resid == 0 ? null : getContext().getDrawable(resid));
	}

	public void getHitRect(Rect outRect) {
		outRect.set(left, top, right, bottom);
	}
	public final boolean getLocalVisibleRect(Rect r) {
		/* AOSP fills r with the visible portion of the view in the
		 * view's own coordinates (its (0,0) is the view's top-left) and
		 * answers whether anything is visible (queue item 224). ATL's
		 * geometry source is native_getGlobalVisibleRect (the view's
		 * corners in window space, see getGlobalVisibleRect below — it
		 * does not clip against ancestors, an existing gap shared here);
		 * in local space the unclipped answer is simply the view's own
		 * bounds. */
		if (!native_getGlobalVisibleRect(widget, r) || r.width() <= 0 || r.height() <= 0) {
			r.setEmpty();
			return false;
		}
		r.set(0, 0, getWidth(), getHeight());
		return true;
	}

	@Export
	public final int getScrollX() {
		return scrollX;
	}

	@Export
	public final int getScrollY() {
		return scrollY;
	}

	public void scrollTo(int x, int y) {
		// AOSP does nothing when the position is unchanged, then routes the
		// change through onScrollChanged (which notifies the listener).
		if (scrollX != x || scrollY != y) {
			int oldX = scrollX;
			int oldY = scrollY;
			scrollX = x;
			scrollY = y;
			onScrollChanged(scrollX, scrollY, oldX, oldY);
			invalidate();
		}
	}

	protected int computeVerticalScrollOffset() {
		/* AOSP base View returns mScrollY (the fork returned -1; nothing
		 * in the fork consumed the sentinel and Scrollbar code paths
		 * call this too). */
		return scrollY;
	}

	protected int computeHorizontalScrollOffset() {
		return scrollX;
	}

	protected void onScrollChanged(int l, int t, int oldl, int oldt) {
		if (on_scroll_change_listener != null) {
			on_scroll_change_listener.onScrollChange(this, l, t, oldl, oldt);
		}
	}

	public void getLocationOnScreen(int[] location) {
		Rect rect = new Rect();
		getGlobalVisibleRect(rect);
		location[0] = rect.left;
		location[1] = rect.top;
	}

	public boolean performHapticFeedback(int feedbackConstant) {
		return performHapticFeedback(feedbackConstant, 0);
	}

	public boolean performHapticFeedback(int feedbackConstant, int flags) {
		/* Queue item 136: the old body vibrated for an arbitrary 10 ms
		 * and returned true regardless of flags. AOSP gates on
		 * FLAG_TACTILE_FEEDBACK_DISABLED / FLAG_SUPPRESS_HAPTIC (return
		 * false, do nothing) and returns whether the haptic was
		 * performed. ATL's only haptic output is a plain vibrate via
		 * the Vibrator service - duration granularity (HapticFeedback-
		 * Constants map to patterns on real devices) is a separate gap. */
		if ((flags & (FLAG_SUPPRESS_HAPTIC | FLAG_TACTILE_FEEDBACK_DISABLED)) != 0)
			return false;
		/* queue item 268: AOSP's first gate is the per-view flag. */
		if (!isHapticFeedbackEnabled())
			return false;
		Vibrator vibrator = (Vibrator)getContext().getSystemService("vibrator");
		vibrator.vibrate(10);
		return true;
	}

	public int getPaddingLeft() {
		return paddingLeft;
	}

	public int getPaddingRight() {
		return paddingRight;
	}

	public int getPaddingTop() {
		return paddingTop;
	}

	public int getPaddingBottom() {
		return paddingBottom;
	}

	public int getPaddingStart() {
		return paddingLeft;
	}

	public int getPaddingEnd() {
		return paddingRight;
	}

	public void postInvalidate() {
		getHandler().post(new Runnable() {
			@Override
			public void run() {
				invalidate();
			}
		});
	}

	public void postInvalidate(final int left, final int top, final int right, final int bottom) {
		getHandler().post(new Runnable() {
			@Override
			public void run() {
				invalidate(left, top, right, bottom);
			}
		});
	}

	/* Generic-motion listener (queue item 295): AOSP stores it in its
	 * ListenerInfo and dispatchGenericMotionEvent calls it before the
	 * onGenericMotionEvent fallback. Observable here: ATL's GTK scroll
	 * controller feeds real scroll/gesture events through
	 * dispatchGenericMotionEvent (api-impl-jni/views/android_view_View.c),
	 * so an app that registered a listener via this setter previously got
	 * nothing and now gets AOSP's order. */
	private OnGenericMotionListener on_generic_motion_listener = null;

	public void setOnGenericMotionListener(View.OnGenericMotionListener l) {
		on_generic_motion_listener = l;
	}

	/* AOSP returns the window's token, shared by every view in it; an ATL
	 * process has one window, so one token for all views (InputMethodManager
	 * and friends compare tokens by identity). Was a fresh Binder per call. */
	private static final IBinder windowToken = new Binder();

	/* AOSP hands out a null token until the view tree is attached to the
	 * window by ViewRootImpl.setView() - which happens after onCreate -
	 * and library code depends on that: androidx ViewPager.populate()
	 * returns early while getWindowToken() == null, so "mutate the
	 * adapter, then notifyDataSetChanged" sequences during onCreate (AppIntro,
	 * used by Phonograph Plus' intro) are legal on Android but crashed on
	 * ATL, which always had a token:
	 * IllegalStateException "PagerAdapter changed the adapter's contents
	 * without calling notifyDataSetChanged" (queue item 64).
	 * ATL has no attach call; a window-driven (JNI) layout of the view or
	 * an ancestor is the closest equivalent: the GTK allocation that runs
	 * layoutInternal happens on the first frame, i.e. after onCreate. */
	private boolean windowTokenAttached = false;

	public IBinder getWindowToken() {
		/* The token is per window: attached if this view or any ancestor
		 * has been laid out by the window. */
		View v = this;
		while (v != null) {
			if (v.windowTokenAttached)
				return windowToken;
			v = v.parent instanceof View ? (View) v.parent : null;
		}
		return null;
	}

	public void getLocationInWindow(int[] location) {
		getLocationOnScreen(location);
	}

	public void addOnAttachStateChangeListener(OnAttachStateChangeListener l) {
		attach_state_listeners.add(l);
	}

	public Context getContext() {
		return this.context;
	}

	@Export
	public void refreshDrawableState() {
		privateFlags |= PFLAG_DRAWABLE_STATE_DIRTY;
		drawableStateChanged();
	}

	/* Descendant focus policy (queue item 137): AOSP stores the flag and
	 * the focus search consults it. ATL's focus search is GTK's, so the
	 * flag has no consumer yet, but apps set/read it (e.g. to stop
	 * children stealing focus before a list is ready) and the setter
	 * silently dropping it was the finding. The FOCUS_*_DESCENDANTS
	 * constants were absent from the fork (apps referencing them hit
	 * NoSuchFieldError); added with AOSP values. */
	private int descendant_focusability = FOCUS_BEFORE_DESCENDANTS;

	public void setDescendantFocusability(int value) {
		descendant_focusability = value;
	}

	public int getDescendantFocusability() {
		return descendant_focusability;
	}

	public void setAccessibilityDelegate(AccessibilityDelegate delegate) {
		accessibilityDelegate = delegate;
	}

	public static class AccessibilityDelegate {
		public void sendAccessibilityEvent(View view, int eventType) {}
	}

	public Drawable getBackground() {
		return background;
	}

	public void setClickable(boolean clickable) {
		this.clickable = clickable;
	}

	public void setEnabled(boolean enabled) {
		if (atl_enabled == enabled)
			return;
		atl_enabled = enabled;
		native_setVisibility(widget, visibility, alpha, atl_enabled);
		refreshDrawableState();
	}

	public CharSequence getContentDescription() {
		return contentDescription;
	}

	private OnLongClickListener on_long_click_listener = null;

	@Export
	public boolean performLongClick(float x, float y) {
		if (on_long_click_listener != null) {
			return on_long_click_listener.onLongClick(this);
		}
		return false;
	}
	public boolean performLongClick() {
		return performLongClick(Float.NaN, Float.NaN);
	}
	public void setOnLongClickListener(OnLongClickListener listener) {
		if (!isLongClickable())
			setLongClickable(true);
		nativeSetOnLongClickListener(widget);
		on_long_click_listener = listener;
	}
	protected native void nativeSetOnLongClickListener(long widget);

	public void setLongClickable(boolean longClickable) {
		this.longclickable = longClickable;
	}

	private OnHoverListener on_hover_listener = null;

	public void setOnHoverListener(OnHoverListener listener) {
		on_hover_listener = listener;
	}

	@Export
	public final void measure(int widthMeasureSpec, int heightMeasureSpec) {
		if (layoutRequested || widthMeasureSpec != oldWidthMeasureSpec || heightMeasureSpec != oldHeightMeasureSpec) {
			layoutRequested = false;
			oldWidthMeasureSpec = widthMeasureSpec;
			oldHeightMeasureSpec = heightMeasureSpec;
			onMeasure(widthMeasureSpec, heightMeasureSpec);
		}
	}

	public final int getMeasuredState() {
		/* AOSP's composition over the measured sizes' state bits (queue
		 * item 156). ATL's measure path never sets
		 * MEASURED_STATE_TOO_SMALL (GTK sizes widgets, it does not
		 * report "wanted more space"), so the value is 0 today - but
		 * this is now AOSP's formula over real fields rather than a
		 * hardcoded 0, so it follows if the measure path records it. */
		return (measuredWidth & MEASURED_STATE_MASK)
				| ((measuredHeight & MEASURED_STATE_MASK) << MEASURED_HEIGHT_STATE_SHIFT);
	}

	public static int combineMeasuredStates(int curState, int newState) {
		return curState | newState;
	}

	@Export
	protected int getSuggestedMinimumHeight() {
		/* AOSP: the larger of the explicit minimum and the background's. */
		return Math.max(getMinimumHeight(),
		                background != null ? background.getMinimumHeight() : 0);
	}

	@Export
	protected int getSuggestedMinimumWidth() {
		return Math.max(getMinimumWidth(),
		                background != null ? background.getMinimumWidth() : 0);
	}

	/**
	 * Utility to reconcile a desired size and state, with constraints imposed
	 * by a MeasureSpec.  Will take the desired size, unless a different size
	 * is imposed by the constraints.  The returned value is a compound integer,
	 * with the resolved size in the {@link #MEASURED_SIZE_MASK} bits and
	 * optionally the bit {@link #MEASURED_STATE_TOO_SMALL} set if the resulting
	 * size is smaller than the size the view wants to be.
	 *
	 * @param size How big the view wants to be
	 * @param measureSpec Constraints imposed by the parent
	 * @return Size information bit mask as defined by
	 * {@link #MEASURED_SIZE_MASK} and {@link #MEASURED_STATE_TOO_SMALL}.
	 */
	public static int resolveSizeAndState(int size, int measureSpec, int childMeasuredState) {
		int result = size;
		int specMode = MeasureSpec.getMode(measureSpec);
		int specSize = MeasureSpec.getSize(measureSpec);
		switch (specMode) {
			case MeasureSpec.UNSPECIFIED:
				result = size;
				break;
			case MeasureSpec.AT_MOST:
				if (specSize < size) {
					result = specSize | MEASURED_STATE_TOO_SMALL;
				} else {
					result = size;
				}
				break;
			case MeasureSpec.EXACTLY:
				result = specSize;
				break;
		}
		return result | (childMeasuredState & MEASURED_STATE_MASK);
	}

	public static int resolveSize(int size, int measureSpec) {
		return resolveSizeAndState(size, measureSpec, 0) & MEASURED_SIZE_MASK;
	}

	@Export
	public final int getMeasuredWidth() {
		return this.measuredWidth & MEASURED_SIZE_MASK;
	}

	@Export
	public final int getMeasuredHeight() {
		return this.measuredHeight & MEASURED_SIZE_MASK;
	}

	@Export
	protected void onLayout(boolean changed, int l, int t, int r, int b) {}

	private boolean layouted = false;
	private boolean in_layout = false;

	public void layout(int l, int t, int r, int b) {
		int oldLeft = this.left, oldTop = this.top, oldRight = this.right, oldBottom = this.bottom;
		this.layouted = true;
		this.left = l;
		this.top = t;
		this.right = r;
		this.bottom = b;
		if (this.in_layout)                   // check re-entrancy
			native_queueAllocate(widget); // GTK is not re-entrant-safe. Queue allocation
		else
			native_layout(widget, l, t, r, b);
		boolean changed = l != oldLeft || t != oldTop || r != oldRight || b != oldBottom;
		if (changed)
			onSizeChanged(r - l, b - t, oldRight - oldLeft, oldBottom - oldTop);
		/* AOSP View.layout() calls onSizeChanged() and dispatches OnLayoutChangeListeners
		 * whenever the frame changed. ATL only did that in layoutInternal() (the JNI/GTK
		 * entry), so views sized by Android-side ViewGroups (RecyclerView and
		 * ConstraintLayout call layout() on their children) never notified their
		 * listeners: Glide's SizeDeterminer adds a one-shot listener while a view is
		 * still 0x0 at bind time, waited on it forever, and album/track covers stayed
		 * unloaded (Fossify showed its error placeholder without any error).
		 * Do NOT call onLayout() here: ViewGroups are laid out by the GTK allocation
		 * pass (layoutInternal), and running a ViewGroup's onLayout() from both paths
		 * double-dispatches it (androidx RecyclerView then throws
		 * "Layout state should be one of 100 but it is 10", RunnerUp went BLANK).
		 * The children of an Android-laid-out ViewGroup get their own layout() calls
		 * from the app's own ViewGroup code. */
		if (on_layout_change_listeners != null && changed) {
			for (OnLayoutChangeListener on_layout_change_listener : on_layout_change_listeners)
				on_layout_change_listener.onLayoutChange(this, l, t, r, b, oldLeft, oldTop, oldRight, oldBottom);
		}
	}

	/** Helper function to be called from GTKs LayoutManager via JNI */
	@Export
	private void layoutInternal(int width, int height) {
		/* Called from the JNI/GTK entry: the window is driving this view's
		 * layout, which is ATL's moment of "attached to window" (see
		 * getWindowToken). */
		windowTokenAttached = true;
		in_layout = true;
		// if the layout is triggered from a native widget, we might not have measured yet
		if (!(parent instanceof ViewGroup) && (width != getMeasuredWidth() || height != getMeasuredHeight())) {
			requestLayout();
			measure(width | MeasureSpec.EXACTLY, height | MeasureSpec.EXACTLY);
		}
		boolean changed = oldWidth != width || oldHeight != height;
		if (changed)
			onSizeChanged(width, height, oldWidth, oldHeight);
		bottom = top + height;
		right = left + width;
		if (this instanceof ViewGroup) {
			for (View child : ((ViewGroup)this).children)
				child.layouted = false;
		}
		onLayout(changed, 0, 0, width, height);
		// ReactViewGroup has onLayout implemented as no-op, so we need to walk children ourself in such cases
		if (this instanceof ViewGroup) {
			for (View child : ((ViewGroup)this).children) {
				if (!child.layouted && child.visibility != GONE)
					child.layout(child.left, child.top, child.right, child.bottom);
			}
		}
		if (on_layout_change_listeners != null) {
			for (OnLayoutChangeListener on_layout_change_listener : on_layout_change_listeners)
				on_layout_change_listener.onLayoutChange(this, left, top, right, bottom, left, top, left + oldWidth, top + oldHeight);
		}
		oldWidth = width;
		oldHeight = height;
		in_layout = false;
	}

	/** Prefill the measureSpec cache, so that AndroidLayout can use it from native code if GTK measure spec is incomplete */
	public void internalSetDefaultMeasureSpec(int widthMeasureSpec, int heightMeasureSpec) {
		this.oldWidthMeasureSpec = widthMeasureSpec;
		this.oldHeightMeasureSpec = heightMeasureSpec;
		requestLayout();
	}

	public int getLeft() {
		return left;
	}
	public int getTop() {
		return top;
	}
	public int getRight() {
		return right;
	}
	public int getBottom() {
		return bottom;
	}

	public void offsetTopAndBottom(int offset) {
		layout(left, top + offset, right, bottom + offset);
	}

	public void offsetLeftAndRight(int offset) {
		layout(left + offset, top, right + offset, bottom);
	}

	public void setBackgroundDrawable(Drawable backgroundDrawable) {
		this.background = backgroundDrawable;
		if (backgroundDrawable instanceof ColorDrawable) {
			native_setBackgroundColor(widget, ((ColorDrawable)backgroundDrawable).getColor());
			return;
		}
		if (backgroundDrawable != null) {
			backgroundDrawable.setCallback(this);
			if (backgroundTint != 0)
				backgroundDrawable.setTint(backgroundTint);
		}
		native_setBackgroundDrawable(widget, backgroundDrawable != null ? backgroundDrawable.paintable : 0);
	}

	/* Over-scroll state (queue item 147): AOSP default is
	 * OVER_SCROLL_IF_CONTENT_SCROLLS, not ALWAYS; the setter stored
	 * nothing and the getter answered 0 (OVER_SCROLL_ALWAYS). State is
	 * stored now; the glow/stretch effect itself does not exist in ATL. */
	private int over_scroll_mode = OVER_SCROLL_IF_CONTENT_SCROLLS;

	public int getOverScrollMode() {
		return over_scroll_mode;
	}

	public void setFitsSystemWindows(boolean fitsSystemWindows) {
		if (this.fitsSystemWindows != fitsSystemWindows) {
			this.fitsSystemWindows = fitsSystemWindows;
			requestLayout();
		}
	}

	public void setWillNotDraw(boolean value) {
		/* AOSP flag: ViewGroup skips its own draw pass. ATL draws through
		 * GTK, which decides on its own; the value is stored because
		 * code such as custom ViewGroups queries it back. */
		willNotDraw = value;
	}

	/* Scroll-container flag (queue item 200): AOSP stores it (from the
	 * android:scrollable attribute) and isScrollContainer() reports it;
	 * it drives scroll handling and accessibility focus-scroll. The
	 * setter dropped it and the getter was not declared at all. */
	private boolean atl_scroll_container = false;

	public void setScrollContainer(boolean isScrollContainer) {
		atl_scroll_container = isScrollContainer;
	}

	public boolean isScrollContainer() {
		return atl_scroll_container;
	}

	public boolean removeCallbacks(Runnable action) {
		getHandler().removeCallbacks(action);
		return true;
	}

	public void requestLayout() {
		native_requestLayout(widget);
		View view = this;
		while (!view.layoutRequested) {
			view.layoutRequested = true;
			if (view.parent instanceof View)
				view = (View)view.parent;
			else
				break;
		}
	};

	public void setOverScrollMode(int mode) {
		over_scroll_mode = mode;
	}

	@Export
	public int getId() { return id; }

	@Export
	public String getIdName() {
		if (this.id == View.NO_ID) {
			return "NO_ID";
		}

		try {
			return getResources().getResourceName(this.id);
		} catch (Resources.NotFoundException e) {
			return "NOT_FOUND";
		}
	}

	@Export
	public String getAllSuperClasses() {
		StringBuilder sb = new StringBuilder();
		Class<?> currentClass = getClass();

		sb.append(currentClass.getCanonicalName());
		currentClass = currentClass.getSuperclass();
		while (currentClass != null) {
			sb.append(" << ").append(currentClass.getCanonicalName());
			currentClass = currentClass.getSuperclass();
		}
		return sb.toString();
	}

	public boolean postDelayed(Runnable action, long delayMillis) {
		getHandler().postDelayed(action, delayMillis);
		return true;
	}

	public boolean post(Runnable action) {
		getHandler().post(action);
		return true;
	}

	/* Save-from-parent flag (queue item 194): AOSP stores it in the view's
	 * flags and the hierarchy save/restore path skips a view whose parent
	 * disabled it; isSaveFromParentEnabled() is AOSP's public getter,
	 * absent from the fork. Nothing reads the flag here until the
	 * saved-state pipeline exists (see saveHierarchyState above). */
	private boolean atl_save_from_parent_enabled = true;

	public void setSaveFromParentEnabled(boolean enabled) {
		atl_save_from_parent_enabled = enabled;
	}

	public boolean isSaveFromParentEnabled() {
		return atl_save_from_parent_enabled;
	}

	public void setTag(int key, Object tag) {
		tags.put(key, tag);
	}
	public Object getTag(int key) {
		return tags.get(key);
	}

	public void setTag(Object tag) {
		this.tag = tag;
	}
	public Object getTag() {
		return tag;
	}

	public void addOnLayoutChangeListener(OnLayoutChangeListener listener) {
		if (on_layout_change_listeners == null)
			on_layout_change_listeners = new CopyOnWriteArrayList<OnLayoutChangeListener>();
		on_layout_change_listeners.add(listener);
	}
	public void removeOnLayoutChangeListener(OnLayoutChangeListener listener) {
		if (on_layout_change_listeners != null)
			on_layout_change_listeners.remove(listener);
	}

	public void sendAccessibilityEvent(int eventType) {
		/* AOSP routes through the AccessibilityDelegate; ATL has no
		 * a11y backend so nothing displays, but the delegate chain is
		 * now honoured instead of dropped. */
		if (accessibilityDelegate != null)
			accessibilityDelegate.sendAccessibilityEvent(this, eventType);
	}

	public void setMinimumHeight(int minHeight) {
		this.minHeight = minHeight;
	}
	public void setMinimumWidth(int minWidth) {
		this.minWidth = minWidth;
	}

	public int getVisibility() { return visibility; }

	public boolean isInEditMode() { return false; }

	@Override
	@SuppressWarnings("deprecation")
	protected void finalize() throws Throwable {
		try {
			native_destructor(widget);
		} finally {
			super.finalize();
		}
	}

	/* Copyright (C) 2006 The Android Open Source Project */
	public final int[] getDrawableState() {
		if ((drawableState != null) && ((privateFlags & PFLAG_DRAWABLE_STATE_DIRTY) == 0)) {
			return drawableState;
		} else {
			drawableState = onCreateDrawableState(0);
			privateFlags &= ~PFLAG_DRAWABLE_STATE_DIRTY;
			return drawableState;
		}
	}

	public float getRotation() {
		return rotation;
	}

	public void bringToFront() {
		/* AOSP: defers to the parent ViewGroup's bringChildToFront, which
		 * moves the child to the end of the drawing order. Both halves
		 * were empty in the fork (View.bringToFront and a `// TODO`
		 * ViewGroup.bringChildToFront), so calls like a popup raising
		 * itself were silently ignored (queue item 206). Now ViewGroup
		 * reorders the Java list and the native GTK child. */
		if (parent instanceof ViewGroup)
			((ViewGroup) parent).bringChildToFront(this);
	}

	public boolean isEnabled() { return atl_enabled; }
	/* AOSP hasFocus: this view *or any descendant* holds focus. GTK's
	 * focus lives on the wrapper/inner widget of the focused view, so the
	 * check is "the window's focus widget is inside this view's GTK
	 * subtree" (queue item 114; used to be constant false, which made
	 * focus-gating layouts treat every container as unfocused). */
	public boolean hasFocus() {
		return nativeHasFocus(widget);
	}
	public boolean isLayoutRequested() { return layoutRequested; }
	public int getBaseline() { return -1; }
	/* AOSP hasFocusable: focusable in either mode. This mirrors ATL's
	 * own focus policy (requestFocus accepts a view when it is
	 * atl_focusable), not AOSP's flag bookkeeping. */
	public boolean hasFocusable() {
		return atl_focusable || atl_focusable_in_touch_mode;
	}
	private static native boolean nativeIsFocused(long widget);
	private static native boolean nativeHasFocus(long widget);
	private native void nativeClearFocus(long widget);
	public boolean isFocused() {
		return nativeIsFocused(widget);
	}

	/* The animation AOSP's startAnimation() leaves running on the view
	 * (mCurrentAnimation): it stays queryable until it ends or
	 * clearAnimation() drops it, and setAnimation() sets it without
	 * starting it. The fork kept no such slot - clearAnimation was empty
	 * and getAnimation() always answered null, so a caller checking
	 * "is anything animating this view?" (or getAnimation() before
	 * restarting it) was told no. ATL has no per-frame animation driver:
	 * startAnimation() runs the animation synchronously to its end
	 * (recorded gap), so the slot is filled by startAnimation() and left
	 * filled, which is the closest honest state, and Animation.hasEnded()
	 * reports what happened. (queue item 195) */
	private Animation atl_current_animation = null;

	public void clearAnimation() {
		atl_current_animation = null;
		invalidate();
	}

	public Animation getAnimation() {
		return atl_current_animation;
	}

	public void setAnimation(Animation animation) {
		atl_current_animation = animation;
	}

	private ViewPropertyAnimator viewPropertyAnimator;
	public ViewPropertyAnimator animate() {
		if (viewPropertyAnimator == null)
			viewPropertyAnimator = new ViewPropertyAnimator(this);
		return viewPropertyAnimator;
	}

	public float getTranslationX() {
		return translationX;
	}

	public float getTranslationY() {
		return translationY;
	}

	public void setTranslationX(float translationX) {
		this.translationX = translationX;
		if (parent instanceof View)
			((View)parent).native_queueAllocate(((View)parent).widget);
	}

	public void setTranslationY(float translationY) {
		this.translationY = translationY;
		if (parent instanceof View)
			((View)parent).native_queueAllocate(((View)parent).widget);
	}

	public void setX(float x) {
		setTranslationX(x - left);
	}

	public void setY(float y) {
		setTranslationY(y - top);
	}

	public void setAlpha(float alpha) {
		native_setVisibility(widget, visibility, alpha, atl_enabled);
		this.alpha = alpha;
	}

	@Export
	public boolean onGenericMotionEvent(MotionEvent event) { return false; }

	@Export
	public boolean dispatchGenericMotionEvent(MotionEvent event) {
		/* AOSP's order (queue item 295): the OnGenericMotionListener set
		 * through setOnGenericMotionListener first, then the
		 * onGenericMotionEvent override as fallback. The listener half
		 * was missing, so listeners registered through the setter never
		 * fired although GTK's scroll controller calls this method with
		 * real ACTION_SCROLL events. */
		if (on_generic_motion_listener != null
				&& on_generic_motion_listener.onGenericMotion(this, event)) {
			return true;
		}
		if (onGenericMotionEvent(event)) {
			return true;
		}
		return false;
	}

	/* Scrollbars (queue item 202): AOSP's returns true when it started a
	 * scrollbar fade and callers branch on it — the fork's own
	 * AbsListView.trackMotionScroll (line ~4114) does
	 * `if (!awakenScrollBars()) invalidate();`. ATL draws no scrollbars of
	 * its own (the GTK scroll widget is the visible affordance), so there
	 * is nothing to wake: false, i.e. "no fade started, you handle the
	 * repaint", is the faithful answer and keeps the caller's fallback
	 * running. Recorded with the scrollbar gap. */
	protected boolean awakenScrollBars() { return false; }

	protected native boolean native_getMatrix(long widget, long matrix);
	public Matrix getMatrix() {
		return Matrix.IDENTITY_MATRIX;
	}

	protected static final int[] EMPTY_STATE_SET = new int[0];

	protected static final int[] ENABLED_STATE_SET = new int[] {R.attr.state_enabled};

	protected static final int[] PRESSED_ENABLED_STATE_SET = new int[] {R.attr.state_pressed, R.attr.state_enabled};

	protected static final int[] SELECTED_STATE_SET = new int[] {R.attr.state_selected};

	/**
	 * Utility to return a default size. Uses the supplied size if the
	 * MeasureSpec imposed no constraints. Will get larger if allowed
	 * by the MeasureSpec.
	 *
	 * @param size Default size for this view
	 * @param measureSpec Constraints imposed by the parent
	 * @return The size this view should be.
	 */
	public static int getDefaultSize(int size, int measureSpec) {
		int result = size;
		int specMode = MeasureSpec.getMode(measureSpec);
		int specSize = MeasureSpec.getSize(measureSpec);
		switch (specMode) {
			case MeasureSpec.UNSPECIFIED:
				result = size;
				break;
			case MeasureSpec.AT_MOST:
			case MeasureSpec.EXACTLY:
				result = specSize;
				break;
		}
		return result;
	}

	public static class BaseSavedState extends AbsSavedState {
	}

	public void clearFocus() {
		/* If this view's GTK subtree holds the window focus, drop it
		 * (the JNI helper only resets when focus is inside the subtree,
		 * so an unrelated view is never unfocused). Focus itself stays
		 * GTK's; Java only ever read it back through hasFocus. */
		nativeClearFocus(widget);
	}

	public static View inflate(Context context, int resource, ViewGroup root) {
		LayoutInflater factory = LayoutInflater.from(context);
		return factory.inflate(resource, root);
	}

	/* Hierarchy state save/restore (queue items 179, 184): empty, and that
	 * is the state of the whole feature rather than a stub to fill: ATL has
	 * no saved-state pipeline at all — nothing asks an Activity to save its
	 * state (Activity.onSaveInstanceState(Bundle) has no caller) and nothing
	 * hands a Bundle of view state back, so there is nothing for these to
	 * feed or consume. AOSP's version walks the hierarchy keyed by view id
	 * (View.onSaveInstanceState + ViewGroup.dispatchSaveInstanceState), and
	 * the fork has neither half — the widget-side SavedState classes that
	 * would use it are commented out too (see
	 * android/widget/ExpandableListView.java). **Gap: view saved-state
	 * pipeline** (needed before these can mean anything); recorded here
	 * instead of half-implementing a dispatch no caller can exercise. */
	public void saveHierarchyState(SparseArray<Parcelable> array) {}

	public void setDuplicateParentStateEnabled(boolean enabled) {
		duplicateParentState = enabled;
	}

	@Export
	public boolean performClick() {
		return callOnClick();
	}

	public void playSoundEffect(int soundConstant) {
		/* AOSP routes this through the ViewRootImpl's window callbacks to
		 * the audio stack; ATL has neither that callback object nor a
		 * sound-effect sink (AudioManager.playSoundEffect in the fork is
		 * an empty stub), so there is nothing to play. Explicit no-op:
		 * queue item 170 finding. */
	}

	@Export
	public void computeScroll() {}

	/* Drawable state animations (queue item 180): AOSP jumps this view's
	 * drawables' state animators to their end values (ViewGroup overrides it
	 * to walk the children). ATL's Drawable has no state-animator machinery
	 * at all — Drawable.jumpToCurrentState is empty and there is no
	 * StateAnimatorData/animation property list in the fork — so there is no
	 * animation to jump: the no-op is faithful, recorded with the drawable
	 * animation gap. */
	public void jumpDrawablesToCurrentState() {}

	/* Focus listener (queue item 187): the setter dropped the listener and
	 * the interface had no method (fixed above). State now round-trips and
	 * getOnFocusChangeListener answers it. Honest gap: nothing calls it
	 * yet - ATL's focus is owned by GTK (requestFocus/clearFocus go to
	 * nativeRequestFocus) and the fork has neither View.onFocusChanged nor
	 * a JNI focus-change callback, so there is no moment to dispatch from.
	 * Recorded with the input gaps rather than half-wired. */
	private OnFocusChangeListener on_focus_change_listener = null;

	public void setOnFocusChangeListener(View.OnFocusChangeListener l) {
		on_focus_change_listener = l;
	}

	/* AOSP: does the window this view is attached to currently have
	 * focus (queue item 133). Was constant true. Uses GTK's real window
	 * activation state via a new JNI helper; a detached view has no
	 * window and answers false like AOSP. */
	public boolean hasWindowFocus() {
		return windowTokenAttached && nativeHasWindowFocus(widget);
	}
	private static native boolean nativeHasWindowFocus(long widget);

	/* Save flag (queue item 179's family): AOSP stores it and the
	 * hierarchy-state save path skips views that cleared it. Nothing reads it
	 * until the saved-state pipeline exists (see saveHierarchyState above);
	 * no public getter is declared because AOSP's View has none (it is a
	 * private flag) and adding a final method risks clashing with an app's
	 * own method of that name. */
	private boolean atl_save_enabled = true;

	public void setSaveEnabled(boolean enabled) {
		atl_save_enabled = enabled;
	}

	public boolean willNotDraw() {
		/* AOSP reads the WILL_NOT_DRAW view flag back (setWillNotDraw
		 * sets it; ViewGroup subclasses and androidx custom containers
		 * query it to skip work). The fork already stores the flag in
		 * setWillNotDraw — only the getter ignored it (queue item 231). */
		return willNotDraw;
	}

	/* Context-menu listener (queue item 244): AOSP stores it and makes
	 * the view long-clickable, because a long press is how Android opens
	 * the menu; the fork did neither. Stored with the long-clickable
	 * side kept exactly as AOSP has it, so the view really does take the
	 * long press. **Gap: context-menu panel machinery** — the fork has
	 * no showContextMenu/createContextMenu path (no panel manager, and
	 * Window.hasFeature answers no to everything, queue item 208's
	 * finding), so nothing can hand the listener a ContextMenu to fill
	 * in yet; performLongClick below only reaches a long-click listener. */
	private OnCreateContextMenuListener on_create_context_menu_listener = null;

	public void setOnCreateContextMenuListener(View.OnCreateContextMenuListener l) {
		if (!isLongClickable())
			setLongClickable(true);
		on_create_context_menu_listener = l;
	}

	protected void onAnimationStart() {}

	protected void onAnimationEnd() {}

	public void startAnimation(Animation animation) {
		if (animation == null)
			return;
		/* AOSP keeps the animation on the view as mCurrentAnimation and
		 * drives it per frame; ATL has no such driver (see the
		 * atl_current_animation comment above), so the fork runs it
		 * synchronously and still records it as the view's current
		 * animation. */
		atl_current_animation = animation;
		onAnimationStart();
		animation.start();
		onAnimationEnd();
	}

	public void getDrawingRect(Rect rect) {
		rect.left = getScrollX();
		rect.top = getScrollY();
		rect.right = getScrollX() + getWidth();
		rect.bottom = getScrollY() + getHeight();
	}

	/* AOSP hands out the same Display object for the same screen; a fresh
	 * one per call breaks identity comparisons. One window per ATL
	 * process => one Display. */
	private static final Display singleDisplay = new Display();

	public Display getDisplay() {
		return singleDisplay;
	}

	private int importantForAccessibility;
	public void setImportantForAccessibility(int importantForAccessibility) {
		this.importantForAccessibility = importantForAccessibility;
	}
	public int getImportantForAccessibility() {
		return importantForAccessibility;
	}

	public boolean getFitsSystemWindows() { return fitsSystemWindows; }

	private OnApplyWindowInsetsListener on_apply_window_insets_listener;

	public void setOnApplyWindowInsetsListener(View.OnApplyWindowInsetsListener l) {
		on_apply_window_insets_listener = l;
	}

	public WindowInsets dispatchApplyWindowInsets(WindowInsets insets) {
		/* AOSP: listener if set, else onApplyWindowInsets(); a null
		 * result from the listener means "fully consumed", matched here
		 * with an empty WindowInsets. ATL has no inset source of its
		 * own (no system bars), so this runs only when apps dispatch
		 * manually - the fitsSystemWindows plumbing stays a gap. */
		if (on_apply_window_insets_listener != null) {
			WindowInsets r = on_apply_window_insets_listener.onApplyWindowInsets(this, insets);
			return r != null ? r : new WindowInsets();
		}
		return onApplyWindowInsets(insets);
	}

	public final boolean isFocusable() { return atl_focusable; }
	public boolean isClickable() { return clickable; }
	public boolean isLongClickable() { return longclickable; }

	public int getLayoutDirection() { return LAYOUT_DIRECTION_LTR; }

	public void setBackground(Drawable background) {
		setBackgroundDrawable(background);
	}

	private float elevation;
	public void setElevation(float elevation) {
		this.elevation = elevation;
	}
	public float getElevation() {
		return elevation;
	}

	/* AOSP: true once a layout pass has completed for this view (while
	 * attached). ATL's layout passes are the window-driven one
	 * (layoutInternal, tracked via the same flag getWindowToken uses) or
	 * explicit layout() calls (the layouted flag). Queue item 115: it
	 * claimed true always, so gating in scroll/measure helpers fired
	 * before anything had been laid out. */
	public boolean isLaidOut() {
		return windowTokenAttached || layouted;
	}

	public void postOnAnimation(Runnable action) {
		postDelayed(action, 1000 / 60);
	}

	public void postOnAnimationDelayed(Runnable action, long delayMillis) {
		if (delayMillis < 1000 / 60)
			delayMillis = 1000 / 60;
		postDelayed(action, delayMillis);
	}

	/* Scrollbar enable flags (queue item 220): AOSP stores these (and
	 * defaults them from the scrollbars styleable — "not drawn by
	 * default", so false on a bare View, true on its scroll widgets) and
	 * is…ScrollBarEnabled() reads them back; they gate its own scrollbar
	 * drawing and awakenScrollBars. ATL draws no scrollbars of its own
	 * (the GTK scroll widget is the visible affordance — same gap as
	 * awakenScrollBars), so the flags gate nothing yet, but callers set
	 * and read them back. */
	private boolean atl_horizontal_scroll_bar_enabled = false;
	private boolean atl_vertical_scroll_bar_enabled = false;

	public void setHorizontalScrollBarEnabled(boolean enabled) {
		atl_horizontal_scroll_bar_enabled = enabled;
	}

	public boolean isHorizontalScrollBarEnabled() {
		return atl_horizontal_scroll_bar_enabled;
	}

	public void setVerticalScrollBarEnabled(boolean enabled) {
		atl_vertical_scroll_bar_enabled = enabled;
	}

	public boolean isVerticalScrollBarEnabled() {
		return atl_vertical_scroll_bar_enabled;
	}

	public void postInvalidateOnAnimation() {
		postInvalidate();
	}

	/* Padding-is-relative flag (queue item 226): AOSP sets it in
	 * setPaddingRelative and isPaddingRelative() reads it back (padding
	 * accessors then treat left/right as start/end). The fork mapped
	 * start/end onto left/right — fine for its always-LTR view — but
	 * dropped the flag, so the pair never round-tripped. */
	private boolean atl_padding_relative = false;

	public void setPaddingRelative(int start, int top, int end, int bottom) {
		// This View is always LTR (getLayoutDirection() returns LAYOUT_DIRECTION_LTR),
		// so start maps to left and end maps to right.
		atl_padding_relative = true;
		setPadding(start, top, end, bottom);
	}

	private native boolean nativeIsAttachedToWindow(long widget);
	public boolean isAttachedToWindow() {
		return nativeIsAttachedToWindow(widget);
	}

	/* AOSP asks the window to re-dispatch insets (queue item 128). ATL
	 * has no system bar insets, but the re-dispatch itself is real:
	 * listener/onApplyWindowInsets chains (dispatchApplyWindowInsets is
	 * already implemented) get re-run against ATL's empty insets, which
	 * is what an attached window with no bars would send. */
	public void requestApplyInsets() {
		if (windowTokenAttached)
			dispatchApplyWindowInsets(new WindowInsets());
	}

	public View getRootView() {
		View view = this;
		while (view.parent instanceof View) {
			view = (View)view.parent;
		}
		return view;
	}

	// See ViewRootImpl: ATL has no Android window session (GTK windows), but
	// compat code reflects on getViewRootImpl()/mAttachInfo, so the root view
	// of each ATL window carries one describing that window.
	ViewRootImpl mAtlViewRootImpl;

	/**
	 * AOSP: the ViewRootImpl of the window this view is attached to, or null
	 * when the view is not attached to a window. ATL reports the same null
	 * for detached views; attached views get the per-window stand-in whose
	 * visible insets are the window frame (an ATL window has no system bars,
	 * which is the state AOSP reports those insets for).
	 */
	public final ViewRootImpl getViewRootImpl() {
		if (!isAttachedToWindow())
			return null;
		View root = getRootView();
		if (root.mAtlViewRootImpl == null)
			root.mAtlViewRootImpl = new ViewRootImpl(root);
		root.mAtlViewRootImpl.mAttachInfo.mVisibleInsets.set(0, 0, root.getWidth(), root.getHeight());
		return root.mAtlViewRootImpl;
	}

	/**
	 * AOSP's per-window attach state shared by the views of a window. ATL
	 * keeps only the parts compat code reads reflectively
	 * (ViewRootImpl.mAttachInfo, AttachInfo.mVisibleInsets); mVisibleInsets
	 * tracks the visible frame of the ATL window, i.e. its full size, since
	 * ATL windows are never partly covered by system bars.
	 */
	static final class AttachInfo {
		final ViewRootImpl mViewRootImpl;
		final Rect mVisibleInsets = new Rect();

		AttachInfo(ViewRootImpl viewRootImpl) {
			mViewRootImpl = viewRootImpl;
		}
	}

	/* Queue item 174: was constant true. AOSP's walk (this and every
	 * ancestor VISIBLE, and attached: the chain must end at a non-View
	 * parent). ATL's chain ends at WindowManagerImpl.WindowViewParent
	 * (set in addView, view.parent = ...), the same role AOSP's
	 * ViewRootImpl plays, so the AOSP walk works verbatim - a detached
	 * view (parent null) correctly answers false. */
	public boolean isShown() {
		View current = this;
		do {
			if (current.getVisibility() != VISIBLE)
				return false;
			ViewParent parent = current.parent;
			if (parent == null)
				return false; // not attached to a window
			if (!(parent instanceof View))
				return true;
			current = (View) parent;
		} while (true);
	}

	public int getWindowVisibility() { return VISIBLE; }

	public float getAlpha() { return alpha; }

	public View findFocus() {
		/* AOSP: the view in this subtree that holds focus (isFocused()
		 * is this view itself; hasFocus() covers descendants). The fork
		 * answered `this` unconditionally, so Activity.getCurrentFocus
		 * and androidx focus helpers could not trust it (queue item
		 * 238). Focus is GTK's (queue item 88's finding) but the fork
		 * answers it per view (nativeIsFocused/nativeHasFocus, queue
		 * item 114's work). ViewGroup overrides this to recurse. */
		return isFocused() ? this : null;
	}

	public int getMinimumHeight() { return minHeight; }
	public int getMinimumWidth() { return minWidth; }

	/* AOSP default is true; state read back by isNestedScrollingEnabled
	 * (queue item 126). ATL's View has no nested scroll machinery
	 * itself, but scroll containers and gesture code gate on this
	 * flag, and the old pair (empty setter, false getter) made every
	 * container report nested scrolling off. */
	private boolean nested_scrolling_enabled = true;

	public boolean isNestedScrollingEnabled() {
		return nested_scrolling_enabled;
	}

	/* Transient state (queue item 193): AOSP reference-counts it (every
	 * setHasTransientState(true) paired with a later false) and
	 * hasTransientState() reports count > 0; the fork kept neither, so an
	 * adapter asking "is this item view mid-animation/selection?" always got
	 * "no". ATL has no view-recycling pool to consume the answer and AOSP's
	 * propagation of the flag to the parents (requestParentHasTransientState)
	 * is not implemented — recorded with the recycling gap — but the count
	 * and the query are real state now, including AOSP's clamp at 0. */
	private int atl_transient_state_count = 0;

	public boolean hasTransientState() {
		return atl_transient_state_count > 0;
	}

	public final void cancelPendingInputEvents() {
		cancelLongPress();
	}

	/* Outline provider (queue item 130): AOSP default is BACKGROUND, and
	 * the old getter invented a fresh object on every call (so provider
	 * identity comparisons failed). State is now stored with the AOSP
	 * default. No render-node outline is drawn in ATL yet (that plus
	 * clipToOutline consumption is the same rendering follow-up), so
	 * invalidateOutline (queue item 120) has nothing to recompute: it
	 * stays a documented no-op rather than an unexplained empty body. */
	private ViewOutlineProvider outline_provider = ViewOutlineProvider.BACKGROUND;

	public ViewOutlineProvider getOutlineProvider() {
		return outline_provider;
	}
	public void setOutlineProvider(ViewOutlineProvider provider) {
		outline_provider = provider;
	}

	public void invalidateOutline() {
		/* AOSP recomputes the render-node outline from
		 * getOutlineProvider(); ATL draws no outlines and no clip
		 * consumes them yet, so there is nothing to recompute. Kept as
		 * an explicit no-op: the queue item 120 finding. */
	}

	private StateListAnimator atl_state_list_animator = null;

	public void setStateListAnimator(StateListAnimator stateListAnimator) {
		atl_state_list_animator = stateListAnimator;
		if (stateListAnimator != null && stateListAnimator.enabledAnimator != null) {
			stateListAnimator.enabledAnimator.setTarget(this);
			stateListAnimator.enabledAnimator.start();
		}
	}

	private static final AtomicInteger nextGeneratedId = new AtomicInteger(1);
	/**
     * Generate a value suitable for use in {@link #setId(int)}.
     * This value will not collide with ID values generated at build time by aapt for R.id.
     *
     * @return a generated ID value
     */
	public static int generateViewId() {
		for (;;) {
			final int result = nextGeneratedId.get();
			// aapt-generated IDs have the high byte nonzero; clamp to the range under that.
			int newValue = result + 1;
			if (newValue > 0x00FFFFFF)
				newValue = 1; // Roll over to 1, not 0.
			if (nextGeneratedId.compareAndSet(result, newValue)) {
				return result;
			}
		}
	}

	public boolean isLayoutDirectionResolved() { return true; }

	public boolean canResolveLayoutDirection() { return true; }

	public boolean isPaddingRelative() {
		return atl_padding_relative;
	}

	/* Foreground drawable (queue item 181): AOSP stores it (drawn over the
	 * content, gravity from setForegroundGravity below); the setter dropped
	 * it and getForeground answered null even when a foreground had been
	 * set. ATL's gsk draw path composites no foreground yet - a rendering
	 * gap - but the state (and the gravity that was already stored) now
	 * round-trips, and callers that gate on "is there a foreground" are
	 * correct. */
	private Drawable atl_foreground = null;

	public void setForeground(Drawable foreground) {
		atl_foreground = foreground;
		invalidate();
	}

	public int getForegroundGravity() { return mForegroundGravity; }

	public void setForegroundGravity(int gravity) { mForegroundGravity = gravity; }

	public boolean canScrollVertically(int direction) {
		/* AOSP View.canScrollVertically maths over the scrollbar
		 * helpers; widgets that scroll really (AbsListView, GridView,
		 * app-side RecyclerView/ScrollView) override the compute*
		 * methods and get the answer through them. */
		final int offset = computeVerticalScrollOffset();
		final int range = computeVerticalScrollRange();
		final int extent = computeVerticalScrollExtent();
		if (range <= 0)
			return false;
		if (direction < 0)
			return offset > 0;
		return range > extent + offset;
	}

	public boolean isInTouchMode() { return false; }

	public void stopNestedScroll() {
		/* AOSP clears this view's active nested-scroll axes up its parent
		 * chain. ATL has no nested scroll machinery at all (no
		 * startNestedScroll, and ViewGroup's onNestedScroll/onNestedScroll-
		 * Accepted callbacks throw - a separate gap), so there is no
		 * state here to clear. Explicit no-op: queue item 138 finding. */
	}

	public long getDrawingTime() {
		return System.currentTimeMillis();
	}

	private boolean keepScreenOn = false;
	private static native void native_keep_screen_on(long widget, boolean keepScreenOn);
	public void setKeepScreenOn(boolean screenOn) {
		if (isAttachedToWindow() && keepScreenOn != screenOn)
			native_keep_screen_on(widget, screenOn);
		keepScreenOn = screenOn;
	}

	public boolean getKeepScreenOn() {
		return keepScreenOn;
	}

	@Export
	protected void onAttachedToWindow() {
		for (OnAttachStateChangeListener l : attach_state_listeners) {
			l.onViewAttachedToWindow(this);
		}
		if (keepScreenOn)
			native_keep_screen_on(widget, true);
		if (floating_observer != null) {
			getViewTreeObserver().merge(floating_observer);
			floating_observer = null;
		}
	}

	@Export
	protected void onDetachedFromWindow() {
		for (OnAttachStateChangeListener l : attach_state_listeners) {
			l.onViewDetachedFromWindow(this);
		}
		if (keepScreenOn)
			native_keep_screen_on(widget, false);
	}

	/* Layer state (queue item 117): AOSP renders the view into an
	 * offscreen buffer for SOFTWARE/HARDWARE; the gsk draw path has no
	 * backing store yet, but the constants and the set/get pair are real
	 * API state apps branch on (invalidation and animation code checks
	 * getLayerType()). */
	public static final int LAYER_TYPE_NONE = 0;
	public static final int LAYER_TYPE_SOFTWARE = 1;
	public static final int LAYER_TYPE_HARDWARE = 2;

	private int atl_layer_type = LAYER_TYPE_NONE;
	private Paint atl_layer_paint = null;

	public void setLayerType(int layerType, Paint paint) {
		atl_layer_type = layerType;
		atl_layer_paint = paint;
	}

	public float getZ() {
		/* AOSP: elevation + translationZ (queue item 182). ViewGroup's
		 * child sort reads this. */
		return elevation + atl_translation_z;
	}

	protected void onSizeChanged(int w, int h, int oldw, int oldh) {}

	/* Background tint state (queue item 139): stored and read back; the
	 * gsk draw path does not apply the tint yet (same rendering
	 * follow-up as the other paint-level View properties). */
	private ColorStateList background_tint_list = null;

	public void setBackgroundTintList(ColorStateList tint) {
		background_tint_list = tint;
	}

	protected int computeHorizontalScrollRange() {
		return getWidth();
	}

	protected int computeHorizontalScrollExtent() {
		return getWidth();
	}

	protected int computeVerticalScrollRange() {
		return getHeight();
	}

	protected int computeVerticalScrollExtent() {
		return getHeight();
	}

	/* Live-region state (queue item 190): AOSP keeps the mode in the view's
	 * flags and getAccessibilityLiveRegion() reads it back; the setter was
	 * empty and the getter constant 0, and the fork lacked the constants.
	 * ATL has no accessibility-node pipeline to consume it (a separate gap),
	 * but the value round-trips now. */
	public static final int ACCESSIBILITY_LIVE_REGION_NONE = 0x00000000;
	public static final int ACCESSIBILITY_LIVE_REGION_POLITE = 0x00000001;
	public static final int ACCESSIBILITY_LIVE_REGION_ASSERTIVE = 0x00000002;

	private int atl_accessibility_live_region = ACCESSIBILITY_LIVE_REGION_NONE;

	public void setAccessibilityLiveRegion(int mode) {
		atl_accessibility_live_region = mode;
	}

	public int getMeasuredWidthAndState() {
		return measuredWidth;
	}

	public void forceLayout() {
		if (Looper.myLooper() == Looper.getMainLooper()) {
			requestLayout();
		} else {
			getHandler().post(new Runnable() {
				@Override
				public void run() {
					requestLayout();
				}
			});
		}
	}

	private final CopyOnWriteArrayList<OnAttachStateChangeListener> attach_state_listeners =
			new CopyOnWriteArrayList<OnAttachStateChangeListener>();
	public void removeOnAttachStateChangeListener(OnAttachStateChangeListener listener) {
		attach_state_listeners.remove(listener);
	}

	@Export
	public boolean onInterceptTouchEvent(MotionEvent event) { return false; }

	@Export
	public boolean dispatchTouchEvent(MotionEvent event) {
		if (on_touch_listener != null && on_touch_listener.onTouch(this, event))
			return true;
		return onTouchEvent(event);
	}

	public boolean canScrollHorizontally(int direction) {
		/* Same AOSP maths horizontally. */
		final int offset = computeHorizontalScrollOffset();
		final int range = computeHorizontalScrollRange();
		final int extent = computeHorizontalScrollExtent();
		if (range <= 0)
			return false;
		if (direction < 0)
			return offset > 0;
		return range > extent + offset;
	}

	protected native boolean native_getGlobalVisibleRect(long widget, Rect visibleRect);

	public boolean getGlobalVisibleRect(Rect visibleRect) {
		return native_getGlobalVisibleRect(widget, visibleRect);
	}

	public boolean onCheckIsTextEditor() { return false; }

	/* Click listener state (queue item 177): AOSP answers whether an
	 * OnClickListener is registered; the fork said false even though
	 * setOnClickListener stores one, so apps' "do I own this click?"
	 * branches (and androidx click-handling helpers) took the wrong
	 * path. */
	public boolean hasOnClickListeners() {
		return on_click_listener != null;
	}

	/* Text alignment (queue item 211): the setter mapped the value onto
	 * GTK CSS classes but dropped the value itself, so the getter had
	 * nothing to answer with. AOSP keeps it (PFLAG2_TEXT_ALIGNMENT_*) and
	 * getTextAlignment() returns the resolved value, defaulting to
	 * TEXT_ALIGNMENT_GRAVITY for a view that never had it set. */
	private int atl_text_alignment = TEXT_ALIGNMENT_GRAVITY;

	public void setTextAlignment(int textAlignment) {
		atl_text_alignment = textAlignment;
		String[] classesToRemove = {"ATL-text-align-left", "ATL-text-align-center", "ATL-text-align-right"};
		native_removeClasses(widget, classesToRemove);

		switch (textAlignment) {
			case TEXT_ALIGNMENT_CENTER:
				native_addClass(widget, "ATL-text-align-center");
				break;
			case TEXT_ALIGNMENT_TEXT_START:
			case TEXT_ALIGNMENT_VIEW_START:
				native_addClass(widget, "ATL-text-align-left");
				break;
			case TEXT_ALIGNMENT_TEXT_END:
			case TEXT_ALIGNMENT_VIEW_END:
				native_addClass(widget, "ATL-text-align-right");
				break;
		}
	}

	/* Haptic feedback (queue item 268): AOSP keeps the flag (default on,
	 * from the view root's setting) and performHapticFeedback returns
	 * false without it. The fork vibrates for real (queue item 136's
	 * work), so honouring the flag is observable: an app that turns
	 * haptics off now really stays quiet. */
	private boolean atl_haptic_feedback_enabled = true;

	public void setHapticFeedbackEnabled(boolean hapticFeedbackEnabled) {
		atl_haptic_feedback_enabled = hapticFeedbackEnabled;
	}

	public boolean isHapticFeedbackEnabled() {
		return atl_haptic_feedback_enabled;
	}

	public StateListAnimator getStateListAnimator() {
		/* AOSP keeps the animator set here (or loaded from
		 * android:stateListAnimator at inflation, which goes through
		 * setStateListAnimator below) and reads it back; the fork
		 * applied the animator but dropped the reference, so
		 * getStateListAnimator() was null even right after inflation
		 * (queue item 236). */
		return atl_state_list_animator;
	}

	public void requestFitSystemWindows() {
		/* AOSP (queue item 278) walks this up the parent chain
		 * (ViewGroup.requestFitSystemWindows) to the view root, whose
		 * ViewRootImpl then schedules an insets re-dispatch — which is
		 * exactly what AOSP's requestApplyInsets does with it
		 * (requestApplyInsets calls requestFitSystemWindows). The fork
		 * has no ViewRootImpl or fit-system-windows flag plumbing, but
		 * its requestApplyInsets really re-runs the insets chain
		 * (queue item 128), so route to that: the observable effect is
		 * kept, the internal propagation detail is what ATL lacks. */
		requestApplyInsets();
	}

	public native void getWindowVisibleDisplayFrame(Rect rect);

	/* Same render-node transform family as scaleX/pivotX above (queue
	 * items 121-123): ROTATION Property (see the Property objects)
	 * animates through these, and the getters were constants. State is
	 * stored AOSP-style; the gsk draw path still ignores it on screen
	 * (same rendering follow-up as scale/pivot). */
	private float rotation = 0.f;
	private float rotationX = 0.f;
	private float rotationY = 0.f;

	public void setRotation(float rotation) {
		this.rotation = rotation;
	}
	public void setRotationX(float deg) {
		rotationX = deg;
	}
	public void setRotationY(float deg) {
		rotationY = deg;
	}

	public float getRotationX() {
		return rotationX;
	}
	public float getRotationY() {
		return rotationY;
	}

	/* AOSP render-node transform properties (queue items 109-112). The
	 * setters used to be empty and the getters constants, so View.SCALE_X
	 * /SCALE_Y animators (the Property objects above feed exactly these
	 * accessors) animated against a value that never changed and apps
	 * reading the transform back saw 1/0. State is now stored AOSP-style,
	 * including the unset-pivot default of width/2, height/2 (AOSP keeps
	 * a "pivot set" flag for exactly this). Honest limitation: the gsk
	 * draw path does not yet apply these transforms on screen (only
	 * translationX/Y reach the JNI allocation, android_view_View.c) -
	 * that is a separate rendering follow-up; the API state itself is
	 * what these queue items flagged. */
	private float scaleX = 1.f;
	private float scaleY = 1.f;
	private float pivotX = 0.f;
	private float pivotY = 0.f;
	private boolean pivotXSet = false;
	private boolean pivotYSet = false;

	public void setScaleX(float scaleX) {
		this.scaleX = scaleX;
	}

	public void setScaleY(float scaleY) {
		this.scaleY = scaleY;
	}

	public float getScaleX() {
		return scaleX;
	}

	public float getScaleY() {
		return scaleY;
	}

	public void setPivotX(float pivot_x) {
		pivotX = pivot_x;
		pivotXSet = true;
	}

	public void setPivotY(float pivot_y) {
		pivotY = pivot_y;
		pivotYSet = true;
	}

	public float getPivotX() {
		return pivotXSet ? pivotX : getWidth() / 2f;
	}

	public float getPivotY() {
		return pivotYSet ? pivotY : getHeight() / 2f;
	}

	/* Translation Z (queue item 182): AOSP keeps it as real state and
	 * getZ() is elevation + translationZ. That matters in ATL, not just in
	 * HWUI: ViewGroup.addView sorts children by getZ() before handing the
	 * order to GTK, so with translationZ dropped the sort saw elevation
	 * only (setZ was empty too, so setting z directly did nothing). */
	private float atl_translation_z = 0f;

	public float getTranslationZ() {
		return atl_translation_z;
	}

	public void setTranslationZ(float translationZ) {
		atl_translation_z = translationZ;
		invalidate();
	}

	/* AOSP returns the visibility flags currently applied to the
	 * window; queue item 127 flagged the constant 0. ATL's window model
	 * has no system bars, and setSystemUiVisibility on this view's
	 * chain is the only thing that ever changes window-level UI state
	 * (it drives fullscreen), so the view's own tracked flags are the
	 * closest honest answer and 0 for untouched views either way. */
	public int getWindowSystemUiVisibility() {
		return system_ui_visibility;
	}

	/* Scroll indicator state (queue item 192). AOSP keeps the indicator
	 * bits (its own flag encoding, public values TOP=1, BOTTOM=2, LEFT=4,
	 * RIGHT=8, START=16, END=32); the setter dropped them and the fork had
	 * neither the getter nor the constants. ATL draws no scroll indicators
	 * of its own (GTK scrollbars are the visible affordance), but scroll
	 * containers set and read these bits, so they now round-trip. */
	public static final int SCROLL_INDICATOR_TOP = 1 << 0;
	public static final int SCROLL_INDICATOR_BOTTOM = 1 << 1;
	public static final int SCROLL_INDICATOR_LEFT = 1 << 2;
	public static final int SCROLL_INDICATOR_RIGHT = 1 << 3;
	public static final int SCROLL_INDICATOR_START = 1 << 4;
	public static final int SCROLL_INDICATOR_END = 1 << 5;

	private static final int SCROLL_INDICATORS_ALL =
			SCROLL_INDICATOR_TOP | SCROLL_INDICATOR_BOTTOM | SCROLL_INDICATOR_LEFT
			| SCROLL_INDICATOR_RIGHT | SCROLL_INDICATOR_START | SCROLL_INDICATOR_END;

	private int atl_scroll_indicators = 0;

	public int getScrollIndicators() {
		return atl_scroll_indicators;
	}

	public void setScrollIndicators(int indicators) {
		setScrollIndicators(indicators, SCROLL_INDICATORS_ALL);
	}

	public void setScrollIndicators(int indicators, int mask) {
		atl_scroll_indicators = (indicators & mask) | (atl_scroll_indicators & ~mask);
	}

	public void setLayoutDirection(int layoutDirection) {
		/* Stored for apps that read their own setting back; ATL's layout
		 * engine is LTR-only (getLayoutDirection keeps answering LTR so
		 * gravity resolution and GTK layout stay consistent). RTL layout
		 * mirroring is a separate, larger gap, not hidden by this setter. */
		this.layoutDirection = layoutDirection;
	}

	public ColorStateList getBackgroundTintList() {
		return background_tint_list;
	}

	public PorterDuff.Mode getBackgroundTintMode() {
		return background_tint_mode;
	}

	private String atl_transition_name = null;

	private static final WindowId atl_window_id = new WindowId();

	public WindowId getWindowId() {
		/* AOSP returns the id of the window the view is attached to, null
		 * while detached (apps compare it to test attachment and to group
		 * views by window). ATL has one window per process, like the one
		 * windowToken (see getWindowToken), so the id is process-wide; it
		 * is only handed out once this view or an ancestor has been
		 * laid out by the window — the same attach test as the token.
		 * (queue item 210) */
		View v = this;
		while (v != null) {
			if (v.windowTokenAttached)
				return atl_window_id;
			v = v.parent instanceof View ? (View) v.parent : null;
		}
		return null;
	}

	/* isInLayout (queue item 185): the FIXME is resolved by using the flag
	 * the fork already maintains — layoutInternal() sets in_layout for the
	 * duration of the GTK-driven layout pass (the same window-driven pass
	 * AOSP's isInLayout() describes) and clears it at the end. Apps use
	 * this to avoid re-entrant requestLayout() from a layout pass; the
	 * constant false hid that state. */
	public boolean isInLayout() {
		return in_layout;
	}

	/* Text direction state (queue item 140): AOSP stores it and derives
	 * the layout direction from it (resolveLayoutDirection). ATL's
	 * getters already answer LTR unconditionally, which equals what
	 * TEXT_DIRECTION_INHERIT/LOCALE resolve to in ATL's fixed-LTR
	 * world; the setter now keeps the flag so get/set round-trip.
	 * Acting on RTL text remains a separate gap. */
	private int atl_text_direction = TEXT_DIRECTION_INHERIT;

	public void setTextDirection(int textDirection) {
		atl_text_direction = textDirection;
	}

	public Drawable getForeground() {
		return atl_foreground;
	}

	/* Scrollbar fading (queue item 296): AOSP keeps fadeScrollBars in its
	 * ScrollabilityCache (created by initScrollCache); the getter answers
	 * false until that cache exists, and the cache itself defaults to
	 * fading. Stored plainly here — the **scrollbar drawing gap** (noted
	 * at setScrollBarStyle) stays recorded: nothing in ATL paints a
	 * scrollbar for the fading state to apply to. AOSP also has
	 * setScrollbarFadingEnabled's siblings (fade delay etc.) reading the
	 * same cache. */
	private boolean atl_scrollbar_fading_enabled = false;

	public void setScrollbarFadingEnabled(boolean fadeEnabled) {
		atl_scrollbar_fading_enabled = fadeEnabled;
	}

	public boolean isScrollbarFadingEnabled() {
		return atl_scrollbar_fading_enabled;
	}

	/* Scrollbar position (queue item 297): AOSP's constants — copied
	 * verbatim, apps compile against these values — and its
	 * mVerticalScrollbarPosition, default DEFAULT. AOSP's setter also
	 * recomputes opaque flags and padding, which only matter to its
	 * scrollbar drawing and padding resolution; the **scrollbar drawing
	 * gap** stays recorded. */
	public static final int SCROLLBAR_POSITION_DEFAULT = 0;

	public static final int SCROLLBAR_POSITION_LEFT = 1;

	public static final int SCROLLBAR_POSITION_RIGHT = 2;

	private int atl_vertical_scrollbar_position = SCROLLBAR_POSITION_DEFAULT;

	/* Scrollbar style (queue item 283): AOSP keeps it in mViewFlags
	 * (SCROLLBARS_STYLE_MASK — the fork already declares these
	 * constants) and its scrollbar drawing, opacity computation and
	 * padding resolution read it back. The setter dropped the value.
	 * Stored with AOSP's default (SCROLLBARS_INSIDE_INSET); the
	 * **scrollbar drawing gap** (same as the fading-edge one above)
	 * stays recorded — nothing here paints a scrollbar to apply the
	 * style to. AOSP's own getScrollBarStyle is @hide, so no public
	 * caller reads it. */
	private int atl_scrollbar_style = SCROLLBARS_INSIDE_INSET;

	public void setScrollBarStyle(int style) {
		atl_scrollbar_style = style & SCROLLBARS_STYLE_MASK;
	}

	public void setVerticalScrollbarPosition(int position) {
		atl_vertical_scrollbar_position = position;
	}

	public int getVerticalScrollbarPosition() {
		return atl_vertical_scrollbar_position;
	}

	public void setNestedScrollingEnabled(boolean enabled) {
		nested_scrolling_enabled = enabled;
	}

	/* Touch delegation (queue item 245): AOSP keeps the delegate here
	 * and getTouchDelegate() reads it back; its touch hit-testing then
	 * asks the delegate whether the touch belongs to it
	 * (touchDelegateExists). ATL receives touches as GTK gestures and
	 * dispatches them to the widget GTK picked, so there is no
	 * Android-side hit-test to consult the delegate in — **gap: touch
	 * delegation in ATL's input path**. Storage + round-trip is what the
	 * pair guarantees and what apps (and androidx) read back. */
	private TouchDelegate atl_touch_delegate = null;

	public void setTouchDelegate(TouchDelegate touchDelegate) {
		atl_touch_delegate = touchDelegate;
	}

	/* Drag and drop (queue item 221): the listener is stored now, but
	 * nothing can call it: the fork's OnDragListener is an empty marker
	 * interface (no onDrag signature) and there is no DragEvent class and
	 * no dispatchDragEvent path — ATL receives no GTK DnD feed either.
	 * **Gap: drag & drop subsystem** (DragEvent, ClipDescription, the
	 * dispatch chain and a GTK DnD source). Storing alone is what AOSP's
	 * setter guarantees; the callback contract needs the subsystem. */
	private OnDragListener on_drag_listener = null;

	public void setOnDragListener(OnDragListener onDragListener) {
		on_drag_listener = onDragListener;
	}

	public void setTransitionName(String transitionName) {
		/* Transition name (queue item 203): AOSP keeps it (setTransitionName
		 * is final there, getTransitionName() reads it back for the
		 * activity-transition scene grab, which walks the hierarchy
		 * looking for non-null names). ATL has no activity transition
		 * machinery, but apps and androidx set and query it. */
		atl_transition_name = transitionName;
	}

	public String getTransitionName() {
		return atl_transition_name;
	}

	private ViewOverlay atl_overlay = null;

	public ViewOverlay getOverlay() {
		/* AOSP keeps one overlay per view (lazily) and getOverlay()
		 * returns that same object, so a caller can add drawables now and
		 * query or clear them later. The fork built a new empty overlay
		 * on every call, so `getOverlay().add(d)` was unobservable and a
		 * later getOverlay().clear() cleared nothing (queue item 204).
		 * The overlay's own drawables are not composited by ATL's gsk
		 * path yet - rendering gap - but the object identity that
		 * callers (and androidx) rely on is now right, matching
		 * ViewGroup.getOverlay() below. */
		if (atl_overlay == null)
			atl_overlay = new ViewOverlay();
		return atl_overlay;
	}

	public void cancelLongPress() {
		removeCallbacks(pendingLongClick);
		pendingLongClick = null;
	}

	public int getTextAlignment() {
		/* AOSP keeps the resolved alignment (setTextAlignment below already
		 * maps the value onto GTK CSS classes but dropped the value
		 * itself), so setTextAlignment(TEXT_ALIGNMENT_CENTER) followed by
		 * getTextAlignment() answered INHERIT. AOSP's default is
		 * TEXT_ALIGNMENT_GRAVITY (the value a bare View resolves to). */
		return atl_text_alignment;
	}

	public View findViewWithTag(Object tag) {
		if (Objects.equals(tag, this.tag))
			return this;
		else
			return null;
	}

	/* Tooltip state (queue item 171): AOSP stores the text (and shows
	 * it on hover/long-click); the fork dropped it and had no getter at
	 * all. ATL shows no tooltip UI yet (a rendering gap), but the value
	 * round-trips now. */
	private CharSequence atl_tooltip_text = null;

	public void setTooltipText(CharSequence tooltip) {
		atl_tooltip_text = tooltip;
	}

	public CharSequence getTooltipText() {
		return atl_tooltip_text;
	}

	/* Autofill hints (queue item 113): AOSP stores the flag and hands it
	 * back through getImportantForAutofill; the autofill service itself
	 * does not exist in ATL, but the state is real API state. */
	public static final int IMPORTANT_FOR_AUTOFILL_AUTO = 0x00;
	public static final int IMPORTANT_FOR_AUTOFILL_YES = 0x01;
	public static final int IMPORTANT_FOR_AUTOFILL_NO = 0x02;
	public static final int IMPORTANT_FOR_AUTOFILL_NO_EXCLUDE_DESCENDANTS = 0x04;

	private int importantForAutofill = IMPORTANT_FOR_AUTOFILL_AUTO;

	public void setImportantForAutofill(int flag) {
		importantForAutofill = flag;
	}

	public int getImportantForAutofill() {
		return importantForAutofill;
	}

	/* Focus highlight (queue item 183): AOSP keeps the flag (default true,
	 * the state getDefaultFocusHighlightEnabled() reports — which the fork
	 * did not even declare); the setter dropped it. ATL draws no focus
	 * highlight of its own (GTK theme draws the focus ring), but apps and
	 * androidx read this back, so it round-trips. */
	private boolean atl_default_focus_highlight = true;

	public void setDefaultFocusHighlightEnabled(boolean enabled) {
		atl_default_focus_highlight = enabled;
	}

	public final boolean getDefaultFocusHighlightEnabled() {
		return atl_default_focus_highlight;
	}

	/* Fading edges (queue item 275): AOSP keeps these as mViewFlags bits
	 * (FADING_EDGE_HORIZONTAL/VERTICAL, the constants the fork already
	 * declares) toggled by the pair of setters; the fork dropped the
	 * value and never declared the getters. Stored now. **gap: edge
	 * effect / scrollbar drawing in ATL** — AOSP fades a scrolled view's
	 * edges in its own draw path; ATL renders through GTK and paints no
	 * fading edges or scrollbars (same gap as setVerticalScrollbarPosition),
	 * so the flag is state nothing paints. AOSP's getter is the is…Enabled
	 * pair. */
	private int atl_fading_edge_flags = FADING_EDGE_NONE;

	public void setHorizontalFadingEdgeEnabled(boolean horizontalFadingEdgeEnabled) {
		if (isHorizontalFadingEdgeEnabled() != horizontalFadingEdgeEnabled)
			atl_fading_edge_flags ^= FADING_EDGE_HORIZONTAL;
	}

	public boolean isHorizontalFadingEdgeEnabled() {
		return (atl_fading_edge_flags & FADING_EDGE_HORIZONTAL) != 0;
	}

	public Handler getHandler() {
		if (handler == null)
			handler = new Handler(Looper.getMainLooper());
		return handler;
	}

	/* AOSP answers mAttachInfo.mHardwareAccelerated, i.e. "HWUI is
	 * drawing this view" (queue item 175). ATL draws through GTK4 render
	 * nodes, not HWUI, and the APIs apps gate on this flag for - layer
	 * types, hardware overlays, some render-node paths - do not exist in
	 * the fork, so false is the honest answer, not a placeholder. */
	public boolean isHardwareAccelerated() {
		return false;
	}

	/* Clip state (queue items 124, 129): AOSP clips drawing to these.
	 * The gsk draw path does not consume them yet (same rendering
	 * follow-up as the transform state), but apps read these back
	 * (getClipBounds) and branch on them, so the state is real API
	 * state now. AOSP defaults: clipToOutline false, clipBounds null. */
	private Rect atl_clip_bounds = null;
	private boolean atl_clip_to_outline = false;

	public void setClipBounds(Rect clipBounds) {
		atl_clip_bounds = clipBounds;
	}

	public Rect getClipBounds() {
		return atl_clip_bounds;
	}

	public void setClipToOutline(boolean clipToOutline) {
		atl_clip_to_outline = clipToOutline;
	}

	public boolean getClipToOutline() {
		return atl_clip_to_outline;
	}

	public void setLeft(int left) {
		layout(left, top, right, bottom);
	}

	public void setTop(int top) {
		layout(left, top, right, bottom);
	}

	public void setRight(int right) {
		layout(left, top, right, bottom);
	}

	public void setBottom(int bottom) {
		layout(left, top, right, bottom);
	}

	/* Camera distance (queue item 222): AOSP pushes it into the view's
	 * RenderNode (normalised by display density) and the getter reads it
	 * back scaled by density; it only ever matters when the node is
	 * drawn with rotationX/rotationY. ATL has no RenderNode and consumes
	 * none of the 3-D view properties (rotationX/rotationY are stored but
	 * nothing composites them — rendering gap), so there is nowhere to
	 * keep or apply this. Returning 0 matches a view whose perspective is
	 * never used; recorded rather than inventing a scale that draws
	 * nothing. */
	public void setCameraDistance(float distance) {}

	/* Scroll-into-view request (queue items 167, 168): AOSP walks the
	 * parent chain calling requestChildRectangleOnScreen; ATL's
	 * ViewParent/ViewGroup has no such hook (no scroll-into-view
	 * machinery at all), so nothing can scroll and "false = no parent
	 * scrolled" is the faithful answer. Explicit no-op: recorded with
	 * the scroll machinery gap. */
	public boolean requestRectangleOnScreen(Rect rectangle, boolean immediate) { return false; }

	public boolean requestRectangleOnScreen(Rect rectangle) { return false; }

	@Export
	/* AOSP's confirm-key (ENTER/DPAD_CENTER) handling presses the view
	 * and, with the key-up half in dispatchKeyEvent, performs the click
	 * (queue item 178). ATL cannot do that honestly today: the
	 * dispatchKeyEvent above only handles ACTION_DOWN and drops the up
	 * event, and the fork has no checkForLongClick/press-target
	 * machinery, so pressing without releasing would leave views stuck
	 * pressed and still never click. Keyboard activation of views is
	 * therefore recorded as its own input gap (key events reach ATL's
	 * focused widget via api-impl-jni/widgets/WrapperWidget.c); the
	 * fork's false = "not handled" is what an unclickable AOSP view
	 * returns. */
	public boolean onKeyDown(int keyCode, KeyEvent event) {
		return false;
	}

	@Export
	public boolean dispatchKeyEvent(KeyEvent event) {
		/* AOSP's dispatchKeyEventNoClient gives the registered
		 * OnKeyListener the event before the view handles it, and stops
		 * if the listener consumes it (queue item 191). The rest stays as
		 * the fork has it: only the DOWN half is handled here, the UP half
		 * is the keyboard-activation gap recorded at onKeyDown. */
		if (on_key_listener != null
				&& on_key_listener.onKey(this, event.getKeyCode(), event))
			return true;
		if (event.getAction() == KeyEvent.ACTION_DOWN)
			return onKeyDown(event.getKeyCode(), event);
		else
			return false;
	}

	/* AOSP returns the root window's insets for attached views and null
	 * for detached ones; queue item 118 flagged the unconditional empty
	 * object. ATL has no system bars, so an attached view's root insets
	 * are genuinely empty - the null case is what callers gate on. */
	public WindowInsets getRootWindowInsets() {
		return windowTokenAttached ? new WindowInsets() : null;
	}

	/* Pointer icon state (queue item 125): AOSP default is null and
	 * getPointerIcon() feeds the pointer-icon machinery; storing the
	 * flag keeps set/get honest even though ATL has no cursor shape
	 * plumbing yet. */
	private PointerIcon atl_pointer_icon = null;

	public PointerIcon getPointerIcon() {
		return atl_pointer_icon;
	}

	public void setPointerIcon(PointerIcon pointerIcon) {
		atl_pointer_icon = pointerIcon;
	}

	/* Same window token as getWindowToken (queue item 150): AOSP answers
	 * attachInfo.mToken for both on phones (per-window == per-app in
	 * ATL, one window per app). */
	public IBinder getApplicationWindowToken() {
		return getWindowToken();
	}

	/* Fading-edge length (queue item 157): AOSP setFadingEdgeLength()
	 * sets the same length for both axes and the getters return it; the
	 * setter stored nothing and the getter was constant 0. ATL composites
	 * no fading edges yet (a rendering gap), but scroll containers set
	 * the length and read it back, so the state round-trips now;
	 * getHorizontalFadingEdgeLength was absent from the fork. */
	private int fading_edge_length = 0;

	public void setFadingEdgeLength(int length) {
		fading_edge_length = length;
	}

	public int getVerticalFadingEdgeLength() {
		return fading_edge_length;
	}

	public int getHorizontalFadingEdgeLength() {
		return fading_edge_length;
	}

	public int getVerticalScrollbarWidth() { return 0; }

	public void saveAttributeDataForStyleable(Context ctxt, int[] styleable, AttributeSet attrs, TypedArray t, int defStyleAttr, int defStyleRes) {}

	public final View requireViewById(int id) {
		View view = findViewById(id);
		if (view == null)
			throw new IllegalArgumentException("ID does not reference a View inside this View");
		return view;
	}
	public float getTransitionAlpha() {
		return 1.0f;
	}

	public void onWindowFocusChanged(boolean hasFocus) {}

	/* AOSP: accessibility services drive view actions through this. ATL
	 * has no a11y node infrastructure (its AccessibilityDelegate has no
	 * performAccessibilityAction hook), so only the actions a view can
	 * genuinely perform are handled; everything else returns false, the
	 * same answer AOSP gives when no receiver takes the action (queue
	 * item 105). */
	public boolean performAccessibilityAction(int action, Bundle arguments) {
		switch (action) {
		case AccessibilityNodeInfo.ACTION_FOCUS:
			return requestFocus();
		case AccessibilityNodeInfo.ACTION_CLEAR_FOCUS:
			clearFocus();
			return true;
		default:
			return false;
		}
	}

	/* Dirty state (queue item 257): AOSP answers whether the view has
	 * been invalidated and not yet redrawn (PFLAG_DIRTY_MASK). ATL really
	 * does run Android's draw path for Java views (draw() below is called
	 * from GTK's draw, see WrapperWidget.c), so there is a moment to
	 * clear it: propagateInvalidation is the fork's single invalidation
	 * funnel (invalidate() calls it and native code calls it for GTK
	 * widgets), and draw() is the redraw. */
	private boolean atl_dirty = false;

	public boolean isDirty() {
		return atl_dirty;
	}

	public float getX() {
		return getLeft() + getTranslationX();
	}
	public float getY() {
		return getTop() + getTranslationY();
	}

	public boolean getGlobalVisibleRect(Rect visibleRect, Point globalOffset) {
		boolean result = native_getGlobalVisibleRect(widget, visibleRect);
		globalOffset.set(visibleRect.left, visibleRect.top);
		return result;
	}

	/* See saveHierarchyState above: no saved-state pipeline in ATL feeds this
	 * (queue item 184), so there is no state to restore. */
	public void restoreHierarchyState(SparseArray<Parcelable> container) {}

	public void scrollBy(int x, int y) {
		scrollTo(scrollX + x, scrollY + y);
	}

	/* Accessibility pane title (queue item 188): AOSP stores it and
	 * announces the pane to accessibility services through the view's
	 * accessibility node. ATL exposes no accessibility node pipeline (the
	 * whole android.view.accessibility surface is stubs — same finding as
	 * queue item 141), so there is nothing to store for and nothing to
	 * announce: faithful to the fork's a11y state, recorded here rather than
	 * storing a value no code path can read back (AOSP's View has no public
	 * getter either). */
	public void setAccessibilityPaneTitle(CharSequence paneTitle) {}

	/* Accessibility heading flag (queue item 196): AOSP stores it and
	 * reports it through the view's accessibility node
	 * (isAccessibilityHeading() is AOSP's public getter, absent from the
	 * fork). The setter dropped it; ATL has no accessibility node
	 * pipeline to consume it (gap recorded at setAccessibilityPaneTitle),
	 * but the flag is real API state and androidx's ViewCompat layer reads
	 * it back. */
	private boolean atl_accessibility_heading = false;

	public void setAccessibilityHeading(boolean isHeading) {
		atl_accessibility_heading = isHeading;
	}

	public boolean isAccessibilityHeading() {
		return atl_accessibility_heading;
	}

	public WindowInsets computeSystemWindowInsets(WindowInsets insets, Rect contentInsets) { return insets; }

	public boolean isDuplicateParentStateEnabled() { return duplicateParentState; }

	/* Same tint family as setBackgroundTintList above (queue item 163):
	 * mode stored and read back; the gsk draw path applies neither yet. */
	private PorterDuff.Mode background_tint_mode = null;

	public void setBackgroundTintMode(PorterDuff.Mode tintMode) {
		background_tint_mode = tintMode;
	}

	/* Next-focus ids (queue items 246, 247): AOSP stores them (default
	 * NO_ID) and the public get… methods read them back; its own focus
	 * search honours them. ATL's focus is GTK's (queue item 88's
	 * finding) and the fork has no Android focus search at all, so these
	 * ids steer nothing here — **gap: Android focus-search subsystem**.
	 * They are still real API state (set from XML inflation and by
	 * callers), and the fork had the setters dropping it while the AOSP
	 * getters were not even declared. */
	private int atl_next_focus_left_id = NO_ID;
	private int atl_next_focus_right_id = NO_ID;
	private int atl_next_focus_up_id = NO_ID;
	private int atl_next_focus_down_id = NO_ID;

	public void setNextFocusLeftId(int id) {
		atl_next_focus_left_id = id;
	}

	public int getNextFocusLeftId() {
		return atl_next_focus_left_id;
	}

	public void setNextFocusRightId(int id) {
		atl_next_focus_right_id = id;
	}

	public int getNextFocusRightId() {
		return atl_next_focus_right_id;
	}

	public void setNextFocusDownId(int id) {
		atl_next_focus_down_id = id;
	}

	public int getNextFocusDownId() {
		return atl_next_focus_down_id;
	}

	public void setNextFocusUpId(int id) {
		atl_next_focus_up_id = id;
	}

	public int getNextFocusUpId() {
		return atl_next_focus_up_id;
	}

	public void setHasTransientState(boolean hasTransientState) {
		/* AOSP reference-counts the flag and clamps it at 0 (an unpaired
		 * false is a bug in the caller, which AOSP logs). ATL's ViewGroup
		 * does not propagate the count to its parents
		 * (requestParentHasTransientState) and there is no view-recycling
		 * pool to gate on it - recorded with the recycling gap. */
		atl_transient_state_count = hasTransientState
				? atl_transient_state_count + 1 : atl_transient_state_count - 1;
		if (atl_transient_state_count < 0)
			atl_transient_state_count = 0;
	}

	protected void onConfigurationChanged(Configuration newConfig) {}

	/* Drawing cache (queue item 267): AOSP keeps the enabled flag (and
	 * getDrawingCache() returns the cached Bitmap once built, null while
	 * disabled). The flag round-trips now; the Bitmap itself cannot be
	 * produced — **gap: drawing-cache/snapshot subsystem**: AOSP renders
	 * the view into a Bitmap with its own software renderer, and ATL has
	 * no such path (its drawing goes through GTK/GSK), so
	 * getDrawingCache/buildDrawingCache have nothing to build. */
	private boolean atl_drawing_cache_enabled = false;

	public boolean isDrawingCacheEnabled() {
		return atl_drawing_cache_enabled;
	}

	public void setDrawingCacheEnabled(boolean enabled) {
		atl_drawing_cache_enabled = enabled;
	}

	public void buildDrawingCache(boolean autoScale) {}

	public Bitmap getDrawingCache() { return null; }

	public void announceForAccessibility(CharSequence text) {
		/* AOSP pushes a text announcement through the accessibility
		 * node pipeline to TalkBack (queue item 235). ATL has no
		 * accessibility node pipeline at all (gap recorded at
		 * setAccessibilityPaneTitle), so there is no node to announce
		 * through; an honest no-op. */
	}

	public WindowInsetsController getWindowInsetsController() {
		return native_get_window(widget).getInsetsController();
	}

	/* Keyboard navigation cluster (queue item 216): AOSP stores the flag
	 * (focus search jumps between clusters) and isKeyboardNavigationCluster
	 * reads it back; the setter dropped it while the getter was already
	 * declared — and hard-wired to false, so the pair never round-tripped.
	 * ATL's focus is owned by GTK (queue item 88's finding), so the
	 * cluster does not steer any search here, but the flag is real API
	 * state that androidx round-trips. */
	private boolean atl_keyboard_navigation_cluster = false;

	public void setKeyboardNavigationCluster(boolean isCluster) {
		atl_keyboard_navigation_cluster = isCluster;
	}

	public final boolean isKeyboardNavigationCluster() {
		return atl_keyboard_navigation_cluster;
	}

	/* Autofill ids (queue item 151): the old getter built a *fresh*
	 * AutofillId on every call, so identity/equality comparisons apps
	 * and frameworks do (setAutofillId/getAutofillId round-trips,
	 * autofill-target lookups) always failed. AOSP lazily assigns a
	 * unique id per view; ATL has no autofill service, but the id
	 * object is still real API state. */
	private static int next_autofill_id = 1;
	private AutofillId autofill_id = null;

	public AutofillId getAutofillId() {
		if (autofill_id == null)
			autofill_id = new AutofillId(next_autofill_id++);
		return autofill_id;
	}

	public void setAutofillId(AutofillId autofillId) {
		autofill_id = autofillId;
	}

	public AccessibilityDelegate getAccessibilityDelegate() { return accessibilityDelegate; }

	public void setScrollX(int value) {
		scrollTo(value, scrollY);
	}
	public void setScrollY(int value) {
		scrollTo(scrollX, value);
	}

	@Export
	/* AOSP chain (queue item 158): listener if set, else onHoverEvent.
	 * ATL's GTK input does not feed hover MotionEvents into the view
	 * tree yet (input gap), but apps' re-dispatch and listener paths
	 * (tooltips, touchpads via injected events) get the real chain
	 * now. */
	protected boolean dispatchHoverEvent(MotionEvent event) {
		if (on_hover_listener != null)
			return on_hover_listener.onHover(this, event);
		return onHoverEvent(event);
	}

	protected boolean onHoverEvent(MotionEvent event) {
		/* AOSP's base implementation only uses the hover event for
		 * accessibility hover enter/exit and returns false unless a
		 * subclass (ViewGroup, AbsListView) consumes it; false is also
		 * what it returns when the view is not hoverable. ATL has no
		 * accessibility node pipeline for the hover part and GTK hover
		 * MotionEvents are not fed to the view tree yet (gap recorded at
		 * dispatchHoverEvent above), so false is the faithful base answer
		 * (queue item 197: not a dropped-state case). */
		return false;
	}

	public ActionMode startActionMode(ActionMode.Callback callback, int type) { return null; }

	public InputConnection onCreateInputConnection(EditorInfo outAttrs) { return null; }

	public ActionMode startActionMode(ActionMode.Callback callback) { return null; }

	public void getFocusedRect(Rect r) {
		/* AOSP base View answers the view's own drawing rect (ViewGroup
		 * overrides to find the focused child's rect; ATL's focus search
		 * is GTK's so there is no focused child to report). Queue item
		 * 164: the body was empty, so callers got a 0x0 rect at origin
		 * even for a laid-out view. */
		getDrawingRect(r);
	}

	/**
	* An implementation of OnClickListener that attempts to lazily load a
	* named click handling method from a parent or ancestor context.
	* See AOSP commit d13f0e21bf8059779c9330a5993907ec88c8e781
	* Copyright (C) 2006 The Android Open Source Project
	*/
	private static class DeclaredOnClickListener implements OnClickListener {
		private final View mHostView;
		private final String mMethodName;
		private Method mResolvedMethod;
		private Context mResolvedContext;
		public DeclaredOnClickListener(@NonNull View hostView, @NonNull String methodName) {
			mHostView = hostView;
			mMethodName = methodName;
		}
		@Override
		public void onClick(@NonNull View v) {
			if (mResolvedMethod == null)
				resolveMethod(mHostView.getContext(), mMethodName);
			try {
				mResolvedMethod.invoke(mResolvedContext, v);
			} catch (IllegalAccessException e) {
				throw new IllegalStateException(
				    "Could not execute non-public method for android:onClick", e);
			} catch (InvocationTargetException e) {
				throw new IllegalStateException(
				    "Could not execute method for android:onClick", e);
			}
		}

		@NonNull
		private void resolveMethod(@Nullable Context context, @NonNull String name) {
			while (context != null) {
				try {
					if (!context.isRestricted()) {
						final Method method = context.getClass().getMethod(mMethodName, View.class);
						if (method != null) {
							mResolvedMethod = method;
							mResolvedContext = context;
							return;
						}
					}
				} catch (NoSuchMethodException e) {
					// Failed to find method, keep searching up the hierarchy.
				}
				if (context instanceof ContextWrapper) {
					context = ((ContextWrapper)context).getBaseContext();
				} else {
					// Can't search up the hierarchy, null out and fail.
					context = null;
				}
			}

			final int id = mHostView.getId();
			final String idText = id == NO_ID ? "" : " with id '" + mHostView.getResources().getResourceEntryName(id) + "'";
			throw new IllegalStateException("Could not find method " + mMethodName
			                                + "(View) in a parent or ancestor Context for android:onClick "
			                                + "attribute defined on view " + mHostView.getClass() + idText);
		}
	}
	/* Force-dark flag (queue item 225): AOSP stores it (and
	 * getForceDarkAllowed reads it back) to let a view opt out of the
	 * system's forced-dark inversion. ATL applies no force-dark pass at
	 * all, so the flag gates nothing, but androidx (ViewCompat) sets and
	 * reads it. */
	private boolean atl_force_dark_allowed = true;

	public void setForceDarkAllowed(boolean forceDark) {
		atl_force_dark_allowed = forceDark;
	}

	public boolean getForceDarkAllowed() {
		return atl_force_dark_allowed;
	}
	/* Insets animation callback state (queue item 144): stored and read
	 * back; ATL has no window insets animations (no system bars to
	 * animate), so nothing calls the callback — the API state round-
	 * trips and apps' null checks behave like AOSP's. */
	private android.view.WindowInsetsAnimation.Callback insets_animation_callback = null;

	public void setWindowInsetsAnimationCallback(android.view.WindowInsetsAnimation.Callback callback) {
		insets_animation_callback = callback;
	}

	public android.view.WindowInsetsAnimation.Callback getWindowInsetsAnimationCallback() {
		return insets_animation_callback;
	}
	/* View translation callback (queue items 228, 229): AOSP keeps one
	 * callback (hidden API 33) that gets told about text-translation
	 * offsets. ATL has no translation service integration to fire it,
	 * but the setters' contract is storage and clear, so store it; that
	 * is all AOSP's setter guarantees in isolation. */
	private ViewTranslationCallback atl_view_translation_callback = null;

	public void setViewTranslationCallback(ViewTranslationCallback callback) {
		atl_view_translation_callback = callback;
	}

	public void clearViewTranslationCallback() {
		atl_view_translation_callback = null;
	}
	/* View-local <-> window-global matrix mapping (queue item 172). AOSP
	 * walks the parent chain composing the scroll offsets and each view's
	 * position, then its own transform matrix. The fork did nothing, so
	 * callers (tooltip/popup placement, drag-and-drop shadow, exposed
	 * surfaces) got an identity mapping. ATL's chain ends at
	 * WindowManagerImpl.WindowViewParent, which already sits at the
	 * window origin, so that leg needs no offset. Honest limitation:
	 * ATL's getMatrix() is always identity (the transform state from
	 * setScale/setRotation is not applied in the gsk draw path yet -
	 * same rendering follow-up), so AOSP's matrix concat leg is a no-op
	 * here by construction; positions and scrolls are real. */
	public void transformMatrixToGlobal(Matrix matrix) {
		ViewParent p = this.parent;
		if (p instanceof View) {
			View vp = (View) p;
			vp.transformMatrixToGlobal(matrix);
			matrix.preTranslate(-vp.scrollX, -vp.scrollY);
		}
		matrix.preTranslate(left, top);
	}

	public void transformMatrixToLocal(Matrix matrix) {
		ViewParent p = this.parent;
		if (p instanceof View) {
			View vp = (View) p;
			vp.transformMatrixToLocal(matrix);
			matrix.postTranslate(vp.scrollX, vp.scrollY);
		}
		matrix.postTranslate(-left, -top);
	}
	public boolean isShowingLayoutBounds() {
		/* AOSP's debug feature ("Show layout bounds", toggled via the
		 * developer option / `wm debug-layouts` broadcast) reflects
		 * whether the layout-bounds overlay is painted; default false
		 * (queue item 237). The fork painted no such overlay, so the
		 * old constant true told any checker the opposite of what was
		 * on screen. */
		return false;
	}

	public boolean callOnClick() {
		if (on_click_listener != null) {
			on_click_listener.onClick(this);
			return true;
		} else {
			return false;
		}
	}

	/* queue item 274: nothing was ever built — see the drawing-cache gap
	 * recorded at setDrawingCacheEnabled — so there is nothing to
	 * destroy. */
	public void destroyDrawingCache() {}

	public void setVerticalFadingEdgeEnabled(boolean verticalFadingEdgeEnabled) {
		/* queue item 275: same pair/flag family as the horizontal one
		 * above. */
		if (isVerticalFadingEdgeEnabled() != verticalFadingEdgeEnabled)
			atl_fading_edge_flags ^= FADING_EDGE_VERTICAL;
	}

	public boolean isVerticalFadingEdgeEnabled() {
		return (atl_fading_edge_flags & FADING_EDGE_VERTICAL) != 0;
	}

	public int getAccessibilityLiveRegion() {
		return atl_accessibility_live_region;
	}

	/* Label-for id (queue item 207): AOSP stores it (android:labelFor) and
	 * getLabelFor() reads it back for the accessibility label relation.
	 * The setter dropped it and the getter was not declared; ATL has no
	 * accessibility node pipeline to consume the relation (gap recorded at
	 * setAccessibilityPaneTitle), but the id is real API state and
	 * androidx's ViewCompat.getLabelFor() reads it. */
	private int atl_label_for = NO_ID;

	public void setLabelFor(int id) {
		atl_label_for = id;
	}

	public int getLabelFor() {
		return atl_label_for;
	}

	/* Focus candidates (queue item 160): AOSP's base View returns a
	 * list holding just itself when it is focusable in touch mode /
	 * visible / enabled; ViewGroup overrides it to collect descendants.
	 * The old empty-list answer meant focus search through this chain
	 * (setOnFocusChangeListener flows, directional D-pad moves) never
	 * found any view. */
	public ArrayList<View> getFocusables(int direction) {
		ArrayList<View> focusables = new ArrayList<View>();
		if (getVisibility() == VISIBLE && atl_focusable && isEnabled())
			focusables.add(this);
		return focusables;
	}

	public WindowInsets onApplyWindowInsets(WindowInsets insets) {
		return insets;
	}

	public void setAccessibilityTraversalBefore(int id) {}

	public int getLayerType() {
		return atl_layer_type;
	}

	public boolean isAccessibilityFocused() {
		/* AOSP reads PFLAG2_ACCESSIBILITY_FOCUSED, set by the
		 * accessibility-focus machinery that walks a node when a screen
		 * reader moves focus (queue item 242). ATL has no accessibility
		 * node pipeline at all (gap recorded at setAccessibilityPaneTitle
		 * and announceForAccessibility), so nothing can ever set that
		 * flag and false is the state, not dropped state. GTK's own
		 * "accessible" focus is not Android's accessibility focus —
		 * answering it here would report keyboard focus to a caller
		 * asking about a screen reader. */
		return false;
	}

	/* queue item 222: see setCameraDistance above (no RenderNode, no 3-D
	 * compositing in ATL). */
	public float getCameraDistance() { return 0; }

	public OnFocusChangeListener getOnFocusChangeListener() {
		return on_focus_change_listener;
	}

	public int getTextDirection() {
		return atl_text_direction;
	}

	public TouchDelegate getTouchDelegate() {
		/* queue item 245: reads back what setTouchDelegate stored. */
		return atl_touch_delegate;
	}

	public void setZ(float z) {
		/* AOSP: z is the sum of elevation and translationZ, so setting it
		 * moves translationZ (queue item 182's pair). The fork's setter was
		 * empty, so setZ() had no effect while getZ() reported elevation. */
		setTranslationZ(z - getElevation());
	}

	/* Copyright (C) 2006 The Android Open Source Project */
	protected void drawableStateChanged() {
		final int[] state = getDrawableState();
		boolean changed = false;

		final Drawable bg = background;
		if (bg != null && bg.isStateful()) {
			changed |= bg.setState(state);
		}

		if (changed) {
			invalidate();
		}
	}
}
