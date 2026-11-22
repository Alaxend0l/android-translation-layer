/*
 * Copyright (C) 2010 The Android Open Source Project
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

import android.content.Context;
import android.graphics.drawable.Drawable;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Toolbar;

/**
 * A window decoration at the top of an activity for handling navigation,
 * title/subtitle display and custom views.
 *
 * <p>This keeps the state a real ActionBar keeps, so apps that configure it in
 * {@code onCreate()} and read it back (title, subtitle, display options, custom
 * view) behave as they do on Android. ATL's window decor has no action-bar slot
 * (see android.view.Window: the decor is a single content FrameLayout with
 * android.R.id.content), so the bar is backed by the framework Toolbar that
 * {@code Window.findViewById(com.android.internal.R.id.action_bar)} hands out;
 * drawing the bar itself is not implemented, which is why these calls do not
 * change what appears on screen. Anything ATL cannot provide returns exactly
 * what Android returns in the same situation, as noted below.
 */
public class ActionBar {
	/** Display flag indicating that home/up should be shown. */
	public static final int DISPLAY_HOME_AS_UP = 4;
	/** Display flag indicating that the activity title should be shown. */
	public static final int DISPLAY_SHOW_TITLE = 2;
	/** Display flag indicating that the home affordance should be shown. */
	public static final int DISPLAY_SHOW_HOME = 1;
	/** Display flag indicating that a custom view should be shown if present. */
	public static final int DISPLAY_SHOW_CUSTOM = 8;
	/** Display flag indicating that the logo should be shown if set. */
	public static final int DISPLAY_USE_LOGO = 0x10;
	/** Display flag kept for compatibility with the framework constant. */
	public static final int DISPLAY_USE_SPLIT_WHEN_NARROW = 0x20;

	private final Context context;
	private final Toolbar toolbar;

	private CharSequence title;
	private CharSequence subtitle;
	private View customView;
	private ViewGroup.LayoutParams customViewLayoutParams;
	private Drawable background;
	private Drawable icon;
	private Drawable logo;
	private Drawable homeAsUpIndicator;
	private boolean showing = true;
	private float elevation;

	private int displayOptions;

	/**
	 * Not part of the Android API: ATL's Window builds the bar itself, the way
	 * PhoneWindow builds its ActionBarImpl.
	 */
	public ActionBar(Context context, Toolbar toolbar) {
		this.context = context;
		this.toolbar = toolbar;
		// AOSP's default display options for a phone-style action bar.
		this.displayOptions = DISPLAY_SHOW_TITLE | DISPLAY_SHOW_HOME;
	}

	public void setTitle(CharSequence title) {
		this.title = title;
	}

	public void setTitle(int resId) {
		setTitle(context.getText(resId));
	}

	public CharSequence getTitle() {
		return title;
	}

	public void setSubtitle(CharSequence subtitle) {
		this.subtitle = subtitle;
	}

	public void setSubtitle(int resId) {
		setSubtitle(context.getText(resId));
	}

	public CharSequence getSubtitle() {
		return subtitle;
	}

	public void setDisplayOptions(int options) {
		displayOptions = options;
	}

	public void setDisplayOptions(int options, int mask) {
		displayOptions = (displayOptions & ~mask) | (options & mask);
	}

	public int getDisplayOptions() {
		return displayOptions;
	}

	public void setDisplayHomeAsUpEnabled(boolean showHomeAsUp) {
		setDisplayOptions(showHomeAsUp ? DISPLAY_HOME_AS_UP : 0, DISPLAY_HOME_AS_UP);
	}

	public void setDisplayShowHomeEnabled(boolean showHomeEnabled) {
		setDisplayOptions(showHomeEnabled ? DISPLAY_SHOW_HOME : 0, DISPLAY_SHOW_HOME);
	}

	public void setDisplayShowTitleEnabled(boolean showTitleEnabled) {
		setDisplayOptions(showTitleEnabled ? DISPLAY_SHOW_TITLE : 0, DISPLAY_SHOW_TITLE);
	}

	public void setDisplayShowCustomEnabled(boolean showCustomEnabled) {
		setDisplayOptions(showCustomEnabled ? DISPLAY_SHOW_CUSTOM : 0, DISPLAY_SHOW_CUSTOM);
	}

	public void setDisplayUseLogoEnabled(boolean useLogoEnabled) {
		setDisplayOptions(useLogoEnabled ? DISPLAY_USE_LOGO : 0, DISPLAY_USE_LOGO);
	}

	public void setCustomView(View view) {
		setCustomView(view, view != null ? view.getLayoutParams() : null);
	}

	public void setCustomView(int resId) {
		// AOSP inflates the layout into the action bar's view stack. ATL has no
		// action bar container to inflate into, so the custom view is inflated
		// unparented and kept for getCustomView(), as Android keeps it.
		setCustomView(LayoutInflater.from(context).inflate(resId, null, false));
	}

	public void setCustomView(View view, ViewGroup.LayoutParams layoutParams) {
		customView = view;
		customViewLayoutParams = layoutParams != null ? layoutParams :
		    new ViewGroup.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT,
		                               ViewGroup.LayoutParams.WRAP_CONTENT);
		if (customView != null) {
			customView.setLayoutParams(customViewLayoutParams);
		}
	}

	public View getCustomView() {
		return customView;
	}

	public void setBackgroundDrawable(Drawable d) {
		background = d;
	}

	public void setBackgroundResource(int resId) {
		setBackgroundDrawable(context.getResources().getDrawable(resId));
	}

	public void setIcon(int resId) {
		setIcon(context.getResources().getDrawable(resId));
	}

	public void setIcon(Drawable d) {
		icon = d;
	}

	public void setLogo(int resId) {
		setLogo(context.getResources().getDrawable(resId));
	}

	public void setLogo(Drawable d) {
		logo = d;
	}

	public void setHomeAsUpIndicator(int resId) {
		setHomeAsUpIndicator(context.getResources().getDrawable(resId));
	}

	public void setHomeAsUpIndicator(Drawable d) {
		homeAsUpIndicator = d;
	}

	public void setElevation(float elevation) {
		this.elevation = elevation;
	}

	public float getElevation() {
		return elevation;
	}

	public void show() {
		showing = true;
		toolbar.setVisibility(View.VISIBLE);
	}

	public void hide() {
		showing = false;
		toolbar.setVisibility(View.GONE);
	}

	public boolean isShowing() {
		return showing;
	}

	public Context getThemedContext() {
		// AOSP returns the action bar's own themed context; ATL's bar has no
		// separate themed context, and the window's context is what Android's
		// bar context resolves to for the app's own theme.
		return context;
	}

	public void setNavigationOnClickListener(View.OnClickListener listener) {
		toolbar.setNavigationOnClickListener(listener);
	}

	Drawable getBackgroundDrawable() {
		return background;
	}

	Drawable getIconDrawable() {
		return icon;
	}

	Drawable getLogoDrawable() {
		return logo;
	}

	Drawable getHomeAsUpIndicator() {
		return homeAsUpIndicator;
	}
}
