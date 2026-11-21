/* Native side of the media library (ATLMediaStore): tags, durations and
 * dimensions via libavformat, embedded cover art, and the XDG user dirs.
 * Strings cross JNI as NUL-separated UTF-8 byte arrays: NewStringUTF only
 * accepts modified UTF-8, and tags/paths routinely contain 4-byte characters. */

#include <glib.h>
#include <string.h>
#include <libavformat/avformat.h>

#include "../defines.h"
#include "../util.h"
#include "../generated_headers/android_atl_ATLMediaStore.h"

static char *path_from_bytes(JNIEnv *env, jbyteArray path)
{
	jsize len = (*env)->GetArrayLength(env, path);
	char *p = g_malloc(len + 1);
	(*env)->GetByteArrayRegion(env, path, 0, len, (jbyte *)p);
	p[len] = '\0';
	return p;
}

static jbyteArray bytes_from_gstring(JNIEnv *env, GString *s)
{
	jbyteArray out = (*env)->NewByteArray(env, s->len);
	(*env)->SetByteArrayRegion(env, out, 0, s->len, (const jbyte *)s->str);
	return out;
}

/* container metadata first, then each stream's (Ogg keeps Vorbis comments on the stream) */
static const char *tag(AVFormatContext *fmt, const char *key)
{
	AVDictionaryEntry *e = av_dict_get(fmt->metadata, key, NULL, 0);
	for (unsigned i = 0; !e && i < fmt->nb_streams; i++)
		e = av_dict_get(fmt->streams[i]->metadata, key, NULL, 0);
	return e ? e->value : NULL;
}

static AVFormatContext *open_media(const char *path)
{
	AVFormatContext *fmt = NULL;
	if (avformat_open_input(&fmt, path, NULL, NULL) < 0)
		return NULL;
	if (avformat_find_stream_info(fmt, NULL) < 0) {
		avformat_close_input(&fmt);
		return NULL;
	}
	return fmt;
}

static void field(GString *out, const char *value)
{
	if (value)
		g_string_append(out, value);
	g_string_append_c(out, '\0');
}

static void field_num(GString *out, long long value, bool known)
{
	if (known)
		g_string_append_printf(out, "%lld", value);
	g_string_append_c(out, '\0');
}

/* Fields, in order (empty = unknown): title, artist, album, album_artist, genre,
 * track, disc, date, composer, duration_ms, width, height, has_picture,
 * has_audio, has_video, bitrate, sample_rate. null if the file can't be read. */
JNIEXPORT jbyteArray JNICALL Java_android_atl_ATLMediaStore_native_1probe(JNIEnv *env, jclass klass, jbyteArray path_bytes)
{
	char *path = path_from_bytes(env, path_bytes);
	AVFormatContext *fmt = open_media(path);
	g_free(path);
	if (!fmt)
		return NULL;

	const char *album_artist = tag(fmt, "album_artist");
	if (!album_artist)
		album_artist = tag(fmt, "albumartist");

	long long duration = -1;
	if (fmt->duration != AV_NOPTS_VALUE && fmt->duration > 0)
		duration = fmt->duration / 1000;

	int width = 0, height = 0, sample_rate = 0;
	bool has_picture = false, has_audio = false, has_video = false;
	for (unsigned i = 0; i < fmt->nb_streams; i++) {
		AVStream *st = fmt->streams[i];
		if (st->disposition & AV_DISPOSITION_ATTACHED_PIC) {
			has_picture |= st->attached_pic.size > 0;
		} else if (st->codecpar->codec_type == AVMEDIA_TYPE_VIDEO) {
			has_video = true;
			if (!width) {
				width = st->codecpar->width;
				height = st->codecpar->height;
			}
			if (duration < 0 && st->duration != AV_NOPTS_VALUE)
				duration = av_rescale_q(st->duration, st->time_base, (AVRational){1, 1000});
		} else if (st->codecpar->codec_type == AVMEDIA_TYPE_AUDIO) {
			has_audio = true;
			if (!sample_rate)
				sample_rate = st->codecpar->sample_rate;
			if (duration < 0 && st->duration != AV_NOPTS_VALUE)
				duration = av_rescale_q(st->duration, st->time_base, (AVRational){1, 1000});
		}
	}

	GString *out = g_string_new(NULL);
	field(out, tag(fmt, "title"));
	field(out, tag(fmt, "artist"));
	field(out, tag(fmt, "album"));
	field(out, album_artist);
	field(out, tag(fmt, "genre"));
	field(out, tag(fmt, "track"));
	field(out, tag(fmt, "disc"));
	field(out, tag(fmt, "date") ? tag(fmt, "date") : tag(fmt, "year"));
	field(out, tag(fmt, "composer"));
	field_num(out, duration, duration >= 0);
	field_num(out, width, width > 0);
	field_num(out, height, height > 0);
	field_num(out, has_picture, true);
	field_num(out, has_audio, true);
	field_num(out, has_video, true);
	field_num(out, fmt->bit_rate, fmt->bit_rate > 0);
	field_num(out, sample_rate, sample_rate > 0);
	avformat_close_input(&fmt);

	jbyteArray ret = bytes_from_gstring(env, out);
	g_string_free(out, TRUE);
	return ret;
}

JNIEXPORT jbyteArray JNICALL Java_android_atl_ATLMediaStore_native_1embedded_1picture(JNIEnv *env, jclass klass, jbyteArray path_bytes)
{
	char *path = path_from_bytes(env, path_bytes);
	AVFormatContext *fmt = NULL;
	int err = avformat_open_input(&fmt, path, NULL, NULL);
	g_free(path);
	if (err < 0)
		return NULL;
	jbyteArray ret = NULL;
	for (unsigned i = 0; i < fmt->nb_streams; i++) {
		AVStream *st = fmt->streams[i];
		if ((st->disposition & AV_DISPOSITION_ATTACHED_PIC) && st->attached_pic.size > 0) {
			ret = (*env)->NewByteArray(env, st->attached_pic.size);
			(*env)->SetByteArrayRegion(env, ret, 0, st->attached_pic.size, (const jbyte *)st->attached_pic.data);
			break;
		}
	}
	avformat_close_input(&fmt);
	return ret;
}

/* XDG music, pictures, videos, download dirs (empty field when unset or $HOME) */
JNIEXPORT jbyteArray JNICALL Java_android_atl_ATLMediaStore_native_1user_1dirs(JNIEnv *env, jclass klass)
{
	const GUserDirectory dirs[] = {G_USER_DIRECTORY_MUSIC, G_USER_DIRECTORY_PICTURES, G_USER_DIRECTORY_VIDEOS, G_USER_DIRECTORY_DOWNLOAD};
	GString *out = g_string_new(NULL);
	for (size_t i = 0; i < G_N_ELEMENTS(dirs); i++) {
		const char *d = g_get_user_special_dir(dirs[i]);
		field(out, (d && strcmp(d, g_get_home_dir()) != 0) ? d : NULL);
	}
	field(out, g_get_home_dir());
	field(out, g_get_user_cache_dir());
	jbyteArray ret = bytes_from_gstring(env, out);
	g_string_free(out, TRUE);
	return ret;
}
