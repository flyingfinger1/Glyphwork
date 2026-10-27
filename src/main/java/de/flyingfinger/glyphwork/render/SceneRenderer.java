package de.flyingfinger.glyphwork.render;

import de.flyingfinger.glyphwork.api.Bar;
import de.flyingfinger.glyphwork.api.Button;
import de.flyingfinger.glyphwork.api.Checkbox;
import de.flyingfinger.glyphwork.api.Choice;
import de.flyingfinger.glyphwork.api.Slider;
import de.flyingfinger.glyphwork.api.Tabs;
import de.flyingfinger.glyphwork.api.Scroll;
import de.flyingfinger.glyphwork.api.Click;
import de.flyingfinger.glyphwork.api.Element;
import de.flyingfinger.glyphwork.api.Grid;
import de.flyingfinger.glyphwork.api.Head;
import de.flyingfinger.glyphwork.api.Hitbox;
import de.flyingfinger.glyphwork.api.Icon;
import de.flyingfinger.glyphwork.api.Image;
import de.flyingfinger.glyphwork.api.Label;
import de.flyingfinger.glyphwork.api.ListView;
import de.flyingfinger.glyphwork.api.Panel;
import de.flyingfinger.glyphwork.api.Scene;
import de.flyingfinger.glyphwork.api.View;
import de.flyingfinger.glyphwork.font.FillSheets;
import de.flyingfinger.glyphwork.font.Glyph;
import de.flyingfinger.glyphwork.font.IconCatalog;
import de.flyingfinger.glyphwork.font.ShiftFonts;
import de.flyingfinger.glyphwork.font.VanillaText;
import de.flyingfinger.glyphwork.theme.Frame;
import de.flyingfinger.glyphwork.theme.Theme;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.object.ObjectContents;
import net.kyori.adventure.text.object.PlayerHeadObjectContents;
import net.kyori.adventure.key.Key;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.text.event.HoverEventSource;
import net.kyori.adventure.text.format.ShadowColor;
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
    private static final TextColor DISABLED_TEXT = TextColor.color(0xA0A0A0);

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

    /** Strips for pixel-exact bar fills (in the pack); without them bars fill in whole tiles. */
    private @Nullable FillSheets fills;

    public SceneRenderer fills(@Nullable FillSheets fills) {
        this.fills = fills;
        return this;
    }

    /** The server's pictures (images/); without them image elements draw nothing. */
    private de.flyingfinger.glyphwork.font.ImageCatalog images = de.flyingfinger.glyphwork.font.ImageCatalog.empty();

    public SceneRenderer images(de.flyingfinger.glyphwork.font.ImageCatalog images) {
        this.images = images;
        return this;
    }

    /** Squares and skin faces for big heads; without them big heads fall back to the client's 8 x 8 head. */
    private de.flyingfinger.glyphwork.font.@Nullable SquareSheets squares;
    private @Nullable FaceSource faces;
    /** Faces asked for in the current pass that are still loading. */
    private final java.util.Set<String> missingFaces = new java.util.HashSet<>();

    public SceneRenderer bigHeads(de.flyingfinger.glyphwork.font.SquareSheets squares, FaceSource faces) {
        this.squares = squares;
        this.faces = faces;
        return this;
    }

    public @Nullable FaceSource faces() {
        return faces;
    }

    /** Faces the last render could not draw yet (still loading); empties the set. */
    public java.util.Set<String> takeMissingFaces() {
        java.util.Set<String> s = java.util.Set.copyOf(missingFaces);
        missingFaces.clear();
        return s;
    }

    /**
     * A face of {@code size} px (8..32) with its top-left at (x, y), painted from the skin. While
     * the skin loads (or without skins), the client's 8 x 8 head stands in, centered.
     */
    private void bigHead(LineBuilder line, String who, int x, int y, int size) {
        int px = Math.max(1, Math.min(4, size / 8));
        int[] face = squares == null || faces == null ? null : faces.face(who);
        if (face == null) {
            if (faces != null) {
                missingFaces.add(who);
            }
            Component small = headOf("head:" + who);
            if (small != null) {
                line.head(small, x + (size - LineBuilder.HEAD) / 2, y + (size - LineBuilder.HEAD) / 2);
            }
            return;
        }
        Glyph g = squares.square(px);
        for (int i = 0; i < 64; i++) {
            line.glyph(g, x + (i % 8) * px, y + (i / 8) * px, TextColor.color(face[i] & 0xFFFFFF), null, null);
        }
    }

    /** "head:Name" -> "Name", otherwise null. */
    private static @Nullable String headName(@Nullable String item) {
        return isHead(item) ? item.substring(5).trim() : null;
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
        missingFaces.clear();
        iconsThisPass = showIcons;
        packIconsThisPass = packTextures;
        if (scene.height() > maxHeight) {
            warnOnce(scene.id() + ":height", "Scene " + scene.id() + " is " + scene.height()
                + " px high, the pack covers " + maxHeight + " px (raise max-height in config.yml for scenes of plugins)");
        }
        LineBuilder line = new LineBuilder(text, maxHeight);
        int i = 0;
        for (Element e : scene.elements()) {
            element(line, e, view, binder, scene.id() + "#" + i++, scene);
        }
        return line;
    }

    /** One element; hidden ones are skipped (their number still counts, so click names stay stable). */
    private void element(LineBuilder line, Element e, View view, Binder binder, String name, Scene scene) {
        try {
            if (!Boolean.TRUE.equals(e.visible().apply(view))) {
                return;
            }
            switch (e) {
                case Panel p -> panel(line, p, view, binder, name, scene);
                case Label l -> label(line, l, view);
                case Button b -> button(line, b, view, binder, name);
                case Hitbox h -> hitbox(line, h.x(), h.y(), h.width(), h.height(),
                    h.onClick() == null ? null : binder.bind(name, h.onClick()), tooltip(h.tooltip(), view));
                case Icon ic -> {
                    String item = ic.item().apply(view);
                    int dy = headOf(item) != null
                        ? headShift("Icon", name, ic.y(), ic.y() + (IconCatalog.SIZE - LineBuilder.HEAD) / 2) : 0;
                    if (icon(line, item, ic.x(), ic.y() + dy, ic.tint() == null ? null : ic.tint().apply(view))) {
                        count(line, ic.count() == null ? null : ic.count().apply(view), ic.x(), ic.y() + dy);
                    }
                    HoverEventSource<?> itemTip = ic.hoverItem() != null ? safeItem(ic.hoverItem().apply(view))
                        : ic.itemTooltip() ? itemHover(item) : null;
                    hitbox(line, ic.x(), ic.y() + dy, IconCatalog.SIZE, IconCatalog.SIZE,
                        ic.onClick() == null ? null : binder.bind(name, ic.onClick()),
                        itemTip != null ? itemTip : tooltip(ic.tooltip(), view));
                }
                case ListView<?> l -> list(line, l, view, binder, name);
                case Grid<?> g -> grid(line, g, view, binder, name);
                case Slider s -> slider(line, s, view, binder, name);
                case Checkbox cb -> checkbox(line, cb, view, binder, name);
                case Choice ch -> choice(line, ch, view, binder, name);
                case Bar b -> bar(line, b, view);
                case Image im -> image(line, im, view, binder, name);
                case Tabs t -> tabs(line, t, view, binder, name, scene);
                case Scroll sc -> scroll(line, sc, view, binder, name, scene);
                case Head hd -> {
                    String who = hd.player().apply(view);
                    if (who != null && !who.isBlank()) {
                        bigHead(line, who.trim(), hd.x(), hd.y(), hd.size());
                        hitbox(line, hd.x(), hd.y(), hd.size(), hd.size(),
                            hd.onClick() == null ? null : binder.bind(name, hd.onClick()), tooltip(hd.tooltip(), view));
                    }
                }
            }
        } catch (RuntimeException ex) {
            // a failing binding hides this element instead of breaking the whole screen
            warnOnce("render:" + name + ":" + ex, "Element " + name + " failed to render: " + ex);
        }
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

    /** Inventory count color and its shadow (a quarter of the brightness, like vanilla). */
    private static final ShadowColor COUNT_SHADOW = LineBuilder.shadowOf(WHITE);

    /**
     * The stack size at the bottom right of a 16 x 16 icon at (x, y), placed like in the inventory:
     * right edge at x + 17 (with shadow), top at y + 9. "1" and empty show nothing.
     */
    private void count(LineBuilder line, @Nullable String count, int x, int y) {
        count(line, count, x, y, Integer.MAX_VALUE);
    }

    /** Same, but the count (7 px + 1 px shadow) ends above {@code bottom}, e.g. the end of a list row. */
    private void count(LineBuilder line, @Nullable String count, int x, int y, int bottom) {
        if (count == null) {
            return;
        }
        String s = count.trim();
        if (s.isEmpty() || s.equals("1")) {
            return;
        }
        line.text(s, x + 17 - line.textWidth(s), Math.min(y + 9, bottom - 8), WHITE, COUNT_SHADOW);
    }

    /**
     * Words of {@code text} in lines no wider than {@code width} px; "\n" starts a new line and a
     * word longer than a line is cut into pieces.
     */
    static List<String> wrapText(LineBuilder line, String text, int width) {
        List<String> out = new java.util.ArrayList<>();
        for (String para : text.split("\n", -1)) {
            StringBuilder cur = new StringBuilder();
            for (String word : para.split(" ")) {
                String next = cur.isEmpty() ? word : cur + " " + word;
                if (line.visibleWidth(next) <= width) {
                    cur.setLength(0);
                    cur.append(next);
                    continue;
                }
                if (!cur.isEmpty()) {
                    out.add(cur.toString());
                    cur.setLength(0);
                }
                // a word that does not fit on a line of its own is cut
                String w = word;
                while (line.visibleWidth(w) > width && w.length() > 1) {
                    int n = w.length() - 1;
                    while (n > 1 && line.visibleWidth(w.substring(0, n)) > width) {
                        n--;
                    }
                    out.add(w.substring(0, n));
                    w = w.substring(n);
                }
                cur.append(w);
            }
            out.add(cur.toString());
        }
        return out;
    }

    /** The vanilla tooltip of an item id ("diamond"), or null for heads, unknown and non-vanilla items. */
    private @Nullable HoverEventSource<?> itemHover(@Nullable String item) {
        if (item == null || isHead(item)) {
            return null;
        }
        String id = item.trim().toLowerCase();
        if (id.startsWith("minecraft:")) {
            id = id.substring("minecraft:".length());
        }
        // the client rejects the whole dialog for an item it does not know, so only catalog items
        if (id.contains(":") || icons == null || icons.icon(id) == null) {
            return null;
        }
        return HoverEvent.showItem(Key.key("minecraft", id), 1);
    }

    /** An item hover from the API; item ids that are not vanilla items are dropped (see itemHover). */
    private @Nullable HoverEventSource<?> safeItem(@Nullable HoverEventSource<?> src) {
        if (src == null) {
            return null;
        }
        HoverEvent<?> e = src.asHoverEvent();
        if (e.action() == HoverEvent.Action.SHOW_ITEM && e.value() instanceof HoverEvent.ShowItem si
            && si.item().namespace().equals("minecraft") && icons != null && icons.icon(si.item().value()) == null) {
            return null;
        }
        return src;
    }

    /**
     * Frame ("slot") with a fill from the left: value / max of the inner width (1 px inset),
     * pixel exact with the fill strips, otherwise in whole tiles; text in the middle.
     */
    private void bar(LineBuilder line, Bar b, View view) {
        Frame bg = frame(b.frame());
        int t = bg == null ? 8 : bg.tileSize();
        int w = Math.max(t, b.width() / t * t);
        int h = Math.max(t, b.height() / t * t);
        if (bg != null) {
            line.frame(bg, b.x(), b.y(), w, h, null, null, null);
        }
        double ratio = Bar.ratio(b.value().apply(view), b.max().apply(view));
        int inner = w - 2;
        int fill = (int) Math.round(ratio * inner);
        TextColor color = b.color().apply(view);
        de.flyingfinger.glyphwork.font.Sheet strips = fills == null ? null : fills.forHeight(h - 2);
        if (fill > 0 && strips != null) {
            int x = b.x() + 1;
            int rest = fill;
            for (int i = 0; i < FillSheets.WIDTHS.length; i++) {
                while (rest >= FillSheets.WIDTHS[i]) {
                    line.glyph(strips.glyph(i), x, b.y() + 1, color, null, null);
                    x += FillSheets.WIDTHS[i];
                    rest -= FillSheets.WIDTHS[i];
                }
            }
        } else if (fill > 0) {
            Frame f = frame("button");
            int tiles = Math.max(1, (int) Math.round(ratio * w / t));
            if (f != null) {
                line.frame(f, b.x(), b.y(), tiles * t, h, color, null, null);
            }
        }
        String s = b.text().apply(view);
        if (s != null && !s.isEmpty()) {
            s = line.fit(s, w - 4);
            // a bar 8 px high has no room for text: it goes above, like the experience level in vanilla
            int ty = h <= 8 ? b.y() - 9 : b.y() + (h - 7) / 2;
            line.centered(s, b.x(), w, ty, WHITE, LineBuilder.shadowOf(WHITE));
        }
        hitbox(line, b.x(), b.y(), w, h, null, tooltip(b.tooltip(), view));
    }

    private void image(LineBuilder line, Image im, View view, Binder binder, String name) {
        String id = im.image().apply(view);
        if (id == null || id.isBlank()) {
            return;
        }
        de.flyingfinger.glyphwork.font.ImageCatalog.Picture p = images.get(id);
        if (p == null) {
            warnOnce("image:" + id, "No picture '" + id + "' in plugins/Glyphwork/images/ (known: "
                + (images.size() == 0 ? "none" : String.join(", ", images.ids())) + ")");
            return;
        }
        line.image(p, im.x(), im.y(), im.tint() == null ? null : im.tint().apply(view));
        hitbox(line, im.x(), im.y(), Math.max(1, p.width()), Math.max(1, p.height()),
            im.onClick() == null ? null : binder.bind(name, im.onClick()), tooltip(im.tooltip(), view));
    }

    /**
     * Tab buttons side by side (the active one in its own color), the panel below, then the
     * elements of the active tab. A click on a tab selects it and draws the scene again.
     */
    private void tabs(LineBuilder line, Tabs t, View view, Binder binder, String name, Scene scene) {
        List<Tabs.Tab> all = t.tabs();
        if (all.isEmpty()) {
            return;
        }
        Tabs.Tab active = t.active(view);
        Frame frame = frame(t.frame());
        int n = all.size();
        int ts = frame == null ? 8 : frame.tileSize();
        int cols = Math.max(n, t.width() / ts);
        int head = Math.max(1, Tabs.HEADER / ts);
        int body = Math.max(1, (t.height() - Tabs.HEADER) / ts);
        // tabs cover whole tiles, so the active one joins the panel without a seam
        int[] col = new int[n + 1];
        for (int i = 0; i <= n; i++) {
            col[i] = i * cols / n;
        }
        if (frame != null) {
            // the other tabs first, 2 px lower: the panel drawn after them covers their lower edge
            for (int i = 0; i < n; i++) {
                Tabs.Tab tab = all.get(i);
                if (tab == active) {
                    continue;
                }
                ClickEvent<?> click = binder.bind(name + ":tab:" + tab.id(), Redraw.after(c -> t.select(c, tab.id())));
                line.frame(frame, t.x() + col[i] * ts, t.y() + 2, LineBuilder.cells(col[i + 1] - col[i], head), t.tint(),
                    click, null);
            }
            boolean[][] cells = new boolean[head + body][cols];
            for (int r = 0; r < head + body; r++) {
                for (int c = 0; c < cols; c++) {
                    cells[r][c] = r >= head || (active != null && c >= col[all.indexOf(active)] && c < col[all.indexOf(active) + 1]);
                }
            }
            line.frame(frame, t.x(), t.y(), cells, null, null, null);
        }
        TextColor text = TextColor.color(theme.text());
        for (int i = 0; i < n; i++) {
            Tabs.Tab tab = all.get(i);
            boolean on = tab == active;
            int x0 = t.x() + col[i] * ts;
            int tw = (col[i + 1] - col[i]) * ts;
            int ty = t.y() + (on ? 0 : 2);
            int textX = x0;
            if (tab.icon() != null && icon(line, tab.icon(), x0 + 3, ty, null)) {
                textX += IconCatalog.SIZE + 3;
                tw -= IconCatalog.SIZE + 3;
            }
            line.centered(line.fit(tab.title(), tw - 6), textX, tw, ty + 5, text, null);
        }
        if (active != null) {
            for (int i = 0; i < active.elements().size(); i++) {
                element(line, active.elements().get(i), view, binder, name + "/" + active.id() + "#" + i, scene);
            }
        }
    }

    /**
     * A window onto its elements: first measured (how far down they reach), then drawn moved up by
     * the position, only what fits completely. The scroll bar comes last, outside the window.
     */
    private void scroll(LineBuilder line, Scroll s, View view, Binder binder, String name, Scene scene) {
        List<Element> inner = s.elements();
        line.window(s.y(), s.y(), 0);
        for (int i = 0; i < inner.size(); i++) {
            element(line, inner.get(i), view, (n, a) -> null, name + "/" + i, scene);
        }
        int bottom = line.endWindow();
        int content = bottom == Integer.MIN_VALUE ? 0 : bottom - s.y();
        int step = s.step();
        // the last position shows the end of the content (in whole steps, so it stays reachable)
        int max = Math.max(0, (content - s.height() + step - 1) / step * step);
        int off = Math.min(s.offset(view), max) / step * step;
        if (s.frame() != null) {
            Frame f = frame(s.frame());
            if (f != null) {
                line.frame(f, s.x(), s.y(), s.width(), s.height(), null, null, null);
            }
        }
        line.window(s.y(), s.y() + s.height(), -off);
        for (int i = 0; i < inner.size(); i++) {
            element(line, inner.get(i), view, binder, name + "/" + i, scene);
        }
        line.endWindow();
        if (max == 0) {
            return;
        }
        int bx = s.x() + s.width() - Scroll.BAR;
        String key = s.key(view);
        Frame button = frame("button");
        Frame slot = frame("slot");
        boolean up = off > 0;
        boolean down = off < max;
        int arrow = 16;
        if (button != null) {
            line.frame(button, bx, s.y(), Scroll.BAR, arrow, up ? Button.GRAY : Button.DISABLED,
                up ? binder.bind(name + ":up", Redraw.after(c -> c.set(key, Math.max(0, off - step)))) : null, null);
            line.frame(button, bx, s.y() + s.height() - arrow, Scroll.BAR, arrow, down ? Button.GRAY : Button.DISABLED,
                down ? binder.bind(name + ":down", Redraw.after(c -> c.set(key, Math.min(max, off + step)))) : null, null);
        }
        line.centered("▲", bx, Scroll.BAR, s.y() + 4, up ? WHITE : DISABLED_TEXT, up ? LineBuilder.shadowOf(WHITE) : null);
        line.centered("▼", bx, Scroll.BAR, s.y() + s.height() - arrow + 5, down ? WHITE : DISABLED_TEXT,
            down ? LineBuilder.shadowOf(WHITE) : null);
        // track between the arrows; the knob shows which part is visible, a click jumps there
        int ts = slot == null ? 8 : slot.tileSize();
        int top = s.y() + arrow;
        int track = (s.height() - 2 * arrow) / ts * ts;
        if (track < ts) {
            return;
        }
        if (slot != null) {
            line.frame(slot, bx, top, Scroll.BAR, track, null, null, null);
        }
        int knob = Math.max(ts, Math.min(track, track * s.height() / Math.max(1, content) / ts * ts));
        int ky = top + (track - knob) * off / max;
        if (button != null) {
            line.frame(button, bx, ky, Scroll.BAR, knob, null, null, null);
        }
        int segments = track / 8;
        for (int i = 0; i < segments; i++) {
            int target = segments == 1 ? 0 : Math.round((float) i / (segments - 1) * max / step) * step;
            if (target == off) {
                continue;
            }
            hitbox(line, bx, top + i * 8, Scroll.BAR, 8,
                binder.bind(name + ":to" + target, Redraw.after(c -> c.set(key, target))), null);
        }
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

    /** Rounds a size up to whole tiles. */
    private static int tilesUp(int size, int tile) {
        return Math.max(tile, (size + tile - 1) / tile * tile);
    }

    private void panel(LineBuilder line, Panel p, View view, Binder binder, String name, Scene scene) {
        Frame f = frame(p.frame());
        if (f == null) {
            return;
        }
        ClickEvent<?> click = p.onClick() == null ? null : binder.bind(name, p.onClick());
        Component tip = tooltip(p.tooltip(), view);
        if (p.cells() != null) {
            line.frame(f, p.x(), p.y(), p.cells(), p.tint().apply(view), click, tip);
        } else if (p.x() == 0 && p.y() == 0 && p.width() == scene.width() && p.height() == scene.height()) {
            // the background covers the whole scene, also when its size is no multiple of the tile
            int t = f.tileSize();
            line.frame(f, 0, 0, tilesUp(p.width(), t), tilesUp(p.height(), t), p.tint().apply(view), click, tip);
        } else {
            line.frame(f, p.x(), p.y(), p.width(), p.height(), p.tint().apply(view), click, tip);
        }
    }

    private void label(LineBuilder line, Label l, View view) {
        String s = l.text().apply(view);
        if (s == null || s.isEmpty()) {
            return;
        }
        TextColor color = skinText(l.color().apply(view));
        RichText.Look base = new RichText.Look(color, l.bold(), l.italic(), l.underline(), l.strikethrough());
        List<RichText.Span> spans = l.markup() ? RichText.parse(s, base) : RichText.plain(s, base);
        List<List<RichText.Span>> lines;
        if (l.wrap() && l.width() > 0) {
            lines = RichText.wrap(text, spans, l.width());
        } else {
            lines = RichText.splitLines(spans);
            if (l.width() > 0) {
                lines.replaceAll(part -> RichText.fit(text, part, l.width()));
            }
        }
        for (int i = 0; i < lines.size(); i++) {
            List<RichText.Span> part = lines.get(i);
            int w = RichText.visibleWidth(text, part);
            int lx = l.x() + (l.width() <= 0 ? 0 : switch (l.align()) {
                case LEFT -> 0;
                case CENTER -> (l.width() - w) / 2;
                case RIGHT -> l.width() - w;
            });
            rich(line, part, lx, l.y() + i * l.lineHeight(), color, l.shadow());
        }
    }

    /**
     * Formatted pieces from x on: bold and italic by the client, underline and strikethrough as
     * 1 px lines of our own (the client's lines would sit where the text line is, not where our
     * shifted glyphs are). Returns the advance.
     */
    private int rich(LineBuilder line, List<RichText.Span> spans, int x, int y, TextColor base, boolean shadow) {
        int cx = x;
        for (RichText.Span s : spans) {
            RichText.Look k = s.look();
            TextColor c = k.color() == null ? base : skinText(k.color());
            line.text(s.text(), cx, y, c, shadow ? LineBuilder.shadowOf(c) : null, k.bold(), k.italic());
            int w = RichText.width(text, s);
            // like the client: from 1 px before the piece to the end of its advance
            if (k.underline()) {
                rule(line, cx - 1, y + 8, w + 1, c, shadow);
            }
            if (k.strike()) {
                rule(line, cx - 1, y + 4, w + 1, c, shadow);
            }
            cx += w;
        }
        return cx - x;
    }

    /** A 1 px line from the fill strips (and its shadow 1 px right and down). */
    private void rule(LineBuilder line, int x, int y, int w, TextColor color, boolean shadow) {
        de.flyingfinger.glyphwork.font.Sheet strips = fills == null ? null : fills.forHeight(1);
        if (strips == null || w <= 0) {
            return;
        }
        if (shadow) {
            strip(line, strips, x + 1, y + 1, w, TextColor.color((color.value() & 0xFCFCFC) >> 2));
        }
        strip(line, strips, x, y, w, color);
    }

    private static void strip(LineBuilder line, de.flyingfinger.glyphwork.font.Sheet strips, int x, int y, int w, TextColor color) {
        int rest = w;
        for (int i = 0; i < FillSheets.WIDTHS.length; i++) {
            while (rest >= FillSheets.WIDTHS[i]) {
                line.glyph(strips.glyph(i), x, y, color, null, null);
                x += FillSheets.WIDTHS[i];
                rest -= FillSheets.WIDTHS[i];
            }
        }
    }

    private void button(LineBuilder line, Button b, View view, Binder binder, String name) {
        Frame f = frame(b.frame());
        boolean enabled = Boolean.TRUE.equals(b.enabled().apply(view));
        ClickEvent<?> click = enabled && b.onClick() != null ? binder.bind(name, b.onClick()) : null;
        String bIcon = b.icon() == null ? null : b.icon().apply(view);
        Component bHead = headOf(bIcon);
        // frames are whole tiles: a 20 px button is drawn 16 px high, so center on what is drawn
        int h = f == null ? b.height() : Math.max(f.tileSize(), b.height() / f.tileSize() * f.tileSize());
        int y = b.y() + (bHead != null ? headShift("Button", name, b.y(), b.y() + (h - LineBuilder.HEAD) / 2) : 0);
        if (f != null) {
            line.frame(f, b.x(), y, b.width(), b.height(), enabled ? b.tint().apply(view) : Button.DISABLED,
                click, tooltip(b.tooltip(), view));
        }
        int textX = b.x();
        // like the height: a 22 px button is drawn 16 px wide, so center on what is drawn
        int textW = f == null ? b.width() : Math.max(f.tileSize(), b.width() / f.tileSize() * f.tileSize());
        int textY = y + (h - 7) / 2;
        if (bHead != null) {
            line.head(bHead, b.x() + 4, y + (h - LineBuilder.HEAD) / 2);
            textX += LineBuilder.HEAD + 4;
            textW -= LineBuilder.HEAD + 4;
        } else if (bIcon != null && icon(line, bIcon, b.x() + 3, y + (h - IconCatalog.SIZE) / 2, null)) {
            textX += IconCatalog.SIZE + 4;
            textW -= IconCatalog.SIZE + 4;
        }
        String s = b.text().apply(view);
        if (s != null && !s.isEmpty()) {
            TextColor color = enabled ? b.textColor().apply(view) : TextColor.color(0xA0A0A0);
            if (b.markup() && enabled) {
                // formatted: measured and centered as pieces
                List<RichText.Span> spans = RichText.fit(text, RichText.parse(s, new RichText.Look(color, false, false, false, false)), textW - 6);
                rich(line, spans, textX + (textW - RichText.visibleWidth(text, spans)) / 2, textY, color, true);
                s = "";
            } else if (b.markup()) {
                s = RichText.plainText(RichText.parse(s, RichText.Look.PLAIN));
            }
            s = line.fit(s, textW - 6);
            // like vanilla buttons: shadow while enabled, flat when grayed out
            line.centered(s, textX, textW, textY, color, enabled ? LineBuilder.shadowOf(color) : null);
        }
    }

    /** Invisible clickable area, covered with 8 x 8 hit glyphs (the last row/column overlaps). */
    private void hitbox(LineBuilder line, int x, int y, int w, int h, @Nullable ClickEvent<?> click, @Nullable HoverEventSource<?> tip) {
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
        boolean itemIcons = false;
        for (int r = 0; r < l.rows() && page * perPage + r < items.size() && l.icon() != null; r++) {
            String ic = l.icon().apply(items.get(page * perPage + r));
            if (headOf(ic) != null && l.headSize() <= LineBuilder.HEAD) {
                heads = true;
            } else if (ic != null && !ic.isBlank()) {
                itemIcons = true;
            }
        }
        // heads and item icons in one list share a 16 px column, so all texts start at the same x
        boolean wideColumn = heads && itemIcons;
        if (heads && l.rowHeight() % ShiftFonts.LINE != 0) {
            warnOnce("headrows:" + name, "List " + name + " shows player heads with row height " + l.rowHeight()
                + "; heads sit on the 9 px text grid, so rows only look even with a row height of 9 or 18");
        }
        int ly = l.y() + (heads ? headShift("List", name, l.y(), l.y() + (l.rowHeight() - LineBuilder.HEAD) / 2) : 0);
        Frame bg = frame(l.frame());
        int frameHeight = height;
        if (bg != null) {
            // rounded up to whole tiles, so that every row lies inside the frame (2 rows of 18 px: 40 px)
            frameHeight = tilesUp(height, bg.tileSize());
            line.frame(bg, l.x(), ly, l.width(), frameHeight, null, null, null);
        }
        Frame highlight = theme.find("button");
        for (int r = 0; r < l.rows(); r++) {
            int index = page * perPage + r;
            if (index >= items.size()) {
                break;
            }
            T item = items.get(index);
            int top = ly + r * l.rowHeight();
            if (highlight != null && l.isSelected(view, item)) {
                line.frame(highlight, l.x(), top, l.width(), l.rowHeight(), l.selectedTint(), null, null);
            }
            int textX = l.x() + 4;
            int textY = top + (l.rowHeight() - 7) / 2;
            String rowIcon = l.icon() == null ? null : l.icon().apply(item);
            Component rowHead = headOf(rowIcon);
            if (rowHead != null && l.headSize() > LineBuilder.HEAD) {
                int hs = Math.min(l.headSize(), l.rowHeight());
                bigHead(line, headName(rowIcon), l.x() + 3, top + (l.rowHeight() - hs) / 2, hs);
                textX += hs + 3;
            } else if (rowHead != null) {
                int hx = wideColumn ? l.x() + 3 + (IconCatalog.SIZE - LineBuilder.HEAD) / 2 : l.x() + 4;
                line.head(rowHead, hx, top + (l.rowHeight() - LineBuilder.HEAD) / 2);
                textX += wideColumn ? IconCatalog.SIZE + 2 : LineBuilder.HEAD + 3;
            } else if (rowIcon != null && icon(line, rowIcon, l.x() + 3, top + (l.rowHeight() - IconCatalog.SIZE) / 2, null)) {
                textX += IconCatalog.SIZE + 2;
                if (l.count() != null) {
                    // rows of 16 px leave no room below the icon: keep the count inside the row
                    count(line, l.count().apply(item), l.x() + 3, top + (l.rowHeight() - IconCatalog.SIZE) / 2,
                        top + l.rowHeight());
                }
            }
            TextColor color = l.color().apply(item);
            String rowText = String.valueOf(l.text().apply(item));
            if (l.markup()) {
                rich(line, RichText.fit(text, RichText.parse(rowText, new RichText.Look(color, false, false, false, false)),
                    l.x() + l.width() - 4 - textX), textX, textY, color, true);
            } else {
                line.text(line.fit(rowText, l.x() + l.width() - 4 - textX), textX, textY, color, LineBuilder.shadowOf(color));
            }
            ClickEvent<?> click = l.onSelect() == null ? null
                : binder.bind(name + ":row" + index, c -> l.onSelect().accept(c, item));
            HoverEventSource<?> rowItem = l.hoverItem() != null ? safeItem(l.hoverItem().apply(item))
                : l.itemTooltip() ? itemHover(rowIcon) : null;
            hitbox(line, l.x(), top, l.width(), l.rowHeight(), click,
                rowItem != null ? rowItem : l.tooltip() == null ? null : l.tooltip().apply(item));
        }
        pager(line, l.id(), page, pages(items.size(), perPage), l.x(), ly + frameHeight + 4, l.width(), binder, name);
    }

    private <T> void grid(LineBuilder line, Grid<T> g, View view, Binder binder, String name) {
        List<T> items = g.items().apply(view);
        int perPage = Math.max(1, g.cols() * g.rows());
        int page = pageOf(view, g.id(), items.size(), perPage);
        boolean noFrame = "none".equalsIgnoreCase(g.frame());
        Frame cell = noFrame ? null : frame(g.frame());
        boolean heads = false;
        for (int i = page * perPage; i < Math.min(items.size(), (page + 1) * perPage) && g.icon() != null; i++) {
            heads |= headOf(g.icon().apply(items.get(i))) != null;
        }
        heads &= g.headSize() <= LineBuilder.HEAD;     // painted heads sit anywhere
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
                HoverEventSource<?> cellItem = item == null ? null : g.hoverItem() != null ? safeItem(g.hoverItem().apply(item))
                    : g.itemTooltip() && g.icon() != null ? itemHover(g.icon().apply(item)) : null;
                HoverEventSource<?> tip = cellItem != null ? cellItem
                    : item == null || g.tooltip() == null ? null : g.tooltip().apply(item);
                if (cell != null) {
                    TextColor tint = item == null ? null : g.isSelected(view, item) ? g.selectedTint() : g.tint().apply(item);
                    line.frame(cell, cx, cy, g.cellSize(), g.cellSize(), tint, click, tip);
                } else if (noFrame && item != null) {
                    hitbox(line, cx, cy, g.cellSize(), g.cellSize(), click, tip);
                }
                boolean drewIcon = false;
                if (item != null && g.icon() != null) {
                    String cellIcon = g.icon().apply(item);
                    Component cellHead = headOf(cellIcon);
                    if (cellHead != null && g.headSize() > LineBuilder.HEAD) {
                        int hs = Math.min(g.headSize(), g.cellSize());
                        bigHead(line, headName(cellIcon), cx + (g.cellSize() - hs) / 2, cy + (g.cellSize() - hs) / 2, hs);
                        drewIcon = true;
                    } else if (cellHead != null) {
                        int off = (g.cellSize() - LineBuilder.HEAD) / 2;
                        line.head(cellHead, cx + off, cy + off);
                        drewIcon = true;
                    } else {
                        int off = (g.cellSize() - IconCatalog.SIZE) / 2;
                        drewIcon = icon(line, cellIcon, cx + off, cy + off, null);
                    }
                    if (drewIcon && g.count() != null) {
                        int off = (g.cellSize() - IconCatalog.SIZE) / 2;
                        count(line, g.count().apply(item), cx + off, cy + off);
                    }
                }
                if (item != null) {
                    String label = g.label().apply(item);
                    if ((label == null || label.isBlank()) && !drewIcon && g.icon() != null) {
                        label = shortName(g.icon().apply(item));
                    }
                    String s = line.fit(label == null ? "" : label, g.cellSize() - 2);
                    TextColor color = g.labelColor().apply(item);
                    line.centered(s, cx, g.cellSize(), cy + (g.cellSize() - 7) / 2, color, LineBuilder.shadowOf(color));
                }
            }
        }
        pager(line, g.id(), page, pages(items.size(), perPage), g.x(), gy + g.rows() * g.cellPitch() + 2,
            g.cols() * g.cellPitch() - (g.cellPitch() - g.cellSize()), binder, name);
    }

    /**
     * Track ("slot", one tile high) and knob ("button", one tile wide, 16 px high). Every pixel column
     * of the track picks the value whose knob would sit under the cursor; neighbouring columns with
     * the same value share one click zone, with that value as tooltip. Hit glyphs are 8 px wide, and
     * the last one under the cursor wins, so zones are laid out left to right and each one starts
     * exactly where the previous one ends.
     */
    private void slider(LineBuilder line, Slider s, View view, Binder binder, String name) {
        Frame track = frame("slot");
        Frame knob = frame("button");
        int t = track == null ? 8 : track.tileSize();
        int w = Math.max(2 * t, s.width() / t * t);
        int knobW = knob == null ? 8 : knob.tileSize();
        if (track != null) {
            line.frame(track, s.x(), s.y() + (Slider.HEIGHT - t) / 2, w, t, null, null, null);
        }
        int n = s.positions();
        int span = Math.max(1, w - knobW);
        Float current = s.value().apply(view);
        int pos = s.positionOf(current == null ? s.min() : current);
        int kx = s.x() + (n <= 1 ? 0 : Math.round(pos * span / (float) (n - 1)));
        if (knob != null) {
            line.frame(knob, kx, s.y(), knobW, Slider.HEIGHT, s.tint().apply(view), null, null);
        }
        if (s.onChange() == null) {
            return;
        }
        int start = 0;
        int startPos = sliderPosition(0, knobW, span, n);
        for (int col = 1; col <= w; col++) {
            int p = col < w ? sliderPosition(col, knobW, span, n) : -1;
            if (p == startPos) {
                continue;
            }
            float v = s.valueAt(startPos);
            ClickEvent<?> click = binder.bind(name + ":v" + startPos, Redraw.after(c -> s.onChange().accept(c, v)));
            Component tip = s.tooltip() != null ? s.tooltip().apply(view, v)
                : Component.text(de.flyingfinger.glyphwork.dialog.FormValues.format(v));
            for (int xx = start; xx < col; xx += hit.width()) {
                for (int yy = 0; yy < Slider.HEIGHT; yy += hit.height()) {
                    line.glyph(hit, s.x() + xx, s.y() + yy, null, click, tip);
                }
            }
            start = col;
            startPos = p;
        }
    }

    /** The slider position whose knob covers pixel column {@code col} of the track best. */
    static int sliderPosition(int col, int knobW, int span, int positions) {
        if (positions <= 1) {
            return 0;
        }
        float f = (col - knobW / 2f + 0.5f) / span;
        return Math.max(0, Math.min(positions - 1, Math.round(f * (positions - 1))));
    }

    /** Box ("slot", 16 x 16) with a check mark, text to the right; box and text toggle. */
    private void checkbox(LineBuilder line, Checkbox cb, View view, Binder binder, String name) {
        boolean checked = Boolean.TRUE.equals(cb.checked().apply(view));
        ClickEvent<?> click = cb.onToggle() == null ? null
            : binder.bind(name, Redraw.after(c -> cb.onToggle().accept(c, !checked)));
        Component tip = tooltip(cb.tooltip(), view);
        Frame box = frame("slot");
        if (box != null) {
            line.frame(box, cb.x(), cb.y(), Checkbox.SIZE, Checkbox.SIZE, null, click, tip);
        } else {
            hitbox(line, cb.x(), cb.y(), Checkbox.SIZE, Checkbox.SIZE, click, tip);
        }
        if (checked) {
            line.centered("\u2714", cb.x(), Checkbox.SIZE, cb.y() + 4, WHITE, LineBuilder.shadowOf(WHITE));
        }
        String s = cb.text().apply(view);
        if (s != null && !s.isEmpty()) {
            TextColor color = skinText(cb.textColor().apply(view));
            line.text(s, cb.x() + Checkbox.SIZE + 4, cb.y() + 4, color);
            hitbox(line, cb.x() + Checkbox.SIZE, cb.y(), Math.max(hit.width(), 4 + line.textWidth(s)), Checkbox.SIZE, click, tip);
        }
    }

    /** A button "&lt;  option  &gt;": its left half picks the previous option, the right half the next. */
    private void choice(LineBuilder line, Choice ch, View view, Binder binder, String name) {
        Frame f = frame("button");
        int h = f == null ? ch.height() : Math.max(f.tileSize(), ch.height() / f.tileSize() * f.tileSize());
        boolean active = ch.onSelect() != null;
        TextColor tint = ch.tint().apply(view);
        if (f != null) {
            line.frame(f, ch.x(), ch.y(), ch.width(), ch.height(), tint, null, null);
        }
        Component tip = tooltip(ch.tooltip(), view);
        if (active) {
            int half = ch.width() / 2;
            hitbox(line, ch.x(), ch.y(), half, h,
                binder.bind(name + ":prev", Redraw.after(c -> ch.onSelect().accept(c, ch.step(c, -1)))), tip);
            hitbox(line, ch.x() + half, ch.y(), ch.width() - half, h,
                binder.bind(name + ":next", Redraw.after(c -> ch.onSelect().accept(c, ch.step(c, 1)))), tip);
        }
        int textY = ch.y() + (h - 7) / 2;
        ShadowColor shadow = LineBuilder.shadowOf(WHITE);
        if (ch.options().size() > 1) {
            line.centered("<", ch.x(), 12, textY, WHITE, shadow);
            line.centered(">", ch.x() + ch.width() - 12, 12, textY, WHITE, shadow);
        }
        String s = line.fit(String.valueOf(ch.text().apply(ch.options().get(ch.index(view)))), ch.width() - 26);
        line.centered(s, ch.x() + 12, ch.width() - 24, textY, WHITE, shadow);
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
        line.centered("<", x, 16, y + 4, prev ? WHITE : DISABLED_TEXT, prev ? LineBuilder.shadowOf(WHITE) : null);
        line.centered(">", x + w - 16, 16, y + 4, next ? WHITE : DISABLED_TEXT, next ? LineBuilder.shadowOf(WHITE) : null);
        line.centered((page + 1) + "/" + pages, x, w, y + 4, skinText(Label.DARK));
    }
}
