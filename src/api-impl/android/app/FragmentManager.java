package android.app;

import java.util.List;

public class FragmentManager {

	private Activity activity;

	public FragmentManager(Activity activity) {
		this.activity = activity;
	}

	public Fragment findFragmentByTag(String tag) {
		/* AOSP's FragmentManagerImpl.findFragmentByTag (oreo-release): scan
		 * the added fragments newest first for tag.equals(f.mTag), then the
		 * back-stack records - this shim has no back stack, and add() now
		 * remembers the tag (it used to discard it, so nothing could match). */
		if (tag == null)
			return null;
		List<Fragment> frags = activity.fragments;
		for (int i = frags.size() - 1; i >= 0; i--) {
			Fragment f = frags.get(i);
			if (f != null && tag.equals(f.mTag))
				return f;
		}
		return null;
	}

	public FragmentTransaction beginTransaction() {
		return new FragmentTransaction(activity);
	}

	public boolean executePendingTransactions() {
		/* AOSP runs queued commit() actions and answers whether any ran.
		 * This shim applies operations in FragmentTransaction.add() directly
		 * (nothing is ever pending), so false - the AOSP answer with an empty
		 * queue - is faithful. */
		return false;
	}
}
