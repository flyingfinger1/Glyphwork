package de.flyingfinger.glyphwork.menu;

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
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.jspecify.annotations.Nullable;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.stream.Stream;

/** Menus from plugins/Glyphwork/menus/*.yml, built as scenes of the public API. */
public final class Menus {

    /** One menu file. */
    public record Menu(String id, Scene scene, @Nullable String command, List<String> aliases,
                       @Nullable String permission, @Nullable String description) {

        public boolean allowed(Player p) {
            return permission == null || p.hasPermission(permission);
        }
    }

    private static final MiniMessage MM = MiniMessage.miniMessage();
    private static final Set<String> MENU_KEYS = Set.of("title", "width", "height", "background", "exit-label",
        "command", "aliases", "permission", "description", "elements", "templates");

    private final Map<String, Menu> menus = new LinkedHashMap<>();
    private final List<String> errors = new ArrayList<>();
    /** Templates from menus/_*.yml, available in every menu. */
    private final Templates shared = new Templates();

    public Collection<Menu> all() {
        return menus.values();
    }

    public @Nullable Menu get(String id) {
        return menus.get(id.toLowerCase());
    }

    /** Problems found while loading, one line each ("warps.yml: elements[2].x: number expected"). */
    public List<String> errors() {
        return errors;
    }

    /**
     * Loads every *.yml in {@code dir}; files with errors are skipped and reported. Files whose name
     * starts with "_" hold templates for all menus (see {@link Templates}) and are loaded first.
     */
    public static Menus load(Path dir) {
        Menus m = new Menus();
        if (!Files.isDirectory(dir)) {
            return m;
        }
        List<Path> files;
        try (Stream<Path> s = Files.list(dir)) {
            files = s.filter(p -> p.getFileName().toString().toLowerCase().endsWith(".yml")).sorted().toList();
        } catch (IOException e) {
            m.errors.add(dir + ": " + e.getMessage());
            return m;
        }
        for (Path f : files) {
            if (f.getFileName().toString().startsWith("_")) {
                try {
                    m.addTemplates(f.getFileName().toString(), Files.readString(f, StandardCharsets.UTF_8));
                } catch (MenuException e) {
                    m.errors.add(e.getMessage());
                } catch (IOException e) {
                    m.errors.add(f.getFileName() + ": " + e.getMessage());
                }
            }
        }
        for (Path f : files) {
            if (f.getFileName().toString().startsWith("_")) {
                continue;
            }
            String id = f.getFileName().toString().replaceFirst("(?i)\\.yml$", "").toLowerCase();
            try {
                m.menus.put(id, m.parse(id, Files.readString(f, StandardCharsets.UTF_8)));
            } catch (MenuException e) {
                m.errors.add(e.getMessage());
            } catch (IOException e) {
                m.errors.add(f.getFileName() + ": " + e.getMessage());
            }
        }
        return m;
    }

    /** Adds the templates of one _*.yml file (also used by tests). */
    public void addTemplates(String file, String yaml) throws MenuException {
        YamlConfiguration c = new YamlConfiguration();
        try {
            c.loadFromString(yaml);
        } catch (InvalidConfigurationException e) {
            throw new MenuException(file + ": not valid YAML: " + firstLine(e.getMessage()));
        }
        shared.addAll(c.getValues(false));
    }

    /** Parses one menu file's content (also used by tests). */
    public Menu parse(String id, String yaml) throws MenuException {
        YamlConfiguration c = new YamlConfiguration();
        try {
            c.loadFromString(yaml);
        } catch (InvalidConfigurationException e) {
            throw new MenuException(id + ".yml: not valid YAML: " + firstLine(e.getMessage()));
        }
        Node root = new Node(c.getValues(false), id + ".yml");
        root.only(MENU_KEYS);
        int width = root.optInt("width", 176);
        int height = root.optInt("height", 96);
        String title = root.optString("title", id);
        Scene.Builder b = Scene.builder("menu:" + id, width, height)
            .title(v -> MM.deserialize(Placeholders.apply(v.player(), title)));
        if (root.has("exit-label")) {
            b.exitLabel(MM.deserialize(root.string("exit-label")));
        }
        String background = root.optString("background", "panel");
        if (!"none".equalsIgnoreCase(background)) {
            b.add(Panel.of(0, 0, width, height).frame(background));
        }
        Templates templates = new Templates(shared);
        if (root.has("templates")) {
            if (!(Templates.plain(c.get("templates")) instanceof Map<?, ?> local)) {
                throw root.error("templates", "map of templates expected (name: element or list of elements)");
            }
            templates.addAll(local);
        }
        List<Node> elements = root.nodes("elements");
        int index = 0;
        for (Node top : elements) {
            for (Node n : templates.expand(top)) {
                element(b, n, index++);
            }
        }
        String command = root.optString("command", null);
        if (command != null) {
            command = command.trim().replaceFirst("^/", "").toLowerCase();
            if (!command.matches("[a-z0-9_.-]+")) {
                throw root.error("command", "only letters, digits, '_', '-', '.' (no spaces), got '" + command + "'");
            }
        }
        return new Menu(id, b.build(), command, root.strings("aliases").stream().map(String::toLowerCase).toList(),
            root.optString("permission", null), root.optString("description", null));
    }

