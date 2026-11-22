/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 *
 * Minimal ATL stand-in for AOSP's ViewRootImpl.
 *
 * AOSP's ViewRootImpl is the client-side window session (surface, traversal,
 * input queues). ATL replaces that whole mechanism with GTK widgets, so the
 * real class cannot be ported and is not needed by ATL itself - nothing in
 * ATL uses it. Compat libraries, however, look it up by name and read its
 * attach-info field reflectively (androidx.core.view.WindowInsetsCompat
 * Impl20.loadReflectionField), and crash-log a NoSuchMethodException /
 * ClassNotFoundException path when it is absent. The pieces those reflectors
 * can see are provided here with AOSP-compatible semantics: an ATL window
 * has no system bars, so its visible insets are its whole frame - exactly
 * what AOSP reports for a window with no system bars.
 */
package android.view;

import android.graphics.Rect;

public class ViewRootImpl {
	final View mView;
	final View.AttachInfo mAttachInfo;

	ViewRootImpl(View view) {
		mView = view;
		mAttachInfo = new View.AttachInfo(this);
	}

	/**
	 * AOSP hidden API used by compat code before ViewRootImpl was hidden per
	 * version: copy the currently visible insets of the window.
	 */
	public void getVisibleInsets(Rect outInsets) {
		outInsets.set(mAttachInfo.mVisibleInsets);
	}
}
