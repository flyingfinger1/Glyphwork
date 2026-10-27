package de.flyingfinger.glyphwork.api;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextColor;
import org.jspecify.annotations.Nullable;

import java.util.function.BiConsumer;
import java.util.function.BiFunction;
import java.util.function.Function;

/**
 * A slider drawn from the skin: a flat track ("slot" frame) and a knob ("button" frame), 16 px high.
 * The server cannot follow the mouse, so there is no dragging: a click on the track moves the knob
 * there, and hovering shows the value at that spot. After {@link #onChange} the scene is drawn
 * again (unless the handler opened, closed or redrew something itself).
 */
public final class Slider implements Element {

    public static final int HEIGHT = 16;

    private Function<View, Boolean> visible = v -> true;

    private final int x;
    private final int y;
    private final int width;
    private final float min;
    private final float max;
    private final float step;
    private Function<View, Float> value;
    private @Nullable BiConsumer<Click, Float> onChange;
    private Function<View, TextColor> tint = v -> Button.GRAY;
    private @Nullable BiFunction<View, Float, @Nullable Component> tooltip;

    private Slider(int x, int y, int width, float min, float max, float step) {
        if (!(max > min) || !(step > 0)) {
            throw new IllegalArgumentException("slider needs min < max and step > 0");
        }
        this.x = x;
        this.y = y;
        this.width = width;
        this.min = min;
        this.max = max;
        this.step = step;
        this.value = v -> min;
    }

    /** A slider {@code width} px wide (rounded down to whole tiles) for values from min to max in steps. */
    public static Slider of(int x, int y, int width, float min, float max, float step) {
        return new Slider(x, y, width, min, max, step);
    }

    /** The current value (clamped to the range when drawn). */
    public Slider value(Function<View, Float> value) {
        this.value = value;
        return this;
    }

    /** Called with the value the player clicked. */
    public Slider onChange(BiConsumer<Click, Float> onChange) {
        this.onChange = onChange;
        return this;
    }

    /** Color of the knob (default gray). */
    public Slider tint(Function<View, TextColor> tint) {
        this.tint = tint;
        return this;
    }

    /** Tooltip for each spot of the track, given the value there (default: the value). */
    public Slider tooltip(Function<Float, @Nullable Component> tooltip) {
        this.tooltip = (v, value) -> tooltip.apply(value);
        return this;
    }

    /** Same, with the viewer (for texts that depend on the player). */
    public Slider tooltip(BiFunction<View, Float, @Nullable Component> tooltip) {
        this.tooltip = tooltip;
        return this;
    }

    /** Shown only while this returns true; hidden elements do not take clicks. */
    public Slider visible(Function<View, Boolean> visible) {
        this.visible = visible;
        return this;
    }

    @Override
    public Function<View, Boolean> visible() {
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

    public int width() {
        return width;
    }

    public float min() {
        return min;
    }

    public float max() {
        return max;
    }

    public float step() {
        return step;
    }

    public Function<View, Float> value() {
        return value;
    }

    public @Nullable BiConsumer<Click, Float> onChange() {
        return onChange;
    }

    public Function<View, TextColor> tint() {
        return tint;
    }

    public @Nullable BiFunction<View, Float, @Nullable Component> tooltip() {
        return tooltip;
    }

    /** Number of values on the slider. */
    public int positions() {
        return Math.round((max - min) / step) + 1;
    }

    /** The value at position {@code i} (0 = min). */
    public float valueAt(int i) {
        return Math.min(max, min + i * step);
    }

    /** The position of a value (clamped and rounded to the nearest step). */
    public int positionOf(float value) {
        float v = Float.isNaN(value) ? min : Math.max(min, Math.min(max, value));
        return Math.round((v - min) / step);
    }
}
