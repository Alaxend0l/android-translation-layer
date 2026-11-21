package android.appwidget;

import android.atl.ATLLoadedApp;
import android.content.ComponentName;
import android.content.Context;
import android.content.pm.PackageParser;
import android.content.res.Resources;
import android.content.res.TypedArray;
import android.content.res.XmlResourceParser;
import android.os.Bundle;
import android.os.UserHandle;
import android.util.AttributeSet;
import android.util.Slog;
import android.util.Xml;
import android.widget.RemoteViews;
import com.android.internal.R;
import java.util.ArrayList;
import java.util.List;
import org.xmlpull.v1.XmlPullParser;

/* Widget API for apps that provide widgets (atl-harness
 * docs/agent-tasks/app-widgets.md, stage 1). Providers are the app's own
 * receivers with an APPWIDGET_UPDATE filter and android.appwidget.provider
 * meta-data. No widget host exists yet, so this behaves like a device where no
 * widget has been placed: no widget ids, updates have no target, pinning isn't
 * supported. */
public class AppWidgetManager {
	private static final String TAG = "AppWidgetManager";

	public static final String ACTION_APPWIDGET_PICK = "android.appwidget.action.APPWIDGET_PICK";
	public static final String ACTION_APPWIDGET_BIND = "android.appwidget.action.APPWIDGET_BIND";
	public static final String ACTION_APPWIDGET_CONFIGURE = "android.appwidget.action.APPWIDGET_CONFIGURE";
	public static final String ACTION_APPWIDGET_UPDATE = "android.appwidget.action.APPWIDGET_UPDATE";
	public static final String ACTION_APPWIDGET_OPTIONS_CHANGED = "android.appwidget.action.APPWIDGET_UPDATE_OPTIONS";
	public static final String ACTION_APPWIDGET_DELETED = "android.appwidget.action.APPWIDGET_DELETED";
	public static final String ACTION_APPWIDGET_DISABLED = "android.appwidget.action.APPWIDGET_DISABLED";
	public static final String ACTION_APPWIDGET_ENABLED = "android.appwidget.action.APPWIDGET_ENABLED";
	public static final String ACTION_APPWIDGET_RESTORED = "android.appwidget.action.APPWIDGET_RESTORED";
	public static final String ACTION_APPWIDGET_HOST_RESTORED = "android.appwidget.action.APPWIDGET_HOST_RESTORED";

	public static final String EXTRA_APPWIDGET_ID = "appWidgetId";
	public static final String EXTRA_APPWIDGET_IDS = "appWidgetIds";
	public static final String EXTRA_APPWIDGET_OLD_IDS = "appWidgetOldIds";
	public static final String EXTRA_APPWIDGET_OPTIONS = "appWidgetOptions";
	public static final String EXTRA_APPWIDGET_PROVIDER = "appWidgetProvider";
	public static final String EXTRA_APPWIDGET_PROVIDER_PROFILE = "appWidgetProviderProfile";
	public static final String EXTRA_HOST_ID = "hostId";
	public static final String EXTRA_CUSTOM_INFO = "customInfo";
	public static final String EXTRA_CUSTOM_EXTRAS = "customExtras";
	public static final String EXTRA_CATEGORY_FILTER = "categoryFilter";
	public static final int INVALID_APPWIDGET_ID = 0;

	public static final String OPTION_APPWIDGET_MIN_WIDTH = "appWidgetMinWidth";
	public static final String OPTION_APPWIDGET_MIN_HEIGHT = "appWidgetMinHeight";
	public static final String OPTION_APPWIDGET_MAX_WIDTH = "appWidgetMaxWidth";
	public static final String OPTION_APPWIDGET_MAX_HEIGHT = "appWidgetMaxHeight";
	public static final String OPTION_APPWIDGET_HOST_CATEGORY = "appWidgetCategory";
	public static final String OPTION_APPWIDGET_SIZES = "appWidgetSizes";
	public static final String OPTION_APPWIDGET_RESTORE_COMPLETED = "appWidgetRestoreCompleted";

	public static final String META_DATA_APPWIDGET_PROVIDER = "android.appwidget.provider";

	private static AppWidgetManager instance;
	private final Context context;
	private List<AppWidgetProviderInfo> providers;

