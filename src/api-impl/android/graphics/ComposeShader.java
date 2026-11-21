package android.graphics;

/* Porter-Duff and blend composition of two shaders: shaderA is the
 * destination, shaderB the source (as in AOSP). Mode codes match
 * enum compose_mode in android_graphics_Shader.c. */
public class ComposeShader extends Shader {
	private final Shader shaderA, shaderB; // kept alive with this shader

	public ComposeShader(Shader shaderA, Shader shaderB, PorterDuff.Mode mode) {
		this.shaderA = shaderA;
		this.shaderB = shaderB;
		init(native_create(shaderA.native_shader, shaderB.native_shader, code(mode.name())));
	}

	public ComposeShader(Shader shaderA, Shader shaderB, Xfermode mode) {
		this(shaderA, shaderB, modeOf(mode));
	}

	private static PorterDuff.Mode modeOf(Xfermode xfermode) {
		if (xfermode instanceof PorterDuffXfermode)
			for (PorterDuff.Mode m : PorterDuff.Mode.values())
				if (m.nativeInt == ((PorterDuffXfermode)xfermode).porterDuffMode)
					return m;
		return PorterDuff.Mode.SRC_OVER;
	}

	private static int code(String mode) {
		switch (mode) {
			case "SRC_OVER": return 0;
			case "DST_OVER": return 1;
			case "SRC_IN": return 2;
			case "DST_IN": return 3;
			case "SRC_OUT": return 4;
			case "DST_OUT": return 5;
			case "SRC": return 6;
			case "DST": return 7;
			case "CLEAR": return 8;
			case "SRC_ATOP": return 9;
			case "DST_ATOP": return 10;
			case "XOR": return 11;
			case "MULTIPLY": return 12;
			case "SCREEN": return 13;
			case "OVERLAY": return 14;
			case "DARKEN": return 15;
			case "LIGHTEN": return 16;
			case "ADD": return 17;
			default: return 0;
		}
	}

	private static native long native_create(long dst, long src, int mode);
}
