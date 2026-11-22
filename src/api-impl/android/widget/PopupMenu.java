/*
 * Copyright (C) 2010 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package android.widget;

import android.atl.annotation.Export;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.res.ColorStateList;
import android.graphics.drawable.Drawable;
import android.view.Gravity;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.SubMenu;
import android.view.View;
import android.view.View.OnTouchListener;
import com.android.internal.R;
import java.util.ArrayList;
import java.util.List;

/**
 * A PopupMenu displays a {@link Menu} in a modal popup window anchored to a
 * {@link View}. The popup will appear below the anchor view if there is room,
 * or above it if there is not. If the IME is visible the popup will not
 * overlap it until it is touched. Touching outside of the popup will dismiss
 * it.
 */

public class PopupMenu {
	private final Context mContext;
	private final View mAnchor;
	private OnMenuItemClickListener mMenuItemClickListener;
	private OnDismissListener mOnDismissListener;
	private OnTouchListener mDragListener;
	private long popover;
	private MenuImpl menu;

	/**
	 * Constructor to create a new popup menu with an anchor view.
	 *
	 * @param context Context the popup menu is running in, through which it
	 *        can access the current theme, resources, etc.
	 * @param anchor Anchor view for this popup. The popup will appear below
	 *        the anchor if there is room, or above it if there is not.
	 */
	public PopupMenu(Context context, View anchor) {
		this(context, anchor, Gravity.NO_GRAVITY);
	}
	/**
	 * Constructor to create a new popup menu with an anchor view and alignment
	 * gravity.
	 *
	 * @param context Context the popup menu is running in, through which it
	 *        can access the current theme, resources, etc.
	 * @param anchor Anchor view for this popup. The popup will appear below
	 *        the anchor if there is room, or above it if there is not.
	 * @param gravity The {@link Gravity} value for aligning the popup with its
	 *        anchor.
	 */
	public PopupMenu(Context context, View anchor, int gravity) {
		this(context, anchor, gravity, R.attr.popupMenuStyle, 0);
	}
	/**
	 * Constructor a create a new popup menu with a specific style.
	 *
	 * @param context Context the popup menu is running in, through which it
	 *        can access the current theme, resources, etc.
	 * @param anchor Anchor view for this popup. The popup will appear below
	 *        the anchor if there is room, or above it if there is not.
	 * @param gravity The {@link Gravity} value for aligning the popup with its
	 *        anchor.
	 * @param popupStyleAttr An attribute in the current theme that contains a
	 *        reference to a style resource that supplies default values for
	 *        the popup window. Can be 0 to not look for defaults.
	 * @param popupStyleRes A resource identifier of a style resource that
	 *        supplies default values for the popup window, used only if
	 *        popupStyleAttr is 0 or can not be found in the theme. Can be 0
	 *        to not look for defaults.
	 */
	public PopupMenu(Context context, View anchor, int gravity, int popupStyleAttr,
	                 int popupStyleRes) {
		mContext = context;
		mAnchor = anchor;
		menu = new MenuImpl();
		popover = native_buildPopover(menu.menu);
	}

	/**
	 * @return a {@link MenuInflater} that can be used to inflate menu items
	 *         from XML into the menu returned by {@link #getMenu()}
	 * @see #getMenu()
	 */
	public MenuInflater getMenuInflater() {
		return new MenuInflater(mContext);
	}

	/**
	 * Sets a listener that will be notified when the user selects an item from
	 * the menu.
	 *
	 * @param listener the listener to notify
	 */
	public void setOnMenuItemClickListener(OnMenuItemClickListener listener) {
		mMenuItemClickListener = listener;
	}

	/**
	 * Sets a listener that will be notified when this menu is dismissed.
	 *
	 * @param listener the listener to notify
	 */
	public void setOnDismissListener(OnDismissListener listener) {
		mOnDismissListener = listener;
	}

	protected native long native_init();
	protected native void native_insertItem(long menu, int position, String item, int id);
	protected native void native_insertSubmenu(long menu, int position, String item, long submenu);
	protected native void native_removeItem(long menu, int position);
	protected native long native_buildPopover(long menu);
	protected native void native_show(long popover, long anchor);