	private AppWidgetManager(Context context) {
		this.context = context;
	}

	public static synchronized AppWidgetManager getInstance(Context context) {
		if (instance == null)
			instance = new AppWidgetManager(context.getApplicationContext() != null ? context.getApplicationContext() : context);
		return instance;
	}

	/* ---- providers ---- */

	public List<AppWidgetProviderInfo> getInstalledProviders() {
		return getInstalledProviders(AppWidgetProviderInfo.WIDGET_CATEGORY_HOME_SCREEN);
	}

	/** @hide (older launchers call it by reflection) */
	public List<AppWidgetProviderInfo> getInstalledProviders(int categoryFilter) {
		List<AppWidgetProviderInfo> out = new ArrayList<>();
		for (AppWidgetProviderInfo info : providers())
			if ((info.widgetCategory & categoryFilter) != 0)
				out.add(info.clone());
		return out;
	}

	public List<AppWidgetProviderInfo> getInstalledProvidersForProfile(UserHandle profile) {
		return getInstalledProviders();
	}

	public List<AppWidgetProviderInfo> getInstalledProvidersForPackage(String packageName, UserHandle profile) {
		List<AppWidgetProviderInfo> out = new ArrayList<>();
		for (AppWidgetProviderInfo info : providers())
			if (packageName == null || packageName.equals(info.provider.getPackageName()))
				out.add(info.clone());
		return out;
	}

	private synchronized List<AppWidgetProviderInfo> providers() {
		if (providers != null)
			return providers;
		providers = new ArrayList<>();
		PackageParser.Package pkg = ATLLoadedApp.getPrimaryApplication().pkg;
		for (PackageParser.Activity receiver : pkg.receivers) {
			if (receiver.intents == null || receiver.metaData == null)
				continue;
			boolean update = false;
			for (PackageParser.ActivityIntentInfo filter : receiver.intents)
				update |= filter.matchAction(ACTION_APPWIDGET_UPDATE);
			int xml = receiver.metaData.getInt(META_DATA_APPWIDGET_PROVIDER, 0);
			if (!update || xml == 0)
				continue;
			try {
				AppWidgetProviderInfo info = parseProviderInfo(pkg.packageName, receiver, xml);
				if (info != null)
					providers.add(info);
			} catch (Exception e) { // a broken provider XML hides only that provider, as on Android
				Slog.w(TAG, "can't read widget provider " + receiver.className + ": " + e);
			}
		}
		return providers;
	}

