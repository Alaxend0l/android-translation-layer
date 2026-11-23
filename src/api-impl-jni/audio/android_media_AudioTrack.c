#include <gtk/gtk.h>

#include <alsa/asoundlib.h>
#include <stdint.h>
#include <stdio.h>

#include "../defines.h"
#include "../util.h"
#include "audio.h"

#include "../generated_headers/android_media_AudioTrack.h"

/* Per-track playback state, keyed by the PCM handle (which is unique per
 * AudioTrack: the handle is closed in release()). ATL needs to know whether
 * the app has started this track yet: Android renders nothing before
 * AudioTrack.start(), and a stream that starts on its own as soon as the app
 * pre-rolls data makes apps play by themselves (see write_frames()). */
typedef struct {
	gboolean started;        /* play() called, and no flush()/stop() since */
	gboolean silence_logged; /* the "written before play()" line was printed */
} track_state;

static GHashTable *track_states;
static GMutex track_states_lock;

static track_state *track_state_get(snd_pcm_t *pcm, gboolean create)
{
	track_state *st = NULL;
	g_mutex_lock(&track_states_lock);
	if (!track_states && create)
		track_states = g_hash_table_new(g_direct_hash, g_direct_equal);
	if (track_states) {
		st = g_hash_table_lookup(track_states, pcm);
		if (!st && create) {
			st = g_new0(track_state, 1);
			g_hash_table_insert(track_states, pcm, st);
		}
	}
	g_mutex_unlock(&track_states_lock);
	return st;
}

static void track_state_remove(snd_pcm_t *pcm)
{
	g_mutex_lock(&track_states_lock);
	if (track_states)
		g_hash_table_remove(track_states, pcm);
	g_mutex_unlock(&track_states_lock);
}

