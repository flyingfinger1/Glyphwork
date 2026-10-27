package de.flyingfinger.glyphwork.api;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextColor;
import org.jspecify.annotations.Nullable;

import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;

/**
 * An autotiled frame from the theme ("panel" by default; also "button", "slot" or any frame a
 * theme defines). Sizes are rounded down to whole tiles of the active theme.
 */
public final class Panel implements Element {

    private Predicate<View> visible = v -> true;

    private final int x;
    private final int y;
    private final int w;
    private final int h;
    private final boolean @Nullable [][] cells;
    private String frame = "panel";
    private Function<View, @Nullable TextColor> tint = v -> null;
    private @Nullable Function<View, @Nullable Component> tooltip;
    private @Nullable Consumer<Click> onClick;

    private Panel(int x, int y, int w, int h, boolean @Nullable [][] cells) {
        this.x = x;
        this.y = y;
        this.w = w;
        this.h = h;
        this.cells = cells;
    }

    public static Panel of(int x, int y, int width, int height) {
        return new Panel(x, y, width, height, null);
    }

    /** Any shape of tiles; rows like {@code "###.", "#..."} where '#' is a tile. */
    public static Panel shape(int x, int y, String... rows) {
        boolean[][] c = new boolean[rows.length][];
        int w = 0;
        for (int r = 0; r < rows.length; r++) {
            c[r] = new boolean[rows[r].length()];
            for (int i = 0; i < rows[r].length(); i++) {
                c[r][i] = rows[r].charAt(i) == '#';
            }
            w = Math.max(w, rows[r].length());
        }
        return new Panel(x, y, w, rows.length, c);
    }

    public Panel frame(String frame) {
        this.frame = frame;
        return this;
    }

    public Panel tint(@Nullable TextColor tint) {
        this.tint = v -> tint;
        return this;
    }

    public Panel tint(Function<View, @Nullable TextColor> tint) {
        this.tint = tint;
        return this;
    }

    public Panel tooltip(Component tooltip) {
        this.tooltip = v -> tooltip;
        return this;
    }

    public Panel tooltip(Function<View, @Nullable Component> tooltip) {
        this.tooltip = tooltip;
        return this;
    }

    /** Makes the whole frame clickable (elements drawn later on top of it win). */
    public Panel onClick(Consumer<Click> onClick) {
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

    /** Width in pixels, or in tiles for {@link #shape}. */
    @org.jetbrains.annotations.ApiStatus.Internal
    public int width() {
        return w;
    }

    /** Height in pixels, or in tiles for {@link #shape}. */
    @org.jetbrains.annotations.ApiStatus.Internal
    public int height() {
        return h;
    }

    /** Tile mask for shapes, null for rectangles. */
    @org.jetbrains.annotations.ApiStatus.Internal
    public boolean @Nullable [][] cells() {
        return cells;
    }

    @org.jetbrains.annotations.ApiStatus.Internal
    public String frame() {
        return frame;
    }

    @org.jetbrains.annotations.ApiStatus.Internal
    public Function<View, @Nullable TextColor> tint() {
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

    /** Shown only while this returns true (evaluated each time the scene is drawn); hidden elements do not take clicks. */
    public Panel visible(Predicate<View> visible) {
        this.visible = visible;
        return this;
    }

    @Override
    public Predicate<View> visible() {
        return visible;
    }
}
