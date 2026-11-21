package android.graphics;

import android.content.res.AssetManager;
import java.io.File;

/* The Typeface model: family, weight (100..1000) and italic, with AOSP's
 * create/style rules. Drawing doesn't use it yet: TextView and Paint still
 * render with the default font (docs/agent-tasks/text-and-fonts.md steps 8-9:
 * pass family/weight/style to Pango, load font files). */
public class Typeface {
	// Style
	public static final int NORMAL = 0;
	public static final int BOLD = 1;
	public static final int ITALIC = 2;
	public static final int BOLD_ITALIC = 3;

	public static final Typeface DEFAULT = new Typeface(null, 400, false);
	public static final Typeface DEFAULT_BOLD = new Typeface(null, 700, false);
	public static final Typeface SANS_SERIF = new Typeface("sans-serif", 400, false);
	public static final Typeface SERIF = new Typeface("serif", 400, false);
	public static final Typeface MONOSPACE = new Typeface("monospace", 400, false);

	public long native_instance = 0; // directly accessed by androidx

	private final String family; // null = system default
	private final int weight;
	private final boolean italic;
	/** a font file this typeface comes from (createFromFile/Asset), or null */
	String fontPath;

	private Typeface(String family, int weight, boolean italic) {
		this.family = family;
		this.weight = weight;
		this.italic = italic;
	}

	private static int weightFor(int style) {
		return (style & BOLD) != 0 ? 700 : 400;
	}

	/** family name as given (sans-serif, serif, monospace, or a font family), null = default */
	public String getFamilyName() {
		return family;
	}

	public int getStyle() {
		return (weight >= 600 ? BOLD : 0) | (italic ? ITALIC : 0);
	}

	public final boolean isBold() {
		return weight >= 600;
	}

	public final boolean isItalic() {
		return italic;
	}

	public int getWeight() {
		return weight;
	}

	public static Typeface create(String familyName, int style) {
		return new Typeface(familyName, weightFor(style), (style & ITALIC) != 0);
	}

	/* AOSP: the family of `family` with the given style; null = default family */
	public static Typeface create(Typeface family, int style) {
		if ((style & ~BOLD_ITALIC) != 0)
			style = NORMAL;
		Typeface base = family != null ? family : DEFAULT;
		Typeface t = new Typeface(base.family, weightFor(style), (style & ITALIC) != 0);
		t.fontPath = base.fontPath;
		return t;
	}

	/* AOSP (API 28): weight 1..1000 */
	public static Typeface create(Typeface family, int weight, boolean italic) {
		if (weight < 1 || weight > 1000)
			throw new IllegalArgumentException("weight must be in [1, 1000]: " + weight);
		Typeface base = family != null ? family : DEFAULT;
		Typeface t = new Typeface(base.family, weight, italic);
		t.fontPath = base.fontPath;
		return t;
	}

	public static Typeface defaultFromStyle(int style) {
		switch (style) {
			case BOLD: return DEFAULT_BOLD;
			case NORMAL: return DEFAULT;
			default: return create((String)null, style);
		}
	}

	/* font files aren't loaded yet (text-and-fonts.md step 9): the typeface
	 * records the path, and renders with the default font until then */
	public static Typeface createFromAsset(AssetManager mgr, String path) {
		Typeface t = new Typeface(null, 400, false);
		t.fontPath = "asset:" + path;
		return t;
	}

	public static Typeface createFromFile(String path) {
		Typeface t = new Typeface(null, 400, false);
		t.fontPath = path;
		return t;
	}

	public static Typeface createFromFile(File file) {
		return createFromFile(file.getPath());
	}

	public static Typeface createFromFamiliesWithDefault(FontFamily[] families) {
		return DEFAULT;
	}

	public static Typeface createFromFamiliesWithDefault(FontFamily[] families, int weight, int italic) {
		return createFromFamiliesWithDefault(families);
	}

	@Override
	public boolean equals(Object o) {
		if (!(o instanceof Typeface))
			return false;
		Typeface t = (Typeface)o;
		return weight == t.weight && italic == t.italic && java.util.Objects.equals(family, t.family)
		    && java.util.Objects.equals(fontPath, t.fontPath);
	}

	@Override
	public int hashCode() {
		return java.util.Objects.hash(family, weight, italic, fontPath);
	}

	public static class Builder {
		private final String path;

		public Builder(AssetManager mgr, String path) {
			this.path = "asset:" + path;
		}

		public Builder(File file) {
			this.path = file.getPath();
		}

		public Builder(String path) {
			this.path = path;
		}

		public Builder setFontVariationSettings(String settings) {
			return this;
		}

		public Typeface build() {
			Typeface t = new Typeface(null, 400, false);
			t.fontPath = path;
			return t;
		}
	}
}
