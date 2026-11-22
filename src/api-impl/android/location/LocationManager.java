package android.location;

import android.app.PendingIntent;
import android.atl.annotation.Export;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import java.lang.Runnable;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class LocationManager {

	static Set<LocationListener> listeners = new HashSet<>();

	public String getBestProvider(Criteria criteria, boolean enabledOnly) {
		return "xdgportal";
	}

	public Location getLastKnownLocation(String provider) {
		return null;
	}

	public void requestLocationUpdates(String provider, long minTimeMs, float minDistanceM, LocationListener listener) {
		listeners.add(listener);
		nativeGetLocation();
	}

	private native void nativeGetLocation();

	@Export
	private static void locationUpdated(double latitude,
	                                    double longitude,
	                                    double altitude,
	                                    double accuracy,
	                                    double speed,
	                                    double bearing,
	                                    long timestamp) {
		for (LocationListener locationListener : listeners) {
			locationListener.onLocationChanged(new Location(latitude, longitude, altitude, accuracy, speed, bearing, timestamp));
		}
	}

	public boolean sendExtraCommand(String provider, String command, Bundle extras) {
		return true;
	}

	public GpsStatus getGpsStatus(GpsStatus status) {
		if (status == null) {
			status = new GpsStatus();
		}
		return status;
	}

	public void removeUpdates(LocationListener listener) {
		listeners.remove(listener);
	}

	public void removeUpdates(PendingIntent intent) {
		// AOSP throws IllegalArgumentException on a null PendingIntent and
		// otherwise removes any PendingIntent-based location registrations.
		// ATL never registers those: the requestLocationUpdates(PendingIntent)
		// overloads do not exist here, and ATL's location pipeline only calls
		// LocationListeners (see locationUpdated above). Nothing to remove.
		if (intent == null) {
			throw new IllegalArgumentException("intent is null");
		}
	}

	public List<String> getAllProviders() {
		return Collections.emptyList();
	}

	public List<String> getProviders(boolean enabledOnly) {
		return Collections.emptyList();
	}

	public boolean registerGnssStatusCallback(GnssStatus.Callback callback, Handler handler) {
		new Handler(Looper.getMainLooper()).post(new Runnable() {
			@Override
			public void run() {
				callback.onSatelliteStatusChanged(new GnssStatus());
			}
		});
		return true;
	}

	public void unregisterGnssStatusCallback(GnssStatus.Callback callback) {
	}

	public boolean isProviderEnabled(String provider) {
		return System.getenv("ATL_UGLY_ENABLE_LOCATION") != null;
	}
}
