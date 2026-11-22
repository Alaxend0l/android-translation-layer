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

import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ResolveInfo;
import android.net.Uri;
import java.util.List;

public final class DocumentsContract {
	public static final String EXTRA_LOADING = "loading";
	public static final String EXTRA_INFO = "info";
	public static final String EXTRA_ERROR = "error";
	public static final String EXTRA_RESULT = "result";
	public static final String METHOD_CREATE_DOCUMENT = "android:createDocument";
	public static final String METHOD_RENAME_DOCUMENT = "android:renameDocument";
	public static final String METHOD_DELETE_DOCUMENT = "android:deleteDocument";
	public static final String METHOD_COPY_DOCUMENT = "android:copyDocument";
	public static final String METHOD_MOVE_DOCUMENT = "android:moveDocument";
	public static final String METHOD_IS_CHILD_DOCUMENT = "android:isChildDocument";
	public static final String METHOD_REMOVE_DOCUMENT = "android:removeDocument";
	public static final String METHOD_EJECT_ROOT = "android:ejectRoot";
	public static final String METHOD_FIND_DOCUMENT_PATH = "android:findDocumentPath";
	public static final String METHOD_CREATE_WEB_LINK_INTENT = "android:createWebLinkIntent";
	public static final String METHOD_GET_DOCUMENT_METADATA = "android:getDocumentMetadata";
	public static final String EXTRA_PARENT_URI = "parentUri";
	public static final String EXTRA_TARGET_URI = "targetUri";
	public static final String EXTRA_URI = "uri";
	public static final String EXTRA_URI_PERMISSIONS = "uriPermissions";
	public static final String EXTRA_OPTIONS = "options";
	/* seems these are not supposed to be ABI but some apps disagree */
	private static final String PATH_ROOT = "root";
	private static final String PATH_RECENT = "recent";
	private static final String PATH_DOCUMENT = "document";
	private static final String PATH_CHILDREN = "children";
	private static final String PATH_SEARCH = "search";
	private static final String PATH_TREE = "tree";
	private static final String PARAM_QUERY = "query";
	private static final String PARAM_MANAGE = "manage";

	public static String getDocumentId(Uri documentUri) {
		final List<String> paths = documentUri.getPathSegments();
		if (paths.size() >= 2 && PATH_DOCUMENT.equals(paths.get(0))) {
			return paths.get(1);
		}
		if (paths.size() >= 4 && PATH_TREE.equals(paths.get(0))
		    && PATH_DOCUMENT.equals(paths.get(2))) {
			return paths.get(3);
		}
		throw new IllegalArgumentException("Invalid URI: " + documentUri);
	}

	public static String getTreeDocumentId(Uri documentUri) {
		final List<String> paths = documentUri.getPathSegments();
		if (paths.size() >= 2 && PATH_TREE.equals(paths.get(0))) {
			return paths.get(1);
		}
		throw new IllegalArgumentException("Invalid URI: " + documentUri);
	}

	public static boolean isContentUri(Uri uri) {
		return uri != null && ContentResolver.SCHEME_CONTENT.equals(uri.getScheme());
	}

	private static boolean isDocumentsProvider(Context context, String authority) {
		return false;
	}

	public static boolean isDocumentUri(Context context, Uri uri) {
		if (isContentUri(uri) && isDocumentsProvider(context, uri.getAuthority())) {
			final List<String> paths = uri.getPathSegments();
			if (paths.size() == 2) {
				return PATH_DOCUMENT.equals(paths.get(0));
			} else if (paths.size() == 4) {
				return PATH_TREE.equals(paths.get(0)) && PATH_DOCUMENT.equals(paths.get(2));
			}
		}
		return false;
	}

	public static boolean isTreeUri(Uri uri) {
		final List<String> paths = uri.getPathSegments();
		return (paths.size() >= 2 && PATH_TREE.equals(paths.get(0)));
	}

	public static Uri buildChildDocumentsUriUsingTree(Uri treeUri, String parentDocumentId) {
		return new Uri.Builder()
		    .scheme(ContentResolver.SCHEME_CONTENT)
		    .authority(treeUri.getAuthority())
		    .appendPath(PATH_TREE)
		    .appendPath(getTreeDocumentId(treeUri))
		    .appendPath(PATH_DOCUMENT)
		    .appendPath(parentDocumentId)
		    .appendPath(PATH_CHILDREN)
		    .build();
	}

	public static Uri buildDocumentUriUsingTree(Uri treeUri, String documentId) {
		return new Uri.Builder()
		    .scheme(ContentResolver.SCHEME_CONTENT)
		    .authority(treeUri.getAuthority())
		    .appendPath(PATH_TREE)
		    .appendPath(getTreeDocumentId(treeUri))
		    .appendPath(PATH_DOCUMENT)
		    .appendPath(documentId)
		    .build();
	}

	public static Uri copyDocument(ContentResolver content, Uri sourceDocumentUri, Uri targetParentDocumentUri) {
		throw new RuntimeException("DocumentsContract.copyDocument not implemented yet");
	}

	public static Uri createDocument(ContentResolver content, Uri parentDocumentUri, String mimeType, String displayName) {
		throw new RuntimeException("DocumentsContract.createDocument not implemented yet");
	}

