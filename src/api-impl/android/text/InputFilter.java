package android.text;

public interface InputFilter {

	public CharSequence filter(CharSequence source, int start, int end, Spanned dest, int dstart, int dend);

	/* AOSP LengthFilter: return null to accept the input unchanged, a
	 * replacement to truncate it, "" when nothing fits. The old stub
	 * returned "" always, i.e. it deleted every input, and ignored max. */
	public static class LengthFilter extends Object implements InputFilter {
		private final int mMax;
		private final int mStart;
		private final int mEnd;

		public LengthFilter(int max) {
			this(max, 0, max);
		}

		public LengthFilter(int max, int start, int end) {
			if (max < 0)
				throw new UnsupportedOperationException(
						"Filter max value must be larger than 0");
			if (start < 0 || end < start)
				throw new UnsupportedOperationException(
						"invalid range " + start + " - " + end);
			mMax = max;
			mStart = start;
			mEnd = end;
		}

		public CharSequence filter(CharSequence source, int start, int end, Spanned dest, int dstart, int dend) {
			int keep = mMax - (dest.length() - (dend - dstart));
			if (keep <= 0) {
				return "";
			} else if (keep >= end - start) {
				return null; // keep original
			} else {
				keep += start;
				if (Character.isHighSurrogate(source.charAt(keep - 1))) {
					--keep;
					if (keep == start) {
						return "";
					}
				}
				return source.subSequence(start, keep);
			}
		}

		/**
		 * @return the maximum length enforced by this input filter
		 */
		public int getMax() {
			return mMax;
		}
	}
}
