package de.flyingfinger.glyphwork.font;

import de.flyingfinger.glyphwork.core.Settings;
import de.flyingfinger.glyphwork.pack.PackFiles;
import org.jspecify.annotations.Nullable;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Duration;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Logger;

/**
 * The own fonts of config.yml ({@code fonts:}), read from plugins/Glyphwork/fonts/ or downloaded
 * once (cached by address in cache/fonts/). A font that cannot be read is skipped with a warning;
 * text that asks for it is drawn in the vanilla glyphs.
 */
public final class Fonts {

    private static final long MAX_BYTES = 20L * 1024 * 1024;

    private final Map<String, CustomFont> fonts;

    private Fonts(Map<String, CustomFont> fonts) {
        this.fonts = fonts;
    }

    public static Fonts none() {
        return new Fonts(Map.of());
    }

    public static Fonts load(List<Settings.FontSpec> specs, Path dataFolder, VanillaText fallback, Logger log) {
        Map<String, CustomFont> out = new LinkedHashMap<>();
        for (Settings.FontSpec spec : specs) {
            String where = "fonts." + spec.id();
            try {
                byte[] data = spec.file() != null ? file(dataFolder, spec.file()) : download(dataFolder, spec.url());
                CustomFont f = CustomFont.rasterize(spec.id(), data, spec.size(), spec.lineHeight(), spec.chars(), fallback);
                out.put(spec.id(), f);
                log.info("Font '" + spec.id() + "': " + f.glyphs() + " characters from "
                    + (spec.file() != null ? spec.file() : spec.url()) + ", size " + spec.size()
                    + ", line height " + f.lineHeight());
            } catch (IOException e) {
                log.warning(where + ": " + e.getMessage() + "; text in this font uses the Minecraft font");
            } catch (RuntimeException | LinkageError e) {
                // e.g. a JVM without font support (AWT): the rest of Glyphwork works without it
                log.warning(where + ": fonts cannot be drawn on this server (" + e + "); text uses the Minecraft font");
            }
        }
        return new Fonts(out);
    }

    private static byte[] file(Path dataFolder, String name) throws IOException {
        Path dir = dataFolder.resolve("fonts").normalize();
        Path p = dir.resolve(name).normalize();
        if (!p.startsWith(dir)) {
            throw new IOException("file '" + name + "' must be inside plugins/Glyphwork/fonts/");
        }
        if (!Files.isRegularFile(p)) {
            throw new IOException("plugins/Glyphwork/fonts/" + name + " not found");
        }
        if (Files.size(p) > MAX_BYTES) {
            throw new IOException(name + " is larger than " + MAX_BYTES / 1024 / 1024 + " MB");
        }
        return Files.readAllBytes(p);
    }

    private static byte[] download(Path dataFolder, String url) throws IOException {
        Path cache = dataFolder.resolve("cache").resolve("fonts");
        Files.createDirectories(cache);
        Path file = cache.resolve(PackFiles.sha1(url.getBytes(StandardCharsets.UTF_8)) + ".font");
        if (Files.isRegularFile(file)) {
            return Files.readAllBytes(file);
        }
        try {
            HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10))
                .followRedirects(HttpClient.Redirect.NORMAL).build();
            HttpResponse<InputStream> r = http.send(HttpRequest.newBuilder(URI.create(url))
                .timeout(Duration.ofSeconds(30)).header("User-Agent", "Glyphwork").GET().build(),
                HttpResponse.BodyHandlers.ofInputStream());
            if (r.statusCode() != 200) {
                throw new IOException(url + " answered " + r.statusCode());
            }
            byte[] data;
            try (InputStream in = r.body()) {
                data = in.readNBytes((int) MAX_BYTES + 1);
            }
            if (data.length > MAX_BYTES) {
                throw new IOException(url + " is larger than " + MAX_BYTES / 1024 / 1024 + " MB");
            }
            Path tmp = cache.resolve("download.tmp");
            Files.write(tmp, data);
            Files.move(tmp, file, StandardCopyOption.REPLACE_EXISTING);
            return data;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("download interrupted");
        } catch (IllegalArgumentException e) {
            throw new IOException("not a valid address: " + url);
        }
    }

    /** The font with this id, or null (unknown, or it could not be read). */
    public @Nullable CustomFont get(String id) {
        return fonts.get(id);
    }

    public Collection<CustomFont> all() {
        return fonts.values();
    }

    public void write(PackFiles pack) {
        for (CustomFont f : fonts.values()) {
            f.write(pack);
        }
    }
}
