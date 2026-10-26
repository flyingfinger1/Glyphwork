package de.flyingfinger.dialogui.render;

import de.flyingfinger.dialogui.font.ShiftFonts;
import de.flyingfinger.dialogui.font.SpaceFont;
import de.flyingfinger.dialogui.font.Sprite;
import de.flyingfinger.dialogui.font.VanillaText;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TextComponent;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEventSource;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.ShadowColor;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.jspecify.annotations.Nullable;

import java.util.Set;
import java.util.TreeSet;

/**
 * Builds the whole GUI as one text line. Elements are appended in paint order (later = on top)
 * and positioned with space glyphs (x) and shifted fonts (y).
 *
 * <p>Clicks: in 26.3 the client hit-tests every glyph against its drawn box (bitmap) or its
 * line box (space), and the LAST glyph under the cursor that has a click event wins. So a
 * clickable sprite is clickable exactly where it is drawn, and sprites without events never
 * block clicks on sprites below them.
 */
public final class LineBuilder {

    private final VanillaText text;
    private final TextComponent.Builder out = Component.text();
    private final Set<Integer> offsets = new TreeSet<>();
    private int cursor;
    private int maxCursor;

    public LineBuilder(VanillaText text) {
        this.text = text;
    }

    private void moveTo(int x) {
        if (x != cursor) {
            out.append(Component.text(SpaceFont.of(x - cursor)).font(SpaceFont.KEY));
            cursor = x;
        }
    }

    private void advance(int dx) {
        cursor += dx;
        maxCursor = Math.max(maxCursor, cursor);
    }

    public LineBuilder sprite(Sprite sprite, int x, int y, @Nullable ClickEvent<?> click, @Nullable HoverEventSource<?> hover) {
        moveTo(x);
        offsets.add(y);
        out.append(Component.text(String.valueOf(sprite.codepoint()))
            .font(ShiftFonts.key(y))
            .color(NamedTextColor.WHITE)
            .clickEvent(click)
            .hoverEvent(hover));
        advance(sprite.advance());
        return this;
    }

    public LineBuilder sprite(Sprite sprite, int x, int y) {
        return sprite(sprite, x, y, null, null);
    }

    /** Text with its top-left glyph pixel at (x, y). */
    public LineBuilder text(String s, int x, int y, TextColor color) {
        moveTo(x);
        offsets.add(y);
        out.append(Component.text(s.replace(' ', VanillaText.NB_SPACE)).font(ShiftFonts.key(y)).color(color));
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

    /** Line width in pixels; pass it as the plain_message width so the client never wraps. */
    public int width() {
        return maxCursor;
    }

    /**
     * The finished message: the drawn line plus enough empty lines that the text widget (and
     * thus its click area) covers {@code height} pixels.
     */
    public Component build(int height) {
        moveTo(maxCursor); // every line ends at the same x, so centering keeps layers aligned
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
