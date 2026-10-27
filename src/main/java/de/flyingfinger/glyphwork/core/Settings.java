package de.flyingfinger.glyphwork.core;

import org.bukkit.configuration.file.FileConfiguration;

/** config.yml, read once per (re)load. */
public record Settings(
    String theme,
    int maxHeight,
    boolean external,
    String urlTemplate,
    boolean sendOnJoin,
    boolean required,
    String prompt,
    String bind,
    int port,
    boolean fallback,
    boolean icons,
    String iconSourcePack,
    boolean playerCommands,
    String language,
    String fallbackLanguage,
    String warningIcon,
    String warningIconHover,
    java.time.Duration feedCache,
    String feedDateFormat,
    boolean provided,
    boolean providedAlways) {

    /** "embedded", "external" or "provided" (the Glyphwork part is merged into a pack the server or proxy sends). */
    public String hosting() {
        return provided ? "provided" : external ? "external" : "embedded";
    }

    /** The prompt that config.yml had before 0.7; treated as "use the language files". */
    private static final String OLD_PROMPT = "Glyphwork braucht dieses Resource-Pack für die Menüs.";

    public static Settings read(FileConfiguration c) {
        String url = c.getString("pack.url");
        if (url == null || url.isBlank()) {
            // config from before 0.1: pack-server.public-url was the base URL
            String base = c.getString("pack-server.public-url", "http://127.0.0.1:8765/");
            url = (base.endsWith("/") ? base : base + "/") + "{sha1}.zip";
        }
        return new Settings(
            c.getString("theme", "vanilla"),
            Math.max(16, Math.min(1024, c.getInt("max-height", 256))),
            "external".equalsIgnoreCase(c.getString("pack.hosting", "embedded")),
            url,
            c.getBoolean("pack.send-on-join", true),
            c.getBoolean("pack.required", false),
            OLD_PROMPT.equals(c.getString("pack.prompt", "")) ? "" : c.getString("pack.prompt", ""),
            c.getString("pack-server.bind", "0.0.0.0"),
            c.getInt("pack-server.port", 8765),
            c.getBoolean("fallback", true),
            c.getBoolean("icons", true),
            c.getString("icon-source-pack", "auto"),
            c.getBoolean("players.commands", true),
            c.getString("language", "auto"),
            c.getString("fallback-language", "en"),
            c.getString("dialog-warning.icon", "").trim(),
            c.getString("dialog-warning.icon-hover", "").trim(),
            duration(c.getString("feeds.cache", "10m"), java.time.Duration.ofMinutes(10)),
            c.getString("feeds.date-format", "dd.MM.yyyy HH:mm"),
            "provided".equalsIgnoreCase(c.getString("pack.hosting", "embedded")),
            !"server-pack".equalsIgnoreCase(c.getString("pack.provided-loaded", "always")));
    }

    /** "30s", "5m", "1h", "1d" or seconds; the fallback for anything else (at least 10 s). */
    public static java.time.Duration duration(@org.jspecify.annotations.Nullable String s, java.time.Duration fallback) {
        if (s == null) {
            return fallback;
        }
        java.util.regex.Matcher m = java.util.regex.Pattern.compile("\\s*(\\d+)\\s*([smhd]?)\\s*").matcher(s.toLowerCase());
        if (!m.matches()) {
            return fallback;
        }
        long n = Long.parseLong(m.group(1));
        long sec = switch (m.group(2)) {
            case "m" -> n * 60;
            case "h" -> n * 3600;
            case "d" -> n * 86400;
            default -> n;
        };
        return java.time.Duration.ofSeconds(Math.max(10, sec));
    }

    /** Download URL for a pack hash; without a {sha1} placeholder the hash is appended. */
    public String url(String sha1) {
        if (urlTemplate.contains("{sha1}")) {
            return urlTemplate.replace("{sha1}", sha1);
        }
        return (urlTemplate.endsWith("/") ? urlTemplate : urlTemplate + "/") + sha1 + ".zip";
    }
}
