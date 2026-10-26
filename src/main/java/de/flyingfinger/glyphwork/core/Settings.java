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
    boolean playerCommands) {

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
            c.getString("pack.prompt", ""),
            c.getString("pack-server.bind", "0.0.0.0"),
            c.getInt("pack-server.port", 8765),
            c.getBoolean("fallback", true),
            c.getBoolean("icons", true),
            c.getBoolean("players.commands", true));
    }

    /** Download URL for a pack hash; without a {sha1} placeholder the hash is appended. */
    public String url(String sha1) {
        if (urlTemplate.contains("{sha1}")) {
            return urlTemplate.replace("{sha1}", sha1);
        }
        return (urlTemplate.endsWith("/") ? urlTemplate : urlTemplate + "/") + sha1 + ".zip";
    }
}
