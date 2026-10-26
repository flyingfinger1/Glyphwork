package de.flyingfinger.glyphwork.render;

import de.flyingfinger.glyphwork.font.Glyph;
import de.flyingfinger.glyphwork.font.ShiftFonts;
import de.flyingfinger.glyphwork.font.SpaceFont;
import de.flyingfinger.glyphwork.font.VanillaText;
import de.flyingfinger.glyphwork.theme.Autotile;
import de.flyingfinger.glyphwork.theme.Frame;
import net.kyori.adventure.key.Key;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TextComponent;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEventSource;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.ShadowColor;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.jspecify.annotations.Nullable;

import java.util.Objects;
import java.util.TreeMap;

/**
 * Builds the GUI as text lines. Glyphs from sheets (frames) go on the first line in paint order
 * (later = on top), positioned with space glyphs (x) and the {@code y<N>} fonts. Text goes on the
 * text line it falls into, with the {@code t<S>} fonts for the remainder, so it is always drawn
 * after (on top of) all frames. Every non-empty line ends at the same x, so the client's
 * per-line centering keeps them aligned. Consecutive glyphs with the same style are merged.
 *
 * <p>Clicks: in 26.3 the client hit-tests every glyph against its drawn box (bitmap) or its
 * line box (space), and the LAST glyph under the cursor that has a click event wins. So a
 * clickable glyph is clickable exactly where it is drawn, and glyphs without events never
 * block clicks on glyphs below them.
 */
public final class LineBuilder {

    private final VanillaText text;
    private final int maxHeight;
    private final TreeMap<Integer, Line> lines = new TreeMap<>();

    /** @param maxHeight number of {@code y<N>} fonts in the pack; frames below are clipped */
    public LineBuilder(VanillaText text, int maxHeight) {
        this.text = text;
        this.maxHeight = maxHeight;
        lines.put(0, new Line());
    }

    private Line line(int index) {
        return lines.computeIfAbsent(index, i -> new Line());
    }

    /** Draws one sheet glyph with its top-left pixel at (x, y) on the first line. */
    public LineBuilder glyph(Glyph g, int x, int y, @Nullable TextColor tint, @Nullable ClickEvent<?> click, @Nullable HoverEventSource<?> hover) {
        if (y < 0 || y >= maxHeight) {
            return this; // no font for this row
        }
        line(0).draw(String.valueOf(g.codepoint()), g.advance(), x, ShiftFonts.sprite(y),
            tint == null ? NamedTextColor.WHITE : tint, click, hover);
        return this;
    }

    /**
     * Draws an autotiled frame. {@code cells[row][col]} marks the occupied tiles (any shape);
     * neighbours pick the right corner/edge/fill tile. The tint multiplies the texture colors.
     */
    public LineBuilder frame(Frame frame, int x, int y, boolean[][] cells, @Nullable TextColor tint,
                             @Nullable ClickEvent<?> click, @Nullable HoverEventSource<?> hover) {
        int t = frame.tileSize();
        for (int r = 0; r < cells.length; r++) {
            for (int c = 0; c < cells[r].length; c++) {
                if (cells[r][c]) {
                    glyph(frame.tile(Autotile.mask(cells, c, r)), x + c * t, y + r * t, tint, click, hover);
                }
            }
        }
        return this;
    }

    /** Rectangular frame of {@code w x h} pixels (rounded down to whole tiles, at least one). */
    public LineBuilder frame(Frame frame, int x, int y, int w, int h, @Nullable TextColor tint,
                             @Nullable ClickEvent<?> click, @Nullable HoverEventSource<?> hover) {
        int t = frame.tileSize();
        return frame(frame, x, y, cells(Math.max(1, w / t), Math.max(1, h / t)), tint, click, hover);
    }

    /** Cell mask from rows like {@code "##.."} ('#' = tile). */
    public static boolean[][] cells(String... rows) {
        boolean[][] c = new boolean[rows.length][];
        for (int r = 0; r < rows.length; r++) {
            c[r] = new boolean[rows[r].length()];
            for (int i = 0; i < rows[r].length(); i++) {
                c[r][i] = rows[r].charAt(i) == '#';
            }
        }
        return c;
    }

    public static boolean[][] cells(int cols, int rows) {
        boolean[][] c = new boolean[rows][cols];
        for (boolean[] row : c) {
            java.util.Arrays.fill(row, true);
        }
        return c;
    }

