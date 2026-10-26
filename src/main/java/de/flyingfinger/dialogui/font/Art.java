package de.flyingfinger.dialogui.font;

import java.awt.image.BufferedImage;

/** Procedural placeholder pixel art (vanilla-container look) until real skins exist. */
public final class Art {

    private static final int BLACK = 0xFF000000;
    private static final int WHITE = 0xFFFFFFFF;

    private Art() {
    }

    /** Vanilla-style window: rounded black outline, white top-left bevel, gray bottom-right bevel. */
    public static BufferedImage panel(int w, int h) {
        BufferedImage img = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
        fill(img, 0, 0, w, h, 0xFFC6C6C6);
        // outline
        fill(img, 2, 0, w - 4, 1, BLACK);
        fill(img, 2, h - 1, w - 4, 1, BLACK);
        fill(img, 0, 2, 1, h - 4, BLACK);
        fill(img, w - 1, 2, 1, h - 4, BLACK);
        set(img, 1, 1, BLACK);
        set(img, w - 2, 1, BLACK);
        set(img, 1, h - 2, BLACK);
        set(img, w - 2, h - 2, BLACK);
        // transparent corners
        for (int[] c : new int[][]{{0, 0}, {1, 0}, {0, 1}, {w - 1, 0}, {w - 2, 0}, {w - 1, 1},
            {0, h - 1}, {1, h - 1}, {0, h - 2}, {w - 1, h - 1}, {w - 2, h - 1}, {w - 1, h - 2}}) {
            set(img, c[0], c[1], 0);
        }
        // bevel
        fill(img, 2, 1, w - 4, 2, WHITE);
        fill(img, 1, 2, 2, h - 5, WHITE);
        fill(img, 3, h - 3, w - 5, 2, 0xFF555555);
        fill(img, w - 3, 3, 2, h - 5, 0xFF555555);
        set(img, w - 3, 2, 0xFFC6C6C6);
        set(img, 2, h - 3, 0xFFC6C6C6);
        return img;
    }

    /** Raised button in the given base color. */
    public static BufferedImage button(int w, int h, int rgb) {
        BufferedImage img = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
        int base = 0xFF000000 | rgb;
        fill(img, 0, 0, w, h, BLACK);
        fill(img, 1, 1, w - 2, h - 2, base);
        fill(img, 1, 1, w - 2, 1, shade(base, 1.45));
        fill(img, 1, 1, 1, h - 2, shade(base, 1.45));
        fill(img, 1, h - 3, w - 2, 2, shade(base, 0.6));
        fill(img, w - 2, 1, 1, h - 2, shade(base, 0.6));
        return img;
    }

    /** Sunken inventory-style slot. */
    public static BufferedImage slot(int w, int h, int fillRgb) {
        BufferedImage img = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
        fill(img, 0, 0, w, h, 0xFF000000 | fillRgb);
        fill(img, 0, 0, w - 1, 1, 0xFF373737);
        fill(img, 0, 0, 1, h - 1, 0xFF373737);
        fill(img, 1, h - 1, w - 1, 1, WHITE);
        fill(img, w - 1, 1, 1, h - 1, WHITE);
        return img;
    }

    private static int shade(int argb, double f) {
        int r = Math.min(255, (int) (((argb >> 16) & 0xFF) * f));
        int g = Math.min(255, (int) (((argb >> 8) & 0xFF) * f));
        int b = Math.min(255, (int) ((argb & 0xFF) * f));
        return 0xFF000000 | (r << 16) | (g << 8) | b;
    }

    private static void fill(BufferedImage img, int x, int y, int w, int h, int argb) {
        for (int yy = y; yy < y + h; yy++) {
            for (int xx = x; xx < x + w; xx++) {
                set(img, xx, yy, argb);
            }
        }
    }

    private static void set(BufferedImage img, int x, int y, int argb) {
        if (x >= 0 && y >= 0 && x < img.getWidth() && y < img.getHeight()) {
            img.setRGB(x, y, argb);
        }
    }
}
