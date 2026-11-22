package android.widget;

import android.annotation.UnsupportedAppUsage;
import android.atl.annotation.Export;
import android.content.Context;
import android.content.res.ColorStateList;
import android.content.res.TypedArray;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.text.BoringLayout;
import android.text.Editable;
import android.text.InputFilter;
import android.text.Layout;
import android.text.SpannableStringBuilder;
import android.text.TextDirectionHeuristic;
import android.text.TextDirectionHeuristics;
import android.text.TextPaint;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.text.method.BaseMovementMethod;
import android.text.method.KeyListener;
import android.text.method.MovementMethod;
import android.text.method.TransformationMethod;
import android.text.style.URLSpan;
import android.util.AttributeSet;
import android.util.TypedValue;
import android.view.ActionMode;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;

public class TextView extends View {
	private ColorStateList colors = new ColorStateList(new int[][] {new int[0]}, new int[1]);
	private int curTextColor;
	private CharSequence text = "";
	private CharSequence hint = null;
	private int inputType = android.text.InputType.TYPE_NULL;
	private int imeOptions = 0; // EditorInfo.IME_NULL
	private TransformationMethod transformationMethod;
	private TextPaint paint = new TextPaint();
	private boolean include_padding = false;
	private int break_strategy = 0 /*BREAK_STRATEGY_SIMPLE*/;
	private int hyphenation_frequency = 0 /*HYPHENATION_FREQUENCY_NONE*/;
	private int gravity = Gravity.TOP | Gravity.START;

	public TextView(Context context, AttributeSet attrs) {
		this(context, attrs, 0);
	}

	public TextView(Context context) {
		this(context, null);
	}

	public TextView(Context context, AttributeSet attrs, int defStyleAttr) {
		this(context, attrs, defStyleAttr, 0);
	}

	public TextView(Context context, AttributeSet attrs, int defStyleAttr, int defStyleRes) {
		super(context, attrs, defStyleAttr, defStyleRes);

		TypedArray a = context.obtainStyledAttributes(attrs, com.android.internal.R.styleable.TextView, defStyleAttr, 0);
		try {
			if (a.hasValue(com.android.internal.R.styleable.TextView_text)) {
				setText(a.getText(com.android.internal.R.styleable.TextView_text));
			}
			if (a.hasValue(com.android.internal.R.styleable.TextView_hint)) {
				setHint(a.getText(com.android.internal.R.styleable.TextView_hint));
			}

			int ap = a.getResourceId(com.android.internal.R.styleable.TextView_textAppearance, -1);
			if (ap != -1) {
				TypedArray aa = context.obtainStyledAttributes(ap, com.android.internal.R.styleable.TextAppearance);
				if (aa.hasValue(com.android.internal.R.styleable.TextAppearance_textColor)) {
					setTextColor(aa.getColorStateList(com.android.internal.R.styleable.TextAppearance_textColor));
				}
				if (aa.hasValue(com.android.internal.R.styleable.TextAppearance_textSize)) {
					setTextSize(aa.getDimensionPixelSize(com.android.internal.R.styleable.TextAppearance_textSize, 10));
				}
				aa.recycle();
			}
			if (a.hasValue(com.android.internal.R.styleable.TextView_textColor)) {
				setTextColor(a.getColorStateList(com.android.internal.R.styleable.TextView_textColor));
			}
			if (a.hasValue(com.android.internal.R.styleable.TextView_imeOptions)) {
				setImeOptions(a.getInt(com.android.internal.R.styleable.TextView_imeOptions, 0));
			}
			if (a.hasValue(com.android.internal.R.styleable.TextView_inputType)) {
				setInputType(a.getInt(com.android.internal.R.styleable.TextView_inputType, android.text.InputType.TYPE_NULL));
			}
			if (a.getBoolean(com.android.internal.R.styleable.TextView_password, false)) {
				setTransformationMethod(android.text.method.PasswordTransformationMethod.getInstance());
			}
			if (a.hasValue(com.android.internal.R.styleable.TextView_gravity)) {
				setGravity(a.getInt(com.android.internal.R.styleable.TextView_gravity, gravity));
			}
			if (a.hasValue(com.android.internal.R.styleable.TextView_textSize)) {
				setTextSize(a.getDimensionPixelSize(com.android.internal.R.styleable.TextView_textSize, 10));
			}

			if (a.hasValue(com.android.internal.R.styleable.TextView_textStyle)) {
				int textStyle = a.getInt(com.android.internal.R.styleable.TextView_textStyle, 0);
				setTypeface(getTypeface(), textStyle);
			}

			if (a.hasValue(com.android.internal.R.styleable.TextView_textAllCaps)) {
				boolean allCaps = a.getBoolean(com.android.internal.R.styleable.TextView_textAllCaps, false);
				setAllCaps(allCaps);
			}

			/* AOSP: drawableLeft falls back to drawableStart, drawableRight to
			 * drawableEnd (LTR only, like our setCompoundDrawablesRelative) */
			Drawable dl = a.getDrawable(com.android.internal.R.styleable.TextView_drawableLeft);
			if (dl == null) {
				dl = a.getDrawable(com.android.internal.R.styleable.TextView_drawableStart);
			}
			Drawable dt = a.getDrawable(com.android.internal.R.styleable.TextView_drawableTop);
			Drawable dr = a.getDrawable(com.android.internal.R.styleable.TextView_drawableRight);
			if (dr == null) {
				dr = a.getDrawable(com.android.internal.R.styleable.TextView_drawableEnd);
			}
			Drawable db = a.getDrawable(com.android.internal.R.styleable.TextView_drawableBottom);
			if (dl != null || dt != null || dr != null || db != null) {
				setCompoundDrawablesWithIntrinsicBounds(dl, dt, dr, db);
			}
		} catch (java.lang.Exception e) {
			System.out.println("exception while inflating TextView:");
			e.printStackTrace();
		}

		a.recycle();
		haveCustomMeasure = false;
	}

