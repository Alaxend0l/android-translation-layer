package com.google.android.gms.ads.measurement;

import android.os.IBinder;
import android.os.IInterface;
import android.os.Parcel;

public class DynamiteMeasurementManager implements IBinder {

	@Override
	public String getInterfaceDescriptor() {
		return "com.google.android.gms.ads.measurement.IMeasurementManager";
	}

	public void initialize() {
	}

	@Override
	public boolean transact(int code, Parcel data, Parcel reply, int flags) {
		return true;
	}

	@Override
	public IInterface queryLocalInterface(String descriptor) {
		return null;
	}

	// a local binder can't die while its process runs (as in AOSP's Binder)
	@Override
	public boolean pingBinder() {
		return true;
	}
	@Override
	public boolean isBinderAlive() {
		return true;
	}
	@Override
	public void linkToDeath(IBinder.DeathRecipient recipient, int flags) {}
	@Override
	public boolean unlinkToDeath(IBinder.DeathRecipient recipient, int flags) {
		return true;
	}
}
