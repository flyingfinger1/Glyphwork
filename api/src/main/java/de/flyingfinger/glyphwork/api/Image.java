package de.flyingfinger.glyphwork.api;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextColor;
import org.jspecify.annotations.Nullable;

import java.util.function.Consumer;
import java.util.function.Function;

/**
 * One of the server's own pictures: a PNG in {@code plugins/Glyphwork/images/}, named by its path
 * without ".png" ("logo", "ranks/gold"). Drawn at its own size, pixel exact, above frames like text
 * and icons. Pictures of any size work; the pack cuts them into tiles. An unknown name draws nothing
 * (and is logged once).
 */
public final class Image implements Element {

    private Function<View, Boolean> visible = v -> true;

    private final int x;
    private final int y;
    private final Function<View, @Nullable String> image;
    private @Nullable Function<View, @Nullable TextColor> tint;
    private @Nullable Function<View, @Nullable Component> tooltip;
    private @Nullable Consumer<Click> onClick;

    private Image(int x, int y, Function<View, @Nullable String> image) {
        this.x = x;
        this.y = y;
        this.image = image;
    }

    public static Image of(int x, int y, String image) {
        return new Image(x, y, v -> image);
    }

    /** The picture per viewer, e.g. {@code v -> "ranks/" + rankOf(v.player())}. */
    public static Image of(int x, int y, Function<View, @Nullable String> image) {
        return new Image(x, y, image);
    }

    /** Multiplies the picture's colors (white parts take the tint). */
    public Image tint(Function<View, @Nullable TextColor> tint) {
        this.tint = tint;
        return this;
    }

    public Image tooltip(Component tooltip) {
        this.tooltip = v -> tooltip;
        return this;
    }

    public Image tooltip(Function<View, @Nullable Component> tooltip) {
        this.tooltip = tooltip;
        return this;
    }

    public Image onClick(Consumer<Click> onClick) {
        this.onClick = onClick;
        return this;
    }

    /** Shown only while this returns true; hidden elements do not take clicks. */
    public Image visible(Function<View, Boolean> visible) {
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

    public Function<View, @Nullable String> image() {
        return image;
    }

    public @Nullable Function<View, @Nullable TextColor> tint() {
        return tint;
    }

    public @Nullable Function<View, @Nullable Component> tooltip() {
        return tooltip;
    }

    public @Nullable Consumer<Click> onClick() {
        return onClick;
    }
}
