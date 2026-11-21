/* Shaders (android.graphics.Shader and subclasses) as GSK render nodes.
 *
 * A Paint with a shader draws its shape as an alpha mask and fills the masked
 * area with the shader (paint_push_shader / paint_pop_shader): gradients become
 * GSK gradient nodes, BitmapShader a texture (repeat nodes for REPEAT/MIRROR,
 * stretched edge pixels for CLAMP), ComposeShader blend or mask nodes.
 * Shaders are reference counted: the Java object and every Paint using it
 * hold a reference. See doc/Shaders.md. */

#include <gtk/gtk.h>
#include <jni.h>
#include <math.h>

#include "../defines.h"
#include "AndroidPaint.h"

enum shader_type { SHADER_LINEAR, SHADER_RADIAL, SHADER_SWEEP, SHADER_BITMAP, SHADER_COMPOSE };
enum tile_mode { TILE_CLAMP = 0, TILE_MIRROR = 1, TILE_REPEAT = 2 }; /* Shader.TileMode ordinals */

/* ComposeShader modes (Java passes these codes, see ComposeShader.java) */
enum compose_mode {
	COMPOSE_SRC_OVER, COMPOSE_DST_OVER, COMPOSE_SRC_IN, COMPOSE_DST_IN, COMPOSE_SRC_OUT, COMPOSE_DST_OUT,
	COMPOSE_SRC, COMPOSE_DST, COMPOSE_CLEAR, COMPOSE_SRC_ATOP, COMPOSE_DST_ATOP, COMPOSE_XOR,
	COMPOSE_MULTIPLY, COMPOSE_SCREEN, COMPOSE_OVERLAY, COMPOSE_DARKEN, COMPOSE_LIGHTEN, COMPOSE_ADD,
};

struct AndroidShader {
	enum shader_type type;
	enum tile_mode tile_x, tile_y;
	graphene_point_t p0, p1; /* linear: start/end; radial and sweep: center in p0 */
	float radius;
	GskColorStop *stops;
	gsize n_stops;
	GdkTexture *texture;
	graphene_matrix_t local;
	bool has_local;
	struct AndroidShader *a, *b; /* compose: dst, src */
	enum compose_mode mode;
};

static void shader_clear(gpointer data)
{
	struct AndroidShader *s = data;
	g_free(s->stops);
	g_clear_object(&s->texture);
	if (s->a)
		android_shader_unref(s->a);
	if (s->b)
		android_shader_unref(s->b);
}

struct AndroidShader *android_shader_ref(struct AndroidShader *s)
{
	return g_atomic_rc_box_acquire(s);
}

void android_shader_unref(struct AndroidShader *s)
{
	g_atomic_rc_box_release_full(s, shader_clear);
}

static struct AndroidShader *shader_new(enum shader_type type)
{
	struct AndroidShader *s = g_atomic_rc_box_new0(struct AndroidShader);
	s->type = type;
	return s;
}

static GdkRGBA argb_to_rgba(jint c)
{
	return (GdkRGBA){((c >> 16) & 0xff) / 255.f, ((c >> 8) & 0xff) / 255.f, (c & 0xff) / 255.f, ((c >> 24) & 0xff) / 255.f};
}

/* colors + optional positions (null: evenly spaced), as Android: positions are
 * clamped to [0, 1] and made non-decreasing */
static void set_stops(JNIEnv *env, struct AndroidShader *s, jintArray colors_arr, jfloatArray positions_arr)
{
	jsize n = (*env)->GetArrayLength(env, colors_arr);
	jint *colors = (*env)->GetIntArrayElements(env, colors_arr, NULL);
	jfloat *positions = positions_arr ? (*env)->GetFloatArrayElements(env, positions_arr, NULL) : NULL;
	s->n_stops = n;
	s->stops = g_new(GskColorStop, n);
	float prev = 0;
	for (jsize i = 0; i < n; i++) {
		float off = positions ? positions[i] : (n > 1 ? (float)i / (n - 1) : 0);
		off = CLAMP(off, prev, 1.f);
		prev = off;
		s->stops[i].offset = off;
		s->stops[i].color = argb_to_rgba(colors[i]);
	}
	(*env)->ReleaseIntArrayElements(env, colors_arr, colors, JNI_ABORT);
	if (positions)
		(*env)->ReleaseFloatArrayElements(env, positions_arr, positions, JNI_ABORT);
}

