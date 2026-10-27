package de.flyingfinger.glyphwork.render;

import de.flyingfinger.glyphwork.core.Messages;
import de.flyingfinger.glyphwork.api.Bar;
import de.flyingfinger.glyphwork.api.Button;
import de.flyingfinger.glyphwork.api.Checkbox;
import de.flyingfinger.glyphwork.api.Choice;
import de.flyingfinger.glyphwork.api.Slider;
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
        List<Bar> bars = new ArrayList<>();
        List<Action> actions = new ArrayList<>();
        java.util.ArrayDeque<Element> todo = new java.util.ArrayDeque<>(scene.elements());
        while (!todo.isEmpty()) {
            Element e = todo.poll();
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
                    case Slider s -> slider(s, view, actions);
                    case de.flyingfinger.glyphwork.api.Scroll sc -> {
                        // the plain dialog scrolls by itself: all elements, in order
                        java.util.List<Element> inner = sc.elements();
                        for (int i = inner.size() - 1; i >= 0; i--) {
                            todo.push(inner.get(i));
                        }
                    }
                    case de.flyingfinger.glyphwork.api.Tabs t -> {
                        de.flyingfinger.glyphwork.api.Tabs.Tab active = t.active(view);
                        for (de.flyingfinger.glyphwork.api.Tabs.Tab tab : t.tabs()) {
                            boolean on = tab == active;
                            actions.add(new Action(Component.text((on ? "\u25b6 " : "") + tab.title()), null,
                                Redraw.after(c -> t.select(c, tab.id()))));
                        }
                        if (active != null) {
                            // the active tab's elements, in order, right after the tabs
                            java.util.List<Element> inner = active.elements();
                            for (int i = inner.size() - 1; i >= 0; i--) {
                                todo.push(inner.get(i));
                            }
                        }
                    }
                    case Bar b -> bars.add(b);
                    case de.flyingfinger.glyphwork.api.Head hd -> {
                        // the client's small head in front of the tooltip, as a button when clickable
                        if (hd.onClick() != null) {
                            Component tip = hd.tooltip() == null ? null : hd.tooltip().apply(view);
                            String who = hd.player().apply(view);
                            actions.add(new Action(withHead(tip != null ? tip : Component.text(who == null ? "•" : who),
                                who == null ? null : "head:" + who), null, hd.onClick()));
                        }
                    }
                    case de.flyingfinger.glyphwork.api.Image im -> {
                        // pictures need the pack; a clickable one becomes a button named by its tooltip
                        if (im.onClick() != null) {
                            Component tip = im.tooltip() == null ? null : im.tooltip().apply(view);
                            String id = im.image().apply(view);
                            actions.add(new Action(tip != null ? tip : Component.text(id == null ? "•" : id), null, im.onClick()));
                        }
                    }
                    case Checkbox cb -> {
                        if (cb.onToggle() != null) {
                            boolean on = Boolean.TRUE.equals(cb.checked().apply(view));
                            String text = cb.text().apply(view);
                            actions.add(new Action(Component.text((on ? "\u2611 " : "\u2610 ") + (text == null ? "" : text)),
                                cb.tooltip() == null ? null : cb.tooltip().apply(view),
                                Redraw.after(c -> cb.onToggle().accept(c, !on))));
                        }
                    }
                    case Choice ch -> {
                        if (ch.onSelect() != null) {
                            String now = ch.text().apply(ch.options().get(ch.index(view)));
                            actions.add(new Action(Component.text(now + "  \u25b6"),
                                ch.tooltip() == null ? null : ch.tooltip().apply(view),
                                Redraw.after(c -> ch.onSelect().accept(c, ch.step(c, 1)))));
                        }
                    }
                }
            } catch (RuntimeException ex) {
                log.warning("Fallback for " + scene.id() + " skipped an element: " + ex);
            }
        }
        List<Component> lines = new ArrayList<>();
        List<de.flyingfinger.glyphwork.api.Element> texts = new ArrayList<>(labels);
        texts.addAll(bars);
        texts.stream()
            .sorted(Comparator.comparingInt(de.flyingfinger.glyphwork.api.Element::y)
                .thenComparingInt(de.flyingfinger.glyphwork.api.Element::x))
            .forEach(e -> {
                if (e instanceof Label l) {
                    String s = l.text().apply(view);
                    if (s != null && !s.isEmpty()) {
                        // the dialog wraps by itself; "\n" lines stay lines
                        for (String part : s.split("\n")) {
                            lines.add(styled(l, part, view));
                        }
                    }
                } else if (e instanceof Bar b) {
                    lines.add(barLine(b, view));
                }
            });
        return new Model(scene.title().apply(view), lines, actions);
    }

    /**
     * The button text with the first line of its tooltip below it, gray (for Bedrock forms, which
     * have no tooltips). Nothing is added when the tooltip only repeats the text.
     */
    public static Component withTooltipLine(Component label, @Nullable Component tooltip) {
        if (tooltip == null) {
            return label;
        }
        net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer plain =
            net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer.plainText();
        String tip = plain.serialize(tooltip).strip();
        int nl = tip.indexOf('\n');
        if (nl >= 0) {
            tip = tip.substring(0, nl).strip();
        }
        if (tip.isEmpty() || tip.equalsIgnoreCase(plain.serialize(label).strip())) {
            return label;
        }
        if (tip.length() > 48) {
            tip = tip.substring(0, 47).strip() + "…";
        }
        return label.append(Component.newline()).append(Component.text(tip, net.kyori.adventure.text.format.NamedTextColor.DARK_GRAY));
    }

    /** A label line as a component: formatting and the label's switches, in a readable color. */
    private static Component styled(Label l, String part, View view) {
        Component c = l.markup() ? net.kyori.adventure.text.minimessage.MiniMessage.miniMessage().deserialize(part)
            : Component.text(part);
        c = c.colorIfAbsent(readable(l.color().apply(view)));
        if (l.bold()) {
            c = c.decoration(net.kyori.adventure.text.format.TextDecoration.BOLD, true);
        }
        if (l.italic()) {
            c = c.decoration(net.kyori.adventure.text.format.TextDecoration.ITALIC, true);
        }
        if (l.underline()) {
            c = c.decoration(net.kyori.adventure.text.format.TextDecoration.UNDERLINED, true);
        }
        if (l.strikethrough()) {
            c = c.decoration(net.kyori.adventure.text.format.TextDecoration.STRIKETHROUGH, true);
        }
        return c;
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
        Component label = text != null && !text.isBlank()
            ? (b.markup() ? net.kyori.adventure.text.minimessage.MiniMessage.miniMessage().deserialize(text) : Component.text(text))
            : tip != null ? tip : Component.text("•");
        actions.add(new Action(withHead(label, b.icon() == null ? null : b.icon().apply(view)), tip, b.onClick()));
    }

    private static <T> void list(ListView<T> l, View view, List<Action> actions) {
        List<T> items = l.items().apply(view);
        int per = Math.max(1, l.rows());
        int pages = Math.max(1, (items.size() + per - 1) / per);
        int page = Math.max(0, Math.min(view.page(l.id()), pages - 1));
        for (int i = page * per; i < Math.min(items.size(), (page + 1) * per); i++) {
            T item = items.get(i);
            if (l.onSelect() == null) {
                continue;
            }
            String text = String.valueOf(l.text().apply(item));
            Component shown = l.markup() ? net.kyori.adventure.text.minimessage.MiniMessage.miniMessage().deserialize(text) : Component.text(text);
            Component label = withHead(withCount(l.isSelected(view, item) ? Component.text("▶ ").append(shown) : shown,
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
            actions.add(new Action(withHead(withCount(Component.text(g.isSelected(view, item) ? "▶ " + text : text),
                    g.count() == null ? null : g.count().apply(item)), g.icon() == null ? null : g.icon().apply(item)), tip,
                c -> g.onClick().accept(c, item)));
        }
        pager(g.id(), page, pages, actions, view);
    }

    /** A slider becomes two buttons: one step down, one step up (labelled with the value they set). */
    private static void slider(Slider s, View view, List<Action> actions) {
        if (s.onChange() == null) {
            return;
        }
        Float current = s.value().apply(view);
        int pos = s.positionOf(current == null ? s.min() : current);
        if (pos > 0) {
            float v = s.valueAt(pos - 1);
            actions.add(new Action(Component.text("\u25c0 ").append(sliderLabel(s, view, v)), null,
                Redraw.after(c -> s.onChange().accept(c, v))));
        }
        if (pos < s.positions() - 1) {
            float v = s.valueAt(pos + 1);
            actions.add(new Action(sliderLabel(s, view, v).append(Component.text(" \u25b6")), null,
                Redraw.after(c -> s.onChange().accept(c, v))));
        }
    }

    private static Component sliderLabel(Slider s, View view, float v) {
        Component tip = s.tooltip() == null ? null : s.tooltip().apply(view, v);
        return tip != null ? tip : Component.text(de.flyingfinger.glyphwork.dialog.FormValues.format(v));
    }

    /** A bar as text: ten blocks in its color, then its text or the percentage. */
    static Component barLine(Bar b, View view) {
        double r = Bar.ratio(b.value().apply(view), b.max().apply(view));
        int full = (int) Math.round(r * 10);
        String text = b.text().apply(view);
        return Component.text()
            .append(Component.text("\u2588".repeat(full), b.color().apply(view)))
            .append(Component.text("\u2588".repeat(10 - full), NamedTextColor.DARK_GRAY))
            .append(Component.text(" " + (text != null && !text.isEmpty() ? text : Math.round(r * 100) + " %"), LIGHT))
            .build();
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