	public static boolean deleteDocument(ContentResolver content, Uri documentUri) {
		throw new RuntimeException("DocumentsContract.deleteDocument not implemented yet");
	}

	public static Uri renameDocument(ContentResolver content, Uri documentUri, String displayName) {
		throw new RuntimeException("DocumentsContract.renameDocument not implemented yet");
	}

	public static boolean isChildDocument(ContentResolver content, Uri parentDocumentUri, Uri childDocumentUri) {
		throw new RuntimeException("DocumentsContract.isChildDocument not implemented yet");
	}

	public static Uri buildDocumentUri(String authority, String documentId) {
		return new Uri.Builder().scheme(ContentResolver.SCHEME_CONTENT).authority(authority).appendPath(PATH_DOCUMENT).appendPath(documentId).build();
	}

	public static Uri buildTreeDocumentUri(String authority, String documentId) {
		// AOSP: documentId may itself contain slashes; the tree id is then
		// everything up to the last one.
		final String treeDocumentId;
		if (documentId.contains("/")) {
			treeDocumentId = documentId.substring(0, documentId.lastIndexOf('/'));
			documentId = documentId.substring(documentId.lastIndexOf('/') + 1);
		} else {
			treeDocumentId = documentId;
		}
		return new Uri.Builder()
		    .scheme(ContentResolver.SCHEME_CONTENT)
		    .authority(authority)
		    .appendPath(PATH_TREE)
		    .appendPath(treeDocumentId)
		    .appendPath(PATH_DOCUMENT)
		    .appendPath(documentId)
		    .build();
	}

	/**
	 * The result of {@link DocumentsProvider#findDocumentPath}: the root the
	 * path starts at (null for tree-relative paths) and the document ids from
	 * that root down to the requested document, inclusive.
	 */
	public static final class Path implements android.os.Parcelable {
		private final String mRootId;
		private final List<String> mPath;

		public Path(String rootId, List<String> path) {
			mRootId = rootId;
			mPath = path;
		}

		public String getRootId() {
			return mRootId;
		}

		public List<String> getPath() {
			return mPath;
		}

		@Override
		public int describeContents() {
			return 0;
		}

		@Override
		public void writeToParcel(android.os.Parcel dest, int flags) {
			// AOSP uses Parcel.writeStringList; ATL's Parcel lacks it, so
			// encode as count + strings (self-consistent: ATL Bundles transfer
			// in-process).
			dest.writeString(mRootId);
			dest.writeInt(mPath.size());
			for (int i = 0; i < mPath.size(); i++) {
				dest.writeString(mPath.get(i));
			}
		}

		public static final android.os.Parcelable.Creator<Path> CREATOR =
				new android.os.Parcelable.Creator<Path>() {
			@Override
			public Path createFromParcel(android.os.Parcel in) {
				final String rootId = in.readString();
				final int n = in.readInt();
				List<String> path = new java.util.ArrayList<>(n);
				for (int i = 0; i < n; i++) {
					path.add(in.readString());
				}
				return new Path(rootId, path);
			}

			@Override
			public Path[] newArray(int size) {
				return new Path[size];
			}
		};
	}

	public final static class Document {
		public static final String COLUMN_DOCUMENT_ID = "document_id";
		public static final String COLUMN_MIME_TYPE = "mime_type";
		public static final String COLUMN_DISPLAY_NAME = "_display_name"; //OpenableColumns.DISPLAY_NAME;
		public static final String COLUMN_SUMMARY = "summary";
		public static final String COLUMN_LAST_MODIFIED = "last_modified";
		public static final String COLUMN_ICON = "icon";
		public static final String COLUMN_FLAGS = "flags";
		public static final String COLUMN_SIZE = "_size"; //OpenableColumns.SIZE;
		public static final String MIME_TYPE_DIR = "vnd.android.document/directory";

		public static final int FLAG_SUPPORTS_THUMBNAIL = 1;
		public static final int FLAG_SUPPORTS_WRITE = 1 << 1;
		public static final int FLAG_SUPPORTS_DELETE = 1 << 2;
		public static final int FLAG_DIR_SUPPORTS_CREATE = 1 << 3;
		public static final int FLAG_DIR_PREFERS_GRID = 1 << 4;
		public static final int FLAG_DIR_PREFERS_LAST_MODIFIED = 1 << 5;
		public static final int FLAG_SUPPORTS_RENAME = 1 << 6;
		public static final int FLAG_SUPPORTS_COPY = 1 << 7;
		public static final int FLAG_SUPPORTS_MOVE = 1 << 8;
		public static final int FLAG_VIRTUAL_DOCUMENT = 1 << 9;
		public static final int FLAG_SUPPORTS_REMOVE = 1 << 10;
		public static final int FLAG_SUPPORTS_SETTINGS = 1 << 11;
		public static final int FLAG_WEB_LINKABLE = 1 << 12;
		public static final int FLAG_PARTIAL = 1 << 13;
		public static final int FLAG_SUPPORTS_METADATA = 1 << 14;
		public static final int FLAG_DIR_BLOCKS_OPEN_DOCUMENT_TREE = 1 << 15;
	}
}
