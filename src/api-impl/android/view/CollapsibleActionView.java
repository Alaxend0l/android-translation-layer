/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 *
 * Ported from AOSP (frameworks/base, core/java/android/view/CollapsibleActionView.java).
 * The action view of a menu item implements this so the framework can tell it
 * when the item is expanded or collapsed.
 */

package android.view;

public interface CollapsibleActionView {
	void onActionViewExpanded();
	void onActionViewCollapsed();
}
