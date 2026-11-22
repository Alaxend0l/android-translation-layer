package android.net;

import android.atl.annotation.Export;
import android.os.Handler;

class ProxyInfo {}

public class ConnectivityManager {

	public class NetworkCallback {
		@Export
		public void onAvailable(Network network) {}

		@Export
		public void onLost(Network network) {}
	}

	public NetworkInfo getNetworkInfo(int networkType) {
		return new NetworkInfo(nativeGetNetworkAvailable());
	}

	public NetworkInfo getActiveNetworkInfo() {
		return new NetworkInfo(nativeGetNetworkAvailable());
	}

	public native void registerNetworkCallback(NetworkRequest request, NetworkCallback callback);

	public void unregisterNetworkCallback(NetworkCallback callback) {}

	public native boolean isActiveNetworkMetered();

	protected native boolean nativeGetNetworkAvailable();

	public NetworkInfo[] getAllNetworkInfo() {
		return new NetworkInfo[] {getActiveNetworkInfo()};
	}

	public Network getActiveNetwork() {
		return new Network();
	}

	public Network[] getAllNetworks() {
		return new Network[] {getActiveNetwork()};
	}

	public NetworkCapabilities getNetworkCapabilities(Network network) {
		// as in getActiveNetworkInfo, a null network means there is none to report on
		if (network == null || !nativeGetNetworkAvailable())
			return null;

		NetworkCapabilities capabilities = new NetworkCapabilities();
		capabilities.addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET);
		// AOSP marks a network that GNetworkMonitor considers reachable as validated; apps and
		// WorkManager's CONNECTED constraint gate on it, so it must be present here.
		capabilities.addCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED);

		if (!isActiveNetworkMetered())
			capabilities.addCapability(NetworkCapabilities.NET_CAPABILITY_NOT_METERED);

		capabilities.addTransportType(NetworkCapabilities.TRANSPORT_WIFI);

		return capabilities;
	}

	public void registerDefaultNetworkCallback(NetworkCallback cb, Handler hdl) {}

	public void registerDefaultNetworkCallback(NetworkCallback cb) {}

	public ProxyInfo getDefaultProxy() { return null; }

	public LinkProperties getLinkProperties(Network network) { return null; }

	// AOSP values (ConnectivityManager.RESTRICT_BACKGROUND_STATUS_*).
	public static final int RESTRICT_BACKGROUND_STATUS_INVALID = 0;
	public static final int RESTRICT_BACKGROUND_STATUS_DISABLED = 1;
	public static final int RESTRICT_BACKGROUND_STATUS_ENABLED = 2;
	public static final int RESTRICT_BACKGROUND_STATUS_WHITELISTED = 3;

	public int getRestrictBackgroundStatus() {
		// ATL has no data-saver / background-restriction state; DISABLED is
		// what Android reports when data saver is off.
		return RESTRICT_BACKGROUND_STATUS_DISABLED;
	}
}
