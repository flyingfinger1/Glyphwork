package de.flyingfinger.glyphwork.core;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.jspecify.annotations.Nullable;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.logging.Logger;
import java.util.stream.Stream;

/**
 * Texts of the plugin itself (chat messages, its own dialogs, command words) in several languages.
 *
 * <p>Built in: {@code lang/de.yml} and {@code lang/en.yml}. Files in {@code plugins/Glyphwork/lang/}
 * replace single keys or add languages ({@code fr.yml}); keys missing there come from the built-in
 * file of the same language, then from the fallback language. Texts are MiniMessage; {@code <name>}
 * is filled with the values passed in (as plain text, so a value cannot inject formatting).
 */
public final class Messages {

    /** Languages that ship with the plugin. */
    public static final List<String> BUILTIN = List.of("de", "en");

    private static final MiniMessage MM = MiniMessage.miniMessage();
    private static volatile Messages active = builtin();

    private final Map<String, Map<String, String>> langs;
    private final @Nullable String fixed;
    private final String fallback;

    private Messages(Map<String, Map<String, String>> langs, @Nullable String fixed, String fallback) {
        this.langs = langs;
        this.fixed = fixed;
        this.fallback = langs.containsKey(fallback) ? fallback : "en";
    }

    /** The messages in use (set by the plugin on start and reload). */
    public static Messages active() {
        return active;
    }

    public static void activate(Messages m) {
        active = m;
    }

    /** Only the built-in files, language from each player's client, English otherwise. */
    public static Messages builtin() {
        Map<String, Map<String, String>> langs = new LinkedHashMap<>();
        for (String code : BUILTIN) {
            langs.put(code, resource(code));
        }
        return new Messages(langs, null, "en");
    }

    /**
     * @param dir      plugins/Glyphwork/lang (may be missing)
     * @param language "auto" (each player's game language) or a language code
     * @param fallback language for players whose language has no file, and for the console
     */
    public static Messages load(Path dir, String language, String fallback, Logger log) {
        Map<String, Map<String, String>> langs = new LinkedHashMap<>();
        for (String code : BUILTIN) {
            langs.put(code, new LinkedHashMap<>(resource(code)));
        }
        if (Files.isDirectory(dir)) {
            List<Path> files;
            try (Stream<Path> s = Files.list(dir)) {
                files = s.filter(p -> p.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".yml")).sorted().toList();
            } catch (IOException e) {
                log.warning("lang: " + e.getMessage());
                files = List.of();
            }
            for (Path f : files) {
                String code = f.getFileName().toString().replaceFirst("(?i)\\.yml$", "").toLowerCase(Locale.ROOT);
                try {
                    Map<String, String> own = parse(Files.readString(f, StandardCharsets.UTF_8));
                    Set<String> unknown = new LinkedHashSet<>(own.keySet());
                    unknown.removeAll(resource("en").keySet());
                    if (!unknown.isEmpty()) {
                        log.warning("lang/" + f.getFileName() + ": unknown keys " + unknown + " (ignored)");
                    }
                    langs.computeIfAbsent(code, k -> new LinkedHashMap<>()).putAll(own);
                } catch (IOException | InvalidConfigurationException e) {
                    log.warning("lang/" + f.getFileName() + " not loaded: " + e.getMessage());
                }
            }
        }
        String lang = language == null ? "auto" : language.trim().toLowerCase(Locale.ROOT);
        String fb = fallback == null ? "en" : fallback.trim().toLowerCase(Locale.ROOT);
        if (!langs.containsKey(fb)) {
            log.warning("fallback-language '" + fb + "' has no file, using en");
        }
        String fixed = lang.equals("auto") ? null : lang;
        if (fixed != null && !langs.containsKey(fixed)) {
            log.warning("language '" + fixed + "' has no file (lang/" + fixed + ".yml), using auto");
            fixed = null;
        }
        return new Messages(langs, fixed, fb);
    }

    /** Copies the built-in language files to {@code dir} if they are missing there (to edit them). */
    public static void export(Path dir) throws IOException {
        Files.createDirectories(dir);
        for (String code : BUILTIN) {
            Path f = dir.resolve(code + ".yml");
            if (!Files.exists(f)) {
                try (InputStream in = Messages.class.getResourceAsStream("/lang/" + code + ".yml")) {
                    Files.write(f, in.readAllBytes());
                }
            }
        }
    }

    private static Map<String, String> resource(String code) {
        try (InputStream in = Messages.class.getResourceAsStream("/lang/" + code + ".yml")) {
            return parse(new String(in.readAllBytes(), StandardCharsets.UTF_8));
        } catch (IOException | InvalidConfigurationException | NullPointerException e) {
            throw new IllegalStateException("built-in lang/" + code + ".yml broken", e);
        }
    }

    private static Map<String, String> parse(String yaml) throws InvalidConfigurationException {
        YamlConfiguration c = new YamlConfiguration();
        c.loadFromString(yaml);
        Map<String, String> out = new LinkedHashMap<>();
        for (String k : c.getKeys(true)) {
            if (!c.isConfigurationSection(k)) {
                out.put(k, String.valueOf(c.get(k)));
            }
        }
        return out;
    }

    /** Language code for this receiver: the fixed one, or the client's language if there is a file for it. */
    public String language(@Nullable CommandSender to) {
        if (fixed != null) {
            return fixed;
        }
        if (to instanceof Player p) {
            Locale l = p.locale();
            if (l != null && langs.containsKey(l.getLanguage())) {
                return l.getLanguage();
            }
        }
        return fallback;
    }

    /** The text of {@code key} in exactly this language, without falling back, or null. */
    public @Nullable String ownRaw(String lang, String key) {
        Map<String, String> m = langs.get(lang);
        return m == null ? null : m.get(key);
    }

    /** The raw text (MiniMessage) of {@code key} in {@code lang}, falling back to the fallback language. */
    public String raw(String lang, String key) {
        Map<String, String> m = langs.get(lang);
        String s = m == null ? null : m.get(key);
        if (s == null) {
            s = langs.get(fallback).get(key);
        }
        if (s == null) {
            s = langs.get("en").getOrDefault(key, key);
        }
        return s;
    }

    /**
     * The message for {@code to}. {@code args} are name/value pairs: {@code get(p, "menu.unknown", "id", id)}.
     */
    public Component get(@Nullable CommandSender to, String key, Object... args) {
        return format(raw(language(to), key), args);
    }

    /** The message as plain text (for places that take a String). */
    public String plain(@Nullable CommandSender to, String key, Object... args) {
        return PlainTextComponentSerializer.plainText().serialize(get(to, key, args));
    }

    public static Component format(String text, Object... args) {
        List<TagResolver> r = new ArrayList<>();
        for (int i = 0; i + 1 < args.length; i += 2) {
            r.add(Placeholder.unparsed(String.valueOf(args[i]), String.valueOf(args[i + 1])));
        }
        return MM.deserialize(text, TagResolver.resolver(r));
    }

    /** All values of {@code key} in every language (command words are accepted in each). */
    public Set<String> everyLanguage(String key) {
        Set<String> out = new LinkedHashSet<>();
        for (String lang : langs.keySet()) {
            out.add(raw(lang, key).toLowerCase(Locale.ROOT));
        }
        return out;
    }

    public Set<String> languages() {
        return langs.keySet();
    }
}