	/* as AOSP's AppWidgetServiceImpl.parseProviderInfoXml (sizes converted to px) */
	private AppWidgetProviderInfo parseProviderInfo(String packageName, PackageParser.Activity receiver, int xml) throws Exception {
		Resources res = context.getResources();
		XmlResourceParser parser = res.getXml(xml);
		try {
			int type;
			while ((type = parser.next()) != XmlPullParser.END_DOCUMENT && type != XmlPullParser.START_TAG) {}
			if (type != XmlPullParser.START_TAG || !"appwidget-provider".equals(parser.getName())) {
				Slog.w(TAG, "provider XML of " + receiver.className + " doesn't start with <appwidget-provider>");
				return null;
			}
			AttributeSet attrs = Xml.asAttributeSet(parser);
			TypedArray sa = res.obtainAttributes(attrs, R.styleable.AppWidgetProviderInfo);
			AppWidgetProviderInfo info = new AppWidgetProviderInfo();
			info.provider = new ComponentName(packageName, receiver.className);
			info.providerInfo = receiver.info;
			info.minWidth = sa.getDimensionPixelSize(R.styleable.AppWidgetProviderInfo_minWidth, 0);
			info.minHeight = sa.getDimensionPixelSize(R.styleable.AppWidgetProviderInfo_minHeight, 0);
			info.minResizeWidth = sa.getDimensionPixelSize(R.styleable.AppWidgetProviderInfo_minResizeWidth, info.minWidth);
			info.minResizeHeight = sa.getDimensionPixelSize(R.styleable.AppWidgetProviderInfo_minResizeHeight, info.minHeight);
			info.maxResizeWidth = sa.getDimensionPixelSize(R.styleable.AppWidgetProviderInfo_maxResizeWidth, 0);
			info.maxResizeHeight = sa.getDimensionPixelSize(R.styleable.AppWidgetProviderInfo_maxResizeHeight, 0);
			info.targetCellWidth = sa.getInt(R.styleable.AppWidgetProviderInfo_targetCellWidth, 0);
			info.targetCellHeight = sa.getInt(R.styleable.AppWidgetProviderInfo_targetCellHeight, 0);
			info.updatePeriodMillis = sa.getInt(R.styleable.AppWidgetProviderInfo_updatePeriodMillis, 0);
			info.initialLayout = sa.getResourceId(R.styleable.AppWidgetProviderInfo_initialLayout, 0);
			info.initialKeyguardLayout = sa.getResourceId(R.styleable.AppWidgetProviderInfo_initialKeyguardLayout, 0);
			String configure = sa.getString(R.styleable.AppWidgetProviderInfo_configure);
			if (configure != null)
				info.configure = new ComponentName(packageName, configure.startsWith(".") ? packageName + configure : configure);
			info.label = receiver.info != null && receiver.info.nonLocalizedLabel != null ? receiver.info.nonLocalizedLabel.toString() : null;
			info.icon = receiver.info == null ? 0 : receiver.info.icon != 0 ? receiver.info.icon : receiver.info.applicationInfo != null ? receiver.info.applicationInfo.icon : 0;
			info.previewImage = sa.getResourceId(R.styleable.AppWidgetProviderInfo_previewImage, 0);
			info.previewLayout = sa.getResourceId(R.styleable.AppWidgetProviderInfo_previewLayout, 0);
			info.autoAdvanceViewId = sa.getResourceId(R.styleable.AppWidgetProviderInfo_autoAdvanceViewId, -1);
			info.resizeMode = sa.getInt(R.styleable.AppWidgetProviderInfo_resizeMode, AppWidgetProviderInfo.RESIZE_NONE);
			info.widgetCategory = sa.getInt(R.styleable.AppWidgetProviderInfo_widgetCategory, AppWidgetProviderInfo.WIDGET_CATEGORY_HOME_SCREEN);
			info.widgetFeatures = sa.getInt(R.styleable.AppWidgetProviderInfo_widgetFeatures, 0);
			info.descriptionRes = sa.getResourceId(R.styleable.AppWidgetProviderInfo_description, 0);
			sa.recycle();
			return info;
		} finally {
			parser.close();
		}
	}

	/* ---- widget instances: none until a host exists (stage 3) ---- */

	public int[] getAppWidgetIds(ComponentName provider) {
		return new int[0];
	}

	public AppWidgetProviderInfo getAppWidgetInfo(int appWidgetId) {
		return null;
	}

	public Bundle getAppWidgetOptions(int appWidgetId) {
		return new Bundle();
	}

	/* updates for ids that don't exist have no target (AppWidgetService ignores them) */
	public void updateAppWidget(int[] appWidgetIds, RemoteViews views) {}

	public void updateAppWidget(int appWidgetId, RemoteViews views) {}

	public void updateAppWidget(ComponentName provider, RemoteViews views) {}

	public void partiallyUpdateAppWidget(int[] appWidgetIds, RemoteViews views) {}

	public void partiallyUpdateAppWidget(int appWidgetId, RemoteViews views) {}

	public void updateAppWidgetOptions(int appWidgetId, Bundle options) {}

	public void updateAppWidgetProviderInfo(ComponentName provider, String metaDataKey) {}

	public void notifyAppWidgetViewDataChanged(int appWidgetId, int viewId) {}

	public void notifyAppWidgetViewDataChanged(int[] appWidgetIds, int viewId) {}

	/* no launcher can pin widgets yet (what Android reports for such a launcher) */
	public boolean isRequestPinAppWidgetSupported() {
		return false;
	}

	public boolean requestPinAppWidget(ComponentName provider, Bundle extras, android.app.PendingIntent successCallback) {
		return false;
	}

	public boolean bindAppWidgetIdIfAllowed(int appWidgetId, ComponentName provider) {
		return false;
	}

	public boolean bindAppWidgetIdIfAllowed(int appWidgetId, ComponentName provider, Bundle options) {
		return false;
	}

	public boolean bindAppWidgetIdIfAllowed(int appWidgetId, UserHandle user, ComponentName provider, Bundle options) {
		return false;
	}
}
