/*
 * Android PathMeasure implementation for Android Translation Layer
 *
 * SPDX-License-Identifier: AGPL-3.0-or-later
 *
 * Flattens a GskPath into polylines (one per contour) so the Java
 * PathMeasure can answer getLength()/getPosTan()/getSegment() from real
 * geometry instead of the previous stub.
 */

#include <gsk/gsk.h>
#include <math.h>
#include <stdlib.h>
#include <string.h>

#include "../defines.h"
#include "../generated_headers/android_graphics_PathMeasure.h"

struct pts {
	float *xy;  /* x,y pairs */
	gsize n;    /* number of points */
	gsize cap;
};

struct flatten {
	GPtrArray *contours;      /* struct pts* (heap) */
	struct pts cur;           /* contour under construction */
	float start_x, start_y;   /* first point of the current contour */
};

static void pts_add(struct pts *p, float x, float y)
{
	if (p->n == p->cap) {
		p->cap = p->cap ? p->cap * 2 : 64;
		p->xy = realloc(p->xy, p->cap * 2 * sizeof(float));
	}
	p->xy[p->n * 2] = x;
	p->xy[p->n * 2 + 1] = y;
	p->n++;
}

static void contour_finish(struct flatten *f)
{
	if (f->cur.n == 0)
		return;
	struct pts *heap = g_new(struct pts, 1);
	*heap = f->cur;
	g_ptr_array_add(f->contours, heap);
	f->cur.xy = NULL;
	f->cur.n = 0;
	f->cur.cap = 0;
}

static void contour_start(struct flatten *f, float x, float y)
{
	contour_finish(f);
	pts_add(&f->cur, x, y);
	f->start_x = x;
	f->start_y = y;
}

static float pen_x(struct flatten *f)
{
	return f->cur.xy[f->cur.n * 2 - 2];
}

static float pen_y(struct flatten *f)
{
	return f->cur.xy[f->cur.n * 2 - 1];
}

/* ~100 subdivision steps per unit of chord length, clamped; the callers
 * (PathInterpolator, VectorDrawable trim path) sample at a coarser
 * interval than this, so the polyline error is not visible. */
static int steps_for(struct flatten *f, float ex, float ey)
{
	float dx = ex - pen_x(f), dy = ey - pen_y(f);
	int steps = (int) ceilf(sqrtf(dx * dx + dy * dy) * 100.0f);
	if (steps < 16)
		steps = 16;
	if (steps > 1024)
		steps = 1024;
	return steps;
}

static void cubic_to_pts(struct flatten *f, float x1, float y1, float x2, float y2, float x3, float y3)
{
	float x0 = pen_x(f), y0 = pen_y(f);
	int steps = steps_for(f, x3, y3);
	for (int i = 1; i <= steps; i++) {
		float t = i / (float) steps, mt = 1.0f - t;
		float a = mt * mt * mt, b = 3.0f * mt * mt * t, c = 3.0f * mt * t * t, d = t * t * t;
		pts_add(&f->cur, a * x0 + b * x1 + c * x2 + d * x3, a * y0 + b * y1 + c * y2 + d * y3);
	}
}

static void quad_to_pts(struct flatten *f, float x1, float y1, float x2, float y2)
{
	float x0 = pen_x(f), y0 = pen_y(f);
	int steps = steps_for(f, x2, y2);
	for (int i = 1; i <= steps; i++) {
		float t = i / (float) steps, mt = 1.0f - t;
		float a = mt * mt, b = 2.0f * mt * t, c = t * t;
		pts_add(&f->cur, a * x0 + b * x1 + c * x2, a * y0 + b * y1 + c * y2);
	}
}

