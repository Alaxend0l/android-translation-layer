package android.webkit;

import android.graphics.Bitmap;

public class WebViewClient {
	/* AOSP signatures (the 2-arg onPageStarted this used to have is not
	 * an AOSP callback: apps override the 3-arg one and never got
	 * called). favicon is null while ATL's WebKit glue has no favicon
	 * plumbing - Android also passes null until an icon is known. */
	public void onPageStarted(WebView view, String url, Bitmap favicon) {}

	public void onPageFinished(WebView view, String url) {}

	/* AOSP deprecated String form, kept (apps still override it). */
	public boolean shouldOverrideUrlLoading(WebView view, String url) {
		return false; // continue loading the URL
	}

	/* AOSP API 24 form; the AOSP default delegates to the deprecated
	 * String version so existing overrides keep working. */
	public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
		return shouldOverrideUrlLoading(view, request.getUrl().toString());
	}
}
