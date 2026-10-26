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
import java.util.Set;
import java.util.TreeSet;

/**
 * Builds the whole GUI as one text line. Elements are appended in paint order (later = on top)
 * and positioned with space glyphs (x) and shifted fonts (y). Consecutive glyphs with the same
 * style are merged into one text component.
 *
 * <p>Clicks: in 26.3 the client hit-tests every glyph against its drawn box (bitmap) or its
 * line box (space), and the LAST glyph under the cursor that has a click event wins. So a
 * clickable glyph is clickable exactly where it is drawn, and glyphs without events never
 * block clicks on glyphs below them.
 */
public final class LineBuilder {

    private final VanillaText text;
    private final TextComponent.Builder out = Component.text();
    private final Set<Integer> offsets = new TreeSet<>();
    private int cursor;
    private int maxCursor;

    // open segment
    private final StringBuilder seg = new StringBuilder();
    private @Nullable Key segFont;
    private @Nullable TextColor segColor;
    private @Nullable ClickEvent<?> segClick;
    private @Nullable HoverEventSource<?> segHover;

    public LineBuilder(VanillaText text) {
        this.text = text;
    }

    private void flush() {
        if (seg.isEmpty()) {
            return;
        }
        out.append(Component.text(seg.toString()).font(segFont).color(segColor).clickEvent(segClick).hoverEvent(segHover));
        seg.setLength(0);
        segFont = null;
    }

    private void emit(String chars, Key font, @Nullable TextColor color, @Nullable ClickEvent<?> click, @Nullable HoverEventSource<?> hover) {
        if (seg.isEmpty() || !font.equals(segFont) || !Objects.equals(color, segColor)
            || !Objects.equals(click, segClick) || !Objects.equals(hover, segHover)) {
            flush();
            segFont = font;
            segColor = color;
            segClick = click;
            segHover = hover;
        }
        seg.append(chars);
    }

    private void moveTo(int x) {
        int dx = x - cursor;
        if (dx == 0) {
            return;
        }
        // Spaces exist in every shifted font. A positive space inherits the segment's events and
        // would be clickable on the first line, so only negative moves join an interactive segment.
        if (!seg.isEmpty() && ShiftFonts.isShiftFont(segFont) && (dx < 0 || (segClick == null && segHover == null))) {
            seg.append(SpaceFont.of(dx));
        } else {
            emit(SpaceFont.of(dx), SpaceFont.KEY, null, null, null);
        }
        cursor = x;
    }

    private void advance(int dx) {
        cursor += dx;
        maxCursor = Math.max(maxCursor, cursor);
    }

    /** Draws one glyph with its top-left pixel at (x, y). */
    public LineBuilder glyph(Glyph g, int x, int y, @Nullable TextColor tint, @Nullable ClickEvent<?> click, @Nullable HoverEventSource<?> hover) {
        moveTo(x);
        offsets.add(y);
        emit(String.valueOf(g.codepoint()), ShiftFonts.key(y), tint == null ? NamedTextColor.WHITE : tint, click, hover);
        advance(g.advance());
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

    /** Rectangular frame of {@code w x h} pixels (rounded down to whole tiles). */
    public LineBuilder frame(Frame frame, int x, int y, int w, int h, @Nullable TextColor tint,
                             @Nullable ClickEvent<?> click, @Nullable HoverEventSource<?> hover) {
        return frame(frame, x, y, cells(w / frame.tileSize(), h / frame.tileSize()), tint, click, hover);
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
        moveTo(x);
        offsets.add(y);
        emit(s.replace(' ', VanillaText.NB_SPACE), ShiftFonts.key(y), color, null, null);
        advance(text.width(s));
        return this;
    }

    /** Text horizontally centered in [x, x + w). */
    public LineBuilder centered(String s, int x, int w, int y, TextColor color) {
        int tw = text.width(s) - 1; // trailing 1px gap of the last glyph is not visible
        return text(s, x + (w - tw) / 2, y, color);
    }

    public int textWidth(String s) {
        return text.width(s);
    }

    /** Offsets used so far; the pack must contain a font for each. */
    public Set<Integer> offsets() {
        return offsets;
    }

    /** Line width in pixels. */
    public int width() {
        return maxCursor;
    }

    /**
     * The finished message: the drawn line plus enough empty lines that the text widget (and
     * thus its click area) covers {@code height} pixels.
     */
    public Component build(int height) {
        moveTo(maxCursor); // the line ends at its widest point, so centering keeps the origin fixed
        flush();
        int lines = Math.max(1, (height + 8) / 9);
        return Component.text()
            .color(NamedTextColor.WHITE)
            .shadowColor(ShadowColor.none())
            .decoration(TextDecoration.ITALIC, false)
            .decoration(TextDecoration.BOLD, false)
            .append(out.build())
            .append(Component.text("\n".repeat(lines - 1)))
            .build();
    }
}
