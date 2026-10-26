package de.flyingfinger.glyphwork.pack;

import org.bukkit.Bukkit;
import org.jspecify.annotations.Nullable;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Duration;
import java.util.logging.Logger;

/**
 * Finds the resource pack whose textures the icons should match: the server's own pack from
 * server.properties ("auto"), a zip file or a URL. Downloads are cached by hash.
 */
public final class SourcePack {

    private static final long MAX_BYTES = 512L * 1024 * 1024;

    private SourcePack() {
    }

    /**
     * @param setting "auto", "" / "none", a path (relative to the plugin folder) or an http(s) URL
     * @return a local zip, or null for "use vanilla textures"
     */
    public static @Nullable Path resolve(String setting, Path dataFolder, Logger log) {
        String s = setting == null ? "" : setting.trim();
        if (s.isEmpty() || s.equalsIgnoreCase("none")) {
            return null;
        }
        String hash = null;
        if (s.equalsIgnoreCase("auto")) {
            String url = Bukkit.getServer().getResourcePack();
            if (url == null || url.isBlank()) {
                return null; // no server pack: vanilla textures
            }
            s = url.trim();
            hash = Bukkit.getServer().getResourcePackHash();
        }
        if (s.startsWith("http://") || s.startsWith("https://")) {
            return download(s, hash, dataFolder.resolve("cache"), log);
        }
        Path p = dataFolder.resolve(s);
        if (!Files.isRegularFile(p)) {
            log.warning("icon-source-pack: " + p + " not found, using vanilla texture sizes");
            return null;
        }
        return p;
    }

    private static @Nullable Path download(String url, @Nullable String hash, Path cache, Logger log) {
        try {
            Files.createDirectories(cache);
            String key = hash != null && hash.matches("[0-9a-fA-F]{40}") ? hash.toLowerCase()
                : PackFiles.sha1(url.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            Path file = cache.resolve("source-pack-" + key + ".zip");
            if (Files.isRegularFile(file) && (hash == null || hash.isBlank() || matches(file, hash))) {
                return file;
            }
            long start = System.currentTimeMillis();
            HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10))
                .followRedirects(HttpClient.Redirect.NORMAL).build();
            HttpResponse<InputStream> r = http.send(HttpRequest.newBuilder(URI.create(url))
                .timeout(Duration.ofSeconds(60)).header("User-Agent", "Glyphwork").GET().build(),
                HttpResponse.BodyHandlers.ofInputStream());
            if (r.statusCode() != 200) {
                log.warning("icon-source-pack: " + url + " answered " + r.statusCode() + ", using vanilla texture sizes");
                return null;
            }
            Path tmp = cache.resolve("download.tmp");
            try (InputStream in = r.body()) {
                Files.copy(new LimitedInputStream(in, MAX_BYTES), tmp, StandardCopyOption.REPLACE_EXISTING);
            }
            if (hash != null && !hash.isBlank() && !matches(tmp, hash)) {
                log.warning("icon-source-pack: downloaded pack does not match resource-pack-sha1, using it anyway");
            }
            Files.move(tmp, file, StandardCopyOption.REPLACE_EXISTING);
            log.info("icon-source-pack: downloaded " + Files.size(file) / 1024 + " KB in "
                + (System.currentTimeMillis() - start) + " ms");
            return file;
        } catch (IOException | RuntimeException e) {
            log.warning("icon-source-pack: " + url + " not loaded (" + e.getMessage() + "), using vanilla texture sizes");
            return null;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return null;
        }
    }

    private static boolean matches(Path file, String hash) throws IOException {
        return PackFiles.sha1(Files.readAllBytes(file)).equalsIgnoreCase(hash.trim());
    }

    /** Stops runaway downloads. */
    private static final class LimitedInputStream extends java.io.FilterInputStream {
        private long left;

        LimitedInputStream(InputStream in, long limit) {
            super(in);
            this.left = limit;
        }

        @Override
        public int read() throws IOException {
            if (left-- <= 0) {
                throw new IOException("pack larger than " + MAX_BYTES / 1024 / 1024 + " MB");
            }
            return super.read();
        }

        @Override
        public int read(byte[] b, int off, int len) throws IOException {
            if (left <= 0) {
                throw new IOException("pack larger than " + MAX_BYTES / 1024 / 1024 + " MB");
            }
            int n = super.read(b, off, (int) Math.min(len, left));
            if (n > 0) {
                left -= n;
            }
            return n;
        }
    }
}
