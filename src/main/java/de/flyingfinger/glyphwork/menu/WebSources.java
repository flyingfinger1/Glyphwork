package de.flyingfinger.glyphwork.menu;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonPrimitive;
import de.flyingfinger.glyphwork.api.View;
import org.jspecify.annotations.Nullable;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * JSON from web APIs, set up in config.yml ({@code web:}): {@code %web_<name>_<path>%} shows one
 * value ({@code %web_status_players.online%}), {@code items-from: "web:<name>.<path>"} turns an array
 * into list entries. Loaded in the background like feeds, every source at most once per
 * {@code every}.
 */
public final class WebSources {

    /** One source of config.yml. */
    public record Source(String name, String url, Duration every, Map<String, String> headers) {
    }

    static final Pattern NAME = Pattern.compile("[a-z0-9-]+");
    /** %web_name_path%; the name has no '_', so the first one ends it. */
    static final Pattern PLACEHOLDER = Pattern.compile("%web_([a-z0-9-]+)_([^%\\s|]+)(?:[|]([a-z-]+))?%");

    private static final int MAX_ITEMS = 100;
    private static final int MAX_KEYS = 200;

    private static volatile Map<String, Source> sources = Map.of();
    /** Replaced in tests: the JSON of a source (null: not loaded yet), and whether it is loading. */
    private static volatile java.util.function.Function<Source, Web.Result<JsonElement>> loader = WebSources::load;

    private WebSources() {
    }

    public static void configure(Map<String, Source> configured) {
        sources = Map.copyOf(configured);
    }

    static void loader(java.util.function.Function<Source, Web.Result<JsonElement>> l) {
        loader = l;
    }

    static boolean known(String name) {
        return sources.containsKey(name);
    }

    /** Reads {@code web:} of config.yml: name -> url, every, headers. Problems go to the log. */
    public static Map<String, Source> read(org.bukkit.configuration.@Nullable ConfigurationSection web, Logger log) {
        Map<String, Source> out = new LinkedHashMap<>();
        if (web == null) {
            return out;
        }
        for (String name : web.getKeys(false)) {
            org.bukkit.configuration.ConfigurationSection s = web.getConfigurationSection(name);
            String url = s == null ? web.getString(name) : s.getString("url");
            if (!NAME.matcher(name).matches()) {
                log.warning("web." + name + ": names use a-z, 0-9 and '-' (no '_'), skipped");
                continue;
            }
            if (url == null || Web.problem(url) != null) {
                log.warning("web." + name + ": " + (url == null ? "url missing" : Web.problem(url)) + ", skipped");
                continue;
            }
            Duration every = de.flyingfinger.glyphwork.core.Settings.duration(
                s == null ? null : s.getString("every"), Duration.ofMinutes(1), "web." + name + ".every", log::warning);
            Map<String, String> headers = new LinkedHashMap<>();
            org.bukkit.configuration.ConfigurationSection h = s == null ? null : s.getConfigurationSection("headers");
            if (h != null) {
                for (String k : h.getKeys(false)) {
                    headers.put(k, String.valueOf(h.get(k)));
                }
            }
            out.put(name, new Source(name, url, every, headers));
        }
        return out;
    }

    private static Web.Result<JsonElement> load(Source s) {
        return Web.get("web:" + s.name(), s.url(), s.every(), s.headers(), "application/json, */*;q=0.5", "Web source " + s.name(),
            data -> JsonParser.parseString(new String(data, StandardCharsets.UTF_8)));
    }

    /**
     * The value of %web_name_path% (without filter), or null if there is no such source. While the
     * source loads for the first time the value is empty and the menu is drawn again soon.
     */
    static @Nullable String value(@Nullable View v, String name, String path) {
        Source src = sources.get(name);
        if (src == null) {
            return null;
        }
        Web.Result<JsonElement> r = loader.apply(src);
        if (r.loading() && v != null) {
            v.redrawAfter(Duration.ofMillis(500));
        }
        return r.value() == null ? "" : format(at(r.value(), path));
    }

    /** Replaces %web_name_path%; while a source is loading for the first time, the menu is drawn again soon. */
    static String fill(@Nullable View v, String text) {
        if (!text.contains("%web_")) {
            return text;
        }
        Matcher m = PLACEHOLDER.matcher(text);
        StringBuilder sb = new StringBuilder();
        while (m.find()) {
            Source src = sources.get(m.group(1));
            String value;
            if (src == null) {
                value = m.group();                                       // unknown source: stays visible
            } else {
                Web.Result<JsonElement> r = loader.apply(src);
                if (r.loading() && v != null) {
                    v.redrawAfter(Duration.ofMillis(500));
                }
                value = r.value() == null ? "" : format(at(r.value(), m.group(2)));
                if (m.group(3) != null) {
                    value = Filters.apply(m.group(3), value);
                }
            }
            m.appendReplacement(sb, Matcher.quoteReplacement(value));
        }
        m.appendTail(sb);
        return sb.toString();
    }

