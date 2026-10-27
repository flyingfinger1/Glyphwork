package de.flyingfinger.glyphwork.api;

import net.kyori.adventure.text.Component;
import org.jspecify.annotations.Nullable;

import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;

/**
 * A player's face, painted pixel by pixel from the skin: 8, 16, 24 or 32 px, at any position (unlike
 * the client's own 8 x 8 heads, which only fit its 9 px text grid). The server loads the skin in the
 * background; until it is there, the small client head stands in, and the scene is drawn again when
 * the face arrives. No pack change: works for every player, also ones who joined just now.
 */
@org.jetbrains.annotations.ApiStatus.Experimental
public final class Head implements Element {

    private Predicate<View> visible = v -> true;

    private final int x;
    private final int y;
    private final Function<View, @Nullable String> player;
    private int size = 32;
    private @Nullable Function<View, @Nullable Component> tooltip;
    private @Nullable Consumer<Click> onClick;

    private Head(int x, int y, Function<View, @Nullable String> player) {
        this.x = x;
        this.y = y;
        this.player = player;
    }

    /** @param player name or UUID */
    public static Head of(int x, int y, String player) {
        return new Head(x, y, v -> player);
    }

    /** The player per viewer, e.g. {@code v -> v.player().getName()}. */
    public static Head of(int x, int y, Function<View, @Nullable String> player) {
        return new Head(x, y, player);
    }

    /** 8, 16, 24 or 32 px (default 32); other values are rounded down to a multiple of 8. */
    public Head size(int size) {
        this.size = sizeOf(size);
        return this;
    }

    public Head tooltip(Component tooltip) {
        this.tooltip = v -> tooltip;
        return this;
    }

    public Head tooltip(Function<View, @Nullable Component> tooltip) {
        this.tooltip = tooltip;
        return this;
    }

    public Head onClick(Consumer<Click> onClick) {
        this.onClick = onClick;
        return this;
    }

    /** Shown only while this returns true; hidden elements do not take clicks. */
    public Head visible(Predicate<View> visible) {
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
    public Function<View, @Nullable String> player() {
        return player;
    }

    @org.jetbrains.annotations.ApiStatus.Internal
    public int size() {
        return size;
    }

    @org.jetbrains.annotations.ApiStatus.Internal
    public @Nullable Function<View, @Nullable Component> tooltip() {
        return tooltip;
    }

    @org.jetbrains.annotations.ApiStatus.Internal
    public @Nullable Consumer<Click> onClick() {
        return onClick;
    }

    /** A head size: 8, 16, 24 or 32. */
    @org.jetbrains.annotations.ApiStatus.Internal
    public static int sizeOf(int size) {
        return Math.max(8, Math.min(32, size / 8 * 8));
    }
}
