package de.flyingfinger.glyphwork.api;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextColor;
import org.jetbrains.annotations.ApiStatus;
import org.jspecify.annotations.Nullable;

import java.util.function.BiFunction;
import java.util.function.DoubleFunction;
import java.util.function.Function;
import java.util.function.ObjDoubleConsumer;
import java.util.function.Predicate;
import java.util.function.ToDoubleFunction;

/**
 * A slider drawn from the skin: a flat track ("slot" frame) and a knob ("button" frame), 16 px high.
 * The server cannot follow the mouse, so there is no dragging: a click on the track moves the knob
 * there, and hovering shows the value at that spot. After {@link #onChange} the scene is drawn
 * again (unless the handler opened, closed or redrew something itself).
 */
public final class Slider implements Element {

    /** Height of a slider in pixels (fixed). */
    public static final int HEIGHT = 16;

    private Predicate<View> visible = v -> true;

    private final int x;
    private final int y;
    private final int width;
    private final double min;
    private final double max;
    private final double step;
    private ToDoubleFunction<View> value;
    private @Nullable ObjDoubleConsumer<Click> onChange;
    private Function<View, TextColor> tint = v -> Button.GRAY;
    private @Nullable BiFunction<View, Double, @Nullable Component> tooltip;

    private Slider(int x, int y, int width, double min, double max, double step) {
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

    /**
     * A slider {@code width} px wide (rounded down to whole tiles) for values from min to max in steps.
     *
     * @throws IllegalArgumentException unless min &lt; max and step &gt; 0
     */
    public static Slider of(int x, int y, int width, double min, double max, double step) {
        return new Slider(x, y, width, min, max, step);
    }

    /** The current value per viewer (clamped to the range and rounded to a step when drawn). */
    public Slider value(ToDoubleFunction<View> value) {
        this.value = value;
        return this;
    }

    /** Called with the value the player clicked. */
    public Slider onChange(ObjDoubleConsumer<Click> onChange) {
        this.onChange = onChange;
        return this;
    }

    /** Color of the knob (default gray). */
    public Slider tint(TextColor tint) {
        this.tint = v -> tint;
        return this;
    }

    /** Color of the knob per viewer. */
    public Slider tint(Function<View, TextColor> tint) {
        this.tint = tint;
        return this;
    }

    /** Tooltip for each spot of the track, given the value there (default: the value). */
    public Slider tooltip(DoubleFunction<@Nullable Component> tooltip) {
        this.tooltip = (v, value) -> tooltip.apply(value);
        return this;
    }

    /** Same, with the viewer (for texts that depend on the player). */
    public Slider tooltip(BiFunction<View, Double, @Nullable Component> tooltip) {
        this.tooltip = tooltip;
        return this;
    }

    /** Shown only while this returns true; hidden elements do not take clicks. */
    public Slider visible(Predicate<View> visible) {
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

    @ApiStatus.Internal
    public int width() {
        return width;
    }

    @ApiStatus.Internal
    public double min() {
        return min;
    }

    @ApiStatus.Internal
    public double max() {
        return max;
    }

    @ApiStatus.Internal
    public double step() {
        return step;
    }

    @ApiStatus.Internal
    public ToDoubleFunction<View> value() {
        return value;
    }

    @ApiStatus.Internal
    public @Nullable ObjDoubleConsumer<Click> onChange() {
        return onChange;
    }

    @ApiStatus.Internal
    public Function<View, TextColor> tint() {
        return tint;
    }

    @ApiStatus.Internal
    public @Nullable BiFunction<View, Double, @Nullable Component> tooltip() {
        return tooltip;
    }

    /** Number of values on the slider. */
    @ApiStatus.Internal
    public int positions() {
        return (int) Math.round((max - min) / step) + 1;
    }

    /** The value at position {@code i} (0 = min). */
    @ApiStatus.Internal
    public double valueAt(int i) {
        return Math.min(max, min + i * step);
    }

    /** The position of a value (clamped and rounded to the nearest step). */
    @ApiStatus.Internal
    public int positionOf(double value) {
        double v = Double.isNaN(value) ? min : Math.max(min, Math.min(max, value));
        return (int) Math.round((v - min) / step);
    }
}