	// callback from native code
	@Export
	protected void menuItemClickCallback(final int id) {
		MenuItem item = getMenu().findItem(id);
		if (item == null) {
			return;
		}
		MenuItemImpl impl = (MenuItemImpl) item;
		// AOSP order: per-item listener (true consumes), then the item's own
		// intent, then the popup-wide listener.
		if (impl.itemListener != null && impl.itemListener.onMenuItemClick(impl)) {
			return;
		}
		if (impl.intent != null) {
			mContext.startActivity(impl.intent);
			return;
		}
		if (mMenuItemClickListener != null) {
			mMenuItemClickListener.onMenuItemClick(impl);
		}
	}

	/**
	 * Inflate a menu resource into this PopupMenu. This is equivalent to
	 * calling {@code popupMenu.getMenuInflater().inflate(menuRes, popupMenu.getMenu())}.
	 *
	 * @param menuRes Menu resource to inflate
	 * @throws Exception
	 */
	public void inflate(int menuRes) throws Exception {
		getMenuInflater().inflate(menuRes, getMenu());
	}

	/**
	* Show the menu popup anchored to the view specified during construction.
	*
	* @see #dismiss()
	*/
	public void show() {
		System.out.println("PopupMenu.show() called");
		native_show(popover, mAnchor.widget);
	}

	public Menu getMenu() {
		return menu;
	}

	public void dismiss() {
		System.out.println("PopupMenu.dismiss() called");
	}

	/**
	 * Interface responsible for receiving menu item click events if the items
	 * themselves do not have individual item click listeners.
	 */
	public interface OnMenuItemClickListener {
		/**
		 * This method will be invoked when a menu item is clicked if the item
		 * itself did not already handle the event.
		 *
		 * @param item the menu item that was clicked
		 * @return {@code true} if the event was handled, {@code false}
		 *         otherwise
		 */
		boolean onMenuItemClick(MenuItem item);
	}

	/**
	 * Callback interface used to notify the application that the menu has closed.
	 */
	public interface OnDismissListener {
		/**
		 * Called when the associated menu has been dismissed.
		 *
		 * @param menu the popup menu that was dismissed
		 */
		void onDismiss(PopupMenu menu);
	}

	private class MenuImpl implements Menu {
		long menu = native_init();

		List<MenuItemImpl> items = new ArrayList<>();
		int numVisibleItems = 0;

		@Override
		public MenuItem add(int groupId, int itemId, int order, CharSequence title) {
			MenuItemImpl item = new MenuItemImpl(groupId, itemId, order, this,
			                                     String.valueOf(title), null);
			items.add(item);
			item.setVisible(true);
			return item;
		}

		@Override
		public MenuItem add(int groupId, int itemId, int order, int titleRes) {
			return add(groupId, itemId, order, mContext.getString(titleRes));
		}

		@Override
		public MenuItem findItem(int id) {
			for (MenuItemImpl item : items) {
				if (item.id == id)
					return item;
				if (item.subMenu != null) {
					MenuItem found = item.subMenu.findItem(id);
					if (found != null)
						return found;
				}
			}
			return null;
		}

		@Override
		public MenuItem getItem(int index) {
			return items.get(index);
		}

		@Override
		public void clear() {
			// Hide every row from the GMenu first (it has no invisible items),
			// then drop the Java-side list.
			for (MenuItemImpl item : new ArrayList<>(items)) {
				item.setVisible(false);
			}
			items.clear();
		}

		@Override
		public void removeGroup(int groupId) {
			for (MenuItemImpl item : new ArrayList<>(items)) {
				if (item.groupId == groupId) {
					item.setVisible(false);
					items.remove(item);
				}
			}
		}

		@Override
		public SubMenu addSubMenu(int id) {
			// AOSP passes a null title; GMenu rows need a string.
			return addSubMenu(0, id, 0, "");
		}

		@Override
		public MenuItem add(int id) {
			// AOSP: add(0, id, 0, null).
			return add(0, id, 0, "");
		}