JNIEXPORT jint JNICALL Java_android_media_AudioTrack_native_1constructor(JNIEnv *env, jobject this, jint streamType, jint rate, jint channel_config, jint audioFormat, jint buffer_size, jint mode)
{

	snd_pcm_t *pcm_handle;
	snd_pcm_hw_params_t *params;

	unsigned int channels;
	unsigned int channels_out;
	unsigned int period_time;
	unsigned int buffer_time;

	int ret;

	/* Open the PCM device in playback mode */
	ret = snd_pcm_open(&pcm_handle, PCM_DEVICE, SND_PCM_STREAM_PLAYBACK, 0);
	if (ret < 0) {
		printf("ERROR: Can't open \"%s\" PCM device. %s\n", PCM_DEVICE, snd_strerror(ret));
		/* Android puts the AudioTrack into STATE_UNINITIALIZED; ATL used
		 * to keep an invalid handle here and every later snd_pcm_* call
		 * segfaulted the caller thread (witnessed on the phone runner:
		 * SIGSEGV on the ExoPlayer playback thread after "Host is down",
		 * run pgram0, 2026-11-22). */
		return -1;
	}

	snd_pcm_hw_params_alloca(&params);
	helper_hw_params_init(pcm_handle, params, rate, channel_config, SND_PCM_FORMAT_S16_LE, &channels);

	/*--↓*/
	snd_pcm_uframes_t buffer_size_as_uframes_t = buffer_size / channels / 2; // 2 means PCM16
	snd_pcm_hw_params_set_buffer_size_near(pcm_handle, params, &buffer_size_as_uframes_t);
	/*--↑*/

	/* set the period time to 1/4 of the buffer time */
	ret = snd_pcm_hw_params_get_buffer_time(params, &buffer_time, NULL);
	period_time = buffer_time / 4;
	ret = snd_pcm_hw_params_set_period_time_near(pcm_handle, params, &period_time, NULL);

	/* Write parameters */
	ret = snd_pcm_hw_params(pcm_handle, params);
	if (ret < 0) {
		printf("ERROR: Can't set harware parameters. %s\n", snd_strerror(ret));
		/* No usable configuration: same STATE_UNINITIALIZED contract as the
		 * open failure above, otherwise period_size below is garbage and the
		 * sw params make the stream impossible to start. */
		snd_pcm_close(pcm_handle);
		return -1;
	}

	//snd_pcm_hw_params_free (hw_params);

	/*--↓*/
	snd_pcm_uframes_t period_size = 0;

	ret = snd_pcm_hw_params_get_period_size(params, &period_size, 0);
	if (ret < 0 || period_size == 0) {
		printf("Error calling snd_pcm_hw_params_get_period_size: %s\n", snd_strerror(ret));
		snd_pcm_close(pcm_handle);
		return -1;
	}

	snd_pcm_sw_params_t *sw_params;

	snd_pcm_sw_params_malloc(&sw_params);
	snd_pcm_sw_params_current(pcm_handle, sw_params);

	/* Android does not render a single frame before AudioTrack.start():
	 * frames written earlier sit in the track buffer and only become
	 * audible once the track is started. A start threshold of one period
	 * (what this used to be) makes ALSA start the stream on the app's
	 * first pre-roll write, so apps that prepare/seek without playing
	 * (media3 priming a freshly restored queue right after launch) leak
	 * that buffer to the device and then starve it: music and stuttering
	 * "skipping" on boot although the app never called play()
	 * (Gramophone on the desktop PoC, 2026-11-23: PipeWire showed this
	 * stream [active] with zero AudioTrack.play() calls in the log, and
	 * the phone's grres runs auto-played at boot the same way).
	 * Asking for the whole buffer means nothing starts by itself; play()
	 * starts the stream explicitly a few lines below. The safety net in
	 * write_frames() covers devices/servers that clamp the threshold. */
	snd_pcm_uframes_t buffer_size_frames = 0;
	snd_pcm_hw_params_get_buffer_size(params, &buffer_size_frames);
	snd_pcm_sw_params_set_start_threshold(pcm_handle, sw_params,
		buffer_size_frames ? buffer_size_frames : period_size * 4);
	/* One period, not "the whole buffer": Android's AudioTrack starts
	 * consuming in real time right after play(), and media3's position
	 * watchdog flags a non-advancing head as an underrun within ~a second
	 * of playback start (Gramophone skip-to-next stall, phone and bazzite
	 * scratch runs, 2026-11-22). */
	snd_pcm_sw_params_set_avail_min(pcm_handle, sw_params, period_size);

	snd_pcm_sw_params(pcm_handle, sw_params);

	//snd_pcm_sw_params_free (sw_params);
	/*--↑*/

	/* Resume information */
	printf("PCM name: '%s'\n", snd_pcm_name(pcm_handle));

	printf("PCM state: %s\n", snd_pcm_state_name(snd_pcm_state(pcm_handle)));

	snd_pcm_hw_params_get_channels(params, &channels_out);
	printf("channels: %i ", channels_out);

	if (channels_out == 1)
		printf("(mono)\n");
	else if (channels_out == 2)
		printf("(stereo)\n");

	unsigned int tmp;
	snd_pcm_hw_params_get_rate(params, &tmp, 0);
	printf("rate: %d bps\n", tmp);

	snd_pcm_hw_params_get_period_time(params, &period_time, NULL);

	_SET_LONG_FIELD(this, "pcm_handle", _INTPTR(pcm_handle));
	_SET_LONG_FIELD(this, "params", _INTPTR(params));
	_SET_INT_FIELD(this, "channels", channels_out);
	_SET_INT_FIELD(this, "period_time", period_time);
	track_state_get(pcm_handle, TRUE);

	return 0;
}

JNIEXPORT jint JNICALL Java_android_media_AudioTrack_getMinBufferSize(JNIEnv *env, jclass this_class, jint sampleRateInHz, jint channelConfig, jint audioFormat)
{
	snd_pcm_t *pcm_handle;
	snd_pcm_hw_params_t *params;
	snd_pcm_uframes_t frames;
	int ret;
	unsigned int num_channels;

	ret = snd_pcm_open(&pcm_handle, PCM_DEVICE, SND_PCM_STREAM_PLAYBACK, 0);
	if (ret < 0) {
		/* No output device to probe (on the phone runner the audio server
		 * intermittently answers "Host is down"). Android's getMinBufferSize
		 * returns ERROR (-1) when it cannot determine a size, and media3
		 * turns that into a reported audio error; probing the unopened
		 * handle instead segfaulted the ExoPlayer playback thread (SIGSEGV
		 * in helper_hw_params_init, phone run pres2, 2026-11-22). */
		printf("Error calling snd_pcm_open: %s\n", snd_strerror(ret));
		return -1;
	}

	snd_pcm_hw_params_alloca(&params);
	helper_hw_params_init(pcm_handle, params, sampleRateInHz, channelConfig, SND_PCM_FORMAT_S16_LE, &num_channels); // FIXME: a switch?

	ret = snd_pcm_hw_params(pcm_handle, params);
	if (ret < 0)
		printf("Error calling snd_pcm_hw_params: %s\n", snd_strerror(ret));

	ret = snd_pcm_hw_params_get_period_size(params, &frames, 0);
	if (ret < 0)
		printf("Error calling snd_pcm_hw_params_get_period_size: %s\n", snd_strerror(ret));

	// TODO: snd_pcm_hw_params_free(params) causes segfault, is it not supposed to be called?
	snd_pcm_close(pcm_handle);

	_SET_STATIC_INT_FIELD(this_class, "frames", frames);
	ATL_CALLBACK_CHECK(env);

	printf("\n\nJava_android_media_AudioTrack_getMinBufferSize is returning: %ld\n\n\n", frames * num_channels * 2);
	return frames * num_channels * 2; // FIXME: 2 bytes = 16 bits (s16)
}

