/*
 * Copyright (C) 2013 The Android Open Source Project
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

package android.provider;

import android.content.ClipDescription;
import android.content.ContentProvider;
import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Context;
import android.content.IntentSender;
import android.content.UriMatcher;
import android.content.pm.ProviderInfo;
import android.content.res.AssetFileDescriptor;
import android.database.Cursor;
import android.graphics.Point;
import android.net.Uri;
import android.os.Bundle;
import android.os.CancellationSignal;
import android.os.ParcelFileDescriptor;
import android.os.ParcelableException;
import android.provider.DocumentsContract.Document;
import android.provider.DocumentsContract.Path;
import android.util.Log;
import java.io.FileNotFoundException;
import java.util.LinkedList;
import java.util.List;
import java.util.Objects;

/**
 * Provides access to a document-based content provider.
 *
 * Port of the AOSP (android-14) DocumentsProvider against ATL's
 * ContentProvider surface. ATL adaptations, all because ATL has one
 * process per app and no cross-process URI-permission model (Context's
 * grant/revoke/check URI-permission methods are log stubs):
 * - The URI-permission enforcement in the call() dispatch
 *   (enforceRead/WritePermissionInner, getCallingAttributionSource, the
 *   MANAGE_DOCUMENTS enforcement of findDocumentPath) is not performed.
 * - canonicalize() is not overridden: it is built entirely on
 *   checkCallingOrSelfUriPermission, which ATL does not provide.
 * - ATL's ContentProvider has no Bundle/CancellationSignal query
 *   variants, so the URI dispatch that AOSP-14 does in
 *   query(Uri,String[],Bundle,CancellationSignal) happens here in the
 *   5-argument query() instead (the pre-Android-O transport, which
 *   dispatched the same way).
 * - Log.wtf does not exist in ATL; Log.e is used at those points.
 * - DocumentsContract.Root (the client-side root column contract) is
 *   still missing from ATL, so getType() inlines the
 *   Root.MIME_TYPE_ITEM value instead of referencing it.
 */
public abstract class DocumentsProvider extends ContentProvider {
	private static final String TAG = "DocumentsProvider";

	private static final int MATCH_ROOTS = 1;
	private static final int MATCH_ROOT = 2;
	private static final int MATCH_RECENT = 3;
	private static final int MATCH_SEARCH = 4;
	private static final int MATCH_DOCUMENT = 5;
	private static final int MATCH_CHILDREN = 6;
	private static final int MATCH_DOCUMENT_TREE = 7;
	private static final int MATCH_CHILDREN_TREE = 8;

	private String mAuthority;

	private UriMatcher mMatcher;

	/**
	 * Implementation is provided by the parent class.
	 */
	@Override
	public void attachInfo(Context context, ProviderInfo info) {
		registerAuthority(info.authority);

		// Validity check our setup
		if (!info.exported) {
			throw new SecurityException("Provider must be exported");
		}
		if (!info.grantUriPermissions) {
			throw new SecurityException("Provider must grantUriPermissions");
		}
		if (!android.Manifest.permission.MANAGE_DOCUMENTS.equals(info.readPermission)
				|| !android.Manifest.permission.MANAGE_DOCUMENTS.equals(info.writePermission)) {
			throw new SecurityException("Provider must be protected by MANAGE_DOCUMENTS");
		}

		super.attachInfo(context, info);
	}

	private void registerAuthority(String authority) {
		mAuthority = authority;

		mMatcher = new UriMatcher(UriMatcher.NO_MATCH);
		mMatcher.addURI(mAuthority, "root", MATCH_ROOTS);
		mMatcher.addURI(mAuthority, "root/*", MATCH_ROOT);
		mMatcher.addURI(mAuthority, "root/*/recent", MATCH_RECENT);
		mMatcher.addURI(mAuthority, "root/*/search", MATCH_SEARCH);
		mMatcher.addURI(mAuthority, "document/*", MATCH_DOCUMENT);
		mMatcher.addURI(mAuthority, "document/*/children", MATCH_CHILDREN);
		mMatcher.addURI(mAuthority, "tree/*/document/*", MATCH_DOCUMENT_TREE);
		mMatcher.addURI(mAuthority, "tree/*/document/*/children", MATCH_CHILDREN_TREE);
	}

