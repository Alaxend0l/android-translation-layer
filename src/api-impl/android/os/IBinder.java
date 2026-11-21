package android.os;

public interface IBinder {

	public interface DeathRecipient {
		public void binderDied();
	}

	/* Binders in ATL are in-process: they live as long as the process. */
	public boolean pingBinder();
	public boolean isBinderAlive();
	public void linkToDeath(DeathRecipient recipient, int flags) throws RemoteException;
	public boolean unlinkToDeath(DeathRecipient recipient, int flags);

	public IInterface queryLocalInterface(String descriptor);

	public boolean transact(int code, Parcel data, Parcel reply, int flags) throws RemoteException;

	public String getInterfaceDescriptor();
}
