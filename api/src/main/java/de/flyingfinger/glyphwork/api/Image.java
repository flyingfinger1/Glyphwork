package de.flyingfinger.glyphwork.api;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextColor;
import org.jspecify.annotations.Nullable;

import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;

/**
 * One of the server's own pictures: a PNG in {@code plugins/Glyphwork/images/}, named by its path
 * without ".png" ("logo", "ranks/gold"). Drawn at its own size, pixel exact, above frames like text
 * and icons. Pictures of any size work; the pack cuts them into tiles. An unknown name draws nothing
 * (and is logged once).
 *
 * <p>{@link #size(int, int)} and {@link #scale(double)} draw the picture smaller or bigger. Each size
 * is a picture of its own in the pack, made when the pack is built: YAML menus add theirs by
 * themselves, plugins announce theirs with {@link Glyphwork#imageSize(String, int, int)}.
 */
public final class Image implements Element {

    private Predicate<View> visible = v -> true;

    private final int x;
    private final int y;
    private final Function<View, @Nullable String> image;
    private @Nullable Function<View, @Nullable TextColor> tint;
    private @Nullable Function<View, @Nullable Component> tooltip;
    private @Nullable Consumer<Click> onClick;
    private int width;
    private int height;
    private double scale;

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

    /**
     * Draws the picture at this size; 0 for one side keeps the aspect ratio ({@code size(32, 0)}).
     * The size must be in the pack: see {@link Glyphwork#imageSize(String, int, int)}.
     */
    @org.jetbrains.annotations.ApiStatus.Experimental
    public Image size(int width, int height) {
        if (width < 0 || height < 0 || width == 0 && height == 0) {
            throw new IllegalArgumentException("size " + width + " x " + height + ": give at least one side");
        }
        this.width = width;
        this.height = height;
        this.scale = 0;
        return this;
    }

    /** Draws the picture scaled, e.g. 0.5 for half its size (both sides rounded). */
    @org.jetbrains.annotations.ApiStatus.Experimental
    public Image scale(double scale) {
        if (!(scale > 0)) {
            throw new IllegalArgumentException("scale must be above 0, got " + scale);
        }
        this.scale = scale;
        this.width = 0;
        this.height = 0;
        return this;
    }

    /** Shown only while this returns true; hidden elements do not take clicks. */
    public Image visible(Predicate<View> visible) {
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
    public Function<View, @Nullable String> image() {
        return image;
    }

    @org.jetbrains.annotations.ApiStatus.Internal
    public @Nullable Function<View, @Nullable TextColor> tint() {
        return tint;
    }

    @org.jetbrains.annotations.ApiStatus.Internal
    public @Nullable Function<View, @Nullable Component> tooltip() {
        return tooltip;
    }

    @org.jetbrains.annotations.ApiStatus.Internal
    public @Nullable Consumer<Click> onClick() {
        return onClick;
    }

    /** Asked width, or 0 (from the aspect ratio, or the picture's own size). */
    @org.jetbrains.annotations.ApiStatus.Internal
    public int width() {
        return width;
    }

    @org.jetbrains.annotations.ApiStatus.Internal
    public int height() {
        return height;
    }

    /** Asked scale, or 0. */
    @org.jetbrains.annotations.ApiStatus.Internal
    public double scale() {
        return scale;
    }
}
