package de.flyingfinger.glyphwork.api;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.HoverEventSource;
import net.kyori.adventure.text.format.TextColor;
import org.jspecify.annotations.Nullable;

import java.util.List;
import java.util.function.BiConsumer;
import java.util.function.Function;

/**
 * A grid of clickable cells ("slot" frames) with a short centered label each, like an inventory or
 * a JEI page. If there are more items than cells, page buttons appear below (20 px).
 *
 * @param <T> item type
 */
public final class Grid<T> implements Element {

    private Function<View, Boolean> visible = v -> true;

    private final String id;
    private final int x;
    private final int y;
    private final int cols;
    private final int rows;
    private final Function<View, List<T>> items;
    private int cell = 18;
    private int size = 16;
    private Function<T, String> label = t -> "";
    private Function<T, TextColor> labelColor = t -> TextColor.color(0xFFFFFF);
    private Function<T, @Nullable TextColor> tint = t -> null;
    private @Nullable Function<T, @Nullable Component> tooltip;
    private @Nullable BiConsumer<Click, T> onClick;
    private String frame = "slot";
    private @Nullable Function<View, @Nullable T> selected;
    private TextColor selectedTint = TextColor.color(0xFFE070);
    private @Nullable Function<T, @Nullable String> icon;
    private @Nullable Function<T, @Nullable String> count;
    private boolean itemTooltip;
    private @Nullable Function<T, @Nullable HoverEventSource<?>> hoverItem;

    private Grid(String id, int x, int y, int cols, int rows, Function<View, List<T>> items) {
        this.id = id;
        this.x = x;
        this.y = y;
        this.cols = cols;
        this.rows = rows;
        this.items = items;
    }

    /** @param id unique within the scene; used for the page state */
    public static <T> Grid<T> of(String id, int x, int y, int cols, int rows, Function<View, List<T>> items) {
        return new Grid<>(id, x, y, cols, rows, items);
    }

    /** Cell pitch and drawn cell size in pixels (default 18 and 16). */
    public Grid<T> cell(int pitch, int size) {
        this.cell = pitch;
        this.size = size;
        return this;
    }

    public Grid<T> label(Function<T, String> label) {
        this.label = label;
        return this;
    }

    public Grid<T> labelColor(Function<T, TextColor> color) {
        this.labelColor = color;
        return this;
    }

    public Grid<T> tint(Function<T, @Nullable TextColor> tint) {
        this.tint = tint;
        return this;
    }

    public Grid<T> tooltip(Function<T, @Nullable Component> tooltip) {
        this.tooltip = tooltip;
        return this;
    }

    public Grid<T> onClick(BiConsumer<Click, T> onClick) {
        this.onClick = onClick;
        return this;
    }

    /**
     * A 16 x 16 item icon per cell ("diamond"), centered. Use a cell size of at least 16; with
     * {@code cell(26, 24)} the slot frame stays visible around the icon. {@code frame("none")}
     * draws no frame (cells stay clickable).
     */
    public Grid<T> icon(Function<T, @Nullable String> icon) {
        this.icon = icon;
        return this;
    }

    public @Nullable Function<T, @Nullable String> icon() {
        return icon;
    }

    /**
     * Stack size in the bottom-right corner of the icon, like in the inventory (white with a
     * shadow). Any short text works ("64", "1k"); null, empty and "1" show nothing. Only drawn
     * together with an item icon or head.
     */
    public Grid<T> count(Function<T, @Nullable String> count) {
        this.count = count;
        return this;
    }

    public @Nullable Function<T, @Nullable String> count() {
        return count;
    }

    /**
     * Shows the item's own tooltip when hovering (name, enchantments, lore) instead of the text
     * tooltip. {@code true}: built from the icon's item id (vanilla items only). For a full item
     * pass {@code itemTooltip(v -> itemStack)}: Paper's ItemStack is a HoverEventSource.
     */
    public Grid<T> itemTooltip(boolean itemTooltip) {
        this.itemTooltip = itemTooltip;
        return this;
    }

    /**
     * Shows the item's own tooltip when hovering (name, enchantments, lore) instead of the text
     * tooltip. {@code true}: built from the icon's item id (vanilla items only). For a full item
     * pass {@code itemTooltip(v -> itemStack)}: Paper's ItemStack is a HoverEventSource.
     */
    public Grid<T> itemTooltip(Function<T, @Nullable HoverEventSource<?>> item) {
        this.hoverItem = item;
        return this;
    }

    public boolean itemTooltip() {
        return itemTooltip;
    }

    public @Nullable Function<T, @Nullable HoverEventSource<?>> hoverItem() {
        return hoverItem;
    }

    /** Tints the cell of the returned item with {@link #selectedTint}. */
    public Grid<T> selected(Function<View, @Nullable T> selected) {
        this.selected = selected;
        return this;
    }

    public Grid<T> selectedTint(TextColor tint) {
        this.selectedTint = tint;
        return this;
    }

    public @Nullable Function<View, @Nullable T> selected() {
        return selected;
    }

    public TextColor selectedTint() {
        return selectedTint;
    }

    public Grid<T> frame(String frame) {
        this.frame = frame;
        return this;
    }

    public String id() {
        return id;
    }

    @Override
    public int x() {
        return x;
    }

    @Override
    public int y() {
        return y;
    }

    public int cols() {
        return cols;
    }

    public int rows() {
        return rows;
    }

    public int cellPitch() {
        return cell;
    }

    public int cellSize() {
        return size;
    }

    public Function<View, List<T>> items() {
        return items;
    }

    public Function<T, String> label() {
        return label;
    }

    public Function<T, TextColor> labelColor() {
        return labelColor;
    }

    public Function<T, @Nullable TextColor> tint() {
        return tint;
    }

    public @Nullable Function<T, @Nullable Component> tooltip() {
        return tooltip;
    }

    public @Nullable BiConsumer<Click, T> onClick() {
        return onClick;
    }

    public String frame() {
        return frame;
    }

    /** Shown only while this returns true (evaluated each time the scene is drawn); hidden elements do not take clicks. */
    public Grid<T> visible(Function<View, Boolean> visible) {
        this.visible = visible;
        return this;
    }

    @Override
    public Function<View, Boolean> visible() {
        return visible;
    }
}
