package android.os;

import android.util.Slog;

public class Vibrator {
	int fd; // vibrator /dev/input/eventX

	public Vibrator() {
		fd = native_constructor();
	}

	public boolean hasVibrator() {
		return true;
	}

	public void vibrate(long millis) {
		if (fd != -1)
			native_vibrate(fd, millis);
		else
			Slog.v("Vibrator", "vibration motor go burrrr for " + millis + "ms");
	}

	private Thread vibrateThread;

	public void vibrate(final long[] pattern, final int repeat) {
		if (repeat < -1 || repeat >= pattern.length)
			throw new ArrayIndexOutOfBoundsException();
		cancel();
		vibrateThread = new Thread(new Runnable() {
			public void run() {
				int i = 0;
				while (true) {
					for (; i < pattern.length; i++) {
						if (i % 2 == 0)
							try {
								Thread.sleep(pattern[i]);
							} catch (InterruptedException e) {
								return;
							}
						else
							vibrate(pattern[i]);
					}
					if (repeat < 0)
						break;
					// AOSP: after the first pass the pattern loops from
					// index `repeat` until cancel().
					i = repeat;
				}
			}
		});
		vibrateThread.start();
	}

	public boolean hasAmplitudeControl() {
		// ATL's native vibrator (input events) only takes durations.
		return false;
	}

	public void vibrate(VibrationEffect effect) {
		vibrate(effect, null);
	}

	public void vibrate(VibrationEffect effect, android.media.AudioAttributes attributes) {
		if (effect == null)
			throw new NullPointerException("VibrationEffect argument cannot be null");
		// ATL cannot render amplitudes: one-shot/predefined effects vibrate
		// for their duration, waveform effects reuse the pattern path.
		if (effect.timings != null)
			vibrate(effect.timings, effect.repeat);
		else
			vibrate(effect.duration);
	}

	public void cancel() {
		if (vibrateThread != null) {
			vibrateThread.interrupt();
			vibrateThread = null;
		}
	}

	private native void native_vibrate(int fd, long millis);
	private native int native_constructor();
}