	@Override
	protected native long native_constructor(Context context, AttributeSet attrs);

	public void setText(int resId) {
		setText(getContext().getResources().getText(resId));
	}

	public void setText(CharSequence text) {
		setText(text, BufferType.NORMAL);
	}

	public void setText(CharSequence text, BufferType type) {
		this.text = text == null ? "" : text;
		if (hint != null && this.text.length() == 0)
			native_setText(hint.toString());
		else
			native_setText(text != null ? displayed(text).toString() : null);

		if (text instanceof android.text.Spanned)
			native_set_markup(1);
		if (!isLayoutRequested())
			requestLayout();
	}

	public void setText(char[] text, int start, int len) {
		setText(new String(text, start, len));
	}

	public final void append(CharSequence text) {
		append(text, 0, text.length());
	}

	public void append(CharSequence text, int start, int end) {
		setText(SpannableStringBuilder.valueOf(this.text).append(text, start, end));
	}

	private native final void native_set_markup(int bool);

	public native final void native_setText(String text);

	public void setTextSize(int unit, float size) {
		if (unit != TypedValue.COMPLEX_UNIT_SP)
			System.out.println("setTextSize called with non-SP unit (" + unit + "), we don't currently handle that");
		setTextSize(size);
	}
	public native void setTextSize(float size);

	public native final void native_setTextColor(int color);
	public void setTextColor(int color) {
		colors = ColorStateList.valueOf(color);
		updateTextColors();
	}
	public void setTextColor(ColorStateList colors) {
		if (colors != null) {
			this.colors = colors;
			updateTextColors();
		}
	}

	/* Copyright (C) 2006 The Android Open Source Project */
	private void updateTextColors() {
		final int[] drawableState = getDrawableState();
		int color = colors.getColorForState(drawableState, 0);
		if (color != curTextColor) {
			curTextColor = color;
			native_setTextColor(color);
		}
	}

