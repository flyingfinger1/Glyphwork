package de.flyingfinger.glyphwork.api;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextColor;
import org.jspecify.annotations.Nullable;

import java.util.function.BiConsumer;
import java.util.function.Function;

/**
 * A checkbox drawn from the skin: a 16 x 16 box ("slot" frame) with a check mark when checked, and
 * an optional text to its right. Box and text are clickable. After {@link #onToggle} the scene is
 * drawn again (unless the handler opened, closed or redrew something itself).
 */
public final class Checkbox implements Element {

    public static final int SIZE = 16;

    private Function<View, Boolean> visible = v -> true;

    private final int x;
    private final int y;
    private Function<View, String> text = v -> "";
    private Function<View, TextColor> textColor = v -> Label.DARK;
    private Function<View, Boolean> checked = v -> false;
    private @Nullable BiConsumer<Click, Boolean> onToggle;
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

    public Checkbox textColor(Function<View, TextColor> color) {
        this.textColor = color;
        return this;
    }

    public Checkbox checked(Function<View, Boolean> checked) {
        this.checked = checked;
        return this;
    }

    /** Called with the new state (the opposite of the drawn one). */
    public Checkbox onToggle(BiConsumer<Click, Boolean> onToggle) {
        this.onToggle = onToggle;
        return this;
    }

    public Checkbox tooltip(Function<View, @Nullable Component> tooltip) {
        this.tooltip = tooltip;
        return this;
    }

    /** Shown only while this returns true; hidden elements do not take clicks. */
    public Checkbox visible(Function<View, Boolean> visible) {
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

    public Function<View, String> text() {
        return text;
    }

    public Function<View, TextColor> textColor() {
        return textColor;
    }

    public Function<View, Boolean> checked() {
        return checked;
    }

    public @Nullable BiConsumer<Click, Boolean> onToggle() {
        return onToggle;
    }

    public @Nullable Function<View, @Nullable Component> tooltip() {
        return tooltip;
    }
}