	/**
	 * Test if a document is descendant (child, grandchild, etc) from the given
	 * parent. For example, providers must implement this to support
	 * {@link android.content.Intent#ACTION_OPEN_DOCUMENT_TREE}.
	 *
	 * @param parentDocumentId parent to verify against.
	 * @param documentId child to verify.
	 * @return if given document is a descendant of the given parent.
	 */
	public boolean isChildDocument(String parentDocumentId, String documentId) {
		return false;
	}

	private void enforceTreeForExtraUris(Bundle extras) {
		enforceTree((Uri) extras.getParcelable(DocumentsContract.EXTRA_URI));
		enforceTree((Uri) extras.getParcelable(DocumentsContract.EXTRA_PARENT_URI));
		enforceTree((Uri) extras.getParcelable(DocumentsContract.EXTRA_TARGET_URI));
	}

	private void enforceTree(Uri documentUri) {
		if (documentUri != null && DocumentsContract.isTreeUri(documentUri)) {
			final String parent = DocumentsContract.getTreeDocumentId(documentUri);
			final String child = DocumentsContract.getDocumentId(documentUri);
			if (Objects.equals(parent, child)) {
				return;
			}
			if (!isChildDocument(parent, child)) {
				throw new SecurityException(
						"Document " + child + " is not a descendant of " + parent);
			}
		}
	}

	/**
	 * Create a new document and return its newly generated
	 * {@link Document#COLUMN_DOCUMENT_ID}.
	 */
	public String createDocument(String parentDocumentId, String mimeType, String displayName)
			throws FileNotFoundException {
		throw new UnsupportedOperationException("Create not supported");
	}

	/**
	 * Rename an existing document.
	 */
	public String renameDocument(String documentId, String displayName)
			throws FileNotFoundException {
		throw new UnsupportedOperationException("Rename not supported");
	}

	/**
	 * Delete the requested document.
	 */
	public void deleteDocument(String documentId) throws FileNotFoundException {
		throw new UnsupportedOperationException("Delete not supported");
	}

	/**
	 * Copy the requested document or a document tree.
	 */
	public String copyDocument(String sourceDocumentId, String targetParentDocumentId)
			throws FileNotFoundException {
		throw new UnsupportedOperationException("Copy not supported");
	}

	/**
	 * Move the requested document or a document tree.
	 */
	public String moveDocument(String sourceDocumentId, String sourceParentDocumentId,
			String targetParentDocumentId)
			throws FileNotFoundException {
		throw new UnsupportedOperationException("Move not supported");
	}

	/**
	 * Removes the requested document or a document tree.
	 */
	public void removeDocument(String documentId, String parentDocumentId)
			throws FileNotFoundException {
		throw new UnsupportedOperationException("Remove not supported");
	}

	/**
	 * Finds the canonical path for the requested document.
	 */
	public Path findDocumentPath(String parentDocumentId, String childDocumentId)
			throws FileNotFoundException {
		throw new UnsupportedOperationException("findDocumentPath not supported.");
	}

	/**
	 * Creates an intent sender for a web link, if the document is web linkable.
	 */
	public IntentSender createWebLinkIntent(String documentId, Bundle options)
			throws FileNotFoundException {
		throw new UnsupportedOperationException("createWebLink is not supported.");
	}

	/**
	 * Return all roots currently provided.
	 */
	public abstract Cursor queryRoots(String[] projection) throws FileNotFoundException;

	/**
	 * Return recently modified documents under the requested root.
	 */
	public Cursor queryRecentDocuments(String rootId, String[] projection)
			throws FileNotFoundException {
		throw new UnsupportedOperationException("Recent not supported");
	}