    /** Entries of {@code items-from: "web:name.path"}: the array there, each element flattened. */
    static List<Map<String, String>> items(@Nullable View v, String spec) {
        int dot = spec.indexOf('.');
        Source src = sources.get(dot < 0 ? spec : spec.substring(0, dot));
        if (src == null) {
            return List.of();
        }
        Web.Result<JsonElement> r = loader.apply(src);
        if (r.loading() && v != null) {
            v.redrawAfter(Duration.ofMillis(500));
        }
        JsonElement e = r.value() == null ? null : dot < 0 ? r.value() : at(r.value(), spec.substring(dot + 1));
        List<Map<String, String>> out = new ArrayList<>();
        if (e instanceof JsonArray a) {
            for (JsonElement x : a) {
                if (out.size() >= MAX_ITEMS) {
                    break;
                }
                out.add(flatten(x));
            }
        } else if (e instanceof JsonObject o) {
            // an object of objects ({"alice": {...}, "bob": {...}}): one entry each, the key as %item_key%
            for (Map.Entry<String, JsonElement> x : o.entrySet()) {
                if (out.size() >= MAX_ITEMS) {
                    break;
                }
                Map<String, String> m = flatten(x.getValue());
                m.put("key", x.getKey());
                if (m.get("").isEmpty()) {
                    m.put("", x.getKey());
                }
                out.add(m);
            }
        }
        return out;
    }

    /**
     * The value at a dotted path: object keys, array indexes ({@code list.0.name}); {@code length}
     * of an array is its size. Null if there is nothing.
     */
    static @Nullable JsonElement at(JsonElement root, String path) {
        JsonElement e = root;
        for (String part : path.split("\\.")) {
            if (e == null || e.isJsonNull()) {
                return null;
            }
            if (e instanceof JsonObject o) {
                e = o.get(part);
            } else if (e instanceof JsonArray a) {
                if (part.equals("length") || part.equals("size")) {
                    e = new JsonPrimitive(a.size());
                } else {
                    try {
                        int i = Integer.parseInt(part);
                        e = i >= 0 && i < a.size() ? a.get(i) : null;
                    } catch (NumberFormatException x) {
                        return null;
                    }
                }
            } else {
                return null;
            }
        }
        return e;
    }

    /** Text of a value: numbers without ".0", strings as they are, objects and arrays as JSON. */
    static String format(@Nullable JsonElement e) {
        if (e == null || e.isJsonNull()) {
            return "";
        }
        if (e instanceof JsonPrimitive p) {
            if (p.isNumber()) {
                try {
                    return new BigDecimal(p.getAsString()).stripTrailingZeros().toPlainString();
                } catch (NumberFormatException x) {
                    return p.getAsString();
                }
            }
            return p.getAsString();
        }
        return e.toString();
    }

    /**
     * An entry of a web list: entries of one array differ (one message has an embed, the next not),
     * so a key that is missing here is empty instead of staying as %item_...%.
     */
    private static final class Entry extends LinkedHashMap<String, String> {
        @Override
        public String get(Object key) {
            String v = super.get(key);
            return v == null ? "" : v;
        }
    }

    /** Every value of an element under its dotted path ({@code user.name}); "" is a plain value itself. */
    static Map<String, String> flatten(JsonElement e) {
        Map<String, String> out = new Entry();
        out.put("", e instanceof JsonPrimitive ? format(e) : "");
        flatten(e, "", out, 0);
        return out;
    }

    private static void flatten(JsonElement e, String prefix, Map<String, String> out, int depth) {
        if (out.size() >= MAX_KEYS || depth > 4) {
            return;
        }
        if (e instanceof JsonObject o) {
            for (Map.Entry<String, JsonElement> x : o.entrySet()) {
                String key = prefix + x.getKey();
                if (x.getValue() instanceof JsonPrimitive || x.getValue().isJsonNull()) {
                    out.put(key, format(x.getValue()));
                } else {
                    if (x.getValue() instanceof JsonArray a) {
                        out.put(key + ".length", String.valueOf(a.size()));
                    }
                    flatten(x.getValue(), key + ".", out, depth + 1);
                }
            }
        } else if (e instanceof JsonArray a) {
            for (int i = 0; i < a.size() && i < 20; i++) {
                JsonElement x = a.get(i);
                if (x instanceof JsonPrimitive || x.isJsonNull()) {
                    out.put(prefix + i, format(x));
                } else {
                    flatten(x, prefix + i + ".", out, depth + 1);
                }
            }
        }
    }
}
