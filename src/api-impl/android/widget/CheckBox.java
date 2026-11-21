package android.widget;

import android.content.Context;
import android.util.AttributeSet;

public class CheckBox extends CompoundButton {

	public CheckBox(Context context) {
		super(context);
	}

	public CheckBox(Context context, AttributeSet attributeSet) {
		super(context, attributeSet);
	}

	public void setLines(int lines) {}

	@Override
	protected native long native_constructor(Context context, AttributeSet attrs);

	@Override
	public native void setOnCheckedChangeListener(OnCheckedChangeListener listener);

	@Override
	protected native void native_setChecked(boolean checked);

	@Override
	public native boolean isChecked();

	@Override
	public void setText(CharSequence text) {
		label = text == null ? "" : text;
		native_setText(widget, label.toString());
	}

	// following methods are overridden to prevent calling incompatible methods from superclasses
	@Override
	public void setTextColor(int color) {}
	@Override
	public void setTextSize(float size) {}

	@Override
	public native void native_setText(long widget, String text);
}