	@Override
	/* Copyright (C) 2006 The Android Open Source Project */
	protected void drawableStateChanged() {
		super.drawableStateChanged();

		if (colors != null && colors.isStateful()) {
			updateTextColors();
		}

		final int[] state = getDrawableState();
		for (Drawable dr : new Drawable[] {drawableLeft, drawableTop, drawableRight, drawableBottom}) {
			if (dr != null && dr.isStateful() && dr.setState(state)) {
				invalidateDrawable(dr);
			}
		}
	}
	private Typeface typeface = Typeface.DEFAULT;

	/* AOSP: style > 0 derives that style from tf (or the default family) */
	public void setTypeface(Typeface tf, int style) {
		if (style > 0)
			tf = tf == null ? Typeface.defaultFromStyle(style) : Typeface.create(tf, style);
		setTypeface(tf);
	}

	/* family and font files aren't applied yet (docs/agent-tasks/text-and-fonts.md
	 * step 8); bold and italic are, through the widget's CSS classes */
	public void setTypeface(Typeface tf) {
		typeface = tf != null ? tf : Typeface.DEFAULT;
		String[] classesToRemove = {"ATL-font-bold", "ATL-font-italic"};
		native_removeClasses(widget, classesToRemove);
		if (typeface.isBold())
			native_addClass(widget, "ATL-font-bold");
		if (typeface.isItalic())
			native_addClass(widget, "ATL-font-italic");
	}
	public void setLineSpacing(float add, float mult) {}
	public final void setLinksClickable(boolean whether) {}

	/* the text as shown: through the transformation method (e.g. password dots) */
	private CharSequence displayed(CharSequence text) {
		if (transformationMethod == null)
			return text;
		CharSequence shown = transformationMethod.getTransformation(text, this);
		return shown != null ? shown : text;
	}

	private static boolean isPasswordInputType(int type) {
		final int variation = type & (android.text.InputType.TYPE_MASK_CLASS | android.text.InputType.TYPE_MASK_VARIATION);
		return variation == (android.text.InputType.TYPE_CLASS_TEXT | android.text.InputType.TYPE_TEXT_VARIATION_PASSWORD)
		    || variation == (android.text.InputType.TYPE_CLASS_TEXT | android.text.InputType.TYPE_TEXT_VARIATION_WEB_PASSWORD)
		    || variation == (android.text.InputType.TYPE_CLASS_NUMBER | android.text.InputType.TYPE_NUMBER_VARIATION_PASSWORD);
	}

	private static boolean isVisiblePasswordInputType(int type) {
		final int variation = type & (android.text.InputType.TYPE_MASK_CLASS | android.text.InputType.TYPE_MASK_VARIATION);
		return variation == (android.text.InputType.TYPE_CLASS_TEXT | android.text.InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD);
	}

	/**
	 * Like AOSP: a password variation switches the transformation to
	 * PasswordTransformationMethod; leaving it (or a visible-password variation)
	 * removes a password transformation again ("show password" toggles).
	 */
	public void setInputType(int type) {
		final boolean wasPassword = isPasswordInputType(inputType);
		inputType = type;
		if (isPasswordInputType(type)) {
			setTransformationMethod(android.text.method.PasswordTransformationMethod.getInstance());
		} else if ((wasPassword || isVisiblePasswordInputType(type))
		           && transformationMethod instanceof android.text.method.PasswordTransformationMethod) {
			setTransformationMethod(null);
		} else {
			onInputTypeChanged();
		}
	}

	/** hook for subclasses with their own native widget (EditText) */
	protected void onInputTypeChanged() {}

	/** hook for subclasses with their own native widget (EditText) */
	protected void onTransformationMethodChanged() {
		native_setText(displayed(text).toString());
	}

