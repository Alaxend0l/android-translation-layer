package android.media;

import android.atl.annotation.Export;
import java.nio.ByteBuffer;

public class AudioTrack {
	public interface OnPlaybackPositionUpdateListener {
		void onMarkerReached(AudioTrack track);

		@Export
		void onPeriodicNotification(AudioTrack track);
	}

	public static final int ERROR_BAD_VALUE = -2; // basically EINVAL

	public static final int PLAYSTATE_STOPPED = 1;
	public static final int PLAYSTATE_PAUSED = 2;
	public static final int PLAYSTATE_PLAYING = 3;

	int streamType;
	int sampleRateInHz;
	int channelConfig;
	int audioFormat;
	int bufferSizeInBytes;
	int mode;
	private int sessionId;
	private boolean initialized = false;
	private int playbackState = PLAYSTATE_STOPPED;
	private int playbackHeadPosition = 0;
	private float volume = 1.f;
	/* Data written while the track is paused. Android buffers those writes
	 * and plays them on play(); ALSA refuses them (writei returns 0), which
	 * deadlocks media3's stalled-write handling (Gramophone, 2026-11-22). */
	private final java.io.ByteArrayOutputStream pendingWhilePaused = new java.io.ByteArrayOutputStream();

	// for native code's use
	long pcm_handle;
	long params;
	int channels;
	int period_time;
	// mostly
	static int frames;
	OnPlaybackPositionUpdateListener periodic_update_listener;

	/* Returns 0 on success, negative if the PCM device could not be opened
	 * (then the track is STATE_UNINITIALIZED, like Android's behaviour). */
	private native int native_constructor(int streamType, int sampleRateInHz, int num_channels, int audioFormat, int bufferSizeInBytes, int mode);
	public AudioTrack(int streamType, int sampleRateInHz, int channelConfig, int audioFormat, int bufferSizeInBytes, int mode) {
		this.streamType = streamType;
		this.sampleRateInHz = sampleRateInHz;
		this.channelConfig = channelConfig;
		this.audioFormat = audioFormat;
		this.bufferSizeInBytes = bufferSizeInBytes;
		this.mode = mode;

		System.out.println("\n\n\nAudioTrack(" + streamType + ", " + sampleRateInHz + ", " + channelConfig + ", " + audioFormat + ", " + bufferSizeInBytes + ", " + mode + "); called\n\n\n\n");
		initialized = native_constructor(streamType, sampleRateInHz, channelConfig, audioFormat, bufferSizeInBytes, mode) == 0;
	}

	public AudioTrack(int streamType, int sampleRateInHz, int channelConfig, int audioFormat, int bufferSizeInBytes, int mode, int sessionId) {
		this(streamType, sampleRateInHz, channelConfig, audioFormat, bufferSizeInBytes, mode);
		this.sessionId = sessionId;
	}

	public AudioTrack(AudioAttributes attributes, AudioFormat format, int bufferSizeInBytes, int mode, int sessionId) {
		this(attributes.streamType, format.sampleRate, format.channelMask, format.encoding, bufferSizeInBytes, mode, sessionId);
	}

	public static native int getMinBufferSize(int sampleRateInHz, int channelConfig, int audioFormat);

	public void setPlaybackPositionUpdateListener(OnPlaybackPositionUpdateListener listener) {
		this.periodic_update_listener = listener;
	}

	public int setPositionNotificationPeriod(int periodInFrames) {
		System.out.println("\n\nAudioTrack.nsetPositionNotificationPeriod(" + periodInFrames + "); called\n\n\n\n");
		return 0; // SUCCESS
	}

	public int getPositionNotificationPeriod() {
		return this.frames;
	}

	public void play() {
		System.out.println("calling AudioTrack.play()\n");
		synchronized (this) {
			playbackState = PLAYSTATE_PLAYING;
			native_play();
			/* Data written while paused plays after the new writes. */
			drainPendingLocked();
		}
	}

	public void stop() {
		System.out.println("calling AudioTrack.stop()\n");
		synchronized (this) {
			/* Android stop() stops playback and discards pending data. */
			playbackState = PLAYSTATE_STOPPED;
			pendingWhilePaused.reset();
			native_flush();
		}
	}

