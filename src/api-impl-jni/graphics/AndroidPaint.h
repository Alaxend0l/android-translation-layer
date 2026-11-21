#include "pango/pango-layout.h"
#include <gdk/gdk.h>
#include <gsk/gsk.h>
#include <pango/pango.h>
#include <gtk/gtk.h>
#include <stdbool.h>
#include <math.h>

struct AndroidShader;

struct AndroidPaint {
	struct AndroidShader *shader; /* owned reference, or NULL */
	GdkRGBA color;
	GskStroke *gsk_stroke;
	PangoFontDescription *font;
	PangoAlignment alignment;
	graphene_matrix_t color_matrix;
	graphene_vec4_t color_offset;
	bool is_fill : 1;
	bool is_stroke : 1;
	bool use_color_filter : 1;
};

/* shaders (android_graphics_Shader.c) */
struct AndroidShader *android_shader_ref(struct AndroidShader *shader);
void android_shader_unref(struct AndroidShader *shader);
void android_shader_append(GtkSnapshot *snapshot, const struct AndroidShader *shader, const graphene_rect_t *bounds);

/* Drawing with a paint that may have a shader:
 *   bool shaded = paint_push_shader(snapshot, paint);
 *   ...draw the shape with paint_draw_color(paint)...
 *   if (shaded) paint_pop_shader(snapshot, paint, &shape_bounds);
 * With a shader the shape becomes an alpha mask over the shader's fill. */
static inline bool paint_push_shader(GtkSnapshot *snapshot, struct AndroidPaint *paint)
{
	if (!paint->shader)
		return false;
	gtk_snapshot_push_mask(snapshot, GSK_MASK_MODE_ALPHA);
	return true;
}

static inline const GdkRGBA *paint_draw_color(const struct AndroidPaint *paint)
{
	static const GdkRGBA opaque = {1, 1, 1, 1};
	return paint->shader ? &opaque : &paint->color;
}

void paint_pop_shader(GtkSnapshot *snapshot, struct AndroidPaint *paint, const graphene_rect_t *bounds);

/* Apply an android.graphics.Matrix (graphene 4x4) to a snapshot. A 2D affine
 * matrix is passed as translate * rotate * skew * scale, which GSK knows to be
 * 2D: gtk_snapshot_transform_matrix() marks any matrix as of unknown category,
 * and the Cairo renderer (used for Bitmaps drawn off the main thread, e.g. by
 * Glide) paints such transforms hot pink instead of drawing them. */
static inline void snapshot_transform_android_matrix(GtkSnapshot *snapshot, const graphene_matrix_t *m)
{
	double a, b, c, d, e, f; /* x' = a x + c y + e, y' = b x + d y + f */
	if (!graphene_matrix_is_2d(m) || !graphene_matrix_to_2d(m, &a, &b, &c, &d, &e, &f)) {
		gtk_snapshot_transform_matrix(snapshot, m);
		return;
	}
	double sx = sqrt(a * a + b * b);
	if (sx < 1e-12) { /* degenerate (collapses x): keep the generic path */
		gtk_snapshot_transform_matrix(snapshot, m);
		return;
	}
	double rotation = atan2(b, a);
	double k = (a * c + b * d) / sx; /* shear */
	double sy = (a * d - b * c) / sx; /* may be negative: mirroring */
	GskTransform *t = gsk_transform_translate(NULL, &GRAPHENE_POINT_INIT(e, f));
	t = gsk_transform_rotate(t, rotation * 180 / M_PI);
	if (fabs(k) > 1e-12 && fabs(sy) > 1e-12)
		t = gsk_transform_skew(t, atan(k / sy) * 180 / M_PI, 0);
	t = gsk_transform_scale(t, sx, sy);
	gtk_snapshot_transform(snapshot, t);
	gsk_transform_unref(t);
}
