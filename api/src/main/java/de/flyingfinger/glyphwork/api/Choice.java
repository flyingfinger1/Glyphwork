package de.flyingfinger.glyphwork.api;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextColor;
import org.jspecify.annotations.Nullable;

import java.util.List;
import java.util.function.BiConsumer;
import java.util.function.Function;

/**
 * A button that steps through options: "&lt;  Normal  &gt;". A click on its left half picks the
 * previous option, on its right half the next one (both wrap around). After {@link #onSelect} the
 * scene is drawn again (unless the handler opened, closed or redrew something itself).
 */
public final class Choice implements Element {

    private Function<View, Boolean> visible = v -> true;

    private final int x;
    private final int y;
    private final int width;
    private int height = 16;
    private final List<String> options;
    private Function<String, String> text = s -> s;
    private Function<View, @Nullable String> selected;
    private @Nullable BiConsumer<Click, String> onSelect;
    private Function<View, TextColor> tint = v -> Button.GRAY;
    private @Nullable Function<View, @Nullable Component> tooltip;

    private Choice(int x, int y, int width, List<String> options) {
        if (options.isEmpty()) {
            throw new IllegalArgumentException("choice needs at least one option");
        }
        this.x = x;
        this.y = y;
        this.width = width;
        this.options = List.copyOf(options);
        this.selected = v -> this.options.getFirst();
    }

    /** @param options ids of the options, in order; {@link #text} turns them into what is shown */
    public static Choice of(int x, int y, int width, List<String> options) {
        return new Choice(x, y, width, options);
    }

    public static Choice of(int x, int y, int width, String... options) {
        return of(x, y, width, List.of(options));
    }

    public Choice height(int height) {
        this.height = height;
        return this;
    }

    /** What an option shows (default: its id). */
    public Choice text(Function<String, String> text) {
        this.text = text;
        return this;
    }

    /** The current option (an unknown id shows the first option). */
    public Choice selected(Function<View, @Nullable String> selected) {
        this.selected = selected;
        return this;
    }

    /** Called with the id of the newly picked option. */
    public Choice onSelect(BiConsumer<Click, String> onSelect) {
        this.onSelect = onSelect;
        return this;
    }

    public Choice tint(Function<View, TextColor> tint) {
        this.tint = tint;
        return this;
    }

    public Choice tooltip(Function<View, @Nullable Component> tooltip) {
        this.tooltip = tooltip;
        return this;
    }

    /** Shown only while this returns true; hidden elements do not take clicks. */
    public Choice visible(Function<View, Boolean> visible) {
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

    public int height() {
        return height;
    }

    public List<String> options() {
        return options;
    }

    public Function<String, String> text() {
        return text;
    }

    public Function<View, @Nullable String> selected() {
        return selected;
    }

    public @Nullable BiConsumer<Click, String> onSelect() {
        return onSelect;
    }

    public Function<View, TextColor> tint() {
        return tint;
    }

    public @Nullable Function<View, @Nullable Component> tooltip() {
        return tooltip;
    }

    /** Index of the current option (0 if unknown). */
    public int index(View view) {
        int i = options.indexOf(selected.apply(view));
        return Math.max(0, i);
    }

    /** The option {@code delta} steps from the current one, wrapping around. */
    public String step(View view, int delta) {
        int n = options.size();
        return options.get(((index(view) + delta) % n + n) % n);
    }
}