    private static String firstLine(@Nullable String s) {
        return s == null ? "" : s.lines().findFirst().orElse("");
    }

    private Function<View, String> text(String template) {
        return v -> Placeholders.apply(v.player(), template);
    }

    private @Nullable Function<View, @Nullable Component> tooltip(Node n) {
        String t = n.optString("tooltip", null);
        return t == null ? null : v -> MM.deserialize(Placeholders.apply(v.player(), t));
    }

    private Consumer<Click> actions(Node n) throws MenuException {
        Consumer<Click> run = Actions.parse(n.strings("actions"), n, "actions", this::get);
        String perm = n.optString("permission", null);
        return perm == null ? run : c -> {
            if (c.player().hasPermission(perm)) {
                run.accept(c);
            }
        };
    }

    /** Keys of an element: its own plus show-if, which every element has. */
    private static Set<String> with(String... keys) {
        Set<String> s = new java.util.HashSet<>(Set.of(keys));
        s.add("show-if");
        return s;
    }

    private void element(Scene.Builder b, Node n, int index) throws MenuException {
        String type = n.string("type").toLowerCase();
        List<Condition> showIf = Condition.all(n, "show-if");
        List<Element> added = new ArrayList<>();
        element(added::add, n, index, type);
        for (Element e : added) {
            if (!showIf.isEmpty()) {
                show(e, v -> Condition.all(showIf, v.player()));
            }
            b.add(e);
        }
    }

    private static void show(Element e, Function<View, Boolean> visible) {
        switch (e) {
            case Label x -> x.visible(visible);
            case Button x -> x.visible(visible);
            case Panel x -> x.visible(visible);
            case Hitbox x -> x.visible(visible);
            case Icon x -> x.visible(visible);
            case ListView<?> x -> x.visible(visible);
            case Grid<?> x -> x.visible(visible);
        }
    }

