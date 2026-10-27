package de.flyingfinger.glyphwork.api;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextColor;
import org.jspecify.annotations.Nullable;

import java.util.function.BiConsumer;
import java.util.function.Function;
import java.util.function.Predicate;

/**
 * A checkbox drawn from the skin: a 16 x 16 box ("slot" frame) with a check mark when checked, and
 * an optional text to its right. Box and text are clickable. After {@link #onChange} the scene is
 * drawn again (unless the handler opened, closed or redrew something itself).
 */
public final class Checkbox implements Element {

    public static final int SIZE = 16;

    private Predicate<View> visible = v -> true;

    private final int x;
    private final int y;
    private Function<View, String> text = v -> "";
    private Function<View, @Nullable TextColor> color = v -> null;
    private Predicate<View> checked = v -> false;
    private @Nullable BiConsumer<Click, Boolean> onChange;
    private @Nullable Function<View, @Nullable Component> tooltip;

    private Checkbox(int x, int y) {
        this.x = x;
        this.y = y;
    }

    public static Checkbox of(int x, int y) {
        return new Checkbox(x, y);
    }

    /** Text right of the box (default color: the skin's text color, like labels). */
    public Checkbox text(String text) {
        this.text = v -> text;
        return this;
    }

    public Checkbox text(Function<View, String> text) {
        this.text = text;
        return this;
    }

    /** Text color per viewer; null (the default) = the skin's text color. */
    public Checkbox color(Function<View, @Nullable TextColor> color) {
        this.color = color;
        return this;
    }

    public Checkbox checked(Predicate<View> checked) {
        this.checked = checked;
        return this;
    }

    /** Called with the new state (the opposite of the drawn one). */
    public Checkbox onChange(BiConsumer<Click, Boolean> onChange) {
        this.onChange = onChange;
        return this;
    }

    public Checkbox tooltip(Function<View, @Nullable Component> tooltip) {
        this.tooltip = tooltip;
        return this;
    }

    /** Shown only while this returns true; hidden elements do not take clicks. */
    public Checkbox visible(Predicate<View> visible) {
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
    public Function<View, String> text() {
        return text;
    }

    @org.jetbrains.annotations.ApiStatus.Internal
    public Function<View, @Nullable TextColor> color() {
        return color;
    }

    @org.jetbrains.annotations.ApiStatus.Internal
    public Predicate<View> checked() {
        return checked;
    }

    @org.jetbrains.annotations.ApiStatus.Internal
    public @Nullable BiConsumer<Click, Boolean> onChange() {
        return onChange;
    }

    @org.jetbrains.annotations.ApiStatus.Internal
    public @Nullable Function<View, @Nullable Component> tooltip() {
        return tooltip;
    }
}