static void conic_to_pts(struct flatten *f, float x1, float y1, float x2, float y2, float w)
{
	float x0 = pen_x(f), y0 = pen_y(f);
	int steps = steps_for(f, x2, y2);
	for (int i = 1; i <= steps; i++) {
		float t = i / (float) steps, mt = 1.0f - t;
		float w0 = mt * mt, w1 = 2.0f * w * mt * t, w2 = t * t;
		float s = w0 + w1 + w2;
		pts_add(&f->cur, (w0 * x0 + w1 * x1 + w2 * x2) / s, (w0 * y0 + w1 * y1 + w2 * y2) / s);
	}
}

static gboolean flatten_foreach(GskPathOperation op, const graphene_point_t *pts, gsize n_pts, float weight, gpointer user_data)
{
	struct flatten *f = user_data;
	(void) n_pts;
	switch (op) {
		case GSK_PATH_MOVE:
			contour_start(f, pts[0].x, pts[0].y);
			break;
		case GSK_PATH_CLOSE:
			if (f->cur.n > 1)
				pts_add(&f->cur, f->start_x, f->start_y);
			break;
		case GSK_PATH_LINE:
			if (f->cur.n == 0)
				contour_start(f, pts[0].x, pts[0].y);
			pts_add(&f->cur, pts[1].x, pts[1].y);
			break;
		case GSK_PATH_QUAD:
			if (f->cur.n == 0)
				contour_start(f, pts[0].x, pts[0].y);
			quad_to_pts(f, pts[1].x, pts[1].y, pts[2].x, pts[2].y);
			break;
		case GSK_PATH_CUBIC:
			if (f->cur.n == 0)
				contour_start(f, pts[0].x, pts[0].y);
			cubic_to_pts(f, pts[1].x, pts[1].y, pts[2].x, pts[2].y, pts[3].x, pts[3].y);
			break;
		case GSK_PATH_CONIC:
			if (f->cur.n == 0)
				contour_start(f, pts[0].x, pts[0].y);
			conic_to_pts(f, pts[1].x, pts[1].y, pts[2].x, pts[2].y, weight);
			break;
	}
	return TRUE;
}

/*
 * Returns the flattened path as [count, x0, y0, ..., count, x0, y0, ...]
 * (one block per contour), or NULL if the path has no points.
 */
JNIEXPORT jfloatArray JNICALL Java_android_graphics_PathMeasure_native_1flatten(JNIEnv *env, jclass this, jlong path_ptr)
{
	if (path_ptr == 0)
		return NULL;

	GskPath *path = _PTR(path_ptr);
	struct flatten f;
	f.contours = g_ptr_array_new();
	memset(&f.cur, 0, sizeof(f.cur));
	f.start_x = f.start_y = 0;

	gsk_path_foreach(path,
		GSK_PATH_FOREACH_ALLOW_QUAD | GSK_PATH_FOREACH_ALLOW_CUBIC | GSK_PATH_FOREACH_ALLOW_CONIC,
		flatten_foreach, &f);
	contour_finish(&f);

	jsize total = 0;
	for (guint i = 0; i < f.contours->len; i++) {
		struct pts *p = f.contours->pdata[i];
		total += 1 + (jsize) (p->n * 2);
	}

	jfloatArray result = NULL;
	if (total > 0) {
		float *buf = malloc((size_t) total * sizeof(float));
		jsize idx = 0;
		for (guint i = 0; i < f.contours->len; i++) {
			struct pts *p = f.contours->pdata[i];
			buf[idx++] = (float) p->n;
			memcpy(buf + idx, p->xy, p->n * 2 * sizeof(float));
			idx += (jsize) (p->n * 2);
		}
		result = (*env)->NewFloatArray(env, total);
		if (result)
			(*env)->SetFloatArrayRegion(env, result, 0, total, buf);
		free(buf);
	}

	for (guint i = 0; i < f.contours->len; i++) {
		struct pts *p = f.contours->pdata[i];
		free(p->xy);
		g_free(p);
	}
	g_ptr_array_free(f.contours, FALSE);
	free(f.cur.xy);

	return result;
}