	/**
	 * Return metadata for the single requested document.
	 */
	public abstract Cursor queryDocument(String documentId, String[] projection)
			throws FileNotFoundException;

	/**
	 * Return the children documents contained in the requested directory.
	 */
	public abstract Cursor queryChildDocuments(
			String parentDocumentId, String[] projection, String sortOrder)
			throws FileNotFoundException;

	/**
	 * Return documents that match the given query under the requested root.
	 */
	public Cursor querySearchDocuments(String rootId, String query, String[] projection)
			throws FileNotFoundException {
		throw new UnsupportedOperationException("Search not supported");
	}

	/**
	 * Eject the mounted root (API 33).
	 */
	public void ejectRoot(String rootId) {
		throw new UnsupportedOperationException("Eject not supported");
	}

	/**
	 * Returns metadata associated with the document (API 30).
	 */
	public Bundle getDocumentMetadata(String documentId) throws FileNotFoundException {
		throw new UnsupportedOperationException("Metadata not supported");
	}

	/**
	 * Return concrete MIME type of the requested document. The default
	 * implementation queries {@link #queryDocument(String, String[])}, so
	 * providers may choose to override this as an optimization.
	 */
	public String getDocumentType(String documentId) throws FileNotFoundException {
		final Cursor cursor = queryDocument(documentId, null);
		try {
			if (cursor.moveToFirst()) {
				return cursor.getString(cursor.getColumnIndexOrThrow(Document.COLUMN_MIME_TYPE));
			} else {
				return null;
			}
		} finally {
			if (cursor != null) {
				cursor.close();
			}
		}
	}

	/**
	 * Open and return the requested document.
	 */
	public abstract ParcelFileDescriptor openDocument(
			String documentId, String mode, CancellationSignal signal)
			throws FileNotFoundException;

	/**
	 * Open and return a thumbnail of the requested document.
	 */
	public AssetFileDescriptor openDocumentThumbnail(
			String documentId, Point sizeHint, CancellationSignal signal)
			throws FileNotFoundException {
		throw new UnsupportedOperationException("Thumbnails not supported");
	}

	/**
	 * Open and return the document in a format matching the specified MIME
	 * type filter.
	 */
	public AssetFileDescriptor openTypedDocument(
			String documentId, String mimeTypeFilter, Bundle opts, CancellationSignal signal)
			throws FileNotFoundException {
		throw new FileNotFoundException("The requested MIME type is not supported.");
	}

	/**
	 * Dispatch a document URI to the concrete document methods. AOSP-14 does
	 * this dispatch in its Bundle/CancellationSignal query variant; ATL's
	 * ContentProvider only has the 5-argument query, so the dispatch happens
	 * here (the pre-Android-O transport performed the same match).
	 */
	@Override
	public final Cursor query(Uri uri, String[] projection, String selection,
			String[] selectionArgs, String sortOrder) {
		if (selection != null || selectionArgs != null) {
			// AOSP-14 rejects the legacy selection form outright; documents
			// are addressed by URI, not by SQL selection.
			Log.w(TAG, "Ignoring selection on documents URI " + uri);
		}
		try {
			switch (mMatcher.match(uri)) {
				case MATCH_ROOTS:
					return queryRoots(projection);
				case MATCH_RECENT:
					return queryRecentDocuments(getRootId(uri), projection);
				case MATCH_SEARCH:
					return querySearchDocuments(
							getRootId(uri), uri.getQueryParameter("query"), projection);
				case MATCH_DOCUMENT:
				case MATCH_DOCUMENT_TREE:
					enforceTree(uri);
					return queryDocument(getDocumentId(uri), projection);
				case MATCH_CHILDREN:
				case MATCH_CHILDREN_TREE:
					enforceTree(uri);
					return queryChildDocuments(getDocumentId(uri), projection, sortOrder);
				default:
					throw new UnsupportedOperationException("Unsupported Uri " + uri);
			}
		} catch (FileNotFoundException e) {
			Log.w(TAG, "Failed during query", e);
			return null;
		}
	}

