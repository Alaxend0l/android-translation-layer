package android.os;

import android.atl.ATLLoadedApp;
import android.content.Context;

public class Binder implements IBinder {

	private String mDescriptor;
	private IInterface mOwner;

	public void attachInterface(IInterface owner, String descriptor) {
		mOwner = owner;
		mDescriptor = descriptor;
	}

	public String getInterfaceDescriptor() {
		return mDescriptor;
	}

	public IInterface getOwner() {
		return mOwner;
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
	public void linkToDeath(DeathRecipient recipient, int flags) {}
	@Override
	public boolean unlinkToDeath(DeathRecipient recipient, int flags) {
		return true;
	}
	public static void flushPendingCommands() {}

	public static long clearCallingIdentity() { return 0; }

	public static void restoreCallingIdentity(long identityToken) {}

	@Override
	public IInterface queryLocalInterface(String descriptor) {
		return (mDescriptor != null && mDescriptor.equals(descriptor)) ? mOwner : null;
	}

	/* ATL_BINDER_TRACE=1: log every in-process transaction (debugging IPC-style libraries such as media3) */
	private static final boolean TRACE = "1".equals(System.getenv("ATL_BINDER_TRACE"));

	@Override
	public final boolean transact(int code, Parcel data, Parcel reply, int flags) throws RemoteException {
		if (data != null)
			data.setDataPosition(0);
		if (TRACE)
			android.util.Slog.i("ATLBinder", "transact " + getClass().getName() + " [" + mDescriptor + "] code=" + code + " flags=" + flags + " size=" + (data != null ? data.dataSize() : -1));
		boolean r = onTransact(code, data, reply, flags);
		if (TRACE)
			android.util.Slog.i("ATLBinder", "  -> " + r + " (" + getClass().getName() + " code=" + code + ")");
		if (reply != null)
			reply.setDataPosition(0);
		return r;
	}

	protected boolean onTransact(int code, Parcel data, Parcel reply, int flags) throws RemoteException { return false; }

	public static int getCallingUid() { return ATLLoadedApp.getPrimaryApplication().pkg.applicationInfo.uid; }

	public static int getCallingPid() { return 0; }

	/* identity, as in AOSP (Object.equals): binders identify their owners, e.g. media3
	 * keys connected controllers by their callback binder. This used to treat every
	 * Binder as equal, which merged media3's controllers (Fossify Music got the
	 * notification controller's restricted commands and couldn't play). */
}