    /** Text with its top-left glyph pixel at (x, y). */
    public LineBuilder text(String s, int x, int y, TextColor color) {
        if (s.isEmpty() || y < 0) {
            return this;
        }
        int index = y / ShiftFonts.LINE;
        line(index).draw(s.replace(' ', VanillaText.NB_SPACE), text.width(s), x,
            ShiftFonts.text(y - index * ShiftFonts.LINE), color, null, null);
        return this;
    }

    /** Text horizontally centered in [x, x + w). */
    public LineBuilder centered(String s, int x, int w, int y, TextColor color) {
        return text(s, x + (w - visibleWidth(s)) / 2, y, color);
    }

    /** Width of the drawn pixels (the advance minus the trailing 1 px gap). */
    public int visibleWidth(String s) {
        return Math.max(0, text.width(s) - 1);
    }

    public int textWidth(String s) {
        return text.width(s);
    }

    /** Cuts {@code s} to fit into {@code maxWidth} pixels, adding "..." when shortened. */
    public String fit(String s, int maxWidth) {
        if (text.width(s) - 1 <= maxWidth) {
            return s;
        }
        String dots = "...";
        int budget = maxWidth - text.width(dots);
        StringBuilder out = new StringBuilder();
        int w = 0;
        for (int i = 0; i < s.length(); ) {
            int cp = s.codePointAt(i);
            int cw = text.width(new String(Character.toChars(cp)));
            if (w + cw > budget) {
                break;
            }
            out.appendCodePoint(cp);
            w += cw;
            i += Character.charCount(cp);
        }
        return out.toString().stripTrailing() + dots;
    }

    /** Widest line in pixels. */
    public int width() {
        return lines.values().stream().mapToInt(l -> l.maxCursor).max().orElse(0);
    }

    /**
     * The finished message: all lines, each ending at the same x, plus enough empty lines that the
     * text widget (and thus its click area) covers {@code height} pixels.
     */
    public Component build(int height) {
        int w = width();
        int last = Math.max(lines.lastKey(), Math.max(1, (height + 8) / ShiftFonts.LINE) - 1);
        TextComponent.Builder root = Component.text()
            .color(NamedTextColor.WHITE)
            .shadowColor(ShadowColor.none())
            .decoration(TextDecoration.ITALIC, false)
            .decoration(TextDecoration.BOLD, false);
        for (int i = 0; i <= last; i++) {
            if (i > 0) {
                root.append(Component.newline());
            }
            Line line = lines.get(i);
            if (line != null && line.used) {
                line.moveTo(w);
                root.append(line.build());
            }
        }
        return root.build();
    }

    /** One text line: cursor, merged segments. */
    private static final class Line {
        private final TextComponent.Builder out = Component.text();
        private final StringBuilder seg = new StringBuilder();
        private @Nullable Key segFont;
        private @Nullable TextColor segColor;
        private @Nullable ClickEvent<?> segClick;
        private @Nullable HoverEventSource<?> segHover;
        private int cursor;
        private int maxCursor;
        private boolean used;

        void draw(String chars, int advance, int x, Key font, @Nullable TextColor color,
                  @Nullable ClickEvent<?> click, @Nullable HoverEventSource<?> hover) {
            used = true;
            moveTo(x);
            if (seg.isEmpty() || !font.equals(segFont) || !Objects.equals(color, segColor)
                || !Objects.equals(click, segClick) || !Objects.equals(hover, segHover)) {
                flush();
                segFont = font;
                segColor = color;
                segClick = click;
                segHover = hover;
            }
            seg.append(chars);
            cursor += advance;
            maxCursor = Math.max(maxCursor, cursor);
        }

        void moveTo(int x) {
            int dx = x - cursor;
            if (dx == 0) {
                return;
            }
            // Spaces exist in every shifted font. A positive space inherits the segment's events
            // and would be clickable on its line, so only negative moves join an interactive segment.
            if (!seg.isEmpty() && ShiftFonts.isShiftFont(segFont) && (dx < 0 || (segClick == null && segHover == null))) {
                seg.append(SpaceFont.of(dx));
            } else {
                flush();
                out.append(Component.text(SpaceFont.of(dx)).font(SpaceFont.KEY));
            }
            cursor = x;
        }

        void flush() {
            if (seg.isEmpty()) {
                return;
            }
            out.append(Component.text(seg.toString()).font(segFont).color(segColor).clickEvent(segClick).hoverEvent(segHover));
            seg.setLength(0);
            segFont = null;
        }

        Component build() {
            flush();
            return out.build();
        }
    }
}
