package de.flyingfinger.glyphwork.api;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.HoverEventSource;
import net.kyori.adventure.text.format.TextColor;
import org.bukkit.Material;
import org.jspecify.annotations.Nullable;

import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;

/**
 * A 16 x 16 item icon ("diamond", "minecraft:oak_log"). Icons use the textures the client already
 * has: flat items look like in the inventory, block items show one face. Items drawn by special
 * renderers (heads, chests, beds, banners, shields, shulker boxes) have no icon. Like text, icons
 * are drawn above all frames.
 */
public final class Icon implements Element {

    private Predicate<View> visible = v -> true;

    private final int x;
    private final int y;
    private final Function<View, @Nullable String> item;
    private @Nullable Function<View, @Nullable TextColor> tint;
    private @Nullable Function<View, @Nullable Component> tooltip;
    private @Nullable Consumer<Click> onClick;
    private @Nullable Function<View, @Nullable String> count;
    private boolean itemTooltip;
    private @Nullable Function<View, @Nullable HoverEventSource<?>> hoverItem;

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

    /**
     * Stack size in the bottom-right corner of the icon, like in the inventory (white with a
     * shadow). Any short text works ("64", "1k"); null, empty and "1" show nothing. Only drawn
     * together with an item icon or head.
     */
    public Icon count(int count) {
        String s = String.valueOf(count);
        this.count = v -> s;
        return this;
    }

    /**
     * Stack size in the bottom-right corner of the icon, like in the inventory (white with a
     * shadow). Any short text works ("64", "1k"); null, empty and "1" show nothing. Only drawn
     * together with an item icon or head.
     */
    public Icon count(Function<View, @Nullable String> count) {
        this.count = count;
        return this;
    }

    @org.jetbrains.annotations.ApiStatus.Internal
    public @Nullable Function<View, @Nullable String> count() {
        return count;
    }

    /**
     * Shows the item's own tooltip when hovering (name, enchantments, lore) instead of the text
     * tooltip. {@code true}: built from the icon's item id (vanilla items only). For a full item
     * pass {@code itemTooltip(v -> itemStack)}: Paper's ItemStack is a HoverEventSource.
     */
    @org.jetbrains.annotations.ApiStatus.Experimental
    public Icon itemTooltip(boolean itemTooltip) {
        this.itemTooltip = itemTooltip;
        return this;
    }

    /**
     * Shows the item's own tooltip when hovering (name, enchantments, lore) instead of the text
     * tooltip. {@code true}: built from the icon's item id (vanilla items only). For a full item
     * pass {@code itemTooltip(v -> itemStack)}: Paper's ItemStack is a HoverEventSource.
     */
    @org.jetbrains.annotations.ApiStatus.Experimental
    public Icon itemTooltip(Function<View, @Nullable HoverEventSource<?>> item) {
        this.hoverItem = item;
        return this;
    }

    @org.jetbrains.annotations.ApiStatus.Internal
    public boolean itemTooltip() {
        return itemTooltip;
    }

    @org.jetbrains.annotations.ApiStatus.Internal
    public @Nullable Function<View, @Nullable HoverEventSource<?>> hoverItem() {
        return hoverItem;
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

    @org.jetbrains.annotations.ApiStatus.Internal
    public Function<View, @Nullable String> item() {
        return item;
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

    /** Shown only while this returns true (evaluated each time the scene is drawn); hidden elements do not take clicks. */
    public Icon visible(Predicate<View> visible) {
        this.visible = visible;
        return this;
    }

    @Override
    public Predicate<View> visible() {
        return visible;
    }
}
