package de.flyingfinger.glyphwork.core;

import com.destroystokyo.paper.profile.PlayerProfile;
import de.flyingfinger.glyphwork.render.FaceSource;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.jspecify.annotations.Nullable;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.net.URI;
import java.net.URL;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Faces for big heads, taken from the players' skins. Online players carry their skin address in
 * their profile; other names and UUIDs are looked up through Paper (Mojang). The skin PNG is
 * fetched in the background, the 8 x 8 face plus hat is kept in memory. A failed lookup is retried
 * after a few minutes.
 */
public final class SkinFaces implements FaceSource {

    private static final long RETRY_MS = 5 * 60_000;

    private final Plugin plugin;
    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5))
        .followRedirects(HttpClient.Redirect.NORMAL).build();
    private final Map<String, int[]> faces = new ConcurrentHashMap<>();
    private final Map<String, Long> failed = new ConcurrentHashMap<>();
    private final Set<String> loading = ConcurrentHashMap.newKeySet();
    private final Map<String, List<Runnable>> waiting = new ConcurrentHashMap<>();

    public SkinFaces(Plugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public int @Nullable [] face(String who) {
        String key = FaceSource.key(who);
        int[] f = faces.get(key);
        if (f != null) {
            return f;
        }
        Long when = failed.get(key);
        if (when == null || System.currentTimeMillis() - when > RETRY_MS) {
            start(key, who.trim());
        }
        return null;
    }

    @Override
    public void whenLoaded(Set<String> who, Runnable then) {
        // one redraw per finished face is enough: the next drawing asks for the rest again
        Runnable once = new Runnable() {
            private boolean done;

            @Override
            public synchronized void run() {
                if (!done) {
                    done = true;
                    then.run();
                }
            }
        };
        for (String w : who) {
            String key = FaceSource.key(w);
            if (faces.containsKey(key)) {
                Bukkit.getScheduler().runTask(plugin, once);
                return;
            }
            waiting.computeIfAbsent(key, k -> new ArrayList<>()).add(once);
        }
    }

    private void start(String key, String who) {
        if (!loading.add(key)) {
            return;
        }
        PlayerProfile profile;
        String name;
        try {
            UUID id = parseUuid(who);
            Player online = id != null ? Bukkit.getPlayer(id) : Bukkit.getPlayerExact(who);
            name = online != null ? online.getName() : id != null ? Bukkit.getOfflinePlayer(id).getName() : who;
            profile = online != null ? online.getPlayerProfile()
                : id != null ? Bukkit.createProfile(id) : Bukkit.createProfile(who);
        } catch (RuntimeException e) {
            done(key, null);
            return;
        }
        CompletableFuture<PlayerProfile> complete = profile.hasTextures()
            ? CompletableFuture.completedFuture(profile) : profile.update();
        complete
            .thenApply(p -> p.getTextures().getSkin())
            .exceptionally(e -> null)
            .thenCompose(skin -> {
                if (skin != null) {
                    return CompletableFuture.completedFuture(skin.toString());
                }
                // offline-mode servers (and proxies without forwarding) give players offline UUIDs and
                // no textures, and Paper resolves names the same way there: ask Mojang by name, like
                // the client does for its own heads
                return name == null ? CompletableFuture.completedFuture((String) null) : mojangSkin(name);
            })
            .thenCompose(skin -> {
                if (skin == null) {
                    plugin.getLogger().info("Big head of '" + who + "': no skin found");
                    return CompletableFuture.completedFuture((byte[]) null);
                }
                return get(skin.replace("http://", "https://")).thenApply(body -> {
                    if (body == null) {
                        plugin.getLogger().info("Big head of '" + who + "': skin " + skin + " could not be downloaded");
                    }
                    return body;
                });
            })
            .thenApply(bytes -> {
                try {
                    return bytes == null ? null : extract(ImageIO.read(new ByteArrayInputStream(bytes)));
                } catch (IOException e) {
                    return null;
                }
            })
            .whenComplete((face, error) -> {
                if (error != null) {
                    plugin.getLogger().info("Big head of '" + who + "' not loaded: " + error);
                }
                done(key, error == null ? face : null);
            });
    }

    private void done(String key, int @Nullable [] face) {
        loading.remove(key);
        if (face != null) {
            faces.put(key, face);
            failed.remove(key);
        } else {
            failed.put(key, System.currentTimeMillis());
        }
        List<Runnable> then = waiting.remove(key);
        if (then != null && face != null && plugin.isEnabled()) {
            Bukkit.getScheduler().runTask(plugin, () -> then.forEach(Runnable::run));
        }
    }

    /** GET, the body on 200, otherwise null. */
    private CompletableFuture<byte @Nullable []> get(String url) {
        HttpRequest req = HttpRequest.newBuilder(URI.create(url)).timeout(Duration.ofSeconds(10)).GET().build();
        return http.sendAsync(req, HttpResponse.BodyHandlers.ofByteArray())
            .thenApply(r -> r.statusCode() == 200 ? r.body() : null);
    }

    /** The skin address of a Mojang account by name (name -> UUID -> textures), or null. */
    private CompletableFuture<@Nullable String> mojangSkin(String name) {
        return get("https://api.mojang.com/users/profiles/minecraft/" + java.net.URLEncoder.encode(name, java.nio.charset.StandardCharsets.UTF_8))
            .thenCompose(body -> {
                if (body == null) {
                    return CompletableFuture.completedFuture(null);
                }
                String id = com.google.gson.JsonParser.parseString(new String(body, java.nio.charset.StandardCharsets.UTF_8))
                    .getAsJsonObject().get("id").getAsString();
                return get("https://sessionserver.mojang.com/session/minecraft/profile/" + id);
            })
            .thenApply(body -> body == null ? null : skinOf(new String(body, java.nio.charset.StandardCharsets.UTF_8)));
    }

    /** The SKIN url inside a session profile answer (textures property, base64 JSON), or null. */
    static @Nullable String skinOf(String profileJson) {
        try {
            for (com.google.gson.JsonElement p : com.google.gson.JsonParser.parseString(profileJson).getAsJsonObject()
                .getAsJsonArray("properties")) {
                com.google.gson.JsonObject o = p.getAsJsonObject();
                if ("textures".equals(o.get("name").getAsString())) {
                    String json = new String(java.util.Base64.getDecoder().decode(o.get("value").getAsString()),
                        java.nio.charset.StandardCharsets.UTF_8);
                    com.google.gson.JsonObject skin = com.google.gson.JsonParser.parseString(json).getAsJsonObject()
                        .getAsJsonObject("textures").getAsJsonObject("SKIN");
                    return skin == null ? null : skin.get("url").getAsString();
                }
            }
        } catch (RuntimeException e) {
            return null;
        }
        return null;
    }

    private static @Nullable UUID parseUuid(String s) {
        try {
            return UUID.fromString(s);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    /**
     * The 8 x 8 face of a skin (64 x 64, or the old 64 x 32) with the hat layer on top, as opaque
     * ARGB pixels row by row; null if the image is no skin.
     */
    public static int @Nullable [] extract(@Nullable BufferedImage skin) {
        if (skin == null || skin.getWidth() < 64 || skin.getHeight() < 16) {
            return null;
        }
        int[] out = new int[64];
        for (int y = 0; y < 8; y++) {
            for (int x = 0; x < 8; x++) {
                int face = skin.getRGB(8 + x, 8 + y) | 0xFF000000;
                int hat = skin.getRGB(40 + x, 8 + y);
                int a = hat >>> 24;
                out[y * 8 + x] = a == 0 ? face : blend(face, hat, a);
            }
        }
        return out;
    }

    private static int blend(int under, int over, int a) {
        int r = ((over >> 16 & 255) * a + (under >> 16 & 255) * (255 - a)) / 255;
        int g = ((over >> 8 & 255) * a + (under >> 8 & 255) * (255 - a)) / 255;
        int b = ((over & 255) * a + (under & 255) * (255 - a)) / 255;
        return 0xFF000000 | r << 16 | g << 8 | b;
    }
}