/* MIRROR as a repeating gradient over twice the length: stops, then the same
 * stops reversed */
static GskColorStop *mirrored_stops(const struct AndroidShader *s, gsize *n_out)
{
	gsize n = s->n_stops;
	GskColorStop *m = g_new(GskColorStop, 2 * n);
	for (gsize i = 0; i < n; i++) {
		m[i].offset = s->stops[i].offset / 2;
		m[i].color = s->stops[i].color;
		m[2 * n - 1 - i].offset = 1 - s->stops[i].offset / 2;
		m[2 * n - 1 - i].color = s->stops[i].color;
	}
	*n_out = 2 * n;
	return m;
}

static void append_linear(GtkSnapshot *snapshot, const struct AndroidShader *s, const graphene_rect_t *bounds)
{
	if (graphene_point_distance(&s->p0, &s->p1, NULL, NULL) < 1e-6) {
		/* degenerate: Android fills with the last color */
		gtk_snapshot_append_color(snapshot, &s->stops[s->n_stops - 1].color, bounds);
		return;
	}
	if (s->tile_x == TILE_CLAMP) {
		gtk_snapshot_append_linear_gradient(snapshot, bounds, &s->p0, &s->p1, s->stops, s->n_stops);
	} else if (s->tile_x == TILE_REPEAT) {
		gtk_snapshot_append_repeating_linear_gradient(snapshot, bounds, &s->p0, &s->p1, s->stops, s->n_stops);
	} else {
		gsize n;
		GskColorStop *m = mirrored_stops(s, &n);
		graphene_point_t end = GRAPHENE_POINT_INIT(2 * s->p1.x - s->p0.x, 2 * s->p1.y - s->p0.y);
		gtk_snapshot_append_repeating_linear_gradient(snapshot, bounds, &s->p0, &end, m, n);
		g_free(m);
	}
}

static void append_radial(GtkSnapshot *snapshot, const struct AndroidShader *s, const graphene_rect_t *bounds)
{
	if (s->radius <= 0) {
		gtk_snapshot_append_color(snapshot, &s->stops[s->n_stops - 1].color, bounds);
		return;
	}
	if (s->tile_x == TILE_CLAMP) {
		gtk_snapshot_append_radial_gradient(snapshot, bounds, &s->p0, s->radius, s->radius, 0, 1, s->stops, s->n_stops);
	} else if (s->tile_x == TILE_REPEAT) {
		gtk_snapshot_append_repeating_radial_gradient(snapshot, bounds, &s->p0, s->radius, s->radius, 0, 1, s->stops, s->n_stops);
	} else {
		gsize n;
		GskColorStop *m = mirrored_stops(s, &n);
		gtk_snapshot_append_repeating_radial_gradient(snapshot, bounds, &s->p0, s->radius, s->radius, 0, 2, m, n);
		g_free(m);
	}
}

static void append_sweep(GtkSnapshot *snapshot, const struct AndroidShader *s, const graphene_rect_t *bounds)
{
	/* Android sweeps clockwise from 3 o'clock, GSK conic gradients from 12 o'clock */
	gtk_snapshot_append_conic_gradient(snapshot, bounds, &s->p0, 90, s->stops, s->n_stops);
}

/* one axis of a CLAMP bitmap: 0 = before the image (first pixel stretched),
 * 1 = over the image, 2 = after it (last pixel stretched). Returns false if
 * the region is empty; sets the region extent and the texture placement. */
static bool clamp_axis(int region, float lo, float hi, int size, float *r_lo, float *r_hi, float *origin, float *scale)
{
	switch (region) {
		case 0:
			*r_lo = lo, *r_hi = MIN(hi, 0);
			*scale = -lo;
			*origin = lo;
			break;
		case 1:
			*r_lo = MAX(lo, 0), *r_hi = MIN(hi, size);
			*scale = 1;
			*origin = 0;
			break;
		default:
			*r_lo = MAX(lo, size), *r_hi = hi;
			*scale = hi - size;
			*origin = size - (size - 1) * *scale;
			break;
	}
	return *r_hi - *r_lo > 0.001f && *scale > 0;
}