	protected boolean isPasswordMasked() {
		return transformationMethod instanceof android.text.method.PasswordTransformationMethod;
	}
	public void setFilters(InputFilter[] filters) {}
	public void setCursorVisible(boolean visible) {}
	public void setImeOptions(int imeOptions) {
		this.imeOptions = imeOptions;
		onImeOptionsChanged();
	}

	public int getImeOptions() {
		return imeOptions;
	}

	/** hook for subclasses with their own native widget (EditText) */
	protected void onImeOptionsChanged() {}

	public final ColorStateList getTextColors() { return colors; }
	public static ColorStateList getTextColors(Context context, TypedArray attrs) { return new ColorStateList(new int[][] {new int[0]}, new int[1]); }

	public TextPaint getPaint() {
		return paint;
	}

	public void addTextChangedListener(TextWatcher watcher) {}
	public void removeTextChangedListener(TextWatcher watcher) {}
	public void setOnEditorActionListener(TextView.OnEditorActionListener l) {}

	public TransformationMethod getTransformationMethod() {
		return transformationMethod;
	}

	public void setHintTextColor(ColorStateList colorStateList) {}
	public void setHintTextColor(int i) {}
	public void setLinkTextColor(ColorStateList colorStateList) {}

	public void setSingleLine() {
		singleLine = true;
	}

	/* AOSP single-line state: me.zhanghai.android.files probes
	 * isSingleLine[] reflectively from its RecyclerView layout path, so
	 * the missing method showed up as a swallowed NoSuchMethodException
	 * in every run. State is stored and read back; ATL's line wrapping
	 * has no rendering consequence from this flag yet. */
	private boolean singleLine = false;

	public boolean isSingleLine() {
		return singleLine;
	}

	public void setSelection(int i) {}
	public void setSelection(int i, int j) {}

	public int getSelectionStart() {
		return 0;
	}

	public int getSelectionEnd() {
		return 0;
	}

	public void setEllipsize(TextUtils.TruncateAt truncateAt) {}

	public void setTextAppearance(Context context, int appearance) {}

	public void setMaxLines(int maxLines) {}

	public void setMinWidth(int minWidth) {}
	public void setMaxWidth(int maxWidth) {}

	public Typeface getTypeface() { return typeface; }

	public float getTextSize() { return 10; }

	/**
	 * Like Android: a missing horizontal part means START, a missing vertical
	 * part TOP. Positions the text inside the view (label alignment natively).
	 */
	public void setGravity(int gravity) {
		if ((gravity & Gravity.RELATIVE_HORIZONTAL_GRAVITY_MASK) == 0)
			gravity |= Gravity.START;
		if ((gravity & Gravity.VERTICAL_GRAVITY_MASK) == 0)
			gravity |= Gravity.TOP;
		this.gravity = gravity;
		float xalign;
		switch (Gravity.getAbsoluteGravity(gravity, getLayoutDirection()) & Gravity.HORIZONTAL_GRAVITY_MASK) {
			case Gravity.CENTER_HORIZONTAL:
				xalign = 0.5f;
				break;
			case Gravity.RIGHT:
				xalign = 1.0f;
				break;
			default:
				xalign = 0.0f;
		}
		float yalign;
		switch (gravity & Gravity.VERTICAL_GRAVITY_MASK) {
			case Gravity.CENTER_VERTICAL:
				yalign = 0.5f;
				break;
			case Gravity.BOTTOM:
				yalign = 1.0f;
				break;
			default:
				yalign = 0.0f;
		}
		native_setAlignment(xalign, yalign);
	}

	/* positions the label of the plain TextView widget; subclasses with their own
	 * native widget (Button, EditText, ...) are left unchanged by the native side */
	private native void native_setAlignment(float xalign, float yalign);

	public int getGravity() {
		return gravity;
	}

	public int getCompoundPaddingTop() { return 0; }
	public int getCompoundPaddingBottom() { return 0; }

	public CharSequence getText() {
		return text;
	};

	public void setCompoundDrawablePadding(int pad) {}

