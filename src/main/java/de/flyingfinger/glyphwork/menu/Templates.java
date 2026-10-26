package de.flyingfinger.glyphwork.menu;

import org.bukkit.configuration.ConfigurationSection;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Menu templates: reusable elements or groups of elements, filled with values.
 *
 * <pre>
 * # menus/_vorlagen.yml (files starting with _ hold templates, they are no menus)
 * warp-button:
 *   type: button
 *   width: 72
 *   text: "{name}"
 *   tint: "{farbe|gray}"            # {key|default}
 *   actions: ["player: warp {ziel}", "close"]
 *
 * # in a menu
 * elements:
 *   - { use: warp-button, x: 8, y: 24, name: Spawn, ziel: spawn }
 * </pre>
 *
 * A template is one element or a list of elements (a group). {@code x}/{@code y} of the {@code use}
 * entry move the whole template (its own positions default to 0); {@code show-if} applies to every
 * element of it; the other keys are values for {@code {key}} in the template's texts. A key the
 * template does not use as a value replaces that setting of a one-element template
 * ({@code width: 100}, {@code enabled-if: ...}); groups only take values.
 */
final class Templates {

    private static final Pattern VAR = Pattern.compile("\\{([a-zA-Z0-9_-]+)(?:\\|([^}]*))?}");
    private static final int MAX_DEPTH = 8;

    private final Map<String, Object> templates = new LinkedHashMap<>();

    Templates() {
    }

    Templates(Templates shared) {
        templates.putAll(shared.templates);
    }

    boolean isEmpty() {
        return templates.isEmpty();
    }

    /** Adds the templates of one file or of a menu's "templates:" section (later ones replace earlier ones). */
    void addAll(Map<?, ?> section) {
        for (Map.Entry<?, ?> e : section.entrySet()) {
            templates.put(String.valueOf(e.getKey()).toLowerCase(), plain(e.getValue()));
        }
    }

    /** Bukkit's YAML gives nested maps as sections; templates are copied as plain maps and lists. */
    static Object plain(Object v) {
        if (v instanceof ConfigurationSection s) {
            Map<String, Object> m = new LinkedHashMap<>();
            for (String k : s.getKeys(false)) {
                m.put(k, plain(s.get(k)));
            }
            return m;
        }
        if (v instanceof Map<?, ?> map) {
            Map<String, Object> m = new LinkedHashMap<>();
            map.forEach((k, x) -> m.put(String.valueOf(k), plain(x)));
            return m;
        }
        if (v instanceof List<?> list) {
            List<Object> l = new ArrayList<>();
            list.forEach(x -> l.add(plain(x)));
            return l;
        }
        return v;
    }

    /** The element itself, or the elements of the template it uses (recursively). */
    List<Node> expand(Node n) throws MenuException {
        List<Node> out = new ArrayList<>();
        expand(n, 0, 0, List.of(), 0, out);
        return out;
    }