	/**
	 * Implementation is provided by the parent class. Cannot be overridden.
	 *
	 * @see #getDocumentType(String)
	 */
	@Override
	public final String getType(Uri uri) {
		try {
			switch (mMatcher.match(uri)) {
				case MATCH_ROOT:
					// DocumentsContract.Root does not exist in ATL yet;
					// Root.MIME_TYPE_ITEM inlined (AOSP value).
					return "vnd.android.document/item";
				case MATCH_DOCUMENT:
				case MATCH_DOCUMENT_TREE:
					enforceTree(uri);
					return getDocumentType(getDocumentId(uri));
				default:
					return null;
			}
		} catch (FileNotFoundException e) {
			Log.w(TAG, "Failed during getType", e);
			return null;
		}
	}

	/**
	 * Implementation is provided by the parent class. Throws by default, and
	 * cannot be overridden.
	 *
	 * @see #createDocument(String, String, String)
	 */
	@Override
	public final Uri insert(Uri uri, ContentValues values) {
		throw new UnsupportedOperationException("Insert not supported");
	}

	/**
	 * Implementation is provided by the parent class. Throws by default, and
	 * cannot be overridden.
	 *
	 * @see #deleteDocument(String)
	 */
	@Override
	public final int delete(Uri uri, String selection, String[] selectionArgs) {
		throw new UnsupportedOperationException("Delete not supported");
	}

	/**
	 * Implementation is provided by the parent class. Throws by default, and
	 * cannot be overridden.
	 */
	@Override
	public final int update(
			Uri uri, ContentValues values, String selection, String[] selectionArgs) {
		throw new UnsupportedOperationException("Update not supported");
	}

	/**
	 * Implementation is provided by the parent class.
	 *
	 * The per-method URI-permission enforcement of AOSP is skipped: ATL has
	 * one process per app and no URI-permission grants to enforce.
	 */
	@Override
	public Bundle call(String method, String arg, Bundle extras) {
		if (!method.startsWith("android:")) {
			// Ignore non-platform methods
			return super.call(method, arg, extras);
		}

		try {
			return callUnchecked(method, arg, extras);
		} catch (FileNotFoundException e) {
			throw new ParcelableException(e);
		}
	}

