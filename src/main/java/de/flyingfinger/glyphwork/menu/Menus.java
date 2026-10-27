package de.flyingfinger.glyphwork.menu;

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
import java.util.function.UnaryOperator;
import java.util.stream.Stream;

/** Menus from plugins/Glyphwork/menus/*.yml, built as scenes of the public API. */
public final class Menus {

    /** One menu file. */
    public record Menu(String id, Scene scene, @Nullable String command, List<String> aliases,
                       @Nullable String permission, @Nullable String description, @Nullable Trigger trigger) {

        public boolean allowed(Player p) {
            return permission == null || p.hasPermission(permission);
        }
    }

    /**
     * When a menu opens by itself ("open-on:"). {@code join}: on joining ("first": only the first
     * time); {@code item}: right-clicking the menu item.
     */
    public record Trigger(boolean join, boolean firstJoinOnly, @Nullable MenuItem item) {
    }

    /** When the menu item is handed out automatically. */
    public enum Give { JOIN, FIRST, NO }

    /**
     * The item that opens a menu, put into hotbar {@code slot}. {@code keep}: it cannot be dropped and
     * comes back after death.
     */
    public record MenuItem(String material, @Nullable String name, List<String> lore, int slot, Give give, boolean keep) {
    }

    private static final MiniMessage MM = MiniMessage.miniMessage();
    private static final Set<String> MENU_KEYS = Set.of("title", "width", "height", "background", "exit-label",
        "command", "aliases", "permission", "description", "elements", "templates", "variables", "refresh", "forms",
        "on-open", "on-close", "open-on", "persist");

    private final Map<String, Menu> menus = new LinkedHashMap<>();
    private final List<String> errors = new ArrayList<>();
    /** Templates from menus/_*.yml, available in every menu. */
    private final Templates shared = new Templates();
    /** Entries of dynamic lists (items-from); replaced in tests. */
    private Function<String, List<Map<String, String>>> sources = Sources::fetch;
    /** Variables of the menu being parsed (parsing runs on one thread, one menu at a time). */
    private Vars vars = Vars.NONE;
    /** Id of the menu being parsed (keys of saved variables and cooldowns). */
    private String menuId = "";
    /** True while the elements of a scroll pane are parsed (no pane in a pane). */
    private boolean inScroll;
    /** Templates of the menu being parsed (also used inside tabs). */
    private Templates templates = new Templates();
    /** Index of the element being parsed (the default cooldown key). */
    private int elementIndex;
    /** Forms of the menu being parsed. */
    private Forms forms = Forms.NONE;

    /** Where dynamic lists get their entries (tests use fixed data). */
    public void sources(Function<String, List<Map<String, String>>> sources) {
        this.sources = sources;
    }

    /** Feeds of items-from: rss:...; replaced in tests. */
    private Function<String, Feeds.Result> feeds = Feeds::get;

