/* SPDX-License-Identifier: AGPL-3.0-or-later */
/*
 * Ported from AOSP framework/media/java/android/media/Rating.java
 * (deprecated on Android but still shipped; androidx.media/media3 legacy
 * media-session IMediaSession signatures reference this class, so ART
 * throws ClassNotFoundException without it - witnessed with Gramophone:
 * bazzite run gpost1 and the phone desktop log, 2026-11-22).
 * AOSP keeps the concrete styles as package-private classes; they are
 * nested here so api-impl/meson.build needs no extra srcs entry.
 */
package android.media;

import android.annotation.NonNull;
import android.annotation.Nullable;
import android.os.Parcel;
import android.os.Parcelable;

public abstract class Rating implements Parcelable {

	/** Rating style: not rated (has no rating value). */
	public static final int NONE = 0;
	/** Rating style: unrated (can be rated, not rated yet). */
	public static final int UNRATED = 1;
	/** Rating style: not available. */
	public static final int NOT_RATED = 2;

	/** Mask for the rating type part of a rating style. */
	public static final int RATING_TYPE_MASK = 0x0000ffff;

	/** Mask for the rating style part. */
	public static final int TYPE_MASK = 0xffff0000;

	public static final int TYPE_HEARTS = 0x00010000;
	public static final int TYPE_3STARS = 0x00020000;
	public static final int TYPE_4STARS = 0x00030000;
	public static final int TYPE_5STARS = 0x00040000;
	public static final int TYPE_PERCENTAGE = 0x00050000;
	public static final int TYPE_CONTEXTUAL = 0x00060000;

	final int mRatingStyle;
	final float mRatingValue;

	Rating(int ratingStyle) {
		mRatingStyle = ratingStyle;
		mRatingValue = 0.0f;
	}

	Rating(int ratingStyle, float rating) {
		mRatingStyle = ratingStyle;
		mRatingValue = rating;
	}

	@Override
	public String toString() {
		switch (mRatingStyle) {
		case UNRATED:
			return "Unrated Rating";
		case NOT_RATED:
			return "Not Rated";
		case TYPE_CONTEXTUAL:
			return "Contextual Rating: \"" + getContextualRating() + "\"";
		case TYPE_HEARTS:
			return hasHeart() ? "Has a heart" : "No heart";
		case TYPE_PERCENTAGE:
			return "Rated " + ((int) getPercentage()) + "%";
		case TYPE_3STARS:
		case TYPE_4STARS:
		case TYPE_5STARS:
			return "Rated " + (int) getStarRating() + " out of "
					+ (mRatingStyle == TYPE_3STARS ? 3 : (mRatingStyle == TYPE_4STARS ? 4 : 5));
		default:
			return "Unknown Rating type " + mRatingStyle;
		}
	}

	@Override
	public boolean equals(@Nullable Object obj) {
		if (obj == this)
			return true;
		if (obj == null || getClass() != obj.getClass())
			return false;
		Rating other = (Rating) obj;
		if (Float.compare(other.mRatingValue, mRatingValue) != 0)
			return false;
		if (other.mRatingStyle != mRatingStyle)
			return false;
		return true;
	}

	@Override
	public int hashCode() {
		int result = mRatingStyle;
		result = 31 * result + (mRatingValue == 0.0f ? 0 : Float.floatToIntBits(mRatingValue));
		return result;
	}

	/** Unrated rating of the given style, or null for an invalid style. */
	@Nullable
	public static Rating newUnratedRating(int ratingStyle) {
		switch (ratingStyle) {
		case TYPE_HEARTS:
		case TYPE_3STARS:
		case TYPE_4STARS:
		case TYPE_5STARS:
		case TYPE_PERCENTAGE:
		case TYPE_CONTEXTUAL:
			return new UnratedRating(ratingStyle);
		default:
			return null;
		}
	}

	/** Rating between 1 and 3 stars, or null out of range. */
	@Nullable
	public static Rating new3StarRating(float starRating) {
		if (inRangeClamped(starRating, 1, 3))
			return new StarRating(TYPE_3STARS, starRating);
		return null;
	}

	/** Rating between 1 and 4 stars, or null out of range. */
	@Nullable
	public static Rating new4StarRating(float starRating) {
		if (inRangeClamped(starRating, 1, 4))
			return new StarRating(TYPE_4STARS, starRating);
		return null;
	}

	/** Rating between 1 and 5 stars, or null out of range. */
	@Nullable
	public static Rating new5StarRating(float starRating) {
		if (inRangeClamped(starRating, 1, 5))
			return new StarRating(TYPE_5STARS, starRating);
		return null;
	}