    private void expand(Node n, int dx, int dy, List<String> showIf, int depth, List<Node> out) throws MenuException {
        if (!n.has("use")) {
            Map<String, Object> m = new LinkedHashMap<>();
            n.raw().forEach((k, v) -> m.put(String.valueOf(k), v));
            if (dx != 0 || dy != 0) {
                m.put("x", n.optInt("x", 0) + dx);
                m.put("y", n.optInt("y", 0) + dy);
            }
            if (!showIf.isEmpty()) {
                List<String> all = new ArrayList<>(showIf);
                all.addAll(n.strings("show-if"));
                m.put("show-if", all);
            }
            out.add(new Node(m, n.path));
            return;
        }
        String name = n.string("use").toLowerCase();
        if (depth >= MAX_DEPTH) {
            throw n.error("use", "templates nested too deep (does '" + name + "' use itself?)");
        }
        Object t = templates.get(name);
        if (t == null) {
            throw n.error("use", "unknown template '" + name + "'"
                + (templates.isEmpty() ? " (no templates: put them into menus/_*.yml or under templates:)"
                : " (known: " + String.join(", ", templates.keySet()) + ")"));
        }
        Map<String, String> values = new LinkedHashMap<>();
        for (Map.Entry<?, ?> e : n.raw().entrySet()) {
            String k = String.valueOf(e.getKey());
            if (!Set.of("use", "x", "y", "show-if").contains(k) && e.getValue() != null) {
                values.put(k, String.valueOf(e.getValue()));
            }
        }
        String where = n.path + " (template " + name + ")";
        Set<String> used = new LinkedHashSet<>();
        Object filled = fill(t, values, used, n, where);
        Set<String> unused = new LinkedHashSet<>(values.keySet());
        unused.removeAll(used);
        if (filled instanceof Map<?, ?> single && !unused.isEmpty()) {
            // settings of the use entry replace the template's (checked like any element setting)
            Map<String, Object> m = new LinkedHashMap<>();
            single.forEach((k, v) -> m.put(String.valueOf(k), v));
            for (String k : unused) {
                m.put(k, n.raw().get(k));
            }
            filled = m;
            unused.clear();
        }
        if (!unused.isEmpty()) {
            Set<String> known = new LinkedHashSet<>();
            names(t, known);
            throw n.error(unused.iterator().next(), "template '" + name + "' is a group and has no value '{"
                + unused.iterator().next() + "}' (it uses: " + (known.isEmpty() ? "none" : String.join(", ", known)) + ")");
        }
        List<String> cond = new ArrayList<>(showIf);
        cond.addAll(n.strings("show-if"));
        int x = dx + n.optInt("x", 0);
        int y = dy + n.optInt("y", 0);
        if (filled instanceof Map<?, ?> one) {
            expand(new Node(one, where), x, y, cond, depth + 1, out);
        } else if (filled instanceof List<?> group) {
            for (int i = 0; i < group.size(); i++) {
                if (!(group.get(i) instanceof Map<?, ?> el)) {
                    throw new MenuException(where + "[" + i + "]: element with settings expected");
                }
                expand(new Node(el, where + "[" + i + "]"), x, y, cond, depth + 1, out);
            }
        } else {
            throw new MenuException(where + ": a template is an element (with type:) or a list of elements");
        }
    }

    /** Copies {@code v} with every {key} / {key|default} replaced. */
    private static Object fill(Object v, Map<String, String> values, Set<String> used, Node at, String where)
        throws MenuException {
        if (v instanceof String s) {
            Matcher m = VAR.matcher(s);
            StringBuilder sb = new StringBuilder();
            while (m.find()) {
                String key = m.group(1);
                String value = values.get(key);
                if (value != null) {
                    used.add(key);
                } else if (m.group(2) != null) {
                    value = m.group(2);
                } else {
                    throw new MenuException(where + ": value '" + key + "' missing (add " + key + ": ... to the use entry"
                        + " at " + at.path + ", or give a default like {" + key + "|...})");
                }
                m.appendReplacement(sb, Matcher.quoteReplacement(value));
            }
            m.appendTail(sb);
            return sb.toString();
        }
        if (v instanceof Map<?, ?> map) {
            Map<String, Object> out = new LinkedHashMap<>();
            for (Map.Entry<?, ?> e : map.entrySet()) {
                out.put(String.valueOf(e.getKey()), fill(e.getValue(), values, used, at, where));
            }
            return out;
        }
        if (v instanceof List<?> list) {
            List<Object> out = new ArrayList<>();
            for (Object o : list) {
                out.add(fill(o, values, used, at, where));
            }
            return out;
        }
        return v;
    }

    private static void names(Object v, Set<String> out) {
        if (v instanceof String s) {
            Matcher m = VAR.matcher(s);
            while (m.find()) {
                out.add(m.group(1));
            }
        } else if (v instanceof Map<?, ?> map) {
            map.values().forEach(x -> names(x, out));
        } else if (v instanceof List<?> list) {
            list.forEach(x -> names(x, out));
        }
    }
}
