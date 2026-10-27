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
import de.flyingfinger.glyphwork.font.ShiftFonts;
import de.flyingfinger.glyphwork.font.VanillaText;
import de.flyingfinger.glyphwork.theme.Frame;
import de.flyingfinger.glyphwork.theme.Theme;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.object.ObjectContents;
import net.kyori.adventure.text.object.PlayerHeadObjectContents;
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
        return render(scene, view, binder, true, true);
    }

    public LineBuilder render(Scene scene, View view, Binder binder, boolean showIcons) {
        return render(scene, view, binder, showIcons, true);
    }

    /** Whether the viewer has the server's pack, so icons of its own textures can be drawn. */
    private boolean packIconsThisPass = true;

    /** Whether icons are drawn in the current pass (renders run on the main thread, one at a time). */
    private boolean iconsThisPass = true;

/**
     * @param showIcons     false: no item icons; grid cells without a label show a short item name
     * @param packTextures  false: the viewer lacks the server's pack, so icons that need its
     *                      textures are left out (the client would draw boxes for them)
     */
    public LineBuilder render(Scene scene, View view, Binder binder, boolean showIcons, boolean packTextures) {
        iconsThisPass = showIcons;
        packIconsThisPass = packTextures;
        if (scene.height() > maxHeight) {
            warnOnce(scene.id() + ":height", "Scene " + scene.id() + " is " + scene.height()
                + " px high, the pack covers " + maxHeight + " px (raise max-height in config.yml)");
        }
        LineBuilder line = new LineBuilder(text, maxHeight);
        int i = 0;
        for (Element e : scene.elements()) {
            String name = scene.id() + "#" + i++;
            try {
                if (!Boolean.TRUE.equals(e.visible().apply(view))) {
                    continue;                      // the name index still counts it, so click names stay stable
                }
                switch (e) {
                    case Panel p -> panel(line, p, view, binder, name);
                    case Label l -> label(line, l, view);
                    case Button b -> button(line, b, view, binder, name);
                    case Hitbox h -> hitbox(line, h.x(), h.y(), h.width(), h.height(),
                        h.onClick() == null ? null : binder.bind(name, h.onClick()), tooltip(h.tooltip(), view));
                    case Icon ic -> {
                        String item = ic.item().apply(view);
                        int dy = headOf(item) != null
                            ? headShift("Icon", name, ic.y(), ic.y() + (IconCatalog.SIZE - LineBuilder.HEAD) / 2) : 0;
                        icon(line, item, ic.x(), ic.y() + dy, ic.tint() == null ? null : ic.tint().apply(view));
                        hitbox(line, ic.x(), ic.y() + dy, IconCatalog.SIZE, IconCatalog.SIZE,
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

    /** Label.DARK stands for "the skin's text color" (dark gray in vanilla, light on dark skins). */
    private TextColor skinText(TextColor color) {
        return Label.DARK.equals(color) ? TextColor.color(theme.text()) : color;
    }

    /**
     * "head:Name" or "head:&lt;uuid&gt;": the player's head as a component, or null if {@code item} is
     * no head. Online players send their skin along (no lookup by the client).
     */
    static @Nullable Component headOf(@Nullable String item) {
        if (item == null || !item.regionMatches(true, 0, "head:", 0, 5)) {
            return null;
        }
        String who = item.substring(5).trim();
        if (who.isEmpty()) {
            return null;
        }
        try {
            java.util.UUID id = java.util.UUID.fromString(who);
            org.bukkit.entity.Player online = org.bukkit.Bukkit.getServer() == null ? null : org.bukkit.Bukkit.getPlayer(id);
            return online instanceof PlayerHeadObjectContents.SkinSource src
                ? Component.object(ObjectContents.playerHead(src)) : Component.object(ObjectContents.playerHead(id));
        } catch (IllegalArgumentException notUuid) {
            if (!PlayerHeadObjectContents.isValidName(who)) {
                return null;
            }
            org.bukkit.entity.Player online = org.bukkit.Bukkit.getServer() == null ? null : org.bukkit.Bukkit.getPlayerExact(who);
            return online instanceof PlayerHeadObjectContents.SkinSource src
                ? Component.object(ObjectContents.playerHead(src)) : Component.object(ObjectContents.playerHead(who));
        }
    }

    /**
     * How far an element must move so that its player head (wanted at {@code headTop}) lands on the
     * 9 px text grid: heads cannot be shifted, so the element moves with them (at most 4 px). Logs
     * once which position avoids the move.
     */
    private int headShift(String what, String name, int y, int headTop) {
        int index = Math.max(1, Math.round((headTop + 1) / (float) ShiftFonts.LINE));
        int dy = index * ShiftFonts.LINE - 1 - headTop;
        if (dy != 0 && hinted.add(name + ":" + y + ":" + dy)) {
            log.info(what + " " + name + " at y=" + y + " moved " + Math.abs(dy) + " px " + (dy > 0 ? "down" : "up")
                + " so that its player head sits in the middle (heads only fit the 9 px text grid); y=" + (y + dy)
                + " avoids the move");
        }
        return dy;
    }

    /** Elements already reported by {@link #headShift}. */
    private final java.util.Set<String> hinted = new java.util.HashSet<>();

    static boolean isHead(@Nullable String item) {
        return item != null && item.regionMatches(true, 0, "head:", 0, 5);
    }

    /** Draws a head with its left edge at x, centered on {@code middle}; returns its top, or null. */
    private static @Nullable Integer head(LineBuilder line, @Nullable String item, int x, int middle) {
        Component head = headOf(item);
        return head == null ? null : line.head(head, x, middle - LineBuilder.HEAD / 2);
    }

    /**
     * Draws an item icon (16 x 16) or a player head ("head:Name", 8 x 8 in the middle of the icon's
     * place); unknown items (or icons disabled) draw nothing. Returns whether it drew.
     */
    private boolean icon(LineBuilder line, @Nullable String item, int x, int y, @Nullable TextColor tint) {
        if (isHead(item)) {
            // heads come from the client, not from the pack: shown in every display mode
            return head(line, item, x + (IconCatalog.SIZE - LineBuilder.HEAD) / 2, y + IconCatalog.SIZE / 2) != null;
        }
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
        if (!packIconsThisPass && layers.stream().anyMatch(l -> l.texture().fromPack())) {
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
        line.text(s, x, l.y(), skinText(l.color().apply(view)));
    }

    private void button(LineBuilder line, Button b, View view, Binder binder, String name) {
        Frame f = frame(b.frame());
        boolean enabled = Boolean.TRUE.equals(b.enabled().apply(view));
        ClickEvent<?> click = enabled && b.onClick() != null ? binder.bind(name, b.onClick()) : null;
        String bIcon = b.icon() == null ? null : b.icon().apply(view);
        Component bHead = headOf(bIcon);
        int y = b.y() + (bHead != null ? headShift("Button", name, b.y(), b.y() + (b.height() - LineBuilder.HEAD) / 2) : 0);
        if (f != null) {
            line.frame(f, b.x(), y, b.width(), b.height(), enabled ? b.tint().apply(view) : Button.DISABLED,
                click, tooltip(b.tooltip(), view));
        }
        int textX = b.x();
        int textW = b.width();
        int textY = y + (b.height() - 7) / 2;
        if (bHead != null) {
            line.head(bHead, b.x() + 4, y + (b.height() - LineBuilder.HEAD) / 2);
            textX += LineBuilder.HEAD + 4;
            textW -= LineBuilder.HEAD + 4;
        } else if (bIcon != null && icon(line, bIcon, b.x() + 3, y + (b.height() - IconCatalog.SIZE) / 2, null)) {
            textX += IconCatalog.SIZE + 4;
            textW -= IconCatalog.SIZE + 4;
        }
        String s = b.text().apply(view);
        if (s != null && !s.isEmpty()) {
            s = line.fit(s, textW - 6);
            line.centered(s, textX, textW, textY,
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
        boolean heads = false;
        for (int r = 0; r < l.rows() && page * perPage + r < items.size() && l.icon() != null; r++) {
            heads |= headOf(l.icon().apply(items.get(page * perPage + r))) != null;
        }
        if (heads && l.rowHeight() % ShiftFonts.LINE != 0) {
            warnOnce("headrows:" + name, "List " + name + " shows player heads with row height " + l.rowHeight()
                + "; heads sit on the 9 px text grid, so rows only look even with a row height of 9 or 18");
        }
        int ly = l.y() + (heads ? headShift("List", name, l.y(), l.y() + (l.rowHeight() - LineBuilder.HEAD) / 2) : 0);
        Frame bg = frame(l.frame());
        if (bg != null) {
            line.frame(bg, l.x(), ly, l.width(), height, null, null, null);
        }
        T selected = l.selected() == null ? null : l.selected().apply(view);
        Frame highlight = theme.find("button");
        for (int r = 0; r < l.rows(); r++) {
            int index = page * perPage + r;
            if (index >= items.size()) {
                break;
            }
            T item = items.get(index);
            int top = ly + r * l.rowHeight();
            if (selected != null && selected.equals(item) && highlight != null) {
                line.frame(highlight, l.x(), top, l.width(), l.rowHeight(), l.selectedTint(), null, null);
            }
            int textX = l.x() + 4;
            int textY = top + (l.rowHeight() - 7) / 2;
            String rowIcon = l.icon() == null ? null : l.icon().apply(item);
            Component rowHead = headOf(rowIcon);
            if (rowHead != null) {
                line.head(rowHead, l.x() + 4, top + (l.rowHeight() - LineBuilder.HEAD) / 2);
                textX += LineBuilder.HEAD + 3;
            } else if (rowIcon != null && icon(line, rowIcon, l.x() + 3, top + (l.rowHeight() - IconCatalog.SIZE) / 2, null)) {
                textX += IconCatalog.SIZE + 2;
            }
            String s = line.fit(String.valueOf(l.text().apply(item)), l.x() + l.width() - 4 - textX);
            line.text(s, textX, textY, l.color().apply(item));
            ClickEvent<?> click = l.onSelect() == null ? null
                : binder.bind(name + ":row" + index, c -> l.onSelect().accept(c, item));
            hitbox(line, l.x(), top, l.width(), l.rowHeight(), click, l.tooltip() == null ? null : l.tooltip().apply(item));
        }
        pager(line, l.id(), page, pages(items.size(), perPage), l.x(), ly + height + 4, l.width(), binder, name);
    }

    private <T> void grid(LineBuilder line, Grid<T> g, View view, Binder binder, String name) {
        List<T> items = g.items().apply(view);
        int perPage = Math.max(1, g.cols() * g.rows());
        int page = pageOf(view, g.id(), items.size(), perPage);
        boolean noFrame = "none".equalsIgnoreCase(g.frame());
        Frame cell = noFrame ? null : frame(g.frame());
        T selected = g.selected() == null ? null : g.selected().apply(view);
        boolean heads = false;
        for (int i = page * perPage; i < Math.min(items.size(), (page + 1) * perPage) && g.icon() != null; i++) {
            heads |= headOf(g.icon().apply(items.get(i))) != null;
        }
        if (heads && g.rows() > 1 && g.cellPitch() % ShiftFonts.LINE != 0) {
            warnOnce("headcells:" + name, "Grid " + name + " shows player heads with a cell pitch of " + g.cellPitch()
                + "; heads sit on the 9 px text grid, so rows only look even with a pitch of 18 or 27");
        }
        int gy = g.y() + (heads ? headShift("Grid", name, g.y(), g.y() + (g.cellSize() - LineBuilder.HEAD) / 2) : 0);
        for (int r = 0; r < g.rows(); r++) {
            for (int c = 0; c < g.cols(); c++) {
                int index = page * perPage + r * g.cols() + c;
                int cx = g.x() + c * g.cellPitch();
                int cy = gy + r * g.cellPitch();
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
                    String cellIcon = g.icon().apply(item);
                    Component cellHead = headOf(cellIcon);
                    if (cellHead != null) {
                        int off = (g.cellSize() - LineBuilder.HEAD) / 2;
                        line.head(cellHead, cx + off, cy + off);
                        drewIcon = true;
                    } else {
                        int off = (g.cellSize() - IconCatalog.SIZE) / 2;
                        drewIcon = icon(line, cellIcon, cx + off, cy + off, null);
                    }
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
        pager(line, g.id(), page, pages(items.size(), perPage), g.x(), gy + g.rows() * g.cellPitch() + 2,
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
        line.centered((page + 1) + "/" + pages, x, w, y + 4, skinText(Label.DARK));
    }
}