	private Bundle callUnchecked(String method, String arg, Bundle extras)
			throws FileNotFoundException {

		final Bundle out = new Bundle();

		// If the URI is a tree URI performs some validation.
		enforceTreeForExtraUris(extras);

		final Uri extraUri = (Uri) extras.getParcelable(DocumentsContract.EXTRA_URI);

		if (DocumentsContract.METHOD_IS_CHILD_DOCUMENT.equals(method)) {
			final Uri documentUri = extraUri;
			final String authority = documentUri.getAuthority();
			final String documentId = DocumentsContract.getDocumentId(documentUri);
			checkAuthority(authority);

			final Uri childUri = (Uri) extras.getParcelable(DocumentsContract.EXTRA_TARGET_URI);
			final String childAuthority = childUri.getAuthority();
			final String childId = DocumentsContract.getDocumentId(childUri);

			out.putBoolean(
					DocumentsContract.EXTRA_RESULT,
					mAuthority.equals(childAuthority)
							&& isChildDocument(documentId, childId));

		} else if (DocumentsContract.METHOD_CREATE_DOCUMENT.equals(method)) {
			final Uri documentUri = extraUri;
			checkAuthority(documentUri.getAuthority());
			final String documentId = DocumentsContract.getDocumentId(documentUri);

			final String mimeType = extras.getString(Document.COLUMN_MIME_TYPE);
			final String displayName = extras.getString(Document.COLUMN_DISPLAY_NAME);
			final String newDocumentId = createDocument(documentId, mimeType, displayName);

			final Uri newDocumentUri = buildDocumentUriMaybeUsingTree(documentUri,
					newDocumentId);
			out.putParcelable(DocumentsContract.EXTRA_URI, newDocumentUri);

		} else if (DocumentsContract.METHOD_RENAME_DOCUMENT.equals(method)) {
			final Uri documentUri = extraUri;
			checkAuthority(documentUri.getAuthority());
			final String documentId = DocumentsContract.getDocumentId(documentUri);

			final String displayName = extras.getString(Document.COLUMN_DISPLAY_NAME);
			final String newDocumentId = renameDocument(documentId, displayName);

			if (newDocumentId != null) {
				final Uri newDocumentUri = buildDocumentUriMaybeUsingTree(documentUri,
						newDocumentId);
				out.putParcelable(DocumentsContract.EXTRA_URI, newDocumentUri);

				// Original document no longer exists, clean up any grants.
				revokeDocumentPermission(documentId);
			}

		} else if (DocumentsContract.METHOD_DELETE_DOCUMENT.equals(method)) {
			final Uri documentUri = extraUri;
			checkAuthority(documentUri.getAuthority());
			final String documentId = DocumentsContract.getDocumentId(documentUri);

			deleteDocument(documentId);

			// Document no longer exists, clean up any grants.
			revokeDocumentPermission(documentId);

		} else if (DocumentsContract.METHOD_COPY_DOCUMENT.equals(method)) {
			final Uri documentUri = extraUri;
			checkAuthority(documentUri.getAuthority());
			final String documentId = DocumentsContract.getDocumentId(documentUri);

			final Uri targetUri = (Uri) extras.getParcelable(DocumentsContract.EXTRA_TARGET_URI);
			final String targetId = DocumentsContract.getDocumentId(targetUri);

			final String newDocumentId = copyDocument(documentId, targetId);

			if (newDocumentId != null) {
				final Uri newDocumentUri = buildDocumentUriMaybeUsingTree(documentUri,
						newDocumentId);
				out.putParcelable(DocumentsContract.EXTRA_URI, newDocumentUri);
			}

		} else if (DocumentsContract.METHOD_MOVE_DOCUMENT.equals(method)) {
			final Uri documentUri = extraUri;
			checkAuthority(documentUri.getAuthority());
			final String documentId = DocumentsContract.getDocumentId(documentUri);

			final Uri parentSourceUri =
					(Uri) extras.getParcelable(DocumentsContract.EXTRA_PARENT_URI);
			final String parentSourceId = DocumentsContract.getDocumentId(parentSourceUri);
			final Uri targetUri = (Uri) extras.getParcelable(DocumentsContract.EXTRA_TARGET_URI);
			final String targetId = DocumentsContract.getDocumentId(targetUri);

			final String newDocumentId = moveDocument(documentId, parentSourceId, targetId);

			if (newDocumentId != null) {
				final Uri newDocumentUri = buildDocumentUriMaybeUsingTree(documentUri,
						newDocumentId);
				out.putParcelable(DocumentsContract.EXTRA_URI, newDocumentUri);
			}

		} else if (DocumentsContract.METHOD_REMOVE_DOCUMENT.equals(method)) {
			final Uri documentUri = extraUri;
			checkAuthority(documentUri.getAuthority());
			final String documentId = DocumentsContract.getDocumentId(documentUri);

			final Uri parentSourceUri =
					(Uri) extras.getParcelable(DocumentsContract.EXTRA_PARENT_URI);
			final String parentSourceId = DocumentsContract.getDocumentId(parentSourceUri);

			removeDocument(documentId, parentSourceId);

			// It's the responsibility of the provider to revoke any grants, as
			// the document may be still attached to another parent.
		} else if (DocumentsContract.METHOD_FIND_DOCUMENT_PATH.equals(method)) {
			final Uri documentUri = extraUri;
			checkAuthority(documentUri.getAuthority());
			final boolean isTreeUri = DocumentsContract.isTreeUri(documentUri);

			// AOSP enforces MANAGE_DOCUMENTS for non-tree URIs here; ATL has
			// no cross-process permission enforcement to call.
			final String documentId = DocumentsContract.getDocumentId(documentUri);
			final String parentDocumentId = isTreeUri
					? DocumentsContract.getTreeDocumentId(documentUri)
					: null;

			Path path = findDocumentPath(parentDocumentId, documentId);

			// Ensure provider doesn't leak information to unprivileged callers.
			if (isTreeUri) {
				if (!Objects.equals(path.getPath().get(0), parentDocumentId)) {
					Log.e(TAG, "Provider doesn't return path from the tree root. Expected: "
							+ parentDocumentId + " found: " + path.getPath().get(0));

					LinkedList<String> docs = new LinkedList<>(path.getPath());
					while (docs.size() > 1 && !Objects.equals(docs.getFirst(), parentDocumentId)) {
						docs.removeFirst();
					}
					path = new Path(null, docs);
				}

				if (path.getRootId() != null) {
					Log.e(TAG, "Provider returns root id :"
							+ path.getRootId() + " unexpectedly. Erase root id.");
					path = new Path(null, path.getPath());
				}
			}

			out.putParcelable(DocumentsContract.EXTRA_RESULT, path);
		} else if (DocumentsContract.METHOD_GET_DOCUMENT_METADATA.equals(method)) {
			final Uri documentUri = extraUri;
			checkAuthority(documentUri.getAuthority());
			return getDocumentMetadata(DocumentsContract.getDocumentId(documentUri));
		} else {
			throw new UnsupportedOperationException("Method not supported " + method);
		}

		return out;
	}

