package de.flyingfinger.glyphwork.menu;

import de.flyingfinger.glyphwork.api.Button;
import de.flyingfinger.glyphwork.api.Click;
import de.flyingfinger.glyphwork.api.Grid;
import de.flyingfinger.glyphwork.api.Hitbox;
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
        "command", "aliases", "permission", "description", "elements");

    private final Map<String, Menu> menus = new LinkedHashMap<>();
    private final List<String> errors = new ArrayList<>();

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

    /** Loads every *.yml in {@code dir}; files with errors are skipped and reported. */
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
        List<Node> elements = root.nodes("elements");
        for (int i = 0; i < elements.size(); i++) {
            element(b, elements.get(i), i);
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

    private void element(Scene.Builder b, Node n, int index) throws MenuException {
        String type = n.string("type").toLowerCase();
        switch (type) {
            case "label" -> {
                n.only(Set.of("type", "x", "y", "text", "color", "width", "align"));
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
                b.add(l);
            }
            case "button" -> {
                n.only(Set.of("type", "x", "y", "width", "height", "text", "tint", "text-color", "tooltip",
                    "actions", "permission", "frame"));
                Button bt = Button.of(n.integer("x"), n.integer("y"), n.optInt("width", 64), n.optInt("height", 16))
                    .text(text(n.optString("text", "")))
                    .tint(n.optColor("tint", Button.GRAY))
                    .textColor(n.optColor("text-color", TextColor.color(0xFFFFFF)))
                    .frame(n.optString("frame", "button"))
                    .onClick(actions(n));
                if (tooltip(n) != null) {
                    bt.tooltip(tooltip(n));
                }
                String perm = n.optString("permission", null);
                if (perm != null) {
                    bt.enabled(v -> v.player().hasPermission(perm));
                }
                b.add(bt);
            }
            case "panel" -> {
                n.only(Set.of("type", "x", "y", "width", "height", "shape", "frame", "tint", "tooltip", "actions"));
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
                b.add(p);
            }
            case "hitbox" -> {
                n.only(Set.of("type", "x", "y", "width", "height", "tooltip", "actions", "permission"));
                Hitbox h = Hitbox.of(n.integer("x"), n.integer("y"), n.integer("width"), n.integer("height"))
                    .onClick(actions(n));
                if (tooltip(n) != null) {
                    h.tooltip(tooltip(n));
                }
                b.add(h);
            }
            case "list" -> {
                n.only(Set.of("type", "id", "x", "y", "width", "rows", "row-height", "items"));
                List<Entry> items = entries(n);
                ListView<Entry> l = ListView.of(n.optString("id", "list" + index), n.integer("x"), n.integer("y"),
                        n.optInt("width", 120), n.optInt("rows", 4), v -> visible(items, v))
                    .rowHeight(n.optInt("row-height", 16))
                    .text(e -> e.text)
                    .color(e -> e.color)
                    .tooltip(e -> e.tooltip)
                    .onSelect((c, e) -> e.actions.accept(c));
                b.add(l);
            }
            case "grid" -> {
                n.only(Set.of("type", "id", "x", "y", "cols", "rows", "cell", "size", "items"));
                List<Entry> items = entries(n);
                Grid<Entry> g = Grid.of(n.optString("id", "grid" + index), n.integer("x"), n.integer("y"),
                        n.optInt("cols", 4), n.optInt("rows", 2), v -> visible(items, v))
                    .cell(n.optInt("cell", 18), n.optInt("size", 16))
                    .label(e -> e.text)
                    .labelColor(e -> e.color)
                    .tint(e -> e.tint)
                    .tooltip(e -> e.tooltip)
                    .onClick((c, e) -> e.actions.accept(c));
                b.add(g);
            }
            default -> throw n.error("type", "unknown type '" + type + "' (label, button, panel, hitbox, list, grid)");
        }
    }

    /** One list row or grid cell. Texts are fixed (placeholders are resolved per viewer in {@link #visible}). */
    private record Entry(String text, TextColor color, @Nullable TextColor tint, @Nullable Component tooltip,
                         Consumer<Click> actions, @Nullable String permission, String rawText, @Nullable String rawTooltip) {
    }

    private List<Entry> entries(Node n) throws MenuException {
        List<Entry> out = new ArrayList<>();
        for (Node e : n.nodes("items")) {
            e.only(Set.of("text", "color", "tint", "tooltip", "actions", "permission"));
            String t = e.optString("text", "");
            String tip = e.optString("tooltip", null);
            out.add(new Entry(t, e.optColor("color", TextColor.color(0xFFFFFF)), e.optColor("tint", null),
                null, actions(e), e.optString("permission", null), t, tip));
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
            Component tip = e.rawTooltip == null ? null : MM.deserialize(Placeholders.apply(v.player(), e.rawTooltip));
            out.add(new Entry(Placeholders.apply(v.player(), e.rawText), e.color, e.tint, tip, e.actions,
                e.permission, e.rawText, e.rawTooltip));
        }
        return out;
    }
}
