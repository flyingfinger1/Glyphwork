package de.flyingfinger.glyphwork.font;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;

/**
 * One texture registered as a single bitmap glyph. The same texture is referenced from every
 * shifted font ({@link ShiftFonts}), so vertical placement costs JSON, not extra PNGs.
 */
public final class Sprite {

    private final String name;
    private final BufferedImage image;
    private final char codepoint;
    private final int advance;

    public Sprite(String name, BufferedImage image, char codepoint) {
        if (image.getHeight() < 8) {
            // ascent may not exceed height; padding to 8 keeps every row from y = -1 reachable
            BufferedImage padded = new BufferedImage(image.getWidth(), 8, BufferedImage.TYPE_INT_ARGB);
            padded.getGraphics().drawImage(image, 0, 0, null);
            image = padded;
        }
        this.name = name;
        this.image = image;
        this.codepoint = codepoint;
        this.advance = actualWidth(image) + 1;
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

    public char codepoint() {
        return codepoint;
    }

    /** Cursor advance after drawing this glyph (visible width + 1). */
    public int advance() {
        return advance;
    }

    public int width() {
        return image.getWidth();
    }

    public int height() {
        return image.getHeight();
    }

    public String textureId() {
        return "glyphwork:sprite/" + name + ".png";
    }

    public String texturePath() {
        return "assets/glyphwork/textures/sprite/" + name + ".png";
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
