package de.flyingfinger.glyphwork.core;

import org.bukkit.configuration.file.FileConfiguration;

/** config.yml, read once per (re)load. */
public record Settings(
    String theme,
    int minHeight,
    boolean external,
    String urlTemplate,
    boolean sendOnJoin,
    boolean required,
    String bind,
    int port,
    boolean plainDialog,
    boolean icons,
    String iconSourcePack,
    boolean playerCommands,
    String language,
    String fallbackLanguage,
    String warningIcon,
    String warningIconHover,
    java.time.Duration feedCache,
    String dateFormat,
    boolean provided,
    boolean providedAlways) {

    /** Version of the config layout; config.yml files without one are from before 1.0. */
    public static final int CONFIG_VERSION = 1;

    /** "embedded", "external" or "provided" (the Glyphwork part is merged into a pack the server or proxy sends). */
    public String hosting() {
        return provided ? "provided" : external ? "external" : "embedded";
    }

    private static final java.util.Set<String> TOP = java.util.Set.of("config-version", "language", "fallback-language",
        "theme", "pack", "pack-server", "plain-dialog", "icons", "dialog-warning", "players", "date-format", "feeds", "web",
        // read with a hint, from before 1.0
        "max-height", "fallback", "icon-source-pack");
    private static final java.util.Set<String> PACK = java.util.Set.of("hosting", "url", "send-on-join", "required",
        "provided-loaded", "min-height", "prompt");

    /** Reads config.yml; keys from before 1.0 still work, {@code warn} gets a hint for each, and for unknown keys. */
    public static Settings read(FileConfiguration c, java.util.function.Consumer<String> warn) {
        for (String k : c.getKeys(false)) {
            if (!TOP.contains(k)) {
                warn.accept("config.yml: unknown setting '" + k + "'");
            }
        }
        org.bukkit.configuration.ConfigurationSection pack = c.getConfigurationSection("pack");
        if (pack != null) {
            for (String k : pack.getKeys(false)) {
                if (!PACK.contains(k)) {
                    warn.accept("config.yml: unknown setting 'pack." + k + "'");
                }
            }
        }
        String hosting = c.getString("pack.hosting", "embedded").toLowerCase();
        if (!java.util.Set.of("embedded", "external", "provided").contains(hosting)) {
            warn.accept("config.yml: pack.hosting '" + hosting + "' is unknown (embedded, external, provided); using embedded");
            hosting = "embedded";
        }
        String loaded = c.getString("pack.provided-loaded", "always").toLowerCase();
        if (!loaded.equals("always") && !loaded.equals("server-pack")) {
            warn.accept("config.yml: pack.provided-loaded '" + loaded + "' is unknown (always, server-pack); using always");
            loaded = "always";
        }
        if (c.contains("pack-server.public-url", true)) {
            warn.accept("config.yml: pack-server.public-url is no longer read; set pack.url (with {sha1})");
        }
        if (c.contains("pack.prompt", true)) {
            warn.accept("config.yml: pack.prompt is no longer read; the text is pack.prompt in the language files");
        }
        int minHeight = c.contains("pack.min-height", true) ? c.getInt("pack.min-height", 256)
            : old(c, "max-height", "pack.min-height", warn) ? c.getInt("max-height", 256) : 256;
        if (minHeight < 16 || minHeight > 2048) {
            warn.accept("config.yml: pack.min-height " + minHeight + " is outside 16..2048");
            minHeight = Math.max(16, Math.min(2048, minHeight));
        }
        boolean plain = c.contains("plain-dialog", true) ? c.getBoolean("plain-dialog", true)
            : !old(c, "fallback", "plain-dialog", warn) || c.getBoolean("fallback", true);
        boolean icons;
        String source;
        if (c.isBoolean("icons")) {
            warn.accept("config.yml: 'icons: true/false' is now 'icons: { enabled: ... }'");
            icons = c.getBoolean("icons", true);
        } else {
            icons = c.getBoolean("icons.enabled", true);
        }
        source = c.contains("icons.source-pack", true) ? c.getString("icons.source-pack", "auto")
            : old(c, "icon-source-pack", "icons.source-pack", warn) ? c.getString("icon-source-pack", "auto") : "auto";
        String dates = c.contains("date-format", true) ? c.getString("date-format", "dd.MM.yyyy HH:mm")
            : old(c, "feeds.date-format", "date-format", warn) ? c.getString("feeds.date-format", "dd.MM.yyyy HH:mm")
            : "dd.MM.yyyy HH:mm";
        return new Settings(
            c.getString("theme", "vanilla"),
            minHeight,
            hosting.equals("external"),
            c.getString("pack.url", "http://127.0.0.1:8765/{sha1}.zip"),
            c.getBoolean("pack.send-on-join", true),
            c.getBoolean("pack.required", false),
            c.getString("pack-server.bind", "0.0.0.0"),
            c.getInt("pack-server.port", 8765),
            plain,
            icons,
            source,
            c.getBoolean("players.commands", true),
            c.getString("language", "auto"),
            c.getString("fallback-language", "en"),
            c.getString("dialog-warning.icon", "").trim(),
            c.getString("dialog-warning.icon-hover", "").trim(),
            duration(c.getString("feeds.cache", "10m"), java.time.Duration.ofMinutes(10), "feeds.cache", warn),
            dates,
            hosting.equals("provided"),
            loaded.equals("always"));
    }

    /** True if a key from before 1.0 is there (with a hint to the new one). */
    private static boolean old(FileConfiguration c, String old, String now, java.util.function.Consumer<String> warn) {
        if (!c.contains(old, true)) {
            return false;
        }
        warn.accept("config.yml: '" + old + "' is now '" + now + "' (still read)");
        return true;
    }

    /** A duration of config.yml (at least 10 s); {@code fallback} with a hint if it is none. */
    public static java.time.Duration duration(@org.jspecify.annotations.Nullable String s, java.time.Duration fallback,
                                              String key, java.util.function.Consumer<String> warn) {
        if (s == null) {
            return fallback;
        }
        try {
            return java.time.Duration.ofMillis(Math.max(10_000, Durations.parse(s)));
        } catch (IllegalArgumentException e) {
            warn.accept("config.yml: " + key + ": " + e.getMessage() + "; using " + fallback.toSeconds() + " s");
            return fallback;
        }
    }


    /** Download URL for a pack hash; without a {sha1} placeholder the hash is appended. */
    public String url(String sha1) {
        if (urlTemplate.contains("{sha1}")) {
            return urlTemplate.replace("{sha1}", sha1);
        }
        return (urlTemplate.endsWith("/") ? urlTemplate : urlTemplate + "/") + sha1 + ".zip";
    }
}
