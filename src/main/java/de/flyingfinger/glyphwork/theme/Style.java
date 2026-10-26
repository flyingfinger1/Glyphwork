package de.flyingfinger.glyphwork.theme;

import org.jspecify.annotations.Nullable;

import java.awt.image.BufferedImage;

/**
 * Procedural frame look: outline, bevel and fill, rendered onto any pixel shape. The code skin
 * renders sample shapes with it and cuts the A2 quarters out of them, so corners, edges and inner
 * corners always match.
 *
 * @param fill       interior color (ARGB)
 * @param outline    1 px outline color, or null for none
 * @param round      cut the outer pixel of convex corners (vanilla window look)
 * @param lightWidth highlight width along top and left edges
 * @param light      highlight color
 * @param shadeWidth shadow width along bottom and right edges
 * @param shade      shadow color
 */
public record Style(int fill, @Nullable Integer outline, boolean round,
                    int lightWidth, int light, int shadeWidth, int shade) {

    /** Vanilla container window. */
    public static final Style PANEL = new Style(0xFFC6C6C6, 0xFF000000, true, 2, 0xFFFFFFFF, 2, 0xFF555555);
    /** Light gray button, meant to be tinted with a text color. */
    public static final Style BUTTON = new Style(0xFFC8C8C8, 0xFF000000, false, 1, 0xFFFFFFFF, 2, 0xFF6E6E6E);
    /** Sunken inventory slot. */
    public static final Style SLOT = new Style(0xFF8B8B8B, null, false, 1, 0xFF373737, 1, 0xFFFFFFFF);

    /** "Dunkel": dark slate window, light text. */
    public static final Style DARK_PANEL = new Style(0xFF2E3138, 0xFF0A0B0D, true, 2, 0xFF4A4F5A, 2, 0xFF1A1C20);
    public static final Style DARK_BUTTON = new Style(0xFF7A7F8A, 0xFF0A0B0D, false, 1, 0xFFA8AEB8, 2, 0xFF464A52);
    public static final Style DARK_SLOT = new Style(0xFF484C55, null, false, 1, 0xFF1A1C20, 1, 0xFF6A707A);

    /** "Pergament": parchment window with a brown ink outline. */
    public static final Style PARCHMENT_PANEL = new Style(0xFFE9DDB8, 0xFF4A3420, true, 2, 0xFFFBF3DC, 2, 0xFFB79F6E);
    public static final Style PARCHMENT_BUTTON = new Style(0xFFD6C49A, 0xFF4A3420, false, 1, 0xFFF4EAD0, 2, 0xFF9C8458);
    public static final Style PARCHMENT_SLOT = new Style(0xFFB8A27A, null, false, 1, 0xFF7A6040, 1, 0xFFF4EAD0);

    /** Renders the shape {@code inside[y][x]}; pixels outside the array count as outside. */
    public BufferedImage render(boolean[][] inside) {
        int h = inside.length;
        int w = inside[0].length;
        boolean[][] in = new boolean[h][w];
        for (int y = 0; y < h; y++) {
            in[y] = inside[y].clone();
        }
        if (round && outline != null) {
            boolean[][] cut = new boolean[h][w];
            for (int y = 0; y < h; y++) {
                for (int x = 0; x < w; x++) {
                    cut[y][x] = in[y][x]
                        && (!at(in, x - 1, y) || !at(in, x + 1, y))
                        && (!at(in, x, y - 1) || !at(in, x, y + 1));
                }
            }
            for (int y = 0; y < h; y++) {
                for (int x = 0; x < w; x++) {
                    if (cut[y][x]) {
                        in[y][x] = false;
                    }
                }
            }
        }
        boolean[][] edge = new boolean[h][w];
        if (outline != null) {
            for (int y = 0; y < h; y++) {
                for (int x = 0; x < w; x++) {
                    if (!in[y][x]) {
                        continue;
                    }
                    for (int dy = -1; dy <= 1 && !edge[y][x]; dy++) {
                        for (int dx = -1; dx <= 1; dx++) {
                            if (!at(in, x + dx, y + dy)) {
                                edge[y][x] = true;
                                break;
                            }
                        }
                    }
                }
            }
        }
        BufferedImage img = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                if (!in[y][x]) {
                    continue;
                }
                if (edge[y][x]) {
                    img.setRGB(x, y, outline);
                    continue;
                }
                boolean lit = false;
                for (int k = 1; k <= lightWidth; k++) {
                    lit |= border(in, edge, x - k, y) || border(in, edge, x, y - k);
                }
                boolean shaded = false;
                for (int k = 1; k <= shadeWidth; k++) {
                    shaded |= border(in, edge, x + k, y) || border(in, edge, x, y + k);
                }
                img.setRGB(x, y, lit && shaded ? fill : lit ? light : shaded ? shade : fill);
            }
        }
        return img;
    }

    private static boolean at(boolean[][] a, int x, int y) {
        return y >= 0 && y < a.length && x >= 0 && x < a[0].length && a[y][x];
    }

    private static boolean border(boolean[][] in, boolean[][] edge, int x, int y) {
        return !at(in, x, y) || edge[y][x];
    }
}
