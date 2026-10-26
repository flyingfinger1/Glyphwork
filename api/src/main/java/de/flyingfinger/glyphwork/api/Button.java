package de.flyingfinger.glyphwork.api;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextColor;
import org.jspecify.annotations.Nullable;

import java.util.function.Consumer;
import java.util.function.Function;

/**
 * A clickable frame ("button" by default) with centered text. The theme's button texture is light
 * gray and is multiplied with the tint, so any color works. A disabled button is drawn gray and
 * ignores clicks.
 */
public final class Button implements Element {

    /** Default tint: vanilla-like gray. */
    public static final TextColor GRAY = TextColor.color(0x9C9C9C);
    public static final TextColor DISABLED = TextColor.color(0x5A5A5A);

    private final int x;
    private final int y;
    private final int w;
    private final int h;
    private String frame = "button";
    private Function<View, String> text = v -> "";
    private Function<View, TextColor> textColor = v -> TextColor.color(0xFFFFFF);
    private Function<View, TextColor> tint = v -> GRAY;
    private @Nullable Function<View, @Nullable Component> tooltip;
    private Function<View, Boolean> enabled = v -> true;
    private @Nullable Consumer<Click> onClick;
    private @Nullable Function<View, @Nullable String> icon;

    private Button(int x, int y, int w, int h) {
        this.x = x;
        this.y = y;
        this.w = w;
        this.h = h;
    }

    public static Button of(int x, int y, int width, int height) {
        return new Button(x, y, width, height);
    }

    public Button frame(String frame) {
        this.frame = frame;
        return this;
    }

    public Button text(String text) {
        this.text = v -> text;
        return this;
    }

    public Button text(Function<View, String> text) {
        this.text = text;
        return this;
    }

    public Button textColor(TextColor color) {
        this.textColor = v -> color;
        return this;
    }

    public Button textColor(Function<View, TextColor> color) {
        this.textColor = color;
        return this;
    }

    public Button tint(TextColor tint) {
        this.tint = v -> tint;
        return this;
    }

    public Button tint(Function<View, TextColor> tint) {
        this.tint = tint;
        return this;
    }

    public Button tooltip(String tooltip) {
        return tooltip(Component.text(tooltip));
    }

    public Button tooltip(Component tooltip) {
        this.tooltip = v -> tooltip;
        return this;
    }

    public Button tooltip(Function<View, @Nullable Component> tooltip) {
        this.tooltip = tooltip;
        return this;
    }

    public Button enabled(Function<View, Boolean> enabled) {
        this.enabled = enabled;
        return this;
    }

    /** A 16 x 16 item icon at the left of the button (the text moves right). Best with height 20+. */
    public Button icon(String item) {
        this.icon = v -> item;
        return this;
    }

    public Button icon(Function<View, @Nullable String> item) {
        this.icon = item;
        return this;
    }

    public @Nullable Function<View, @Nullable String> icon() {
        return icon;
    }

    public Button onClick(Consumer<Click> onClick) {
        this.onClick = onClick;
        return this;
    }

    @Override
    public int x() {
        return x;
    }

    @Override
    public int y() {
        return y;
    }

    public int width() {
        return w;
    }

    public int height() {
        return h;
    }

    public String frame() {
        return frame;
    }

    public Function<View, String> text() {
        return text;
    }

    public Function<View, TextColor> textColor() {
        return textColor;
    }

    public Function<View, TextColor> tint() {
        return tint;
    }

    public @Nullable Function<View, @Nullable Component> tooltip() {
        return tooltip;
    }

    public Function<View, Boolean> enabled() {
        return enabled;
    }

    public @Nullable Consumer<Click> onClick() {
        return onClick;
    }
}