	private void checkAuthority(String authority) {
		if (!mAuthority.equals(authority)) {
			throw new SecurityException(
					"Requested authority " + authority + " doesn't match provider " + mAuthority);
		}
	}

	/**
	 * Revoke any active permission grants for the given
	 * {@link Document#COLUMN_DOCUMENT_ID}.
	 */
	public final void revokeDocumentPermission(String documentId) {
		final Context context = getContext();
		context.revokeUriPermission(DocumentsContract.buildDocumentUri(mAuthority, documentId), ~0);
		context.revokeUriPermission(
				DocumentsContract.buildTreeDocumentUri(mAuthority, documentId), ~0);
	}

	/**
	 * Implementation is provided by the parent class. Cannot be overridden.
	 *
	 * @see #openDocument(String, String, CancellationSignal)
	 */
	@Override
	public final ParcelFileDescriptor openFile(Uri uri, String mode) throws FileNotFoundException {
		enforceTree(uri);
		return openDocument(getDocumentId(uri), mode, null);
	}

	/**
	 * Implementation is provided by the parent class. Cannot be overridden.
	 *
	 * @see #openDocument(String, String, CancellationSignal)
	 */
	@Override
	@SuppressWarnings("resource")
	public final AssetFileDescriptor openAssetFile(Uri uri, String mode)
			throws FileNotFoundException {
		enforceTree(uri);
		final ParcelFileDescriptor fd = openDocument(getDocumentId(uri), mode, null);
		return fd != null ? new AssetFileDescriptor(fd, 0, -1) : null;
	}

	/**
	 * Implementation is provided by the parent class. Cannot be overridden.
	 *
	 * @see #openDocumentThumbnail(String, Point, CancellationSignal)
	 * @see #openTypedDocument(String, String, Bundle, CancellationSignal)
	 * @see #getDocumentStreamTypes(String, String)
	 */
	@Override
	public final AssetFileDescriptor openTypedAssetFile(Uri uri, String mimeTypeFilter, Bundle opts)
			throws FileNotFoundException {
		return openTypedAssetFileImpl(uri, mimeTypeFilter, opts, null);
	}