    private void element(Consumer<Element> b, Node n, int index, String type) throws MenuException {
        switch (type) {
            case "label" -> {
                n.only(with("type", "x", "y", "text", "color", "width", "align"));
                Label l = Label.of(n.integer("x"), n.integer("y"), text(n.string("text")))
                    .color(n.optColor("color", Label.DARK));
                if (n.has("width")) {
                    l.width(n.integer("width"));
                }
                String align = n.optString("align", "left").toUpperCase();
                try {
                    l.align(Label.Align.valueOf(align));
                } catch (IllegalArgumentException e) {
                    throw n.error("align", "left, center or right expected");
                }
                b.accept(l);
            }
            case "button" -> {
                n.only(with("type", "x", "y", "width", "height", "text", "tint", "text-color", "tooltip",
                    "actions", "permission", "frame", "icon", "enabled-if", "disabled-tooltip"));
                Button bt = Button.of(n.integer("x"), n.integer("y"), n.optInt("width", 64), n.optInt("height", 16))
                    .text(text(n.optString("text", "")))
                    .tint(n.optColor("tint", Button.GRAY))
                    .textColor(n.optColor("text-color", TextColor.color(0xFFFFFF)))
                    .frame(n.optString("frame", "button"))
                    .onClick(actions(n));
                String perm = n.optString("permission", null);
                List<Condition> enabledIf = Condition.all(n, "enabled-if");
                Function<View, Boolean> enabled = v -> (perm == null || v.player().hasPermission(perm))
                    && Condition.all(enabledIf, v.player());
                bt.enabled(enabled);
                Function<View, @Nullable Component> tip = tooltip(n);
                String off = n.optString("disabled-tooltip", null);
                if (off != null) {
                    bt.tooltip(v -> enabled.apply(v) ? (tip == null ? null : tip.apply(v))
                        : MM.deserialize(Placeholders.apply(v.player(), off)));
                } else if (tip != null) {
                    bt.tooltip(tip);
                }
                if (n.has("icon")) {
                    bt.icon(n.string("icon"));
                }
                b.accept(bt);
            }
            case "panel" -> {
                n.only(with("type", "x", "y", "width", "height", "shape", "frame", "tint", "tooltip", "actions"));
                Panel p = n.has("shape")
                    ? Panel.shape(n.integer("x"), n.integer("y"), n.strings("shape").toArray(String[]::new))
                    : Panel.of(n.integer("x"), n.integer("y"), n.integer("width"), n.integer("height"));
                p.frame(n.optString("frame", "panel")).tint(n.optColor("tint", null));
                if (tooltip(n) != null) {
                    p.tooltip(tooltip(n));
                }
                if (n.has("actions")) {
                    p.onClick(actions(n));
                }
                b.accept(p);
            }
            case "hitbox" -> {
                n.only(with("type", "x", "y", "width", "height", "tooltip", "actions", "permission"));
                Hitbox h = Hitbox.of(n.integer("x"), n.integer("y"), n.integer("width"), n.integer("height"))
                    .onClick(actions(n));
                if (tooltip(n) != null) {
                    h.tooltip(tooltip(n));
                }
                b.accept(h);
            }
            case "icon" -> {
                n.only(with("type", "x", "y", "item", "tint", "tooltip", "actions"));
                String item = n.string("item");
                TextColor tint = n.optColor("tint", null);
                Icon ic = Icon.of(n.integer("x"), n.integer("y"), item).tint(v -> tint);
                if (tooltip(n) != null) {
                    ic.tooltip(tooltip(n));
                }
                if (n.has("actions")) {
                    ic.onClick(actions(n));
                }
                b.accept(ic);
            }
            case "list" -> {
                n.only(with("type", "id", "x", "y", "width", "rows", "row-height", "items"));
                List<Entry> items = entries(n);
                ListView<Entry> l = ListView.of(n.optString("id", "list" + index), n.integer("x"), n.integer("y"),
                        n.optInt("width", 120), n.optInt("rows", 4), v -> visible(items, v))
                    .rowHeight(n.optInt("row-height", 16))
                    .text(e -> e.text)
                    .color(e -> e.color)
                    .tooltip(e -> e.tooltip)
                    .icon(e -> e.icon)
                    .onSelect((c, e) -> e.actions.accept(c));
                b.accept(l);
            }
            case "grid" -> {
                n.only(with("type", "id", "x", "y", "cols", "rows", "cell", "size", "frame", "items"));
                List<Entry> items = entries(n);
                Grid<Entry> g = Grid.of(n.optString("id", "grid" + index), n.integer("x"), n.integer("y"),
                        n.optInt("cols", 4), n.optInt("rows", 2), v -> visible(items, v))
                    .cell(n.optInt("cell", 18), n.optInt("size", 16))
                    .label(e -> e.text)
                    .labelColor(e -> e.color)
                    .tint(e -> e.tint)
                    .tooltip(e -> e.tooltip)
                    .icon(e -> e.icon)
                    .frame(n.optString("frame", "slot"))
                    .onClick((c, e) -> e.actions.accept(c));
                b.accept(g);
            }
            default -> throw n.error("type", "unknown type '" + type + "' (label, button, panel, hitbox, icon, list, grid)");
        }
    }

    /** One list row or grid cell. Texts are fixed (placeholders are resolved per viewer in {@link #visible}). */
    private record Entry(String text, TextColor color, @Nullable TextColor tint, @Nullable Component tooltip,
                         Consumer<Click> actions, @Nullable String permission, String rawText, @Nullable String rawTooltip,
                         @Nullable String icon, List<Condition> showIf) {
    }

    private List<Entry> entries(Node n) throws MenuException {
        List<Entry> out = new ArrayList<>();
        for (Node e : n.nodes("items")) {
            e.only(Set.of("text", "color", "tint", "tooltip", "actions", "permission", "icon", "show-if"));
            String t = e.optString("text", "");
            String tip = e.optString("tooltip", null);
            out.add(new Entry(t, e.optColor("color", TextColor.color(0xFFFFFF)), e.optColor("tint", null),
                null, actions(e), e.optString("permission", null), t, tip, e.optString("icon", null),
                Condition.all(e, "show-if")));
        }
        return out;
    }

    /** Items the viewer may see, with placeholders resolved for them. */
    private static List<Entry> visible(List<Entry> items, View v) {
        List<Entry> out = new ArrayList<>();
        for (Entry e : items) {
            if (e.permission != null && v.player() != null && !v.player().hasPermission(e.permission)) {
                continue;
            }
            if (!Condition.all(e.showIf, v.player())) {
                continue;
            }
            Component tip = e.rawTooltip == null ? null : MM.deserialize(Placeholders.apply(v.player(), e.rawTooltip));
            out.add(new Entry(Placeholders.apply(v.player(), e.rawText), e.color, e.tint, tip, e.actions,
                e.permission, e.rawText, e.rawTooltip, e.icon, e.showIf));
        }
        return out;
    }
}
