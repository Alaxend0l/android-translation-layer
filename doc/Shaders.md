# Shaders and canvas transforms

## Shaders (android.graphics.Shader)
`LinearGradient`, `RadialGradient`, `SweepGradient`, `BitmapShader` and
`ComposeShader` create a native, reference-counted `struct AndroidShader`
(`src/api-impl-jni/graphics/android_graphics_Shader.c`); `Paint.setShader`
gives the native paint a reference. Every GskCanvas draw call (rect, round
rect/oval/circle, path, line, text) draws through the same helper: the shape
becomes an alpha mask (`gtk_snapshot_push_mask`) over the shader's fill, and
the paint's alpha modulates it (its RGB is ignored, as on Android).

| Shader | GSK | Notes |
|---|---|---|
| Linear | (repeating) linear gradient | MIRROR = repeating gradient over twice the length with mirrored stops |
| Radial | (repeating) radial gradient | same for MIRROR |
| Sweep | conic gradient | starts at 3 o'clock like Android |
| Bitmap | texture; repeat node for REPEAT/MIRROR | CLAMP stretches edge pixels (nearest filter) around the image; an axis with CLAMP mixed with a tiled axis is tiled |
| Compose | blend node (MULTIPLY, SCREEN, OVERLAY, DARKEN, LIGHTEN) or masks (SRC_IN, DST_IN, SRC_OUT, DST_OUT, XOR, ATOP) | ADD is drawn as SRC_OVER; ATOP is approximated |

Local matrices are honoured. `BitmapShader` takes the bitmap's pixels when it
is created. GradientDrawable's sweep gradients are enabled again.

## Transforms: never `gtk_snapshot_transform_matrix` for Android matrices
It marks the transform as of unknown category, and GSK's Cairo renderer (used
for Bitmaps drawn off the main thread) paints such transforms hot pink
(255,105,180) instead of drawing them. `snapshot_transform_android_matrix()`
(AndroidPaint.h) passes 2D affine matrices as translate × rotate × skew ×
scale instead.

## Debugging
`ATL_DUMP_NODES=DIR` writes each Bitmap's render tree (GSK text format) to
DIR; `gtk4-rendernode-tool show|render FILE` displays it.
