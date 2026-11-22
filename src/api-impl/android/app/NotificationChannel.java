/*
 * Copyright (C) 2014 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package android.app;

import android.media.AudioAttributes;
import android.net.Uri;

/*
 * ATL version: stores the channel settings and answers the getters like
 * AOSP (the app-facing half). ATL has no notification backend that acts
 * on sound/lights/vibration, so those setters keep the value for the
 * getters but nothing plays or blinks - unlike the earlier version, an
 * app that creates a channel and reads it back now sees what it set.
 */
public class NotificationChannel {

	private final String mId;
	private final CharSequence mName;
	private final int mImportance;
	private String mDescription;
	private String mGroup;
	private Uri mSound;
	private AudioAttributes mAudioAttributes;
	private boolean mShowBadge = true; /* AOSP default */
	private boolean mBypassDnd = false;
	private int mLockscreenVisibility = -1; /* Notification.VISIBILITY_PRIVATE */
	private boolean mLights = false;
	private int mLightColor = 0;
	private boolean mVibrate = false;
	private long[] mVibrationPattern;

	public NotificationChannel(String id, CharSequence name, int importance) {
		mId = id;
		mName = name;
		mImportance = importance;
	}

	public String getId() {
		return mId;
	}

	public CharSequence getName() {
		return mName;
	}

	public int getImportance() {
		return mImportance;
	}

	public void setLockscreenVisibility(int a) {
		mLockscreenVisibility = a;
	}

	public int getLockscreenVisibility() {
		return mLockscreenVisibility;
	}

	public void setShowBadge(boolean a) {
		mShowBadge = a;
	}

	public boolean shouldShowBadge() {
		return mShowBadge;
	}

	public void setGroup(String grp) {
		mGroup = grp;
	}

	public String getGroup() {
		return mGroup;
	}

	public void enableLights(boolean en) {
		mLights = en;
	}

	public boolean shouldShowLights() {
		return mLights;
	}

	public void setLightColor(int color) {
		mLightColor = color;
	}

	public int getLightColor() {
		return mLightColor;
	}

	public void setVibrationPattern(long[] pattern) {
		mVibrationPattern = pattern;
	}

	public long[] getVibrationPattern() {
		return mVibrationPattern;
	}

	public void enableVibration(boolean en) {
		mVibrate = en;
	}

	public boolean shouldVibrate() {
		return mVibrate;
	}

	public void setSound(Uri uri, AudioAttributes attrs) {
		mSound = uri;
		mAudioAttributes = attrs;
	}

	public Uri getSound() {
		return mSound;
	}

	public AudioAttributes getAudioAttributes() {
		return mAudioAttributes;
	}

	public void setDescription(String description) {
		mDescription = description;
	}

	public String getDescription() {
		return mDescription;
	}

	public void setBypassDnd(boolean bypassDnd) {
		mBypassDnd = bypassDnd;
	}

	public boolean canBypassDnd() {
		return mBypassDnd;
	}
}