		@Override
		public MenuItem add(CharSequence text) {
			return add(0, 0, 0, text);
		}

		@Override
		public void setGroupCheckable(int group, boolean checkable, boolean exclusive) {
			// AOSP propagates checkable to the group's items; `exclusive`
			// (radio behaviour) is not rendered by the popover.
			for (MenuItemImpl item : items) {
				if (item.groupId == group)
					item.setCheckable(checkable);
			}
		}

		@Override
		public SubMenu addSubMenu(int groupId, int itemId, int order, CharSequence title) {
			SubMenuImpl submenu = new SubMenuImpl(groupId, itemId, order, this,
			                                      String.valueOf(title));
			items.add(submenu.item);
			submenu.item.setVisible(true);
			return submenu;
		}

		@Override
		public void setGroupVisible(int group, boolean visible) {
			for (MenuItemImpl item : items) {
				if (item.groupId == group)
					item.setVisible(visible);
			}
		}

		@Override
		public void removeItem(int id) {
			// Only this menu's items (like AOSP MenuBuilder), and hide from the
			// GMenu before dropping the Java-side entry.
			for (MenuItemImpl item : new ArrayList<>(items)) {
				if (item.id == id) {
					item.setVisible(false);
					items.remove(item);
				}
			}
		}

		@Override
		public int size() {
			return items.size();
		}

		@Override
		public boolean hasVisibleItems() {
			return numVisibleItems > 0;
		}

		@Override
		public SubMenu addSubMenu(int groupId, int itemId, int order, int titleRes) {
			return addSubMenu(groupId, itemId, order, mContext.getString(titleRes));
		}

		@Override
		public int addIntentOptions(int groupId, int itemId, int order, ComponentName caller, Intent[] specifics, Intent intent, int flags, MenuItem[] outSpecificItems) {
			return 0;
		}

		@Override
		public void close() {
			// AOSP MenuBuilder.close on a PopupMenu's menu dismisses the popup.
			PopupMenu.this.dismiss();
		}
	}

	private class SubMenuImpl extends MenuImpl implements SubMenu {
		private MenuItemImpl item;

		public SubMenuImpl(int groupId, int id, int order, MenuImpl parent, String title) {
			item = new MenuItemImpl(groupId, id, order, parent, title, this);
		}

		@Override
		public MenuItem getItem() {
			return item;
		}

		@Override
		public void clearHeader() {
			// AOSP clears the submenu header; the GTK popover has no header.
		}

		@Override
		public SubMenu setIcon(Drawable icon) {
			// The popover submenu rows show only the title.
			return this;
		}
	}

	private class MenuItemImpl implements MenuItem {
		private int id;
		private int groupId;
		private int order;
		private MenuImpl parent;
		private String title;
		private CharSequence titleCondensed;
		private boolean checkable;
		private boolean checked;
		private boolean enabled = true;
		private Intent intent;
		private Drawable icon;
		private OnMenuItemClickListener itemListener;
		SubMenuImpl subMenu;
		int position; // position in list of visible items, or -1 if not visible

		private MenuItemImpl(int groupId, int id, int order, MenuImpl parent, String title,
		                     SubMenuImpl subMenu) {
			this.groupId = groupId;
			this.id = id;
			this.order = order;
			this.parent = parent;
			this.position = -1;
			this.title = title;
			this.subMenu = subMenu;
		}

		@Override
		public MenuItem setIcon(int iconRes) {
			return setIcon(mContext.getDrawable(iconRes));
		}

		@Override
		public MenuItem setIcon(Drawable icon) {
			// The GMenu popover rows are label-only, but store the icon like
			// AOSP so getIcon() answers correctly.
			this.icon = icon;
			return this;
		}

