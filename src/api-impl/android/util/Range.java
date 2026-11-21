package android.util;

import java.util.Objects;

/* AOSP android.util.Range: an immutable range of comparable values, inclusive
 * at both ends. */
public final class Range<T extends Comparable<? super T>> {
	private final T mLower;
	private final T mUpper;

	public Range(final T lower, final T upper) {
		if (lower == null)
			throw new NullPointerException("lower must not be null");
		if (upper == null)
			throw new NullPointerException("upper must not be null");
		if (lower.compareTo(upper) > 0)
			throw new IllegalArgumentException("lower must be less than or equal to upper");
		mLower = lower;
		mUpper = upper;
	}

	public static <T extends Comparable<? super T>> Range<T> create(final T lower, final T upper) {
		return new Range<T>(lower, upper);
	}

	public T getLower() {
		return mLower;
	}

	public T getUpper() {
		return mUpper;
	}

	public boolean contains(T value) {
		if (value == null)
			throw new NullPointerException("value must not be null");
		return value.compareTo(mLower) >= 0 && value.compareTo(mUpper) <= 0;
	}

	public boolean contains(Range<T> range) {
		if (range == null)
			throw new NullPointerException("value must not be null");
		return range.mLower.compareTo(mLower) >= 0 && range.mUpper.compareTo(mUpper) <= 0;
	}

	public T clamp(T value) {
		if (value == null)
			throw new NullPointerException("value must not be null");
		if (value.compareTo(mLower) < 0)
			return mLower;
		if (value.compareTo(mUpper) > 0)
			return mUpper;
		return value;
	}

	public Range<T> intersect(Range<T> range) {
		if (range == null)
			throw new NullPointerException("range must not be null");
		int cmpLower = range.mLower.compareTo(mLower);
		int cmpUpper = range.mUpper.compareTo(mUpper);
		if (cmpLower <= 0 && cmpUpper >= 0)
			return this;
		if (cmpLower >= 0 && cmpUpper <= 0)
			return range;
		return Range.create(cmpLower <= 0 ? mLower : range.mLower, cmpUpper >= 0 ? mUpper : range.mUpper);
	}

	public Range<T> intersect(T lower, T upper) {
		return intersect(Range.create(lower, upper));
	}

	public Range<T> extend(Range<T> range) {
		if (range == null)
			throw new NullPointerException("range must not be null");
		int cmpLower = range.mLower.compareTo(mLower);
		int cmpUpper = range.mUpper.compareTo(mUpper);
		if (cmpLower <= 0 && cmpUpper >= 0)
			return range;
		if (cmpLower >= 0 && cmpUpper <= 0)
			return this;
		return Range.create(cmpLower >= 0 ? mLower : range.mLower, cmpUpper <= 0 ? mUpper : range.mUpper);
	}

	public Range<T> extend(T lower, T upper) {
		return extend(Range.create(lower, upper));
	}

	public Range<T> extend(T value) {
		if (value == null)
			throw new NullPointerException("value must not be null");
		return extend(value, value);
	}

	@Override
	public boolean equals(Object obj) {
		if (obj == this)
			return true;
		if (!(obj instanceof Range))
			return false;
		Range<?> other = (Range<?>)obj;
		return mLower.equals(other.mLower) && mUpper.equals(other.mUpper);
	}

	@Override
	public int hashCode() {
		return Objects.hash(mLower, mUpper);
	}

	@Override
	public String toString() {
		return "[" + mLower + ", " + mUpper + "]";
	}
}
