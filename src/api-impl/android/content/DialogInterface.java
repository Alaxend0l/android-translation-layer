package android.content;

import android.atl.annotation.Export;
import android.view.KeyEvent;

public interface DialogInterface {
	/** The identifier for the positive button. */
	int BUTTON_POSITIVE = -1;
	/** The identifier for the negative button. */
	int BUTTON_NEGATIVE = -2;
	/** The identifier for the neutral button. */
	int BUTTON_NEUTRAL = -3;

	@Export
	public void dismiss();

	public void cancel();

	public interface OnDismissListener {
		void onDismiss(DialogInterface dialog);
	}
	public interface OnClickListener {
		@Export
		void onClick(DialogInterface dialog, int which);
	}
	public interface OnShowListener {
		void onShow(DialogInterface dialog);
	}
	public interface OnCancelListener {
		/* The method apps implement (AOSP DialogInterface.OnCancelListener);
		 * it was missing here, so a listener could not be invoked (or even
		 * match the SDK signature). */
		void onCancel(DialogInterface dialog);
	}
	public interface OnMultiChoiceClickListener {
	}
	public interface OnKeyListener {
		/* The method apps implement (AOSP DialogInterface.OnKeyListener);
		 * it was missing, so a listener had nothing to implement and the
		 * dialog nothing to call. */
		boolean onKey(DialogInterface dialog, int keyCode, KeyEvent event);
	}
}
