package android.os;

public class VibrationEffect {
	// AOSP constant: amplitude value meaning "use the default".
	public static final int DEFAULT_AMPLITUDE = -1;

	// Predefined effects (API 29), AOSP ids.
	public static final int EFFECT_CLICK = 0;
	public static final int EFFECT_DOUBLE_CLICK = 1;
	public static final int EFFECT_TICK = 2;
	public static final int EFFECT_HEAVY_CLICK = 5;

	// Waveform timings (off, on, off, on, ...); null for one-shot and
	// predefined effects. ATL's native vibrator only takes durations, so
	// amplitudes are accepted but not rendered (hasAmplitudeControl()==false).
	long[] timings;
	int repeat = -1; // -1 = do not repeat
	long duration;

	public static VibrationEffect createOneShot(long milliseconds, int amplitude) {
		VibrationEffect effect = new VibrationEffect();
		effect.duration = milliseconds;
		return effect;
	}

	public static VibrationEffect createWaveform(long[] timings, int repeat) {
		return createWaveform(timings, null, repeat);
	}

	public static VibrationEffect createWaveform(long[] timings, int[] amplitudes, int repeat) {
		VibrationEffect effect = new VibrationEffect();
		if (timings == null)
			throw new IllegalArgumentException("timings cannot be null");
		effect.timings = new long[timings.length];
		System.arraycopy(timings, 0, effect.timings, 0, timings.length);
		effect.repeat = repeat;
		long sum = 0;
		for (int i = 0; i < timings.length; i++)
			sum += timings[i];
		effect.duration = sum;
		return effect;
	}

	public static VibrationEffect createPredefined(int effectId) {
		VibrationEffect effect = new VibrationEffect();
		// Durations per AOSP's default vibration profiles.
		switch (effectId) {
		case EFFECT_CLICK:
			effect.duration = 20;
			break;
		case EFFECT_DOUBLE_CLICK:
			effect.duration = 30;
			break;
		case EFFECT_TICK:
			effect.duration = 10;
			break;
		case EFFECT_HEAVY_CLICK:
			effect.duration = 40;
			break;
		default:
			throw new IllegalArgumentException("Unknown predefined effect id " + effectId);
		}
		return effect;
	}

	public long getDuration() {
		return duration;
	}
}
