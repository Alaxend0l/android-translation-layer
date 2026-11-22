/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 *
 * android.widget.SearchView, ported from AOSP (frameworks/base, 7.1) with the
 * parts ATL cannot provide left out, as the fork rules require.
 *
 * AOSP's SearchView is a LinearLayout with a SearchAutoComplete edit field and
 * the search / submit / go / cancel buttons of a search drop-down, and it asks
 * SearchManager for suggestions. ATL has no SearchManager service, no search
 * suggestions window and no suggester API, so suggestions are not provided here:
 * the widget keeps the same public state (query, query hint, iconified, submit
 * button, listeners) and fires OnQueryTextListener from the edit field text,
 * from Enter on the edit field and from the submit button. Submitting through
 * the IME action is not possible in ATL either, because both
 * TextView.setOnEditorActionListener and View.setOnKeyListener are no-ops there;
 * the submit button is what reaches onQueryTextSubmit.
 */

package android.widget;

import android.content.Context;
import android.graphics.Rect;
import android.util.AttributeSet;
import android.view.CollapsibleActionView;
import android.view.KeyEvent;
import android.view.View;
import android.text.Editable;
import android.text.TextWatcher;

public class SearchView extends LinearLayout implements CollapsibleActionView {
	/**
	 * Listener for the text entered in the search field.
	 */
	public interface OnQueryTextListener {
		/**
		 * @return True if the new query was submitted and handled, false otherwise.
		 */
		boolean onQueryTextSubmit(String query);

		/**
		 * @return True if the change is handled by the listener, false otherwise.
		 */
		boolean onQueryTextChange(String newText);
	}

	/**
	 * Listener for closing the search field.
	 */
	public interface OnCloseListener {
		/**
		 * @return True if the closing is handled by the listener, false otherwise.
		 */
		boolean onClose();
	}

	/**
	 * Listener for suggestion selection. ATL has no suggestions (no SearchManager
	 * service), so nothing calls this; the interface exists because apps reference it.
	 */
	public interface OnSuggestionListener {
		boolean onSuggestionSelect(int position);
		boolean onSuggestionClick(int position);
	}

	private EditText mSearchSrcTextView;
	private Button mGoBtn;
	private OnQueryTextListener mOnQueryChangeListener;
	private OnCloseListener mOnCloseListener;
	private OnSuggestionListener mOnSuggestListener;
	private OnClickListener mOnSearchClickListener;
	private CharSequence mQueryHint;
	private boolean mIconifiedByDefault = true;
	private boolean mIconified = true;
	private boolean mSubmitButtonEnabled = false;
	private boolean mExpanded = false;

	public SearchView(Context context) {
		super(context);
		init(context);
	}

	public SearchView(Context context, AttributeSet attrs) {
		super(context, attrs);
		init(context);
	}

	public SearchView(Context context, AttributeSet attrs, int defStyleAttr) {
		super(context, attrs, defStyleAttr);
		init(context);
	}

