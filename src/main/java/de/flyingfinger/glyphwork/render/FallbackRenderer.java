package de.flyingfinger.glyphwork.render;

import de.flyingfinger.glyphwork.core.Messages;
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
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.function.Consumer;
import java.util.logging.Logger;

/**
 * Plain version of a scene for players without the pack: labels become text lines, everything
 * clickable becomes a native dialog button. Positions and frames are dropped.
 */
public final class FallbackRenderer {

    public record Model(Component title, List<Component> lines, List<Action> actions) {
    }

    public record Action(Component label, @Nullable Component tooltip, Consumer<Click> onClick) {
    }

    private static final TextColor LIGHT = TextColor.color(0xE0E0E0);
    private final Logger log;

    public FallbackRenderer(Logger log) {
        this.log = log;
    }

    public Model render(Scene scene, View view) {
        List<Label> labels = new ArrayList<>();
        List<Action> actions = new ArrayList<>();
        for (Element e : scene.elements()) {
            try {
                if (!Boolean.TRUE.equals(e.visible().apply(view))) {
                    continue;
                }
                switch (e) {
                    case Label l -> labels.add(l);
                    case Button b -> button(b, view, actions);
                    case Panel p -> {
                        // panels are decoration (often a clickable background); not offered without the pack
                    }
                    case Hitbox h -> {
                        Component tip = h.tooltip() == null ? null : h.tooltip().apply(view);
                        if (h.onClick() != null) {
                            actions.add(new Action(tip != null ? tip : Component.text("•"), null, h.onClick()));
                        }
                    }
                    case Icon ic -> {
                        if (ic.onClick() != null) {
                            Component tip = ic.tooltip() == null ? null : ic.tooltip().apply(view);
                            String item = ic.item().apply(view);
                            Component label = withCount(tip != null ? tip : Component.text(humanize(item)),
                                ic.count() == null ? null : ic.count().apply(view));
                            actions.add(new Action(withHead(label, item), null, ic.onClick()));
                        }
                    }
                    case ListView<?> l -> list(l, view, actions);
                    case Grid<?> g -> grid(g, view, actions);
                }
            } catch (RuntimeException ex) {
                log.warning("Fallback for " + scene.id() + " skipped an element: " + ex);
            }
        }
        List<Component> lines = new ArrayList<>();
        labels.stream()
            .sorted(Comparator.comparingInt(Label::y).thenComparingInt(Label::x))
            .forEach(l -> {
                String s = l.text().apply(view);
                if (s != null && !s.isEmpty()) {
                    lines.add(Component.text(s, readable(l.color().apply(view))));
                }
            });
        return new Model(scene.title().apply(view), lines, actions);
    }

    /** Label colors are chosen for light panels; the dialog background is dark. */
    static TextColor readable(TextColor c) {
        double lum = (0.299 * c.red() + 0.587 * c.green() + 0.114 * c.blue()) / 255;
        return lum < 0.45 ? LIGHT : c;
    }

    private static void button(Button b, View view, List<Action> actions) {
        if (b.onClick() == null || !Boolean.TRUE.equals(b.enabled().apply(view))) {
            return;
        }
        String text = b.text().apply(view);
        Component tip = b.tooltip() == null ? null : b.tooltip().apply(view);
        Component label = text != null && !text.isBlank() ? Component.text(text) : tip != null ? tip : Component.text("•");
        actions.add(new Action(withHead(label, b.icon() == null ? null : b.icon().apply(view)), tip, b.onClick()));
    }

