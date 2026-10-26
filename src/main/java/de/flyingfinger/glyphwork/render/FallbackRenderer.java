package de.flyingfinger.glyphwork.render;

import de.flyingfinger.glyphwork.api.Button;
import de.flyingfinger.glyphwork.api.Click;
import de.flyingfinger.glyphwork.api.Element;
import de.flyingfinger.glyphwork.api.Grid;
import de.flyingfinger.glyphwork.api.Hitbox;
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
                switch (e) {
                    case Label l -> labels.add(l);
                    case Button b -> button(b, view, actions);
                    case Panel p -> {
                        // backgrounds are often clickable; only offer panels that say what they do
                        Component tip = p.tooltip() == null ? null : p.tooltip().apply(view);
                        if (p.onClick() != null && tip != null) {
                            actions.add(new Action(tip, null, p.onClick()));
                        }
                    }
                    case Hitbox h -> {
                        Component tip = h.tooltip() == null ? null : h.tooltip().apply(view);
                        if (h.onClick() != null) {
                            actions.add(new Action(tip != null ? tip : Component.text("•"), null, h.onClick()));
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
        actions.add(new Action(label, tip, b.onClick()));
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
            Component label = Component.text(item.equals(selected) ? "▶ " + text : text);
            actions.add(new Action(label, l.tooltip() == null ? null : l.tooltip().apply(item), c -> l.onSelect().accept(c, item)));
        }
        pager(l.id(), page, pages, actions);
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
            String text = tip != null ? PlainTextComponentSerializer.plainText().serialize(tip)
                : label == null || label.isBlank() ? "•" : label;
            actions.add(new Action(Component.text(item.equals(selected) ? "▶ " + text : text), tip,
                c -> g.onClick().accept(c, item)));
        }
        pager(g.id(), page, pages, actions);
    }

    private static void pager(String id, int page, int pages, List<Action> actions) {
        if (pages <= 1) {
            return;
        }
        String key = "glyphwork:page:" + id;
        Component info = Component.text(" (" + (page + 1) + "/" + pages + ")", NamedTextColor.GRAY);
        if (page > 0) {
            actions.add(new Action(Component.text("◀ zurück").append(info), null, c -> { c.set(key, page - 1); c.reopen(); }));
        }
        if (page < pages - 1) {
            actions.add(new Action(Component.text("weiter ▶").append(info), null, c -> { c.set(key, page + 1); c.reopen(); }));
        }
    }
}