	/**
	 * Return a list of streamable MIME types matching the filter, which can be
	 * passed to
	 * {@link #openTypedDocument(String, String, Bundle, CancellationSignal)}.
	 *
	 * AOSP uses {@code MimeTypeFilter.matches}; ATL has no MimeTypeFilter, so
	 * the wildcard match of {@link ClipDescription#compareMimeTypes} is used.
	 */
	public String[] getDocumentStreamTypes(String documentId, String mimeTypeFilter) {
		Cursor cursor = null;
		try {
			cursor = queryDocument(documentId, null);
			if (cursor.moveToFirst()) {
				final String mimeType =
						cursor.getString(cursor.getColumnIndexOrThrow(Document.COLUMN_MIME_TYPE));
				final long flags =
						cursor.getLong(cursor.getColumnIndexOrThrow(Document.COLUMN_FLAGS));
				if ((flags & Document.FLAG_VIRTUAL_DOCUMENT) == 0 && mimeType != null
						&& ClipDescription.compareMimeTypes(mimeType, mimeTypeFilter)) {
					return new String[] { mimeType };
				}
			}
		} catch (FileNotFoundException e) {
			return null;
		} finally {
			if (cursor != null) {
				cursor.close();
			}
		}

		// No streamable MIME types.
		return null;
	}

	/**
	 * Called by a client to determine the types of data streams that this
	 * content provider supports for the given URI.
	 */
	@Override
	public String[] getStreamTypes(Uri uri, String mimeTypeFilter) {
		enforceTree(uri);
		return getDocumentStreamTypes(getDocumentId(uri), mimeTypeFilter);
	}

	private final AssetFileDescriptor openTypedAssetFileImpl(
			Uri uri, String mimeTypeFilter, Bundle opts, CancellationSignal signal)
			throws FileNotFoundException {
		enforceTree(uri);
		final String documentId = getDocumentId(uri);
		if (opts != null && opts.containsKey(ContentResolver.EXTRA_SIZE)) {
			final Point sizeHint = (Point) opts.getParcelable(ContentResolver.EXTRA_SIZE);
			return openDocumentThumbnail(documentId, sizeHint, signal);
		}
		if ("*/*".equals(mimeTypeFilter)) {
			// If they can take anything, the untyped open call is good enough.
			return openAssetFile(uri, "r");
		}
		final String baseType = getType(uri);
		if (baseType != null && ClipDescription.compareMimeTypes(baseType, mimeTypeFilter)) {
			// Use old untyped open call if this provider has a type for this
			// URI and it matches the request.
			return openAssetFile(uri, "r");
		}
		// For any other yet unhandled case, let the provider subclass handle it.
		return openTypedDocument(documentId, mimeTypeFilter, opts, signal);
	}

	/**
	 * Mirror of DocumentsContract.getRootId (not in ATL's DocumentsContract
	 * yet): the root id is the first wildcard segment of root/* URIs.
	 */
	private static String getRootId(Uri rootUri) {
		final List<String> segments = rootUri.getPathSegments();
		if (segments.size() < 2 || !"root".equals(segments.get(0))) {
			throw new IllegalArgumentException("Not a root URI: " + rootUri);
		}
		return segments.get(1);
	}

	private static String getDocumentId(Uri documentUri) {
		return DocumentsContract.getDocumentId(documentUri);
	}

	/**
	 * Mirror of DocumentsContract.buildDocumentUriMaybeUsingTree (not in ATL's
	 * DocumentsContract yet).
	 */
	private static Uri buildDocumentUriMaybeUsingTree(Uri treeUri, String documentId) {
		if (DocumentsContract.isTreeUri(treeUri)) {
			return DocumentsContract.buildTreeDocumentUri(treeUri.getAuthority(), documentId);
		} else {
			return DocumentsContract.buildDocumentUri(treeUri.getAuthority(), documentId);
		}
	}
}
