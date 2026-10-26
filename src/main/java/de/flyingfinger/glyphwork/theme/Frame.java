package de.flyingfinger.glyphwork.theme;

import de.flyingfinger.glyphwork.font.Glyph;
import de.flyingfinger.glyphwork.font.GlyphAllocator;
import de.flyingfinger.glyphwork.font.Sheet;

import java.awt.image.BufferedImage;

/** One autotiled frame type of a theme (panel, button, slot ...): 47 tile glyphs in one sheet. */
public final class Frame {

    private final String name;
    private final int tileSize;
    private final BufferedImage a2;
    private final Sheet sheet;

    public Frame(String theme, String name, BufferedImage a2, int tileSize, GlyphAllocator alloc) {
        this.name = name;
        this.tileSize = tileSize;
        this.a2 = a2;
        this.sheet = new Sheet(theme + "_" + name, Autotile.tiles(a2, tileSize), 8, alloc);
    }

    public String name() {
        return name;
    }

    public int tileSize() {
        return tileSize;
    }

    /** The source A2 sheet (2T x 3T). */
    public BufferedImage a2() {
        return a2;
    }

    public Sheet sheet() {
        return sheet;
    }

    /** Tile glyph for a cell with the given neighbour mask ({@link Autotile} bits). */
    public Glyph tile(int mask) {
        return sheet.glyph(Autotile.index(mask));
    }
}