struct jni_callback_data {
	JavaVM *jvm;
	jobject this;
	jclass this_class;
	jobject listener;
	jint period_time;
};
void periodic_update_callback(snd_async_handler_t *pcm_callback)
{
	struct jni_callback_data *d = snd_async_handler_get_callback_private(pcm_callback);
	int getenv_ret;
	int attach_ret = -1;

	//	printf("periodic_update_callback called!\n");

	JNIEnv *env;
	getenv_ret = (*d->jvm)->GetEnv(d->jvm, (void **)&env, JNI_VERSION_1_6);

	//printf("!!!! GetEnv: %p getenv_ret: %d\n",env, getenv_ret);
	if (getenv_ret == JNI_EDETACHED) {
		printf("!!!! JNI_EDETACHED\n");
		attach_ret = (*d->jvm)->AttachCurrentThread(d->jvm, (void **)&env, NULL);
		// TODO error checking
	}

	if (d->listener)
		J__AudioTrack__OnPlaybackPositionUpdateListener__onPeriodicNotification(env, d->listener, d->this);

	ATL_CALLBACK_CHECK(env);

	if (attach_ret == JNI_OK) // if we (succesfully) attached a thread, we should probably detach it now
		(*d->jvm)->DetachCurrentThread(d->jvm);

	// microseconds to milliseconds
	//g_timeout_add (d->period_time / 1000 - 2, G_SOURCE_FUNC(helper_loop), d);
	//return G_SOURCE_REMOVE;
}

JNIEXPORT void JNICALL Java_android_media_AudioTrack_native_1play(JNIEnv *env, jobject this)
{
	snd_pcm_t *pcm_handle = _PTR(_GET_LONG_FIELD(this, "pcm_handle"));
	if (!pcm_handle)
		return;

	jint period_time = _GET_INT_FIELD(this, "period_time");

	// FIXME - this callback should probably be set up elsewhere

	JavaVM *jvm;
	(*env)->GetJavaVM(env, &jvm);

	struct jni_callback_data *callback_data = malloc(sizeof(struct jni_callback_data));
	callback_data->jvm = jvm;
	callback_data->this = _REF(this);
	callback_data->this_class = _REF(_CLASS(this));
	callback_data->listener = _REF(_GET_OBJ_FIELD(this, "periodic_update_listener", "Landroid/media/AudioTrack$OnPlaybackPositionUpdateListener;"));
	callback_data->period_time = period_time;

	// microseconds to milliseconds
	//g_timeout_add (period_time / 1000, G_SOURCE_FUNC(helper_loop), callback_data);

	/*--↓*/
	snd_async_handler_t *pcm_callback;

	snd_async_add_pcm_handler(&pcm_callback, pcm_handle, periodic_update_callback, callback_data);
	track_state *st = track_state_get(pcm_handle, TRUE);
	if (st)
		st->started = TRUE;
	snd_pcm_start(pcm_handle);
	snd_pcm_pause(pcm_handle, FALSE);
	/*--↑*/
}

