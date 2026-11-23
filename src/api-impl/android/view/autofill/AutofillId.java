package android.view.autofill;

public class AutofillId {
	/* AOSP AutofillId wraps a unique int (View.generateAutofillId) and
	 * is compared by value (queue item 151: the class was empty, so
	 * View.getAutofillId's fresh-object answer could never satisfy
	 * equality even if it had been stable). */
	private final int value;

	public AutofillId() {
		this(0);
	}

	public AutofillId(int value) {
		this.value = value;
	}

	public final int getValue() {
		return value;
	}

	@Override
	public final boolean equals(Object obj) {
		if (this == obj)
			return true;
		if (!(obj instanceof AutofillId))
			return false;
		return ((AutofillId) obj).value == value;
	}

	@Override
	public final int hashCode() {
		return value;
	}
}
