package de.flyingfinger.glyphwork.menu;

import de.flyingfinger.glyphwork.api.View;
import org.jspecify.annotations.Nullable;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Fills a menu text for one viewer, in this order: {@code %input%} (inside input actions), the
 * entry of a dynamic list ({@code %item%}, {@code %item_world%} ...), menu variables
 * ({@code %menu_tab%}), then {@code %player%}, {@code %world%}, {@code %online%} and PlaceholderAPI.
 *
 * <p>Menu variables live in the viewer's state of this menu (set by the {@code set}/{@code toggle}
 * actions) and keep their value until the player leaves; {@code variables:} gives the defaults.
 */
final class Vars {

    static final Pattern NAME = Pattern.compile("[a-z0-9_-]+");
    private static final Pattern MENU_VAR = Pattern.compile("%menu_([a-z0-9_-]+)%");
    private static final Pattern ITEM_VAR = Pattern.compile("%item(?:_([A-Za-z0-9_.-]+))?%");
    private static final String KEY = "menu:var:";

    /** No variables (tests, menus without a "variables:" section). */
    static final Vars NONE = new Vars(Map.of(), "", java.util.Set.of());

    private final Map<String, String> defaults;
    /** Menu id (for the keys of saved variables). */
    private final String menu;
    /** Variables kept in the player's data ("persist:"), surviving logouts and restarts. */
    private final java.util.Set<String> persist;

    Vars(Map<String, String> defaults) {
        this(defaults, "", java.util.Set.of());
    }

    Vars(Map<String, String> defaults, String menu, java.util.Set<String> persist) {
        this.defaults = new LinkedHashMap<>(defaults);
        this.menu = menu;
        this.persist = java.util.Set.copyOf(persist);
    }

    static Vars of(Node root) throws MenuException {
        return of(root, "");
    }

    /** "variables:" with their defaults and "persist:" (true = all, or a list of names). */
    static Vars of(Node root, String menu) throws MenuException {
        Map<String, String> d = new LinkedHashMap<>();
        Object raw = Templates.plain(root.raw().get("variables"));
        if (raw == null) {
            if (root.has("persist")) {
                throw root.error("persist", "there are no variables: to keep");
            }
            return new Vars(d);
        }
        if (!(raw instanceof Map<?, ?> m)) {
            throw root.error("variables", "map expected, e.g. variables: { tab: shop }");
        }
        for (Map.Entry<?, ?> e : m.entrySet()) {
            String name = String.valueOf(e.getKey()).toLowerCase();
            if (!NAME.matcher(name).matches()) {
                throw root.error("variables." + e.getKey(), "variable names use a-z, 0-9, '_' and '-'");
            }
            d.put(name, e.getValue() == null ? "" : String.valueOf(e.getValue()));
        }
        java.util.Set<String> persist = new java.util.LinkedHashSet<>();
        Object p = Templates.plain(root.raw().get("persist"));
        if (p instanceof Boolean b || "true".equalsIgnoreCase(String.valueOf(p)) || "false".equalsIgnoreCase(String.valueOf(p))) {
            if (Boolean.parseBoolean(String.valueOf(p))) {
                persist.addAll(d.keySet());
            }
        } else if (p != null) {
            for (String name : root.strings("persist")) {
                String n = name.trim().toLowerCase();
                if (!d.containsKey(n)) {
                    throw root.error("persist", "'" + n + "' is not under variables: (there are: " + String.join(", ", d.keySet()) + ")");
                }
                persist.add(n);
            }
        }
        return new Vars(d, menu, persist);
    }

    private String dataKey(String name) {
        return "var/" + menu + "/" + name;
    }

    String get(@Nullable View v, String name) {
        String value = v == null ? null : v.get(KEY + name, (String) null);
        if (value == null && v != null && persist.contains(name)) {
            value = PlayerData.get(v.player(), dataKey(name));
        }
        return value != null ? value : defaults.getOrDefault(name, "");
    }

    /** {@code value} null = back to the default (also for saved variables). */
    void set(View v, String name, @Nullable String value) {
        v.set(KEY + name, value);
        if (persist.contains(name)) {
            PlayerData.set(v.player(), dataKey(name), value);
        }
    }

    String fill(@Nullable View v, String text, @Nullable Map<String, String> item, @Nullable String input) {
        if (text.indexOf('%') < 0) {
            return text;
        }
        String s = input == null ? text : text.replace("%input%", input);
        if (item != null) {
            s = replace(s, ITEM_VAR, m -> {
                String value = item.get(m.group(1) == null ? "" : m.group(1));
                return value != null ? value : m.group();
            });
        }
        s = replace(s, MENU_VAR, m -> get(v, m.group(1)));
        s = WebSources.fill(v, s);
        return Placeholders.apply(v == null ? null : v.player(), s);
    }

    private static String replace(String s, Pattern p, java.util.function.Function<Matcher, String> f) {
        Matcher m = p.matcher(s);
        StringBuilder sb = new StringBuilder();
        while (m.find()) {
            m.appendReplacement(sb, Matcher.quoteReplacement(f.apply(m)));
        }
        m.appendTail(sb);
        return sb.toString();
    }
}
