package de.flyingfinger.glyphwork.api;

import net.kyori.adventure.text.Component;
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
    private TextColor selectedTint = TextColor.color(0xFFD25A);
    private String frame = "slot";
    private @Nullable Function<T, @Nullable String> icon;

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
