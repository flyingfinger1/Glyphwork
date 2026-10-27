package de.flyingfinger.glyphwork.menu;

import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Typed, path-aware access to one YAML map, so errors say where they are. */
final class Node {

    private final Map<?, ?> map;
    final String path;

    Node(Map<?, ?> map, String path) {
        this.map = map;
        this.path = path;
    }

    MenuException error(String key, String message) {
        return new MenuException(path + (key.isEmpty() ? "" : "." + key) + ": " + message);
    }

    /** The map itself (for templates, which copy and fill it). */
    Map<?, ?> raw() {
        return map;
    }

    boolean has(String key) {
        return map.containsKey(key) && map.get(key) != null;
    }

    /** Fails on keys that are not in {@code allowed} (catches typos like "widht"). */
    void only(Set<String> allowed) throws MenuException {
        for (Object k : map.keySet()) {
            if (!allowed.contains(String.valueOf(k))) {
                throw error(String.valueOf(k), "unknown setting (allowed: " + String.join(", ", allowed.stream().sorted().toList()) + ")");
            }
        }
    }

    String string(String key) throws MenuException {
        String s = optString(key, null);
        if (s == null) {
            throw error(key, "missing");
        }
        return s;
    }

    @Nullable String optString(String key, @Nullable String fallback) {
        Object v = map.get(key);
        return v == null ? fallback : String.valueOf(v);
    }

    int integer(String key) throws MenuException {
        if (!has(key)) {
            throw error(key, "missing");
        }
        return optInt(key, 0);
    }

    boolean optBool(String key, boolean fallback) throws MenuException {
        Object v = map.get(key);
        if (v == null) {
            return fallback;
        }
        if (v instanceof Boolean b) {
            return b;
        }
        String t = String.valueOf(v).trim().toLowerCase();
        if (t.equals("true") || t.equals("yes")) {
            return true;
        }
        if (t.equals("false") || t.equals("no")) {
            return false;
        }
        throw error(key, "true or false expected, got '" + v + "'");
    }

        int optInt(String key, int fallback) throws MenuException {
        Object v = map.get(key);
        if (v == null) {
            return fallback;
        }
        if (v instanceof Number n) {
            return n.intValue();
        }
        try {
            return Integer.parseInt(String.valueOf(v).trim());
        } catch (NumberFormatException e) {
            throw error(key, "number expected, got '" + v + "'");
        }
    }

    @Nullable TextColor optColor(String key, @Nullable TextColor fallback) throws MenuException {
        String s = optString(key, null);
        if (s == null) {
            return fallback;
        }
        return color(s.trim(), key);
    }

    TextColor color(String s, String key) throws MenuException {
        TextColor c = s.startsWith("#") ? TextColor.fromHexString(s) : NamedTextColor.NAMES.value(s.toLowerCase());
        if (c == null) {
            throw error(key, "color expected (\"#RRGGBB\" or a name like gold), got '" + s + "'");
        }
        return c;
    }

    List<String> strings(String key) throws MenuException {
        Object v = map.get(key);
        if (v == null) {
            return List.of();
        }
        if (v instanceof List<?> list) {
            List<String> out = new ArrayList<>();
            for (Object o : list) {
                out.add(String.valueOf(o));
            }
            return out;
        }
        if (v instanceof String s) {
            return List.of(s);
        }
        throw error(key, "list expected");
    }

    List<Node> nodes(String key) throws MenuException {
        Object v = map.get(key);
        if (v == null) {
            return List.of();
        }
        if (!(v instanceof List<?> list)) {
            throw error(key, "list expected");
        }
        List<Node> out = new ArrayList<>();
        for (int i = 0; i < list.size(); i++) {
            Object o = list.get(i);
            if (!(o instanceof Map<?, ?> m)) {
                throw error(key + "[" + i + "]", "entry with settings expected");
            }
            out.add(new Node(m, path + "." + key + "[" + i + "]"));
        }
        return out;
    }
}