	private void init(Context context) {
		setOrientation(LinearLayout.HORIZONTAL);

		mSearchSrcTextView = new EditText(context);
		mSearchSrcTextView.setSingleLine();
		mSearchSrcTextView.addTextChangedListener(new TextWatcher() {
			@Override
			public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

			@Override
			public void onTextChanged(CharSequence s, int start, int before, int count) {}

			@Override
			public void afterTextChanged(Editable s) {
				handleTextChange(s == null ? "" : s.toString());
			}
		});
		mSearchSrcTextView.setOnKeyListener(new OnKeyListener() {
			@Override
			public boolean onKey(View v, int keyCode, KeyEvent event) {
				if (keyCode == KeyEvent.KEYCODE_ENTER && event.getAction() == KeyEvent.ACTION_DOWN) {
					submitQuery();
					return true;
				}
				return false;
			}
		});
		addView(mSearchSrcTextView,
		        new LayoutParams(0, LayoutParams.WRAP_CONTENT, 1.0f));

		mGoBtn = new Button(context);
		mGoBtn.setText("Go");
		mGoBtn.setOnClickListener(new OnClickListener() {
			@Override
			public void onClick(View v) {
				submitQuery();
			}
		});
		addView(mGoBtn, new LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT));
		updateSubmitButtonVisibility();
	}

	private void handleTextChange(String newText) {
		if (mOnQueryChangeListener == null || !mExpanded) {
			return;
		}
		mOnQueryChangeListener.onQueryTextChange(newText);
	}

	private boolean submitQuery() {
		if (mOnQueryChangeListener == null) {
			return false;
		}
		CharSequence query = mSearchSrcTextView.getText();
		return mOnQueryChangeListener.onQueryTextSubmit(query == null ? "" : query.toString());
	}

	@Override
	public void setEnabled(boolean enabled) {
		super.setEnabled(enabled);
		mSearchSrcTextView.setEnabled(enabled);
	}

	public void setIconifiedByDefault(boolean iconified) {
		if (mIconifiedByDefault == iconified)
			return;
		mIconifiedByDefault = iconified;
		updateFocusedState();
	}

	public boolean isIconifiedByDefault() {
		return mIconifiedByDefault;
	}

	/**
	 * Collapse or expand the search field.
	 */
	public void setIconified(boolean iconify) {
		if (iconify) {
			mIconified = true;
			clearFocus();
		} else {
			mIconified = false;
			focusSearch();
		}
		updateFocusedState();
	}

	public boolean isIconified() {
		return mIconified;
	}

	public void setOnQueryTextListener(OnQueryTextListener listener) {
		mOnQueryChangeListener = listener;
	}

	public void setOnCloseListener(OnCloseListener listener) {
		mOnCloseListener = listener;
	}

	public void setOnSuggestionListener(OnSuggestionListener listener) {
		// ATL has no suggestions, nothing will call this.
		mOnSuggestListener = listener;
	}

	public void setOnSearchClickListener(OnClickListener listener) {
		mOnSearchClickListener = listener;
	}

	public void setQueryHint(CharSequence hint) {
		mQueryHint = hint;
		mSearchSrcTextView.setHint(hint);
	}

	public CharSequence getQueryHint() {
		if (mQueryHint != null) {
			return mQueryHint;
		}
		// AOSP falls back to the searchable configuration's hint and then to the
		// framework string "Search". ATL has no searchable configuration, so only
		// the framework string is left.
		return "Search";
	}

	/**
	 * @param query The new text for the search field.
	 * @param submit If true, also calls OnQueryTextListener.onQueryTextSubmit().
	 */
	public void setQuery(CharSequence query, boolean submit) {
		mSearchSrcTextView.setText(query == null ? "" : query);
		if (submit && query != null && mOnQueryChangeListener != null) {
			mOnQueryChangeListener.onQueryTextSubmit(query.toString());
		}
	}

	public CharSequence getQuery() {
		return mSearchSrcTextView.getText();
	}

	public void setSubmitButtonEnabled(boolean enabled) {
		mSubmitButtonEnabled = enabled;
		updateSubmitButtonVisibility();
	}

	public boolean isSubmitButtonEnabled() {
		return mSubmitButtonEnabled;
	}

	public void setImeOptions(int imeOptions) {
		mSearchSrcTextView.setImeOptions(imeOptions);
	}

	public int getImeOptions() {
		return mSearchSrcTextView.getImeOptions();
	}

	public void setMaxWidth(int pixels) {
		mSearchSrcTextView.setMaxWidth(pixels);
	}

	/**
	 * Give the search field focus. ATL's GTK entry shows the keyboard itself when
	 * the entry is focused, so there is no InputMethodManager call here.
	 */
	public void focusSearch() {
		mSearchSrcTextView.requestFocus();
	}

	/**
	 * Clear the search field, telling OnCloseListener first, then collapse it.
	 *
	 * @return True if the close was handled.
	 */
	public boolean onCloseClicked() {
		if (mOnCloseListener != null && mOnCloseListener.onClose()) {
			mSearchSrcTextView.setText("");
			return true;
		}
		CharSequence query = mSearchSrcTextView.getText();
		if (query == null || query.length() == 0) {
			setIconified(true);
		} else {
			mSearchSrcTextView.setText("");
		}
		return true;
	}

	@Override
	public void onActionViewExpanded() {
		mExpanded = true;
		mIconified = false;
		updateFocusedState();
		if (mOnSearchClickListener != null) {
			mOnSearchClickListener.onClick(this);
		}
	}

	@Override
	public void onActionViewCollapsed() {
		mExpanded = false;
		mIconified = true;
		setQuery(null, false);
		updateFocusedState();
	}

	@Override
	public void clearFocus() {
		mSearchSrcTextView.clearFocus();
	}

	@Override
	public boolean requestFocus(int direction, Rect previouslyFocusedRect) {
		return mSearchSrcTextView.requestFocus(direction, previouslyFocusedRect);
	}

	private void updateFocusedState() {
		// AOSP swaps the iconified and expanded child views here. ATL always draws
		// the edit field, so only the submit button follows the state.
		updateSubmitButtonVisibility();
	}

	private void updateSubmitButtonVisibility() {
		if (mGoBtn == null)
			return;
		mGoBtn.setVisibility(mSubmitButtonEnabled && mExpanded ? View.VISIBLE : View.GONE);
	}

	EditText getSearchSrcTextView() {
		return mSearchSrcTextView;
	}
}