		@Override
		public MenuItem setVisible(boolean visible) {
			// GMenu doesn't support invisible items, so we remove them while they're not visible
			if (!visible && isVisible()) {
				parent.numVisibleItems--;
				for (int i = parent.items.size() - 1; i >= 0; i--) {
					MenuItemImpl item = parent.items.get(i);
					if (item != this && item.isVisible())
						item.position--;
					else if (item == this)
						break;
				}
				native_removeItem(parent.menu, position);
				position = -1;
			} else if (visible && !isVisible()) {
				position = parent.numVisibleItems++;
				for (int i = parent.items.size() - 1; i >= 0; i--) {
					MenuItemImpl item = parent.items.get(i);
					if (item != this && item.isVisible())
						position = item.position++;
					else if (item == this)
						break;
				}
				if (subMenu != null)
					native_insertSubmenu(parent.menu, position, title, subMenu.menu);
				else
					native_insertItem(parent.menu, position, title, id);
			}
			return this;
		}

		@Override
		public MenuItem setChecked(boolean checked) {
			// Popover rows do not render check state; store like AOSP.
			this.checked = checked;
			return this;
		}

		@Override
		public MenuItem setEnabled(boolean enabled) {
			// GMenu has no insensitive rows; store like AOSP.
			this.enabled = enabled;
			return this;
		}

		@Override
		public MenuItem setCheckable(boolean checkable) {
			this.checkable = checkable;
			return this;
		}

		@Override
		public boolean isCheckable() {
			return checkable;
		}

		@Override
		public MenuItem setTitleCondensed(CharSequence titleCondensed) {
			this.titleCondensed = titleCondensed;
			return this;
		}

		@Override
		public MenuItem setTitle(CharSequence title) {
			this.title = String.valueOf(title);
			// GMenu rows cannot be renamed: hide and re-insert so the popover
			// label updates (same visible-position bookkeeping as setVisible).
			if (isVisible()) {
				setVisible(false);
				setVisible(true);
			}
			return this;
		}

		@Override
		public MenuItem setActionView(View actionView) {
			// GMenu popover rows are label-only; nothing can host the view.
			return this;
		}

		@Override
		public void setShowAsAction(int action) {
			// Popups have no action bar: the item always stays in the popup.
		}

		@Override
		public int getItemId() {
			return id;
		}

		@Override
		public int getGroupId() {
			return groupId;
		}

		@Override
		public MenuItem setOnMenuItemClickListener(OnMenuItemClickListener listener) {
			this.itemListener = listener;
			return this;
		}

		@Override
		public MenuItem setTitle(int resId) {
			return setTitle(mContext.getString(resId));
		}

		@Override
		public boolean isVisible() {
			return position >= 0;
		}

		@Override
		public Drawable getIcon() {
			return icon;
		}

		@Override
		public SubMenu getSubMenu() {
			return subMenu;
		}

		@Override
		public MenuItem setActionView(int resId) {
			// Label-only popover rows (see setActionView(View)).
			return this;
		}

		@Override
		public View getActionView() {
			return null;
		}

		@Override
		public boolean hasSubMenu() {
			return subMenu != null;
		}

		@Override
		public MenuItem setOnActionExpandListener(OnActionExpandListener listener) {
			// Popups have no action bar to expand into.
			return this;
		}

		@Override
		public boolean isChecked() {
			return checked;
		}

		@Override
		public MenuItem setShowAsActionFlags(int action) {
			// Popups have no action bar.
			return this;
		}

		@Override
		public MenuItem setAlphabeticShortcut(char alphaChar) {
			return this;
		}

		@Override
		public MenuItem setShortcut(char numeric, char alpha) { return this; }

		@Override
		public int getOrder() {
			return order;
		}

		@Override
		public boolean isEnabled() {
			return enabled;
		}

		@Override
		public CharSequence getTitleCondensed() {
			return titleCondensed != null ? titleCondensed : title;
		}

		@Override
		public CharSequence getTitle() {
			return title;
		}

		@Override
		public MenuItem setNumericShortcut(char numericChar) {
			return this;
		}

		@Override
		public boolean expandActionView() {
			return false;
		}

		@Override
		public boolean isActionViewExpanded() {
			return false;
		}

		@Override
		public MenuItem setIntent(Intent intent) {
			this.intent = intent;
			return this;
		}

		@Override
		public MenuItem setIconTintList(ColorStateList tint) {
			return this;
		}
	}
}
