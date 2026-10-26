package de.flyingfinger.glyphwork.render;

import de.flyingfinger.glyphwork.api.Button;
import de.flyingfinger.glyphwork.api.Click;
import de.flyingfinger.glyphwork.api.Element;
import de.flyingfinger.glyphwork.api.Grid;
import de.flyingfinger.glyphwork.api.Hitbox;
import de.flyingfinger.glyphwork.api.Icon;
import de.flyingfinger.glyphwork.api.Label;
import de.flyingfinger.glyphwork.api.ListView;
import de.flyingfinger.glyphwork.api.Panel;
import de.flyingfinger.glyphwork.api.Scene;
import de.flyingfinger.glyphwork.api.View;
import de.flyingfinger.glyphwork.font.Glyph;
import de.flyingfinger.glyphwork.font.IconCatalog;
import de.flyingfinger.glyphwork.font.VanillaText;
import de.flyingfinger.glyphwork.theme.Frame;
import de.flyingfinger.glyphwork.theme.Theme;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.format.TextColor;
import org.jspecify.annotations.Nullable;

import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;
import java.util.logging.Logger;

/** Turns a {@link Scene} into text lines for one viewer. */
public final class SceneRenderer {

    /** Creates the click event for an action (production: a callback; tests: a named command). */
    @FunctionalInterface
    public interface Binder {
        ClickEvent<?> bind(String name, Consumer<Click> action);
    }

    private static final TextColor WHITE = TextColor.color(0xFFFFFF);
    private static final TextColor PAGER_TEXT = Label.DARK;

    private final VanillaText text;
    private final Theme theme;
    private final Glyph hit;
    private final int maxHeight;
    private final Logger log;
    private final @Nullable IconCatalog icons;
    private final Set<String> warned = ConcurrentHashMap.newKeySet();

    public SceneRenderer(VanillaText text, Theme theme, Glyph hit, int maxHeight, Logger log) {
        this(text, theme, hit, maxHeight, log, null);
    }

    /** @param icons item icons in the pack, or null if disabled */
    public SceneRenderer(VanillaText text, Theme theme, Glyph hit, int maxHeight, Logger log, @Nullable IconCatalog icons) {
        this.icons = icons;
        this.text = text;
        this.theme = theme;
        this.hit = hit;
        this.maxHeight = maxHeight;
        this.log = log;
    }

    public LineBuilder render(Scene scene, View view, Binder binder) {
        return render(scene, view, binder, true);
    }

    /** Whether icons are drawn in the current pass (renders run on the main thread, one at a time). */
    private boolean iconsThisPass = true;

    /** @param showIcons false: no item icons; grid cells without a label show a short item name */
    public LineBuilder render(Scene scene, View view, Binder binder, boolean showIcons) {
        iconsThisPass = showIcons;
        if (scene.height() > maxHeight) {
            warnOnce(scene.id() + ":height", "Scene " + scene.id() + " is " + scene.height()
                + " px high, the pack covers " + maxHeight + " px (raise max-height in config.yml)");
        }
        LineBuilder line = new LineBuilder(text, maxHeight);
        int i = 0;
        for (Element e : scene.elements()) {
            String name = scene.id() + "#" + i++;
            try {
                switch (e) {
                    case Panel p -> panel(line, p, view, binder, name);
                    case Label l -> label(line, l, view);
                    case Button b -> button(line, b, view, binder, name);
                    case Hitbox h -> hitbox(line, h.x(), h.y(), h.width(), h.height(),
                        h.onClick() == null ? null : binder.bind(name, h.onClick()), tooltip(h.tooltip(), view));
                    case Icon ic -> {
                        icon(line, ic.item().apply(view), ic.x(), ic.y(), ic.tint() == null ? null : ic.tint().apply(view));
                        hitbox(line, ic.x(), ic.y(), IconCatalog.SIZE, IconCatalog.SIZE,
                            ic.onClick() == null ? null : binder.bind(name, ic.onClick()), tooltip(ic.tooltip(), view));
                    }
                    case ListView<?> l -> list(line, l, view, binder, name);
                    case Grid<?> g -> grid(line, g, view, binder, name);
                }
            } catch (RuntimeException ex) {
                // a failing binding hides this element instead of breaking the whole screen
                warnOnce("render:" + name + ":" + ex, "Element " + name + " failed to render: " + ex);
            }
        }
        return line;
    }

    /** Draws an item icon; unknown items (or icons disabled) draw nothing. Returns whether it drew. */
    private boolean icon(LineBuilder line, @Nullable String item, int x, int y, @Nullable TextColor tint) {
        if (item == null || item.isBlank() || !iconsThisPass) {
            return false;
        }
        if (icons == null) {
            warnOnce("icons-off", "Item icons are disabled (icons: false in config.yml); icons are not drawn");
            return false;
        }
        List<IconCatalog.Layer> layers = icons.icon(item);
        if (layers == null) {
            warnOnce("icon:" + item, "No icon for item '" + item + "' (unknown, or drawn by a special renderer"
                + " like heads, chests, beds, banners)");
            return false;
        }
        line.icon(layers, x, y, tint);
        return true;
    }