	public void flush() {
		System.out.println("calling AudioTrack.flush()\n");
		synchronized (this) {
			/* Android flush() (valid when paused/stopped) discards the
			 * data written but not yet played. */
			pendingWhilePaused.reset();
			native_flush();
		}
	}

	public void release() {
		System.out.println("calling AudioTrack.release()\n");
		synchronized (this) {
			pendingWhilePaused.reset();
			native_release();
		}
	}

	public int getState() {
		return initialized ? 1 : 0; // STATE_INITIALIZED / STATE_UNINITIALIZED
	}

	private void drainPendingLocked() {
		if (pendingWhilePaused.size() == 0)
			return;
		byte[] data = pendingWhilePaused.toByteArray();
		pendingWhilePaused.reset();
		int frames = data.length / channels / 2; // 2 means PCM16
		int ret = native_write(data, 0, frames, volume);
		if (ret > 0) {
			playbackHeadPosition += ret;
		}
	}

	public int write(byte[] audioData, int offsetInBytes, int sizeInBytes) {
		/* sanity check the parameters before calling native_write */
		if ((audioData == null)
		    || (offsetInBytes < 0) || (sizeInBytes < 0)
		    || (offsetInBytes + sizeInBytes < 0)
		    || (offsetInBytes + sizeInBytes > audioData.length)) {
			return ERROR_BAD_VALUE;
		}
		if (!initialized) {
			/* Android throws instead of touching an uninitialized track
			 * (ATL previously kept a bad PCM handle and segfaulted). */
			throw new IllegalStateException("can not be used in the UNINITIALIZED state");
		}

		synchronized (this) {
			if (playbackState == PLAYSTATE_PAUSED) {
				/* Android buffers writes to a paused track; ALSA returns 0,
				 * which made media3 spin in handleStalledWrite forever. */
				pendingWhilePaused.write(audioData, offsetInBytes, sizeInBytes);
				return sizeInBytes;
			}
			drainPendingLocked();
			int framesToWrite = sizeInBytes / channels / 2; // 2 means PCM16
			int ret = native_write(audioData, offsetInBytes, framesToWrite, volume);
			if (ret > 0) {
				playbackHeadPosition += ret;
			}
			return ret * channels * 2; // 2 means PCM16
		}
	}

	public int write(ByteBuffer audioData, int sizeInBytes, int writeMode) {
		int ret = write(audioData.array(), audioData.arrayOffset() + audioData.position(), sizeInBytes);
		audioData.position(audioData.position() + ret);
		return ret;
	}

	public int write(short audioData[], int offsetInShorts, int sizeInShorts) {
		/* sanity check the parameters before calling native_write */
		if ((audioData == null)
		    || (offsetInShorts < 0) || (sizeInShorts < 0)
		    || (offsetInShorts + sizeInShorts < 0)
		    || (offsetInShorts + sizeInShorts > audioData.length)) {
			return ERROR_BAD_VALUE;
		}
		if (!initialized) {
			throw new IllegalStateException("can not be used in the UNINITIALIZED state");
		}

		synchronized (this) {
			if (playbackState == PLAYSTATE_PAUSED) {
				for (int i = offsetInShorts; i < offsetInShorts + sizeInShorts; i++) {
					short s = audioData[i];
					pendingWhilePaused.write((byte) (s & 0xff));
					pendingWhilePaused.write((byte) ((s >> 8) & 0xff));
				}
				return sizeInShorts * 2;
			}
			drainPendingLocked();
			int framesToWrite = sizeInShorts / channels;
			int ret = native_write(audioData, offsetInShorts, framesToWrite, volume);
			if (ret > 0) {
				playbackHeadPosition += ret;
			}
			return ret * channels;
		}
	}

	public int getAudioSessionId() {
		return sessionId;
	}

	public int getSessionId() {
		return sessionId;
	}

	/* AudioRouting (API 33). ATL's audio output route is fixed, so listeners
	 * are registered but no routing-change callback is ever generated. */
	private final java.util.concurrent.CopyOnWriteArrayList<AudioRouting.OnRoutingChangedListener> mRoutingListeners =
		new java.util.concurrent.CopyOnWriteArrayList<>();

