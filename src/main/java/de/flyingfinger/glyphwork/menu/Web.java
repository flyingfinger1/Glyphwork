package de.flyingfinger.glyphwork.menu;

import org.jspecify.annotations.Nullable;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Logger;

/**
 * Loads web addresses in the background and keeps the parsed answer for a while (feeds, JSON
 * sources). Menus never wait: they get what is there and, while the first answer is missing, ask to
 * be drawn again. On errors the last answer stays; the next try comes after the cache time.
 */
final class Web {

    static final int MAX_BYTES = 1 << 20;

    private static volatile Logger log = Logger.getLogger("Glyphwork");

    private static final Map<String, State<?>> CACHE = new ConcurrentHashMap<>();
    private static final HttpClient HTTP = HttpClient.newBuilder()
        .connectTimeout(Duration.ofSeconds(5))
        .followRedirects(HttpClient.Redirect.NORMAL)
        .build();

    private Web() {
    }

    /** Turns the downloaded bytes into what the cache keeps. */
    interface Parser<T> {
        T parse(byte[] data) throws Exception;
    }

    /** The answer so far (null: none yet), and whether the first load is still running. */
    record Result<T>(@Nullable T value, boolean loading) {
    }

    private static final class State<T> {
        volatile @Nullable T value;
        volatile long fetched;
        volatile boolean loading;
    }

    static void logger(Logger logger) {
        log = logger;
    }

    /** Forgets every answer (reload). */
    static void clear() {
        CACHE.clear();
    }

    /** Checks an address: http or https with a host; null if fine. */
    static @Nullable String problem(String url) {
        try {
            URI u = URI.create(url);
            if (!"http".equalsIgnoreCase(u.getScheme()) && !"https".equalsIgnoreCase(u.getScheme())) {
                return "only http:// and https:// addresses work";
            }
            if (u.getHost() == null) {
                return "the address has no host";
            }
            return null;
        } catch (IllegalArgumentException e) {
            return "not a valid address (" + e.getMessage() + ")";
        }
    }

    /**
     * The cached answer for {@code key}; starts loading {@code url} when there is none yet or it is
     * older than {@code ttl}.
     */
    @SuppressWarnings("unchecked")
    static <T> Result<T> get(String key, String url, Duration ttl, Map<String, String> headers, String accept,
                             String what, Parser<T> parser) {
        State<T> s = (State<T>) CACHE.computeIfAbsent(key, k -> new State<T>());
        long now = System.currentTimeMillis();
        if (!s.loading && (s.fetched == 0 || now - s.fetched > ttl.toMillis())) {
            load(url, headers, accept, what, parser, s);
        }
        T value = s.value;
        return new Result<>(value, value == null && s.loading);
    }

    private static <T> void load(String url, Map<String, String> headers, String accept, String what, Parser<T> parser,
                                 State<T> s) {
        synchronized (s) {
            if (s.loading) {
                return;
            }
            s.loading = true;
        }
        HttpRequest.Builder req = HttpRequest.newBuilder(URI.create(url))
            .timeout(Duration.ofSeconds(10))
            .header("User-Agent", "Glyphwork (Minecraft server menu)")
            .header("Accept", accept);
        headers.forEach(req::header);
        HTTP.sendAsync(req.GET().build(), HttpResponse.BodyHandlers.ofInputStream()).whenComplete((resp, err) -> {
            try {
                if (err != null) {
                    throw err;
                }
                try (InputStream in = resp.body()) {
                    if (resp.statusCode() / 100 != 2) {
                        throw new IOException("HTTP " + resp.statusCode());
                    }
                    byte[] data = in.readNBytes(MAX_BYTES + 1);
                    if (data.length > MAX_BYTES) {
                        throw new IOException("larger than " + MAX_BYTES / 1024 + " KB");
                    }
                    s.value = parser.parse(data);
                }
            } catch (Throwable e) {
                // keep what we had; try again after the cache time
                log.warning(what + " " + url + " could not be loaded: " + e.getMessage());
            }
            s.fetched = System.currentTimeMillis();
            s.loading = false;
        });
    }
}
