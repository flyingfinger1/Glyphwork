package de.flyingfinger.glyphwork.api;

import net.kyori.adventure.text.format.TextColor;

import java.util.function.Function;

/**
 * One line of text in the Minecraft font (no shadow). {@code y} is the top of the glyphs; capital
 * letters are 7 px high. With a {@link #width}, text is clipped ("...") and can be centered.
 */
public final class Label implements Element {

    private Function<View, Boolean> visible = v -> true;

    public enum Align { LEFT, CENTER, RIGHT }

    /** Default label color (vanilla GUI dark gray). */
    public static final TextColor DARK = TextColor.color(0x404040);

    private final int x;
    private final int y;
    private final Function<View, String> text;
    private Function<View, TextColor> color = v -> DARK;
    private int width = -1;
    private Align align = Align.LEFT;
    private boolean shadow;
    private boolean wrap;
    private int lineHeight = 10;

    private Label(int x, int y, Function<View, String> text) {
        this.x = x;
        this.y = y;
        this.text = text;
    }

    public static Label of(int x, int y, String text) {
        return new Label(x, y, v -> text);
    }

    public static Label of(int x, int y, Function<View, String> text) {
        return new Label(x, y, text);
    }

    public Label color(TextColor color) {
        this.color = v -> color;
        return this;
    }

    public Label color(Function<View, TextColor> color) {
        this.color = color;
        return this;
    }

    /** Area width in pixels: longer text is clipped, and alignment refers to this area. */
    public Label width(int width) {
        this.width = width;
        return this;
    }

    /**
     * Draws the text with a drop shadow, like button and list text. Off by default: labels are
     * usually dark text on a light panel, like inventory titles, which have no shadow either.
     */
    public Label shadow(boolean shadow) {
        this.shadow = shadow;
        return this;
    }

    public boolean shadow() {
        return shadow;
    }

    /**
     * Breaks the text into lines at spaces so that each line fits {@link #width} (needs a width);
     * "\n" in the text always starts a new line. Lines are {@link #lineHeight} apart.
     */
    public Label wrap(boolean wrap) {
        this.wrap = wrap;
        return this;
    }

    public boolean wrap() {
        return wrap;
    }

    /** Distance between wrapped lines in pixels (default 10). */
    public Label lineHeight(int lineHeight) {
        this.lineHeight = Math.max(8, lineHeight);
        return this;
    }

    public int lineHeight() {
        return lineHeight;
    }

    public Label align(Align align) {
        this.align = align;
        return this;
    }

    /** Shortcut for {@code width(width).align(CENTER)}. */
    public Label center(int width) {
        return width(width).align(Align.CENTER);
    }

    @Override
    public int x() {
        return x;
    }

    @Override
    public int y() {
        return y;
    }

    public Function<View, String> text() {
        return text;
    }

    public Function<View, TextColor> color() {
        return color;
    }

    public int width() {
        return width;
    }

    public Align align() {
        return align;
    }

    /** Shown only while this returns true (evaluated each time the scene is drawn); hidden elements do not take clicks. */
    public Label visible(Function<View, Boolean> visible) {
        this.visible = visible;
        return this;
    }

    @Override
    public Function<View, Boolean> visible() {
        return visible;
    }
}
