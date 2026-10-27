package de.flyingfinger.glyphwork.api;

import net.kyori.adventure.text.format.TextColor;
import org.jspecify.annotations.Nullable;

import java.util.function.Function;
import java.util.function.Predicate;

/**
 * One line of text in the Minecraft font (no shadow). {@code y} is the top of the glyphs; capital
 * letters are 7 px high. With a {@link #width}, text is clipped ("...") and can be centered.
 */
public final class Label implements Element {

    private Predicate<View> visible = v -> true;

    public enum Align { LEFT, CENTER, RIGHT }

    private final int x;
    private final int y;
    private final Function<View, String> text;
    private Function<View, @Nullable TextColor> color = v -> null;
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

    /** Text color; null (the default) = the skin's text color (dark gray in vanilla, light on dark skins). */
    public Label color(@Nullable TextColor color) {
        this.color = v -> color;
        return this;
    }

    /** Text color per viewer; null = the skin's text color. */
    public Label color(Function<View, @Nullable TextColor> color) {
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

    @org.jetbrains.annotations.ApiStatus.Internal
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

    @org.jetbrains.annotations.ApiStatus.Internal
    public boolean wrap() {
        return wrap;
    }

    private boolean markup;
    private boolean bold;
    private boolean italic;
    private boolean underline;
    private boolean strikethrough;

    /**
     * Reads the text as MiniMessage: {@code <b>}, {@code <i>}, {@code <u>}, {@code <st>} and colors
     * for parts of it ("Price: <b>100</b> <gold>coins"). Off by default (the text is shown as it is).
     */
    @org.jetbrains.annotations.ApiStatus.Experimental
    public Label markup(boolean markup) {
        this.markup = markup;
        return this;
    }

    @org.jetbrains.annotations.ApiStatus.Internal
    public boolean markup() {
        return markup;
    }

    /** The whole text bold (each character 1 px wider). */
    @org.jetbrains.annotations.ApiStatus.Experimental
    public Label bold(boolean bold) {
        this.bold = bold;
        return this;
    }

    @org.jetbrains.annotations.ApiStatus.Internal
    public boolean bold() {
        return bold;
    }

    @org.jetbrains.annotations.ApiStatus.Experimental
    public Label italic(boolean italic) {
        this.italic = italic;
        return this;
    }

    @org.jetbrains.annotations.ApiStatus.Internal
    public boolean italic() {
        return italic;
    }

    @org.jetbrains.annotations.ApiStatus.Experimental
    public Label underline(boolean underline) {
        this.underline = underline;
        return this;
    }

    @org.jetbrains.annotations.ApiStatus.Internal
    public boolean underline() {
        return underline;
    }

    @org.jetbrains.annotations.ApiStatus.Experimental
    public Label strikethrough(boolean strikethrough) {
        this.strikethrough = strikethrough;
        return this;
    }

    @org.jetbrains.annotations.ApiStatus.Internal
    public boolean strikethrough() {
        return strikethrough;
    }

    /** Distance between wrapped lines in pixels (default 10). */
    public Label lineHeight(int lineHeight) {
        this.lineHeight = Math.max(8, lineHeight);
        return this;
    }

    @org.jetbrains.annotations.ApiStatus.Internal
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

    @org.jetbrains.annotations.ApiStatus.Internal
    public Function<View, String> text() {
        return text;
    }

    @org.jetbrains.annotations.ApiStatus.Internal
    public Function<View, @Nullable TextColor> color() {
        return color;
    }

    @org.jetbrains.annotations.ApiStatus.Internal
    public int width() {
        return width;
    }

    @org.jetbrains.annotations.ApiStatus.Internal
    public Align align() {
        return align;
    }

    /** Shown only while this returns true (evaluated each time the scene is drawn); hidden elements do not take clicks. */
    public Label visible(Predicate<View> visible) {
        this.visible = visible;
        return this;
    }

    @Override
    public Predicate<View> visible() {
        return visible;
    }
}
