package de.flyingfinger.glyphwork.api;

import net.kyori.adventure.text.Component;
import org.jspecify.annotations.Nullable;

import java.util.function.Consumer;
import java.util.function.Function;

/**
 * An invisible clickable area (at least 8 x 8 px), e.g. to make a label or a part of an image
 * clickable. It wins over everything drawn before it.
 */
public final class Hitbox implements Element {

    private Function<View, Boolean> visible = v -> true;

    private final int x;
    private final int y;
    private final int w;
    private final int h;
    private @Nullable Function<View, @Nullable Component> tooltip;
    private @Nullable Consumer<Click> onClick;

    private Hitbox(int x, int y, int w, int h) {
        this.x = x;
        this.y = y;
        this.w = Math.max(8, w);
        this.h = Math.max(8, h);
    }

    public static Hitbox of(int x, int y, int width, int height) {
        return new Hitbox(x, y, width, height);
    }

    public Hitbox tooltip(Component tooltip) {
        this.tooltip = v -> tooltip;
        return this;
    }

    public Hitbox tooltip(Function<View, @Nullable Component> tooltip) {
        this.tooltip = tooltip;
        return this;
    }

    public Hitbox onClick(Consumer<Click> onClick) {
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

    public @Nullable Function<View, @Nullable Component> tooltip() {
        return tooltip;
    }

    public @Nullable Consumer<Click> onClick() {
        return onClick;
    }

    /** Shown only while this returns true (evaluated each time the scene is drawn); hidden elements do not take clicks. */
    public Hitbox visible(Function<View, Boolean> visible) {
        this.visible = visible;
        return this;
    }

    @Override
    public Function<View, Boolean> visible() {
        return visible;
    }
}