static void append_bitmap(GtkSnapshot *snapshot, const struct AndroidShader *s, const graphene_rect_t *bounds)
{
	if (!s->texture)
		return;
	int w = gdk_texture_get_width(s->texture), h = gdk_texture_get_height(s->texture);
	if (s->tile_x == TILE_CLAMP && s->tile_y == TILE_CLAMP) {
		float x0 = bounds->origin.x, x1 = x0 + bounds->size.width;
		float y0 = bounds->origin.y, y1 = y0 + bounds->size.height;
		for (int ry = 0; ry < 3; ry++) {
			float ylo, yhi, oy, sy;
			if (!clamp_axis(ry, y0, y1, h, &ylo, &yhi, &oy, &sy))
				continue;
			for (int rx = 0; rx < 3; rx++) {
				float xlo, xhi, ox, sx;
				if (!clamp_axis(rx, x0, x1, w, &xlo, &xhi, &ox, &sx))
					continue;
				gtk_snapshot_push_clip(snapshot, &GRAPHENE_RECT_INIT(xlo, ylo, xhi - xlo, yhi - ylo));
				gtk_snapshot_append_scaled_texture(snapshot, s->texture,
				                                   (rx == 1 && ry == 1) ? GSK_SCALING_FILTER_LINEAR : GSK_SCALING_FILTER_NEAREST,
				                                   &GRAPHENE_RECT_INIT(ox, oy, w * sx, h * sy));
				gtk_snapshot_pop(snapshot);
			}
		}
		return;
	}
	/* REPEAT / MIRROR (a CLAMP axis mixed with a tiled one is tiled too) */
	bool mx = s->tile_x == TILE_MIRROR, my = s->tile_y == TILE_MIRROR;
	gtk_snapshot_push_repeat(snapshot, bounds, &GRAPHENE_RECT_INIT(0, 0, w * (mx ? 2 : 1), h * (my ? 2 : 1)));
	for (int fy = 0; fy <= (my ? 1 : 0); fy++) {
		for (int fx = 0; fx <= (mx ? 1 : 0); fx++) {
			gtk_snapshot_save(snapshot);
			if (fx) {
				gtk_snapshot_translate(snapshot, &GRAPHENE_POINT_INIT(2 * w, 0));
				gtk_snapshot_scale(snapshot, -1, 1);
			}
			if (fy) {
				gtk_snapshot_translate(snapshot, &GRAPHENE_POINT_INIT(0, 2 * h));
				gtk_snapshot_scale(snapshot, 1, -1);
			}
			gtk_snapshot_append_texture(snapshot, s->texture, &GRAPHENE_RECT_INIT(0, 0, w, h));
			gtk_snapshot_restore(snapshot);
		}
	}
	gtk_snapshot_pop(snapshot);
}

static void append_masked(GtkSnapshot *snapshot, const struct AndroidShader *mask, GskMaskMode mode, const struct AndroidShader *source, const graphene_rect_t *bounds)
{
	gtk_snapshot_push_mask(snapshot, mode);
	android_shader_append(snapshot, mask, bounds);
	gtk_snapshot_pop(snapshot);
	android_shader_append(snapshot, source, bounds);
	gtk_snapshot_pop(snapshot);
}