    private @Nullable Frame frame(String name) {
        Frame f = theme.find(name);
        if (f == null) {
            warnOnce("frame:" + name, "Theme " + theme.id() + " has no frame '" + name + "'");
        }
        return f;
    }

    private void warnOnce(String key, String message) {
        if (warned.add(key)) {
            log.warning(message);
        }
    }

    private static @Nullable Component tooltip(java.util.function.@Nullable Function<View, @Nullable Component> f, View view) {
        return f == null ? null : f.apply(view);
    }

    private void panel(LineBuilder line, Panel p, View view, Binder binder, String name) {
        Frame f = frame(p.frame());
        if (f == null) {
            return;
        }
        ClickEvent<?> click = p.onClick() == null ? null : binder.bind(name, p.onClick());
        Component tip = tooltip(p.tooltip(), view);
        if (p.cells() != null) {
            line.frame(f, p.x(), p.y(), p.cells(), p.tint().apply(view), click, tip);
        } else {
            line.frame(f, p.x(), p.y(), p.width(), p.height(), p.tint().apply(view), click, tip);
        }
    }

    private void label(LineBuilder line, Label l, View view) {
        String s = l.text().apply(view);
        if (s == null || s.isEmpty()) {
            return;
        }
        int x = l.x();
        if (l.width() > 0) {
            s = line.fit(s, l.width());
            int w = line.visibleWidth(s);
            x += switch (l.align()) {
                case LEFT -> 0;
                case CENTER -> (l.width() - w) / 2;
                case RIGHT -> l.width() - w;
            };
        }
        line.text(s, x, l.y(), l.color().apply(view));
    }

    private void button(LineBuilder line, Button b, View view, Binder binder, String name) {
        Frame f = frame(b.frame());
        boolean enabled = Boolean.TRUE.equals(b.enabled().apply(view));
        ClickEvent<?> click = enabled && b.onClick() != null ? binder.bind(name, b.onClick()) : null;
        if (f != null) {
            line.frame(f, b.x(), b.y(), b.width(), b.height(), enabled ? b.tint().apply(view) : Button.DISABLED,
                click, tooltip(b.tooltip(), view));
        }
        int textX = b.x();
        int textW = b.width();
        if (b.icon() != null && icon(line, b.icon().apply(view), b.x() + 3, b.y() + (b.height() - IconCatalog.SIZE) / 2, null)) {
            textX += IconCatalog.SIZE + 4;
            textW -= IconCatalog.SIZE + 4;
        }
        String s = b.text().apply(view);
        if (s != null && !s.isEmpty()) {
            s = line.fit(s, textW - 6);
            line.centered(s, textX, textW, b.y() + (b.height() - 7) / 2,
                enabled ? b.textColor().apply(view) : TextColor.color(0xA0A0A0));
        }
    }

    /** Invisible clickable area, covered with 8 x 8 hit glyphs (the last row/column overlaps). */
    private void hitbox(LineBuilder line, int x, int y, int w, int h, @Nullable ClickEvent<?> click, @Nullable Component tip) {
        if (click == null && tip == null) {
            return;
        }
        int size = hit.width();
        for (int yy = 0; yy < h; yy += size) {
            int ty = Math.min(yy, h - size);
            for (int xx = 0; xx < w; xx += size) {
                line.glyph(hit, x + Math.min(xx, w - size), y + ty, null, click, tip);
            }
        }
    }

    private <T> void list(LineBuilder line, ListView<T> l, View view, Binder binder, String name) {
        List<T> items = l.items().apply(view);
        int perPage = Math.max(1, l.rows());
        int page = pageOf(view, l.id(), items.size(), perPage);
        int height = l.rows() * l.rowHeight();
        Frame bg = frame(l.frame());
        if (bg != null) {
            line.frame(bg, l.x(), l.y(), l.width(), height, null, null, null);
        }
        T selected = l.selected() == null ? null : l.selected().apply(view);
        Frame highlight = theme.find("button");
        for (int r = 0; r < l.rows(); r++) {
            int index = page * perPage + r;
            if (index >= items.size()) {
                break;
            }
            T item = items.get(index);
            int top = l.y() + r * l.rowHeight();
            if (selected != null && selected.equals(item) && highlight != null) {
                line.frame(highlight, l.x(), top, l.width(), l.rowHeight(), l.selectedTint(), null, null);
            }
            int textX = l.x() + 4;
            if (l.icon() != null && icon(line, l.icon().apply(item), l.x() + 3, top + (l.rowHeight() - IconCatalog.SIZE) / 2, null)) {
                textX += IconCatalog.SIZE + 2;
            }
            String s = line.fit(String.valueOf(l.text().apply(item)), l.x() + l.width() - 4 - textX);
            line.text(s, textX, top + (l.rowHeight() - 7) / 2, l.color().apply(item));
            ClickEvent<?> click = l.onSelect() == null ? null
                : binder.bind(name + ":row" + index, c -> l.onSelect().accept(c, item));
            hitbox(line, l.x(), top, l.width(), l.rowHeight(), click, l.tooltip() == null ? null : l.tooltip().apply(item));
        }
        pager(line, l.id(), page, pages(items.size(), perPage), l.x(), l.y() + height + 4, l.width(), binder, name);
    }

