package android.appwidget;

import android.content.ComponentName;
import android.content.Context;
import android.content.pm.ActivityInfo;
import android.content.pm.PackageManager;
import android.graphics.drawable.Drawable;
import android.os.Process;
import android.os.UserHandle;

/* AOSP's description of a widget provider (fields filled by AppWidgetManager
 * from the receiver's <appwidget-provider> XML, sizes in pixels). */
public class AppWidgetProviderInfo implements Cloneable {
	public static final int RESIZE_NONE = 0;
	public static final int RESIZE_HORIZONTAL = 1;
	public static final int RESIZE_VERTICAL = 2;
	public static final int RESIZE_BOTH = RESIZE_HORIZONTAL | RESIZE_VERTICAL;

	public static final int WIDGET_CATEGORY_HOME_SCREEN = 1;
	public static final int WIDGET_CATEGORY_KEYGUARD = 2;
	public static final int WIDGET_CATEGORY_SEARCHBOX = 4;

	public static final int WIDGET_FEATURE_RECONFIGURABLE = 1;
	public static final int WIDGET_FEATURE_HIDE_FROM_PICKER = 2;
	public static final int WIDGET_FEATURE_CONFIGURATION_OPTIONAL = 4;

	public ComponentName provider;
	public int minWidth;
	public int minHeight;
	public int minResizeWidth;
	public int minResizeHeight;
	public int maxResizeWidth;
	public int maxResizeHeight;
	public int targetCellWidth;
	public int targetCellHeight;
	public int updatePeriodMillis;
	public int initialLayout;
	public int initialKeyguardLayout;
	public ComponentName configure;
	@Deprecated
	public String label;
	public int icon;
	public int autoAdvanceViewId = -1;
	public int previewImage;
	public int previewLayout;
	public int resizeMode;
	public int widgetCategory = WIDGET_CATEGORY_HOME_SCREEN;
	public int widgetFeatures;
	public int descriptionRes;

	/** the provider's receiver */
	public ActivityInfo providerInfo;

	public AppWidgetProviderInfo() {}

	public final String loadLabel(PackageManager packageManager) {
		CharSequence l = providerInfo != null ? providerInfo.loadLabel(packageManager) : null;
		return l != null ? l.toString().trim() : label;
	}

	public final Drawable loadIcon(Context context, int density) {
		if (icon != 0)
			return context.getResources().getDrawable(icon, context.getTheme());
		return providerInfo != null ? providerInfo.loadIcon(context.getPackageManager()) : null;
	}

	public final Drawable loadPreviewImage(Context context, int density) {
		return previewImage != 0 ? context.getResources().getDrawable(previewImage, context.getTheme()) : null;
	}

	public final CharSequence loadDescription(Context context) {
		return descriptionRes != 0 ? context.getResources().getText(descriptionRes) : null;
	}

	public final UserHandle getProfile() {
		return Process.myUserHandle();
	}

	public ActivityInfo getActivityInfo() {
		return providerInfo;
	}

	@Override
	public AppWidgetProviderInfo clone() {
		try {
			return (AppWidgetProviderInfo)super.clone();
		} catch (CloneNotSupportedException e) {
			throw new AssertionError(e);
		}
	}

	@Override
	public String toString() {
		return "AppWidgetProviderInfo(" + provider + ")";
	}
}