	public void addOnRoutingChangedListener(AudioRouting.OnRoutingChangedListener listener, android.os.Handler handler) {
		if (listener == null) {
			throw new IllegalArgumentException("listener cannot be null");
		}
		mRoutingListeners.addIfAbsent(listener);
	}

	public void removeOnRoutingChangedListener(AudioRouting.OnRoutingChangedListener listener) {
		if (listener == null) {
			throw new IllegalArgumentException("listener cannot be null");
		}
		mRoutingListeners.remove(listener);
	}

	public int getSampleRate() {
		return sampleRateInHz;
	}

	public int setStereoVolume(float leftVolume, float rightVolume) {
		this.volume = (leftVolume + rightVolume) / 2;
		return 0;
	}

	public int getPlayState() {
		return playbackState;
	}

	public int getChannelCount() {
		return channels;
	}

	/* AOSP reports underruns tracked natively; ATL's write path doesn't track
	 * them, so no underrun has been recorded. */
	public int getUnderrunCount() {
		return 0;
	}

	public int getEncoding() {
		return audioFormat;
	}

	/* AOSP reads the frame count from the audioflinger client buffer; ATL sizes
	 * its write buffer in bytes (2 == PCM16, same assumption as write()). */
	public int getBufferSizeInFrames() {
		return bufferSizeInBytes / channels / 2;
	}

	/* AOSP queries audioflinger; ATL's native layer has no latency report. */
	public int getLatency() {
		return 0;
	}

	public void pause() {
		System.out.println("calling AudioTrack.pause()\n");
		synchronized (this) {
			playbackState = PLAYSTATE_PAUSED;
			native_pause();
		}
	}

	public int getPlaybackHeadPosition() {
		/* Android reports frames consumed since play() and never goes
		 * backwards; some ALSA plugins report a delay larger than the
		 * frames written (e.g. while paused), which would read as the
		 * head rewinding and make media3 declare an underrun. Clamp it. */
		int head = playbackHeadPosition - native_getPlaybackHeadPosition();
		return head < 0 ? 0 : head;
	}

	public int setVolume(float volume) {
		this.volume = volume;
		return 0;
	}

	public boolean getTimestamp(AudioTimestamp timestamp) {
		return false;
	}

	private native int native_getPlaybackHeadPosition();
	private native void native_flush();
	public native void native_play();
	public native void native_pause();
	private native int native_write(byte[] audioData, int offsetInBytes, int framesToWrite, float volume);
	private native int native_write(short[] audioData, int offsetInShorts, int framesToWrite, float volume);
	public native void native_release();

	public static int getNativeOutputSampleRate(int i) {
		return -1;
	}

	// nested classes
	public static class Builder {
		private AudioAttributes mAttributes;
		private AudioFormat mFormat;
		private int mBufferSizeInBytes;
		private int mTransferMode;
		private int mSessionId; // 0 == AudioTrack.SESSION_ID_GENERATE

		public Builder() {}

		public AudioTrack build() {
			return new AudioTrack(mAttributes, mFormat, mBufferSizeInBytes, mTransferMode, mSessionId);
		}

		/* AOSP rejects SESSION_ID_NONE (-1) and SESSION_ID_OMAP (-2); used by
		 * androidx.media3's AudioTrack when building through the Builder API. */
		public Builder setSessionId(int sessionId) {
			if (sessionId == -1 || sessionId == -2) {
				throw new IllegalArgumentException("Not a valid audio session id");
			}
			mSessionId = sessionId;
			return this;
		}

		public Builder setAudioAttributes(AudioAttributes attributes) {
			mAttributes = attributes;
			return this;
		}

		public Builder setAudioFormat(AudioFormat format) {
			mFormat = format;
			return this;
		}

		public Builder setBufferSizeInBytes(int bufferSizeInBytes) {
			mBufferSizeInBytes = bufferSizeInBytes;
			return this;
		}

		public Builder setTransferMode(int mode) {
			mTransferMode = mode;
			return this;
		}

		public Builder setPerformanceMode(int performanceMode) {
			return this;
		}
	}
}
