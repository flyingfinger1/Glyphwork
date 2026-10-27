package de.flyingfinger.glyphwork.api;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextColor;
import org.bukkit.Material;
import org.jspecify.annotations.Nullable;

import java.util.function.Consumer;
import java.util.function.Function;

/**
 * A 16 x 16 item icon ("diamond", "minecraft:oak_log"). Icons use the textures the client already
 * has: flat items look like in the inventory, block items show one face. Items drawn by special
 * renderers (heads, chests, beds, banners, shields, shulker boxes) have no icon. Like text, icons
 * are drawn above all frames.
 */
public final class Icon implements Element {

    private Function<View, Boolean> visible = v -> true;

    private final int x;
    private final int y;
    private final Function<View, @Nullable String> item;
    private @Nullable Function<View, @Nullable TextColor> tint;
    private @Nullable Function<View, @Nullable Component> tooltip;
    private @Nullable Consumer<Click> onClick;

    private Icon(int x, int y, Function<View, @Nullable String> item) {
        this.x = x;
        this.y = y;
        this.item = item;
    }

    /**
     * A player's head ({@code player} = name or UUID). The client draws heads as 8 x 8 px in the
     * middle of the icon's 16 x 16 place, and only on its 9 px text grid: up to 4 px higher or lower.
     * Same as {@code Icon.of(x, y, "head:" + player)}; "head:..." works wherever an item id does.
     */
    public static Icon head(int x, int y, String player) {
        return of(x, y, "head:" + player);
    }

    public static Icon of(int x, int y, String item) {
        return new Icon(x, y, v -> item);
    }

    public static Icon of(int x, int y, Material material) {
        return of(x, y, material.getKey().getKey());
    }

    public static Icon of(int x, int y, Function<View, @Nullable String> item) {
        return new Icon(x, y, item);
    }

    /** Replaces the tint of tinted layers (leather color, potion color, ...). */
    public Icon tint(Function<View, @Nullable TextColor> tint) {
        this.tint = tint;
        return this;
    }

    public Icon tooltip(Component tooltip) {
        this.tooltip = v -> tooltip;
        return this;
    }

    public Icon tooltip(Function<View, @Nullable Component> tooltip) {
        this.tooltip = tooltip;
        return this;
    }

    public Icon onClick(Consumer<Click> onClick) {
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

    public Function<View, @Nullable String> item() {
        return item;
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

    /** Shown only while this returns true (evaluated each time the scene is drawn); hidden elements do not take clicks. */
    public Icon visible(Function<View, Boolean> visible) {
        this.visible = visible;
        return this;
    }

    @Override
    public Function<View, Boolean> visible() {
        return visible;
    }
}
