package android.content;

import android.net.Uri;
import android.util.Printer;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;

public class IntentFilter {

	private List<String> actions = new ArrayList<>();
	private Set<String> categories = new HashSet<>();
	private List<String> dataSchemes = new ArrayList<>();

	public IntentFilter() {}
	public IntentFilter(String action) {
		addAction(action);
	}

	public void addAction(String action) {
		actions.add(action);
	}
	public int countActions() {
		return actions.size();
	}

	public final boolean matchAction(String action) {
		return actions.contains(action);
	}

	public void addCategory(String category) {
		categories.add(category);
	}
	public int countCategories() {
		return categories.size();
	}

	public final boolean hasCategory(String category) {
		return categories.contains(category);
	}

	public String getAction(int index) {
		return actions.get(index);
	}

	public void setPriority(int priority) {}

	public void addDataScheme(String dataScheme) {
		dataSchemes.add(dataScheme);
	}

	public void addDataAuthority(String host, String port) {}

	public void addDataPath(String path, int type) {}

	public final void addDataSchemeSpecificPart(String ssp, int type) {
		/* FIXME */
	}

	public boolean hasDataScheme(String dataScheme) {
		return dataSchemes.contains(dataScheme);
	}

	public int countDataSchemes() {
		return dataSchemes.size();
	}

	public String getDataScheme(int index) {
		return dataSchemes.get(index);
	}

	public final Iterator<String> actionsIterator() {
		return actions.iterator();
	}

	public boolean hasAction(String action) {
		return actions.contains(action);
	}

	public int match(String action, String type, String scheme, Uri data, Set<String> categories, String logTag) {
		int ret = 0;
		if (!matchAction(action)) {
			ret = -3 /*NO_MATCH_ACTION*/;
		}
		if (scheme == null) {
			ret = 0x00100000 /*MATCH_CATEGORY_EMPTY*/ | 0x00008000 /*MATCH_ADJUSTMENT_NORMAL*/;
		} else if (hasDataScheme(scheme)) {
			ret = 0x00200000 /*MATCH_CATEGORY_SCHEME*/ | 0x00008000 /*MATCH_ADJUSTMENT_NORMAL*/;
		} else {
			ret = -2 /*NO_MATCH_DATA*/;
		}
		return ret;
	}

	public final Iterator<String> schemesIterator() {
		return dataSchemes.iterator();
	}

	public final Iterator authoritiesIterator() {
		return null;
	}

	public void dump(Printer pw, String prefix) {
		// AOSP IntentFilter has no dump() at all; davdroid's debug path calls
		// invoke-virtual IntentFilter.dump(Printer,String)V (the void
		// Printer-style dump, as on BroadcastReceiver/Service). Print the
		// filter contents so the dump output is real, with the descriptor the
		// call sites resolve to.
		pw.println(prefix + "Intent Filter:");
		for (int i = 0; i < actions.size(); i++) {
			pw.println(prefix + "  action=" + actions.get(i));
		}
		for (String category : categories) {
			pw.println(prefix + "  category=" + category);
		}
	}
}
