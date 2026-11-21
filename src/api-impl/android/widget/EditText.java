package android.widget;

import android.content.Context;
import android.graphics.drawable.Drawable;
import android.text.Editable;
import android.text.SpannableStringBuilder;
import android.text.TextWatcher;
import android.util.AttributeSet;
import android.view.MotionEvent;

public class EditText extends TextView {
	public EditText(Context context) {
		super(context);
	}

	public EditText(Context context, AttributeSet attrs) {
		super(context, attrs);
		defaultInputType();
	}

	/* AOSP: an editable TextView without android:inputType gets TYPE_CLASS_TEXT */
	private void defaultInputType() {
		if (getInputType() == android.text.InputType.TYPE_NULL)
			setRawInputType(android.text.InputType.TYPE_CLASS_TEXT);
	}

	/* Masking happens in the GtkText itself (it keeps the real text); the input
	 * purpose tells the on-screen keyboard not to learn or suggest passwords. */
	@Override
	protected void onTransformationMethodChanged() {
		native_setPasswordMode(widget, isPasswordMasked(),
		                       (getInputType() & android.text.InputType.TYPE_MASK_CLASS) == android.text.InputType.TYPE_CLASS_NUMBER);
	}

	@Override
	protected void onInputTypeChanged() {
		native_setPasswordMode(widget, isPasswordMasked(),
		                       (getInputType() & android.text.InputType.TYPE_MASK_CLASS) == android.text.InputType.TYPE_CLASS_NUMBER);
	}

	protected native void native_setPasswordMode(long widget, boolean masked, boolean numeric);

	public EditText(Context context, AttributeSet attrs, int defStyle, int defStyleRes) {
		super(context, attrs, defStyle, defStyleRes);
		defaultInputType();
	}

	@Override
	protected native long native_constructor(Context context, AttributeSet attrs);
	protected native String native_getText(long widget);
	protected native void native_addTextChangedListener(long widget, TextWatcher watcher);
	protected native void native_removeTextChangedListener(long widget, TextWatcher watcher);
	protected native void native_setOnEditorActionListener(long widget, OnEditorActionListener l);
	protected native void native_setText(long widget, String text);
	protected native void native_setHint(long widget, CharSequence s);
	protected native CharSequence native_getHint(long widget); // gtk_entry_set_placeholder_text

	@Override
	public boolean onTouchEvent(MotionEvent event) {
		return native_gtkEventPropagation();
	}

	/**
	 * The Editable apps get from getText(): one instance per EditText, synced from
	 * the GtkText on every getText() call, and writing every change (insert,
	 * delete, append, clear, replace all go through replace()) back to it, which
	 * also fires TextWatchers as on Android. A reference held across user typing
	 * is refreshed on the next getText().
	 */
	private final class LiveEditable extends SpannableStringBuilder {
		private boolean syncing;

		@Override
		public SpannableStringBuilder replace(final int start, final int end, CharSequence tb, int tbstart, int tbend) {
			super.replace(start, end, tb, tbstart, tbend);
			if (!syncing) {
				// only when it differs: GTK reports a "change" even for identical
				// text, which would loop with TextWatchers that rewrite the text
				String text = toString();
				if (!text.equals(native_getText(widget)))
					native_setText(widget, text);
			}
			return this;
		}

		void syncFrom(String text) {
			if (toString().equals(text))
				return;
			syncing = true;
			try {
				super.replace(0, length(), text, 0, text.length());
			} finally {
				syncing = false;
			}
		}
	}

	private LiveEditable editable;

	/* Both getters use this, never each other: apps override getText() and call
	 * getEditableText() from it (Fossify's MyEditText), so delegating between
	 * the two public methods recursed forever. As in AOSP, neither calls the
	 * other. */
	private Editable liveText() {
		if (editable == null)
			editable = new LiveEditable();
		String current = native_getText(widget);
		editable.syncFrom(current == null ? "" : current);
		return editable;
	}

	public Editable getText() {
		return liveText();
	}

	public Editable getEditableText() {
		return liveText();
	}

	/* Enter: onEditorAction gets this IME action as actionId (IME_NULL if none) */
	@Override
	protected void onImeOptionsChanged() {
		native_setImeAction(widget, getImeOptions() & 0xff /* EditorInfo.IME_MASK_ACTION */);
	}

	protected native void native_setImeAction(long widget, int action);

	@Override
	public void setText(CharSequence text) {
		native_setText(widget, text == null ? "" : text.toString());
	}
	@Override
	public void setTextSize(float size) {}

	@Override
	public void removeTextChangedListener(TextWatcher watcher) {
		native_removeTextChangedListener(widget, watcher);
	}

	@Override
	public void addTextChangedListener(TextWatcher watcher) {
		native_addTextChangedListener(widget, watcher);
	}

	@Override
	public void setOnEditorActionListener(OnEditorActionListener l) {
		native_setOnEditorActionListener(widget, l);
	}

	@Override
	public void setCompoundDrawables(Drawable left, Drawable top, Drawable right, Drawable bottom) {}

	@Override
	public void setHint(CharSequence s) {
		native_setHint(widget, s == null ? "" : s.toString());
	}

	@Override
	public CharSequence getHint() {
		return native_getHint(widget);
	}

	public void selectAll() {}
}