	protected native void native_setCompoundDrawables(long widget, long left, long top, long right, long bottom);

	// just to prevent garbage collection while native side uses it
	private Drawable drawableLeft = null;
	private Drawable drawableTop = null;
	private Drawable drawableRight = null;
	private Drawable drawableBottom = null;

	public void setCompoundDrawables(Drawable left, Drawable top, Drawable right, Drawable bottom) {
		native_setCompoundDrawables(widget,
		                            left != null ? left.paintable : 0,
		                            top != null ? top.paintable : 0,
		                            right != null ? right.paintable : 0,
		                            bottom != null ? bottom.paintable : 0);
		drawableLeft = left;
		drawableTop = top;
		drawableRight = right;
		drawableBottom = bottom;
	}

	public void setAllCaps(boolean allCaps) {
		String[] classesToRemove = {"ATL-text-uppercase"};
		native_removeClasses(widget, classesToRemove);

		if (allCaps) {
			native_addClass(widget, "ATL-text-uppercase");
		}
	}

	public void setSaveEnabled(boolean enabled) {}

	public final void setAutoLinkMask(int mask) {}

	public void setEditableFactory(Editable.Factory factory) {}

	public KeyListener getKeyListener() { return null; }

	public int getInputType() { return inputType; }

	public final void setTransformationMethod(TransformationMethod method) {
		if (method == transformationMethod)
			return;
		transformationMethod = method;
		onTransformationMethodChanged();
		onInputTypeChanged();
	}

	public InputFilter[] getFilters() { return new InputFilter[0]; }

	public int getMaxLines() { return -1; }

	public void setCompoundDrawablesRelative(Drawable start, Drawable top, Drawable end, Drawable bottom) {
		setCompoundDrawables(start, top, end, bottom);
	}

	public int getLineCount() { return 1; }

	public URLSpan[] getUrls() { return new URLSpan[0]; }

	public void setMovementMethod(MovementMethod method) {}

	public void setTextIsSelectable(boolean selectable) {}

	public MovementMethod getMovementMethod() {
		return new BaseMovementMethod();
	}

	public CharSequence getHint() { return hint; }

	public int getMinHeight() { return 0; }
	public int getMinWidth() { return 0; }
	public void setMinHeight(int minHeight) {}
	public void setMaxHeight(int maxHeight) {}

	public void setHorizontallyScrolling(boolean whether) {}
	public boolean getHorizontallyScrolling() {
		return false;
	}

	public static interface OnEditorActionListener {
		@Export
		public abstract boolean onEditorAction(TextView v, int actionId, KeyEvent event);
	}

	public static enum BufferType {
		EDITABLE,
		NORMAL,
		SPANNABLE,
	}

	public Layout getLayout() {
		return new BoringLayout(getText(), getPaint(), getWidth(), Layout.Alignment.ALIGN_NORMAL, 1, 0, new BoringLayout.Metrics(), false);
	}

	public int getCurrentTextColor() {
		return curTextColor;
	}

	public void setSingleLine(boolean singleLine) {
		this.singleLine = singleLine;
	}

	public int getCompoundPaddingLeft() { return 0; }

	public int getCompoundPaddingRight() { return 0; }

	public void setHint(int resId) {
		setHint(getContext().getResources().getText(resId));
	}

	public float getLetterSpacing() { return 0.f; }

	public void setCompoundDrawablesRelativeWithIntrinsicBounds(int start, int top, int end, int bottom) {
		/* LTR only, same as setCompoundDrawablesRelative */
		setCompoundDrawablesWithIntrinsicBounds(start, top, end, bottom);
	}

	public void setCompoundDrawablesRelativeWithIntrinsicBounds(Drawable start, Drawable top, Drawable end, Drawable bottom) {
		/* LTR only, same as setCompoundDrawablesRelative */
		setCompoundDrawablesWithIntrinsicBounds(start, top, end, bottom);
	}

	public boolean getLinksClickable() { return true; }

