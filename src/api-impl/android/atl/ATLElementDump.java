/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 *
 * Gated view-tree dump for the harness's element-addressed interaction
 * scripts (docs/agent-tasks/element-addressed-interaction.md, P1/M-view).
 *
 * Off unless the environment variable ATL_ELEMENT_DUMP names a directory:
 * apps that don't set it never start a thread and nothing changes, so gate
 * runs stay comparable. When on, every Window's decor view is registered
 * (see android.view.Window's constructor) and a daemon thread writes
 * <dir>/views.json about once a second: a flat list of every attached
 * view's class, text, content description, clickable/enabled flags and
 * bounds in window-local pixels. Touching <dir>/request only makes the
 * next dump happen sooner; the harness polls views.json.
 *
 * The tree is walked on the main thread (posted to the main looper, which
 * ATL pumps from the GLib loop, see api-impl-jni/android_os_MessageQueue.c)
 * so bounds and child lists are never read mid-layout. dx constraints: no
 * lambdas, anonymous classes only.
 */
package android.atl;

import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import java.io.File;
import java.io.FileOutputStream;
import java.lang.ref.WeakReference;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

public final class ATLElementDump {
	private static final List<WeakReference<View>> sRoots = new ArrayList<>();
	private static Thread sWorker;

	/** Register a window's decor view; starts the dump thread on first call. */
	public static void register(View decor) {
		String dir = System.getenv("ATL_ELEMENT_DUMP");
		if (dir == null || dir.isEmpty() || decor == null)
			return;
		synchronized (sRoots) {
			sRoots.add(new WeakReference<View>(decor));
			if (sWorker == null) {
				sWorker = new Thread(new Runnable() {
					public void run() {
						work(dir);
					}
				}, "ATLElementDump");
				sWorker.setDaemon(true);
				sWorker.start();
			}
		}
	}

	private static void work(String dir) {
		File out = new File(dir, "views.json");
		File req = new File(dir, "request");
		long lastReq = 0;
		while (!Thread.currentThread().isInterrupted()) {
			long nowReq = req.lastModified();
			dumpOnce(dir, out);
			if (nowReq != lastReq)
				lastReq = nowReq; // request consumed; next loop may go quicker
			try {
				Thread.sleep(nowReq == 0 ? 1000 : 400);
			} catch (InterruptedException e) {
				return;
			}
		}
	}

	private static void dumpOnce(String dir, File out) {
		final CountDownLatch latch = new CountDownLatch(1);
		final String[] json = new String[1];
		try {
			new Handler(Looper.getMainLooper()).post(new Runnable() {
				public void run() {
					try {
						json[0] = build();
					} catch (Throwable t) {
						json[0] = null; // never kill the app for a dump
					}
					latch.countDown();
				}
			});
			if (!latch.await(4, TimeUnit.SECONDS))
				return; // main thread busy; keep the previous dump
		} catch (Throwable t) {
			return;
		}
		if (json[0] == null)
			return;
		try {
			File tmp = new File(dir, "views.json.tmp");
			FileOutputStream fos = new FileOutputStream(tmp);
			try {
				fos.write(json[0].getBytes(StandardCharsets.UTF_8));
			} finally {
				fos.close();
			}
			if (out.exists() && !out.delete())
				return;
			if (!tmp.renameTo(out))
				tmp.delete();
		} catch (Exception e) {
			// dir vanished or full; the next loop retries
		}
	}

	private static String build() {
		List<WeakReference<View>> snapshot;
		synchronized (sRoots) {
			snapshot = new ArrayList<WeakReference<View>>(sRoots);
			for (int i = sRoots.size() - 1; i >= 0; i--)
				if (sRoots.get(i).get() == null)
					sRoots.remove(i);
		}
		StringBuilder sb = new StringBuilder(4096);
		sb.append("{\"v\":1,\"nodes\":[");
		int[] n = new int[] { 0 };
		for (WeakReference<View> wr : snapshot) {
			View root = wr.get();
			if (root == null || !root.isAttachedToWindow())
				continue;
			append(sb, root, n);
		}
		sb.append("]}");
		return sb.toString();
	}

	private static void append(StringBuilder sb, View v, int[] n) {
		if (n[0]++ > 0)
			sb.append(',');
		sb.append("{\"c\":\"").append(esc(v.getClass().getName())).append('"');
		int[] loc = new int[2];
		v.getLocationOnScreen(loc);
		sb.append(",\"l\":").append(loc[0]).append(",\"t\":").append(loc[1])
			.append(",\"r\":").append(loc[0] + v.getWidth())
			.append(",\"b\":").append(loc[1] + v.getHeight());
		if (!v.isEnabled())
			sb.append(",\"en\":false");
		if (v.isClickable())
			sb.append(",\"clk\":true");
		CharSequence text = null;
		if (v instanceof TextView) {
			try {
				text = ((TextView) v).getText();
			} catch (Throwable t) {
				text = null;
			}
		}
		if (text != null && text.length() > 0)
			sb.append(",\"txt\":\"").append(esc(text.toString())).append('"');
		CharSequence desc = null;
		try {
			desc = v.getContentDescription();
		} catch (Throwable t) {
			desc = null;
		}
		if (desc != null && desc.length() > 0)
			sb.append(",\"desc\":\"").append(esc(desc.toString())).append('"');
		sb.append('}');
		if (v instanceof ViewGroup) {
			ViewGroup g = (ViewGroup) v;
			int count = g.getChildCount();
			for (int i = 0; i < count; i++) {
				View child = g.getChildAt(i);
				if (child != null)
					append(sb, child, n);
			}
		}
	}

	private static String esc(String s) {
		StringBuilder out = new StringBuilder(s.length() + 8);
		for (int i = 0; i < s.length(); i++) {
			char c = s.charAt(i);
			switch (c) {
			case '"':
				out.append("\\\"");
				break;
			case '\\':
				out.append("\\\\");
				break;
			case '\n':
				out.append("\\n");
				break;
			case '\r':
				out.append("\\r");
			break;
			case '\t':
				out.append("\\t");
				break;
			default:
				if (c < 0x20)
					out.append(String.format("\\u%04x", (int) c));
				else
					out.append(c);
			}
		}
		return out.toString();
	}
}
