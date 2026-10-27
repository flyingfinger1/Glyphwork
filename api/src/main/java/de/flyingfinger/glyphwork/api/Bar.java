package de.flyingfinger.glyphwork.api;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextColor;
import org.jspecify.annotations.Nullable;

import java.util.function.Function;
import java.util.function.Predicate;

/**
 * A progress bar: a frame ("slot" by default) filled from the left by {@code value / max}, pixel
 * exact, in a color of your choice; optionally with a text in the middle ("12 / 30"). 8 px high
 * (6 px fill) or 16 px high (14 px fill).
 */
public final class Bar implements Element {

    /** Default fill color (vanilla experience green). */
    public static final TextColor GREEN = TextColor.color(0x80FF20);

    private Predicate<View> visible = v -> true;

    private final int x;
    private final int y;
    private final int width;
    private int height = 8;
    private Function<View, Double> value = v -> 0.0;
    private Function<View, Double> max = v -> 1.0;
    private Function<View, TextColor> fill = v -> GREEN;
    private Function<View, String> text = v -> "";
    private String frame = "slot";
    private @Nullable Function<View, @Nullable Component> tooltip;

    private Bar(int x, int y, int width) {
        this.x = x;
        this.y = y;
        this.width = width;
    }

    public static Bar of(int x, int y, int width) {
        return new Bar(x, y, width);
    }

    /** 8 (default) or 16 px. */
    public Bar height(int height) {
        this.height = height;
        return this;
    }

    /** The filled part is value / max (clamped to 0..1). */
    public Bar value(Function<View, ? extends Number> value) {
        this.value = v -> {
            Number n = value.apply(v);
            return n == null ? 0.0 : n.doubleValue();
        };
        return this;
    }

    public Bar max(double max) {
        this.max = v -> max;
        return this;
    }

    public Bar max(Function<View, ? extends Number> max) {
        this.max = v -> {
            Number n = max.apply(v);
            return n == null ? 0.0 : n.doubleValue();
        };
        return this;
    }

    /** Color of the filled part (default {@link #GREEN}). */
    public Bar fill(TextColor color) {
        this.fill = v -> color;
        return this;
    }

    public Bar fill(Function<View, TextColor> color) {
        this.fill = color;
        return this;
    }

    /** Text in the middle of the bar (white with a shadow). */
    public Bar text(Function<View, String> text) {
        this.text = text;
        return this;
    }

    /** Frame of the empty bar (default "slot"). */
    public Bar frame(String frame) {
        this.frame = frame;
        return this;
    }

    public Bar tooltip(Function<View, @Nullable Component> tooltip) {
        this.tooltip = tooltip;
        return this;
    }

    /** Shown only while this returns true. */
    public Bar visible(Predicate<View> visible) {
        this.visible = visible;
        return this;
    }

    @Override
    public Predicate<View> visible() {
        return visible;
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
    public int width() {
        return width;
    }

    @org.jetbrains.annotations.ApiStatus.Internal
    public int height() {
        return height;
    }

    @org.jetbrains.annotations.ApiStatus.Internal
    public Function<View, Double> value() {
        return value;
    }

    @org.jetbrains.annotations.ApiStatus.Internal
    public Function<View, Double> max() {
        return max;
    }

    @org.jetbrains.annotations.ApiStatus.Internal
    public Function<View, TextColor> fill() {
        return fill;
    }

    @org.jetbrains.annotations.ApiStatus.Internal
    public Function<View, String> text() {
        return text;
    }

    @org.jetbrains.annotations.ApiStatus.Internal
    public String frame() {
        return frame;
    }

    @org.jetbrains.annotations.ApiStatus.Internal
    public @Nullable Function<View, @Nullable Component> tooltip() {
        return tooltip;
    }

    /** Filled share (0..1) of a value; max <= 0 counts as empty. */
    @org.jetbrains.annotations.ApiStatus.Internal
    public static double ratio(double value, double max) {
        if (!(max > 0) || Double.isNaN(value)) {
            return 0;
        }
        return Math.max(0, Math.min(1, value / max));
    }
}