    private static <T> void list(ListView<T> l, View view, List<Action> actions) {
        List<T> items = l.items().apply(view);
        int per = Math.max(1, l.rows());
        int pages = Math.max(1, (items.size() + per - 1) / per);
        int page = Math.max(0, Math.min(view.page(l.id()), pages - 1));
        T selected = l.selected() == null ? null : l.selected().apply(view);
        for (int i = page * per; i < Math.min(items.size(), (page + 1) * per); i++) {
            T item = items.get(i);
            if (l.onSelect() == null) {
                continue;
            }
            String text = String.valueOf(l.text().apply(item));
            Component label = withHead(withCount(Component.text(item.equals(selected) ? "▶ " + text : text),
                l.count() == null ? null : l.count().apply(item)), l.icon() == null ? null : l.icon().apply(item));
            actions.add(new Action(label, l.tooltip() == null ? null : l.tooltip().apply(item), c -> l.onSelect().accept(c, item)));
        }
        pager(l.id(), page, pages, actions, view);
    }

    private static <T> void grid(Grid<T> g, View view, List<Action> actions) {
        List<T> items = g.items().apply(view);
        int per = Math.max(1, g.cols() * g.rows());
        int pages = Math.max(1, (items.size() + per - 1) / per);
        int page = Math.max(0, Math.min(view.page(g.id()), pages - 1));
        T selected = g.selected() == null ? null : g.selected().apply(view);
        for (int i = page * per; i < Math.min(items.size(), (page + 1) * per); i++) {
            T item = items.get(i);
            if (g.onClick() == null) {
                continue;
            }
            Component tip = g.tooltip() == null ? null : g.tooltip().apply(item);
            String label = g.label().apply(item);
            // grid labels are short (a symbol, a number); the tooltip usually says more
            String iconName = g.icon() == null ? null : humanize(g.icon().apply(item));
            String text = tip != null ? PlainTextComponentSerializer.plainText().serialize(tip)
                : label != null && !label.isBlank() ? label : iconName != null ? iconName : "•";
            actions.add(new Action(withHead(withCount(Component.text(item.equals(selected) ? "▶ " + text : text),
                    g.count() == null ? null : g.count().apply(item)), g.icon() == null ? null : g.icon().apply(item)), tip,
                c -> g.onClick().accept(c, item)));
        }
        pager(g.id(), page, pages, actions, view);
    }

    /** "Diamond" with count "64" -> "Diamond ×64" (gray); "1" and empty add nothing. */
    static Component withCount(Component label, @Nullable String count) {
        String s = count == null ? "" : count.trim();
        return s.isEmpty() || s.equals("1") ? label : label.append(Component.text(" ×" + s, NamedTextColor.GRAY));
    }

    /** A player head ("head:Name" icon) in front of the label; native buttons show it as is. */
    private static Component withHead(Component label, @Nullable String icon) {
        Component head = SceneRenderer.headOf(icon);
        return head == null ? label : Component.text().append(head).append(Component.text(" ")).append(label).build();
    }

    /** "minecraft:diamond_sword" -> "Diamond Sword" (for icons without text), "head:Steve" -> "Steve". */
    static String humanize(@Nullable String item) {
        if (item == null || item.isBlank()) {
            return "•";
        }
        if (SceneRenderer.isHead(item)) {
            return item.substring(5).trim();
        }
        String id = item.contains(":") ? item.substring(item.indexOf(':') + 1) : item;
        StringBuilder sb = new StringBuilder();
        for (String part : id.split("_")) {
            if (!part.isEmpty()) {
                sb.append(sb.isEmpty() ? "" : " ").append(Character.toUpperCase(part.charAt(0))).append(part.substring(1));
            }
        }
        return sb.toString();
    }

    private static void pager(String id, int page, int pages, List<Action> actions, View view) {
        if (pages <= 1) {
            return;
        }
        String key = "glyphwork:page:" + id;
        Component info = Component.text(" (" + (page + 1) + "/" + pages + ")", NamedTextColor.GRAY);
        if (page > 0) {
            actions.add(new Action(Messages.active().get(view.player(), "fallback.previous").append(info), null,
                c -> { c.set(key, page - 1); c.reopen(); }));
        }
        if (page < pages - 1) {
            actions.add(new Action(Messages.active().get(view.player(), "fallback.next").append(info), null,
                c -> { c.set(key, page + 1); c.reopen(); }));
        }
    }
}
