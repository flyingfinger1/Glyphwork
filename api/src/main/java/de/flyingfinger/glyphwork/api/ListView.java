package de.flyingfinger.glyphwork.api;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.HoverEventSource;
import net.kyori.adventure.text.format.TextColor;
import org.jspecify.annotations.Nullable;

import java.util.List;
import java.util.function.BiConsumer;
import java.util.function.Function;

/**
 * A vertical list in a sunken frame ("slot"), one clickable row per item. If there are more items
 * than rows, page buttons appear below the list (the list is then 20 px taller).
 *
 * @param <T> item type
 */
public final class ListView<T> implements Element {

    private Function<View, Boolean> visible = v -> true;

    private final String id;
    private final int x;
    private final int y;
    private final int w;
    private final int rows;
    private final Function<View, List<T>> items;
    private int rowHeight = 16;
    private Function<T, String> text = String::valueOf;
    private Function<T, TextColor> color = t -> TextColor.color(0xFFFFFF);
    private @Nullable Function<T, @Nullable Component> tooltip;
    private @Nullable BiConsumer<Click, T> onSelect;
    private @Nullable Function<View, @Nullable T> selected;
    private java.util.function.@Nullable BiPredicate<View, T> selectedIf;
    private TextColor selectedTint = TextColor.color(0xFFD25A);
    private String frame = "slot";
    private @Nullable Function<T, @Nullable String> icon;
    private @Nullable Function<T, @Nullable String> count;
    private boolean itemTooltip;
    private boolean markup;
    private int headSize = 8;
    private @Nullable Function<T, @Nullable HoverEventSource<?>> hoverItem;

    private ListView(String id, int x, int y, int w, int rows, Function<View, List<T>> items) {
        this.id = id;
        this.x = x;
        this.y = y;
        this.w = w;
        this.rows = rows;
        this.items = items;
    }

    /** @param id unique within the scene; used for the page state */
    public static <T> ListView<T> of(String id, int x, int y, int width, int rows, Function<View, List<T>> items) {
        return new ListView<>(id, x, y, width, rows, items);
    }

    /** Row height in pixels (default 16; a multiple of the tile size looks best). */
    public ListView<T> rowHeight(int rowHeight) {
        this.rowHeight = rowHeight;
        return this;
    }

    /** Reads the row texts as MiniMessage (bold, italic, underline, strikethrough, colors for parts). */
    public ListView<T> markup(boolean markup) {
        this.markup = markup;
        return this;
    }

    public boolean markup() {
        return markup;
    }

    public ListView<T> text(Function<T, String> text) {
        this.text = text;
        return this;
    }

    public ListView<T> color(Function<T, TextColor> color) {
        this.color = color;
        return this;
    }

    public ListView<T> tooltip(Function<T, @Nullable Component> tooltip) {
        this.tooltip = tooltip;
        return this;
    }

    public ListView<T> onSelect(BiConsumer<Click, T> onSelect) {
        this.onSelect = onSelect;
        return this;
    }

    /** Highlights the row of the returned item. */
    public ListView<T> selected(Function<View, @Nullable T> selected) {
        this.selected = selected;
        return this;
    }

    /** Highlights every entry this holds for (instead of {@link #selected}: one entry by equality). */
    public ListView<T> selectedIf(java.util.function.BiPredicate<View, T> selectedIf) {
        this.selectedIf = selectedIf;
        return this;
    }

    /** Whether {@code item} is shown as selected for this viewer. */
    public boolean isSelected(View view, T item) {
        if (selectedIf != null) {
            return selectedIf.test(view, item);
        }
        return selected != null && item.equals(selected.apply(view));
    }

    public ListView<T> selectedTint(TextColor tint) {
        this.selectedTint = tint;
        return this;
    }

    /** A 16 x 16 item icon at the start of each row (the text moves right). */
    public ListView<T> icon(Function<T, @Nullable String> icon) {
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
    public ListView<T> count(Function<T, @Nullable String> count) {
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
    public ListView<T> itemTooltip(boolean itemTooltip) {
        this.itemTooltip = itemTooltip;
        return this;
    }

    /**
     * Shows the item's own tooltip when hovering (name, enchantments, lore) instead of the text
     * tooltip. {@code true}: built from the icon's item id (vanilla items only). For a full item
     * pass {@code itemTooltip(v -> itemStack)}: Paper's ItemStack is a HoverEventSource.
     */
    public ListView<T> itemTooltip(Function<T, @Nullable HoverEventSource<?>> item) {
        this.hoverItem = item;
        return this;
    }

    public boolean itemTooltip() {
        return itemTooltip;
    }

    /**
     * Size of player heads ("head:Name" icons): 8 (default) is the client's own head, 16, 24 or 32
     * paints the face from the skin, centered, at any position (see {@link Head}).
     */
    public ListView<T> headSize(int size) {
        this.headSize = Head.sizeOf(size);
        return this;
    }

    public int headSize() {
        return headSize;
    }

    public @Nullable Function<T, @Nullable HoverEventSource<?>> hoverItem() {
        return hoverItem;
    }

    /** Frame of the list background (default "slot"). */
    public ListView<T> frame(String frame) {
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

    public int width() {
        return w;
    }

    public int rows() {
        return rows;
    }

    public int rowHeight() {
        return rowHeight;
    }

    public Function<View, List<T>> items() {
        return items;
    }

    public Function<T, String> text() {
        return text;
    }

    public Function<T, TextColor> color() {
        return color;
    }

    public @Nullable Function<T, @Nullable Component> tooltip() {
        return tooltip;
    }

    public @Nullable BiConsumer<Click, T> onSelect() {
        return onSelect;
    }

    public @Nullable Function<View, @Nullable T> selected() {
        return selected;
    }

    public TextColor selectedTint() {
        return selectedTint;
    }

    public String frame() {
        return frame;
    }

    /** Shown only while this returns true (evaluated each time the scene is drawn); hidden elements do not take clicks. */
    public ListView<T> visible(Function<View, Boolean> visible) {
        this.visible = visible;
        return this;
    }

    @Override
    public Function<View, Boolean> visible() {
        return visible;
    }
}
