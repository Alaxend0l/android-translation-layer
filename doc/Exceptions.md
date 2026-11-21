## Exceptions thrown by app code called from native code

Native code (GTK callbacks, the main loop, startup in `main.c`) calls into app
code through upcalls. If app code throws, the exception is pending when the
upcall returns. ART's JNI checker aborts the process on the next JNI call made
with an exception pending, and `ExceptionDescribe` alone does not clear it, so
every upcall must be followed by one of these checks (`util.h`):

- `ATL_LIFECYCLE_CHECK(env)` after lifecycle methods and app startup
  (`Application` creation and `onCreate`, content providers, activity
  creation, `onCreate`/`onStart`/`onResume`/`onPause`/`onStop`/`onDestroy`
  and their `dispatchActivity*` hooks, `onNewIntent`, `onBackPressed`,
  `onWindowFocusChanged`, activity results). Like Android, the exception is
  handed to the thread's uncaught-exception handlers (the app's own crash
  handler runs) via `android.atl.ATLUncaught.dispatch`, which logs
  `FATAL EXCEPTION: main` and ends the process with exit code 10, as
  Android's `KillApplicationHandler` does.
- `ATL_CALLBACK_CHECK(env)` everywhere else (drawing, input, layout,
  measuring, popups, dialogs, broadcasts, ...). The exception is printed after
  a `W/ATL: swallowed exception in <function>` line, cleared, and the app
  continues. Android would end the process in these cases too; ATL keeps the
  app running, and the marker lets tools count each occurrence as a failure.

Don't add bare `ExceptionCheck`/`ExceptionDescribe` pairs.