    public void feeds(Function<String, Feeds.Result> feeds) {
        this.feeds = feeds;
    }

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
        vars = Vars.of(root, id);
        menuId = id;
        forms = Forms.of(root, vars, this::get);
        Vars mv = vars;
        int width = root.optInt("width", 176);
        int height = root.optInt("height", 96);
        String title = root.optString("title", id);
        Scene.Builder b = Scene.builder("menu:" + id, width, height)
            .title(v -> MM.deserialize(mv.fill(v, title, null, null)));
        if (root.has("exit-label")) {
            b.exitLabel(MM.deserialize(root.string("exit-label")));
        }
        if (root.has("refresh")) {
            b.refresh(refresh(root));
        }
        if (root.has("on-open")) {
            Actions.Chain open = Actions.parse(root.strings("on-open"), root, "on-open", this::get, vars, forms);
            b.onOpen(k -> open.run(k, null));
        }
        if (root.has("on-close")) {
            Actions.Chain close = Actions.parse(root.strings("on-close"), root, "on-close", this::get, vars, forms);
            b.onClose(k -> close.run(k, null));
        }
        Trigger trigger = trigger(root);
        String background = root.optString("background", "panel");
        if (!"none".equalsIgnoreCase(background)) {
            b.add(Panel.of(0, 0, width, height).frame(background));
        }
        templates = new Templates(shared);
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
                element(b::add, n, index++);
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
            root.optString("permission", null), root.optString("description", null), trigger);
    }

    /** "open-on:" with join and item. */
    private static @Nullable Trigger trigger(Node root) throws MenuException {
        Object raw = Templates.plain(root.raw().get("open-on"));
        if (raw == null) {
            return null;
        }
        if (!(raw instanceof Map<?, ?> m)) {
            throw root.error("open-on", "settings expected, e.g. open-on: { join: true, item: compass }");
        }
        Node n = new Node(m, root.path + ".open-on");
        n.only(Set.of("join", "item"));
        boolean join = false;
        boolean first = false;
        String j = n.optString("join", "false").trim().toLowerCase();
        switch (j) {
            case "true", "yes", "always" -> join = true;
            case "first" -> { join = true; first = true; }
            case "false", "no" -> { }
            default -> throw n.error("join", "true, first or false");
        }
        MenuItem item = null;
        Object it = Templates.plain(n.raw().get("item"));
        if (it instanceof String material) {
            item = new MenuItem(material(n, material), null, List.of(), 8, Give.JOIN, true);
        } else if (it instanceof Map<?, ?> im) {
            Node i = new Node(im, n.path + ".item");
            i.only(Set.of("material", "name", "lore", "slot", "give", "keep"));
            int slot = i.optInt("slot", 8);
            if (slot < 0 || slot > 8) {
                throw i.error("slot", "hotbar slot 0 to 8");
            }
            Give give = switch (i.optString("give", "join").trim().toLowerCase()) {
                case "join", "true", "yes" -> Give.JOIN;
                case "first" -> Give.FIRST;
                case "no", "false", "never" -> Give.NO;
                default -> throw i.error("give", "join, first or no");
            };
            item = new MenuItem(material(i, i.string("material")), i.optString("name", null), i.strings("lore"), slot,
                give, i.optBool("keep", true));
        } else if (it != null) {
            throw n.error("item", "a material (item: compass) or settings (material, name, lore, slot, give)");
        }
        return join || item != null ? new Trigger(join, first, item) : null;
    }

    private static String material(Node at, String material) throws MenuException {
        String s = material.trim().toLowerCase();
        if (s.startsWith("minecraft:")) {
            s = s.substring("minecraft:".length());
        }
        if (!s.matches("[a-z0-9_]+")) {
            throw at.error("material", "item id expected (e.g. compass), got '" + material + "'");
        }
        return s;
    }

    /** "refresh: 5" or "2.5" seconds, at least 1. */
    private static java.time.Duration refresh(Node root) throws MenuException {
        String s = root.string("refresh").trim().toLowerCase().replaceFirst("s$", "").trim();
        double seconds;
        try {
            seconds = Double.parseDouble(s);
        } catch (NumberFormatException e) {
            throw root.error("refresh", "seconds expected (e.g. 5), got '" + root.string("refresh") + "'");
        }
        if (!(seconds >= 1)) {
            throw root.error("refresh", "at least 1 second (every redraw sends the whole menu again), got " + s);
        }
        return java.time.Duration.ofMillis(Math.round(seconds * 1000));
    }

    private static String firstLine(@Nullable String s) {
        return s == null ? "" : s.lines().findFirst().orElse("");
    }

    private Function<View, String> text(String template) {
        Vars mv = vars;
        return v -> mv.fill(v, template, null, null);
    }

    private @Nullable Function<View, @Nullable Component> tooltip(Node n) {
        String t = n.optString("tooltip", null);
        Vars mv = vars;
        return t == null ? null : v -> MM.deserialize(mv.fill(v, t, null, null));
    }

    private UnaryOperator<String> fill(View v) {
        Vars mv = vars;
        return s -> mv.fill(v, s, null, null);
    }

    private Consumer<Click> actions(Node n) throws MenuException {
        Actions.Chain run = Actions.parse(n.strings("actions"), n, "actions", this::get, vars, forms);
        String perm = n.optString("permission", null);
        Cooldown cd = cooldown(n);
        String waitText = n.optString("cooldown-message", null);
        Vars cv = vars;
        Consumer<Click> body = c -> {
            if (perm != null && !c.player().hasPermission(perm)) {
                return;
            }
            if (cd != null) {
                long left = cd.left(c.player());
                if (left > 0) {
                    c.player().sendMessage(waitText != null
                        ? MM.deserialize(cv.fill(c, waitText.replace("%cooldown%", formatDuration(left)), null, null))
                        : de.flyingfinger.glyphwork.core.Messages.active().get(c.player(), "cooldown.wait", "time", formatDuration(left)));
                    return;
                }
                cd.start(c.player());              // before the actions, so a redraw already shows it
            }
            if (!run.run(c, null) && cd != null) {
                cd.reset(c.player());              // a "require" stopped the actions: nothing was used up
            }
        };
        // a button on cooldown is grayed out: draw again unless the actions showed something else
        return cd == null ? body : de.flyingfinger.glyphwork.render.Redraw.after(body);
    }

    /** A wait time per player after a click ("cooldown: 1h"), kept in the player's data. */
    record Cooldown(String key, long millis) {

        long left(org.bukkit.entity.@Nullable Player p) {
            long until = PlayerData.getLong(p, "cooldown/" + key);
            return Math.max(0, until - System.currentTimeMillis());
        }

        /**
         * Time left, and asks the view to redraw when the shown time changes: every second in the
         * last minute, at the next minute before, and when the cooldown runs out.
         */
        long left(View v) {
            long left = left(v.player());
            if (left > 0) {
                long next = left <= 60_000 ? (left - 1) % 1000 + 1 : (left - 1) % 60_000 + 1;
                v.redrawAfter(java.time.Duration.ofMillis(next + 50));
            }
            return left;
        }

        void start(org.bukkit.entity.@Nullable Player p) {
            PlayerData.setLong(p, "cooldown/" + key, System.currentTimeMillis() + millis);
        }

        void reset(org.bukkit.entity.@Nullable Player p) {
            PlayerData.setLong(p, "cooldown/" + key, 0);
        }
    }

    /** "cooldown:" of an element (key: "cooldown-key" or menu + element number), or null. */
    private @Nullable Cooldown cooldown(Node n) throws MenuException {
        if (!n.has("cooldown")) {
            if (n.has("cooldown-key")) {
                throw n.error("cooldown-key", "only together with cooldown");
            }
            return null;
        }
        return new Cooldown(n.optString("cooldown-key", menuId + "/" + elementIndex).trim().toLowerCase(),
            parseDuration(n, "cooldown"));
    }

    private static final java.util.regex.Pattern DURATION = java.util.regex.Pattern.compile("(\\d+)\\s*(d|h|min|m|s)?");

    /** "90", "90s", "5m", "5min", "2h", "1d", "1d 12h" -> milliseconds (at least 1 s). */
    static long parseDuration(Node n, String key) throws MenuException {
        String s = n.string(key).trim().toLowerCase();
        java.util.regex.Matcher m = DURATION.matcher(s);
        long total = 0;
        int end = 0;
        while (m.find()) {
            if (!s.substring(end, m.start()).isBlank()) {
                break;
            }
            long v = Long.parseLong(m.group(1));
            String unit = m.group(2) == null ? "s" : m.group(2);
            total += v * switch (unit) {
                case "d" -> 86_400_000L;
                case "h" -> 3_600_000L;
                case "m", "min" -> 60_000L;
                default -> 1000L;
            };
            end = m.end();
        }
        if (end == 0 || !s.substring(end).isBlank() || total < 1000) {
            throw n.error(key, "duration expected, e.g. 30s, 5m, 2h, 1d or 1d 12h; got '" + s + "'");
        }
        return total;
    }

    /** 7 s, 12 min, 3 h 12 min, 1 d 4 h */
    static String formatDuration(long ms) {
        long s = (ms + 999) / 1000;
        if (s < 60) {
            return s + " s";
        }
        long min = (s + 59) / 60;
        if (min < 60) {
            return min + " min";
        }
        long h = min / 60;
        if (h < 24) {
            return h + " h" + (min % 60 > 0 ? " " + min % 60 + " min" : "");
        }
        return h / 24 + " d" + (h % 24 > 0 ? " " + h % 24 + " h" : "");
    }

    /** Keys of an element: its own plus show-if, which every element has. */
    private static Set<String> with(String... keys) {
        Set<String> s = new java.util.HashSet<>(Set.of(keys));
        s.add("show-if");
        return s;
    }

    private void element(Consumer<Element> b, Node n, int index) throws MenuException {
        elementIndex = index;
        String type = n.string("type").toLowerCase();
        List<Condition> showIf = Condition.all(n, "show-if");
        List<Element> added = new ArrayList<>();
        element(added::add, n, index, type);
        Vars mv = vars;
        for (Element e : added) {
            if (!showIf.isEmpty()) {
                show(e, v -> Condition.all(showIf, s -> mv.fill(v, s, null, null)));
            }
            b.accept(e);
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
            case Slider x -> x.visible(visible);
            case Checkbox x -> x.visible(visible);
            case Choice x -> x.visible(visible);
            case Bar x -> x.visible(visible);
            case de.flyingfinger.glyphwork.api.Image x -> x.visible(visible);
            case de.flyingfinger.glyphwork.api.Head x -> x.visible(visible);
            case de.flyingfinger.glyphwork.api.Tabs x -> x.visible(visible);
            case de.flyingfinger.glyphwork.api.Scroll x -> x.visible(visible);
        }
    }

    private void element(Consumer<Element> b, Node n, int index, String type) throws MenuException {
        switch (type) {
            case "label" -> {
                n.only(with("type", "x", "y", "text", "color", "width", "align", "shadow", "wrap", "line-height"));
                Label l = Label.of(n.integer("x"), n.integer("y"), text(n.string("text")))
                    .color(n.optColor("color", Label.DARK))
                    .shadow(n.optBool("shadow", false))
                    .wrap(n.optBool("wrap", false))
                    .lineHeight(n.optInt("line-height", 10));
                if (l.wrap() && !n.has("width")) {
                    throw n.error("wrap", "wrap needs a width (where lines break)");
                }
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
                    "actions", "permission", "frame", "icon", "enabled-if", "disabled-tooltip", "cooldown", "cooldown-key",
                    "cooldown-tooltip", "cooldown-message"));
                Button bt = Button.of(n.integer("x"), n.integer("y"), n.optInt("width", 64), n.optInt("height", 16))
                    .text(text(n.optString("text", "")))
                    .tint(n.optColor("tint", Button.GRAY))
                    .textColor(n.optColor("text-color", TextColor.color(0xFFFFFF)))
                    .frame(n.optString("frame", "button"))
                    .onClick(actions(n));
                String perm = n.optString("permission", null);
                List<Condition> enabledIf = Condition.all(n, "enabled-if");
                Vars mv = vars;
                Cooldown cd = cooldown(n);
                Function<View, Boolean> allowed = v -> (perm == null || v.player().hasPermission(perm))
                    && Condition.all(enabledIf, s -> mv.fill(v, s, null, null));
                Function<View, Boolean> enabled = v -> allowed.apply(v) && (cd == null || cd.left(v) == 0);
                bt.enabled(enabled);
                Function<View, @Nullable Component> tip = tooltip(n);
                String off = n.optString("disabled-tooltip", null);
                String coolTip = n.optString("cooldown-tooltip", null);
                bt.tooltip(v -> {
                    long left = cd == null ? 0 : cd.left(v);
                    if (left > 0 && allowed.apply(v)) {
                        return coolTip != null
                            ? MM.deserialize(mv.fill(v, coolTip.replace("%cooldown%", formatDuration(left)), null, null))
                            : de.flyingfinger.glyphwork.core.Messages.active().get(v.player(), "cooldown.tooltip",
                                "time", formatDuration(left));
                    }
                    if (off != null && !allowed.apply(v)) {
                        return MM.deserialize(mv.fill(v, off, null, null));
                    }
                    return tip == null ? null : tip.apply(v);
                });
                if (n.has("icon")) {
                    bt.icon(text(n.string("icon")));        // placeholders: icon: "head:%player%"
                }
                b.accept(bt);
            }
            case "panel" -> {
                n.only(with("type", "x", "y", "width", "height", "shape", "frame", "tint", "tooltip", "actions", "cooldown",
                    "cooldown-key", "cooldown-message"));
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
                n.only(with("type", "x", "y", "width", "height", "tooltip", "actions", "permission", "cooldown", "cooldown-key", "cooldown-message"));
                Hitbox h = Hitbox.of(n.integer("x"), n.integer("y"), n.integer("width"), n.integer("height"))
                    .onClick(actions(n));
                if (tooltip(n) != null) {
                    h.tooltip(tooltip(n));
                }
                b.accept(h);
            }
            case "icon" -> {
                n.only(with("type", "x", "y", "item", "tint", "tooltip", "actions", "count", "item-tooltip", "cooldown",
                    "cooldown-key", "cooldown-message"));
                TextColor tint = n.optColor("tint", null);
                Icon ic = Icon.of(n.integer("x"), n.integer("y"), text(n.string("item"))).tint(v -> tint);
                if (n.has("count")) {
                    ic.count(text(n.string("count")));
                }
                ic.itemTooltip(n.optBool("item-tooltip", false));
                if (tooltip(n) != null) {
                    ic.tooltip(tooltip(n));
                }
                if (n.has("actions")) {
                    ic.onClick(actions(n));
                }
                b.accept(ic);
            }
            case "list" -> {
                n.only(with("type", "id", "x", "y", "width", "rows", "row-height", "items", "items-from", "item", "item-tooltip",
                    "head-size"));
                ListView<Row> l = ListView.of(n.optString("id", "list" + index), n.integer("x"), n.integer("y"),
                        n.optInt("width", 120), n.optInt("rows", 4), rows(n))
                    // lists of players show heads, which need rows of 9 or 18 px to look even
                    .rowHeight(n.optInt("row-height", "online-players".equalsIgnoreCase(n.optString("items-from", "")) ? 18 : 16))
                    .text(e -> e.text)
                    .color(e -> e.color)
                    .tooltip(e -> e.tooltip)
                    .icon(e -> e.icon)
                    .count(e -> e.count)
                    .itemTooltip(n.optBool("item-tooltip", false))
                    .headSize(n.optInt("head-size", 8))
                    .selectedIf((v, e) -> e.selected)
                    .onSelect((c, e) -> e.onClick.accept(c));
                b.accept(l);
            }
            case "grid" -> {
                n.only(with("type", "id", "x", "y", "cols", "rows", "cell", "size", "frame", "items", "items-from", "item",
                    "item-tooltip", "head-size"));
                Grid<Row> g = Grid.of(n.optString("id", "grid" + index), n.integer("x"), n.integer("y"),
                        n.optInt("cols", 4), n.optInt("rows", 2), rows(n))
                    .cell(n.optInt("cell", 18), n.optInt("size", 16))
                    .label(e -> e.text)
                    .labelColor(e -> e.color)
                    .tint(e -> e.tint)
                    .tooltip(e -> e.tooltip)
                    .icon(e -> e.icon)
                    .count(e -> e.count)
                    .itemTooltip(n.optBool("item-tooltip", false))
                    .headSize(n.optInt("head-size", 8))
                    .frame(n.optString("frame", "slot"))
                    .selectedIf((v, e) -> e.selected)
                    .onClick((c, e) -> e.onClick.accept(c));
                b.accept(g);
            }
            case "slider" -> {
                n.only(with("type", "x", "y", "width", "min", "max", "step", "var", "tint", "tooltip", "actions"));
                String var = varName(n);
                float min = floatOf(n, "min", 0);
                float max = floatOf(n, "max", 10);
                float step = floatOf(n, "step", 1);
                if (!(max > min) || !(step > 0)) {
                    throw n.error("max", "slider needs min < max and step > 0");
                }
                Vars mv = vars;
                Actions.Chain then = Actions.parse(n.strings("actions"), n, "actions", this::get, vars, forms);
                String tip = n.optString("tooltip", null);
                TextColor tint = n.optColor("tint", Button.GRAY);
                Slider s = Slider.of(n.integer("x"), n.integer("y"), n.optInt("width", 100), min, max, step)
                    .value(v -> {
                        try {
                            return Float.parseFloat(mv.get(v, var).trim());
                        } catch (NumberFormatException e) {
                            return min;
                        }
                    })
                    .tint(v -> tint)
                    .onChange((c, value) -> {
                        mv.set(c, var, de.flyingfinger.glyphwork.dialog.FormValues.format(value));
                        then.run(c, null);
                    });
                if (tip != null) {
                    s.tooltip((v, value) -> MM.deserialize(mv.fill(v,
                        tip.replace("%value%", de.flyingfinger.glyphwork.dialog.FormValues.format(value)), null, null)));
                }
                b.accept(s);
            }
            case "checkbox" -> {
                n.only(with("type", "x", "y", "var", "text", "color", "tooltip", "actions"));
                String var = varName(n);
                Vars mv = vars;
                Actions.Chain then = Actions.parse(n.strings("actions"), n, "actions", this::get, vars, forms);
                TextColor color = n.optColor("color", Label.DARK);
                Checkbox cb = Checkbox.of(n.integer("x"), n.integer("y"))
                    .text(text(n.optString("text", "")))
                    .textColor(v -> color)
                    .checked(v -> "true".equalsIgnoreCase(mv.get(v, var).trim()))
                    .onToggle((c, on) -> {
                        mv.set(c, var, String.valueOf(on));
                        then.run(c, null);
                    });
                if (tooltip(n) != null) {
                    cb.tooltip(tooltip(n));
                }
                b.accept(cb);
            }
            case "choice" -> {
                n.only(with("type", "x", "y", "width", "height", "var", "options", "tint", "tooltip", "actions"));
                String var = varName(n);
                Map<String, String> options = options(n);
                Vars mv = vars;
                Actions.Chain then = Actions.parse(n.strings("actions"), n, "actions", this::get, vars, forms);
                TextColor tint = n.optColor("tint", Button.GRAY);
                Choice ch = Choice.of(n.integer("x"), n.integer("y"), n.optInt("width", 80), List.copyOf(options.keySet()))
                    .height(n.optInt("height", 16))
                    // pixel text has no formatting: "<green>Easy" shows as "Easy"
                    .text(id -> net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer.plainText()
                        .serialize(MM.deserialize(options.getOrDefault(id, id))))
                    .selected(v -> mv.get(v, var).trim())
                    .tint(v -> tint)
                    .onSelect((c, id) -> {
                        mv.set(c, var, id);
                        then.run(c, null);
                    });
                if (tooltip(n) != null) {
                    ch.tooltip(tooltip(n));
                }
                b.accept(ch);
            }
            case "head" -> {
                n.only(with("type", "x", "y", "player", "size", "tooltip", "actions", "cooldown", "cooldown-key", "cooldown-message"));
                int size = n.optInt("size", 32);
                if (size % 8 != 0 || size < 8 || size > 32) {
                    throw n.error("size", "8, 16, 24 or 32");
                }
                de.flyingfinger.glyphwork.api.Head hd = de.flyingfinger.glyphwork.api.Head.of(n.integer("x"), n.integer("y"),
                    text(n.optString("player", "%player%"))).size(size);
                if (tooltip(n) != null) {
                    hd.tooltip(tooltip(n));
                }
                if (n.has("actions")) {
                    hd.onClick(actions(n));
                }
                b.accept(hd);
            }
            case "image" -> {
                n.only(with("type", "x", "y", "image", "tint", "tooltip", "actions", "cooldown", "cooldown-key", "cooldown-message"));
                TextColor tint = n.optColor("tint", null);
                de.flyingfinger.glyphwork.api.Image im = de.flyingfinger.glyphwork.api.Image.of(n.integer("x"), n.integer("y"),
                    text(n.string("image"))).tint(v -> tint);   // placeholders: image: "ranks/%menu_rank%"
                if (tooltip(n) != null) {
                    im.tooltip(tooltip(n));
                }
                if (n.has("actions")) {
                    im.onClick(actions(n));
                }
                b.accept(im);
            }
            case "scroll" -> {
                n.only(with("type", "id", "x", "y", "width", "height", "step", "frame", "elements"));
                if (inScroll) {
                    throw n.error("type", "a scroll pane cannot be inside another scroll pane");
                }
                int x = n.integer("x");
                int y = n.integer("y");
                List<Element> inner = new ArrayList<>();
                int saved = elementIndex;
                inScroll = true;
                try {
                    int ci = 0;
                    for (Node child : n.nodes("elements")) {
                        for (Node c : templates.expand(child)) {
                            // positions inside the pane are relative to its top left corner
                            element(inner::add, shifted(c, x, y), index * 1000 + ci++);
                        }
                    }
                } finally {
                    inScroll = false;
                }
                elementIndex = saved;
                int step = n.optInt("step", 18);
                if (step < 1) {
                    throw n.error("step", "at least 1 px expected");
                }
                String id = n.optString("id", "scroll" + index);
                de.flyingfinger.glyphwork.api.Scroll sc = de.flyingfinger.glyphwork.api.Scroll.of(id, x, y,
                        n.optInt("width", 120), n.optInt("height", 80), inner)
                    .step(step).frame(n.optString("frame", null));
                if (id.indexOf('%') >= 0) {
                    // e.g. "detail-%menu_link%": every article keeps its own position and starts at the top
                    Vars mv = vars;
                    sc.position(v -> mv.fill(v, id, null, null));
                }
                b.accept(sc);
            }
            case "tabs" -> {
                n.only(with("type", "id", "x", "y", "width", "height", "var", "frame", "tint", "tabs"));
                int x = n.integer("x");
                int y = n.integer("y");
                de.flyingfinger.glyphwork.api.Tabs t = de.flyingfinger.glyphwork.api.Tabs.of(n.optString("id", "tabs" + index),
                        x, y, n.optInt("width", 160), n.optInt("height", 100))
                    .frame(n.optString("frame", "panel"))
                    .tint(n.optColor("tint", de.flyingfinger.glyphwork.api.Tabs.INACTIVE));
                String var = n.has("var") ? varName(n) : null;
                if (var != null) {
                    Vars mv = vars;
                    t.selected(v -> mv.get(v, var).trim()).onSelect((c, id) -> mv.set(c, var, id));
                }
                java.util.Set<String> ids = new java.util.HashSet<>();
                List<Node> tabNodes = n.nodes("tabs");
                if (tabNodes.isEmpty()) {
                    throw n.error("tabs", "list of tabs expected ({ id, title, elements })");
                }
                int saved = elementIndex;
                for (int ti = 0; ti < tabNodes.size(); ti++) {
                    Node tn = tabNodes.get(ti);
                    tn.only(Set.of("id", "title", "icon", "elements"));
                    String id = tn.optString("id", "tab" + (ti + 1)).trim().toLowerCase();
                    if (!ids.add(id)) {
                        throw tn.error("id", "tab id '" + id + "' is used twice");
                    }
                    List<Element> inner = new ArrayList<>();
                    int ci = 0;
                    for (Node child : tn.nodes("elements")) {
                        for (Node c : templates.expand(child)) {
                            // positions inside a tab are relative to the panel below the tab buttons
                            element(inner::add, shifted(c, x, y + de.flyingfinger.glyphwork.api.Tabs.HEADER),
                                index * 1000 + ti * 100 + ci++);
                        }
                    }
                    t.tab(id, tn.optString("title", id), tn.optString("icon", null), inner);
                }
                elementIndex = saved;
                b.accept(t);
            }
            case "bar" -> {
                n.only(with("type", "x", "y", "width", "height", "value", "max", "color", "text", "tooltip", "frame"));
                int h = n.optInt("height", 8);
                if (h != 8 && h != 16) {
                    throw n.error("height", "8 or 16");
                }
                Function<View, String> value = text(n.string("value"));
                Function<View, String> max = text(n.optString("max", "100"));
                TextColor color = n.optColor("color", Bar.GREEN);
                Bar bar = Bar.of(n.integer("x"), n.integer("y"), n.optInt("width", 100))
                    .height(h)
                    .value(v -> number(value.apply(v)))
                    .max(v -> number(max.apply(v)))
                    .color(v -> color)
                    .text(text(n.optString("text", "")))
                    .frame(n.optString("frame", "slot"));
                if (tooltip(n) != null) {
                    bar.tooltip(tooltip(n));
                }
                b.accept(bar);
            }
            default -> throw n.error("type", "unknown type '" + type
                + "' (label, button, panel, hitbox, icon, list, grid, slider, checkbox, choice, bar, image, head, tabs, scroll)");
        }
    }

    /** A number from a filled-in placeholder ("12", "1,234.5"), 0 if it is none. */
    private static double number(@Nullable String s) {
        Double d = s == null ? null : Condition.number(s.trim());
        return d == null ? 0 : d;
    }

    /** A copy of an element node moved by (dx, dy) (its x and y default to 0). */
    private static Node shifted(Node n, int dx, int dy) throws MenuException {
        Map<String, Object> m = new LinkedHashMap<>();
        n.raw().forEach((k, v) -> m.put(String.valueOf(k), v));
        m.put("x", n.optInt("x", 0) + dx);
        m.put("y", n.optInt("y", 0) + dy);
        return new Node(m, n.path);
    }

    /** The menu variable an input element writes ("var: sichtweite"). */
    private static String varName(Node n) throws MenuException {
        String var = n.string("var").trim().toLowerCase();
        if (!Vars.NAME.matcher(var).matches()) {
            throw n.error("var", "variable name expected (a-z, 0-9, '_', '-'), got '" + var + "'");
        }
        return var;
    }

    private static float floatOf(Node n, String key, float fallback) throws MenuException {
        String s = n.optString(key, null);
        if (s == null) {
            return fallback;
        }
        try {
            return Float.parseFloat(s.trim());
        } catch (NumberFormatException e) {
            throw n.error(key, "number expected, got '" + s + "'");
        }
    }

    /** "options: [easy, hard]" or "options: { easy: Easy, hard: <red>Hard }" (value: shown text). */
    private static Map<String, String> options(Node n) throws MenuException {
        Map<String, String> options = new LinkedHashMap<>();
        Object o = Templates.plain(n.raw().get("options"));
        if (o instanceof List<?> list) {
            for (Object x : list) {
                options.put(String.valueOf(x), String.valueOf(x));
            }
        } else if (o instanceof Map<?, ?> map) {
            for (Map.Entry<?, ?> x : map.entrySet()) {
                options.put(String.valueOf(x.getKey()), String.valueOf(x.getValue()));
            }
        }
        if (options.isEmpty()) {
            throw n.error("options", "list of options expected ([easy, normal]) or value: text pairs");
        }
        return options;
    }

    /** One configured entry of a list or grid: fixed (items:) or the template of a dynamic list (item:). */
    private record ItemDef(String text, TextColor color, @Nullable TextColor tint, @Nullable String tooltip,
                           Actions.Chain actions, @Nullable String permission, @Nullable String icon,
                           @Nullable String count, List<Condition> showIf, List<Condition> selectedIf) {
    }

    /** One row or cell as one viewer sees it. */
    private record Row(String text, TextColor color, @Nullable TextColor tint, @Nullable Component tooltip,
                       @Nullable String icon, @Nullable String count, Consumer<Click> onClick, boolean selected) {
    }

    private static final java.util.regex.Pattern ITEM_KEY = java.util.regex.Pattern.compile("%item(?:_([a-z0-9_-]+))?%");

    private ItemDef itemDef(Node e, String defaultText) throws MenuException {
        e.only(Set.of("text", "color", "tint", "tooltip", "actions", "permission", "icon", "count", "show-if", "selected-if"));
        return new ItemDef(e.optString("text", defaultText), e.optColor("color", TextColor.color(0xFFFFFF)),
            e.optColor("tint", null), e.optString("tooltip", null),
            Actions.parse(e.strings("actions"), e, "actions", this::get, vars, forms),
            e.optString("permission", null), e.optString("icon", null), e.optString("count", null),
            Condition.all(e, "show-if"), Condition.all(e, "selected-if"));
    }

    /** Fixed items, then the entries of items-from, filtered and filled in for the viewer. */
    private Function<View, List<Row>> rows(Node n) throws MenuException {
        List<ItemDef> fixed = new ArrayList<>();
        for (Node e : n.nodes("items")) {
            fixed.add(itemDef(e, ""));
        }
        String from = n.optString("items-from", null);
        ItemDef dynamic = null;
        if (from != null) {
            from = from.trim();
            boolean feed = from.regionMatches(true, 0, "rss:", 0, 4);
            from = feed ? "rss:" + from.substring(4).trim() : from.toLowerCase();
            if (feed && Feeds.problem(from.substring(4)) != null) {
                throw n.error("items-from", "feed " + from.substring(4) + ": " + Feeds.problem(from.substring(4)));
            }
            Set<String> keys = feed ? Feeds.KEYS : Sources.KEYS.get(from);
            if (keys == null) {
                throw n.error("items-from", "unknown source '" + from + "' (" + String.join(", ",
                    new java.util.TreeSet<>(Sources.KEYS.keySet())) + ", rss:<address>)");
            }
            Object raw = Templates.plain(n.raw().get("item"));
            if (raw != null && !(raw instanceof Map<?, ?>)) {
                throw n.error("item", "settings of each entry expected (text, icon, tooltip, actions, show-if ...)");
            }
            Node item = new Node(raw == null ? Map.of() : (Map<?, ?>) raw, n.path + ".item");
            checkItemKeys(item, from, keys);
            dynamic = itemDef(item, "%item%");
        } else if (n.has("item")) {
            throw n.error("item", "only together with items-from");
        }
        Vars mv = vars;
        Function<String, List<Map<String, String>>> src = sources;
        Function<String, Feeds.Result> fd = feeds;
        String source = from;
        ItemDef d = dynamic;
        return v -> {
            List<Row> out = new ArrayList<>();
            for (ItemDef def : fixed) {
                row(def, null, v, mv, out);
            }
            if (d != null) {
                List<Map<String, String>> entries;
                if (source.startsWith("rss:")) {
                    Feeds.Result r = fd.apply(source.substring(4));
                    if (r.loading()) {
                        v.redrawAfter(java.time.Duration.ofMillis(500));      // first load: draw again when it is there
                    }
                    entries = r.items();
                } else {
                    entries = src.apply(source);
                }
                for (Map<String, String> item : entries) {
                    row(d, item, v, mv, out);
                }
            }
            return out;
        };
    }

    /** Catches %item_wrold% and similar typos when the menu is loaded. */
    private static void checkItemKeys(Node item, String source, Set<String> keys) throws MenuException {
        java.util.regex.Matcher m = ITEM_KEY.matcher(String.valueOf(item.raw()));
        while (m.find()) {
            String k = m.group(1) == null ? "" : m.group(1);
            if (!keys.contains(k)) {
                throw item.error("", "%item_" + k + "% does not exist for " + source + " (there are: %item%"
                    + keys.stream().filter(x -> !x.isEmpty()).sorted().map(x -> ", %item_" + x + "%").reduce("", String::concat) + ")");
            }
        }
    }

    private static void row(ItemDef d, @Nullable Map<String, String> item, View v, Vars mv, List<Row> out) {
        if (d.permission() != null && v.player() != null && !v.player().hasPermission(d.permission())) {
            return;
        }
        UnaryOperator<String> fill = s -> mv.fill(v, s, item, null);
        if (!Condition.all(d.showIf(), fill)) {
            return;
        }
        String icon = d.icon() == null ? null : fill.apply(d.icon()).trim();
        out.add(new Row(fill.apply(d.text()), d.color(), d.tint(),
            d.tooltip() == null ? null : MM.deserialize(fill.apply(d.tooltip())),
            icon == null || icon.isEmpty() ? null : icon,
            d.count() == null ? null : fill.apply(d.count()),
            c -> d.actions().run(c, item),
            !d.selectedIf().isEmpty() && Condition.all(d.selectedIf(), fill)));
    }
}