	public boolean isTextSelectable() { return true; }

	public void setCompoundDrawablesWithIntrinsicBounds(int left, int top, int right, int bottom) {
		final Context c = getContext();
		setCompoundDrawablesWithIntrinsicBounds(left != 0 ? c.getDrawable(left) : null,
		                                       top != 0 ? c.getDrawable(top) : null,
		                                       right != 0 ? c.getDrawable(right) : null,
		                                       bottom != 0 ? c.getDrawable(bottom) : null);
	}

	/* Copyright (C) 2006 The Android Open Source Project */
	public void setCompoundDrawablesWithIntrinsicBounds(Drawable left, Drawable top, Drawable right, Drawable bottom) {
		if (left != null) {
			left.setBounds(0, 0, left.getIntrinsicWidth(), left.getIntrinsicHeight());
		}
		if (top != null) {
			top.setBounds(0, 0, top.getIntrinsicWidth(), top.getIntrinsicHeight());
		}
		if (right != null) {
			right.setBounds(0, 0, right.getIntrinsicWidth(), right.getIntrinsicHeight());
		}
		if (bottom != null) {
			bottom.setBounds(0, 0, bottom.getIntrinsicWidth(), bottom.getIntrinsicHeight());
		}
		setCompoundDrawables(left, top, right, bottom);
	}

	public void setHint(CharSequence s) {
		/* AOSP paints the hint (in hintColors) whenever the text is empty.
		 * ATL has no hint styling, but painting it as ordinary text still
		 * beats showing nothing; styling is a separate gap. */
		this.hint = s;
		if (this.text.length() == 0)
			native_setText(s != null ? s.toString() : null);
	}

	public Drawable[] getCompoundDrawablesRelative() {
		/* LTR only, same as setCompoundDrawablesRelative */
		return getCompoundDrawables();
	}

	public Drawable[] getCompoundDrawables() {
		return new Drawable[] {drawableLeft, drawableTop, drawableRight, drawableBottom};
	}

	public void setTextAppearance(int dummy) {}

	public int length() {
		return getText().length();
	}

	public void setHighlightColor(int color) {}

	public Editable getEditableText() {
		return new SpannableStringBuilder(getText());
	}

	public int getMaxWidth() { return 1000; }

	public void nullLayouts() {}

	public void setLinkTextColor(int color) {}

	public void setCustomSelectionActionModeCallback(ActionMode.Callback actionModeCallback) {}

	public int getExtendedPaddingTop() { return 0; }

	public void setRawInputType(int type) {
		inputType = type;
		onInputTypeChanged();
	}

	public TextUtils.TruncateAt getEllipsize() { return null; }

	public void setLines(int lines) {}

	public int getMinLines() {
		return -1;
	}

	public void setMinLines(int lines) {}

	public void setSelectAllOnFocus(boolean selectAllOnFocus) {}

	public int getCompoundDrawablePadding() { return 0; }

	public int getPaintFlags() { return 0; }

	public void setPaintFlags(int flags) {}

	public int getLineHeight() {
		return 10; // FIXME
	}

	public int getMaxHeight() {
		return -1;
	}

	public boolean isAllCaps() { return false; }

	public int getAutoSizeStepGranularity() {
		return -1;
	}

	public void setAutoSizeTextTypeUniformWithPresetSizes(int[] presetSizes, int unit) {}

	public void setCompoundDrawableTintList(ColorStateList tint) {}

	public int getHyphenationFrequency() {
		return hyphenation_frequency;
	}

	public void setHyphenationFrequency(int hyphenationFrequency) {
		hyphenation_frequency = hyphenationFrequency;
	}

	public boolean getIncludeFontPadding() { return include_padding; }

	public void setIncludeFontPadding(boolean includePadding) {
		include_padding = includePadding;
	}

	public float getLineSpacingExtra() { return 0.f; }

	public float getLineSpacingMultiplier() { return 1.f; }

