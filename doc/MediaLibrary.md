# Media library (content://media)

ATL answers MediaStore queries from an index of real files
(`android.atl.ATLMediaStore`), so music players, galleries and video apps see
the user's media the way they would on Android.

## What is indexed
- `$ATL_MEDIA_DIRS` (`:`-separated) if set; an empty value means no library.
- Otherwise the XDG user dirs for music, pictures, videos and downloads
  (`~/.config/user-dirs.dirs`; skipped when unset or equal to `$HOME`) plus the
  app's external storage directory.
- Media by file extension (audio, images, video). Hidden files and directories
  (including macOS `.DS_Store` and `._name` AppleDouble files) and directories
  containing `.nomedia` are skipped, as on Android.

## How
- The index is an SQLite database in `$XDG_CACHE_HOME/android_translation_layer/media-<hash>/`
  (one per app storage dir) with MediaStore's column names. Collections are
  views (`audio_media`, `images_media`, `video_media`, `audio_albums`,
  `audio_artists`, `audio_genres`, `visible_files`); an app's selection, args
  and sort order are passed to SQLite unchanged.
- A query rescans when the index is older than 10 s; only files whose size or
  mtime changed are probed again (libavformat: tags, duration, dimensions,
  embedded pictures). `MediaScannerConnection.scanFile` forces a rescan.
- IDs are derived from the path (albums: name + folder, artists: name), so
  they survive restarts: playlists and "last played" keep working.
- Album art (`content://media/external/audio/albumart/<id>`): `cover`,
  `folder`, `front`, `album`, `albumart` image in the album folder, else the
  only image there, else the first embedded picture (cached).
- Android semantics kept: `track` = disc × 1000 + track; missing album = folder
  name; missing artist = `<unknown>`; pending/trashed rows hidden.

## Not implemented
- Thumbnails are not generated (the thumbnails table is empty; apps decode
  the item itself). No EXIF (`datetaken` = mtime, `orientation` = 0).
- The internal volume is empty (no system ringtones).
- No change notifications to ContentObservers.
- Scoped-storage permissions: every app sees the whole library.
