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
    private static final Pattern ITEM_VAR = Pattern.compile("%item(?:_([a-z0-9_-]+))?%");
    private static final String KEY = "menu:var:";

    /** No variables (tests, menus without a "variables:" section). */
    static final Vars NONE = new Vars(Map.of());

    private final Map<String, String> defaults;

    Vars(Map<String, String> defaults) {
        this.defaults = new LinkedHashMap<>(defaults);
    }

    static Vars of(Node root) throws MenuException {
        Map<String, String> d = new LinkedHashMap<>();
        Object raw = Templates.plain(root.raw().get("variables"));
        if (raw == null) {
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
        return new Vars(d);
    }

    String get(@Nullable View v, String name) {
        String value = v == null ? null : v.get(KEY + name, (String) null);
        return value != null ? value : defaults.getOrDefault(name, "");
    }

    /** {@code value} null = back to the default. */
    static void set(View v, String name, @Nullable String value) {
        v.set(KEY + name, value);
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
