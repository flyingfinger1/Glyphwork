package de.flyingfinger.glyphwork.font;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import de.flyingfinger.glyphwork.pack.PackFiles;
import net.kyori.adventure.key.Key;
import org.jspecify.annotations.Nullable;

import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.Font;
import java.awt.FontFormatException;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.font.FontRenderContext;
import java.awt.font.GlyphVector;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * An own font from a TrueType/OpenType file, turned into a bitmap sheet when the pack is built.
 *
 * <p>The server must know every glyph's width exactly (wrapping, cutting, centering), so the font
 * is not handed to the client as TrueType (it rasterizes and rounds by itself): Glyphwork draws
 * each character at the given pixel size without anti-aliasing, moves it to the left edge of its
 * cell and uses the client's bitmap rule, advance = width + 1. Best with pixel fonts.
 *
 * <p>Placement is like the vanilla glyphs: the top of the capitals sits at the text's {@code y}.
 * Characters the font does not have come from the vanilla glyphs (they follow in the same font,
 * so the client falls back in the same order as {@link #width}).
 */
public final class CustomFont implements TextFont {

    /** Font ids: lower case letters, digits, '_' and '-' (they become part of pack file names). */
    public static final Pattern ID = Pattern.compile("[a-z0-9_-]{1,32}");
    public static final int MIN_SIZE = 6;
    public static final int MAX_SIZE = 48;
    /** The client's font atlas takes glyph cells up to this size. */
    private static final int MAX_CELL = 128;
    private static final int COLUMNS = 16;
    /** Characters tried by default: ASCII, Latin-1, Latin Extended-A and common punctuation. */
    private static final String DEFAULT_CHARS;

    static {
        StringBuilder b = new StringBuilder();
        for (int c = 0x21; c < 0x7F; c++) {
            b.append((char) c);
        }
        for (int c = 0xA1; c <= 0x17F; c++) {
            if (c != 0xAD) {                          // soft hyphen: invisible
                b.append((char) c);
            }
        }
        b.append("€„“”‚‘’–—…•‹›™←↑→↓");
        DEFAULT_CHARS = b.toString();
    }

    private final String id;
    private final VanillaText fallback;
    private final Map<Integer, Integer> advances;
    private final int space;
    private final List<String> rows;
    private final int cellHeight;
    private final int offset;
    private final int capHeight;
    private final int descent;
    private final int lineHeight;
    private final byte[] png;

    private CustomFont(String id, VanillaText fallback, Map<Integer, Integer> advances, int space, List<String> rows,
                       int cellHeight, int offset, int capHeight, int descent, int lineHeight, byte[] png) {
        this.id = id;
        this.fallback = fallback;
        this.advances = advances;
        this.space = space;
        this.rows = rows;
        this.cellHeight = cellHeight;
        this.offset = offset;
        this.capHeight = capHeight;
        this.descent = descent;
        this.lineHeight = lineHeight;
        this.png = png;
    }

    /**
     * Rasterizes a TrueType/OpenType font.
     *
     * @param size       pixel size the font is drawn at ({@link #MIN_SIZE}..{@link #MAX_SIZE})
     * @param lineHeight distance of wrapped lines, or 0 for one that fits the glyphs
     * @param extraChars characters to add to the default set (others than Latin)
     * @throws IOException with a readable message if the file is no usable font
     */
    public static CustomFont rasterize(String id, byte[] fontFile, int size, int lineHeight, @Nullable String extraChars,
                                       VanillaText fallback) throws IOException {
        if (!ID.matcher(id).matches()) {
            throw new IOException("font id '" + id + "': use lower case letters, digits, '_' and '-'");
        }
        if (size < MIN_SIZE || size > MAX_SIZE) {
            throw new IOException("size " + size + ": use " + MIN_SIZE + " to " + MAX_SIZE);
        }
        Font font;
        try {
            font = Font.createFont(Font.TRUETYPE_FONT, new ByteArrayInputStream(fontFile)).deriveFont((float) size);
        } catch (FontFormatException e) {
            throw new IOException("not a TrueType/OpenType font (" + e.getMessage() + ")");
        }
        String chars = DEFAULT_CHARS + (extraChars == null ? "" : extraChars);
        FontRenderContext frc = new FontRenderContext(null, false, false);
        int canvas = size * 4;
        int base = size * 3;                          // baseline: room above for accents
        List<Integer> cps = new ArrayList<>();
        List<int[]> boxes = new ArrayList<>();        // minX, maxX, minY, maxY on the canvas
        List<BufferedImage> drawn = new ArrayList<>();
        java.util.Set<Integer> seen = new java.util.HashSet<>();
        for (int i = 0; i < chars.length(); ) {
            int cp = chars.codePointAt(i);
            i += Character.charCount(cp);
            if (!seen.add(cp) || Character.isWhitespace(cp) || !font.canDisplay(cp)) {
                continue;
            }
            BufferedImage img = new BufferedImage(canvas, canvas, BufferedImage.TYPE_INT_ARGB);
            Graphics2D g = img.createGraphics();
            g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_OFF);
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_OFF);
            g.setRenderingHint(RenderingHints.KEY_FRACTIONALMETRICS, RenderingHints.VALUE_FRACTIONALMETRICS_OFF);
            g.setColor(Color.WHITE);
            GlyphVector gv = font.createGlyphVector(frc, new String(Character.toChars(cp)));
            g.drawGlyphVector(gv, size, base);
            g.dispose();
            int[] box = {Integer.MAX_VALUE, -1, Integer.MAX_VALUE, -1};
            for (int y = 0; y < canvas; y++) {
                for (int x = 0; x < canvas; x++) {
                    if ((img.getRGB(x, y) >>> 24) != 0) {
                        box[0] = Math.min(box[0], x);
                        box[1] = Math.max(box[1], x);
                        box[2] = Math.min(box[2], y);
                        box[3] = Math.max(box[3], y);
                    }
                }
            }
            if (box[1] < 0) {
                continue;                             // nothing drawn: the client would show nothing either
            }
            cps.add(cp);
            boxes.add(box);
            drawn.add(img);
        }
        if (cps.isEmpty()) {
            throw new IOException("the font draws none of the Latin characters");
        }
        int top = Integer.MAX_VALUE;
        int bottom = -1;
        int capTop = Integer.MAX_VALUE;
        int width = 1;
        for (int i = 0; i < cps.size(); i++) {
            int[] b = boxes.get(i);
            top = Math.min(top, b[2]);
            bottom = Math.max(bottom, b[3]);
            width = Math.max(width, b[1] - b[0] + 1);
            if (cps.get(i) == 'H') {
                capTop = b[2];
            }
        }
        if (capTop == Integer.MAX_VALUE) {            // no H: the tallest capital, else the tallest glyph
            for (int i = 0; i < cps.size(); i++) {
                if (cps.get(i) >= 'A' && cps.get(i) <= 'Z') {
                    capTop = Math.min(capTop, boxes.get(i)[2]);
                }
            }
            if (capTop == Integer.MAX_VALUE) {
                capTop = top;
            }
        }
        int offset = capTop - top;                    // rows above the capitals (accents)
        int capHeight = base - capTop;
        int descent = Math.max(0, bottom - base + 1);
        // the client wants ascent <= height; the font for shift 0 has ascent 7 + offset
        int cellHeight = Math.max(bottom - top + 1, 7 + offset);
        if (width > MAX_CELL || cellHeight > MAX_CELL) {
            throw new IOException("glyphs up to " + width + " x " + cellHeight + " px; the client takes "
                + MAX_CELL + " at most (use a smaller size)");
        }
        int sheetRows = (cps.size() + COLUMNS - 1) / COLUMNS;
        BufferedImage sheet = new BufferedImage(COLUMNS * width, sheetRows * cellHeight, BufferedImage.TYPE_INT_ARGB);
        Map<Integer, Integer> advances = new HashMap<>();
        List<String> rows = new ArrayList<>();
        StringBuilder row = new StringBuilder();
        for (int i = 0; i < sheetRows * COLUMNS; i++) {
            if (i < cps.size()) {
                int[] b = boxes.get(i);
                BufferedImage img = drawn.get(i);
                int cx = (i % COLUMNS) * width;
                int cy = (i / COLUMNS) * cellHeight;
                for (int y = b[2]; y <= b[3]; y++) {
                    for (int x = b[0]; x <= b[1]; x++) {
                        if ((img.getRGB(x, y) >>> 24) != 0) {
                            sheet.setRGB(cx + x - b[0], cy + y - top, 0xFFFFFFFF);
                        }
                    }
                }
                advances.put(cps.get(i), b[1] - b[0] + 2);   // drawn width + 1, the client's bitmap rule
                row.appendCodePoint(cps.get(i));
            } else {
                row.append('\u0000');
            }
            if (i % COLUMNS == COLUMNS - 1) {
                rows.add(row.toString());
                row.setLength(0);
            }
        }
        int space = Math.max(1, Math.round((float) font.createGlyphVector(frc, " ").getGlyphMetrics(0).getAdvance()));
        int lines = lineHeight > 0 ? lineHeight : Math.max(10, capHeight + descent + 2);
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ImageIO.write(sheet, "png", out);
        return new CustomFont(id, fallback, advances, space, rows, cellHeight, offset, capHeight, descent, lines,
            out.toByteArray());
    }

    public String id() {
        return id;
    }

    /** Characters the font itself draws. */
    public int glyphs() {
        return advances.size();
    }

    @Override
    public int width(String text) {
        int w = 0;
        for (int i = 0; i < text.length(); ) {
            int cp = text.codePointAt(i);
            Integer a = advances.get(cp);
            if (a != null) {
                w += a;
            } else if (cp == ' ' || cp == VanillaText.NB_SPACE || cp == 0xA0) {
                w += space;
            } else {
                w += fallback.width(new String(Character.toChars(cp)));
            }
            i += Character.charCount(cp);
        }
        return w;
    }

    @Override
    public Key key(int shift) {
        return Key.key("glyphwork", "f_" + id + "_" + shift);
    }

    @Override
    public int lineHeight() {
        return lineHeight;
    }

    @Override
    public int height() {
        return capHeight + descent;
    }

    /**
     * The font for the menu designer ({@code <id>.glyphwork-font} in plugins/Glyphwork/fonts/): JSON
     * with the sheet (base64 PNG, 16 columns), its characters, the widths and the placement, so the
     * designer measures and draws exactly like the plugin.
     */
    public String designerFile() {
        JsonObject o = new JsonObject();
        o.addProperty("format", "glyphwork-font");
        o.addProperty("version", 1);
        o.addProperty("id", id);
        o.addProperty("cellHeight", cellHeight);
        o.addProperty("offset", offset);
        o.addProperty("lineHeight", lineHeight);
        o.addProperty("height", capHeight + descent);
        o.addProperty("space", space);
        JsonArray chars = new JsonArray();
        rows.forEach(chars::add);
        o.add("chars", chars);
        JsonObject adv = new JsonObject();
        advances.forEach((cp, a) -> adv.addProperty(new String(Character.toChars(cp)), a));
        o.add("advances", adv);
        o.addProperty("png", java.util.Base64.getEncoder().encodeToString(png));
        return new Gson().toJson(o);
    }

    String texture() {
        return "glyphwork:font/f_" + id + ".png";
    }

    /** The sheet and the fonts for every shift (with the cursor spaces and the vanilla glyphs behind). */
    public void write(PackFiles pack) {
        pack.put("assets/glyphwork/textures/font/f_" + id + ".png", png);
        Gson gson = new Gson();
        for (int s = 0; s < ShiftFonts.LINE; s++) {
            JsonArray providers = new JsonArray();
            providers.add(SpaceFont.provider());
            JsonObject sp = new JsonObject();
            sp.addProperty("type", "space");
            JsonObject adv = new JsonObject();
            adv.addProperty(" ", space);
            adv.addProperty(String.valueOf(VanillaText.NB_SPACE), space);
            adv.addProperty(" ", space);
            sp.add("advances", adv);
            providers.add(sp);
            JsonObject bitmap = new JsonObject();
            bitmap.addProperty("type", "bitmap");
            bitmap.addProperty("file", texture());
            bitmap.addProperty("height", cellHeight);
            bitmap.addProperty("ascent", 7 - s + offset);
            JsonArray chars = new JsonArray();
            rows.forEach(chars::add);
            bitmap.add("chars", chars);
            providers.add(bitmap);
            fallback.addShiftedProviders(providers, s);
            JsonObject font = new JsonObject();
            font.add("providers", providers);
            pack.put("assets/glyphwork/font/f_" + id + "_" + s + ".json", gson.toJson(font));
        }
    }
}