	/** Percentage rating between 0 and 100, or null out of range. */
	@Nullable
	public static Rating newPercentageRating(float percent) {
		if (inRangeClamped(percent, 0, 100))
			return new PercentageRating(percent);
		return null;
	}

	/** Heart rating between 0 (no heart) and 1 (has heart), or null out of range. */
	@Nullable
	public static Rating newHeartRating(float hasHeart) {
		if (inRangeClamped(hasHeart, 0, 1))
			return new HeartRating(hasHeart);
		return null;
	}

	/** Heart rating from a boolean (API 30 overload; androidx.media calls it). */
	@NonNull
	public static Rating newHeartRating(boolean hasHeart) {
		return new HeartRating(hasHeart ? 1.0f : 0.0f);
	}

	/**
	 * Contextual rating from a short string. AOSP keeps only the string's
	 * hash code as the rating value, so getContextualRating() returns the
	 * hash string, not the original text; this port keeps that quirk.
	 */
	@Nullable
	public static Rating newContextualRating(String contextualRating) {
		if (contextualRating != null && contextualRating.trim().length() > 0)
			return new ContextRating(contextualRating);
		return null;
	}

	private static boolean inRangeClamped(float value, float start, float end) {
		return value >= start && value <= end;
	}

	/** Whether this rating is rated (has a value). */
	public boolean isRated() {
		if (mRatingStyle == TYPE_HEARTS)
			return mRatingValue > 0.0f;
		else
			return mRatingValue >= 0.0f;
	}

	public int getRatingStyle() {
		return mRatingStyle;
	}

	/** Star value, or -1 if this is not a star rating. */
	public float getStarRating() {
		switch (mRatingStyle) {
		case TYPE_3STARS:
		case TYPE_4STARS:
		case TYPE_5STARS:
			return mRatingValue;
		default:
			return -1.0f;
		}
	}

	/** Percentage value, or -1 if this is not a percentage rating. */
	public float getPercentage() {
		if (mRatingStyle == TYPE_PERCENTAGE)
			return mRatingValue;
		return -1.0f;
	}

	/** Whether this heart rating has a heart. */
	public boolean hasHeart() {
		if (mRatingStyle == TYPE_HEARTS)
			return mRatingValue > 0.0f;
		return false;
	}

	/** Contextual text (AOSP: the hash string), or null. */
	@Nullable
	public String getContextualRating() {
		if (mRatingStyle == TYPE_CONTEXTUAL)
			return Integer.toString((int) mRatingValue);
		return null;
	}

	@Override
	public int describeContents() {
		return 0;
	}

	@Override
	public void writeToParcel(@NonNull Parcel out, int flags) {
		out.writeInt(mRatingStyle);
		out.writeFloat(mRatingValue);
	}

	/** Recreates the concrete style from the parcel written by writeToParcel. */
	public static final Parcelable.Creator<Rating> CREATOR = new Parcelable.Creator<Rating>() {
		@Override
		public Rating createFromParcel(Parcel in) {
			int style = in.readInt();
			float value = in.readFloat();
			switch (style) {
			case TYPE_HEARTS:
				return value < 0.0f ? new HeartRating(0.0f, true) : new HeartRating(value);
			case TYPE_PERCENTAGE:
				return value < 0.0f ? new PercentageRating() : new PercentageRating(value);
			case TYPE_3STARS:
			case TYPE_4STARS:
			case TYPE_5STARS:
				return value < 0.0f ? new StarRating(style) : new StarRating(style, value);
			case TYPE_CONTEXTUAL:
				return new ContextRating(style, value);
			default:
				return new UnratedRating(style);
			}
		}

		@Override
		public Rating[] newArray(int size) {
			return new Rating[size];
		}
	};

	static class UnratedRating extends Rating {
		UnratedRating(int ratingStyle) {
			super(ratingStyle, -1.0f);
		}
	}

	static class HeartRating extends Rating {
		HeartRating(float hasHeart) {
			super(TYPE_HEARTS, hasHeart);
		}

		HeartRating(float hasHeart, boolean unrated) {
			super(TYPE_HEARTS, -1.0f);
		}
	}

	static class PercentageRating extends Rating {
		PercentageRating() {
			super(TYPE_PERCENTAGE, -1.0f);
		}

		PercentageRating(float percentage) {
			super(TYPE_PERCENTAGE, percentage);
		}
	}

	static class StarRating extends Rating {
		StarRating(int ratingStyle) {
			super(ratingStyle, -1.0f);
		}

		StarRating(int ratingStyle, float starRating) {
			super(ratingStyle, starRating);
		}
	}

	static class ContextRating extends Rating {
		ContextRating(String contextualRating) {
			super(TYPE_CONTEXTUAL, contextualRating.hashCode());
		}

		ContextRating(int ratingStyle, float value) {
			super(ratingStyle, value);
		}
	}
}