	public TextDirectionHeuristic getTextDirectionHeuristic() {
		return TextDirectionHeuristics.LTR;
	}

	public Bundle getInputExtras(boolean key) {
		return new Bundle();
	}

	public void setError(CharSequence error) {
		System.out.println("ERROR: " + error);
	}

	public int getTotalPaddingLeft() { return 0; }

	public int getTotalPaddingTop() { return 0; }

	public int getTotalPaddingRight() { return 0; }

	public int getTotalPaddingBottom() { return 0; }

	public void setShadowLayer(float radius, float dx, float dy, int color) {}

	public int getBreakStrategy() {
		return break_strategy;
	}

	public void setBreakStrategy(int strategy) {
		break_strategy = strategy;
	}

	public void clearComposingText() {}

	public void setKeyListener(KeyListener keyListener) {}

	public int getAutoLinkMask() { return 0; }

	public void setWidth(int width) {}
	public void setHeight(int height) {}

	public void setFreezesText(boolean freezesText) {}

	public void setLetterSpacing(float letterSpacing) {}

	public void setMarqueeRepeatLimit(int marqueeLimit) {}

	public void setShowSoftInputOnFocus(boolean showSoftInputOnFocus) {}

	public void setCustomInsertionActionModeCallback(ActionMode.Callback actionModeCallback) {}

	public int getJustificationMode() {
		return 0; // JUSTIFICATION_MODE_NONE
	}

	public void setImeActionLabel(CharSequence label, int actionId) {}

	public int getExtendedPaddingBottom() { return 0; }

	public boolean hasSelection() { return false; }

	@UnsupportedAppUsage /* androidx ACTVAutoSizeHelper seems to love this */
	/* Copyright (C) 2006 The Android Open Source Project */
	private Layout.Alignment getLayoutAlignment() {
		Layout.Alignment alignment;
		switch (getTextAlignment()) {
			case TEXT_ALIGNMENT_GRAVITY:
				switch (gravity & Gravity.RELATIVE_HORIZONTAL_GRAVITY_MASK) {
					case Gravity.START:
						alignment = Layout.Alignment.ALIGN_NORMAL;
						break;
					case Gravity.END:
						alignment = Layout.Alignment.ALIGN_OPPOSITE;
						break;
					case Gravity.LEFT:
						alignment = Layout.Alignment.ALIGN_LEFT;
						break;
					case Gravity.RIGHT:
						alignment = Layout.Alignment.ALIGN_RIGHT;
						break;
					case Gravity.CENTER_HORIZONTAL:
						alignment = Layout.Alignment.ALIGN_CENTER;
						break;
					default:
						alignment = Layout.Alignment.ALIGN_NORMAL;
						break;
				}
				break;
			case TEXT_ALIGNMENT_TEXT_START:
				alignment = Layout.Alignment.ALIGN_NORMAL;
				break;
			case TEXT_ALIGNMENT_TEXT_END:
				alignment = Layout.Alignment.ALIGN_OPPOSITE;
				break;
			case TEXT_ALIGNMENT_CENTER:
				alignment = Layout.Alignment.ALIGN_CENTER;
				break;
			case TEXT_ALIGNMENT_VIEW_START:
				alignment = (getLayoutDirection() == LAYOUT_DIRECTION_RTL) ? Layout.Alignment.ALIGN_RIGHT : Layout.Alignment.ALIGN_LEFT;
				break;
			case TEXT_ALIGNMENT_VIEW_END:
				alignment = (getLayoutDirection() == LAYOUT_DIRECTION_RTL) ? Layout.Alignment.ALIGN_LEFT : Layout.Alignment.ALIGN_RIGHT;
				break;
			case TEXT_ALIGNMENT_INHERIT:
				// This should never happen as we have already resolved the text alignment
				// but better safe than sorry so we just fall through
			default:
				alignment = Layout.Alignment.ALIGN_NORMAL;
				break;
		}
		return alignment;
	}
}
