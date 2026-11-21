package android.widget;

import android.content.Context;
import android.util.AttributeSet;

public class RadioButton extends CompoundButton {

	public RadioButton(Context context) {
		super(context);
	}

	public RadioButton(Context context, AttributeSet attributeSet) {
		super(context, attributeSet);
	}

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
		native_setRadioText(label.toString());
	}

	private native void native_setRadioText(String text);

	// following methods are overridden to prevent calling incompatible methods from superclasses
	@Override
	public void setTextColor(int color) {}
	@Override
	public void setTextSize(float size) {}
}
