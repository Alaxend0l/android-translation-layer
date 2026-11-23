package android.app;

public class FragmentTransaction {

	private Activity activity;

	public FragmentTransaction(Activity activity) {
		this.activity = activity;
	}

	public FragmentTransaction add(Fragment fragment, String string) {
		fragment.activity = activity;
		fragment.mTag = string;
		activity.fragments.add(fragment);
		return this;
	}

	public int commit() {
		/* AOSP's BackStackRecord.commit() returns the transaction's back-stack
		 * index, which is -1 unless addToBackStack() was called (oreo-release
		 * BackStackRecord.commitInternal). This shim has no back stack, so -1 is
		 * the faithful answer; 0 claimed an index that does not exist. The
		 * operations themselves were already applied by add(). */
		return -1;
	}
}