static int write_frames(snd_pcm_t *pcm_handle, const void *buffer, int frames_to_write, float volume)
{
	if (volume != 1.f) {
		for (int i = 0; i < frames_to_write * 2; i++)
			((jshort *)(buffer))[i] *= volume;
	}
	snd_pcm_sframes_t frames_written = snd_pcm_writei(pcm_handle, buffer, frames_to_write);
	if (frames_written == -EPIPE) {
		printf("XRUN.\n");
		snd_pcm_recover(pcm_handle, frames_written, 0);
		frames_written = snd_pcm_writei(pcm_handle, buffer, frames_to_write);
		snd_pcm_start(pcm_handle);
	}
	if (frames_written < 0) {
		printf("ERROR. Can't write to PCM device. %s\n", snd_strerror(frames_written));
		frames_written = 0;
	}
	track_state *st = track_state_get(pcm_handle, FALSE);
	if (st && !st->started && frames_written > 0) {
		/* Safety net for the start_threshold above: some servers (the
		 * PipeWire ALSA plugin among them) may start the stream anyway
		 * once data is available. Android renders nothing before
		 * AudioTrack.start(), so put the stream back to PAUSED - the
		 * frames stay in the buffer and are played by play(). */
		if (snd_pcm_state(pcm_handle) == SND_PCM_STATE_RUNNING) {
			int ret = snd_pcm_pause(pcm_handle, TRUE);
			if (!st->silence_logged) {
				st->silence_logged = TRUE;
				printf("AudioTrack: %d frames written before play(); stream paused "
				       "(pause ret=%d: %s)\n", (int) frames_written, ret, snd_strerror(-ret));
			}
		}
	}
	return frames_written;
}

JNIEXPORT jint JNICALL Java_android_media_AudioTrack_native_1write___3BIIF(JNIEnv *env, jobject this, jbyteArray audio_data, jint offset_in_bytes, jint frames_to_write, jfloat volume)
{
	snd_pcm_t *pcm_handle = _PTR(_GET_LONG_FIELD(this, "pcm_handle"));
	if (!pcm_handle)
		return 0;

	jbyte *buffer = _GET_BYTE_ARRAY_ELEMENTS(audio_data);
	snd_pcm_sframes_t frames_written = write_frames(pcm_handle, buffer + offset_in_bytes, frames_to_write, volume);
	_RELEASE_BYTE_ARRAY_ELEMENTS(audio_data, buffer);

	return frames_written;
}

JNIEXPORT jint JNICALL Java_android_media_AudioTrack_native_1write___3SIIF(JNIEnv *env, jobject this, jshortArray audio_data, jint offset_in_shorts, jint frames_to_write, jfloat volume)
{
	snd_pcm_t *pcm_handle = _PTR(_GET_LONG_FIELD(this, "pcm_handle"));
	if (!pcm_handle)
		return 0;

	jshort *buffer = (*env)->GetShortArrayElements(env, audio_data, NULL);
	snd_pcm_sframes_t frames_written = write_frames(pcm_handle, buffer + offset_in_shorts, frames_to_write, volume);
	(*env)->ReleaseShortArrayElements(env, audio_data, buffer, 0);

	return frames_written;
}

JNIEXPORT void JNICALL Java_android_media_AudioTrack_native_1pause(JNIEnv *env, jobject this)
{
	snd_pcm_t *pcm_handle = _PTR(_GET_LONG_FIELD(this, "pcm_handle"));
	if (!pcm_handle)
		return;
	track_state *st = track_state_get(pcm_handle, FALSE);
	if (st)
		st->started = FALSE;
	snd_pcm_pause(pcm_handle, TRUE);
}

JNIEXPORT void JNICALL Java_android_media_AudioTrack_native_1flush(JNIEnv *env, jobject this)
{
	snd_pcm_t *pcm_handle = _PTR(_GET_LONG_FIELD(this, "pcm_handle"));
	if (!pcm_handle)
		return;
	/* Discard frames written but not played (Android flush()/stop()).
	 * Errors by stream state are intentionally ignored. */
	track_state *st = track_state_get(pcm_handle, FALSE);
	if (st) {
		st->started = FALSE;
		st->silence_logged = FALSE;
	}
	snd_pcm_drop(pcm_handle);
}

JNIEXPORT void JNICALL Java_android_media_AudioTrack_native_1release(JNIEnv *env, jobject this)
{
	snd_pcm_t *pcm_handle = _PTR(_GET_LONG_FIELD(this, "pcm_handle"));
	if (!pcm_handle)
		return;
	track_state_remove(pcm_handle);
	snd_pcm_close(pcm_handle);
	_SET_LONG_FIELD(this, "pcm_handle", 0);
}

JNIEXPORT jint JNICALL Java_android_media_AudioTrack_native_1getPlaybackHeadPosition(JNIEnv *env, jobject this)
{
	snd_pcm_t *pcm_handle = _PTR(_GET_LONG_FIELD(this, "pcm_handle"));
	if (!pcm_handle)
		return 0;
	snd_pcm_sframes_t delay;
	snd_pcm_delay(pcm_handle, &delay);

	return delay;
}
