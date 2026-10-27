package de.flyingfinger.glyphwork.font;

import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.List;

/**
 * White squares of 1 to 4 px, tinted when drawn: big player heads are painted pixel by pixel from
 * them (8 x 8 face pixels, each a square of 1 to 4 px -> heads of 8 to 32 px). One sheet, cells of
 * 8 x 8 px (the square at the top left, the rest transparent).
 */
public final class SquareSheets {

    public static final int MAX = 4;

    private final Sheet sheet;

    public SquareSheets(GlyphAllocator alloc) {
        List<BufferedImage> cells = new ArrayList<>();
        for (int size = 1; size <= MAX; size++) {
            BufferedImage img = new BufferedImage(8, 8, BufferedImage.TYPE_INT_ARGB);
            for (int y = 0; y < size; y++) {
                for (int x = 0; x < size; x++) {
                    img.setRGB(x, y, 0xFFFFFFFF);
                }
            }
            cells.add(img);
        }
        sheet = new Sheet("glyphwork_squares", cells, MAX, alloc);
    }

    public Sheet sheet() {
        return sheet;
    }

    /** The square of {@code size} px (1..4). */
    public Glyph square(int size) {
        return sheet.glyph(Math.max(1, Math.min(MAX, size)) - 1);
    }
}