    private <T> void grid(LineBuilder line, Grid<T> g, View view, Binder binder, String name) {
        List<T> items = g.items().apply(view);
        int perPage = Math.max(1, g.cols() * g.rows());
        int page = pageOf(view, g.id(), items.size(), perPage);
        boolean noFrame = "none".equalsIgnoreCase(g.frame());
        Frame cell = noFrame ? null : frame(g.frame());
        T selected = g.selected() == null ? null : g.selected().apply(view);
        for (int r = 0; r < g.rows(); r++) {
            for (int c = 0; c < g.cols(); c++) {
                int index = page * perPage + r * g.cols() + c;
                int cx = g.x() + c * g.cellPitch();
                int cy = g.y() + r * g.cellPitch();
                T item = index < items.size() ? items.get(index) : null;
                ClickEvent<?> click = item == null || g.onClick() == null ? null
                    : binder.bind(name + ":cell" + index, k -> g.onClick().accept(k, item));
                Component tip = item == null || g.tooltip() == null ? null : g.tooltip().apply(item);
                if (cell != null) {
                    TextColor tint = item == null ? null : item.equals(selected) ? g.selectedTint() : g.tint().apply(item);
                    line.frame(cell, cx, cy, g.cellSize(), g.cellSize(), tint, click, tip);
                } else if (noFrame && item != null) {
                    hitbox(line, cx, cy, g.cellSize(), g.cellSize(), click, tip);
                }
                boolean drewIcon = false;
                if (item != null && g.icon() != null) {
                    int off = (g.cellSize() - IconCatalog.SIZE) / 2;
                    drewIcon = icon(line, g.icon().apply(item), cx + off, cy + off, null);
                }
                if (item != null) {
                    String label = g.label().apply(item);
                    if ((label == null || label.isBlank()) && !drewIcon && g.icon() != null) {
                        label = shortName(g.icon().apply(item));
                    }
                    String s = line.fit(label == null ? "" : label, g.cellSize() - 2);
                    line.centered(s, cx, g.cellSize(), cy + (g.cellSize() - 7) / 2, g.labelColor().apply(item));
                }
            }
        }
        pager(line, g.id(), page, pages(items.size(), perPage), g.x(), g.y() + g.rows() * g.cellPitch() + 2,
            g.cols() * g.cellPitch() - (g.cellPitch() - g.cellSize()), binder, name);
    }

    /** "diamond" -> "Dia", "gold_ingot" -> "GI": stands in for an icon that is not drawn. */
    static String shortName(@Nullable String item) {
        if (item == null || item.isBlank()) {
            return "";
        }
        String id = item.contains(":") ? item.substring(item.indexOf(':') + 1) : item;
        String[] parts = id.split("_");
        if (parts.length == 1) {
            String w = parts[0];
            return Character.toUpperCase(w.charAt(0)) + w.substring(1, Math.min(3, w.length()));
        }
        StringBuilder sb = new StringBuilder();
        for (String part : parts) {
            if (!part.isEmpty() && sb.length() < 3) {
                sb.append(Character.toUpperCase(part.charAt(0)));
            }
        }
        return sb.toString();
    }

    private static int pages(int count, int perPage) {
        return Math.max(1, (count + perPage - 1) / perPage);
    }

    private static int pageOf(View view, String id, int count, int perPage) {
        return Math.max(0, Math.min(view.page(id), pages(count, perPage) - 1));
    }

    /** "<  2/5  >" below a list or grid; only drawn when there is more than one page. */
    private void pager(LineBuilder line, String id, int page, int pages, int x, int y, int w, Binder binder, String name) {
        if (pages <= 1) {
            return;
        }
        Frame button = frame("button");
        String key = "glyphwork:page:" + id;
        boolean prev = page > 0;
        boolean next = page < pages - 1;
        if (button != null) {
            line.frame(button, x, y, 16, 16, prev ? Button.GRAY : Button.DISABLED,
                prev ? binder.bind(name + ":prev", c -> { c.set(key, page - 1); c.reopen(); }) : null, null);
            line.frame(button, x + w - 16, y, 16, 16, next ? Button.GRAY : Button.DISABLED,
                next ? binder.bind(name + ":next", c -> { c.set(key, page + 1); c.reopen(); }) : null, null);
        }
        line.centered("<", x, 16, y + 4, WHITE);
        line.centered(">", x + w - 16, 16, y + 4, WHITE);
        line.centered((page + 1) + "/" + pages, x, w, y + 4, PAGER_TEXT);
    }
}
