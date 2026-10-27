package de.flyingfinger.glyphwork.font;

import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.List;

/**
 * White strips for pixel-exact fills (progress bars): widths 1, 2, 4 ... 128; wider fills repeat
 * the 128 px strip. Tinted when drawn. Two heights: 6 px (bars 8 px high) and 14 px (bars 16 px
 * high); sheet cells are at least 8 px high, the rest of a cell stays transparent.
 *
 * <p>The client uploads every glyph with its whole cell into a 256 x 256 font texture; a cell wider
 * than that never fits and the glyph shows as a missing-character box, so cells stay 128 px wide.
 */
public final class FillSheets {

    public static final int[] WIDTHS = {128, 64, 32, 16, 8, 4, 2, 1};
    private static final int CELL = 128;
    /** 1: lines (underline, strikethrough), 6 and 14: bars. */
    public static final int[] HEIGHTS = {1, 6, 14};

    private final List<Sheet> sheets = new ArrayList<>();

    public FillSheets(GlyphAllocator alloc) {
        for (int h : HEIGHTS) {
            int cellH = Math.max(8, h);
            List<BufferedImage> cells = new ArrayList<>();
            for (int w : WIDTHS) {
                BufferedImage img = new BufferedImage(CELL, cellH, BufferedImage.TYPE_INT_ARGB);
                for (int y = 0; y < h; y++) {
                    for (int x = 0; x < w; x++) {
                        img.setRGB(x, y, 0xFFFFFFFF);
                    }
                }
                cells.add(img);
            }
            sheets.add(new Sheet("glyphwork_fill" + h, cells, 1, alloc));
        }
    }

    public List<Sheet> sheets() {
        return sheets;
    }

    /** The sheet for a fill this high, or null if there is none (6 and 14 exist). */
    public Sheet forHeight(int height) {
        for (int i = 0; i < HEIGHTS.length; i++) {
            if (HEIGHTS[i] == height) {
                return sheets.get(i);
            }
        }
        return null;
    }
}
