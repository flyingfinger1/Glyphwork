package de.flyingfinger.glyphwork.theme;

import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.List;
import java.util.TreeSet;

/**
 * Turns an A2 autotile sheet into the 47 blob tiles and maps a neighbour mask to one of them.
 *
 * <p>A2 sheet (tile size T, quarter q = T/2), 2 x 3 tiles = 4 x 6 quarters:
 * <pre>
 *   tile (0,0): preview / single tile (not used for lookup)
 *   tile (1,0): inner corners; quarter (2,0) TL, (3,0) TR, (2,1) BL, (3,1) BR
 *   tiles (0..1, 1..2): a 2x2-tile frame: outer corners, edges, fill
 * </pre>
 * Each quadrant of a cell depends on its horizontal, vertical and diagonal neighbour only, which
 * gives 5 cases (outer corner, horizontal edge, vertical edge, inner corner, fill) and 47 distinct
 * tiles overall.
 */
public final class Autotile {

    public static final int N = 1, NE = 2, E = 4, SE = 8, S = 16, SW = 32, W = 64, NW = 128;

    /** The 47 canonical masks in ascending order; the tile index is the position in this list. */
    public static final List<Integer> MASKS;
    private static final int[] INDEX = new int[256];

    static {
        TreeSet<Integer> set = new TreeSet<>();
        for (int m = 0; m < 256; m++) {
            set.add(canonical(m));
        }
        MASKS = List.copyOf(set);
        for (int m = 0; m < 256; m++) {
            INDEX[m] = MASKS.indexOf(canonical(m));
        }
    }

    private Autotile() {
    }

    /** Drops diagonal bits that do not matter (diagonal counts only if both adjacent sides are set). */
    public static int canonical(int m) {
        int r = m & (N | E | S | W);
        if ((m & NE) != 0 && (m & N) != 0 && (m & E) != 0) r |= NE;
        if ((m & SE) != 0 && (m & S) != 0 && (m & E) != 0) r |= SE;
        if ((m & SW) != 0 && (m & S) != 0 && (m & W) != 0) r |= SW;
        if ((m & NW) != 0 && (m & N) != 0 && (m & W) != 0) r |= NW;
        return r;
    }

    public static int index(int mask) {
        return INDEX[mask & 0xFF];
    }

    /** Neighbour mask of cell (c, r) in {@code cells[row][col]}; outside the grid counts as empty. */
    public static int mask(boolean[][] cells, int c, int r) {
        int m = 0;
        if (cell(cells, c, r - 1)) m |= N;
        if (cell(cells, c + 1, r - 1)) m |= NE;
        if (cell(cells, c + 1, r)) m |= E;
        if (cell(cells, c + 1, r + 1)) m |= SE;
        if (cell(cells, c, r + 1)) m |= S;
        if (cell(cells, c - 1, r + 1)) m |= SW;
        if (cell(cells, c - 1, r)) m |= W;
        if (cell(cells, c - 1, r - 1)) m |= NW;
        return m;
    }

    private static boolean cell(boolean[][] cells, int c, int r) {
        return r >= 0 && r < cells.length && c >= 0 && c < cells[r].length && cells[r][c];
    }

    /** The 47 tiles (index order = {@link #MASKS}) built from an A2 sheet of size 2T x 3T. */
    public static List<BufferedImage> tiles(BufferedImage a2, int t) {
        if (t % 2 != 0 || a2.getWidth() != 2 * t || a2.getHeight() != 3 * t) {
            throw new IllegalArgumentException("A2 sheet must be " + (2 * t) + "x" + (3 * t) + " px for tile size " + t);
        }
        int q = t / 2;
        List<BufferedImage> out = new ArrayList<>();
        for (int mask : MASKS) {
            BufferedImage tile = new BufferedImage(t, t, BufferedImage.TYPE_INT_ARGB);
            for (int qy = 0; qy < 2; qy++) {
                for (int qx = 0; qx < 2; qx++) {
                    boolean h = (mask & (qx == 0 ? W : E)) != 0;
                    boolean v = (mask & (qy == 0 ? N : S)) != 0;
                    int diag = qy == 0 ? (qx == 0 ? NW : NE) : (qx == 0 ? SW : SE);
                    boolean d = (mask & diag) != 0;
                    int col;
                    int row;
                    if (h && v && !d) {
                        col = 2 + qx;
                        row = qy;
                    } else {
                        col = qx == 0 ? (h ? 2 : 0) : (h ? 1 : 3);
                        row = qy == 0 ? (v ? 4 : 2) : (v ? 3 : 5);
                    }
                    for (int y = 0; y < q; y++) {
                        for (int x = 0; x < q; x++) {
                            tile.setRGB(qx * q + x, qy * q + y, a2.getRGB(col * q + x, row * q + y));
                        }
                    }
                }
            }
            out.add(tile);
        }
        return out;
    }

    /** Builds an A2 sheet from a procedural style by rendering sample shapes and cutting quarters. */
    public static BufferedImage a2(Style style, int t) {
        int q = t / 2;
        BufferedImage a2 = new BufferedImage(2 * t, 3 * t, BufferedImage.TYPE_INT_ARGB);
        // preview: a single cell
        copy(style.render(rect(t, t)), 0, 0, a2, 0, 0, t, t);
        // frame block: a 2x2-tile window
        copy(style.render(rect(2 * t, 2 * t)), 0, 0, a2, 0, t, 2 * t, 2 * t);
        // inner corners: a 3x3-tile block without one corner tile, cut from the center tile
        for (int qy = 0; qy < 2; qy++) {
            for (int qx = 0; qx < 2; qx++) {
                boolean[][] shape = rect(3 * t, 3 * t);
                int cx = qx == 0 ? 0 : 2;
                int cy = qy == 0 ? 0 : 2;
                for (int y = cy * t; y < (cy + 1) * t; y++) {
                    for (int x = cx * t; x < (cx + 1) * t; x++) {
                        shape[y][x] = false;
                    }
                }
                copy(style.render(shape), t + qx * q, t + qy * q, a2, t + qx * q, qy * q, q, q);
            }
        }
        return a2;
    }

    private static boolean[][] rect(int w, int h) {
        boolean[][] r = new boolean[h][w];
        for (boolean[] row : r) {
            java.util.Arrays.fill(row, true);
        }
        return r;
    }

    private static void copy(BufferedImage src, int sx, int sy, BufferedImage dst, int dx, int dy, int w, int h) {
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                dst.setRGB(dx + x, dy + y, src.getRGB(sx + x, sy + y));
            }
        }
    }
}