static void append_compose(GtkSnapshot *snapshot, const struct AndroidShader *s, const graphene_rect_t *bounds)
{
	const struct AndroidShader *dst = s->a, *src = s->b;
	GskBlendMode blend;
	switch (s->mode) {
		case COMPOSE_CLEAR:
			return;
		case COMPOSE_SRC:
			android_shader_append(snapshot, src, bounds);
			return;
		case COMPOSE_DST:
			android_shader_append(snapshot, dst, bounds);
			return;
		case COMPOSE_DST_OVER:
			android_shader_append(snapshot, src, bounds);
			android_shader_append(snapshot, dst, bounds);
			return;
		case COMPOSE_SRC_IN:
			append_masked(snapshot, dst, GSK_MASK_MODE_ALPHA, src, bounds);
			return;
		case COMPOSE_DST_IN:
			append_masked(snapshot, src, GSK_MASK_MODE_ALPHA, dst, bounds);
			return;
		case COMPOSE_SRC_OUT:
			append_masked(snapshot, dst, GSK_MASK_MODE_INVERTED_ALPHA, src, bounds);
			return;
		case COMPOSE_DST_OUT:
			append_masked(snapshot, src, GSK_MASK_MODE_INVERTED_ALPHA, dst, bounds);
			return;
		case COMPOSE_SRC_ATOP: /* dst, then src where dst is */
			android_shader_append(snapshot, dst, bounds);
			append_masked(snapshot, dst, GSK_MASK_MODE_ALPHA, src, bounds);
			return;
		case COMPOSE_DST_ATOP:
			android_shader_append(snapshot, src, bounds);
			append_masked(snapshot, src, GSK_MASK_MODE_ALPHA, dst, bounds);
			return;
		case COMPOSE_XOR:
			append_masked(snapshot, src, GSK_MASK_MODE_INVERTED_ALPHA, dst, bounds);
			append_masked(snapshot, dst, GSK_MASK_MODE_INVERTED_ALPHA, src, bounds);
			return;
		case COMPOSE_MULTIPLY: blend = GSK_BLEND_MODE_MULTIPLY; break;
		case COMPOSE_SCREEN: blend = GSK_BLEND_MODE_SCREEN; break;
		case COMPOSE_OVERLAY: blend = GSK_BLEND_MODE_OVERLAY; break;
		case COMPOSE_DARKEN: blend = GSK_BLEND_MODE_DARKEN; break;
		case COMPOSE_LIGHTEN: blend = GSK_BLEND_MODE_LIGHTEN; break;
		case COMPOSE_SRC_OVER:
		case COMPOSE_ADD: /* GSK has no additive blend: drawn as SRC_OVER */
		default:
			android_shader_append(snapshot, dst, bounds);
			android_shader_append(snapshot, src, bounds);
			return;
	}
	gtk_snapshot_push_blend(snapshot, blend);
	android_shader_append(snapshot, dst, bounds); /* bottom */
	gtk_snapshot_pop(snapshot);
	android_shader_append(snapshot, src, bounds); /* top */
	gtk_snapshot_pop(snapshot);
}

/* Fill `bounds` (in the current coordinate system) with the shader. */
void android_shader_append(GtkSnapshot *snapshot, const struct AndroidShader *s, const graphene_rect_t *bounds)
{
	graphene_rect_t b = *bounds;
	if (s->has_local) {
		graphene_matrix_t inverse;
		if (!graphene_matrix_inverse(&s->local, &inverse))
			return; /* singular local matrix: nothing visible */
		gtk_snapshot_save(snapshot);
		snapshot_transform_android_matrix(snapshot, &s->local);
		graphene_matrix_transform_bounds(&inverse, bounds, &b);
	}
	switch (s->type) {
		case SHADER_LINEAR: append_linear(snapshot, s, &b); break;
		case SHADER_RADIAL: append_radial(snapshot, s, &b); break;
		case SHADER_SWEEP: append_sweep(snapshot, s, &b); break;
		case SHADER_BITMAP: append_bitmap(snapshot, s, &b); break;
		case SHADER_COMPOSE: append_compose(snapshot, s, &b); break;
	}
	if (s->has_local)
		gtk_snapshot_restore(snapshot);
}

void paint_pop_shader(GtkSnapshot *snapshot, struct AndroidPaint *paint, const graphene_rect_t *bounds)
{
	gtk_snapshot_pop(snapshot); /* end of the mask (the shape) */
	/* the paint's alpha modulates the shader; its RGB is ignored, as on Android */
	bool fade = paint->color.alpha < 1.f;
	if (fade)
		gtk_snapshot_push_opacity(snapshot, paint->color.alpha);
	android_shader_append(snapshot, paint->shader, bounds);
	if (fade)
		gtk_snapshot_pop(snapshot);
	gtk_snapshot_pop(snapshot); /* end of the masked source */
}

