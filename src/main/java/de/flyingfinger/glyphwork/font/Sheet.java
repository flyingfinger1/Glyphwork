package de.flyingfinger.glyphwork.font;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.List;

/**
 * A PNG with a grid of equally sized cells, registered as one bitmap provider. The same PNG is
 * referenced from every shifted font ({@link ShiftFonts}), so vertical placement costs JSON, not
 * extra textures.
 */
public final class Sheet {

    private final String name;
    private final BufferedImage image;
    private final int cellW;
    private final int cellH;
    private final int cols;
    private final int rows;
    private final Glyph[] glyphs;

    /**
     * @param cells images of equal size; laid out row by row, {@code cols} per row
     */
    public Sheet(String name, List<BufferedImage> cells, int cols, GlyphAllocator alloc) {
        if (cells.isEmpty()) {
            throw new IllegalArgumentException("empty sheet " + name);
        }
        this.name = name;
        this.cellW = cells.getFirst().getWidth();
        this.cellH = cells.getFirst().getHeight();
        if (cellH < 8) {
            throw new IllegalArgumentException(name + ": cells must be at least 8 px high (ascent <= height)");
        }
        this.cols = Math.min(cols, cells.size());
        this.rows = (cells.size() + this.cols - 1) / this.cols;
        this.image = new BufferedImage(this.cols * cellW, rows * cellH, BufferedImage.TYPE_INT_ARGB);
        this.glyphs = new Glyph[cells.size()];
        for (int i = 0; i < cells.size(); i++) {
            BufferedImage cell = cells.get(i);
            if (cell.getWidth() != cellW || cell.getHeight() != cellH) {
                throw new IllegalArgumentException(name + ": cell " + i + " has a different size");
            }
            image.getGraphics().drawImage(cell, (i % this.cols) * cellW, (i / this.cols) * cellH, null);
            glyphs[i] = new Glyph(alloc.next(), actualWidth(cell) + 1, cellW, cellH);
        }
    }

    /** Single-cell sheet. */
    public Sheet(String name, BufferedImage image, GlyphAllocator alloc) {
        this(name, List.of(image), 1, alloc);
    }

    /**
     * One 8 x 8 cell of alpha-1 pixels: the client discards it when drawing (alpha < 0.1) but its
     * box counts for clicks, so it makes invisible hitboxes.
     */
    public static Sheet invisible(GlyphAllocator alloc) {
        BufferedImage img = new BufferedImage(8, 8, BufferedImage.TYPE_INT_ARGB);
        for (int y = 0; y < 8; y++) {
            for (int x = 0; x < 8; x++) {
                img.setRGB(x, y, 0x01000000);
            }
        }
        return new Sheet("glyphwork_hit", img, alloc);
    }

    /** Same rule as the client: rightmost column with any non-zero alpha pixel. */
    static int actualWidth(BufferedImage img) {
        for (int x = img.getWidth() - 1; x >= 0; x--) {
            for (int y = 0; y < img.getHeight(); y++) {
                if ((img.getRGB(x, y) >>> 24) != 0) {
                    return x + 1;
                }
            }
        }
        return 0;
    }

    public String name() {
        return name;
    }

    public Glyph glyph(int index) {
        return glyphs[index];
    }

    public Glyph glyph() {
        return glyphs[0];
    }

    public int size() {
        return glyphs.length;
    }

    /** Image of one cell (for previews). */
    public BufferedImage cell(int index) {
        return image.getSubimage((index % cols) * cellW, (index / cols) * cellH, cellW, cellH);
    }

    public String texturePath() {
        return "assets/glyphwork/textures/sheet/" + name + ".png";
    }

    /** Bitmap provider for a font whose content is drawn {@code shift} px below the first line. */
    public JsonObject provider(int shift) {
        JsonObject p = new JsonObject();
        p.addProperty("type", "bitmap");
        p.addProperty("file", "glyphwork:sheet/" + name + ".png");
        p.addProperty("height", cellH);
        p.addProperty("ascent", 7 - shift);
        JsonArray chars = new JsonArray();
        for (int r = 0; r < rows; r++) {
            StringBuilder row = new StringBuilder();
            for (int c = 0; c < cols; c++) {
                int i = r * cols + c;
                row.append(i < glyphs.length ? glyphs[i].codepoint() : '\0');
            }
            chars.add(row.toString());
        }
        p.add("chars", chars);
        return p;
    }

    public byte[] png() {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try {
            ImageIO.write(image, "png", out);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        return out.toByteArray();
    }
}