/* ---- JNI ---- */

JNIEXPORT void JNICALL Java_android_graphics_Shader_native_1unref(JNIEnv *env, jclass clazz, jlong shader_ptr)
{
	if (shader_ptr)
		android_shader_unref(_PTR(shader_ptr));
}

JNIEXPORT void JNICALL Java_android_graphics_Shader_native_1set_1local_1matrix(JNIEnv *env, jclass clazz, jlong shader_ptr, jlong matrix_ptr)
{
	struct AndroidShader *s = _PTR(shader_ptr);
	if (matrix_ptr) {
		graphene_matrix_init_from_matrix(&s->local, _PTR(matrix_ptr));
		s->has_local = !graphene_matrix_is_identity(&s->local);
	} else {
		s->has_local = false;
	}
}

JNIEXPORT jlong JNICALL Java_android_graphics_LinearGradient_native_1create(JNIEnv *env, jclass clazz, jfloat x0, jfloat y0, jfloat x1, jfloat y1, jintArray colors, jfloatArray positions, jint tile)
{
	struct AndroidShader *s = shader_new(SHADER_LINEAR);
	s->p0 = GRAPHENE_POINT_INIT(x0, y0);
	s->p1 = GRAPHENE_POINT_INIT(x1, y1);
	s->tile_x = s->tile_y = tile;
	set_stops(env, s, colors, positions);
	return _INTPTR(s);
}

JNIEXPORT jlong JNICALL Java_android_graphics_RadialGradient_native_1create(JNIEnv *env, jclass clazz, jfloat cx, jfloat cy, jfloat radius, jintArray colors, jfloatArray positions, jint tile)
{
	struct AndroidShader *s = shader_new(SHADER_RADIAL);
	s->p0 = GRAPHENE_POINT_INIT(cx, cy);
	s->radius = radius;
	s->tile_x = s->tile_y = tile;
	set_stops(env, s, colors, positions);
	return _INTPTR(s);
}

JNIEXPORT jlong JNICALL Java_android_graphics_SweepGradient_nativeCreate1(JNIEnv *env, jclass clazz, jfloat cx, jfloat cy, jintArray colors, jfloatArray positions)
{
	struct AndroidShader *s = shader_new(SHADER_SWEEP);
	s->p0 = GRAPHENE_POINT_INIT(cx, cy);
	set_stops(env, s, colors, positions);
	return _INTPTR(s);
}

JNIEXPORT jlong JNICALL Java_android_graphics_SweepGradient_nativeCreate2(JNIEnv *env, jclass clazz, jfloat cx, jfloat cy, jint color0, jint color1)
{
	struct AndroidShader *s = shader_new(SHADER_SWEEP);
	s->p0 = GRAPHENE_POINT_INIT(cx, cy);
	s->n_stops = 2;
	s->stops = g_new(GskColorStop, 2);
	s->stops[0] = (GskColorStop){0, argb_to_rgba(color0)};
	s->stops[1] = (GskColorStop){1, argb_to_rgba(color1)};
	return _INTPTR(s);
}

JNIEXPORT jlong JNICALL Java_android_graphics_BitmapShader_native_1create(JNIEnv *env, jclass clazz, jlong texture_ptr, jint tile_x, jint tile_y)
{
	struct AndroidShader *s = shader_new(SHADER_BITMAP);
	if (texture_ptr)
		s->texture = g_object_ref(GDK_TEXTURE(_PTR(texture_ptr)));
	s->tile_x = tile_x;
	s->tile_y = tile_y;
	return _INTPTR(s);
}

JNIEXPORT jlong JNICALL Java_android_graphics_ComposeShader_native_1create(JNIEnv *env, jclass clazz, jlong dst_ptr, jlong src_ptr, jint mode)
{
	struct AndroidShader *s = shader_new(SHADER_COMPOSE);
	s->a = android_shader_ref(_PTR(dst_ptr));
	s->b = android_shader_ref(_PTR(src_ptr));
	s->mode = mode;
	return _INTPTR(s);
}
